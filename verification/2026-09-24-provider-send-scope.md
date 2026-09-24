# Stargram provider send-scope repair — 2026-09-24

## Scope and authorization

The user requested live AIBI verification, repair of discovered problems, and internal completion including installation and Git backup. AIBI providers are Gemini, ChatGPT, and Claude web sessions. This does not establish Codex CLI or Desktop compatibility.

The portable contract and AIBI/finish-work skills were followed. Only Stargram host adapters required a source change. The common runtime and DenimDex adapters were inspected for this exact defect and do not use the same restricted scope; no new end-to-end success is asserted for DenimDex.

## Sanitized device trace

On the existing authenticated Stargram iPhone installation (iOS 27.0), plain synthetic text with no media was used. No prompt, generated answer, cookie, account identifier, DOM, or full URL is retained here.

| Stage | Provider | Observation |
| --- | --- | --- |
| Before | ChatGPT | One send, one result application; completed in 13.360 seconds |
| Before | Gemini | Input ready but send button absent from local scope; three readiness timeouts; zero sends; failed in 48.885 seconds |
| Before | Claude | Same scope failure; zero sends; failed in 50.015 seconds |
| Numeric diagnosis | Gemini | No enclosing form; one visible matching send control in the document, none in the old local scope; nearest containing ancestor depth 7 |
| Numeric diagnosis | Claude | No enclosing form; matching controls outside the old local scope; nearest containing ancestor depth 6 |
| Repaired, visible | Gemini | One send, one result application, completion in 24.514 seconds |
| Repaired, visible | Claude | One send, one result application, completion in 81.434 seconds |

The temporary diagnosis recorded only counts, depths, and booleans. It was removed before the final build. Existing bounded diagnostics continue to omit user content.

## Repair and source review

AIBI 0.5.1 adds a host-only fallback after the existing local search fails. For Gemini and Claude without an enclosing form, ascend composer containers without querying body/html/document. Accept only a unique visible send candidate with provider-specific meaning. Multiple candidates fail safely. Existing disabled-button readiness, Stop/voice exclusion, attachment checks, cancellation, and one-shot dispatch remain in place. ChatGPT behavior is unchanged.

Claude implemented the Apple repair; Gemini implemented the corresponding Android repair. Claude independently reviewed the Android diff once and found no new blocking wrong-button, duplicate-send, or disabled/Stop regression. This is source review, not Android runtime evidence.

## Build and distribution

- Stargram Apple 2.6.5 (202609242208), iPhone/iPad targets: Release device build and strict code-signature verification passed. Existing app was terminated, updated without deleting data, and relaunched on the primary connected iPhone. Authentication remained available.
- Stargram Android 2.6.5 (363899), phone/tablet: Release APK builds passed in both the existing workspace and a detached checkout containing only this repair. The detached checkout initially lacked its local SDK path; copying the existing SDK-location-only configuration restored the normal build, which passed in 21 seconds.
- No native Mac, Windows, or Google TV application target was introduced. No connected Android device or mDNS target was found; Android installation and live behavior are unverified.
- Canonical Stargram distribution files and both consumer copies passed hash-based synchronization checks.
- Installed SKILL.md is byte-identical to skill-source.md; all eight shared runtime/provider/platform assets match canonical source. Stargram reference notes were updated, including removal of obsolete multi-send and unlimited-observation guidance.
- No test suite or test runner was created or executed. The live trace above documents the selector regression.
- Other uncommitted user work is excluded from the repair commits. No TestFlight, public release, or website publication was requested or performed.

## Final installed build: hidden execution

All three runs used Stargram 2.6.5 (202609242208) with adapter aibi-stargram-0.5.1 after temporary diagnostics were removed. The original browser-display preference was switched off for these checks.

| Provider | Total elapsed | Send attempts | Results applied | Outcome |
| --- | --- | --- | --- | --- |
| Gemini | 8.176 seconds | 1 | 1 | Completed |
| Claude | 14.925 seconds | 1 | 1 | Completed |
| ChatGPT | 11.998 seconds | 1 | 1 | Completed |

These runs validate text input, one-shot submission, generation observation, and the app result sink. Image attachments, forced provider errors, fresh login, and disconnected Android/iPad runtime behavior were not exercised by this check. Existing image behavior is not claimed from the text-only traces.

A further Claude hidden run was cancelled during generation at 7.966 seconds: one send attempt, terminal run_cancelled, zero result applications. Provider buttons became available again. The original browser-display preference was restored and the synthetic draft cleared.

## Cleanup anomaly and recovery boundary

During cleanup, tapping the bottom Send tab with a nonempty draft started the last-used Claude provider. That additional request was interrupted by navigation and failed readiness before dispatch (zero sends). This was not cancellation automatically restarting a request; source review confirms that the Send action explicitly starts the last-used provider.

A subsequent visible request once stayed before browser loading for 90.448 seconds and was cancelled (zero sends). No content was sent. The exact cause of this presentation stall is unconfirmed; it must not be described as fixed by the send-selector repair. Restarting the same installed app restored visible Claude execution: one send, one applied result, 47.846 seconds. A further controlled hidden-generation cancellation followed by reset, Settings off-to-on, and a new visible request successfully displayed the browser and completed in 44.357 seconds with one send and one result application. The display stall did not reproduce in that sequence. No speculative presentation code was added.

## Internal Git backup

- Stargram: 1df19e43aba963819250c8b254af37ad79c8f40b, pushed to origin/main.
- Stargram Android: dc324de93c80435a0d1145c05eabfd953085a215, pushed to github/main.
- AIBI: this record accompanies the host-adapter and skill-source repair commit on main.
