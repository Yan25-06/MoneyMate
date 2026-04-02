# MoneyMate Concurrency and Sync Audit - Round 1

Date: 2026-04-02

## Executive Summary

Overall status: **High risk** for concurrent operation across UI, AI background parsing, and periodic sync.

Main causes:
- Incorrect soft-delete sync semantics in multiple DAOs.
- Broad use of `OnConflictStrategy.REPLACE` in offline-first entities.
- Multi-step write flows without a single transaction boundary.
- Full-object updates that increase lost-update risk.

## Critical Vulnerabilities

### 1) Soft delete writes wrong sync status

Affected methods:
- `app/src/main/java/com/group10/moneymate/data/local/dao/TransactionDao.java` (`softDelete`)
- `app/src/main/java/com/group10/moneymate/data/local/dao/DebtDao.java` (`softDelete`)
- `app/src/main/java/com/group10/moneymate/data/local/dao/EventDao.java` (`softDelete`)

Current behavior marks deleted records with `sync_status = 1` (pending upload), while deleted rows should be `sync_status = 2` (pending delete).

Impact:
- Delete operations can be uploaded as normal updates.
- Cloud-side delete may never happen.
- Deleted items can be resurrected by reconciliation.

### 2) Conflict strategy `REPLACE` can overwrite newer local state

`REPLACE` is used in multiple DAOs (`TransactionDao`, `WalletDao`, `CategoryDao`, `BudgetDao`, `DebtDao`, `EventDao`).

Impact:
- Newer local unsynced changes can be replaced by stale payloads.
- `sync_status`, `updated_at`, and deletion flags can be silently lost.

### 3) Budget write workflow is not atomic

`BudgetRepository` uses multi-step read-validate-write logic (`addBudget`, `updateBudget`, `softDeleteBudget`, and `syncOtherCategoriesBudget`) without wrapping the full sequence in one DB transaction.

Impact:
- Parallel writes can interleave and violate business invariants.
- Derived "other category" budget can become inconsistent.

### 4) Full-row updates increase lost-update risk

Most writes rely on `@Update` with full entity objects.

Impact:
- Concurrent writers can overwrite each other even when touching different fields.
- Typical conflict example: local UI/AI write racing with sync thread state transition.

## Architectural Gaps

- Inconsistent conflict-resolution metadata across entities (`created_at` / `updated_at` / `sync_status`).
- Hard delete methods still exist in DAO surface (`deleteAllByUser` patterns).
- Repository-level business rules are not fully encoded as atomic units at DB boundary.

## Recommended Refactoring Baseline

1. Fix all soft-delete SQL to set `sync_status = 2`.
2. Replace broad `REPLACE` usage with explicit merge policy.
3. Wrap multi-step budget mutations in a transaction.
4. Prefer partial updates with optimistic guards over full-object overwrites.
5. Keep hard delete out of normal sync flow.

## Suggested Verification Set

- Parallel update test on same row (UI vs AI).
- Delete vs update race test.
- Duplicate budget creation race test.
- Sync semantic test for pending delete propagation.

