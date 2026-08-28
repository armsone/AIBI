# StarManager Android fresh-install login truth regression

- Date: 2026-08-28
- Level: source, sanitized fixture, focused JVM tests
- Scope: fresh-install login-state detection and visible-login auto-dismiss for Gemini, ChatGPT, and Claude

## Cause

Android reused broad prompt-input selectors as authentication evidence and its off-screen status probe allowed hidden DOM matches. Logged-out public pages can expose a generic composer, `contenteditable`, or send control, so Claude and the other providers could report login completion before an account existed on the new phone.

## Change

- The attached 375×667 status probe now requires visible evidence.
- Visible challenge and login controls are resolved before authentication.
- Authentication now requires a provider-specific account or user-menu marker; generic prompt inputs and send controls only describe page readiness.
- The same script is used by the visible login surface, so it no longer dismisses until the account marker appears.
- `fixtures/providers/claude-logged-out-generic-editor.json` remains the sanitized logged-out regression state.

## Evidence

- `python3 -m unittest tests.test_auth_contract` passed in the canonical AIBI project.
- StarManager Android Kotlin compiled and the focused login/parity test set passed after correcting one test-only source-string expectation.
- No passwords, cookies, tokens, prompts, or generated answers were captured.

## Remaining device verification

A clean-install phone trace is still required for each provider: logged-out status, visible login without premature dismissal, completed login, exactly-once automatic dismissal, refreshed `로그인됨` status, and one authenticated generation.

## 2026-08-29 provider callback follow-up

- Gemini's broad `a[href*='accounts.google.com']` login selector also matched an authenticated Google account-menu link. The login selector now accepts only explicit login endpoints, while `SignOutOptions`, `myaccount.google.com`, and visible Google account labels are positive account evidence.
- The Android reference visible browser now accepts only user-gesture-created popup windows, routes the first exact allowlisted script/auth URL into the visible WebView, and destroys the temporary popup. This covers provider OAuth buttons that use `window.open` without weakening the origin allowlist.
- ChatGPT adds account/profile-menu markers but still excludes generic composer and sidebar controls.
- Source and sanitized-fixture verification do not prove that Google or another provider permits embedded WebView OAuth. Physical-device completion remains required.
