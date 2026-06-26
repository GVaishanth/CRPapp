package com.example.crashresilientpdf.core.anomaly

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AnomalyMonitor - Phase 4
 *
 * Real metrics, explainable scoring.
 *
 * ANOMALY ENGINE IS FROZEN - DO NOT MODIFY
 * - Weights: memory 0.40 / cpu 0.20 / render 0.20 / scroll 0.20
 * - Thresholds: LOW <0.4 / ELEVATED 0.4-0.7 / HIGH ≥0.7
 * - EWMA α = 0.35
 * - Score 0.0 .. 1.0
 *
 * See docs/ANOMALY_MODEL.md
 */
class AnomalyMonitor(
    context: Context,
    private val scrollSpeedProvider: () -> Double
) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private val collector = MetricsCollector(context)
    private val model = AnomalyModel()

    private val _state = MutableStateFlow(
        AnomalyState(
            timestampMs = System.currentTimeMillis(),
            score = 0.0,
            riskTier = AnomalyState.RiskTier.LOW,
            metrics = AnomalyState.MetricsSnapshot(
                AnomalyState.MetricValue(0.0, 0.0, 0.40),
                AnomalyState.MetricValue(0.0, 0.0, 0.20),
                AnomalyState.MetricValue(0.0, 0.0, 0.20),
                AnomalyState.MetricValue(0.0, 0.0, 0.20)
            ),
            contributions = AnomalyState.Contributions(0.0, 0.0, 0.0, 0.0),
            triggerReason = null
        )
    )
    val stateFlow: StateFlow<AnomalyState> = _state.asStateFlow()

    /** Backward compat - Phase 1-3 callers */
    val anomalyScore: Double get() = _state.value.score

    private var ticker: Runnable? = null

    /**
     * Phase 1-3 compat: start(onTick: (score: Double) -> Unit)
     */
    fun start(onTick: (score: Double) -> Unit) {
        start { state -> onTick(state.score) }
    }

    /**
     * Phase 4: start with full AnomalyState
     */
    fun start(onState: (AnomalyState) -> Unit) {
        if (running) return
        running = true
        collector.start()
        model.reset()

        ticker = object : Runnable {
            override fun run() {
                val mem = collector.readMemory()
                val cpu = collector.readCpu()
                val render = collector.readRender()
                val scrollRaw = scrollSpeedProvider()
                val scroll = collector.normalizeScroll(scrollRaw)

                val state = model.evaluate(mem, cpu, render, scroll)
                _state.value = state
                onState(state)

                if (running) {
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(ticker!!)
    }

    fun stop() {
        running = false
        ticker?.let { handler.removeCallbacks(it) }
        collector.stop()
    }

    /**
     * Phase 1 compat - direct compute, no smoothing
     * Used by unit tests only.
     */
    fun computeAnomaly(m: Double, c: Double, s: Double, r: Double): Double {
        val w = model.weights
        return (w.memory * m + w.cpu * c + w.scroll * s + w.render * r).coerceIn(0.0, 1.0)
    }

    companion object {
        /** Checkpoint threshold - UNCHANGED from Phase 1 */
        const val HIGH_RISK_THRESHOLD = 0.7
    }
}
