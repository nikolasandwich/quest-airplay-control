# Third-party notices

Source remains GPL-3.0-or-later, matching https://github.com/phoria-sam-tg/localair.
Original copyright and file-level notices remain authoritative.

| Component | Source/version | License evidence |
|---|---|---|
| RPiPlay | 64d0341ed3bef098c940c9ed0675948870a271f9 plus tracked lifecycle patch | GPL-3.0 repository; some files LGPL-2.1-or-later |
| playfair | bundled with pinned RPiPlay | bundled GPL license |
| llhttp | bundled with pinned RPiPlay | MIT |
| libplist library | 2117b8fdb6b4096455bd2041a63e59a028120136 | LGPL-2.1-or-later source headers and COPYING.LESSER |
| OpenSSL | Official OpenSSL 3.5.8 source, pinned in setup-openssl.sh | Apache-2.0; official source LICENSE.txt |
| AndroidX, Kotlin, coroutines | Gradle declarations | retain published Apache-2.0 notices in binary release inventory |
| C++ runtime | NDK 27.2.12479018 | retain NDK runtime notices in binary release inventory |

Full native license copies are in LICENSES. Third-party sources retain their own
notices; the project LICENSE does not replace them.

## OpenSSL migration

The current build uses OpenSSL 3.5.8 under Apache-2.0, compatible with GPLv3.
The previous 1.1.1q dependency used OpenSSL AND original SSLeay terms and had an
unresolved GPL compatibility gate. It is no longer a current build dependency;
its license copy remains for historical versions, not as an exception or relicensing.

The library is now compiled from official source; no third-party OpenSSL AAR is used.
See docs/OPENSSL.md for provenance, checksums, testing and remaining release work.

- https://openssl-library.org/source/license/index.html
- https://www.apache.org/licenses/GPL-compatibility

Public binaries require matching complete corresponding source, pinned dependency
source, patches, build instructions and notices. A private repository link is not
public source delivery. No Apple or Meta endorsement or trademark rights are implied.
