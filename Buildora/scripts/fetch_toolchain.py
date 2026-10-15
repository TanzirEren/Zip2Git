#!/usr/bin/env python3
"""
Collects the on-device toolchain that gets bundled into the Buildora APK:

  app/src/main/assets/toolchain/android.jar            (copied from the Android SDK on the runner)
  app/src/main/assets/toolchain/aapt2-arm64-v8a        (Android/ARM64 build of aapt2)
  app/src/main/assets/toolchain/aapt2-armeabi-v7a      (Android/ARM32 build of aapt2)
  app/src/main/assets/toolchain/aapt2-x86_64           (optional, emulators)

aapt2 binaries are resolved in this order, per ABI:
  1. a file you committed:       toolchain/aapt2-<abi>
  2. a URL you configured:       env AAPT2_ARM64_URL / AAPT2_ARM_URL / AAPT2_X86_64_URL
                                 (repo "Variables" in GitHub Settings > Secrets and variables > Actions)
  3. auto-discovery in GitHub releases of AAPT2_REPOS (default: Lzhiyong/sdk-tools)

This script never fails the build if aapt2 cannot be found: Buildora can import its own aapt2 at runtime
(Settings > Toolchain). It prints a clear warning instead.
"""
import io, json, os, shutil, struct, sys, tarfile, tempfile, urllib.request, zipfile

DEST = "app/src/main/assets/toolchain"
ABIS = {
    "arm64-v8a": {"machine": 183, "tokens": ["aarch64", "arm64"], "env": "AAPT2_ARM64_URL"},
    "armeabi-v7a": {"machine": 40, "tokens": ["armv7", "arm32", "android-arm", "_arm", "-arm."], "env": "AAPT2_ARM_URL"},
    "x86_64": {"machine": 62, "tokens": ["x86_64", "x64"], "env": "AAPT2_X86_64_URL"},
}
REPOS = [r for r in os.environ.get("AAPT2_REPOS", "Lzhiyong/sdk-tools").split(",") if r.strip()]
AUTO_ABIS = ["arm64-v8a", "armeabi-v7a"]


def log(*a):
    print("[toolchain]", *a, flush=True)


def http(url, accept=None):
    req = urllib.request.Request(url, headers={"User-Agent": "buildora-ci"})
    tok = os.environ.get("GITHUB_TOKEN")
    if tok and "api.github.com" in url:
        req.add_header("Authorization", "Bearer " + tok)
    if accept:
        req.add_header("Accept", accept)
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read()


def elf_machine(path):
    try:
        with open(path, "rb") as f:
            h = f.read(20)
        if h[:4] != b"\x7fELF":
            return None
        return struct.unpack("<H", h[18:20])[0]
    except Exception:
        return None


def find_aapt2(root, machine):
    for dp, _, files in os.walk(root):
        for fn in files:
            if fn == "aapt2" or fn.startswith("aapt2-") or fn == "libaapt2.so":
                p = os.path.join(dp, fn)
                if elf_machine(p) == machine:
                    return p
    return None


def extract(blob, name, outdir):
    n = name.lower()
    if n.endswith(".zip"):
        zipfile.ZipFile(io.BytesIO(blob)).extractall(outdir)
    elif n.endswith((".tar.gz", ".tgz", ".tar.xz", ".tar.bz2", ".tar")):
        tarfile.open(fileobj=io.BytesIO(blob)).extractall(outdir)
    else:
        with open(os.path.join(outdir, "aapt2"), "wb") as f:
            f.write(blob)


def install(src, abi):
    dst = os.path.join(DEST, "aapt2-" + abi)
    shutil.copyfile(src, dst)
    os.chmod(dst, 0o755)
    log("OK ", abi, "->", dst, "(%d KB)" % (os.path.getsize(dst) // 1024))


def from_url(abi, url):
    log("downloading", abi, "from", url)
    blob = http(url)
    tmp = tempfile.mkdtemp()
    extract(blob, url.split("?")[0].split("/")[-1], tmp)
    p = find_aapt2(tmp, ABIS[abi]["machine"])
    if not p:
        log("   no matching aapt2 inside the download for", abi)
        return False
    install(p, abi)
    return True


def discover(abi):
    info = ABIS[abi]
    for repo in REPOS:
        try:
            rels = json.loads(http("https://api.github.com/repos/%s/releases?per_page=8" % repo.strip(),
                                   "application/vnd.github+json"))
        except Exception as e:
            log("   cannot list releases of", repo, "-", e)
            continue
        tries = 0
        for rel in rels:
            for a in rel.get("assets", []):
                name = a["name"].lower()
                if not any(t in name for t in info["tokens"]):
                    continue
                if abi == "armeabi-v7a" and ("aarch64" in name or "arm64" in name):
                    continue
                if not name.endswith((".zip", ".tar.gz", ".tgz", ".tar.xz", ".tar.bz2")) and "aapt2" not in name:
                    continue
                tries += 1
                if tries > 4:
                    break
                log("   candidate:", repo, a["name"], "(%d MB)" % (a["size"] // 1048576))
                try:
                    if from_url(abi, a["browser_download_url"]):
                        return True
                except Exception as e:
                    log("   failed:", e)
            if tries > 4:
                break
    return False


def main():
    os.makedirs(DEST, exist_ok=True)

    sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME") or ""
    jar = os.path.join(sdk, "platforms", "android-34", "android.jar")
    if not os.path.isfile(jar):
        log("FATAL: android.jar not found at", jar)
        return 1
    shutil.copyfile(jar, os.path.join(DEST, "android.jar"))
    log("OK  android.jar (%d MB)" % (os.path.getsize(jar) // 1048576))

    found = []
    for abi, info in ABIS.items():
        local = os.path.join("toolchain", "aapt2-" + abi)
        if os.path.isfile(local):
            if elf_machine(local) != info["machine"]:
                log("WARN", local, "is not a", abi, "ELF binary - ignored")
            else:
                install(local, abi)
                found.append(abi)
                continue
        url = os.environ.get(info["env"], "").strip()
        if url:
            try:
                if from_url(abi, url):
                    found.append(abi)
                    continue
            except Exception as e:
                log("WARN download failed for", abi, "-", e)
        if abi in AUTO_ABIS:
            log("searching GitHub releases for an", abi, "aapt2 ...")
            if discover(abi):
                found.append(abi)
            else:
                log("WARN no aapt2 found for", abi)

    if not any(a in found for a in ("arm64-v8a", "armeabi-v7a")):
        log("=" * 70)
        log("WARNING: no ARM aapt2 was bundled. The APK will still build, but on the phone")
        log("you must import an aapt2 binary in Settings > Toolchain before building apps.")
        log("To bundle one, commit it as toolchain/aapt2-arm64-v8a (see toolchain/README.md)")
        log("or set the repository variable AAPT2_ARM64_URL.")
        log("=" * 70)
    return 0


if __name__ == "__main__":
    sys.exit(main())
