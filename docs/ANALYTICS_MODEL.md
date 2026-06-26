# CRPapp – Resilience Analytics Model & Telemetry Architecture

**Version:** 2.0-phase9  
**Date:** 2026-06-26  
**Status:** Complete & Refined (Phase 9)

---

## 1. Executive Summary & Philosophy

The Resilience Analytics layer serves as the **final feature phase (Phase 8)** of the CRPapp framework, refined in **Phase 9** for release engineering. Its primary objective is to aggregate, transform, and visualize recorded resilience telemetry to provide clear executive insights for demonstrations, evaluations, and enterprise research.

```
┌────────────────────────────────────────────────────────┐
│             RESILIENCE ANALYTICS CONSOLE               │
└──────────────────▲──────────────────────────────▲──────┘
                   │ (DataStore Proto)            │ (JSON Journal)
            ┌──────┴───────┐              ┌───────┴────────┐
            │  Checkpoint  │              │ RecoveryRecord │
            │   History    │              │    History     │
            └──────────────┘              └────────────────┘
```

### 1.1 Architectural Freeze & Read-Only Constraint
In strict adherence to the architectural freeze established after Phase 5, the analytics layer operates as a **completely read-only observation console**. 
*   **Zero Feedback Loops:** No analytics component may influence prediction scoring, alter checkpoint creation intervals, or modify recovery exception handling.
*   **Zero Synthetic Fabrication:** Every displayed statistic and exported record originates strictly from empirical, recorded system data. Inferred or artificial values are strictly prohibited.

### 1.2 Phase 9 Architectural Refinement: Historical Focus
To ensure robust, long-term analytical integrity, the Analytics module is strictly focused on **historical resilience data** derived entirely from persisted records (`Checkpoint` history and `RecoveryJournal`). 
*   **Separation of Concerns:** The live operational view belongs exclusively to the System Health Dashboard (`HealthDashboardActivity`).
*   **Documented Windowing Limitation:** Historical anomaly data between checkpoints is **not persisted** beyond the active reading session. Rather than implying long-term continuous history, long-term Prediction Analytics rely entirely on the rich anomaly metadata (`anomalyScore`, `riskTier`, `triggerReason`) preserved within atomic `Checkpoint` storage records.

---

## 2. Data Sources

The analytics engine securely consumes data from two isolated, persisted resilience stores:

1.  **Checkpoint History (`core/checkpoint/`):** Polled asynchronously from Google Proto DataStore (`checkpoint_store.pb`). Captures persistent spatial reading state (`page`, `zoom`, `offsetX`, `offsetY`), trigger context (`AUTO_INTERVAL`, `AUTO_ANOMALY`, `MANUAL`), and historical predictive context (`anomalyScore`, `riskTier`, `triggerReason`).
2.  **Recovery Journal (`core/recovery/`):** Queried from `recovery_journal.json`. Provides an independent audit log of recovery outcomes (`NORMAL` vs `FALLBACK`), exact recovery durations, and validation results.

---

## 3. Calculated Metrics & Sections

The analytics console organizes resilience insights into four distinct executive sections:

### 3.1 Framework Overview
*   **Protected Sessions:** Total active documents registered in the CheckpointManager store (`sessions.size`).
*   **Total Checkpoints:** Aggregate sum of all historical checkpoints preserved across all document histories.
*   **Total Recoveries:** Aggregate count of unhandled exception crashes successfully intercepted and restored (`recoveries.size`).
*   **Recovery Success Rate:** Ratio of successful restorations (`validationResult == "OK"`) against total recorded crashes, formatted as a percentage (`(successful / total * 100)%`).
*   **Current Framework Version:** Statically bound to the active enterprise milestone (`2.0-phase9`).

