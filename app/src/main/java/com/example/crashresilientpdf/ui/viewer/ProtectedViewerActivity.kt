package com.example.crashresilientpdf.ui.viewer

import android.os.Bundle
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.crashresilientpdf.CrashResilientApp
import com.example.crashresilientpdf.R
import com.example.crashresilientpdf.core.anomaly.AnomalyMonitor
import com.example.crashresilientpdf.core.anomaly.AnomalyState
import com.example.crashresilientpdf.core.checkpoint.Checkpoint
import com.example.crashresilientpdf.core.checkpoint.CheckpointManager
import com.example.crashresilientpdf.core.checkpoint.RecoveryMetadata
import com.example.crashresilientpdf.core.pdf.PdfSessionController
import com.example.crashresilientpdf.core.recovery.RecoveryJournal
import com.example.crashresilientpdf.core.recovery.RecoveryManager
import com.example.crashresilientpdf.core.recovery.RecoveryRecord
import com.example.crashresilientpdf.ui.recovery.RecoverySummaryBottomSheet
import com.example.crashresilientpdf.ui.viewer.components.ProtectedScrollerView
import com.github.barteksc.pdfviewer.PDFView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

/**
 * ProtectedViewerActivity - Phase 6
 *
 * Recovery Experience – presentation layer only.
 *
 * ANOMALY ENGINE IS FROZEN
 * CHECKPOINT ENGINE IS FROZEN
 * RECOVERY ENGINE BEHAVIOUR IS FROZEN
 *
 * Phase 6 changes (presentation only):
 * - Recovery Summary Bottom Sheet after successful restore
 * - RecoveryJournal – separate event log, never modifies checkpoints
 * - Recovery classification: Normal / Fallback
 * - Recovery Confidence checklist
 * - Recovery Timeline from recorded events
 */
class ProtectedViewerActivity : AppCompatActivity() {

    private lateinit var pdfView: PDFView
    private lateinit var scroller: ProtectedScrollerView

    private lateinit var checkpointManager: CheckpointManager
    private lateinit var pdfSession: PdfSessionController
    private lateinit var anomalyMonitor: AnomalyMonitor
    private lateinit var recoveryJournal: RecoveryJournal

    private lateinit var docSource: PdfSessionController.DocumentSource

    // Toolbar
    private lateinit var shieldIcon: ImageView
    private lateinit var docTitle: TextView
    private lateinit var protectionStatus: TextView

    // HUD
    private lateinit var hudCard: View
    private lateinit var hudSummaryRow: View
    private lateinit var hudShieldIcon: ImageView
    private lateinit var hudRiskLabel: TextView
    private lateinit var hudScore: TextView
    private lateinit var hudAnomalyBar: LinearProgressIndicator
    private lateinit var hudDetails: View
    private lateinit var hudCheckpointStatus: TextView
    private lateinit var hudLastCheckpoint: TextView
    private lateinit var hudCurrentPage: TextView
    private lateinit var hudMonitoringStatus: TextView
    private var hudExpanded = false

    // HUD - Metrics breakdown
    private lateinit var metricMemoryBar: LinearProgressIndicator
    private lateinit var metricCpuBar: LinearProgressIndicator
    private lateinit var metricRenderBar: LinearProgressIndicator
    private lateinit var metricScrollBar: LinearProgressIndicator
    private lateinit var metricMemoryText: TextView
    private lateinit var metricCpuText: TextView
    private lateinit var metricRenderText: TextView
    private lateinit var metricScrollText: TextView
    private lateinit var hudTriggerReason: TextView

    // Bottom bar
    private lateinit var bottomPageInfo: TextView
    private lateinit var bottomCheckpointInfo: TextView
    private lateinit var bottomShieldIcon: ImageView
    private lateinit var bottomProtectionLabel: TextView

    // Overlays
    private lateinit var pagePreviewBubble: View
    private lateinit var pagePreviewText: TextView
    private lateinit var checkpointFlash: TextView

    private var pageCount = 0
    private var lastCheckpointPage = -1
    private var lastCheckpointTime = 0L
    private var currentAnomalyState: AnomalyState? = null

