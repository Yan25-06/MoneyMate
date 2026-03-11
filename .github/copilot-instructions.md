# MoneyMate — AI Code Generation Context Guide

> **MỤC ĐÍCH:** File này là nguồn chân lý duy nhất (Single Source of Truth) cho mọi AI khi generate code cho project MoneyMate. Mọi AI tool (GitHub Copilot, ChatGPT, Gemini, Cursor, v.v.) **PHẢI** đọc file này trước khi viết code.

---

## 1. THÔNG TIN DỰ ÁN

| Key | Value |
|-----|-------|
| **Tên ứng dụng** | MoneyMate |
| **Package name** | `com.group10.moneymate` |
| **Ngôn ngữ** | **Java** (KHÔNG dùng Kotlin) |
| **Min SDK** | 29 (Android 10) |
| **Target/Compile SDK** | 36 |
| **Build system** | Gradle Kotlin DSL (`build.gradle.kts`) |
| **Version catalog** | `gradle/libs.versions.toml` |
| **Kiến trúc** | **MVVM + Repository Pattern** |
| **DI** | Manual DI qua `AppContainer` (KHÔNG dùng Hilt/Dagger) |
| **Database** | Room (offline-first, sync with Firestore) |
| **Auth** | Firebase Auth (Email/Password) + Passcode offline |
| **Cloud** | Firebase Firestore (backup/sync) |
| **Navigation** | Jetpack Navigation Component + Safe Args |
| **UI Binding** | ViewBinding (KHÔNG dùng DataBinding expressions) |
| **AI Feature** | Google Gemini API (`BuildConfig.GEMINI_API_KEY`) |

---

## 2. CẤU TRÚC THƯ MỤC

```
com.group10.moneymate/
├── MainActivity.java              ← Router: check login → HomeActivity hoặc LoginActivity
│
├── models/                        ← Enums & value objects
│   ├── TransactionType.java       ← INCOME, EXPENSE
│   ├── WalletType.java            ← CASH, BANK, E_WALLET
│   ├── CategoryType.java          ← INCOME, EXPENSE
│   ├── DebtType.java              ← LEND, BORROW
│   ├── DebtStatus.java            ← ACTIVE, SETTLED
│   └── SyncStatus.java            ← int constants: SYNCED=0, PENDING_UPLOAD=1, PENDING_DELETE=2
│
├── data/
│   ├── local/
│   │   ├── AppDatabase.java       ← Room DB singleton, version 3, 7 entities, 7 DAOs
│   │   ├── Converters.java        ← Date + Enum type converters
│   │   ├── entity/                ← 7 Room @Entity classes (see §4)
│   │   │   ├── UserEntity.java
│   │   │   ├── WalletEntity.java
│   │   │   ├── CategoryEntity.java
│   │   │   ├── TransactionEntity.java
│   │   │   ├── BudgetEntity.java
│   │   │   ├── DebtEntity.java
│   │   │   └── EventEntity.java
│   │   └── dao/                   ← 7 Room @Dao interfaces
│   │       ├── UserDao.java
│   │       ├── WalletDao.java
│   │       ├── CategoryDao.java
│   │       ├── TransactionDao.java
│   │       ├── BudgetDao.java
│   │       ├── DebtDao.java
│   │       └── EventDao.java
│   ├── remote/
│   │   └── FirebaseAuthHelper.java ← Wrapper for FirebaseAuth
│   └── repository/                ← 8 Repository classes
│       ├── AuthRepository.java
│       ├── UserRepository.java
│       ├── WalletRepository.java
│       ├── CategoryRepository.java
│       ├── TransactionRepository.java
│       ├── BudgetRepository.java
│       ├── DebtRepository.java
│       └── EventRepository.java
│
├── di/
│   ├── AppContainer.java          ← Manual DI: holds DB, all DAOs, all Repositories
│   └── MoneyMateApplication.java  ← Custom Application class
│
├── utils/
│   ├── Constants.java             ← Prefs keys, defaults
│   ├── CurrencyFormatter.java     ← format(amount, currency) → "250.000 ₫"
│   ├── DateUtils.java             ← Date helpers
│   └── PrefsManager.java          ← SharedPreferences wrapper
│
└── ui/                            ← Feature-based UI packages
    ├── auth/                      ← LoginActivity, LoginFragment, RegisterFragment, AuthViewModel
    ├── main/                      ← HomeActivity (BottomNavigationView host)
    ├── home/                      ← HomeFragment, HomeViewModel
    ├── transaction/               ← TransactionListFragment, AddEditTransactionFragment, TransactionAdapter, TransactionViewModel
    ├── category/                  ← CategoryListFragment, AddEditCategoryFragment, CategoryAdapter, CategoryViewModel
    ├── budget/                    ← BudgetListFragment, AddEditBudgetFragment, BudgetAdapter, BudgetViewModel
    ├── wallet/                    ← WalletListFragment, AddEditWalletFragment, WalletAdapter, WalletViewModel
    ├── debt/                      ← DebtListFragment, AddEditDebtFragment, DebtViewModel
    ├── event/                     ← EventListFragment, AddEditEventFragment, EventViewModel
    ├── statistics/                ← StatisticsFragment, StatisticsViewModel
    ├── profile/                   ← ProfileFragment, ProfileViewModel
    ├── settings/                  ← SettingsFragment, SettingsViewModel
    ├── security/                  ← PasscodeFragment (create/verify passcode, biometric)
    ├── ai/                        ← AiAssistantFragment, AiReceiptScannerFragment
    └── common/                    ← Shared UI components (adapters, custom views)
```

