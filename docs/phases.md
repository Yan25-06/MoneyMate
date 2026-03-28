# MoneyMate — Kế hoạch triển khai

> **Nguyên tắc:** Mỗi phase phải **build được & chạy được** trước khi sang phase tiếp theo.  
> **Stack:** Java · Room · Firebase Auth · Navigation Component · MVVM · MPAndroidChart

---

## Tổng quan

| Phase | Tên | Nội dung cốt lõi | Kết quả kiểm tra |
|-------|-----|-------------------|------------------|
| 0 | Foundation | Project setup, DI, DB | App khởi động không crash |
| 1 | Auth | Đăng ký / Đăng nhập Email | Vào được HomeActivity |
| 2 | Wallet | CRUD Ví | Thêm/sửa/xóa ví, xem danh sách |
| 3 | Category | CRUD Danh mục + default data | Thêm/sửa/xóa danh mục |
| 4 | Transaction | CRUD Giao dịch | Thêm/sửa/xóa giao dịch, chọn ví + danh mục |
| 5 | Home Dashboard | Tổng quan tài chính | Xem số dư, giao dịch gần đây |
| 6 | Budget | CRUD Ngân sách + cảnh báo | Thiết lập & theo dõi ngân sách |
| 7 | Statistics | Biểu đồ PieChart / BarChart | Xem thống kê theo tháng |
| 8 | Profile & Settings | Hồ sơ, cài đặt, dark mode | Chỉnh sửa profile, đổi theme |
| 9 | Passcode | Đăng nhập Passcode offline | Login bằng passcode |
| 10 | Polish & QA | Bug fix, UX, edge cases | Ứng dụng ổn định |

---

## PHASE 0 — Foundation (Nền tảng)

> **Mục tiêu:** Toàn bộ infrastructure sẵn sàng, app build và chạy được.

### 0.1 — Room Database

**File cần làm:**

| File | Việc cần làm |
|------|-------------|
| `entity/UserEntity.java` | `@Entity` với: `uid`, `email`, `displayName`, `avatarUrl`, `currency`, `language`, `passcodeHash`, `createdAt` |
| `entity/WalletEntity.java` | `@Entity`: `id`, `uid`, `name`, `type` (CASH/BANK/E_WALLET), `balance`, `currency`, `createdAt` |
| `entity/CategoryEntity.java` | `@Entity`: `id`, `uid`, `name`, `iconName`, `type` (INCOME/EXPENSE), `isDefault`, `colorHex` |
| `entity/TransactionEntity.java` | `@Entity`: `id`, `uid`, `walletId`, `categoryId`, `amount`, `type`, `note`, `date`, `createdAt` |
| `entity/BudgetEntity.java` | `@Entity`: `id`, `uid`, `categoryId`, `limitAmount`, `month`, `year`, `spentAmount` |
| `dao/UserDao.java` | `insert`, `update`, `getByUid(uid)` |
| `dao/WalletDao.java` | `insert`, `update`, `delete`, `getAllByUid(uid): LiveData<List>`, `getById(id)`, `updateBalance` |
| `dao/CategoryDao.java` | `insert`, `update`, `delete`, `getAllByUid(uid): LiveData<List>`, `getDefaults()` |
| `dao/TransactionDao.java` | `insert`, `update`, `delete`, `getAllByUid(uid): LiveData`, `getByWallet`, `getByCategory`, `getByDateRange`, `getTotalByType` |
| `dao/BudgetDao.java` | `insert`, `update`, `delete`, `getByUidAndMonth(uid,month,year): LiveData`, `getByCategory` |
| `AppDatabase.java` | `@Database(entities={...})`, singleton `getInstance()`, `Converters` |
| `Converters.java` | `Date ↔ Long`, `TransactionType ↔ String`, `WalletType ↔ String` |

