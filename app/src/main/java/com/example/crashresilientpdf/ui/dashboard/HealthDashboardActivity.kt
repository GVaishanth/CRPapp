package com.example.crashresilientpdf.ui.dashboard

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.crashresilientpdf.R
import com.example.crashresilientpdf.core.anomaly.AnomalyMonitor
import com.example.crashresilientpdf.core.anomaly.AnomalyState
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HealthDashboardActivity - Phase 7
 *
 * System Health Dashboard – operational console for the Crash Resilience Framework.
 * NOT a developer debugging screen.
 *
 * Four sections:
 *  1. Prediction  – AnomalyMonitor (FROZEN)
 *  2. Protection  – CheckpointManager (FROZEN)
 *  3. Recovery    – RecoveryJournal
 *  4. Framework Status – three-engine overview
 *
 * All values come directly from frozen engines via HealthDashboardViewModel.
 * UI is read-only – never affects anomaly monitoring, checkpoint timing, or recovery.
 */
class HealthDashboardActivity : AppCompatActivity() {

    private lateinit var viewModel: HealthDashboardViewModel
    private var uiJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_health_dashboard)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        viewModel = ViewModelProvider(this)[HealthDashboardViewModel::class.java]
        viewModel.attachAnomalyMonitor(AnomalyMonitorHolder.instance)
    }

    override fun onResume() {
        super.onResume()
        viewModel.start()
        startUiObservation()
    }

    override fun onPause() {
        super.onPause()
        uiJob?.cancel()
        viewModel.stop()
    }

    private fun startUiObservation() {
        val sparkline = findViewById<AnomalySparklineView>(R.id.anomalySparkline)

        // Prediction fields
        val predScore = findViewById<TextView>(R.id.predScore)
        val predTier = findViewById<TextView>(R.id.predTier)
        val predTrend = findViewById<TextView>(R.id.predTrend)
        val metricMem = findViewById<TextView>(R.id.metricMem)
        val metricCpu = findViewById<TextView>(R.id.metricCpu)
        val metricRender = findViewById<TextView>(R.id.metricRender)
        val metricScroll = findViewById<TextView>(R.id.metricScroll)

        // Protection fields
        val cpStatus = findViewById<TextView>(R.id.cpStatus)
        val cpLastAge = findViewById<TextView>(R.id.cpLastAge)
        val cpTotal = findViewById<TextView>(R.id.cpTotal)
        val cpDocStatus = findViewById<TextView>(R.id.cpDocStatus)
        val checkpointTimeline = findViewById<LinearLayout>(R.id.checkpointTimeline)

        // Recovery fields
        val recLast = findViewById<TextView>(R.id.recLast)
        val recType = findViewById<TextView>(R.id.recType)
        val recDuration = findViewById<TextView>(R.id.recDuration)
        val recReadiness = findViewById<TextView>(R.id.recReadiness)
        val recoveryTimeline = findViewById<LinearLayout>(R.id.recoveryTimeline)

        // Framework status
        val fwPrediction = findViewById<TextView>(R.id.fwPrediction)
        val fwCheckpoint = findViewById<TextView>(R.id.fwCheckpoint)
        val fwRecovery = findViewById<TextView>(R.id.fwRecovery)
        val fwVersion = findViewById<TextView>(R.id.fwVersion)
        val fwStatus = findViewById<TextView>(R.id.fwStatus)

        var lastScore = 0.0
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        uiJob = lifecycleScope.launch {
            viewModel.state.collectLatest { state ->
                // --- Prediction ---
                if (state.monitoringActive) {
                    predScore.text = "%.3f".format(state.anomalyScore)
                    val (tierText, tierColor) = when (state.riskTier) {
                        AnomalyState.RiskTier.HIGH -> "HIGH RISK" to R.color.resilience_high
                        AnomalyState.RiskTier.ELEVATED -> "ELEVATED" to R.color.resilience_elevated
                        AnomalyState.RiskTier.LOW -> "LOW" to R.color.resilience_healthy
                    }
                    predTier.text = tierText
                    predTier.setTextColor(ContextCompat.getColor(this@HealthDashboardActivity, tierColor))

                    val trend = state.anomalyScore - lastScore
                    predTrend.text = when {
                        trend > 0.03 -> "▲ rising"
                        trend < -0.03 -> "▼ falling"
                        else -> "● stable"
                    }
                    lastScore = state.anomalyScore

                    state.metrics?.let { m ->
                        metricMem.text = "Memory  %.2f".format(m.memory.smoothed)
                        metricCpu.text = "CPU  %.2f".format(m.cpu.smoothed)
                        metricRender.text = "Render  %.2f".format(m.render.smoothed)
                        metricScroll.text = "Scroll  %.2f".format(m.scroll.smoothed)
                    }

                    val checkpointTimes = state.checkpointHistory.map { it.timestampMs }.toSet()
                    sparkline.setSamples(state.anomalyHistory, checkpointTimes)
                } else {
                    predScore.text = "—"
                    predTier.text = "MONITORING INACTIVE"
                    predTier.setTextColor(ContextCompat.getColor(this@HealthDashboardActivity, R.color.md_theme_light_outline))
                    predTrend.text = "Open a protected document to begin"
                    metricMem.text = "Memory —"
                    metricCpu.text = "CPU —"
                    metricRender.text = "Render —"
                    metricScroll.text = "Scroll —"
                }

                // --- Protection ---
                cpStatus.text = state.checkpointStatus
                cpLastAge.text = if (state.lastCheckpointAgeMs > 0) "${state.lastCheckpointAgeMs / 1000}s ago • ${state.lastCheckpointTrigger}" else "—"
                cpTotal.text = if (state.totalCheckpoints > 0) "${state.totalCheckpoints} checkpoints" else "0"
                cpDocStatus.text = if (state.protectedDocuments > 0) "${state.protectedDocuments} document(s) protected" else "No active documents"

                // Checkpoint Timeline Binding
                checkpointTimeline.removeAllViews()
                if (state.checkpointHistory.isEmpty()) {
                    val tv = TextView(this@HealthDashboardActivity).apply {
                        text = "No checkpoints recorded"
                        setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_outline))
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    }
                    checkpointTimeline.addView(tv)
                } else {
                    state.checkpointHistory.forEach { cp ->
                        val tv = TextView(this@HealthDashboardActivity).apply {
                            val timeStr = dateFormat.format(Date(cp.timestampMs))
                            text = "• $timeStr — ${cp.displayName} (Page ${cp.page + 1}) [${cp.trigger.name}]"
                            setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_onSurfaceVariant))
                            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                            setPadding(0, 4, 0, 4)
                        }
                        checkpointTimeline.addView(tv)
                    }
                }

                // --- Recovery ---
                val lastRec = state.lastRecovery
                if (lastRec != null) {
                    recLast.text = "${lastRec.displayName} • Page ${lastRec.restoredPage + 1}"
                    recType.text = lastRec.recoveryType.name
                    recDuration.text = "${lastRec.recoveryDurationMs} ms"
                } else {
                    recLast.text = "No recoveries"
                    recType.text = "—"
                    recDuration.text = "—"
                }
                recReadiness.text = state.recoveryReadiness

                // Recovery Timeline Binding
                recoveryTimeline.removeAllViews()
                if (state.recoveryHistory.isEmpty()) {
                    val tv = TextView(this@HealthDashboardActivity).apply {
                        text = "No recovery events recorded"
                        setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_outline))
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    }
                    recoveryTimeline.addView(tv)
                } else {
                    state.recoveryHistory.forEach { rec ->
                        val tv = TextView(this@HealthDashboardActivity).apply {
                            val timeStr = dateFormat.format(Date(rec.timestampMs))
                            text = "• $timeStr — ${rec.displayName} (Page ${rec.restoredPage + 1}) [${rec.recoveryType.name} - ${rec.recoveryDurationMs}ms]"
                            setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_onSurfaceVariant))
                            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                            setPadding(0, 4, 0, 4)
                        }
                        recoveryTimeline.addView(tv)
                    }
                }

                // --- Framework Status ---
                fwPrediction.text = state.predictionEngineStatus
                fwCheckpoint.text = state.checkpointEngineStatus
                fwRecovery.text = state.recoveryEngineStatus
                fwVersion.text = state.frameworkVersion
                fwStatus.text = state.frameworkStatus
                val fwColor = when (state.riskTier) {
                    AnomalyState.RiskTier.HIGH -> R.color.resilience_high
                    AnomalyState.RiskTier.ELEVATED -> R.color.resilience_elevated
                    else -> R.color.resilience_healthy
                }
                fwStatus.setTextColor(ContextCompat.getColor(this@HealthDashboardActivity, fwColor))
            }
        }
    }
}

/**
 * AnomalyMonitorHolder – Phase 7
 *
 * Lightweight bridge so HealthDashboard can observe the live
 * AnomalyMonitor running in ProtectedViewerActivity.
 *
 * This does NOT create a new monitor, does NOT affect scoring.
 * Pure observation – frozen engine remains single source of truth.
 */
object AnomalyMonitorHolder {
    @Volatile var instance: AnomalyMonitor? = null
}