---

## 3. QUY TẮC CODE — BẮT BUỘC TUÂN THỦ

### 3.1 Ngôn ngữ & Style

| Quy tắc | Chi tiết |
|----------|----------|
| **Chỉ dùng Java** | TUYỆT ĐỐI KHÔNG generate Kotlin. Toàn bộ project là Java. |
| **Naming convention** | Class: `PascalCase`, method/variable: `camelCase`, constant: `UPPER_SNAKE_CASE`, layout XML: `snake_case` |
| **Field prefix** | Entity fields dùng `camelCase` trong Java, `snake_case` trong `@ColumnInfo(name = "...")` |
| **Getter/Setter** | Dùng standard Java getter/setter (KHÔNG dùng Lombok) |
| **No wildcard imports** | KHÔNG dùng `import java.util.*`, phải import cụ thể |
| **String resources** | Mọi text hiển thị cho user PHẢI nằm trong `res/values/strings.xml`, KHÔNG hard-code string |
| **ViewBinding** | Dùng `FragmentXxxBinding` / `ActivityXxxBinding`. KHÔNG dùng `findViewById()` |

### 3.2 Kiến trúc MVVM

```
Fragment/Activity  →  ViewModel  →  Repository  →  DAO / Remote
     (UI)            (LiveData)     (data logic)    (Room / Firebase)
```

| Layer | Quy tắc |
|-------|---------|
| **Fragment/Activity** | Chỉ observe LiveData và handle UI events. KHÔNG chứa business logic. |
| **ViewModel** | Expose `LiveData`/`MutableLiveData`. Gọi Repository methods. KHÔNG giữ reference đến Context/View. |
| **Repository** | Cầu nối giữa ViewModel và data sources. Xử lý logic chọn data source (local/remote). |
| **DAO** | Room DAO interface. Query annotation. KHÔNG chứa logic. |

### 3.3 Room Database

