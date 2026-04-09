# MoneyMate Android Project Architecture Analysis Report

> Scope: analysis based on the current Java source under `app/src/main/java/com/group10/moneymate`, navigation graphs, Gradle setup, and existing tests.
>
> Key evidence sources include `MainActivity.java`, `di/AppContainer.java`, `data/local/*`, `data/repository/*`, `ui/*`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `res/navigation/nav_main.xml`, and `res/navigation/nav_auth.xml`.

---

## 1) System Architecture Overview

### 1.1 What architecture is actually implemented?

The project is **mostly MVVM + Repository + Room DAO**, with **manual DI** via `AppContainer`:

- Router bootstrap:
  - `MainActivity.onCreate()` decides `HomeActivity` vs `LoginActivity` via `authRepository.isLoggedIn()`.
- DI root:
  - `MoneyMateApplication.onCreate()` creates `AppContainer` and calls `bootstrapLocalData()`.
  - `AppContainer` wires database, helper, prefs, repositories.
- Data layering pattern in many features:
  - Fragment/Activity -> ViewModel -> Repository -> DAO -> Room.

However, it is **not uniformly applied** (hybrid):

- Some features follow AndroidViewModel + AppContainer access (e.g., `AuthViewModel`, `WalletViewModel`, `TransactionViewModel`, `SettingsViewModel`).
- Some features use custom `ViewModelProvider.Factory` + plain `ViewModel` (e.g., `BudgetViewModel`, `StatisticsViewModel`, `AddEditBudgetViewModel`).
- Some modules are placeholders (`ui/ai/*`, `ui/security/*`).

### 1.2 Why this leads to the current code state

Because multiple implementation styles coexist, the codebase shows:

- Strong feature growth speed (copy/adapt works quickly).
- Inconsistent patterns (constructor DI style, navigation style, lifecycle handling style).
- Drift between design intent and code reality (example: transaction balance-update rule documented, but repository constructor currently only receives `TransactionDao` in `AppContainer`).

### 1.3 Advantages and disadvantages

**Advantages**

- Straightforward onboarding for Android developers familiar with Fragment + LiveData + Room.
- Good offline core with reactive reads (`LiveData`) and background writes (`AppDatabase.databaseWriteExecutor`).
- Clear package grouping by feature under `ui/*` and by layer under `data/*`.

**Disadvantages**

- Pattern inconsistency increases maintenance cost.
- Harder testability where repositories use async executors + callback interfaces without deterministic schedulers.
- Architectural drift risks regressions (e.g., sync semantics and nav animation rules are inconsistently enforced).

---

## 2) Main Components and Their Roles

## 2.1 Top-level package map

- `com.group10.moneymate`
  - `MainActivity.java` (router entry)
- `com.group10.moneymate.di`
  - `MoneyMateApplication.java`, `AppContainer.java`
- `com.group10.moneymate.data`
  - `local/` (Room DB, entities, DAOs, migration, DTOs)
  - `remote/` (`FirebaseAuthHelper`)
  - `repository/` (Auth/User/Wallet/Category/Transaction/Budget/Debt/Event)
- `com.group10.moneymate.models`
  - enums/constants model layer (`TransactionType`, `WalletType`, etc.)
- `com.group10.moneymate.ui`
  - feature packages: `auth`, `main`, `home`, `transaction`, `category`, `budget`, `wallet`, `debt`, `event`, `statistics`, `profile`, `settings`, `security`, `ai`, `common`
- `com.group10.moneymate.utils`
  - prefs, formatting, date, icon helpers, UI helpers

(Workspace scan shows ~133 Java files in `app/src/main/java/com/group10/moneymate`.)

## 2.2 Core component responsibilities

### Activities

- `MainActivity`
  - Responsibility: app launch router.
  - Why exists: centralize first-screen decision.
  - Lifecycle/creation: launcher activity from `AndroidManifest.xml`; direct repository access through `AppContainer`.
- `LoginActivity`
  - Responsibility: host auth nav graph UI.
  - Why exists: isolate auth flow from main flow.
  - Lifecycle: redirects in `onStart()` if already logged in.
- `HomeActivity`
  - Responsibility: host main nav graph + custom bottom navigation.
  - Why exists: single shell for post-login features.
  - Lifecycle: sets listeners and destination-change behavior in `onCreate()`.

### ViewModels

- `AuthViewModel` (`AndroidViewModel`)
  - Responsibility: auth state machine (`IDLE/LOADING/AUTHENTICATED/...`).
  - Why exists: remove auth logic from fragments.
  - Creation: `new ViewModelProvider(this).get(AuthViewModel.class)`.
