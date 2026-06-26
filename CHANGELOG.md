# CRPapp – Full Enterprise Changelog & Milestone Release Notes

**Current Flagship Release:** Version 2.0-phase10 (Release Edition)  
**Date:** 2026-06-26

---

## [2.0-phase10] – 2026-06-26 (Flagship Release Preparation)

### Release Engineering & Repository Polish
*   **Repository Audit:** Executed a comprehensive repository cleanup pass. Purged all dead code, unused resources, and obsolete developer comments.
*   **Testing Suite Expansion:** Authored `ResilienceUnitTests.kt` and `ResilienceInstrumentationTests.kt`, establishing robust unit and instrumentation verification across `AnomalyModel`, `CheckpointManager`, and `RecoveryJournal`.
*   **Build & ProGuard Verification:** Configured strict R8/ProGuard minification rules (`proguard-rules.pro`) protecting Protocol Buffers, DataStore serializers, PDFView classes, and Coroutines from obfuscation errors.
*   **GitHub Readiness:** Created GitHub Actions verification workflows (`.github/workflows/android.yml`), added Apache 2.0 `LICENSE`, authored `DEMO_SCRIPT.md` and `CONTRIBUTING.md`, and produced the final `docs/ARCHITECTURE_AUDIT.md`.

---

## [2.0-phase9] – 2026-06-26 (Quality & Accessibility Refinement)

### Accessibility & UI Parity
*   **TalkBack Compatibility:** Updated all XML layout files to apply `android:importantForAccessibility="no"` to decorative shield icons, ovals, and bullet points, while adding localized content descriptions to meaningful interactive views.
*   **Touch Targets:** Enforced minimum 48dp touch targets (`android:minHeight="48dp"`, `android:focusable="true"`) across `item_recent_session.xml` and `view_resilience_hud.xml`.
*   **Reduced Motion Support:** Updated `ProtectedViewerActivity.kt` to inspect `ValueAnimator.areAnimatorsEnabled()`, instantly setting view states and bypassing scale pulses when reduced motion preferences are active.
*   **Architectural Refinement:** Re-architected `ResilienceAnalyticsViewModel` to rely entirely on persisted historical records (`Checkpoint` history and `RecoveryJournal`), removing live monitor dependencies and clearly documenting session persistence windowing in `docs/ANALYTICS_MODEL.md`.

---

## [2.0-phase8] – 2026-06-26 (Resilience Analytics – Final Feature Phase)

### Executive Telemetry Console
*   **Read-Only Analytics Layer:** Implemented `ResilienceAnalyticsActivity.kt` and `ResilienceAnalyticsViewModel.kt` as an executive report console operating on `Dispatchers.IO`.
*   **Visual Aggregations:** Features Material 3 cards displaying Framework Overview statistics, Prediction Analytics (Risk Tiers & Metric Contributions), Protection Analytics (Checkpoints per Session & Trigger Distribution), and Recovery Analytics (Success Rates & Fallback Ratios).
*   **Data Export:** Introduced background export mechanics for structured JSON (`resilience_analytics_export.json`) and CSV (`resilience_analytics_export.csv`) files in `cacheDir`.
*   **Documentation:** Created `docs/ANALYTICS_MODEL.md`.

---

## [2.0-phase7.1] – 2026-06-26 (Audit Remediation & Stabilization)

### Build Restoration & Crash Fixes
*   **Compile Error Resolutions:** Purged invalid method invocations (`RecoveryManager.notifyCheckpointSaved` in `ProtectedViewerActivity.kt` and `getHistorySnapshot` in `HealthDashboardActivity.kt`).
*   **Runtime Crash Prevention:** Fixed a fatal `Resources.NotFoundException` caused by a nested `ContextCompat.getColor` call in `RecoverySummaryBottomSheet.kt` and registered `HealthDashboardActivity` in `AndroidManifest.xml`.
*   **Dashboard Wiring:** Rewrote `HealthDashboardActivity` to strictly observe `HealthDashboardViewModel` and bound dynamic checkpoint/recovery timelines.
*   **Concurrency & Performance:** Shifted `RecoveryJournal` file I/O entirely to `Dispatchers.IO` with an in-memory cache, wrapped `onPause` checkpoint saves in `NonCancellable` coroutine contexts, and eliminated redundant Mutex locking in `CheckpointManager`.
*   **Codebase Cleanup:** Deleted legacy `MainActivity.kt` and `activity_main.xml` files and extracted hardcoded layout strings into `strings.xml`.

