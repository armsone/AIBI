# Android AIBI 수명주기 안정화 — 2026-10-17

## 범위

TM의 지시 및 Android 수명주기 안정성 점검 요구에 따라, Android AIBI 어댑터 전체에서 취소(cancellation), 지연 콜백(stale callbacks), 1회 전송 보장(submission exactly once), 결과 거부/수동 전환(rejected-result/manual takeover), 그리고 렌더러 프로세스 강제 종료(`onRenderProcessGone`) 처리를 전수 점검하고 수정했다.

허용된 수정 범위는 다음 3개 소스 파일 및 본 검증 문서 1개로 제한되었다.
- `packages/android/AIBIEngine.kt` (AIBI 공통 Android 참조 구현)
- `profiles/denimdex/distribution/android/AIBIEngine.kt` (DenimDex Android 배포 어댑터)
- `profiles/imanager/distribution/android/ExternalAINativeFileBridge.kt` (Stargram/iManager 네이티브 파일 브리지 배포 어댑터)
- `verification/2026-10-17-android-stability.md` (본 검증 기록)

## 점검 및 확인된 결함

### 1. `WebViewClient.onRenderProcessGone` 콜백 누락 (치명적)
- **현상**: `packages/android/AIBIEngine.kt`, `profiles/denimdex/distribution/android/AIBIEngine.kt`의 보안 `WebViewClient` 및 OAuth 팝업용 `WebViewClient` 모두 `onRenderProcessGone`이 전혀 오버라이드되지 않고 기본값(`false` 반환)으로 남아 있었다.
- **영향**: Android 시스템이 OOM(메모리 부족)으로 WebView 렌더러 프로세스를 종료(`detail.didCrash() == false`)시키거나 WebContent 크래시(`detail.didCrash() == true`)가 발생할 때, `onRenderProcessGone`이 `true`를 반환하지 않으면 호스트 앱 전체 프로세스가 즉시 동반 종료(crash)된다. 또한 죽은 WebView 참조를 해제·정리하지 않아 타이머/코루틴이 응답 없는 WebView에 `evaluateJavascript`를 계속 쏘며 메모리 누수와 ANR/행 상태를 유발한다.

### 2. 관찰 루프 유한 119초 상한 누락 및 중복 전송 위험
- **현상**: 전송 확인 후 답변을 관찰하는 `startObservationLoop`에서 `docs/portable-contract.md`에 명시된 119초 상한 강제 종료 로직이 없었음.
- **영향**: 생성 표식이 나타났으나 모델이 응답을 영원히 완료하지 못하거나 멈춘 경우 무한 대기에 빠질 수 있었음.
- **현상**: 전송 요청(`submitPrompt`) 후 응답 시작 확인 대기 중 타임아웃이 발생했을 때, 프롬프트가 이미 소비되었을 가능성이 있음에도 명확한 전송 여부 상태(`submissionDispatched`)를 추적하지 않고 단순 타임아웃 실패 처리되어 이후 재진입 시 중복 전송 위험이 있었음.

### 3. 결과 검증 거부 및 수동 전환 시 대화 손실 (Takeover Continuity 위반)
- **현상**: `completeWithResult`에서 호스트 `resultSink`가 유효성 검사 실패(`.failure`)를 반환하거나 에스컬레이션(`escalateToVisible`) 시, 기존 숨김 WebView를 파괴(`destroyHiddenBrowser()`)하고 새 WebView에 홈페이지(`config.initialUrl`)를 새로 로드하고 있었음.
- **영향**: `docs/portable-contract.md` 75행 명시 계약("A finished answer rejected by the host validator, or a finite extraction timeout, must retain the current conversation during visible takeover. Do not navigate to the provider home page and discard the answer.")을 위반하여 사용자가 작성된 답변이나 대화 문맥을 볼 수 없게 날려버림.

### 4. 파일 패널 콜백 취소 시 행(Hang) 및 지연 콜백 예외
- **현상**: `ExternalAINativeFileBridge.kt`의 `handleFileChooser`에서 nullable 파라미터 방어 및 전송 예외 발생 시 콜백이 처리되지 않고 버려지면 WebView 파일 패널이 멈춰 이후 웹뷰 상호작용이 차단될 수 있었음.

## 적용된 수정 내역

