# OpenSSL source migration

The current Android build statically links **OpenSSL 3.5.8 (LTS)** into
`libairplay_native.so`. The project remains GPL-3.0-or-later. OpenSSL 3 uses
Apache-2.0, which is compatible with GPLv3; no new exception is being invented
for upstream code. The old 1.1.1q AAR is removed, including Prefab integration.

## Pinned inputs

- Source: https://github.com/openssl/openssl/releases/download/openssl-3.5.8/openssl-3.5.8.tar.gz
- SHA256: `a8f84a39918ec6415ce765d9b429d313ba97b8143169c172e734b9514464f5b2`
- Checksum source: the official release's `.tar.gz.sha256` asset.
- Android NDK r27c / 27.2.12479018, Linux x86_64 host, Android API 28, arm64-v8a.
- Recipe: `setup-openssl.sh`; static PIC, no loadable provider modules, no apps.
- License: `LICENSES/OpenSSL-3.5.8-Apache-2.0.txt`, copied from the official source.

The script checks the archive hash before extraction. Build output and downloaded
source are ignored under `third_party/openssl`; signing credentials are not needed.
The recipe is repeatable, but byte-for-byte reproducibility has not been established
(timestamps and build paths can affect output). This is not a FIPS-validated build.

An initial experiment with a third-party 3.6.2 AAR compiled successfully, but that
package lagged current OpenSSL patches. It is not a dependency of this version.

## Build on Linux or Windows with WSL

Install `build-essential`, Perl, curl and unzip on Linux/WSL. Download and extract
the Linux NDK r27c from https://developer.android.com/ndk/downloads/older_releases.
The Windows NDK cannot be used by the Linux build script.

From the repository, after `bash setup-deps.sh`:

```sh
ANDROID_NDK_ROOT=/path/to/android-ndk-r27c bash setup-openssl.sh
```

For Windows, run this in WSL from the same checkout under `/mnt/c/...`. Then use
the normal Windows JDK/SDK/NDK and `gradlew.bat` to build the app. On Linux, use
`./gradlew` with the Linux SDK/NDK. CMake fails explicitly if the crypto library is
missing. JNI compilation also checks the exact header version; startup logs the
linked OpenSSL runtime version.

## Crypto regression checks

Install `qemu-user` on Linux/WSL, then:

```sh
ANDROID_NDK_ROOT=/path/to/android-ndk-r27c bash verification/test-openssl.sh
```

This compiles the actual pinned RPiPlay crypto wrappers and Android `libcrypto.a`
with the NDK and runs the ARM64/Bionic executable under QEMU. It checks the
OpenSSL runtime, AES CTR fragmentation/reset and CBC encryption/decryption known
vectors, SHA512, X25519 agreement and Ed25519 verification/tamper rejection.
It does not use the Linux host's OpenSSL. The static test executable needs native
TLS and an aligned TLS anchor for Bionic; application build settings are unchanged.

CMake also provides the opt-in `crypto_migration_test` target, excluded from the
APK. After the Android native build, run `cmake --build <native-build-directory>
--target crypto_migration_test` to produce the device executable. This target
requires the packaged C++ runtime on the device library path.

Real Quest AirPlay pairing, encrypted video/audio, reconnection and mouse controls
remain separate device checks. QEMU does not validate the Android service or network.

## Distribution

The APK includes the project license, copyright and native third-party license
copies under `assets/licenses`. Gradle artifact checksums are recorded in
`gradle/verification-metadata.xml`; review changes before accepting new hashes.
The OpenSSL source is verified by the separate source-build script.

For a public binary, deliver the matching complete corresponding source, this
recipe, the pinned source archive, other dependency sources and patches, and all
required notices. A private GitHub link alone is not source delivery. The migration
resolves the identified legacy crypto-license conflict, not unrelated FairPlay,
trademark, patent, store-distribution or third-party rights questions.

References:
- https://openssl-library.org/source/license/index.html
- https://openssl-library.org/source/
- https://www.apache.org/licenses/GPL-compatibility
