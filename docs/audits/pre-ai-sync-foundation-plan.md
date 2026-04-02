# Pre-AI & Pre-Sync Foundation Plan (MoneyMate)

> **Phạm vi tài liệu này:** chỉ gia cố nền móng Local (Room + MVVM + Repository + UI lifecycle), **không** triển khai logic gọi API AI hoặc đồng bộ Cloud.
>
> **Mục tiêu:** đưa hệ thống về trạng thái an toàn trước khi mở Phase AI/Sync, tránh crash, race condition, data corruption và UI thrashing.

## Mục tiêu kỹ thuật

- Chuẩn hóa metadata thời gian và soft-delete để dữ liệu nhất quán.
- Đảm bảo thao tác nhiều bước là atomic (không bị chen luồng).
- Loại bỏ conflict strategy nguy hiểm (`REPLACE`) trong các entity mutable.
- Ổn định luồng UI/LiveData khi DB có write dày.
- Thiết lập luật kiến trúc bắt buộc trước khi bắt đầu code AI/Sync.

## Theo dõi triển khai

| Phase | Trọng tâm | Ưu tiên | Trạng thái |
|---|---|---|---|
| Phase 1 | Schema & Metadata | P0 | [ ] Chưa bắt đầu |
| Phase 2 | Atomic Ops & DI Wiring | P0 | [ ] Chưa bắt đầu |
| Phase 3 | Conflict & Deletion Cleanup | P0 | [ ] Chưa bắt đầu |
| Phase 4 | UI Performance Hardening | P1 | [ ] Chưa bắt đầu |
| Phase 5 | Architecture Rules Gate | P0 | [ ] Chưa bắt đầu |

---

## Phase 1 - Database Schema & Metadata (Chuẩn bị dữ liệu)

### [ ] Task 1.1 - Chuẩn hóa metadata thời gian cho toàn bộ Entity (UTC Unix Timestamp)
**Why:** Hiện metadata không đồng đều giữa các bảng, gây khó merge conflict và sai lệch thống kê theo thời gian.

**Files Affected:**
- `app/src/main/java/com/group10/moneymate/data/local/entity/UserEntity.java`
- `app/src/main/java/com/group10/moneymate/data/local/entity/TransactionEntity.java`
- `app/src/main/java/com/group10/moneymate/data/local/entity/CategoryEntity.java`
- `app/src/main/java/com/group10/moneymate/data/local/entity/DebtEntity.java`
- `app/src/main/java/com/group10/moneymate/data/local/entity/EventEntity.java`
- `app/src/main/java/com/group10/moneymate/data/local/entity/WalletEntity.java`
- `app/src/main/java/com/group10/moneymate/data/local/entity/BudgetEntity.java`

**Action:**
- Bổ sung/chuẩn hóa đầy đủ field `created_at`, `updated_at` kiểu `long` cho các entity còn thiếu.
- Chuẩn hóa tên cột bằng `@ColumnInfo(name = "created_at")`, `@ColumnInfo(name = "updated_at")`.
- Quy ước duy nhất: lưu epoch millis theo UTC (không lưu chuỗi ngày định dạng text).

### [ ] Task 1.2 - Tạo migration nâng version DB để backfill metadata
**Why:** Tránh crash trên máy user hiện tại và đảm bảo dữ liệu cũ vẫn đọc được sau khi thêm cột mới.

**Files Affected:**
- `app/src/main/java/com/group10/moneymate/data/local/AppDatabase.java`
- `app/src/main/java/com/group10/moneymate/data/local/migrations/Migration8To9.java` (mới)

**Action:**
- Tăng version DB từ `8 -> 9`.
- Tạo migration `8->9`: thêm các cột metadata còn thiếu với default an toàn.
- Backfill `created_at`/`updated_at` bằng giá trị hợp lệ (ưu tiên dùng timestamp hiện có, fallback `System.currentTimeMillis()`).

### [ ] Task 1.3 - Chuẩn hóa query thời gian cho logic Local
**Why:** Giảm rủi ro lệch ngày khi phân tích dữ liệu theo kỳ.

**Files Affected:**
- `app/src/main/java/com/group10/moneymate/data/local/dao/TransactionDao.java`
- `app/src/main/java/com/group10/moneymate/ui/statistics/StatisticsViewModel.java`
- `app/src/main/java/com/group10/moneymate/ui/statistics/StatisticsCategoryDayDetailViewModel.java`

**Action:**
- Rà soát các query/grouping có dùng `localtime`, thống nhất policy thời gian nội bộ.
- Định nghĩa helper time-window dùng UTC epoch boundaries cho logic kỳ báo cáo.

> **Lưu ý quan trọng:** Phase 1 phải hoàn tất và chạy pass migration test trước khi chạm các phase còn lại.

