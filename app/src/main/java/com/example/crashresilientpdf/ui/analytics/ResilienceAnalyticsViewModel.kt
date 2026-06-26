package com.example.crashresilientpdf.ui.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.crashresilientpdf.core.anomaly.AnomalyState
import com.example.crashresilientpdf.core.checkpoint.Checkpoint
import com.example.crashresilientpdf.core.checkpoint.CheckpointManager
import com.example.crashresilientpdf.core.recovery.RecoveryJournal
import com.example.crashresilientpdf.core.recovery.RecoveryRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

/**
 * ResilienceAnalyticsViewModel - Phase 8 & Phase 9 Refinement
 *
 * Precomputes and aggregates all resilience telemetry on Dispatchers.IO.
 * Completely read-only layer – never modifies core engine state.
 * Never performs expensive calculations on the main thread.
 *
 * Phase 9 Architectural Refinement:
 * Relies entirely on persisted historical records (Checkpoint history & Recovery Journal).
 * Live operational observation remains exclusively in System Health Dashboard.
 */
class ResilienceAnalyticsViewModel(app: Application) : AndroidViewModel(app) {

    private val checkpointManager = CheckpointManager(app)
    private val recoveryJournal = RecoveryJournal(app)

    data class AnalyticsState(
        val isLoading: Boolean = true,

        // Framework Overview
        val protectedSessionsCount: Int = 0,
        val totalCheckpointsCount: Int = 0,
        val totalRecoveriesCount: Int = 0,
        val recoverySuccessRateText: String = "100%",
        val frameworkVersionText: String = "2.0-phase9",

        // Prediction Analytics (Historical from Persisted Checkpoints)
        val avgAnomalyScoreText: String = "0.000",
        val tierLowPct: Int = 100,
        val tierElevatedPct: Int = 0,
        val tierHighPct: Int = 0,
        val contribMemPct: Int = 40,
        val contribCpuPct: Int = 20,
        val contribRenderPct: Int = 20,
        val contribScrollPct: Int = 20,
        val rollingTrendsText: String = "No historical anomaly data persisted",
        val triggerReasonsText: String = "No anomaly triggers recorded",

        // Protection Analytics
        val checkpointsPerSessionText: String = "0.0",
        val avgCheckpointAgeText: String = "0.0s",
        val triggerDistributionText: String = "No triggers recorded",
        val manualVsAutoText: String = "0% Manual / 100% Automatic",
        val checkpointTimeline: List<Checkpoint> = emptyList(),

        // Recovery Analytics
        val recNormalVsFallbackText: String = "100% Normal / 0% Fallback",
        val avgRecoveryDurationText: String = "0 ms",
        val avgAgeAtRecoveryText: String = "0.0s",
        val recoveryTimeline: List<RecoveryRecord> = emptyList()
    )

    private val _state = MutableStateFlow(AnalyticsState())
    val state: StateFlow<AnalyticsState> = _state.asStateFlow()

    private var calcJob: Job? = null

