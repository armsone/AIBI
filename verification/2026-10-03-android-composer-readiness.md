# Android Composer Readiness & Formless Provider Empty Composer Root Discovery

- Date: 2026-10-03
- Author: Android & Shared Runtime Adapter Worker
- Context: Repair Gemini/Claude photo failure (attachmentScope insisting on visible send button during empty composer preparation)

## 1. Problem Analysis & Evidence
- **Observed Failures**: Stargram iPhone traces showed Gemini photo 1/3/8 and Claude photo 3/8 failing with `composer_reset: root_present 0` while `composer_present 1` and `send_present 0`. Text-only requests passed.
- **Root Cause**: Both Gemini and Claude Web UIs dynamically omit the send/submit button when the text input and attachment queue are empty. On Apple, the attachmentScope insisted on finding a visible `sendButton()` before constructing a valid composer root for formless providers, causing prep-before-attachment to deadlock.
- **Shared Runtime Verification (`packages/runtime/aibi-browser-runtime.js`)**:
  - `composerRoot(config)`: `input && (input.closest('form') || input.parentElement?.parentElement?.parentElement)`. It is purely structural based on the input element ancestry.
  - `drainDiagnostics`, `checkReadiness`, `prepareAttachmentInput`, and `getAttachmentState`: do not require send button visibility to locate or scope composer root or previews.
  - Therefore, the shared DenimDex runtime path is already decoupled from send button visibility when finding the composer root. Source evidence was confirmed and retained without introducing regression or breaking changes.

## 2. Android Source Adaptation (`profiles/imanager/distribution/android/ExternalAIScripts.kt`)
- In `submissionHelpers(provider)`:
  - Formless Gemini/Claude empty composer root discovery is enhanced to support nearest semantic landmark containers (`fieldset`, `[role="region"]`, `[role="group"]`, `[data-testid="composer"]`, `[class*="composer"]`) and immediate parent hierarchy (`parentElement`, `parentElement.parentElement.parentElement`).
  - Strict guard: `isSafeComposerRoot(node)` explicitly rejects `document`, `document.body`, `document.documentElement`, and any nodes containing `historySelector` (`model-response`, `message-content`, `user-query`, `[data-testid*="conversation-turn"]`, `[data-testid*="assistant"]`, `.font-claude-message`, etc.).
  - Preserved guards:
    - Send ambiguity guards (`candidates.length === 1`).
    - Stop/voice/dictation exclusion patterns (`/stop|중지|정지|voice|음성|dictat|받아쓰기/i`).
    - Single dispatch reservation (`window.__sm_submit_dispatched`).
    - Task cancellation checks (`window.__sm_cancelled`).
    - Exact per-family preview counting without summing overlapping selector families.
    - Zero history leakage or arbitrary global DOM scanning.

## 3. Verification Artifacts & Fixtures
- Created sanitized synthetic device traces: [`fixtures/sanitized-composer-readiness-traces.json`](file:///Users/armsone/git/AIBI/fixtures/sanitized-composer-readiness-traces.json).
  - *Explicit Contract Note*: These synthetic numeric JSON records represent design inputs and contract specifications, NOT observed device traces, and do not substitute for actual DOM regression evidence.
- Created actual sanitized HTML DOM regression fixtures: [`fixtures/sanitized-composer-isolation-regressions.html`](file:///Users/armsone/git/AIBI/fixtures/sanitized-composer-isolation-regressions.html).
  - Provides actual DOM evidence covering: empty formless Gemini composer, empty formless Claude composer, page-root boundary rejection, prior history attachment exclusion, and ambiguous send candidate rejection.
- Traces capture:
  - `gemini-empty-composer-prep-01`: `composer_present: 1`, `send_present: 0`, `root_present: 1`, `preview_count: 0`, `reset_tiles: 0`, `reset_clicks: 0`, `reset_error: 0`.
  - `claude-empty-composer-prep-01`: `composer_present: 1`, `send_present: 0`, `root_present: 1`, `preview_count: 0`, `reset_tiles: 0`, `reset_clicks: 0`, `reset_error: 0`.
  - `gemini-multi-attachment-staged-08`: `composer_present: 1`, `send_present: 1`, `preview_count: 8`.
  - `claude-multi-attachment-staged-08`: `composer_present: 1`, `send_present: 1`, `preview_count: 8`.

## 4. Integrity and Compilation Status
- Language: Kotlin script literals and embedded JavaScript within `ExternalAIScripts.kt`.
- Verified syntax, delimiter pairings, string templates (`$allowAncestorSearch`, `${config.input}`, etc.), and scope boundaries.
- No shell/build/test/git/release execution performed pursuant to task instructions. Code changes remain uncompiled drafts pending authorized integration/test tasks.