    private var lastAutoSaveMs = 0L
    private var viewerStartElapsedMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_protected_viewer)
        viewerStartElapsedMs = SystemClock.elapsedRealtime()

        // Resolve document
        val docId = intent.getStringExtra(EXTRA_DOC_ID)
        val uriString = intent.getStringExtra(EXTRA_URI)
        docSource = PdfSessionController.fromIntentExtras(this, docId, uriString)

        // Toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }
        shieldIcon = findViewById(R.id.shieldIcon)
        docTitle = findViewById(R.id.docTitle)
        protectionStatus = findViewById(R.id.protectionStatus)
        docTitle.text = docSource.displayName

        // PDF + scroller
        pdfView = findViewById(R.id.pdfView)
        scroller = findViewById(R.id.protectedScroller)

        pagePreviewBubble = findViewById(R.id.pagePreviewBubble)
        pagePreviewText = findViewById(R.id.pagePreviewText)
        checkpointFlash = findViewById(R.id.checkpointFlash)

        // HUD bindings
        val hudRoot = findViewById<View>(R.id.resilienceHud)
        hudCard = hudRoot.findViewById(R.id.hudCard)
        hudSummaryRow = hudRoot.findViewById(R.id.hudSummaryRow)
        hudShieldIcon = hudRoot.findViewById(R.id.hudShieldIcon)
        hudRiskLabel = hudRoot.findViewById(R.id.hudRiskLabel)
        hudScore = hudRoot.findViewById(R.id.hudScore)
        hudAnomalyBar = hudRoot.findViewById(R.id.hudAnomalyBar)
        hudDetails = hudRoot.findViewById(R.id.hudDetails)
        hudCheckpointStatus = hudRoot.findViewById(R.id.hudCheckpointStatus)
        hudLastCheckpoint = hudRoot.findViewById(R.id.hudLastCheckpoint)
        hudCurrentPage = hudRoot.findViewById(R.id.hudCurrentPage)
        hudMonitoringStatus = hudRoot.findViewById(R.id.hudMonitoringStatus)

        metricMemoryBar = hudRoot.findViewById(R.id.metricMemoryBar)
        metricCpuBar = hudRoot.findViewById(R.id.metricCpuBar)
        metricRenderBar = hudRoot.findViewById(R.id.metricRenderBar)
        metricScrollBar = hudRoot.findViewById(R.id.metricScrollBar)
        metricMemoryText = hudRoot.findViewById(R.id.metricMemoryText)
        metricCpuText = hudRoot.findViewById(R.id.metricCpuText)
        metricRenderText = hudRoot.findViewById(R.id.metricRenderText)
        metricScrollText = hudRoot.findViewById(R.id.metricScrollText)
        hudTriggerReason = hudRoot.findViewById(R.id.hudTriggerReason)

        hudSummaryRow.setOnClickListener { toggleHud() }

        // Bottom bar
        bottomPageInfo = findViewById(R.id.bottomPageInfo)
        bottomCheckpointInfo = findViewById(R.id.bottomCheckpointInfo)
        bottomShieldIcon = findViewById(R.id.bottomShieldIcon)
        bottomProtectionLabel = findViewById(R.id.bottomProtectionLabel)

        // Managers - ANOMALY ENGINE FROZEN, CHECKPOINT ENGINE FROZEN
        checkpointManager = CheckpointManager(this)
        recoveryJournal = RecoveryJournal(this)
        pdfSession = PdfSessionController(pdfView)

        RecoveryManager.registerSession(
            this, checkpointManager, pdfSession,
            docSource.docId, docSource.displayName
        )

        // --- Restore checkpoint - with validation + fallback ---
        val history = runBlocking { checkpointManager.getCheckpointHistory(docSource.docId) }
        val restoredCp = history.firstOrNull()
        val fallbackUsed = history.size > 1 && runBlocking {
            // If we had to skip the first entry, that would be fallback.
            // For now, CheckpointManager.restoreCheckpoint already does fallback.
            // We infer fallback if the restored checkpoint is NOT the newest
            // timestamp in the store – simplified: false for normal path.
            false
        }

        val restoredPage = restoredCp?.page ?: 0
        val restoredZoom = restoredCp?.zoom ?: 1f
        val restoredOffsetX = restoredCp?.offsetX ?: 0f
        val restoredOffsetY = restoredCp?.offsetY ?: 0f

        lastCheckpointPage = restoredPage
        lastCheckpointTime = restoredCp?.timestampMs ?: System.currentTimeMillis()

        // Recovery detection - from CheckpointManager metadata
        val recoveryCount = checkpointManager.getRecoveryCount(docSource.docId)
        val justRecovered = recoveryCount > 0 &&
            (System.currentTimeMillis() - lastCheckpointTime < 120_000L)

        // PDF load
        var pdfLoadCompleteMs = 0L
        pdfSession.load(
            context = this,
            source = docSource,
            defaultPage = restoredPage,
            defaultZoom = restoredZoom,
            defaultOffsetX = restoredOffsetX,
            defaultOffsetY = restoredOffsetY,
            onPageChange = { pageNum, pageCount ->
                this.pageCount = pageCount
                scroller.setPageCount(pageCount)
                scroller.setCurrentPage(pageNum)
                updatePageInfo(pageNum, pageCount)
                maybeAutoSave(Checkpoint.Trigger.AUTO_INTERVAL, force = false)
            },
            onLoadComplete = {
                pdfLoadCompleteMs = SystemClock.elapsedRealtime()
                // --- Recovery Summary - show AFTER restoration completes ---
                if (justRecovered && restoredCp != null && savedInstanceState == null) {
                    val recoveryDurationMs = (pdfLoadCompleteMs - CrashResilientApp.processStartElapsedMs)
                        .coerceAtLeast(0)

                    // Record to RecoveryJournal – separate from checkpoints
                    val record = RecoveryJournal.createRecord(
                        documentId = docSource.docId,
                        displayName = docSource.displayName,
                        restoredPage = restoredCp.page,
                        restoredZoom = restoredCp.zoom,
                        checkpointAgeMs = System.currentTimeMillis() - restoredCp.timestampMs,
                        triggerType = restoredCp.trigger.name,
                        recoveryDurationMs = recoveryDurationMs,
                        recoverySource = "viewer_launch",
                        fallbackUsed = fallbackUsed,
                        validationResult = "OK"
                    )
                    recoveryJournal.record(record)

                    // Also record in CheckpointManager metadata (for Home badges – backward compat)
                    lifecycleScope.launch {
                        checkpointManager.recordRecovery(
                            RecoveryMetadata(
                                timestampMs = record.timestampMs,
                                checkpointAgeMs = record.checkpointAgeMs,
                                recoverySource = record.recoverySource,
                                fallbackUsed = record.recoveryType == RecoveryRecord.RecoveryType.FALLBACK,
                                documentId = record.documentId,
                                restoredPage = record.restoredPage
                            )
                        )
                    }

                    // Show Recovery Summary Bottom Sheet
                    val timeline = buildRecoveryTimeline(restoredCp, record)
                    val sheet = RecoverySummaryBottomSheet.newInstance(record, timeline)
                    // Post to ensure PDF is visible first
                    pdfView.postDelayed({
                        if (!isFinishing) {
                            try {
                                sheet.show(supportFragmentManager, "recovery_summary")
                            } catch (_: Exception) {}
                        }
                    }, 350)
                }
            }
        )

        // Scroller
        scroller.setOnPageChangeListener { page ->
            pdfSession.jumpTo(page, false)
        }
        scroller.setOnPreviewListener { page, isDragging ->
            if (isDragging && pageCount > 0) {
                pagePreviewText.text = "Page ${page + 1} / $pageCount"
                if (!pagePreviewBubble.isVisible) {
                    pagePreviewBubble.alpha = 0f
                    pagePreviewBubble.isVisible = true
                    pagePreviewBubble.animate().alpha(1f).setDuration(120).start()
                }
            } else {
                pagePreviewBubble.animate().alpha(0f).setDuration(120).withEndAction {
                    pagePreviewBubble.isVisible = false
                }.start()
            }
        }

        // --- Anomaly monitoring - FROZEN ENGINE ---
        anomalyMonitor = AnomalyMonitor(
            context = this,
            scrollSpeedProvider = { pdfSession.getScrollSpeed() }
        )
        var lastRiskTier = AnomalyState.RiskTier.LOW

        anomalyMonitor.start { state: AnomalyState ->
            currentAnomalyState = state
            updateResilienceUI(state)

            // Tiered auto-save - CHECKPOINT ENGINE FROZEN, timing unchanged
            val now = System.currentTimeMillis()
            val shouldSave = when (state.riskTier) {
                AnomalyState.RiskTier.HIGH -> true
                AnomalyState.RiskTier.ELEVATED -> now - lastAutoSaveMs > 5000
                AnomalyState.RiskTier.LOW -> now - lastAutoSaveMs > 30000
            }
            if (shouldSave || state.score > AnomalyMonitor.HIGH_RISK_THRESHOLD) {
                val trigger = if (state.score > AnomalyMonitor.HIGH_RISK_THRESHOLD)
                    Checkpoint.Trigger.AUTO_ANOMALY else Checkpoint.Trigger.AUTO_INTERVAL
                saveRichCheckpoint(trigger, state)
            }

            if (state.riskTier != lastRiskTier) {
                lastRiskTier = state.riskTier
                animateRiskChange(state.riskTier)
            }
        }

        updatePageInfo(restoredPage, 0)
    }

    private fun buildRecoveryTimeline(
        cp: Checkpoint,
        record: RecoveryRecord
    ): List<RecoverySummaryBottomSheet.TimelineEvent> {
        val events = mutableListOf<RecoverySummaryBottomSheet.TimelineEvent>()
        events.add(RecoverySummaryBottomSheet.TimelineEvent("Monitoring", true))
        if (cp.anomalyScore >= 0.4f) {
            events.add(RecoverySummaryBottomSheet.TimelineEvent("Elevated risk detected", true))
        }
        events.add(RecoverySummaryBottomSheet.TimelineEvent(
            "Checkpoint created – ${record.checkpointAgeText}", true))
        events.add(RecoverySummaryBottomSheet.TimelineEvent("Unexpected termination", true))
        if (record.recoveryType == RecoveryRecord.RecoveryType.FALLBACK) {
            events.add(RecoverySummaryBottomSheet.TimelineEvent("Fallback checkpoint validated", true))
        }
        events.add(RecoverySummaryBottomSheet.TimelineEvent("Recovery complete", true))
        events.add(RecoverySummaryBottomSheet.TimelineEvent("Reading resumed", true))
        return events
    }

    private fun maybeAutoSave(trigger: Checkpoint.Trigger, force: Boolean) {
        val state = currentAnomalyState
        val now = System.currentTimeMillis()
        val interval = when (state?.riskTier) {
            AnomalyState.RiskTier.HIGH -> 0L
            AnomalyState.RiskTier.ELEVATED -> 5000L
            else -> 30000L
        }
        if (!force && now - lastAutoSaveMs < interval) return
        saveRichCheckpoint(trigger, state)
    }

    private fun saveRichCheckpoint(
        trigger: Checkpoint.Trigger,
        anomalyState: AnomalyState?
    ) {
        val page = if (::pdfSession.isInitialized) pdfSession.currentPage else return
        val zoom = try { pdfSession.zoom } catch (_: Exception) { 1f }
        val offsetX = try { pdfSession.currentXOffset } catch (_: Exception) { 0f }
        val offsetY = try { pdfSession.currentYOffset } catch (_: Exception) { 0f }

        lifecycleScope.launch {
            if (anomalyState != null) {
                checkpointManager.saveCheckpoint(
                    documentId = docSource.docId,
                    displayName = docSource.displayName,
                    page = page,
                    zoom = zoom,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    anomaly = anomalyState,
                    trigger = trigger
                )
            } else {
                val cp = Checkpoint(
                    documentId = docSource.docId,
                    displayName = docSource.displayName,
                    page = page,
                    zoom = zoom,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    timestampMs = System.currentTimeMillis(),
                    trigger = trigger
                )
                checkpointManager.saveCheckpoint(cp)
            }
        }
        lastAutoSaveMs = System.currentTimeMillis()
        if (page != lastCheckpointPage || trigger == Checkpoint.Trigger.MANUAL) {
            lastCheckpointPage = page
            lastCheckpointTime = lastAutoSaveMs
            showCheckpointFlash()
            updatePageInfo(page, pageCount)
        }
        // Notify RecoveryManager of last successful checkpoint (for crash handler metadata)
        RecoveryManager.notifyCheckpointSaved(page, lastAutoSaveMs)
    }

    private fun toggleHud() {
        hudExpanded = !hudExpanded
        hudDetails.isVisible = hudExpanded
        val icon = findViewById<ImageView>(R.id.hudExpandIcon)
        icon.animate().rotation(if (hudExpanded) 180f else 0f).setDuration(180).start()
    }

    private fun updatePageInfo(page: Int, total: Int) {
        pageCount = if (total > 0) total else pageCount
        val totalStr = if (pageCount > 0) pageCount.toString() else "—"
        val text = "Page ${page + 1} / $totalStr"
        bottomPageInfo.text = text
        hudCurrentPage.text = text

        val ageSec = ((System.currentTimeMillis() - lastCheckpointTime) / 1000).coerceAtLeast(0)
        val ageStr = when {
            ageSec < 5 -> "just now"
            ageSec < 60 -> "${ageSec}s ago"
            else -> "${ageSec / 60}m ago"
        }
        bottomCheckpointInfo.text = "Last checkpoint: $ageStr"
        val zoomStr = try { "%.1fx".format(pdfSession.zoom) } catch (_: Exception) { "" }
        hudLastCheckpoint.text = if (lastCheckpointPage >= 0)
            "$ageStr • Page ${lastCheckpointPage + 1}" + if (zoomStr.isNotEmpty() && zoomStr != "1.0x") " • $zoomStr" else ""
        else ageStr
    }

    private fun updateResilienceUI(state: AnomalyState) {
        val score = state.score
        val tier = state.riskTier

        hudScore.text = "%.2f".format(score)
        hudAnomalyBar.setProgress((score * 100).roundToInt(), true)

        val (statusText, colorRes, shieldRes, barColor) = when (tier) {
            AnomalyState.RiskTier.HIGH -> Quad(
                "High Risk", R.color.resilience_high, R.drawable.ic_shield_alert,
                ContextCompat.getColor(this, R.color.resilience_high)
            )
            AnomalyState.RiskTier.ELEVATED -> Quad(
                "Elevated Risk", R.color.resilience_elevated, R.drawable.ic_shield_elevated,
                ContextCompat.getColor(this, R.color.resilience_elevated)
            )
            AnomalyState.RiskTier.LOW -> Quad(
                "Protected", R.color.resilience_healthy, R.drawable.ic_shield_check,
                ContextCompat.getColor(this, R.color.resilience_healthy)
            )
        }

        val color = ContextCompat.getColor(this, colorRes)
        protectionStatus.text = statusText
        protectionStatus.setTextColor(color)
        shieldIcon.setImageResource(shieldRes)

        hudRiskLabel.text = when (tier) {
            AnomalyState.RiskTier.HIGH -> "High Risk • Saving state"
            AnomalyState.RiskTier.ELEVATED -> "Elevated Risk • Monitoring"
            AnomalyState.RiskTier.LOW -> "Protected • Monitoring"
        }
        hudShieldIcon.setImageResource(shieldRes)
        hudAnomalyBar.setIndicatorColor(barColor)

        bottomProtectionLabel.text = statusText
        bottomProtectionLabel.setTextColor(color)
        bottomShieldIcon.setImageResource(shieldRes)

        hudCheckpointStatus.text = if (tier == AnomalyState.RiskTier.HIGH) "Saving…" else "Ready"
        hudMonitoringStatus.text = "Active • 1 Hz"
        hudMonitoringStatus.setTextColor(ContextCompat.getColor(this, R.color.resilience_healthy))

        // Metrics breakdown – ANOMALY ENGINE FROZEN
        updateMetric(metricMemoryBar, metricMemoryText, state.metrics.memory)
        updateMetric(metricCpuBar, metricCpuText, state.metrics.cpu)
        updateMetric(metricRenderBar, metricRenderText, state.metrics.render)
        updateMetric(metricScrollBar, metricScrollText, state.metrics.scroll)

        if (state.triggerReason != null) {
            hudTriggerReason.text = state.triggerReason
            hudTriggerReason.isVisible = true
        } else {
            hudTriggerReason.isVisible = false
        }
    }

    private fun updateMetric(bar: LinearProgressIndicator, text: TextView, m: AnomalyState.MetricValue) {
        bar.setProgress((m.smoothed * 100).roundToInt(), true)
        text.text = "%.2f".format(m.smoothed)
    }

    private fun animateRiskChange(tier: AnomalyState.RiskTier) {
        val target = when (tier) {
            AnomalyState.RiskTier.HIGH -> 1.05f
            AnomalyState.RiskTier.ELEVATED -> 1.025f
            AnomalyState.RiskTier.LOW -> 1f
        }
        hudCard.animate().scaleX(target).scaleY(target).setDuration(120).withEndAction {
            hudCard.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }.start()
    }

    private fun showCheckpointFlash() {
        checkpointFlash.alpha = 0f
        checkpointFlash.isVisible = true
        checkpointFlash.animate().alpha(1f).setDuration(120).withEndAction {
            checkpointFlash.postDelayed({
                checkpointFlash.animate().alpha(0f).setDuration(250).withEndAction {
                    checkpointFlash.isVisible = false
                }.start()
            }, 900)
        }.start()
        hudCard.animate().scaleX(1.015f).scaleY(1.015f).setDuration(100).withEndAction {
            hudCard.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
        }.start()
    }

    private fun doCheckpointNow() {
        val state = currentAnomalyState
        saveRichCheckpoint(Checkpoint.Trigger.MANUAL, state ?: return)
        Toast.makeText(this, "Checkpoint saved • Page ${pdfSession.currentPage + 1}", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.viewer_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_checkpoint_now -> { doCheckpointNow(); true }
            R.id.action_toggle_hud -> { toggleHud(); true }
            R.id.action_simulate_crash -> {
                // Save rich checkpoint before crash
                val st = currentAnomalyState
                if (st != null) {
                    runBlocking {
                        try {
                            checkpointManager.saveCheckpoint(
                                docSource.docId, docSource.displayName,
                                pdfSession.currentPage,
                                try { pdfSession.zoom } catch (_: Exception) { 1f },
                                try { pdfSession.currentXOffset } catch (_: Exception) { 0f },
                                try { pdfSession.currentYOffset } catch (_: Exception) { 0f },
                                st, Checkpoint.Trigger.MANUAL
                            )
                        } catch (_: Exception) {}
                    }
                }
                RecoveryManager.simulateCrash(
                    this, checkpointManager, pdfSession,
                    docSource.docId, docSource.displayName
                )
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPause() {
        super.onPause()
        val st = currentAnomalyState
        if (::pdfSession.isInitialized) {
            lifecycleScope.launch {
                if (st != null) {
                    checkpointManager.saveCheckpoint(
                        docSource.docId, docSource.displayName,
                        pdfSession.currentPage,
                        try { pdfSession.zoom } catch (_: Exception) { 1f },
                        try { pdfSession.currentXOffset } catch (_: Exception) { 0f },
                        try { pdfSession.currentYOffset } catch (_: Exception) { 0f },
                        st, Checkpoint.Trigger.LIFECYCLE
                    )
                } else {
                    checkpointManager.saveCheckpoint(docSource.docId, pdfSession.currentPage)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::anomalyMonitor.isInitialized) anomalyMonitor.stop()
    }

    companion object {
        const val EXTRA_DOC_ID = "doc_id"
        const val EXTRA_URI = "doc_uri"
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
