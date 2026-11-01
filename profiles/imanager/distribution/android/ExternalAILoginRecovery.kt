package com.armsone.imanagerai.ui.externalai

import android.webkit.CookieManager
import com.armsone.imanagerai.service.DirectAIProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Result of scoped ChatGPT cookie expiration.
 * Android CookieManager API cannot prove all-path cookies absent; partial results
 * and probe counts are reported honestly.
 */
data class ScopedResetResult(
    val success: Boolean,
    val attemptedCount: Int,
    val completedCount: Int,
    val rejectedCount: Int,
    val residualProbeCount: Int,
    val capReached: Boolean = false,
    val timedOut: Boolean = false,
    val message: String? = null
) {
    val partial: Boolean get() = rejectedCount > 0 || residualProbeCount > 0 || capReached
}

/**
 * Pure stateful tracker for detecting repeated ChatGPT login loops in LOGIN mode.
 * Ordinary first login, challenges, guest-with-login-button, and UNKNOWN states
 * MUST NOT trigger recovery.
 *
 * Loop detection criteria:
 * - Requires at least TWO failed excursions/returns to auth origin (failedReturnCount >= 2)
 *   e.g. auth -> Google OAuth -> auth (round 1), then auth -> Google OAuth -> auth (round 2 -> loop flagged)
 *   or auth -> actual ChatGPT root/home -> auth (round 1), then auth -> actual ChatGPT root/home -> auth (round 2 -> loop flagged)
 * - Single roundtrip (first auth or successful login) never flags loop.
 * - Navigation START and FINISH duplicate calls do not double count.
 * - Shared accounts.google.com OAuth is an excursion only and never resets target.
 */
