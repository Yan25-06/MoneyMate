# Pre-AI & Pre-Sync Foundation Plan (MoneyMate) – Hoàn chỉnh

> **Phạm vi tài liệu này:** gia cố toàn diện nền tảng Local (Room + MVVM + Repository + UI lifecycle + Durability + Performance), **không** triển khai logic gọi API AI hoặc đồng bộ Cloud.
>
> **Mục tiêu:** đưa hệ thống về trạng thái an toàn, bền vững và hiệu năng cao trước khi mở Phase AI/Sync, loại bỏ hoàn toàn nguy cơ crash, race condition, data corruption, UI thrashing, mất dữ liệu do process chết, và contention SQLite.

## Mục tiêu kỹ thuật (bổ sung)

- Chuẩn hóa metadata thời gian và soft-delete để dữ liệu nhất quán.
- Đảm bảo thao tác nhiều bước là atomic (không bị chen luồng).
- Loại bỏ conflict strategy nguy hiểm (`REPLACE`) trong các entity mutable.
- Ổn định luồng UI/LiveData khi DB có write dày.
- **Thiết lập durable execution cho critical writes (WorkManager).**
- **Áp dụng pagination cho mọi query list có dung lượng lớn.**
- **Tối ưu SQLite bằng index và loại bỏ timezone drift trong grouping.**
- Thiết lập luật kiến trúc bắt buộc trước khi bắt đầu code AI/Sync.

## Theo dõi triển khai

| Phase | Trọng tâm | Ưu tiên | Trạng thái |
|---|---|---|---|
| Phase 1 | Schema & Metadata | P0 | [X] Chưa bắt đầu |
| Phase 2 | Atomic Ops & DI Wiring | P0 | [X] Chưa bắt đầu |
| Phase 3 | Conflict & Deletion Cleanup | P0 | [X] Chưa bắt đầu |
| Phase 4 | UI Performance Hardening | **P0 (nâng cấp)** | [X] Chưa bắt đầu |
| **Phase 5** | **Durability & Background Tasks (WorkManager)** | **P0 (mới)** | [X] Chưa bắt đầu |
| **Phase 6** | **Indexing, Pagination & Timezone** | **P0 (mới)** | [X] Chưa bắt đầu |
| Phase 7 | Architecture Rules Gate | P0 | [ ] Chưa bắt đầu |

> **Lưu ý:** Phase 4, 5, 6 là bắt buộc, không thể bỏ qua. Phase cũ 5 chuyển thành Phase 7.

---

## Phase 1 - Database Schema & Metadata (Chuẩn bị dữ liệu)

### [X] Task 1.1 - Chuẩn hóa metadata thời gian cho toàn bộ Entity (UTC Unix Timestamp)
**Why:** Hiện metadata không đồng đều giữa các bảng, gây khó merge conflict và sai lệch thống kê theo thời gian.

**Files Affected:**
- `UserEntity.java`, `TransactionEntity.java`, `CategoryEntity.java`, `DebtEntity.java`, `EventEntity.java`, `WalletEntity.java`, `BudgetEntity.java`

**Action:**
- Bổ sung/chuẩn hóa đầy đủ field `created_at`, `updated_at` kiểu `long`.
- Chuẩn hóa tên cột: `@ColumnInfo(name = "created_at")`, `@ColumnInfo(name = "updated_at")`.
- Quy ước: lưu epoch millis theo UTC.

### [X] Task 1.2 - Tạo migration nâng version DB để backfill metadata
**Files Affected:** `AppDatabase.java`, `Migration8To9.java`

**Action:**
- Tăng version DB từ `8 -> 9`.
- Thêm cột metadata với default an toàn.
- Backfill `created_at`/`updated_at` bằng timestamp hiện có hoặc `System.currentTimeMillis()`.

### [X] Task 1.3 - Chuẩn hóa query thời gian cho logic Local
**Files Affected:** `TransactionDao.java`, `StatisticsViewModel.java`, `StatisticsCategoryDayDetailViewModel.java`

**Action:**
- Rà soát các query dùng `localtime`, thống nhất policy UTC.
- Định nghĩa helper `TimeWindowUtils.startOfDayUtc()`.

> **Lưu ý:** Phase 1 phải hoàn tất và chạy pass migration test trước khi chạm các phase còn lại.

---

## Phase 2 - Atomic Operations & DI Wiring

### [X] Task 2.1 - Inject `WalletDao` vào `TransactionRepository`
**Files:** `TransactionRepository.java`, `AppContainer.java`

**Action:**
- Đổi constructor nhận thêm `WalletDao`.
- Đồng bộ wiring.
- Nếu giữ computed balance, bỏ tham số `oldTransaction` gây hiểu nhầm.

### [X] Task 2.2 - Bọc thao tác nhiều bước Budget bằng transaction boundary
**Files:** `BudgetRepository.java`, `BudgetDao.java`, `AppDatabase.java`

**Action:**
- Dùng `runInTransaction` hoặc `@Transaction` cho toàn bộ chuỗi validate+write+syncOther.

### [X] Task 2.3 - Chuẩn hóa thao tác nhiều bước liên quan Wallet
**Files:** `WalletDao.java`, `WalletRepository.java`

**Action:**
- Tạo method cập nhật cụm field nhất quán, hạn chế full-object overwrite.
- Bổ sung `@Transaction` cho flow nhiều bước.

---

## Phase 3 - Conflict Resolution & Deletion

### Goal
Refactor all changes made in Phase 3 (Tasks 3.1, 3.2, 3.3) to use the **UPSERT pattern** described below. Ensure that:
- All DAOs use `@Query` UPSERT methods instead of `@Insert` with `ABORT` or `REPLACE`.
- All Repositories call the UPSERT methods directly, without pre-checking existence.
- Soft-delete semantics remain correct (`sync_status = 2`, `is_deleted = 1`).
- No hard-delete APIs remain in production paths.
- The app builds successfully and passes all relevant tests.

### Step-by-Step Instructions

#### Step 1: Revert previous incorrect changes (if needed)
- Identify all DAO methods that were changed to `@Insert(onConflict = OnConflictStrategy.ABORT)` or similar.
- Identify all Repository methods that were changed to use `findById` + conditional insert/update.
- You may either delete those changes and replace, or overwrite them with the new pattern.

#### Step 2: Implement Conditional UPSERT in each DAO (the optimal way)

For **each** of these DAOs: `TransactionDao.java`, `WalletDao.java`, `CategoryDao.java`, `BudgetDao.java`, `DebtDao.java`, `EventDao.java`

##### Pattern A: Standard entity with `id` primary key (Transaction, Wallet, Category, Debt, Event)
Add a method named `upsertLocal` (or `upsert`) as follows:

```java
@Query("INSERT INTO table_name (all_columns_except_auto) "
     + "VALUES (:all_values) "
     + "ON CONFLICT(id) DO UPDATE SET "
     + "column1 = excluded.column1, "
     + "column2 = excluded.column2, "
     + "updated_at = excluded.updated_at, "
     + "sync_status = CASE WHEN table_name.sync_status = 2 THEN 2 ELSE excluded.sync_status END, "
     + "is_deleted = CASE WHEN table_name.is_deleted = 1 THEN 1 ELSE excluded.is_deleted END, "
     + "created_at = COALESCE(table_name.created_at, excluded.created_at)")
void upsertLocal(Entity entity);
```

**Important:**
- Replace `table_name` with actual table name.
- List all columns except those that should never be overwritten (e.g., `id` is used for conflict, `created_at` is coalesced, `sync_status` and `is_deleted` are conditionally kept).
- Ensure `updated_at` is always overwritten with the new value.

##### Pattern B: BudgetEntity with unique composite key
For `BudgetDao.java`, add:

```java
@Query("INSERT INTO budgets (id, user_id, wallet_id, period, category_id, amount, spent, sync_status, created_at, updated_at) "
     + "VALUES (:id, :userId, :walletId, :period, :categoryId, :amount, :spent, :syncStatus, :createdAt, :updatedAt) "
     + "ON CONFLICT(user_id, wallet_id, period, category_id) DO UPDATE SET "
     + "amount = excluded.amount, "
     + "spent = excluded.spent, "
     + "updated_at = excluded.updated_at, "
     + "sync_status = CASE WHEN budgets.sync_status = 2 THEN 2 ELSE excluded.sync_status END, "
     + "created_at = COALESCE(budgets.created_at, excluded.created_at)")
void upsertLocal(BudgetEntity budget);
```

