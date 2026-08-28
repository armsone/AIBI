# Gemini mobile photo upload provider trace

- Date: 2026-08-28 KST
- Provider surface: `https://gemini.google.com/app` in the installed Android host WebView
- Privacy: selector names, element roles, input accept values, and state counts only. No cookies, account data, prompts, or generated answers were recorded.

## Sanitized observed DOM

- Upload trigger: `button[aria-label="업로드 및 도구"]`
- Provider wrapper: `gem-icon-button[arialabel="업로드 및 도구"]`
- Opening the menu creates multiple hidden file inputs.
- The first input accepts documents and does not accept images.
- A later input is `input[type="file"][accept="image/*"]`.
- Assigning a sanitized one-pixel PNG through `DataTransfer`, then dispatching `change` and `input`, produced a visible `uploader-file-preview` and `.file-preview-container`.

## Regression requirement

The StarManager adapters must open the current upload menu, prefer an image-accepting file input over the first generic/document input, dispatch the standard input events, and wait for a visible preview before submitting the prompt.

## Installed-device verification

- Android `SM-F968N`: selected one representative photo with no draft text, tapped Gemini, observed automatic preview attachment and submission, then observed a photo-grounded answer return to the same StarManager editor. No manual file-picker action occurred inside Gemini.
- iPhone 17 Pro: selected one representative photo with no draft text, tapped Gemini, observed the attachment preview and submitted prompt on the provider surface, then observed the generated answer return to the same StarManager editor and the provider surface dismiss automatically. No manual attachment action occurred.
- Full prompt and answer text were not retained in this trace.