### 1. `WebViewClient.onRenderProcessGone` 안전 처리 구현
- `packages/android/AIBIEngine.kt` 및 `profiles/denimdex/distribution/android/AIBIEngine.kt`:
  - `createSecurityWebViewClient()`에 `onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean` 구현.
  - 진단 이벤트 `browser_load_failed` 기록.
  - 죽은 WebView의 `stopLoading()`, 부모 뷰그룹 제거, `destroy()`를 수행하고 `hiddenWebView` / `visibleWebView` 레퍼런스를 null로 클리어.
  - `detail.didCrash()` 여부에 따른 구체적인 사용자 안내 메시지 생성 후 `failWithError(msg)`로 안전 종료(세대를 증가시켜 진행 중이던 비동기/지연 콜백 즉시 무효화).
  - 자동 재전송/자동 복구 로딩을 하지 않고 `true`를 반환하여 호스트 프로세스 크래시 방지.
  - `onCreateWindow`의 OAuth 팝업용 임시 WebView의 `webViewClient`에도 `onRenderProcessGone`을 구현하여 팝업 프로세스 종료 시 팝업 뷰 정리 후 `true` 반환.

### 2. 1회 전송 보장 및 119초 유한 관찰 타임아웃
- `packages/android/AIBIEngine.kt`의 `AIBITimingProfile`에 `observationTimeoutMs = 119_000L` 추가.
- `AIBISession`에 `submissionDispatched: Boolean` 및 `observationStartTimeMs: Long` 상태 추가.
- `startTask`, `cancelCurrentTask`, `fullReset` 시 `submissionDispatched = false`, `observationStartTimeMs = 0L`로 초기화 및 무효화.
- `submitPrompt` 응답에서 `attempted == true`가 확인되면 즉시 `submissionDispatched = true`로 승격. 전송 대기 타임아웃 발생 시 모호한 재전송을 차단하고 브라우저를 확인하도록 안내.
- `startObservationLoop`에서 `SystemClock.elapsedRealtime() - observationStartTimeMs > 119_000L` 초과 시 `generation_failed` 기록 및 `failWithError`로 정격 종료.

### 3. 결과 거부 및 수동 전환 시 대화 컨텍스트 유지 (Seamless Takeover)
- `completeWithResult`에서 호스트 `resultSink` 검증 실패 시, 브라우저나 세션을 파괴하지 않고 현재 대화와 결과가 유지된 브라우저를 전면에 표시(`presentVisibleBrowser()`).
- `escalateToVisible` 시 숨김 브라우저(`hiddenWebView`)가 존재하면 새 WebView로 교체하지 않고 기존 인스턴스의 레이아웃 파라미터/투명도를 복원하여 `visibleWebView`로 승격(promote)시킴으로써 기존 DOM, 로그인, 작성기 상태를 그대로 유지.

### 4. 파일 패널 콜백 안전성 강화
- `ExternalAINativeFileBridge.kt`:
  - `handleFileChooser(callback: ValueCallback<Array<Uri>>?, parameters: WebChromeClient.FileChooserParams?): Boolean`으로 null 안전성 강화.
  - 콜백 처리 블록을 try-catch로 감싸고 예외 시 `cb.onReceiveValue(null)`을 전달하여 WebView 파일 선택 대기 상태 해제.

## 형제 저장소 호스트(Stargram-Android) 후속 점검 및 필요 조치

- **형제 저장소 파일 위치**: `/Users/armsone/git/Stargram-Android` 내부의 Compose WebView 래퍼 (`StargramWebView.kt` 또는 호스트의 `WebViewClient` 구현체).
- **필요한 후속 수정**:
  - 호스트 Compose `AndroidView(factory = { WebView(context)... })` 또는 커스텀 `AccompanistWebViewClient`/`WebViewClient`에 `onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean` 오버라이드 누락 여부 확인 필요.
  - 렌더러 종료 시 Compose 상태 트리에서 WebView 인스턴스를 즉시 제거하고 `true`를 반환하도록 조치해야 앱 강제 종료를 방지할 수 있음.
  - 이번 마일스톤에서는 규칙에 따라 형제 저장소를 직접 수정하지 않고, 후속 점검 파일 및 정확한 수정 요구사항을 본 문서에 명시함.

## 검증 상태 (정직한 소스 검증 고지)

- **문법 및 시그니처 검증**: 수정된 `packages/android/AIBIEngine.kt`, `profiles/denimdex/distribution/android/AIBIEngine.kt`, `profiles/imanager/distribution/android/ExternalAINativeFileBridge.kt` 전 영역에 대해 괄호 매칭, 오버라이드 시그니처(`onRenderProcessGone`, `onShowFileChooser`), 임포트 누락 여부, 변수 스코프를 소스 레벨에서 전수 대조 확인 완료.
- **기기 테스트 미수행**: 실제 다중 Android 기기(API 24~35)에서의 WebContent 렌더러 프로세스 강제 킬(`adb shell am crash` / low memory killer) 동작은 테스트 러너/직렬 빌드 일정에 따라 TM이 호스트 빌드 후 실기기 테스트를 수행해야 함.
- 본 수정은 테스트 러너 실행, 빌드/설치, Git 커밋/푸시 없이 순수 소스 수정 및 무결성 검증으로 완결됨.