**Note:** Ensure that the unique index on `(user_id, wallet_id, period, category_id)` exists (create migration if not). If not, add to `BudgetEntity`:

```java
@Indices(value = {@Index(value = {"user_id", "wallet_id", "period", "category_id"}, unique = true)})
```

#### Step 3: Remove any `findById` + conditional insert/update from Repositories

For each Repository (TransactionRepository, WalletRepository, CategoryRepository, BudgetRepository, DebtRepository, EventRepository):

- **Remove** code that checks `findById` before insert/update.
- **Replace** with direct call to `dao.upsertLocal(entity)`.
- Keep all metadata setup (`setUpdatedAt`, `setSyncStatus`, `setCreatedAt` if new) before calling upsert.

Example for TransactionRepository:

```java
public void saveTransaction(TransactionEntity transaction, WriteCallback callback) {
    databaseWriteExecutor.execute(() -> {
        try {
            if (transaction.getId() == null) {
                transaction.setId(UUID.randomUUID().toString());
                transaction.setCreatedAt(System.currentTimeMillis());
            }
            transaction.setUpdatedAt(System.currentTimeMillis());
            transaction.setSyncStatus(SyncStatus.PENDING_UPLOAD);
            transactionDao.upsertLocal(transaction);
            // (Optional) update wallet balance inside a transaction
            callback.onSuccess();
        } catch (Exception e) {
            callback.onError(e);
        }
    });
}
```

Similarly for other repositories.

#### Step 4: Ensure soft-delete methods are correct (Task 3.2)

Soft-delete methods in DAOs should remain as simple updates:

```java
@Query("UPDATE transactions SET is_deleted = 1, sync_status = 2, updated_at = :updatedAt WHERE id = :id")
void softDelete(String id, long updatedAt);
```

Repository calls this directly. **No upsert for soft-delete** – it's a dedicated update.

#### Step 5: Remove hard-delete APIs (Task 3.3)

- Locate any `deleteAllByUser` or similar methods in DAOs.
- Either delete them entirely, or move to a debug-only package and annotate with `@RestrictTo(RestrictTo.Scope.LIBRARY)`.
- Ensure no production code calls them.

#### Step 6: Update ViewModels and Fragments if necessary

- The changes are only in data layer (DAO and Repository). ViewModels and Fragments should remain unchanged because repository method signatures stay the same (they still receive `TransactionEntity` and callback).
- However, verify that no ViewModel directly calls `findById` or old insert methods. If any, update to use the new repository method.

#### Step 7: Build and test

- Run `./gradlew clean build` to ensure no compilation errors.
- Run instrumentation tests (if any) or manually test the following scenarios:
    1. Create new transaction → check `sync_status=1`, `created_at` and `updated_at` set.
    2. Update existing transaction → `updated_at` changes, `sync_status` remains 1, `created_at` unchanged.
    3. Soft-delete a transaction → `is_deleted=1`, `sync_status=2`.
    4. Try to update a soft-deleted transaction → the UPSERT should keep `sync_status=2` (not change to 1). Verify by checking DB after update.
    5. Create a budget with same (user, wallet, period, category) → should update existing, not duplicate.
    6. Perform concurrent updates (optional but recommended) to ensure no race condition.

#### Step 8: Commit changes

Commit with message: `[Phase3] Replace ABORT+findById with optimal conditional UPSERT; ensure soft-delete semantics and remove hard-delete`

#### Definition of Done for Phase 3 (after this rework)

- [ ] All DAOs have `upsertLocal` method with conditional protection for `sync_status`, `is_deleted`, `created_at`.
- [ ] No DAO uses `OnConflictStrategy.REPLACE` or `ABORT` for mutable entities.
- [ ] All repositories use `upsertLocal` without pre-checking `findById`.
- [ ] Soft-delete methods set `sync_status = 2`.
- [ ] No hard-delete APIs accessible from production code.
- [ ] App builds without errors.
- [ ] Manual tests pass (scenarios above).

#### Additional Notes

- If the Room version is lower than 2.4.0, you need to upgrade `build.gradle.kts`:
  ```kotlin
  implementation("androidx.room:room-runtime:2.6.1")
  kapt("androidx.room:room-compiler:2.6.1")
  ```
- For `BudgetEntity`, if the unique index does not exist in the current DB version, create a migration (e.g., 9→10) to add it. But since Phase 1 already upgraded to version 9, you can add the index in the same migration 9→10 (or create 10→11 if needed). The simplest: add the `@Index` annotation to the entity and let Room handle it in the next migration (recommended to create a new migration version 10).


---

## Phase 4 - UI Performance Hardening (NÂNG CẤP P0 - HOÀN HẢO)

> **Mục tiêu:** Loại bỏ hoàn toàn UI thrashing, memory leak, và rớt frame khi DB được ghi dày đặc (AI + Sync). Đảm bảo mọi màn hình hoạt động mượt mà với dataset lớn.

### [X] Task 4.1 - Tạo base class DebounceableViewModel để tái sử dụng debounce logic

**Why:** Nhiều ViewModel (Budget, Statistics, Transaction) có logic rebuild nặng khi DB thay đổi. Debounce giúp gộp nhiều invalidation trong một khoảng thời gian ngắn, tránh UI thrashing.

**Files Affected:**
- `app/src/main/java/com/group10/moneymate/ui/common/DebounceableViewModel.java` (mới)

**Action:**
- Tạo abstract class `DebounceableViewModel` kế thừa `ViewModel`.
- Cung cấp method `debounce(Runnable action, long delayMs)`.
- Tự động hủy pending runnable trong `onCleared()`.

**Code mẫu:**
```java
package com.group10.moneymate.ui.common;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;

public abstract class DebounceableViewModel extends ViewModel {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingRunnable;

    protected void debounce(@NonNull Runnable action, long delayMs) {
        if (pendingRunnable != null) {
            handler.removeCallbacks(pendingRunnable);
        }
        pendingRunnable = action;
        handler.postDelayed(action, delayMs);
    }

    @Override
    protected void onCleared() {
        if (pendingRunnable != null) {
            handler.removeCallbacks(pendingRunnable);
        }
        super.onCleared();
    }
}
```

---

### [X] Task 4.2 - Áp dụng debounce cho BudgetViewModel (thay thế rebuildUiModels trực tiếp)

**Files Affected:**
- `app/src/main/java/com/group10/moneymate/ui/budget/BudgetViewModel.java`

**Action:**
- Cho `BudgetViewModel extends DebounceableViewModel`.
- Thay tất cả các lệnh gọi `rebuildUiModels()` trong các observer (dòng 319, 331, 347, và các nơi khác) bằng `debounce(this::rebuildUiModels, 100L)`.
- Xóa `uiHandler` cũ nếu có.

**Code thay đổi mẫu:**
```java
// Trước:
rebuildUiModels();

// Sau:
debounce(this::rebuildUiModels, 100L);
```

---

### [X] Task 4.3 - Áp dụng debounce cho các ViewModel nặng khác

**Files Affected:**
- `app/src/main/java/com/group10/moneymate/ui/statistics/StatisticsViewModel.java`
- `app/src/main/java/com/group10/moneymate/ui/transaction/TransactionViewModel.java`
- `app/src/main/java/com/group10/moneymate/ui/wallet/WalletViewModel.java` (nếu có rebuild logic)

**Action:**
- Mỗi ViewModel trên `extends DebounceableViewModel`.
- Xác định các method rebuild/recompute nặng và thay bằng `debounce()`.
- Đặc biệt chú ý các observer trên `LiveData` trả về list.

---

### [X] Task 4.4 - Triển khai distinctUntilChanged cho LiveData list

**Why:** Ngăn chặn việc submit cùng một danh sách nhiều lần (khi Room emit cùng dữ liệu do metadata thay đổi nhưng nội dung không đổi).

**Files Affected:**
- `BudgetViewModel.java`
- `StatisticsViewModel.java`
- `TransactionViewModel.java`
- (Các ViewModel khác có LiveData<List<T>>)