    fun loadAnalytics() {
        if (calcJob?.isActive == true) return
        calcJob = viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()

            // 1. Fetch Recorded Data from Persisted Storage
            val sessions = try { checkpointManager.getRecentSessions() } catch (_: Exception) { emptyList() }
            val allCheckpoints = mutableListOf<Checkpoint>()
            try {
                sessions.forEach { s ->
                    allCheckpoints.addAll(checkpointManager.getCheckpointHistory(s.docId))
                }
            } catch (_: Exception) {}

            val recoveries = try { recoveryJournal.getHistory() } catch (_: Exception) { emptyList() }

            // --- Framework Overview Aggregations ---
            val sessionsCount = sessions.size
            val cpCount = allCheckpoints.size
            val recCount = recoveries.size
            val successfulRecs = recoveries.count { it.validationResult == "OK" }
            val successRate = if (recCount > 0) "${((successfulRecs.toDouble() / recCount) * 100).roundToInt()}%" else "100%"

            // --- Prediction Analytics Aggregations (From Persisted Checkpoints) ---
            var avgScoreText = "0.000"
            var tLow = 100; var tElev = 0; var tHigh = 0
            val cMem = 40; val cCpu = 20; val cRend = 20; val cScr = 20 // Static explainable engine weights
            var trendsText = "Historical anomaly data between checkpoints is not persisted beyond the current session. Trends reflect persisted checkpoint states."
            var trigText = "No anomaly triggers recorded in checkpoint history"

            if (cpCount > 0) {
                var totalScore = 0.0
                var lowCount = 0; var elevCount = 0; var highCount = 0
                val reasonMap = mutableMapOf<String, Int>()

                allCheckpoints.forEach { cp ->
                    totalScore += cp.anomalyScore
                    when (cp.riskTier) {
                        2 -> highCount++
                        1 -> elevCount++
                        else -> lowCount++
                    }
                    if (!cp.triggerReason.isNullOrBlank()) {
                        val r = cp.triggerReason
                        reasonMap[r] = (reasonMap[r] ?: 0) + 1
                    }
                }

                avgScoreText = "%.3f".format(totalScore / cpCount)
                tLow = ((lowCount.toDouble() / cpCount) * 100).roundToInt()
                tElev = ((elevCount.toDouble() / cpCount) * 100).roundToInt()
                tHigh = 100 - tLow - tElev

                trendsText = "Historical analysis of $cpCount persisted checkpoint states. Anomaly data between checkpoints is not persisted beyond active sessions."
                
                if (reasonMap.isNotEmpty()) {
                    val sortedReasons = reasonMap.entries.sortedByDescending { it.value }.take(3)
                    val totalReasons = reasonMap.values.sum()
                    trigText = sortedReasons.joinToString("\n") { entry ->
                        "• ${entry.key} (${((entry.value.toDouble() / totalReasons) * 100).roundToInt()}%)"
                    }
                } else {
                    trigText = "System health stable • No active threshold breaches recorded"
                }
            }

            // --- Protection Analytics Aggregations ---
            val cpPerSession = if (sessionsCount > 0) "%.1f".format(cpCount.toDouble() / sessionsCount) else "0.0"
            var totalAgeMs = 0L
            var manualCount = 0
            var autoAnomalyCount = 0
            var autoIntervalCount = 0
            var otherCount = 0

            allCheckpoints.forEach { cp ->
                totalAgeMs += (now - cp.timestampMs).coerceAtLeast(0)
                when (cp.trigger) {
                    Checkpoint.Trigger.MANUAL -> manualCount++
                    Checkpoint.Trigger.AUTO_ANOMALY -> autoAnomalyCount++
                    Checkpoint.Trigger.AUTO_INTERVAL -> autoIntervalCount++
                    else -> otherCount++
                }
            }

            val avgAgeText = if (cpCount > 0) "%.1fs".format((totalAgeMs.toDouble() / cpCount) / 1000.0) else "0.0s"
            val trigDistText = if (cpCount > 0) {
                val pMan = ((manualCount.toDouble() / cpCount) * 100).roundToInt()
                val pAnom = ((autoAnomalyCount.toDouble() / cpCount) * 100).roundToInt()
                val pInt = ((autoIntervalCount.toDouble() / cpCount) * 100).roundToInt()
                val pOth = 100 - pMan - pAnom - pInt
                "AUTO_INTERVAL: $pInt% • AUTO_ANOMALY: $pAnom% • MANUAL: $pMan%" + (if (pOth > 0) " • OTHER: $pOth%" else "")
            } else "No triggers recorded"

            val manVsAutoText = if (cpCount > 0) {
                val pMan = ((manualCount.toDouble() / cpCount) * 100).roundToInt()
                "$pMan% Manual / ${100 - pMan}% Automatic"
            } else "0% Manual / 100% Automatic"

            // --- Recovery Analytics Aggregations ---
            var fallbackCount = 0
            var recTotalDuration = 0L
            var recTotalAge = 0L
            recoveries.forEach { r ->
                if (r.recoveryType == RecoveryRecord.RecoveryType.FALLBACK) fallbackCount++
                recTotalDuration += r.recoveryDurationMs
                recTotalAge += r.checkpointAgeMs
            }

            val recNormVsFallText = if (recCount > 0) {
                val pFall = ((fallbackCount.toDouble() / recCount) * 100).roundToInt()
                "${100 - pFall}% Normal / $pFall% Fallback"
            } else "100% Normal / 0% Fallback"

            val avgRecDurText = if (recCount > 0) "${recTotalDuration / recCount} ms" else "0 ms"
            val avgAgeAtRecText = if (recCount > 0) "%.1fs".format((recTotalAge.toDouble() / recCount) / 1000.0) else "0.0s"

            _state.value = AnalyticsState(
                isLoading = false,
                protectedSessionsCount = sessionsCount,
                totalCheckpointsCount = cpCount,
                totalRecoveriesCount = recCount,
                recoverySuccessRateText = successRate,
                frameworkVersionText = "2.0-phase9",
                avgAnomalyScoreText = avgScoreText,
                tierLowPct = tLow, tierElevatedPct = tElev, tierHighPct = tHigh,
                contribMemPct = cMem, contribCpuPct = cCpu, contribRenderPct = cRend, contribScrollPct = cScr,
                rollingTrendsText = trendsText, triggerReasonsText = trigText,
                checkpointsPerSessionText = cpPerSession, avgCheckpointAgeText = avgAgeText,
                triggerDistributionText = trigDistText, manualVsAutoText = manVsAutoText,
                checkpointTimeline = allCheckpoints.sortedByDescending { it.timestampMs }.take(10),
                recNormalVsFallbackText = recNormVsFallText, avgRecoveryDurationText = avgRecDurText,
                avgAgeAtRecoveryText = avgAgeAtRecText, recoveryTimeline = recoveries.take(10)
            )
        }
    }

    /** Exports analytics to a JSON file in cacheDir on Dispatchers.IO */
    fun exportToJson(onComplete: (File) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val curr = _state.value
            val root = JSONObject().apply {
                put("export_timestamp", System.currentTimeMillis())
                put("framework_version", curr.frameworkVersionText)
                put("framework_overview", JSONObject().apply {
                    put("protected_sessions", curr.protectedSessionsCount)
                    put("total_checkpoints", curr.totalCheckpointsCount)
                    put("total_recoveries", curr.totalRecoveriesCount)
                    put("recovery_success_rate", curr.recoverySuccessRateText)
                })
                put("prediction_analytics", JSONObject().apply {
                    put("avg_anomaly_score", curr.avgAnomalyScoreText)
                    put("risk_tier_distribution", JSONObject().apply {
                        put("LOW", curr.tierLowPct)
                        put("ELEVATED", curr.tierElevatedPct)
                        put("HIGH", curr.tierHighPct)
                    })
                    put("metric_contributions", JSONObject().apply {
                        put("memory", curr.contribMemPct)
                        put("cpu", curr.contribCpuPct)
                        put("render", curr.contribRenderPct)
                        put("scroll", curr.contribScrollPct)
                    })
                    put("rolling_trends", curr.rollingTrendsText)
                    put("top_trigger_reasons", curr.triggerReasonsText)
                })
                put("protection_analytics", JSONObject().apply {
                    put("checkpoints_per_session", curr.checkpointsPerSessionText)
                    put("avg_checkpoint_age", curr.avgCheckpointAgeText)
                    put("trigger_distribution", curr.triggerDistributionText)
                    put("manual_vs_auto", curr.manualVsAutoText)
                    val cpArr = JSONArray()
                    curr.checkpointTimeline.forEach { cp ->
                        cpArr.put(JSONObject().apply {
                            put("document_id", cp.documentId)
                            put("display_name", cp.displayName)
                            put("page", cp.page)
                            put("trigger", cp.trigger.name)
                            put("timestamp_ms", cp.timestampMs)
                        })
                    }
                    put("checkpoint_timeline", cpArr)
                })
                put("recovery_analytics", JSONObject().apply {
                    put("recovery_success_rate", curr.recoverySuccessRateText)
                    put("normal_vs_fallback", curr.recNormalVsFallbackText)
                    put("avg_recovery_duration", curr.avgRecoveryDurationText)
                    put("avg_age_at_recovery", curr.avgAgeAtRecoveryText)
                    val recArr = JSONArray()
                    curr.recoveryTimeline.forEach { r ->
                        recArr.put(JSONObject().apply {
                            put("document_id", r.documentId)
                            put("display_name", r.displayName)
                            put("restored_page", r.restoredPage)
                            put("recovery_type", r.recoveryType.name)
                            put("duration_ms", r.recoveryDurationMs)
                            put("timestamp_ms", r.timestampMs)
                        })
                    }
                    put("recovery_timeline", recArr)
                })
            }

            val file = File(getApplication<Application>().cacheDir, "resilience_analytics_export.json")
            file.writeText(root.toString(4))
            launch(Dispatchers.Main) { onComplete(file) }
        }
    }

    /** Exports analytics to a CSV file in cacheDir on Dispatchers.IO */
    fun exportToCsv(onComplete: (File) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val curr = _state.value
            val sb = StringBuilder()
            sb.append("Section,Metric,Value\n")
            sb.append("Overview,Protected Sessions,${curr.protectedSessionsCount}\n")
            sb.append("Overview,Total Checkpoints,${curr.totalCheckpointsCount}\n")
            sb.append("Overview,Total Recoveries,${curr.totalRecoveriesCount}\n")
            sb.append("Overview,Recovery Success Rate,${curr.recoverySuccessRateText}\n")
            sb.append("Overview,Framework Version,${curr.frameworkVersionText}\n")
            sb.append("Prediction,Avg Anomaly Score,${curr.avgAnomalyScoreText}\n")
            sb.append("Prediction,Tier LOW %,${curr.tierLowPct}%\n")
            sb.append("Prediction,Tier ELEVATED %,${curr.tierElevatedPct}%\n")
            sb.append("Prediction,Tier HIGH %,${curr.tierHighPct}%\n")
            sb.append("Prediction,Contrib Memory %,${curr.contribMemPct}%\n")
            sb.append("Prediction,Contrib CPU %,${curr.contribCpuPct}%\n")
            sb.append("Prediction,Contrib Render %,${curr.contribRenderPct}%\n")
            sb.append("Prediction,Contrib Scroll %,${curr.contribScrollPct}%\n")
            sb.append("Protection,Checkpoints Per Session,${curr.checkpointsPerSessionText}\n")
            sb.append("Protection,Avg Checkpoint Age,${curr.avgCheckpointAgeText}\n")
            sb.append("Protection,Manual vs Auto,${curr.manualVsAutoText}\n")
            sb.append("Recovery,Normal vs Fallback,${curr.recNormalVsFallbackText}\n")
            sb.append("Recovery,Avg Recovery Duration,${curr.avgRecoveryDurationText}\n")
            sb.append("Recovery,Avg Age At Recovery,${curr.avgAgeAtRecoveryText}\n")

            sb.append("\n--- Checkpoint Timeline ---\n")
            sb.append("TimestampMs,DisplayName,Page,Trigger\n")
            curr.checkpointTimeline.forEach { cp ->
                sb.append("${cp.timestampMs},\"${cp.displayName}\",${cp.page},${cp.trigger.name}\n")
            }

            sb.append("\n--- Recovery Timeline ---\n")
            sb.append("TimestampMs,DisplayName,RestoredPage,RecoveryType,DurationMs\n")
            curr.recoveryTimeline.forEach { r ->
                sb.append("${r.timestampMs},\"${r.displayName}\",${r.restoredPage},${r.recoveryType.name},${r.recoveryDurationMs}\n")
            }

            val file = File(getApplication<Application>().cacheDir, "resilience_analytics_export.csv")
            file.writeText(sb.toString())
            launch(Dispatchers.Main) { onComplete(file) }
        }
    }
}
