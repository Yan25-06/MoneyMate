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
│       ├── TransactionRepository.java  ← nhận cả TransactionDao + WalletDao
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
    ├── transaction/               ← ✅ Phase 4 done
    │                                 TransactionListFragment, AddEditTransactionFragment,
    │                                 TransactionAdapter, TransactionViewModel
    ├── category/                  ← ✅ Phase 3 done
    │                                 CategoryListFragment, AddEditCategoryFragment,
    │                                 CategoryAdapter, CategoryViewModel
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
| **Arguments** | Truyền argument giữa fragments qua `<argument>` tag trong nav graph. Nullable string dùng `app:nullable="true"` và `android:defaultValue="@null"` |
| **Directions class** | Navigate bằng `XxxFragmentDirections.actionXxxToYyy(args)` hoặc `navigate(R.id.action_xxx, bundle)` |
| **Activity structure** | `LoginActivity` → host `nav_auth.xml`. `HomeActivity` → host `nav_main.xml` với BottomNavigationView |
| **Add/Edit pattern** | Dùng nullable argument (e.g. `transactionId`, `categoryId`): null = Add mode, non-null = Edit mode |

### 3.6 UI & Layout

| Quy tắc | Chi tiết |
|----------|----------|
| **Material Design 3** | Dùng Material Components (`com.google.android.material`). Theme kế thừa `Theme.Material3.*` |
| **Layout naming** | Activity: `activity_xxx.xml`. Fragment: `fragment_xxx.xml`. Item: `item_xxx.xml` |
| **ID naming** | Format: `type_description`. VD: `tv_amount`, `btn_save`, `et_note`, `rv_transactions`, `fab_add` |
| **RecyclerView** | Dùng `ListAdapter` + `DiffUtil.ItemCallback` cho tất cả danh sách |
| **ViewHolder** | Luôn khai báo `static class ViewHolder` để tránh visibility scope warning và memory leak |
| **Dark mode** | Support qua `values/` và `values-night/`. Dùng `?attr/colorXxx` thay vì hard-code color |
| **Empty state** | Mọi màn hình list PHẢI có `tv_empty` hiển thị khi danh sách rỗng |
| **Status bar** | RecyclerView dùng `paddingTop` + `clipToPadding="false"` để tránh che status bar |
| **Back navigation** | Mọi màn hình Add/Edit PHẢI có `MaterialToolbar` với `navigationIcon` (thường là `outline_close_24` hoặc `outline_arrow_back_24`). Gán listener trong Fragment: `binding.topAppBar.setNavigationOnClickListener(v -> Navigation.findNavController(v).navigateUp())` |
| **Toolbar title động** | Add/Edit fragment PHẢI cập nhật title của toolbar theo mode: `binding.topAppBar.setTitle(isEditMode ? R.string.edit_xxx : R.string.add_xxx)` |

### 3.7 LiveData & Observer

| Quy tắc | Chi tiết |
|----------|----------|
| **Observe lifecycle** | Luôn dùng `getViewLifecycleOwner()` trong Fragment (KHÔNG dùng `this`) |
| **Null binding** | Set `binding = null` trong `onDestroyView()` để tránh memory leak |
| **Observer riêng** | Khi cần switch giữa nhiều LiveData (e.g. filter type), dùng `Observer` field riêng + `removeObserver()` trước khi observe LiveData mới — KHÔNG gọi `observe()` mới mỗi lần |
| **switchMap** | Dùng `Transformations.switchMap()` trong ViewModel khi LiveData phụ thuộc vào MutableLiveData khác |
| **UI flag** | Dùng boolean flag (e.g. `isLoadingEdit`) để block listener khi đang populate form từ DB, tránh side effect |

### 3.8 Navigation Animations

Dự án có sẵn 6 animation files trong `res/anim/`. AI **PHẢI** sử dụng các animation này trong `nav_main.xml` và `nav_auth.xml` khi khai báo `<action>`.

**Danh sách animation có sẵn:**

