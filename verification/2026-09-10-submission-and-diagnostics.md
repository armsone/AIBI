# Submission and diagnostic update

## Evidence, not assumptions

Stargram iPhone had image-containing requests followed by failure, and a later text-only answer asking for images. The previous retry path could click the same composer button after its semantic role changed, and refill an already consumed composer. This is a confirmed unsafe retry path; it is not proof that every historical failure had that single cause.

After one-shot dispatch, composer-scoped attachments, semantic Stop/voice exclusion, and observation-only verification:

- One image: real image description, result applied; 28.308 seconds including initial prompt preparation.
- Five images: actual messages request contained five image entries, one send attempt; result applied at 14.451 seconds.
- Five images plus text: actual messages request contained five image entries; result applied at 15.344 seconds.
- No request_failed events in these three recorded runs. No prompts, responses, image bytes, accounts, or URLs are included in this report.
- Stargram settings displayed the latest diagnostic JSON in the system share sheet. No recipient was selected.

## Scope for this release

Portable: one-shot dispatch and pending/started distinction; current-composer attachment count and pre-send check; cancellation before side effects; bounded privacy-safe diagnostics and user-initiated JSON export.

Host-only: Stargram composer bottom scroll clearance (96 points on Apple; corresponding product behavior on Android), existing wording and result policy. Do not impose Stargram geometry on DenimDex.

## Transfer acceptance

Update canonical Apple/Android adapters and provider runtime first. Port canonical changes into the four host apps using existing native architecture. Keep host UI and data intact. Preserve unrelated dirty changes and record implementation, build, device behavior, source review, release and publication separately. No new test suites or runners without a user test instruction. Do not claim cross-platform visual parity from source alone.

Current evidence above belongs only to Stargram iPhone. Other consumers require their own verification.

## Release preparation verification

- Independent source review covered the canonical runtime, Apple/Android engines and stores, and all four host adapters and Settings integration. Identified refill-task cancellation and Android readiness/result-cancellation/config/queued-script defects were corrected and rechecked. No confirmed P1/P2 source blockers remain in the reviewed scope.
- Stargram Apple 2.6.4 (202609101649): Release archive and strict signature verification passed; data-preserving iPhone replacement installation succeeded.
- DenimDex Apple 0.3.2 (202609101658): Release archive and strict signature verification passed.
- Stargram Android 2.6.4 (363898): Release APK build passed from a clean release checkout containing only this change. Existing unrelated Automation/Random/provider-settings edits remain in the original worktree and are excluded from this release.
- Installed AIBI skill runtime, providers, native engines, diagnostic stores and submission guidance were synchronized with 0.5.0.
- All four consumer manifests report current source hashes. No test runners were executed for this release instruction.
- Android physical-device connection and mDNS discovery returned no devices. Android installation/rendered parity is not verified. New authenticated end-to-end behavior in DenimDex is not inferred from build success.
- Publication is a separate step; this report does not itself assert App Store/TestFlight approval or public availability.
