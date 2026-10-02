# iPhone Gemini/Claude photo composer_reset failure

Date: 2026-10-03

## Sanitized device trace

- Device class: iPhone (BK_iPhone17pro, iPhone 17 Pro).
- App: `com.armsone.StarManager` 2.6.5 (202609242307), adapter `aibi-stargram-0.5.1`, iOS 27.0.1.
- Source: TM-copied sanitized diagnostics under `AIBIDiagnostics` before the fix (numeric-only; no prompt, answer, account identifier, cookie, token, or photo content).
- Claude image run (3 images): `composer_reset` repeated 8 times at ~300ms spacing, each with `composer_ready:0, root_present:0`. One `bridge_snapshot` in the same run shows `composer_present:1, input_count:1, send_present:0, send_enabled:0` — the send button was not present in the DOM while the composer held zero text and zero attachments. Run ends `attachment_failed` → `run_failed`.
- Gemini image run (3 images): identical signature — 8× `composer_reset` with `root_present:0` at the same cadence, then `attachment_failed` → `run_failed`.
- ChatGPT image run (3 images) and Gemini text-only run (no images) both complete normally in the same diagnostics set, confirming the failure is specific to the Gemini/Claude image-attachment composer-reset path, not a general regression.

## Cause

`attachmentScope()` in `ExternalAIBrowserScripts` (Stargram `ExternalAIBrowserView.swift`) only widens the composer scope for Gemini/Claude (`allowsAncestorSendSearch`) by climbing from the narrow 3-ancestor `composerScope()` until it reaches a node containing a currently visible send button. When the composer is empty (no text, no attachments yet — exactly the state `prepareFreshComposer` runs in before any photo is attached), Gemini and Claude do not render a visible send button at all, so `sendButton()` returns `null` and the function returned `null` unconditionally. `composer_reset` therefore could never report `composer_ready:1`, the 8-attempt/300ms retry loop in `handleNavigationUpdate` exhausted, and the run failed before any attachment attempt, matching the observed trace exactly.

## Change

- `attachmentScope()` now falls back to the narrow `composerScope()` (same bounding used when no ancestor widening is needed) when the send button is not yet present/visible, instead of returning `null`. The history-exclusion check (`containsHistory`) is still applied to this fallback scope. Once a send button does become visible (e.g. after an image or text is present), the existing ancestor-widening and tile-counting logic is unchanged.
- File touched: `/Users/armsone/git/Stargram/iManagerAI/Features/Composer/ExternalAIBrowserView.swift` (single function, `attachmentScope`).
- No selector list changes; no change to ChatGPT/Grok (`allowsAncestorSendSearch` false) code paths.

## Verification

- `xcodebuild -scheme iManagerAI -configuration Debug -destination "id=00008150-001E342C3C87801C" build` — **BUILD SUCCEEDED**.
- `xcrun devicectl device install app` to `BK_iPhone17pro` (data-preserving reinstall; databaseUUID unchanged container).
- `xcrun devicectl device process launch` — app relaunched, PID confirmed live in `devicectl device info processes`.
- Live Gemini/Claude photo-attach retest on-device is pending TM's independent UI verification through iPhone Mirroring (not performed via shell/automation per task constraints).
- No prompt, answer, account identifier, cookie, token, or source photo was captured for this document.
