# MoneyMate – Copilot Coding Agent Instructions

## Project Overview

**MoneyMate** is an Android personal finance management application built in **Java** using **MVVM + Repository Pattern** with an offline-first architecture. The app manages wallets, transactions, budgets, debts, events, categories, and includes AI features (Gemini chatbot, OCR receipt scanner) and security (biometric auth, passcode).

- **Language:** Java 11
- **Min SDK:** API 29 (Android 10) | **Target SDK:** API 36
- **Build System:** Gradle with Kotlin DSL (`.gradle.kts` files)
- **UI:** Material Design 3, Jetpack Navigation with Safe Args, ViewBinding
- **Local DB:** Room (SQLite) v3 schema, 7 entities
- **Remote:** Firebase Authentication + Firestore

---

## Repository Structure

```
MoneyMate/
├── app/
│   ├── build.gradle.kts          # App-level build config (dependencies, buildConfig)
│   ├── google-services.json      # Firebase config (NOT in Git – must be provided locally)
│   ├── proguard-rules.pro        # ProGuard rules for release builds
│   └── src/main/java/com/group10/moneymate/
│       ├── MainActivity.java     # Router: checks login state, redirects to auth or home
│       ├── models/               # 6 enums: TransactionType, WalletType, CategoryType,
│       │                         #          DebtType, DebtStatus, SyncStatus
│       ├── data/
│       │   ├── local/
│       │   │   ├── AppDatabase.java          # Room singleton (fallbackToDestructiveMigration)
│       │   │   ├── Converters.java           # Type converters for dates & 5 enums
│       │   │   ├── entity/                   # 7 Room entities (User, Wallet, Category,
│       │   │   │                             #   Transaction, Budget, Debt, Event)
│       │   │   └── dao/                      # 7 DAO interfaces with LiveData queries
│       │   ├── remote/
│       │   │   └── FirebaseAuthHelper.java   # Firebase Auth wrapper
│       │   └── repository/                   # 8 Repository classes (one per entity + Auth)
│       ├── di/
│       │   ├── AppContainer.java             # Manual DI: instantiates all 8 repositories
│       │   └── MoneyMateApplication.java     # Application class; holds AppContainer
│       ├── ui/                               # 14 feature packages (see list below)
│       └── utils/
│           ├── Constants.java
│           ├── PrefsManager.java             # SharedPreferences wrapper
│           ├── CurrencyFormatter.java
│           └── DateUtils.java
├── gradle/
│   └── libs.versions.toml        # Central dependency version catalog
├── build.gradle.kts              # Root build config
├── settings.gradle.kts           # Project settings, repository URLs
├── gradle.properties             # JVM args, AndroidX flags
├── docs/
│   ├── project-structure.md      # Detailed file list
│   └── implementation-phases.md  # 14-day parallel dev plan (Vietnamese)
└── README.md                     # Project documentation (Vietnamese)
```

### UI Packages (`ui/`)

| Package | Responsibility |
|---------|----------------|
| `ui/auth/` | Login + Register (Firebase email/password), AuthViewModel |
| `ui/main/` | HomeActivity (bottom navigation host, 4 tabs) |
| `ui/home/` | Dashboard / overview screen |
| `ui/wallet/` | Wallet CRUD (list, add/edit) |
| `ui/category/` | Category CRUD with tabs for INCOME/EXPENSE |
| `ui/transaction/` | Transaction CRUD + filters |
| `ui/budget/` | Budget tracking with progress indicators |
| `ui/debt/` | Lending/borrowing records (LEND/BORROW tabs) |
| `ui/event/` | Financial events (trips, birthdays, etc.) |
| `ui/statistics/` | Charts: PieChart + BarChart (MPAndroidChart) |
| `ui/profile/` | User profile view/edit |
| `ui/settings/` | App settings (theme, currency, date format) |
| `ui/security/` | Passcode entry + biometric authentication |
| `ui/ai/` | AI chatbot (Gemini) + receipt scanner (ML Kit) |

---

## Local Setup (Required Before Building)