## 후속 통합 수정 — 2026-10-17 (2차, 렌더러/타임아웃 결함 보강)

TM이 1차 수정 중 `ExternalAINativeFileBridge.kt` 변경분(콜백이 `return false` 전에 호출되어 네이티브 소유권이 깨지는 문제, `catch` 블록이 동일 콜백을 중복 호출할 수 있는 문제, 원시 `Throwable` 로깅으로 인한 개인정보 노출 문제)을 반려(reject)·원복(revert)했다. 본 2차 수정에서는 **`ExternalAINativeFileBridge.kt`를 다시 건드리지 않았으며, 베이스라인 상태를 그대로 유지**했다(`git status`로 미변경 확인). 위 "4. 파일 패널 콜백 안전성 강화" 절의 서술은 TM 반려 이후 더 이상 유효하지 않으므로 본 절로 정정 보고한다.

허용 수정 파일: `packages/android/AIBIEngine.kt`, `profiles/denimdex/distribution/android/AIBIEngine.kt`, 본 문서. 두 `AIBIEngine.kt`는 구조가 동일(패키지/부가 설정값만 다름)하여 동일한 결함과 동일한 수정을 양쪽에 적용했다.

### 수정 1 — 결과 거부 시 빈 WebView 생성 결함
- **현상**: `completeWithResult`의 호스트 검증 실패 분기에서 `presentVisibleBrowser()`를 호출하는데, 세션이 숨김(hidden) 상태로 동작 중이었다면 `visibleWebView == null`이라 **완전히 새로운 빈 WebView**를 만들어 사용자가 작성된 답변과 대화 컨텍스트를 전혀 볼 수 없게 되는 결함이 있었다(1차 문서의 "유지된다"는 서술은 부정확했음).
- **수정**: 숨김 WebView를 파괴·재로드 없이 그대로 가시 영역으로 승격(promote)시키는 공용 헬퍼 `promoteHiddenToVisible()`을 신설했다. `packages/android/AIBIEngine.kt`에서는 `escalateToVisible()`과 `completeWithResult()`의 거부 분기가 이 헬퍼를 사용하도록 교체했다. `profiles/denimdex/.../AIBIEngine.kt`의 `presentVisibleBrowser()`는 이미 유사한 승격 로직을 자체 보유하고 있었으므로, 중복 로직을 제거하고 동일한 공용 헬퍼로 통합했다(결과적으로 `completeWithResult`의 거부 분기는 `presentVisibleBrowser()` 호출 그대로 유지되지만, 그 내부가 이제 올바르게 승격하도록 고쳐졌다).

### 수정 2 — 제출/무응답 타임아웃과 관찰 타임아웃이 숨김 대화를 파괴
- **현상**: `startSubmissionLoop`의 "응답 시작 미확인" 타임아웃과 `startObservationLoop`의 119초 관찰 타임아웃이 모두 `failWithError()`를 호출했는데, 이 함수는 `destroyHiddenBrowser()`와 `disposeNativeAttachmentBatch()`를 실행한다. 두 경우 모두 사용자 안내 메시지는 "브라우저에서 대화를 확인하라"고 명시하지만 실제로는 숨김 WebView가 파괴되어 확인할 대상이 사라지는 모순이 있었다.
- **수정**: 신규 `failWithErrorPreservingBrowser(message)`를 추가했다. 세대(generation) 무효화와 진행 중 Job 정지는 동일하게 수행하되, 브라우저를 파괴하지 않고 숨김 상태였다면 `promoteHiddenToVisible()`로 승격시켜 사용자가 실제로 확인할 수 있게 한다. 첨부 미디어는 작업이 완결되는 지점(`failWithError`, `completeWithResult`)에서만 정리하도록 유지했다. 네트워크 오류(`onReceivedError`) 등 기존의 일반 실패 경로는 그대로 `failWithError()`를 사용하도록 두어, 일반 네트워크 실패를 무조건 가시 전환으로 바꾸지 않았다.

