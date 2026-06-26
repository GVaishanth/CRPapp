# Contributing to CRPapp (Predictive Crash Resilience Framework)

First, thank you for considering contributing to CRPapp! We welcome enterprise developers, academic researchers, and Android engineers to evaluate, audit, and contribute to this flagship resilience project.

---

## 1. Architectural Rules & The Immutable Workflow

The core purpose of CRPapp is **not** to become a standard PDF reader, but to demonstrate an intelligent, fail-safe resilience paradigm. The framework revolves entirely around one immutable, continuous lifecycle workflow:

$$\text{Predict} \longrightarrow \text{Checkpoint} \longrightarrow \text{Recover}$$

### 1.1 Strict Architectural Freeze
In accordance with our flagship Version 2.0 roadmap, the three core engines operate under a strict architectural freeze to guarantee absolute stability and prevent regression:
1.  **Prediction Engine (`core/anomaly/`):** Frozen after Phase 4.
2.  **Checkpoint Engine (`core/checkpoint/`):** Frozen after Phase 5.
3.  **Recovery Engine (`core/recovery/`):** Frozen after Phase 5.

**Rule:** Zero modifications may be made to these frozen engines unless a confirmed, verified architectural defect or runtime crash is discovered. Feature extensions within Version 2 are strictly prohibited.

### 1.2 Read-Only Telemetry Constraint
All observational consoles—such as the System Health Dashboard (`ui/dashboard/`) and Resilience Analytics (`ui/analytics/`)—must remain completely read-only. No telemetry component may influence prediction scoring, checkpoint creation intervals, or recovery crash handling.

---

## 2. Setting Up Your Local Environment

### Prerequisites
*   **Java Development Kit (JDK):** JDK 17+ is required to build the project.
*   **Android Studio / Gradle:** Gradle 8.0+ (or use the provided Gradle Wrapper `./gradlew`).
*   **Protocol Buffers:** The build system automatically pulls `protoc:3.21.12` to compile `checkpoint.proto`.

### Building & Testing
To build the project and execute the complete resilience verification suite locally:
```bash
# Clean and build Debug APK
./gradlew clean assembleDebug

# Run Unit Verification Tests
./gradlew testDebugUnitTest

# Run Static Analysis (Lint)
./gradlew lintDebug
```

---

## 3. Submitting Pull Requests & Issues

When submitting pull requests or opening issues for long-term maintenance, please adhere to the following guidelines:

### 3.1 Bug Fixes & Remediation
*   **Scope:** Keep fixes as small, localized, and specific as possible.
*   **Validation:** For every submitted fix, include an explanation of why the issue existed, a description of the change made, a justification of why it preserves existing behavior, and confirmation that the `Predict → Checkpoint → Recover` workflow passes regression checks.

### 3.2 Code Quality & Concurrency Standards
*   **Disk I/O:** All file reads and writes (e.g., in `RecoveryJournal`) must run strictly within `Dispatchers.IO` coroutines. Zero main-thread file I/O is permitted.
*   **Lifecycle Safety:** Lifecycle checkpoint persistence (e.g., in `onPause`) must be wrapped in `withContext(NonCancellable)` to ensure atomic completion during rapid activity teardown.
*   **Accessibility:** All new layout modifications must adhere to WCAG AAA contrast standards, provide TalkBack content descriptions for interactive items, apply `android:importantForAccessibility="no"` to decorative items, and maintain minimum 48dp touch targets.

---

## 4. Code of Conduct

We are committed to providing a welcoming, professional, and respectful environment for all contributors. Please maintain clear, constructive communication across all pull request reviews and issue discussions.

*CRPapp – Predictive Crash Resilience Framework*  
*Predict → Checkpoint → Recover*