**Action:**
- Sử dụng `MediatorLiveData` + `distinctUntilChanged` pattern.
- Hoặc tạo helper method `LiveData.distinctUntilChanged()` (dùng extension function nếu Kotlin, hoặc util class cho Java).

**Code mẫu cho Java (util class):**
```java
public class LiveDataUtils {
    public static <T> LiveData<T> distinctUntilChanged(LiveData<T> source) {
        MediatorLiveData<T> mediator = new MediatorLiveData<>();
        final Object[] lastValue = new Object[1];
        mediator.addSource(source, newValue -> {
            if (!Objects.equals(lastValue[0], newValue)) {
                lastValue[0] = newValue;
                mediator.setValue(newValue);
            }
        });
        return mediator;
    }
}
```

**Sử dụng trong ViewModel:**
```java
public LiveData<List<Transaction>> getTransactions() {
    if (distinctTransactions == null) {
        distinctTransactions = LiveDataUtils.distinctUntilChanged(
            transactionRepository.getAllTransactions()
        );
    }
    return distinctTransactions;
}
```

---

### [X] Task 4.5 - Kiểm tra và sửa toàn bộ `observeForever` (ngăn memory leak)

**Why:** `observeForever` không tự động cleanup, dễ gây leak ViewModel và context. Nhiều chỗ trong codebase đang dùng sai.

**Files Affected (tìm tất cả):**
- `BudgetViewModel.java`
- `StatisticsViewModel.java`
- Bất kỳ ViewModel nào dùng `observeForever`

**Action:**
- Với mỗi `observeForever`, lưu `Observer` instance vào field.
- Ghi đè `onCleared()` và gọi `removeObserver()`.
- Nếu có thể, thay bằng `observe()` với `LifecycleOwner` (Fragment/Activity).

**Code mẫu sửa trong BudgetViewModel:**
```java
// Field lưu observer
private final Observer<List<Budget>> budgetObserver = budgets -> debounce(this::rebuildUiModels, 100L);

// Trong constructor hoặc init:
budgetRepository.getAllBudgets().observeForever(budgetObserver);

// onCleared:
@Override
protected void onCleared() {
    budgetRepository.getAllBudgets().removeObserver(budgetObserver);
    // remove các observer khác tương tự
    super.onCleared();
}
```

**Kiểm tra thêm:** Các `observeForever` ở dòng 323, 335, 351 cũng phải được xử lý tương tự (lưu observer và remove).

---

### [X] Task 4.6 - Hoàn thiện pagination cho TransactionListFragment

**Files Affected:**
- `app/src/main/java/com/group10/moneymate/data/local/dao/TransactionDao.java`
- `app/src/main/java/com/group10/moneymate/data/repository/TransactionRepository.java`
- `app/src/main/java/com/group10/moneymate/ui/transaction/TransactionViewModel.java`
- `app/src/main/java/com/group10/moneymate/ui/transaction/TransactionListFragment.java`
- `app/src/main/java/com/group10/moneymate/ui/transaction/TransactionAdapter.java`

**Action:**

**4.6.1 - DAO thêm phương thức phân trang:**
```java
@Query("SELECT * FROM transactions WHERE user_id = :userId ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
LiveData<List<TransactionEntity>> getTransactionsPaged(String userId, int limit, int offset);

// Hoặc nếu không cần LiveData (dùng load more thủ công):
@Query("SELECT * FROM transactions WHERE user_id = :userId ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
List<TransactionEntity> getTransactionsPagedSync(String userId, int limit, int offset);
```

**4.6.2 - Repository:**
```java
public void getTransactionsPage(String userId, int limit, int offset, DataCallback<List<TransactionEntity>> callback) {
    executor.execute(() -> {
        List<TransactionEntity> page = transactionDao.getTransactionsPagedSync(userId, limit, offset);
        mainHandler.post(() -> callback.onSuccess(page));
    });
}
```

**4.6.3 - ViewModel thêm method loadMore:**
```java
private final MutableLiveData<Boolean> isLoadingMore = new MutableLiveData<>(false);
private final MutableLiveData<Boolean> hasMore = new MutableLiveData<>(true);
private int currentPage = 0;
private static final int PAGE_SIZE = 30;

public void loadNextPage() {
    if (Boolean.TRUE.equals(isLoadingMore.getValue()) || Boolean.FALSE.equals(hasMore.getValue())) return;
    isLoadingMore.setValue(true);
    transactionRepository.getTransactionsPage(userId, PAGE_SIZE, currentPage * PAGE_SIZE, new DataCallback<>() {
        @Override
        public void onSuccess(List<TransactionEntity> page) {
            if (page.isEmpty() || page.size() < PAGE_SIZE) hasMore.setValue(false);
            currentPage++;
            // append to existing list
            List<TransactionEntity> current = transactionsLiveData.getValue();
            List<TransactionEntity> merged = new ArrayList<>();
            if (current != null) merged.addAll(current);
            merged.addAll(page);
            transactionsLiveData.setValue(merged);
            isLoadingMore.setValue(false);
        }
        @Override
        public void onError(Exception e) {
            isLoadingMore.setValue(false);
        }
    });
}
```

**4.6.4 - Fragment lắng nghe scroll và gọi loadNextPage():**
```java
recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
    @Override
    public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
        LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
        int totalItemCount = lm.getItemCount();
        int lastVisible = lm.findLastVisibleItemPosition();
        if (!viewModel.isLoadingMore().getValue() && viewModel.hasMore().getValue() 
                && lastVisible >= totalItemCount - 5) {
            viewModel.loadNextPage();
        }
    }
});
```

**4.6.5 - Adapter hỗ trợ thêm item (addItems):**
```java
public void addItems(List<TransactionEntity> newItems) {
    int startPos = getItemCount();
    items.addAll(newItems);
    notifyItemRangeInserted(startPos, newItems.size());
}
```

---

### [X] Task 4.7 - Thống nhất UTC grouping và loại bỏ 'localtime' trong toàn bộ query

**Files Affected:**
- `TransactionDao.java` (các query dùng STRFTIME)
- `StatisticsViewModel.java`
- `TimeWindowUtils.java` (mới)

**Action:**

**4.7.1 - Tạo TimeWindowUtils.java:**
```java
package com.group10.moneymate.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class TimeWindowUtils {
    private TimeWindowUtils() {}

    public static long startOfDayUtc(long epochMillis) {
        Instant instant = Instant.ofEpochMilli(epochMillis);
        LocalDate date = instant.atZone(ZoneOffset.UTC).toLocalDate();
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    public static long startOfMonthUtc(long epochMillis) {
        Instant instant = Instant.ofEpochMilli(epochMillis);
        LocalDate date = instant.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate firstDay = date.withDayOfMonth(1);
        return firstDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    public static long endOfMonthUtc(long epochMillis) {
        Instant instant = Instant.ofEpochMilli(epochMillis);
        LocalDate date = instant.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate lastDay = date.withDayOfMonth(date.lengthOfMonth());
        return lastDay.atStartOfDay(ZoneOffset.UTC).plusDays(1).minusMillis(1).toInstant().toEpochMilli();
    }

    // Helper hiển thị (chuyển UTC về local time)
    public static String formatDateLocal(long epochUtc, String pattern) {
        LocalDateTime local = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochUtc), ZoneOffset.UTC);
        return local.format(DateTimeFormatter.ofPattern(pattern));
    }
}
```

**4.7.2 - Sửa tất cả query STRFTIME trong TransactionDao:**

**Ví dụ query cũ:**
```sql
STRFTIME('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') as date
```

**Query mới (dùng epoch boundaries trong code, không dùng STRFTIME trong SQL):**
```java
@Query("SELECT * FROM transactions WHERE user_id = :userId AND timestamp >= :startUtc AND timestamp <= :endUtc")
LiveData<List<TransactionEntity>> getTransactionsInRange(String userId, long startUtc, long endUtc);
```

Hoặc nếu cần group theo ngày trong SQL, dùng UTC epoch day:
```sql
CAST(timestamp / 86400000 AS INTEGER) as day_offset
-- rồi group theo day_offset
```