### 3.2 Prediction Analytics (Historical View)
*   **Average Anomaly Score:** Mathematical mean of historical anomaly values preserved across all recorded checkpoints (`sum(cp.anomalyScore) / total`).
*   **Risk Tier Distribution:** Empirical breakdown of system stress across the three standard risk brackets: `LOW` (< 0.4), `ELEVATED` (0.4 – 0.699), and `HIGH` (≥ 0.7), derived from checkpoint metadata.
*   **Metric Contribution Distribution:** Fixed static weights communicating the underlying explainable engine architecture (`Memory = 0.40`, `CPU = 0.20`, `Render = 0.20`, `Scroll = 0.20`).
*   **Rolling Anomaly Trends:** Textual synthesis summarizing persisted historical checkpoint stress trends and clearly stating the session persistence limitation.
*   **Trigger Reason Frequency:** Ranking of dominant score delta catalysts recorded at checkpoint creation (e.g., `memory rising`, `cpu rising`).

### 3.3 Protection Analytics
*   **Checkpoints Per Session:** Average frequency ratio (`totalCheckpoints / protectedSessions`).
*   **Trigger Distribution:** Empirical breakdown of checkpoint creation mechanisms (`AUTO_INTERVAL` vs `AUTO_ANOMALY` vs `MANUAL`).
*   **Average Checkpoint Age:** Mean elapsed time between checkpoint creation and active evaluation (`(now - cp.timestampMs) / total`).
*   **Manual vs Automatic Checkpoints:** Comparative percentage distribution highlighting user-initiated saves versus framework-driven protections.
*   **Checkpoint Timeline:** Chronological audit log of the 10 most recent state protections.

### 3.4 Recovery Analytics
*   **Recovery Success Rate:** Redundant display of the framework-wide restoration success percentage.
*   **Normal vs Fallback Recoveries:** Ratio highlighting standard restorations (`NORMAL`) against fallback executions (`FALLBACK`) triggered by corrupted primary checkpoints.
*   **Average Recovery Duration:** Mean time required to complete a full process cold start, DataStore read, and PDF render (`sum(recoveryDurationMs) / total`).
*   **Average Checkpoint Age at Recovery:** Mean age of checkpoints at the exact moment of crash interception.
*   **Recovery Timeline:** Chronological audit log of recent crash survival events.

---

## 4. Export Schema

To support academic evaluation and enterprise demonstrations, the analytics console provides background export mechanics for both JSON and CSV formats. Files are saved securely to `cacheDir` and exposed to the user via Toast notification paths.

### 4.1 JSON Export Schema (`resilience_analytics_export.json`)
```json
{
    "export_timestamp": 1719416000000,
    "framework_version": "2.0-phase9",
    "framework_overview": {
        "protected_sessions": 12,
        "total_checkpoints": 48,
        "total_recoveries": 5,
        "recovery_success_rate": "100%"
    },
    "prediction_analytics": {
        "avg_anomaly_score": "0.142",
        "risk_tier_distribution": { "LOW": 94, "ELEVATED": 5, "HIGH": 1 },
        "metric_contributions": { "memory": 40, "cpu": 20, "render": 20, "scroll": 20 },
        "rolling_trends": "Historical analysis of 48 persisted checkpoint states. Anomaly data between checkpoints is not persisted beyond active sessions.",
        "top_trigger_reasons": "• memory rising (100%)"
    },
    "protection_analytics": {
        "checkpoints_per_session": "4.0",
        "avg_checkpoint_age": "14.5s",
        "trigger_distribution": "AUTO_INTERVAL: 64% • AUTO_ANOMALY: 22% • MANUAL: 14%",
        "manual_vs_auto": "14% Manual / 86% Automatic",
        "checkpoint_timeline": [
            {
                "document_id": "asset:sample.pdf",
                "display_name": "sample.pdf",
                "page": 12,
                "trigger": "AUTO_INTERVAL",
                "timestamp_ms": 1719415980000
            }
        ]
    },
    "recovery_analytics": {
        "recovery_success_rate": "100%",
        "normal_vs_fallback": "100% Normal / 0% Fallback",
        "avg_recovery_duration": "824 ms",
        "avg_age_at_recovery": "3.2s",
        "recovery_timeline": [
            {
                "document_id": "asset:sample.pdf",
                "display_name": "sample.pdf",
                "restored_page": 12,
                "recovery_type": "NORMAL",
                "duration_ms": 824,
                "timestamp_ms": 1719415950000
            }
        ]
    }
}
```

