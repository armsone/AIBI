package com.armsone.starmanager.ui.externalai

import java.util.Locale

/** 외부 AI 대기 상한과 iPhone 동일 역카운터 포맷. */
object ExternalAITimerFormatter {

    const val GENERATION_TIMEOUT_SECONDS = 119L

    fun formatElapsedSeconds(seconds: Long): String {
        val clamped = seconds.coerceAtLeast(0L)
        val minutes = clamped / 60
        val remSec = clamped % 60
        return if (minutes > 0) {
            String.format(Locale.ROOT, "%d분 %02d초", minutes, remSec)
        } else {
            String.format(Locale.ROOT, "%02d초", remSec)
        }
    }

    fun formatWaitingStatus(seconds: Long): String {
        return "남은 시간 ${formatCountdown(remainingSeconds(seconds))}"
    }

    fun remainingSeconds(elapsedSeconds: Long): Long {
        return (GENERATION_TIMEOUT_SECONDS - elapsedSeconds).coerceAtLeast(0L)
    }

    fun progress(elapsedSeconds: Long): Float {
        return remainingSeconds(elapsedSeconds).toFloat() / GENERATION_TIMEOUT_SECONDS.toFloat()
    }

    fun formatCountdown(remainingSeconds: Long): String {
        val clamped = remainingSeconds.coerceAtLeast(0L)
        return String.format(Locale.ROOT, "%d:%02d", clamped / 60, clamped % 60)
    }
}