| Quy tắc | Chi tiết |
|----------|----------|
| **Primary Key** | Tất cả entity dùng `String id` (UUID) làm PK. Generate bằng `UUID.randomUUID().toString()` |
| **Soft delete** | Mọi entity có field `is_deleted` (boolean) và `sync_status` (int). Khi xóa: set `is_deleted=true`, `sync_status=PENDING_DELETE` |
| **Timestamps** | Mọi entity có `created_at` và `updated_at` (dạng `long`, epoch millis) |
| **User scoped** | Mọi entity (trừ User) có `user_id` FK. Mọi query PHẢI filter theo `user_id`. |
| **LiveData return** | DAO query trả về `LiveData<List<T>>` cho list, `LiveData<T>` cho single item |
| **Sync queries** | Cần query đồng bộ (non-LiveData) cho background work: suffix `Sync`, e.g., `getByIdSync()` |
| **Write operations** | Chạy trên `AppDatabase.databaseWriteExecutor` (thread pool), KHÔNG chạy trên main thread |

### 3.4 Offline-First & Sync

| Quy tắc | Chi tiết |
|----------|----------|
| **Local first** | Mọi thao tác CRUD ghi vào Room trước. Cloud sync là secondary. |
| **SyncStatus** | `SYNCED=0`: đã sync. `PENDING_UPLOAD=1`: cần upload. `PENDING_DELETE=2`: cần xóa trên cloud. |
| **Khi INSERT/UPDATE** | Set `sync_status = PENDING_UPLOAD`, `updated_at = System.currentTimeMillis()` |
| **Khi DELETE** | Set `is_deleted = true`, `sync_status = PENDING_DELETE` (soft delete) |
| **SyncWorker** | WorkManager periodic task upload pending records → Firestore → set `sync_status = SYNCED` |

### 3.5 Navigation

| Quy tắc | Chi tiết |
|----------|----------|
| **Navigation graphs** | `nav_auth.xml` (login flow), `nav_main.xml` (home flow with BottomNav) |
| **Safe Args** | Dùng Navigation Safe Args plugin để truyền arguments giữa fragments |
| **Directions class** | Navigate bằng `XxxFragmentDirections.actionXxxToYyy(args)` |
| **Activity structure** | `LoginActivity` → host `nav_auth.xml`. `HomeActivity` → host `nav_main.xml` với BottomNavigationView |

### 3.6 UI & Layout

| Quy tắc | Chi tiết |
|----------|----------|
| **Material Design 3** | Dùng Material Components (`com.google.android.material`). Theme kế thừa `Theme.Material3.*` |
| **Layout naming** | Activity: `activity_xxx.xml`. Fragment: `fragment_xxx.xml`. Item: `item_xxx.xml` |
| **ID naming** | Format: `type_description`. VD: `tv_amount`, `btn_save`, `et_note`, `rv_transactions`, `fab_add` |
| **RecyclerView** | Dùng `ListAdapter` + `DiffUtil.ItemCallback` cho tất cả danh sách |
| **Dark mode** | Support qua `values/` và `values-night/`. Dùng `?attr/colorXxx` thay vì hard-code color |

---

## 4. DATABASE SCHEMA

### 4.1 Entity: `users`

| Column | Type | Note |
|--------|------|------|
| `id` | `TEXT PK` | Firebase UID |
| `email` | `TEXT` | |
| `display_name` | `TEXT` | |
| `avatar_url` | `TEXT` | nullable |
| `currency` | `TEXT` | default "VND" |
| `language` | `TEXT` | default "vi" |
| `passcode_hash` | `TEXT` | nullable, hashed passcode |
| `updated_at` | `INTEGER` | epoch millis |
| `sync_status` | `INTEGER` | 0/1/2 |
| `created_at` | `INTEGER` | epoch millis |

### 4.2 Entity: `wallets`

| Column | Type | Note |
|--------|------|------|
| `id` | `TEXT PK` | UUID |
| `user_id` | `TEXT FK→users` | |
| `name` | `TEXT` | |
| `balance` | `REAL` | |
| `type` | `TEXT` | CASH / BANK / E_WALLET |
| `color_hex` | `TEXT` | e.g. "#4CAF50" |
| `is_excluded` | `INTEGER` | boolean, excluded from total balance |
| `updated_at` | `INTEGER` | |
| `sync_status` | `INTEGER` | |
| `is_deleted` | `INTEGER` | boolean |
| `created_at` | `INTEGER` | |