---

## Phase 2 - Atomic Operations & DI Wiring (Đảm bảo tính toàn vẹn)

### [ ] Task 2.1 - Inject `WalletDao` vào `TransactionRepository`
**Why:** Chốt rõ contract xử lý giao dịch cũ/mới, tránh drift giữa chữ ký hàm và hành vi thực tế.

**Files Affected:**
- `app/src/main/java/com/group10/moneymate/data/repository/TransactionRepository.java`
- `app/src/main/java/com/group10/moneymate/di/AppContainer.java`

**Action:**
- Đổi constructor `TransactionRepository` nhận thêm `WalletDao`.
- Đồng bộ wiring trong `AppContainer`.
- Nếu giữ chiến lược balance tính từ transactions: vẫn phải bỏ tham số gây hiểu nhầm (`oldTransaction`) hoặc hiện thực đúng semantics old/new.

### [ ] Task 2.2 - Bọc thao tác nhiều bước Budget bằng transaction boundary
**Why:** `validate + write + syncOtherCategoriesBudget` hiện có thể bị interleave khi chạy đa luồng.

**Files Affected:**
- `app/src/main/java/com/group10/moneymate/data/repository/BudgetRepository.java`
- `app/src/main/java/com/group10/moneymate/data/local/dao/BudgetDao.java`
- `app/src/main/java/com/group10/moneymate/data/local/AppDatabase.java`

**Action:**
- Dùng `runInTransaction` hoặc DAO `@Transaction` entrypoint cho toàn bộ chuỗi thao tác.
- Callback thành công/lỗi chỉ gọi sau khi transaction hoàn tất.

### [ ] Task 2.3 - Chuẩn hóa thao tác nhiều bước liên quan Wallet (archive/restore/delete)
**Why:** Tránh trạng thái trung gian khi update nhiều field (is_archived/is_deleted/sync_status/updated_at).

**Files Affected:**
- `app/src/main/java/com/group10/moneymate/data/local/dao/WalletDao.java`
- `app/src/main/java/com/group10/moneymate/data/repository/WalletRepository.java`

**Action:**
- Tạo các method cập nhật theo cụm field nhất quán, hạn chế full-object overwrite.
- Bổ sung `@Transaction` cho flow nhiều bước nếu có read-modify-write.

---

## Phase 3 - Conflict Resolution & Deletion (Dọn rác DAO)

### [ ] Task 3.1 - Bỏ `OnConflictStrategy.REPLACE` trên các bảng mutable
**Why:** `REPLACE` có thể ghi đè mất `sync_status`, `updated_at`, soft-delete flag khi có ghi đồng thời.

**Files Affected:**
- `app/src/main/java/com/group10/moneymate/data/local/dao/TransactionDao.java`
- `app/src/main/java/com/group10/moneymate/data/local/dao/WalletDao.java`
- `app/src/main/java/com/group10/moneymate/data/local/dao/CategoryDao.java`
- `app/src/main/java/com/group10/moneymate/data/local/dao/BudgetDao.java`
- `app/src/main/java/com/group10/moneymate/data/local/dao/DebtDao.java`
- `app/src/main/java/com/group10/moneymate/data/local/dao/EventDao.java`

**Action:**
- Chuyển sang conflict strategy an toàn hơn (ví dụ `ABORT`/`IGNORE` + update có điều kiện).
- Tách rõ đường local write và đường merge sau này (chỉ chuẩn bị nền, chưa làm sync cloud).

### [ ] Task 3.2 - Sửa toàn bộ soft-delete về `sync_status = 2`
**Why:** Hiện có DAO xóa mềm nhưng set nhầm pending upload (`1`).

**Files Affected:**
- `app/src/main/java/com/group10/moneymate/data/local/dao/TransactionDao.java`
- `app/src/main/java/com/group10/moneymate/data/local/dao/DebtDao.java`
- `app/src/main/java/com/group10/moneymate/data/local/dao/EventDao.java`

**Action:**
- Update SQL soft-delete thành `is_deleted = 1, sync_status = 2, updated_at = :updatedAt`.
- Đảm bảo repository gọi đúng method soft-delete chuẩn hóa.

### [ ] Task 3.3 - Loại bỏ hard-delete API khỏi production path
**Why:** Hard-delete làm mất dữ liệu trước khi có cơ chế reconciliation, phá offline-first invariants.

**Files Affected:**
- `app/src/main/java/com/group10/moneymate/data/local/dao/TransactionDao.java`
- `app/src/main/java/com/group10/moneymate/data/local/dao/DebtDao.java`
- `app/src/main/java/com/group10/moneymate/data/local/dao/EventDao.java`

**Action:**
- Xóa hoặc cô lập `deleteAllByUser(...)` khỏi luồng app chính.
- Nếu cần maintenance nội bộ, tách sang debug-only path và không expose qua repository UI.