---

## [2.0-phase7] – 2026-06-26 (System Health Dashboard)

### Observational Console
*   **Implementation:** Authored `HealthDashboardActivity.kt` and `HealthDashboardViewModel.kt` to serve as a real-time operational console.
*   **Visual Components:** Developed custom Canvas-drawn sparklines (`AnomalySparklineView.kt`) to render rolling 60-second anomaly trends and checkpoint markers.
*   **Static Bridge:** Introduced `AnomalyMonitorHolder` for lightweight observation of live viewer metrics.

---

## [2.0-phase6] – 2026-06-26 (Recovery Experience)

### Post-Crash UX & Journaling
*   **Bottom Sheet Summary:** Built `RecoverySummaryBottomSheet.kt` to present a non-blocking recovery confidence checklist and timeline 350ms after successful post-crash PDF restoration.
*   **Recovery Journal:** Established `RecoveryJournal.kt` to persist an independent JSON event log (`recovery_journal.json`), cleanly separating recovery outcomes from checkpoint histories.
*   **Classification:** Introduced `NORMAL` vs. `FALLBACK` recovery status chips.

---

## [2.0-phase5] – 2026-06-26 (Checkpoint Engine Upgrade)

### Rich Atomic State Preservation
*   **Proto DataStore:** Migrated storage to Google Proto DataStore (`checkpoint_store.pb`) via Protocol Buffers (`checkpoint.proto`).
*   **Rich Data Model:** Upgraded `Checkpoint.kt` to persist rich spatial state (`page`, `zoom`, `offsetX`, `offsetY`) alongside anomaly metadata.
*   **Ring Buffer:** Established a 3-deep ring buffer per document with robust fallback restore mechanics if the primary checkpoint fails validation.
*   **Legacy Migration:** Built `CheckpointMigrator.kt` for lossless, idempotent migration of legacy SharedPreferences data.

---

## [2.0-phase4] – 2026-06-26 (Prediction Engine v2)

### Real Physical Metric Sensing
*   **Metrics Collector:** Implemented `MetricsCollector.kt` to evaluate JVM Heap/PSS, CPU saturation via `/proc/self/stat`, UI render jank via `Choreographer`, and scroll velocity. Discarded all legacy `Random.nextDouble()` placeholders.
*   **EWMA Filtering:** Applied Exponentially Weighted Moving Average smoothing ($\alpha = 0.35$) to eliminate false-positive spike triggers.
*   **Risk Tiers:** Established explainable scoring models clamped to `0.0–1.0` across `LOW` (<0.4), `ELEVATED` (0.4–0.7), and `HIGH` (≥0.7) risk brackets.

---

## [2.0-phase3] – 2026-06-26 (Protected Viewer UI)

### Document Interface Redesign
*   **Resilience HUD:** Created `view_resilience_hud.xml` to surface live anomaly scores, progress indicators, and dynamic risk badges in a collapsible top card.
*   **Protected Scroller:** Developed `ProtectedScrollerView.kt` with a robust 48dp touch target and floating page preview bubbles, resolving the legacy thumb height calculation bug.

---

## [2.0-phase2] – 2026-06-26 (Protected Document Workspace)

### Framework Identity Transition
*   **Home Console:** Established `HomeActivity.kt` as the primary launcher with Material 3 design, System Status operational cards, Quick Statistics grids, and SAF document picking.
*   **Recent Sessions List:** Integrated `RecentSessionsAdapter.kt` to display recent protected documents alongside recovery count badges.

---

## [1.0-phase1] – 2026-06-26 (Architecture Refactor)

### Decoupling Monolith
*   **Manager Extraction:** Decoupled monolithic `MainActivity.kt` logic into focused, single-responsibility components (`AnomalyMonitor`, `CheckpointManager`, `RecoveryManager`, `PdfSessionController`).
*   **Global Crash Handling:** Created `CrashResilientApp.kt` custom application class to install global exception handling before activity initialization.
