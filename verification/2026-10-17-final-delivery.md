# 2026-10-17 아이비 최종 전달 기록

확인 시각: 2026-10-17 10:50 KST. 작성: Codex. 운영 원문·계정·비밀값·사용자 요청/답변 본문은 포함하지 않는다.

## 범위와 확인 경계

현재 Mac 프로젝트 소스를 기준으로 AIBI 0.5.4를 개선하고 등록된 앱에 반영했다. 설치된 Codex 아이비 스킬과 공통 자산 8개는 원본과 해시가 같고, 등록 소비자 파일은 모두 current로 확인했다. 별도 테스트 러너는 실행하지 않았다.

실제 iPhone에서 ChatGPT와 합성 사진 한 장의 전체 흐름을 확인했다. Stargram 2.6.7/202610171037은 17.419초에 결과 반영·완료, DenimDex 0.3.6/202610171037은 31.144초에 결과 반영·완료했다. 다른 제공사와 다중 사진, Android 최종 기기의 같은 전체 흐름 성공으로 확대하지 않는다. 기존 실패 기록과 후속 성공을 함께 보존한다.

## 최종 제품별 상태

| 제품 | 최종 버전 / 빌드 | 빌드·설치·실행 | 공개 상태 |
| --- | --- | --- | --- |
| AIBI | 0.5.4 | 소스 ZIP, 설치 스킬+8자산 해시 일치 | v0.5.4 공개. ZIP 실제 재다운로드 HTTP 200·SHA 확인. 홈페이지 API 최신판 확인 |
| Stargram iOS | 2.6.7 / 202610171037 | 빌드·iPhone 데이터 유지 교체 설치·실행. 합성 사진 1장 ChatGPT 전체 흐름 17.419초 확인 | 업로드·VALID·기존 Public Beta 연결·한국어 안내·자동 알림·심사 제출 완료. 내부 IN_BETA_TESTING, 외부 WAITING_FOR_BETA_REVIEW; 외부 승인 대기 |
| Stargram Android | 2.6.9 / 202610171037, code 416797 | 직렬 빌드 22초 성공. 기존 공개 인증서·버전·해시 확인. 최종 기기 오프라인으로 설치 대기 | android-v2.6.9 정식 공개·APK HTTP 200·홈페이지 API 최신판 확인 |
| DenimDex iOS | 0.3.6 / 202610171037 | 빌드·iPhone 데이터 유지 교체 설치·실행. 합성 사진 1장 ChatGPT 전체 흐름 31.144초 확인 | 최종 0.3.6 업로드·VALID·기존 Public Beta 연결·한국어 안내·자동 알림·심사 제출 완료. 내부 IN_BETA_TESTING, 외부 WAITING_FOR_BETA_REVIEW; 외부 승인 대기 |
| DenimDex Android | 0.3.6 / 202610171037, code 416797 | 직렬 빌드 17초 성공. 전용 배포 인증서·버전·해시 확인. 같은 컴파일 결과의 호환 기기 서명본 준비. 최종 기기 오프라인으로 설치 대기 | android-v0.3.6 정식 공개·APK HTTP 200·홈페이지 API 최신판 확인 |
| 바로보기 Mac | 0.7.97 / 202610171037 | 빌드·공증·코드서명·업데이트 Ed25519 검증 완료. 데이터 위치를 보존한 교체 설치·재실행 확인. 본체·Safari 0.7.97/202610171037 일치, 설치 런타임은 공개 DMG와 일치 | v0.7.97 정식 최신판 공개. DMG·appcast·release.json과 다운로드·홈페이지 API 최신판 확인 |
| 바로보기 iOS | 0.8.13 / 내부 빌드 44 | 기존 전용 Google/DeepL 웹 번역 경로의 종료·이동 실패 및 늦은 응답 처리를 보강. 문법·독립 소스 검토·아카이브 빌드와 데이터 유지 설치·재실행·기본 화면 확인 | 업로드·VALID·기존 Public Beta·한국어 안내·자동 알림·심사 제출 완료. 10:50 조회 외부 WAITING_FOR_BETA_REVIEW / 내부 READY_FOR_BETA_TESTING. Git 저장소가 아니므로 소스 ZIP으로 보관; 공통 AIBI 소비자와 구분 |
| 바로보기 Windows | 변경 없음 | 현 프로젝트 소스는 로컬 번역 모델. 공통 AIBI 웹 연결 소비자로 확인되지 않음 | 이번 작업에서 변경·신규 릴리즈하지 않음. 현재 Git 저장소가 아님 |

## 소스 백업과 공개 소스 기준

프로젝트 경로는 작업 대상을 식별하기 위한 로컬 운영 기록이며 공개 ZIP에 이 문서를 포함하지 않는다.

| 프로젝트 | 최종 기록 커밋 직전 HEAD / 확인된 원격 main |
| --- | --- |
| /Users/armsone/git/AIBI | cd655cf2d462460c313d866a345841193be096b9 — 일치 |
| /Users/armsone/git/Stargram | 65bb8484804e39deb7e3d2d45006947bb90903ed — 일치 |
| /Users/armsone/git/Stargram-Android | 13f20b643fad06466afbcec441d2eea12840bb40 — 일치 |
| /Users/armsone/git/DenimDex-iOS | 47ba6d173f21e122d81401ba1ad9092fec37f453 — 일치 |
| /Users/armsone/git/DenimDex-Android | def420c1709c9d7897b620fe8e890e3747e5767d — 일치 |
| /Users/armsone/git/Barobogi-MacOS | 644db9caf36591edcdbfa007f684724f10435ff7 — 일치 |
| /Users/armsone/git/Barobogi-iOS | Git 저장소 아님. 221개 파일의 0.8.13 소스 ZIP과 source-backup.json 보관 |
| /Users/armsone/git/Barobogi-Win | Git 저장소 아님; 커밋/푸시 상태 해당 없음 |

