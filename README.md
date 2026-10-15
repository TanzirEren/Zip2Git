<p align="center">
  <img src="docs/logo.png" width="128" alt="Buildora logo">
</p>

<h1 align="center">Buildora</h1>

<p align="center">
  <b>Build real Android apps with Java &amp; XML — entirely on your phone.</b><br>
  Write code, compile, sign and install an APK without a PC.
</p>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Language" src="https://img.shields.io/badge/built%20with-Java%20%2B%20XML-3F7EB8">
  <img alt="License" src="https://img.shields.io/badge/license-MIT-blue">
</p>

---

## What is Buildora?

Buildora is an on-device app builder. It contains a code editor and a complete build pipeline, so you can
create a project, press **▶**, and get a signed, installable APK in seconds — no computer, no cloud build.

```
Java sources ──ECJ──▶ .class ──D8──▶ dex ─┐
Resources / Manifest ──aapt2──▶ resources ─┴─▶ package ──apksig──▶ signed APK
```

Buildora itself is written in Java + XML and is built with GitHub Actions.

## Download

Get the latest `Buildora.apk` (v2.0.0) from the **[Releases](../../releases)** page and install it.
Android asks you to allow installing from your browser/file manager the first time.

**Requirements:** Android 8.0 (API 26) or newer, an ARM phone, ~300 MB free storage.
On first launch allow **All files access** — projects are stored in shared storage so you can reach them from any file manager.

## What's new in v2.0.0

- **Faster builds** - incremental builds reuse compiled resources and only recompile the Java files you changed (plus the files that use them).
  Nothing changed? Run finishes instantly. If a cache ever goes stale, Buildora retries once with a clean cache by itself.
- **Builds survive leaving the app** - they run in a foreground service with a progress notification (Cancel button included) and a wake lock, so a locked screen no longer stops them.
- **Clickable errors with plain-language hints** - tap an error to jump to the file and line. Missing import? One tap adds it. **Copy all** is still there.
- **126 libraries in the catalog** - every library from the community list plus Retrofit + Moshi / Gson, OkHttp interceptors, Room, Material 3, Navigation, Lottie,
  MPAndroidChart, Glide, Coil, Firebase (with `google-services.json` support), WorkManager, DataStore, ZXing, Media3/ExoPlayer, CameraX, Biometric and Play Services.
  Use `group:artifact` (or `+`) for the latest stable version; a missing pinned version falls back to the latest one.
- **Build variants** - debug / release / your own build types and product flavors with their own application id, version suffix, signing key,
  libraries, `BuildConfig` fields and source sets (`app/src/<flavor>/java`, `res`, `AndroidManifest.xml`).
- **Multi-module projects** - add Android or Java library modules, choose what the app and each module use.
- **Release builds** - R8 shrinking, obfuscation and optimisation with a `mapping.txt`, **AAB export**, signature verification and a zipalign / 16 KB check.
- **ProGuard rules editor** with one-tap presets (Gson, Retrofit/OkHttp, Moshi, Glide, Firebase, Room, WebView JS, Kotlin, EventBus, remove logs, ...).
- **App icon from the Gallery** - pick an icon straight from your photos for new projects and existing ones.

## Quick start

1. Open Buildora and tap **＋ → New project**.
2. Choose a name, package id, icon and template, then **Create**.
3. In the IDE tap **▶ Run**. When the build finishes tap **Install** (or **Download**).

Projects live in `/storage/emulated/0/Buildora/<Project_Name>/` and the finished
`<Project_Name>.apk` is placed right inside that folder.

## Features

**Projects**
- New project wizard (name, app name, package id, icon + corner radius, min/target SDK, theme, orientation)
- 4 buildable templates: Empty Activity, Counter, WebView, AppCompat
- App icon from the Gallery, a file, or the default Buildora icon (with corner radius)
- Import any folder (Gradle-style layouts are detected, including dependencies from `build.gradle`)
- Clone a Git repository
- Rename, duplicate, export as ZIP, delete

**Code IDE**
- Syntax highlighting for Java, XML, JSON, Gradle, properties and Smali
- Line numbers, open-file tabs, undo / redo, auto-save
- Two-finger pinch zoom
- Find &amp; replace (regex), go to line, code formatter
- Auto-indent, auto-close brackets and XML tags, symbol bar
- Live red error lines for XML / bracket mistakes, and compiler errors marked in the editor after a failed build
- 6 colour schemes, custom fonts, light / dark app theme

