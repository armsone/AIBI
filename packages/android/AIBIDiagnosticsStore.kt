/**
 * AIBIDiagnosticsStore.kt
 * Bounded, private diagnostic store for AIBI runs on Android.
 *
 * Platform: Android API 24+
 * Specification: docs/portable-contract.md & verification/2026-09-10-submission-and-diagnostics.md
 */

package com.aibi.core

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class AIBIDiagnosticsStore(context: Context) {

    private val appContext: Context = context.applicationContext

    private val lock = Any()

    @Volatile
    private var _storageError: String? = null

    val storageError: String?
        get() = _storageError

    private val appVersion: String by lazy {
        try {
            val pInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            sanitizeVersion(pInfo.versionName)
        } catch (_: Throwable) {
            "0"
        }
    }

    private val appBuild: String by lazy {
        try {
            val pInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toString()
            }
        } catch (_: Throwable) {
            "0"
        }
    }

    private val osVersion: String by lazy {
        sanitizeVersion(Build.VERSION.RELEASE)
    }

    private val activeRuns = mutableMapOf<String, RunRecord>()

    private data class EventRecord(
        val stage: String,
        val elapsedMilliseconds: Long,
        val metrics: Map<String, Int>
    )

    private class RunRecord(
        val runId: String,
        val provider: String,
        val startTimeMs: Long,
        val file: File,
        val events: MutableList<EventRecord> = mutableListOf(),
        var hasReachedLimit: Boolean = false,
        var lastSnapshotMetrics: Map<String, Int>? = null
    )

    val latestExportFile: File?
        get() = synchronized(lock) {
            try {
                val dir = File(appContext.noBackupFilesDir, DIRECTORY_NAME)
                if (!dir.exists()) return@synchronized null
                ensureDirectory(dir)
                val latest = runFiles(dir).maxByOrNull { it.lastModified() } ?: return@synchronized null
                require(latest.length() in 1..MAX_FILE_BYTES)
                val sanitized = sanitizeStoredRun(JSONObject(latest.readText(Charsets.UTF_8)))
                writePrivateJSON(latest, sanitized)
                pruneOldRunsLocked(dir)
                _storageError = null
                latest
            } catch (_: Exception) {
                _storageError = "진단 로그를 읽지 못했어요. AI를 다시 실행하면 새 로그를 저장합니다."
                null
            }
        }

    fun start(provider: String): String {
        val runId = UUID.randomUUID().toString()
        val normalizedProvider = normalizeProvider(provider)
        val timestamp = SystemClock.elapsedRealtime()

        synchronized(lock) {
            activeRuns.clear()
            try {
                val dir = File(appContext.noBackupFilesDir, DIRECTORY_NAME)
                ensureDirectory(dir)
                val targetFile = File(dir, "run_${runId}.json")
                val record = RunRecord(
                    runId = runId,
                    provider = normalizedProvider,
                    startTimeMs = timestamp,
                    file = targetFile
                )
                activeRuns[runId] = record

                // Append run_started stage as event 1
                val initialEvent = EventRecord(
                    stage = "run_started",
                    elapsedMilliseconds = 0L,
                    metrics = emptyMap()
                )
                record.events.add(initialEvent)

                saveRunLocked(record)
            } catch (_: Exception) {
                _storageError = "진단 로그를 시작하지 못했어요. 기기 저장 공간을 확인해 주세요."
            }
        }

        return runId
    }

    fun record(runId: String, event: String, metrics: Map<String, Int> = emptyMap()) {
        if (!ALLOWED_STAGES.contains(event)) {
            return
        }

        val sanitizedMetrics = sanitizeMetrics(metrics)

        synchronized(lock) {
            try {
                val record = activeRuns[runId] ?: return@synchronized

                // Deduplicate unchanged bridge snapshots
                if (event == "bridge_snapshot") {
                    if (record.lastSnapshotMetrics != null && record.lastSnapshotMetrics == sanitizedMetrics) {
                        return@synchronized
                    }
                    record.lastSnapshotMetrics = sanitizedMetrics
                }

                // Event limit check (maximum 400 events)
                if (record.events.size >= MAX_EVENTS_PER_RUN) {
                    if (!record.hasReachedLimit) {
                        record.hasReachedLimit = true
                        if (record.events.lastOrNull()?.stage != "event_limit_reached") {
                            val elapsed = (SystemClock.elapsedRealtime() - record.startTimeMs).coerceIn(0L, MAX_ELAPSED_MS)
                            record.events[record.events.size - 1] = EventRecord(
                                stage = "event_limit_reached",
                                elapsedMilliseconds = elapsed,
                                metrics = emptyMap()
                            )
                            saveRunLocked(record)
                        }
                    }
                    return@synchronized
                }

                val elapsed = (SystemClock.elapsedRealtime() - record.startTimeMs).coerceIn(0L, MAX_ELAPSED_MS)
                record.events.add(EventRecord(stage = event, elapsedMilliseconds = elapsed, metrics = sanitizedMetrics))

                saveRunLocked(record)
            } catch (_: Exception) {
                _storageError = "진단 로그를 저장하지 못했어요. 기기 저장 공간을 확인해 주세요."
            }
        }
    }

    private fun saveRunLocked(record: RunRecord) {
        try {
            val dir = File(appContext.noBackupFilesDir, DIRECTORY_NAME)
            ensureDirectory(dir)

            val json = JSONObject().apply {
                put("schemaVersion", 1)
                put("adapterVersion", ADAPTER_VERSION)
                put("runID", record.runId)
                put("appVersion", appVersion)
                put("appBuild", appBuild)
                put("osVersion", osVersion)
                put("provider", record.provider)

                val eventsArray = JSONArray()
                for (ev in record.events) {
                    val evObj = JSONObject().apply {
                        put("stage", ev.stage)
                        put("elapsedMilliseconds", ev.elapsedMilliseconds)
                        val mObj = JSONObject()
                        for ((k, v) in ev.metrics) {
                            mObj.put(k, v)
                        }
                        put("metrics", mObj)
                    }
                    eventsArray.put(evObj)
                }
                put("events", eventsArray)

                val descObj = JSONObject()
                for ((k, v) in CODE_DESCRIPTIONS) {
                    descObj.put(k, v)
                }
                put("codeDescriptions", descObj)
            }

            writePrivateJSON(record.file, json)

            pruneOldRunsLocked(dir)
            _storageError = null
        } catch (_: Exception) {
            _storageError = "진단 로그를 저장하지 못했어요. 기기 저장 공간을 확인해 주세요."
        }
    }

    private fun pruneOldRunsLocked(dir: File) {
        for (file in runFiles(dir).sortedByDescending { it.lastModified() }.drop(MAX_STORED_RUNS)) {
            check(file.delete())
        }
    }

    private fun ensureDirectory(dir: File) {
        check(dir.isDirectory || dir.mkdirs())
        check(dir.canonicalFile.parentFile == appContext.noBackupFilesDir.canonicalFile && dir.canonicalFile.name == DIRECTORY_NAME)
        Os.chmod(dir.path, 0x1C0) // 0700: owner only.
    }

    private fun runFiles(dir: File): List<File> = dir.listFiles()?.filter { file ->
        file.isFile && file.canonicalFile.parentFile == dir.canonicalFile && file.canonicalFile.name == file.name &&
            file.name.matches(Regex("run_(?:[0-9]+_)?[0-9a-fA-F-]{36}\\.json"))
    } ?: emptyList()

    private fun writePrivateJSON(destination: File, json: JSONObject) {
        val temporary = File(destination.parentFile, "${UUID.randomUUID()}.tmp")
        val fd = Os.open(temporary.path, OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_EXCL, 0x180)
        try {
            FileOutputStream(fd).use { stream ->
                stream.write(json.toString(2).toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            Os.rename(temporary.path, destination.path)
            Os.chmod(destination.path, 0x180) // 0600, set before any content is written above.
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun sanitizeStoredRun(raw: JSONObject): JSONObject {
        require(raw.opt("schemaVersion") == SCHEMA_VERSION)
        val adapter = raw.opt("adapterVersion") as? String
        require(adapter == ADAPTER_VERSION || adapter == "aibi-android-0.5.0")
        val runID = raw.opt("runID") as? String ?: raw.opt("runId") as? String
        require(runID != null && UUID.fromString(runID).toString().equals(runID, ignoreCase = true))
        val provider = raw.opt("provider") as? String
        require(provider in setOf("chatgpt", "gemini", "claude", "grok", "unknown"))
        val clean = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION).put("adapterVersion", adapter)
            .put("runID", runID).put("provider", provider)
        for (key in listOf("appVersion", "appBuild", "osVersion")) {
            val version = raw.opt(key) as? String
            require(version != null && version.matches(Regex("[0-9.]{1,40}")))
            clean.put(key, version)
        }
        val events = raw.opt("events") as? JSONArray ?: throw IllegalArgumentException("Invalid events")
        val sanitizedEvents = JSONArray()
        for (index in 0 until minOf(events.length(), MAX_EVENTS_PER_RUN)) {
            val event = events.opt(index) as? JSONObject ?: continue
            val stage = event.opt("stage") as? String ?: continue
            if (stage !in ALLOWED_STAGES) continue
            val elapsedValue = event.opt("elapsedMilliseconds")
            if (elapsedValue !is Int && elapsedValue !is Long) continue
            val elapsed = (elapsedValue as Number).toLong()
            if (elapsed !in 0L..MAX_ELAPSED_MS) continue
            val metrics = event.opt("metrics") as? JSONObject ?: continue
            val sanitizedMetrics = JSONObject()
            for ((key, bounds) in METRIC_BOUNDS) {
                val value = metrics.opt(key)
                if (value !is Int && value !is Long) continue
                val number = (value as Number).toLong()
                if (number in bounds.first.toLong()..bounds.last.toLong()) sanitizedMetrics.put(key, number.toInt())
            }
            sanitizedEvents.put(JSONObject().put("stage", stage)
                .put("elapsedMilliseconds", elapsed).put("metrics", sanitizedMetrics))
        }
        return clean.put("events", sanitizedEvents).put("codeDescriptions", JSONObject(CODE_DESCRIPTIONS))
    }

    private fun sanitizeVersion(value: String?): String =
        value?.takeIf { it.matches(Regex("[0-9.]{1,40}")) } ?: "0"

    companion object {
        const val SCHEMA_VERSION = 1
        const val ADAPTER_VERSION = "0.5.0"
        const val DIRECTORY_NAME = "AIBIDiagnostics"
        const val MAX_STORED_RUNS = 10
        const val MAX_EVENTS_PER_RUN = 400
        private const val MAX_ELAPSED_MS = 86_400_000L
        private const val MAX_FILE_BYTES = 1_048_576L

        @Volatile
        private var defaultInstance: AIBIDiagnosticsStore? = null

        fun getInstance(context: Context): AIBIDiagnosticsStore {
            return defaultInstance ?: synchronized(this) {
                defaultInstance ?: AIBIDiagnosticsStore(context.applicationContext).also {
                    defaultInstance = it
                }
            }
        }

        fun normalizeProvider(provider: String?): String {
            val p = provider?.lowercase()?.trim() ?: return "unknown"
            return when {
                p.contains("chatgpt") || p.contains("openai") -> "chatgpt"
                p.contains("gemini") || p.contains("google") -> "gemini"
                p.contains("claude") || p.contains("anthropic") -> "claude"
                p.contains("grok") || p.contains("xai") -> "grok"
                else -> "unknown"
            }
        }

        val ALLOWED_STAGES: Set<String> = setOf(
            "run_started",
            "media_preparation_started",
            "media_prepared",
            "media_preparation_failed",
            "browser_loaded",
            "browser_load_failed",
            "bridge_ready",
            "bridge_failed",
            "composer_found",
            "composer_missing",
            "attachment_started",
            "attachment_input_found",
            "attachment_input_missing",
            "attachment_dispatched",
            "attachment_progress",
            "attachment_ready",
            "attachment_failed",
            "attachment_timeout",
            "prompt_inserted",
            "prompt_failed",
            "send_ready",
            "send_attempted",
            "send_blocked",
            "send_observed",
            "send_timeout",
            "generation_started",
            "generation_progress",
            "generation_completed",
            "generation_failed",
            "response_rejected",
            "result_applied",
            "manual_takeover",
            "run_cancelled",
            "run_failed",
            "run_completed",
            "bridge_snapshot",
            "request_started",
            "request_response",
            "request_failed",
            "event_limit_reached"
        )

        val METRIC_BOUNDS: Map<String, IntRange> = mapOf(
            "expected_count" to 0..100,
            "prepared_count" to 0..100,
            "attached_count" to 0..100,
            "preview_count" to 0..100,
            "input_count" to 0..100,
            "uploading_count" to 0..100,
            "failed_count" to 0..100,
            "image_count" to 0..100,
            "message_count" to 0..100_000,
            "response_length" to 0..10_000_000,
            "prompt_length" to 0..10_000_000,
            "total_bytes" to 0..1_000_000_000,
            "attempt" to 0..1_000,
            "stable_samples" to 0..1_000,
            "request_id" to 1..1_000,
            "request_kind" to 1..2,
            "request_has_messages" to 0..1,
            "http_status" to 0..599,
            "failure_kind" to 1..3,
            "composer_present" to 0..1,
            "send_present" to 0..1,
            "send_enabled" to 0..1,
            "stop_present" to 0..1,
            "prompt_present" to 0..1,
            "upload_complete" to 0..1,
            "user_message_present" to 0..1,
            "assistant_message_present" to 0..1,
            "attachment_verified" to 0..1,
            "generation_active" to 0..1
        )

        fun sanitizeMetrics(raw: Map<String, Int>): Map<String, Int> {
            val result = mutableMapOf<String, Int>()
            for ((k, v) in raw) {
                val range = METRIC_BOUNDS[k] ?: continue
                if (v in range) result[k] = v
            }
            return result
        }

        val CODE_DESCRIPTIONS: Map<String, String> = mapOf(
            "request_kind:1" to "file upload request",
            "request_kind:2" to "conversation completion request",
            "failure_kind:1" to "AbortError",
            "failure_kind:2" to "TypeError",
            "failure_kind:3" to "other error",
            "request_has_messages:0" to "request body has no messages array",
            "request_has_messages:1" to "request body has messages array",
            "composer_present:0" to "composer input not present",
            "composer_present:1" to "composer input present",
            "send_present:0" to "send button not present",
            "send_present:1" to "send button present",
            "send_enabled:0" to "send button disabled",
            "send_enabled:1" to "send button enabled",
            "stop_present:0" to "stop button not present",
            "stop_present:1" to "stop button present",
            "prompt_present:0" to "prompt text not present",
            "prompt_present:1" to "prompt text present",
            "upload_complete:0" to "upload pending",
            "upload_complete:1" to "upload complete",
            "user_message_present:0" to "user message not present",
            "user_message_present:1" to "user message present",
            "assistant_message_present:0" to "assistant message not present",
            "assistant_message_present:1" to "assistant message present",
            "attachment_verified:0" to "attachment count not verified",
            "attachment_verified:1" to "attachment count verified",
            "generation_active:0" to "generation inactive",
            "generation_active:1" to "generation active"
        )
    }
}
