# Apple AIBI 수명주기 안정화 — 2026-10-17

## 범위

TM이 지시한 대로, 네이티브 Apple AIBI 어댑터 전체에서 취소, 지연 콜백, 거부된 결과/수동 전환,
그리고 WebContent 프로세스 종료 처리를 점검했다. 수정 대상은 다음 4개 파일로 한정했다.

- `packages/apple/AIBIEngine.swift`
- `profiles/imanager/distribution/ios/ExternalAIBrowserView.swift`
- `profiles/denimdex/distribution/apple/AIBISession.swift`
- `profiles/mailtranslator/distribution/macos/AIBIMacEngine.swift`

## 확인된 결함

**WKNavigationDelegate에 `webViewWebContentProcessDidTerminate`가 어디에도 없었다.**

4개 파일 모두 `WKNavigationDelegate`를 구현하면서 `didFail`/`didFailProvisionalNavigation`만
처리하고 있었다. 하지만 WebKit은 WebContent 프로세스가 메모리 압박이나 충돌로 강제 종료될 때
이 두 콜백을 호출하지 않는다 — 공식 전용 콜백은 `webViewWebContentProcessDidTerminate(_:)`뿐이다.

### 실제 영향

- `packages/apple/AIBIEngine.swift`, `profiles/denimdex/distribution/apple/AIBISession.swift`:
  렌더러가 죽은 뒤에도 `stateTimer`/`elapsedTimer`가 계속 돌며 이미 응답할 수 없는 WebView에
  `evaluateJavaScript`를 계속 보낸다. 포털 계약의 "취소는 WebView·타이머·taskId를 즉시 무효화"
  규정과 "119초 유한 상한" 규정이, 프로세스 종료라는 경로에서는 전혀 작동하지 않는다. 사용자는
  멈춘 진행 화면만 보고, 복구 가능한 명확한 실패를 받지 못한다.
- `profiles/imanager/distribution/ios/ExternalAIBrowserView.swift`: 메인 자동화 `Coordinator`가
  종료를 모르면 `bridge.isGenerating`/`isAnswerStable`이 영원히 갱신되지 않아 숨김 실행이
  멈춘 채로 남는다. 로그인 상태 확인용 `Coordinator`(두 번째, 별도 probe)도 같은 문제로
  8초 probe 루프가 자신의 데드라인까지 그냥 공회전만 하게 된다(최종적으로는 `.checking`으로
  빠지긴 하지만, 그사이 죽은 렌더러에 불필요한 polling을 계속한다).
- `profiles/mailtranslator/distribution/macos/AIBIMacEngine.swift`: `AIBINavigationGuard`에
  동일 누락. `AIBIRunner`의 `waitForReadiness`는 `navigationFailed` 플래그를 확인하지만, 그
  플래그를 켜는 유일한 통로였던 `onFailure` 클로저가 프로세스 종료에서는 절대 호출되지 않았다.

## 적용한 수정

4개 파일 모두, 기존 "실패 처리" 경로(타이머 정지 → `generationId`/세대 증가로 무효화 →
명확한 실패 메시지 → 자동 재전송 없음)를 그대로 재사용해 `webViewWebContentProcessDidTerminate`
콜백만 추가했다. 새 기능이나 새 정책을 넣지 않고, 이미 있던 `handleNavError`/`onFailure`/
`markNavigationFinished` 패턴에 공식 콜백 하나를 연결하는 수준으로 최소화했다.

1. `packages/apple/AIBIEngine.swift` (`AIBISession: WKNavigationDelegate` 확장)
   - 종료된 WebView가 현재 `hiddenWebView`/`visibleWebView` 중 하나일 때만 반응하도록 가드.
   - `log("browser_load_failed")` 후 `failWithError(...)`로 기존 네트워크 오류 처리와 동일하게
     taskId 세대를 무효화하고 복구 안내 문구를 표시.

2. `profiles/denimdex/distribution/apple/AIBISession.swift` (동일 패턴, 한국어 호스트 문구)
   - 동일하게 `hiddenWebView`/`visibleWebView` 가드 후 `failWithError(...)`.