### 1. `local.properties` (create in project root, never commit)
```properties
sdk.dir=/path/to/Android/Sdk
GEMINI_API_KEY=your_gemini_api_key_here
```
The `GEMINI_API_KEY` is injected as `BuildConfig.GEMINI_API_KEY` via `app/build.gradle.kts`.

### 2. `app/google-services.json`
Download from Firebase Console and place in `app/`. Required for Firebase Auth and Firestore.

### 3. Firebase Console
- Enable **Email/Password** authentication
- Enable **Cloud Firestore**

---

## Build Commands

```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease

# Unit tests (no device needed)
./gradlew test

# Instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest
```

**IDE:** Android Studio Ladybug 2024.2.1 or newer is recommended.

---

## Architecture: MVVM + Repository

```
Fragment / Activity
    │  observes LiveData
    ▼
ViewModel
    │  calls repository methods
    ▼
Repository  ──────────────────────────┐
    │  Room DAO                        │  Firebase Firestore / Auth
    ▼                                  ▼
AppDatabase (SQLite)             FirebaseAuthHelper
```

### Key principles
- **No Android framework code in ViewModels.** ViewModels contain only pure Java and Jetpack Lifecycle types.
- **LiveData for all observable state.** Use `MutableLiveData` internally; expose `LiveData` publicly.
- **Repository is the single source of truth.** UI never accesses DAO directly.
- **Offline-first.** Room DB is always queried first; Firestore sync runs in the background.
- **Manual DI via `AppContainer`.** No Dagger/Hilt. Repositories are constructed in `AppContainer` and accessed from fragments via `((MoneyMateApplication) requireActivity().getApplication()).appContainer.xxxRepository`.
- **Soft-delete only.** Never call `DELETE` SQL on user data. Mark `is_deleted = 1` and set `sync_status` accordingly.

---

## Offline Sync Pattern

Every persistent entity includes two sync fields:

| Field | Type | Meaning |
|-------|------|---------|
| `sync_status` | `int` | `0` = SYNCED, `1` = PENDING_UPLOAD, `2` = PENDING_DELETE |
| `is_deleted` | `boolean` | Soft-delete flag |

See `models/SyncStatus.java` for the enum constants.

**Rules:**
- When inserting/updating a record locally → set `sync_status = SyncStatus.PENDING_UPLOAD`.
- When soft-deleting a record locally → set `is_deleted = 1`, `sync_status = SyncStatus.PENDING_DELETE`.
- After successfully syncing to Firestore → set `sync_status = SyncStatus.SYNCED`.
- All DAO queries for UI display must include `WHERE is_deleted = 0`.

---

## Database Schema (Room v3)

**Database name:** `moneymate_database`  
**Migration strategy:** `fallbackToDestructiveMigration()` (development only)

| Table | Key Columns | Foreign Keys |
|-------|-------------|--------------|
| `users` | `id` (Firebase UID), `email`, `display_name`, `hashed_passcode` | — |
| `wallets` | `id`, `user_id`, `name`, `balance`, `type` (WalletType), `color_hex`, `is_excluded` | → users (CASCADE) |
| `categories` | `id`, `user_id` (nullable for system), `name`, `type` (CategoryType), `icon`, `is_default` | → users (CASCADE) |
| `transactions` | `id`, `user_id`, `wallet_id`, `category_id`, `debt_id` (nullable), `event_id` (nullable), `amount`, `type` (TransactionType), `note`, `date` | → wallets (CASCADE), categories (NO_ACTION), debts (SET_NULL), events (SET_NULL) |
| `budgets` | `id`, `user_id`, `category_id` (nullable), `limit_amount`, `alert_threshold`, `month`, `year` | → users (CASCADE), categories (SET_NULL) |
| `debts` | `id`, `user_id`, `person_name`, `type` (DebtType), `amount`, `remaining_amount`, `status` (DebtStatus), `due_date` | → users (CASCADE) |
| `events` | `id`, `user_id`, `name`, `budget`, `start_date`, `end_date` | → users (CASCADE) |

---

## Naming Conventions

