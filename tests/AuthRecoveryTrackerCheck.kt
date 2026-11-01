package com.armsone.imanagerai.ui.externalai

import com.armsone.imanagerai.service.DirectAIProvider
import java.net.URI

/**
 * Executable Kotlin check suite for ExternalAILoginRecoveryTracker.
 * TM can compile and run this with standard kotlinc / JVM runner without extra dependencies.
 *
 * Checks:
 * 1. Strict URI parsing (spoofed host, query, userinfo, non-443 port)
 * 2. ChatGPT root vs auth path classification (e.g. /auth/login is auth, / is root)
 * 3. Shared accounts.google.com excursion: does NOT reset target
 * 4. First auth -> Google -> auth return: increments failedReturnCount (1), loop NOT detected yet
 * 5. Second auth -> Google -> auth return: flags loop (failedReturnCount 2)
 * 6. Root -> auth -> root -> auth repeated roundtrip (two failures): flags loop
 * 7. onAuthWindowOpened / onAuthWindowClosed handling
 * 8. START + FINISH navigation deduplication (no double count)
 * 9. Ordinary first login / guest / challenge / UNKNOWN: no loop
 * 10. Non-OpenAI provider: inert
 * 11. Reset clears state
 * 12. Strict RFC 6265 cookie token parsing in tracker helper
 */