**Khuyến nghị:** Chuyển logic group ra khỏi SQL, thực hiện trong ViewModel/Repository để dễ kiểm soát UTC và hiệu suất.

**4.7.3 - Cập nhật StatisticsViewModel:**
- Các method tính tổng theo ngày/tháng phải dùng `TimeWindowUtils.startOfDayUtc()` và `startOfMonthUtc()` để xác định khoảng thời gian.
- Đảm bảo tất cả query gửi đến DAO đều dùng epoch millis UTC.

---

### [X] Task 4.8 - Tối ưu BudgetListFragment và TransactionListFragment với DiffUtil đúng chuẩn

**Files Affected:**
- `BudgetAdapter.java`
- `TransactionAdapter.java`

**Action:**
- Đảm bảo `DiffUtil.ItemCallback` so sánh đúng các field (bao gồm `id`, `updated_at`, và các field hiển thị).
- Tránh gọi `notifyDataSetChanged()` – chỉ dùng `submitList()` của `ListAdapter`.
- Nếu đang dùng `RecyclerView.Adapter` thông thường, cân nhắc chuyển sang `ListAdapter` có sẵn DiffUtil.

**Code mẫu cho TransactionAdapter (nếu chưa dùng ListAdapter):**
```java
public class TransactionAdapter extends ListAdapter<TransactionEntity, TransactionAdapter.ViewHolder> {
    protected TransactionAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<TransactionEntity> DIFF_CALLBACK =
        new DiffUtil.ItemCallback<TransactionEntity>() {
            @Override
            public boolean areItemsTheSame(@NonNull TransactionEntity oldItem, @NonNull TransactionEntity newItem) {
                return oldItem.getId().equals(newItem.getId());
            }
            @Override
            public boolean areContentsTheSame(@NonNull TransactionEntity oldItem, @NonNull TransactionEntity newItem) {
                return oldItem.getUpdatedAt() == newItem.getUpdatedAt()
                    && oldItem.getAmount() == newItem.getAmount()
                    && Objects.equals(oldItem.getNote(), newItem.getNote());
            }
        };
}
```

---

### [X] Task 4.9 (Optional P2) - Thêm test UI performance cơ bản

**Files Affected:**
- `app/src/androidTest/java/com/group10/moneymate/ui/performance/TransactionListPerformanceTest.java` (mới)

**Action:**
- Sử dụng `RecyclerViewScrollHelper` để scroll nhanh và kiểm tra không crash, không rớt frame quá mức.
- Tạo dữ liệu ảo 1000+ transactions trong test.

**Code mẫu:**
```java
@Test
public void scrollLargeList_noJank() {
    onView(withId(R.id.recyclerView)).perform(RecyclerViewActions.scrollToPosition(500));
    // Kiểm tra không có ANR hoặc exception
}
```

---

### ✅ Definition of Done cho Phase 4 (hoàn hảo)

- [X] `DebounceableViewModel` được tạo và sử dụng trong BudgetViewModel, StatisticsViewModel, TransactionViewModel.
- [X] Tất cả `observeForever` đã được kiểm tra, lưu observer và remove trong `onCleared()`.
- [X] `distinctUntilChanged` được áp dụng cho tất cả LiveData list trả về từ repository.
- [X] Pagination hoạt động trên TransactionListFragment: load 500+ giao dịch, scroll mượt, không duplicate, có loading indicator.
- [X] Tất cả query thống kê đã chuyển sang UTC boundaries, không còn `STRFTIME(..., 'localtime')`.
- [X] `TimeWindowUtils` có sẵn và được dùng trong toàn bộ logic thời gian.
- [X] BudgetAdapter và TransactionAdapter dùng `ListAdapter` + DiffUtil, không còn `notifyDataSetChanged()` tùy tiện.
- [X] Thử nghiệm thủ công: bật AI/Sync giả lập (ghi DB liên tục), UI không bị rớt frame, không memory leak.
- [X] (Optional) Performance test trên thiết bị low-end đạt yêu cầu.

### Futhur considerations
#### 1. Pagination strategy: LIMIT/OFFSET vs Cursor/Keyset paging

##### Kết luận: **Dùng keyset/cursor paging (còn gọi là "seek method") cho TransactionListFragment, giữ LIMIT/OFFSET cho các trường hợp ít thay đổi.**

##### Lý do:

| Tiêu chí | LIMIT/OFFSET | Keyset (WHERE timestamp < lastTimestamp) |
|----------|--------------|-------------------------------------------|
| Hiệu năng trên dataset lớn | Kém (OFFSET càng lớn càng chậm) | Tốt (chỉ quét từ mốc) |
| Ổn định khi có insert/delete trong lúc scroll | **Không ổn định** – bị trùng/sót item | Ổn định (nếu sắp xếp theo timestamp+id) |
| Độ phức tạp triển khai | Thấp | Trung bình (cần lưu last value) |
| Hỗ trợ refresh/load page đầu tiên | Dễ | Dễ |

Với MoneyMate, **transaction list là nơi người dùng thường xuyên scroll sâu**, và **dữ liệu thay đổi liên tục** (thêm giao dịch mới, sync từ cloud). Dùng OFFSET sẽ gây hiện tượng nhảy item hoặc bỏ sót khi có insert vào đầu danh sách.

##### Giải pháp keyset cụ thể:

**Thay vì:**
```sql
LIMIT 30 OFFSET 90
```

**Dùng:**
```sql
SELECT * FROM transactions 
WHERE user_id = :userId 
AND (timestamp < :lastTimestamp OR (timestamp = :lastTimestamp AND id < :lastId))
ORDER BY timestamp DESC, id DESC
LIMIT :limit
```

**Trong Repository, lưu last values:**
```java
private long lastTimestamp = Long.MAX_VALUE;
private String lastId = "";

public void loadNextPage(LoadCallback callback) {
    List<Transaction> page = dao.getTransactionsAfter(userId, lastTimestamp, lastId, PAGE_SIZE);
    if (!page.isEmpty()) {
        lastTimestamp = page.get(page.size() - 1).getTimestamp();
        lastId = page.get(page.size() - 1).getId();
    }
    callback.onSuccess(page);
}
```

**Khi refresh (kéo xuống load mới):** reset `lastTimestamp = Long.MAX_VALUE`, `lastId = ""`, và gọi lại load page đầu.

**Khuyến nghị:** Task 4.6 trong Phase 4 nên được **nâng cấp lên keyset paging** thay vì LIMIT/OFFSET. Độ phức tạp tăng không đáng kể, nhưng mang lại sự ổn định cao.


#### 2. distinctUntilChanged semantics: Objects.equals vs fingerprinting

##### Kết luận: **Giữ `Objects.equals` cho toàn bộ object là đủ tốt và an toàn. Không cần fingerprinting.**

##### Lý do:

- `Objects.equals(listA, listB)` so sánh **nội dung từng phần tử** (gọi `equals()` của từng entity). Nếu bạn đã implement `equals()` dựa trên `id` + `updated_at` (hoặc toàn bộ field quan trọng), thì đây là cách đúng.
- Fingerprinting (tạo hash từ id+updated_at) có thể nhanh hơn một chút với list rất lớn (hàng nghìn item), nhưng gây phức tạp hóa, dễ sai (quên cập nhật fingerprint khi thay đổi).
- Trong thực tế, list transaction thường không quá 500-1000 item. `Objects.equals` là đủ nhanh.

##### Tuy nhiên, cần đảm bảo:

**Các entity (TransactionEntity, BudgetEntity, ...) phải override `equals()` và `hashCode()` đúng cách:**

```java
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TransactionEntity that = (TransactionEntity) o;
    return Objects.equals(id, that.id)
        && updatedAt == that.updatedAt;
}

@Override
public int hashCode() {
    return Objects.hash(id, updatedAt);
}
```

**Nếu chưa có, hãy thêm vào tất cả entity trong Phase 1 hoặc Phase 4 như một task nhỏ.**

##### Vậy cập nhật Task 4.4:

- Giữ `DistinctLiveData` dùng `Objects.equals`.
- **Bổ sung Task 4.4.1:** Đảm bảo mọi entity đã override `equals()` và `hashCode()` dựa trên `id` + `updated_at`.


#### 3. UTC scope boundary: Chỉ DAO/query windows hay toàn bộ UI?

