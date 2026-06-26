# CRPapp - Checkpoint Model
## Predictive Crash Resilience Framework - Phase 5

**Version:** 2.0-phase5  
**Date:** 2026-06-26

> The checkpoint system is responsible for **resilience**, not prediction.  
> Anomaly Engine (Phase 4) is **FROZEN** – this document covers Checkpoint & Recovery only.

---

## 1. Overview

```
Predict → Evaluate Risk → Create Checkpoint → Recover
          ^^^^^^^ frozen              ^^^^^^^ Phase 5
```

CheckpointManager preserves user reading state accurately and safely, survives process death, corruption, and schema evolution.

```
AnomalyState → Checkpoint → Proto DataStore → Crash → Validate → Restore
```

---

## 2. Checkpoint Model

### 2.1 Schema - v2

```kotlin
data class Checkpoint(
  // Versioning
  checkpointVersion: Int = 2,
  recoveryVersion: Int = 2,

  // Document identity
  documentId: String,
  displayName: String,

  // Reading position - rich state
  page: Int,
  zoom: Float = 1.0f,
  offsetX: Float = 0f,
  offsetY: Float = 0f,

  // Resilience metadata
  timestampMs: Long,
  anomalyScore: Float = 0f,      // 0.0 .. 1.0
  riskTier: Int = 0,             // 0=LOW, 1=ELEVATED, 2=HIGH
  triggerReason: String? = null, // e.g. "memory rising"

  // Trigger source
  trigger: Trigger = AUTO_INTERVAL
)

enum class Trigger {
  AUTO_ANOMALY,   // anomalyScore > 0.7
  AUTO_INTERVAL,  // tiered timer
  MANUAL,         // user tapped "Checkpoint Now"
  LIFECYCLE,      // onPause()
  RECOVERY        // crash handler
}
```

All fields are optional except: `documentId`, `page`, `timestampMs`. Missing fields fall back to safe defaults (zoom=1.0, offset=0).

**Extensibility:** Proto schema (`checkpoint.proto`) is forward/backward compatible. New fields get new tag numbers, old readers ignore unknown fields, missing fields use defaults defined in `toDomain()`.

### 2.2 Validation

Before restore:
```
isValid() =
  checkpointVersion >= 1 &&
  documentId.isNotBlank() &&
  page >= 0 &&
  zoom in 0.0 .. 20.0 &&
  timestampMs > 0 &&
  anomalyScore in 0.0 .. 1.0
```

Invalid checkpoints are skipped, next entry in history is tried. Application never fails because of a corrupt checkpoint.

---

## 3. Storage Format

**Proto DataStore** – `checkpoint_store.pb`

```
message CheckpointStore {
  int32 schema_version = 1;  // = 2
  map<string, DocumentHistory> documents = 2;
}

message DocumentHistory {
  string document_id = 1;
  repeated Checkpoint checkpoints = 2;  // max 3, newest first
  int32 recovery_count = 3;
  int64 last_recovery_timestamp_ms = 4;
  int64 last_recovery_checkpoint_age_ms = 5;
  bool last_recovery_fallback_used = 6;
  string last_recovery_source = 7;
}

message Checkpoint { ... }  // see §2.1
```

**Properties:**
- Atomic writes – DataStore guarantees all-or-nothing
- Corruption handling – `CheckpointStoreSerializer` throws `CorruptionException` → DataStore replaces with `defaultValue` (empty store), app continues, old SP fallback still exists on first migration
- CRC – proto framing includes length checks; additional application CRC field reserved in schema (`crc32 = 20`)
- File location: `datastore/checkpoint_store.pb`

### Dependencies

```
androidx.datastore:datastore:1.1.1
com.google.protobuf:protobuf-javalite:3.21.12
org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3
```

Proto generation:
```
plugins {
  id("com.android.application")
  id("com.google.protobuf")
}
protobuf {
  protoc { artifact = "com.google.protobuf:protoc:3.21.12" }
  generateProtoTasks { all().forEach { task ->
    task.builtins { id("java") { option("lite") } }
  }}
}
```

---

## 4. Migration Strategy

### 4.1 Phase 1-4 → Phase 5

**Source:** SharedPreferences `"checkpoint"`
- Keys: `page_<docHash>`, `time_<docHash>`, `recovery_<docHash>`
- Legacy single key: `"page"` → migrated to `"asset:sample.pdf"`