### ✅ Cập nhật trạng thái hiện tại
- Room DB hiện đã lên **version 7**
- `budgets` hiện đã mở rộng theo `userId`, `walletId`, `startDate`, `endDate`

### ✅ Kiểm tra Phase 0
- [x] `./gradlew assembleDebug` không có lỗi
- [x] App launch không crash (MainActivity hiển thị)

---

## PHASE 1 — Authentication (Xác thực)

> **Mục tiêu:** Đăng ký, đăng nhập bằng Email/Password Firebase. Ghi nhớ trạng thái login.

### 1.1 — Remote & Repository

| File | Việc cần làm |
|------|-------------|
| `data/remote/FirebaseAuthHelper.java` | Wrap `FirebaseAuth`: `register(email,pass,cb)`, `login(email,pass,cb)`, `logout()`, `getCurrentUser()`, `sendPasswordReset(email,cb)` |
| `data/repository/AuthRepository.java` | Gọi `FirebaseAuthHelper` + lưu `UserEntity` vào Room sau khi đăng ký/login |
| `data/repository/UserRepository.java` | `getUserByUid(uid): LiveData<UserEntity>`, `updateUser(entity)` |

### ✅ Cập nhật trạng thái hiện tại
- Auth flow đã hoàn chỉnh
- Có thêm logic phục hồi local user record sau khi DB bị recreate

### ✅ Kiểm tra Phase 1
- [x] Đăng ký tài khoản mới thành công
- [x] Đăng nhập với tài khoản đã tạo
- [x] Mở lại app → vào thẳng Home (không hỏi login lại)
- [x] Sai mật khẩu → hiển thị lỗi rõ ràng

---

## PHASE 2 — Wallet Management (Quản lý Ví)

> **Mục tiêu:** CRUD đầy đủ cho ví. Đây là prerequisite của Transaction.

### 2.1 — Repository

| File | Việc cần làm |
|------|-------------|
| `data/repository/WalletRepository.java` | `getAllWallets(uid): LiveData`, `addWallet`, `updateWallet`, `deleteWallet`, `getWalletById` |

### ✅ Cập nhật trạng thái hiện tại
- Wallet CRUD đã ổn định
- Input/validation đã được chuẩn hóa thêm để tránh lỗi khi lưu

### ✅ Kiểm tra Phase 2
- [x] Thêm ví mới
- [x] Sửa tên ví
- [x] Xóa ví (với confirm dialog)
- [x] Số dư hiển thị đúng format tiền tệ

---

## PHASE 3 — Category Management (Quản lý Danh mục)

> **Mục tiêu:** Seed danh mục mặc định, CRUD danh mục tùy chỉnh.

### 3.1 — Default Categories Seed

| File | Việc cần làm |
|------|-------------|
| `utils/Constants.java` | Thêm list `DEFAULT_CATEGORIES` |
| `di/AppContainer.java` hoặc `MoneyMateApplication.java` | Sau login: seed default categories vào Room nếu chưa có |

### ✅ Cập nhật trạng thái hiện tại
- Default categories hiện có 16 mục
- Đã bổ sung icon XML cho toàn bộ seed mặc định
- Đã có icon picker trong màn add/edit category
- Có category ảo riêng phục vụ budget: `Các mục khác`

### ✅ Kiểm tra Phase 3
- [x] Default categories tự động xuất hiện sau login / app start
- [x] Thêm danh mục tùy chỉnh
- [x] Không thể xóa danh mục mặc định
- [x] Có thể xóa danh mục tự tạo

---

## PHASE 4 — Transaction Management (Quản lý Giao dịch)

> **Mục tiêu:** CRUD giao dịch — core feature của app.  
> **Prerequisite:** Phase 2 (Wallet) + Phase 3 (Category) phải xong.

### 4.1 — Repository

| File | Việc cần làm |
|------|-------------|
| `data/repository/TransactionRepository.java` | `getAllTransactions(uid): LiveData`, `add`, `update`, `delete`, `getByDateRange`, `getTotalByType(uid,month,year)` |

