# AIBI 0.5.6 Embedding Verification Record

- **Date**: 2026-11-01
- **Target**: Android Stargram Distribution Adapter (`profiles/imanager/distribution/android/`)
- **Scope**: Source packaging and diagnostics store alignment for Android Gemini stable-send, Claude login detection, and OAuth child window maintenance.

## Source & Diagnostics Alignment Evidence

1. **Diagnostics Stages and Bounds**:
   - `ExternalAIDiagnosticsStore.kt` updated to accept stages: `navigation_blocked`, `navigation_finished`, `auth_window_opened`, `auth_window_closed`.
   - Numeric bounds enforced:
     - `host_category`: `0..6`
     - `auth_window`: `0..1`
     - `main_frame`: `0..1`
     - `close_kind`: `1..5`
   - Descriptive fixed mappings provided in `descriptions()` for `host_category`, `auth_window`, `main_frame`, and `close_kind`, matching definitions in `ExternalAISurface.kt`.
   - Strict adherence to privacy guarantees: zero raw errors, URLs, prompts, answers, cookies, or credentials recorded.

2. **Pre-existing iPhone Traces Cited**:
   - `fixtures/providers/claude-ios-collapsed-sidebar-hypothesis.json` (`deviceProbe` trace)
   - `fixtures/providers/gemini-ios-premature-send-stability-trace.json` (`deviceAcceptance` trace)
   - Verification record `verification/2026-10-31-iphone.md`

3. **Physical Verification Limits**:
   - Packaging and source-level review verified purely via static inspection of edited files.
   - **Explicit Limit**: Actual on-device execution flows, UI interactions, and runtime behavior on Android physical devices and macOS hosts have not been executed or proven in this step.
   - Neither tests, compilation builds, APK generation, nor device installations were executed or claimed as succeeded. TM executes subsequent build and test gates.

## TM final integration — 2026-11-01 22:13 KST

- Existing unfinished Android source changes preserved and embedded: bounded Claude/ChatGPT sidebar checks, Gemini stable-send gate, visible user-gesture OAuth child preserving opener, allowlisted top-frame navigation and child teardown, cancellation-safe fill status. No new authentication origins. TM inspected origin/gesture checks, child lifecycle and fixed numeric diagnostic bounds; no additional blocker found. This is source inspection, not provider OAuth acceptance evidence. Existing iPhone selector fixtures above and `verification/2026-10-03-mailtranslator-input.md` document the source recipe; Android popup behavior remains physically unverified.
- Fixed a concrete omission: new navigation/auth-child events had been silently dropped by the diagnostic store. Gemini account2 implemented fixed-stage/numeric-bound alignment; TM integrated its disjoint temporary workspace output. A broader Gemini source-inspection run produced no accepted outcome and was stopped; it is not counted as verification.
- `python3 -m unittest tests.test_auth_contract tests.test_media_contract.MediaContractTests.test_imanager_ios_gemini_first_send_waits_for_stable_ready_composer tests.test_media_contract.MediaContractTests.test_imanager_ios_gemini_hydration_replaced_prompt_refills_before_any_send tests.test_sync`: 17 passed. Full suite not run; previously recorded unrelated runtime Node-harness/static-assertion failures are not claimed fixed.
- `aibi_sync.py apply all`, then `check all`: every managed file current for Stargram Apple/Android, DenimDex Apple/Android, HTT Mac. Locks record package 0.5.6; unchanged Apple and DenimDex runtime binaries are not represented as newly built apps.
- HTT latest engine/settings were imported FROM the current clean tracked host into its AIBI profile before lock adoption. Installed HTT 1.1.1, host and profile runtime all SHA-256 `f9a3766c9c920a198177b5d99478d4b621c1754a7bca1e591a5f2c22bb6e2260`. No host engine/settings overwritten or Mac rebuild needed.
- Pulled the installed TriFold DenimDex 0.3.8 APK; bundled runtime SHA-256 `0a155d75e4912bed1066e44a8435d40e95cf2c29a28e782806a33f20817584f7`, identical to canonical. Its prior build/install evidence is reused. iPhone Stargram/DenimDex managed source also current; prior successful physical flows are retained, not re-run.
- Stargram Android 2.6.11 / versionCode 439110 / BUILD_NUMBER 202611012210: `./gradlew assembleRelease` BUILD SUCCESSFUL in 35 seconds. Existing databaseEnabled deprecation warnings plus an unnecessary safe-call warning; no build errors. Release APK `Stargram-Android/app/build/outputs/apk/release/app-release.apk`, SHA-256 `f97415c057ea28756cd6ae56be01ad8c477941555510f0689a1132b3061e7eb0`, v2 signature verified, existing certificate digest `837bd274f558659a3aec9bd31308b8ac01916c386230f946f5f7347d7f6f9b0f`.
- Terminated old Stargram app, `adb install -r` Success on both registered phones. TriFold: version 2.6.11/439110, original first install 2026-08-21 retained, launcher Status ok, PID 9622. Fold6: same version/code, first install 2026-08-28 retained; its existing ClassicIconAlias resolved and launched Status ok/COLD, PID 22757 (TriFold uses BkIconAlias). No uninstall, data reset, signature change or user icon-setting change.
- Installed Codex AIBI skill's maintenance-history and distribution references synchronized. Existing SKILL.md layout/customization preserved; runtime unchanged and already canonical. No synthetic prompts/media/conversations created in this embedding pass; existing provider test conversations were not touched.
- Completion scope: source embedding, affected final Android build, both phone update installs/relaunch and Git backup. Actual new Android/macOS provider response flows and deliberate manual-send race are unverified. No TestFlight/GitHub Release/site publication in this task.
