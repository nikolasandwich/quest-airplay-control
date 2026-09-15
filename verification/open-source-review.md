# Open-source readiness review — 2026-09-16

Baseline: 36a1d04, app 0.2.28 / code 35, with the working-tree fixes described below.
Scope: application input/codec/service boundaries, native callbacks, dependency and
license declarations, repository/history hygiene, documentation and build checks.
This is a release-readiness review, not a proof of absence of bugs, legal clearance,
full third-party line-by-line audit, fuzzing campaign or new headset validation.

## Resolved in this working tree

- Native video/audio/connection callbacks attached receiver threads to the JVM
  without detaching. ScopedJniEnv now detaches only threads it attached; existing
  Java-owned threads are retained. Audio/video allocation failure returns before
  using a null JNI byte array.
- Production service started unauthenticated LAN diagnostics on TCP 8765.
  Startup is now restricted to debuggable builds; release input/video features
  do not depend on the diagnostic server.
- README still presented 0.2.1 as current. Current preview and historical baseline
  are now distinguished. Build command includes unit tests.
- Corrected legacy OpenSSL license description; added attribution, contributing,
  security and release documentation and native license copies. Project license
  remains GPL-3.0-or-later. No extra proprietary or noncommercial restriction.
- Current tracked documentation uses portable paths and a placeholder headset IP.
  Ignore rules cover keys, credentials, packages and private store preparation notes.

## Open gates and known limitations

1. **Binary publication gate:** OpenSSL 1.1.1q uses legacy OpenSSL/SSLeay terms.
   No exception sufficient for the combined GPL work was established. A validated
   compatible crypto migration or appropriate upstream permission is needed.
   See THIRD_PARTY_NOTICES.md. No rights over upstream code are invented.
2. **Overload robustness:** VideoDecoder bounds its downstream queue at 120 entries,
   but posts a task for each incoming NAL before that bound. A slow codec can grow
   the handler backlog. Future hardening must bound ingress bytes/tasks while
   preserving session ordering and resynchronizing at an IDR after a drop.
3. **Native protocol boundary:** inherited RPiPlay/playfair parsers were not fuzzed.
   RPiPlay itself flags uncertainty about FairPlay's legal status in its README.
   GPL licensing is not a guarantee of protocol/patent/trademark clearance.
4. **Device behavior:** immediate headset-doff protection, long-session audio and
   complex moving-background tracking are not established by unit tests. Use
   explicit pause before headset removal and label releases Preview/Alpha.
5. **History exposure:** current-file cleanup does not erase old Git revisions.
   Review publication branches and author emails before making the existing
   repository public. Preserve attribution; do not rewrite shared history silently.

## Verification

- Scanned 357 reachable Git blobs across all local refs, including stash, for
  common GitHub/AWS/OpenAI credential patterns and PEM private-key headers: no
  matching secrets. This heuristic is not an exhaustive secret detector.
- Checked history filenames for p12/jks/keystore/pem/env/local.properties: no
  matching committed key/config files in that check.
- Clean directory built from tracked source plus review changes, excluding build
  outputs and existing dependency checkouts. Dependency setup fetched the pinned
  upstream revisions and applied the lifecycle patch successfully.
- Initial clean build: 127 executed tasks; unit tests, debug APK, instrumentation
  APK assembly and lint succeeded. Shared SDK/Maven caches were used; this is not
  an offline or newly provisioned machine test.
- Post-fix results are recorded below after completion. Instrumentation tests were
  built but not run on the headset; no APK was installed and no mouse input sent.

## Publication state

No public repository visibility change, Git push, store upload or device installation
was performed by this review. Existing untracked research/store notes and stash
were preserved. Source publication can be considered after reviewing the documented
history exposure; combined binary distribution remains gated above.

Post-fix verification: debug and unsigned release APK builds, debug lint and
release lintVital passed (187 tasks; 115 executed). Unit results: {'tests': 88, 'failures': 0, 'errors': 0, 'skipped': 0}.
Native code was compiled for arm64; JNI lifecycle change still needs a real
reconnect/teardown smoke test before distributing the new binary.