### 4.2 CSV Export Schema (`resilience_analytics_export.csv`)
```csv
Section,Metric,Value
Overview,Protected Sessions,12
Overview,Total Checkpoints,48
Overview,Total Recoveries,5
Overview,Recovery Success Rate,100%
Overview,Framework Version,2.0-phase9
Prediction,Avg Anomaly Score,0.142
Prediction,Tier LOW %,94%
Prediction,Tier ELEVATED %,5%
Prediction,Tier HIGH %,1%
Prediction,Contrib Memory %,40%
Prediction,Contrib CPU %,20%
Prediction,Contrib Render %,20%
Prediction,Contrib Scroll %,20%
Protection,Checkpoints Per Session,4.0
Protection,Avg Checkpoint Age,14.5s
Protection,Manual vs Auto,14% Manual / 86% Automatic
Recovery,Normal vs Fallback,100% Normal / 0% Fallback
Recovery,Avg Recovery Duration,824 ms
Recovery,Avg Age At Recovery,3.2s

--- Checkpoint Timeline ---
TimestampMs,DisplayName,Page,Trigger
1719415980000,"sample.pdf",12,AUTO_INTERVAL

--- Recovery Timeline ---
TimestampMs,DisplayName,RestoredPage,RecoveryType,DurationMs
1719415950000,"sample.pdf",12,NORMAL,824
```

---

## 5. Aggregation Strategy & Concurrency

```
┌──────────────────────────────────────────────────────────────┐
│                ResilienceAnalyticsViewModel                  │
├──────────────────────────────────────────────────────────────┤
│  1. Launch on Dispatchers.IO (Zero Main-Thread Blocking)     │
│  2. Asynchronous Polling (Proto DataStore & JSON Journal)   │
│  3. Precompute Averages, Ratios, and String Formatting       │
│  4. Emit Immutable AnalyticsState via StateFlow to UI        │
└──────────────────────────────────────────────────────────────┘
```

To strictly enforce the performance boundaries established during Phase 7.1 remediation, the analytics engine is prohibited from executing expensive mathematical aggregations or disk I/O on the main UI thread.
*   **ViewModel Isolation:** `ResilienceAnalyticsViewModel` launches all data ingestion and aggregation coroutines exclusively on `Dispatchers.IO`.
*   **State Decoupling:** Precomputed strings, ratios, and timeline lists are packaged into an immutable `AnalyticsState` data class and emitted to `ResilienceAnalyticsActivity` via Kotlin `StateFlow`.
*   **Non-Interference:** Opening, browsing, or exporting analytics never interrupts or contends with active 1 Hz anomaly monitoring, atomic checkpoint creation, or recovery crash handling.

---

## 6. Known Limitations

1.  **Session Persistence Limitation:** Historical anomaly data between checkpoints is not persisted beyond the active reading session. Long-term Prediction Analytics rely entirely on the rich anomaly metadata preserved within atomic `Checkpoint` storage records.
2.  **Data Pruning:** To maintain lightweight storage overhead, Checkpoint history is bounded by a 3-deep ring buffer per document, and the Recovery Journal is capped at 50 FIFO entries. Consequently, aggregate sums reflect active persistent histories rather than lifetime unbounded events.
3.  **Static Engine Weights:** Metric contribution percentages reflect the static, explainable engine weights (`Memory = 0.40`, `CPU = 0.20`, `Render = 0.20`, `Scroll = 0.20`) established in Phase 4. Online machine learning adaptations are intentionally excluded to preserve explainability.
4.  **Local File Export:** Exported JSON and CSV files are persisted locally to `cacheDir`. Cloud synchronization or automated webhooks are out of scope for Phase 8.

---

*CRPapp – Predictive Crash Resilience Framework*  
*Predict → Checkpoint → Recover*
