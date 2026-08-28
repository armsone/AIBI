package com.armsone.starmanager.ui.externalai

import com.armsone.starmanager.service.DirectAIProvider
import java.net.URI

/**
 * AIBI 보안 정책:
 * - 정확한 스킴(https) 및 호스트 일치 검사 (취약한 접두사/느슨한 접미사 검사 배제)
 * - 스크립트 주입 허용 오리진(Script Origins)과 인증/로그인 오리진(Auth Origins)의 엄격한 분리
 * - 인증/로그인 및 보안 챌린지 오리진에는 자동화 스크립트 주입 절대 금지
 */
object ExternalAISecurityPolicy {

    private val geminiScriptHosts = setOf(
        "gemini.google.com"
    )

    private val geminiAuthHosts = setOf(
        "accounts.google.com",
        "myaccount.google.com",
        "support.google.com"
    )

    private val chatGptScriptHosts = setOf(
        "chatgpt.com",
        "chat.openai.com"
    )

    private val chatGptAuthHosts = setOf(
        "auth0.openai.com",
        "auth.openai.com",
        "accounts.google.com",
        "appleid.apple.com",
        "login.microsoftonline.com",
        "login.live.com"
    )

    private val claudeScriptHosts = setOf(
        "claude.ai"
    )

    private val claudeAuthHosts = setOf(
        "accounts.google.com",
        "appleid.apple.com"
    )

    private val grokScriptHosts = setOf(
        "grok.com",
        "x.ai"
    )

    private val grokAuthHosts = setOf(
        "x.com",
        "twitter.com",
        "auth.x.ai"
    )

    private val commonChallengeHosts = setOf(
        "challenges.cloudflare.com",
        "recaptcha.net",
        "www.recaptcha.net",
        "www.google.com"
    )

    private val commonCdnHosts = setOf(
        "oaistatic.com",
        "oaiusercontent.com",
        "claudecdn.com",
        "gstatic.com",
        "googleusercontent.com"
    )

    private val authPathSegments = listOf(
        "/login",
        "/signin",
        "/signup",
        "/auth",
        "/oauth",
        "/u/login",
        "/api/auth",
        "/ServiceLogin"
    )

    fun isScriptOrigin(url: String?, provider: DirectAIProvider): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() != "https") return false
        val host = uri.host?.lowercase() ?: return false

        val scriptHosts = when (provider) {
            DirectAIProvider.GEMINI -> geminiScriptHosts
            DirectAIProvider.OPEN_AI -> chatGptScriptHosts
            DirectAIProvider.CLAUDE -> claudeScriptHosts
            DirectAIProvider.GROK -> grokScriptHosts
        }

        if (host !in scriptHosts) return false

        // 스크립트 허용 호스트더라도 인증 경로인 경우 스크립트 오리진에서 제외
        val path = uri.path?.lowercase() ?: ""
        if (authPathSegments.any { path.contains(it) }) {
            return false
        }

        return true
    }

    fun isAuthOrigin(url: String?, provider: DirectAIProvider): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() != "https") return false
        val host = uri.host?.lowercase() ?: return false

        val authHosts = when (provider) {
            DirectAIProvider.GEMINI -> geminiAuthHosts
            DirectAIProvider.OPEN_AI -> chatGptAuthHosts
            DirectAIProvider.CLAUDE -> claudeAuthHosts
            DirectAIProvider.GROK -> grokAuthHosts
        }

        if (host in authHosts || host in commonChallengeHosts) {
            return true
        }

        // 제공사 도메인의 로그인/인증 서브패스 검사
        val path = uri.path?.lowercase() ?: ""
        val scriptHosts = when (provider) {
            DirectAIProvider.GEMINI -> geminiScriptHosts
            DirectAIProvider.OPEN_AI -> chatGptScriptHosts
            DirectAIProvider.CLAUDE -> claudeScriptHosts
            DirectAIProvider.GROK -> grokScriptHosts
        }
        if (host in scriptHosts && authPathSegments.any { path.contains(it) }) {
            return true
        }

        return false
    }

    fun isAllowedUrl(url: String?, provider: DirectAIProvider): Boolean {
        if (url.isNullOrBlank()) return false
        if (url == "about:blank") return true

        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() != "https") return false
        val host = uri.host?.lowercase() ?: return false

        if (host in commonCdnHosts || host in commonChallengeHosts) return true

        return isScriptOrigin(url, provider) || isAuthOrigin(url, provider)
    }

    /**
     * 스크립트 주입이 허용되는지 여부.
     * 인증/로그인 오리진에는 절대로 자동화 스크립트를 주입하지 않는다.
     */
    fun canInjectScript(url: String?, provider: DirectAIProvider): Boolean {
        return isScriptOrigin(url, provider) && !isAuthOrigin(url, provider)
    }
}
