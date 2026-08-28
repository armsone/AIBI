package com.armsone.starmanager.ui.externalai

import com.armsone.starmanager.service.DirectAIProvider

/**
 * 외부 AI 웹 표면에서 감지된 사이트 오류 메시지를 정제하는 유틸리티 (iPhone sanitizeErrorMessage 1:1 일치).
 *
 * 요구사항:
 * - HTML 태그 및 script/style 블록 제거
 * - URL, 토큰, 비밀번호, API 키 등 민감 정보 마스킹/제거
 * - 스택 트레이스 및 기술적 잡음 제거 (at com..., Exception, traceback 등)
 * - 공백 정규화 및 최대 약 80자 간결한 메시지로 축약 (말줄임표 포함)
 * - 빈 문자열 또는 정제 불가 시 사용자 친화적 기본 메시지 반환
 */
object ExternalAIErrorSanitizer {

    private const val MAX_LENGTH = 80
    private const val DEFAULT_FALLBACK = "오류가 발생했어요. 다시 시도해 주세요."

    private val scriptStyleRegex = Regex("(?is)<(script|style)[^>]*>.*?</\\1>")
    private val htmlTagRegex = Regex("<[^>]+>")
    private val urlRegex = Regex("(?i)https?://\\S+|www\\.\\S+")
    private val secretPattern = Regex(
        "(?i)(?:bearer|token|key|secret|password|auth)[\\s:=]+[A-Za-z0-9_\\-\\.]+|[A-Za-z0-9_\\-]{32,}"
    )

    fun sanitize(raw: String?, provider: DirectAIProvider? = null, maxLength: Int = MAX_LENGTH): String {
        if (raw.isNullOrBlank()) return formatFallback(provider)
        var text = raw.trim()

        // 1. script / style 태그 및 내부 스크립트 제거
        text = scriptStyleRegex.replace(text, " ")

        // 2. HTML 태그 제거
        text = htmlTagRegex.replace(text, " ")

        // 3. 스택 트레이스 줄 필터링
        val filteredLines = text.lines().filter { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@filter false
            val lower = trimmed.lowercase()
            if (lower.startsWith("at ") ||
                lower.contains("traceback") ||
                lower.contains("stack trace") ||
                lower.contains("eval(") ||
                lower.contains("function()") ||
                lower.contains("exception:") ||
                lower.contains("error:")
            ) {
                return@filter false
            }
            true
        }
        text = if (filteredLines.isNotEmpty()) filteredLines.joinToString(" ") else text

        // 4. URL 제거
        text = urlRegex.replace(text, " ")

        // 5. 토큰, 키, 비밀번호 등 민감 패턴 제거
        text = secretPattern.replace(text, " ")

        // 6. 공백 정규화
        text = text.replace(Regex("\\s+"), " ").trim()

        // 7. 흔한 접두사 제거
        text = text.removePrefix("Error:").removePrefix("error:").removePrefix("오류:").trim()

        if (text.isBlank()) {
            return formatFallback(provider)
        }

        // 8. 최대 길이 제한 (말줄임표 포함)
        return if (text.length > maxLength) {
            text.take(maxLength - 1).trimEnd() + "…"
        } else {
            text
        }
    }

    private fun formatFallback(provider: DirectAIProvider?): String {
        return if (provider != null) {
            "${provider.title} 서비스에서 오류가 발생했어요."
        } else {
            DEFAULT_FALLBACK
        }
    }
}
