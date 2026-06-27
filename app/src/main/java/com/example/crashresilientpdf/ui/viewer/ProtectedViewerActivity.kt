package com.example.crashresilientpdf.ui.viewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import com.example.crashresilientpdf.ui.dashboard.AnomalyMonitorHolder
import com.example.crashresilientpdf.ui.dashboard.HealthDashboardActivity
import com.example.crashresilientpdf.ui.analytics.ResilienceAnalyticsActivity
import com.example.crashresilientpdf.ui.recovery.RecoverySummaryBottomSheet
import com.example.crashresilientpdf.ui.viewer.components.ProtectedScrollerView
import com.github.barteksc.pdfviewer.PDFView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * ProtectedViewerActivity - Phase 11 (Version 3.0 Roadmap)
 *
 * Multi-Document Tabbed Resilience Workspace & GUI Modernization.
 * Emulates a Google Chrome / Google Docs tabbed switcher.
 *
 * ANOMALY ENGINE IS FROZEN
 * CHECKPOINT ENGINE IS FROZEN
 * RECOVERY ENGINE BEHAVIOUR IS FROZEN
 *
 * Version 3.0 Enhancements:
 * - Chrome-style TabLayout supporting instant background tab switching.
 * - Atomic tab state protection: switching tabs triggers LIFECYCLE saves; exact Page/Zoom/Pan restored.
 * - True edge-to-edge window insets and polished Material 3 typography.
 * - Full support for OS-level reduced motion preferences.
 */
class ProtectedViewerActivity : AppCompatActivity() {

    private lateinit var pdfView: PDFView
    private lateinit var scroller: ProtectedScrollerView

    private lateinit var checkpointManager: CheckpointManager
    private lateinit var pdfSession: PdfSessionController
    private lateinit var anomalyMonitor: AnomalyMonitor
    private lateinit var recoveryJournal: RecoveryJournal

    private val tabWorkspaceManager = TabWorkspaceManager()
    private lateinit var tabLayout: TabLayout
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
    private var isInitialAppLaunch = true

    private val openNewTabContract = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {}
            val newSource = PdfSessionController.fromIntentExtras(this, uri.toString(), uri.toString())
            val index = tabWorkspaceManager.openTab(newSource)
            val tab = tabLayout.newTab().setText(newSource.displayName)
            tabLayout.addTab(tab, true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_protected_viewer)
        viewerStartElapsedMs = SystemClock.elapsedRealtime()

