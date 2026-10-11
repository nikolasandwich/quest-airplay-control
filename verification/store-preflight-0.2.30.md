# Store preflight — 0.2.30 / code 38

Reviewed on 2026-10-11. Scope: current source changes, APK metadata/signature and
license inventory, existing test reports, live store configuration, public support
and privacy page, and selected screenshot exports. This is not a full dependency
audit, legal clearance or comprehensive device regression. No review submitted.

## Confirmed

- Meta malware and security-vulnerability checks passed for code 38.
- Build association was blocked by missing forward-compatible device targeting.
  Adding Future devices alongside Quest 3 family unlocked code 38. Saved the build
  association, no-ads declaration, and Bluetooth/notification permission explanations.
  Submission overview now shows the binary and an enabled Submit for review button.
- Store descriptions and application UI disclose Beta/experimental alignment.
  Early Access is enabled. Price remains free.
- Store APK signature verifies (v3), min API 28, target API 34, arm64; no debuggable
  flag in its badging. Project/native license files are present in assets/licenses.
  No p12/jks/keystore/pem/env-named files found in the APK (filename check only).
- Existing unit reports contain 92 tests, zero failures/errors. No full rerun during
  this read-only review; latest debug assembly succeeded.
- Installed same-source DEBUG code 38 over DEBUG code 37 with data preserved.
  App starts; foreground receiver service is active. Logs show bonded reconnect,
  restored report subscription and input disarmed. No AndroidRuntime fatal output
  for the observed new process. User pointer/video response is not verified here.
  This is not installation or acceptance testing of the store-signed APK.

## Findings before public distribution

1. **Matching source delivery is incomplete.** Rename, pairing recovery and Beta
   changes are uncommitted in the working tree. An older public repository does
   not provide the exact source for code 38. Archive/publish the matching source,
   pinned dependencies, build recipe and notices; verify downstream runtime notices
   as well as native ones. README still says code 37.
2. **Privacy documentation does not match diagnostics.** The live website describes
   an unauthenticated local status page without saying it starts only in debug
   builds. More importantly, MainActivity's long press on Relative ray invokes
   PointerObservation in release too: up to 30 frame PNGs plus pointer/timing CSVs
   per run, at most four runs, saved in app cache. The policy does not disclose this
   user-triggered local capture. Clarify trigger, stored content and cache deletion;
   consider an explicit confirmation or disabling this diagnostic entry for release.
3. **Release-binary validation remains open.** Headset tests use debug signing and
   debug-specific services; do not count them as store APK validation. Validate the
   actual signed package on a clean test installation/device, including fresh pairing,
   reconnect, mirroring/audio and background/resume. Replacing the existing debug
   install with release signing would require a deliberate data-migration/uninstall plan.
4. **Bluetooth identity remains prototype-grade.** HidController's PnP ID advertises
   SIG vendor 0xffff and a test product; its own comment says not a shipping vendor ID.
   Firmware revision is still 0.2.8-align-preview. Confirm a legitimate identity and
   applicable Bluetooth requirements before treating this as a supported public product;
   do not substitute another company's identifier. Exact qualification/legal scope
   has not been established by this review.
5. **Known overload risk persists.** VideoDecoder.onNalUnit posts one closure with
   the input byte array before limiting the downstream pending queue to 120 entries.
   Slow decode can therefore accumulate an unbounded handler backlog. This is a
   source-level risk, not a newly reproduced crash; bound ingress bytes/tasks and
   validate recovery under load before claiming robust long-session behavior.

## Further review/quality items

- POST_NOTIFICATIONS is declared but not requested at runtime; RECEIVE_BOOT_COMPLETED
  has no declared boot receiver. Review necessity and remove unused permissions in
  a separately tested package. A foreground service itself does not require granting
  notification permission; the current store explanation should not imply otherwise.
- Store screenshots precede the Beta UI update. They are genuine captures but do
  not show the new Beta labels. Refresh the main/settings captures for exact consistency.
- Mountain screenshot has account/avatar/message-name redaction, but retains the
  third-party app's chrome and media context. User says the content belongs to a
  friend; authorization for store promotion has not been independently established.
- Headset-removal timing, extended audio/video, moving-background pointer alignment,
  Quest 3S and future hardware are not comprehensively tested. Future-device targeting
  satisfies a submission rule; it is not evidence of physical device testing.
- OpenSSL migration resolves the previously identified legacy crypto-license issue;
  it does not establish protocol/patent/trademark clearance for inherited AirPlay/
  FairPlay components. Do not claim Apple or Meta certification.

## Public references checked

- Android notification permission: https://developer.android.com/develop/ui/compose/notifications/notification-permission
- Meta build/ad declaration flow: https://developers.meta.com/vr/documentation/android-apps/ads-attestation/
- Bluetooth assigned-number authority: https://www.bluetooth.com/specifications/assigned-numbers/
- Live privacy page: https://quest-mirror-mouse.nikolasandwich.chatgpt.site/#privacy

Recommendation: retain the draft for now; close source-delivery and privacy gaps,
resolve the prototype Bluetooth identity, and validate the actual release package
before public distribution. A clickable submission button is not release approval.