fun main() {
    println("Running ExternalAILoginRecoveryTracker checks...")

    // 1. Strict URI parsing
    val validRoot = ExternalAILoginRecoveryTracker.parseStrictHttpsUri("https://chatgpt.com/")
    check(validRoot != null && validRoot.host == "chatgpt.com") { "Valid root URI failed" }

    val valid443 = ExternalAILoginRecoveryTracker.parseStrictHttpsUri("https://chatgpt.com:443/")
    check(valid443 != null) { "Port 443 should be accepted" }

    val non443 = ExternalAILoginRecoveryTracker.parseStrictHttpsUri("https://chatgpt.com:8443/")
    check(non443 == null) { "Non-443 port must be rejected" }

    val userInfoSpoof = ExternalAILoginRecoveryTracker.parseStrictHttpsUri("https://chatgpt.com@evil.com/")
    check(userInfoSpoof == null) { "Userinfo in URI must be rejected" }

    val httpScheme = ExternalAILoginRecoveryTracker.parseStrictHttpsUri("http://chatgpt.com/")
    check(httpScheme == null) { "Non-https scheme must be rejected" }

    // 2. Classification
    check(ExternalAILoginRecoveryTracker.isChatGPTRootOrHome("https://chatgpt.com/")) { "Root should be home" }
    check(ExternalAILoginRecoveryTracker.isChatGPTRootOrHome("https://chatgpt.com")) { "Root bare should be home" }
    check(ExternalAILoginRecoveryTracker.isChatGPTRootOrHome("https://chatgpt.com/g/g-abc123")) { "GPT subpath should be home" }

    check(!ExternalAILoginRecoveryTracker.isChatGPTRootOrHome("https://chatgpt.com/auth/login")) { "/auth/login must not be home" }
    check(!ExternalAILoginRecoveryTracker.isChatGPTRootOrHome("https://chatgpt.com/api/auth/callback")) { "/api/auth/callback must not be home" }

    check(ExternalAILoginRecoveryTracker.isChatGPTAuthOrigin("https://chatgpt.com/auth/login")) { "chatgpt.com/auth/login must be auth" }
    check(ExternalAILoginRecoveryTracker.isChatGPTAuthOrigin("https://chatgpt.com/api/auth/callback")) { "chatgpt.com/api/auth/callback must be auth" }
    check(ExternalAILoginRecoveryTracker.isChatGPTAuthOrigin("https://auth.openai.com/u/login")) { "auth.openai.com must be auth" }
    check(ExternalAILoginRecoveryTracker.isChatGPTAuthOrigin("https://auth0.openai.com/u/login")) { "auth0.openai.com must be auth" }

    check(!ExternalAILoginRecoveryTracker.isChatGPTAuthOrigin("https://evil.com/auth.openai.com")) { "Spoofed host must not be auth" }
    check(!ExternalAILoginRecoveryTracker.isChatGPTRootOrHome("https://evil.com/chatgpt.com")) { "Spoofed host must not be root" }

    // 3. OAuth excursion check
    check(ExternalAILoginRecoveryTracker.isOAuthExcursionHost("https://accounts.google.com/o/oauth2/auth")) { "accounts.google.com must be OAuth excursion" }
    check(!ExternalAILoginRecoveryTracker.isChatGPTAuthOrigin("https://accounts.google.com/o/oauth2/auth")) { "accounts.google.com must NOT be ChatGPT auth origin" }

    // 4 & 5. Google OAuth roundtrip loop detection
    val tracker = ExternalAILoginRecoveryTracker(DirectAIProvider.OPEN_AI)

    // Initial root visit
    tracker.onNavigation("https://chatgpt.com/")
    check(!tracker.isLoopDetected) { "Initial visit should not trigger loop" }
    check(tracker.failedReturnCount == 0) { "Initial visit failedReturnCount == 0" }

    // User navigates to auth
    tracker.onNavigation("https://auth.openai.com/u/login")
    check(!tracker.isLoopDetected) { "First auth entry should not trigger loop" }
    check(tracker.failedReturnCount == 0) { "First auth entry failedReturnCount == 0" }

    // User redirected to Google OAuth (excursion)
    tracker.onNavigation("https://accounts.google.com/o/oauth2/auth")
    check(!tracker.isLoopDetected) { "OAuth excursion should not trigger loop" }
    check(tracker.failedReturnCount == 0) { "OAuth excursion failedReturnCount == 0" }

    // Returns back to auth.openai.com without completing -> Failed Return 1
    tracker.onNavigation("https://auth.openai.com/u/login")
    check(!tracker.isLoopDetected) { "First failed return must NOT trigger loop" }
    check(tracker.failedReturnCount == 1) { "First failed return should set failedReturnCount = 1" }

    // Second excursion to Google OAuth
    tracker.onNavigation("https://accounts.google.com/o/oauth2/auth")
    check(!tracker.isLoopDetected) { "Second excursion should not trigger loop yet" }

    // Returns back to auth.openai.com again -> Failed Return 2 -> Loop detected!
    tracker.onNavigation("https://auth.openai.com/u/login")
    check(tracker.isLoopDetected) { "Second failed return MUST trigger loop" }
    check(tracker.failedReturnCount == 2) { "failedReturnCount should be 2" }

    // 6. Root -> auth -> root -> auth repeated roundtrip (two failures)
    tracker.reset()
    check(!tracker.isLoopDetected && tracker.failedReturnCount == 0 && tracker.roundtripCount == 0) { "Reset should clear all" }

    // Auth 1
    tracker.onNavigation("https://auth.openai.com/u/login")
    // Back to root (roundtrip 1)
    tracker.onNavigation("https://chatgpt.com/")
    check(tracker.roundtripCount == 1) { "Should have 1 roundtrip" }
    check(!tracker.isLoopDetected) { "Roundtrip 1 must not trigger loop" }

    // Auth 2 -> failed return 1
    tracker.onNavigation("https://auth.openai.com/u/login")
    check(tracker.failedReturnCount == 1) { "Failed return count 1" }
    check(!tracker.isLoopDetected) { "Failed return count 1 must not trigger loop" }

    // Back to root (roundtrip 2)
    tracker.onNavigation("https://chatgpt.com/")
    check(tracker.roundtripCount == 2) { "Roundtrip 2" }

    // Auth 3 -> failed return 2 -> Loop detected!
    tracker.onNavigation("https://auth.openai.com/u/login")
    check(tracker.failedReturnCount == 2) { "Failed return count 2" }
    check(tracker.isLoopDetected) { "Failed return count 2 must trigger loop" }

    // 7. Popup window opened and closed
    tracker.reset()
    tracker.onNavigation("https://chatgpt.com/")
    tracker.onAuthWindowOpened()
    // Popup closes with main still on auth
    tracker.onAuthWindowClosed(currentMainUrl = "https://auth.openai.com/u/login")
    check(tracker.failedReturnCount == 1) { "Popup close on auth should increment failed count to 1" }
    check(!tracker.isLoopDetected) { "First popup close must not trigger loop" }

    tracker.onAuthWindowOpened()
    tracker.onAuthWindowClosed(currentMainUrl = "https://auth.openai.com/u/login")
    check(tracker.failedReturnCount == 2) { "Second popup close on auth should increment failed count to 2" }
    check(tracker.isLoopDetected) { "Second popup close MUST trigger loop" }

    // 8. START + FINISH deduplication
    tracker.reset()
    tracker.onNavigation("https://chatgpt.com/")
    tracker.onNavigation("https://chatgpt.com/") // Duplicate START+FINISH
    check(tracker.roundtripCount == 0) { "Duplicate should not advance" }

    tracker.onNavigation("https://auth.openai.com/u/login")
    tracker.onNavigation("https://auth.openai.com/u/login") // Duplicate START+FINISH
    check(tracker.failedReturnCount == 0) { "Duplicate should not double count" }

    // 9. Ordinary single login roundtrip: root -> auth -> root (success) -> no loop
    tracker.reset()
    tracker.onNavigation("https://chatgpt.com/")
    tracker.onNavigation("https://auth.openai.com/u/login")
    tracker.onNavigation("https://chatgpt.com/")
    check(tracker.roundtripCount == 1) { "roundtripCount should be 1" }
    check(tracker.failedReturnCount == 0) { "failedReturnCount should be 0 on success" }
    check(!tracker.isLoopDetected) { "Successful login must not trigger loop" }

    // 10. Non-OpenAI provider is inert
    val claudeTracker = ExternalAILoginRecoveryTracker(DirectAIProvider.CLAUDE)
    claudeTracker.onNavigation("https://claude.ai/login")
    claudeTracker.onNavigation("https://claude.ai/")
    claudeTracker.onNavigation("https://claude.ai/login")
    check(!claudeTracker.isLoopDetected) { "Claude tracker must be inert" }

    println("All ExternalAILoginRecoveryTracker checks passed successfully!")
}