| Layer | Convention | Example |
|-------|-----------|---------|
| Entity | `XxxEntity` | `WalletEntity` |
| DAO | `XxxDao` | `WalletDao` |
| Repository | `XxxRepository` | `WalletRepository` |
| ViewModel | `XxxViewModel` | `WalletViewModel` |
| Fragment | `XxxFragment` | `WalletListFragment` |
| Adapter | `XxxAdapter` | `WalletAdapter` |
| Activity | `XxxActivity` | `LoginActivity` |
| Layout (fragment) | `fragment_xxx.xml` | `fragment_wallet_list.xml` |
| Layout (item) | `item_xxx.xml` | `item_wallet.xml` |
| Navigation action | `action_source_to_dest` | `action_walletList_to_addEdit` |

**Identifiers:** Use UUID strings as primary keys (e.g., `UUID.randomUUID().toString()`).

---

## How to Add a New Feature

Follow these steps in order to keep the architecture consistent:

1. **Entity** – Create `data/local/entity/XxxEntity.java` with `@Entity`, all sync fields (`sync_status`, `is_deleted`), and proper foreign keys.
2. **DAO** – Create `data/local/dao/XxxDao.java` as a `@Dao` interface. Expose `LiveData<List<XxxEntity>>` for UI queries and synchronous methods (suffix `Sync`) for background workers.
3. **Register DAO** – Add the entity to `@Database(entities = {...})` in `AppDatabase.java` and add the abstract DAO getter.
4. **Repository** – Create `data/repository/XxxRepository.java`. Wrap DAO calls; run writes on the `AppDatabase.databaseWriteExecutor`.
5. **DI** – Instantiate the repository in `AppContainer.java` and expose it as a `public final` field.
6. **ViewModel** – Create `ui/xxx/XxxViewModel.java` extending `ViewModel`. Inject the repository, expose `LiveData`.
7. **Fragments & Adapter** – Create `XxxListFragment`, `AddEditXxxFragment`, and `XxxAdapter` under `ui/xxx/`.
8. **Layouts** – Create `fragment_xxx_list.xml`, `fragment_add_edit_xxx.xml`, `item_xxx.xml` in `res/layout/`.
9. **Navigation** – Add fragment entries and action elements to `res/navigation/nav_main.xml` (or `nav_auth.xml`).
10. **Strings** – Add all user-visible strings to `res/values/strings.xml`.

---

## Navigation

- **Two navigation graphs:** `res/navigation/nav_auth.xml` (login/register) and `res/navigation/nav_main.xml` (all main screens).
- **Bottom navigation:** 4 tabs defined in `res/menu/bottom_nav_menu.xml` (home, transactions, statistics, settings) hosted by `HomeActivity`.
- **Type-safe arguments:** Use Navigation Safe Args plugin. Define `<argument>` elements in the nav graph; access them via generated `XxxFragmentArgs.fromBundle(getArguments())`.
- **Routing:** `MainActivity.java` checks `authRepository.isLoggedIn()` and starts either `LoginActivity` or `HomeActivity`.

---

## ViewBinding Usage

ViewBinding is enabled. In fragments, follow this pattern:

```java
private FragmentXxxBinding binding;

@Override
public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    binding = FragmentXxxBinding.inflate(inflater, container, false);
    return binding.getRoot();
}

@Override
public void onDestroyView() {
    super.onDestroyView();
    binding = null;  // Prevent memory leaks
}
```

---

## Dependency Injection Pattern

No framework DI is used. Repositories are accessed as follows:

```java
// In a Fragment
MoneyMateApplication app = (MoneyMateApplication) requireActivity().getApplication();
WalletRepository walletRepository = app.appContainer.walletRepository;

// Create ViewModel with factory
WalletViewModel viewModel = new ViewModelProvider(this,
    new ViewModelProvider.Factory() {
        @NonNull @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            return (T) new WalletViewModel(walletRepository);
        }
    }).get(WalletViewModel.class);
```

---

## Error Handling Conventions

