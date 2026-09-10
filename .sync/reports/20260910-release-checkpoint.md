# AIBI 0.5.0 five-project release checkpoint

Status at 2026-09-10 17:08 KST: source ports, independent source review, four app Release builds and Git backups completed. Sequential public release stopped at the second project because Xcode could not find an App Store Connect account for team T7B4EPLHPK. This is not a five-project release-complete report.

| Order | Project | Version | Current result |
| --- | --- | --- | --- |
| 1 | AIBI | 0.5.0 | GitHub release published; both asset digests match local archives |
| 2 | Stargram Apple | 2.6.4 / 202609101649 | Archive, strict signature, data-preserving iPhone install and relaunch passed; TestFlight export blocked by account access |
| 3 | Stargram Android | 2.6.4 / 363898 | Clean scoped release APK built; signer matches 2.6.1; public release waits for preceding step |
| 4 | DenimDex Apple | 0.3.2 / 202609101658 | Archive and strict signature passed; iPhone install first timed out, paired-device refresh then retry could no longer locate device; upload waits |
| 5 | DenimDex Android | 0.3.2 / 363902 | Release APK built and signed with the established release certificate; public release waits |

## Preserved implementation checkpoints

These are source/build checkpoints, not full rendered or authenticated runtime parity certification.

```text
/Users/armsone/git/AIBI=3ab8ec484e32b91ab238da233ba2bd421dfa65c2
/Users/armsone/git/Stargram=7dd7830ae11daa17191420538e111bf86f7cd7e6
/Users/armsone/git/Stargram-Android=80663c2d2875ed3a01b6bf14e9e5da3ab5ee2511
/Users/armsone/git/DenimDex-iOS=e6df06d367293df0bfa62cab1377ba7281970670
/Users/armsone/git/DenimDex-Android=258b1e75ef9bf65fc69fe442338beeb8580d5e11
```

All five implementation commits are pushed. Stargram Android's unrelated existing Automation, Random, provider controls, branding, and related data/model edits were left uncommitted and preserved. Its release APK was built from `/Users/armsone/git/Stargram-Android-release-20260910` at the scoped commit above, not from that dirty working tree. Global rule files and unrelated reports were not included.

## Artifacts and integrity

- AIBI: https://github.com/armsone/AIBI/releases/tag/v0.5.0
  - tar.gz SHA256: e5c318c6d6f83da25e4e82d883b68e21b0bbda4869dc9ac6cd3ff53f34ac8c60
  - zip SHA256: 94a187646c73fdd5b4a0a76dc971d79c8ea59da5f330ceaec4618f78c92e456a
- Stargram archive: `/Users/armsone/git/Stargram/build/Archives/Stargram-2.6.4-202609101649.xcarchive`
- DenimDex archive: `/Users/armsone/git/DenimDex-iOS/build/Archives/DenimDex-0.3.2-202609101658.xcarchive`
- Stargram APK: `/Users/armsone/git/Stargram-Android-release-20260910/app/build/outputs/apk/release/Stargram-Android-2.6.4.apk`
  - SHA256: d65fb475acfbc3c05a7e5a0a7273dde597d551f195081c8c5a2f563262a3e582
  - signer SHA256: 837bd274f558659a3aec9bd31308b8ac01916c386230f946f5f7347d7f6f9b0f (same as prior public release)
- DenimDex APK: `/Users/armsone/git/DenimDex-Android/app/build/outputs/apk/release/DenimDex-Android-0.3.2.apk`
  - SHA256: e40eed90c0a326aa7cc6fa7cac82e5d40c7eef7391e969902cc981769b7c9479
  - signer SHA256: 9fa4857ee94b8b33a44b4f91dabdab241121e7c8471ff8796511eca22cd0a59b (same as prior public release)

## Resume without rebuilding or repeating failures

1. Representative reconnects an Apple account with App Store Connect access in Xcode Settings > Accounts and signs into App Store Connect in the opened browser. No credential, certificate, security or account change was performed by the agent.
2. Retry the existing Stargram `xcodebuild -exportArchive` command with the archive above, `ExportOptions-TestFlight.plist`, export directory `build/Exports/Stargram-2.6.4-202609101649`, and `-allowProvisioningUpdates`. Previous result: `exportArchive Failed to Use Accounts`; detailed log: `Failed to find an account with App Store Connect access` for the existing team.
3. Confirm processing, existing internal-group preservation, new build notes, existing Public Beta group linkage/review and public-link state. Do not equate upload with external beta availability.
4. Publish Stargram Android APK, then DenimDex Apple through its existing ExportOptions.plist, then DenimDex Android APK. Keep the requested order.
5. Reconnect designated Android devices and iPhone for remaining data-preserving install/runtime checks. No Android device or mDNS service was available. Do not auto-start an emulator or replace a differently signed installation by deleting data.
6. Only after app release outcomes are established, update Hanstree.com in NasFinder.com with truthful availability and download links. Website files have not been opened or changed for this release.

## Verification truth and rules

Applied AIBI canonical contract, scoped project-sync, release/finish-work and TestFlight procedures under the user's global AGENTS rules. Native sources and Settings were reviewed independently once; corrected findings were rechecked. No test runners were executed. Prior AIBI consumer-path renames, including already-existing path-only test edits, were preserved as distribution dependencies; no new tests were authored. Provider success evidence is limited to the earlier Stargram iPhone 1/5/5-plus-text runs. DenimDex authenticated end-to-end behavior and new Android rendered parity remain unverified. Linear ARM-25 tracks the release blocker and continuation.
