# AIBI Distribution and Host Updates

`packages/` is the portable reference engine. `profiles/<host>/distribution/` contains the host-adapted files that are safe to install as whole files. `consumers/*.json` is the explicit allowlist connecting those files to app repositories.

## Stargram (iManager compatibility profile)

Stargram Apple and Android are registered in `consumers/imanager.json`; the profile identifier remains `imanager` for compatibility.

- Apple receives the host-adapted browser, media pipeline, and diagnostic store.
- Android receives isolated runtime, media, security, cleaner, timing, authentication, and diagnostic adapters. The host-adapted `ExternalAISurface.kt` is also registered for renderer lifecycle maintenance. `ComposerScreen` and the host result sink remain app-owned and are not overwritten by the sync tool.
- DenimDex Apple and Android retain their split native host engines; their runtime, providers and host-adapted diagnostic/session files are registered separately. The DenimDex Apple ChatGPT login probe (`AIBILoginProbe.swift`) is registered as a host-adapted auth probe since 0.5.5.

## Commands

From the AIBI project root:

```sh
python3 tools/aibi_sync.py status imanager
python3 tools/aibi_sync.py apply imanager
python3 tools/aibi_sync.py check imanager
python3 tools/aibi_sync.py apply all
```

`status` is read-only. `apply` copies only allowlisted files and records their SHA-256 hashes in each app's `.aibi-lock.json`. `check` exits non-zero if a file is missing, modified locally, diverged, or has an update waiting.

The first `apply` adopts an existing file only when it already equals the distribution source; a missing allowlisted destination may be created. Later updates are installed only when the app file still equals the last recorded hash. A local app edit is never overwritten: the command stops before writing any managed file. `apply all` preflights every registered consumer for conflicts before installing any update.

## Updating the engine

1. Change portable source in `packages/` and add the required sanitized fixture or device trace for provider changes.
2. Port the change into each affected host distribution without adding host policy to `packages/`.
3. Increment `aibi-version.json`.
4. Run `python3 tools/aibi_sync.py status all`. Run test suites only when the user explicitly authorizes testing; otherwise record that tests were not run.
5. Run `apply`, then the verification command recorded for each repository in the consumer manifest.
6. Run `check`; it must report every managed file as `current`.
7. Record the verification level under `verification/` and synchronize the installed Codex AIBI skill.

The sync tool does not run builds, devices, Git commands, or releases. Those remain explicit verification and delivery steps in the affected app repository.

## Current HTT host (formerly Barobogi, 0.5.4 → 0.5.5 path)

`consumers/mailtranslator.json` now points to `../HTT-MacOS` (the renamed Barobogi-MacOS checkout; consumer ID `mailtranslator` and repository ID `mailtranslator-macos` are kept for compatibility), where mail and screen translation share the current native AIBI runner. The standalone MailTranslator checkout is historical. Refresh distribution snapshots from the current host project first; do not copy historical standalone files over this host. Existing app-specific modifications remain owned by that app.

The host's iOS app currently uses its own Google/DeepL browser adapter; Windows currently uses a local model rather than the common AI-site runtime. Do not silently install the common runtime into either path or claim provider parity.

## Provider structure verification (0.5.4)

The 2026-10-17 ChatGPT mobile control, deep composer and current image-preview changes are backed by sanitized official-page observations and static fixtures. DenimDex iOS completed a one-image task through attachment confirmation, one observed submission, stable response and host commit. Each other native host still needs its own stated verification level. Mac mail-specific runtime policies remain in its profile; portable composer changes must be merged without replacing those policies.

## Gemini input/send and Claude login propagation (0.5.5)

Source of the fixes: Stargram iPhone (2.6.8 / 202610311912, `aibi-stargram-0.5.5`). Physical iPhone evidence: Claude Settings auto-probe and login-sheet auto-dismiss (`fixtures/providers/claude-ios-collapsed-sidebar-hypothesis.json`, `deviceProbe`), Gemini text and one synthetic image with exactly one click after three stable samples, including one bounded pre-send refill on slow hydration (`fixtures/providers/gemini-ios-premature-send-stability-trace.json`, `deviceAcceptance`).

- Portable runtime: Gemini Quill input (native `insertText`, then DOM paragraphs, success only on complete normalized equality), one-shot submit gate with document complete, unique send candidate and three unchanged polls spanning at least 900 ms, and at most three successful writes per page before any dispatch. Provider generation seen before our own dispatch (possibly the visible user's send) latches for the page: no rewrite or click afterwards, also after the stop indicator disappears; callers keep their existing not-dispatched timeout/takeover and never log an automatic `send_attempted` for it. ChatGPT/Claude follow the same latch; their normal flow is unchanged.
- Stargram iPhone: the observer applies the same per-document generation latch to the refill state, the fill itself and the click; the native sheet/hidden runner already latch the task as submitted on observed generation.
- DenimDex enables ChatGPT only; iPhone 0.4.1 bundles the runtime (Gemini paths dormant). The iPhone login probe now requires a visible, non-loading `authenticatedIndicator` account marker (login/challenge first, exact closed-sidebar reveal, bounded) and ends in an explicit unknown state; it no longer uses runtime readiness.
- Release scope: 0.5.5 is embedded only in the iPhone apps Stargram 2.6.8 (202610311912) and DenimDex 0.4.1 (202610312018). Android adapters (Stargram, DenimDex) and the macOS mail host profile are not part of this release; their propagation and device verification are deferred and not claimed.

Verification level per platform is recorded in `verification/2026-10-31-iphone.md`. Physical iPhone: Stargram three providers each one synthetic image to a native result with one automatic send; DenimDex positive login probe, automatic login-sheet dismissal and one full three-photo ChatGPT appraisal pipeline (proves the app pipeline, not appraisal accuracy). Trace coverage is not physical proof on Android or macOS.

## Android Stargram maintenance update (0.5.6)

Android Stargram profile (`profiles/imanager/distribution/android/`) embeds Gemini stable-send readiness gating (Quill input normalization, poll stability latching), Claude login account detection with collapsed-sidebar probe, and managed OAuth child window (`auth_window_opened`/`auth_window_closed`, bounded `host_category`, `auth_window`, `main_frame`, `close_kind`) diagnostics storage alignment.

- Source and static packaging verified; actual physical device execution flows on Android and macOS remain unproven and are not claimed.
- Verification details recorded in `verification/2026-11-01-embedding.md`.
