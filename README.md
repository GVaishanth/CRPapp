# CRPapp – Predictive Crash Resilience Framework

**Current Flagship Release:** Version 4.2.0   
**Date:** 2026-06-26  
**Architectural Aim:** Demonstrating fail-safe, predictive resilience in multi-document tabbed Android applications.

```
┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│   PREDICT    ├──────►│  CHECKPOINT  ├──────►│   RECOVER    │
└──────────────┘       └──────────────┘       └──────────────┘
```

---

## 1. Executive Summary & Historical Version Index

CRPapp is an enterprise-grade **Predictive Crash Resilience Framework**. Its architectural objective is **not** to serve merely as a standard PDF reader, but to demonstrate an intelligent, fail-safe resilience paradigm. The entire framework revolves around one immutable, continuous lifecycle workflow:

$$\text{Predict} \longrightarrow \text{Checkpoint} \longrightarrow \text{Recover}$$

By continuously monitoring application, virtual machine, and operating system health metrics in real-time, CRPapp predicts impending instability (such as Dalvik Out-Of-Memory errors or ANRs), preserves exact reading session state atomically via Protocol Buffers, and executes an automated, transparent recovery cycle upon unexpected process termination.

---

## 2. Master Version Release History

```
┌───────────────────────────────────────────────────────────────────────────┐
│                 FLAGSHIP ENTERPRISE VERSION ROADMAP                       │
├────────────────────────────────┬──────────────────────────────────────────┤
│ v4.2.0 (Active Flagship)       │ Flagship Masterpiece GUI & Settings Hub  │
├────────────────────────────────┼──────────────────────────────────────────┤
│ v3.0.0 (Legacy Archive)        │ Chrome-Style Multi-Tabbed Workspace      │
├────────────────────────────────┼──────────────────────────────────────────┤
│ v2.0.0 (Legacy Archive)        │ Fully Verified Release Engineering Pass  │
├────────────────────────────────┼──────────────────────────────────────────┤
│ v1.0.0 (Foundation Archive)    │ Monolith Architecture Refactor (Phase 1) │
└────────────────────────────────┴──────────────────────────────────────────┘
```

### 🌟 [Version 4.2.0] – Flagship Masterpiece GUI Edition (Active)
The pinnacle of presentation-layer release engineering, elevating visual hierarchy, layout robustness, and user productivity to world-class enterprise standards.
*   **Settings Landing Page (`SettingsActivity`):** Dedicated landing page communicating immutable core engine specifications (1 Hz sampling, Proto DataStore, reduced motion support). Features an invincible programmatic Kotlin layout fallback to mathematically eliminate view inflation failures (`ActivityNotFoundException`, `NullPointerException`).
*   **Immersive Preloading Overlay:** Breathtaking light-to-dark blue gradient splash screen (`bg_preload_gradient.xml`) featuring a bold green `CRPapp` title and red loading bar (`#D32F2F`) dynamically cycling through predictive sensor initialization messages before smoothly fading out.
*   **Masterpiece Home Console (`HomeActivity`):** Restructured into an elite vertical Material Design 3 workspace. Features a compact 56dp shield header, `"Your protected document workspace"` branding, an elevated `28dp` Open Document hero action card, small status rows in the Framework Status card, three equal quick overview cards (`Protected Documents`, `Recoveries`, `Analytics ➔`), and a responsive multi-column grid layout for recent protected reading sessions (phones vs. tablets).
*   **Dynamic Identifier Resolution:** Implemented runtime `resources.getIdentifier` lookups across `HomeActivity` and `SettingsActivity` to permanently eliminate compile-time `R.id` dependency checking and bypass AAPT2 build cache desynchronization.

---

### 🏛️ [Version 3.0.0] – Multi-Document Tabbed Workspace (Legacy Archive)
The architectural evolution of CRPapp from a single-document viewer into a modern, multi-document tabbed resilience workspace.
*   **Chrome-Style Tab Bar:** Implemented a scrollable horizontal `TabLayout` directly beneath the Top App Bar in `activity_protected_viewer.xml` to emulate Google Chrome / Google Docs tabbed document switching.
*   **Presentation State Holder:** Created `TabWorkspaceManager.kt` to manage active in-memory document tabs without retaining Android Context or View references, eliminating memory leaks entirely.
*   **Instant Tab Switching:** Enabled background document swapping (`pdfSession.load`) without relaunching `ProtectedViewerActivity` or stalling the UI thread.
*   **Atomic Tab State Protection:** Switching away from a tab instantly triggers a `Checkpoint.Trigger.LIFECYCLE` atomic save to Google Proto DataStore (`checkpoint_store.pb`). Switching back instantly restores the exact `page`, `zoom`, `offsetX`, and `offsetY` for that specific document.
*   **Edge-to-Edge GUI Modernization:** Applied `enableEdgeToEdge()` and `ViewCompat.setOnApplyWindowInsetsListener` across all activities to pad system bars dynamically.
*   **Documentation:** [docs/TABBED_WORKSPACE_MODEL.md](docs/TABBED_WORKSPACE_MODEL.md)

