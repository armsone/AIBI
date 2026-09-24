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
- Other uncommitted user work is excluded from the repair commits. At the initial text-only internal completion, TestFlight, public release, and website publication had not been requested or performed. The later explicit release request and reopened image-plus-text work are recorded below.

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

## Reopened: image plus request text (2026-09-24, later the same session)

The user reported that Gemini and Claude attached a photo but did not enter the request text. Public upload had not started and is withheld pending repair and live confirmation of this path. The earlier text-only pass does not establish image-plus-text correctness.

Privacy-safe user-run diagnostics showed one image prepared and dispatched for each failing provider, then preview_count 0 and prompt_length 0, with no send attempt. The same one-image ChatGPT flow recorded attachment_ready 1, prompt insertion, a request containing one image, and result application in 20.678 seconds.

A diagnostic build reproduced the problem using the same selected photo and a synthetic request. Both providers reported no enclosing form, document-visible preview node union 2, old composer-scope preview count 0, and nearest preview-containing editor ancestor depth 7. Claude dispatched at 1.700 seconds and Gemini at 2.469 seconds. Neither entered the request text or sent it. The union value 2 can include different selector-family nodes for one image and must not be used as the actual image count.

This confirms that the host attachment-completion gate is observing the wrong scope. The corrective work must retain per-family attachment counting, exact requested-count matching, cancellation, and one-shot dispatch, and must not count document-wide or old conversation images. Android has the same source-level scope restriction; its live behavior remains unverified without a device.

A count-only candidate exposed a second contributing problem: visible-browser mode did not run the fresh-composer preparation used by hidden mode. A cancelled Claude draft therefore retained three preview tiles while the new request expected one, correctly blocking text insertion and dispatch. The repair also shares the current attachment root with reset and invokes preparation once before visible-mode automatic photo attachment; it does not waive the exact-count check. Verification remains in progress.

The visible-browser candidate (2.6.5, build 202609242307) then completed Gemini with one photo: attachment_ready at 3.251 seconds, prompt_inserted at 3.353 seconds, one send at 3.416 seconds, and one result application at 12.687 seconds (run completed 12.690 seconds). The previously cancelled Gemini attachments did not accumulate. This is an image-plus-text live pass, not a text-only inference. Claude remained blocked before new attachment by three stale preview tiles because the initial scoped reset could not locate its remove buttons; no send occurred, and work continued on that reset boundary.

## Android public release

Android 2.6.5 (versionCode 384427, display build 202609242307) was built from an isolated checkout containing only this repair, reviewed independently, signed with the same certificate as the previous public APK, committed as dc64413d2a9ec265963cf0fc0de08e61ba56b163, and pushed to github/main. Published release: https://github.com/armsone/Stargram-Android/releases/tag/android-v2.6.5 . The downloaded public APK exactly matches SHA-256 f9754b243000cdbe48bfcc7150c252c67a25f2bb6d5ba53858becd3ff4290d1d. It is not a draft or prerelease. No Android physical-device pass is claimed; this limitation is included in the release notes. Apple image-plus-text recovery was still in progress at this publication point.

## Apple preparation race and user confirmation

A later trace isolated an additional race: the navigation-finished signal preceded the live composer. Reset returned root_present 0, reset_tiles 0, reset_clicks 0, reset_error 0, but a temporary count of zero had been accepted as successful preparation. Restored old attachments appeared after dispatch. The corrected gate requires a connected visible editor, a validated current composer root, successful reset, and zero remaining attachments; preparation retries are bounded and occur only before the first new attachment attempt.

With that gate, Claude run 9234DDEE-8A9B-4B40-9E50-4A6EDB50B91C entered the request at 2.298 seconds, dispatched one send at 2.356 seconds, and observed generation at 2.939 seconds. The user subsequently reported that it appeared to work and explicitly asked to proceed with the release. A result_applied event was not present in the captured Claude trace, so end-to-end answer import is not claimed for this image run. Further repetitive functional trials were ended in accordance with that instruction. The final release also removes temporary broad DOM diagnostics and addresses the reviewed empty-composer/history distinction; this final delta is checked by source review and build.

## Final Apple release candidate

The final scoped-history correction and removal of temporary broad DOM diagnostics passed the independent delta review. A permanent composer_reset event retains only five bounded readiness/count/error integers. The synchronized Apple 2.6.5 (202609242307) archive passed xcodebuild archive and strict code-signature verification; app, widget, and share extension have matching versions and iPhone/iPad device families. The final archived app was installed without deleting data. Relaunch was attempted but iOS refused because the phone was locked; no final relaunch success is claimed. Stargram commit 9cfd461 contains only the four authorized app files; unrelated user files remain excluded.

The canonical iOS and Android distribution files match their consumer copies. Installed SKILL.md remains byte-identical to skill-source.md (SHA-256 2d3a38032e3e859f5f699bfe74dbe55778f7c2f74ed261dcf39eae0bab6bcc65). No new test suite was added or run. Final broad diagnostic fields and temporary helper references are absent.

## 2026-09-25 00:00 KST — 중간 릴리즈 증거

2026-09-25 00:00:31 KST 기준, AIBI `v0.5.1` 및 Android `v2.6.5` 공개 자산은 다운로드 후 해시 일치를 확인했습니다. Android 공개 APK(SHA-256 `f9754b243000cdbe48bfcc7150c252c67a25f2bb6d5ba53858becd3ff4290d1d`)를 SM-F968N에 `adb install -r`로 데이터 유지 교체 설치하여 `Success`를 확인했고, 공식 런처로 재실행한 뒤 설치 버전 `2.6.5`, `versionCode=384427`, 실행 프로세스 존재를 확인했습니다. 추가 AI 기능 시험은 수행하지 않았습니다. Apple Stargram `2.6.5` 빌드 `202609242307`은 내보내기·업로드가 성공하여 Apple 처리 중입니다. 이 기록은 TestFlight 공개 베타 승인·공개 완료나 홈페이지 반영 완료를 뜻하지 않습니다.

## 2026-09-25 00:05 KST — Apple 외부 베타 심사 제출

팀장이 App Store Connect 화면에서 Stargram `2.6.5` 빌드 `202609242307`의 처리 완료를 확인했습니다. 기존 내부 테스트 그룹의 9명을 유지하고 기존 Public Beta 그룹 8명을 연결하여 총 2개 그룹으로 표시됐습니다. 한국어 테스트 안내를 저장하고 자동 테스터 알림을 선택한 뒤 최종 `심사를 위해 제출`을 실행했습니다. 제출 후 상태는 `심사 대기 중`, 만료 안내는 `90일 후 만료`로 확인됐습니다. 2026-09-25 00:06 KST에 팀장이 기존 공개 초대 링크 `https://testflight.apple.com/join/nzmW4WxW`의 `Stargram 베타에 참여하기` 페이지와 `TestFlight에서 보기` 버튼을 확인했으며, 이용 불가 또는 정원 초과 안내는 없었습니다. 기존 공개 초대 링크의 사용 가능 여부 확인은 심사 대기 중인 새 `2.6.5` 빌드의 승인을 뜻하지 않습니다. 새 `2.6.5` 빌드는 아직 외부 베타 심사 승인 또는 공개 배포가 완료된 상태가 아니며, 이 기록은 Apple 릴리즈 전체 완료를 의미하지 않습니다.
