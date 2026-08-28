# Portable eight-image pipeline verification

- Date: 2026-08-28 KST
- Scope: portable AIBI packages only
- Version: 0.3.0
- Host installation: intentionally not performed; StarManager insertion is deferred until the
  portable capability is accepted as ready.
- Privacy: fixtures contain selector families, counts, and synthetic one-byte image payloads only.
  No prompts, answers, accounts, cookies, tokens, original filenames, EXIF, or user photos exist in
  the test material.

## Implemented contract

- Ordered task snapshot of zero to eight images with optional host-owned semantic roles.
- Sequential Apple and Android normalization to generated JPEG copies.
- Default 2,048 px long edge and 2,000,000 byte per-image ceiling.
- Generated `aibi-01.jpg` through `aibi-08.jpg` filenames and source-metadata removal.
- Chunked one-image-at-a-time WebView bridge staging followed by one atomic `DataTransfer` commit.
- Apple iOS 18.4+ public `WKUIDelegate` multi-file panel path before DOM fallback.
- Preview-count baseline and exact full-batch confirmation before prompt injection and submission.
- Explicit fallback for provider capability failure, non-multiple input, partial preview, order
  mismatch, or timeout. Text-only silent degradation is prohibited.

## Verification performed

- `node --check packages/runtime/aibi-browser-runtime.js`: passed.
- `node tests/runtime_media_test.js`: passed for ordered eight-file assignment, preview count,
  non-multiple rejection, sequential bridge staging, atomic commit, and order mismatch rejection.
- `python3 -m unittest discover -s tests -v`: 10 tests passed.
- Apple simulator `swiftc -typecheck` for `AIBIMediaPipeline.swift` and `AIBIEngine.swift`: passed
  with iOS 15 deployment target and iOS 18.4 availability-gated file panel delegate.
- Kotlin 2.3.21 JVM compilation against Android API 37, coroutines 1.9.0, and annotations 23.0.0
  for `AIBIMediaPipeline.kt` and `AIBIEngine.kt`: passed. Only pre-existing/dependency deprecation
  warnings were emitted.
- JSON parsing and `git diff --check`: passed.

## Verification level

`implemented from source`. Live Gemini, ChatGPT, and Claude eight-image runs on Apple and Android
devices remain required before claiming `runtime verified`. No host integration or AIBI parity claim
is made by this record.
