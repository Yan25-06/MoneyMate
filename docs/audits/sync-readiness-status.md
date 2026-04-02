# Sync Readiness Status / Trang thai san sang Sync - MoneyMate

- **Date / Ngay:** 2026-04-03
- **Scope / Pham vi:** Current readiness for offline-first to cloud sync rollout.
- **Baseline / Moc code:** Post R5-R6 remediation, `AppDatabase` version `13`.

## 1) Overall Status / Tong quan
- **VI:** **READY FOR CONTROLLED IMPLEMENTATION** (staging-first, rollout tung buoc).
- **EN:** **READY FOR CONTROLLED IMPLEMENTATION** (staging-first, phased rollout).

- **VI:** Nen tang local + sync da du de trien khai, nhung chua nen mo full production neu chua co telemetry va e2e conflict validation.
- **EN:** Local and sync foundations are sufficient to proceed, but full production rollout should wait until telemetry and e2e conflict validation are complete.

## 2) What Is Already Ready / Nhung gi da san sang
- **VI:** Pending sync reads da co paging + checkpoint cho `TransactionDao`, `DebtDao`, `EventDao`, `CategoryDao`, `WalletDao`.
- **EN:** Pending sync reads are now paged + checkpoint-based in `TransactionDao`, `DebtDao`, `EventDao`, `CategoryDao`, `WalletDao`.

- **VI:** `SyncWorker` da xu ly theo batch cho transactions/budgets/categories/wallets/debts/events.
- **EN:** `SyncWorker` now processes batched sync for transactions/budgets/categories/wallets/debts/events.

- **VI:** Index hot-path sync da duoc bo sung:
  - `idx_tx_user_sync_deleted_updated`
  - `idx_budget_user_sync_deleted_updated`
  - `idx_debt_user_sync_deleted_updated`
  - `idx_wallet_user_sync_deleted_updated`
- **EN:** Sync hot-path indexes were added:
  - `idx_tx_user_sync_deleted_updated`
  - `idx_budget_user_sync_deleted_updated`
  - `idx_debt_user_sync_deleted_updated`
  - `idx_wallet_user_sync_deleted_updated`

- **VI:** Da co migration khong pha du lieu `Migration12To13` va da nang DB len v13.
- **EN:** Non-destructive migration `Migration12To13` is in place and DB is upgraded to v13.

## 3) Mandatory Implementation Notes / Luu y bat buoc khi trien khai
- **VI:** Server API phai idempotent cho upsert/delete theo `(userId, entityId, updatedAt)`.
- **EN:** Server API must be idempotent for upsert/delete using `(userId, entityId, updatedAt)`.

- **VI:** Conflict policy can duoc fix ro rang (local-wins / server-wins / field-merge), khong de implicit.
- **EN:** Conflict policy must be explicit (local-wins / server-wins / field-merge), never implicit.

- **VI:** Checkpoint chi di tien, khong rollback; tiep tuc theo thu tu `updated_at`, `id`.
- **EN:** Checkpoint must be monotonic; process in `updated_at`, `id` order.

- **VI:** Tat ca local writes van phai chay qua `AppDatabase.databaseWriteExecutor`, khong tren main thread.
- **EN:** All local writes must remain on `AppDatabase.databaseWriteExecutor`, never on main thread.

- **VI:** Delete flow: local soft-delete (`sync_status=2`) -> cloud delete thanh cong -> hard-delete local maintenance path.
- **EN:** Delete flow: local soft-delete (`sync_status=2`) -> successful cloud delete -> local maintenance hard-delete.

- **VI:** Batch size nen configurable (100-200 mac dinh), tune theo telemetry.
- **EN:** Batch size should be configurable (default 100-200), tuned with telemetry.

## 4) Remaining Risks / Rui ro con lai
- **VI:** Chua du metrics cho batch duration, retry count, per-domain failure rate.
- **EN:** Metrics for batch duration, retry count, and per-domain failure rate are still incomplete.

- **VI:** Chua test e2e day du cho network flap + duplicate delivery + app process restart.
- **EN:** Full e2e tests are still missing for network flaps, duplicate delivery, and process restarts.

- **VI:** Chua co dashboard canh bao nghen sync/retry loop.
- **EN:** Alerting dashboard for sync stalls/retry loops is not complete yet.

## 5) Production Gate Checklist / Checklist gate production
- [ ] Telemetry day du: throughput, latency, retries, fail domains.
- [ ] E2E test 10k+ records/domain tren mang khong on dinh.
- [ ] Validate idempotency cho upsert/delete o server.
- [ ] Verify migration `12 -> 13` tren data thuc (khong clean install only).
- [ ] Verify WorkManager constraints, battery policy, retry backoff.

## 6) Suggested Rollout Plan / De xuat rollout
1. **VI:** Bat dau voi domain nho (categories/wallets) tren staging.  
   **EN:** Start with smaller domains (categories/wallets) in staging.
2. **VI:** Theo doi 3-7 ngay, fix regression truoc khi mo rong.  
   **EN:** Observe for 3-7 days and fix regressions before expansion.
3. **VI:** Mo rong transactions -> budgets -> debts/events.  
   **EN:** Expand to transactions -> budgets -> debts/events.
4. **VI:** Canary production theo ty le user tang dan.  
   **EN:** Enable production canary with gradual user percentage.