##### Kết luận: **Phải áp dụng UTC xuyên suốt từ DAO đến UI date-pickers và báo cáo, nhưng hiển thị thì convert về local.**

##### Cụ thể:

| Layer | Policy |
|-------|--------|
| **Database storage** | Lưu epoch millis UTC (đã có) |
| **DAO query** | Dùng UTC boundaries (startOfDayUtc, endOfDayUtc) – không dùng `localtime` |
| **Repository / ViewModel** | Giữ nguyên UTC, không chuyển đổi |
| **UI date-picker (người dùng chọn ngày)** | Chuyển local date → UTC start/end trước khi gửi xuống repository |
| **UI hiển thị (TextView hiển thị ngày/tháng)** | Chuyển UTC epoch → local date để hiển thị thân thiện |

##### Những file cần sửa (ngoài TransactionDao và StatisticsViewModel):

- `StatisticsOverviewFragment` – khi người dùng chọn ngày trong `DatePicker`, gọi `TimeWindowUtils.startOfDayUtc(localEpoch)`.
- `IncomeExpenseDetailFragment` – tương tự.
- `CategoryReportFragment` – tương tự.
- `BudgetListFragment` – nếu có lọc theo tháng, cần dùng UTC boundaries.
- `AddEditTransactionFragment` – khi lưu giao dịch, `timestamp` lưu là UTC epoch (đã đúng, nhưng cần đảm bảo nếu người dùng chọn ngày giờ local thì convert sang UTC).

##### Thêm một helper trong `TimeWindowUtils`:

```java
public static long startOfDayLocalToUtc(long localEpochMillis, TimeZone timeZone) {
    // Chuyển local epoch sang UTC boundary
    Calendar cal = Calendar.getInstance(timeZone);
    cal.setTimeInMillis(localEpochMillis);
    cal.set(Calendar.HOUR_OF_DAY, 0);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    return cal.getTimeInMillis();
}
```

Nhưng khuyến khích dùng `java.time` nếu min API >= 26, hoặc ThreeTenABP.

##### Vậy cần mở rộng Task 4.7:

- Không chỉ sửa DAO query, mà còn **rà soát tất cả fragment có date picker hoặc hiển thị báo cáo thời gian**, đảm bảo dùng `TimeWindowUtils` để chuyển đổi.


#### Tổng hợp các bổ sung cần cập nhật vào Phase 4 (hoặc Phase riêng)

| Hạng mục | Quyết định | Hành động |
|----------|-----------|-----------|
| Pagination | Dùng **keyset paging** thay vì LIMIT/OFFSET | Sửa Task 4.6 trong plan |
| distinctUntilChanged | Giữ `Objects.equals`, thêm override equals/hashCode cho entity | Bổ sung Task 4.4.1 |
| UTC scope | Toàn bộ UI date-picker và báo cáo | Mở rộng Task 4.7, rà soát thêm các fragment |


---

## Phase 5 - Durability & Background Tasks (WorkManager) – BẮT BUỘC MỚI (ĐÃ TỐI ƯU)

> **Nguyên tắc:** WorkManager chỉ dùng cho tác vụ dài, không cần phản hồi ngay lập tức (sync, AI xử lý ảnh, chat).  
> **Local writes (insert/update/delete)** vẫn dùng `databaseWriteExecutor` để có callback và hiệu năng cao.

### Mục tiêu
- Đảm bảo các tác vụ nền trọng yếu (sync, AI) không bị mất khi app bị kill.
- Có cơ chế retry, checkpoint, và thông báo lỗi.
- Không ảnh hưởng đến trải nghiệm người dùng (local writes vẫn nhanh).

---

### [X] Task 5.1 - Tạo WorkerFactory để inject repository vào Worker

**Why:** Worker không thể dùng constructor injection trực tiếp. Cần `WorkerFactory` để cung cấp dependency từ `AppContainer`.

**Files Affected:**
- `app/src/main/java/com/group10/moneymate/di/MoneyMateWorkerFactory.java` (mới)
- `app/src/main/java/com/group10/moneymate/di/MoneyMateApplication.java` (sửa)
- `app/src/main/java/com/group10/moneymate/di/AppContainer.java` (thêm getters nếu cần)

**Action:**

**5.1.1 - Tạo MoneyMateWorkerFactory:**
```java
package com.group10.moneymate.di;

import android.content.Context;
import androidx.work.ListenableWorker;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;
import com.group10.moneymate.data.repository.TransactionRepository;
import com.group10.moneymate.data.repository.BudgetRepository;
import com.group10.moneymate.data.repository.SyncMetadataRepository; // sẽ tạo
import com.group10.moneymate.workers.SyncWorker;
import com.group10.moneymate.workers.AIReceiptScannerWorker;

public class MoneyMateWorkerFactory extends WorkerFactory {
    private final AppContainer appContainer;

    public MoneyMateWorkerFactory(AppContainer appContainer) {
        this.appContainer = appContainer;
    }

    @Override
    public ListenableWorker createWorker(
            @NonNull Context context,
            @NonNull String workerClassName,
            @NonNull WorkerParameters workerParameters) {
        
        if (workerClassName.equals(SyncWorker.class.getName())) {
            return new SyncWorker(
                context,
                workerParameters,
                appContainer.getTransactionRepository(),
                appContainer.getBudgetRepository(),
                appContainer.getSyncMetadataRepository()
            );
        } else if (workerClassName.equals(AIReceiptScannerWorker.class.getName())) {
            return new AIReceiptScannerWorker(
                context,
                workerParameters,
                appContainer.getTransactionRepository(),
                appContainer.getCategoryRepository()
            );
        }
        // fallback
        return null;
    }
}
```

**5.1.2 - Cấu hình WorkManager trong MoneyMateApplication:**
```java
@Override
public void onCreate() {
    super.onCreate();
    appContainer = new AppContainer(this);
    appContainer.bootstrapLocalData();

    Configuration config = new Configuration.Builder()
        .setWorkerFactory(new MoneyMateWorkerFactory(appContainer))
        .build();
    WorkManager.initialize(this, config);
}
```


### [X] Task 5.2 - Tạo bảng sync_metadata và SyncMetadataRepository

**Why:** Để lưu checkpoint cho mỗi bảng, tránh upload lại dữ liệu cũ khi worker bị gián đoạn.

**Files Affected:**
- `app/src/main/java/com/group10/moneymate/data/local/entity/SyncMetadataEntity.java` (mới)
- `app/src/main/java/com/group10/moneymate/data/local/dao/SyncMetadataDao.java` (mới)
- `app/src/main/java/com/group10/moneymate/data/repository/SyncMetadataRepository.java` (mới)
- `app/src/main/java/com/group10/moneymate/data/local/AppDatabase.java` (sửa)
- Migration mới: `Migration10To11.java`

**Action:**

**5.2.1 - Entity:**
```java
@Entity(tableName = "sync_metadata")
public class SyncMetadataEntity {
    @PrimaryKey
    @NonNull
    public String tableName;          // "transactions", "budgets", ...
    public long lastSyncTimestamp;    // max updated_at đã upload
    public String lastSyncId;         // id cuối cùng (để tránh trùng)
}
```

**5.2.2 - DAO:**
```java
@Dao
public interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata WHERE tableName = :tableName")
    SyncMetadataEntity getByTableName(String tableName);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(SyncMetadataEntity entity);

    @Query("UPDATE sync_metadata SET lastSyncTimestamp = :timestamp, lastSyncId = :id WHERE tableName = :tableName")
    void updateLastSync(String tableName, long timestamp, String id);
}
```

**5.2.3 - Migration 10 → 11:**
```java
static final Migration MIGRATION_10_11 = new Migration(10, 11) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_metadata (" +
                   "tableName TEXT PRIMARY KEY NOT NULL, " +
                   "lastSyncTimestamp INTEGER NOT NULL DEFAULT 0, " +
                   "lastSyncId TEXT NOT NULL DEFAULT '')");
    }
};
```