AIBI 공개 ZIP의 원본 소스 커밋은 cd655cf2d462460c313d866a345841193be096b9이다. 이후 확인 기록만의 커밋은 최종 저장소 HEAD를 바꿀 수 있지만 공개 ZIP과 제품 실행 소스는 바꾸지 않는다. 공개 자산을 다시 포장하거나 덮어쓰지 않는다.

기존 사용자 변경을 보존했다. AIBI의 AGENTS 및 미추적 CLAUDE/GEMINI, Stargram의 기존 동기화 기록·산출물, Android의 parity ledger·규칙·산출물, DenimDex iOS의 기존 AGENTS·동기화 기록, 바로보기 Mac의 README·MailToolbarButton·WindowGeometry와 기존 미추적 문서는 이번 커밋에 포함하지 않았다.

## 공개 다운로드 근거

| 제품 | 릴리즈 | 최종 자산 SHA-256 / 바이트 |
| --- | --- | --- |
| AIBI | https://github.com/armsone/AIBI/releases/tag/v0.5.4 | d9dd55163603ebf0b83a8021d8f3d0d2ccb15d638d2770652bcf22f345341c56 / 295747 |
| Stargram Android | https://github.com/armsone/Stargram-Android/releases/tag/android-v2.6.9 | d27c3fbec5e49259a8eddebf6f1b45e3534cb58c1f16a0e97f27bc516fd10178 / 18133339 |
| DenimDex Android | https://github.com/armsone/DenimDex-Android/releases/tag/android-v0.3.6 | b071617798cdbb0d3ed73bb6d7b689ce2e6427a414ec90d9c77eec6b8d328fdf / 48865667 |
| 바로보기 Mac | https://github.com/armsone/Barobogi-MacOS/releases/tag/v0.7.97 | f65c7f0595e7fed593e2ab793fe132e39fb4ae6ee8df5bf997119d834d98d6d8 / 29138885 |

홈페이지는 공개 릴리즈 정보를 자동 연결한다. 과거 Worker→GitHub 조회 403으로 이전 정보가 보였으나 이후 기존 경로로 회복했다. AIBI 0.5.4와 두 Android 최종판, 바로보기 Mac 0.7.97은 운영 API에서 source=github 및 파일·버전·해시 일치를 확인했다. 단순 버전 반영을 위한 홈페이지 소스 수정·배포는 하지 않았다.

## 남은 항목과 데이터 보존

- 10:47:39 iPhone 미러링에서 사용자의 실제 기기 사용 전환을 감지해 폰 UI 조작·실행·설치·종료를 중단했다.
- 합성 파란 사진 1장이 Photos에 남아 있다. 앱 선택은 0/30으로 정리됐지만 Photos 정리는 사용자 기기 사용 때문에 보류했다. 사용자 원본 사진은 변경하지 않았다.
- 확인용 AI 대화는 남아 있다. 타인 원본을 변경하지 않았고 영구 삭제를 시도하지 않았다.
- 임시 HTTP 서버는 종료됐다. 내부 브라우저의 answerProbe3를 포함한 모든 작업 탭을 닫았고 viewport 설정도 복구했다.
- Android 최종 설치·실행은 기기 연결이 없어 대기 상태다. 삭제·인증 변경·반복 재연결은 하지 않았다.
- Stargram·DenimDex·바로보기 iOS 최종 TestFlight 심사 제출은 완료했다. 외부 승인과 새 빌드의 공개 참여 가능 여부는 별도 후속 확인 대상이다.

## 추가 완료 근거

바로보기 Mac의 최종 태그·원격 main·소스 HEAD는 644db9caf36591edcdbfa007f684724f10435ff7로 일치한다. 설치 실행 PID는 4106으로 확인됐다. 기존 OS 설정에서 Gatekeeper 강제 적용이 비활성으로 관찰되어 이를 강제 정책 통과로 확대하지 않는다. 홈페이지 API는 2026-10-17T01:49:08Z에 source=github/0.7.97/빌드 202610171037/해시·크기 일치를 확인했다.

바로보기 iOS는 0.8.12에서 0.8.13/44로 변경했다. 로컬 보관 아카이브는 Release/0.8.13/Barobogi-0.8.13.xcarchive, 소스 보관본은 Release/0.8.13/Barobogi-iOS-0.8.13-source.zip(221개 파일)이며 SHA-256은 018fc35adae559b99135717972328265ed57e56ab59786c4b789756083d6e4b2이다. 새 Git 저장소를 만들지 않았다. 10:50 공식 기존 조회 경로로 VALID, WAITING_FOR_REVIEW, external WAITING_FOR_BETA_REVIEW를 확인했다.

이번 마지막 백업 대상은 이 전달 기록과 2026-10-17-denimdex-attachment-trace.json 두 파일뿐이다. 마지막 기록 커밋의 정확한 해시는 커밋 후 최종 전달 메시지로 보고한다. 이 기록 커밋은 공개 ZIP 원본 커밋 cd655cf2d462460c313d866a345841193be096b9 및 공개 자산을 바꾸지 않는다.
