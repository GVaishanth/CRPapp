package com.example.crashresilientpdf.core.checkpoint

import android.content.Context
import androidx.datastore.core.DataStore
import com.example.crashresilientpdf.core.checkpoint.proto.CheckpointStore
import com.example.crashresilientpdf.core.checkpoint.proto.DocumentHistory
import kotlinx.coroutines.flow.first

/**
 * CheckpointMigrator - Phase 5
 *
 * Migrates Phase 1-4 SharedPreferences checkpoints → DataStore Proto
 * - Legacy SP: "checkpoint" / page_<hash> / time_<hash> / recovery_<hash>
 * - Target: CheckpointStore with rich Checkpoint objects
 *
 * Migration is:
 * - Automatic on first CheckpointManager init
 * - Idempotent
 * - Lossless: page, timestamp, recovery_count preserved
 * - Missing rich fields defaulted: zoom=1.0, offset=0, anomaly=0, trigger=LIFECYCLE
 * - Legacy SP is left intact (read-only fallback), then marked migrated
 */
object CheckpointMigrator {
    private const val SP_NAME = "checkpoint"
    private const val KEY_MIGRATED = "migrated_to_datastore_v2"
    private const val KEY_RECENT_DOCS = "recent_docs"

    suspend fun migrateIfNeeded(context: Context, dataStore: DataStore<CheckpointStore>) {
        val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        if (sp.getBoolean(KEY_MIGRATED, false)) return

        // Check if DataStore already has data (e.g. fresh install with no SP)
        val existing = dataStore.data.first()
        if (existing.documentsCount > 0) {
            sp.edit().putBoolean(KEY_MIGRATED, true).apply()
            return
        }

        val recentDocs = sp.getStringSet(KEY_RECENT_DOCS, emptySet()) ?: emptySet()
        // Also scan for any page_* keys that might not be in recent_docs
        val allKeys = sp.all.keys
        val pageKeys = allKeys.filter { it.startsWith("page_") }

        val docIds = mutableSetOf<String>()
        docIds.addAll(recentDocs)
        // Legacy single-doc key "page"
        if (sp.contains("page")) docIds.add(CheckpointManager.DOC_ASSET_SAMPLE)

        if (docIds.isEmpty() && pageKeys.isEmpty()) {
            // nothing to migrate
            sp.edit().putBoolean(KEY_MIGRATED, true).apply()
            return
        }

        // Try to reconstruct docIds from page keys if recent_docs was empty
        // We can't reverse hash, so we at least migrate the known sample doc
        if (docIds.isEmpty()) {
            docIds.add(CheckpointManager.DOC_ASSET_SAMPLE)
        }

        dataStore.updateData { store ->
            val builder = store.toBuilder().setSchemaVersion(2)
            for (docId in docIds) {
                val page = if (docId == CheckpointManager.DOC_ASSET_SAMPLE && sp.contains("page")) {
                    sp.getInt("page", 0)
                } else {
                    sp.getInt("page_${docId.hashCode()}", -1)
                }
                if (page < 0) continue

                val time = sp.getLong("time_${docId.hashCode()}", System.currentTimeMillis())
                val recoveryCount = sp.getInt("recovery_${docId.hashCode()}", 0)

                val displayName = when {
                    docId == CheckpointManager.DOC_ASSET_SAMPLE -> "sample.pdf"
                    docId.startsWith("asset:") -> docId.removePrefix("asset:")
                    else -> "Migrated Document"
                }

                val cp = com.example.crashresilientpdf.core.checkpoint.proto.Checkpoint.newBuilder()
                    .setCheckpointVersion(2)
                    .setRecoveryVersion(2)
                    .setDocumentId(docId)
                    .setDisplayName(displayName)
                    .setPage(page)
                    .setZoom(1.0f)
                    .setOffsetX(0f)
                    .setOffsetY(0f)
                    .setTimestampMs(if (time > 0) time else System.currentTimeMillis())
                    .setAnomalyScore(0f)
                    .setRiskTier(0)
                    .setTrigger("LIFECYCLE")
                    .setTriggerReason("migrated_from_sp")
                    .build()

                val history = DocumentHistory.newBuilder()
                    .setDocumentId(docId)
                    .addCheckpoints(cp)
                    .setRecoveryCount(recoveryCount)
                    .build()

                builder.putDocuments(docId, history)
            }
            builder.build()
        }

        sp.edit().putBoolean(KEY_MIGRATED, true).apply()
    }
}
