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
| `AppDatabase.java` | `@Database(entities={...}, version=1)`, singleton `getInstance()`, `Converters` |
| `Converters.java` | `Date ↔ Long`, `TransactionType ↔ String`, `WalletType ↔ String` |

### 0.2 — Models / Enums

| File | Nội dung |
|------|----------|
| `models/TransactionType.java` | `enum { INCOME, EXPENSE }` |
| `models/WalletType.java` | `enum { CASH, BANK, E_WALLET }` |

### 0.3 — DI Container

| File | Việc cần làm |
|------|-------------|
| `di/MoneyMateApplication.java` | Extend `Application`, khởi tạo `AppContainer` |
| `di/AppContainer.java` | Giữ instance: `AppDatabase`, tất cả DAO, tất cả Repository |

### 0.4 — Utilities

| File | Việc cần làm |
|------|-------------|
| `utils/PrefsManager.java` | SharedPrefs wrapper: `saveUid`, `getUid`, `savePasscode`, `getPasscode`, `isLoggedIn`, `clear` |
| `utils/Constants.java` | Hằng số: Prefs keys, default categories list, currency symbols |
| `utils/CurrencyFormatter.java` | `format(amount, currency)` → `"250.000 ₫"` |
| `utils/DateUtils.java` | `formatDate`, `getCurrentMonth`, `getCurrentYear`, `toTimestamp`, `fromTimestamp` |

### ✅ Kiểm tra Phase 0
- [ ] `./gradlew assembleDebug` không có lỗi
- [ ] App launch không crash (MainActivity hiển thị)

---

## PHASE 1 — Authentication (Xác thực)

> **Mục tiêu:** Đăng ký, đăng nhập bằng Email/Password Firebase. Ghi nhớ trạng thái login.

### 1.1 — Remote & Repository

| File | Việc cần làm |
|------|-------------|
| `data/remote/FirebaseAuthHelper.java` | Wrap `FirebaseAuth`: `register(email,pass,cb)`, `login(email,pass,cb)`, `logout()`, `getCurrentUser()`, `sendPasswordReset(email,cb)` |
| `data/repository/AuthRepository.java` | Gọi `FirebaseAuthHelper` + lưu `UserEntity` vào Room sau khi đăng ký/login |
| `data/repository/UserRepository.java` | `getUserByUid(uid): LiveData<UserEntity>`, `updateUser(entity)` |

### 1.2 — UI Auth

| File | Việc cần làm |
|------|-------------|
| `ui/auth/AuthViewModel.java` | `loginResult: MutableLiveData<Result>`, `registerResult`, gọi `AuthRepository` |
| `ui/auth/LoginActivity.java` | Host cho `nav_auth.xml`; check `PrefsManager.isLoggedIn()` → skip to HomeActivity |
| `ui/auth/LoginFragment.java` | Form email + password, nút Login, nút "Chưa có tài khoản?", observe `AuthViewModel` |
| `ui/auth/RegisterFragment.java` | Form email + password + confirm, gọi `AuthViewModel.register()`, sau thành công → LoginFragment |

### 1.3 — Navigation & Layouts

| File | Việc cần làm |
|------|-------------|
| `res/navigation/nav_auth.xml` | `LoginFragment` (start) → `RegisterFragment` |
| `res/layout/activity_login.xml` | `FragmentContainerView` cho nav_auth |
| `res/layout/fragment_login.xml` | EditText email, EditText password, Button Login, TextView Register |
| `res/layout/fragment_register.xml` | EditText email, password, confirm, Button Register |

### 1.4 — MainActivity Router

| File | Việc cần làm |
|------|-------------|
| `MainActivity.java` | Check `PrefsManager.isLoggedIn()` → `startActivity(HomeActivity)` hoặc `LoginActivity` |

### ✅ Kiểm tra Phase 1
- [ ] Đăng ký tài khoản mới thành công
- [ ] Đăng nhập với tài khoản đã tạo
- [ ] Mở lại app → vào thẳng Home (không hỏi login lại)
- [ ] Sai mật khẩu → hiển thị lỗi rõ ràng

---

## PHASE 2 — Wallet Management (Quản lý Ví)