| File | Mô tả |
|------|-------|
| `slide_in_right.xml` | Fragment mới trượt vào từ phải (enter khi navigate forward) |
| `slide_out_left.xml` | Fragment cũ trượt ra bên trái (exit khi navigate forward) |
| `slide_in_left.xml` | Fragment cũ trượt vào từ trái (popEnter khi back) |
| `slide_out_right.xml` | Fragment mới trượt ra bên phải (popExit khi back) |
| `slide_in_up.xml` | Fragment trượt lên từ dưới (enter cho bottom sheet / dialog style) |
| `slide_out_down.xml` | Fragment trượt xuống (exit cho bottom sheet / dialog style) |

**Cách khai báo animation trong navigation graph (`nav_main.xml` / `nav_auth.xml`):**

```xml
<!-- Pattern chuẩn: navigate forward (List → AddEdit) -->
<action
    android:id="@+id/action_xxxList_to_addEdit"
    app:destination="@id/addEditXxxFragment"
    app:enterAnim="@anim/slide_in_right"
    app:exitAnim="@anim/slide_out_left"
    app:popEnterAnim="@anim/slide_in_left"
    app:popExitAnim="@anim/slide_out_right" />

<!-- Pattern bottom-up: dùng cho dialog/bottom sheet style -->
<action
    android:id="@+id/action_xxx_to_yyy"
    app:destination="@id/yyyFragment"
    app:enterAnim="@anim/slide_in_up"
    app:exitAnim="@anim/slide_out_down"
    app:popEnterAnim="@anim/slide_in_up"
    app:popExitAnim="@anim/slide_out_down" />
```

**Quy tắc chọn animation:**

| Tình huống | Enter | Exit | PopEnter | PopExit |
|-----------|-------|------|----------|---------|
| Navigate sang màn hình con (List → Detail, List → AddEdit) | `slide_in_right` | `slide_out_left` | `slide_in_left` | `slide_out_right` |
| Navigate ngang giữa tab / sibling screens | `slide_in_right` | `slide_out_left` | `slide_in_left` | `slide_out_right` |
| Màn hình dạng bottom-up (picker, confirm) | `slide_in_up` | `slide_out_down` | `slide_in_up` | `slide_out_down` |

> ⚠️ **BẮT BUỘC:** Mọi `<action>` trong nav graph PHẢI khai báo đủ cả 4 thuộc tính animation. KHÔNG để action không có animation.

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
| `user_id` | `TEXT FK→users` | nullable (null = system default) |
| `name` | `TEXT` | |
| `icon_res_id` | `TEXT` | resource name, e.g. "ic_food" |
| `type` | `TEXT` | INCOME / EXPENSE |
| `is_default` | `INTEGER` | boolean |
| `color_hex` | `TEXT` | |
| `updated_at` | `INTEGER` | |
| `sync_status` | `INTEGER` | |
| `is_deleted` | `INTEGER` | boolean |

### 4.4 Entity: `transactions`

| Column | Type | Note |
|--------|------|------|
| `id` | `TEXT PK` | UUID |
| `user_id` | `TEXT FK→users` | denormalized cho user-scoped queries |
| `wallet_id` | `TEXT FK→wallets` | source wallet |
| `category_id` | `TEXT FK→categories` | |
| `to_wallet_id` | `TEXT FK→wallets` | nullable, for TRANSFER type |
| `debt_id` | `TEXT FK→debts` | nullable |
| `event_id` | `TEXT FK→events` | nullable |
| `amount` | `REAL` | always positive |
| `type` | `TEXT` | INCOME / EXPENSE / TRANSFER |
| `note` | `TEXT` | nullable |
| `image_path` | `TEXT` | nullable, receipt photo path |
| `timestamp` | `INTEGER` | epoch millis, ngày giao dịch (field thực tế trong code) |
| `updated_at` | `INTEGER` | |
| `sync_status` | `INTEGER` | |
| `is_deleted` | `INTEGER` | boolean |