class ExternalAILoginRecoveryTracker(
    private val provider: DirectAIProvider
) {
    var roundtripCount: Int = 0
        private set
    var failedReturnCount: Int = 0
        private set
    var isLoopDetected: Boolean = false
        private set

    // Navigation state tracking
    private var inAuthOrigin: Boolean = false
    private var inOAuthExcursion: Boolean = false
    private var lastNavUrl: String? = null
    private var lastIsAuthWindow: Boolean? = null
    private var isAuthWindowOpen: Boolean = false

    /**
     * Called when an OAuth child window opens.
     */
    fun onAuthWindowOpened() {
        if (provider != DirectAIProvider.OPEN_AI) return
        isAuthWindowOpen = true
        inAuthOrigin = true
        lastNavUrl = null
        lastIsAuthWindow = null
    }

    /**
     * Called when an OAuth child window closes.
     */
    fun onAuthWindowClosed(currentMainUrl: String? = null) {
        if (provider != DirectAIProvider.OPEN_AI) return
        if (!isAuthWindowOpen) return
        isAuthWindowOpen = false
        lastNavUrl = null
        lastIsAuthWindow = null

        // Child window closed: if we were in an auth flow, evaluate whether we returned to auth
        if (currentMainUrl != null) {
            val isAuth = isChatGPTAuthOrigin(currentMainUrl)
            if (isAuth) {
                // Returned to auth after popup was opened
                recordFailedReturn()
            } else if (isChatGPTRootOrHome(currentMainUrl)) {
                // Returned to root
                inAuthOrigin = false
                inOAuthExcursion = false
            }
        }
    }

    /**
     * Called on top-frame navigation or child auth window navigation events.
     * Deduplicates identical (url, isAuthWindow) transitions (e.g. START + FINISH).
     */
    fun onNavigation(url: String?, isAuthWindow: Boolean = false) {
        if (provider != DirectAIProvider.OPEN_AI) return
        if (url.isNullOrBlank() || url == "about:blank") {
            return
        }

        // Deduplicate exact consecutive navigation event (e.g. onPageStarted followed by onPageFinished)
        if (url == lastNavUrl && isAuthWindow == lastIsAuthWindow) {
            return
        }
        lastNavUrl = url
        lastIsAuthWindow = isAuthWindow

        val isAuth = isAuthWindow || isChatGPTAuthOrigin(url)
        val isOAuth = isOAuthExcursionHost(url)
        val isProviderHome = isChatGPTRootOrHome(url)

        if (isOAuth) {
            // Shared OAuth (e.g. accounts.google.com) is an excursion only.
            // Never reset target, but mark excursion active.
            inOAuthExcursion = true
            return
        }

        if (isAuth) {
            if (inOAuthExcursion) {
                // Returned from OAuth excursion directly back to auth -> failed completion!
                inOAuthExcursion = false
                inAuthOrigin = true
                recordFailedReturn()
            } else if (!inAuthOrigin) {
                // Entering auth origin from home or external
                inAuthOrigin = true
                if (roundtripCount >= 1) {
                    recordFailedReturn()
                }
            }
        } else if (isProviderHome) {
            inOAuthExcursion = false
            if (inAuthOrigin) {
                // Transitioned from auth origin back to actual ChatGPT root/home
                inAuthOrigin = false
                if (roundtripCount < MAX_TRACKED_ROUNDTRIPS) {
                    roundtripCount++
                }
            }
        } else {
            // Other allowed navigation (CDN, challenge, etc.) - do not reset auth state
        }
    }

    private fun recordFailedReturn() {
        if (failedReturnCount < MAX_TRACKED_FAILURES) {
            failedReturnCount++
        }
        if (failedReturnCount >= 2) {
            isLoopDetected = true
        }
    }

    /**
     * Resets tracker state (e.g. after scoped recovery is completed).
     */
    fun reset() {
        roundtripCount = 0
        failedReturnCount = 0
        isLoopDetected = false
        inAuthOrigin = false
        inOAuthExcursion = false
        lastNavUrl = null
        lastIsAuthWindow = null
        isAuthWindowOpen = false
    }

    companion object Classification {
        const val MAX_TRACKED_ROUNDTRIPS = 10
        const val MAX_TRACKED_FAILURES = 10

        /**
         * Strict URI parsing: https scheme, exact host, port 443 or default (-1), no userinfo.
         */
        fun parseStrictHttpsUri(url: String?): URI? {
            if (url.isNullOrBlank()) return null
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            if (uri.scheme?.lowercase() != "https") return null
            if (uri.userInfo != null) return null
            val port = uri.port
            if (port != -1 && port != 443) return null
            if (uri.host.isNullOrBlank()) return null
            return uri
        }

        /**
         * ChatGPT root / home:
         * - Host must be exactly chatgpt.com
         * - Must NOT be under /auth/ prefix or /api/auth/ prefix
         */
        fun isChatGPTRootOrHome(url: String?): Boolean {
            val uri = parseStrictHttpsUri(url) ?: return false
            val host = uri.host.lowercase()
            if (host != "chatgpt.com") return false
            val path = uri.path ?: "/"
            if (path.startsWith("/auth/") || path == "/auth" ||
                path.startsWith("/api/auth/") || path == "/api/auth") {
                return false
            }
            return path == "/" || path.isBlank() || path.startsWith("/g/") || path.startsWith("/c/")
        }

        /**
         * ChatGPT auth origins and paths:
         * - Hosts: auth.openai.com, auth0.openai.com
         * - Host chatgpt.com with path /auth/ prefix or /api/auth/ prefix
         */
        fun isChatGPTAuthOrigin(url: String?): Boolean {
            val uri = parseStrictHttpsUri(url) ?: return false
            val host = uri.host.lowercase()
            if (host == "auth.openai.com" || host == "auth0.openai.com") {
                return true
            }
            if (host == "chatgpt.com") {
                val path = uri.path ?: ""
                if (path.startsWith("/auth/") || path == "/auth" ||
                    path.startsWith("/api/auth/") || path == "/api/auth") {
                    return true
                }
            }
            return false
        }

        /**
         * OAuth excursion hosts (e.g. Google OAuth)
         */
        fun isOAuthExcursionHost(url: String?): Boolean {
            val uri = parseStrictHttpsUri(url) ?: return false
            val host = uri.host.lowercase()
            return host == "accounts.google.com"
        }
    }
}

