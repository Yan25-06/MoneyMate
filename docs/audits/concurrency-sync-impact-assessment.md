# MoneyMate Impact Assessment Report for Concurrency/Sync Remediation Plan

Date: 2026-04-02
Input plan: `docs/audits/concurrency-sync-audit-action-plan.md`
Related audits:
- `docs/audits/concurrency-sync-audit-round1.md`
- `docs/audits/concurrency-sync-audit-round2.md`

## 1) Executive Summary

This report evaluates how the remediation plan will affect current features, method signatures, call chains, behavior, and regression surface.

Overall impact level: **High (architecture + data-layer)**, but rollout risk can be controlled with staged commits and migration-first compatibility.

Most affected functional domains:
- Transaction CRUD and delete flows
- Budget CRUD + "other category" auto-calculation
- Sync conflict behavior (insert/update/delete semantics)
- Statistics filters and transaction type handling

Least affected domains:
- Auth screens and login routing
- Static UI rendering and navigation-only features

## 2) Impact Heatmap by Feature

| Feature area | Impact level | Why impacted |
|---|---|---|
| Transaction Add/Edit/Delete | High | DAO conflict strategy, delete semantics, repository wiring changes |
| Wallet balance & totals | High | Transaction repository contract and potential balance-logic adjustment |
| Budget Add/Edit/Delete | High | Atomic transaction boundary changes in repository |
| Debt/Event delete | Medium | Soft-delete sync semantics corrected |
| Category seed/default logic | Medium | Conflict policy changes in category inserts/upserts |
| Statistics (type filters/charts) | Medium | Transaction type model alignment (`TRANSFER`) |
| Auth/Profile/Settings | Low | Mostly unaffected directly; only indirect DB migration dependency |

## 3) Detailed Impact by Planned Commit

## Commit 1 - Fix soft-delete semantics and remove hard delete path

### Affected methods and call chains

1. Transaction delete chain:
- `app/src/main/java/com/group10/moneymate/ui/transaction/AddEditTransactionFragment.java` -> `TransactionViewModel.deleteTransaction(...)`
- `app/src/main/java/com/group10/moneymate/ui/transaction/TransactionViewModel.java` -> `TransactionRepository.softDeleteTransaction(...)`
- `app/src/main/java/com/group10/moneymate/data/repository/TransactionRepository.java` -> `TransactionDao.softDelete(...)`

2. Debt delete chain:
- `app/src/main/java/com/group10/moneymate/ui/debt/DebtViewModel.java` -> `DebtRepository.softDelete(...)` -> `DebtDao.softDelete(...)`

3. Event delete chain:
- `app/src/main/java/com/group10/moneymate/ui/event/EventViewModel.java` -> `EventRepository.softDelete(...)` -> `EventDao.softDelete(...)`

### Functional impact

- User-facing delete behavior remains the same (still soft delete).
- Sync behavior changes significantly: deleted rows now enter correct pending-delete state.
- Pending-sync queue quality improves; fewer delete resurrection bugs.

### API/signature impact

- Usually no public signature change needed.
- If `deleteAllByUser` is removed/hidden, any maintenance/debug callers (if later introduced) must migrate to soft-delete batch APIs.

### Regression risks

- Low for UI flow.
- Medium for sync worker logic if it currently assumes old wrong state (`sync_status = 1`) for delete handling.

---

## Commit 2 - Align TransactionRepository contract and DI wiring

### Affected methods and call chains

- `app/src/main/java/com/group10/moneymate/di/AppContainer.java` (`new TransactionRepository(...)`)
- `app/src/main/java/com/group10/moneymate/data/repository/TransactionRepository.java`
- `app/src/main/java/com/group10/moneymate/ui/transaction/TransactionViewModel.java`
- `app/src/main/java/com/group10/moneymate/ui/transaction/AddEditTransactionFragment.java` (edit path passes old/new transaction)

### Functional impact

Two possible implementation branches in plan:

1) **Persisted wallet balance branch**
- Constructor changes to include `WalletDao`.
- `updateTransaction(old, new)` must reverse old effect then apply new effect atomically.
- Wallet totals may shift compared to current behavior if historical drift exists.

