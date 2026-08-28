/**
 * AIBIEngine.kt
 * AIBI — AI Browser Interface (Android Platform Reference)
 *
 * Platform: Android API 24+
 * Framework: Kotlin Coroutines, Android WebView, Jetpack Compose friendly
 * Description: Complete, copy-ready reference implementation of AIBI core orchestrator,
 *              lifecycle state machine, and Android WebView adapter.
 * Date: 2026-08-28
 */

package com.aibi.core

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.util.UUID

// MARK: - Core Enums & Data Models

enum class AIBIPhase(val value: String) {
    IDLE("IDLE"),
    INITIALIZING("INITIALIZING"),
    NAVIGATING("NAVIGATING"),
    READY_CHECKING("READY_CHECKING"),
    ATTACHING_MEDIA("ATTACHING_MEDIA"),
    INJECTING_PROMPT("INJECTING_PROMPT"),
    SUBMITTING("SUBMITTING"),
    GENERATING("GENERATING"),
    STABILIZING("STABILIZING"),
    COMPLETED("COMPLETED"),
    FALLBACK_REQUIRED("FALLBACK_REQUIRED"),
    FAILED("FAILED"),
    CANCELLED("CANCELLED")
}

enum class AIBIFallbackReason(val value: String) {
    AUTH_REQUIRED("AUTH_REQUIRED"),
    SECURITY_CHALLENGE_PRESENTED("SECURITY_CHALLENGE_PRESENTED"),
    NAVIGATION_DISALLOWED("NAVIGATION_DISALLOWED"),
    INPUT_NOT_FOUND("INPUT_NOT_FOUND"),
    ATTACHMENT_FAILED("ATTACHMENT_FAILED"),
    READINESS_TIMEOUT("READINESS_TIMEOUT"),
    USER_INTERVENTION_REQUESTED("USER_INTERVENTION_REQUESTED")
}

enum class AIBIPresentationPreference(val value: String) {
    ALWAYS_VISIBLE("ALWAYS_VISIBLE"),
    VISIBLE_WHEN_NEEDED("VISIBLE_WHEN_NEEDED")
}

data class AIBITask(
    val id: UUID = UUID.randomUUID(),
    val providerId: String,
    val promptText: String,
    val attachments: List<AIBIMediaAttachment> = emptyList(),
    val presentation: AIBIPresentationPreference = AIBIPresentationPreference.VISIBLE_WHEN_NEEDED,
    val forceFill: Boolean = false
)

data class AIBIResult(
    val taskId: UUID,
    val providerId: String,
    val rawText: String,
    val cleanedText: String,
    val isComplete: Boolean
)

data class AIBIProgress(
    val phase: AIBIPhase,
    val elapsedSeconds: Double,
    val statusMessage: String,
    val isWaiting: Boolean
) {
    companion object {
        val initial = AIBIProgress(
            phase = AIBIPhase.IDLE,
            elapsedSeconds = 0.0,
            statusMessage = "Ready",
            isWaiting = false
        )
    }
}

// MARK: - Host Result Sink & Validator Hook

interface AIBIResultSink {
    /**
     * Validates and commits the imported result to the host application.
     * Return true on successful host commit; false retains browser for manual retry.
     */
    fun commitResult(result: AIBIResult): Result<Unit>
}

// MARK: - Provider Configuration Models

data class AIBIProviderSelectors(
    val promptInput: List<String>,
    val submitButton: List<String>,
    val stopButton: List<String>,
    val assistantMessage: List<String>,
    val preCode: List<String>? = null,
    val errorBanner: List<String>,
    val loginIndicator: List<String>,
    val challengeIndicator: List<String>,
    val attachmentInput: List<String> = emptyList(),
    val attachmentTrigger: List<String> = emptyList(),
    val attachmentMenuAction: List<String> = emptyList(),
    val attachmentMenuActionText: List<String> = emptyList(),
    val attachmentPreview: List<String> = emptyList()
)

data class AIBIMediaCapabilities(
    val supportsImages: Boolean = false,
    val maxImagesPerTask: Int = 0,
    val requiresMultipleInputForBatch: Boolean = true
)

data class AIBIProviderConfig(
    val id: String,
    val displayName: String,
    val initialUrl: String,
    val allowedScriptOrigins: List<String>,
    val allowedAuthOrigins: List<String>,
    val selectors: AIBIProviderSelectors,
    val mediaCapabilities: AIBIMediaCapabilities = AIBIMediaCapabilities()
)

