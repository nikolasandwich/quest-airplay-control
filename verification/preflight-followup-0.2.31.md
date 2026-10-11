# Preflight follow-up — 0.2.31 / code 39

2026-10-11. This is a partial remediation record, not release approval.

## Completed

- Published the existing code 38 source on main at commit
  `1ae2b7c` before changing the candidate. This preserves its application source
  independently of code 39. Complete dependency-source delivery remains a release item.
- Reused the approved store icon, unchanged, in the Android adaptive launcher icon
  and TV banner. The adaptive foreground is inset to protect the design from masks.
  No generated edits to the original artwork were made.
- Removed unused POST_NOTIFICATIONS and RECEIVE_BOOT_COMPLETED declarations.
  The receiver still posts its required foreground-service notification.
- Restricted the pointer diagnostic capture entry and its implementation to
  debuggable installations. Release builds no longer offer that long-press capture.
- Bluetooth firmware revision now follows the installed package version instead
  of a stale alignment prototype string. This does not resolve the PnP vendor ID.
- Public support/privacy site version 3 deployed successfully. It distinguishes
  debug-only LAN status access and describes older builds' local image/CSV captures,
  retention, private-content implications and deletion. It also documents that the
  capture entry is debug-only starting with 0.2.31.

## Evidence

- `testDebugUnitTest`, `assembleDebug`, `assembleRelease` (including release vital
  lint): BUILD SUCCESSFUL. 92 tests, 0 failures, 0 errors.
- Debug APK installed in place on Quest 3; package manager confirms code 39,
  version 0.2.31. App launched. Startup logs confirm receiver registration,
  bonded reconnect, report subscription restored and control remaining disarmed.
- Store candidate signed with the existing release key; APK signature v3 verifies.
  Release metadata contains neither the removed permissions nor the debug flag.
- The approved icon is packaged; actual headset launcher appearance/cache refresh
  has not been visually verified. Mirroring, audio and pointer response after this
  particular update have not been verified by the user.

## Still open

- Exact store-signed APK installation and functional regression. Device retains a
  differently signed debug app. Do not uninstall/clear data without an agreed plan.
- Legitimate Bluetooth PnP identity. Do not borrow another vendor's identifier.
  The Bluetooth Device Information Service specification describes obtaining a
  USB-IF Vendor ID or Bluetooth SIG Company Identifier; whether existing platform
  qualification applies to this software still needs confirmation.
  https://www.bluetooth.com/wp-content/uploads/Files/Specification/HTML/DIS_v1.2/out/en/index-en.html
- Decoder ingress backlog risk from the earlier audit is not fixed in this version.
- Complete corresponding dependency source and runtime notice inventory still need
  packaging/verification before distributing a public binary.
- Refresh main/settings screenshots to show the Beta notice, and update the Meta
  submission to the final accepted candidate. Code 39 has NOT been uploaded or
  substituted into the submission. No store review was submitted.

Public privacy page: https://quest-mirror-mouse.nikolasandwich.chatgpt.site/#privacy