- `TransactionViewModel` (`AndroidViewModel`)
  - Responsibility: transaction lists, filters, picker sources.
  - Why exists: expose filtered/query state via LiveData.
- `WalletViewModel` (`AndroidViewModel`)
  - Responsibility: wallet CRUD + total balance.
- `BudgetViewModel` (`ViewModel` + factory)
  - Responsibility: aggregate budget list, partitioning (this month/future/custom), summary.
  - Notable: extensive `observeForever` orchestration and manual source maps.
- `StatisticsViewModel` (`ViewModel` + factory)
  - Responsibility: date/wallet filter state, totals, category slices, net summary.

### Repositories

- `AuthRepository`
  - Uses Firebase auth helper + UserDao + PrefsManager.
  - Bridges remote auth and local user persistence.
- `TransactionRepository`
  - Exposes many analytics/reporting reads; writes mark sync status and timestamps.
- `BudgetRepository`
  - Contains business rules for "all categories" and virtual "other categories" budget.
- `CategoryRepository`
  - Hierarchy validation, default seed, virtual categories.
- `WalletRepository`, `UserRepository`, `DebtRepository`, `EventRepository`
  - Thin wrappers around DAO + executor writes.

### Data sources

- Local DB: `AppDatabase` (Room, version 8) with entities and DAO set.
- Remote auth: `FirebaseAuthHelper` for FirebaseAuth operations.
- Note: Firestore sync layer is **not implemented yet** in current Java sources.

### UI adapters/custom views

- Widespread `ListAdapter + DiffUtil` usage for lists.
- Custom visual components in budget/statistics (`BudgetArcProgressView`, `StatisticsDonutBreakdownView`, etc.).

---

## 3) Relationships and Communication Between Components

## 3.1 Communication mechanisms used

- **Direct method calls** between layers (Fragment -> ViewModel -> Repository -> DAO).
- **LiveData observation** for reactive UI updates.
- **Transformations.switchMap** for dependent query streams (`TransactionViewModel`, `StatisticsViewModel`).
- **SavedStateHandle** for fragment result passing (wallet/category pickers).
- **Navigation Safe Args** for argument transfer.
- **Callback interfaces** for async write completion (`BudgetRepository.WriteCallback`, `AuthRepository.AuthCallback`).

No EventBus/RxJava/Flow/BroadcastReceiver orchestration found in app layer.

## 3.2 Typical data flow (example: Add/Edit Transaction)

```text
AddEditTransactionFragment
  -> TransactionViewModel.insertTransaction/updateTransaction
    -> TransactionRepository.insertTransaction/updateTransaction
      -> AppDatabase.databaseWriteExecutor
        -> TransactionDao.insertTransaction/updateTransaction
          -> Room DB updates
            -> LiveData observers in TransactionListFragment refresh UI
```

Relevant code points:

- UI trigger: `ui/transaction/AddEditTransactionFragment.java` (`setupSaveButton()`)
- VM call: `ui/transaction/TransactionViewModel.java` (`insertTransaction`, `updateTransaction`)
- Repo write: `data/repository/TransactionRepository.java`
- DAO SQL: `data/local/dao/TransactionDao.java`

## 3.3 Is this communication method appropriate?

Mostly yes for this project size:

- LiveData + Room gives low-boilerplate reactive updates.
- Manual DI keeps complexity lower than Hilt for small teams.

But current implementation needs tighter consistency:

- Some features use factory DI while others use AndroidViewModel global access.
- Several flows rely on non-type-safe string constants and manual state propagation.

---

## 4) Techniques and Technologies Applied

## 4.1 Libraries/frameworks detected

From `app/build.gradle.kts` and `gradle/libs.versions.toml`:

- AndroidX: AppCompat, Activity, Fragment, ConstraintLayout
- Material Components (XML View system, not Compose)
- Room (`room-runtime`, `room-compiler`)
- Lifecycle (`lifecycle-viewmodel`, `lifecycle-livedata`)
- Navigation + Safe Args
- Firebase Auth + Firebase Analytics + Firebase Firestore dependency
- WorkManager dependency
- CameraX dependency set
- ML Kit text recognition dependency
- Gemini SDK dependency
- MPAndroidChart
- Biometric dependency

## 4.2 How they are used in this project