> **Mục tiêu:** CRUD đầy đủ cho ví. Đây là prerequisite của Transaction.

### 2.1 — Repository

| File | Việc cần làm |
|------|-------------|
| `data/repository/WalletRepository.java` | `getAllWallets(uid): LiveData`, `addWallet`, `updateWallet`, `deleteWallet`, `getWalletById` |

### 2.2 — UI Wallet

| File | Việc cần làm |
|------|-------------|
| `ui/wallet/WalletViewModel.java` | Expose `wallets: LiveData<List<WalletEntity>>`, `addWallet(entity)`, `deleteWallet`, `updateWallet` |
| `ui/wallet/WalletListFragment.java` | RecyclerView danh sách ví, FAB thêm ví, swipe-to-delete |
| `ui/wallet/WalletAdapter.java` | Bind `WalletEntity` → item view; click listener |
| `ui/wallet/AddEditWalletFragment.java` | Form: tên ví, loại (Spinner: Tiền mặt/Ngân hàng/Ví điện tử), số dư ban đầu |

### 2.3 — Layouts

| File | Việc cần làm |
|------|-------------|
| `res/layout/fragment_wallet_list.xml` | RecyclerView + FAB |
| `res/layout/fragment_add_edit_wallet.xml` | EditText name, Spinner type, EditText balance, Button Save |
| `res/layout/item_wallet.xml` | Icon loại ví, tên ví, số dư, menu (sửa/xóa) |

### ✅ Kiểm tra Phase 2
- [ ] Thêm ví mới (Tiền mặt với số dư 500.000đ)
- [ ] Sửa tên ví
- [ ] Xóa ví (với confirm dialog)
- [ ] Số dư hiển thị đúng format tiền tệ

---

## PHASE 3 — Category Management (Quản lý Danh mục)

> **Mục tiêu:** Seed danh mục mặc định, CRUD danh mục tùy chỉnh.

### 3.1 — Default Categories Seed

| File | Việc cần làm |
|------|-------------|
| `utils/Constants.java` | Thêm list `DEFAULT_CATEGORIES`: [{name:"Ăn uống", icon:"ic_food", type:EXPENSE}, {name:"Di chuyển",...}, {name:"Lương", type:INCOME}, ...] (ít nhất 10 mục) |
| `di/AppContainer.java` hoặc `MoneyMateApplication.java` | Sau login: seed default categories vào Room nếu chưa có |

### 3.2 — Repository

| File | Việc cần làm |
|------|-------------|
| `data/repository/CategoryRepository.java` | `getAllCategories(uid): LiveData`, `addCategory`, `updateCategory`, `deleteCategory` (chỉ non-default), `seedDefaults(uid)` |

### 3.3 — UI Category

| File | Việc cần làm |
|------|-------------|
| `ui/category/CategoryViewModel.java` | Expose `categories: LiveData`, CRUD methods, filter by `INCOME`/`EXPENSE` |
| `ui/category/CategoryListFragment.java` | Tab INCOME / EXPENSE, RecyclerView, FAB thêm, swipe-delete (chỉ custom) |
| `ui/category/CategoryAdapter.java` | Hiện icon + tên + badge "Mặc định" nếu `isDefault=true` |
| `ui/category/AddEditCategoryFragment.java` | EditText tên, chọn icon (GridView icon picker), RadioGroup Thu/Chi |

### 3.4 — Layouts

| File | Việc cần làm |
|------|-------------|
| `res/layout/fragment_category_list.xml` | TabLayout (Thu/Chi) + RecyclerView + FAB |
| `res/layout/fragment_add_edit_category.xml` | EditText name, icon picker, RadioGroup |
| `res/layout/item_category.xml` | Icon, tên, badge default |

### ✅ Kiểm tra Phase 3
- [ ] Default categories tự động xuất hiện sau login
- [ ] Thêm danh mục tùy chỉnh "Học phí" loại Chi
- [ ] Không thể xóa danh mục mặc định
- [ ] Có thể xóa danh mục tự tạo

---

## PHASE 4 — Transaction Management (Quản lý Giao dịch)

