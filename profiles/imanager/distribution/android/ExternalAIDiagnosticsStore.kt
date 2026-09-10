package com.armsone.imanagerai.ui.externalai

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Private, bounded diagnostics. No API accepts raw errors, DOM, URLs, or content. */
class ExternalAIDiagnosticsStore(context: Context) {
    private val app = context.applicationContext
    private val directory = File(app.noBackupFilesDir, "AIBIDiagnostics")
    private var current: JSONObject? = null
    private var started = 0L
    var storageError: String? = null
        private set

    @Synchronized
    @Suppress("DEPRECATION")
    fun start(provider: String): UUID {
        val id = UUID.randomUUID()
        val metadata = runCatching { app.packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()
        val build = if (Build.VERSION.SDK_INT >= 28) metadata?.longVersionCode?.toString()
            else metadata?.versionCode?.toString()
        val normalized = when (provider.lowercase()) { "openai", "open_ai" -> "chatgpt"; else -> provider.lowercase() }
        current = JSONObject().put("schemaVersion", 1).put("adapterVersion", ADAPTER_VERSION)
            .put("runID", id.toString()).put("appVersion", version(metadata?.versionName))
            .put("appBuild", version(build)).put("osVersion", version(Build.VERSION.RELEASE))
            .put("provider", normalized.takeIf { it in providers } ?: "unknown")
            .put("events", JSONArray()).put("codeDescriptions", descriptions())
        started = SystemClock.elapsedRealtime()
        record(id, "run_started")
        return id
    }

    @Synchronized
    fun record(runID: UUID, event: String, metrics: Map<String, Int> = emptyMap()) {
        val run = current ?: return
        if (run.optString("runID") != runID.toString() || event !in stages) return
        val events = run.getJSONArray("events")
        if (events.length() >= 400) return
        val capped = events.length() == 399
        events.put(JSONObject().put("stage", if (capped) "event_limit_reached" else event)
            .put("elapsedMilliseconds", (SystemClock.elapsedRealtime() - started).coerceIn(0, 86_400_000))
            .put("metrics", if (capped) JSONObject() else safeMetrics(metrics)))
        try {
            persist(run)
            files().drop(10).forEach { file -> check(file.delete()) }
            storageError = null
        } catch (_: Exception) {
            storageError = "진단 로그를 저장하지 못했어요. 기기 저장 공간을 확인해 주세요."
        }
    }

    /** Reconstructs a safe export from the allowlist, including after process restart. */
    @Synchronized
    fun latestFile(): File? = try {
        files().firstOrNull()?.let { file ->
            check(file.length() <= 1_000_000)
            val source = JSONObject(file.readText())
            check(source.optInt("schemaVersion") == 1)
            check(source.optString("adapterVersion") == ADAPTER_VERSION)
            val id = UUID.fromString(source.getString("runID"))
            check(file.name == "$id.json")
            val provider = source.getString("provider")
            check(provider in providers)
            val safe = JSONObject().put("schemaVersion", 1).put("adapterVersion", ADAPTER_VERSION)
                .put("runID", id.toString()).put("provider", provider).put("codeDescriptions", descriptions())
            listOf("appVersion", "appBuild", "osVersion").forEach { safe.put(it, version(source.optString(it))) }
            val events = JSONArray()
            val originals = source.optJSONArray("events") ?: JSONArray()
            for (index in 0 until minOf(400, originals.length())) {
                val event = originals.optJSONObject(index) ?: continue
                val stage = event.optString("stage")
                if (stage !in stages) continue
                val metrics = event.optJSONObject("metrics") ?: JSONObject()
                val numbers = mutableMapOf<String, Int>()
                for ((key, range) in limits) {
                    val value = metrics.opt(key)
                    if (value is Number && value.toDouble() == value.toInt().toDouble() && value.toInt() in range) numbers[key] = value.toInt()
                }
                events.put(JSONObject().put("stage", stage)
                    .put("elapsedMilliseconds", event.optLong("elapsedMilliseconds").coerceIn(0, 86_400_000))
                    .put("metrics", safeMetrics(numbers)))
            }
            safe.put("events", events)
            persist(safe).also { storageError = null }
        }
    } catch (_: Exception) {
        storageError = "진단 로그를 읽지 못했어요. AI를 다시 실행하면 새 로그를 저장합니다."
        null
    }

    private fun files(): List<File> = directory.listFiles()?.filter {
        it.isFile && it.extension == "json" && runCatching { UUID.fromString(it.nameWithoutExtension) }.isSuccess
    }?.sortedByDescending { it.lastModified() } ?: emptyList()

    private fun persist(run: JSONObject): File {
        check(directory.isDirectory || directory.mkdirs())
        directory.setReadable(false, false); directory.setWritable(false, false); directory.setExecutable(false, false)
        check(directory.setReadable(true, true) && directory.setWritable(true, true) && directory.setExecutable(true, true))
        val id = UUID.fromString(run.getString("runID"))
        val file = File(directory, "$id.json")
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            file.setReadable(false, false); file.setWritable(false, false)
            file.setReadable(true, true); file.setWritable(true, true)
            stream.write(run.toString(2).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
        check(file.setReadable(false, false) && file.setWritable(false, false))
        check(file.setReadable(true, true) && file.setWritable(true, true))
        return file
    }

    companion object {
        const val ADAPTER_VERSION = "0.5.0"
        private val providers = setOf("chatgpt", "gemini", "claude", "grok", "kimi", "perplexity", "unknown")
        private val stages = setOf(
            "run_started", "media_preparation_started", "media_prepared", "media_preparation_failed",
            "browser_loaded", "browser_load_failed", "bridge_ready", "bridge_failed", "composer_found", "composer_missing",
            "attachment_started", "attachment_input_found", "attachment_input_missing", "attachment_dispatched",
            "attachment_progress", "attachment_ready", "attachment_failed", "attachment_timeout", "prompt_inserted", "prompt_failed",
            "send_ready", "send_attempted", "send_blocked", "send_observed", "send_timeout", "generation_started",
            "generation_progress", "generation_completed", "generation_failed", "response_rejected", "result_applied",
            "manual_takeover", "run_cancelled", "run_failed", "run_completed", "bridge_snapshot", "event_limit_reached",
            "request_started", "request_response", "request_failed"
        )
        private val limits = buildMap<String, IntRange> {
            listOf("expected_count", "prepared_count", "attached_count", "preview_count", "input_count", "uploading_count", "failed_count", "image_count").forEach { put(it, 0..100) }
            listOf("composer_present", "send_present", "send_enabled", "stop_present", "prompt_present", "upload_complete", "user_message_present", "assistant_message_present", "attachment_verified", "generation_active", "request_has_messages").forEach { put(it, 0..1) }
            put("message_count", 0..100_000); put("response_length", 0..10_000_000); put("prompt_length", 0..10_000_000)
            put("total_bytes", 0..1_000_000_000); put("attempt", 0..1000); put("stable_samples", 0..1000)
            put("request_kind", 1..2); put("http_status", 0..599); put("failure_kind", 1..3); put("request_id", 1..1000)
        }
        private fun safeMetrics(metrics: Map<String, Int>): JSONObject = JSONObject().also { result ->
            metrics.forEach { (key, value) -> if (limits[key]?.contains(value) == true) result.put(key, value) }
        }
        private fun version(value: String?): String = value?.takeIf { it.matches(Regex("[0-9.]{1,40}")) } ?: "0"
        private fun descriptions(): JSONObject = JSONObject()
            .put("request_kind", JSONObject().put("1", "file").put("2", "conversation"))
            .put("request_id", JSONObject().put("1...1000", "Page-local request sequence; not an account or file identifier"))
            .put("request_has_messages", JSONObject().put("0", "No messages array observed").put("1", "Messages array observed; content not recorded"))
            .put("failure_kind", JSONObject().put("1", "AbortError").put("2", "TypeError").put("3", "other"))
            .put("http_status", JSONObject().put("0", "No response status").put("1...599", "Observed response status"))
            .put("image_count", JSONObject().put("0...100", "Observed image count"))
            .put("stages", JSONObject().put("request_started", "Request started; acceptance not confirmed")
                .put("request_response", "HTTP response; image usability not confirmed").put("request_failed", "Request failed before normal response"))
    }
}