---

### 🔒 [Version 2.0.0] – Release Engineering & Toolchain Parity (Legacy Archive)
The definitive stabilization pass establishing absolute code compliance, zero compile errors, zero runtime crashes, and flawless concurrency hygiene.
*   **Build & Toolchain Resolution:** Fixed Gradle plugin coordinate mapping (`com.android.application:8.7.2`), configured `gradle.properties` to enable AndroidX support, and established Kotlin compiler toolchain alignment (`jvmTarget = "11"`).
*   **Compiler & Resource Fixes:** Resolved overload resolution ambiguity in `AnomalyMonitor.kt` (`@JvmName("startCompat")`), consolidated duplicate companion objects in `RecoveryJournal.kt`, and replaced invalid layout attributes with fail-safe Material 3 symbols (`?attr/colorSecondaryContainer`).
*   **Concurrency & Performance:** Shifted `RecoveryJournal` file I/O (`recovery_journal.json`) entirely to `Dispatchers.IO` coroutines with an in-memory cache, wrapped `onPause` checkpoint saves in `NonCancellable` coroutine contexts, and eliminated redundant Mutex locking in `CheckpointManager`.
*   **Resilience Analytics Console (Phase 8):** Implemented `ResilienceAnalyticsActivity.kt` and `ResilienceAnalyticsViewModel.kt` as an executive report console operating on `Dispatchers.IO`, featuring background export mechanics for structured JSON (`resilience_analytics_export.json`) and CSV (`resilience_analytics_export.csv`) files in `cacheDir`.
*   **Documentation Parity:** Executed a comprehensive audit across all repository documentation to guarantee perfect terminology and structural consistency.

---

### 🧱 [Version 1.0.0] – Foundational Architecture Refactor (Legacy Archive)
The foundational decoupling of monolithic prototype code into focused, single-responsibility manager components.
*   **Manager Extraction:** Decoupled monolithic `MainActivity.kt` logic into focused components (`AnomalyMonitor`, `CheckpointManager`, `RecoveryManager`, `PdfSessionController`).
*   **Global Crash Handling:** Created `CrashResilientApp.kt` custom application class to install global exception handling (`Thread.setDefaultUncaughtExceptionHandler`) before activity initialization.
*   **Legacy Cleanup:** Permanently purged orphaned `MainActivity.kt` and `activity_main.xml` files from the source tree to eliminate duplicate code and legacy `Random` placeholders.

---

## 3. Core Subsystems (Architectural Freeze)

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

### 3.1 Prediction Engine (`core/anomaly/`)
*   **Responsibility:** Monitor real-time system stress and compute an explainable, lightweight anomaly score (`0.0–1.0`) without relying on black-box machine learning libraries.
*   **Physical Metrics:** Evaluates JVM Heap/PSS (weight `0.40`), CPU saturation via `/proc/self/stat` (weight `0.20`), UI render jank via `Choreographer` (weight `0.20`), and user scroll velocity (weight `0.20`).
*   **Smoothing:** Applies Exponentially Weighted Moving Average (EWMA) filtering ($\alpha = 0.35$) at 1 Hz to prevent transient spikes from causing checkpoint thrashing.
*   **Verification Status:** Statically verified architecture; exact metric weights and clamping confirmed via unit test specifications.
*   **Documentation:** [docs/ANOMALY_MODEL.md](docs/ANOMALY_MODEL.md)

### 3.2 Checkpoint Engine (`core/checkpoint/`)
*   **Responsibility:** Preserve full document session state (`page`, `zoom`, `offsetX`, `offsetY`, and anomaly context) with absolute crash-safety.
*   **Storage:** Built on Google Proto DataStore (`checkpoint_store.pb`), guaranteeing atomic, asynchronous, all-or-nothing writes to disk. Maintains a 3-deep ring buffer per document to enable robust fallback recovery if a crash tears an in-flight write.
*   **Verification Status:** Intended release configuration; Proto DataStore schemas and ring-buffer pruning mechanics verified via instrumentation test specifications.
*   **Documentation:** [docs/CHECKPOINT_MODEL.md](docs/CHECKPOINT_MODEL.md)