### 수정 3 — 승격된 WebView의 접근성/포커스/팝업 지원 복원
- **현상**: 숨김 WebView는 생성 시 `importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO`, `isFocusable = false`로 설정되고 팝업 미지원 `WebChromeClient`(`popupTarget = null`)가 바인딩되어 있었다. 승격 시 이 속성들이 되돌려지지 않아, 승격된 뷰가 접근성 트리에서 숨겨진 채로 남고 터치 포커스를 받지 못하며 팝업(OAuth 등)을 열 수 없는 결함이 있었다.
- **수정**: `promoteHiddenToVisible()`에서 `importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_AUTO`, `isFocusable = true`, `isFocusableInTouchMode = true`, `settings.setSupportMultipleWindows(true)`를 설정하고 `webChromeClient`를 `createAttachmentWebChromeClient(promoted)`로 재바인딩하여 팝업 타겟을 자기 자신으로 지정했다. 기존에 다른 `visibleWebView` 인스턴스가 남아 있었을 경우(승격 대상과 다른 레퍼런스) 누수 없이 `stopLoading/destroy` 후 교체하도록 처리했다.

### 수정 4 — 렌더러 콜백의 소유권 오판 (null-null 매칭)
- **현상**: `onRenderProcessGone(view, detail)`에서 `val isHidden = (view == hiddenWebView)` 형태로 비교했는데, `view`와 `hiddenWebView`가 둘 다 `null`인 경우 `null == null`이 `true`가 되어 소속 없는(stale/unowned) 렌더러 종료 이벤트도 현재 작업 상태로 오판하여 `failWithError`를 호출할 위험이 있었다. 또한 "low memory" 원인을 단정하는 메시지도 부정확했다(`didCrash() == false`가 반드시 저사양 메모리 부족을 의미하지 않음).
- **수정**: `view`가 non-null이고 참조 동일성(`===`)이 성립할 때만 `isHidden`/`isVisible`을 참으로 판정하도록 수정했다. 두 조건이 모두 거짓이면(소속 없는 렌더러) 전달된 죽은 뷰에 대한 `stopLoading/removeView/destroy` 정리만 수행하고 작업 상태(`failWithError`, 진단 기록)는 건드리지 않고 조기 반환한다. 저사양 메모리를 단정하던 메시지는 "Renderer process was terminated unexpectedly."로 일반화했다.

### 수정 5 — 미사용 상태 변수 제거
- **현상**: 1차 수정에서 도입한 `submissionDispatched` 변수는 `true`로 대입만 되고 어디서도 읽히지 않았다(로컬 `dispatched` 변수와 JS 런타임 측 1회성 보장이 이미 중복 방지를 담당). 불필요한 상태로 혼란을 유발할 수 있었다.
- **수정**: `submissionDispatched` 선언 및 모든 대입(`startTask`, `cancelCurrentTask`, `fullReset`, 제출 루프)을 제거했다. 119초 관찰 타임아웃 상한과 세대(generation) 무효화 로직은 그대로 유지했다.

### 수정 6 — `ExternalAINativeFileBridge.kt` 재적용 금지 확인
- TM이 반려한 변경(조기 콜백 호출로 인한 네이티브 소유권 붕괴, `catch`에서 콜백 중복 호출 가능성, 원시 `Throwable` 로깅에 의한 개인정보 노출)은 본 작업에서 **재적용하지 않았다**. 해당 파일은 할당된 수정 범위에 포함되지 않으며, 베이스라인 상태 그대로 남아 있다.

### 검증 상태 (정직한 소스 검증 고지, 2차)
- **범위**: `packages/android/AIBIEngine.kt`, `profiles/denimdex/distribution/android/AIBIEngine.kt`에 대해서만 네이티브 Kotlin 소스 읽기/수정 및 괄호·중괄호 균형, 함수 시그니처, 중복 선언 여부를 수동으로 전수 대조했다.
- **미수행**: 컴파일(`kotlinc`/Gradle), 유닛/인스트루먼트 테스트, 빌드, 배포, Git 커밋/푸시는 수행하지 않았다(지시 범위 밖). `kotlinc`가 로컬 환경에 없어 실제 컴파일 검증은 TM/빌드 파이프라인에서 별도로 수행해야 한다.
- 다른 작업자가 동시에 수정 중인 Stargram Android(Surface/Auth) 관련 파일은 열람·수정하지 않았다.

## 후속 통합 수정 — 2026-10-17 (3차, `evaluateScript` 무한 대기 결함 보강)

릴리스 리뷰어가 2차 수정 이후에도 `performObservationTick` 바깥(예: `drainDiagnostics` 호출부, 첨부 패널/제출/검증 루프의 다수 `evaluateScript` 호출부)에는 119초 관찰 상한이 적용되지 않으며, `evaluateScript`의 `suspendCancellableCoroutine`이 `WebView.evaluateJavascript`의 콜백이 영원히 호출되지 않을 경우(WebView 교착, 렌더러 응답 없음 등) 영구히 재개(resume)되지 않아 `drainDiagnostics`를 포함한 모든 호출부가 영원히 걸릴 수 있다는 결함을 확인·지적했다.

