# OpenSSL migration validation — 2026-09-16

Version: 0.2.29-openssl3-preview, code 36; branch feature/ray-pointer.

## Executed

- Built official OpenSSL 3.5.8 source for Android arm64-v8a/API 28 with Linux NDK r27c; source SHA256 checked by setup-openssl.sh.
- Ran verification/test-openssl.sh against that Android static library under QEMU: all 9 checks passed (version, AES CTR fragments, CTR reset/decrypt, CBC encrypt, CBC decrypt, SHA512, X25519, Ed25519 valid and tampered signatures).
- Forced a full Gradle task rerun for debug APK, unsigned release APK, Android test APK, unit tests and lint: 214 tasks executed, success.
- Rebuilt after adding runtime version guard and packaged notices: 215 tasks, 46 executed, success under dependency verification.
- 88 application unit tests, zero failures/errors. Lint: zero errors, 30 warnings (existing baseline categories).
- Built the opt-in CMake crypto_migration_test Android executable successfully.
- Verified both APKs contain the OpenSSL 3.5.8 runtime string and the exact license copy. Neither contains a standalone libcrypto.so or libssl.so; crypto is linked into libairplay_native.so.

## Artifacts (local, not published)

- `app-debug.apk` SHA256: `705436859a5a3e01af45718cf38128d08ee0dc1c8519074d2baba7fb7531d4d6`
- `app-release-unsigned.apk` SHA256: `a5e311e5be01037e38bda050cbcdf274b99782bf647c668a1666349c50db158a`

## Limits

No ADB device was connected. No install, Quest instrumentation run, real AirPlay
pairing/video/audio, reconnection or pointer-control session was performed in this
migration. QEMU crypto checks are not a substitute for those device checks. The
release APK is unsigned. The existing installed preview remains unchanged.

The old crypto-license conflict is addressed by using Apache-2.0 OpenSSL with
GPLv3 project code. This does not complete public binary source delivery or settle
other protocol/IP rights. See ../docs/OPENSSL.md and ../docs/RELEASING.md.

Gradle checksums pin the artifacts resolved during review; they do not independently
prove the provenance of every transitive dependency. Review new hashes on updates.
