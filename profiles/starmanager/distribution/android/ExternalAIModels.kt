package com.armsone.starmanager.ui.externalai

enum class ExternalAISurfaceMode {
    LOGIN,
    GENERATION;

    val isLogin: Boolean get() = this == LOGIN
}

/** 외부 AI 인증 상태 (설정 화면 표기 및 프로빙용) */
enum class ExternalAIAuthState(val label: String) {
    CHECKING("확인 중"),
    LOGGED_IN("로그인됨"),
    REQUIRES_LOGIN("로그인 필요"),
    UNKNOWN("확인 안 됨");

    val isChecking: Boolean get() = this == CHECKING
    val isLoggedIn: Boolean get() = this == LOGGED_IN
    val requiresLogin: Boolean get() = this == REQUIRES_LOGIN
}

/** 외부 AI 첨부용 정규화 이미지 데이터 */
data class ExternalAIAttachment(
    val data: ByteArray,
    val mimeType: String = "image/jpeg",
    val filename: String = "aibi-01.jpg",
    val sourceIndex: Int = 0
) {
    val dataUrl: String
        get() {
            val base64 = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
            return "data:$mimeType;base64,$base64"
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ExternalAIAttachment
        return data.contentEquals(other.data) && mimeType == other.mimeType &&
            filename == other.filename && sourceIndex == other.sourceIndex
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + filename.hashCode()
        result = 31 * result + sourceIndex
        return result
    }
}

/** 외부 AI 인증 확인 DOM 스크립트 실행 결과 */
data class ExternalAIAuthCheckResult(
    val success: Boolean,
    val authenticated: Boolean,
    val hasInput: Boolean,
    val hasLogin: Boolean,
    val hasChallenge: Boolean,
    val reason: String? = null
)

enum class ExternalAIStatus(val message: String) {
    IDLE("대기 중"),
    CONNECTING("연결 중…"),
    ATTACHING("사진 첨부 중…"),
    WAITING_FOR_INPUT("입력창 찾는 중…"),
    INPUT_READY("요청문 입력 완료"),
    GENERATING("답변 생성 중…"),
    COMPLETED("생성 완료"),
    ERROR("확인 필요")
}

/** 백그라운드 브라우저 자동화 단계 */
enum class ExternalAIAutomationPhase {
    IDLE,
    CONNECTING,
    ATTACHING,
    SUBMITTED,
    WAITING_ELAPSED,
    COMPLETED,
    FALLBACK_REQUIRED,
    ERROR
}

/** 대화형 웹뷰 폴백 사유 및 사용자 안내 배너 문구 (iPhone ExternalAIFallbackReason과 1:1 일치) */
enum class ExternalAIFallbackReason(
    val bannerText: String,
    val detailText: String = ""
) {
    LOGIN_REQUIRED(
        bannerText = "로그인이 필요해요",
        detailText = "서비스에 로그인하면 자동으로 글을 이어서 써요."
    ),
    SECURITY_VERIFICATION(
        bannerText = "보안 확인이 필요해요",
        detailText = "보안 확인을 마치면 자동으로 글을 이어서 써요."
    ),
    ATTACHMENT_FAILED(
        bannerText = "선택한 사진을 모두 첨부해 주세요",
        detailText = "화면의 첨부 버튼으로 선택한 사진을 모두 추가하면 이어서 자동으로 진행돼요."
    ),
    MANUAL_INPUT_REQUIRED(
        bannerText = "입력창을 찾지 못했어요",
        detailText = "화면에서 로그인이나 입력을 직접 확인해 주세요."
    ),
    MANUAL_CONFIRMATION(
        bannerText = "화면 확인이 필요해요",
        detailText = "화면을 직접 확인하고 필요한 버튼을 눌러 주세요."
    ),
    NAVIGATION_DISALLOWED(
        bannerText = "다른 페이지로 이동했어요",
        detailText = "원래 대화 화면으로 돌아가거나 직접 확인해 주세요."
    ),
    USER_TAKEOVER(
        bannerText = "직접 확인하기",
        detailText = "화면을 직접 확인하고 진행해 주세요."
    )
}

/** AIBI 정규 타이밍 프로필 */
data class ExternalAITimingProfile(
    val readinessTimeoutMs: Long = 35_000L,
    val readinessCadenceMs: Long = 700L,
    val maxReadinessMisses: Int = 12, // 약 8.4초 동안 입력창 미발견 시 폴백
    val attachmentTimeoutMs: Long = 30_000L,
    val attachmentCadenceMs: Long = 350L,
    val submitTimeoutMs: Long = 15_000L,
    val submitCadenceMs: Long = 500L,
    val submitVerificationDelayMs: Long = 700L,
    val visibleAutoFillTimeoutMs: Long = 45_000L,
    val observationCadenceMs: Long = 700L,
    val stabilityRequiredTicks: Int = 2 // 초기 1회 + 추가 2회 일치 = 총 3회 관측 (~1.4초)
) {
    companion object {
        val DEFAULT = ExternalAITimingProfile()
    }
}

data class ExternalAIDomErrorResult(
    val hasError: Boolean,
    val error: String? = null
)

data class ExternalAIInjectionResult(
    val success: Boolean,
    val inputFound: Boolean,
    val submitted: Boolean,
    val isAuthChallenge: Boolean = false,
    val error: String? = null
)

data class ExternalAIAttachmentResult(
    val success: Boolean,
    val inputFound: Boolean = false,
    val acceptedCount: Int = 0,
    val error: String? = null
)

data class ExternalAIReadinessResult(
    val isReady: Boolean,
    val reason: String? = null
)

data class ExternalAIPollResult(
    val newAnswer: Boolean,
    val generating: Boolean,
    val text: String
)

data class ExternalAIStabilityState(
    val consecutiveMatches: Int = 0,
    val lastCleanedText: String = "",
    val isStable: Boolean = false,
    val stableAnswer: String? = null
) {
    fun reset(): ExternalAIStabilityState = ExternalAIStabilityState()

    companion object {
        const val REQUIRED_STABLE_POLLS = 3
    }
}

object ExternalAIStabilityReducer {
    fun step(
        currentState: ExternalAIStabilityState,
        pollResult: ExternalAIPollResult
    ): ExternalAIStabilityState {
        // 새 응답이 아니거나 생성/스트리밍 진행 중인 경우 안정성 카운터 초기화
        if (!pollResult.newAnswer || pollResult.generating) {
            val cleaned = if (pollResult.newAnswer) ExternalAIAnswerCleaner.clean(pollResult.text) else ""
            return currentState.copy(
                consecutiveMatches = 0,
                lastCleanedText = cleaned,
                isStable = false,
                stableAnswer = null
            )
        }

        val cleaned = ExternalAIAnswerCleaner.clean(pollResult.text)
        if (cleaned.isBlank()) {
            return currentState.copy(
                consecutiveMatches = 0,
                lastCleanedText = "",
                isStable = false,
                stableAnswer = null
            )
        }

        return if (cleaned == currentState.lastCleanedText) {
            val newCount = currentState.consecutiveMatches + 1
            val stable = newCount >= ExternalAIStabilityState.REQUIRED_STABLE_POLLS
            currentState.copy(
                consecutiveMatches = newCount,
                lastCleanedText = cleaned,
                isStable = stable,
                stableAnswer = if (stable) cleaned else null
            )
        } else {
            currentState.copy(
                consecutiveMatches = 1,
                lastCleanedText = cleaned,
                isStable = false,
                stableAnswer = null
            )
        }
    }
}
