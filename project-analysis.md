# Phân Tích Dự Án

## 1. Tổng quan kiến trúc

- **Mô hình chính:** `MVVM + Repository Pattern` với phân tách package rõ ràng (`ui` -> các lớp ViewModel -> `data/repository` -> `data/local` / `data/remote`).
- **Chiến lược dữ liệu:** `Offline-first` bằng Room (`data/local`) với ý đồ đồng bộ nền qua worker (`workers/SyncWorker`, `workers/SyncScheduler`) và tích hợp Firebase (`data/remote/FirebaseAuthHelper`, Firestore được suy ra từ ngữ cảnh dự án).
- **Dependency Injection:** DI thủ công qua `di/AppContainer` và khởi tạo cấp ứng dụng trong `di/MoneyMateApplication`.
- **Điều hướng/UI:** Cấu trúc nhiều activity (`MainActivity`, `LoginActivity`, `HomeActivity`, `PasscodeActivity`) kết hợp Jetpack Navigation graph (`nav_auth`, `nav_main`, `nav_passcode`).
- **Ước lượng kiểu kiến trúc:** Chưa phải Clean Architecture đầy đủ (không có package domain/use-case tách riêng). Đây là kiến trúc MVVM phân lớp theo hướng thực dụng.

## 2. Phân rã theo layer

| Layer | Packages | Description |
|---|---|---|
| UI | `com.group10.moneymate.ui.*`, `com.group10.moneymate.MainActivity` | Chứa Fragment/Activity, adapter, custom view cho các màn hình theo feature (auth, budget, category, debt, event, home, settings, statistics, transaction, wallet, AI). |
| ViewModel | `com.group10.moneymate.ui.*.*ViewModel`, `ui.common.Debounceable*ViewModel` | Giữ trạng thái UI bằng LiveData, xử lý logic hiển thị, và ủy quyền thao tác dữ liệu cho repository. |
| Repository | `com.group10.moneymate.data.repository.*` | Điều phối truy cập dữ liệu và áp dụng business rule giữa ViewModel và các data source (Room/Firebase/auth/sync metadata). |
| Data (Local) | `com.group10.moneymate.data.local` (`entity`, `dao`, `dto`, `migrations`, `AppDatabase`, `Converters`) | Chứa schema Room, truy vấn DAO, DTO projection, migration DB và lớp lưu trữ cục bộ. |
| Data (Remote) | `com.group10.moneymate.data.remote` | Lớp helper làm việc với dịch vụ từ xa (hiện thấy rõ helper cho auth), đóng vai trò biên tích hợp cloud. |
| DI | `com.group10.moneymate.di` | Đồ thị phụ thuộc DI thủ công qua AppContainer, bootstrap Application và wiring WorkerFactory. |
| Utils | `com.group10.moneymate.utils`, `com.group10.moneymate.models` | Helper dùng chung, hằng số, formatter, validator và các kiểu model/enums. |
| Workers | `com.group10.moneymate.workers` | Tác vụ nền và cơ chế lập lịch/thử lại cho luồng đồng bộ và quét hóa đơn AI. |

## 3. Điểm vào (Entry Points)

- `app/src/main/java/com/group10/moneymate/MainActivity.java`: Router khi khởi động; quyết định đi auth/passcode/home.
- `app/src/main/java/com/group10/moneymate/ui/auth/LoginActivity.java`: Activity host cho auth graph.
- `app/src/main/java/com/group10.moneymate/ui/main/HomeActivity.java`: Activity host chính sau đăng nhập với điều hướng bottom navigation.
- `app/src/main/java/com/group10.moneymate/ui/security/PasscodeActivity.java`: Activity host cho luồng xác thực passcode.
- `app/src/main/java/com/group10.moneymate/di/MoneyMateApplication.java`: Khởi tạo cấp ứng dụng và sở hữu DI container.
- `app/src/main/res/navigation/nav_auth.xml`: Navigation graph cho luồng auth (login/register/forgot password).
- `app/src/main/res/navigation/nav_main.xml`: Navigation graph chính trong app (home/transactions/budgets/settings/features).
- `app/src/main/res/navigation/nav_passcode.xml`: Navigation graph dành riêng cho passcode.
- `app/src/main/AndroidManifest.xml`: Nguồn khai báo entry runtime và đăng ký component.

## 4. Luồng tổng quan (Ước lượng)

`UI (Activity/Fragment)` -> `ViewModel` -> `Repository` -> `Room DAO (ưu tiên local trước)` -> `Workers đồng bộ dữ liệu chờ lên remote (Firebase/Firestore)` -> `UI quan sát cập nhật qua LiveData`

## 4.1 Dependency Map

`UI (Fragment/Activity)`
  -> `ViewModel`
  -> `Repository`
    -> `Local (Room DAO)`
    -> `Remote (Firebase)`

### Hướng phụ thuộc (dependency direction)

- UI (`ui/*`) phụ thuộc vào ViewModel qua `ViewModelProvider`, observe `LiveData`, và phát sự kiện người dùng (click/save/filter).
- ViewModel (`*ViewModel.java`) phụ thuộc vào Repository thông qua DI từ `AppContainer` (qua `MoneyMateApplication`).
- Repository (`data/repository/*`) phụ thuộc vào data source local (`data/local/dao/*`) và một phần remote/helper (`data/remote/*`) theo use case.
- Local (Room) không phụ thuộc ngược lên Repository/ViewModel/UI; chỉ cung cấp API dữ liệu (DAO/Entity/DTO).
- Remote (Firebase) được gọi qua boundary repository/worker; UI và ViewModel không nên gọi trực tiếp.

## 4.2 Transaction Flow

User Action
 -> Fragment
 -> ViewModel
 -> Repository
 -> DAO
 -> Database

### Data flow theo màn hình

