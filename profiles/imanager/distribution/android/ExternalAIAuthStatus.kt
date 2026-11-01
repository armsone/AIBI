package com.armsone.imanagerai.ui.externalai

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebStorage
import android.widget.FrameLayout
import com.armsone.imanagerai.service.DirectAIProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * 외부 AI 제공사(Gemini, ChatGPT, Claude)의 기존 WebView 세션과
 * 실제 DOM 긍정 증거를 바탕으로 인증 상태를 비동기로 판별하는 프로버.
 */
object ExternalAIAuthStatus {
    private const val AUTH_STATE_PREFERENCES = "aibi_auth_state"
    private const val EXPLICIT_LOGOUT_PREFIX = "explicit_logout_"

    private fun explicitLogoutKey(provider: DirectAIProvider): String =
        EXPLICIT_LOGOUT_PREFIX + provider.rawValue

    private fun wasExplicitlyLoggedOut(context: Context, provider: DirectAIProvider): Boolean =
        context.getSharedPreferences(AUTH_STATE_PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(explicitLogoutKey(provider), false)

    /** Allows the provider to be probed again once the user deliberately opens its login flow. */
    fun markLoginFlowStarted(context: Context, provider: DirectAIProvider) {
        context.getSharedPreferences(AUTH_STATE_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .remove(explicitLogoutKey(provider))
            .apply()
    }

    /**
     * 지정한 제공사의 인증 상태를 백그라운드 WebView로 안전하게 프로빙한다.
     * - 쿠키 단순 존재나 로그인 버튼 부재만으로 로그인으로 간주하지 않음.
     * - 반드시 컴포저/입력창 또는 사용자 계정 마커 등 긍정적 DOM 증거가 존재해야 LOGGED_IN으로 판정.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun probe(
        context: Context,
        provider: DirectAIProvider,
        timeoutMs: Long = 12_000L
    ): ExternalAIAuthState = withContext(Dispatchers.Main) {
        if (wasExplicitlyLoggedOut(context, provider)) {
            return@withContext ExternalAIAuthState.REQUIRES_LOGIN
        }
        val result = withTimeoutOrNull(timeoutMs) {
            probeInternal(context, provider)
        }
        result ?: ExternalAIAuthState.UNKNOWN
    }

    private suspend fun probeInternal(
        context: Context,
        provider: DirectAIProvider
    ): ExternalAIAuthState = suspendCancellableCoroutine { continuation ->
        val webView = WebView(context)
        val hostView = context.findActivity()?.window?.decorView as? ViewGroup
        val hasCompleted = AtomicBoolean(false)

        // Provider portals need a real on-screen layout to hydrate account controls. Keep the
        // probe behind the opaque app surface at near-zero alpha instead of moving it off-screen.
        val density = context.resources.displayMetrics.density
        webView.alpha = 0.001f
        webView.isClickable = false
        webView.isFocusable = false
        webView.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        hostView?.addView(
            webView,
            0,
            FrameLayout.LayoutParams(
                (412 * density).toInt(),
                (892 * density).toInt(),
                Gravity.TOP or Gravity.START
            )
        )

        fun complete(state: ExternalAIAuthState) {
            if (hasCompleted.compareAndSet(false, true)) {
                try {
                    webView.stopLoading()
                    hostView?.removeView(webView)
                    webView.destroy()
                } catch (_: Exception) {}
                if (continuation.isActive) {
                    continuation.resume(state)
                }
            }
        }

        continuation.invokeOnCancellation {
            if (hasCompleted.compareAndSet(false, true)) {
                try {
                    webView.stopLoading()
                    hostView?.removeView(webView)
                    webView.destroy()
                } catch (_: Exception) {}
            }
        }

        try {
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = userAgentString.replace("; wv", "")
            }

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)

            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val targetUrl = request?.url?.toString()
                    if (!ExternalAISecurityPolicy.isAllowedUrl(targetUrl, provider)) {
                        complete(ExternalAIAuthState.UNKNOWN)
                        return true
                    }
                    if (ExternalAISecurityPolicy.isAuthOrigin(targetUrl, provider) ||
                        ExternalAIFallbackClassifier.classifyUrl(targetUrl) == ExternalAIFallbackReason.LOGIN_REQUIRED
                    ) {
                        complete(ExternalAIAuthState.REQUIRES_LOGIN)
                        return true
                    }
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    cookieManager.flush()

                    if (ExternalAISecurityPolicy.isAuthOrigin(url, provider) ||
                        ExternalAIFallbackClassifier.classifyUrl(url) == ExternalAIFallbackReason.LOGIN_REQUIRED
                    ) {
                        complete(ExternalAIAuthState.REQUIRES_LOGIN)
                        return
                    }

                    if (!ExternalAISecurityPolicy.canInjectScript(url, provider)) {
                        complete(ExternalAIAuthState.UNKNOWN)
                        return
                    }

                    // DOM 폴링 시작 (하이드레이션 대기: 최대 5초간 400ms 주기)
                    view?.post(object : Runnable {
                        var pollCount = 0
                        override fun run() {
                            if (hasCompleted.get() || !continuation.isActive) return
                            val wv = view

                            val currentUrl = wv.url
                            if (!ExternalAISecurityPolicy.canInjectScript(currentUrl, provider)) {
                                complete(ExternalAIAuthState.UNKNOWN)
                                return
                            }

                            val script = ExternalAIScripts.checkAuthStatusScript(
                                provider = provider,
                                requireVisible = true
                            )
                            wv.evaluateJavascript(script) { rawRes ->
                                if (hasCompleted.get()) return@evaluateJavascript
                                val checkResult = ExternalAIScripts.parseAuthCheckResult(rawRes)

                                if (checkResult.authenticated) {
                                    complete(ExternalAIAuthState.LOGGED_IN)
                                } else if (checkResult.hasLogin) {
                                    complete(ExternalAIAuthState.REQUIRES_LOGIN)
                                } else if (checkResult.hasChallenge) {
                                    complete(ExternalAIAuthState.UNKNOWN)
                                } else {
                                    if ((provider == DirectAIProvider.GEMINI ||
                                            provider == DirectAIProvider.OPEN_AI) && pollCount == 0
                                    ) {
                                        wv.evaluateJavascript(sidebarOpenScript(provider), null)
                                    }
                                    pollCount++
                                    if (pollCount >= 25) {
                                        complete(ExternalAIAuthState.UNKNOWN)
                                    } else {
                                        wv.postDelayed(this, 400L)
                                    }
                                }
                            }
                        }
                    })
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    // SPA redirects can abort an intermediate main-frame request even though the
                    // canonical provider page immediately replaces it and hydrates successfully.
                    // Keep the bounded outer timeout authoritative instead of publishing UNKNOWN
                    // on the first transient navigation error.
                }

                // complete() already guarantees a single release via hasCompleted; a dead
                // renderer is not logout evidence, so it only resolves as UNKNOWN.
                override fun onRenderProcessGone(
                    renderedView: WebView?,
                    detail: android.webkit.RenderProcessGoneDetail?
                ): Boolean {
                    complete(ExternalAIAuthState.UNKNOWN)
                    return true
                }
            }

            webView.loadUrl(provider.url)
            // Some provider SPAs (notably ChatGPT) keep their initial navigation open long
            // enough that onPageFinished is not a reliable gate. Run the same positive-evidence
            // probe independently; complete() guarantees exactly one published result.
            webView.postDelayed(object : Runnable {
                var pollCount = 0

                override fun run() {
                    if (hasCompleted.get() || !continuation.isActive) return
                    val currentUrl = webView.url
                    if (!ExternalAISecurityPolicy.canInjectScript(currentUrl, provider)) {
                        pollCount++
                        if (pollCount < 28) webView.postDelayed(this, 350L)
                        return
                    }

                    val script = ExternalAIScripts.checkAuthStatusScript(
                        provider = provider,
                        requireVisible = true
                    )
                    webView.evaluateJavascript(script) { rawRes ->
                        if (hasCompleted.get()) return@evaluateJavascript
                        val checkResult = ExternalAIScripts.parseAuthCheckResult(rawRes)
                        when {
                            checkResult.authenticated -> complete(ExternalAIAuthState.LOGGED_IN)
                            checkResult.hasLogin -> complete(ExternalAIAuthState.REQUIRES_LOGIN)
                            checkResult.hasChallenge -> complete(ExternalAIAuthState.UNKNOWN)
                            else -> {
                                // Bounded (≤8) only while no account, login or challenge marker exists.
                                if (hasExplicitClosedSidebarReveal(provider) &&
                                    pollCount < 16 && pollCount % 2 == 0
                                ) {
                                    webView.evaluateJavascript(sidebarOpenScript(provider), null)
                                }
                                pollCount++
                                if (pollCount < 28) webView.postDelayed(this, 350L)
                            }
                        }
                    }
                }
            }, 700L)
        } catch (e: Exception) {
            complete(ExternalAIAuthState.UNKNOWN)
        }
    }

    /** Clears the shared AIBI WebView session so every provider can be signed in again. */
    suspend fun clearAllSessions(context: Context): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            CookieManager.getInstance().removeAllCookies { removed ->
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
                context.getSharedPreferences(AUTH_STATE_PREFERENCES, Context.MODE_PRIVATE)
                    .edit()
                    .apply {
                        putBoolean(explicitLogoutKey(DirectAIProvider.GEMINI), true)
                        putBoolean(explicitLogoutKey(DirectAIProvider.OPEN_AI), true)
                        putBoolean(explicitLogoutKey(DirectAIProvider.CLAUDE), true)
                    }
                    .apply()
                if (continuation.isActive) continuation.resume(removed)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Providers whose closed-sidebar control is clicked only on explicit semantic closed-state evidence. */
internal fun hasExplicitClosedSidebarReveal(provider: DirectAIProvider): Boolean =
    provider == DirectAIProvider.OPEN_AI || provider == DirectAIProvider.CLAUDE

/**
 * Opens a closed provider sidebar once so account markers can render. ChatGPT is clicked only when
 * exactly one visible closed toggle matches (Gemini keeps its single-selector click); an expanded or ambiguous toggle is never clicked.
 * Claude (iPhone 2026-10-31 sanitized probe: one visible exact open-sidebar label, explicit aria-expanded=false,
 * no close/pin/test-id controls, account marker present after the click) uses the same exact closed-state form only.
 * Stateless toggles, pin controls and generic menu names are never clicked. Authentication still comes only from
 * provider account markers in [ExternalAIScripts.checkAuthStatusScript].
 */
internal fun sidebarOpenScript(provider: DirectAIProvider): String {
    if (provider == DirectAIProvider.GEMINI) {
        return """
            (function() {
                var button = document.querySelector("button[data-test-id='side-nav-menu-button']");
                if (!button) return false;
                button.click();
                return true;
            })();
        """.trimIndent()
    }
    if (provider == DirectAIProvider.CLAUDE) return claudeClosedSidebarScript()
    val selectors = when (provider) {
        DirectAIProvider.OPEN_AI -> listOf(
            "button[data-testid='open-sidebar-button']",
            "button[aria-label='사이드바 표시/숨기기'][aria-controls='app-shell-sidebar'][aria-expanded='false']",
            "button[aria-label='사이드바 보기'][aria-controls='browser-sidebar-popover'][aria-expanded='false']"
        )
        else -> emptyList()
    }
    val selectorsJson = org.json.JSONArray(selectors).toString()
    return """
        (function() {
            var selectors = $selectorsJson, matches = [];
            for (var i = 0; i < selectors.length; i++) {
                try {
                    var nodes = document.querySelectorAll(selectors[i]);
                    for (var j = 0; j < nodes.length; j++) {
                        var el = nodes[j];
                        if (matches.indexOf(el) !== -1) continue;
                        if (el.closest("[aria-hidden='true'], [inert]")) continue;
                        var shown = true;
                        for (var node = el; node && node.nodeType === 1; node = node.parentElement) {
                            var s = getComputedStyle(node);
                            if (s.display === 'none' || s.visibility === 'hidden' || s.opacity === '0' || node.hidden) { shown = false; break; }
                        }
                        var r = el.getBoundingClientRect();
                        if (shown && r.width > 0 && r.height > 0) matches.push(el);
                    }
                } catch (e) {}
            }
            if (matches.length !== 1) return false;
            matches[0].click();
            return true;
        })();
    """.trimIndent()
}

// Same exact open/close/pin label sets as the Stargram iOS adapter (lowercase, whitespace-normalized equality).
private val claudeSidebarOpenLabels = listOf(
    "open sidebar", "show sidebar", "expand sidebar",
    "사이드바 열기", "사이드바 보기", "사이드바 표시", "사이드바 펼치기"
)
private val claudeSidebarCloseLabels = listOf(
    "close sidebar", "hide sidebar", "collapse sidebar", "사이드바 닫기", "사이드바 숨기기", "사이드바 접기"
)
private val claudeSidebarPinMarkers = listOf(
    "pin-sidebar-toggle", "pin sidebar", "unpin sidebar", "사이드바 고정", "사이드바 고정 해제"
)

/**
 * Claude: click only when exactly one visible, enabled control has an exact open-sidebar aria-label/title and an
 * explicit aria-expanded=false, and no visible close-sidebar control exists. Hidden/inert clones, pin controls and
 * any other control are ignored. Returns true only after a click.
 */
private fun claudeClosedSidebarScript(): String {
    val open = org.json.JSONArray(claudeSidebarOpenLabels).toString()
    val close = org.json.JSONArray(claudeSidebarCloseLabels).toString()
    val pin = org.json.JSONArray(claudeSidebarPinMarkers).toString()
    return """
        (function() {
            try {
                var openLabels = $open, closeLabels = $close, pinMarkers = $pin;
                function norm(v) { return String(v || '').replace(/\s+/g, ' ').trim().toLowerCase(); }
                function shown(el) {
                    if (el.closest("[aria-hidden='true'], [inert]")) return false;
                    for (var node = el; node && node.nodeType === 1; node = node.parentElement) {
                        var s = getComputedStyle(node);
                        if (s.display === 'none' || s.visibility === 'hidden' || s.opacity === '0' || node.hidden) return false;
                    }
                    var r = el.getBoundingClientRect();
                    return r.width > 0 && r.height > 0;
                }
                var nodes = document.querySelectorAll("button, [role='button']"), matches = [];
                for (var i = 0; i < nodes.length; i++) {
                    var el = nodes[i];
                    var names = [norm(el.getAttribute('aria-label')), norm(el.getAttribute('title'))];
                    var isOpen = names.some(function(n) { return n && openLabels.indexOf(n) !== -1; });
                    var isClose = names.some(function(n) { return n && closeLabels.indexOf(n) !== -1; });
                    var isPin = names.concat([norm(el.getAttribute('data-testid'))]).some(function(n) { return n && pinMarkers.indexOf(n) !== -1; });
                    if (!(isOpen || isClose) || isPin || !shown(el)) continue;
                    if (isClose) return false;
                    matches.push(el);
                }
                if (matches.length !== 1) return false;
                var target = matches[0];
                if (target.disabled || target.getAttribute('aria-disabled') === 'true') return false;
                if (target.getAttribute('aria-expanded') !== 'false') return false;
                target.click();
                return true;
            } catch (e) {
                return false;
            }
        })();
    """.trimIndent()
}