> **Mục tiêu:** CRUD giao dịch — core feature của app.  
> **Prerequisite:** Phase 2 (Wallet) + Phase 3 (Category) phải xong.

### 4.1 — Repository

| File | Việc cần làm |
|------|-------------|
| `data/repository/TransactionRepository.java` | `getAllTransactions(uid): LiveData`, `add` (→ cập nhật balance ví), `update`, `delete` (→ hoàn balance ví), `getByDateRange`, `getTotalByType(uid,month,year)` |

### 4.2 — UI Transaction

| File | Việc cần làm |
|------|-------------|
| `ui/transaction/TransactionViewModel.java` | Expose `transactions: LiveData`, CRUD, `totalIncome/totalExpense: LiveData` |
| `ui/transaction/TransactionListFragment.java` | RecyclerView giao dịch nhóm theo ngày, FAB thêm, search bar |
| `ui/transaction/TransactionAdapter.java` | Hiện: icon danh mục, tên DM, số tiền (xanh/đỏ), ngày, ghi chú |
| `ui/transaction/AddEditTransactionFragment.java` | Form: số tiền (keypad lớn), Thu/Chi toggle, chọn danh mục, chọn ví, chọn ngày (DatePicker), ghi chú |

### 4.3 — Layouts

| File | Việc cần làm |
|------|-------------|
| `res/layout/fragment_transaction_list.xml` | SearchView + RecyclerView + FAB |
| `res/layout/fragment_add_edit_transaction.xml` | Amount input lớn, toggle INCOME/EXPENSE, Spinner category, Spinner wallet, date, note |
| `res/layout/item_transaction.xml` | Category icon, name, note, amount (màu), date |

### 4.4 — Logic quan trọng
- Khi **thêm** giao dịch EXPENSE → `walletBalance -= amount`
- Khi **thêm** giao dịch INCOME → `walletBalance += amount`
- Khi **xóa** giao dịch → **hoàn tác** thay đổi balance
- Khi **sửa** giao dịch → tính lại diff và cập nhật balance

### ✅ Kiểm tra Phase 4
- [ ] Thêm giao dịch Chi 50.000đ → balance ví giảm 50.000
- [ ] Thêm giao dịch Thu 1.000.000đ → balance ví tăng
- [ ] Sửa số tiền giao dịch → balance ví cập nhật đúng
- [ ] Xóa giao dịch → balance ví được hoàn lại
- [ ] Danh sách giao dịch cập nhật real-time

---

## PHASE 5 — Home Dashboard (Trang chủ)

> **Mục tiêu:** Màn hình tổng quan hiển thị số dư, thu/chi tháng này, giao dịch gần đây.

### 5.1 — Navigation chính

| File | Việc cần làm |
|------|-------------|
| `ui/main/HomeActivity.java` | `BottomNavigationView` với 4 tab: Home / Giao dịch / Thống kê / Cài đặt |
| `res/navigation/nav_main.xml` | Định nghĩa 4 fragment + back stack |
| `res/menu/bottom_nav_menu.xml` | 4 menu item với icon + label |
| `res/layout/activity_home.xml` | BottomNav + FragmentContainerView |

### 5.2 — Home Fragment

| File | Việc cần làm |
|------|-------------|
| `ui/home/HomeViewModel.java` | `totalBalance: LiveData` (tổng tất cả ví), `monthlyIncome`, `monthlyExpense`, `recentTransactions: LiveData<List>` (5 gần nhất) |
| `ui/home/HomeFragment.java` | Observe ViewModel, cập nhật UI, click "Xem tất cả" → TransactionList |

### 5.3 — Layout Home

| File | Việc cần làm |
|------|-------------|
| `res/layout/fragment_home.xml` | Card tổng số dư (ẩn/hiện), Card Thu tháng + Chi tháng, RecyclerView 5 giao dịch gần nhất, nút "Xem tất cả" |

### ✅ Kiểm tra Phase 5
- [ ] Tổng số dư = tổng balance của tất cả ví
- [ ] Thu/Chi tháng tính đúng tháng hiện tại
- [ ] 5 giao dịch mới nhất hiển thị
- [ ] Bottom navigation hoạt động

---

## PHASE 6 — Budget Management (Ngân sách)