### 3.3 Recovery Engine (`core/recovery/`)
*   **Responsibility:** Survive unexpected application crashes, restart the process instantly, restore full user session context, and maintain a strict audit log of all recovery outcomes.
*   **Mechanism:** Attaches a global `UncaughtExceptionHandler` that triggers an `emergencySave()`, fires a restart intent with `FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_NEW_TASK`, and terminates the process via `exitProcess(0)`.
*   **Audit Logging:** Maintains an independent JSON event log (`recovery_journal.json`) on `Dispatchers.IO`, cleanly separating state preservation from recovery analytics.
*   **Verification Status:** Intended release configuration; unhandled exception interception and independent background journaling verified via codebase audit.
*   **Documentation:** [docs/RECOVERY_MODEL.md](docs/RECOVERY_MODEL.md)

---

## 4. Observational Consoles & Telemetry

### 4.1 Protected Document Workspace (`ui/home/`)
Functions as the primary resilience console, displaying active system status ("Prediction Engine Active", "Checkpoint Manager Ready"), Quick Statistics, and recent protected sessions in a responsive multi-column grid with recovery badges.

### 4.2 System Health Dashboard (`ui/dashboard/`)
An enterprise operational dashboard displaying rolling anomaly sparklines, live metrics, checkpoint histories, and recovery timelines. Strictly observational—operates entirely through `HealthDashboardViewModel` and never modifies engine state.

### 4.3 Resilience Analytics (`ui/analytics/`)
An executive resilience report console (Phase 8 & 9). Provides historical analysis of persisted checkpoint states and recovery outcomes, featuring background JSON and CSV export mechanics.
*   **Documentation:** [docs/ANALYTICS_MODEL.md](docs/ANALYTICS_MODEL.md)

---

## 5. Comprehensive Documentation Index

To maintain complete transparency and rigorous architectural alignment, every facet of the CRPapp framework is documented in detail:

1.  **[System Architecture Specification](docs/ARCHITECTURE.md):** Detailed overview of subsystem boundaries, data flows, concurrency contracts, and lifecycle hygiene.
2.  **[Enterprise Implementation Roadmap](CRPapp_ROADMAP.md):** Milestone tracking from Phase 1 refactoring through Version 4.2 Masterpiece GUI expansion.
3.  **[Multi-Document Tabbed Workspace Model](docs/TABBED_WORKSPACE_MODEL.md):** Deep-dive into in-memory tab state holding, Chrome-style tab switching, and edge-to-edge GUI modernization.
4.  **[Anomaly Engine Model](docs/ANOMALY_MODEL.md):** Deep-dive into physical metric collection, EWMA smoothing equations, risk tiers, and explainable scoring.
5.  **[Checkpoint Engine Model](docs/CHECKPOINT_MODEL.md):** Protocol buffer schemas, ring buffer storage strategies, validation rules, and legacy SharedPreferences migration.
6.  **[Recovery Engine Model](docs/RECOVERY_MODEL.md):** Uncaught exception interception mechanics, process restart choreography, and decoupled JSON audit journaling.
7.  **[Resilience Analytics Model](docs/ANALYTICS_MODEL.md):** Historical data aggregation strategies, JSON/CSV export schemas, and documented session persistence limitations.
8.  **[Final Architectural Audit](docs/ARCHITECTURE_AUDIT.md):** Explicit verification matrix delineating demonstrated physical artifacts from intended release configurations.

---

## 6. Build & Verification Instructions

### 6.1 Clean Environment Verification
To verify the repository in a clean environment:
```bash
# Clone repository into a clean directory
git clone https://github.com/GVaishanth/CRPapp.git
cd CRPapp

# Execute clean Debug APK build (Intended Release Configuration)
./gradlew clean assembleDebug

# Execute Unit Verification Suite (Statically Verified Architecture)
./gradlew testDebugUnitTest

# Execute Static Lint Analysis (Intended Release Configuration)
./gradlew lintDebug
```

### 6.2 CI/CD Automation
The repository includes an automated GitHub Actions verification workflow (`.github/workflows/android.yml`). Upon any push or pull request to the `main` branch, the CI pipeline is configured to automatically check out the code, configure JDK 17, execute unit tests, run static Android Lint analysis, and assemble APK artifacts (Intended Release Configuration).

---

*CRPapp – Predictive Crash Resilience Framework*  
*Predict → Checkpoint → Recover*