- **Transaction list (đọc dữ liệu):** `TransactionListFragment.onViewCreated()` gọi `observeTransactions()` -> observe `viewModel.getAllTransactions()`.
- **Khởi tạo danh sách:** `TransactionViewModel` gọi `resetPagination()` trong constructor -> `loadNextPage()` -> `transactionRepository.getFirstTransactionsPage(userId, PAGE_SIZE, callback)`.
- **Phân trang tiếp:** `TransactionListFragment.setupPagination()` gọi `viewModel.loadNextPage()` -> `transactionRepository.getTransactionsPageByCursor(...)`.
- **Add/Edit load by id:** `AddEditTransactionFragment.loadExistingTransaction()` -> `viewModel.getTransactionById(transactionId)` -> `transactionRepository.getTransactionById(id)` -> `transactionDao.getTransactionById(id)`.
- **Lưu Add/Edit:** `AddEditTransactionFragment.setupSaveButton()` tạo/cập nhật `TransactionEntity` -> `viewModel.insertTransaction(...)` hoặc `viewModel.updateTransaction(...)` -> `TransactionRepository.upsertTransactionInternal(...)` -> `transactionDao.upsertLocal(...)` -> bảng `transactions` trong Room.
- **Soft delete (nếu được gọi):** `viewModel.deleteTransaction(...)` -> `transactionRepository.softDeleteTransaction(...)` -> `transactionDao.softDelete(id, updatedAt)` (set `is_deleted = 1`, `sync_status = 2`).

## Function Chain

- `TransactionListFragment.observeTransactions()`
  -> `TransactionViewModel.getAllTransactions()`
  -> `TransactionViewModel.loadNextPage()`
  -> `TransactionRepository.getFirstTransactionsPage()` / `getTransactionsPageByCursor()`
  -> `TransactionDao.getFirstTransactionsPageSync()` / `getTransactionsPagedByCursorSync()`
  -> `AppDatabase (transactions table)`

- `AddEditTransactionFragment.loadExistingTransaction()`
  -> `TransactionViewModel.getTransactionById()`
  -> `TransactionRepository.getTransactionById()`
  -> `TransactionDao.getTransactionById()`
  -> `AppDatabase (transactions table)`

- `AddEditTransactionFragment.setupSaveButton()`
  -> `TransactionViewModel.insertTransaction()` / `updateTransaction()`
  -> `TransactionRepository.upsertTransactionInternal()`
  -> `TransactionDao.upsertLocal()`
  -> `TransactionDao.upsertLocalInternal()`
  -> `AppDatabase (transactions table)`

- Sau khi write local thành công:
  -> `TransactionRepository.scheduleSyncIfEnabled()`
  -> `SyncScheduler.scheduleOneTimeSyncDebounced()` (đẩy bước sync nền, không gọi Firebase trực tiếp trong chain này)

### Notes

- **Read path tách 2 kiểu rõ ràng:** list dùng phân trang cursor (`timestamp + id`) thay vì load all; detail/edit dùng lookup theo `transactionId`.
- **Write path chuẩn offline-first:** insert/update/soft delete đều ghi Room trước, set `sync_status` phù hợp, rồi mới trigger one-time sync.
- **Đảm bảo invalidation ổn định:** `TransactionRepository` dùng `LAST_WRITE_TIMESTAMP` để giữ `updated_at` tăng đơn điệu, giảm nguy cơ miss refresh khi nhiều write cùng millisecond.
- **Query nhất quán theo dữ liệu active:** phần lớn query đọc có điều kiện `is_deleted = 0` và join kiểm tra `wallets.is_deleted = 0`, tránh hiển thị transaction trỏ tới wallet đã xóa.
- **DTO/statistics query dồn ở DAO:** ngoài CRUD, `TransactionDao` còn là điểm tổng hợp báo cáo (`CategorySumDTO`, `DailyTrendDTO`, `NetIncomeDTO`), làm transaction layer kiêm cả analytical workload.
- **Coupling đáng chú ý:** `TransactionRepository` nhận cả `WalletDao`; trong luồng write hiện tại không thấy cập nhật số dư ví trực tiếp tại repository như pattern mô tả ban đầu.

### Open Questions / Uncertain Areas

- `TransactionDao.getPendingSyncSince(...)` và `getPendingSyncTransactionsPagedSince(...)` cùng tồn tại; cần chốt API canonical để tránh drift/nhầm giữa hai luồng pending-sync.
- Một số query aggregate loại `sync_status = 2`, trong khi query list cơ bản chủ yếu dùng `is_deleted = 0`; cần xác nhận chuẩn lọc thống nhất cho report (dựa theo `is_deleted` hay `sync_status`).
- Thiết kế hiện tại để `TransactionDao` gánh nhiều query thống kê có thể làm DAO phình to; có cần tách read-model/report DAO riêng để giảm coupling và dễ tối ưu hiệu năng không?

## 4.3 Budget Flow

### Main Flow
User Action
 -> Fragment
 -> ViewModel
 -> Repository
 -> DAO
 -> Database

### Function Chain (Estimated)

- **List budgets (`BudgetListFragment`):**
  `BudgetListFragment.observeViewModel()`
  -> `BudgetViewModel.getActiveBudgets()` / `getSummary()` / `getHasAnyBudgets()`
  -> `BudgetViewModel` khởi tạo `budgetSource = budgetRepository.getAllBudgets(userId)`
  -> `BudgetRepository.getAllBudgets(userId)`
  -> `BudgetDao.getAllBudgets(userId)`
  -> `Room table budgets`

- **Detail budget (`BudgetDetailFragment` - single item):**
  `BudgetDetailFragment.observeData()`
  -> `BudgetViewModel.getBudgetUiModel(budgetId)`
  -> `BudgetRepository.getBudgetById(userId, budgetId)` + `TransactionRepository.getTotalExpenseByCategory(...)`
  -> `BudgetDao.getBudgetById(...)` + `TransactionDao` aggregate query
  -> `Room tables budgets + transactions`

- **Create budget (`AddEditBudgetFragment`):**
  `AddEditBudgetFragment.saveBudget()`
  -> `AddEditBudgetFragment.performSaveBudget()`
  -> `AddEditBudgetViewModel.addBudget(...)`
  -> `BudgetRepository.addBudget(budgetEntity, callback)`
  -> `appDatabase.runInTransaction { validateManualInsert; insertBudgetInternal; syncOtherCategoriesBudget }`
  -> `BudgetDao.upsertLocal(...)` (+ queries count/sum/get... cho rule)
  -> `Room table budgets`

