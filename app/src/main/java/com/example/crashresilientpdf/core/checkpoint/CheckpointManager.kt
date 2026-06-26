package com.example.crashresilientpdf.core.checkpoint

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.example.crashresilientpdf.core.anomaly.AnomalyState
import com.example.crashresilientpdf.core.checkpoint.proto.CheckpointStore
import com.example.crashresilientpdf.core.checkpoint.proto.CheckpointStoreSerializer
import com.example.crashresilientpdf.core.checkpoint.proto.DocumentHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

private val Context.checkpointDataStore: DataStore<CheckpointStore> by dataStore(
    fileName = "checkpoint_store.pb",
    serializer = CheckpointStoreSerializer
)

/**
 * CheckpointManager - Phase 5
 *
 * Rich, versioned, crash-safe checkpoints.
 * Proto DataStore, atomic writes, corruption handling, ring buffer.
 *
 * ANOMALY ENGINE IS FROZEN - consumes AnomalyState, does not modify it.
 *
 * Backward compat:
 *  - Migrates Phase 1-4 SharedPreferences "checkpoint" automatically on first init
 *  - Legacy CheckpointManager.saveCheckpoint(page:Int) still works -> creates v2 Checkpoint
 */
class CheckpointManager(private val context: Context) {

    private val dataStore = context.checkpointDataStore
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val writeMutex = Mutex()

    // In-memory debounce
    private val lastWriteMs = ConcurrentHashMap<String, Long>()

    init {
        // Migrate SP -> DataStore once, blocking (small, safe at startup)
        runBlocking {
            CheckpointMigrator.migrateIfNeeded(context, dataStore)
        }
    }

    // -------------------------------------------------------------------------
    // Save
    // -------------------------------------------------------------------------

    suspend fun saveCheckpoint(checkpoint: Checkpoint) {
        if (!checkpoint.isValid()) return

        // Debounce: max 1 write / 800ms per document (Phase 5 spec)
        val now = System.currentTimeMillis()
        val last = lastWriteMs[checkpoint.documentId] ?: 0L
        if (now - last < 800 && checkpoint.trigger == Checkpoint.Trigger.AUTO_ANOMALY) {
            return
        }

        writeMutex.withLock {
            dataStore.updateData { store ->
                val existing = store.documentsMap[checkpoint.documentId]
                val history = existing?.checkpointsList ?: emptyList()

                // Ring buffer: keep newest 3
                val newHistory = listOf(checkpoint.toProto()) + history.take(2)

                val newDocHistory = (existing?.toBuilder() ?: DocumentHistory.newBuilder()
                    .setDocumentId(checkpoint.documentId))
                    .clearCheckpoints()
                    .addAllCheckpoints(newHistory)
                    .build()

                store.toBuilder()
                    .putDocuments(checkpoint.documentId, newDocHistory)
                    .setSchemaVersion(Checkpoint.CHECKPOINT_VERSION)
                    .build()
            }
        }
        lastWriteMs[checkpoint.documentId] = now
    }

    /** Convenience: save from AnomalyState */
    suspend fun saveCheckpoint(
        documentId: String,
        displayName: String,
        page: Int,
        zoom: Float,
        offsetX: Float,
        offsetY: Float,
        anomaly: AnomalyState,
        trigger: Checkpoint.Trigger
    ) {
        val cp = Checkpoint.fromAnomalyState(
            documentId, displayName, page, zoom, offsetX, offsetY, anomaly, trigger
        )
        saveCheckpoint(cp)
    }

    /** Phase 1-4 compat: save page only */
    fun saveCheckpoint(docId: String, page: Int) {
        val cp = Checkpoint(
            documentId = docId,
            displayName = displayNameFor(docId),
            page = page,
            timestampMs = System.currentTimeMillis(),
            trigger = Checkpoint.Trigger.AUTO_INTERVAL
        )
        scope.launch { saveCheckpoint(cp) }
    }

    fun saveCheckpoint(page: Int) = saveCheckpoint(DOC_ASSET_SAMPLE, page)

    // -------------------------------------------------------------------------
    // Restore - with validation + fallback
    // -------------------------------------------------------------------------

    suspend fun restoreCheckpoint(docId: String): Checkpoint? {
        val store = dataStore.data.first()
        val history = store.documentsMap[docId] ?: return null

        for (proto in history.checkpointsList) {
            val cp = proto.toDomain()
            if (cp.isValid() && cp.documentId == docId) {
                return cp
            }
        }
        return null
    }

    /** Phase 1-4 compat: returns page Int */
    fun restoreCheckpoint(docId: String, defaultPage: Int = 0): Int {
        return runBlocking {
            restoreCheckpoint(docId)?.page ?: defaultPage
        }
    }

    fun restoreCheckpoint(): Int = restoreCheckpoint(DOC_ASSET_SAMPLE, 0)

    // -------------------------------------------------------------------------
    // History
    // -------------------------------------------------------------------------

    fun checkpointHistoryFlow(docId: String): Flow<List<Checkpoint>> =
        dataStore.data.map { store ->
            store.documentsMap[docId]?.checkpointsList?.mapNotNull {
                val cp = it.toDomain()
                if (cp.isValid()) cp else null
            } ?: emptyList()
        }

