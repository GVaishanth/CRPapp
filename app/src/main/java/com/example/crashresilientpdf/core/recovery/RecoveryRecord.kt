package com.example.crashresilientpdf.core.recovery

/**
 * RecoveryRecord - Phase 6
 *
 * Factual recovery event log.
 * Separate from Checkpoint history.
 *
 * Every field comes from recorded metadata – never estimated.
 */
data class RecoveryRecord(
    val recoveryId: String,
    val timestampMs: Long,

    val documentId: String,
    val displayName: String,

    val recoveryType: RecoveryType,

    val restoredPage: Int,
    val restoredZoom: Float,

    val checkpointAgeMs: Long,
    val triggerType: String,
    val recoveryDurationMs: Long,
    val recoverySource: String,
    val validationResult: String
) {
    enum class RecoveryType { NORMAL, FALLBACK }

    val checkpointAgeSeconds: Long get() = checkpointAgeMs / 1000
    val checkpointAgeText: String get() = when {
        checkpointAgeMs < 5000 -> "just now"
        checkpointAgeMs < 60000 -> "${checkpointAgeMs / 1000}s ago"
        else -> "${checkpointAgeMs / 60000}m ago"
    }
}
