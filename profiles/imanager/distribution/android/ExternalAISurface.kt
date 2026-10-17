package com.armsone.imanagerai.ui.externalai

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.armsone.imanagerai.design.BrandTheme
import com.armsone.imanagerai.design.LocalAppAppearance
import com.armsone.imanagerai.model.AppAppearance
import com.armsone.imanagerai.service.DirectAIProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ExternalAISurface(
    provider: DirectAIProvider,
    mode: ExternalAISurfaceMode,
    prompt: String = "",
    attachments: List<ExternalAIAttachment> = emptyList(),
    fallbackReason: ExternalAIFallbackReason? = null,
    onClose: () -> Unit,
    onLoginSuccess: () -> Unit = {},
    onImport: (String) -> Unit = {},
    onSubmitted: () -> Unit = {},
    onError: (String?) -> Unit = {},
    autoImportOnComplete: Boolean = true,
    appearance: AppAppearance = LocalAppAppearance.current
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val diagnostics = remember { ExternalAIDiagnosticsStore(context) }
    val diagnosticRun = remember(provider, prompt) { if (mode.isLogin) null else diagnostics.start(provider.name) }
    var taskCancelled by remember { mutableStateOf(false) }
    var hasDispatchedPrompt by remember { mutableStateOf(false) }
    var taskInitialized by remember { mutableStateOf(false) }
    var baselineForSubmission by remember { mutableStateOf(0) }
    fun record(event: String, metrics: Map<String, Int> = emptyMap()) {
        diagnosticRun?.let { diagnostics.record(it, event, metrics) }
    }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var rendererDead by remember { mutableStateOf(false) }
    var popupWebViewRef by remember { mutableStateOf<WebView?>(null) }
    var activeFallbackReason by remember(fallbackReason) { mutableStateOf(fallbackReason) }
    var detectedFallbackReason by remember { mutableStateOf<ExternalAIFallbackReason?>(null) }
    val effectiveFallbackReason = activeFallbackReason ?: detectedFallbackReason
    var hasClosedOnAuth by remember { mutableStateOf(false) }

    fun handlePositiveAuthSuccess() {
        if (hasClosedOnAuth) return
        hasClosedOnAuth = true
        activeFallbackReason = null
        detectedFallbackReason = null
        CookieManager.getInstance().flush()
        onLoginSuccess()
        onClose()
    }

    var pageProgress by remember { mutableFloatStateOf(0f) }
    var isPageReady by remember { mutableStateOf(false) }
    var isAutoFilling by remember { mutableStateOf(false) }
    var hasFilledPrompt by remember { mutableStateOf(false) }
    var fillFailed by remember { mutableStateOf(false) }
    var hasSubmittedPrompt by remember { mutableStateOf(false) }
    var submitFailed by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var latestAnswer by remember { mutableStateOf("") }
    var extractedAnswer by remember { mutableStateOf<String?>(null) }
    var hasImportedAnswer by remember { mutableStateOf(false) }
    var detectedErrorMessage by remember { mutableStateOf<String?>(null) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var stabilityState by remember { mutableStateOf(ExternalAIStabilityState()) }
    var navigationGeneration by remember { mutableStateOf(0) }
    var nativeAttachmentBatch by remember { mutableStateOf(ExternalAINativeAttachmentBatch.EMPTY) }
    var nativeAttachmentPreparationFailed by remember { mutableStateOf(false) }
    var hasAttachedBatch by remember(attachments) { mutableStateOf(attachments.isEmpty()) }

    LaunchedEffect(attachments) {
        nativeAttachmentPreparationFailed = false
        record("media_preparation_started", mapOf("expected_count" to attachments.size))
        runCatching {
            withContext(Dispatchers.IO) {
                ExternalAINativeAttachmentBatch.prepare(context, attachments)
            }
        }.onSuccess {
            nativeAttachmentBatch = it
            record("media_prepared", mapOf("prepared_count" to it.uris.size))
        }.onFailure {
            nativeAttachmentPreparationFailed = true
            record("media_preparation_failed")
        }
    }
    val batchForDisposal = nativeAttachmentBatch
    DisposableEffect(batchForDisposal) {
        onDispose { batchForDisposal.dispose() }
    }

    fun copyPromptToClipboard() {
        if (prompt.isNotBlank()) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Stargram Prompt", prompt))
        }
    }

    fun importAnswer(text: String) {
        if (hasImportedAnswer || taskCancelled) return
        val cleaned = ExternalAIAnswerCleaner.clean(text, provider)
        if (cleaned.isBlank()) return
        hasImportedAnswer = true
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Stargram Result", cleaned))
        record("result_applied", mapOf("response_length" to cleaned.length))
        record("run_completed")
        onImport(cleaned)
        onClose()
    }

    // One dispatch per task. Every subsequent pass only observes server generation evidence.
    suspend fun submitPromptWhenReady(wv: WebView, baselineCount: Int): Boolean {
        if (hasSubmittedPrompt) return true
        val started = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - started < 15_000L && !taskCancelled) {
            currentCoroutineContext().ensureActive()
            if (!hasDispatchedPrompt) {
                hasDispatchedPrompt = ExternalAIScripts.parseSubmissionDispatched(
                    wv.evaluateForAIBI(ExternalAIScripts.submissionStateScript())
                )
                if (!hasDispatchedPrompt) {
                    hasDispatchedPrompt = wv.dispatchAIBISubmitOnce(provider, attachments.size) { hasDispatchedPrompt = true }
                    if (hasDispatchedPrompt) record("send_attempted", mapOf("attempt" to 1))
                }
            }
            delay(700L)
            if (taskCancelled) return false
            diagnosticRun?.let { wv.drainAIBIDiagnostics(provider, diagnostics, it) }
            val verified = wv.evaluateForAIBI(ExternalAIScripts.verifySubmissionScript(provider, baselineCount))
            if (ExternalAIScripts.parseSubmissionVerified(verified)) {
                hasSubmittedPrompt = true
                isGenerating = true
                record("generation_started")
                onSubmitted()
                return true
            }
        }
        submitFailed = true
        taskCancelled = true
        wv.evaluateJavascript(ExternalAIScripts.cancelTaskScript(), null)
        record("send_timeout")
        record("run_failed")
        detectedErrorMessage = "전송 후 답변 시작을 확인하지 못했어요. 취소 후 다시 시도하거나 설정에서 진단 로그를 공유해 주세요."
        onError(detectedErrorMessage)
        return false
    }

    // 프롬프트 입력 시도
    suspend fun fillPrompt(wv: WebView, force: Boolean): Boolean {
        if (taskCancelled || hasDispatchedPrompt) return false
        val script = ExternalAIScripts.injectPromptScript(provider, prompt, force = force)
        val result = suspendCancellableCoroutine<String?> { cont ->
            wv.evaluateJavascript(script) { if (cont.isActive) cont.resume(it) }
        }
        val injection = ExternalAIScripts.parseInjectionResult(result)
        return if (injection.success && injection.inputFound) {
            hasFilledPrompt = true
            record("prompt_inserted", mapOf("prompt_length" to prompt.length))
            fillFailed = false
            wv.evaluateJavascript("if(document.activeElement){document.activeElement.blur();}", null)
            wv.clearFocus()
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(wv.windowToken, 0)
            if (activeFallbackReason == ExternalAIFallbackReason.MANUAL_INPUT_REQUIRED) {
                activeFallbackReason = null
            }
            if (detectedFallbackReason == ExternalAIFallbackReason.MANUAL_INPUT_REQUIRED) {
                detectedFallbackReason = null
            }
            true
        } else {
            if (force) fillFailed = true
            false
        }
    }

    // LOGIN 모드: 내비게이션 완료 및 늦은 하이드레이션 중 긍정적 인증 상태를 감지하여 자동 닫기
    LaunchedEffect(mode, navigationGeneration, isPageReady, taskCancelled) {
        if (!mode.isLogin || hasClosedOnAuth || !isPageReady || taskCancelled) return@LaunchedEffect
        val wv = webViewRef ?: return@LaunchedEffect

        val currentUrl = wv.url
        if (!ExternalAISecurityPolicy.canInjectScript(currentUrl, provider)) {
            return@LaunchedEffect
        }

        if (detectedFallbackReason == ExternalAIFallbackReason.LOGIN_REQUIRED) {
            detectedFallbackReason = null
        }
        if (activeFallbackReason == ExternalAIFallbackReason.LOGIN_REQUIRED) {
            activeFallbackReason = null
        }

        val startTime = System.currentTimeMillis()
        while (isActive && !hasClosedOnAuth && !taskCancelled && webViewRef === wv && System.currentTimeMillis() - startTime < 45_000L) {
            val checkUrl = wv.url
            if (!ExternalAISecurityPolicy.canInjectScript(checkUrl, provider)) {
                break
            }

            val authScript = ExternalAIScripts.checkAuthStatusScript(provider)
            val authRes = suspendCancellableCoroutine<String?> { cont ->
                wv.evaluateJavascript(authScript) { if (cont.isActive) cont.resume(it) }
            }

            if (taskCancelled || webViewRef !== wv) return@LaunchedEffect

            val check = ExternalAIScripts.parseAuthCheckResult(authRes)
            if (check.authenticated) {
                handlePositiveAuthSuccess()
                return@LaunchedEffect
            } else if (check.hasChallenge) {
                detectedFallbackReason = ExternalAIFallbackReason.SECURITY_VERIFICATION
            } else if (check.hasLogin) {
                detectedFallbackReason = ExternalAIFallbackReason.LOGIN_REQUIRED
            } else {
                if (detectedFallbackReason == ExternalAIFallbackReason.LOGIN_REQUIRED) {
                    detectedFallbackReason = null
                }
            }

            delay(500L)
        }
    }

    // 메인 프레임 내비게이션 완료 시 자동 채우기 및 전송 루프 실행
    LaunchedEffect(navigationGeneration, nativeAttachmentBatch.uris.size, nativeAttachmentPreparationFailed, taskCancelled) {
        if (mode.isLogin || taskCancelled || (prompt.isBlank() && attachments.isEmpty()) || hasSubmittedPrompt || hasImportedAnswer) return@LaunchedEffect
        if (nativeAttachmentPreparationFailed) {
            fillFailed = true
            return@LaunchedEffect
        }
        if (attachments.isNotEmpty() && nativeAttachmentBatch.uris.size != attachments.size) return@LaunchedEffect
        nativeAttachmentBatch.resetDelivery()
        val wv = webViewRef ?: return@LaunchedEffect
        if (!ExternalAISecurityPolicy.canInjectScript(wv.url, provider)) return@LaunchedEffect
        if (!taskInitialized) {
            wv.evaluateForAIBI(ExternalAIScripts.beginTaskScript(attachments.size))
            wv.evaluateForAIBI(ExternalAIScripts.installDiagnosticsScript())
            taskInitialized = true
            record("browser_loaded")
        }
        if (hasDispatchedPrompt) {
            submitPromptWhenReady(wv, baselineForSubmission)
            return@LaunchedEffect
        }
        isAutoFilling = true
        fillFailed = false
        val startTime = System.currentTimeMillis()
        var baselineCaptured = false
        var baselineCount = 0
        var attachmentHandled = attachments.isEmpty() || hasAttachedBatch

        if (!attachmentHandled) {
            record("attachment_started", mapOf("expected_count" to attachments.size))
            attachmentHandled = attachOrderedPhotosToProvider(wv, provider, attachments)
            if (!attachmentHandled) {
                record("attachment_failed")
                record("manual_takeover")
                activeFallbackReason = ExternalAIFallbackReason.ATTACHMENT_FAILED
                detectedFallbackReason = ExternalAIFallbackReason.ATTACHMENT_FAILED
                isAutoFilling = false
                fillFailed = true
                return@LaunchedEffect
            }
            hasAttachedBatch = true
            record("attachment_ready", mapOf("attached_count" to attachments.size))
        }

        while (isActive && !taskCancelled && webViewRef === wv && System.currentTimeMillis() - startTime < 45_000L && !hasSubmittedPrompt && !hasImportedAnswer) {
            if (isPageReady) {
                if (!baselineCaptured) {
                    val baselineScript = ExternalAIScripts.recordBaselineScript(provider)
                    val baselineRes = suspendCancellableCoroutine<String?> { cont ->
                        wv.evaluateJavascript(baselineScript) { if (cont.isActive) cont.resume(it) }
                    }
                    baselineCount = ExternalAIScripts.parseBaselineCount(baselineRes)
                    baselineForSubmission = baselineCount
                    baselineCaptured = true
                }

                if (attachmentHandled || attachments.isEmpty()) {
                    if (fillPrompt(wv, force = false)) {
                        delay(350L)
                        if (submitPromptWhenReady(wv, baselineCount)) {
                            isAutoFilling = false
                            return@LaunchedEffect
                        }
                        isAutoFilling = false
                        return@LaunchedEffect
                    }
                }
            }
            delay(700L)
        }

        isAutoFilling = false
        if (!hasSubmittedPrompt) {
            fillFailed = true
        }
    }

    // 답변 관찰 루프
    LaunchedEffect(hasSubmittedPrompt, navigationGeneration, taskCancelled) {
        if (mode.isLogin || !hasSubmittedPrompt || hasImportedAnswer || taskCancelled) return@LaunchedEffect
        val wv = webViewRef ?: return@LaunchedEffect
        if (!ExternalAISecurityPolicy.canInjectScript(wv.url, provider)) return@LaunchedEffect
        stabilityState = stabilityState.reset()

        while (isActive && !hasImportedAnswer && !taskCancelled && webViewRef === wv) {
            delay(700L)

            if (taskCancelled || webViewRef !== wv) return@LaunchedEffect
            diagnosticRun?.let { wv.drainAIBIDiagnostics(provider, diagnostics, it) }
            if (taskCancelled || webViewRef !== wv) return@LaunchedEffect
            val errorScript = ExternalAIScripts.extractErrorScript()
            val errorRes = suspendCancellableCoroutine<String?> { cont ->
                wv.evaluateJavascript(errorScript) { if (cont.isActive) cont.resume(it) }
            }
            if (taskCancelled || webViewRef !== wv) return@LaunchedEffect
            val domError = ExternalAIScripts.parseErrorResult(errorRes)
            if (domError.hasError && !domError.error.isNullOrBlank()) {
                val sanitized = ExternalAIErrorSanitizer.sanitize(domError.error, provider)
                record("generation_failed")
                taskCancelled = true
                wv.evaluateJavascript(ExternalAIScripts.cancelTaskScript(), null)
                detectedErrorMessage = sanitized
                isGenerating = false
                onError(sanitized)
                return@LaunchedEffect
            }

            val script = ExternalAIScripts.extractAnswerScript(provider)
            val answerRes = suspendCancellableCoroutine<String?> { cont ->
                wv.evaluateJavascript(script) { if (cont.isActive) cont.resume(it) }
            }
            if (taskCancelled || webViewRef !== wv) return@LaunchedEffect
            val poll = ExternalAIScripts.parsePollResult(answerRes)
            isGenerating = poll.generating

            if (poll.text.isNotBlank()) {
                latestAnswer = poll.text
            }

            val nextStability = ExternalAIStabilityReducer.step(stabilityState, poll)
            stabilityState = nextStability

            if (nextStability.isStable && nextStability.stableAnswer != null) {
                val stableText = nextStability.stableAnswer
                extractedAnswer = stableText
                if (autoImportOnComplete && !hasImportedAnswer) {
                    importAnswer(stableText)
                    return@LaunchedEffect
                }
            }
        }
    }

    // 남은 시간 역카운터
    LaunchedEffect(hasSubmittedPrompt, hasImportedAnswer, taskCancelled) {
        if (hasSubmittedPrompt && !hasImportedAnswer && !taskCancelled) {
            elapsedSeconds = 0L
            while (isActive && !hasImportedAnswer && !taskCancelled) {
                delay(1000L)
                elapsedSeconds += 1L
                if (elapsedSeconds >= ExternalAITimerFormatter.GENERATION_TIMEOUT_SECONDS) {
                    val message = "1분 59초 동안 답변이 없어서 중단했어요. 다시 시도해 주세요."
                    record("generation_failed")
                    taskCancelled = true
                    webViewRef?.evaluateJavascript(ExternalAIScripts.cancelTaskScript(), null)
                    detectedErrorMessage = message
                    onError(message)
                    onClose()
                    break
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler {
            val wv = webViewRef
            if (wv != null && wv.canGoBack()) {
                wv.goBack()
            } else {
                onClose()
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(BrandTheme.settingsBackground(appearance))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 상단 바
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .background(BrandTheme.settingsSectionBackground(appearance))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("externalai.close")
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "닫기",
                        tint = BrandTheme.labelPrimary(appearance)
                    )
                }

                Spacer(Modifier.width(4.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        if (mode.isLogin) "${provider.title} 로그인" else "${provider.title}에서 만들기",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandTheme.labelPrimary(appearance),
                        maxLines = 1
                    )
                    Text(
                        statusSubtitleText(
                            mode = mode,
                            detectedErrorMessage = detectedErrorMessage,
                            hasImportedAnswer = hasImportedAnswer,
                            isGenerating = isGenerating,
                            hasSubmittedPrompt = hasSubmittedPrompt,
                            latestAnswer = latestAnswer,
                            isPageReady = isPageReady,
                            hasFilledPrompt = hasFilledPrompt,
                            submitFailed = submitFailed,
                            isAutoFilling = isAutoFilling,
                            fillFailed = fillFailed,
                            elapsedSeconds = elapsedSeconds
                        ),
                        fontSize = 12.sp,
                        color = if (detectedErrorMessage != null) BrandTheme.red else BrandTheme.labelSecondary(appearance),
                        maxLines = 1
                    )
                }

                if (!mode.isLogin) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (latestAnswer.isNotBlank() && !hasImportedAnswer) {
                            ActionButton(
                                title = "가져오기",
                                icon = Icons.Outlined.FileDownload,
                                testTag = "externalai.import",
                                appearance = appearance,
                                onClick = { importAnswer(latestAnswer) }
                            )
                        }
                        ActionButton(
                            title = "취소",
                            icon = Icons.Filled.Close,
                            testTag = "externalai.cancel",
                            appearance = appearance,
                            onClick = {
                                taskCancelled = true
                                webViewRef?.evaluateJavascript(ExternalAIScripts.cancelTaskScript(), null)
                                record("run_cancelled")
                                onClose()
                            }
                        )
                        ActionButton(
                            title = "문구 복사",
                            icon = Icons.Filled.ContentCopy,
                            testTag = "externalai.copyPrompt",
                            appearance = appearance,
                            onClick = { copyPromptToClipboard() }
                        )
                    }
                }
            }

            if (pageProgress in 0.01f..0.99f) {
                LinearProgressIndicator(
                    progress = { pageProgress },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = BrandTheme.accent
                )
            } else {
                HorizontalDivider(color = BrandTheme.divider(appearance))
            }

            if (!mode.isLogin && hasSubmittedPrompt && !hasImportedAnswer && detectedErrorMessage == null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(BrandTheme.settingsSectionBackground(appearance))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("답변을 기다리는 중", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text(
                            ExternalAITimerFormatter.formatWaitingStatus(elapsedSeconds),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { ExternalAITimerFormatter.progress(elapsedSeconds) },
                        modifier = Modifier.fillMaxWidth(),
                        color = BrandTheme.accent
                    )
                }
            }

            // 폴백 사유 배너
            AnimatedVisibility(visible = effectiveFallbackReason != null) {
                effectiveFallbackReason?.let { reason ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(BrandTheme.settingsSectionBackground(appearance))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("externalai.fallbackBanner"),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = when (reason) {
                                ExternalAIFallbackReason.LOGIN_REQUIRED -> Icons.Filled.Warning
                                ExternalAIFallbackReason.SECURITY_VERIFICATION -> Icons.Filled.Shield
                                ExternalAIFallbackReason.ATTACHMENT_FAILED -> Icons.Filled.Photo
                                else -> Icons.Filled.Warning
                            },
                            contentDescription = null,
                            tint = BrandTheme.accent,
                            modifier = Modifier.size(20.dp).padding(top = 2.dp)
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                reason.bannerText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandTheme.labelPrimary(appearance)
                            )
                            val detailText = if (
                                reason == ExternalAIFallbackReason.ATTACHMENT_FAILED && attachments.isNotEmpty()
                            ) {
                                "화면의 첨부 버튼으로 사진 ${attachments.size}장을 모두 추가하면 이어서 자동으로 진행돼요."
                            } else {
                                reason.detailText
                            }
                            if (detailText.isNotBlank()) {
                                Text(
                                    detailText,
                                    fontSize = 12.sp,
                                    color = BrandTheme.labelSecondary(appearance)
                                )
                            }
                        }
                    }
                }
            }

            // 오류 배너
            AnimatedVisibility(visible = detectedErrorMessage != null) {
                detectedErrorMessage?.let { err ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(BrandTheme.red.copy(alpha = 0.1f))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = BrandTheme.red, modifier = Modifier.size(16.dp))
                        Text(err, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BrandTheme.red, modifier = Modifier.weight(1f))
                    }
                }
            }

            // WebView 표면
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.White)
            ) {
                if (!rendererDead) {
                AndroidView(
                    factory = { ctx ->
                        WebView.setWebContentsDebuggingEnabled(
                            (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                        )
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                setSupportMultipleWindows(true)
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                cacheMode = WebSettings.LOAD_DEFAULT
                                userAgentString = userAgentString.replace("; wv", "")
                            }

                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val targetUrl = request?.url?.toString()
                                    if (!ExternalAISecurityPolicy.isAllowedUrl(targetUrl, provider)) {
                                        return true
                                    }
                                    val urlReason = ExternalAIFallbackClassifier.classifyUrl(targetUrl)
                                    if (urlReason != null) {
                                        detectedFallbackReason = urlReason
                                    } else if (ExternalAISecurityPolicy.isScriptOrigin(targetUrl, provider)) {
                                        if (detectedFallbackReason == ExternalAIFallbackReason.LOGIN_REQUIRED) {
                                            detectedFallbackReason = null
                                        }
                                        if (activeFallbackReason == ExternalAIFallbackReason.LOGIN_REQUIRED) {
                                            activeFallbackReason = null
                                        }
                                    }
                                    return false
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isPageReady = false
                                    detectedErrorMessage = null
                                    val urlReason = ExternalAIFallbackClassifier.classifyUrl(url)
                                    if (urlReason != null) {
                                        detectedFallbackReason = urlReason
                                    } else if (ExternalAISecurityPolicy.isScriptOrigin(url, provider)) {
                                        if (detectedFallbackReason == ExternalAIFallbackReason.LOGIN_REQUIRED) {
                                            detectedFallbackReason = null
                                        }
                                        if (activeFallbackReason == ExternalAIFallbackReason.LOGIN_REQUIRED) {
                                            activeFallbackReason = null
                                        }
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    cookieManager.flush()
                                    isPageReady = true
                                    navigationGeneration += 1
                                    val urlReason = ExternalAIFallbackClassifier.classifyUrl(url)
                                    if (urlReason != null) {
                                        detectedFallbackReason = urlReason
                                    } else if (ExternalAISecurityPolicy.isScriptOrigin(url, provider)) {
                                        if (detectedFallbackReason == ExternalAIFallbackReason.LOGIN_REQUIRED) {
                                            detectedFallbackReason = null
                                        }
                                        if (activeFallbackReason == ExternalAIFallbackReason.LOGIN_REQUIRED) {
                                            activeFallbackReason = null
                                        }
                                    }
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    if (request?.isForMainFrame == true) {
                                        val desc = error?.description?.toString() ?: "연결 실패"
                                        val sanitized = ExternalAIErrorSanitizer.sanitize("Network error: $desc", provider)
                                        detectedErrorMessage = sanitized
                                        onError(sanitized)
                                    }
                                }

                                // Renderer death must be acknowledged with destroy(); leaving the
                                // dead WebView attached/referenced after returning true is undefined
                                // per the WebViewClient contract.
                                override fun onRenderProcessGone(
                                    view: WebView?,
                                    detail: android.webkit.RenderProcessGoneDetail?
                                ): Boolean {
                                    val dead = view ?: return true
                                    // A late callback from an already-replaced/cleared view must not
                                    // clobber the state of a healthy current/replacement WebView.
                                    if (webViewRef !== dead) {
                                        (dead.parent as? ViewGroup)?.removeView(dead)
                                        dead.stopLoading()
                                        dead.destroy()
                                        return true
                                    }
                                    taskCancelled = true
                                    webViewRef = null
                                    rendererDead = true
                                    (dead.parent as? ViewGroup)?.removeView(dead)
                                    dead.stopLoading()
                                    dead.destroy()
                                    val message = "표시 화면이 중단되어 작업을 멈췄어요. 다시 시도해 주세요."
                                    detectedErrorMessage = message
                                    isGenerating = false
                                    onError(message)
                                    return true
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onShowFileChooser(
                                    webView: WebView?,
                                    filePathCallback: android.webkit.ValueCallback<Array<android.net.Uri>>,
                                    fileChooserParams: FileChooserParams
                                ): Boolean {
                                    if (taskCancelled || hasDispatchedPrompt) {
                                        filePathCallback.onReceiveValue(null)
                                        return true
                                    }
                                    return nativeAttachmentBatch.handleFileChooser(filePathCallback, fileChooserParams)
                                }

                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    pageProgress = newProgress / 100f
                                }

                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: android.os.Message?
                                ): Boolean {
                                    if (!isUserGesture) return false
                                    val target = view ?: return false
                                    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                                    popupWebViewRef?.destroy()
                                    val popup = WebView(ctx).apply {
                                        settings.apply {
                                            javaScriptEnabled = true
                                            domStorageEnabled = true
                                            databaseEnabled = true
                                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                            userAgentString = target.settings.userAgentString
                                        }
                                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(
                                                popupView: WebView?,
                                                request: WebResourceRequest?
                                            ): Boolean {
                                                val targetUrl = request?.url?.toString() ?: return true
                                                if (targetUrl == "about:blank") return false
                                                if (ExternalAISecurityPolicy.isAllowedUrl(targetUrl, provider)) {
                                                    target.loadUrl(targetUrl)
                                                } else {
                                                    val message = "허용되지 않은 로그인 페이지예요."
                                                    detectedErrorMessage = message
                                                    onError(message)
                                                }
                                                popupView?.stopLoading()
                                                popupView?.destroy()
                                                if (popupWebViewRef === popupView) popupWebViewRef = null
                                                return true
                                            }

                                            override fun onRenderProcessGone(
                                                popupView: WebView?,
                                                detail: android.webkit.RenderProcessGoneDetail?
                                            ): Boolean {
                                                val dead = popupView ?: return true
                                                if (popupWebViewRef === dead) popupWebViewRef = null
                                                dead.stopLoading()
                                                dead.destroy()
                                                return true
                                            }
                                        }
                                    }
                                    popupWebViewRef = popup
                                    transport.webView = popup
                                    resultMsg.sendToTarget()
                                    return true
                                }
                            }

                            loadUrl(provider.url)
                            webViewRef = this
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("externalai.webview")
                )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!hasImportedAnswer && !taskCancelled) record("run_cancelled")
            taskCancelled = true
            webViewRef?.evaluateJavascript(ExternalAIScripts.cancelTaskScript(), null)
            popupWebViewRef?.let { popup ->
                popup.stopLoading()
                popup.destroy()
            }
            popupWebViewRef = null
            webViewRef?.let { wv ->
                wv.stopLoading()
                wv.destroy()
            }
            webViewRef = null
        }
    }
}