> ⚠️ Lưu ý: Field ngày giao dịch tên là `timestamp` (KHÔNG phải `date`) trong `TransactionEntity` và `TransactionDao`.

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
container.transactionRepository.getAllTransactions(userId);
container.prefsManager.getCurrency();
```

> **KHÔNG** tự tạo instance Repository/DAO. Luôn lấy từ `AppContainer`.

**Lưu ý:** `TransactionRepository` nhận **2 tham số** trong constructor:
```java
// AppContainer.java
transactionRepository = new TransactionRepository(database.transactionDao(), database.walletDao());
```

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

        String userId = container.prefsManager.getUid();
        items = repository.getAllByUser(userId);
    }

    public LiveData<List<XxxEntity>> getItems() { return items; }

    public void add(XxxEntity entity) {
        entity.setId(UUID.randomUUID().toString());
        entity.setUserId(userId);
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

### 7.1 List Fragment

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
            // Empty state
            binding.tvEmpty.setVisibility(items == null || items.isEmpty() ? View.VISIBLE : View.GONE);
            binding.rvXxx.setVisibility(items == null || items.isEmpty() ? View.GONE : View.VISIBLE);
        });

        binding.fabAdd.setOnClickListener(v ->
            Navigation.findNavController(v).navigate(R.id.action_xxxList_to_addEdit)
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;  // Prevent memory leak
    }
}
```

### 7.2 Add/Edit Fragment — Back Navigation & Toolbar

Mọi màn hình Add/Edit **PHẢI** có `MaterialToolbar` với nút điều hướng quay lại. Cấu trúc layout và Java bắt buộc:

**Layout XML (`fragment_add_edit_xxx.xml`):**
```xml
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <com.google.android.material.appbar.AppBarLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content">

        <com.google.android.material.appbar.MaterialToolbar
            android:id="@+id/top_app_bar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            android:title="@string/add_xxx"
            app:navigationIcon="@drawable/outline_close_24"
            app:navigationContentDescription="@string/common_cancel" />

    </com.google.android.material.appbar.AppBarLayout>

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:padding="16dp"
        app:layout_behavior="@string/appbar_scrolling_view_behavior">
        <!-- Form fields... -->
    </ScrollView>

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

**Java Fragment (`AddEditXxxFragment.java`):**
```java
@Override
public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    // 1. Back navigation — BẮT BUỘC
    binding.topAppBar.setNavigationOnClickListener(v ->
        Navigation.findNavController(v).navigateUp()
    );

    // 2. Xác định Add/Edit mode từ Safe Args
    String xxxId = AddEditXxxFragmentArgs.fromBundle(getArguments()).getXxxId();
    boolean isEditMode = xxxId != null;

    // 3. Cập nhật title toolbar theo mode
    binding.topAppBar.setTitle(isEditMode
        ? R.string.edit_xxx
        : R.string.add_xxx);

    // 4. Nếu Edit mode: load dữ liệu từ ViewModel rồi populate form
    if (isEditMode) {
        viewModel.getById(xxxId).observe(getViewLifecycleOwner(), entity -> {
            if (entity != null) {
                isLoadingEdit = true;
                binding.etName.setText(entity.getName());
                // ... populate other fields
                isLoadingEdit = false;
            }
        });
    }

    // 5. Save button
    binding.btnSave.setOnClickListener(v -> saveEntity());
}
```

**Quy tắc back navigation:**

| Tình huống | NavigationIcon | Hành vi |
|-----------|----------------|---------|
| Add/Edit form | `outline_close_24` | `navigateUp()` — hủy và quay lại |
| Detail / sub-screen | `outline_arrow_back_24` | `navigateUp()` — quay lại màn trước |
| Dialog / picker | `outline_close_24` | `navigateUp()` hoặc dismiss |

> ⚠️ KHÔNG dùng `requireActivity().onBackPressed()`. Luôn dùng `Navigation.findNavController(v).navigateUp()`.

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

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemXxxBinding binding = ItemXxxBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    // ⚠️ PHẢI là static class để tránh "exposed outside visibility scope" warning
    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemXxxBinding binding;

        ViewHolder(ItemXxxBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(XxxEntity item) {
            // bind data...
        }
    }

    private static final DiffUtil.ItemCallback<XxxEntity> DIFF_CALLBACK =
        new DiffUtil.ItemCallback<XxxEntity>() {
            @Override
            public boolean areItemsTheSame(@NonNull XxxEntity oldItem, @NonNull XxxEntity newItem) {
                return oldItem.getId().equals(newItem.getId());
            }

            @Override
            public boolean areContentsTheSame(@NonNull XxxEntity oldItem, @NonNull XxxEntity newItem) {
                return oldItem.getUpdatedAt() == newItem.getUpdatedAt();
            }
        };
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
        entity.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        entity.setUpdatedAt(System.currentTimeMillis());
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

> ⚠️ **TransactionRepository** là trường hợp đặc biệt: nhận thêm `WalletDao` để tự động cập nhật số dư ví sau mỗi thao tác CRUD. Xem §17 để biết chi tiết.

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
    ├── 💰 TransactionListFragment ✅ — Full transaction list + filter by type
    │         └── AddEditTransactionFragment ✅ — Add/Edit (transactionId nullable arg)
    ├── 📊 StatisticsFragment — Charts (PieChart, BarChart)
    └── ⚙️ SettingsFragment — Hub to:
            ├── ProfileFragment
            ├── CategoryListFragment ✅
            │         └── AddEditCategoryFragment ✅ (categoryId nullable arg)
            ├── WalletListFragment ✅
            │         └── AddEditWalletFragment ✅ (walletId nullable arg)
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
| Dùng `requireActivity().onBackPressed()` cho back navigation | Dùng `Navigation.findNavController(v).navigateUp()` |
| Tạo `<action>` trong nav graph không có animation | Khai báo đủ 4 animation attributes cho mọi `<action>` |
| Add/Edit fragment không có nút quay lại | Luôn thêm `MaterialToolbar` với `navigationIcon` và listener `navigateUp()` |
| Dùng `Serializable` để truyền data | Dùng Safe Args với primitive/String |
| Dùng deprecated APIs | Dùng API mới nhất trong min SDK 29 |
| Gọi `observe()` mới mỗi lần switch filter | Dùng `Observer` field + `removeObserver()` |
| Inner class `ViewHolder` không có `static` | Luôn dùng `static class ViewHolder` |
| Set form field trực tiếp mà không block listener | Dùng flag boolean (e.g. `isLoadingEdit`) |

---

## 14. CHECKLIST KHI GENERATE CODE MỚI

Trước khi output code, AI **PHẢI** tự verify:

- [ ] File là `.java` (KHÔNG phải `.kt`)
- [ ] Package đúng: `com.group10.moneymate.xxx`
- [ ] Import cụ thể (không wildcard)
- [ ] Entity có `id`, `userId`, `updatedAt`, `syncStatus`, `isDeleted`
- [ ] DAO query filter theo `user_id` VÀ `is_deleted = 0`
- [ ] ViewModel extends `AndroidViewModel`
- [ ] Fragment dùng ViewBinding, null binding trong `onDestroyView()`
- [ ] String hiển thị nằm trong `strings.xml`
- [ ] Write operations chạy trên `databaseWriteExecutor`
- [ ] Set `syncStatus = PENDING_UPLOAD` khi insert/update
- [ ] Navigation dùng action id hoặc Safe Args Directions
- [ ] Adapter dùng `ListAdapter` + `DiffUtil` + `static class ViewHolder`
- [ ] List fragment có `tv_empty` empty state
- [ ] Observer LiveData dùng `getViewLifecycleOwner()`
- [ ] Nếu switch giữa nhiều LiveData → dùng Observer field + `removeObserver()`
- [ ] Add/Edit fragment có `MaterialToolbar` với `navigationIcon` và `setNavigationOnClickListener → navigateUp()`
- [ ] Add/Edit fragment cập nhật toolbar title theo Add/Edit mode
- [ ] Mọi `<action>` trong nav graph có đủ 4 animation: `enterAnim`, `exitAnim`, `popEnterAnim`, `popExitAnim`
- [ ] Dùng đúng bộ animation: `slide_in_right`/`slide_out_left`/`slide_in_left`/`slide_out_right` cho navigate forward/back

---

## 15. FILE LAYOUT CONVENTIONS

### XML ID Naming

| Prefix | Component | Ví dụ |
|--------|-----------|-------|
| `tv_` | TextView | `tv_amount`, `tv_wallet_name`, `tv_empty` |
| `et_` | EditText / TextInputEditText | `et_note`, `et_amount`, `et_date` |
| `btn_` | Button | `btn_save`, `btn_cancel`, `btn_expense`, `btn_income` |
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
| `toggle_` | MaterialButtonToggleGroup | `toggle_type` |
| `chip_group_` | ChipGroup | `chip_group_category` |
| `dropdown_` | AutoCompleteTextView (ExposedDropdownMenu) | `dropdown_wallet` |

---

## 16. HƯỚNG DẪN CHO TỪNG LOẠI TASK

### Khi AI được yêu cầu tạo feature mới:
1. Tạo/update Entity nếu cần → update `AppDatabase` entities list & tăng version
2. Tạo/update DAO interface
3. Tạo/update Repository class → đăng ký trong `AppContainer`
4. Tạo ViewModel (extends `AndroidViewModel`)
5. Tạo Fragment + Layout XML:
    - List fragment: nhớ thêm `tv_empty` cho empty state
    - Add/Edit fragment: **PHẢI** có `MaterialToolbar` với `navigationIcon` + `setNavigationOnClickListener → navigateUp()`
6. Tạo Adapter nếu có RecyclerView (`static class ViewHolder`)
7. Thêm `<argument>` vào Navigation graph (`nav_main.xml` hoặc `nav_auth.xml`)
8. Thêm `<action>` vào Navigation graph với **đầy đủ 4 animation** (`slide_in_right`, `slide_out_left`, `slide_in_left`, `slide_out_right`)
9. Thêm strings vào `strings.xml` (bao gồm cả `add_xxx` và `edit_xxx` cho toolbar title)

### Khi AI được yêu cầu sửa bug:
1. Xác định layer nào (UI? ViewModel? Repository? DAO?)
2. Kiểm tra null safety, threading, lifecycle
3. Kiểm tra Observer leak (gọi observe() nhiều lần không?)
4. Đảm bảo không break existing patterns

### Khi AI được yêu cầu thêm query:
1. Thêm method vào DAO interface
2. Wrap trong Repository
3. Expose qua ViewModel LiveData
4. DAO list queries: `WHERE user_id = :userId AND is_deleted = 0`

---

## 17. TRANSACTION REPOSITORY — QUY TẮC ĐẶC BIỆT

`TransactionRepository` khác các repository khác ở chỗ nó **tự động cập nhật số dư ví** sau mỗi thao tác CRUD. Đây là business logic quan trọng:

```java
// Constructor nhận thêm WalletDao
public TransactionRepository(TransactionDao transactionDao, WalletDao walletDao) { ... }