2) **Computed balance branch**
- Keep wallet totals derived from transactions only.
- Simplify API by removing misleading `oldTransaction` parameter and document invariant.

### API/signature impact

- **High likelihood** of constructor signature change in `TransactionRepository` and `AppContainer` wiring.
- Possible method signature change for `updateTransaction(...)` affecting:
  - `TransactionViewModel.updateTransaction(...)`
  - `AddEditTransactionFragment` call site.

### Regression risks

- High around transfer handling (`wallet_id` vs `to_wallet_id`) and edit flows.
- High around wallet total display if logic branch changes.

---

## Commit 3 - Make BudgetRepository multi-step writes atomic

### Affected methods and call chains

- Repository write APIs:
  - `BudgetRepository.addBudget(...)`
  - `BudgetRepository.updateBudget(...)`
  - `BudgetRepository.softDeleteBudget(...)`
  - internal `syncOtherCategoriesBudget(...)`
- Callers:
  - `app/src/main/java/com/group10/moneymate/ui/budget/BudgetViewModel.java`
  - `app/src/main/java/com/group10/moneymate/ui/budget/AddEditBudgetViewModel.java`
  - `app/src/main/java/com/group10/moneymate/ui/budget/AddEditBudgetFragment.java`

### Functional impact

- Business rules become deterministic under concurrent writes.
- "Other category" derived budget becomes stable and less race-prone.

### API/signature impact

- Public ViewModel APIs can remain unchanged.
- Internal repository implementation changes are significant (transaction boundary orchestration).

### Behavioral side effects

- Callback timing may shift (callback fired after entire transaction commit, not after partial step).
- Error surfaces may be more consistent (all-or-nothing failures).

---

## Commit 4 - Replace broad `REPLACE` conflict strategy

### Affected methods

Potentially all DAO insert methods currently using `OnConflictStrategy.REPLACE`, including:
- `WalletDao.insert(...)`
- `TransactionDao.insertTransaction(...)`
- `CategoryDao.insertCategory(...)`, `CategoryDao.insertAll(...)`
- `BudgetDao.insert(...)`
- `DebtDao.insertDebt(...)`
- `EventDao.insertEvent(...)`

### Functional impact

- Duplicate key behavior changes from implicit overwrite to explicit conflict handling.
- Existing code paths that relied on overwrite semantics must be updated (or split local insert vs remote merge path).

### High-risk dependency points

- Category default seeding and virtual categories:
  - `CategoryRepository.seedDefaults()`
  - `CategoryRepository.ensureVirtualOtherCategoriesExistInternal()`
- Any future sync-upsert path (currently pending implementation) must not assume REPLACE.

### API/signature impact

- DAO method names may split (e.g., `insertLocal`, `applyRemoteIfNewer`).
- Repository layer will need new merge-specific calls for sync thread.

---

## Commit 5 - Metadata normalization + migration 8->9

### Affected schema/API areas

- Entities under `app/src/main/java/com/group10/moneymate/data/local/entity/`
- `AppDatabase` version and migration registration
- DAO queries that project/select/update new metadata columns
- Any mapping code in repositories and sync layer

### Functional impact

- Better conflict resolution reliability.
- Potentially larger object payloads in entity writes.
- Old DBs must migrate safely without data loss.

### API/signature impact

- Getter/setter additions on entities.
- Possible constructor/default-value adjustments.
- DAO SQL updates where `INSERT`/`UPDATE` enumerate columns.

### Migration risks

- Medium/high if defaults/backfill are not carefully chosen.
- If `NOT NULL` added without backfill strategy, startup crash risk on upgrade.

---

## Commit 6 - Metadata/constraint hardening + migration 9->10

### Affected areas

- New/updated indexes for hot sync scans (`user_id`, `sync_status`, `is_deleted`, `updated_at`).
- DAO query plans for pending sync retrieval.

### Functional impact

- No visible UI changes expected.
- Performance characteristics will change (normally improved read/scan speed).

### Risks

- Index mismatch or migration SQL defects can block startup.
- Query plans can regress if indexes conflict with current WHERE order.

---

## Commit 7 - Transaction type model consistency (`TRANSFER`)

### Affected methods and files

