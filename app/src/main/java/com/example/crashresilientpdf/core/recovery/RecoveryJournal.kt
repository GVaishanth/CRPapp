package com.example.crashresilientpdf.core.recovery

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * RecoveryJournal - Phase 6 (Remediated in Phase 7.1 & Phase 10 Polish)
 *
 * Lightweight append-only event log for recovery events.
 * Presentation / analytics layer ONLY.
 *
 * - Separate from Checkpoint history
 * - A recovery event NEVER modifies checkpoint history
 * - Read-only projection for UI / analytics
 * - Capped at 50 entries, FIFO
 * - Remediated in Phase 7.1: In-memory caching & background I/O to eliminate main-thread stalls.
 *
 * This is NOT a core engine, manager, or controller.
 * Category: Journal
 */
class RecoveryJournal(private val context: Context) {

    companion object {
        @Volatile private var cachedHistory: List<RecoveryRecord>? = null

        fun createRecord(
            documentId: String,
            displayName: String,
            restoredPage: Int,
            restoredZoom: Float,
            checkpointAgeMs: Long,
            triggerType: String,
            recoveryDurationMs: Long,
            recoverySource: String,
            fallbackUsed: Boolean,
            validationResult: String = "OK"
        ): RecoveryRecord = RecoveryRecord(
            recoveryId = UUID.randomUUID().toString(),
            timestampMs = System.currentTimeMillis(),
            documentId = documentId,
            displayName = displayName,
            recoveryType = if (fallbackUsed) RecoveryRecord.RecoveryType.FALLBACK
            else RecoveryRecord.RecoveryType.NORMAL,
            restoredPage = restoredPage,
            restoredZoom = restoredZoom,
            checkpointAgeMs = checkpointAgeMs,
            triggerType = triggerType,
            recoveryDurationMs = recoveryDurationMs,
            recoverySource = recoverySource,
            validationResult = validationResult
        )
    }

    private val file: File get() = File(context.filesDir, "recovery_journal.json")
    private val lock = Any()
    private val maxEntries = 50

    init {
        if (RecoveryJournal.cachedHistory == null) {
            CoroutineScope(Dispatchers.IO).launch {
                synchronized(lock) {
                    if (RecoveryJournal.cachedHistory == null) {
                        RecoveryJournal.cachedHistory = readAllLocked()
                    }
                }
            }
        }
    }

    fun record(record: RecoveryRecord) {
        CoroutineScope(Dispatchers.IO).launch {
            synchronized(lock) {
                val list = (RecoveryJournal.cachedHistory ?: readAllLocked()).toMutableList()
                list.add(0, record) // newest first
                val trimmed = list.take(maxEntries)
                RecoveryJournal.cachedHistory = trimmed
                writeAllLocked(trimmed)
            }
        }
    }

    fun getHistory(documentId: String? = null): List<RecoveryRecord> {
        val all = RecoveryJournal.cachedHistory ?: synchronized(lock) {
            readAllLocked().also { RecoveryJournal.cachedHistory = it }
        }
        return if (documentId != null) {
            all.filter { it.documentId == documentId }
        } else all
    }

    fun getLastRecovery(documentId: String): RecoveryRecord? {
        return getHistory(documentId).firstOrNull()
    }

    fun getTotalRecoveryCount(): Int = getHistory().size

    // --- JSON persistence ---

    private fun readAllLocked(): List<RecoveryRecord> {
        if (!file.exists()) return emptyList()
        return try {
            val json = file.readText()
            val arr = JSONArray(json)
            (0 until arr.length()).map { i -> fromJson(arr.getJSONObject(i)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeAllLocked(records: List<RecoveryRecord>) {
        try {
            val arr = JSONArray()
            records.forEach { arr.put(toJson(it)) }
            file.writeText(arr.toString())
        } catch (_: Exception) {}
    }

    private fun toJson(r: RecoveryRecord): JSONObject = JSONObject().apply {
        put("recoveryId", r.recoveryId)
        put("timestampMs", r.timestampMs)
        put("documentId", r.documentId)
        put("displayName", r.displayName)
        put("recoveryType", r.recoveryType.name)
        put("restoredPage", r.restoredPage)
        put("restoredZoom", r.restoredZoom.toDouble())
        put("checkpointAgeMs", r.checkpointAgeMs)
        put("triggerType", r.triggerType)
        put("recoveryDurationMs", r.recoveryDurationMs)
        put("recoverySource", r.recoverySource)
        put("validationResult", r.validationResult)
    }

    private fun fromJson(o: JSONObject): RecoveryRecord = RecoveryRecord(
        recoveryId = o.optString("recoveryId", UUID.randomUUID().toString()),
        timestampMs = o.optLong("timestampMs", 0),
        documentId = o.optString("documentId", ""),
        displayName = o.optString("displayName", "Protected Document"),
        recoveryType = try {
            RecoveryRecord.RecoveryType.valueOf(o.optString("recoveryType", "NORMAL"))
        } catch (_: Exception) { RecoveryRecord.RecoveryType.NORMAL },
        restoredPage = o.optInt("restoredPage", 0),
        restoredZoom = o.optDouble("restoredZoom", 1.0).toFloat(),
        checkpointAgeMs = o.optLong("checkpointAgeMs", 0),
        triggerType = o.optString("triggerType", "UNKNOWN"),
        recoveryDurationMs = o.optLong("recoveryDurationMs", 0),
        recoverySource = o.optString("recoverySource", ""),
        validationResult = o.optString("validationResult", "OK")
    )
}