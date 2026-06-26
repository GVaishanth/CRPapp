package com.example.crashresilientpdf.core.anomaly

/**
 * AnomalyState - Phase 4
 *
 * Structured, explainable anomaly report.
 * The UI consumes this model directly - no independent calculation.
 *
 * Every score is traceable to its contributing metrics.
 */
data class AnomalyState(
    val timestampMs: Long,
    val score: Double,  // 0.0 .. 1.0
    val riskTier: RiskTier,
    val metrics: MetricsSnapshot,
    val contributions: Contributions,
    val triggerReason: String?
) {
    enum class RiskTier { LOW, ELEVATED, HIGH }

    data class MetricsSnapshot(
        val memory: MetricValue,
        val cpu: MetricValue,
        val render: MetricValue,
        val scroll: MetricValue
    )

    data class MetricValue(
        val raw: Double,        // 0.0 .. 1.0, direct sensor reading
        val smoothed: Double,   // EWMA filtered
        val weight: Double      // contribution weight in final score
    ) {
        val contribution: Double get() = smoothed * weight
    }

    data class Contributions(
        val memory: Double,
        val cpu: Double,
        val render: Double,
        val scroll: Double
    )

    companion object {
        const val TIER_ELEVATED_THRESHOLD = 0.4
        const val TIER_HIGH_THRESHOLD = 0.7
    }
}