**5.2.4 - Repository:**
```java
public class SyncMetadataRepository {
    private final SyncMetadataDao dao;
    public SyncMetadataRepository(SyncMetadataDao dao) { this.dao = dao; }
    public long getLastSyncTimestamp(String tableName) {
        SyncMetadataEntity e = dao.getByTableName(tableName);
        return e == null ? 0 : e.lastSyncTimestamp;
    }
    public void updateLastSync(String tableName, long timestamp, String id) {
        dao.updateLastSync(tableName, timestamp, id);
    }
}
```



### [X] Task 5.3 - Tạo SyncWorker (durable sync with checkpoint & retry)

**Files Affected:**
- `app/src/main/java/com/group10/moneymate/workers/SyncWorker.java` (mới)
- `TransactionRepository.java`, `BudgetRepository.java` (thêm method `getPendingSyncSince`)

**Action:**

**5.3.1 - Thêm method trong TransactionRepository (tương tự BudgetRepository):**
```java
public List<TransactionEntity> getPendingSyncSince(long sinceTimestamp) {
    return transactionDao.getPendingSyncSince(sinceTimestamp);
}
// TransactionDao:
@Query("SELECT * FROM transactions WHERE (sync_status = 1 OR sync_status = 2) AND updated_at > :since ORDER BY updated_at ASC")
List<TransactionEntity> getPendingSyncSince(long since);
```

**5.3.2 - SyncWorker hoàn chỉnh:**
```java
public class SyncWorker extends Worker {
    private final TransactionRepository transactionRepo;
    private final BudgetRepository budgetRepo;
    private final SyncMetadataRepository metadataRepo;
    private static final int MAX_RETRIES = 3;

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params,
                      TransactionRepository transactionRepo,
                      BudgetRepository budgetRepo,
                      SyncMetadataRepository metadataRepo) {
        super(context, params);
        this.transactionRepo = transactionRepo;
        this.budgetRepo = budgetRepo;
        this.metadataRepo = metadataRepo;
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            // Lấy checkpoint
            long lastTx = metadataRepo.getLastSyncTimestamp("transactions");
            long lastBudget = metadataRepo.getLastSyncTimestamp("budgets");

            List<TransactionEntity> pendingTx = transactionRepo.getPendingSyncSince(lastTx);
            List<BudgetEntity> pendingBudget = budgetRepo.getPendingSyncSince(lastBudget);

            boolean hasError = false;

            if (!pendingTx.isEmpty()) {
                boolean ok = uploadTransactions(pendingTx);
                if (ok) {
                    long newTimestamp = pendingTx.get(pendingTx.size()-1).getUpdatedAt();
                    String lastId = pendingTx.get(pendingTx.size()-1).getId();
                    metadataRepo.updateLastSync("transactions", newTimestamp, lastId);
                } else {
                    hasError = true;
                }
            }

            if (!pendingBudget.isEmpty()) {
                boolean ok = uploadBudgets(pendingBudget);
                if (ok) {
                    long newTimestamp = pendingBudget.get(pendingBudget.size()-1).getUpdatedAt();
                    String lastId = pendingBudget.get(pendingBudget.size()-1).getId();
                    metadataRepo.updateLastSync("budgets", newTimestamp, lastId);
                } else {
                    hasError = true;
                }
            }

            if (hasError) {
                int retryCount = getRunAttemptCount(); // WorkManager cung cấp
                if (retryCount >= MAX_RETRIES) {
                    showSyncErrorNotification();
                    return Result.failure();
                }
                return Result.retry();
            }
            return Result.success();
        } catch (Exception e) {
            if (getRunAttemptCount() >= MAX_RETRIES) {
                showSyncErrorNotification();
                return Result.failure();
            }
            return Result.retry();
        }
    }

    private void showSyncErrorNotification() {
        // Dùng NotificationHelper
    }

    private boolean uploadTransactions(List<TransactionEntity> list) {
        // Gọi API, trả về true nếu thành công
    }
    // ... tương tự
}
```

**5.3.3 - Enqueue sync từ repository (gọi khi cần, ví dụ sau mỗi local write):**
```java
Constraints constraints = new Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build();

OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(SyncWorker.class)
    .setConstraints(constraints)
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
    .addTag("sync_worker")
    .build();

WorkManager.getInstance(context).enqueueUniqueWork(
    "critical_sync",
    ExistingWorkPolicy.KEEP,
    syncRequest
);
```



### [X] Task 5.4 - Tạo AIReceiptScannerWorker (xử lý ảnh nền)

**Files Affected:**
- `app/src/main/java/com/group10/moneymate/workers/AIReceiptScannerWorker.java` (mới)
- `TransactionRepository`, `CategoryRepository`

**Action:**
- Worker nhận `imageUri` (dạng string), chạy ML Kit text recognition, parse thành transaction, gọi repository lưu.
- **Không cần network** – chỉ dùng CPU.
- Retry tối đa 2 lần (nếu lỗi parse thì bỏ qua, không retry).

```java
public class AIReceiptScannerWorker extends Worker {
    private final TransactionRepository transactionRepo;
    private final CategoryRepository categoryRepo;

    public AIReceiptScannerWorker(...) { ... }

    @NonNull
    @Override
    public Result doWork() {
        String imageUri = getInputData().getString("image_uri");
        try {
            String text = runTextRecognition(imageUri);
            TransactionEntity tx = parseTextToTransaction(text);
            if (tx != null) {
                transactionRepo.saveTransaction(tx, new WriteCallback() {
                    @Override public void onSuccess() { /* no UI needed */ }
                    @Override public void onError(Exception e) { /* log */ }
                });
                return Result.success();
            } else {
                return Result.failure(); // không retry
            }
        } catch (Exception e) {
            if (getRunAttemptCount() >= 2) return Result.failure();
            return Result.retry();
        }
    }
}
```

**Enqueue từ UI:**
```java
Data inputData = new Data.Builder().putString("image_uri", uri.toString()).build();
OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(AIReceiptScannerWorker.class)
    .setInputData(inputData)
    .build();
WorkManager.getInstance(context).enqueue(request);
```



### [X] Task 5.5 - Giữ nguyên executor cho local writes (không thay đổi)

**Files:** `TransactionRepository.java`, `BudgetRepository.java`, `DebtRepository.java`, `EventRepository.java`

**Action:** **Không sửa gì thêm.** Các method insert/update/delete vẫn dùng `databaseWriteExecutor.execute()` để có callback và hiệu năng cao. WorkManager **không thay thế** executor.

**Chỉ thêm method `getPendingSyncSince` nếu cần cho SyncWorker.**



### [X] Task 5.6 - Thêm notification khi sync thất bại (Task 5.3 đã có)

**Files:** `NotificationHelper.java` (mới hoặc sẵn có)

**Action:** Đảm bảo `showSyncErrorNotification()` hiển thị notification với action "Thử lại" (trigger sync mới).



### ✅ Definition of Done cho Phase 5

- [X] `MoneyMateWorkerFactory` được tạo và đăng ký trong Application.
- [X] Bảng `sync_metadata` và migration 10→11 được thêm.
- [X] `SyncWorker` hoạt động: lấy checkpoint, upload từng bảng, cập nhật checkpoint, retry tối đa 3 lần.
- [X] `AIReceiptScannerWorker` hoạt động (có thể là placeholder parse cơ bản).
- [X] Các local writes **vẫn dùng executor** – không chuyển sang WorkManager.
- [X] Notification hiển thị khi sync thất bại sau 3 lần.
- [X] Build thành công, không lỗi dependency.
- [X] Test durability: tạo transaction, kill app, mở lại → sync vẫn chạy (kiểm tra log).
- [X] Test AI receipt: chọn ảnh, app không crash, worker chạy nền.


**Lưu ý:** Không thay đổi logic local writes. Phase 5 chỉ thêm các worker mới và infrastructure cho sync/AI nền.

### Further Considerations – Đã chốt

Dựa trên phân tích hiện trạng và đề xuất của bạn, tôi xác nhận các quyết định sau:

1. **Sync scope v1:** ✅ Chỉ `transactions` và `budgets`. Các bảng khác để phase sau.

2. **Checkpoint key:** ✅ Dùng composite `(user_id, domain)` với domain là string `"transactions"`, `"budgets"`.

3. **UniqueWork policy cho one-time sync:** ✅ `ExistingWorkPolicy.KEEP` – không tạo mới nếu đã có sync đang chạy.

