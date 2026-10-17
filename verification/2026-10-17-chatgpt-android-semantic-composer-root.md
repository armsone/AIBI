# Android ChatGPT semantic composer root and closed-sidebar toggle (source only, unverified candidate)

- Date: 2026-10-17
- Level: source change only. No build, install, test, Git action, or device run. Not a verified fix; no device claim.
- Evidence: `2026-10-17-chatgpt-current-structure.md` (official-page browser DOM, not Android WebView DOM identity).
- Files: Stargram-Android `ExternalAIScripts.kt`, `ExternalAIAuthStatus.kt`, mirrored to
  `profiles/imanager/distribution/android/`. Reused fixture `fixtures/chatgpt-deep-composer-semantic-root.html`; none added.

## Changes

1. `submissionHelpers` (ChatGPT only, `useSemanticRoot`, editor without enclosing form): `semanticComposerRoot()` climbs at most 6
   ancestors from the editor's parent. Unsafe root (document/body/html/history selector) ends the climb as not found.
   More than one visible editor, or more than one attach or send candidate, returns a hard rejection: `composer=null`
   with no legacy/ancestor fallback. The nearest root with one editor and at least one visible semantic control wins;
   attach alone (empty composer, no Send) is sufficient. Attach candidates must match the provider attach selectors
   or the exact `파일 등 추가` label, carry an attach-like label and no stop/voice/model/remove/delete/close label.
2. ChatGPT `attachTrigger` gained `button[aria-label='파일 등 추가']` so the attachment open paths find the observed control.
3. `ExternalAIAuthStatus.kt`: `sidebarOpenScript()` replaces both inline clicks. ChatGPT clicks only when exactly one visible
   candidate matches (legacy `open-sidebar-button` or the exact `사이드바 표시/숨기기` toggle with
   `aria-controls=app-shell-sidebar` and `aria-expanded=false`); expanded or ambiguous toggles are not clicked.
   Gemini keeps its previous single-selector click. Positive auth markers are unchanged (`프로필 메뉴` already matched);
   no composer-based, cookie, URL or login-absence inference was added.
4. Gemini/Claude selector path, provider fallback, media order, one-shot send, count dedup and voice exclusion are untouched.

## Verification state

- The injected `submissionHelpers` JS and the sidebar JS pass `node --check` after placeholder substitution (syntax only).
- Kotlin was not compiled (no kotlinc); fixture scenarios not executed. Build and gate are left to TM.
