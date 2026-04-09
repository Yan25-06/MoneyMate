# MoneyMate Concurrency and Sync Audit - Round 3 (System-Level Side Effects)

Date: 2026-04-02
Scope: Android lifecycle, memory/CPU pressure, observer invalidation, SQLite contention, timezone consistency.
Related:
- `docs/audits/concurrency-sync-audit-round1.md`
- `docs/audits/concurrency-sync-audit-round2.md`
- `docs/audits/concurrency-sync-audit-action-plan.md`
- `docs/audits/concurrency-sync-impact-assessment.md`

## Executive Summary

Round 3 confirms the current architecture still has **high system-level risk** under concurrent heavy load (UI + AI + Sync), even after Round 1/2 findings.

Main systemic risks:
- Memory/GC churn due to non-paged reads and heavy observer fan-out.
- UI thrashing from frequent LiveData invalidation and repeated full-model rebuild.
- Background task durability gaps (Executor-only write tasks are not resilient to OS process death).
- SQLite lock contention risk from expensive aggregate queries and missing hot-path composite indexes.
- Time grouping drift risk because analytics grouping uses local timezone transformations.

---

## 1) System Vulnerabilities

## P0 - UI Thrashing + Memory Churn under high update frequency

### VUL-P0-01: Observer fan-out with repeated full recomputation in budget screen

- File: `app/src/main/java/com/group10/moneymate/ui/budget/BudgetViewModel.java`
- Functions: `onBudgetsChanged`, `syncChildSources`, `rebuildUiModels`
- Evidence:
  - `observeForever` root subscriptions: lines 88-90
  - Per-budget dynamic `observeForever` for category/wallet/spent: lines 323, 335, 351
  - Each child observer immediately triggers `rebuildUiModels()`: lines 319, 331, 347

Risk:
- With many budgets and frequent DB invalidation (sync/AI batch writes), UI receives cascaded recompute storms.
- Large object churn (`new ArrayList<>`, repeated mapping) increases GC frequency and frame drops.

Impact:
- Jank in budget list/details and unstable scrolling performance.

### VUL-P0-02: Non-paged reads returning full lists for hot domains

- File: `app/src/main/java/com/group10/moneymate/data/local/dao/TransactionDao.java`
- Functions: `getAllTransactions`, `getTransactionsByDateRange`, `getTransactionsByType`, other list methods returning `LiveData<List<TransactionEntity>>`

Risk:
- Room emits full list snapshots; if data set is large, each invalidation allocates large lists repeatedly.
- Combined with multiple observers, this can create high GC churn and transient memory spikes.

Impact:
- OOM risk increases on low-RAM devices; perceived lag increases on data-heavy users.

---

## P1 - OS process death durability gaps

### VUL-P1-01: Critical background writes rely on in-process executor only

- File: `app/src/main/java/com/group10/moneymate/data/local/AppDatabase.java`
- Evidence: global `databaseWriteExecutor = Executors.newFixedThreadPool(4)` at line 46
- Files using executor for critical writes:
  - `app/src/main/java/com/group10/moneymate/data/repository/TransactionRepository.java` (insert/update/softDelete)
  - `app/src/main/java/com/group10/moneymate/data/repository/BudgetRepository.java` (add/update/delete and derived sync)
  - `app/src/main/java/com/group10/moneymate/data/repository/DebtRepository.java`
  - `app/src/main/java/com/group10/moneymate/data/repository/EventRepository.java`

Risk:
- If app process is killed while queued tasks are pending, in-memory task queue is lost.
- No durable scheduling/guaranteed retry for long-running critical work.

Impact:
- Silent write loss and inconsistent local state transitions under background pressure.

### VUL-P1-02: AI pipeline durability not implemented yet (future high risk)

- Files:
  - `app/src/main/java/com/group10/moneymate/ui/ai/AIViewModel.java`
  - `app/src/main/java/com/group10/moneymate/ui/ai/AIAssistantFragment.java`
  - `app/src/main/java/com/group10/moneymate/ui/ai/AIReceiptScannerFragment.java`

Observation:
- AI feature layer is currently stub-level; no WorkManager-backed durable path is visible in scanned Java sources.

Risk:
- If implemented with raw executor/network calls in UI scope, tasks can be dropped when app backgrounds.

---

## P1 - SQLite contention / connection starvation

### VUL-P1-03: Heavy aggregate query patterns and repeated correlated subqueries

- File: `app/src/main/java/com/group10/moneymate/data/local/dao/WalletDao.java`
- Functions: `getAllByUserWithBalance`, `getActiveByUserWithBalance`, `getByIdWithBalance`, `getTotalBalance`

Risk:
- Per-wallet correlated subqueries over `transactions` are expensive under high cardinality.
- Concurrent stats reads + sync writes increase lock hold time and writer waiting.

Impact:
- Potential `SQLiteDatabaseLockedException`/timeouts under sustained load.

### VUL-P1-04: Analytics queries use heavy grouping functions on large windows

