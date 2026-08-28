# StarManager Android selected-image AIBI batch

Date: 2026-08-28

## Implemented

- StarManager snapshots every selected image, skips videos, and preserves the user's current media order.
- One through eight selected images are normalized sequentially to JPEG, at most 2,048 px on the long edge and 2,000,000 bytes each.
- EXIF orientation is applied before resizing so portrait and mirrored source photos remain correctly oriented.
- Each normalized file crosses the Android WebView bridge separately; the browser commits the complete ordered `DataTransfer` batch with one change event.
- Gemini mobile advances through `업로드 및 도구 → 파일 → file input`; ChatGPT and Claude retain direct-input support.
- Android now prioritizes ordered `FileProvider` URIs through `WebChromeClient.onShowFileChooser`. The actual callback mode controls whether the URI array is returned at once or one URI is returned per callback; provider DOM metadata is not treated as the native-mode authority. `DataTransfer` remains the bounded fallback.
- Android photo tasks use an attached visible WebView because a native attachment-sheet gesture cannot be completed reliably from the host's off-screen text-automation WebView.
- StarManager gallery and camera ingestion normalize one source at a time, apply EXIF orientation, and retain bounded copies. UI thumbnails and the active preview decode off the main thread at bounded dimensions.
- Prompt injection is blocked until one attachment-preview selector family reports exactly the requested count.
- Partial, over-limit, non-multiple, and unconfirmed batches fail closed into the visible all-photos recovery path.

## Verification level

- StarManager Android `testDebugUnitTest`: passed (48 tests).
- StarManager Android `assembleDebug`: passed.
- StarManager Android `lintDebug`: passed.
- AIBI Python contract suite: passed (18 tests).
- AIBI JavaScript runtime media regression: passed.
- APK SHA-256: `93592894cbb30d03e146c33d14b33c1a711ac47cbebc8cf9d7bb423d552123c3`.
- Data-preserving replacement install and launch passed on Samsung SM-F968N (`192.168.0.142:5555`), SM-T500 (`192.168.0.152:5555`), and SM-F956N (`192.168.0.166:5555`).
- Installed Codex AIBI Android assets are byte-identical to the canonical project packages.
- A first bounded-preview build crashed because it explicitly recycled a bitmap while Compose still retained a display-list reference. The physical-device crash trace identified `Canvas: trying to use a recycled bitmap`; explicit UI bitmap recycling was removed, the full verification gate was rerun, and the corrected APK was reinstalled.
- Authenticated SM-F968N live run on 2026-08-29 selected five photos through ADB, delivered five ordered native URIs with `MODE_OPEN_MULTIPLE`, observed five semantic attachment controls, submitted only after the exact-count gate, and entered Gemini generation with the app process alive.

## Remaining device evidence

- Gemini Android attachment and submission are runtime-confirmed for five selected photos. The chosen fixture consisted of prior Gemini screenshots and caused Gemini to enter image generation; it exceeded StarManager's existing 1:59 answer-import window, so this run does not claim answer-import success.
- Authenticated attachment and answer-import traces for ChatGPT and Claude on Android remain pending.
- Fresh portrait-orientation, repeated paging, and eight-photo responsiveness traces remain pending after the corrected reinstall.
