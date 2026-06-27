package com.example.crashresilientpdf.ui.viewer

import com.example.crashresilientpdf.core.pdf.PdfSessionController

/**
 * TabWorkspaceManager - Phase 11 (Version 3.0 Roadmap)
 *
 * Presentation-layer state holder managing the multi-document tabbed workspace.
 * Emulates a Google Chrome / Google Docs tabbed interface.
 *
 * - Maintains active in-memory document tabs.
 * - Manages active tab switching without relaunching the underlying Activity.
 * - Strictly respects the frozen core resilience engines (Zero engine modifications).
 *
 * Category: Presentation / Tab State Holder
 */
class TabWorkspaceManager {

    private val _tabs = mutableListOf<PdfSessionController.DocumentSource>()
    val tabs: List<PdfSessionController.DocumentSource> get() = _tabs

    var activeTabIndex: Int = -1
        private set

    val activeTab: PdfSessionController.DocumentSource?
        get() = if (activeTabIndex in _tabs.indices) _tabs[activeTabIndex] else null

    /**
     * Opens a new document tab or switches to it if already open.
     * Returns the active tab index.
     */
    fun openTab(source: PdfSessionController.DocumentSource): Int {
        val existingIndex = _tabs.indexOfFirst { it.docId == source.docId }
        if (existingIndex != -1) {
            activeTabIndex = existingIndex
            return existingIndex
        }
        _tabs.add(source)
        activeTabIndex = _tabs.lastIndex
        return activeTabIndex
    }

    /**
     * Selects an existing tab by index.
     */
    fun selectTab(index: Int): Boolean {
        if (index in _tabs.indices && index != activeTabIndex) {
            activeTabIndex = index
            return true
        }
        return false
    }

    /**
     * Closes a tab by index. Adjusts the active tab index cleanly.
     * Returns true if tabs still remain open, false if the workspace is now empty.
     */
    fun closeTab(index: Int): Boolean {
        if (index !in _tabs.indices) return _tabs.isNotEmpty()
        _tabs.removeAt(index)
        if (_tabs.isEmpty()) {
            activeTabIndex = -1
            return false
        }
        if (activeTabIndex >= index) {
            activeTabIndex = (activeTabIndex - 1).coerceAtLeast(0)
        }
        return true
    }

    fun isEmpty(): Boolean = _tabs.isEmpty()
}