- File: `app/src/main/java/com/group10/moneymate/data/local/dao/TransactionDao.java`
- Evidence examples:
  - `STRFTIME(..., 'unixepoch', 'localtime')` grouping at lines around 179-193, 338-373, 382-398

Risk:
- Function-based grouping over large ranges can cause long-running scans, especially without matching composite indexes.

Impact:
- DB read pressure can delay write commits from AI/Sync threads.

---

## P2 - Timezone and clock drift

### VUL-P2-01: Localtime-based SQL grouping can diverge from server UTC windows

- File: `app/src/main/java/com/group10/moneymate/data/local/dao/TransactionDao.java`
- Pattern: `STRFTIME(..., 'unixepoch', 'localtime')`

Risk:
- Daily/monthly budget and statistics buckets depend on device timezone.
- Same timestamp may map to different periods locally vs cloud-side UTC logic.

Impact:
- Off-by-one-day budget/statistics discrepancies after sync or timezone changes.

### Positive note

- Time columns are stored as `long` epoch millis in entities (`timestamp`, `updated_at`, etc.), which is correct foundation.
- Primary drift issue is in *period grouping policy* (localtime vs UTC), not raw storage type.

---

## 2) Refactoring Code (System-Level Fix Snippets)

## A) Chunk/Pagination for pending sync reads

```java
// TransactionDao.java
@Query("SELECT * FROM transactions " +
       "WHERE user_id = :userId AND sync_status != 0 " +
       "ORDER BY updated_at ASC " +
       "LIMIT :limit OFFSET :offset")
List<TransactionEntity> getPendingSyncTransactionsPage(String userId, int limit, int offset);
```

```java
// Sync runner pseudo-flow
int page = 0;
int size = 200;
while (true) {
    List<TransactionEntity> batch = transactionDao.getPendingSyncTransactionsPage(userId, size, page * size);
    if (batch == null || batch.isEmpty()) break;
    uploadBatch(batch);
    page++;
}
```

## B) Coalesce UI recompute instead of rebuilding on every emission

```java
// BudgetViewModel.java
private final Handler uiHandler = new Handler(Looper.getMainLooper());
private final Runnable rebuildRunnable = this::rebuildUiModels;

private void scheduleRebuildUiModels() {
    uiHandler.removeCallbacks(rebuildRunnable);
    uiHandler.postDelayed(rebuildRunnable, 100L);
}
```

Replace direct `rebuildUiModels()` calls inside child observers with `scheduleRebuildUiModels()`.

## C) Batch writes in one transaction to reduce invalidation storms

```java
// Repository-side
appDatabase.runInTransaction(() -> {
    for (TransactionEntity tx : incomingBatch) {
        tx.setUpdatedAt(System.currentTimeMillis());
        tx.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        transactionDao.updateTransaction(tx);
    }
});
```

## D) Durable execution for critical background tasks

```java
Constraints constraints = new Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build();

OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncWorker.class)
        .setConstraints(constraints)
        .build();

WorkManager.getInstance(context)
        .enqueueUniqueWork("critical_sync", ExistingWorkPolicy.KEEP, request);
```

## E) Add hot-path indexes for sync scans and time-window filtering

```java
// TransactionEntity.java (indices add-on)
@Index(value = {"user_id", "sync_status", "updated_at"}),
@Index(value = {"user_id", "is_deleted", "timestamp"}),
@Index(value = {"wallet_id", "is_deleted", "type", "timestamp"})
```

## F) UTC-safe period boundaries for cloud-consistent grouping

```java
public final class TimeWindowUtils {
    private TimeWindowUtils() {}

    public static long startOfDayUtc(long epochMillis) {
        Instant instant = Instant.ofEpochMilli(epochMillis);
        LocalDate date = instant.atZone(ZoneOffset.UTC).toLocalDate();
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }
}
```

Use UTC policy consistently in both local query windows and server sync contracts.

---

## 3) Strict Engineering Guidelines (Mandatory Rules)

1. **No unbounded pending-sync reads**: all sync fetch APIs must support chunking/paging.
2. **No per-row observer fan-out without coalescing**: bulk observer emissions must be debounced/coalesced before UI rebuild.
3. **No critical write job on raw executor alone**: important background work must be scheduled with WorkManager.
4. **No multi-step business write without transaction boundary**: validate + write + derived write must be atomic.
5. **No heavy analytics query without matching composite index**: every hot WHERE/ORDER BY path must have index proof.
6. **No localtime-vs-UTC ambiguity**: define one canonical period policy (recommended UTC for sync consistency) and enforce globally.
7. **No migration without large-data test**: migration PR must include instrumentation tests for data-heavy upgrade paths.
8. **No UI list full reload by default**: prefer incremental diff updates and throttled observer processing.
9. **No silent task drop acceptable**: background jobs require retry/backoff/idempotency design.
10. **No merge without perf gate**: add baseline thresholds for DB query time, dropped frames, and sync batch latency.

---

## Round 3 Exit Criteria

Round 3 is considered complete only when:
- P0 findings are addressed in code and verified by tests.
- P1 findings have implementation tickets and measurable guardrails.
- Team adopts mandatory rules in PR checklist and CI quality gate.