3. `profiles/imanager/distribution/ios/ExternalAIBrowserView.swift`
   - 로그인 probe `Coordinator`: `bridge.markNavigationFinished()`만 호출해, probe가 자체
     데드라인 로직으로 `.checking`을 돌려주도록 기존 동작을 그대로 따름(새 정책 추가 없음).
   - 메인 자동화 `Coordinator`: 기존에 모든 실패가 흘러가는 `bridge.detectedError`에 값을 채운
     뒤 `markNavigationFinished()`를 호출해, 이미 있는 `onChange(of: bridge.detectedError)`
     경로(숨김 뷰의 `onError`, 보이는 시트의 `detectedErrorMessage` 배너)를 통해 작업이
     종료되게 함. 재전송·재입력 로직은 그대로 두었다.

4. `profiles/mailtranslator/distribution/macos/AIBIMacEngine.swift` (`AIBINavigationGuard`)
   - 기존 `onFailure?()` 클로저를 그대로 호출. `AIBIRunner`가 이미 그 클로저로
     `navigationFailed = true`를 설정하므로, 이후 `waitForReadiness` 루프가 네트워크 오류와
     동일하게 처리한다.

## 범위 밖으로 남긴 것

- `profiles/mailtranslator/.../AIBIMacEngine.swift`의 `injectAndSubmit`/`observe` 루프는
  `navigationFailed` 플래그를 확인하지 않는다. 이는 일반 `didFail` 오류에도 이미 있던 기존
  구조적 특성이며, 이번 작업에서 요청받은 "프로세스 종료 콜백 누락" 범위를 벗어나는 별도
  리팩터링이라 손대지 않았다. 필요하면 별도 작업으로 TM에 보고한다.
- 선택자, 추출 로직, 세션/쿠키 처리, 진단 스키마는 전혀 건드리지 않았다.

## 검증 상태 (정직한 고지)

- `xcrun swift -frontend -parse`로 4개 파일 모두 문법 오류 없음을 확인했다(타입 검사·빌드
  아님 — Xcode 프로젝트·의존 모듈이 없어 전체 컴파일은 이번 범위에서 수행하지 않았음, TM이
  예정한 직렬 호스트 빌드 단계에서 확인 필요).
- **기기 검증은 하지 않았다.** 실제 iOS/iPadOS/macOS 기기에서 WebContent 프로세스를 강제
  종료시켜 숨김/보이기 모드 각각에서 복구 메시지가 뜨는지는 확인되지 않은 상태이며, 소스
  코드 검토와 기존 실패 경로 재사용에 근거한 수정이다. 디바이스 테스트는 TM이 빌드/설치
  단계에서 별도로 수행해야 한다.
- 테스트 러너·빌드·설치·커밋은 수행하지 않았다(지시대로 TM이 직렬화된 호스트 빌드 이후
  스케줄링).

## 추가 확인된 결함 — iPhone 17 Pro, Stargram 2.6.6 (08:56/08:57/09:00 KST 설치본)

TM이 보고한 기기 증거: Gemini·Claude는 로그인됨으로 뜨지만 ChatGPT는 "확인 중"에서 멈춰
계속 표시된다. 생성 요청이나 개인 콘텐츠는 쓰지 않았다. 원인은
`profiles/imanager/distribution/ios/ExternalAIBrowserView.swift`(수정 전 744~877행)의
로그인 상태 probe에 있었다. 수정 대상은 이 파일과 본 문서 두 개로 한정했다.

1. **세 값만 있던 `ExternalAILoginStatus`가 챌린지/타임아웃/렌더러 종료를 모두 `.checking`으로
   되돌렸다.** 포털 계약은 `확인 중 / 로그인됨 / 로그인 필요 / 확인 안 됨` 네 상태를 요구하는데,
   세 번째 상태(`.unknown`)가 없어 증거 불충분 상황이 영원히 "확인 중"으로만 보였다(ChatGPT가
   본 증상과 일치). `.unknown`을 추가해 `title`="확인 안 됨", `iconName`="questionmark.circle",
   `color`=`.gray`로 다른 세 상태와 구분되게 했다. 8초 데드라인 소진, `hasChallenge`,
   렌더러 종료 세 경로 모두 `.checking` 대신 `.unknown`으로 끝맺도록 바꿨다 — `needsLogin`이나
   `loggedIn`으로 추측하지 않는다는 기존 원칙은 유지했다.
2. **`setStatus`가 `loggedIn` 뒤에 오는 지연 `.checking` 콜백만 막고 있었다.** `.unknown`을
   새로 추가하면서 같은 보호를 `.unknown`에도 적용해, 이미 확인된 로그인 상태가 늦게 도착한
   불확실한 콜백으로 되돌아가지 않게 했다.
