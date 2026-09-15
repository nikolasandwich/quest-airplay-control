# Quest AirPlay and Mouse

Quest 3 Android AirPlay receiver with service-owned BLE HID mouse controls, based on [localair](https://github.com/phoria-sam-tg/localair).

## Current preview: 0.2.29-openssl3-preview

Version code 36. Adds relative ray control, shape-and-motion tracking, four
languages, settings, onboarding, adjustable gestures and hideable controls.
Context loss disarms input; resume manually. Pause before removing the headset.
Immediate physical headset-removal detection is not verified.

See [review](verification/open-source-review.md), [security](SECURITY.md),
[notices](THIRD_PARTY_NOTICES.md) and [release checklist](docs/RELEASING.md).
OpenSSL has migrated to Apache-2.0-licensed 3.5.8; see [migration evidence](docs/OPENSSL.md).
Public binary release still requires matching source delivery and device validation.

## Historical baseline: 0.2.1

- AirPlay video reception, surface lifecycle recovery, and audio decode/output path.
- Integrated BLE mouse connection, explicit enable/pause, progressive vertical scrolling, horizontal mouse drags, and clicks at the current iPad pointer.
- Serialized codec teardown and native worker shutdown before clock destruction.
- Waiting overlay clears when a real video buffer has been confirmed.

Ray-to-iPad absolute pointer positioning is not implemented in this stable version. Horizontal drags are mouse gestures; page behavior depends on the target iPad app. Audio quality and all foreground/background combinations have not been exhaustively validated.

See [0.2.1 validation](verification/release-0.2.1.md) and [0.2.0 integration validation](verification/release-0.2.0.md) for evidence and limitations. Build and lint passed; 19 Android instrumentation tests passed without generating real mouse input.

## Build

Requires JDK 17, Android SDK 35, Build Tools 36.0.0, NDK 27.2.12479018, and CMake 3.22.1. The native target is arm64-v8a. On Windows, run dependency setup with Git Bash and use `gradlew.bat` for Gradle.

```sh
./setup-deps.sh
# On Linux/WSL, with Linux NDK r27c (see docs/OPENSSL.md):
ANDROID_NDK_ROOT=/path/to/android-ndk-r27c bash ./setup-openssl.sh
cp local.properties.example local.properties # edit sdk.dir for your machine
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

`setup-deps.sh` pins RPiPlay and libplist to the tested revisions and idempotently applies [the native lifecycle patch](patches/RPiPlay-quest-lifecycle.patch). Existing dependencies at other revisions are left untouched and reported as an error. Dependency checkouts, local SDK paths, APKs, raw device logs, and Bluetooth preference backups are excluded from this repository.

The Android package is `com.questlab.airplayreceiver`, versionCode 36. Installing or restarting it interrupts the active AirPlay session; schedule device validation with the user. The earlier standalone HID app is retained for rollback and should not run its GATT service concurrently.

## Licensing

GPL-3.0-or-later. This project statically links RPiPlay, which is GPL-3.0,
so the combined work inherits GPL-3.0. See `LICENSE`.

Third-party components and their licenses:

| Component           | License           |
| ------------------- | ----------------- |
| RPiPlay             | GPL-3.0           |
| libplist            | LGPL-2.1-or-later |
| OpenSSL (libcrypto) | Apache-2.0 (OpenSSL 3.5.8; see notices) |

## Credits

Standing on the shoulders of:

- [RPiPlay](https://github.com/FD-/RPiPlay) — entire AirPlay 2 / FairPlay 2 stack
- [shairplay](https://github.com/juhovh/shairplay) — RAOP groundwork RPiPlay forked from
- [libplist](https://github.com/libimobiledevice/libplist) — Apple plist parsing
