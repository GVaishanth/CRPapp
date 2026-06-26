package com.example.crashresilientpdf.core.checkpoint

/**
 * ProtectedSession - Phase 2
 * Lightweight metadata for the Recent Protected Sessions list on Home.
 *
 * In Phase 1, CheckpointManager only stored page:Int.
 * For Phase 2 Home UI we need to list recent documents.
 * This is a read-only projection over existing checkpoints - no schema change yet.
 *
 * Phase 5 will replace this with rich Checkpoint objects (docId, zoom, offset, etc.)
 */
data class ProtectedSession(
    val docId: String,
    val displayName: String,
    val lastPage: Int,
    val lastCheckpointMs: Long,
    val recoveryCount: Int = 0
)
