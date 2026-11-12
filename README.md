# NovaTerm

**NovaTerm** is an open-source Android terminal workspace built with Kotlin and Jetpack Compose. Its design combines a modern Android interface with a developer-focused terminal.

> **Current status: early development (0.1.0).** The app shell, local Android command runner, private workspace browser and GitHub Actions APK build are in this repository. It is not yet a full Termux replacement: Debian/Ubuntu proot, apt, SSH, Python packages and persistent PTY sessions are roadmap items.

## Current features

- Kotlin + Jetpack Compose / Material 3 interface
- Dark hybrid layout with Terminal, Files, Environment and Settings sections
- Commands executed by Android's `/system/bin/sh`
- App-private workspace with a persistent `cd` working directory
- Built-in `help`, `pwd`, `cd` and `clear` commands
- Command timeout and output truncation safeguards
- GitHub Actions builds a debug APK and publishes it as a workflow artifact

## Important limitations

The current shell runs with the normal Android application UID. It is **not root**, does not unlock Android system partitions, and does not include a Debian or Ubuntu filesystem. The file browser currently shows only NovaTerm's private workspace. Interactive full-screen terminal apps, multi-session PTY, package installation, SSH and Linux distributions are not implemented yet.

See [the roadmap](docs/ROADMAP.md) for planned milestones.

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
│       │   └── ShellSession.kt
│       └── AndroidManifest.xml
├── .github/workflows/android.yml
├── docs/ROADMAP.md
├── CONTRIBUTING.md
├── LICENSE
└── settings.gradle.kts
```

## License

GPL-3.0-only. See [LICENSE](LICENSE) and the [GNU license text](https://www.gnu.org/licenses/gpl-3.0.txt).