- **Update budget (`AddEditBudgetFragment`):**
  `AddEditBudgetViewModel.updateBudget(...)`
  -> `BudgetRepository.updateBudget(..., callback)`
  -> `appDatabase.runInTransaction { getBudgetByIdSync; validateManualUpdate; updateBudgetInternal; syncOtherCategoriesBudget }`
  -> `BudgetDao.upsertLocal(...)`
  -> `Room table budgets`

- **Delete budget (`BudgetDetailFragment`):**
  `BudgetDetailFragment.deleteBudget(...)`
  -> `BudgetViewModel.deleteBudget(budgetEntity)`
  -> `BudgetRepository.softDeleteBudget(userId, id)`
  -> `BudgetDao.softDelete(userId, id, updatedAt)`
  -> `Room table budgets` (soft delete: `is_deleted = 1`, `sync_status = 2`)

- **Sau write local:**
  `BudgetRepository.scheduleSyncIfEnabled()`
  -> `SyncScheduler.scheduleOneTimeSyncDebounced()`

### Notes

- **Data truyền qua các layer (chính):** `budgetId`, `userId`, `categoryId` (nullable = all categories), `walletId` (nullable = total scope), `amount`, `startDate`, `endDate`, `updatedAt`, `syncStatus`, `isDeleted`.
- **Rule/business logic nằm ở `BudgetRepository`:**
  - Chặn tạo/sửa budget thủ công cho category `VIRTUAL_OTHER*` (`BudgetRuleException.OTHER_CATEGORY_MANUAL_NOT_ALLOWED`).
  - Chặn trùng budget all-categories cùng scope thời gian + wallet (`ALL_CATEGORIES_ALREADY_EXISTS`).
  - Tự đồng bộ budget “Other categories” bằng `syncOtherCategoriesBudget()` dựa trên tổng budget cụ thể.
- **Logic tính toán/UI nằm ở UI layer:**
  - `BudgetViewModel`: partition tab (`THIS_MONTH/FUTURE/CUSTOM`), build `BudgetUIModel`, tổng hợp `BudgetSummaryUIModel`.
  - `BudgetDetailFragment` + `BudgetStatisticsCalculator`: tính progress/recommended daily/projected spending.
  - `BudgetUiUtils`: format tiền/ngày, icon resolve, active-day checks.
- **DTO/model thực sự được dùng trong flow này:**
  - `WalletWithBalance` (dropdown ví trong Add/Edit).
  - `BudgetUIModel`, `BudgetSummaryUIModel` (presentation models trong `BudgetViewModel`).
  - `Constants` (category special IDs/type).
  - `SyncStatus` (set ở repository/entity khi write).
- **Coupling đáng chú ý:**
  - `BudgetViewModel` phụ thuộc đồng thời `BudgetRepository`, `CategoryRepository`, `TransactionRepository`, `WalletRepository` (coupling ngang cao để dựng UI model tổng hợp).
  - `AddEditBudgetFragment` gọi trực tiếp `appContainer.walletRepository.getTotalBalance(userId)` ngoài ViewModel (UI dính vào tầng DI/repository).

### Open Questions / Uncertain Areas

- `BudgetDetailFragment` dùng chung `BudgetViewModel` để lấy cả budget và transaction aggregate; có cần tách `BudgetDetailViewModel` riêng để giảm coupling và tách trách nhiệm không?
- Rule `syncOtherCategoriesBudget()` đang nằm hoàn toàn ở repository; có cần tách thành domain service/use-case để dễ test unit độc lập hơn không?

## 4.4 Category Flow

### Main Flow
User Action
 -> Fragment
 -> ViewModel
 -> Repository
 -> DAO
 -> Database

### Hierarchy Handling
- `CategoryEntity` dùng `parent_id` để biểu diễn quan hệ cha-con; root category có `parent_id = null`.
- Ràng buộc hierarchy nằm ở `CategoryRepository.validateHierarchy*()`:
  - Không cho self-parent (`SELF_PARENT_NOT_ALLOWED`).
  - Parent phải tồn tại (`PARENT_NOT_FOUND`).
  - Giới hạn độ sâu 2 tầng: parent được chọn không được có `parent_id` (`DEPTH_LIMIT_EXCEEDED`).
  - Parent và child phải cùng `type` và cùng wallet scope (`TYPE_MISMATCH`, `WALLET_SCOPE_MISMATCH`).
- Xóa category dùng `CategoryDao.softDeleteCascade(id, updatedAt)` (đánh dấu xóa root và toàn bộ child trực tiếp theo `parent_id = id`).
- Load tree + render tree nằm ở `CategoryListFragment.buildListItems(...)`:
  - Tách danh sách thành `roots` và `childrenByParent`.
  - Map sang `CategoryListItem.group(root, children, walletLabel)` để adapter render dạng hierarchy.
  - Child mồ côi (không tìm thấy root trong list hiện tại) vẫn được render như một group độc lập để tránh mất dữ liệu trên UI.

### Function Chain (Estimated)
- **List categories (`CategoryListFragment`):**
  `CategoryListFragment.observeCategories(type, walletId)`
  -> `CategoryViewModel.getCategoriesByTypeAndWallet(type, walletId)`
  -> `CategoryRepository.getCategoriesByTypeAndWallet(userId, type, walletId)`
  -> `CategoryDao.getCategoriesByTypeAndWallet(...)`
  -> `CategoryListFragment.buildListItems(...)` -> `CategoryAdapter.submitList(...)`

- **Add category (`AddEditCategoryFragment`):**
  `AddEditCategoryFragment.saveCategory()`
  -> `CategoryViewModel.addCategory(...)`
  -> `CategoryRepository.addCategoryValidatedAsync(...)`
  -> `prepareAndValidateForCreate()` + `validateHierarchyForCreate()`
  -> `CategoryDao.upsertLocal(...)`

- **Update category (`AddEditCategoryFragment`):**
  `AddEditCategoryFragment.saveCategory()`
  -> `CategoryViewModel.updateCategory(category)`
  -> `CategoryRepository.updateCategoryValidatedAsync(...)`
  -> `prepareAndValidateForUpdate()` + `validateHierarchyForUpdate()`
  -> `CategoryDao.upsertLocal(...)`

- **Delete category (`AddEditCategoryFragment` / list action result):**
  `CategoryViewModel.deleteCategory(category)`
  -> `CategoryRepository.deleteCategoryValidatedAsync(...)`
  -> `validateDelete()`
  -> `CategoryDao.softDeleteCascade(categoryId, now)`