/** Android WebView에 정규화된 사진을 작은 호출로 옮긴 뒤 한 번의 change 이벤트로 커밋한다. */
internal suspend fun attachOrderedPhotosToProvider(
    webView: WebView,
    provider: DirectAIProvider,
    attachments: List<ExternalAIAttachment>,
    timings: ExternalAITimingProfile = ExternalAITimingProfile.DEFAULT
): Boolean {
    currentCoroutineContext().ensureActive()
    if (attachments.isEmpty()) return true
    if (attachments.size !in 1..8) return false
    val ordered = attachments.sortedBy { it.sourceIndex }
    val deadline = System.currentTimeMillis() + timings.attachmentTimeoutMs
    val baselineRaw = webView.evaluateForAIBI(
        ExternalAIScripts.checkAttachmentConfirmedScript(provider, ordered.size)
    )
    val baselineCount = ExternalAIScripts.parseAttachmentPreviewCount(baselineRaw)
    val expectedTotal = baselineCount + ordered.size
    Log.d(
        "AIBIAttachmentCount",
        "provider=${provider.name} baseline=$baselineCount expected=$expectedTotal"
    )

    // ChatGPT may expose a hydrated image input before its attachment menu is opened. Prefer it
    // when available; otherwise open the nested `+ -> Photos` flow without promoting the hidden
    // browser above the opaque host surface.
    var observedCount = baselineCount
    while (observedCount < expectedTotal && System.currentTimeMillis() < deadline) {
        val directInputOpened = if (provider == DirectAIProvider.OPEN_AI) {
            var target = ExternalAIScripts.parseSubmitPoint(
                webView.evaluateForAIBI(ExternalAIScripts.attachmentImageInputTargetScript(provider))
            )
            if (target != null) {
                webView.requestFocusFromTouch()
                webView.dispatchTrustedTap(target)
            } else {
                // Opening ChatGPT's ordinary plus control does not itself invoke a privileged
                // file operation, so the DOM click is safe here. The resulting Photos action
                // is then activated with the trusted Android touch required by WebChromeClient.
                webView.evaluateForAIBI(ExternalAIScripts.openAttachmentTriggerScript(provider))
                delay(350L)
                target = ExternalAIScripts.parseSubmitPoint(
                    webView.evaluateForAIBI(ExternalAIScripts.attachmentMenuActionTargetScript(provider))
                )
                if (target != null) {
                    webView.requestFocusFromTouch()
                    webView.dispatchTrustedTap(target)
                }
            }
            Log.d(
                "AIBIDirectAttachment",
                "provider=${provider.name} inputFound=${target != null} trusted=${target != null}"
            )
            target != null
        } else {
            false
        }

        // Other providers, and future ChatGPT pages without the direct image input, retain the
        // trusted native-touch path because some sites reject HTMLElement.click().
        // Navigation can finish before the provider hydrates its attachment portal. Keep the
        // browser hidden and retry instead of exposing the visible fallback immediately.
        if (!directInputOpened && !webView.openAttachmentChooserWithTrustedTouch(provider)) {
            delay(timings.attachmentCadenceMs)
            continue
        }
        val previousCount = observedCount
        val nativePreviewWaitMs = if (provider == DirectAIProvider.GEMINI) 20_000L else 6_000L
        val nativeStepDeadline = minOf(deadline, System.currentTimeMillis() + nativePreviewWaitMs)
        var lastLoggedCount = observedCount
        while (System.currentTimeMillis() < nativeStepDeadline) {
            val state = webView.evaluateForAIBI(
                ExternalAIScripts.checkAttachmentConfirmedScript(provider, expectedTotal)
            )
            observedCount = ExternalAIScripts.parseAttachmentPreviewCount(state)
            if (observedCount != lastLoggedCount) {
                Log.d(
                    "AIBIAttachmentCount",
                    "provider=${provider.name} observed=$observedCount expected=$expectedTotal"
                )
                lastLoggedCount = observedCount
            }
            if (observedCount == expectedTotal) return true
            if (observedCount > previousCount) break
            delay(timings.attachmentCadenceMs)
        }
        if (observedCount <= previousCount) break
    }

    // Keep the standard DataTransfer path as a provider-compatible fallback.
    var inputReady = false
    while (!inputReady && System.currentTimeMillis() < deadline) {
        val raw = webView.evaluateForAIBI(ExternalAIScripts.prepareAttachmentInputScript(provider))
        inputReady = ExternalAIScripts.parseAttachmentResult(raw).inputFound
        if (!inputReady) delay(timings.attachmentCadenceMs)
    }
    if (!inputReady) return false

    val begun = ExternalAIScripts.parseAttachmentResult(
        webView.evaluateForAIBI(ExternalAIScripts.beginAttachmentBatchScript(ordered.size))
    )
    if (!begun.success) return false
    for ((index, attachment) in ordered.withIndex()) {
        val appended = ExternalAIScripts.parseAttachmentResult(
            webView.evaluateForAIBI(ExternalAIScripts.appendAttachmentToBatchScript(attachment))
        )
        if (!appended.success || appended.acceptedCount != index + 1) return false
    }
    val committed = ExternalAIScripts.parseAttachmentResult(
        webView.evaluateForAIBI(ExternalAIScripts.commitAttachmentBatchScript(provider))
    )
    if (!committed.success || committed.acceptedCount != ordered.size) return false

    while (System.currentTimeMillis() < deadline) {
        val raw = webView.evaluateForAIBI(
            ExternalAIScripts.checkAttachmentConfirmedScript(provider, ordered.size)
        )
        if (ExternalAIScripts.parseAttachmentConfirmed(raw)) {
            Log.d(
                "AIBIAttachmentCount",
                "provider=${provider.name} observed=${ordered.size} expected=${ordered.size} path=data-transfer"
            )
            return true
        }
        delay(timings.attachmentCadenceMs)
    }
    return false
}

