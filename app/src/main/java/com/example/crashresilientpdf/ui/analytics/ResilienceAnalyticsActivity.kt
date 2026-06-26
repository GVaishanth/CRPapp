package com.example.crashresilientpdf.ui.analytics

import android.os.Bundle
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.crashresilientpdf.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ResilienceAnalyticsActivity - Phase 8
 *
 * Executive resilience report console.
 * Completely read-only layer – never modifies core engine state.
 * Never performs expensive calculations on the main thread.
 */
class ResilienceAnalyticsActivity : AppCompatActivity() {

    private lateinit var viewModel: ResilienceAnalyticsViewModel
    private var observeJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_resilience_analytics)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.action_export_json -> {
                    Toast.makeText(this, getString(R.string.toast_exporting), Toast.LENGTH_SHORT).show()
                    viewModel.exportToJson { file ->
                        Toast.makeText(this, getString(R.string.toast_export_complete, file.absolutePath), Toast.LENGTH_LONG).show()
                    }
                    true
                }
                R.id.action_export_csv -> {
                    Toast.makeText(this, getString(R.string.toast_exporting), Toast.LENGTH_SHORT).show()
                    viewModel.exportToCsv { file ->
                        Toast.makeText(this, getString(R.string.toast_export_complete, file.absolutePath), Toast.LENGTH_LONG).show()
                    }
                    true
                }
                else -> false
            }
        }

        viewModel = ViewModelProvider(this)[ResilienceAnalyticsViewModel::class.java]
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadAnalytics()
        startObservation()
    }

    override fun onPause() {
        super.onPause()
        observeJob?.cancel()
    }

    private fun startObservation() {
        // Overview
        val overviewSessions = findViewById<TextView>(R.id.overviewSessions)
        val overviewCheckpoints = findViewById<TextView>(R.id.overviewCheckpoints)
        val overviewRecoveries = findViewById<TextView>(R.id.overviewRecoveries)
        val overviewSuccessRate = findViewById<TextView>(R.id.overviewSuccessRate)
        val overviewVersion = findViewById<TextView>(R.id.overviewVersion)

        // Prediction
        val predAvgScore = findViewById<TextView>(R.id.predAvgScore)
        val tierLowBar = findViewById<LinearProgressIndicator>(R.id.tierLowBar)
        val tierLowText = findViewById<TextView>(R.id.tierLowText)
        val tierElevatedBar = findViewById<LinearProgressIndicator>(R.id.tierElevatedBar)
        val tierElevatedText = findViewById<TextView>(R.id.tierElevatedText)
        val tierHighBar = findViewById<LinearProgressIndicator>(R.id.tierHighBar)
        val tierHighText = findViewById<TextView>(R.id.tierHighText)

        val contribMemBar = findViewById<LinearProgressIndicator>(R.id.contribMemBar)
        val contribMemText = findViewById<TextView>(R.id.contribMemText)
        val contribCpuBar = findViewById<LinearProgressIndicator>(R.id.contribCpuBar)
        val contribCpuText = findViewById<TextView>(R.id.contribCpuText)
        val contribRenderBar = findViewById<LinearProgressIndicator>(R.id.contribRenderBar)
        val contribRenderText = findViewById<TextView>(R.id.contribRenderText)
        val contribScrollBar = findViewById<LinearProgressIndicator>(R.id.contribScrollBar)
        val contribScrollText = findViewById<TextView>(R.id.contribScrollText)

        val predRollingTrends = findViewById<TextView>(R.id.predRollingTrends)
        val predTriggerReasons = findViewById<TextView>(R.id.predTriggerReasons)

        // Protection
        val protCpPerSession = findViewById<TextView>(R.id.protCpPerSession)
        val protAvgAge = findViewById<TextView>(R.id.protAvgAge)
        val protTriggerDist = findViewById<TextView>(R.id.protTriggerDist)
        val protManualVsAuto = findViewById<TextView>(R.id.protManualVsAuto)
        val protTimeline = findViewById<LinearLayout>(R.id.protTimeline)

        // Recovery
        val recAvgDuration = findViewById<TextView>(R.id.recAvgDuration)
        val recAvgAgeAtRec = findViewById<TextView>(R.id.recAvgAgeAtRec)
        val recNormalVsFallback = findViewById<TextView>(R.id.recNormalVsFallback)
        val recTimeline = findViewById<LinearLayout>(R.id.recTimeline)

        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        observeJob = lifecycleScope.launch {
            viewModel.state.collectLatest { state ->
                if (state.isLoading) return@collectLatest

                // Overview binding
                overviewSessions.text = state.protectedSessionsCount.toString()
                overviewCheckpoints.text = state.totalCheckpointsCount.toString()
                overviewRecoveries.text = state.totalRecoveriesCount.toString()
                overviewSuccessRate.text = state.recoverySuccessRateText
                overviewVersion.text = state.frameworkVersionText

                // Prediction binding
                predAvgScore.text = state.avgAnomalyScoreText
                tierLowBar.setProgress(state.tierLowPct, true)
                tierLowText.text = "${state.tierLowPct}%"
                tierElevatedBar.setProgress(state.tierElevatedPct, true)
                tierElevatedText.text = "${state.tierElevatedPct}%"
                tierHighBar.setProgress(state.tierHighPct, true)
                tierHighText.text = "${state.tierHighPct}%"

                contribMemBar.setProgress(state.contribMemPct, true)
                contribMemText.text = "${state.contribMemPct}%"
                contribCpuBar.setProgress(state.contribCpuPct, true)
                contribCpuText.text = "${state.contribCpuPct}%"
                contribRenderBar.setProgress(state.contribRenderPct, true)
                contribRenderText.text = "${state.contribRenderPct}%"
                contribScrollBar.setProgress(state.contribScrollPct, true)
                contribScrollText.text = "${state.contribScrollPct}%"

                predRollingTrends.text = state.rollingTrendsText
                predTriggerReasons.text = state.triggerReasonsText

                // Protection binding
                protCpPerSession.text = state.checkpointsPerSessionText
                protAvgAge.text = state.avgCheckpointAgeText
                protTriggerDist.text = state.triggerDistributionText
                protManualVsAuto.text = state.manualVsAutoText

                protTimeline.removeAllViews()
                if (state.checkpointTimeline.isEmpty()) {
                    val tv = TextView(this@ResilienceAnalyticsActivity).apply {
                        text = "No checkpoints recorded in history"
                        setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_outline))
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    }
                    protTimeline.addView(tv)
                } else {
                    state.checkpointTimeline.forEach { cp ->
                        val tv = TextView(this@ResilienceAnalyticsActivity).apply {
                            val timeStr = dateFormat.format(Date(cp.timestampMs))
                            text = "• $timeStr — ${cp.displayName} (Page ${cp.page + 1}) [${cp.trigger.name}]"
                            setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_onSurfaceVariant))
                            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                            setPadding(0, 4, 0, 4)
                        }
                        protTimeline.addView(tv)
                    }
                }

                // Recovery binding
                recAvgDuration.text = state.avgRecoveryDurationText
                recAvgAgeAtRec.text = state.avgAgeAtRecoveryText
                recNormalVsFallback.text = state.recNormalVsFallbackText

                recTimeline.removeAllViews()
                if (state.recoveryTimeline.isEmpty()) {
                    val tv = TextView(this@ResilienceAnalyticsActivity).apply {
                        text = "No recovery events recorded in history"
                        setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_outline))
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                    }
                    recTimeline.addView(tv)
                } else {
                    state.recoveryTimeline.forEach { r ->
                        val tv = TextView(this@ResilienceAnalyticsActivity).apply {
                            val timeStr = dateFormat.format(Date(r.timestampMs))
                            text = "• $timeStr — ${r.displayName} (Page ${r.restoredPage + 1}) [${r.recoveryType.name} - ${r.recoveryDurationMs}ms]"
                            setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_onSurfaceVariant))
                            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
                            setPadding(0, 4, 0, 4)
                        }
                        recTimeline.addView(tv)
                    }
                }
            }
        }
    }
}
