# NovaTerm roadmap

## Milestone 0.1 — Android foundation
- [x] Kotlin + Jetpack Compose application shell
- [x] Dark hybrid interface
- [x] App-local command execution via Android system shell
- [x] Persistent working-directory handling for Android shell
- [x] Private workspace file browser
- [x] GitHub Actions debug APK artifact

## Milestone 0.2 — Rootless Debian foundation
- [x] Pinned Android ARM64 PRoot runtime download
- [x] SHA-256 verification before runtime extraction
- [x] Pinned Debian Bookworm ARM64 rootfs download
- [x] SHA-256 verification before rootfs extraction
- [x] First-run installer and install progress display
- [x] App-private Debian root filesystem
- [x] Basic Debian command execution through PRoot
- [ ] Device-level smoke tests across Android 8–15 and OEM variants
- [ ] Resumable downloads, repair and remove/reset controls
- [ ] ARMv7 and x86_64 rootfs support

## Milestone 0.3 — Terminal foundations
- [ ] Native PTY session with interactive full-screen applications
- [ ] Persistent shell process and real terminal input/output
- [ ] Multiple terminal sessions and tabs
- [ ] Scrollback, selection, copy/paste and keyboard shortcuts
- [ ] User-configurable font size and color themes

## Milestone 0.4 — Developer tools
- [ ] Package-manager UI over Debian apt
- [ ] Python and pip setup assistant
- [ ] Git and SSH setup assistant
- [ ] Compiler/toolchain presets
- [ ] Export/import of environment backups

## Milestone 0.5 — Multi-distro and integration
- [ ] Ubuntu image
- [ ] User-managed distro profiles
- [ ] Android shared storage through Storage Access Framework
- [ ] Background environment lifecycle and process controls
- [ ] Resource usage monitor and safe cleanup

Checked items have code in the repository. Device-specific runtime behavior still needs real-phone testing.