### 4.3 Entity: `categories`

| Column | Type | Note |
|--------|------|------|
| `id` | `TEXT PK` | UUID |
| `user_id` | `TEXT FK→users` | |
| `name` | `TEXT` | |
| `icon_name` | `TEXT` | resource name, e.g. "ic_food" |
| `type` | `TEXT` | INCOME / EXPENSE |
| `is_default` | `INTEGER` | boolean |
| `color_hex` | `TEXT` | |
| `updated_at` | `INTEGER` | |
| `sync_status` | `INTEGER` | |
| `is_deleted` | `INTEGER` | boolean |
| `created_at` | `INTEGER` | |

### 4.4 Entity: `transactions`

| Column | Type | Note |
|--------|------|------|
| `id` | `TEXT PK` | UUID |
| `user_id` | `TEXT FK→users` | |
| `wallet_id` | `TEXT FK→wallets` | source wallet |
| `category_id` | `TEXT FK→categories` | |
| `to_wallet_id` | `TEXT FK→wallets` | nullable, for TRANSFER type |
| `debt_id` | `TEXT FK→debts` | nullable |
| `event_id` | `TEXT FK→events` | nullable |
| `amount` | `REAL` | always positive |
| `type` | `TEXT` | INCOME / EXPENSE / TRANSFER |
| `note` | `TEXT` | |
| `image_path` | `TEXT` | nullable, receipt photo path |
| `date` | `INTEGER` | epoch millis, transaction date |
| `updated_at` | `INTEGER` | |
| `sync_status` | `INTEGER` | |
| `is_deleted` | `INTEGER` | boolean |
| `created_at` | `INTEGER` | |

### 4.5 Entity: `budgets`

| Column | Type | Note |
|--------|------|------|
| `id` | `TEXT PK` | UUID |
| `user_id` | `TEXT FK→users` | |
| `category_id` | `TEXT FK→categories` | nullable (null = total budget) |
| `limit_amount` | `REAL` | |
| `alert_threshold` | `REAL` | 0.0–1.0 (e.g. 0.8 = warn at 80%) |
| `month` | `INTEGER` | 1–12 |
| `year` | `INTEGER` | e.g. 2026 |
| `updated_at` | `INTEGER` | |
| `sync_status` | `INTEGER` | |
| `is_deleted` | `INTEGER` | boolean |
| `created_at` | `INTEGER` | |

### 4.6 Entity: `debts`

| Column | Type | Note |
|--------|------|------|
| `id` | `TEXT PK` | UUID |
| `user_id` | `TEXT FK→users` | |
| `person_name` | `TEXT` | |
| `type` | `TEXT` | LEND / BORROW |
| `amount` | `REAL` | original amount |
| `remaining_amount` | `REAL` | |
| `status` | `TEXT` | ACTIVE / SETTLED |
| `note` | `TEXT` | |
| `due_date` | `INTEGER` | nullable, epoch millis |
| `updated_at` | `INTEGER` | |
| `sync_status` | `INTEGER` | |
| `is_deleted` | `INTEGER` | boolean |
| `created_at` | `INTEGER` | |

### 4.7 Entity: `events`

| Column | Type | Note |
|--------|------|------|
| `id` | `TEXT PK` | UUID |
| `user_id` | `TEXT FK→users` | |
| `name` | `TEXT` | |
| `budget_amount` | `REAL` | planned budget for event |
| `start_date` | `INTEGER` | epoch millis |
| `end_date` | `INTEGER` | epoch millis |
| `is_active` | `INTEGER` | boolean |
| `updated_at` | `INTEGER` | |
| `sync_status` | `INTEGER` | |
| `is_deleted` | `INTEGER` | boolean |
| `created_at` | `INTEGER` | |