- Enum and converters:
  - `app/src/main/java/com/group10/moneymate/models/TransactionType.java`
  - `app/src/main/java/com/group10/moneymate/data/local/Converters.java`
- Statistics stack importing enum (examples):
  - `StatisticsViewModel`, `IncomeExpenseDetailViewModel`, `CategoryReportViewModel`, `StatisticsCategoryDayDetailViewModel`
  - related fragments/adapters in `ui/statistics/`
- Transaction UI that currently uses string constants for transfer.

### Functional impact

- Stronger type safety and fewer string-literal inconsistencies.
- Charts/reports must explicitly define whether `TRANSFER` is included or excluded.

### Regression risks

- Medium: if default parser logic assumes only INCOME/EXPENSE, adding TRANSFER can affect filter defaults and labels.

---

## Commit 8 - Add concurrency/sync regression suite

### Affected areas

- `app/src/test/` and `app/src/androidTest/`
- CI configuration (if tests are made mandatory)

### Functional impact

- No runtime user feature change.
- Delivery pace may initially slow due to stricter quality gate.

### Benefits

- Detects race conditions and migration failures before release.

## 4) Function/Call-Site Impact Matrix

| Current function/call | Planned change impact | Expected code action |
|---|---|---|
| `TransactionRepository.updateTransaction(old, new)` | High | Either implement old/new atomic semantics or simplify signature and all callers |
| `AppContainer -> new TransactionRepository(...)` | High | Update constructor args and DI wiring |
| `TransactionDao.softDelete(...)` | Medium | SQL semantic correction only, same method signature |
| `DebtDao.softDelete(...)` | Medium | SQL semantic correction only, same method signature |
| `EventDao.softDelete(...)` | Medium | SQL semantic correction only, same method signature |
| `BudgetRepository.add/update/softDelete` | High | Wrap full chain in one transaction, keep callback contract stable |
| DAO `@Insert(...REPLACE)` methods | High | Replace with explicit conflict policy and possibly split APIs |
| `CategoryRepository.seedDefaults()` path | Medium | Validate behavior under new conflict policy |
| Statistics ViewModels using `TransactionType` | Medium | Review filter logic when enum includes `TRANSFER` |
| `deleteAllByUser` DAO methods | Low/Medium | Remove or isolate as non-production maintenance APIs |

## 5) Cross-Cutting Behavioral Impacts

### Data consistency
- Improves significantly after Commit 1-4.
- Most critical win: delete semantics and conflict determinism.

### Performance
- Commit 3 may slightly increase lock contention but yields correctness.
- Commit 6 should improve sync scan performance with proper indexing.

### UX
- Minimal direct UX changes expected.
- Possible subtle differences in list/chart numbers if previously corrupted states are corrected.

### Backward compatibility
- Migration commits (5, 6) are the major compatibility risk and require full upgrade testing.

## 6) Recommended Rollout and Safety Gates

1. Ship Commit 1 first (low UI risk, high correctness gain).
2. Ship Commit 3 before broad conflict-policy refactor to stabilize budget integrity.
3. Introduce constructor/signature changes (Commit 2) in isolated PR with explicit call-site updates.
4. Perform migrations in separate releases with dedicated upgrade testing (`8->9`, then `9->10`).
5. Only after migrations are stable, enforce stricter conflict and optimistic-lock policies.

## 7) Minimum Regression Checklist for This Plan

- [ ] Add/Edit/Delete transaction still works from `AddEditTransactionFragment`.
- [ ] Debt/Event soft delete no longer appears as pending upload.
- [ ] Budget add/update/delete remains functional in `BudgetViewModel` and `AddEditBudgetViewModel`.
- [ ] No duplicate all-categories budget after concurrent write simulation.
- [ ] Statistics screens still render valid totals and type-filter behavior.
- [ ] App upgrades cleanly from DB v8 to v9 and v10.

## 8) Conclusion

Executing the plan will materially improve concurrency safety and offline-first correctness, but it is a **high-touch refactor** for data layer contracts. The biggest blast radius is around transaction repository contract changes, DAO conflict policy redesign, and migration correctness. With commit isolation, strict tests, and staged rollout, the risk is manageable and justified.