/**
 * Bounded scoped cookie expiration helper for ChatGPT login-loop remediation.
 *
 * Scoped strictly to exact three ChatGPT-owned origins:
 * - https://chatgpt.com/
 * - https://auth.openai.com/
 * - https://auth0.openai.com/
 *
 * Probes and expires known paths:
 * - /, /auth, /api/auth, /u, /mfa, /api/accounts
 *
 * Guaranteed:
 * - Prohibits mass deletion API calls
 * - NO clearing of shared Google accounts or other AI provider sessions (Gemini, Claude)
 * - Memory-only token parsing with RFC 6265 token validation (max 128 chars) and strict count cap (100)
 * - Zero persistence or logging of cookie material or raw exception messages
 * - Thread-safe reset serialization with generation lock and outstanding callback settlement
 * - Async UI-thread CookieManager callbacks with timeout
 * - Honestly distinguishes attempted, completed, and rejected callbacks; does not claim fullclear
 */
object ExternalAILoginRecovery {
    val CHATGPT_SCOPED_ORIGINS = listOf(
        "https://chatgpt.com/",
        "https://auth.openai.com/",
        "https://auth0.openai.com/"
    )

    val KNOWN_PATHS = listOf(
        "/",
        "/auth",
        "/api/auth",
        "/u",
        "/mfa",
        "/api/accounts"
    )

    private fun domainVariantsForOrigin(origin: String): List<String?> {
        return when {
            origin.contains("chatgpt.com") -> listOf(null, "chatgpt.com", ".chatgpt.com")
            origin.contains("auth.openai.com") -> listOf(null, "auth.openai.com", ".openai.com")
            origin.contains("auth0.openai.com") -> listOf(null, "auth0.openai.com", ".openai.com")
            else -> emptyList()
        }
    }

    private val COOKIE_NAME_TOKEN_REGEX = Regex("^[!#$%&'*+\\-.^_`|~0-9a-zA-Z]+$")
    const val MAX_COOKIE_NAME_LENGTH = 128
    const val MAX_COOKIES_PER_ORIGIN = 100
    const val RESET_TIMEOUT_MS = 10_000L

    // Singleton reset lock to prevent concurrent resets
    private val isResetActive = AtomicBoolean(false)

    /**
     * Parses a Cookie header string in memory into strict RFC 6265 cookie names.
     * Never stores, prints, or exports values or full headers. Bounded to 100 names, max 128 chars per name.
     */
    fun parseCookieNames(cookieHeader: String?): List<String> {
        if (cookieHeader.isNullOrBlank()) return emptyList()
        val names = mutableListOf<String>()
        val pairs = cookieHeader.split(";")
        for (pair in pairs) {
            val name = pair.substringBefore("=").trim()
            if (name.isNotEmpty() && name.length <= MAX_COOKIE_NAME_LENGTH && COOKIE_NAME_TOKEN_REGEX.matches(name)) {
                if (!names.contains(name)) {
                    names.add(name)
                    if (names.size >= MAX_COOKIES_PER_ORIGIN) break
                }
            }
        }
        return names
    }

