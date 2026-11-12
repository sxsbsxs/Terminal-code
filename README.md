# NovaTerm

**NovaTerm** is an open-source Android terminal workspace built with Kotlin and Jetpack Compose. It combines a modern Android interface with a developer-focused terminal and an optional rootless Debian environment.

> **Current status: early development (0.2.0).** The app now has an on-demand PRoot + Debian Bookworm ARM64 installer, SHA-256 verification, a first command runner for Debian, and a GitHub Actions debug APK pipeline. It is still an early prototype: persistent interactive PTY sessions, multiple terminal tabs, graphical package management and other CPU architectures are not ready.

## Current features

- Kotlin + Jetpack Compose / Material 3 interface
- Terminal, Files, Environment and Settings sections
- Android app-local shell via `/system/bin/sh`
- App-private workspace and working-directory handling
- On-demand Android ARM64 PRoot runtime download with SHA-256 verification
- On-demand Debian Bookworm ARM64 root filesystem download with SHA-256 verification
- Safe first-run extraction into app-private storage
- Debian commands launched through PRoot with app-private rootfs and isolated HOME
- GitHub Actions builds a debug APK and publishes it as a workflow artifact

## Install the Linux environment

1. Install the NovaTerm APK.
2. Open **Environment** and press **Install Debian Bookworm**.
3. Keep the app open and wait for the PRoot and Debian archives to be downloaded, verified and extracted.
4. Open **Terminal** and switch from **ANDROID** to **DEBIAN**.
5. Run:

```bash
apt update
apt install -y python3 git openssh-client
python3 --version
git --version
```

The initial Debian archive is about 48 MB compressed; the extracted root filesystem needs substantially more internal storage. The current installer supports `arm64-v8a` only.

## Important limitations

- PRoot's simulated root identity is **not Android root**. NovaTerm does not gain access to Android system partitions.
- Debian currently runs one command per process. A persistent PTY shell, interactive editors, terminal job control and multiple sessions are not implemented yet.
- The current file browser shows NovaTerm's app-private workspace. Android shared storage integration through the Storage Access Framework is planned.
- Python, Git and SSH are installed inside Debian with `apt`; they are not bundled in the APK.
- The Debian installer is pinned to a Debian Bookworm rootfs snapshot and a PRoot release checksum. A successful source commit does not mean that the runtime has already been tested on every device. Please report device-specific startup failures with the Android version and ABI.

See [the roadmap](docs/ROADMAP.md) and [third-party notices](THIRD_PARTY_NOTICES.md).

## Build

Requirements:

- JDK 17
- Android SDK Platform 35
- Gradle 8.9
- Android Gradle Plugin 8.7.3

From the project directory, run:

```bash
gradle --no-daemon assembleDebug
```

The APK is created at:

```
app/build/outputs/apk/debug/app-debug.apk
```

Every push to `main` or `master` and every pull request targeting those branches triggers **Android CI**. You can also run it manually from the repository's Actions tab. Download the `NovaTerm-debug` artifact from the completed workflow.

## Project structure

```
.
├── app/
│   └── src/main/
│       ├── java/dev/novaterm/
│       │   ├── MainActivity.kt
│       │   ├── ShellSession.kt
│       │   └── LinuxEnvironmentManager.kt
│       └── AndroidManifest.xml
├── .github/workflows/android.yml
├── docs/ROADMAP.md
├── CONTRIBUTING.md
├── THIRD_PARTY_NOTICES.md
├── LICENSE
└── settings.gradle.kts
```

## License

NovaTerm application code is GPL-3.0-only. Separately downloaded third-party runtime components retain their own licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