---

## 5. DEPENDENCY INJECTION PATTERN

```java
// Trong Fragment hoặc Activity, lấy dependencies:
MoneyMateApplication app = (MoneyMateApplication) requireActivity().getApplication();
AppContainer container = app.getAppContainer();

// Sử dụng:
container.transactionRepository.getAllByUser(userId);
container.prefsManager.getCurrency();
```

> **KHÔNG** tự tạo instance Repository/DAO. Luôn lấy từ `AppContainer`.

---

## 6. VIEWMODEL PATTERN

```java
public class XxxViewModel extends AndroidViewModel {

    private final XxxRepository repository;
    private final LiveData<List<XxxEntity>> items;

    public XxxViewModel(@NonNull Application application) {
        super(application);
        MoneyMateApplication app = (MoneyMateApplication) application;
        AppContainer container = app.getAppContainer();
        repository = container.xxxRepository;
        
        String userId = container.prefsManager.getUid(); // hoặc FirebaseAuth.getInstance().getUid()
        items = repository.getAllByUser(userId);
    }

    public LiveData<List<XxxEntity>> getItems() { return items; }

    public void add(XxxEntity entity) {
        entity.setId(UUID.randomUUID().toString());
        entity.setUserId(userId);
        entity.setCreatedAt(System.currentTimeMillis());
        entity.setUpdatedAt(System.currentTimeMillis());
        entity.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        repository.insert(entity);
    }
}
```

> **Ghi chú:** Dùng `AndroidViewModel` (có Application param) để truy cập `AppContainer`.  
> KHÔNG dùng `ViewModelProvider.Factory` phức tạp trừ khi cần custom constructor ngoài Application.

---

## 7. FRAGMENT PATTERN

```java
public class XxxListFragment extends Fragment {

    private FragmentXxxListBinding binding;
    private XxxViewModel viewModel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentXxxListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(XxxViewModel.class);

        XxxAdapter adapter = new XxxAdapter();
        binding.rvXxx.setAdapter(adapter);
        binding.rvXxx.setLayoutManager(new LinearLayoutManager(requireContext()));

        viewModel.getItems().observe(getViewLifecycleOwner(), items -> {
            adapter.submitList(items);
        });

        binding.fabAdd.setOnClickListener(v -> {
            Navigation.findNavController(v).navigate(
                XxxListFragmentDirections.actionXxxListToAddEditXxx()
            );
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;  // Prevent memory leak
    }
}
```

---

## 8. ADAPTER PATTERN (RecyclerView)

```java
public class XxxAdapter extends ListAdapter<XxxEntity, XxxAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(XxxEntity item);
    }

    private OnItemClickListener listener;

    public XxxAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<XxxEntity> DIFF_CALLBACK =
        new DiffUtil.ItemCallback<XxxEntity>() {
            @Override
            public boolean areItemsTheSame(@NonNull XxxEntity oldItem, @NonNull XxxEntity newItem) {
                return oldItem.getId().equals(newItem.getId());
            }

            @Override
            public boolean areContentsTheSame(@NonNull XxxEntity oldItem, @NonNull XxxEntity newItem) {
                return oldItem.equals(newItem); // implement equals() in entity
            }
        };

    // ... ViewHolder, onCreateViewHolder, onBindViewHolder
}
```

---

## 9. REPOSITORY PATTERN