internal suspend fun WebView.dispatchAIBISubmitOnce(provider: DirectAIProvider, expectedCount: Int, onClaimed: () -> Unit = {}): Boolean {
    currentCoroutineContext().ensureActive()
    val point = ExternalAIScripts.parseSubmitPoint(evaluateForAIBI(ExternalAIScripts.claimNativeSubmissionScript(provider, expectedCount)))
        ?: return false
    currentCoroutineContext().ensureActive()
    onClaimed()
    dispatchTrustedTap(point)
    return true
}

internal suspend fun WebView.drainAIBIDiagnostics(
    provider: DirectAIProvider,
    store: ExternalAIDiagnosticsStore,
    runID: java.util.UUID
) {
    if (!ExternalAISecurityPolicy.canInjectScript(url, provider)) return
    ExternalAIScripts.parseDiagnosticEvents(evaluateForAIBI(ExternalAIScripts.drainDiagnosticsScript(provider)))
        .forEach { (stage, metrics) -> store.record(runID, stage, metrics) }
}

internal suspend fun WebView.evaluateForAIBI(script: String): String? =
    suspendCancellableCoroutine { continuation ->
        if (!continuation.isActive) return@suspendCancellableCoroutine
        evaluateJavascript(script) { result ->
            if (continuation.isActive) continuation.resume(result)
        }
    }

