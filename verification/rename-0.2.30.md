# Mirror and Mouse 0.2.30 — 2026-10-11

- Version code 37; Android application ID and signing identities unchanged.
- All four application locales and fallback text use the new product name.
- Default AirPlay name and the saved legacy default migrate to Mirror and Mouse; custom receiver names remain intact.
- HID model name and Android's shared Bluetooth adapter name use Mirror and Mouse. Name changes do not remove pairing records or automatically arm input. Advertising waits up to three seconds for the asynchronous adapter-name change.
- Includes the preceding pairing-recovery change: explicit Connect/Enable requests bonding for an unbonded connection, clears stale subscriptions and times out after 30 seconds; completion never auto-arms input.
- 92 unit/Robolectric tests passed, zero failures/errors. Debug and release builds, including release vital lint, passed.
- Debug update installed in place on Quest 3 and launched. Device logs confirm the new AirPlay receiver name, accepted Bluetooth rename, bonded reconnect with restored subscription and input remaining disarmed. Android Bluetooth state independently reports the new name.
- Store-signed release package verifies using the existing release certificate. The headset uses the same-source debug build to preserve its existing installation and data; the release-signed binary has not been installed over it.
- Actual phone-side name-cache refresh, fresh pairing after rename and pointer movement after this update still require user verification. Earlier pairing fix had user-confirmed mouse response.
- Existing screenshots are historical captures and have not been repainted to change labels. Refresh any old receiver-name instructions before final store review.

## Production draft retry — code 38

- Existing code 37 was visible as a 2D Native production draft with age certification complete, and both malware and security-vulnerability checks passed. Nevertheless, the store submission build picker disabled codes 37 and 36; ending metadata preview did not unlock it, and the application requirements page reported no outstanding requirements.
- The direct production upload validator rejected reusing code 37, so the candidate uses code 38 while retaining version name 0.2.30, application ID, signing identity and control behavior.
- User requested an obvious Beta notice during this retry. Added a localized Beta preview prefix to the main status bar (including startup) and a highlighted preview notice at the top of Settings. All four languages and English fallback text are included. Hiding controls still hides the status bar.
- Store short and full descriptions now begin with BETA PREVIEW and disclose ongoing compatibility/pointer improvements and experimental alignment.
- Release build and vital lint passed; all 92 unit/Robolectric tests passed with zero failures or errors. Store-signed code 38 verifies with the existing certificate. Not installed on the headset in this retry.
- Uploading directly as a production draft is a recovery attempt; it is not evidence that the build association issue or store review has passed.
- Code 38 uploaded successfully directly to the production channel as a draft (build 1280234945183407), with the existing mixed-audience age range confirmed. At the end of this check both automated malware and security-vulnerability tests remained in progress, and the submission binary was still N/A. The picker had not yet unlocked. No review was submitted and no public release occurred. Beta channel/headset remain on the preceding build.