- **Room**: actively used as primary local source (`AppDatabase`, `*Entity`, `*Dao`).
- **Navigation**: actively used (`nav_auth.xml`, `nav_main.xml`, Safe Args directions classes).
- **Lifecycle LiveData/ViewModel**: actively used across most features.
- **Firebase Auth**: actively used via `FirebaseAuthHelper` + `AuthRepository`.

Partially/unused in current source snapshot:

- **Firestore**: dependency present, but no app-layer Firestore integration classes found.
- **WorkManager**: dependency present, no Worker implementation found.
- **CameraX/MLKit/Gemini/Biometric**: dependencies present, AI/security features are placeholders.

## 4.3 Integration quality assessment

- Correct and good: Room + LiveData + Navigation integration core.
- Needs improvement:
  - Unused heavyweight dependencies increase APK/build footprint.
  - Several hardcoded strings in Java/SQL instead of string resources, reducing i18n consistency.
  - Navigation animation policy is not uniformly applied in `nav_main.xml`.

---

## 5) Potential Problems and Anomalies (Risk Analysis)

## 5.1 High-risk issues

1. **`sync_status` semantics inconsistent for delete operations**
   - Evidence:
     - `TransactionDao.softDelete()` sets `sync_status = 1`.
     - `DebtDao.softDelete()` sets `sync_status = 1`.
     - `EventDao.softDelete()` sets `sync_status = 1`.
     - Meanwhile wallet/category delete paths use `sync_status = 2`.
   - Why risky: if cloud sync is introduced, delete intents may be misinterpreted as upload/update.
   - Likely cause: copy-paste + partial migration of sync convention.

2. **Documented transaction-wallet balance coupling not implemented in current wiring**
   - Evidence:
     - `AppContainer` creates `new TransactionRepository(database.transactionDao())` (single DAO).
     - `TransactionRepository` has no `WalletDao` and does not apply wallet deltas on CRUD.
   - Why risky: business expectation and actual balance model can diverge.

3. **Navigation action animations are incomplete in many actions**
   - Evidence in `nav_main.xml`:
     - e.g., actions at lines around home/settings/budget fragments only define partial animation fields or none.
   - Why risky: inconsistent UX, unexpected transitions, and violates internal navigation convention.

## 5.2 Medium-risk issues

4. **Pattern inconsistency in ViewModel construction**
   - Mixed styles:
     - AndroidViewModel + AppContainer (auth/wallet/transaction/settings)
     - Plain ViewModel + custom Factory (budget/statistics)
   - Why risky: higher cognitive load; new contributors may duplicate the wrong pattern.

5. **`observeForever` complexity in `BudgetViewModel`**
   - Evidence: multiple `observeForever` maps and manual cleanup in `onCleared()`.
   - Why risky: easy to leak or mis-handle source lifecycle during refactors.

6. **Hardcoded UI/domain text in code paths**
   - Examples:
     - "Tổng cộng", "Chưa phân loại", "Hôm qua", "TẤT CẢ", "Các mục khác" in repositories/viewmodels/fragments.
   - Why risky: localization inconsistency and brittle text behavior.

7. **Feature mismatch bug in settings navigation**
   - Evidence: `SettingsFragment.setupListeners()` maps `btnBudgets` to `actionSettingsToStatistics()`.
   - Why risky: wrong destination for user intent.

8. **Direct `findViewById` usage in a ViewBinding-centric project**
   - Evidence: `AddEditWalletFragment` retrieves bottom nav via `requireActivity().findViewById(...)`.
   - Why risky: fragile coupling to host layout ID and potential NPE/future regression.

## 5.3 Lower-risk but meaningful concerns

9. **Stubs shipped as real feature entry points**
   - `ui/ai/*`, `ui/security/*` contain placeholder fragments/viewmodels.
   - Risk: users can navigate to incomplete features.

10. **Dependency drift / unused integrations**
    - Firestore/WorkManager/CameraX/MLKit/Gemini/Biometric are declared but not integrated in current Java sources.

11. **Testing coverage is very limited for project size**
    - Unit tests: 1 sample (`ExampleUnitTest`).
    - Instrumented tests: 6 files (mostly focused slices).
    - Risk: regression probability high for navigation/lifecycle-heavy flows.

---

## 6) Likely Reasons for the Current Code State (Development Process Deduction)

Observed indicators suggest **rapid feature-first development with iterative patching**:

- Frequent pattern mixing (different DI/ViewModel creation styles).
- Some advanced modules are placeholders while navigation/dependency declarations already expose them.
- Business rules partially centralized (good in `BudgetRepository`, `CategoryRepository`) but not fully consistent in others.
- Signs of copy-adapt coding:
  - Similar CRUD scaffolding repeated with subtle convention differences (`sync_status` values, toolbar handling, animation attrs).
  - Comments indicating planned future implementation in AI/security viewmodels.