- **Pick parent (`ParentCategoryPickerFragment`):**
  `ParentCategoryPickerFragment.onViewCreated()`
  -> `viewModel.setSelectedType(type)`
  -> `CategoryViewModel.getCategories()` (`switchMap(selectedType)`)
  -> `CategoryRepository.getCategoriesByType(userId, type)`
  -> `CategoryDao.getCategoriesByType(...)`
  -> `filterCurrentCategory(...)` (loại currentCategory khỏi danh sách chọn parent)

### Notes
- **Filter nằm ở đâu:**
  - Filter theo tab type: `CategoryViewModel.selectedType` + `Transformations.switchMap(...)`.
  - Filter theo wallet scope: `CategoryListFragment` gọi `getCategoriesByTypeAndWallet(type, walletId)`.
  - Filter danh sách parent picker: `ParentCategoryPickerFragment.filterCurrentCategory(...)`.
- **Mapping sang UI model nằm ở đâu:**
  - Không dùng DTO riêng cho category tree.
  - Dùng mapping trực tiếp `CategoryEntity -> CategoryListItem` trong `CategoryListFragment.buildListItems(...)`.
- **Utils/models thực sự dùng trong flow này:**
  - `Constants` (type/category constants), `IconProvider` (resolve icon), `LoadingHelper` (save state UI).
  - `SyncStatus` được set ở repository khi create/update (`PENDING_UPLOAD`).
  - `CategoryRepository.CategoryValidationResult/Error` dùng để trả kết quả validation ngược về UI.
- **Coupling đáng chú ý:**
  - `CategoryListFragment` chứa cả filter logic + tree mapping + wallet label resolution (UI layer gánh khá nhiều presentation logic).
  - `AddEditCategoryFragment` xử lý một phần rule hiển thị/validation message mapping trực tiếp theo `errorKey`.

### Open Questions / Uncertain Areas
- `CategoryListFragment` map key `category.validation.cannot_delete_with_children`, nhưng `CategoryRepository.validateDelete()` hiện chỉ chặn default category và delete dùng cascade; key này còn được phát sinh ở luồng nào?
- `ParentCategoryPickerFragment.filterCurrentCategory(...)` chỉ loại chính nó, không loại descendants; hiện có đủ ràng buộc ở repository để chặn mọi vòng lặp trong tất cả kịch bản migrate data cũ chưa?

## 4.5 Wallet Flow

### Main Flow
User Action
 -> WalletListFragment / AddEditWalletFragment
 -> WalletViewModel
 -> WalletRepository
 -> WalletDao
 -> Database (wallets + transactions for computed balance)

### Balance Calculation
- Balance hiển thị theo wallet item được lấy từ `WalletWithBalance.currentBalance` (alias SQL: `current_balance`), không chỉ từ `WalletEntity.balance`.
- Công thức tính nằm trong `WalletDao` (các query `getAllByUserWithBalance`, `getActiveByUserWithBalance`, `getByIdWithBalance`):
  - Base snapshot: `wallets.balance`
  - Cộng/trừ giao dịch nguồn (`transactions.wallet_id`):
    - `INCOME` => `+amount`
    - `EXPENSE` => `-amount`
    - `TRANSFER` (wallet nguồn) => `-amount`
  - Cộng thêm giao dịch chuyển đến (`transactions.to_wallet_id`, `type = 'TRANSFER'`) => `+amount`
- Total balance header lấy từ `WalletDao.getTotalBalance(userId)`, là tổng các current balance của mọi ví chưa deleted và chưa excluded (`is_excluded = 0`).
- Kết luận: logic tính balance nằm chủ yếu ở DAO (SQL), repository/viewmodel chỉ pass-through LiveData.

### Function Chain (Estimated)
- **List wallets (`WalletListFragment`)**
  `WalletListFragment.onViewCreated()`
  -> `WalletViewModel.getWallets()`
  -> `WalletRepository.getAllByUserWithBalance(userId)`
  -> `WalletDao.getAllByUserWithBalance(userId)`
  -> trả `LiveData<List<WalletWithBalance>>` cho `WalletAdapter`

- **Header total (`WalletListFragment`)**
  `WalletListFragment.onViewCreated()`
  -> `WalletViewModel.getTotalBalance()`
  -> `WalletRepository.getTotalBalance(userId)`
  -> `WalletDao.getTotalBalance(userId)`
  -> trả `LiveData<Double>`

- **Load wallet để edit (`AddEditWalletFragment`)**
  `AddEditWalletFragment.loadEditingWallet(walletId)`
  -> `WalletViewModel.getWalletById(walletId)`
  -> `WalletRepository.getById(walletId)`
  -> `WalletDao.getById(walletId)`
  -> trả `LiveData<WalletEntity>` (raw entity, không kèm computed current_balance)

- **Save Add/Edit (`AddEditWalletFragment`)**
  `AddEditWalletFragment.saveWallet()`
  -> `WalletViewModel.addWallet(...)` / `updateWallet(...)`
  -> tạo/copy `WalletEntity`, set `updatedAt`, `syncStatus=PENDING_UPLOAD`
  -> `WalletRepository.insert(...)` / `update(...)`
  -> `WalletRepository.insertWalletInternal(...)`
  -> `WalletDao.upsertLocal(...)`
  -> `WalletDao.upsertLocalInternal(...)` (INSERT ... ON CONFLICT DO UPDATE)

- **Delete/Archive/Restore (`WalletListFragment` actions)**
  `WalletListFragment.show*ConfirmDialog()`
  -> `WalletViewModel.deleteWallet/archiveWallet/restoreWallet`
  -> `WalletRepository.softDelete/archive/restore`
  -> `WalletDao.softDelete/archive/restore`
  -> update cờ soft-delete/archive và `sync_status`

### Notes
- **Entry points được trace trực tiếp:**
  - `WalletListFragment`: read list + read total + trigger archive/restore/delete
  - `AddEditWalletFragment`: read-by-id để populate form + write add/update
- **Join/query đặc biệt (DTO):**
  - Không dùng JOIN tường minh; dùng correlated subqueries trên bảng `transactions` để tính `current_balance` cho từng wallet.
  - DTO `WalletWithBalance` gồm `@Embedded WalletEntity wallet` + field tổng hợp `currentBalance`.
