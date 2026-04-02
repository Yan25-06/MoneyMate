# CRITICAL AUDIT REPORT / BAO CAO KIEM TOAN NGHIEM TRONG

## 1) Header / Thong tin
- **Date / Ngay:** 2026-04-03
- **Scope / Pham vi:** Static audit `app/src/main/java/com/group10/moneymate/` for R1-R9 (concurrency + offline-sync blockers).
- **Total Rules / Tong so rule:** 9
- **Snapshot / Moc danh gia:** Post-remediation after R5 and R6 fixes.

## 2) Executive Summary / Tong ket dieu hanh
- **VI:** Ket qua hien tai la **PASS trong pham vi R1-R9**. Cac blocker R5 (non-paged sync reads) va R6 (missing composite indexes) da duoc khac phuc.
- **EN:** Current result is **PASS within R1-R9 scope**. Former blockers R5 (non-paged sync reads) and R6 (missing composite indexes) are remediated.

- **VI:** PASS nay chi ap dung cho pham vi audit R1-R9; chua dong nghia da san sang rollout production quy mo lon cho cloud sync/AI.
- **EN:** This PASS applies only to the R1-R9 audit scope; it does not automatically mean large-scale production rollout readiness for cloud sync/AI.

## 3) Detailed Rule Status / Trang thai chi tiet

| Rule | Severity | Status | Evidence |
|---|---|---|---|
| R1 | P0 | PASS | `app/src/main/java/com/group10/moneymate/data/local/dao/TransactionDao.java`, `app/src/main/java/com/group10/moneymate/data/local/dao/DebtDao.java`, `app/src/main/java/com/group10/moneymate/data/local/dao/EventDao.java` soft-delete sets `sync_status = 2`. |
| R2 | P0 | PASS | Mutable sync DAO upsert paths no longer use broad `OnConflictStrategy.REPLACE` pattern for risky overwrite behavior. |
| R3 | P0 | PASS | `app/src/main/java/com/group10/moneymate/data/repository/BudgetRepository.java` uses transaction boundary (`runInTransaction`) for multi-step writes. |
| R4 | P0 | PASS | `app/src/main/java/com/group10/moneymate/ui/budget/BudgetViewModel.java` no blocking fan-out pattern remaining in audited path. |
| R5 | P1 | PASS | Paged pending-sync methods exist in `TransactionDao`, `DebtDao`, `EventDao`, `CategoryDao`, `WalletDao`; batching consumed by `SyncWorker`. |
| R6 | P1 | PASS | Composite indexes added in `TransactionEntity`, `BudgetEntity`, `DebtEntity`, `WalletEntity`; migration `Migration12To13` applied. |
| R7 | P2* | PASS | No `localtime`-dependent STRFTIME hot path for audited sync/stat critical path. |
| R8 | P1 | PASS | `app/src/main/java/com/group10/moneymate/di/AppContainer.java` wires `TransactionRepository` with both `TransactionDao` and `WalletDao`. |
| R9 | P1 | PASS | Hard-delete is restricted to maintenance/sync path, not UI production flow in audited scope. |

## 4) Implemented Evidence for R5/R6 / Bang chung da trien khai

### R5 - Paged sync reads / Doc du lieu sync theo trang
- **VI:** Da bo sung cac method `getPendingSync...PagedSince(..., limit, offset)` cho 5 DAO: Transaction, Debt, Event, Category, Wallet.
- **EN:** Added `getPendingSync...PagedSince(..., limit, offset)` across 5 DAOs: Transaction, Debt, Event, Category, Wallet.

- **VI:** `SyncWorker` da chay theo batch co checkpoint (`updated_at`, `id`) cho cac domain transactions/budgets/categories/wallets/debts/events.
- **EN:** `SyncWorker` now processes checkpoint-based batches (`updated_at`, `id`) for transactions/budgets/categories/wallets/debts/events.

### R6 - Composite indexes / Index tong hop hot path
- **VI:** Da them index theo pattern `(user_id, sync_status, is_deleted, updated_at)`:
  - `idx_tx_user_sync_deleted_updated`
  - `idx_budget_user_sync_deleted_updated`
  - `idx_debt_user_sync_deleted_updated`
  - `idx_wallet_user_sync_deleted_updated`
- **EN:** Added composite indexes with shape `(user_id, sync_status, is_deleted, updated_at)`:
  - `idx_tx_user_sync_deleted_updated`
  - `idx_budget_user_sync_deleted_updated`
  - `idx_debt_user_sync_deleted_updated`
  - `idx_wallet_user_sync_deleted_updated`

- **VI:** Da tao migration khong pha du lieu `app/src/main/java/com/group10/moneymate/data/local/migrations/Migration12To13.java`, va nang `AppDatabase` len version 13.
- **EN:** Added non-destructive migration `app/src/main/java/com/group10/moneymate/data/local/migrations/Migration12To13.java`, and bumped `AppDatabase` to version 13.

## 5) Residual Risks Outside R1-R9 / Rui ro con lai ngoai pham vi
- **VI:** Can tiep tuc hardening cho idempotency server, conflict policy chi tiet, telemetry sync va e2e staging du lieu lon.
- **EN:** Further hardening is still needed for server idempotency, explicit conflict policy, sync telemetry, and large-scale staging e2e tests.

## 6) Conclusion / Ket luan
- **VI:** Trong pham vi R1-R9, codebase hien tai dat muc compliance. Co the tiep tuc phase sync/AI theo lo trinh rollout co kiem soat.
- **EN:** Within R1-R9 scope, the codebase is compliant. The team can proceed with controlled sync/AI rollout phases.
