# Concurrency and Sync Audit Action Plan

Date: 2026-04-02
Related reports:
- `docs/audits/concurrency-sync-audit-round1.md`
- `docs/audits/concurrency-sync-audit-round2.md`

## Goal

Remediate multithreading and offline-first sync risks with safe, incremental commits.

## Commit-by-Commit Plan

### Commit 1 - Fix soft-delete semantics and remove destructive delete path

Scope:
- `app/src/main/java/com/group10/moneymate/data/local/dao/TransactionDao.java`
- `app/src/main/java/com/group10/moneymate/data/local/dao/DebtDao.java`
- `app/src/main/java/com/group10/moneymate/data/local/dao/EventDao.java`

Changes:
- Set soft delete SQL to `sync_status = 2` (`PENDING_DELETE`) for all affected DAOs.
- Replace/retire `deleteAllByUser` hard-delete APIs from app flow (or guard as maintenance-only).

Acceptance criteria:
- Deleting any transaction/debt/event always results in `is_deleted = 1` and `sync_status = 2`.
- No production path uses hard delete for these tables.

---

### Commit 2 - Align TransactionRepository wiring and balance-integrity contract

Scope:
- `app/src/main/java/com/group10/moneymate/data/repository/TransactionRepository.java`
- `app/src/main/java/com/group10/moneymate/di/AppContainer.java`

Changes:
- Align constructor and implementation with intended old/new update semantics.
- If wallet persisted-balance mutation is required, inject `WalletDao` and apply reverse-old/apply-new atomically.
- If computed-balance-only strategy is retained, simplify API signature to remove misleading `oldTransaction` argument and document invariant clearly.

Acceptance criteria:
- Repository API and implementation are consistent and unambiguous.
- Update path has deterministic behavior under concurrent updates.

---

### Commit 3 - Make BudgetRepository multi-step writes atomic

Scope:
- `app/src/main/java/com/group10/moneymate/data/repository/BudgetRepository.java`
- `app/src/main/java/com/group10/moneymate/data/local/dao/BudgetDao.java`

Changes:
- Wrap validate + insert/update + syncOther budget sequence in one transaction boundary (`runInTransaction` or DAO transaction entrypoint).
- Keep callbacks outside transactional section.

Acceptance criteria:
- No partial write states when exceptions occur mid-flow.
- Parallel writes for same budget scope cannot break invariants.

---

### Commit 4 - Replace destructive conflict strategy on critical entities

Scope:
- DAOs under `app/src/main/java/com/group10/moneymate/data/local/dao/`

Changes:
- Replace broad `OnConflictStrategy.REPLACE` usage for mutable sync entities.
- Split write API into:
  - local write path (safe insert/update)
  - remote merge path (timestamp/version guarded update)
- Add explicit conflict policy doc comments per DAO.

Acceptance criteria:
- Stale remote payload cannot overwrite newer local unsynced rows.
- Conflict behavior is deterministic and test-covered.

---

### Commit 5 - Metadata normalization and migration phase 1

Scope:
- Entities under `app/src/main/java/com/group10/moneymate/data/local/entity/`
- `app/src/main/java/com/group10/moneymate/data/local/AppDatabase.java`
- new migration file(s) in `app/src/main/java/com/group10/moneymate/data/local/migrations/`

Changes:
- Add missing metadata fields required by final sync policy (for example `created_at`, `updated_at`, `sync_status` where missing by architecture decision).
- Introduce migration **8 -> 9** for additive columns and backfill defaults.

Acceptance criteria:
- DB upgrade preserves data.
- New columns are fully backfilled and queries remain valid.

---

### Commit 6 - Metadata/constraint hardening and migration phase 2

Scope:
- `AppDatabase`, migration files, DAO indexes/constraints

Changes:
- Apply migration **9 -> 10** for refined indexes/constraints and optional optimistic-lock support columns if chosen.
- Add/adjust indexes for hot sync queries (`user_id`, `sync_status`, `is_deleted`, `updated_at`).

Acceptance criteria:
- Migrations run sequentially on real upgrade paths.
- Pending-sync scans are performant on large datasets.

---

### Commit 7 - Transaction type model consistency

Scope:
- `app/src/main/java/com/group10/moneymate/models/TransactionType.java`
- dependent UI/DAO/repository mapping code

Changes:
- Align enum and string usage to include all supported types (`INCOME`, `EXPENSE`, `TRANSFER`) consistently.

Acceptance criteria:
- No mixed type-source assumptions.
- Compile-time model and SQL semantics match.

---

### Commit 8 - Add concurrency and sync regression test suite

Scope:
- `app/src/test/`
- `app/src/androidTest/`

Changes:
- Add deterministic tests for race, conflict, and soft-delete semantics.
- Add migration tests for 8->9->10 upgrade path.

Acceptance criteria:
- Test suite catches prior bug classes.
- CI gate requires these tests for merge.

## Migration Order

1. Ship code that can read both old and new schema safely.
2. Apply migration **8 -> 9** (metadata columns + backfill).
3. Release verification build and run migration instrumentation tests.
4. Apply migration **9 -> 10** (indexes/constraints/optimization).
5. Enable stricter DAO conflict and optimistic-lock logic after both migrations are stable.

## Test Checklist

### A) Concurrency tests

- [ ] UI update and AI update on same entity id within overlapping window.
- [ ] Update vs soft-delete race on same id.
- [ ] Concurrent budget create for same scope (same user/wallet/date range).
- [ ] Concurrent budget update while `syncOtherCategoriesBudget` is running.

### B) Sync semantics tests

- [ ] Soft delete always writes `sync_status = PENDING_DELETE`.
- [ ] Pending upload queue excludes rows already pending delete.
- [ ] Stale remote payload does not overwrite newer local `updated_at`.
- [ ] Retry behavior keeps sync state monotonic and idempotent.

### C) Data integrity tests

- [ ] Wallet totals remain consistent after transaction insert/update/delete cycles.
- [ ] No duplicate all-categories budget in same scope.
- [ ] "Other category" budget value remains deterministic after parallel operations.

### D) Migration tests

- [ ] Upgrade from v8 to v9 keeps all rows and backfills metadata.
- [ ] Upgrade from v9 to v10 keeps constraints and index validity.
- [ ] End-to-end open DB on old version then query via new DAOs without crash.

### E) Regression checks

- [ ] Existing feature flows (add/edit/delete transaction, wallet, budget, debt, event) still pass.
- [ ] LiveData observers still receive expected state transitions.

## Implementation Notes

- Keep each commit small and independently reviewable.
- Prefer backward-compatible schema evolution; avoid destructive migration.
- Do not mix broad refactor and schema migration in same commit unless required.

