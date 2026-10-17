# Sanitized official-page structure observation
Observed 2026-10-17 09:50–09:51 KST through the official ChatGPT homepage in Codex IAB. This is browser evidence, not proof of iPhone DOM identity. No conversation contents, account data, cookies or full answers retained.

- Unique native BUTTON accessibility label: 사이드바 표시/숨기기.
- It has aria-controls=app-shell-sidebar and aria-expanded=true while open; no data-testid.
- Positive account control label includes 프로필 메뉴 열기.
- Current editor is DIV role=textbox, empty id, with contenteditable representation.
- First through fourth editor ancestors contain no buttons/file input; fifth ancestor contains one editor and the semantic button 파일 등 추가, plus model/voice controls.
- Editor has no enclosing form in this observed homepage.
- Current common runtime returns safe third ancestor without requiring semantic composer controls. Stargram Apple host uses fixed third ancestor. Both can select a root too narrow to contain attachment previews.
- Before any media selection, no file input exists within the inspected composer ancestors.
- This browser observation supports bounded semantic root repair. It does not prove upload success or authorize generic page-wide roots/history scanning.

At 09:51 KST the rebuilt DenimDex failure handler promoted the SAME WKWebView successfully. Its visible composer contained TWO copies of the synthetic blue image and an empty prompt. Thus actual upload worked, preview recognition failed, and native-timeout followed by DataTransfer fallback duplicated the media. No prompt was automatically submitted. This device observation confirms the too-narrow/obsolete preview path and makes native-to-fallback replay unsafe after a handled native panel with unknown preview outcome.

At 09:55 KST, the synthetic PNG was uploaded through the official IAB file chooser without sending a prompt. Its preview is a DIV role=button containing one IMG and a direct BUTTON whose aria-label ends in Korean 제거. No data-testid was present. Legacy attachmentPreview selectors match none of this observed tile. Added a composer-scoped role/image/direct-remove semantic selector; exact count and selector-family max remain. The synthetic attachment was removed through its own button and the composer returned empty.

At 10:04 KST, the official page was inspected at 375×667. Its MOBILE opening control differs from desktop: native BUTTON aria-label=사이드바 보기, aria-controls=browser-sidebar-popover, aria-expanded=false. Two raw DOM clones exist; one lies under an aria-hidden ancestor with hidden parent visibility and one is accessible/visible. An exact role/name accessible lookup returns only one. The desktop app-shell-sidebar toggle is hidden at this width. Therefore desktop-only open-sidebar selectors cannot open this actual mobile control.

Source change (not yet device-verified): the Apple `openChatGPTSidebarScript` and Android `sidebarOpenScript(OPEN_AI)` now also match `button[aria-label='사이드바 보기'][aria-controls='browser-sidebar-popover'][aria-expanded='false']`, with the old/desktop selectors kept. Visibility now excludes elements with a `[aria-hidden='true']`/`[inert]` ancestor, a display:none/visibility:hidden/opacity 0/`hidden` ancestor, or a zero rect; a click happens only when exactly one unique valid control remains. Fixture: fixtures/chatgpt-mobile-sidebar-control.html. This records source and fixture only; iPhone behavior is unproven until a device run.