- **WalletWithBalance dùng ở đâu (khi có tham gia flow):**
  - Wallet feature: `WalletViewModel`, `WalletAdapter`, `WalletListFragment`
  - Home: `HomeViewModel`, `HomeWalletAdapter`, `HomeFragment`
  - Transaction: `TransactionViewModel` (`walletsWithBalance`), `TransactionListFragment` (map wallet và tổng balance theo filter)
  - Budget: `AddEditBudgetViewModel`, `BudgetWalletPickerFragment`, `BudgetWalletPickerAdapter`, `AddEditBudgetFragment`
  - Statistics: `StatisticsViewModel` (map `getByIdWithBalance` -> header balance theo ví đã chọn)
- **Models/utils liên quan trực tiếp trong wallet flow:**
  - `WalletType` (map giữa label UI và enum type của wallet)
  - `SyncStatus` (set trạng thái đồng bộ khi insert/update/delete/archive/restore)
  - `CurrencyFormatter` (parse/format input balance và display)
  - `IconProvider` (resolve iconName để render UI)

### Open Questions / Uncertain Areas
- `AddEditWalletFragment` đang load dữ liệu bằng `getById()` (raw `WalletEntity.balance`), trong khi list/header dùng computed balance từ `WalletWithBalance`; UX mong muốn là edit số dư gốc (snapshot ban đầu) hay số dư hiện tại sau giao dịch?
- `WalletDao.softDelete()` có bước `restoreTransferSourceWalletBalances(...)` để cộng lại balance cho ví nguồn của transfer trước khi mark transaction deleted. Cần xác nhận đây là chiến lược chủ đích để bảo toàn snapshot `wallets.balance`, vì các màn hình đọc hiện tại chủ yếu dùng computed balance từ transactions.

## 4.6 Auth Flow

### Login Flow
User Action
 -> `LoginFragment` (nhập email/password, `btnLogin`)
 -> `AuthViewModel.login(...)`
 -> `AuthRepository.login(...)`
 -> `FirebaseAuthHelper.signInWithEmail(...)`
 -> Firebase Auth (`signInWithEmailAndPassword`)

Khi thành công:
-> `AuthRepository.handleAuthSuccess(...)` ghi local (`users` + `PrefsManager`)
-> `AuthViewModel.AuthState.AUTHENTICATED`
-> `LoginFragment` mở `HomeActivity`.

### Google Sign-In Flow
User Action
 -> `LoginFragment` (tap Google button, `btnGoogleSignIn`)
 -> `AuthViewModel.loginWithGoogle(idToken)`
 -> `AuthRepository.loginWithGoogle(idToken)`
 -> `FirebaseAuthHelper.signInWithGoogleCredential(idToken)`
 -> Firebase Auth (`signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))`)

Khi thành công:
-> `AuthRepository.handleAuthSuccess(...)` ghi local (`users` + `PrefsManager`)
-> `AuthViewModel.AuthState.AUTHENTICATED`
-> `LoginFragment` mở `HomeActivity`.

### Google Account Linking (same email with Email/Password)
- Khi user đã có tài khoản Email/Password và đăng nhập Google cùng email, Firebase hợp nhất theo cùng user (1 email = 1 account) nếu cấu hình Firebase Auth đã bật đúng.
- Ứng dụng vẫn giữ flow local như login thường:
  - cập nhật `UserEntity` trong Room
  - cập nhật `PrefsManager` (`uid`, `is_logged_in`)
- Sau khi link thành công, user có thể đăng nhập lại bằng cả:
  - Email/Password
  - Google provider

### Function Chain (Google)
`LoginFragment.onGoogleSignInResult()`
-> `AuthViewModel.loginWithGoogle(idToken)`
-> `AuthRepository.loginWithGoogle(idToken, callback)`
-> `FirebaseAuthHelper.signInWithGoogleCredential(idToken, callback)`
-> Firebase success
-> `AuthRepository.handleAuthSuccess(...)`
-> `UserDao.getUserByIdSync(...)` + `insertUser(...)`/`updateUser(...)` (background executor)
-> `PrefsManager.saveUid(...)`, `PrefsManager.setLoggedIn(true)`

### Notes (Google)
- `idToken` phải là token mới từ Google Sign-In client; token rỗng/hết hạn sẽ gây `FirebaseAuthInvalidCredentialsException`.
- `AuthRepository` nên log debug theo `tag = AuthRepository` cho các điểm:
  - nhận `idToken` null/rỗng
  - lỗi từ `signInWithCredential`
  - thông tin provider hiện có của user sau đăng nhập thành công
- Google flow dùng chung `handleAuthSuccess(...)` để đảm bảo nhất quán local persistence với Email/Password flow.

### Register Flow
User Action
 -> `RegisterFragment` (`btnRegister`)
 -> `AuthViewModel.register(...)`
 -> `AuthRepository.register(...)`
 -> `FirebaseAuthHelper.signUpWithEmail(...)`
 -> Firebase Auth (`createUserWithEmailAndPassword`)
 -> (nếu có display name) `FirebaseAuthHelper.updateDisplayName(...)`

Khi thành công:
-> `AuthRepository.handleAuthSuccess(...)` ghi local (`users` + `PrefsManager`)
-> `AuthViewModel.AuthState.REGISTERED_NEEDS_PASSCODE`
-> `RegisterFragment` điều hướng Passcode (create/confirm).

### Local Persistence (if any)
- Có lưu local user trong Room (`users`) qua `UserDao`:
  - `AuthRepository.handleAuthSuccess(...)` insert/update `UserEntity` sau login/register thành công.
  - `AuthRepository.ensureLocalUserRecord()` được gọi trong `AppContainer.bootstrapLocalData()` (trigger ở `MoneyMateApplication.onCreate`) để đảm bảo bản ghi local tồn tại.
- Có lưu local auth state trong `PrefsManager`:
  - `uid`, `is_logged_in`, `passcode_hash`, `passcode_uid`, `passcode_enabled`.
- Passcode flow có local-first:
  - `savePasscode(...)`: hash bằng `PasscodeHasher`, lưu cả Prefs + `UserEntity.hashed_passcode`.
  - `verifyPasscode(...)`: ưu tiên Prefs, fallback Room nếu Prefs không còn.
