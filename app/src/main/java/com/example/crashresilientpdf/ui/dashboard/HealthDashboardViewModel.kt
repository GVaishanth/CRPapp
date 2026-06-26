package com.example.crashresilientpdf.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.crashresilientpdf.core.anomaly.AnomalyMonitor
import com.example.crashresilientpdf.core.anomaly.AnomalyState
import com.example.crashresilientpdf.core.checkpoint.Checkpoint
import com.example.crashresilientpdf.core.checkpoint.CheckpointManager
import com.example.crashresilientpdf.core.recovery.RecoveryJournal
import com.example.crashresilientpdf.core.recovery.RecoveryRecord
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * HealthDashboardViewModel - Phase 7
 *
 * Observational console for the Crash Resilience Framework.
 * Consumes frozen engines - never modifies them.
 *
 * Answers 4 questions immediately:
 * 1. Is the framework healthy?
 * 2. Is the current document protected?
 * 3. Has recovery been working correctly?
 * 4. Why does the framework believe the current risk level?
 */
class HealthDashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val checkpointManager = CheckpointManager(app)
    private val recoveryJournal = RecoveryJournal(app)

    // Anomaly history - presentation layer, NOT part of frozen AnomalyMonitor
    private val anomalyHistoryRepo = AnomalyHistoryRepository(120)

    data class DashboardState(
        // Prediction
        val anomalyScore: Double = 0.0,
        val riskTier: AnomalyState.RiskTier = AnomalyState.RiskTier.LOW,
        val anomalyHistory: List<AnomalyState> = emptyList(),
        val metrics: AnomalyState.MetricsSnapshot? = null,
        val triggerReason: String? = null,
        val monitoringActive: Boolean = false,

        // Protection
        val checkpointStatus: String = "Standby",
        val lastCheckpointAgeMs: Long = 0,
        val lastCheckpointTrigger: String = "—",
        val lastCheckpointPage: Int = -1,
        val totalCheckpoints: Int = 0,
        val protectedDocuments: Int = 0,
        val checkpointHistory: List<Checkpoint> = emptyList(),

        // Recovery
        val recoveryReadiness: String = "Ready",
        val lastRecovery: RecoveryRecord? = null,
        val totalRecoveries: Int = 0,
        val recoveryHistory: List<RecoveryRecord> = emptyList(),

        // Framework
        val predictionEngineStatus: String = "Standby",
        val checkpointEngineStatus: String = "Operational",
        val recoveryEngineStatus: String = "Operational",
        val frameworkVersion: String = "2.0",
        val frameworkStatus: String = "Protected"
    )

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var anomalyMonitor: AnomalyMonitor? = null

    fun attachAnomalyMonitor(monitor: AnomalyMonitor?) {
        anomalyMonitor = monitor
    }

    fun start() {
        if (pollJob != null) return

        // Subscribe to live anomaly stream if available
        anomalyMonitor?.let { monitor ->
            viewModelScope.launch {
                monitor.stateFlow.collect { state ->
                    anomalyHistoryRepo.push(state)
                }
            }
        }

        pollJob = viewModelScope.launch {
            var lastScore = 0.0
            while (isActive) {
                val anomaly = anomalyMonitor?.stateFlow?.value
                val history = anomalyHistoryRepo.snapshot()

                // --- Protection: read from CheckpointManager ---
                val sessions = try { checkpointManager.getRecentSessions() } catch (_: Exception) { emptyList() }
                var totalCps = 0
                var lastCpAge = Long.MAX_VALUE
                var lastCpTrigger = "—"
                var lastCpPage = -1
                val cpHistory = mutableListOf<Checkpoint>()
                try {
                    for (s in sessions.take(3)) {
                        val hist = checkpointManager.getCheckpointHistory(s.docId)
                        totalCps += hist.size
                        cpHistory.addAll(hist.take(3))
                        hist.firstOrNull()?.let { cp ->
                            val age = System.currentTimeMillis() - cp.timestampMs
                            if (age < lastCpAge) {
                                lastCpAge = age
                                lastCpTrigger = cp.trigger.name
                                lastCpPage = cp.page
                            }
                        }
                    }
                } catch (_: Exception) {}

                // --- Recovery: read from RecoveryJournal ---
                val recoveryHistory = try { recoveryJournal.getHistory() } catch (_: Exception) { emptyList() }
                val lastRecovery = recoveryHistory.firstOrNull()
                val totalRecoveries = recoveryHistory.size

                _state.value = DashboardState(
                    // Prediction
                    anomalyScore = anomaly?.score ?: 0.0,
                    riskTier = anomaly?.riskTier ?: AnomalyState.RiskTier.LOW,
                    anomalyHistory = history,
                    metrics = anomaly?.metrics,
                    triggerReason = anomaly?.triggerReason,
                    monitoringActive = anomaly != null,

                    // Protection
                    checkpointStatus = if (sessions.isNotEmpty()) "Active" else "Standby",
                    lastCheckpointAgeMs = if (lastCpAge != Long.MAX_VALUE) lastCpAge else 0,
                    lastCheckpointTrigger = lastCpTrigger,
                    lastCheckpointPage = lastCpPage,
                    totalCheckpoints = totalCps,
                    protectedDocuments = sessions.size,
                    checkpointHistory = cpHistory.sortedByDescending { it.timestampMs }.take(8),

                    // Recovery
                    recoveryReadiness = if (totalRecoveries > 0) "Ready • $totalRecoveries recovered" else "Ready • Standby",
                    lastRecovery = lastRecovery,
                    totalRecoveries = totalRecoveries,
                    recoveryHistory = recoveryHistory.take(5),

                    // Framework
                    predictionEngineStatus = if (anomaly != null) "Operational" else "Standby",
                    checkpointEngineStatus = "Operational",
                    recoveryEngineStatus = "Operational",
                    frameworkVersion = "2.0",
                    frameworkStatus = when (anomaly?.riskTier) {
                        AnomalyState.RiskTier.HIGH -> "High Risk"
                        AnomalyState.RiskTier.ELEVATED -> "Elevated Risk"
                        else -> "Protected"
                    }
                )

                delay(100) // 10 FPS UI
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }
}
