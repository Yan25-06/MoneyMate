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

### [X] Task 3.1 - Bỏ `OnConflictStrategy.REPLACE` trên các bảng mutable
**Files:** `TransactionDao.java`, `WalletDao.java`, `CategoryDao.java`, `BudgetDao.java`, `DebtDao.java`, `EventDao.java`

**Action:**
- Chuyển sang `ABORT`/`IGNORE` + update có điều kiện.
- Tách rõ local write và remote merge path.

### [X] Task 3.2 - Sửa toàn bộ soft-delete về `sync_status = 2`
**Files:** `TransactionDao.java`, `DebtDao.java`, `EventDao.java`

**Action:**
- Update SQL soft-delete thành `is_deleted = 1, sync_status = 2, updated_at = :updatedAt`.

### [X] Task 3.3 - Loại bỏ hard-delete API khỏi production path
**Files:** `TransactionDao.java`, `DebtDao.java`, `EventDao.java`

**Action:**
- Xóa hoặc cô lập `deleteAllByUser(...)` khỏi luồng app chính.
- Nếu cần maintenance, chuyển sang debug-only path.

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