허용 수정 파일: `packages/android/AIBIEngine.kt`, `profiles/denimdex/distribution/android/AIBIEngine.kt`, 본 문서. 기존 1·2차 수정 내용은 모두 보존했다.

### 수정 — `evaluateScript`에 호출 단위 유한 타임아웃 적용
- **현상**: `evaluateScript(webView, script)`는 `suspendCancellableCoroutine`으로만 감싸여 있어, `webView.evaluateJavascript`의 콜백이 호출되지 않으면 해당 코루틴은 취소되지 않는 한 영원히 매달린다. 이 함수는 `performObservationTick`뿐 아니라 `drainDiagnostics`, `ensureRuntimeInjected`, 첨부 패널 준비/스테이징/부착, 제출, 검증 등 거의 모든 호출부에서 공용으로 쓰이므로, 119초 관찰 상한이 적용되지 않는 경로(특히 진단 수집)에서는 영구 행(hang)으로 이어질 수 있었다.
- **수정**: 두 `AIBIEngine.kt`의 `evaluateScript` 본문을 `withTimeoutOrNull(EVALUATE_SCRIPT_TIMEOUT_MS) { suspendCancellableCoroutine { ... } }`로 감쌌다(`EVALUATE_SCRIPT_TIMEOUT_MS = 5_000L`, `AIBISession` 클래스 선언 바로 앞에 파일 전역 `private const val`로 추가). 기존의 Main 스레드 디스패치(`Handler(Looper.getMainLooper()).post`), 지연 콜백 가드(`if (!continuation.isActive) return@...`), 콜백 측 결과 정제(sanitize) 로직은 모두 그대로 보존했다.
- **동작 근거**: `withTimeoutOrNull`은 지정 시간 내 블록이 완료되지 않으면 내부 코루틴(여기서는 `suspendCancellableCoroutine`이 생성한 continuation)을 취소하고 `null`을 반환한다. 취소되면 `continuation.isActive`가 `false`가 되므로, 이후 `evaluateJavascript`의 콜백이 실제로(지연되어) 호출되더라도 기존의 `if (!continuation.isActive) return@evaluateJavascript` 가드에 의해 `continuation.resume`을 시도하지 않고 조기 반환한다 — 즉 "비협조적인 구조적 자식 대기(cooperative cancellation에 의존하지 않는 await)" 없이, 호출자 쪽에서 타임아웃으로 안전하게 손을 떼는 방식이다. 자동 재전송/재시도는 추가하지 않았다.
- **영향받는 호출부 검증**: `evaluateScript`의 모든 호출부(`drainDiagnostics`, `ensureRuntimeInjected`, `performObservationTick`, 첨부 패널/제출/검증 루프 등)는 이미 `?: return` 또는 `null` 결과를 정상 처리하는 분기를 갖고 있어, 타임아웃으로 `null`이 반환되어도 예외 없이 안전하게 복귀한다. 특히 `drainDiagnostics`는 호출 실패 시 단순히 반환(`?: return`)하므로 진단 수집 실패가 상위 작업(제출/관찰/완료 흐름)의 실패로 전파되지 않는다. 기존 세대(generation) 무효화 가드(`if (generation != currentGeneration) return`) 및 119초 관찰 루프 상한은 그대로 유지되며, 이번 수정으로 각 개별 `evaluateJavascript` 호출이 최대 5초 내에 반드시 반환(성공 결과 또는 `null`)하도록 추가로 제한되어 관찰 루프 등 호출 측 루프가 타임아웃 이후에도 정상적으로 진행(return)할 수 있음을 소스 레벨로 확인했다.

### 검증 상태 (정직한 소스 검증 고지, 3차)
- **범위**: `packages/android/AIBIEngine.kt`, `profiles/denimdex/distribution/android/AIBIEngine.kt`의 `evaluateScript` 함수 및 그 호출부 전체에 대해서만 소스 레벨로 수정·대조했다. 두 파일의 `evaluateScript` 본문과 `EVALUATE_SCRIPT_TIMEOUT_MS` 선언은 동일하게 적용했다.
- **미수행**: 컴파일, 테스트, 빌드, 배포, Git 커밋/푸시는 수행하지 않았다(지시 범위 밖). TM이 영향받는 Android 앱만 재빌드할 예정임을 확인했다.
- `kotlinx.coroutines.*` 임포트가 이미 두 파일에 존재하여 `withTimeoutOrNull` 사용에 추가 임포트가 필요 없음을 확인했다.