3. **probe 뷰가 화면 밖 -10,000pt에 오프셋되어 있었고 부모 프레임이 0×0이었다.** 포털 계약은
   "실제 뷰 계층과 실제 화면 좌표에 기준 크기로 붙이고, 앱의 불투명 표면 뒤에 거의 투명하게
   둔다"와 "0×0, GONE, unattached, 수천 px 화면 밖 WebView는 금지"를 명시한다. 오프셋과
   0×0 프레임을 제거하고, `ExternalAILoginStatusProbeView`를 375×667 실제 크기로 두어
   `Color(uiColor: .systemBackground)`를 먼저 그린 뒤 그 위에 `opacity(0.001)`인 probe를
   얹었다. `allowsHitTesting(false)`로 터치는 막혀 있다. 이 뷰 자체에는 설정 화면 전용
   배경 바인딩이 없어(이 프로필 디렉터리에 `ProfileSettingsView`가 존재하지 않음 — 별도
   Stargram 라이브 프로젝트 소스로 추정) 표준 `systemBackground`로 대체했다; 호스트 화면에
   삽입될 때 실제 설정 리스트 배경과 다르면 TM이 호스트 쪽에서 추가로 맞춰야 한다.
4. **렌더러 종료가 8초 데드라인까지 공회전만 했다.** 이전 안정화 작업에서 추가한
   `webViewWebContentProcessDidTerminate`는 `markNavigationFinished()`만 불러 `isPageReady`를
   `true`로 만들 뿐이었고, 죽은 프로세스에 `evaluateJavaScript`를 계속 보내며 데드라인까지
   기다렸다. `ExternalAILoginProbeBridge`에 `rendererTerminated` 플래그를 추가해 같은 콜백에서
   같이 켜고, `runProbe` 루프 맨 앞에서 이 플래그를 확인해 즉시 `.unknown`으로 끝내도록 했다
   (기존 브리지 하나만 재사용, 새 메시징 경로 없음).

### 범위 밖으로 남긴 것

- Provider 선택자·DOM 추출 로직은 그대로 두었다. ChatGPT가 멈춘 원인은 상태 세 값 설계와
  probe 뷰 배치였으며, provider별 인증 판정 스크립트 자체의 결함 증거는 없었다.
- 실제 Stargram 라이브 프로젝트의 `ProfileSettingsView`(이 Mac 저장소 밖)를 읽지 않았다 —
  이번 세션에서는 해당 파일이 이 저장소 어디에도 없음을 확인했을 뿐이다. 배경 일치 여부는
  TM이 호스트 화면에 붙일 때 확인해야 한다.

### 검증 상태 (정직한 고지)

- `xcrun swift -frontend -parse`로 수정한 `ExternalAIBrowserView.swift` 문법 오류 없음을
  확인했다(타입 검사·빌드 아님).
- `ExternalAILoginStatus`를 switch하는 지점은 이 파일 안에 enum 정의 자체(title/iconName/color)
  하나뿐임을 grep으로 확인해, 새 `.unknown` case 추가로 인한 비전수(exhaustive) switch 컴파일
  오류 위험이 없음을 소스 레벨에서 확인했다.
- **기기 재검증은 하지 않았다.** iPhone 17 Pro에 새 빌드를 설치해 ChatGPT 행이 더 이상
  "확인 중"에 멈추지 않고 "확인 안 됨"이나 정상 상태로 끝맺는지, probe 전환 중 화면 깜빡임이나
  터치 간섭이 없는지는 TM이 직렬화된 빌드/재설치 단계에서 확인해야 한다. 기기 증거(로그,
  스크린샷)는 DOM·계정 데이터를 포함하지 않는 한 그대로 TM이 보고해도 되며, 이 세션에서는
  어떤 기기 로그 원문도 생성하거나 저장하지 않았다.
- 빌드·테스트 러너·설치·Git 작업·배포는 수행하지 않았다(지시대로 TM이 변경된 네이티브
  파일만 라이브 프로젝트에 복사해 직렬 빌드/재설치/Git/릴리스를 진행).

## 추가 확인된 결함 — 독립 릴리스 리뷰가 지적한 `runProbe` 잔여 교착 경로

