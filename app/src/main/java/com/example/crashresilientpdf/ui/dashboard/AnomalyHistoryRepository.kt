package com.example.crashresilientpdf.ui.dashboard

import com.example.crashresilientpdf.core.anomaly.AnomalyState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AnomalyHistoryRepository - Phase 7
 *
 * Presentation-layer rolling history collector.
 * Subscribes to AnomalyMonitor.stateFlow, builds a ring buffer.
 *
 * This is NOT part of the Prediction Engine.
 * The Prediction Engine (AnomalyMonitor / AnomalyModel) remains FROZEN.
 *
 * Category: Presentation / UI data
 */
class AnomalyHistoryRepository(
    private val maxSamples: Int = 120
) {
    private val lock = Any()
    private val _history = MutableStateFlow<List<AnomalyState>>(emptyList())
    val history: StateFlow<List<AnomalyState>> = _history.asStateFlow()

    fun push(state: AnomalyState) {
        synchronized(lock) {
            val current = _history.value
            val next = if (current.size >= maxSamples) {
                current.drop(1) + state
            } else {
                current + state
            }
            _history.value = next
        }
    }

    fun clear() {
        synchronized(lock) {
            _history.value = emptyList()
        }
    }

    fun snapshot(): List<AnomalyState> = synchronized(lock) { _history.value }
}