    suspend fun getCheckpointHistory(docId: String): List<Checkpoint> =
        checkpointHistoryFlow(docId).first()

    // -------------------------------------------------------------------------
    // Recovery metadata
    // -------------------------------------------------------------------------

    suspend fun recordRecovery(meta: RecoveryMetadata) {
        dataStore.updateData { store ->
            val existing = store.documentsMap[meta.documentId]
                ?: DocumentHistory.newBuilder().setDocumentId(meta.documentId).build()

            val updated = existing.toBuilder()
                .setRecoveryCount(existing.recoveryCount + 1)
                .setLastRecoveryTimestampMs(meta.timestampMs)
                .setLastRecoveryCheckpointAgeMs(meta.checkpointAgeMs)
                .setLastRecoveryFallbackUsed(meta.fallbackUsed)
                .setLastRecoverySource(meta.recoverySource)
                .build()

            store.toBuilder()
                .putDocuments(meta.documentId, updated)
                .build()
        }
    }

    fun incrementRecoveryCount(docId: String) {
        // Phase 2 compat - no-op, recovery metadata now in recordRecovery()
        // Keep for binary compat
        scope.launch {
            val cp = restoreCheckpoint(docId) ?: return@launch
            recordRecovery(
                RecoveryMetadata(
                    timestampMs = System.currentTimeMillis(),
                    checkpointAgeMs = System.currentTimeMillis() - cp.timestampMs,
                    recoverySource = "legacy_increment",
                    fallbackUsed = false,
                    documentId = docId,
                    restoredPage = cp.page
                )
            )
        }
    }

    fun getRecoveryCount(docId: String): Int = runBlocking {
        val store = dataStore.data.first()
        store.documentsMap[docId]?.recoveryCount ?: 0
    }

    fun getLastCheckpointTime(docId: String): Long = runBlocking {
        restoreCheckpoint(docId)?.timestampMs ?: 0L
    }

    // -------------------------------------------------------------------------
    // Recent sessions
    // -------------------------------------------------------------------------

    fun getRecentSessions(): List<ProtectedSession> = runBlocking {
        val store = dataStore.data.first()
        store.documentsMap.values.mapNotNull { hist ->
            val cp = hist.checkpointsList.firstOrNull()?.toDomain()?.takeIf { it.isValid() }
                ?: return@mapNotNull null
            ProtectedSession(
                docId = cp.documentId,
                displayName = cp.displayName,
                lastPage = cp.page,
                lastCheckpointMs = cp.timestampMs,
                recoveryCount = hist.recoveryCount
            )
        }.sortedByDescending { it.lastCheckpointMs }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun displayNameFor(docId: String): String {
        return when {
            docId == DOC_ASSET_SAMPLE -> "sample.pdf"
            docId.startsWith("content://") -> try {
                Uri.parse(docId).lastPathSegment?.substringAfterLast('/') ?: "Protected Document"
            } catch (_: Exception) { "Protected Document" }
            docId.startsWith("asset:") -> docId.removePrefix("asset:")
            else -> "Protected Document"
        }
    }

    companion object {
        const val DOC_ASSET_SAMPLE = "asset:sample.pdf"
    }
}

// -------------------------------------------------------------------------
// Proto <-> Domain mapping
// -------------------------------------------------------------------------

private fun Checkpoint.toProto(): com.example.crashresilientpdf.core.checkpoint.proto.Checkpoint =
    com.example.crashresilientpdf.core.checkpoint.proto.Checkpoint.newBuilder()
        .setCheckpointVersion(checkpointVersion)
        .setRecoveryVersion(recoveryVersion)
        .setDocumentId(documentId)
        .setDisplayName(displayName)
        .setPage(page)
        .setZoom(zoom)
        .setOffsetX(offsetX)
        .setOffsetY(offsetY)
        .setTimestampMs(timestampMs)
        .setAnomalyScore(anomalyScore)
        .setRiskTier(riskTier)
        .setTriggerReason(triggerReason ?: "")
        .setTrigger(trigger.name)
        .build()

private fun com.example.crashresilientpdf.core.checkpoint.proto.Checkpoint.toDomain(): Checkpoint =
    Checkpoint(
        checkpointVersion = if (checkpointVersion != 0) checkpointVersion else 2,
        recoveryVersion = if (recoveryVersion != 0) recoveryVersion else 2,
        documentId = documentId,
        displayName = if (displayName.isNotBlank()) displayName else documentId,
        page = page,
        zoom = if (zoom != 0f) zoom else 1f,
        offsetX = offsetX,
        offsetY = offsetY,
        timestampMs = timestampMs,
        anomalyScore = anomalyScore,
        riskTier = riskTier,
        triggerReason = triggerReason.takeIf { it.isNotBlank() },
        trigger = try {
            Checkpoint.Trigger.valueOf(trigger)
        } catch (_: Exception) {
            Checkpoint.Trigger.AUTO_INTERVAL
        }
    )
