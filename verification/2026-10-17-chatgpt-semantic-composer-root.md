# ChatGPT semantic composer root and closed-sidebar toggle (source only, unverified candidate)

- Date: 2026-10-17
- Level: source change only. No build, install, test, Git action, or device run. Not a verified fix.
- Evidence: `2026-10-17-chatgpt-current-structure.md` (official-page browser DOM, not iPhone DOM identity).
- Files: Stargram `ExternalAIBrowserView.swift` (mirrored byte-identically to
  `profiles/imanager/distribution/ios/ExternalAIBrowserView.swift`), fixture
  `fixtures/chatgpt-deep-composer-semantic-root.html`.

## Changes

1. `openChatGPTSidebarScript()`: added one strict closed-toggle selector,
   `button[aria-label='사이드바 표시/숨기기'][aria-controls='app-shell-sidebar'][aria-expanded='false']`.
   No English `Toggle sidebar` selector was added because that label is not observed. Existing exact open
   selectors are kept and deduplicated; the script still clicks only a single visible candidate, once
   (existing per-navigation `sidebarOpenAttempted`), and never clicks an expanded/close toggle.
2. `observerScript` (ChatGPT only, `usesSemanticComposerRoot`): new `semanticComposerRoot()` for editors with no
   enclosing form. It walks from the editor's parent up to 8 levels and returns the nearest ancestor that
   contains the current editor as the only visible editor and at least one visible semantic control (attach: existing
   attach-trigger selectors plus exact `파일 등 추가`, excluding voice/stop/remove-like labels; send: existing send
   selectors with explicit send meaning or `#composer-submit-button`). It returns null at document/body/html,
   on any history selector match, on multiple visible editors, or on more than one attach or send candidate.
   Attachment scope works without a send button when the semantic attach button is present.
3. `composerScope()` uses the semantic root first (ChatGPT, formless), else the legacy form/3rd-ancestor result.
   `attachmentScope()` for formless ChatGPT now rejects document/body/html and history-containing legacy
   fallbacks. Form-based ChatGPT, Gemini and Claude paths are unchanged. Exact-photo-count selectors are unchanged.
   Native callbacks, epochs, and logging are untouched; no content is logged.

## Verification state

- JS blocks extracted from the Swift strings pass `node --check` (syntax only). Swift itself not compiled.
- Fixture scenarios are documented but not executed.
- Cold settings state before this change: ChatGPT unknown, Claude unknown; manual sidebar opening on the
  previous candidate immediately confirmed login. This change is a new unverified candidate; root cause is not claimed fixed.
