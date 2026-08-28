# Gemini iOS nested file-menu adaptation

Date: 2026-08-28

## Sanitized device trace

- Device class: iPhone.
- Requested images: 5.
- Visible provider state: Gemini mobile `업로드 및 도구` menu was open and showed `카메라`, `파일`, and `Drive` actions.
- Host state: StarManager correctly stopped before prompt submission and showed its visible fallback asking for all five images.
- No prompt, answer, account identifier, cookie, token, or source photo was captured.

## Cause

The adapter handled a direct file input and the first attachment trigger, but Gemini mobile required a nested sequence: `업로드 및 도구 → 파일 → WebKit file panel`. The automatic path stopped after the first menu.

## Change

- Added provider-owned attachment menu-action selectors and exact semantic labels.
- The portable runtime can advance from the trigger to the nested file action on separate hydration ticks.
- The Apple adapter now prepares temporary ordered URLs before advancing the menu, so `WKUIDelegate` can return all selected files when Gemini opens the panel.
- The StarManager host-adapted runtime advances up to four bounded steps: existing input, nested file action, or top-level trigger.
- Exact requested preview-count verification remains mandatory before prompt submission.

## Verification

- Sanitized regression fixture: `fixtures/providers/gemini-mobile-nested-file-menu.json`.
- JavaScript runtime regression covers trigger → menu action → input discovery.
- Python media-contract tests cover fixture sanitization and the observed five-image request.
- StarManager signed iPhone Debug build passed after the adapter change.
- The rebuilt app was installed and launched successfully on `BK_iPhone17pro`.
- The user subsequently confirmed the rebuilt iPhone Gemini path worked perfectly. No prompt, answer, account identifier, cookie, token, or source photo was recorded.
