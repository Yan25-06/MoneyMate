# BÁO CÁO CHI TIẾT: GIA CỐ NỀN TẢNG MONEYMATE TRƯỚC KHI TRIỂN KHAI AI VÀ CLOUD SYNC

## MỤC LỤC

1. [Lời mở đầu: Tại sao phải đọc báo cáo này?](#1-lời-mở-đầu-tại-sao-phải-đọc-báo-cáo-này)
2. [Tổng quan về app và kế hoạch gia cố](#2-tổng-quan-về-app-và-kế-hoạch-gia-cố)
3. [Database – Các vấn đề và giải pháp chi tiết](#3-database--các-vấn-đề-và-giải-pháp-chi-tiết)
    - 3.1. Conflict strategy `REPLACE` – Mất dữ liệu khi sync
    - 3.2. Thiếu composite index – Query sync cực chậm
    - 3.3. Metadata không đồng nhất – Không xác định được record nào mới hơn
    - 3.4. Migration an toàn – Lên version 13 không mất dữ liệu
4. [Concurrency & Race Condition – Các vấn đề và giải pháp chi tiết](#4-concurrency--race-condition--các-vấn-đề-và-giải-pháp-chi-tiết)
    - 4.1. Budget write không atomic – Tạo ra duplicate budget
    - 4.2. Soft-delete ghi sai sync_status – Cloud không xóa được dữ liệu
    - 4.3. Hard-delete còn tồn tại – Mất dữ liệu vĩnh viễn
5. [UI Performance – Các vấn đề và giải pháp chi tiết](#5-ui-performance--các-vấn-đề-và-giải-pháp-chi-tiết)
    - 5.1. UI rebuild quá nhiều (UI thrashing) – Rớt frame, lag
    - 5.2. Transaction list load toàn bộ không phân trang – OOM, giật khi scroll
    - 5.3. `observeForever` gây memory leak – App chậm dần, crash
6. [Durability & Background Tasks – Các vấn đề và giải pháp chi tiết](#6-durability--background-tasks--các-vấn-đề-và-giải-pháp-chi-tiết)
    - 6.1. Critical writes chỉ dùng Executor – Mất dữ liệu khi app bị kill
    - 6.2. Sync không có checkpoint – Upload lại từ đầu mỗi lần
7. [Timezone & Data Consistency – Các vấn đề và giải pháp chi tiết](#7-timezone--data-consistency--các-vấn-đề-và-giải-pháp-chi-tiết)
    - 7.1. SQL dùng `localtime` cho grouping – Thống kê sai khi đổi múi giờ
8. [Tóm tắt các thay đổi đã thực hiện](#8-tóm-tắt-các-thay-đổi-đã-thực-hiện)
9. [Hướng dẫn đọc các file kèm theo](#9-hướng-dẫn-đọc-các-file-kèm-theo)
10. [Glossary – Giải thích thuật ngữ](#10-glossary--giải-thích-thuật-ngữ)

---

## 1. Lời mở đầu: Tại sao phải đọc báo cáo này?

**Bối cảnh:** App MoneyMate hiện chưa có AI (scan hóa đơn, trợ lý tài chính) và chưa có cloud sync. Chúng ta đã tiến hành 3 vòng audit (kiểm tra kỹ thuật) và phát hiện ra **rất nhiều lỗi tiềm ẩn** trong codebase. Những lỗi này không gây sự cố ngay lúc này, nhưng khi thêm AI (tạo nhiều giao dịch tự động) và cloud sync (đồng bộ hai chiều), chúng sẽ **bùng nổ** thành crash, mất dữ liệu, UI lag, và tốn pin.

**Mục tiêu của báo cáo này:** Giải thích một cách **chi tiết, dễ hiểu**:
- Mỗi vấn đề là gì? (kèm ví dụ cụ thể)
- Tại sao nó lại là vấn đề (đặc biệt khi có AI/Sync)?
- Chúng ta đã sửa như thế nào? (giải pháp kỹ thuật, code mẫu)
- Kết quả sau khi sửa ra sao?

**Cách đọc:** Bạn không cần đọc hết tất cả các file audit cũ. Báo cáo này tổng hợp và giải thích đầy đủ. Sau khi đọc xong, bạn có thể tham khảo các file chi tiết nếu muốn đi sâu.

---

## 2. Tổng quan về app và kế hoạch gia cố

**MoneyMate là gì?**  
Ứng dụng quản lý chi tiêu cá nhân offline-first (dữ liệu chính trên máy, sync lên cloud sau). Các tính năng chính: giao dịch (thu/chi), ví, ngân sách, thống kê, nợ, sự kiện.

**Tại sao phải "gia cố" trước khi làm AI/Sync?**  
Hãy tưởng tượng bạn xây một ngôi nhà 2 tầng. Nếu móng (database, concurrency, UI) bị nứt, bạn không thể xây thêm tầng 2 (AI, sync) mà không sập. Chúng ta đã phát hiện nhiều vết nứt trong "móng" và cần sửa chúng trước.

**Quy trình đã thực hiện:**

```
Audit Round 1,2,3 (phát hiện vấn đề)
         ↓
Pre-AI-Sync Plan (lên giải pháp chi tiết cho từng vấn đề)
         ↓
AI Agent thực hiện sửa theo plan (hàng trăm thay đổi)
         ↓
Critical Audit Report (kiểm tra lại, xác nhận đã sửa xong 9 quy tắc)
         ↓
AI Readiness + Sync Readiness (ghi lại lưu ý cho tương lai)
```

**Kết quả:** Codebase hiện tại (version DB 13) đã sẵn sàng để bắt đầu phát triển AI và sync một cách an toàn.

---

## 3. Database – Các vấn đề và giải pháp chi tiết

Database là trái tim của app offline-first. Mọi dữ liệu đều được lưu trong SQLite qua Room. Các vấn đề ở đây sẽ ảnh hưởng trực tiếp đến tính toàn vẹn dữ liệu.

### 3.1. Conflict strategy `REPLACE` – Mất dữ liệu khi sync

**Vấn đề là gì?**  
Trong nhiều DAO (TransactionDao, WalletDao, CategoryDao, BudgetDao, DebtDao, EventDao), chúng ta dùng `@Insert(onConflict = OnConflictStrategy.REPLACE)`.

```java
// VÍ DỤ CODE CŨ (NGUY HIỂM)
@Insert(onConflict = OnConflictStrategy.REPLACE)
void insertTransaction(TransactionEntity transaction);
```

`REPLACE` có nghĩa: nếu đã có record cùng primary key (`id`), hãy **xóa record cũ** và **chèn record mới** vào.

**Tại sao là vấn đề khi có sync?**  
Giả sử tình huống sau:

1. **User A** tạo một giao dịch mới trên điện thoại lúc 10:00. Giao dịch này có `id = "abc"`, `amount = 100`, `sync_status = 1` (chưa sync). Chưa kịp sync lên cloud.
2. **Cloud** (do đồng bộ từ thiết bị khác hoặc do lỗi) gửi xuống một giao dịch cũ hơn, cũng có `id = "abc"`, nhưng `amount = 50`, `sync_status = 0` (đã sync), `updated_at = 09:00`.
3. Khi Room thực hiện `REPLACE`, nó sẽ **xóa record của user** (amount=100) và **chèn record cũ** (amount=50). Kết quả: user mất 100, thấy 50. **Dữ liệu mới hơn bị ghi đè bởi dữ liệu cũ**.

Vấn đề còn tồi tệ hơn với `sync_status`: record mới đang chờ upload (1) bị thay bằng record đã sync (0) → cloud không bao giờ nhận được giao dịch của user.

**Giải pháp: UPSERT có điều kiện (Conditional UPSERT)**  
Chúng ta thay thế `REPLACE` bằng một câu lệnh SQL `INSERT ... ON CONFLICT DO UPDATE SET ...` với các quy tắc bảo vệ:

- **Không ghi đè `created_at`** nếu đã có (giữ nguyên ngày tạo gốc).
- **Không ghi đè `sync_status = 2`** (pending delete) – một khi đã đánh dấu xóa, không thể biến nó thành pending upload.
- **Không ghi đè `is_deleted = 1`** – một khi đã xóa mềm, không thể "hồi sinh" nếu không có chủ ý.
- **Luôn cập nhật `updated_at`** để biết record mới nhất.

**Code mẫu giải pháp:**

```java
// MỚI - AN TOÀN
@Query("INSERT INTO transactions (id, user_id, amount, note, timestamp, type, wallet_id, category_id, sync_status, is_deleted, created_at, updated_at) "
     + "VALUES (:id, :userId, :amount, :note, :timestamp, :type, :walletId, :categoryId, :syncStatus, :isDeleted, :createdAt, :updatedAt) "
     + "ON CONFLICT(id) DO UPDATE SET "
     + "amount = excluded.amount, "
     + "note = excluded.note, "
     + "timestamp = excluded.timestamp, "
     + "type = excluded.type, "
     + "wallet_id = excluded.wallet_id, "
     + "category_id = excluded.category_id, "
     + "updated_at = excluded.updated_at, "
     + "sync_status = CASE WHEN transactions.sync_status = 2 THEN 2 ELSE excluded.sync_status END, "
     + "is_deleted = CASE WHEN transactions.is_deleted = 1 THEN 1 ELSE excluded.is_deleted END, "
     + "created_at = COALESCE(transactions.created_at, excluded.created_at)")
void upsertLocal(TransactionEntity transaction);
```

**Giải thích các `CASE` và `COALESCE`:**
- `sync_status = CASE WHEN transactions.sync_status = 2 THEN 2 ELSE excluded.sync_status END`: Nếu record cũ đã có `sync_status = 2` (đã xóa mềm và chờ xóa cloud), giữ nguyên 2. Nếu không, cập nhật thành giá trị mới (thường là 1 cho pending upload).
- `is_deleted` tương tự: giữ 1 nếu đã bị xóa.
- `created_at = COALESCE(transactions.created_at, excluded.created_at)`: Nếu đã có `created_at` thì giữ, nếu chưa (record mới) thì lấy giá trị mới.

**Kết quả:** Không còn tình trạng ghi đè dữ liệu mới hơn bằng dữ liệu cũ. Sync trở nên an toàn.

### 3.2. Thiếu composite index – Query sync cực chậm

**Vấn đề là gì?**  
Khi sync, chúng ta cần query tất cả các record có `sync_status != 0` (pending upload hoặc pending delete) theo từng user, sắp xếp theo `updated_at` để upload dần.

```sql
SELECT * FROM transactions 
WHERE user_id = ? AND sync_status != 0 
ORDER BY updated_at ASC
```

Nếu **không có index** trên `(user_id, sync_status, updated_at)`, database phải **quét toàn bộ bảng** (full table scan). Với 10,000 giao dịch, mỗi lần sync quét 10,000 dòng → chậm, tốn pin, có thể gây `SQLiteDatabaseLockedException` (timeout).

**Tại sao là vấn đề khi có AI/Sync?**  
AI có thể tạo ra hàng trăm giao dịch mới, trigger sync liên tục. Nếu mỗi sync mất 1-2 giây, app sẽ bị đơ, và nếu có nhiều sync cùng lúc, database có thể bị lock.

**Giải pháp: Thêm composite index**  
Index giống như mục lục của cuốn sách. Thay vì lật từng trang (full scan), bạn tra mục lục và đến đúng trang cần.

```java
// Trong TransactionEntity.java
@Entity(indices = {
    @Index(value = {"user_id", "sync_status", "is_deleted", "updated_at"}, 
           name = "idx_tx_user_sync_deleted_updated")
})
public class TransactionEntity { ... }
```

Index này bao gồm 4 cột theo thứ tự: `user_id` (lọc theo user), `sync_status` (lọc theo trạng thái), `is_deleted` (lọc xóa), `updated_at` (sắp xếp). Database sẽ dùng index để tìm nhanh.

**Kiểm tra bằng EXPLAIN QUERY PLAN:**  
Chúng ta đã viết test để xác nhận rằng query sử dụng index, không phải full table scan.

**Kết quả:** Query sync chạy nhanh, chỉ đọc đúng số record cần, database không bị quá tải.

### 3.3. Metadata không đồng nhất – Không xác định được record nào mới hơn

**Vấn đề là gì?**  
Một số entity có `created_at`, `updated_at`, một số không. Tên cột không thống nhất (`createdAt` vs `created_at`). Điều này khiến cho logic merge (hợp nhất dữ liệu từ cloud) không thể biết được record nào là mới hơn.

**Tại sao là vấn đề?**  
Khi sync hai chiều, cả local và cloud đều có thể thay đổi cùng một record. Cần có cơ chế **last-write-wins** (ai ghi sau thì thắng) dựa trên timestamp. Nếu thiếu `updated_at` hoặc không đồng bộ, chúng ta không thể quyết định.

**Giải pháp:**
- Thêm cột `created_at` và `updated_at` (kiểu `long`, lưu epoch millis UTC) vào **tất cả entity**.
- Chuẩn hóa tên cột: `@ColumnInfo(name = "created_at")`, `@ColumnInfo(name = "updated_at")`.
- Trong repository, mỗi lần insert/update, tự động set `updated_at = System.currentTimeMillis()` và nếu là insert mới thì set `created_at`.

**Code mẫu trong repository:**

```java
public void saveTransaction(TransactionEntity transaction, WriteCallback callback) {
    databaseWriteExecutor.execute(() -> {
        if (transaction.getId() == null) {
            transaction.setId(UUID.randomUUID().toString());
            transaction.setCreatedAt(System.currentTimeMillis());
        }
        transaction.setUpdatedAt(System.currentTimeMillis());
        transaction.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        transactionDao.upsertLocal(transaction);
        callback.onSuccess();
    });
}
```

**Kết quả:** Mọi record đều có dấu ấn thời gian, sync có thể so sánh `updated_at` để quyết định giữ lại bản mới nhất.

### 3.4. Migration an toàn – Lên version 13 không mất dữ liệu

**Vấn đề là gì?**  
App hiện tại có database version 8 (theo `AppDatabase.java` cũ). Sau khi thêm cột metadata, index, v.v., chúng ta cần nâng version lên 13. Việc migration (di chuyển dữ liệu từ schema cũ sang mới) phải được thực hiện cẩn thận, không làm mất dữ liệu người dùng.

**Giải pháp:**  
Tạo các migration class tuần tự: `Migration8To9`, `Migration9To10`, `Migration10To11`, `Migration11To12`, `Migration12To13`. Mỗi migration chỉ thêm cột mới với giá trị default an toàn, hoặc tạo index.

Ví dụ `Migration12To13` (thêm index):

```java
static final Migration MIGRATION_12_13 = new Migration(12, 13) {
    @Override
    public void migrate(@NonNull SupportSQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tx_user_sync_deleted_updated ON transactions(user_id, sync_status, is_deleted, updated_at)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_budget_user_sync_deleted_updated ON budgets(user_id, sync_status, is_deleted, updated_at)");
        // ... các index khác
    }
};
```

**Lưu ý quan trọng:** Tất cả migration đều dùng `IF NOT EXISTS` hoặc kiểm tra điều kiện để tránh lỗi khi chạy lại. Không dùng `fallbackToDestructiveMigration()` (xóa dữ liệu cũ).

**Kết quả:** App có thể nâng cấp từ phiên bản cũ lên mới mà không mất một giao dịch nào.

---

## 4. Concurrency & Race Condition – Các vấn đề và giải pháp chi tiết

Concurrency xảy ra khi nhiều luồng (thread) cùng truy cập và thay đổi dữ liệu. App Android có UI thread, background thread (executor), và sync thread. Nếu không được bảo vệ, dữ liệu sẽ bị "xé lẻ" hoặc sai.

### 4.1. Budget write không atomic – Tạo ra duplicate budget

**Vấn đề là gì?**  
`BudgetRepository.addBudget()` thực hiện 3 bước:
1. Kiểm tra xem budget cho (user, wallet, period, category) đã tồn tại chưa.
2. Nếu chưa, insert budget mới.
3. Gọi `syncOtherCategoriesBudget()` để cập nhật budget "Các mục khác" (một budget ảo đại diện cho tất cả category không có budget riêng).

Các bước này **không được bọc trong một transaction** của database. Mỗi bước là một câu lệnh SQL riêng biệt.

**Tại sao là vấn đề?**  
Giả sử có hai luồng chạy đồng thời (ví dụ: user bấm nút "Thêm budget" nhanh hai lần, hoặc AI tự động tạo budget trong khi user đang thêm):

- **Luồng A:** Kiểm tra → chưa có → chuẩn bị insert.
- **Luồng B:** Kiểm tra (trong khi A chưa insert xong) → cũng thấy chưa có → cũng insert.
- Kết quả: **Hai budget giống hệt nhau** được tạo, vi phạm unique constraint (nếu có) hoặc gây sai số liệu.

**Giải pháp: Bọc trong `runInTransaction()`**  
Transaction đảm bảo rằng **các bước trong khối được thực hiện một cách nguyên tử (atomic)**: hoặc tất cả thành công, hoặc không có gì thay đổi. Trong transaction, các luồng khác sẽ bị khóa (hoặc chờ) cho đến khi transaction hoàn tất.

```java
public void addBudget(BudgetEntity budget, WriteCallback callback) {
    databaseWriteExecutor.execute(() -> {
        try {
            appDatabase.runInTransaction(() -> {
                // Bước 1: Kiểm tra
                BudgetEntity existing = budgetDao.findByUserWalletPeriodCategory(
                    budget.getUserId(), budget.getWalletId(), 
                    budget.getPeriod(), budget.getCategoryId()
                );
                if (existing != null) {
                    throw new IllegalStateException("Budget already exists");
                }
                // Bước 2: Insert
                budgetDao.insert(budget);
                // Bước 3: Sync other categories budget
                syncOtherCategoriesBudget(budget.getUserId(), budget.getWalletId(), budget.getPeriod());
            });
            callback.onSuccess();
        } catch (Exception e) {
            callback.onError(e);
        }
    });
}
```

**Kết quả:** Dù có bao nhiêu luồng cố gắng tạo budget cùng lúc, chỉ một luồng thành công, các luồng khác sẽ thấy đã tồn tại hoặc chờ.

### 4.2. Soft-delete ghi sai sync_status – Cloud không xóa được dữ liệu

**Vấn đề là gì?**  
Trong `TransactionDao.softDelete()`, code cũ:

```java
@Query("UPDATE transactions SET is_deleted = 1, sync_status = 1 WHERE id = :id")
void softDelete(String id, long updatedAt);
```

`sync_status = 1` có nghĩa là "pending upload" (chờ upload lên cloud). Nhưng đối với một record bị xóa, chúng ta cần báo cho cloud biết rằng nó đã bị xóa, không phải là một bản cập nhật thông thường.

**Tại sao là vấn đề?**
- Cloud nhận được một record với `is_deleted = 1` và `sync_status = 1`. Nếu cloud chỉ nhìn vào `sync_status`, nó có thể hiểu rằng đây là một record mới cần được đồng bộ (nhưng thực chất là xóa).
- Hậu quả: Record không bị xóa trên cloud, và khi sync lần sau, cloud có thể gửi lại record đó xuống local, khiến giao dịch đã xóa "hồi sinh".

**Giải pháp:**  
Sửa tất cả soft-delete methods để set `sync_status = 2` (pending delete).

```java
// MỚI - ĐÚNG
@Query("UPDATE transactions SET is_deleted = 1, sync_status = 2, updated_at = :updatedAt WHERE id = :id")
void softDelete(String id, long updatedAt);
```

**Luồng xóa đúng sau khi có sync:**
1. User xóa giao dịch → local: `is_deleted = 1`, `sync_status = 2`.
2. SyncWorker lấy tất cả record có `sync_status = 2` (pending delete), gửi request `DELETE` lên cloud.
3. Cloud xóa record và trả về thành công.
4. Local thực hiện **hard-delete** (xóa vĩnh viễn) record đó trong một maintenance job (vì không còn cần sync nữa).

**Kết quả:** Xóa trên local và cloud nhất quán.

### 4.3. Hard-delete còn tồn tại – Mất dữ liệu vĩnh viễn

**Vấn đề là gì?**  
Trong codebase cũ, có các method như `deleteAllByUser()` trong `TransactionDao`, `DebtDao`, `EventDao`. Các method này dùng `@Delete` hoặc `DELETE FROM ...` để xóa vĩnh viễn record khỏi database.

**Tại sao là vấn đề?**  
Trong mô hình offline-first, chúng ta **không bao giờ được hard-delete** dữ liệu chính (trừ khi đã xác nhận cloud đã xóa). Hard-delete sớm sẽ làm mất cơ hội sync trạng thái xóa lên cloud. Nếu user xóa một giao dịch trên máy A, hard-delete ngay, máy B vẫn còn giao dịch đó, cloud cũng còn → không nhất quán.

**Giải pháp:**
- Xóa hoặc đánh dấu `@RestrictTo(RestrictTo.Scope.LIBRARY)` các method hard-delete trong DAO.
- Trong production code, chỉ dùng soft-delete (đã sửa ở trên).
- Sau khi cloud xác nhận, một maintenance worker (chạy định kỳ) mới hard-delete các record có `sync_status = 2` và đã được xóa trên cloud quá lâu.

**Kết quả:** Không còn đường dẫn nào trong UI hoặc sync flow có thể hard-delete dữ liệu chưa được cloud xác nhận.

---

## 5. UI Performance – Các vấn đề và giải pháp chi tiết

UI là những gì người dùng nhìn thấy. Nếu UI bị lag, giật, crash, người dùng sẽ rất bực mình.

### 5.1. UI rebuild quá nhiều (UI thrashing) – Rớt frame, lag

**Vấn đề là gì?**  
Trong `BudgetViewModel`, có rất nhiều `observeForever` lắng nghe sự thay đổi của các LiveData từ repository (danh sách budgets, categories, wallets, v.v.). Mỗi khi database thay đổi (dù chỉ một budget thay đổi), các observer này gọi `rebuildUiModels()` ngay lập tức. `rebuildUiModels()` duyệt qua toàn bộ dữ liệu, tạo lại các model cho UI (mất ~50-100ms).

Khi AI sync 100 giao dịch mới, mỗi giao dịch có thể ảnh hưởng đến budget (cập nhật spent amount). Kết quả: `rebuildUiModels()` được gọi 100 lần trong vòng 2 giây → UI bị đơ, không thể tương tác, có thể gây ANR (Application Not Responding).

**Tại sao là vấn đề với AI?**  
AI có thể tạo ra hàng loạt giao dịch chỉ trong vài giây (quét ảnh hàng loạt). UI thrashing sẽ làm trải nghiệm trở nên tồi tệ.

**Giải pháp: Debounce (gộp các lệnh gọi)**  
Debounce có nghĩa là: khi có nhiều lệnh gọi trong một khoảng thời gian ngắn, chúng ta chỉ thực thi **một lần duy nhất** sau khi các lệnh gọi dừng lại.

Chúng ta tạo một base class `DebounceableViewModel`:

```java
public abstract class DebounceableViewModel extends ViewModel {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingRunnable;

    protected void debounce(Runnable action, long delayMs) {
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

Sau đó, trong `BudgetViewModel`, thay vì gọi trực tiếp `rebuildUiModels()`:

```java
// CŨ (gây thrashing)
budgetRepository.getAllBudgets().observeForever(budgets -> {
    rebuildUiModels();
});

// MỚI (debounce 100ms)
budgetRepository.getAllBudgets().observeForever(budgets -> {
    debounce(this::rebuildUiModels, 100L);
});
```

**Kết quả:** Dù có 100 thay đổi trong 100ms, `rebuildUiModels()` chỉ chạy một lần sau 100ms cuối cùng. UI mượt mà.

### 5.2. Transaction list load toàn bộ không phân trang – OOM, giật khi scroll

**Vấn đề là gì?**  
`TransactionDao.getAllTransactions()` trả về `LiveData<List<TransactionEntity>>`. Khi có 5000 giao dịch, Room sẽ đọc cả 5000 record vào bộ nhớ mỗi khi có thay đổi (ví dụ thêm một giao dịch mới). Mỗi `TransactionEntity` có thể tốn vài trăm bytes → 5000 record tốn ~2-5 MB. Mỗi lần thay đổi, GC phải dọn dẹp list cũ, tạo list mới → memory churn, UI giật.

**Tại sao là vấn đề với AI?**  
AI có thể thêm 100 giao dịch cùng lúc, mỗi lần lại emit toàn bộ 5000+ giao dịch, gây OOM trên máy thấp cấu hình.

**Giải pháp: Keyset pagination (phân trang dựa trên con trỏ)**

Thay vì tải tất cả, chúng ta tải từng trang 30 giao dịch. Khi user scroll đến gần cuối, tải trang tiếp theo.

**Tại sao không dùng LIMIT/OFFSET?**  
`LIMIT 30 OFFSET 90` sẽ gặp vấn đề khi có giao dịch mới được chèn vào đầu danh sách (ví dụ giao dịch hôm nay). Khi đang ở trang 3, nếu có 10 giao dịch mới được thêm vào trước, OFFSET sẽ bị lệch, dẫn đến hiển thị trùng hoặc bỏ sót. Keyset dựa trên giá trị thực của cột `timestamp` và `id` nên ổn định.

**Cách keyset hoạt động:**

Lần đầu: lấy 30 giao dịch đầu tiên (sắp xếp theo timestamp DESC, id DESC). Ghi nhớ `timestamp` và `id` của giao dịch cuối cùng trong trang.

Lần tiếp theo: dùng điều kiện `(timestamp < lastTimestamp) OR (timestamp = lastTimestamp AND id < lastId)` để lấy 30 giao dịch tiếp theo.

**Code trong DAO:**

```java
@Query("SELECT * FROM transactions " +
       "WHERE user_id = :userId " +
       "AND (timestamp < :lastTimestamp OR (timestamp = :lastTimestamp AND id < :lastId)) " +
       "ORDER BY timestamp DESC, id DESC " +
       "LIMIT :limit")
List<TransactionEntity> loadNextPage(String userId, long lastTimestamp, String lastId, int limit);
```

**Code trong Repository quản lý cursor:**

```java
private long lastTimestamp = Long.MAX_VALUE;
private String lastId = "";
private static final int PAGE_SIZE = 30;

public void loadNextPage(DataCallback<List<TransactionEntity>> callback) {
    executor.execute(() -> {
        List<TransactionEntity> page;
        if (lastTimestamp == Long.MAX_VALUE && lastId.isEmpty()) {
            // Trang đầu tiên
            page = transactionDao.getFirstPage(userId, PAGE_SIZE);
        } else {
            page = transactionDao.loadNextPage(userId, lastTimestamp, lastId, PAGE_SIZE);
        }
        if (!page.isEmpty()) {
            lastTimestamp = page.get(page.size() - 1).getTimestamp();
            lastId = page.get(page.size() - 1).getId();
        }
        mainHandler.post(() -> callback.onSuccess(page));
    });
}
```

**Kết quả:** Chỉ có 30 giao dịch trong bộ nhớ cùng lúc, scroll mượt, không OOM.

### 5.3. `observeForever` gây memory leak – App chậm dần, crash

**Vấn đề là gì?**  
`LiveData.observeForever(Observer)` đăng ký một observer **không tự động hủy** khi lifecycle (Fragment/Activity) kết thúc. Trong `BudgetViewModel`, có nhiều `observeForever` được gọi trong constructor, nhưng không có `removeObserver` tương ứng.

**Tại sao là vấn đề?**  
ViewModel tồn tại lâu hơn Fragment (ViewModel vẫn sống khi Fragment bị destroy, chờ được tái sử dụng). Nhưng nếu Fragment bị destroy và không còn ai dùng ViewModel đó nữa, ViewModel vẫn giữ các observer. Các observer này giữ reference đến ViewModel (thông qua lambda hoặc anonymous class), và ViewModel giữ reference đến Repository, Repository giữ DAO, v.v. → không có gì được GC → memory leak.

**Giải pháp:**  
Lưu từng observer vào một field, và gọi `removeObserver()` trong `onCleared()` của ViewModel.

```java
public class BudgetViewModel extends DebounceableViewModel {
    private final Observer<List<Budget>> budgetObserver = budgets -> debounce(this::rebuildUiModels, 100L);
    private final Observer<List<Category>> categoryObserver = categories -> debounce(this::rebuildUiModels, 100L);

    public BudgetViewModel(...) {
        budgetRepository.getAllBudgets().observeForever(budgetObserver);
        categoryRepository.getAllCategories().observeForever(categoryObserver);
    }

    @Override
    protected void onCleared() {
        budgetRepository.getAllBudgets().removeObserver(budgetObserver);
        categoryRepository.getAllCategories().removeObserver(categoryObserver);
        super.onCleared();
    }
}
```

**Kết quả:** Không còn memory leak, app chạy ổn định lâu dài.

---

## 6. Durability & Background Tasks – Các vấn đề và giải pháp chi tiết

Durability (độ bền) có nghĩa là công việc nền không bị mất khi app bị kill hoặc thiết bị khởi động lại.

### 6.1. Critical writes chỉ dùng Executor – Mất dữ liệu khi app bị kill

**Vấn đề là gì?**  
Hiện tại, tất cả các write (insert, update, delete) đều được gửi đến `AppDatabase.databaseWriteExecutor`, một `Executor` với thread pool in-memory:

```java
public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(4);
```

Khi bạn gọi `repository.saveTransaction()`, nó `execute()` một task vào hàng đợi của thread pool. Nếu app bị kill (user swipe away, system kill do memory pressure), **toàn bộ hàng đợi biến mất** cùng với các task chưa kịp chạy.

**Tại sao là vấn đề với AI/Sync?**
- AI có thể tạo 10 giao dịch và enqueue task. User thoát app ngay sau đó → 10 giao dịch mất vĩnh viễn.
- Sync worker đang upload 100 record, bị kill ở giữa → mất checkpoint, lần sau upload lại từ đầu.

**Giải pháp: Dùng WorkManager cho critical background tasks**  
`WorkManager` là thư viện chính thức của Android để chạy tác vụ nền **durable**. Nó lưu task vào database hệ thống, đảm bảo task sẽ chạy (kể cả app bị kill, thiết bị reboot).

**Phân định rõ ràng:**
- **Local writes do user tương tác trực tiếp (thêm/sửa/xóa giao dịch):** vẫn dùng Executor, vì cần callback ngay và hiệu năng cao. Người dùng đang chờ phản hồi, không thể trì hoãn.
- **Sync và AI processing:** dùng WorkManager, vì có thể chạy lâu, cần retry, và không cần kết quả tức thì.

**Cài đặt WorkManager với Dependency Injection:**  
Tạo `MoneyMateWorkerFactory` để inject repository vào Worker:

```java
public class MoneyMateWorkerFactory extends WorkerFactory {
    private final AppContainer appContainer;

    public MoneyMateWorkerFactory(AppContainer appContainer) {
        this.appContainer = appContainer;
    }

    @Override
    public ListenableWorker createWorker(Context context, String workerClassName, WorkerParameters params) {
        if (workerClassName.equals(SyncWorker.class.getName())) {
            return new SyncWorker(context, params, 
                appContainer.getTransactionRepository(),
                appContainer.getBudgetRepository(),
                appContainer.getSyncMetadataRepository());
        }
        return null;
    }
}
```

**Enqueue sync từ repository:**

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
    ExistingWorkPolicy.KEEP,  // Không tạo mới nếu đã có sync đang chạy
    syncRequest
);
```

**Kết quả:** Dù app có bị kill, task sync vẫn được thực thi trong lần chạy tiếp theo.

### 6.2. Sync không có checkpoint – Upload lại từ đầu mỗi lần

**Vấn đề là gì?**  
Nếu không có checkpoint, mỗi lần sync, chúng ta lấy **tất cả** pending records (có thể 10,000 record) và upload lên cloud. Nếu mạng yếu hoặc bị gián đoạn ở giữa, lần sau lại upload lại từ đầu, gây lãng phí băng thông và pin.

**Giải pháp: Bảng `sync_metadata` để lưu checkpoint**  
Chúng ta tạo một bảng riêng để lưu `lastSyncTimestamp` và `lastSyncId` cho mỗi loại dữ liệu (transactions, budgets, v.v.).

```java
@Entity(tableName = "sync_metadata")
public class SyncMetadataEntity {
    @PrimaryKey @NonNull
    public String tableName;          // "transactions", "budgets", ...
    public long lastSyncTimestamp;    // updated_at lớn nhất đã upload thành công
    public String lastSyncId;         // id của record cuối cùng
}
```

Trong `SyncWorker`, chúng ta:
1. Đọc checkpoint từ bảng `sync_metadata`.
2. Lấy các pending records có `updated_at > lastSyncTimestamp` hoặc `(updated_at == lastSyncTimestamp AND id > lastSyncId)`, sắp xếp theo `updated_at ASC, id ASC`.
3. Upload từng batch (ví dụ 200 records).
4. Sau khi upload batch thành công, cập nhật checkpoint với `updated_at` và `id` của record cuối cùng trong batch.
5. Nếu bị gián đoạn, lần sau sẽ bắt đầu từ checkpoint, không upload lại những record đã thành công.

**Kết quả:** Sync hiệu quả, tiết kiệm băng thông, và có thể resume sau khi bị gián đoạn.

---

## 7. Timezone & Data Consistency – Các vấn đề và giải pháp chi tiết

### 7.1. SQL dùng `localtime` cho grouping – Thống kê sai khi đổi múi giờ

**Vấn đề là gì?**  
Trong `TransactionDao`, có các query dùng `STRFTIME` với tham số `'localtime'` để group theo ngày:

```sql
STRFTIME('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') as date
```

`'localtime'` chuyển đổi timestamp từ UTC sang múi giờ của thiết bị. Điều này gây ra hai vấn đề:

1. **Thống kê không nhất quán giữa các thiết bị:** User A ở Việt Nam (UTC+7) và User B ở New York (UTC-5) xem cùng một giao dịch có timestamp 23:00 UTC → ở VN là 06:00 ngày hôm sau, ở NY là 18:00 cùng ngày. Group theo localtime sẽ cho kết quả khác nhau.
2. **Khi sync lên cloud, server dùng UTC:** Cloud tính tổng chi tiêu theo ngày UTC, nhưng local lại hiển thị theo localtime → số liệu lệch.

**Giải pháp: Lưu UTC, tính boundary trong code, hiển thị local**

Nguyên tắc:
- **Database lưu epoch millis UTC** (đã đúng).
- **Query không dùng STRFTIME**. Thay vào đó, ViewModel/Repository dùng `TimeWindowUtils` để tính `startUtc` và `endUtc` của ngày/tháng cần lấy, rồi gửi đến DAO dưới dạng số.
- **Hiển thị:** Convert UTC epoch sang local datetime bằng `TimeWindowUtils.formatDateLocal()`.

`TimeWindowUtils.java`:

```java
public static long startOfDayUtc(long epochMillis) {
    Instant instant = Instant.ofEpochMilli(epochMillis);
    LocalDate date = instant.atZone(ZoneOffset.UTC).toLocalDate();
    return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
}

public static long endOfDayUtc(long epochMillis) {
    return startOfDayUtc(epochMillis) + 86400000 - 1;
}

public static String formatDateLocal(long epochUtc, String pattern) {
    LocalDateTime local = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochUtc), ZoneOffset.UTC);
    return local.format(DateTimeFormatter.ofPattern(pattern));
}
```

**DAO mới:**

```java
@Query("SELECT * FROM transactions WHERE user_id = :userId AND timestamp BETWEEN :startUtc AND :endUtc")
LiveData<List<TransactionEntity>> getTransactionsInRange(String userId, long startUtc, long endUtc);
```

**Kết quả:** Thống kê nhất quán giữa local và cloud, bất kể user ở múi giờ nào.

---

## 8. Tóm tắt các thay đổi đã thực hiện

| STT | Vấn đề | Giải pháp | File chính thay đổi |
|-----|--------|-----------|---------------------|
| 1 | `REPLACE` conflict | UPSERT có điều kiện | Tất cả DAO |
| 2 | Thiếu composite index | Thêm `@Index` + migration v13 | `TransactionEntity`, `BudgetEntity`, `DebtEntity`, `WalletEntity` |
| 3 | Metadata không đồng nhất | Thêm `created_at`, `updated_at` cho mọi entity, migration 8→9 | Các entity, `Migration8To9` |
| 4 | Budget write không atomic | Bọc `runInTransaction()` | `BudgetRepository` |
| 5 | Soft-delete sai sync_status | Set `sync_status = 2` | `TransactionDao`, `DebtDao`, `EventDao` |
| 6 | Hard-delete còn trong production | Xóa hoặc `@RestrictTo` | `TransactionDao`, `DebtDao`, `EventDao` |
| 7 | UI rebuild loạn (thrashing) | Debounce qua `DebounceableViewModel` | `BudgetViewModel`, `StatisticsViewModel` |
| 8 | Transaction list không phân trang | Keyset pagination | `TransactionDao`, `TransactionRepository`, `TransactionListFragment` |
| 9 | `observeForever` memory leak | Lưu observer, remove trong `onCleared` | `BudgetViewModel` |
| 10 | Critical writes không durable | WorkManager cho sync/AI | `SyncWorker`, `MoneyMateWorkerFactory` |
| 11 | Sync không checkpoint | Bảng `sync_metadata` | `SyncMetadataEntity`, `SyncMetadataDao` |
| 12 | Timezone grouping sai | `TimeWindowUtils`, bỏ STRFTIME | `TransactionDao`, `StatisticsViewModel` |
| 13 | TransactionRepository thiếu WalletDao | Inject thêm `WalletDao` | `AppContainer`, `TransactionRepository` |

**Database version:** từ 8 lên 13 (qua các migration 8→9, 9→10, 10→11, 11→12, 12→13).

---

## 9. Hướng dẫn đọc các file kèm theo

Nếu bạn muốn đào sâu vào từng giai đoạn, hãy đọc theo thứ tự sau:

| Thứ tự | Tên file | Nội dung chính |
|--------|----------|----------------|
| 1 | `architecture-analysis-report.md` | Hiện trạng app trước khi sửa: pattern không đồng nhất, điểm mạnh/yếu. |
| 2 | `concurrency-sync-audit-round1.md` | Phát hiện vấn đề đợt 1 (soft-delete, REPLACE, budget non-atomic). |
| 3 | `concurrency-sync-audit-round2.md` | Phát hiện sâu hơn (wiring drift, hard-delete, metadata). |
| 4 | `concurrency-sync-audit-round3-system-level.md` | Phát hiện system-level (UI thrashing, non-paged reads, durability, timezone). |
| 5 | `concurrency-sync-audit-action-plan.md` | Kế hoạch commit-by-commit để sửa. |
| 6 | `concurrency-sync-impact-assessment.md` | Đánh giá tác động của các thay đổi lên feature hiện tại. |
| 7 | `pre-ai-sync-foundation-plan.md` | **Kế hoạch chi tiết nhất**, bao gồm code mẫu cho từng Phase. |
| 8 | `CRITICAL_AUDIT_REPORT.md` | **Kết quả kiểm tra sau sửa** – PASS cho 9 quy tắc. |
| 9 | `ai-readiness-status.md` | Lưu ý khi triển khai AI (human-in-the-loop, validation, privacy). |
| 10 | `sync-readiness-status.md` | Lưu ý khi triển khai cloud sync (idempotency, conflict policy, telemetry). |

**Khuyến nghị:** Nếu bạn chỉ muốn biết "đã sửa được gì và còn phải làm gì", hãy đọc `CRITICAL_AUDIT_REPORT.md` + `ai-readiness-status.md` + `sync-readiness-status.md`.

---

## 10. Glossary – Giải thích thuật ngữ

| Thuật ngữ | Giải thích |
|-----------|-----------|
| **Offline-first** | Ứng dụng ưu tiên làm việc với dữ liệu local, sau đó đồng bộ lên cloud khi có mạng. |
| **Soft-delete** | Không xóa hẳn record khỏi database, chỉ đánh dấu `is_deleted = 1`. Dữ liệu vẫn còn để có thể sync lên cloud. |
| **Hard-delete** | Xóa vĩnh viễn record khỏi database. |
| **sync_status** | Trạng thái đồng bộ: 0 = đã sync, 1 = chờ upload (pending upload), 2 = chờ xóa trên cloud (pending delete). |
| **REPLACE** | Chiến lược của Room: nếu primary key đã tồn tại, xóa record cũ và chèn record mới. |
| **UPSERT** | Kết hợp INSERT và UPDATE: nếu record đã tồn tại thì UPDATE, nếu chưa thì INSERT. |
| **Transaction (database)** | Nhóm nhiều câu lệnh SQL thành một đơn vị không thể tách rời: hoặc tất cả thành công, hoặc không có gì thay đổi. |
| **Atomic** | Tính chất không thể chia cắt. |
| **Race condition** | Xảy ra khi hai hoặc nhiều luồng cùng truy cập dữ liệu và kết quả phụ thuộc vào thứ tự thực thi. |
| **Debounce** | Kỹ thuật gộp nhiều lệnh gọi trong một khoảng thời gian thành một lệnh duy nhất. |
| **Keyset pagination** | Phân trang dựa trên giá trị của cột cuối cùng (ví dụ `WHERE timestamp < lastTimestamp`), ổn định hơn OFFSET khi có insert. |
| **Composite index** | Index trên nhiều cột, giúp query có nhiều điều kiện WHERE chạy nhanh. |
| **WorkManager** | Thư viện Android chạy tác vụ nền durable, có retry, có thể sống qua reboot. |
| **Checkpoint** | Điểm đánh dấu tiến trình đã xử lý, để resume sau gián đoạn. |
| **Idempotent** | Tính chất của một API: gọi nhiều lần cho cùng một dữ liệu đều cho kết quả như nhau (không tạo duplicate). |
| **Memory leak** | Bộ nhớ không được giải phóng khi không còn dùng đến, dần dần làm app chậm và crash. |
| **UI thrashing** | UI bị buộc phải vẽ lại quá nhiều lần trong thời gian ngắn, gây rớt frame, lag. |

