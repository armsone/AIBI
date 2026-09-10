# ChatGPT attachment source lifetime correction

## Sanitized device trace

- Environment: iPhone, visible ChatGPT AIBI session, five selected images.
- Observed: all five thumbnails and the generated prompt appeared in the ChatGPT user turn, but no assistant generation began before the host's 1:59 observation deadline.
- Privacy: no image content, prompt text, account data, cookies, URLs, or response text are recorded here.

## Identified lifetime risk and correction

- A preview-count match was treated as upload completion on Apple. The portable engine then scheduled deletion of the native-file-panel directory after 60 seconds; its iManager distribution also cleared files once a preview was visible or a navigation began.
- Provider web apps can continue reading selected file URLs after the conversation turn has appeared. The files now remain available until the task completes, fails, is cancelled, or its WebView is dismantled.
- Android already retains its native attachment batch through task completion, failure, cancellation, or browser disposal. Its lifetime behavior was inspected and needs no source change.
- This was a source-lifetime risk, not proof of the sole cause of the observed failure. Later reproduction found an unsafe repeat-send/refill path. See `2026-09-10-submission-and-diagnostics.md` for subsequent changes and successful device evidence.

## Verification level

- Source change and sanitized physical-device trace recorded.
- Subsequent Stargram iPhone build, data-preserving installation, and authenticated one-image, five-image, and five-image-plus-text completions are recorded in the linked submission report. This evidence does not establish completion on other consumers.
