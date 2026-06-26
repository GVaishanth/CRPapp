package com.example.crashresilientpdf

import com.example.crashresilientpdf.core.anomaly.AnomalyModel
import com.example.crashresilientpdf.core.anomaly.AnomalyState
import com.example.crashresilientpdf.core.checkpoint.Checkpoint
import com.example.crashresilientpdf.core.recovery.RecoveryJournal
import com.example.crashresilientpdf.core.recovery.RecoveryRecord
import org.junit.Assert.*
import org.junit.Test

/**
 * ResilienceUnitTests - Phase 10 (Release Engineering & Verification)
 *
 * Comprehensive unit test suite verifying the immutable Predict → Checkpoint → Recover workflow.
 * Validates frozen engine boundaries, model clamping, validation bounds, and metadata correctness.
 */
class ResilienceUnitTests {

    // --- 1. PREDICT: AnomalyModel Tests ---

    @Test
    fun testAnomalyModel_weightsSumToOne() {
        val model = AnomalyModel()
        val w = model.weights
        val sum = w.memory + w.cpu + w.render + w.scroll
        assertEquals("Weights must sum exactly to 1.0", 1.0, sum, 0.0001)
        assertEquals(0.40, w.memory, 0.0001)
        assertEquals(0.20, w.cpu, 0.0001)
        assertEquals(0.20, w.render, 0.0001)
        assertEquals(0.20, w.scroll, 0.0001)
    }

    @Test
    fun testAnomalyModel_scoreClampingAndTiers() {
        val model = AnomalyModel()

        // LOW tier test
        val stateLow = model.evaluate(0.1, 0.1, 0.1, 0.1)
        assertTrue("Score should be clamped in 0..1", stateLow.score in 0.0..1.0)
        assertEquals("Should be LOW risk tier", AnomalyState.RiskTier.LOW, stateLow.riskTier)

        // ELEVATED tier test (0.4 .. 0.699)
        val stateElevated = model.evaluate(0.6, 0.5, 0.5, 0.5)
        assertTrue("Score should be in ELEVATED range", stateElevated.score >= 0.4 && stateElevated.score < 0.7)
        assertEquals("Should be ELEVATED risk tier", AnomalyState.RiskTier.ELEVATED, stateElevated.riskTier)

        // HIGH tier test (>= 0.7)
        val stateHigh = model.evaluate(0.95, 0.9, 0.9, 0.9)
        assertTrue("Score should be in HIGH range", stateHigh.score >= 0.7)
        assertEquals("Should be HIGH risk tier", AnomalyState.RiskTier.HIGH, stateHigh.riskTier)
    }

    @Test
    fun testAnomalyModel_ewmaSmoothing() {
        val model = AnomalyModel()
        // First sample seeds directly
        val s1 = model.evaluate(0.5, 0.5, 0.5, 0.5)
        assertEquals(0.5, s1.score, 0.001)

        // Transient spike should be smoothed by alpha = 0.35
        val s2 = model.evaluate(1.0, 1.0, 1.0, 1.0)
        assertTrue("EWMA should dampen immediate spike", s2.score < 1.0)
        assertEquals(0.5 + 0.35 * 0.5, s2.score, 0.01)
    }

    // --- 2. CHECKPOINT: Checkpoint Validation Tests ---

    @Test
    fun testCheckpoint_validState() {
        val cp = Checkpoint(
            checkpointVersion = 2,
            recoveryVersion = 2,
            documentId = "asset:sample.pdf",
            displayName = "sample.pdf",
            page = 12,
            zoom = 1.2f,
            offsetX = 10f,
            offsetY = 20f,
            timestampMs = System.currentTimeMillis(),
            anomalyScore = 0.15f,
            riskTier = 0,
            triggerReason = null,
            trigger = Checkpoint.Trigger.AUTO_INTERVAL
        )
        assertTrue("Checkpoint should be valid", cp.isValid())
    }

    @Test
    fun testCheckpoint_invalidBounds() {
        val emptyDoc = Checkpoint(documentId = "", displayName = "sample", page = 1, timestampMs = 1000L)
        assertFalse("Empty documentId should be invalid", emptyDoc.isValid())

        val negPage = Checkpoint(documentId = "doc1", displayName = "sample", page = -1, timestampMs = 1000L)
        assertFalse("Negative page should be invalid", negPage.isValid())

        val invalidZoom = Checkpoint(documentId = "doc1", displayName = "sample", page = 1, zoom = -1f, timestampMs = 1000L)
        assertFalse("Negative zoom should be invalid", invalidZoom.isValid())

        val invalidScore = Checkpoint(documentId = "doc1", displayName = "sample", page = 1, anomalyScore = 1.5f, timestampMs = 1000L)
        assertFalse("Score > 1.0 should be invalid", invalidScore.isValid())
    }

    // --- 3. RECOVER: Recovery Record & Journal Tests ---

    @Test
    fun testRecoveryRecord_formattingAndType() {
        val recordNormal = RecoveryJournal.createRecord(
            documentId = "doc1",
            displayName = "sample.pdf",
            restoredPage = 10,
            restoredZoom = 1.0f,
            checkpointAgeMs = 4500L,
            triggerType = "AUTO_INTERVAL",
            recoveryDurationMs = 824L,
            recoverySource = "viewer_launch",
            fallbackUsed = false,
            validationResult = "OK"
        )
        assertEquals("Should be NORMAL recovery type", RecoveryRecord.RecoveryType.NORMAL, recordNormal.recoveryType)
        assertEquals("just now", recordNormal.checkpointAgeText)

        val recordFallback = RecoveryJournal.createRecord(
            documentId = "doc1",
            displayName = "sample.pdf",
            restoredPage = 5,
            restoredZoom = 1.0f,
            checkpointAgeMs = 15000L,
            triggerType = "AUTO_ANOMALY",
            recoveryDurationMs = 950L,
            recoverySource = "viewer_launch",
            fallbackUsed = true,
            validationResult = "OK"
        )
        assertEquals("Should be FALLBACK recovery type", RecoveryRecord.RecoveryType.FALLBACK, recordFallback.recoveryType)
        assertEquals("15s ago", recordFallback.checkpointAgeText)
    }
}