```java
public class XxxRepository {

    private final XxxDao dao;

    public XxxRepository(XxxDao dao) {
        this.dao = dao;
    }

    public LiveData<List<XxxEntity>> getAllByUser(String userId) {
        return dao.getAllByUser(userId);
    }

    public void insert(XxxEntity entity) {
        AppDatabase.databaseWriteExecutor.execute(() -> dao.insert(entity));
    }

    public void update(XxxEntity entity) {
        entity.setUpdatedAt(System.currentTimeMillis());
        entity.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        AppDatabase.databaseWriteExecutor.execute(() -> dao.update(entity));
    }

    public void softDelete(XxxEntity entity) {
        entity.setDeleted(true);
        entity.setSyncStatus(SyncStatus.PENDING_DELETE);
        entity.setUpdatedAt(System.currentTimeMillis());
        AppDatabase.databaseWriteExecutor.execute(() -> dao.update(entity));
    }
}
```

---

## 10. CÁC LIBRARIES & VERSIONS

> Tất cả versions được quản lý tập trung trong `gradle/libs.versions.toml`. KHÔNG hard-code version trong `build.gradle.kts`.

| Library | Alias trong TOML | Mục đích |
|---------|------------------|----------|
| Room | `libs.room.runtime`, `libs.room.compiler` | Local SQLite database |
| Firebase Auth | `libs.firebase.auth` | Email/Password authentication |
| Firebase Firestore | `libs.firebase.firestore` | Cloud backup & sync |
| Navigation | `libs.navigation.fragment`, `libs.navigation.ui` | Fragment navigation |
| Lifecycle | `libs.lifecycle.viewmodel`, `libs.lifecycle.livedata` | ViewModel + LiveData |
| Material | `libs.material` | Material Design 3 components |
| MPAndroidChart | `libs.mpandroidchart` | PieChart, BarChart |
| Biometric | `libs.biometric` | Fingerprint/face unlock |
| WorkManager | `libs.workmanager` | Background sync tasks |
| CameraX | `libs.camerax.*` | Camera for receipt scanning |
| ML Kit | `libs.mlkit.text.recognition` | OCR text recognition |
| Gemini AI | `libs.generativeai` | AI auto-fill transactions |

---

## 11. FIRESTORE COLLECTION STRUCTURE

```
users/{userId}/
    wallets/{walletId}
    categories/{categoryId}
    transactions/{transactionId}
    budgets/{budgetId}
    debts/{debtId}
    events/{eventId}
```

> Mỗi document trên Firestore **giống hệt** Room entity (cùng field names). Sync logic map 1:1.

---

## 12. APP FLOW

```
[App Launch]
    └── MainActivity (Router)
          ├── Has passcode saved? → PasscodeFragment (verify)
          ├── FirebaseAuth.currentUser != null? → HomeActivity
          └── Else → LoginActivity
                      ├── LoginFragment (email/password)
                      ├── RegisterFragment → CreatePasscodeFragment
                      └── Forgot Password

[HomeActivity] — BottomNavigationView (4 tabs)
    ├── 🏠 HomeFragment — Dashboard (total balance, recent transactions, budget alerts)
    ├── 💰 TransactionListFragment — Full transaction list + filters
    ├── 📊 StatisticsFragment — Charts (PieChart, BarChart)
    └── ⚙️ SettingsFragment — Hub to:
            ├── ProfileFragment
            ├── CategoryListFragment
            ├── WalletListFragment
            ├── BudgetListFragment
            ├── DebtListFragment
            ├── EventListFragment
            └── SecurityFragment (passcode, biometric)
```

---

## 13. QUAN TRỌNG — NHỮNG ĐIỀU KHÔNG ĐƯỢC LÀM

| ❌ KHÔNG | ✅ THAY VÀO ĐÓ |
|----------|-----------------|
| Viết code Kotlin | Viết Java |
| Dùng Hilt / Dagger | Dùng `AppContainer` manual DI |
| Dùng `findViewById` | Dùng ViewBinding |
| Hard-code string trong UI | Dùng `@string/xxx` |
| Hard-code color | Dùng `?attr/colorXxx` hoặc color resource |
| Dùng Coroutines / Flow | Dùng `LiveData` + `ExecutorService` |
| Tạo Repository/DAO instance thủ công | Lấy từ `AppContainer` |
| Query trên main thread | Dùng `AppDatabase.databaseWriteExecutor` |
| Dùng `DataBinding` expressions (`@{}`) | Dùng `ViewBinding` thuần (set data trong code) |
| Xóa cứng (hard delete) | Soft delete (`is_deleted=true`) |
| Dùng `Serializable` để truyền data | Dùng Safe Args với primitive/String |
| Dùng deprecated APIs | Dùng API mới nhất trong min SDK 29 |