private suspend fun WebView.openAttachmentChooserWithTrustedTouch(
    provider: DirectAIProvider
): Boolean {
    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
        ?.hideSoftInputFromWindow(windowToken, 0)
    requestFocusFromTouch()
    delay(350L)
    var menuPoint = ExternalAIScripts.parseSubmitPoint(
        evaluateForAIBI(ExternalAIScripts.attachmentMenuActionTargetScript(provider))
    )
    if (menuPoint == null) {
        val triggerPoint = ExternalAIScripts.parseSubmitPoint(
            evaluateForAIBI(ExternalAIScripts.attachmentTriggerTargetScript(provider))
        ) ?: return false
        dispatchTrustedTap(triggerPoint)
        // Give Gemini's animated sheet uninterrupted render windows. Rapid JS polling can
        // keep its portal from laying out until polling stops on some Samsung builds.
        val menuDiscoveryAttempts = when (provider) {
            DirectAIProvider.GEMINI -> 5
            DirectAIProvider.OPEN_AI -> 8
            else -> 6
        }
        for (attempt in 0 until menuDiscoveryAttempts) {
            delay(
                when (provider) {
                    DirectAIProvider.GEMINI -> 1_000L
                    DirectAIProvider.OPEN_AI -> 500L
                    else -> 150L
                }
            )
            menuPoint = ExternalAIScripts.parseSubmitPoint(
                evaluateForAIBI(ExternalAIScripts.attachmentMenuActionTargetScript(provider))
            )
            if (menuPoint != null) {
                Log.d("AIBIMenuTarget", "provider=${provider.name} attempt=${attempt + 1}")
                break
            }
        }
        // A direct file trigger has already invoked WebChromeClient when no nested menu appears.
        if (menuPoint == null) {
            Log.d("AIBIMenuTarget", "provider=${provider.name} not-found attempts=$menuDiscoveryAttempts")
            return true
        }
    }
    dispatchTrustedTap(menuPoint)
    return true
}

