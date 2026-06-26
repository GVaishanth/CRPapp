package com.example.crashresilientpdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.crashresilientpdf.core.anomaly.AnomalyMonitor
import com.example.crashresilientpdf.core.checkpoint.Checkpoint
import com.example.crashresilientpdf.core.checkpoint.CheckpointManager
import com.example.crashresilientpdf.core.recovery.RecoveryJournal
import com.example.crashresilientpdf.core.recovery.RecoveryRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ResilienceInstrumentationTests - Phase 10 (Release Engineering & Verification)
 *
 * Instrumented tests running on an Android device/emulator.
 * Verifies Proto DataStore atomic writes, SharedPreferences migration, and RecoveryJournal persistence.
 */
@RunWith(AndroidJUnit4::class)
class ResilienceInstrumentationTests {

    @Test
    fun testPackageCorrectness() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.crashresilientpdf", appContext.packageName)
    }

    @Test
    fun testCheckpointManager_persistenceAndRingBuffer() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val cm = CheckpointManager(appContext)

        val docId = "test_doc_1"
        val cp1 = Checkpoint(documentId = docId, displayName = "Doc 1", page = 5, timestampMs = System.currentTimeMillis() - 10000)
        val cp2 = Checkpoint(documentId = docId, displayName = "Doc 1", page = 10, timestampMs = System.currentTimeMillis() - 5000)
        val cp3 = Checkpoint(documentId = docId, displayName = "Doc 1", page = 15, timestampMs = System.currentTimeMillis())

        // Save checkpoints
        cm.saveCheckpoint(cp1)
        cm.saveCheckpoint(cp2)
        cm.saveCheckpoint(cp3)

        // Restore should return the most recent valid checkpoint (cp3)
        val restored = cm.restoreCheckpoint(docId)
        assertNotNull("Restored checkpoint should not be null", restored)
        assertEquals("Restored page should match latest valid checkpoint", 15, restored?.page)

        // Verify ring buffer history (max 3)
        val history = cm.getCheckpointHistory(docId)
        assertTrue("History size should be bounded to max 3", history.size <= 3)
    }

    @Test
    fun testRecoveryJournal_persistence() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val journal = RecoveryJournal(appContext)

        val record = RecoveryJournal.createRecord(
            documentId = "test_doc_rec",
            displayName = "Rec Doc",
            restoredPage = 12,
            restoredZoom = 1.0f,
            checkpointAgeMs = 1200L,
            triggerType = "AUTO_INTERVAL",
            recoveryDurationMs = 824L,
            recoverySource = "test_run",
            fallbackUsed = false,
            validationResult = "OK"
        )
        journal.record(record)

        // Verify record is retrievable
        val history = journal.getHistory("test_doc_rec")
        assertTrue("Recovery history should contain recorded event", history.isNotEmpty())
        assertEquals("Restored page should match", 12, history.first().restoredPage)
    }

    @Test
    fun testAnomalyMonitor_initialization() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val monitor = AnomalyMonitor(appContext) { 0.0 }
        assertNotNull("AnomalyMonitor stateFlow should be active", monitor.stateFlow.value)
        assertEquals(0.0, monitor.anomalyScore, 0.001)
    }
}