---

## 14. CHECKLIST KHI GENERATE CODE MỚI

Trước khi output code, AI **PHẢI** tự verify:

- [ ] File là `.java` (KHÔNG phải `.kt`)
- [ ] Package đúng: `com.group10.moneymate.xxx`
- [ ] Import cụ thể (không wildcard)
- [ ] Entity có `id`, `userId`, `createdAt`, `updatedAt`, `syncStatus`, `isDeleted`
- [ ] DAO query filter theo `user_id` VÀ `is_deleted = 0`
- [ ] ViewModel extends `AndroidViewModel`
- [ ] Fragment dùng ViewBinding, null binding trong `onDestroyView()`
- [ ] String hiển thị nằm trong `strings.xml`
- [ ] Write operations chạy trên `databaseWriteExecutor`
- [ ] Set `syncStatus = PENDING_UPLOAD` khi insert/update
- [ ] Navigation dùng Safe Args Directions
- [ ] Adapter dùng `ListAdapter` + `DiffUtil`

---

## 15. FILE LAYOUT CONVENTIONS

### XML ID Naming

| Prefix | Component | Ví dụ |
|--------|-----------|-------|
| `tv_` | TextView | `tv_amount`, `tv_wallet_name` |
| `et_` | EditText | `et_note`, `et_amount` |
| `btn_` | Button | `btn_save`, `btn_cancel` |
| `fab_` | FloatingActionButton | `fab_add` |
| `rv_` | RecyclerView | `rv_transactions` |
| `iv_` | ImageView | `iv_avatar`, `iv_category_icon` |
| `til_` | TextInputLayout | `til_email` |
| `tiet_` | TextInputEditText | `tiet_email` |
| `sw_` | Switch | `sw_dark_mode` |
| `cb_` | CheckBox | `cb_remember` |
| `rb_` | RadioButton | `rb_income` |
| `rg_` | RadioGroup | `rg_type` |
| `sp_` | Spinner | `sp_wallet` |
| `pb_` | ProgressBar | `pb_loading` |
| `tb_` | Toolbar | `tb_main` |
| `bnv_` | BottomNavigationView | `bnv_main` |
| `tl_` | TabLayout | `tl_category_type` |
| `vp_` | ViewPager2 | `vp_content` |
| `cv_` | CardView | `cv_balance` |

---

## 16. HƯỚNG DẪN CHO TỪNG LOẠI TASK

### Khi AI được yêu cầu tạo feature mới:
1. Tạo/update Entity nếu cần → update `AppDatabase` entities list & tăng version
2. Tạo/update DAO interface
3. Tạo/update Repository class → đăng ký trong `AppContainer`
4. Tạo ViewModel (extends `AndroidViewModel`)
5. Tạo Fragment + Layout XML
6. Tạo Adapter nếu có RecyclerView
7. Thêm vào Navigation graph (`nav_main.xml` hoặc `nav_auth.xml`)
8. Thêm strings vào `strings.xml`

### Khi AI được yêu cầu sửa bug:
1. Xác định layer nào (UI? ViewModel? Repository? DAO?)
2. Kiểm tra null safety, threading, lifecycle
3. Đảm bảo không break existing patterns

### Khi AI được yêu cầu thêm query:
1. Thêm method vào DAO interface
2. Wrap trong Repository
3. Expose qua ViewModel LiveData
4. DAO list queries: `WHERE user_id = :userId AND is_deleted = 0`

