package com.armsone.starmanager.ui.externalai

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.armsone.starmanager.service.DirectAIProvider
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

    /**
     * 지정한 제공사의 인증 상태를 백그라운드 WebView로 안전하게 프로빙한다.
     * - 쿠키 단순 존재나 로그인 버튼 부재만으로 로그인으로 간주하지 않음.
     * - 반드시 컴포저/입력창 또는 사용자 계정 마커 등 긍정적 DOM 증거가 존재해야 LOGGED_IN으로 판정.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun probe(
        context: Context,
        provider: DirectAIProvider,
        timeoutMs: Long = 7_000L
    ): ExternalAIAuthState = withContext(Dispatchers.Main) {
        val result = withTimeoutOrNull(timeoutMs) {
            probeInternal(context, provider)
        }
        result ?: ExternalAIAuthState.CHECKING
    }

    private suspend fun probeInternal(
        context: Context,
        provider: DirectAIProvider
    ): ExternalAIAuthState = suspendCancellableCoroutine { continuation ->
        val webView = WebView(context)
        val hostView = (context as? Activity)?.window?.decorView as? ViewGroup
        val hasCompleted = AtomicBoolean(false)

        // WKWebView 기준 구현과 같이 실제 레이아웃 크기를 가진 채 화면 밖에 붙인다.
        // 창에 붙지 않은 WebView는 일부 SPA가 렌더링·하이드레이션되지 않아 상태가 영원히 확인 중에 머문다.
        hostView?.addView(
            webView,
            FrameLayout.LayoutParams(375, 667, Gravity.TOP or Gravity.START).apply {
                leftMargin = -10_000
            }
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
                        complete(ExternalAIAuthState.CHECKING)
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
                        complete(ExternalAIAuthState.CHECKING)
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
                                complete(ExternalAIAuthState.CHECKING)
                                return
                            }

                            val script = ExternalAIScripts.checkAuthStatusScript(
                                provider = provider,
                                requireVisible = false
                            )
                            wv.evaluateJavascript(script) { rawRes ->
                                if (hasCompleted.get()) return@evaluateJavascript
                                val checkResult = ExternalAIScripts.parseAuthCheckResult(rawRes)

                                if (checkResult.authenticated) {
                                    complete(ExternalAIAuthState.LOGGED_IN)
                                } else if (checkResult.hasLogin) {
                                    complete(ExternalAIAuthState.REQUIRES_LOGIN)
                                } else if (checkResult.hasChallenge) {
                                    complete(ExternalAIAuthState.CHECKING)
                                } else {
                                    pollCount++
                                    if (pollCount >= 12) {
                                        complete(ExternalAIAuthState.CHECKING)
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
                    if (request?.isForMainFrame == true) {
                        complete(ExternalAIAuthState.CHECKING)
                    }
                }
            }

            webView.loadUrl(provider.url)
        } catch (e: Exception) {
            complete(ExternalAIAuthState.CHECKING)
        }
    }
}