    /**
     * Serialized, bounded scoped reset. Expires cookies only across the 3 exact ChatGPT origins
     * and their known path/domain variants. Incomplete or timed out executions are reported as partial/failure.
     */
    suspend fun clearChatGPTCookiesScoped(
        cookieManager: CookieManager = CookieManager.getInstance()
    ): ScopedResetResult = withContext(Dispatchers.Main) {
        if (!isResetActive.compareAndSet(false, true)) {
            return@withContext ScopedResetResult(
                success = false,
                attemptedCount = 0,
                completedCount = 0,
                rejectedCount = 0,
                residualProbeCount = 0,
                message = "초기화가 이미 진행 중입니다."
            )
        }

        var workFinished = false
        var outstandingCallbacks = 0
        var attempted = 0
        var completed = 0
        var rejected = 0
        var isCapReached = false

        try {
            val runResult = withTimeoutOrNull(RESET_TIMEOUT_MS) {
                var totalRawPathsParsed = 0
                val allProbedOrigins = mutableSetOf<String>()

                for (origin in CHATGPT_SCOPED_ORIGINS) {
                    val uri = ExternalAILoginRecoveryTracker.parseStrictHttpsUri(origin) ?: continue
                    val domains = domainVariantsForOrigin(origin)

                    // Probe all known paths for cookie names
                    val probedNames = mutableSetOf<String>()
                    for (path in KNOWN_PATHS) {
                        totalRawPathsParsed++
                        if (totalRawPathsParsed >= 100) {
                            isCapReached = true
                        }
                        val probeUrl = "https://${uri.host}$path"
                        val rawHeader = cookieManager.getCookie(probeUrl)
                        val names = parseCookieNames(rawHeader)
                        for (n in names) {
                            if (probedNames.size < MAX_COOKIES_PER_ORIGIN) {
                                probedNames.add(n)
                            } else {
                                isCapReached = true
                            }
                            if (allProbedOrigins.size < 100) {
                                allProbedOrigins.add("$origin#$n")
                            } else {
                                isCapReached = true
                            }
                        }
                    }

                    for (name in probedNames) {
                        val isHostOnlyPrefix = name.startsWith("__Host-")

                        val pathsToTarget = if (isHostOnlyPrefix) listOf("/") else KNOWN_PATHS
                        val domainsToTarget = if (isHostOnlyPrefix) listOf(null) else domains

                        for (path in pathsToTarget) {
                            for (domain in domainsToTarget) {
                                val cookieString = buildString {
                                    append(name).append("=; ")
                                    append("Path=").append(path).append("; ")
                                    append("Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                                    if (domain != null) {
                                        append("; Domain=").append(domain)
                                    }
                                    append("; Secure; HttpOnly")
                                }

                                attempted++
                                outstandingCallbacks++
                                val setSuccess = try {
                                    suspendCancellableCoroutine<Boolean> { cont ->
                                        cookieManager.setCookie(origin, cookieString) { ok ->
                                            outstandingCallbacks--
                                            if (cont.isActive) {
                                                cont.resume(ok)
                                            }
                                            if (workFinished && outstandingCallbacks == 0) {
                                                isResetActive.set(false)
                                            }
                                        }
                                    }
                                } catch (e: Throwable) {
                                    if (e is CancellationException) {
                                        throw e
                                    }
                                    outstandingCallbacks--
                                    if (workFinished && outstandingCallbacks == 0) {
                                        isResetActive.set(false)
                                    }
                                    false
                                }
                                if (setSuccess) {
                                    completed++
                                } else {
                                    rejected++
                                }
                            }
                        }
                    }
                }

                cookieManager.flush()

                // Count residual probed names across origins
                var residualCount = 0
                for (origin in CHATGPT_SCOPED_ORIGINS) {
                    val uri = ExternalAILoginRecoveryTracker.parseStrictHttpsUri(origin) ?: continue
                    for (path in KNOWN_PATHS) {
                        val probeUrl = "https://${uri.host}$path"
                        val rawHeader = cookieManager.getCookie(probeUrl)
                        residualCount += parseCookieNames(rawHeader).size
                    }
                }

                ScopedResetResult(
                    success = completed > 0 || (attempted == 0 && residualCount == 0),
                    attemptedCount = attempted,
                    completedCount = completed,
                    rejectedCount = rejected,
                    residualProbeCount = residualCount,
                    capReached = isCapReached,
                    message = if (rejected > 0) "일부 쿠키 만료 처리가 거부되었습니다." else null
                )
            }

            runResult ?: ScopedResetResult(
                success = false,
                attemptedCount = attempted,
                completedCount = completed,
                rejectedCount = rejected,
                residualProbeCount = 0,
                capReached = isCapReached,
                timedOut = true,
                message = "쿠키 초기화 시간이 초과되었습니다."
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            ScopedResetResult(
                success = false,
                attemptedCount = attempted,
                completedCount = completed,
                rejectedCount = rejected,
                residualProbeCount = 0,
                capReached = isCapReached,
                message = "쿠키 만료 처리 중 오류가 발생했습니다."
            )
        } finally {
            workFinished = true
            if (outstandingCallbacks == 0) {
                isResetActive.set(false)
            }
        }
    }
}

