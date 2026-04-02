# MoneyMate Concurrency and Sync Audit - Round 2 (Deep Dive)

Date: 2026-04-02

## Executive Summary

Overall status: **Not ready for safe multithreading** in current form.

Round 2 confirms Round 1 and adds structural risks:
- Repository wiring drift vs intended wallet-integrity architecture.
- Metadata consistency gaps for deterministic conflict resolution.
- Hard delete paths still present in DAO layer.

## Critical Vulnerabilities

### CV-01: Wrong sync status in soft delete (P0)

Evidence:
- `app/src/main/java/com/group10/moneymate/data/local/dao/TransactionDao.java` (`softDelete`)
- `app/src/main/java/com/group10/moneymate/data/local/dao/DebtDao.java` (`softDelete`)
- `app/src/main/java/com/group10/moneymate/data/local/dao/EventDao.java` (`softDelete`)

Deleted rows are flagged as pending upload instead of pending delete.

### CV-02: Non-atomic budget write chain (P0)

Evidence:
- `app/src/main/java/com/group10/moneymate/data/repository/BudgetRepository.java` (`addBudget`, `updateBudget`, `softDeleteBudget`, `syncOtherCategoriesBudget`)
- `app/src/main/java/com/group10/moneymate/data/local/AppDatabase.java` (`databaseWriteExecutor` with multi-thread pool)

Multi-query workflows can interleave under parallel writes.

### CV-03: `REPLACE` conflict policy causes overwrite risk (P0)

Evidence in insert APIs:
- `app/src/main/java/com/group10/moneymate/data/local/dao/WalletDao.java`
- `app/src/main/java/com/group10/moneymate/data/local/dao/TransactionDao.java`
- `app/src/main/java/com/group10/moneymate/data/local/dao/EventDao.java`
- `app/src/main/java/com/group10/moneymate/data/local/dao/DebtDao.java`
- `app/src/main/java/com/group10/moneymate/data/local/dao/CategoryDao.java`
- `app/src/main/java/com/group10/moneymate/data/local/dao/BudgetDao.java`

### CV-04: Transaction repository wiring drift (P1)

Evidence:
- `app/src/main/java/com/group10/moneymate/di/AppContainer.java` creates `TransactionRepository` with `TransactionDao` only.
- `app/src/main/java/com/group10/moneymate/data/repository/TransactionRepository.java` constructor accepts only `TransactionDao`.
- `updateTransaction(oldTransaction, newTransaction)` does not use `oldTransaction`.

Risk:
- Current signature implies old/new delta handling, but implementation ignores old state.
- If wallet persisted-balance logic is restored, corruption risk is high unless operations are atomic.

### CV-05: Hard delete methods violate soft-delete sync model (P1)

Evidence:
- `app/src/main/java/com/group10/moneymate/data/local/dao/TransactionDao.java` (`deleteAllByUser`)
- `app/src/main/java/com/group10/moneymate/data/local/dao/DebtDao.java` (`deleteAllByUser`)
- `app/src/main/java/com/group10/moneymate/data/local/dao/EventDao.java` (`deleteAllByUser`)

## Architectural Gaps

### AG-01: Metadata inconsistency for conflict resolution

- `UserEntity` does not expose full sync metadata expected for offline-first merge control.
- `CategoryEntity`, `TransactionEntity`, `DebtEntity`, `EventEntity` do not consistently include `created_at`.
- This weakens deterministic merge policy for distributed sync.

### AG-02: Transaction type model mismatch

- `app/src/main/java/com/group10/moneymate/models/TransactionType.java` has only `INCOME`, `EXPENSE`.
- DAO logic and UI flows also use `TRANSFER` as a valid type.

This mismatch reduces type safety and can produce runtime-only failures.

### AG-03: Sync execution layer not visible in scanned sources

Pending-sync DAO methods exist, but no concrete sync worker implementation was found in the scanned Java scope.

## Round 2 Refactoring Priorities

1. P0: Correct soft-delete semantics and eliminate delete ambiguity.
2. P0: Make budget write chain transactional.
3. P0: Replace destructive conflict handling strategy for hot entities.
4. P1: Align `TransactionRepository` wiring and update semantics.
5. P1: Remove hard deletes from normal application path.
6. P2: Normalize entity sync metadata and transaction domain typing.

## Regression Targets for Round 2

- Same-row concurrent write race (UI + AI + sync marker updates).
- Delete/update race on same transaction/debt/event id.
- Budget parallel create/update for same period and wallet.
- Remote stale payload should not overwrite newer local pending upload.
- Pending delete queue should be preserved through retry cycles.

