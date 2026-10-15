# On-device toolchain (aapt2)

Buildora compiles Android resources with **aapt2**, which must be a build that runs *on Android*
(ELF for ARM64 / ARM32 — not the Linux x86 one shipped with the Android SDK).

The GitHub Action (`scripts/fetch_toolchain.py`) looks for it in this order:

1. **Files committed here** (recommended — fully reproducible):

   ```
   toolchain/aapt2-arm64-v8a      <- aapt2 for 64-bit ARM phones  (most phones)
   toolchain/aapt2-armeabi-v7a    <- aapt2 for 32-bit ARM phones  (optional)
   toolchain/aapt2-x86_64         <- aapt2 for x86_64 emulators   (optional)
   ```
   Just drop the raw binaries in with exactly these names and push.

2. **Repository variables** (Settings → Secrets and variables → Actions → *Variables*):
   `AAPT2_ARM64_URL`, `AAPT2_ARM_URL`, `AAPT2_X86_64_URL` — a direct download link to the binary,
   or to a `.zip` / `.tar.*` archive that contains a file named `aapt2`.

3. **Auto discovery** in the GitHub releases of the repos listed in the `AAPT2_REPOS` variable
   (default: `Lzhiyong/sdk-tools`, an Android-native build of the SDK tools).

If nothing is found the APK still builds. Open **Buildora → Settings → Toolchain** and use
*Import aapt2 binary* or *Download aapt2 from URL* on the phone.

`android.jar` (API 34) is copied automatically from the Android SDK on the runner.
