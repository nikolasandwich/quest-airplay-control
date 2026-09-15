# Quest device validation — 2026-09-16

Installed version 0.2.29-openssl3-preview (code 36), built from a31dbd7.
Debug APK SHA256: 705436859a5a3e01af45718cf38128d08ee0dc1c8519074d2baba7fb7531d4d6.

- Quest 3 USB connection authorized; upgraded existing code 35 with install -r (no app-data clear).
- Installed matching Android test APK; instrumentation: OK (21 tests). Covers HID state gates, pointer action state and real Android decoder lifecycle.
- Ran native crypto_migration_test directly on Quest: all 9 checks passed, runtime OpenSSL 3.5.8.
- Launched application; native receiver and foreground service started successfully.
- A real sender connected; hardware AVC decoder started, PixelCopy confirmed a surface buffer and rendered frame count advanced past 2,400.
- BLE host restored and subscribed; control initially remained unarmed. Read-only diagnostics reported foreground-loss status with active=false.
- No new fatal exception or native crash observed for this application process. Device crash buffer included an older different-process codec crash, not attributed to this run.

User feedback after installation: “还可以” (“currently feels okay”). This is a general
positive first impression, not confirmation of each individual scenario.

Pending specific human confirmation: audible sound, pointer movement/click/scroll accuracy,
manual disconnect/reconnect, and headset-removal timing. No synthetic mouse actions
were sent to the phone by this validation. Automated tests do not prove all end-to-end
behaviour. The matching test APK remains installed; temporary native test files and
USB diagnostic forwarding are removed after observation. No store release performed.