// Insert: ghi transaction + cộng/trừ balance ví
public void insertTransaction(TransactionEntity transaction) {
    AppDatabase.databaseWriteExecutor.execute(() -> {
        transactionDao.insertTransaction(transaction);
        applyBalanceChange(transaction, false); // false = áp dụng
    });
}

// Update: hoàn tác balance cũ → áp dụng balance mới
public void updateTransaction(TransactionEntity oldTx, TransactionEntity newTx) {
    AppDatabase.databaseWriteExecutor.execute(() -> {
        applyBalanceChange(oldTx, true);  // true = hoàn tác
        applyBalanceChange(newTx, false);
        transactionDao.updateTransaction(newTx);
    });
}

// SoftDelete: hoàn tác balance trước khi xóa
public void softDeleteTransaction(TransactionEntity transaction) {
    AppDatabase.databaseWriteExecutor.execute(() -> {
        applyBalanceChange(transaction, true);
        transactionDao.softDelete(transaction.getId(), System.currentTimeMillis());
    });
}
```

**Logic `applyBalanceChange`:**
- `INCOME` → cộng vào wallet
- `EXPENSE` → trừ khỏi wallet
- `TRANSFER` → trừ wallet nguồn (`walletId`), cộng wallet đích (`toWalletId`)
- `reverse = true` → đảo ngược delta (dùng khi undo)

**Trong AppContainer:**
```java
transactionRepository = new TransactionRepository(database.transactionDao(), database.walletDao());
```