private suspend fun WebView.dispatchTrustedTap(point: Pair<Float, Float>) {
    currentCoroutineContext().ensureActive()
    val x = point.first * width
    val y = point.second * height
    val downTime = SystemClock.uptimeMillis()
    val downConsumed = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0).let {
        it.source = InputDevice.SOURCE_TOUCHSCREEN
        val consumed = dispatchTouchEvent(it)
        it.recycle()
        consumed
    }
    try {
        delay(80L)
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, x, y, 0).also {
            dispatchTouchEvent(it)
            it.recycle()
        }
        throw cancelled
    }
    val upConsumed = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0).let {
        it.source = InputDevice.SOURCE_TOUCHSCREEN
        val consumed = dispatchTouchEvent(it)
        it.recycle()
        consumed
    }
    Log.d("AIBITrustedTouch", "x=${point.first} y=${point.second} down=$downConsumed up=$upConsumed")
}

private fun statusSubtitleText(
    mode: ExternalAISurfaceMode,
    detectedErrorMessage: String?,
    hasImportedAnswer: Boolean,
    isGenerating: Boolean,
    hasSubmittedPrompt: Boolean,
    latestAnswer: String,
    isPageReady: Boolean,
    hasFilledPrompt: Boolean,
    submitFailed: Boolean,
    isAutoFilling: Boolean,
    fillFailed: Boolean,
    elapsedSeconds: Long
): String {
    if (mode.isLogin) return "공식 로그인 페이지"
    if (detectedErrorMessage != null) return detectedErrorMessage
    if (hasImportedAnswer) return "답변을 가져왔어요"
    if (isGenerating || hasSubmittedPrompt) {
        return ExternalAITimerFormatter.formatWaitingStatus(elapsedSeconds)
    }
    if (latestAnswer.isNotBlank()) return "답변이 끝나가는 중이에요…"
    if (!isPageReady) return "페이지를 불러오는 중…"
    if (hasFilledPrompt) {
        if (submitFailed) return "자동 전송 실패 · 보내기 버튼을 눌러 주세요"
        return "자동으로 보내는 중…"
    }
    if (isAutoFilling) return "입력창에 자동으로 채우는 중…"
    if (fillFailed) return "채우기 실패 · ⋯ 메뉴에서 다시 넣기나 문구 복사를 써 주세요"
    return "입력창을 기다리는 중…"
}

@Composable
private fun ActionButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    testTag: String,
    appearance: AppAppearance,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .background(BrandTheme.accent.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = BrandTheme.accent, modifier = Modifier.size(14.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BrandTheme.accent)
    }
}
