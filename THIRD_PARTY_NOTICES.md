# Third-party runtime notices

NovaTerm downloads selected runtime components on demand. These files are not bundled in the APK.

## PRoot

The optional Android PRoot executable is downloaded from the release page of [ahmed-alnassif/proot](https://github.com/ahmed-alnassif/proot/releases). That project states that its PRoot binary is built from the [Termux PRoot fork](https://github.com/termux/proot).

The Termux PRoot fork is distributed under **GNU General Public License, version 2**. Its license and copyright notices are available in the upstream [COPYING file](https://github.com/termux/proot/blob/master/COPYING). The exact binary release and SHA-256 pin used by NovaTerm are recorded in `LinuxEnvironmentManager.kt`.

PRoot is a separate executable process. It provides userspace path and identity emulation; it does not grant Android system/root privileges.

## Debian Bookworm root filesystem

The optional Debian ARM64 root filesystem is downloaded from the immutable [debuerreotype/docker-debian-artifacts](https://github.com/debuerreotype/docker-debian-artifacts) commit pinned in `LinuxEnvironmentManager.kt`. Debian packages in the root filesystem retain their own copyright and license files under `/usr/share/doc` and `/usr/share/common-licenses`.

## Checksums

NovaTerm verifies the SHA-256 of both downloaded archives before installing or extracting them. If verification fails, installation is aborted.
