# ChatGPT iOS result extraction verification

- Trigger: DenimDex physical-device run produced a visible JSON response but no committed result card.
- Privacy: no prompt, generated answer, cookie, URL, credential, or image was captured.
- Finding: overlapping ChatGPT assistant selector families could count the same DOM node more than once and leave selector-family order in place instead of document order.
- Fix: `queryAll` now deduplicates nodes by identity and restores DOM order. Assistant baseline, submit verification, and observation use a first-matching selector family so turn containers and nested markdown descendants are never merged. Current mobile `article` turn fallbacks were added.
- Regression fixture: `fixtures/providers/chatgpt-overlapping-assistant-selectors.json`.
- Verification level: sanitized runtime regression; physical-device recheck required after consumer installation.

## Apple hidden-render follow-up

- Physical-device finding: a `WKWebView` mounted in a real off-screen 375×667 container still delayed ChatGPT composer hydration when the web view itself used near-zero alpha.
- Portable fix: keep the Apple web view fully opaque and rely on the off-screen host container for invisibility.
- DenimDex host fix: wait through the complete readiness window instead of exposing the provider browser after early composer misses; preserve a successful result when the visible fallback sheet dismisses.

## Child-frame navigation follow-up

- Physical-device symptom: ChatGPT image upload completed, but the host reported a disallowed-page error.
- Finding: the Apple navigation delegate applied the main-frame origin allowlist to ChatGPT uploader child-frame navigation such as `about:`, `blob:`, and provider CDN frames.
- Fix: allow child-frame navigation without script injection and continue applying the strict origin gate to main-frame navigation only.
- Privacy: diagnosis used only the host error code and sanitized screen state; no prompt, image, cookie, token, or generated response was recorded.
