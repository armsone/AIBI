# Shared Runtime Bounded Repair Verification Evidence

## Problem Identified by Independent Reviewer
In `packages/runtime/aibi-browser-runtime.js`, `sendButton()` queried all selectors in `config.selectors.submitButton` simultaneously across the scoped composer root.
For Claude, the selector chain in `aibi-providers.json` ends with `fieldset button[type='button']:not([disabled])`.
When the composer fieldset contains both an "Attach files" button (`<button type="button" aria-label="Attach files">`) and the actual send button (`<button type="button" aria-label="Send Message"><svg data-icon="paper-plane"></svg></button>`), querying all submit selectors collected both elements as candidates (`candidates.length === 2`).
Because `candidates.length !== 1`, `sendButton()` returned `null`, falsely blocking legitimate submission in formless Claude composers.

## Solution Implemented

1. **Negative Filtering**:
   - Expanded candidate exclusion beyond `stop|중지|정지|중단|voice|음성|dictat|받아쓰기` to include `attach|첨부|upload|업로드|tool|도구|menu|메뉴|plus|추가|file|파일|photo|사진`.
   - Prevents file upload/tool/menu trigger buttons from ever qualifying as send candidates.

2. **Ordered Selector Priority & Positive Semantic Disambiguation**:
   - Evaluates selectors in precedence order (`for (const selector of selectorList)`).
   - At each selector priority level:
     - Filters out invisible elements and excluded buttons (`isExcludedCandidate`).
     - Detects explicit positive send semantics:
       - `aria-label`, `data-testid`, `title`, or text containing `send|submit|전송|보내기`.
       - Element `type === 'submit'`.
       - Enclosed `<svg>` with icon markers `paper-plane|arrow-up|send`.
     - If exactly one candidate has positive semantic confirmation, it is returned.
     - If multiple candidates have positive semantic confirmation at the same priority level, submission is safely blocked (`null`) to preserve uniqueness protection.
     - If no explicit positive markers exist but only one candidate matches the selector, that unique candidate is returned.
     - If multiple candidates match without distinction, submission is blocked (`null`).

3. **Invariants Preserved**:
   - Enabled guards (`submitBtn.disabled`, `aria-disabled === 'true'`) remain intact in `submitPrompt()`.
   - One-shot dispatch invariants (`submitDispatched`) and generation state checks (`generationVisible()`) are fully preserved.
   - Ambiguity protection is strictly enforced: ambiguous candidates are rejected without guessing or mis-clicking.

4. **Synthetic Regression Scenarios Added**:
   - Added `Scenario 7` to `fixtures/sanitized-composer-isolation-regressions.html`: Claude composer with broad `fieldset button[type=button]:not([disabled])` matching both an "Attach files" button and a true send button (`type='button'` with paper-plane SVG). Verifies that attach is excluded and the unique positive send button is resolved.
   - Added `Scenario 8` to `fixtures/sanitized-composer-isolation-regressions.html`: Ambiguous two true send candidates at the same priority level (`Send now` vs `Send with reasoning`), ensuring that ambiguity protection is preserved and returns `null`.

## Verification Scope and Limitations
- Source and signature inspection performed directly on `packages/runtime/aibi-browser-runtime.js` and `fixtures/sanitized-composer-isolation-regressions.html`.
- No shell, build, test runner, git, or physical device executions were performed (headless execution guard respected).
- Uncompiled draft changes have been checked against runtime signatures and DOM API conventions; live web or native bridge execution is not claimed.