- **ViewModel errors:** Expose a `MutableLiveData<String> errorMessage` and update it on failure. Observe in the fragment to show a Snackbar or Toast.
- **Null safety:** Annotate all parameters with `@NonNull` or `@Nullable`. Avoid unchecked null dereferences.
- **Date parsing:** Use `DateUtils` helpers; they handle `ParseException` internally and return `0` on failure.
- **Database writes:** Always execute on `AppDatabase.databaseWriteExecutor` (4-thread pool), not on the main thread.

---

## Security and Secrets

- **Never hard-code** `GEMINI_API_KEY` or any other secret in source code.
- Add secret keys to `local.properties` only; reference them via `buildConfigField` in `app/build.gradle.kts`.
- `local.properties` and `google-services.json` are in `.gitignore` and must never be committed.
- Passcodes are stored as SHA-256 hashes in `UserEntity.hashedPasscode`.
- For biometric auth, use `AndroidX Biometric 1.1.0` (`BiometricPrompt`).

---

## Key Libraries and Their Usage

| Library | Version | Usage |
|---------|---------|-------|
| Room | 2.8.4 | Local SQLite ORM |
| Firebase BOM | 34.10.0 | Auth + Firestore (cloud sync) |
| Jetpack Navigation | 2.9.7 | Fragment navigation + Safe Args |
| Lifecycle (ViewModel/LiveData) | 2.10.0 | MVVM |
| Material Design 3 | 1.13.0 | UI components |
| MPAndroidChart | v3.1.0 | PieChart + BarChart in statistics |
| AndroidX Biometric | 1.1.0 | Fingerprint/Face ID |
| WorkManager | 2.10.1 | Background Firestore sync worker |
| CameraX (4 modules) | 1.4.2 | Camera preview + image capture |
| ML Kit Text Recognition | 16.0.1 | OCR for receipt scanning |
| Google Generative AI (Gemini) | 0.9.0 | AI chatbot assistant |

Dependency versions are managed centrally in `gradle/libs.versions.toml`.

---

## Git Workflow

- **Main branch:** `main` – merge only after build passes and PR is reviewed.
- **Feature branches:** `feature/dev1-wallet`, `feature/dev2-budget`, `feature/dev3-auth`, etc.
- **Commit convention:** `feat(wallet): add soft delete`, `fix(budget): fix month filter`
- **Do not commit** to `main` directly.
- **Package ownership:** Each developer owns specific `ui/` packages. Do not modify another developer's package without a reviewed PR.
  - Track 1: `ui/wallet`, `ui/category`, `ui/transaction`, `ui/event`
  - Track 2: `ui/budget`, `ui/statistics`, `ui/debt`
  - Track 3: `ui/auth`, `ui/settings`, `ui/security`, `ui/ai`
- **Cross-track reads:** Track 2 may call `TransactionDao` read-only methods but must never INSERT/UPDATE/DELETE transactions directly.

---

## Common Pitfalls and Known Issues

- **`local.properties` missing:** The build will fail if `sdk.dir` or `GEMINI_API_KEY` are absent. Always set these before building.
- **`google-services.json` missing:** Firebase initialization will crash at runtime. Ensure the file is placed in `app/` before running.
- **Database schema changes:** The project uses `fallbackToDestructiveMigration()`, so schema changes will drop and recreate the database. This is acceptable in development but must be replaced with proper migrations before release.
- **Main thread DB access:** Room prohibits synchronous queries on the main thread (except those explicitly annotated). Always execute writes via `AppDatabase.databaseWriteExecutor`.
- **Soft-delete filter:** Any new DAO query that lists records for UI must include `WHERE is_deleted = 0` to exclude soft-deleted entries.
- **UUID generation:** Use `UUID.randomUUID().toString()` for all new entity IDs. Never use auto-increment integers for entities that need to sync with Firestore.
- **String resources:** All user-visible text must be in `res/values/strings.xml`. The app's primary locale is Vietnamese.
- **Navigation Safe Args:** After modifying `nav_main.xml` or `nav_auth.xml`, rebuild the project so the generated `Args` and `Directions` classes are updated.
