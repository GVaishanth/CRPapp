# CRPapp – System Architecture & Resilience Framework Specification

**Version:** 2.0-phase7.1  
**Date:** 2026-06-26  
**Status:** Stabilized & Remediated (Phase 7.1)

---

## 1. Executive Summary & Core Philosophy

CRPapp is an enterprise-grade **Predictive Crash Resilience Framework** designed to protect reading and interaction sessions in document-heavy Android applications. Its architectural objective is **not** to serve merely as a PDF reader, but to demonstrate a fail-safe, predictive resilience paradigm revolving around one immutable, continuous lifecycle workflow:

```
┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│   PREDICT    ├──────►│  CHECKPOINT  ├──────►│   RECOVER    │
└──────────────┘       └──────────────┘       └──────────────┘
```

The system continuously assesses application, JVM, and operating system health metrics in real-time, predicts impending crashes (such as Dalvik Out-Of-Memory errors or ANRs), preserves exact session state atomically via Protocol Buffers, and executes an automated, transparent recovery cycle upon process termination.

---

## 2. Subsystem Architecture

```
┌────────────────────────────────────────────────────────┐
│               ProtectedViewerActivity                  │
└──────┬───────────────────┬──────────────────────┬──────┘
       │                   │                      │
       ▼ (1 Hz Flow)       ▼ (DataStore Proto)    ▼ (UncaughtException)
┌──────────────┐    ┌──────────────┐    ┌────────────────┐
│  PREDICTION  │───►│  CHECKPOINT  │───►│    RECOVERY    │
│    ENGINE    │    │    ENGINE    │    │     ENGINE     │
└──────────────┘    └──────────────┘    └────────────────┘
```

### 2.1 Prediction Engine (`core/anomaly/`)

**Responsibility:** Monitor real-time system stress and compute an explainable, lightweight anomaly score without relying on black-box machine learning libraries.

*   **Metrics Collector (`MetricsCollector.kt`):** Captures 4 real-time physical metrics:
    1.  **Memory (Weight 0.40):** A weighted blend of JVM heap utilization (`(total - free) / max`), OS memory pressure (`ActivityManager.MemoryInfo`), and native RAM footprint (`totalPss` normalized against a 256 MB budget).
    2.  **CPU (Weight 0.20):** Evaluates process CPU saturation via `/proc/self/stat` (utime+stime delta) with an automated fallback to `Debug.threadCpuTimeNanos()` on Android 8.0+ when OS throttling occurs.
    3.  **Render / Jank (Weight 0.20):** Measures frame deadline misses (>32ms) via `Choreographer.FrameCallback` over a 1-second sliding window.
    4.  **Scroll / Interaction Intensity (Weight 0.20):** Tracks user scroll velocity via `PdfSessionController.getScrollSpeed()`, mapped through a soft knee equation `speed / (speed + 1.2)`.

*   **Scoring & Smoothing (`AnomalyModel.kt`):** Applies Exponentially Weighted Moving Average (EWMA) smoothing ($\alpha = 0.35$) to prevent transient spikes (e.g., single GC pauses) from causing checkpoint thrashing. Output is strictly clamped to `[0.0, 1.0]`.
*   **Risk Tiers:**
    *   **LOW (< 0.4):** Protected state. Normal background checkpointing.
    *   **ELEVATED (0.4 – 0.699):** Elevated risk. Auto-saves every 5 seconds.
    *   **HIGH (≥ 0.7):** Critical risk. Triggers immediate atomic checkpointing.

---

### 2.2 Checkpoint Engine (`core/checkpoint/`)

**Responsibility:** Preserve full document session state (Document ID, Page N, Zoom Level, Pan X/Y Offset, and Anomaly Metadata) with absolute crash-safety and zero data corruption.

*   **Storage Architecture (`checkpoint_store.pb`):** Built on Google Proto DataStore, guaranteeing atomic, asynchronous, all-or-nothing writes to disk.
*   **Data Model (`Checkpoint.kt`):** Rich versioned data class capturing spatial position (`page`, `zoom`, `offsetX`, `offsetY`) and predictive context (`anomalyScore`, `riskTier`, `triggerReason`, `trigger`).
*   **Ring Buffer (`CheckpointManager.kt`):** Keeps the newest 3 checkpoints per document. If a crash tears an in-flight write, `restoreCheckpoint()` automatically iterates newest-to-oldest to execute a graceful fallback restore.
*   **Legacy Migration (`CheckpointMigrator.kt`):** Automatically migrates Phase 1-4 SharedPreferences data (`page_<hash>`) into Proto DataStore on startup without user data loss.

---

### 2.3 Recovery Engine (`core/recovery/`)

**Responsibility:** Survive unexpected application crashes, restart the process instantly, restore full user session context, and maintain a strict audit log of all recovery outcomes.

*   **Global Exception Handling (`RecoveryManager.kt`):** Attaches an `UncaughtExceptionHandler` at the `Application` level (`CrashResilientApp.kt`). Upon an unhandled crash, it executes a synchronous-feeling `emergencySave()`, fires a restart intent with `FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_NEW_TASK`, and terminates the process via `exitProcess(0)`.
*   **Audit Logging (`RecoveryJournal.kt`):** Maintains an independent JSON event log (`recovery_journal.json`) capped at 50 entries. A recovery event never modifies checkpoint history, cleanly separating state preservation from recovery analytics.
*   **Phase 7.1 Remediation:** Optimized with in-memory caching and `Dispatchers.IO` coroutines to completely eliminate main-thread file I/O stalls during app launch.

---

## 3. Observational Console & Presentation Layer

### 3.1 Protected Document Workspace (`ui/home/HomeActivity.kt`)
Functions as the primary resilience console, displaying active system status ("Prediction Engine Active", "Checkpoint Manager Ready"), Quick Statistics, and recent protected sessions with recovery badges.

### 3.2 Protected Viewer UI (`ui/viewer/ProtectedViewerActivity.kt`)
Houses the main PDF rendering view and the custom `ProtectedScrollerView` (minimum 48dp touch target, floating page preview bubbles). Integrates the collapsible Resilience HUD showing real-time metrics breakdown and dynamic protection status indicators.

### 3.3 System Health Dashboard (`ui/dashboard/HealthDashboardActivity.kt`)
An enterprise operational dashboard displaying rolling anomaly sparklines, live metrics, checkpoint histories, and recovery timelines. Strictly observational—operates entirely through `HealthDashboardViewModel` and never modifies engine state.

---

## 4. Concurrency & Lifecycle Boundaries

```
┌──────────────────────────────┬───────────────────────────────┐
│          OPERATIONS          │      THREADING & CONCURRENCY  │
├──────────────────────────────┼───────────────────────────────┤
│ Anomaly Monitoring (1 Hz)    │ Main Looper Handler           │
│ Proto DataStore Checkpoints  │ Dispatchers.IO Coroutines     │
│ RecoveryJournal Audit I/O    │ Dispatchers.IO Coroutines     │
│ Health Dashboard Polling     │ Dispatchers.IO (10 FPS UI)    │
│ onPause Checkpoint Save      │ NonCancellable Context        │
└──────────────────────────────┴───────────────────────────────┘
```

By strictly separating disk I/O onto `Dispatchers.IO`, wrapping lifecycle transitions in `NonCancellable` execution blocks, and managing static references cleanly, the framework ensures zero memory leaks, zero UI jank, and rock-solid crash resilience.