4. **Periodic sync interval:** ✅ **1 giờ** – tiết kiệm pin và data. User có thể manual sync qua pull-to-refresh hoặc notification.

5. **Retry policy:** ✅ Max retry = **3**, backoff exponential bắt đầu **30s** (30s, 1m, 2m). Sau 3 lần → failure + notification.

6. **Xử lý PENDING_DELETE sau sync thành công:** ✅ **Hard delete local** sau khi remote confirm thành công. Đảm bảo idempotent.

7. **Notification fail sync:** ✅ **Chỉ khi app ở background** (dùng `ProcessLifecycleOwner` để kiểm tra foreground). Nếu foreground, dùng Snackbar.

8. **AIReceiptScannerWorker v1:** ✅ **Scaffolding + validation** – chưa OCR thực. Nhận image_uri, validate, log, trả về success giả. Dễ thay thế sau.

9. **Trigger enqueue sync:** ✅ **Cả hai**:
    - One-time sync sau mỗi local write (có debounce 5 giây để gộp).
    - Periodic sync 1 giờ.
    - Manual retry từ notification.
    - Dùng `KEEP` để tránh trùng lặp.

---
## Phase 6 - Indexing & Pagination Optimization (BẮT BUỘC MỚI, ĐÃ TỐI ƯU)

> **Mục tiêu:** Tối ưu hiệu năng truy vấn bằng index (hot path sync, list queries) và pagination keyset, đảm bảo migration nhất quán với các phase trước.

### [X] Task 6.1 - Xác định và đồng bộ version DB

**Files:** `AppDatabase.java`, tất cả migration classes.

**Action:**
- Kiểm tra `AppDatabase.version` hiện tại. Giả sử là 11.
- Đặt `version = 12` cho Phase 6.
- Tạo migration `Migration11To12.java` (hoặc tên tương ứng) chứa tất cả index và constraint.

---

### [X] Task 6.2 - Thêm index cho hot path sync và query (các entity)

**Files:**
- `TransactionEntity.java`
- `BudgetEntity.java`
- `WalletEntity.java`
- `Migration11To12.java`

**Action:**

**6.2.1 - TransactionEntity:**
```java
@Index(value = {"user_id", "sync_status", "updated_at"})
@Index(value = {"user_id", "is_deleted", "timestamp"})
@Index(value = {"wallet_id", "is_deleted", "type", "timestamp"})
```

**6.2.2 - BudgetEntity:**
```java
@Index(value = {"user_id", "sync_status", "updated_at"})
// Unique constraint đã được thêm trong Phase 3, nhưng nếu chưa có thì thêm:
@Index(value = {"user_id", "wallet_id", "period", "category_id"}, unique = true)
```

**6.2.3 - WalletEntity:**
```java
@Index(value = {"user_id", "sync_status", "updated_at"})
```

**6.2.4 - Migration SQL (Migration11To12.java):**
```sql
-- Transaction indexes
CREATE INDEX IF NOT EXISTS idx_transactions_user_sync_updated ON transactions(user_id, sync_status, updated_at);
CREATE INDEX IF NOT EXISTS idx_transactions_user_deleted_timestamp ON transactions(user_id, is_deleted, timestamp);
CREATE INDEX IF NOT EXISTS idx_transactions_wallet_deleted_type_timestamp ON transactions(wallet_id, is_deleted, type, timestamp);

-- Budget indexes
CREATE INDEX IF NOT EXISTS idx_budgets_user_sync_updated ON budgets(user_id, sync_status, updated_at);
CREATE UNIQUE INDEX IF NOT EXISTS idx_budgets_unique ON budgets(user_id, wallet_id, period, category_id);

-- Wallet index
CREATE INDEX IF NOT EXISTS idx_wallets_user_sync_updated ON wallets(user_id, sync_status, updated_at);
```


### [X] Task 6.3 - Áp dụng keyset pagination (thay thế LIMIT/OFFSET)

**Files:**
- `TransactionDao.java`
- `TransactionRepository.java`
- `TransactionListFragment.java`
- `TransactionAdapter.java` (nếu cần thêm method `addItems`)

**Action:**

**6.3.1 - DAO method keyset (thêm vào TransactionDao.java):**
```java
@Query("SELECT * FROM transactions " +
       "WHERE user_id = :userId " +
       "AND (timestamp < :lastTimestamp OR (timestamp = :lastTimestamp AND id < :lastId)) " +
       "ORDER BY timestamp DESC, id DESC " +
       "LIMIT :limit")
List<TransactionEntity> getTransactionsPageKeyset(String userId, long lastTimestamp, String lastId, int limit);

@Query("SELECT * FROM transactions WHERE user_id = :userId ORDER BY timestamp DESC, id DESC LIMIT :limit")
List<TransactionEntity> getFirstTransactionPage(String userId, int limit);
```

**6.3.2 - Repository quản lý cursor (TransactionRepository.java):**
```java
private long lastTimestamp = Long.MAX_VALUE;
private String lastId = "";
private static final int PAGE_SIZE = 30;

public void resetPagination() {
    lastTimestamp = Long.MAX_VALUE;
    lastId = "";
}

public void loadFirstPage(DataCallback<List<TransactionEntity>> callback) {
    resetPagination();
    loadNextPage(callback);
}

public void loadNextPage(DataCallback<List<TransactionEntity>> callback) {
    executor.execute(() -> {
        List<TransactionEntity> page;
        if (lastTimestamp == Long.MAX_VALUE && lastId.isEmpty()) {
            page = transactionDao.getFirstTransactionPage(userId, PAGE_SIZE);
        } else {
            page = transactionDao.getTransactionsPageKeyset(userId, lastTimestamp, lastId, PAGE_SIZE);
        }
        if (!page.isEmpty()) {
            lastTimestamp = page.get(page.size() - 1).getTimestamp();
            lastId = page.get(page.size() - 1).getId();
        }
        mainHandler.post(() -> callback.onSuccess(page));
    });
}
```

**6.3.3 - Fragment sử dụng (TransactionListFragment.java):**
- Dùng `OnScrollListener` tương tự Phase 4, gọi `viewModel.loadNextPage()`.
- Adapter cần có `addItems(List<TransactionEntity>)` để nối thêm dữ liệu.


### [X] Task 6.4 - Verify index bằng EXPLAIN QUERY PLAN

**Files:** (Test class hoặc hướng dẫn thủ công)

**Action:**
- Viết một test hoặc hướng dẫn kiểm tra:
```sql
EXPLAIN QUERY PLAN SELECT * FROM transactions WHERE user_id = 'some_id' AND sync_status = 1 AND updated_at > 1000;
```
- Kết quả phải hiển thị `SEARCH TABLE transactions USING INDEX idx_transactions_user_sync_updated`.
- Tương tự kiểm tra các query còn lại.

### [X] Task 6.5 - Migration 11→12 hoàn chỉnh

**Files:** `Migration11To12.java`, `AppDatabase.java`

**Action:**
- Đăng ký migration trong `AppDatabase.addMigrations(...)`.
- Migration phải chạy được trên database cũ (version 11) mà không mất dữ liệu.
- Sau migration, version DB là 12.

**Code mẫu Migration11To12:**
```java
static final Migration MIGRATION_11_12 = new Migration(11, 12) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
        // Indexes
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_user_sync_updated ON transactions(user_id, sync_status, updated_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_user_deleted_timestamp ON transactions(user_id, is_deleted, timestamp)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_transactions_wallet_deleted_type_timestamp ON transactions(wallet_id, is_deleted, type, timestamp)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_budgets_user_sync_updated ON budgets(user_id, sync_status, updated_at)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_budgets_unique ON budgets(user_id, wallet_id, period, category_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_wallets_user_sync_updated ON wallets(user_id, sync_status, updated_at)");
    }
};
```

### ✅ Definition of Done cho Phase 6

- [X] DB version được nâng lên 12, migration 11→12 thành công.
- [X] Các index đã được thêm vào entity và migration.
- [X] `TransactionListFragment` dùng **keyset pagination**, load mượt, không bị trùng/sót dữ liệu khi có insert trong lúc scroll.
- [X] `EXPLAIN QUERY PLAN` xác nhận các query hot đều sử dụng index.
- [X] Budget unique constraint đã tồn tại (nếu chưa, đã thêm).
- [X] App build thành công, test thủ công với 5000+ transactions, scroll không ANR, không OOM.