위 4번 수정 이후에도, 8초 데드라인 자체는 루프 바깥에서만 검사되고 있었다. `runProbe`가
`await bridge.checkAuthStatus(provider:)`를 호출하는 동안에는 그 `await`가 끝나기 전까지
루프로 되돌아오지 않으므로 데드라인도 `rendererTerminated`도 확인할 수 없었다. 그런데
`checkAuthStatus` 내부의 `evaluateJavaScript`는 개별 호출 단위의 유한한 완료 보장이 없는
`try await` 하나뿐이었다 — 렌더러가 멈춰 completion handler가 영원히 오지 않으면(크래시로
간주되지 않는 "응답 없음" 상태), `await`가 끝나지 않아 바깥 루프가 그 한 번의 호출에 영원히
묶이는 경로가 남아 있었다.

### 적용한 수정

`ExternalAILoginProbeBridge.checkAuthStatus(provider:)`가 호출하던 `try await
webView.evaluateJavaScript(script)`를 새 private 정적 메서드
`evaluateBounded(_:in:timeout:)`로 대체했다(둘 다 같은 파일, `checkAuthStatus` 바로 아래에
추가). 이 메서드는:

- `webView.evaluateJavaScript(_:completionHandler:)`(콜백 기반 오버로드)와
  `DispatchQueue.main.asyncAfter(deadline: .now() + 5)`를 하나의 `withCheckedContinuation`
  안에서 동시에 등록해 경쟁시킨다. 둘 다 메인 스레드에서 실행되는 `@MainActor` 클래스의
  코드이므로, 콜백은 `MainActor.assumeIsolated`로 격리를 명시한다.
- `FinishGuard` 클래스(단일 `finished` 불값)로 "한 번만 resume"을 보장한다. JS completion이
  먼저 오면 그 결과로 끝내고 타이머를 무시하게 막으며, 타이머가 먼저 울리면(5초 경과)
  `authenticated/hasLogin/hasChallenge` 모두 `false`인 결과로 끝내고 이후 도착하는 JS
  콜백은 `guard !guardBox.finished else { return }`로 조용히 버린다 — 늦게 도착한 completion이
  이미 끝난 continuation을 다시 resume하려는 크래시나 상태 오염을 막는다.
- 5초는 probe 전체 예산(8초) 안에 들어가며, 타임아웃 결과(`false/false/false`)는 `runProbe`의
  기존 분기(`hasLogin`/`hasChallenge` 모두 false면 다음 루프 반복으로 진행)를 그대로 타므로,
  루프가 되돌아와 데드라인과 `rendererTerminated`를 다시 검사할 수 있게 된다. 이 결과 하나만으로
  바로 `.unknown`을 반환하지는 않는다 — 렌더러가 실제로는 살아 있고 느릴 뿐인 경우까지
  `.needsLogin`/`.loggedIn`으로 잘못 추측하지 않으면서도 섣불리 끝내지 않기 위함이며, 정말
  정체된 경우에는 8초 전체 데드라인 소진이나 `rendererTerminated` 플래그가 최종적으로
  `.unknown`으로 끝맺는다(로그아웃으로 추측하지 않는다는 기존 원칙 유지).
- `withThrowingTaskGroup`/`Task { }`로 두 개의 비협조적 비동기 작업을 "경쟁"시키는 방식은
  쓰지 않았다 — Swift task 취소는 협조적이라 JS evaluation 자체가 멈추거나 끝나지 않으며,
  그런 구조는 바깥 호출자만 반환시킬 뿐 내부적으로 completion/타이머 경쟁과 "한 번만 resume"
  보장을 동시에 주지 못한다. 대신 completion-handler API와 명시적 타이머를 같은 continuation
  안에서 직접 경쟁시켜 평가 자체를 유한하게 만들었다.
- 선택자, DOM 추출, 로그, 개인 데이터 처리는 건드리지 않았다. 양성 로그인 판정 동작(URL
  감지, `hasLogin`/`hasChallenge`/`authenticated` 해석), stale callback 로그인 보호
  (`setStatus`의 `loggedIn` 고정), 실제 375×667 화면 부착 숨김 probe 배치는 모두 그대로다.

### 검증 상태 (정직한 고지)

- `xcrun swift -frontend -parse`로 수정한 `ExternalAIBrowserView.swift` 문법 오류 없음을
  다시 확인했다(타입 검사·빌드 아님).
- **기기 재검증은 하지 않았다.** 렌더러가 멈춘 상태에서 `checkAuthStatus`가 실제로 5초 안에
  빠져나와 probe가 `.unknown`으로 끝맺는지는 TM이 직렬화된 빌드/재설치 단계에서 기기로
  확인해야 한다.
- 빌드·테스트 러너·설치·Git 작업·배포는 수행하지 않았다.
