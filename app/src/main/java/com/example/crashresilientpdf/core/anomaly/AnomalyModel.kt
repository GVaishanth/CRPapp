package com.example.crashresilientpdf.core.anomaly

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * AnomalyModel - Phase 4
 *
 * Explainable, lightweight crash-risk scoring.
 * NO machine learning frameworks. NO black box.
 *
 * Pipeline per metric:
 *   raw (0..1) → EWMA smoothed → weighted contribution → sum
 *
 * Output: 0.0 .. 1.0, with per-metric breakdown.
 *
 * Smoothing prevents transient spikes from triggering checkpoints.
 * Sustained elevated risk → checkpoint.
 */
class AnomalyModel {

    // EWMA smoothing factor - higher = faster response
    // 0.35 → ~3 sample half-life at 1 Hz = ~2 sec to react to sustained change
    //       single spike decays to <10% in ~6 sec
    private val alpha = 0.35

    // Per-metric smoothed state
    private var sMem = 0.0
    private var sCpu = 0.0
    private var sRender = 0.0
    private var sScroll = 0.0

    // Weights - sum to 1.0, preserved from Phase 1 philosophy
    // Memory remains dominant predictor for PDF OOM crashes
    data class Weights(
        val memory: Double = 0.40,
        val cpu: Double = 0.20,
        val render: Double = 0.20,
        val scroll: Double = 0.20
    )
    var weights = Weights()
        private set

    // For explainability: track last raw values and trend
    private var lastScore = 0.0

    fun reset() {
        sMem = 0.0; sCpu = 0.0; sRender = 0.0; sScroll = 0.0
        lastScore = 0.0
    }

    /**
     * Evaluate one sample.
     * All inputs must be 0.0 .. 1.0 normalized.
     */
    fun evaluate(
        memoryRaw: Double,
        cpuRaw: Double,
        renderRaw: Double,
        scrollRaw: Double,
        timestampMs: Long = System.currentTimeMillis()
    ): AnomalyState {

        val m = memoryRaw.coerceIn(0.0, 1.0)
        val c = cpuRaw.coerceIn(0.0, 1.0)
        val r = renderRaw.coerceIn(0.0, 1.0)
        val s = scrollRaw.coerceIn(0.0, 1.0)

        // EWMA smoothing - first sample seeds directly
        sMem = if (sMem == 0.0) m else alpha * m + (1 - alpha) * sMem
        sCpu = if (sCpu == 0.0) c else alpha * c + (1 - alpha) * sCpu
        sRender = if (sRender == 0.0) r else alpha * r + (1 - alpha) * sRender
        sScroll = if (sScroll == 0.0) s else alpha * s + (1 - alpha) * sScroll

        val w = weights
        val contribMem = sMem * w.memory
        val contribCpu = sCpu * w.cpu
        val contribRender = sRender * w.render
        val contribScroll = sScroll * w.scroll

        val score = (contribMem + contribCpu + contribRender + contribScroll)
            .coerceIn(0.0, 1.0)

        val tier = when {
            score >= AnomalyState.TIER_HIGH_THRESHOLD -> AnomalyState.RiskTier.HIGH
            score >= AnomalyState.TIER_ELEVATED_THRESHOLD -> AnomalyState.RiskTier.ELEVATED
            else -> AnomalyState.RiskTier.LOW
        }

        // Explainable trigger reason
        val triggerReason = buildTriggerReason(
            score, lastScore,
            sMem to w.memory,
            sCpu to w.cpu,
            sRender to w.render,
            sScroll to w.scroll
        )
        lastScore = score

        return AnomalyState(
            timestampMs = timestampMs,
            score = score,
            riskTier = tier,
            metrics = AnomalyState.MetricsSnapshot(
                memory = AnomalyState.MetricValue(m, sMem, w.memory),
                cpu = AnomalyState.MetricValue(c, sCpu, w.cpu),
                render = AnomalyState.MetricValue(r, sRender, w.render),
                scroll = AnomalyState.MetricValue(s, sScroll, w.scroll)
            ),
            contributions = AnomalyState.Contributions(
                memory = contribMem,
                cpu = contribCpu,
                render = contribRender,
                scroll = contribScroll
            ),
            triggerReason = triggerReason
        )
    }

    private fun buildTriggerReason(
        score: Double,
        lastScore: Double,
        mem: Pair<Double, Double>,
        cpu: Pair<Double, Double>,
        render: Pair<Double, Double>,
        scroll: Pair<Double, Double>
    ): String? {
        val delta = score - lastScore
        if (abs(delta) < 0.05) return null // stable

        // Find largest contributor to the change
        val contribs = listOf(
            "memory" to mem.first * mem.second,
            "cpu" to cpu.first * cpu.second,
            "render" to render.first * render.second,
            "scroll" to scroll.first * scroll.second
        )
        val top = contribs.maxByOrNull { it.second } ?: return null
        val dir = if (delta > 0) "rising" else "falling"
        return "${top.first} $dir"
    }

    // For testing / tuning
    fun setWeights(weights: Weights) {
        val sum = weights.memory + weights.cpu + weights.render + weights.scroll
        require(abs(sum - 1.0) < 0.001) { "Weights must sum to 1.0, got $sum" }
        this.weights = weights
    }
}