- Logout (`AuthRepository.signOut`) chỉ sign out Firebase + clear `uid/is_logged_in`; không xóa `users` table và không xóa passcode.
- Không thấy cơ chế sync user profile 2 chiều remote/local trong worker hiện tại (SyncWorker không có domain `users`).

### Function Chain (Estimated)
- **Login (`LoginFragment`)**
  `LoginFragment.setupListeners()`
  -> `AuthViewModel.login(email, password)`
  -> `AuthInputValidator.validateLoginInput(...)`
  -> `AuthRepository.login(...)`
  -> `FirebaseAuthHelper.signInWithEmail(...)`
  -> Firebase success
  -> `AuthRepository.handleAuthSuccess(...)`
  -> `UserDao.getUserByIdSync(...)` + `insertUser(...)`/`updateUser(...)` (background executor)
  -> `PrefsManager.saveUid(...)`, `PrefsManager.setLoggedIn(true)`

- **Register (`RegisterFragment`)**
  `RegisterFragment.setupListeners()`
  -> `AuthViewModel.register(...)`
  -> `AuthInputValidator.validateRegisterInput(...)`
  -> `AuthRepository.register(...)`
  -> `FirebaseAuthHelper.signUpWithEmail(...)`
  -> optional `FirebaseAuthHelper.updateDisplayName(...)`
  -> `AuthRepository.handleAuthSuccess(...)`
  -> Room + Prefs update như login
  -> `RegisterFragment.navigateToCreatePasscode()`

- **Logout (entry hiện tại từ Settings)**
  `SettingsFragment.onLogoutClicked()`
  -> `SettingsViewModel.signOut()`
  -> `AuthRepository.signOut()`
  -> `FirebaseAuthHelper.signOut()` + clear local auth flags in Prefs
  -> `SettingsFragment.navigateToLogin()`

- **Forgot password**
  `ForgotPasswordFragment.btnSendResetLink`
  -> `AuthViewModel.sendPasswordResetEmail(email)`
  -> `AuthRepository.sendPasswordResetEmail(...)`
  -> `FirebaseAuthHelper.sendPasswordResetEmail(...)`
  -> Firebase Auth gửi reset email

### Notes
- `AuthViewModel` đang dùng callback từ repository để cập nhật `authState`, chưa chuyển sang sealed-state cho từng phase (loading/success/error) như một số feature khác.
- `AuthRepository.handleAuthSuccess(...)` chạy write Room trên `databaseWriteExecutor` nhưng set Prefs ngay; có thể có race nhỏ giữa điều hướng UI và thời điểm user record local hoàn tất.
- `ensureLocalUserRecord()` chạy ở app startup giúp hạn chế thiếu bản ghi `users`, nhưng có thể tạo thêm truy cập DB sớm ngay khi app mở.

### Open Questions / Uncertain Areas
- `MainActivity` và `LoginActivity` đều có routing logic liên quan trạng thái đăng nhập/passcode; cần xác nhận có muốn gom về một điểm để tránh duplication không?
- `AuthRepository.signOut()` hiện không clear passcode; đây là chủ đích để giữ khóa offline cho lần đăng nhập sau hay cần reset theo account context?

## 4.7 Database Design

### Entities
| Entity | Role |
|---|---|
| `UserEntity` | Bảng `users`: hồ sơ user local + metadata app (`currency`, `language`, `theme_mode`) + `hashed_passcode` fallback. |
| `WalletEntity` | Bảng `wallets`: ví tiền theo user, lưu snapshot balance + trạng thái archive/exclude + sync flags. |
| `CategoryEntity` | Bảng `categories`: danh mục thu/chi, hỗ trợ hierarchy qua `parent_id` và wallet scope qua `wallet_id`. |
| `TransactionEntity` | Bảng `transactions`: ledger giao dịch (income/expense/transfer), có FK sang wallet/category/debt/event và `to_wallet_id` cho transfer. |
| `BudgetEntity` | Bảng `budgets`: ngân sách theo khoảng ngày, category scope (nullable = all categories) và wallet scope (nullable = all wallets). |
| `DebtEntity` | Bảng `debts`: theo dõi cho vay/đi vay, số dư còn lại, trạng thái khoản nợ. |
| `EventEntity` | Bảng `events`: event tài chính theo khoảng thời gian + hạn mức event (`budget_limit`). |
| `SyncMetadataEntity` | Bảng `sync_metadata`: checkpoint đồng bộ theo cặp (`user_id`, `domain`). |

### DAO
| DAO | Handles |
|---|---|
| `UserDao` | CRUD user local theo `id` (LiveData + sync read). |
| `WalletDao` | Upsert wallet, list/filter active, soft delete/archive/restore, aggregate balance (`WalletWithBalance`, total balance), pending sync paging. |
| `CategoryDao` | Upsert category, filter theo `type`/`wallet`/`parent`, tree queries, soft delete cascade, pending sync paging. |
| `TransactionDao` | Upsert transaction, list/filter/search/pagination, stats aggregates (`NetIncomeDTO`, `CategorySumDTO`, `DailyTrendDTO`), budget-related expense queries, soft delete + pending sync paging. |
| `BudgetDao` | Upsert budget theo scope unique key, list/get/sync queries, business support queries (all-categories budget, other-categories budget), soft delete + pending sync. |
| `DebtDao` | Upsert debt, list/filter/status queries, soft delete + pending sync paging. |
| `EventDao` | Upsert event, list/filter active queries, soft delete + pending sync paging. |
| `SyncMetadataDao` | Upsert/update checkpoint đồng bộ theo (`user_id`, `domain`). |

### Notes
- **Entity ↔ table mapping:** 1:1 rõ ràng qua `@Entity(tableName = ...)`; `AppDatabase` đăng ký 8 entities, DB version hiện tại = 13.
- **Quan hệ giữa entities (FK):**
  - `wallets.user_id -> users.id` (CASCADE)
  - `categories.user_id -> users.id` (CASCADE)
  - `transactions.wallet_id -> wallets.id` (CASCADE)
  - `transactions.to_wallet_id -> wallets.id` (SET_NULL)
  - `transactions.category_id -> categories.id` (NO_ACTION)
  - `transactions.debt_id -> debts.id` (SET_NULL)
  - `transactions.event_id -> events.id` (SET_NULL)
  - `budgets.user_id -> users.id` (CASCADE), `budgets.category_id -> categories.id` (NO_ACTION)
  - `debts.user_id -> users.id` (CASCADE)
  - `events.user_id -> users.id` (CASCADE)