This is typical of a team optimizing for delivery speed first, then architecture hardening later.

---

## 7) Specific Improvement Guidance (Safe Refactoring Roadmap)

## Phase 0 - Stabilize critical behavior (short term, 1-2 sprints)

1. **Normalize sync semantics**
   - Introduce centralized constants usage in all DAO soft-delete queries.
   - Align delete operations to `PENDING_DELETE` consistently.

2. **Fix obvious UX/flow mismatches**
   - Correct `SettingsFragment` budget button destination.
   - Enforce animation completeness for all nav actions in `nav_main.xml`.

3. **Feature gating for placeholders**
   - Hide or disable AI/security entries until implemented, or show explicit "coming soon" guard.

## Phase 1 - Architecture consistency (mid term)

4. **Choose one ViewModel provisioning strategy per module**
   - Option A: keep AndroidViewModel + AppContainer for simplicity.
   - Option B: move to explicit factory-based constructor injection everywhere.
   - Apply consistently in all `ui/*/ViewModel` classes.

5. **Harden transaction business invariants**
   - Decide single source of truth for wallet balance behavior:
     - computed from transactions only, or
     - updated via repository side-effects.
   - Refactor `TransactionRepository` + `WalletDao` accordingly.

6. **Extract shared list/add-edit fragment patterns**
   - Introduce base utilities for toolbar/back handling, empty state, and result observers.

## Phase 2 - Platform completeness and quality

7. **Implement or remove declared but unused integrations**
   - Firestore sync service and WorkManager worker.
   - AI/security modules or dependency pruning.

8. **Test strategy upgrade**
   - Add repository tests for sync status and soft-delete behavior.
   - Add navigation/lifecycle tests for add-edit and picker flows.
   - Add regression tests for settings routing and budget summary calculations.

9. **Localization and string governance**
   - Move all user-facing literals from Java/SQL assembly to resources where practical.

---

## Appendix A - Representative Evidence Pointers

- Router and app bootstrap
  - `app/src/main/java/com/group10/moneymate/MainActivity.java`
  - `app/src/main/java/com/group10/moneymate/di/MoneyMateApplication.java`
  - `app/src/main/java/com/group10/moneymate/di/AppContainer.java`
- Room and migration
  - `app/src/main/java/com/group10/moneymate/data/local/AppDatabase.java`
  - `app/src/main/java/com/group10/moneymate/data/local/migrations/Migration7To8.java`
- Transaction stack
  - `app/src/main/java/com/group10/moneymate/ui/transaction/AddEditTransactionFragment.java`
  - `app/src/main/java/com/group10/moneymate/ui/transaction/TransactionViewModel.java`
  - `app/src/main/java/com/group10/moneymate/data/repository/TransactionRepository.java`
  - `app/src/main/java/com/group10/moneymate/data/local/dao/TransactionDao.java`
- Budget stack
  - `app/src/main/java/com/group10/moneymate/ui/budget/BudgetViewModel.java`
  - `app/src/main/java/com/group10/moneymate/data/repository/BudgetRepository.java`
  - `app/src/main/java/com/group10/moneymate/ui/budget/AddEditBudgetFragment.java`
- Auth stack
  - `app/src/main/java/com/group10/moneymate/ui/auth/AuthViewModel.java`
  - `app/src/main/java/com/group10/moneymate/data/repository/AuthRepository.java`
  - `app/src/main/java/com/group10/moneymate/data/remote/FirebaseAuthHelper.java`
- Navigation + dependencies
  - `app/src/main/res/navigation/nav_main.xml`
  - `app/src/main/res/navigation/nav_auth.xml`
  - `app/build.gradle.kts`
  - `gradle/libs.versions.toml`

---

## Appendix B - Short Architecture Diagram (Current State)

```text
[MainActivity]
    -> checks auth via AppContainer.authRepository
    -> [LoginActivity] (auth graph) OR [HomeActivity] (main graph)

[Fragment]
    -> [ViewModel] (LiveData state, filters)
        -> [Repository] (rules + threading)
            -> [DAO] (SQL)
                -> [Room DB]

Remote currently active:
    Auth only -> FirebaseAuthHelper -> FirebaseAuth

Remote declared but not integrated in code paths yet:
    Firestore / WorkManager sync, AI receipt/chat pipelines
```

