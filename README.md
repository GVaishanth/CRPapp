# CRPapp – Predictive Crash Resilience Framework

**Version:** 2.0-phase9 (Release Engineering & Refinement)  
**Date:** 2026-06-26  
**Architectural Aim:** Demonstrating fail-safe, predictive resilience in document-heavy Android applications.

```
┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│   PREDICT    ├──────►│  CHECKPOINT  ├──────►│   RECOVER    │
└──────────────┘       └──────────────┘       └──────────────┘
```

---

## 1. Project Overview & Philosophy

CRPapp is an enterprise-grade **Predictive Crash Resilience Framework**. Its architectural objective is **not** to serve merely as a standard PDF reader, but to demonstrate an intelligent, fail-safe resilience paradigm. The entire framework revolves around one immutable, continuous lifecycle workflow:

$$\text{Predict} \longrightarrow \text{Checkpoint} \longrightarrow \text{Recover}$$

By continuously monitoring application, virtual machine, and operating system health metrics in real-time, CRPapp predicts impending instability (such as Dalvik Out-Of-Memory errors or ANRs), preserves exact reading session state atomically via Protocol Buffers, and executes an automated, transparent recovery cycle upon unexpected process termination.

---

## 2. Core Subsystems (Architectural Freeze)

In accordance with the enterprise roadmap, the three foundational engines operate under a strict architectural freeze to guarantee rock-solid stability and prevent regression:

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
*   **Responsibility:** Monitor real-time system stress and compute an explainable, lightweight anomaly score (`0.0–1.0`) without relying on black-box machine learning libraries.
*   **Physical Metrics:** Evaluates JVM Heap/PSS (weight `0.40`), CPU saturation via `/proc/self/stat` (weight `0.20`), UI render jank via `Choreographer` (weight `0.20`), and user scroll velocity (weight `0.20`).
*   **Smoothing:** Applies Exponentially Weighted Moving Average (EWMA) filtering ($\alpha = 0.35$) at 1 Hz to prevent transient spikes from causing checkpoint thrashing.
*   **Documentation:** [docs/ANOMALY_MODEL.md](docs/ANOMALY_MODEL.md)

### 2.2 Checkpoint Engine (`core/checkpoint/`)
*   **Responsibility:** Preserve full document session state (`page`, `zoom`, `offsetX`, `offsetY`, and anomaly context) with absolute crash-safety.
*   **Storage:** Built on Google Proto DataStore (`checkpoint_store.pb`), guaranteeing atomic, asynchronous, all-or-nothing writes to disk. Maintains a 3-deep ring buffer per document to enable robust fallback recovery if a crash tears an in-flight write.
*   **Documentation:** [docs/CHECKPOINT_MODEL.md](docs/CHECKPOINT_MODEL.md)

### 2.3 Recovery Engine (`core/recovery/`)
*   **Responsibility:** Survive unexpected application crashes, restart the process instantly, restore full user session context, and maintain a strict audit log of all recovery outcomes.
*   **Mechanism:** Attaches a global `UncaughtExceptionHandler` that triggers an `emergencySave()`, fires a restart intent with `FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_NEW_TASK`, and terminates the process via `exitProcess(0)`.
*   **Audit Logging:** Maintains an independent JSON event log (`recovery_journal.json`) on `Dispatchers.IO`, cleanly separating state preservation from recovery analytics.
*   **Documentation:** [docs/RECOVERY_MODEL.md](docs/RECOVERY_MODEL.md)

---

## 3. Observational Consoles & Telemetry

### 3.1 Protected Document Workspace (`ui/home/`)
Functions as the primary resilience console, displaying active system status ("Prediction Engine Active", "Checkpoint Manager Ready"), Quick Statistics, and recent protected sessions with recovery badges.

### 3.2 System Health Dashboard (`ui/dashboard/`)
An enterprise operational dashboard displaying rolling anomaly sparklines, live metrics, checkpoint histories, and recovery timelines. Strictly observational—operates entirely through `HealthDashboardViewModel` and never modifies engine state.

### 3.3 Resilience Analytics (`ui/analytics/`)
An executive resilience report console (Phase 8 & 9). Provides historical analysis of persisted checkpoint states and recovery outcomes, featuring background JSON and CSV export mechanics. 
*   **Documentation:** [docs/ANALYTICS_MODEL.md](docs/ANALYTICS_MODEL.md)

---

## 4. Comprehensive Documentation Index

To maintain complete transparency and rigorous architectural alignment, every facet of the CRPapp framework is documented in detail:

1.  **[System Architecture Specification](docs/ARCHITECTURE.md):** Detailed overview of subsystem boundaries, data flows, concurrency contracts, and lifecycle hygiene.
2.  **[Enterprise Implementation Roadmap](CRPapp_ROADMAP.md):** Milestone tracking from Phase 1 refactoring through Phase 9 quality refinement and Phase 10 release preparation.
3.  **[Anomaly Engine Model](docs/ANOMALY_MODEL.md):** Deep-dive into physical metric collection, EWMA smoothing equations, risk tiers, and explainable scoring.
4.  **[Checkpoint Engine Model](docs/CHECKPOINT_MODEL.md):** Protocol buffer schemas, ring buffer storage strategies, validation rules, and legacy SharedPreferences migration.
5.  **[Recovery Engine Model](docs/RECOVERY_MODEL.md):** Uncaught exception interception mechanics, process restart choreography, and decoupled JSON audit journaling.
6.  **[Resilience Analytics Model](docs/ANALYTICS_MODEL.md):** Historical data aggregation strategies, JSON/CSV export schemas, and documented session persistence limitations.

---

*CRPapp – Predictive Crash Resilience Framework*  
*Predict → Checkpoint → Recover*
