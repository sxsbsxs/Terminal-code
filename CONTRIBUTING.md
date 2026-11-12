# Contributing to NovaTerm

Thanks for your interest in NovaTerm!

## Development setup

- Android Studio with Android SDK 35
- JDK 17
- Gradle 8.9
- Kotlin 2.0.21

## Guidelines

1. Keep command execution explicit and visible to the user.
2. Do not silently request root access or broaden Android storage permissions.
3. Treat downloaded root filesystems and binaries as untrusted until checksums/signatures are verified.
4. Keep Linux userspace management separate from the Compose UI.
5. Include build or test notes in pull requests.

## Current project status

The first milestone contains the Compose shell UI, app-local command runner, app-private workspace browser, and CI APK build. A proot Linux distribution, package manager, SSH client, and persistent PTY terminal are not implemented yet.
