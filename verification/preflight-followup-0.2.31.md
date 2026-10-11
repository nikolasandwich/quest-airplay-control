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
- Refresh main/settings screenshots to show the Beta notice.

## Store submission update — 2026-10-11

- At the user's explicit instruction to submit, uploaded the signed code 39 APK
  to Production (Store), with Quest 3 family and Future devices supported.
- Confirmed mixed age groups, saved the no-ads declaration and Bluetooth
  permission explanations, and associated version 0.2.31 with the initial listing.
- Meta's malware and security vulnerability checks both passed.
- Submitted for review. The dashboard confirms metadata v1 and binary v0.2.31
  are Submitted, with submission date 2026-10-11 and no review date yet.
- This is not approval or public launch. The dashboard indicates launch can be
  configured after approval. Pricing remains free and Beta disclosures remain.
- Reviewer notes explicitly disclose that Quest 3 testing used the same-source
  debug-signed app, not a separate regression of the exact store-signed APK.
  The other open items above are not resolved by submitting for review.

Public privacy page: https://quest-mirror-mouse.nikolasandwich.chatgpt.site/#privacy