- **DTO projection tham gia data layer:**
  - `WalletWithBalance` (wallet + computed `current_balance` từ subquery transactions)
  - `NetIncomeDTO`, `CategorySumDTO`, `DailyTrendDTO` (projection cho thống kê từ `TransactionDao`)
- **Normalization:**
  - Mức chuẩn hóa ở mức trung bình: có FK cho nhiều quan hệ cốt lõi.
  - Có chủ đích denormalize: `transactions.user_id` để tối ưu user-scoped query; `wallets.balance` giữ snapshot trong khi vẫn tính `current_balance` từ transactions cho UI.
  - Các cột trạng thái offline-first (`sync_status`, `is_deleted`, `updated_at`) lặp lại trên hầu hết bảng để hỗ trợ sync pipeline.
- **Tổ chức migration:**
  - Migration chain đăng ký tuần tự trong `AppDatabase`: `7->8`, `8->9`, `9->10`, `10->11`, `11->12`, `12->13`.
  - Mỗi migration tách file riêng theo version pair trong `data/local/migrations`.
  - Nội dung migration hiện tại gồm: reshape schema lớn (`7->8`), chuẩn hóa timestamp (`8->9`), unique index budget scope (`9->10`), tạo bảng sync metadata (`10->11`), tối ưu index sync/read path (`11->12`, `12->13`).
- **Utils/models liên quan trực tiếp:**
  - `Converters` map enum/date <-> DB scalar.
  - Các enum `TransactionType`, `WalletType`, `CategoryType`, `DebtType`, `DebtStatus` dùng trong conversion và điều kiện query string.
  - `SyncStatus` chuẩn hóa state machine đồng bộ cho nhiều bảng.

### Open Questions / Uncertain Areas
- `BudgetEntity` khai báo unique index gồm `wallet_id` nhưng không có FK sang `wallets`; đây là chủ đích để cho phép historical wallet reference mềm hay là thiếu ràng buộc quan hệ?
- `TransactionEntity` comment ghi denormalized `user_id`, nhưng FK không ràng buộc trực tiếp tới `users`; cần xác nhận có muốn thêm FK để tăng toàn vẹn dữ liệu không.
- `EventEntity` dùng cột `budget_limit`, trong khi tài liệu nghiệp vụ trước đó từng mô tả `budget_amount`; cần chốt tên canonical để tránh lệch schema-doc.
- Migration chain chỉ khai báo từ version 7; nếu user nâng cấp từ DB cũ hơn 7 thì chiến lược chính thức là destructive migration hay không support?

## 4.8 Background & Sync Flow

### Sync Flow (Trigger -> Worker -> Repository -> Data)
1. **Trigger layer**
   - App start: `MoneyMateApplication.onCreate()` gọi `syncScheduler.ensurePeriodicSync()` để đăng ký periodic sync unique work (`periodic_sync`, mỗi 1 giờ, network required).
   - Write-driven one-time sync:
     - `TransactionRepository` và `BudgetRepository` gọi `scheduleSyncIfEnabled()` sau write thành công -> `SyncScheduler.scheduleOneTimeSyncDebounced()` (`critical_sync`, delay 5s, KEEP).
   - Manual retry:
     - Từ foreground Snackbar (`ForegroundUiNotifier`) hoặc notification action (`NotificationHelper` -> `SyncRetryReceiver`) -> `SyncScheduler.enqueueManualRetryNow(...)`.

2. **Worker layer**
   - WorkManager khởi tạo `SyncWorker` qua `MoneyMateWorkerFactory` (manual DI từ `AppContainer`).
   - `SyncWorker.doWork()`:
     - Lấy `userId` từ `AuthRepository.getCurrentUserId()`.
     - Chạy tuần tự theo domain: `transactions` -> `budgets` -> `categories` -> `wallets` -> `debts` -> `events`.
     - Mỗi domain dùng checkpoint (`SyncMetadataRepository.getOrCreateCheckpoint`) để đọc incremental từ `updated_at + id`.

3. **Repository/Data layer**
   - Worker gọi `getPendingSync...` ở từng repository (backed by DAO queries `sync_status IN (1,2)` + cursor condition).
   - Per record:
     - `sync_status = PENDING_DELETE (2)` -> `hardDeleteById(...)` local.
     - Ngược lại -> `markSynced(...)` (set `sync_status = 0`).
   - Sau mỗi record, worker cập nhật checkpoint qua `syncMetadataRepository.updateCheckpoint(...)`.

4. **Current remote status**
   - Trong `SyncWorker`, phần push lên cloud hiện là placeholder comment (“treated as successful in Phase 5 scaffolding”).
   - Nghĩa là flow hiện tại chủ yếu xử lý trạng thái sync cục bộ + cleanup local, chưa thấy gateway remote thực thi thật trong worker này.

### Retry Mechanism
- **WorkManager backoff:** cả periodic và one-time request dùng `BackoffPolicy.EXPONENTIAL`, initial backoff 30s.
- **Worker retry policy:** `SyncWorker` retry khi exception và `runAttemptCount < 3` (`MAX_ATTEMPTS = 3`), quá ngưỡng thì `Result.failure()`.
- **User-facing retry:**
  - Foreground: hiển thị Snackbar retry (`ForegroundUiNotifier.showSyncFailedSnackbar`).
  - Background: hiển thị notification có action retry (`NotificationHelper` + `SyncRetryReceiver`).
  - Cả hai đều enqueue lại unique one-time sync (`critical_sync`, KEEP).

### Dependency Với Database
- `SyncWorker` phụ thuộc trực tiếp các repository có DAO-backed sync APIs: `TransactionRepository`, `BudgetRepository`, `CategoryRepository`, `WalletRepository`, `DebtRepository`, `EventRepository`, `SyncMetadataRepository`.
- `SyncMetadataEntity`/`sync_metadata` là state store cho checkpoint theo (`user_id`, `domain`) để sync incremental, tránh full-scan mỗi lần chạy.
- Tất cả write local liên quan sync state (`markSynced`, `hardDeleteById`, soft-delete trước đó) đi qua Room DAO.
- Execution model:
  - Domain write business logic chạy trước đó trên `AppDatabase.databaseWriteExecutor` trong repositories.
  - SyncWorker chạy dưới WorkManager thread, đọc/ghi Room trực tiếp qua repositories/DAO.

