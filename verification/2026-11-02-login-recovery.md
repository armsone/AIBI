# AIBI 0.5.7 Login Recovery Verification Record

- **Date**: 2026-11-02
- **Target**: Android Stargram Distribution Adapter (`profiles/imanager/distribution/android/`)
- **Scope**: ChatGPT login-loop maintenance, async generation callback guards, privacy-safe login diagnostics, and scoped cookie expiration helper.

## Known Prior Runtime Repair vs. New Code Scope

1. **Known Prior Runtime Repair**:
   - Context: On an actual device session, a login loop was observed where ChatGPT root (`https://chatgpt.com/`) navigated to login (`auth.openai.com`), user completed Google OAuth / MFA / SMS, but was redirected back to login instead of guest home or authenticated user session.
   - Known Remediation: Explicit manual expiration of cookies strictly across the exact three ChatGPT origins (`chatgpt.com`, `auth.openai.com`, `auth0.openai.com`) restored the guest home, after which user successfully logged in.
   - Specific offending cookie is unknown; root cause is explicitly not claimed as established.

2. **Newly Authored Code (Implementation Phase)**:
   - `profiles/imanager/distribution/android/ExternalAILoginRecovery.kt`:
     - `ExternalAILoginRecoveryTracker`: Pure stateful tracker detecting repeated returns to auth (requires at least two failed returns to auth origin). Ordinary first login, challenges, guest-with-login-button, and UNKNOWN states never trigger loop detection or automatic actions.
     - `ExternalAILoginRecovery.clearChatGPTCookiesScoped`: Serialized, bounded helper expiring cookies only across the 3 exact ChatGPT-owned origins (host-only, host domain, `.openai.com`). Never executes `removeAllCookies()`, `WebStorage.deleteAllData()`, or clears shared Google or other provider sessions (Gemini/Claude).
   - `profiles/imanager/distribution/android/ExternalAISurface.kt`:
     - Navigation generation advanced on `onPageStarted` (and finish) so stale async page callbacks cannot mark success or mutate state.
     - Privacy-safe login diagnostics enabled (`diagnostics.start(provider.name)` for LOGIN mode; events `login_started`, `positive_auth_observed`, `auth_loop_detected`, `auth_recovery_*`).
     - Error-only user affordance with native confirmation dialog for "ChatGPT 다시 연결".
     - Stops active OAuth child window before reset and performs a single user-initiated root reload with `LOAD_NO_CACHE`.
   - `profiles/imanager/distribution/android/ExternalAIDiagnosticsStore.kt`:
     - Stages allowlist extended: `login_started`, `positive_auth_observed`, `auth_loop_detected`, `auth_recovery_started`, `auth_recovery_completed`, `auth_recovery_failed`.
     - Numeric limits extended: `auth_roundtrips` (0..50), `recovery_attempt` (0..10), `cookie_callback_count` (0..1000), `recovery_result` (0..2).
     - Descriptions and schema validation updated: `recovery_result` accurately records 0 (failed/timed out), 1 (completed without observed residual or rejection), and 2 (partial completion or residual detected). `cookie_cleared_count` alias removed.
   - `consumers/imanager.json`: Manifest mapping added for `ExternalAILoginRecovery.kt`.
   - `fixtures/providers/chatgpt-login-loop.json`: Sanitized fixture with failure pattern, remediation contract, and synthetic trace.
   - `tests/test_auth_recovery.py`: Focused unit tests verifying fixture sanitization, helper invariants, tracker state transitions, generation guards, and diagnostics store allowlists.

## Verification Limits & Status

- **Status**: TM review and test batch pending (`차단` / `완료` final evaluation belongs to TM review).
- **Explicit Verification Limit**:
  - No shell commands, compilation runs (`./gradlew assembleRelease`), unit test executions (`python3 -m unittest`), or device installations were executed in this implementation phase.
  - The new automated helper code, UI flows, and diagnostic store updates have NOT been executed on a physical device.
  - Verification in this phase is strictly confined to static code inspection and file integrity checks.
  - Apple implementation is not yet updated.

## TM final checks (2026-11-02 KST)

- Authentication contract checks: 15 tests passed across the initial batch and the affected assertion rerun. Six existing conflict-safe synchronization tests passed.
- Actual Kotlin helper and `tests/AuthRecoveryTrackerCheck.kt` compiled and ran on the JVM with a minimal provider enum stub, existing Android SDK classes and cached coroutine libraries. Checks cover exact-origin spoof resistance, Google and child-window returns, duplicate navigation events, successful first login, non-ChatGPT isolation, cookie-name validation and count bounds. This executes the actual tracker/parser; it does not execute real CookieManager deletion.
- Independent TM authentication review found and corrected callback overlap, incomplete-result reporting and navigation classification defects before delivery. The final Android release build passed; packaged app identity/version and existing signing certificate were checked.
- Stargram Android 2.6.13, code 439228, display build 202611020028 was installed as a data-preserving replacement on TriFold and Fold6. At this checkpoint both devices are locked, so foreground/login preservation confirmation is pending. No live authentication cookies were cleared during this maintenance.
- AIBI changes apply to the Stargram Android host adapter and portable behavior contract only. Apple adapters, generic engine and other apps are not claimed updated. Installed skill references were synchronized.

## Publication checkpoint

- AIBI 0.5.7: https://github.com/armsone/AIBI/releases/tag/v0.5.7
- Stargram Android 2.6.13: https://github.com/armsone/Stargram-Android/releases/tag/android-v2.6.13
- Both public assets returned HTTP 200; GitHub asset SHA-256 values matched local release metadata. Hanstree public release metadata returned HTTP 200 with the same versions, filenames, hashes and Android code/build. Website source was not changed for this version update.
- Source commits pushed: AIBI bbb2c23cb85cc51c5f3ff181958d384ed12436cc; Android c97861a62050586d09ace76f9c4621d183b8b08e.
- Both installed app processes start, but devices remain locked. Post-install foreground/auth-state confirmation and requested chat archival remain pending; no recovery cookie deletion was performed on the current valid sessions.

## Final unlocked-device confirmation (2026-11-02 07:12 KST)

- User unlocked both devices. TriFold and Fold6 each report installed Stargram Android 2.6.13 / code 439228.
- Actual foreground settings on both devices completed their positive account probes and showed ChatGPT, Gemini and Claude as logged in. Existing sessions were preserved after replacement installation.
- On each device the ChatGPT login-status control opened the official ChatGPT WebView. After positive authentication was recognized, the browser automatically dismissed back to settings, where all three providers remained logged in. No prompt was sent and no session reset was performed.
- This resolves the previous locked-device verification checkpoint. Existing synthetic tests cover recovery detection and parsing; destructive recovery was deliberately not rerun on the now-valid sessions. No required follow-up remains for this maintenance/release; chat archival is authorized by the user.