        // Apply Window Insets for true edge-to-edge GUI modernization
        val appBarLayout = findViewById<AppBarLayout>(R.id.appBarLayout)
        val bottomBar = findViewById<BottomAppBar>(R.id.bottomBar)
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, view.paddingBottom)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(bottomBar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, systemBars.bottom)
            insets
        }

        // Resolve initial document
        val docId = intent.getStringExtra(EXTRA_DOC_ID)
        val uriString = intent.getStringExtra(EXTRA_URI)
        val initialSource = PdfSessionController.fromIntentExtras(this, docId, uriString)

        // Toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }
        shieldIcon = findViewById(R.id.shieldIcon)
        docTitle = findViewById(R.id.docTitle)
        protectionStatus = findViewById(R.id.protectionStatus)

        // PDF + scroller
        pdfView = findViewById(R.id.pdfView)
        scroller = findViewById(R.id.protectedScroller)

        pagePreviewBubble = findViewById(R.id.pagePreviewBubble)
        pagePreviewText = findViewById(R.id.pagePreviewText)
        checkpointFlash = findViewById(R.id.checkpointFlash)

        // HUD bindings
        val hudRoot = findViewById<View>(R.id.resilienceHud)
        hudCard = hudRoot
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

        // Initialize Tabbed Workspace
        tabLayout = findViewById(R.id.tabLayout)
        tabWorkspaceManager.openTab(initialSource)
        tabLayout.addTab(tabLayout.newTab().setText(initialSource.displayName), true)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                switchTab(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {
                saveCurrentTabState(Checkpoint.Trigger.LIFECYCLE)
            }
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // Initial tab load
        switchTab(0)

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
        AnomalyMonitorHolder.instance = anomalyMonitor
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
                saveCurrentTabState(trigger, state)
            }

            if (state.riskTier != lastRiskTier) {
                lastRiskTier = state.riskTier
                animateRiskChange(state.riskTier)
            }
        }
    }

    private fun switchTab(position: Int) {
        tabWorkspaceManager.selectTab(position)
        val nextSource = tabWorkspaceManager.activeTab ?: return
        docSource = nextSource
        docTitle.text = nextSource.displayName

        RecoveryManager.registerSession(
            this, checkpointManager, pdfSession,
            nextSource.docId, nextSource.displayName
        )

        lifecycleScope.launch(Dispatchers.IO) {
            val history = checkpointManager.getCheckpointHistory(nextSource.docId)
            val restoredCp = history.firstOrNull()
            val fallbackUsed = history.size > 1 && false

            val restoredPage = restoredCp?.page ?: 0
            val restoredZoom = restoredCp?.zoom ?: 1f
            val restoredOffsetX = restoredCp?.offsetX ?: 0f
            val restoredOffsetY = restoredCp?.offsetY ?: 0f

            val recoveryCount = checkpointManager.getRecoveryCount(nextSource.docId)
            val justRecovered = recoveryCount > 0 && (System.currentTimeMillis() - (restoredCp?.timestampMs ?: 0L) < 120_000L)

            withContext(Dispatchers.Main) {
                lastCheckpointPage = restoredPage
                lastCheckpointTime = restoredCp?.timestampMs ?: System.currentTimeMillis()

                var pdfLoadCompleteMs = 0L
                pdfSession.load(
                    context = this@ProtectedViewerActivity,
                    source = nextSource,
                    defaultPage = restoredPage,
                    defaultZoom = restoredZoom,
                    defaultOffsetX = restoredOffsetX,
                    defaultOffsetY = restoredOffsetY,
                    onPageChange = { pageNum, pageCount ->
                        this@ProtectedViewerActivity.pageCount = pageCount
                        scroller.setPageCount(pageCount)
                        scroller.setCurrentPage(pageNum)
                        updatePageInfo(pageNum, pageCount)
                        maybeAutoSave(Checkpoint.Trigger.AUTO_INTERVAL, force = false)
                    },
                    onLoadComplete = {
                        pdfLoadCompleteMs = SystemClock.elapsedRealtime()
                        if (justRecovered && restoredCp != null && isInitialAppLaunch) {
                            isInitialAppLaunch = false
                            val recoveryDurationMs = (pdfLoadCompleteMs - CrashResilientApp.processStartElapsedMs).coerceAtLeast(0)

                            val record = RecoveryJournal.createRecord(
                                documentId = nextSource.docId,
                                displayName = nextSource.displayName,
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

                            lifecycleScope.launch(Dispatchers.IO) {
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

                            val timeline = buildRecoveryTimeline(restoredCp, record)
                            val sheet = RecoverySummaryBottomSheet.newInstance(record, timeline)
                            pdfView.postDelayed({
                                if (!isFinishing) {
                                    try { sheet.show(supportFragmentManager, "recovery_summary") } catch (_: Exception) {}
                                }
                            }, 350)
                        }
                    }
                )
                updatePageInfo(restoredPage, 0)
            }
        }
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
        events.add(RecoverySummaryBottomSheet.TimelineEvent("Checkpoint created – ${record.checkpointAgeText}", true))
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
        saveCurrentTabState(trigger, state)
    }

    private fun saveCurrentTabState(
        trigger: Checkpoint.Trigger,
        anomalyState: AnomalyState? = currentAnomalyState
    ) {
        val page = if (::pdfSession.isInitialized) pdfSession.currentPage else return
        val zoom = try { pdfSession.zoom } catch (_: Exception) { 1f }
        val offsetX = try { pdfSession.currentXOffset } catch (_: Exception) { 0f }
        val offsetY = try { pdfSession.currentYOffset } catch (_: Exception) { 0f }

        lifecycleScope.launch(Dispatchers.IO) {
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
    }

    private fun toggleHud() {
        hudExpanded = !hudExpanded
        hudDetails.isVisible = hudExpanded
        val icon = findViewById<ImageView>(R.id.hudExpandIcon)
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) {
            icon.rotation = if (hudExpanded) 180f else 0f
        } else {
            icon.animate().rotation(if (hudExpanded) 180f else 0f).setDuration(180).start()
        }
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
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) return
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
        checkpointFlash.isVisible = true
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) {
            checkpointFlash.alpha = 1f
            checkpointFlash.postDelayed({ checkpointFlash.isVisible = false }, 900)
            return
        }
        checkpointFlash.alpha = 0f
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
        saveCurrentTabState(Checkpoint.Trigger.MANUAL, state ?: return)
        Toast.makeText(this, "Checkpoint saved • Page ${pdfSession.currentPage + 1}", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.viewer_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_open_new_tab -> {
                openNewTabContract.launch(arrayOf("application/pdf"))
                true
            }
            R.id.action_checkpoint_now -> { doCheckpointNow(); true }
            R.id.action_toggle_hud -> { toggleHud(); true }
            R.id.action_health_dashboard -> {
                startActivity(Intent(this, HealthDashboardActivity::class.java))
                true
            }
            R.id.action_resilience_analytics -> {
                startActivity(Intent(this, ResilienceAnalyticsActivity::class.java))
                true
            }
            R.id.action_simulate_crash -> {
                val st = currentAnomalyState
                if (st != null) {
                    lifecycleScope.launch(Dispatchers.IO) {
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
            lifecycleScope.launch(Dispatchers.IO) {
                withContext(NonCancellable) {
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
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::anomalyMonitor.isInitialized) anomalyMonitor.stop()
        AnomalyMonitorHolder.instance = null
    }

    companion object {
        const val EXTRA_DOC_ID = "doc_id"
        const val EXTRA_URI = "doc_uri"
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}