> **Mục tiêu:** Thiết lập ngân sách theo danh mục, cảnh báo khi vượt.

### 6.1 — Repository

| File | Việc cần làm |
|------|-------------|
| `data/repository/BudgetRepository.java` | `getBudgetsForMonth(uid,month,year): LiveData`, `add`, `update`, `delete`, `getSpentByCategory(categoryId,month,year)` |

### 6.2 — UI Budget

| File | Việc cần làm |
|------|-------------|
| `ui/budget/BudgetViewModel.java` | Expose `budgetsWithProgress: LiveData`, tự tính `spentAmount` từ TransactionDao |
| `ui/budget/BudgetListFragment.java` | Month picker, RecyclerView ngân sách với ProgressBar % |
| `ui/budget/BudgetAdapter.java` | Hiện: tên DM, limit, spent, progress bar (đổi màu khi > 80%) |
| `ui/budget/AddEditBudgetFragment.java` | Chọn danh mục (Spinner), nhập hạn mức, chọn tháng/năm |

### 6.3 — Budget Warning Logic

| Nơi thực hiện | Logic |
|---------------|-------|
| `TransactionRepository.java` | Sau khi thêm giao dịch → kiểm tra budget của category đó → nếu spent ≥ 80% limit → post notification/event |
| `utils/BudgetChecker.java` *(mới)* | Helper: `checkBudget(categoryId, month, year, db, callback)` |

### 6.4 — Layouts

| File | Việc cần làm |
|------|-------------|
| `res/layout/fragment_budget_list.xml` | Month picker + RecyclerView |
| `res/layout/fragment_add_edit_budget.xml` | Spinner category, EditText limit, month picker |
| `res/layout/item_budget.xml` | Category icon+name, amount spent/limit, ProgressBar |

### ✅ Kiểm tra Phase 6
- [ ] Tạo ngân sách "Ăn uống" 2.000.000đ/tháng
- [ ] Thêm giao dịch Chi danh mục "Ăn uống" → spentAmount cập nhật
- [ ] ProgressBar phản ánh đúng % đã dùng
- [ ] Màu progress bar đổi đỏ khi > 80%

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

### 7.3 — Layout

| File | Việc cần làm |
|------|-------------|
| `res/layout/fragment_statistics.xml` | Month selector, TabLayout, PieChart (MPAndroidChart), BarChart, tổng Thu/Chi |

### ✅ Kiểm tra Phase 7
- [ ] PieChart hiển thị đúng tỷ lệ chi tiêu theo danh mục
- [ ] Chuyển tháng → biểu đồ cập nhật
- [ ] BarChart so sánh 6 tháng gần nhất
- [ ] Không crash khi không có dữ liệu

---

## PHASE 8 — Profile & Settings (Hồ sơ & Cài đặt)

> **Mục tiêu:** Cá nhân hóa, dark mode, quản lý tài khoản.

### 8.1 — Repository

| File | Việc cần làm |
|------|-------------|
| `data/repository/UserRepository.java` | `getUser(uid)`, `updateDisplayName`, `updateCurrency`, `updateLanguage` |

### 8.2 — UI Profile

| File | Việc cần làm |
|------|-------------|
| `ui/profile/ProfileViewModel.java` | Expose `currentUser: LiveData<UserEntity>`, update methods |
| `ui/profile/ProfileFragment.java` | Hiện avatar (initials), tên, email; nút chỉnh sửa tên; chọn currency (VND/USD/EUR); toggle ẩn số dư |

### 8.3 — UI Settings

| File | Việc cần làm |
|------|-------------|
| `ui/settings/SettingsViewModel.java` | `isDarkMode: LiveData<Boolean>`, `notificationsEnabled` |
| `ui/settings/SettingsFragment.java` | PreferenceFragment hoặc manual: Dark/Light toggle (AppCompatDelegate), link đến Profile/Categories/Budgets/Wallets, nút Đăng xuất, nút Xóa dữ liệu |

### 8.4 — Dark Mode