**Lưu ý:** Phase 6 không xử lý timezone nữa (đã chuyển sang Phase 4). Tên phase phản ánh đúng nội dung: Indexing & Pagination.

### Further Considerations – Đã chốt + Bổ sung hoàn thiện (Phase 6)
#### 1. Index strategy – chốt chỉ 3 nhóm, nhưng làm rõ sync index

- ✅ **Ledger keyset index:** `transactions(user_id, is_deleted, timestamp DESC, id DESC)`
- ✅ **Sync checkpoint index (transactions):** Thay vì `(user_id, updated_at ASC, id ASC)`, yêu cầu **dùng index `(user_id, sync_status, updated_at, id)`** và sửa query sync thành `sync_status IN (1,2)` thay vì `!=0`.  
  → Lý do: index sẽ được sử dụng triệt để. Agent phải viết test `EXPLAIN QUERY PLAN` để xác nhận.
- ✅ **Sync checkpoint index (budgets):** Tương tự `budgets(user_id, sync_status, updated_at, id)`
- ✅ **Wallet index:** `wallets(user_id, sync_status, updated_at)` – thêm luôn.

#### 2. Pagination hardening – bổ sung các thành phần còn thiếu

- ✅ **Page size:** giữ **30**.
- ✅ **First-page API:** thêm `getFirstTransactionPage` rõ ràng.
- ✅ **Load-more trigger:** chỉ giữ `RecyclerView.OnScrollListener`, bỏ `NestedScrollView`.
- ✅ **Thêm flag `hasMore`** vào ViewModel. Khi `page.size() < PAGE_SIZE`, set `hasMore = false`. Fragment ngừng gọi `loadNextPage`.
- ✅ **Reset pagination khi filter thay đổi:** dù chưa có filter UI, **thêm ngay method `applyFilter(FilterParams)` trong ViewModel** (hoặc `resetPaginationForNewFilter()`), bên trong gọi `resetPagination()`. Để sau này không bỏ sót.
- ✅ **Test concurrent insert trong lúc scroll:** yêu cầu agent mô tả test cụ thể: background thread insert mỗi 0.5s, scroll liên tục, kiểm tra không duplicate/skipped item.

#### 3. Migration và rủi ro ANR

- ✅ Migration `11→12` dùng `CREATE INDEX IF NOT EXISTS` trong một transaction.
- ✅ Với bảng lớn (ước lượng >10k rows), index creation có thể mất vài giây trên máy yếu. **Chấp nhận rủi ro** nhưng yêu cầu agent:
    - Ghi rõ trong release note: "Phiên bản này sẽ tối ưu database, lần đầu khởi động có thể hơi lâu hơn bình thường."
    - Không dùng `fallbackToDestructiveMigration()`.

#### 4. Verification (EXPLAIN + smoke test)

- ✅ **Tự động instrumentation test** cho `EXPLAIN QUERY PLAN`:
    - Kiểm tra ledger query dùng index `transactions(user_id, is_deleted, timestamp DESC, id DESC)`
    - Kiểm tra sync pending query dùng index `transactions(user_id, sync_status, updated_at, id)`
- ✅ **Smoke test pagination với dataset 5000 rows**:
    - Scroll từ đầu đến cuối, ghi log số lần load.
    - Trong lúc scroll, chạy background insert 100 transactions mới.
    - Kết thúc, kiểm tra tổng số item hiển thị = 5000 + 100, không bị trùng/sót.

#### 5. Phạm vi – chỉ TransactionListFragment, giữ nguyên local writes

- ✅ Không mở rộng sang Budget/Debt list.
- ✅ Tuyệt đối không thay đổi write flows (insert/update/delete vẫn dùng executor + callback).

#### 6. Các yêu cầu bổ sung (bắt buộc) đối với agent trước khi code

Trước khi bắt đầu implement, agent phải **cập nhật revised implementation plan** bao gồm:
- Chi tiết index `(user_id, sync_status, updated_at, id)` và sửa query sync thành `sync_status IN (1,2)`.
- Cách thêm `hasMore` flag và `applyFilter` stub.
- Mô tả test concurrent insert.
- Cảnh báo ANR trong release note.


---

## Phase 7 - Architecture Rules (Gate trước AI/Sync)

### [ ] Task 7.1 - Ban hành "Mandatory Rules" ở cấp team
**Files:** `docs/audits/concurrency-sync-audit-action-plan.md`, `AGENT.md`, PR template

**Action:**
- Checklist bắt buộc trong PR:
  1. Cấm `REPLACE` cho entity mutable offline-first.
  2. Cấm hard-delete trong production flow.
  3. Mọi multi-step write phải atomic (`@Transaction`/`runInTransaction`).
  4. Mọi record mới/sửa phải cập nhật `updated_at` UTC + `sync_status` hợp lệ.
  5. Cấm gọi `observeForever` fan-out nếu không có coalesce.
  6. **Critical write bắt buộc dùng WorkManager, không dùng executor thô.**
  7. **Query list lớn bắt buộc có pagination.**
  8. **Mọi query mới phải được giải thích index đã dùng.**

### [ ] Task 7.2 - Rule cho phase AI/Sync kế tiếp
**Files:** File này

**Action:**
- Định nghĩa trước các nguyên tắc cho phase sau:
  - DB write quan trọng không dùng executor thô.
  - Dữ liệu lớn phải chunk/paginate.
  - Query thống kê nặng phải có index chứng minh trước khi merge.
  - AI pipeline phải dùng WorkManager, không gọi API từ UI thread.

> **Gate sang phase tiếp theo:** Chỉ bắt đầu code AI/Sync khi **toàn bộ task P0 của Phase 1-6 hoàn thành** + migration test pass + performance regression checklist pass.

---

## Definition of Done (Pre-AI & Pre-Sync) – BỔ SUNG

- [ ] DB version mới migrate thành công trên dữ liệu cũ.
- [ ] Không còn DAO mutable dùng `OnConflictStrategy.REPLACE`.
- [ ] Tất cả soft-delete chuẩn hóa `sync_status = 2`.
- [ ] Không còn hard-delete production path cho transaction/debt/event.
- [ ] Budget flow không còn race condition.
- [ ] Budget UI không rebuild dồn dập (debounce đã áp dụng).
- [ ] **Transaction list đã pagination, load 5000+ giao dịch không OOM, không rớt frame.**
- [ ] **Tất cả critical write đã chuyển sang WorkManager, kiểm tra durability bằng force-kill app.**
- [ ] **Các index hot path đã được thêm và verify bằng `EXPLAIN QUERY PLAN`.**
- [ ] **Timezone grouping thống nhất UTC, thay đổi timezone không làm sai số liệu.**
- [ ] Bộ kiến trúc rules đã được đưa vào quy trình review.

---

## Phụ lục: Mẫu code nhanh cho các task mới

### TimeWindowUtils.java
```java
public final class TimeWindowUtils {
    private TimeWindowUtils() {}
    public static long startOfDayUtc(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli();
    }
}
```

### InsertTransactionWorker.java (minimal)
```java
public class InsertTransactionWorker extends Worker {
    public InsertTransactionWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }
    @NonNull
    @Override
    public Result doWork() {
        // Lấy dữ liệu từ inputData
        // Gọi repository insert (cần repository instance – có thể dùng singleton hoặc DI helper)
        // Trả về Result.success()/retry()/failure()
    }
}
```

### Ví dụ gọi WorkManager từ repository
```java
Data inputData = new Data.Builder()
    .putLong("transaction_id", transaction.getId())
    .putString("json", transaction.toJson())
    .build();
OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(InsertTransactionWorker.class)
    .setInputData(inputData)
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
    .build();
WorkManager.getInstance(context).enqueueUniqueWork(
    "tx_insert_" + transaction.getId(),
    ExistingWorkPolicy.KEEP,
    request
);
```

---

**Tài liệu này là nguồn duy nhất cho kế hoạch gia cố nền móng trước AI/Sync. Mọi thay đổi về kiến trúc phải được cập nhật tại đây và phê duyệt lại.**
```

