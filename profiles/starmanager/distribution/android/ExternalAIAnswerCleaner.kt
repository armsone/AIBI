package com.armsone.starmanager.ui.externalai

import com.armsone.starmanager.service.DirectAIProvider

/**
 * 외부 AI 응답 텍스트 정제기 (iPhone cleanedImportedAnswer 및 AIBI 규격과 1:1 일치).
 *
 * 정제 절차:
 * 1. Grok 생각/작업 시간 헤더 접두사 및 보이지 않는 포맷팅 문자 제거
 * 2. <think>...</think> 태그 제거
 * 3. 앞뒤 마크다운 코드 펜스(```...```) 및 언어 태그 제거
 * 4. 서두 대화형 안내 문구 및 단독 헤더 라인 제거 ("글", "본문", "답변", "결과", "text", "plaintext", "markdown")
 * 5. 프롬프트 에코 블록 ([내가 입력한 내용], [원하는 결과]) 제거
 * 6. 전체를 감싸는 따옴표 제거 및 공백 정규화
 */
object ExternalAIAnswerCleaner {

    private val thinkTagRegex = Regex("(?is)<think>.*?</think>")
    private val codeFenceRegex = Regex("(?s)^```(?:[a-zA-Z0-9_-]+)?\\s*\\n?(.*?)\\n?```$")
    private val removableHeaders = setOf("글", "본문", "답변", "결과", "text", "plaintext", "markdown", "포스팅", "초안", "인스타그램", "result", "output", "response")
    private val headerPrefixRegex = Regex("^(글|본문|답변|결과|포스팅|초안|인스타그램|text|plaintext|markdown|result|output|response)\\s*[:：]\\s*", RegexOption.IGNORE_CASE)

    private val grokWorkDurationLinePatterns = listOf(
        Regex("^\\d+\\s*(?:s|m|h|초|분|시간)\\s*(?:동안\\s*)?(?:작업함|생각함)$", RegexOption.IGNORE_CASE),
        Regex("^(?:worked|thought)\\s+for\\s+\\d+\\s*(?:s|m|h|seconds?|minutes?|hours?)$", RegexOption.IGNORE_CASE)
    )

    private val grokPrefixPatterns = listOf(
        Regex("^[\\s\\p{Cf}]*\\d+[\\s\\p{Cf}]*(?:s|m|h|초|분|시간)[\\s\\p{Cf}]*(?:동안)?[\\s\\p{Cf}]*(?:작업함|생각함)[\\s\\p{Cf}]*", RegexOption.IGNORE_CASE),
        Regex("^[\\s\\p{Cf}]*(?:worked|thought)[\\s\\p{Cf}]+for[\\s\\p{Cf}]+\\d+[\\s\\p{Cf}]*(?:s|m|h|seconds?|minutes?|hours?)[\\s\\p{Cf}]*", RegexOption.IGNORE_CASE)
    )

    fun clean(raw: String, provider: DirectAIProvider? = null): String {
        if (raw.isBlank()) return ""
        var text = raw.replace("\r\n", "\n").replace("\r", "\n").trim()

        // 1. Grok 특화 생각 시간 접두사 제거
        if (provider == DirectAIProvider.GROK || provider == null) {
            text = removingGrokPrefix(text)
        }

        // 2. <think>...</think> 태그 제거
        text = thinkTagRegex.replace(text, "").trim()

        // 3. 마크다운 코드 펜스 제거
        val fenceMatch = codeFenceRegex.find(text)
        if (fenceMatch != null) {
            text = fenceMatch.groupValues[1].trim()
        }

        var lines = text.lines().toMutableList()

        // Grok 첫 줄 헤더 제거
        if (lines.isNotEmpty()) {
            val first = lines.first().trim()
            if (grokWorkDurationLinePatterns.any { it.matches(first) }) {
                lines.removeAt(0)
            }
        }

        // 단독 헤더 라인 제거 ("글", "본문", "답변" 등)
        if (lines.size > 1) {
            val first = lines.first().trim().lowercase()
            if (removableHeaders.contains(first)) {
                lines.removeAt(0)
            }
        }

        // 마크다운 펜스 라인 시작/끝 잔여분 제거
        while (lines.isNotEmpty() && lines.first().trim().startsWith("```")) {
            lines.removeAt(0)
        }
        while (lines.isNotEmpty() && lines.last().trim().startsWith("```")) {
            lines.removeAt(lines.size - 1)
        }

        text = lines.joinToString("\n").trim()

        // 4. 서두 헤더 콜론 접두사 제거 (예: "본문: #태그 ...")
        text = headerPrefixRegex.replace(text, "").trim()

        // 5. 프롬프트 에코 블록 정리
        if (text.contains("[원하는 결과]")) {
            val parts = text.split("[원하는 결과]")
            val after = parts.lastOrNull()?.trim() ?: ""
            val contentLines = after.lines().dropWhile { line ->
                val l = line.trim()
                l.startsWith("-") || l.startsWith("다른 설명") || l.isEmpty()
            }
            if (contentLines.isNotEmpty()) {
                text = contentLines.joinToString("\n").trim()
            }
        } else if (text.startsWith("[내가 입력한 내용]")) {
            val contentLines = text.lines().dropWhile { line ->
                val l = line.trim()
                l.startsWith("[") || l.startsWith("-") || l.isEmpty()
            }
            if (contentLines.isNotEmpty()) {
                text = contentLines.joinToString("\n").trim()
            }
        }

        // 6. 전체를 감싸는 따옴표 제거
        if ((text.startsWith("\"") && text.endsWith("\"") && text.length >= 2) ||
            (text.startsWith("“") && text.endsWith("”") && text.length >= 2)
        ) {
            text = text.substring(1, text.length - 1).trim()
        }

        return text
    }

    private fun removingGrokPrefix(text: String): String {
        var result = text
        for (pattern in grokPrefixPatterns) {
            val match = pattern.find(result)
            if (match != null && match.range.first == 0) {
                result = result.substring(match.range.last + 1)
                break
            }
        }
        return result.trim()
    }
}
