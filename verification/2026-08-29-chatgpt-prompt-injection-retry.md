# ChatGPT prompt-injection retry after attachment-induced composer replacement

- Trigger: DenimDex iOS surfaced `ChatGPT 입력 화면을 제어하지 못했습니다. 다시 시도해주세요.` right
  after a six-photo attachment batch completed and `injectPrompt` was called exactly once.
- Privacy: no prompt, generated answer, cookie, token, or full URL was captured. Diagnosis used
  only the host error string and the provider's documented `quirks.lateDomReplacement` flag.
- Finding: `chatgpt.quirks.lateDomReplacement` documents that ChatGPT frequently replaces the
  prompt-textarea DOM node during hydration and right after attachment insertion. The Apple
  orchestrator called `injectPrompt` a single time and treated any non-success (or any thrown
  JS error) as an immediate terminal failure, with no relocate/retry window for this known,
  recurring provider quirk. A JS exception's absence was also trusted as proof of a durable
  insertion, even though the composer node can be swapped between `injectPrompt` returning and
  the next step reading the DOM.
- Fix:
  - `packages/runtime/aibi-browser-runtime.js`: added `RUNTIME.verifyPromptInjected(config,
    promptText)`, which independently re-queries the prompt input and compares its current text
    against the expected prompt. This gives callers a way to confirm insertion instead of only
    checking `injectPrompt`'s own reported success.
  - `packages/apple/AIBIEngine.swift`: `recordBaselineAndInject` now retries prompt injection up
    to `AIBITimingProfile.promptInjectionRetryLimit` (default 4, `promptInjectionRetryDelay`
    0.6s) attempts. Each attempt re-locates the input fresh and, on a reported success, calls
    `verifyPromptInjected` before trusting it. `EXISTING_TEXT_PRESERVED` (a different,
    non-empty user prompt already present) is treated as terminal and is never retried or
    force-overwritten, preserving the existing "do not clobber user text" rule.
  - DenimDex mirrored the same runtime function and retry/classification logic in
    `DenimDex/AIBI/Resources/aibi-browser-runtime.js` and `DenimDex/AIBI/AIBISession.swift`,
    keeping the host's existing Korean failure messages and `hiddenOnly` presentation handling
    unchanged; only the transient-miss/verification gap was fixed.
- Regression fixture: `fixtures/providers/chatgpt-late-dom-replacement-injection.json` documents
  the sanitized attempt-by-attempt trace (bounded retries succeeding within the retry limit, and
  the terminal existing-text case never retried).
- Regression tests: `tests/runtime_prompt_injection_test.js` (composer missing, verified match,
  simulated late replacement causing a verify mismatch, existing-text block, and force override)
  and `tests/test_media_contract.py::test_runtime_retries_late_dom_replacement_and_preserves_existing_text`
  plus a fixture-shape check and a source-level assertion that the Apple engine wires
  `verifyPromptInjected` and `EXISTING_TEXT_PRESERVED` into the retry loop.
- Verification performed on 2026-08-29:
  - `python3 -m unittest discover -s tests`: 25 tests passed.
  - DenimDex iPhone 17 Pro simulator test run: 53 tests passed, including the four host-side
    prompt-injection classification cases; app and test targets built successfully.
  - Installed Codex AIBI `SKILL.md`, Apple engine, and browser runtime were synchronized and
    byte-compared with this project source.
- Verification level: sanitized runtime/unit regression plus iOS simulator build/test. A
  physical-device recheck on an authenticated ChatGPT session after a 6+ photo attachment batch
  is still required before this can be considered device-verified.
