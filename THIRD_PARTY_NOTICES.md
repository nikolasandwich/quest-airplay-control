# Third-party notices

Source remains GPL-3.0-or-later, matching https://github.com/phoria-sam-tg/localair.
Original copyright and file-level notices remain authoritative.

| Component | Source/version | License evidence |
|---|---|---|
| RPiPlay | 64d0341ed3bef098c940c9ed0675948870a271f9 plus tracked lifecycle patch | GPL-3.0 repository; some files LGPL-2.1-or-later |
| playfair | bundled with pinned RPiPlay | bundled GPL license |
| llhttp | bundled with pinned RPiPlay | MIT |
| libplist library | 2117b8fdb6b4096455bd2041a63e59a028120136 | LGPL-2.1-or-later source headers and COPYING.LESSER |
| OpenSSL | com.android.ndk.thirdparty:openssl:1.1.1q-beta-1 | OpenSSL License AND original SSLeay; not Apache-2.0 |
| AndroidX, Kotlin, coroutines | Gradle declarations | retain published Apache-2.0 notices in binary release inventory |
| C++ runtime | NDK 27.2.12479018 | retain NDK runtime notices in binary release inventory |

Full native license copies are in LICENSES. Third-party sources retain their own
notices; the project LICENSE does not replace them.

## Binary release gate: legacy OpenSSL

Upstream's OpenSSL/Apache dual-license wording is incorrect for 1.1.1q. OpenSSL
uses Apache-2.0 from 3.0 onwards; earlier releases use OpenSSL and SSLeay terms.
GNU identifies the legacy license as GPL-incompatible absent suitable permission.
This review has not established an exception covering all relevant upstream code.

Do not claim a compatible combined APK until a compatible crypto migration has
been built and validated, or sufficient permission is obtained. Adding an exception
to our own code cannot grant rights over upstream code. This is an unresolved
distribution gate, not an assertion that upstream granted an exception.

- https://openssl-library.org/source/license/index.html
- https://www.gnu.org/licenses/license-list.html#OpenSSL

Public binaries require matching complete corresponding source, pinned dependency
source, patches, build instructions and notices. A private repository link is not
public source delivery. No Apple or Meta endorsement or trademark rights are implied.