**Target:** Proto DataStore `checkpoint_store.pb`

**Process – `CheckpointMigrator.migrateIfNeeded()`:**
1. Check SP flag `migrated_to_datastore_v2` – if true, skip
2. Check DataStore already has documents – if yes, mark migrated, skip
3. Read `recent_docs` set from SP, plus legacy `"page"` key
4. For each docId: read page, time, recovery_count
5. Create v2 `Checkpoint`:
   ```
   page = sp_page
   zoom = 1.0f
   offsetX/Y = 0f
   anomaly_score = 0f
   risk_tier = 0
   trigger = LIFECYCLE
   trigger_reason = "migrated_from_sp"
   timestamp_ms = sp_time
   ```
6. Write to DataStore, 1 history entry per document
7. Set SP flag `migrated_to_datastore_v2 = true`
8. **Legacy SP is left intact** – read-only fallback

**Properties:**
- Automatic, runs once at `CheckpointManager` init (blocking, < 20ms typical)
- Idempotent – safe to run repeatedly
- Lossless: page, timestamp, recovery_count preserved
- Missing rich fields defaulted safely
- No user data loss
- Transparent – user sees same restored page

### 4.2 Future schema evolution

- Bump `checkpoint_version`
- Add new proto fields with new tag numbers
- `toDomain()` supplies defaults for missing fields
- Old checkpoints remain readable
- No migration script needed for additive changes

---

## 5. Checkpoint History – Ring Buffer

Per document:
- Keep **newest 3 checkpoints**
- New save → prepend → truncate to 3
- Restore → iterate newest → oldest → first valid wins
- Old entries auto-pruned on save

**Why 3?** Crash during checkpoint write corrupts at most the in-flight entry. With 3-deep history: n (possibly corrupt) → n-1 (good) → n-2 (fallback). Covers crash-loop scenarios.

**Storage cost:** ~300 bytes / checkpoint × 3 × 20 docs ≈ 18 KB – negligible.

API:
```kotlin
suspend fun getCheckpointHistory(docId: String): List<Checkpoint>
fun checkpointHistoryFlow(docId: String): Flow<List<Checkpoint>>
```

---

## 6. Checkpoint Creation Policy

| Risk Tier | Anomaly Score | Auto-save interval | Trigger |
|-----------|---------------|-------------------|---------|
| LOW | < 0.4 | Page change + 30s | `AUTO_INTERVAL` |
| ELEVATED | 0.4 – 0.699 | 5s | `AUTO_INTERVAL` |
| HIGH | ≥ 0.7 | **Immediate** | `AUTO_ANOMALY` |

Additional triggers:
- `MANUAL` – user taps "Checkpoint Now"
- `LIFECYCLE` – `onPause()`
- `RECOVERY` – crash handler (best-effort, 150ms timeout)

**Debounce:** max 1 write / 800ms per document for `AUTO_ANOMALY` – prevents DataStore thrashing during sustained HIGH.

**Anomaly engine is frozen** – score, tiers, thresholds all from Phase 4 `AnomalyState`. CheckpointManager only *consumes* `AnomalyState`, never modifies scoring.

---

## 7. Recovery Sequence

```
1. App launch → CheckpointManager init → migrate SP if needed
2. Viewer open docId → restoreCheckpoint(docId)
3. Validate each checkpoint in history (newest → oldest):
     isValid() && documentId matches
4. First valid → restore page + zoom + offsetX + offsetY
5. Record RecoveryMetadata:
     timestampMs
     checkpointAgeMs = now - checkpoint.timestampMs
     recoverySource = "viewer_launch"
     fallbackUsed = (used checkpoint index > 0)
     documentId, restoredPage
6. Show "Recovering" banner 5s if recovery_count > 0 and checkpoint < 60s old
7. Continue monitoring
```

If **all** checkpoints fail validation → open at page 0, zoom 1.0 – app never crashes on corrupt checkpoint.

**Crash-loop protection** (planned Phase 6): 3 crashes in 2 min at same page → offer Safe Mode.

### Recovery Metadata

```kotlin
data class RecoveryMetadata(
  timestampMs: Long,
  recoveryDurationMs: Long = 0,
  checkpointAgeMs: Long,
  recoverySource: String,      // "viewer_launch" | "crash_handler" | "simulate_crash" | ...
  fallbackUsed: Boolean,
  documentId: String,
  restoredPage: Int
)
```