| Nơi thực hiện | Logic |
|---------------|-------|
| `PrefsManager.java` | `saveDarkMode(bool)`, `isDarkMode(): bool` |
| `MoneyMateApplication.java` | Khi start: đọc prefs → `AppCompatDelegate.setDefaultNightMode(...)` |
| `SettingsFragment.java` | Switch toggle → lưu prefs → apply theme ngay lập tức |

### 8.5 — Layouts

| File | Việc cần làm |
|------|-------------|
| `res/layout/fragment_profile.xml` | Avatar circle, name, email, EditText name, Spinner currency, Switch ẩn số dư |
| `res/layout/fragment_settings.xml` | ListPreference hoặc LinearLayout: Dark mode switch, nav links, Logout, Delete data |

### ✅ Kiểm tra Phase 8
- [ ] Đổi tên hiển thị → lưu vào Room
- [ ] Chuyển Dark mode → toàn app đổi theme
- [ ] Đăng xuất → về LoginActivity, `PrefsManager.clear()`
- [ ] Toggle ẩn số dư → HomeFragment ẩn số dư

---

## PHASE 9 — Passcode Login (Đăng nhập bằng Passcode)

> **Mục tiêu:** Đăng nhập nhanh bằng mã 6 số, hoạt động offline.

### 9.1 — Logic Passcode

| File | Việc cần làm |
|------|-------------|
| `utils/PasscodeUtils.java` *(mới)* | `hash(passcode): String` (SHA-256), `verify(input, hash): boolean` |
| `PrefsManager.java` | `savePasscodeHash(hash)`, `getPasscodeHash()`, `hasPasscode(): boolean` |

### 9.2 — Setup Passcode (sau khi đăng ký)

| File | Việc cần làm |
|------|-------------|
| `ui/auth/SetupPasscodeFragment.java` *(mới)* | PIN input 6 số, confirm PIN, lưu hash qua `PrefsManager` |
| `res/layout/fragment_setup_passcode.xml` | 6 ô tròn + numpad |
| `res/navigation/nav_auth.xml` | Thêm `SetupPasscodeFragment` sau `RegisterFragment` |

### 9.3 — Passcode Login Screen

| File | Việc cần làm |
|------|-------------|
| `ui/auth/PasscodeLoginFragment.java` *(mới)* | PIN input 6 số, verify với hash local → vào Home; nút "Dùng mật khẩu thay thế" |
| `res/layout/fragment_passcode_login.xml` | Greeting + tên user, 6 ô PIN, numpad, nút login khác |

### 9.4 — Router Logic

| Nơi thực hiện | Logic |
|---------------|-------|
| `LoginActivity.java` / `MainActivity.java` | Nếu `hasPasscode()` + biết `uid` → hiện `PasscodeLoginFragment` thay vì `LoginFragment` |

### ✅ Kiểm tra Phase 9
- [ ] Sau đăng ký → bắt tạo passcode
- [ ] Mở app (đã login trước đó) → hiện màn hình passcode
- [ ] Tắt mạng → vẫn vào được app bằng passcode
- [ ] Sai passcode 3 lần → khóa 30 giây

---

## PHASE 10 — Polish & QA

> **Mục tiêu:** Xử lý edge cases, UX hoàn thiện, không còn crash.

### 10.1 — Error Handling
- [ ] Mất mạng → Toast thông báo, disable nút Firebase
- [ ] Form validation: số tiền > 0, tên không rỗng
- [ ] Empty state: hiển thị illustration khi list trống
- [ ] Loading state: ProgressBar khi thao tác async

### 10.2 — UX Improvements
- [ ] Confirm dialog trước khi xóa
- [ ] Snackbar với "Hoàn tác" sau khi xóa giao dịch
- [ ] Format số tiền real-time khi nhập (1000 → 1.000)
- [ ] DatePicker mặc định hôm nay
- [ ] Scroll tự động đến item mới thêm

### 10.3 — Notification (Optional)
- [ ] Budget warning notification khi > 80%
- [ ] Daily reminder notification

### 10.4 — Final Testing Checklist
- [ ] Xoay màn hình không mất dữ liệu (ViewModel survive)
- [ ] Back button hoạt động đúng
- [ ] Không memory leak (LifecycleOwner đúng)
- [ ] Room migration nếu thay đổi schema

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