---

## Phase 4 - UI Performance (Chống bão UI)

### [ ] Task 4.1 - Debounce/coalesce rebuild ở Budget ViewModel
**Why:** `observeForever` fan-out và gọi `rebuildUiModels()` liên tục gây UI thrashing khi DB cập nhật dày.

**Files Affected:**
- `app/src/main/java/com/group10/moneymate/ui/budget/BudgetViewModel.java`

**Action:**
- Thay gọi trực tiếp `rebuildUiModels()` trong các observer bằng cơ chế debounce/coalesce (Handler delay ngắn hoặc equivalent).
- Đảm bảo `onCleared()` hủy callback pending để tránh leak.

### [ ] Task 4.2 - Giảm full list refresh không cần thiết
**Why:** Mỗi invalidation Room đang có nguy cơ kéo theo compute nặng và cấp phát list mới liên tục.

**Files Affected:**
- `app/src/main/java/com/group10/moneymate/ui/budget/BudgetListFragment.java`
- `app/src/main/java/com/group10/moneymate/ui/budget/BudgetAdapter.java`
- `app/src/main/java/com/group10/moneymate/ui/transaction/TransactionListFragment.java`

**Action:**
- Áp dụng `distinct` logic ở ViewModel cho các state lặp.
- Giữ `ListAdapter + DiffUtil` đúng chuẩn và tránh submit list trùng nội dung không cần thiết.

### [ ] Task 4.3 - Chuẩn bị query phân trang/chunk cho tập dữ liệu lớn
**Why:** Tránh RAM/GC churn khi dữ liệu local tăng lớn trước khi bật sync thật.

**Files Affected:**
- `app/src/main/java/com/group10/moneymate/data/local/dao/TransactionDao.java`
- `app/src/main/java/com/group10/moneymate/data/repository/TransactionRepository.java`

**Action:**
- Thêm API query có `LIMIT/OFFSET` (hoặc keyset) cho các list nóng.
- Ưu tiên áp dụng trước ở màn hình lịch sử giao dịch và báo cáo dài hạn.

---

## Phase 5 - Architecture Rules (Gate trước khi bước sang AI/Sync)

### [ ] Task 5.1 - Ban hành "Mandatory Rules" ở cấp team
**Why:** Ngăn tái phát các lỗi kiến trúc khi bắt đầu phase kế tiếp.

**Files Affected:**
- `docs/audits/concurrency-sync-audit-action-plan.md`
- `docs/audits/pre-ai-sync-foundation-plan.md` (file này)
- `AGENT.md`

**Action:**
- Chốt và áp dụng checklist bắt buộc trong PR template/review.
- Các rule tối thiểu phải có:
  1. Cấm `REPLACE` cho entity mutable offline-first.
  2. Cấm hard-delete trong production flow.
  3. Mọi multi-step write phải atomic (`@Transaction`/`runInTransaction`).
  4. Mọi record mới/sửa phải cập nhật `updated_at` UTC + `sync_status` hợp lệ.
  5. Cấm gọi `observeForever` fan-out nếu không có coalesce và cleanup rõ ràng.

### [ ] Task 5.2 - Rule cho phase AI/Sync kế tiếp (chỉ định nghĩa trước, chưa implement)
**Why:** Đảm bảo lúc mở phase mới không phá nền local vừa gia cố.

**Files Affected:**
- `docs/audits/pre-ai-sync-foundation-plan.md` (file này)

**Action:**
- Định nghĩa rõ các nguyên tắc bắt buộc cho phase sau:
  - DB write quan trọng không dùng executor thô đơn lẻ cho tác vụ cần durability.
  - Dữ liệu lớn phải chunk/paginate, không load toàn bộ vào RAM.
  - Query thống kê nặng phải có index chứng minh trước khi merge.

> **Gate sang phase tiếp theo:** Chỉ bắt đầu code AI/Sync khi toàn bộ task P0 của Phase 1-3 hoàn thành + migration test pass + regression checklist pass.

---

## Definition of Done (Pre-AI & Pre-Sync)

- [ ] DB version mới migrate thành công trên dữ liệu cũ, không mất dữ liệu.
- [ ] Không còn DAO mutable dùng `OnConflictStrategy.REPLACE`.
- [ ] Tất cả soft-delete chuẩn hóa `sync_status = 2`.
- [ ] Không còn hard-delete production path cho transaction/debt/event.
- [ ] Budget flow không còn race condition do thao tác nhiều bước.
- [ ] Budget UI không còn hiện tượng rebuild dồn dập khi data invalidation liên tục.
- [ ] Bộ kiến trúc rules đã được đưa vào quy trình review trước khi mở Phase AI/Sync.

