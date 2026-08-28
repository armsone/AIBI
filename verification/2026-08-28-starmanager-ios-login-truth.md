# StarManager iOS login truth regression

- Date: 2026-08-28
- Level: source, sanitized fixture, regression test, iOS Simulator build
- Scope: fresh-install login-state detection for Gemini, ChatGPT, and Claude

## Cause

The iOS status probe reused prompt-filling input selectors as authentication evidence. Claude and Gemini include broad `contenteditable` fallbacks for resilient prompt entry, so a generic editor on a logged-out public page could be mistaken for an authenticated composer.

## Change

- Authentication now uses only the provider-specific authenticated marker list.
- Generic prompt-input fallbacks are excluded from session truth.
- Positive login confirmation waits for a shared `WKWebsiteDataStore.default()` cookie-store read barrier before notifying the host and dismissing exactly once.
- `fixtures/providers/claude-logged-out-generic-editor.json` records the sanitized logged-out regression state.

## Evidence

- `python3 -m unittest tests.test_auth_contract` passed.
- StarManager built successfully for the iPhone 17 Pro iOS 26.5 simulator with code signing disabled.
- The installed Codex AIBI skill matches `skill-source.md` byte-for-byte.

## Remaining device verification

A clean-install device trace for all three providers is still required before claiming full AIBI parity: logged-out status, visible login, positive authenticated evidence, automatic dismissal, refreshed status, and one authenticated generation per provider.
