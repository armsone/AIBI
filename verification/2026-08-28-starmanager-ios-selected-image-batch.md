# StarManager iOS selected-image AIBI batch

Date: 2026-08-28

## Implemented

- StarManager snapshots every selected image, excluding videos, in the user's current media order.
- The task sends the selected image count from one through eight; eight is the host maximum, not a fixed upload count.
- Transfer copies are normalized sequentially to at most 2,048 px on the long edge and 2,000,000 bytes per JPEG.
- iOS 18.4+ supplies the ordered URL array through the public WebKit file-panel delegate when the provider permits multiple selection.
- The DOM fallback stages one normalized image per bridge call and commits one ordered `DataTransfer` batch.
- Prompt injection and submission remain blocked until the visible attachment-preview count increases by exactly the requested count.
- Partial, over-limit, non-multiple, and order-mismatch paths fail closed and transition to visible user confirmation.

## Verification level

- `python3 -m unittest discover -s tests -p 'test_*.py'`: passed (11 tests, including the StarManager iOS distribution assertion).
- `node tests/runtime_media_test.js`: passed.
- StarManager iOS Simulator Debug build with code signing disabled: passed.
- StarManager iPhone Debug build for `BK_iPhone17pro`: passed and signed.
- StarManager 2.3.0 build 202608282124: installed successfully on `BK_iPhone17pro` with existing app identity preserved.
- The final incremental device build passed, was installed again, and launched successfully on `BK_iPhone17pro` after the device became available.
- The installed Codex AIBI StarManager reference was synchronized with the selected-count 1–8 image behavior.
- After installation, the user confirmed that the iPhone Gemini, ChatGPT, and Claude paths all worked perfectly. This is recorded as user-observed live-provider evidence; no prompt, answer, account data, or session data was captured.

## Device evidence status

- Live authenticated execution is confirmed by the user for all three supported providers. The exact per-run selected counts were not recorded separately, so count-specific 1-versus-8 traces remain outside this document.
