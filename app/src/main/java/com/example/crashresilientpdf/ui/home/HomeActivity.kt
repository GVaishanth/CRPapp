package com.example.crashresilientpdf.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.crashresilientpdf.R
import com.example.crashresilientpdf.core.checkpoint.CheckpointManager
import com.example.crashresilientpdf.core.checkpoint.ProtectedSession
import com.example.crashresilientpdf.databinding.ActivityHomeBinding
import com.example.crashresilientpdf.ui.viewer.ProtectedViewerActivity

/**
 * HomeActivity - Protected Document Workspace
 * Phase 2 - Entry point for the Predictive Crash Resilience Framework
 *
 * Communicates: Stability, Protection, Intelligence
 * NOT a file manager - this is a resilience console.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var checkpointManager: CheckpointManager
    private lateinit var recoveryJournal: com.example.crashresilientpdf.core.recovery.RecoveryJournal
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
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkpointManager = CheckpointManager(this)
        recoveryJournal = com.example.crashresilientpdf.core.recovery.RecoveryJournal(this)

        adapter = RecentSessionsAdapter { session ->
            openViewer(session.docId, null)
        }
        binding.recentRecycler.layoutManager = LinearLayoutManager(this)
        binding.recentRecycler.adapter = adapter
        binding.recentRecycler.isNestedScrollingEnabled = false

        binding.openDocBtn.setOnClickListener {
            openDocument.launch(arrayOf("application/pdf"))
        }
        binding.openSampleBtn.setOnClickListener {
            openViewer(CheckpointManager.DOC_ASSET_SAMPLE, null)
        }

        binding.viewAnalyticsBtn.setOnClickListener {
            startActivity(Intent(this, com.example.crashresilientpdf.ui.analytics.ResilienceAnalyticsActivity::class.java))
        }
        binding.analyticsCard.setOnClickListener {
            startActivity(Intent(this, com.example.crashresilientpdf.ui.analytics.ResilienceAnalyticsActivity::class.java))
        }

        refreshSessions()
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

        // Quick stats - real where available, placeholder where not
        binding.statDocsProtected.text = if (sessions.isNotEmpty()) sessions.size.toString() else "—"
        val totalRecoveries = recoveryJournal.getTotalRecoveryCount()
        binding.statCrashesRecovered.text = if (totalRecoveries > 0) totalRecoveries.toString() else "—"

        // Last recovery summary - from RecoveryJournal
        val lastRecoveryText = binding.root.findViewById<TextView>(R.id.lastRecoveryText)
        val lastRecovery = recoveryJournal.getHistory().firstOrNull()
        if (lastRecovery != null) {
            val typeLabel = if (lastRecovery.recoveryType == com.example.crashresilientpdf.core.recovery.RecoveryRecord.RecoveryType.FALLBACK) "fallback recovered" else "recovered"
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
