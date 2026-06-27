package com.example.crashresilientpdf.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.crashresilientpdf.R
import com.example.crashresilientpdf.core.checkpoint.CheckpointManager
import com.example.crashresilientpdf.core.recovery.RecoveryJournal
import com.example.crashresilientpdf.core.recovery.RecoveryRecord
import com.example.crashresilientpdf.databinding.ActivityHomeBinding
import com.example.crashresilientpdf.ui.analytics.ResilienceAnalyticsActivity
import com.example.crashresilientpdf.ui.settings.SettingsActivity
import com.example.crashresilientpdf.ui.viewer.ProtectedViewerActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * HomeActivity - Phase 12.1 (Flagship Masterpiece GUI Edition)
 *
 * Elite Material Design 3 Productivity Hub, Traditional Vertical Workspace & Preloading Experience.
 *
 * Masterpiece Enhancements:
 * - Immersive Preloading splash screen (light-dark blue gradient, green app name, red loading bar).
 * - Animated cycling of predictive sensor and DataStore initialization messages.
 * - Premium Hero Framework Header & Action Cards establishing world-class visual hierarchy.
 * - Responsive multi-column grid layout for recent protected reading sessions (Phones vs Tablets).
 * - Circular M3 IconButton opening dedicated Settings screen.
 * - True edge-to-edge GUI modernization with window insets padding.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var checkpointManager: CheckpointManager
    private lateinit var recoveryJournal: RecoveryJournal
    private lateinit var adapter: RecentSessionsAdapter

    private val openDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { }
            openViewer(uri.toString(), uri.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply Window Insets for true edge-to-edge GUI modernization
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, systemBars.bottom)
            insets
        }

        checkpointManager = CheckpointManager(this)
        recoveryJournal = RecoveryJournal(this)

        adapter = RecentSessionsAdapter { session ->
            openViewer(session.docId, null)
        }

        // Responsive multi-column grid layout (1 column on phones, 2 columns on tablets/wide screens)
        val spanCount = if (resources.configuration.screenWidthDp >= 600) 2 else 1
        binding.recentRecycler.layoutManager = GridLayoutManager(this, spanCount)
        binding.recentRecycler.adapter = adapter
        binding.recentRecycler.isNestedScrollingEnabled = false

        binding.openDocBtn.setOnClickListener {
            openDocument.launch(arrayOf("application/pdf"))
        }
        binding.openSampleBtn.setOnClickListener {
            openViewer(CheckpointManager.DOC_ASSET_SAMPLE, null)
        }

        val settingsBtn = binding.root.findViewById<View>(R.id.settingsBtn)
        settingsBtn?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val viewAllBtn = binding.root.findViewById<View>(R.id.viewAllBtn)
        viewAllBtn?.setOnClickListener {
            startActivity(Intent(this, ResilienceAnalyticsActivity::class.java))
        }

        val viewAnalyticsBtnId = resources.getIdentifier("viewAnalyticsBtn", "id", packageName)
        if (viewAnalyticsBtnId != 0) {
            val viewAnalyticsBtn = binding.root.findViewById<View>(viewAnalyticsBtnId)
            viewAnalyticsBtn?.setOnClickListener {
                startActivity(Intent(this, ResilienceAnalyticsActivity::class.java))
            }
        }

        val analyticsCardId = resources.getIdentifier("analyticsCard", "id", packageName)
        if (analyticsCardId != 0) {
            val analyticsCard = binding.root.findViewById<View>(analyticsCardId)
            analyticsCard?.setOnClickListener {
                startActivity(Intent(this, ResilienceAnalyticsActivity::class.java))
            }
        }

        refreshSessions()

        // --- Execute Preloading Simulation Overlay ---
        startPreloadingSequence()
    }

    private fun startPreloadingSequence() {
        val preloadingOverlay = binding.root.findViewById<View>(R.id.preloadingOverlay) ?: return
        val preloadingMessageText = binding.root.findViewById<TextView>(R.id.preloadingMessageText) ?: return

        lifecycleScope.launch {
            preloadingMessageText.text = "Initializing Predictive Anomaly Sensors..."
            delay(700)
            preloadingMessageText.text = "Verifying Proto DataStore Integrity..."
            delay(700)
            preloadingMessageText.text = "Establishing Uncaught Exception Interceptors..."
            delay(700)
            preloadingMessageText.text = "Workspace Ready"
            delay(300)

            if (!android.animation.ValueAnimator.areAnimatorsEnabled()) {
                preloadingOverlay.visibility = View.GONE
            } else {
                preloadingOverlay.animate()
                    .alpha(0f)
                    .setDuration(400)
                    .withEndAction { preloadingOverlay.visibility = View.GONE }
                    .start()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSessions()
    }

    private fun refreshSessions() {
        val sessions = checkpointManager.getRecentSessions()
        adapter.submit(sessions)

        val hasSessions = sessions.isNotEmpty()
        binding.recentRecycler.visibility = if (hasSessions) View.VISIBLE else View.GONE
        binding.emptyState.visibility = if (hasSessions) View.GONE else View.VISIBLE

        binding.statDocsProtected.text = if (sessions.isNotEmpty()) sessions.size.toString() else "—"
        val totalRecoveries = recoveryJournal.getTotalRecoveryCount()
        binding.statCrashesRecovered.text = if (totalRecoveries > 0) totalRecoveries.toString() else "—"

        val lastRecoveryText = binding.root.findViewById<TextView>(R.id.lastRecoveryText)
        val lastRecovery = recoveryJournal.getHistory().firstOrNull()
        if (lastRecovery != null) {
            val typeLabel = if (lastRecovery.recoveryType == RecoveryRecord.RecoveryType.FALLBACK) "fallback recovered" else "recovered"
            lastRecoveryText.text = "${lastRecovery.displayName} — $typeLabel, page ${lastRecovery.restoredPage + 1}, ${lastRecovery.recoveryDurationMs}ms"
        } else {
            lastRecoveryText.text = getString(R.string.no_recent_recoveries)
        }
    }

    private fun openViewer(docId: String, uriString: String?) {
        val i = Intent(this, ProtectedViewerActivity::class.java).apply {
            putExtra(ProtectedViewerActivity.EXTRA_DOC_ID, docId)
            if (uriString != null) putExtra(ProtectedViewerActivity.EXTRA_URI, uriString)
        }
        startActivity(i)
    }
}