// MARK: - Session Configuration & Timers Profile

data class AIBITimingProfile(
    val readinessTimeoutMs: Long = 35_000L,
    val readinessCadenceMs: Long = 700L,
    val maxReadinessMisses: Int = 12, // ~8.4s of consecutive misses
    val attachmentTimeoutMs: Long = 30_000L,
    val attachmentCadenceMs: Long = 350L,
    val submitTimeoutMs: Long = 15_000L,
    val submitCadenceMs: Long = 500L,
    val submitVerificationDelayMs: Long = 700L,
    val visibleAutoFillTimeoutMs: Long = 45_000L,
    val observationCadenceMs: Long = 700L,
    val stabilityRequiredTicks: Int = 2 // 3 matching consecutive observations (~1.4s)
) {
    companion object {
        val default = AIBITimingProfile()
    }
}

// MARK: - AIBISession (Core Orchestrator)

class AIBISession(
    private val context: Context,
    private val runtimeJavaScript: String,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
    val timingProfile: AIBITimingProfile = AIBITimingProfile.default
) {
    private val _currentPhase = MutableStateFlow(AIBIPhase.IDLE)
    val currentPhase: StateFlow<AIBIPhase> = _currentPhase.asStateFlow()

    private val _progress = MutableStateFlow(AIBIProgress.initial)
    val progress: StateFlow<AIBIProgress> = _progress.asStateFlow()

    private val _isVisibleBrowserPresented = MutableStateFlow(false)
    val isVisibleBrowserPresented: StateFlow<Boolean> = _isVisibleBrowserPresented.asStateFlow()

    private val _activeProviderId = MutableStateFlow<String?>(null)
    val activeProviderId: StateFlow<String?> = _activeProviderId.asStateFlow()

    private val _pendingResult = MutableStateFlow<AIBIResult?>(null)
    val pendingResult: StateFlow<AIBIResult?> = _pendingResult.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    var resultSink: AIBIResultSink? = null

    private var activeTask: AIBITask? = null
    private var activeConfig: AIBIProviderConfig? = null
    private var generationId: Long = 0L

    private var hiddenWebView: WebView? = null
    var visibleWebView: WebView? = null
        private set

    private var activeJob: Job? = null
    private var elapsedJob: Job? = null
    private var nativeAttachmentDirectory: File? = null
    private var nativeAttachmentUris: List<Uri> = emptyList()
    private var nativeAttachmentNextSingleIndex = 0
    private var taskStartTimeMs: Long = 0L
    private var consecutiveMisses = 0
    private var submitAttemptCount = 0
    private var baselineAssistantCount = 0
    private var stabilityText: String? = null
    private var stabilityTickCount = 0

    init {
        configureCookieManager()
    }

    private fun configureCookieManager() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        // Third-party cookies can be enabled if required by provider sign-in
    }

    // MARK: - Public Task Entrypoints

    fun startTask(task: AIBITask, providerConfig: AIBIProviderConfig, parentViewGroup: ViewGroup? = null) {
        cancelCurrentTask()

        activeTask = task
        activeConfig = providerConfig
        _activeProviderId.value = task.providerId
        generationId++
        taskStartTimeMs = System.currentTimeMillis()
        _lastErrorMessage.value = null
        _pendingResult.value = null

        if (task.attachments.size > 20 ||
            (task.attachments.isNotEmpty() &&
                (!providerConfig.mediaCapabilities.supportsImages ||
                    task.attachments.size > providerConfig.mediaCapabilities.maxImagesPerTask))) {
            failWithError("Image attachments are not supported for this task.")
            return
        }

        updatePhase(AIBIPhase.INITIALIZING, "Connecting to ${providerConfig.displayName}...")
        startElapsedTimer()

        val currentGen = generationId
        scope.launch {
            val prepared = try {
                withContext(Dispatchers.IO) {
                    prepareNativeAttachmentBatch(task.attachments)
                }
            } catch (_: Exception) {
                if (generationId == currentGen) {
                    failWithError("Could not prepare image attachments.")
                }
                return@launch
            }
            if (generationId != currentGen) {
                prepared.first?.deleteRecursively()
                return@launch
            }
            nativeAttachmentDirectory = prepared.first
            nativeAttachmentUris = prepared.second
            nativeAttachmentNextSingleIndex = 0

            if (task.presentation == AIBIPresentationPreference.ALWAYS_VISIBLE) {
                presentVisibleBrowser()
            } else if (parentViewGroup != null) {
                mountHiddenBrowser(parentViewGroup)
            } else {
                // An unattached WebView is not a valid hidden automation surface.
                presentVisibleBrowser()
            }

            val webView = activeWebView
            updatePhase(AIBIPhase.NAVIGATING, "Loading ${providerConfig.displayName}...")
            webView?.loadUrl(providerConfig.initialUrl)
            scheduleReadinessCheck(currentGen)
        }
    }

    fun manualCopyPrompt() {
        val prompt = activeTask?.promptText ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("AIBI Prompt", prompt)
        clipboard?.setPrimaryClip(clip)
    }

    fun manualImportText(text: String) {
        val task = activeTask ?: return
        val cleaned = cleanOutputLocally(text)
        val result = AIBIResult(
            taskId = task.id,
            providerId = task.providerId,
            rawText = text,
            cleanedText = cleaned,
            isComplete = true
        )
        completeWithResult(result)
    }

    fun cancelCurrentTask() {
        stopAllJobs()
        generationId++
        if (_currentPhase.value != AIBIPhase.IDLE) {
            updatePhase(AIBIPhase.CANCELLED, "Cancelled")
        }
        destroyHiddenBrowser()
        disposeNativeAttachmentBatch()
    }

    fun fullReset() {
        stopAllJobs()
        generationId++
        activeTask = null
        activeConfig = null
        _activeProviderId.value = null
        _pendingResult.value = null
        _lastErrorMessage.value = null
        destroyHiddenBrowser()
        destroyVisibleBrowser()
        disposeNativeAttachmentBatch()
        updatePhase(AIBIPhase.IDLE, "Ready")
    }

    // MARK: - State Machine Transitions & Timers

    private fun updatePhase(phase: AIBIPhase, message: String, isWaiting: Boolean = false) {
        _currentPhase.value = phase
        val elapsed = if (taskStartTimeMs > 0) (System.currentTimeMillis() - taskStartTimeMs) / 1000.0 else 0.0
        _progress.value = AIBIProgress(
            phase = phase,
            elapsedSeconds = elapsed,
            statusMessage = message,
            isWaiting = isWaiting
        )
    }

    private fun startElapsedTimer() {
        elapsedJob?.cancel()
        elapsedJob = scope.launch {
            while (isActive && taskStartTimeMs > 0) {
                delay(500L)
                val elapsed = (System.currentTimeMillis() - taskStartTimeMs) / 1000.0
                _progress.value = AIBIProgress(
                    phase = _currentPhase.value,
                    elapsedSeconds = elapsed,
                    statusMessage = _progress.value.statusMessage,
                    isWaiting = _progress.value.isWaiting
                )
            }
        }
    }

    private fun stopAllJobs() {
        activeJob?.cancel()
        activeJob = null
        elapsedJob?.cancel()
        elapsedJob = null
    }

    // MARK: - Readiness Phase

    private fun scheduleReadinessCheck(generation: Long) {
        activeJob?.cancel()
        consecutiveMisses = 0
        val startTime = System.currentTimeMillis()

        activeJob = scope.launch {
            while (isActive && generationId == generation) {
                if (System.currentTimeMillis() - startTime > timingProfile.readinessTimeoutMs) {
                    escalateToVisible(AIBIFallbackReason.READINESS_TIMEOUT)
                    break
                }

                performReadinessProbe(generation)
                delay(timingProfile.readinessCadenceMs)
            }
        }
    }

    private suspend fun performReadinessProbe(generation: Long) {
        val config = activeConfig ?: return
        val webView = activeWebView ?: return
        ensureRuntimeInjected(webView)

        val configJsonStr = JSONObject(mapOf(
            "selectors" to JSONObject(mapOf(
                "promptInput" to config.selectors.promptInput,
                "submitButton" to config.selectors.submitButton,
                "stopButton" to config.selectors.stopButton,
                "assistantMessage" to config.selectors.assistantMessage,
                "errorBanner" to config.selectors.errorBanner,
                "loginIndicator" to config.selectors.loginIndicator,
                "challengeIndicator" to config.selectors.challengeIndicator
            ))
        )).toString()

        val script = "window.__AIBI_RUNTIME__.checkReadiness($configJsonStr)"
        val rawResult = evaluateScript(webView, script) ?: return
        if (generationId != generation) return

        try {
            val json = JSONObject(rawResult)
            if (!json.optBoolean("success", false)) return
            val data = json.optJSONObject("data") ?: return

            val isReady = data.optBoolean("isReady", false)
            val isLoggedIn = data.optBoolean("isLoggedIn", true)
            val hasChallenge = data.optBoolean("hasChallenge", false)
            val reason = data.optString("reason")

            if (!isLoggedIn) {
                activeJob?.cancel()
                escalateToVisible(AIBIFallbackReason.AUTH_REQUIRED)
                return
            }

            if (hasChallenge) {
                activeJob?.cancel()
                escalateToVisible(AIBIFallbackReason.SECURITY_CHALLENGE_PRESENTED)
                return
            }

            if (isReady) {
                activeJob?.cancel()
                recordBaselineAndInject(generation)
            } else if (reason == "INPUT_NOT_FOUND") {
                consecutiveMisses++
                if (consecutiveMisses >= timingProfile.maxReadinessMisses) {
                    activeJob?.cancel()
                    escalateToVisible(AIBIFallbackReason.INPUT_NOT_FOUND)
                }
            }
        } catch (_: Exception) {
            // Silently continue until deadline
        }
    }

    // MARK: - Injection & Submission Phase

    private fun recordBaselineAndInject(generation: Long) {
        val config = activeConfig ?: return
        val webView = activeWebView ?: return
        val task = activeTask ?: return
        updatePhase(AIBIPhase.INJECTING_PROMPT, "Preparing prompt...")

        scope.launch {
            val configJsonStr = buildConfigJsonString(config)

            // Baseline
            val baselineScript = "window.__AIBI_RUNTIME__.getBaselineState($configJsonStr)"
            val baselineResult = evaluateScript(webView, baselineScript)
            if (baselineResult != null) {
                try {
                    val json = JSONObject(baselineResult)
                    val data = json.optJSONObject("data")
                    baselineAssistantCount = data?.optInt("assistantCount", 0) ?: 0
                } catch (_: Exception) {}
            }

            if (task.attachments.isNotEmpty() && !attachImagesAtomically(webView, config, task, generation)) {
                if (generationId == generation) escalateToVisible(AIBIFallbackReason.ATTACHMENT_FAILED)
                return@launch
            }

            // Inject
            val escapedPrompt = JSONObject.quote(task.promptText)
            val injectScript = "window.__AIBI_RUNTIME__.injectPrompt($configJsonStr, $escapedPrompt, ${task.forceFill})"
            val injectResult = evaluateScript(webView, injectScript)
            if (generationId != generation) return@launch

            if (injectResult != null && JSONObject(injectResult).optBoolean("success", false)) {
                startSubmissionLoop(generation)
            } else {
                escalateToVisible(AIBIFallbackReason.INPUT_NOT_FOUND)
            }
        }
    }

    private suspend fun attachImagesAtomically(
        webView: WebView,
        config: AIBIProviderConfig,
        task: AIBITask,
        generation: Long
    ): Boolean {
        updatePhase(AIBIPhase.ATTACHING_MEDIA, "Attaching ${task.attachments.size} photos...", isWaiting = true)
        val configJsonStr = buildConfigJsonString(config)
        val stateScript = "window.__AIBI_RUNTIME__.getAttachmentState($configJsonStr)"
        val baseline = parseAttachmentPreviewCount(evaluateScript(webView, stateScript)) ?: 0
        val expectedTotal = baseline + task.attachments.size
        nativeAttachmentNextSingleIndex = 0

        val prepareScript = "window.__AIBI_RUNTIME__.prepareAttachmentInput($configJsonStr)"
        evaluateScript(webView, prepareScript)
        delay(700L)
        if (generationId != generation) return false

        var observedCount = baseline
        val nativeOverallDeadline = System.currentTimeMillis() + timingProfile.attachmentTimeoutMs
        while (generationId == generation && observedCount < expectedTotal &&
            System.currentTimeMillis() < nativeOverallDeadline) {
            val panelResult = evaluateScript(
                webView,
                "window.__AIBI_RUNTIME__.openAttachmentPanel($configJsonStr)"
            )
            // Navigation can finish before the provider hydrates its attachment portal. Keep the
            // browser hidden and retry instead of exposing the visible fallback immediately.
            if (panelResult == null || !parseRuntimeSuccess(panelResult)) {
                delay(timingProfile.attachmentCadenceMs)
                continue
            }
            val previousCount = observedCount
            val nativePreviewWaitMs = if (config.id == "gemini") 20_000L else 6_000L
            val nativeStepDeadline = minOf(
                nativeOverallDeadline,
                System.currentTimeMillis() + nativePreviewWaitMs
            )
            while (generationId == generation && System.currentTimeMillis() < nativeStepDeadline) {
                observedCount = parseAttachmentPreviewCount(evaluateScript(webView, stateScript)) ?: 0
                if (observedCount == expectedTotal) return true
                if (observedCount > previousCount) break
                delay(timingProfile.attachmentCadenceMs)
            }
            if (observedCount <= previousCount) break
        }

        val ordered = task.attachments.sortedBy { it.sourceIndex }
        val beginResult = evaluateScript(
            webView,
            "window.__AIBI_RUNTIME__.beginAttachmentBatch($configJsonStr, ${ordered.size})"
        ) ?: return false
        if (!parseRuntimeSuccess(beginResult)) return false
        ordered.forEachIndexed { index, attachment ->
            val imageJson = JSONObject(mapOf(
                "dataUrl" to attachment.dataUrl(),
                "mimeType" to attachment.mimeType,
                "filename" to attachment.filename
            ))
            val staged = evaluateScript(
                webView,
                "window.__AIBI_RUNTIME__.stageAttachment($imageJson, $index)"
            )
            if (staged == null) {
                evaluateScript(webView, "window.__AIBI_RUNTIME__.clearAttachmentBatch()")
                return false
            }
            if (!parseRuntimeSuccess(staged)) {
                evaluateScript(webView, "window.__AIBI_RUNTIME__.clearAttachmentBatch()")
                return false
            }
        }
        val attachResult = evaluateScript(
            webView,
            "window.__AIBI_RUNTIME__.commitAttachmentBatch($configJsonStr)"
        ) ?: return false
        if (!parseRuntimeSuccess(attachResult)) return false

        val deadline = System.currentTimeMillis() + timingProfile.attachmentTimeoutMs
        while (generationId == generation && System.currentTimeMillis() < deadline) {
            val previewCount = parseAttachmentPreviewCount(evaluateScript(webView, stateScript)) ?: 0
            if (previewCount == baseline + task.attachments.size) return true
            delay(timingProfile.attachmentCadenceMs)
        }
        return false
    }

    private fun parseAttachmentPreviewCount(raw: String?): Int? = try {
        JSONObject(raw ?: return null).optJSONObject("data")?.optInt("previewCount")
    } catch (_: Exception) {
        null
    }

    private fun parseRuntimeSuccess(raw: String): Boolean = try {
        JSONObject(raw).optBoolean("success", false)
    } catch (_: Exception) {
        false
    }

    private fun startSubmissionLoop(generation: Long) {
        activeJob?.cancel()
        submitAttemptCount = 1
        val startTime = System.currentTimeMillis()
        updatePhase(AIBIPhase.SUBMITTING, "Sending prompt...")

        activeJob = scope.launch {
            while (isActive && generationId == generation) {
                if (System.currentTimeMillis() - startTime > timingProfile.submitTimeoutMs) {
                    escalateToVisible(AIBIFallbackReason.INPUT_NOT_FOUND)
                    break
                }

                performSubmitAttempt(generation)
                delay(timingProfile.submitCadenceMs + timingProfile.submitVerificationDelayMs)
            }
        }
    }

    private suspend fun performSubmitAttempt(generation: Long) {
        val config = activeConfig ?: return
        val webView = activeWebView ?: return
        val configJsonStr = buildConfigJsonString(config)

        val submitScript = "window.__AIBI_RUNTIME__.submitPrompt($configJsonStr, $submitAttemptCount)"
        evaluateScript(webView, submitScript)
        if (generationId != generation) return

        delay(timingProfile.submitVerificationDelayMs)
        if (generationId != generation) return

        val verifyScript = "window.__AIBI_RUNTIME__.verifySubmission($configJsonStr, $baselineAssistantCount)"
        val verifyResult = evaluateScript(webView, verifyScript)
        if (verifyResult != null) {
            try {
                val json = JSONObject(verifyResult)
                val data = json.optJSONObject("data")
                if (data?.optBoolean("submitted", false) == true) {
                    activeJob?.cancel()
                    startObservationLoop(generation)
                    return
                }
            } catch (_: Exception) {}
        }

        submitAttemptCount++
    }

    // MARK: - Observation & Stability Phase

    private fun startObservationLoop(generation: Long) {
        activeJob?.cancel()
        stabilityText = null
        stabilityTickCount = 0
        updatePhase(AIBIPhase.GENERATING, "Waiting for answer...", isWaiting = true)

        activeJob = scope.launch {
            while (isActive && generationId == generation) {
                performObservationTick(generation)
                delay(timingProfile.observationCadenceMs)
            }
        }
    }

    private suspend fun performObservationTick(generation: Long) {
        val config = activeConfig ?: return
        val webView = activeWebView ?: return
        val task = activeTask ?: return
        val configJsonStr = buildConfigJsonString(config)

        val script = "window.__AIBI_RUNTIME__.observeGeneration($configJsonStr, $baselineAssistantCount)"
        val result = evaluateScript(webView, script) ?: return
        if (generationId != generation) return

        try {
            val json = JSONObject(result)
            if (!json.optBoolean("success", false)) return
            val data = json.optJSONObject("data") ?: return

            val phaseStr = data.optString("phase", "GENERATING")
            val isGenerating = data.optBoolean("isGenerating", true)
            val hasNewAnswer = data.optBoolean("hasNewAnswer", false)
            val rawText = data.optString("rawText", "")
            val errorMessage = data.optString("errorMessage").takeIf { it.isNotEmpty() && it != "null" }

            if (phaseStr == "FAILED" && !errorMessage.isNullOrEmpty()) {
                activeJob?.cancel()
                failWithError(errorMessage)
                return
            }

            if (phaseStr == "FALLBACK_REQUIRED") {
                activeJob?.cancel()
                escalateToVisible(AIBIFallbackReason.SECURITY_CHALLENGE_PRESENTED)
                return
            }

            if (hasNewAnswer && !isGenerating && rawText.trim().isNotEmpty()) {
                if (stabilityText != null && stabilityText == rawText) {
                    stabilityTickCount++
                    if (stabilityTickCount >= timingProfile.stabilityRequiredTicks) {
                        activeJob?.cancel()
                        val cleanScript = "window.__AIBI_RUNTIME__.cleanOutput(${JSONObject.quote(rawText)}, '${task.providerId}')"
                        var cleaned = rawText
                        val cleanResult = evaluateScript(webView, cleanScript)
                        if (cleanResult != null) {
                            try {
                                val cleanJson = JSONObject(cleanResult)
                                val cleanData = cleanJson.optJSONObject("data")
                                cleaned = cleanData?.optString("cleanedText", rawText) ?: rawText
                            } catch (_: Exception) {}
                        }

                        val finalResult = AIBIResult(
                            taskId = task.id,
                            providerId = task.providerId,
                            rawText = rawText,
                            cleanedText = cleaned,
                            isComplete = true
                        )
                        completeWithResult(finalResult)
                    }
                } else {
                    stabilityText = rawText
                    stabilityTickCount = 0
                    updatePhase(AIBIPhase.STABILIZING, "Receiving answer...", isWaiting = true)
                }
            } else {
                stabilityText = null
                stabilityTickCount = 0
                updatePhase(AIBIPhase.GENERATING, "Waiting for answer...", isWaiting = true)
            }
        } catch (_: Exception) {}
    }

    // MARK: - Completion & Fallback Handling

    private fun completeWithResult(result: AIBIResult) {
        stopAllJobs()
        _pendingResult.value = result

        // 1. Copy to clipboard
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("AIBI Result", result.cleanedText)
        clipboard?.setPrimaryClip(clip)

        // 2. Commit to host sink before dismissal
        val sink = resultSink
        if (sink != null) {
            val commitOutcome = sink.commitResult(result)
            if (commitOutcome.isSuccess) {
                updatePhase(AIBIPhase.COMPLETED, "Import completed")
                dismissVisibleBrowser()
                destroyHiddenBrowser()
                disposeNativeAttachmentBatch()
            } else {
                val err = commitOutcome.exceptionOrNull()?.message ?: "Validation error"
                updatePhase(AIBIPhase.FAILED, "Host import validation failed: $err")
            }
        } else {
            updatePhase(AIBIPhase.COMPLETED, "Result ready")
            dismissVisibleBrowser()
            destroyHiddenBrowser()
            disposeNativeAttachmentBatch()
        }
    }

    private fun failWithError(message: String) {
        stopAllJobs()
        _lastErrorMessage.value = message
        updatePhase(AIBIPhase.FAILED, message)
        destroyHiddenBrowser()
        disposeNativeAttachmentBatch()
    }

    private fun escalateToVisible(reason: AIBIFallbackReason) {
        stopAllJobs()
        updatePhase(AIBIPhase.FALLBACK_REQUIRED, "Opening browser for required action...")

        // Teardown hidden WebView and instantiate fresh visible WebView sharing cookie store
        destroyHiddenBrowser()
        presentVisibleBrowser()

        val config = activeConfig ?: return
        visibleWebView?.loadUrl(config.initialUrl)
    }

    // MARK: - Browser Lifecycle & Surface Helpers

    private val activeWebView: WebView?
        get() = if (_isVisibleBrowserPresented.value) visibleWebView else hiddenWebView

    @SuppressLint("SetJavaScriptEnabled")
    private fun mountHiddenBrowser(parent: ViewGroup?) {
        if (hiddenWebView != null) return

        val webView = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(375, 667)
            alpha = 0.001f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            webViewClient = createSecurityWebViewClient()
            webChromeClient = createAttachmentWebChromeClient()
        }

        hiddenWebView = webView
        parent?.addView(webView)
    }

    private fun destroyHiddenBrowser() {
        hiddenWebView?.apply {
            stopLoading()
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
        hiddenWebView = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun presentVisibleBrowser() {
        if (visibleWebView == null) {
            val webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.setSupportMultipleWindows(true)
                webViewClient = createSecurityWebViewClient()
                webChromeClient = createAttachmentWebChromeClient(this)
            }
            visibleWebView = webView
        }
        _isVisibleBrowserPresented.value = true
    }

    private fun dismissVisibleBrowser() {
        _isVisibleBrowserPresented.value = false
    }

    private fun destroyVisibleBrowser() {
        visibleWebView?.apply {
            stopLoading()
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
            destroy()
        }
        visibleWebView = null
        _isVisibleBrowserPresented.value = false
    }

    private fun createAttachmentWebChromeClient(
        popupTarget: WebView? = null
    ): WebChromeClient = object : WebChromeClient() {
        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
        ): Boolean {
            val callback = filePathCallback ?: return false
            if (nativeAttachmentUris.isEmpty()) return false
            if (fileChooserParams?.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                nativeAttachmentNextSingleIndex = nativeAttachmentUris.size
                callback.onReceiveValue(nativeAttachmentUris.toTypedArray())
            } else {
                val next = nativeAttachmentUris.getOrNull(nativeAttachmentNextSingleIndex)
                if (next == null) {
                    callback.onReceiveValue(null)
                } else {
                    nativeAttachmentNextSingleIndex += 1
                    callback.onReceiveValue(arrayOf(next))
                }
            }
            return true
        }

        override fun onCreateWindow(
            view: WebView?,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: android.os.Message?
        ): Boolean {
            val target = popupTarget ?: return false
            if (!isUserGesture) return false
            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
            val popup = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        popupView: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url ?: return true
                        if (url.toString() == "about:blank") return false
                        val config = activeConfig ?: return true
                        val allowed = originAllowed(url, config.allowedScriptOrigins) ||
                            originAllowed(url, config.allowedAuthOrigins)
                        if (allowed) {
                            target.loadUrl(url.toString())
                        } else {
                            failWithError("This sign-in page is not in the allowed origin list.")
                        }
                        popupView?.stopLoading()
                        popupView?.destroy()
                        return true
                    }
                }
            }
            transport.webView = popup
            resultMsg.sendToTarget()
            return true
        }
    }

    private fun prepareNativeAttachmentBatch(
        attachments: List<AIBIMediaAttachment>
    ): Pair<File?, List<Uri>> {
        if (attachments.isEmpty()) return null to emptyList()
        val root = File(context.cacheDir, "aibi").apply { mkdirs() }
        root.listFiles()?.filter(File::isDirectory)?.forEach { stale ->
            if (System.currentTimeMillis() - stale.lastModified() > 15 * 60 * 1_000L) {
                stale.deleteRecursively()
            }
        }
        val directory = File(root, "batch-${UUID.randomUUID()}").apply { mkdirs() }
        return try {
            val uris = attachments.sortedBy { it.sourceIndex }.mapIndexed { index, attachment ->
                val file = File(directory, "aibi-${(index + 1).toString().padStart(2, '0')}.jpg")
                file.writeBytes(attachment.data)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
            directory to uris
        } catch (error: Exception) {
            directory.deleteRecursively()
            throw error
        }
    }

    private fun disposeNativeAttachmentBatch() {
        nativeAttachmentDirectory?.deleteRecursively()
        nativeAttachmentDirectory = null
        nativeAttachmentUris = emptyList()
        nativeAttachmentNextSingleIndex = 0
    }

    private fun createSecurityWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return false
                val config = activeConfig ?: return false

                val isScriptOrigin = originAllowed(url, config.allowedScriptOrigins)
                val isAuthOrigin = originAllowed(url, config.allowedAuthOrigins)

                return if (isScriptOrigin || isAuthOrigin) {
                    false
                } else {
                    if (_isVisibleBrowserPresented.value) {
                        failWithError("This sign-in page is not in the allowed origin list.")
                    } else {
                        escalateToVisible(AIBIFallbackReason.NAVIGATION_DISALLOWED)
                    }
                    true
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val config = activeConfig ?: return
                val pageUri = url?.let(Uri::parse) ?: return
                if (originAllowed(pageUri, config.allowedScriptOrigins)) view?.let {
                    scope.launch { ensureRuntimeInjected(it) }
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    val sanitized = "Network error: ${error?.description ?: "Connection failed"}"
                    failWithError(sanitized)
                }
            }
        }
    }

    private fun originAllowed(url: Uri, origins: List<String>): Boolean {
        return origins.any { candidate ->
            val allowed = Uri.parse(candidate)
            url.scheme.equals(allowed.scheme, ignoreCase = true) &&
                url.host.equals(allowed.host, ignoreCase = true) &&
                url.port == allowed.port
        }
    }

    private suspend fun ensureRuntimeInjected(webView: WebView) {
        val config = activeConfig ?: return
        val pageUri = webView.url?.let(Uri::parse) ?: return
        if (runtimeJavaScript.isEmpty() || !originAllowed(pageUri, config.allowedScriptOrigins)) return
        val checkScript = "typeof window.__AIBI_RUNTIME__ !== 'undefined'"
        val exists = evaluateScript(webView, checkScript)
        if (exists == "true") return
        evaluateScript(webView, runtimeJavaScript)
    }

    private suspend fun evaluateScript(webView: WebView, script: String): String? {
        return suspendCancellableCoroutine { continuation ->
            Handler(Looper.getMainLooper()).post {
                webView.evaluateJavascript(script) { result ->
                    val sanitized = if (result != null && result != "null") {
                        if (result.startsWith("\"") && result.endsWith("\"") && result.length >= 2) {
                            try {
                                JSONObject("{v:$result}").getString("v")
                            } catch (_: Exception) {
                                result.substring(1, result.length - 1)
                            }
                        } else {
                            result
                        }
                    } else {
                        null
                    }
                    continuation.resume(sanitized) {}
                }
            }
        }
    }

    private fun buildConfigJsonString(config: AIBIProviderConfig): String {
        return JSONObject(mapOf(
            "selectors" to JSONObject(mapOf(
                "promptInput" to config.selectors.promptInput,
                "submitButton" to config.selectors.submitButton,
                "stopButton" to config.selectors.stopButton,
                "assistantMessage" to config.selectors.assistantMessage,
                "preCode" to (config.selectors.preCode ?: listOf("pre code")),
                "errorBanner" to config.selectors.errorBanner,
                "loginIndicator" to config.selectors.loginIndicator,
                "challengeIndicator" to config.selectors.challengeIndicator,
                "attachmentInput" to config.selectors.attachmentInput,
                "attachmentTrigger" to config.selectors.attachmentTrigger,
                "attachmentMenuAction" to config.selectors.attachmentMenuAction,
                "attachmentMenuActionText" to config.selectors.attachmentMenuActionText,
                "attachmentPreview" to config.selectors.attachmentPreview
            )),
            "mediaCapabilities" to JSONObject(mapOf(
                "supportsImages" to config.mediaCapabilities.supportsImages,
                "maxImagesPerTask" to config.mediaCapabilities.maxImagesPerTask,
                "requiresMultipleInputForBatch" to config.mediaCapabilities.requiresMultipleInputForBatch
            ))
        )).toString()
    }

    private fun cleanOutputLocally(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```") && text.endsWith("```")) {
            val lines = text.lines()
            if (lines.size >= 2) {
                text = lines.subList(1, lines.size - 1).joinToString("\n").trim()
            }
        }
        return text
    }
}
