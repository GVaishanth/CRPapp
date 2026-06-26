package com.example.crashresilientpdf.core.recovery

import android.app.Application
import android.content.Context
import android.content.Intent
import com.example.crashresilientpdf.core.checkpoint.CheckpointManager
import com.example.crashresilientpdf.core.pdf.PdfSessionController
import kotlin.system.exitProcess

/**
 * RecoveryManager - Phase 5
 *
 * - Installs global UncaughtExceptionHandler in Application
 * - Saves checkpoint on crash (best-effort, non-blocking)
 * - Records recovery metadata via CheckpointManager
 *
 * ANOMALY ENGINE IS FROZEN - not touched.
 */
object RecoveryManager {

    private var checkpointManager: CheckpointManager? = null
    private var pdfSessionController: PdfSessionController? = null
    private var appContext: Context? = null
    private var currentDocId: String = CheckpointManager.DOC_ASSET_SAMPLE

    fun install(app: Application) {
        appContext = app.applicationContext
        Thread.setDefaultUncaughtExceptionHandler { _, _ ->
            emergencySave()
            restartApp()
        }
    }

    fun registerSession(
        context: Context,
        checkpointManager: CheckpointManager,
        pdfSessionController: PdfSessionController,
        docId: String = CheckpointManager.DOC_ASSET_SAMPLE,
        @Suppress("UNUSED_PARAMETER") displayName: String = "sample.pdf"
    ) {
        this.appContext = context.applicationContext
        this.checkpointManager = checkpointManager
        this.pdfSessionController = pdfSessionController
        this.currentDocId = docId

        Thread.setDefaultUncaughtExceptionHandler { _, _ ->
            emergencySave()
            restartApp(context)
        }
    }

    private fun emergencySave() {
        try {
            val cm = checkpointManager ?: return
            val pdf = pdfSessionController ?: return
            // Best-effort, non-blocking - uses CheckpointManager compat path
            // which launches a coroutine with DataStore atomic write
            cm.saveCheckpoint(currentDocId, pdf.currentPage)
        } catch (_: Exception) {}
    }

    fun simulateCrash(
        context: Context,
        checkpointManager: CheckpointManager,
        pdfSessionController: PdfSessionController,
        docId: String = CheckpointManager.DOC_ASSET_SAMPLE,
        displayName: String = "sample.pdf"
    ) {
        // Synchronous-feeling save - use compat which launches IO coroutine,
        // then give it a brief moment before killing process
        // For a true crash simulation we still want the checkpoint to land
        checkpointManager.saveCheckpoint(docId, pdfSessionController.currentPage)
        try { Thread.sleep(120) } catch (_: Exception) {}

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        exitProcess(0)
    }

    private fun restartApp(context: Context? = appContext) {
        val ctx = context ?: return
        try {
            val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ctx.startActivity(intent)
        } catch (_: Exception) {}
        exitProcess(0)
    }
}
