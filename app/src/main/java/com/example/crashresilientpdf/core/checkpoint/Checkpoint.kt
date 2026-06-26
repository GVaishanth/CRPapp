package com.example.crashresilientpdf.core.checkpoint

import com.example.crashresilientpdf.core.anomaly.AnomalyState

/**
 * Checkpoint - Phase 5
 * Rich, versioned resilience state.
 *
 * Frozen anomaly engine is consumed, not modified.
 * AnomalyState -> Checkpoint trigger metadata
 */
data class Checkpoint(
    val checkpointVersion: Int = CHECKPOINT_VERSION,
    val recoveryVersion: Int = RECOVERY_VERSION,

    val documentId: String,
    val displayName: String,

    val page: Int,
    val zoom: Float = 1.0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,

    val timestampMs: Long,

    val anomalyScore: Float = 0f,
    val riskTier: Int = 0,  // 0=LOW, 1=ELEVATED, 2=HIGH
    val triggerReason: String? = null,

    val trigger: Trigger = Trigger.AUTO_INTERVAL
) {
    enum class Trigger {
        AUTO_ANOMALY,
        AUTO_INTERVAL,
        MANUAL,
        LIFECYCLE,
        RECOVERY
    }

    fun isValid(): Boolean {
        if (checkpointVersion < 1) return false
        if (documentId.isBlank()) return false
        if (page < 0) return false
        if (zoom <= 0f || zoom > 20f) return false
        if (timestampMs <= 0) return false
        if (anomalyScore < 0f || anomalyScore > 1f) return false
        return true
    }

    companion object {
        const val CHECKPOINT_VERSION = 2
        const val RECOVERY_VERSION = 2

        fun fromAnomalyState(
            documentId: String,
            displayName: String,
            page: Int,
            zoom: Float,
            offsetX: Float,
            offsetY: Float,
            anomaly: AnomalyState,
            trigger: Trigger
        ) = Checkpoint(
            documentId = documentId,
            displayName = displayName,
            page = page,
            zoom = zoom,
            offsetX = offsetX,
            offsetY = offsetY,
            timestampMs = anomaly.timestampMs,
            anomalyScore = anomaly.score.toFloat(),
            riskTier = when (anomaly.riskTier) {
                AnomalyState.RiskTier.LOW -> 0
                AnomalyState.RiskTier.ELEVATED -> 1
                AnomalyState.RiskTier.HIGH -> 2
            },
            triggerReason = anomaly.triggerReason,
            trigger = trigger
        )
    }
}

data class RecoveryMetadata(
    val timestampMs: Long,
    val recoveryDurationMs: Long = 0,
    val checkpointAgeMs: Long,
    val recoverySource: String,      // e.g. "checkpoint_v2", "fallback_v1"
    val fallbackUsed: Boolean,
    val documentId: String,
    val restoredPage: Int
)
