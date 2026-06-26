# CRPapp – Official Presentation & Demonstration Script

**Version:** 2.0-phase10 (Release Edition)  
**Target Audience:** GitHub Showcase, Research Demonstrations, Technical Interviews, and Academic Evaluators.

---

## 1. Executive Setup & Verification

Before initiating the demonstration, verify that the application has successfully compiled and launched onto the target device or emulator. CRPapp launches directly into the **Protected Document Workspace** (`HomeActivity`).

```
┌────────────────────────────────────────────────────────┐
│             DEMONSTRATION STAGE SEQUENCING             │
├────────────────────────────────────────────────────────┤
│ STAGE 1: The Protected Workspace (Home Console)        │
│ STAGE 2: The Protected Viewer (Live Intelligence HUD)  │
│ STAGE 3: The Uncaught Crash & Recovery Interception    │
│ STAGE 4: Observational Analytics & Telemetry Export    │
└────────────────────────────────────────────────────────┘
```

---

## 2. Stage-by-Stage Script

### STAGE 1: The Protected Document Workspace (Home Console)

**[Visual Cue: Open App → Show Home Screen]**

*   **Presenter Narrative:**  
    *"Welcome to the CRPapp Predictive Crash Resilience Framework. What you are seeing is not a standard document reader, but an intelligent resilience workspace designed for document-heavy Android applications.*  
    *Notice the System Status card immediately confirming that our three core subsystems—the Prediction Engine, Checkpoint Manager, and Recovery System—are active and operational.*  
    *On the bottom, our Recent Protected Sessions list leverages Google Proto DataStore to surface previously protected documents alongside exact page positions and historical recovery badges."*

*   **Action:** Click the **"Open sample.pdf"** text button or select a document via the SAF picker.

---

### STAGE 2: The Protected Viewer & Resilience HUD

**[Visual Cue: Enter Protected Viewer → Show PDF Render & Scroller]**

*   **Presenter Narrative:**  
    *"We are now in the Protected Viewer. On the right is our custom `ProtectedScrollerView`, featuring an accessible 48dp minimum touch target and real-time floating page preview bubbles as we scroll.*  
    *But the true power of CRPapp lies above the document in our Collapsible Resilience HUD. Let's tap the summary row to expand it."*

**[Visual Cue: Tap HUD Summary Row → Chevron Rotates 180° → Details Expand]**

*   **Presenter Narrative:**  
    *"Here you can see our frozen Prediction Engine sampling physical system health at 1 Hz. Notice the Live Metrics Breakdown: we are capturing real-time JVM Heap and PSS memory footprints, CPU saturation via `/proc/self/stat`, UI render jank via `Choreographer`, and user scroll velocity.*  
    *The engine applies Exponentially Weighted Moving Average (EWMA) smoothing ($\alpha = 0.35$) to eliminate false-positive spike triggers. As long as our score remains below 0.4, our status displays a reassuring green 'Protected' shield."*

---

### STAGE 3: Uncaught Crash Simulation & Automated Recovery

**[Visual Cue: Tap Top App Bar Menu → Select 'Simulate Crash']**

*   **Presenter Narrative:**  
    *"In the real world, complex PDF decoding can trigger sudden Out-Of-Memory errors or unhandled native exceptions. Let's simulate a fatal, unpredicted process crash right now.*  
    *When I select 'Simulate Crash', our global `UncaughtExceptionHandler` instantly intercepts the dying process, fires a non-blocking `emergencySave()` to our Proto DataStore, executes an immediate application restart intent, and terminates the VM."*

**[Visual Cue: Screen Flashes Briefly → App Restarts → Resumes Exact PDF Page & Zoom → Recovery Summary Bottom Sheet Slides Up]**

*   **Presenter Narrative:**  
    *"Notice the speed of restoration. The application restarted instantly, read our rich Checkpoint v2 data, and restored the exact page, zoom level, and pan offset before you even realized the crash occurred.*  
    *Now, 350ms after the document has completely rendered, our non-blocking Recovery Summary Bottom Sheet slides up. It presents a factual Recovery Confidence checklist confirming document, page, zoom, and scroll restoration, alongside an exact chronological timeline of the crash interception."*

---

### STAGE 4: Observational Consoles & Telemetry Export

**[Visual Cue: Dismiss Bottom Sheet → Tap Menu → Select 'System Health Dashboard']**

*   **Presenter Narrative:**  
    *"To inspect the real-time operational state of our framework, we open the System Health Dashboard. This console is completely read-only, observing our frozen engines via `HealthDashboardViewModel`. Notice the smooth custom Canvas sparklines mapping our rolling 60-second anomaly trends, alongside historical checkpoint logs and recovery readiness statistics."*

**[Visual Cue: Exit Dashboard → Return to Home or Viewer Menu → Select 'Resilience Analytics']**

*   **Presenter Narrative:**  
    *"Finally, let's open Resilience Analytics. Designed as an executive report, this screen aggregates our persisted historical Checkpoint and Recovery Journal records. It showcases risk tier distribution progress bars, manual vs. automatic save ratios, and average recovery durations.*  
    *For enterprise audit and academic evaluation, we can tap the top app bar menu to instantly export this entire dataset into structured JSON or CSV files in the background."*

**[Visual Cue: Tap Menu → Select 'Export JSON' → Toast Confirms File Path]**

*   **Presenter Narrative:**  
    *"Thank you for experiencing CRPapp—where predictive intelligence meets fail-safe crash resilience."*

---

*CRPapp – Predictive Crash Resilience Framework*  
*Predict → Checkpoint → Recover*