### ✅ Cập nhật trạng thái hiện tại
- Hỗ trợ ghi chú tiếng Việt
- Format số tiền realtime kiểu Việt
- Có thêm query read-only phục vụ Budget detail / aggregate / `Các mục khác`

### ✅ Kiểm tra Phase 4
- [x] Thêm giao dịch Chi → balance ví giảm đúng
- [x] Thêm giao dịch Thu → balance ví tăng
- [x] Sửa số tiền giao dịch → balance ví cập nhật đúng
- [x] Xóa giao dịch → balance ví được hoàn lại
- [x] Danh sách giao dịch cập nhật real-time

---

## PHASE 5 — Home Dashboard (Trang chủ)

> **Mục tiêu:** Màn hình tổng quan hiển thị số dư, thu/chi tháng này, giao dịch gần đây.

### ✅ Cập nhật trạng thái hiện tại
- Home dashboard đã có flow cơ bản
- Bottom navigation hiện tại là `Home / Transactions / Budgets / Settings`

### ✅ Kiểm tra Phase 5
- [x] Tổng số dư = tổng balance của tất cả ví
- [x] Thu/Chi tháng tính đúng tháng hiện tại
- [x] Bottom navigation hoạt động

---

## PHASE 6 — Budget Management (Ngân sách)

> **Mục tiêu:** Thiết lập ngân sách theo danh mục, cảnh báo khi vượt.

### 6.1 — Repository

| File | Việc cần làm |
|------|-------------|
| `data/repository/BudgetRepository.java` | `getBudgets`, `add`, `update`, `softDelete`, `getSpentByCategory(...)` |

### 6.2 — UI Budget

| File | Việc cần làm |
|------|-------------|
| `ui/budget/BudgetViewModel.java` | Expose list theo tab/filter, summary, detail model |
| `ui/budget/BudgetListFragment.java` | Header + wallet filter + TabLayout + empty state + RecyclerView |
| `ui/budget/BudgetFinishedFragment.java` | Danh sách ngân sách đã kết thúc |
| `ui/budget/BudgetDetailFragment.java` | Chi tiết ngân sách + chart + thống kê + transaction list |
| `ui/budget/AddEditBudgetFragment.java` | Chọn danh mục, ví, khoảng ngày, số tiền |
| `ui/budget/BudgetWalletPickerFragment.java` | Chọn ví để lọc ngân sách |

### 6.3 — Budget Logic

| Nơi thực hiện | Logic |
|---------------|-------|
| `BudgetViewModel.java` | phân tab `Tháng này / Tương lai / Thời gian khác`, tính summary, wallet filter |
| `TransactionDao.java` | query tổng chi / transaction list cho budget thường, aggregate và `Các mục khác` |
| `BudgetStatisticsCalculator.java` | tính `Actual Daily Average`, `Recommended Daily Spend`, `Projected Total Spend` |

### ✅ Cập nhật trạng thái hiện tại
- **Phase 6 đã hoàn thành**
- Đã có:
  - running budgets + finished budgets
  - wallet filter cho cả 2 màn
  - wallet picker
  - 3 tab thời gian
  - budget detail + chart
  - `Tất cả danh mục`
  - `Các mục khác`
  - empty state + redirect tạo ví
  - transaction list theo budget
- Đảm bảo:
  - xóa budget không xóa transaction
  - budget quá khứ vẫn cập nhật spent nếu transaction mới nằm trong khoảng ngày của budget

### ✅ Kiểm tra Phase 6
- [x] Tạo ngân sách thường / tổng / các mục khác
- [x] Thêm giao dịch → spentAmount cập nhật
- [x] ProgressBar phản ánh đúng % đã dùng
- [x] Wallet filter hoạt động trên cả running và finished budgets
- [x] Budget detail mở đúng transaction list

---

## PHASE 7 — Statistics (Thống kê)