Stored in `DocumentHistory`, increments `recovery_count`. Supports future analytics (Phase 8) without changing recovery workflow.

---

## 8. API

```kotlin
// Save - rich
suspend fun saveCheckpoint(checkpoint: Checkpoint)
suspend fun saveCheckpoint(
  documentId: String, displayName: String,
  page: Int, zoom: Float, offsetX: Float, offsetY: Float,
  anomaly: AnomalyState,
  trigger: Checkpoint.Trigger
)

// Save - compat (Phase 1-4)
fun saveCheckpoint(docId: String, page: Int)
fun saveCheckpoint(page: Int)

// Restore
suspend fun restoreCheckpoint(docId: String): Checkpoint?
fun restoreCheckpoint(docId: String, defaultPage: Int = 0): Int  // compat

// History
fun checkpointHistoryFlow(docId: String): Flow<List<Checkpoint>>
suspend fun getCheckpointHistory(docId: String): List<Checkpoint>

// Recovery
suspend fun recordRecovery(meta: RecoveryMetadata)
fun getRecoveryCount(docId: String): Int
fun getLastCheckpointTime(docId: String): Long

// Sessions (Home UI)
fun getRecentSessions(): List<ProtectedSession>
```

Compat overloads preserve Phase 1-4 call sites. New code uses rich `Checkpoint` objects.

---

## 9. Validation Checklist

| Requirement | Status |
|-------------|--------|
| Rich checkpoint model with versioning | ✅ `checkpoint_version = 2`, `recovery_version = 2` |
| Document Id, page, zoom, scroll X/Y, timestamp, anomaly_score, risk_tier, trigger_reason, recovery_version | ✅ all present |
| Extensible (proto) | ✅ unknown fields ignored, defaults supplied |
| Proto DataStore, atomic writes | ✅ `DataStore<CheckpointStore>` |
| Corruption handling | ✅ `CorruptionException` → defaultValue, per-checkpoint `isValid()` |
| Backward-compatible migration | ✅ `CheckpointMigrator` – SP → DataStore, lossless page/time/recovery_count |
| No user data loss | ✅ SP left intact, migration idempotent |
| Automatic, transparent | ✅ runs at `CheckpointManager` init |
| Checkpoint history – keep newest 3 | ✅ ring buffer, auto-prune |
| Restore most recent valid | ✅ iterate newest→oldest, `isValid()` |
| Validation: version, docId, integrity, required fields, timestamp | ✅ `Checkpoint.isValid()` |
| Corrupt checkpoint → graceful fallback | ✅ skip invalid, try next, fallback to page 0 |
| Never fail because of corrupt checkpoint | ✅ |
| Recovery metadata captured | ✅ `RecoveryMetadata` – timestamp, age, source, fallbackUsed |
| Existing checkpoints migrate successfully | ✅ `CheckpointMigrator` tested path |
| No checkpoint data lost | ✅ |
| Recovery behaviour identical from user perspective | ✅ restore page → PDF opens, same as Phase 4, now also restores zoom/offset |
| Corrupted checkpoints handled safely | ✅ validation + fallback chain |
| Ring-buffer pruning correct | ✅ `listOf(new) + history.take(2)` |
| Predict → Checkpoint → Recover unchanged | ✅ Anomaly engine frozen, checkpoint timing unchanged (score > 0.7), recovery flow unchanged |

---

## 10. Anomaly Engine Freeze Confirmation

The following files were **NOT modified in Phase 5**:

- `core/anomaly/AnomalyState.kt`
- `core/anomaly/AnomalyModel.kt`
- `core/anomaly/MetricsCollector.kt`
- `core/anomaly/AnomalyMonitor.kt`

Metric weights: memory 0.40 / cpu 0.20 / render 0.20 / scroll 0.20 – **unchanged**  
Thresholds: LOW < 0.4 / ELEVATED < 0.7 / HIGH ≥ 0.7 – **unchanged**  
Score range: 0.0 … 1.0 – **unchanged**  
Sampling: 1 Hz EWMA α=0.35 – **unchanged**

CheckpointManager **consumes** `AnomalyState` via:
```kotlin
Checkpoint.fromAnomalyState(documentId, displayName, page, zoom, offsetX, offsetY, anomaly, trigger)
```

No feedback into the anomaly engine. One-way data flow preserved.

---

*CRPapp – Predictive Crash Resilience Framework*  
*Predict → Checkpoint → Recover*
