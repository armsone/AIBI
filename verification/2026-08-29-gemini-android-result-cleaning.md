# Gemini Android validated-result cleaning

Date: 2026-08-29

## Observed failure

- The Android host successfully attached and submitted the selected photos.
- Gemini returned final prose together with Python length-check statements and length statistics.
- StarManager imported the complete assistant response instead of only the validated prose.
- No prompt, answer text, photo content, account data, or session data was recorded in the fixture.

## Correction

- The StarManager Android cleaner activates only when Python or length-diagnostic markers exist.
- It then selects content after the last standalone `Text:` marker.
- Ordinary prose containing `Text:` is unchanged when no diagnostic marker exists.

## Verification

- Sanitized provider-shape fixture: `fixtures/providers/gemini-android-answer-validation-debug.json`.
- AIBI contract tests passed.
- StarManager Android cleaner regression, full JVM suite, build, and lint passed.
