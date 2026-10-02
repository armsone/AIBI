# Shared Runtime Current-Composer Isolation Verification Note

- **Date**: 2026-10-03
- **Author**: Antigravity (Assistant)
- **Target File**: [`packages/runtime/aibi-browser-runtime.js`](file:///Users/armsone/git/AIBI/packages/runtime/aibi-browser-runtime.js)
- **Allowed Scope**: Narrow repair of `composerRoot`, `attachmentCount`, and `sendButton` isolation across all providers; regression fixtures in `fixtures/`; trace contract clarification in `fixtures/sanitized-composer-readiness-traces.json`; verification records.

---

## 1. Problem Identification & Root Cause
- **Vulnerability in Previous Implementation**:
  - `composerRoot(config)` previously fell back unconditionally to `input.parentElement?.parentElement?.parentElement` without checking for boundaries or history container leakage.
  - `attachmentCount(config)` and `sendButton(config)` scoped to `composerRoot` only if `config.id === 'chatgpt'`; for all other providers (Gemini, Claude, Grok, etc.), they fell back directly to `document`.
  - Scoping to `document` caused:
    1. Past history attachments (e.g., prior user messages or file thumbnails in conversation turns) to be erroneously counted in `attachmentCount`, violating isolation contracts.
    2. Global/navigation buttons or inactive send buttons anywhere on the page to be queried, risking mis-clicks or false ready states.
    3. Traversal that could escape upward to `document.body` or `document.documentElement` if safe container boundaries were not strictly enforced.

## 2. Shared Runtime Implementation Changes
In [`packages/runtime/aibi-browser-runtime.js`](file:///Users/armsone/git/AIBI/packages/runtime/aibi-browser-runtime.js):

1. **`isSafeComposerRoot(node, config)`**:
   - Rejects `null`, `undefined`, non-element nodes (`nodeType !== 1`), `document`, `document.body`, `document.documentElement`, and nodes with tag name `'BODY'` or `'HTML'`.
   - Rejects any node matching or containing conversation history / assistant content selectors:
     - `model-response`, `message-content`, `user-query`
     - `[data-test-id="model-response"]`, `[data-test-id="user-query"]`
     - `[data-testid="transcript-row"]`, `[data-testid*="conversation-turn"]`
     - `[data-testid*="assistant"]`, `[data-testid*="user-message"]`
     - `.font-claude-message`, `.font-claude-response`, `.font-user-message`
     - `[data-message-author-role]`
     - Any provider-configured `config.selectors.assistantMessage` selectors.

2. **`composerRoot(config)`**:
   - Locates `promptInput` first.
   - Evaluates nearest candidate boundaries in order:
     - `input.closest('form')`
     - `input.closest('[data-testid="composer"]')`
     - `input.closest('[class*="composer"]')`
     - `input.closest('fieldset,[role="region"],[role="group"]')`
     - Immediate structural ancestors: `input.parentElement?.parentElement?.parentElement`, `input.parentElement?.parentElement`, `input.parentElement`.
   - Validates each candidate through `isSafeComposerRoot(candidate, config)`.
   - Returns `null` if no safe boundary is found (never leaks to `document.body` or `document`).

3. **`attachmentCount(config)`**:
   - Evaluates `const root = composerRoot(config);` for **all** providers (not just ChatGPT).
   - If `root` is missing or unsafe, returns `0`.
   - Counts `visibleFamilyCount` strictly within the verified `root`, completely isolating current attachments from conversation history.

4. **`sendButton(config)`**:
   - Evaluates `const root = composerRoot(config);` for **all** providers.
   - If `root` is missing, returns `null` (supports empty formless composers where send button is dynamically omitted).
   - Queries `config.selectors.submitButton` strictly within `root`.
   - Filters candidates for visibility and excludes stop/voice/dictation labels (`/stop|중지|정지|voice|음성|dictat|받아쓰기/`).
   - Enforces unique send candidate requirement: returns the button if and only if `candidates.length === 1`. If ambiguous (0 or >1 candidates), returns `null`.

5. **Preserved Invariants & Existing Guards**:
   - Single dispatch reservation guard (`submitDispatched`) in `submitPrompt`.
   - Prompt verification and idempotency check (`current !== normalize(lastInjectedPrompt)`).
   - Generation visibility check (`generationVisible(config)`).
   - Diagnostic recording and snapshot reporting (`drainDiagnostics`).

## 3. Regression Fixtures & Evidence
- **Fixture Index**: [`fixtures/sanitized-composer-isolation-regressions.html`](file:///Users/armsone/git/AIBI/fixtures/sanitized-composer-isolation-regressions.html)
  - This file is an index only (plain links + loading/config-selection instructions + expected outcomes). It previously held all 9 scenarios as sibling `<div>` sections inside one shared document, which meant a `queryFirst`-style query would only ever resolve against the first matching scenario rather than the one under test, so the combined document did not provide independent regression evidence per scenario.
  - Each scenario is now a standalone, sanitized HTML document under [`fixtures/composer-isolation/`](file:///Users/armsone/git/AIBI/fixtures/composer-isolation/), loadable as its own full-page `document`:
    - `01-gemini-empty-formless.html`: Empty formless Gemini composer (omitted send button, safe landmark discovery).
    - `02-claude-empty-formless.html`: Empty formless Claude composer (`fieldset` boundary, absent send button).
    - `03-page-root-boundary-orphan.html`: Page-root boundary guard; the orphan `textarea` is now a *direct child of `document.body`* (no wrapper `div`s), so `parentElement`/`parentElement.parentElement`/`parentElement.parentElement.parentElement` resolve to `body`/`html`/`null` respectively and no `form`/`[data-testid="composer"]`/`[class*="composer"]`/`fieldset,[role="region"],[role="group"]` ancestor exists at any depth. `promptInput` is resolved via an explicit fixture-only override (`['#orphan-prompt-input']`), since no Gemini/Claude registry `promptInput` selector matches a bare `<textarea>`. `composerRoot` must still resolve to `null`.
    - `04-history-attachment-exclusion.html`: Prior history attachment exclusion. `promptInput` matches the Gemini registry selector `textarea[aria-label*='prompt' i]` exactly (`aria-label="Prompt input"` added, no override). `composerRoot` resolves to `form.current-composer-form` via `closest('[data-testid="composer"]')`. `attachmentCount` is counted via the Claude registry selector `div[data-testid='file-thumbnail']`, matching exactly 1 element scoped inside that form; the history thumbnail is a sibling outside the scoped root and is excluded by DOM scope, not by a different attachment selector. `sendButton` matches the shared Gemini/Claude registry selector `button[aria-label*='Send' i]`.
    - `05-ambiguous-send-candidates.html`: Two `type="submit"` candidates (both matching the shared Gemini/Claude registry selector `button[aria-label*='Send' i]`) in one composer scope (`form.ambiguous-composer`) must produce `sendButton === null`. `promptInput` matches the Gemini registry selector `textarea[aria-label*='prompt' i]` exactly (no override).
    - `06-filtered-send-candidates.html`: Voice/dictation controls alongside one legitimate send button in `form.control-composer`. `promptInput` matches the Gemini registry selector `textarea[aria-label*='prompt' i]` exactly (no override). Neither voice (`aria-label="voice mode"`) nor dictation (`aria-label="음성 받아쓰기"`) matches any registry `submitButton` selector on its own, so this fixture uses an explicit fixture-only `submitButton` override (`['.button-group button']`) so the runtime's negative-label filter — not the selector query — performs the exclusion, leaving exactly the registry-matched `button[aria-label*='보내기' i]` send button.
    - `07-claude-attach-and-true-send-typebutton.html`: Claude "Attach files" `type="button"` plus a true `type="button"` send control with a paper-plane icon; attach button must be excluded, leaving exactly one positive candidate.
    - `08-ambiguous-two-true-send.html`: Two distinct buttons with explicit positive send semantics at the same priority; uniqueness guard must reject both, producing `null`.
    - `09-unlabeled-unknown-button-no-positives.html`: Restructured as a real Claude `fieldset.composer-fieldset[role="group"]` composer with a `div.ProseMirror[contenteditable='true']` editor (exact Claude registry `promptInput` match: `fieldset div[contenteditable='true']`) and one unlabeled button that exactly matches the Claude registry `submitButton` selector `fieldset button[type='button']:not([disabled])`. The candidate is therefore admitted into the pool by the selector itself, then rejected by the runtime's no-positive-semantic branch (no "Send"/"전송" label, no aria-label, no paper-plane icon) — exercising that rejection branch specifically, not selector-level exclusion — producing `sendButton === null`.
  - Source-only: these fixtures have not been loaded, parsed by a browser engine, or executed against the runtime. Expected `composerRoot`/`attachmentCount`/`sendButton` outcomes are documented in the index per the runtime contract, not captured from an observed run. All markup is synthetic placeholder content; no real prompts, answers, or private content appear in any fixture.
- **Synthetic Traces Contract Clarification**: [`fixtures/sanitized-composer-readiness-traces.json`](file:///Users/armsone/git/AIBI/fixtures/sanitized-composer-readiness-traces.json)
  - Explicitly marked as **DESIGN INPUT ONLY**; numeric JSON specifications are design inputs, not observed device traces, and do not substitute for actual DOM evidence.

## 4. Verification & Integrity Checklist
- [x] Syntax & Delimiters: Verified in [`packages/runtime/aibi-browser-runtime.js`](file:///Users/armsone/git/AIBI/packages/runtime/aibi-browser-runtime.js).
- [x] Scope: All providers use `composerRoot(config)` for both `attachmentCount` and `sendButton`.
- [x] Boundary Guards: `document`, `document.body`, `document.documentElement`, and history-containing nodes rejected.
- [x] Formless Composer Support: Returns `null` without throwing when send button is absent in empty composers.
- [x] Uniqueness Guard: Enforced `candidates.length === 1` for `sendButton`.
- [x] Execution Restrictions: Native file read/edit only. No shell commands, build, test, git, or release executed.

## 5. Fixture/Selector Correction (Read-Only Review Follow-Up)
- A read-only review found that fixtures 03, 04, 05, 06, and 09 previously referenced
  selectors/expectations that did not structurally match the markup (e.g. fixture 03
  retained an intervening `div` that would have resolved as a valid `composerRoot`
  rather than `null`; 04/05/06 used plain `textarea`/`button` markup with no registry
  `promptInput` match; 09's button did not match any registry `submitButton` selector,
  so it never reached the no-positive-semantic rejection branch it was meant to exercise).
- Each of those five fixtures was corrected per Section 3 above to either match an exact
  registry selector cited from `packages/providers/aibi-providers.json`, or to use an
  explicit, documented fixture-only config override. No fixture is described as matching
  "either provider" ambiguously.
- Scope of this correction: `fixtures/composer-isolation/*.html`,
  `fixtures/sanitized-composer-isolation-regressions.html`, and this verification note
  only. The shared runtime (`packages/runtime/aibi-browser-runtime.js`) and all
  Apple/mobile files were not touched. Source-only: no execution, DOM evaluation, build,
  test, or git action was performed for this correction.