**File explorer**
- Glass side panel with a tree view
- Create / import files and folders
- Long-press menus: rename, duplicate, move, export, delete, properties, copy path, install / share APKs

**Build &amp; signing**
- Fully on-device pipeline: ECJ → aapt2 → D8 / R8 → packager → apksig (v1 + v2 + v3), incremental
- Library manager: Maven resolver (Google Maven, Maven Central, JitPack), AAR support, a 126-entry catalog, custom coordinates, latest-version lookup and local `.jar` / `.aar` files
- Generate, import and export signing keys; choose the active key
- Live build log, copyable error report, install / download / share the APK

**Git** — commit, pull, push and status (with a personal access token).

## Building Buildora from source

Buildora is built by GitHub Actions — no local Android setup needed.

1. Fork or upload this repository to GitHub.
2. Make sure the Android-native `aapt2` binaries are in [`toolchain/`](toolchain/README.md)
   (`aapt2-arm64-v8a`, optionally `aapt2-armeabi-v7a`).
3. Open **Actions → Build Buildora APK → Run workflow**.
4. Download the **Buildora-APK** artifact.

The workflow installs JDK 17 and the Android SDK platform, bundles `android.jar` and `aapt2` into the
app assets (`scripts/fetch_toolchain.py`), and runs `gradle :app:assembleDebug`.

## Project structure

```
app/src/main/java/com/tenix/buildora/
  MainActivity · IdeActivity · SettingsActivity · KeystoreActivity · LibrariesActivity · VariantsActivity · ModulesActivity · ProguardActivity
  BuildRunner · BuildManager · BuildService (foreground service)
  core/    projects, templates, file picker, icons, preferences, installer
  editor/  code editor view, highlighter, linter, colour schemes
  build/   build engine, incremental Java, Maven resolver, manifest merger, APK/AAB writers, R8 shrinker, signer, error hints, catalog, Git helper
app/src/main/java/javax/lang/model/   SourceVersion shim (Android has no javax.lang.model; ECJ needs it)
.github/workflows/build.yml           CI
scripts/fetch_toolchain.py            bundles android.jar + aapt2 into the APK
toolchain/                            Android-native aapt2 binaries
```

## Good to know

- **Java 8 + XML projects.** Buildora compiles Java sources at language level 1.8 (lambdas and method references work).
  Kotlin *libraries* can be used as dependencies, but Kotlin source files are not compiled.
- **No annotation processors** (Room, Dagger/Hilt, Glide's generated API, …) yet. Such libraries are marked in the catalog.
- **Modules are compiled together with the app** (merged sources, resources and manifests), not as separate artifacts.
- **R8 shrinks code only**; resource shrinking is not included. AAB export produces a single `base` module.
- **`targetSdk` is 28 on purpose.** Android 10+ blocks executing binaries from app storage for newer targets, and
  Buildora has to run `aapt2`. It also keeps plain file access to `/storage/emulated/0/Buildora`.
- Large library stacks (AppCompat, Material, …) use a simplified manifest merger and are the most likely place to hit edge cases.
- Signing-key passwords are stored in the app's private preferences.
- Builds run on-device, so heavy projects need RAM; the first build with libraries is slower (dex results are cached).

## Troubleshooting

| Problem | Fix |
|---|---|
| *aapt2 cannot run* | Put the right-architecture binary in `toolchain/`, or use **Settings → Toolchain → Import aapt2** |
| Build error | Tap the error to jump to the line, or **Copy all** and open an issue with the text |
| Odd build behaviour after a crash | **Settings → Build → Clear incremental build cache** |
| Can't see projects / save fails | Grant **All files access** in Android settings |
| Dependency download fails | Check your internet connection, or remove the library in **Libraries** |

## Contributing

Issues and pull requests are welcome. Please include your Android version, device architecture and the copied build
log when reporting a build problem.

## Third-party components

Buildora bundles open-source software, each under its own license: the Eclipse Compiler for Java (ECJ),
R8/D8, apksig, Eclipse JGit, SLF4J, AndroidX, the Android SDK `android.jar` and `aapt2`.
Please see each project for its license terms.

## License

Buildora's own source code is released under the [MIT License](LICENSE).
