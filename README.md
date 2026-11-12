# NovaTerm

An open-source Android terminal workspace built with Kotlin and Jetpack Compose. NovaTerm starts with a safe app-local command shell and a modular architecture for adding a rootless Linux userspace, package management, SSH and developer tools.

> Project status: early development. The current build provides the Android app shell, command runner, file browser and GitHub Actions APK pipeline. A full Debian/Ubuntu proot userspace is planned and is not yet bundled or enabled in this first commit.

## Build

Open the project in Android Studio (JDK 17, Android SDK 35) or run `./gradlew assembleDebug`. GitHub Actions builds a debug APK on pushes and manual dispatches.

## License

GPL-3.0-only. See [LICENSE](LICENSE).