> **Mục tiêu:** Biểu đồ trực quan thu/chi theo danh mục và theo thời gian.

### 7.1 — ViewModel & Data

| File | Việc cần làm |
|------|-------------|
| `ui/statistics/StatisticsViewModel.java` | `pieChartData: LiveData<List<PieEntry>>`, `barChartData: LiveData<BarData>`, `selectedMonth/Year`, methods để filter |

### 7.2 — UI Statistics

| File | Việc cần làm |
|------|-------------|
| `ui/statistics/StatisticsFragment.java` | Month picker, Tab "Chi tiêu" / "Thu nhập", PieChart danh mục, BarChart 6 tháng gần nhất, Legend danh mục + % |

### ✅ Cập nhật trạng thái hiện tại
- Đây là **mục tiêu tiếp theo**
- Có thể tái sử dụng logic aggregate / filter thời gian / formatter từ Budget

### ✅ Kiểm tra Phase 7
- [ ] PieChart hiển thị đúng tỷ lệ chi tiêu theo danh mục
- [ ] Chuyển tháng → biểu đồ cập nhật
- [ ] BarChart so sánh 6 tháng gần nhất
- [ ] Không crash khi không có dữ liệu

---

## PHASE 8 — Profile & Settings (Hồ sơ & Cài đặt)

> **Mục tiêu:** Cá nhân hóa, dark mode, quản lý tài khoản.

### ✅ Cập nhật trạng thái hiện tại
- Đã có nền tảng màn hình và navigation
- Settings hiện là điểm vào của Wallets, Categories, Budgets, Statistics, Debts, Events

---

## PHASE 9 — Passcode Login (Đăng nhập bằng Passcode)

> **Mục tiêu:** Đăng nhập nhanh bằng mã 6 số, hoạt động offline.

### ✅ Cập nhật trạng thái hiện tại
- Có nền tảng package / màn hình
- Chưa phải priority hiện tại

---

## PHASE 10 — Polish & QA

> **Mục tiêu:** Xử lý edge cases, UX hoàn thiện, không còn crash.

### ✅ Cập nhật trạng thái hiện tại
- Budget đã trải qua một vòng QA/polish sâu
- Các module còn lại tiếp tục polish theo nhu cầu thực tế

---

## Thứ tự dependencies giữa các Phase

```
Phase 0 (Foundation)
    ↓
Phase 1 (Auth) ────────────────────────────────────┐
    ↓                                              │
Phase 2 (Wallet) ──── cần cho Phase 4              │
    ↓                        ↓                     │
Phase 3 (Category) ──→ Phase 4 (Transaction)       │
                              ↓                    │
                         Phase 5 (Home)            │
                              ↓                    │
                    ┌─────────┴──────────┐         │
               Phase 6 (Budget)    Phase 7 (Stats) │
                    └─────────┬──────────┘         │
                         Phase 8 (Settings) ←──────┘
                              ↓
                         Phase 9 (Passcode)
                              ↓
                         Phase 10 (Polish)
```

---

## Ước tính thời gian

| Phase | Độ phức tạp | Ước tính |
|-------|-------------|----------|
| 0 | ⭐⭐ | 1 ngày |
| 1 | ⭐⭐⭐ | 1–2 ngày |
| 2 | ⭐⭐ | 1 ngày |
| 3 | ⭐⭐ | 1 ngày |
| 4 | ⭐⭐⭐⭐ | 2–3 ngày |
| 5 | ⭐⭐ | 1 ngày |
| 6 | ⭐⭐⭐ | 1–2 ngày |
| 7 | ⭐⭐⭐ | 1–2 ngày |
| 8 | ⭐⭐ | 1 ngày |
| 9 | ⭐⭐⭐ | 1–2 ngày |
| 10 | ⭐⭐⭐ | 2–3 ngày |
| **Tổng** | | **~14–19 ngày** |