### Notes
- Coupling chính nằm ở `SyncWorker` + `MoneyMateWorkerFactory`: worker biết rõ tất cả domain repositories và thứ tự sync.
- `SyncScheduler` dùng unique work names (`critical_sync`, `periodic_sync`) + `ExistingWorkPolicy.KEEP` để tránh enqueue trùng khi có nhiều trigger sát nhau.
- One-time sync hiện được trigger tự động sau write ở `TransactionRepository` và `BudgetRepository`; các repository khác (wallet/category/debt/event) hiện không tự gọi scheduler trong write path.
- `SyncRetryReceiver` được khai báo `android:exported="false"` trong manifest, giảm bề mặt trigger từ app ngoài.

### Open Questions / Uncertain Areas
- Vì one-time debounce hiện chỉ thấy gọi từ transaction/budget writes, thay đổi ở wallet/category/debt/event có thể phải chờ periodic sync (hoặc retry manual). Đây là chủ đích tối ưu hay thiếu trigger nhất quán giữa các domain?
- Worker hiện đánh dấu record là synced/hard-deleted mà chưa thấy call remote gateway thực thi thật trong code hiện tại; cần xác nhận đây là scaffold tạm thời hay đã có sync remote ở lớp khác chưa được wiring vào `SyncWorker`.
- `SyncScheduler.enqueueManualRetryNow(...)` dùng `ExistingWorkPolicy.KEEP`; nếu đã có one-time work đang pending/running thì action retry không tạo run mới ngay. UX mong muốn có “force retry now” hay giữ behavior chống trùng hiện tại?

## 4.9 Navigation Flow

### Flow tổng quan (Login -> Main -> Feature screens)
1. **Launcher router**
   - `MainActivity` là LAUNCHER activity.
   - `MainActivity` kiểm tra `authRepository.isLoggedIn()`:
     - Logged in -> mở `HomeActivity`.
     - Chưa logged in -> mở `LoginActivity`.

2. **Auth host**
   - `LoginActivity` host `nav_auth` (`activity_login.xml` -> `FragmentContainerView` với `@navigation/nav_auth`).
   - `LoginActivity.onStart()` có routing phụ:
     - Logged in + passcode enabled -> `PasscodeActivity` (VERIFY mode).
     - Logged in + không passcode -> `HomeActivity`.
     - Chưa logged in -> ở lại flow `nav_auth`.

3. **Passcode host**
   - `PasscodeActivity` host `nav_passcode` (`activity_passcode.xml`) và truyền args `passcode_mode`, `passcode_finish_to_home` vào graph.

4. **Main feature host**
   - `HomeActivity` host `nav_main` (`activity_home.xml` -> `nav_host_main`).
   - Bottom navigation custom tabs map tới top-level destinations:
     - `homeFragment`
     - `transactionListFragment`
     - `budgetListFragment`
     - `settingsFragment`
   - FAB trung tâm điều hướng nhanh tới `addEditTransactionFragment`.

### Navigation graph mapping
- **`nav_auth` (start: `loginFragment`)**
  - `loginFragment` -> `registerFragment`, `forgotPasswordFragment`, `passcodeFragment` (verify path)
  - `registerFragment` -> back `loginFragment`, hoặc `passcodeFragment` (create path sau register)
  - `forgotPasswordFragment` -> back `loginFragment`
  - `passcodeFragment` có self action `action_passcode_to_confirm` (CREATE -> CONFIRM bằng args mode)

- **`nav_passcode` (start: `passcodeFragment`)**
  - Graph tối giản cho passcode-only activity, nhận args mode/finish_to_home từ intent extras.

- **`nav_main` (start: `homeFragment`)**
  - Home: `homeFragment` -> add transaction / AI assistant / wallet list / statistics / transaction detail
  - Transactions: list/detail/add-edit + category picker/icon picker + wallet picker
  - Statistics: overview -> detail/category detail/day detail/report list + category/wallet pickers
  - Settings hub: settings -> profile/categories/budgets/statistics/wallets/debts/events/passcode
  - Feature clusters riêng:
    - Category: list -> add/edit -> parent picker/icon picker
    - Budget: list/finished/detail/add-edit/wallet picker
    - Wallet: list -> add/edit -> icon picker
    - Debt/Event: list -> add/edit
    - AI: assistant -> receipt scanner

### Notes
- App dùng mô hình **multi-activity + per-activity nav graph**:
  - `LoginActivity` + `nav_auth`
  - `PasscodeActivity` + `nav_passcode`
  - `HomeActivity` + `nav_main`
- Điều hướng top-level ở `HomeActivity` không dùng `BottomNavigationView.setupWithNavController`; thay vào đó dùng custom bottom bar + `navigateToBottomDestination(...)` với `NavOptions` (`launchSingleTop`, `restoreState`, `popUpTo(startDestination)`).
- Đa số màn hình feature đã có action map rõ trong `nav_main`; argument-driven add/edit pattern được dùng rộng rãi (`nullable id` cho mode Add/Edit).

### Open Questions / Uncertain Areas
- Trong `nav_main`, một số `<action>` chưa khai báo đủ 4 animation attributes (hoặc chỉ khai báo một phần), trong khi guideline nội bộ yêu cầu đồng nhất animation cho mọi action. Cần xác nhận chuẩn hiện tại là “bắt buộc đủ 4 attrs” hay “cho phép rút gọn ở một số route”.
- Tên flow yêu cầu “Login -> Main -> Feature screens”, nhưng thực tế runtime có bước router `MainActivity` trước `LoginActivity`; cần chốt terminology: “Main” đang ám chỉ `MainActivity` (router) hay `HomeActivity` (main features).
- `LoginActivity` vừa host auth graph vừa có redirect trong `onStart()` sang `PasscodeActivity`/`HomeActivity`; cần xác nhận có muốn giữ routing logic phân tán giữa `MainActivity` và `LoginActivity`, hay gom về một entry router duy nhất để giảm complexity.

