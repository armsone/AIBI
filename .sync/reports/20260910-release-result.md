# AIBI five-project sequential release result

Confirmed 2026-09-10 20:51 KST. Implementation and release submission are complete; new Apple external beta approval remains pending/unverified, not claimed as publicly installable. No automated future monitoring was created.

## Ordered results

| Order | Project | Version / build | Result |
| --- | --- | --- | --- |
| 1 | AIBI | 0.5.0 | GitHub release published; ZIP and tar.gz digests verified |
| 2 | Stargram Apple | 2.6.4 / 202609101649 | Upload succeeded 20:32:23; processing completed; Korean notes saved; existing internal group (9 testers) preserved; existing Public Beta added and review submitted with automatic notification |
| 3 | Stargram Android | 2.6.4 / 363898 / display 202609101658 | android-v2.6.4 published; actual public download stream and GitHub digest verified |
| 4 | DenimDex Apple | 0.3.2 / 202609101658 | Upload succeeded 20:41:20; processing completed; Korean notes saved; existing Internal group (2 testers) preserved; existing Public Beta added and review submitted with automatic notification |
| 5 | DenimDex Android | 0.3.2 / 363902 / display 202609101702 | android-v0.3.2 published; actual public download stream and GitHub digest verified |

## Public artifacts

- https://github.com/armsone/AIBI/releases/tag/v0.5.0
- https://github.com/armsone/Stargram-Android/releases/tag/android-v2.6.4
  - APK: 18,099,399 bytes; SHA256 d65fb475acfbc3c05a7e5a0a7273dde597d551f195081c8c5a2f563262a3e582
- https://github.com/armsone/DenimDex-Android/releases/tag/android-v0.3.2
  - APK: 48,861,571 bytes; SHA256 e40eed90c0a326aa7cc6fa7cac82e5d40c7eef7391e969902cc981769b7c9479
- Stargram existing invitation: https://testflight.apple.com/join/nzmW4WxW
- DenimDex existing invitation: https://testflight.apple.com/join/5pBrz6ME

Independent read-only verification confirmed both Android downloads and that both existing TestFlight pages identify the intended app and show participation guidance without full/closed messages. Actual participant acceptance and new Apple build approval were not verified.

## Installation and recovery

- Stargram release archive was data-preserving installed and relaunched on the designated iPhone earlier in this task.
- DenimDex release archive was installed successfully at 20:40. Launch initially returned Locked. Opening the existing iPhone Mirroring connection restored the normal launch path; the same bundle launched successfully at 20:41:44.
- Android devices and mDNS services were absent at 20:40. No emulator was started, no app was uninstalled, and no user data or signing identity was changed.
- Xcode account absence caused the earlier export failure. Representative's Xcode login restored export/upload. The separate browser login restored group/review work. No duplicate uploads or version bumps were made during recovery.

## Website

Only app/data.ts, app/testflight.ts and app/releases.ts changed in /Users/armsone/git/NasFinder.com. AIBI 0.5.0, new Android versions/downloads, two Apple review-submitted states, existing invitation links and private diagnostic-log descriptions were published. Existing product imagery, download allowlists and unrelated edits were preserved.

- Source: 5637476be3b041e5608953e3e757bc5d9ba8e7c3, pushed to github/main and sites/main.
- Sites version 374; deployment appgdep_6aa299a4baa48191b958a643dbe42857 succeeded.
- https://hanstree.com/apps/aibi, /apps/starmanager and /apps/denimdex returned 200 with the new version and diagnostic/review copy.
- Both release-download endpoints redirected to the exact new APKs.
- Public testflight-builds endpoint returned both new build identifiers, existing invitation links and waitingForReview. App Store Connect timestamps were Stargram 2026-09-10T04:33:35-07:00 and DenimDex 2026-09-10T04:42:01-07:00; these are server-registration timestamps, later than the local upload-success timestamps.
- Asset/catalog audit: errors=0; independent source review: no confirmed mismatches; production build passed. No test runner executed.
- Local non-browser home probe hit the existing ASSETS-only crawler path and returned 500; a normal browser-document request returned 200. Production content and downloads were checked independently. No unrelated worker change was made.
- Existing GitHub dependency warnings: 4 high, not investigated or fixed by this release task.
- A Sites lookup response included a temporary access token in tool output. The representative was informed; no secret was written to files and subsequent credential output was filtered. No credential/security rotation was performed.

## Source synchronization checkpoints

These are implementation checkpoints, not certification of full rendered/runtime parity.

```text
/Users/armsone/git/AIBI=3ab8ec484e32b91ab238da233ba2bd421dfa65c2
/Users/armsone/git/Stargram=7dd7830ae11daa17191420538e111bf86f7cd7e6
/Users/armsone/git/Stargram-Android=80663c2d2875ed3a01b6bf14e9e5da3ab5ee2511
/Users/armsone/git/DenimDex-iOS=e6df06d367293df0bfa62cab1377ba7281970670
/Users/armsone/git/DenimDex-Android=258b1e75ef9bf65fc69fe442338beeb8580d5e11
```

Global AGENTS, AIBI/project-sync, release/finish-work, TestFlight and Hanstree/Sites instructions were applied. Independent source review and corrected-finding rechecks were completed earlier. Real authenticated success evidence remains limited to the prior Stargram iPhone 1-photo, 5-photo and 5-photo-plus-text runs. New DenimDex end-to-end AI and Android rendered/runtime parity remain unverified. User confirmation is still needed for those behaviors. Linear ARM-25 contains the recovery and publication history.
