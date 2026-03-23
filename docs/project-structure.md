# MoneyMate — Cấu trúc dự án

## Kiến trúc: MVVM + Repository Pattern + Manual DI

---

## Cấu trúc Java

```
com.group10.moneymate/
├── MainActivity.java                        ← Router (login or home)
│
├── models/
│   ├── TransactionType.java                 (INCOME, EXPENSE)
│   ├── WalletType.java                      (CASH, BANK, E_WALLET)
│   ├── CategoryType.java                    (INCOME, EXPENSE)
│   ├── DebtType.java                        (LEND, BORROW)
│   ├── DebtStatus.java                      (ACTIVE, SETTLED)
│   └── SyncStatus.java                      (SYNCED=0, PENDING_UPLOAD=1, PENDING_DELETE=2)
│
├── data/
│   ├── local/
│   │   ├── AppDatabase.java                 ← Room singleton (version 3, 7 entities)
│   │   ├── Converters.java                  ← TypeConverters: Enum↔String, Date↔Long
│   │   ├── entity/
│   │   │   ├── UserEntity.java
│   │   │   ├── WalletEntity.java
│   │   │   ├── CategoryEntity.java
│   │   │   ├── TransactionEntity.java
│   │   │   ├── BudgetEntity.java
│   │   │   ├── DebtEntity.java
│   │   │   └── EventEntity.java
│   │   └── dao/
│   │       ├── UserDao.java
│   │       ├── WalletDao.java
│   │       ├── CategoryDao.java             ← thêm countDefaultCategoriesByUid()
│   │       ├── TransactionDao.java
│   │       ├── BudgetDao.java
│   │       ├── DebtDao.java
│   │       └── EventDao.java
│   ├── remote/
│   │   └── FirebaseAuthHelper.java
│   └── repository/
│       ├── AuthRepository.java
│       ├── UserRepository.java
│       ├── WalletRepository.java
│       ├── CategoryRepository.java          ← seedDefaults(), soft delete, background executor
│       ├── TransactionRepository.java
│       ├── BudgetRepository.java
│       ├── DebtRepository.java
│       └── EventRepository.java
│
├── di/
│   ├── AppContainer.java                    ← Manual DI + seedDefaultCategoriesIfNeeded()
│   └── MoneyMateApplication.java            ← getAppContainer()
│
├── utils/
│   ├── Constants.java                       ← Prefs keys, DefaultCategory, getDefaultCategories()
│   ├── CurrencyFormatter.java
│   ├── DateUtils.java
│   └── PrefsManager.java                    ← getUid(), saveUid(), isLoggedIn(), setLoggedIn()
│
└── ui/
    ├── auth/
    │   ├── LoginActivity.java
    │   ├── LoginFragment.java
    │   ├── RegisterFragment.java
    │   ├── ForgotPasswordFragment.java
    │   └── AuthViewModel.java
    ├── main/
    │   └── HomeActivity.java
    ├── home/
    │   ├── HomeFragment.java
    │   └── HomeViewModel.java
    ├── transaction/
    │   ├── TransactionListFragment.java
    │   ├── AddEditTransactionFragment.java
    │   ├── TransactionAdapter.java
    │   └── TransactionViewModel.java
    ├── category/                             ← ✅ Phase 3 hoàn thành
    │   ├── CategoryListFragment.java         ← TabLayout, RecyclerView, FAB, Safe Args
    │   ├── AddEditCategoryFragment.java      ← Add/Edit mode, color picker
    │   ├── CategoryAdapter.java              ← ListAdapter + DiffUtil
    │   └── CategoryViewModel.java            ← AndroidViewModel, switchMap filter
    ├── budget/
    │   ├── BudgetListFragment.java
    │   ├── AddEditBudgetFragment.java
    │   ├── BudgetAdapter.java
    │   └── BudgetViewModel.java
    ├── wallet/
    │   ├── WalletListFragment.java
    │   ├── AddEditWalletFragment.java
    │   ├── WalletAdapter.java
    │   └── WalletViewModel.java
    ├── debt/
    │   ├── DebtListFragment.java
    │   ├── AddEditDebtFragment.java
    │   └── DebtViewModel.java
    ├── event/
    │   ├── EventListFragment.java
    │   ├── AddEditEventFragment.java
    │   └── EventViewModel.java
    ├── statistics/
    │   ├── StatisticsFragment.java
    │   └── StatisticsViewModel.java
    ├── profile/
    │   ├── ProfileFragment.java
    │   └── ProfileViewModel.java
    ├── settings/
    │   ├── SettingsFragment.java             ← navigation tới tất cả destinations
    │   └── SettingsViewModel.java
    ├── security/
    │   ├── PasscodeFragment.java
    │   └── SecurityViewModel.java
    ├── ai/
    │   ├── AIAssistantFragment.java
    │   ├── AIReceiptScannerFragment.java
    │   └── AIViewModel.java
    └── common/
        └── BaseListAdapter.java
```

---

## Layout XML

```
res/layout/
├── activity_main.xml
├── activity_login.xml
├── activity_home.xml
├── fragment_login.xml
├── fragment_register.xml
├── fragment_forgot_password.xml
├── fragment_home.xml
├── fragment_transaction_list.xml
├── fragment_add_edit_transaction.xml
├── fragment_category_list.xml               ← ✅ Phase 3.4
├── fragment_add_edit_category.xml           ← ✅ Phase 3.4
├── fragment_budget_list.xml
├── fragment_add_edit_budget.xml
├── fragment_wallet_list.xml
├── fragment_add_edit_wallet.xml
├── fragment_debt_list.xml
├── fragment_add_edit_debt.xml
├── fragment_event_list.xml
├── fragment_add_edit_event.xml
├── fragment_statistics.xml
├── fragment_profile.xml
├── fragment_settings.xml                    ← navigation buttons đầy đủ
├── fragment_passcode.xml
├── fragment_ai_assistant.xml
├── fragment_ai_receipt_scanner.xml
├── item_transaction.xml
├── item_category.xml                        ← ✅ Phase 3.4
├── item_budget.xml
└── item_wallet.xml
```

---

## Drawables

```
res/drawable/
├── bg_circle_icon.xml                       ← ✅ mới thêm (Phase 3.4)
├── bg_circle_color_preview.xml              ← ✅ mới thêm (Phase 3.4)
├── ic_launcher_background.xml
├── ic_launcher_foreground.xml
├── outline_account_balance_24.xml
├── outline_account_balance_wallet_24.xml
├── outline_add_24.xml
├── outline_arrow_back_24.xml
├── outline_attach_money_24.xml
├── outline_bar_chart_24.xml
├── outline_close_24.xml
├── outline_credit_card_24.xml
├── outline_home_24.xml
├── outline_more_horiz_24.xml
├── outline_payments_24.xml
├── outline_receipt_24.xml
├── outline_settings_24.xml
├── outline_visibility_24.xml
└── outline_visibility_off_24.xml
```

---

## Navigation

```
res/navigation/
├── nav_auth.xml     ← Login → Register → ForgotPassword
└── nav_main.xml     ← Home, Transactions, Statistics, Settings + tất cả sub-screens
```

---

## Dependencies (build.gradle.kts)

| Thư viện | Mục đích |
|----------|----------|
| Room 2.8.4 | Local database (offline-first) |
| Firebase Auth (BOM 34.10.0) | Đăng nhập Email/Password |
| Firebase Firestore | Cloud backup & sync |
| Navigation 2.9.7 + Safe Args | Fragment navigation |
| Lifecycle 2.10.0 | ViewModel + LiveData |
| MPAndroidChart v3.1.0 | PieChart, BarChart thống kê |
| Material Design 3 (1.13.0) | UI Components |
| Biometric 1.1.0 | Vân tay / Face ID |
| WorkManager 2.10.1 | Background sync |
| CameraX 1.4.2 | Camera quét hoá đơn |
| ML Kit 16.0.1 | OCR nhận diện văn bản |
| Gemini AI 0.9.0 | AI assistant |

---

## App Flow

```
MainActivity (router)
├── Có passcode → PasscodeFragment
├── Firebase logged in → HomeActivity
└── Chưa login → LoginActivity
      ├── LoginFragment
      ├── RegisterFragment
      └── ForgotPasswordFragment

HomeActivity — BottomNav 4 tabs
├── 🏠 Home
├── 💰 Transactions
├── 📊 Statistics
└── ⚙️ Settings
      ├── Profile
      ├── Security (Passcode)
      ├── Wallets
      ├── Categories        ← ✅ Phase 3 done
      ├── Budgets
      ├── Debts
      └── Events
```

---

## Trạng thái phát triển

| Phase | Nội dung | Trạng thái |
|-------|----------|------------|
| 0 | Foundation & Scaffolding | ✅ Hoàn thành |
| 1 | Authentication | ✅ Hoàn thành |
| 2 | Wallet CRUD | ✅ Hoàn thành |
| 3 | Category CRUD + seed defaults | ✅ Hoàn thành |
| 4 | Transaction CRUD | 🔲 Chưa bắt đầu |
| 5 | Home Dashboard | 🔲 Chưa bắt đầu |
| 6 | Budget | 🔲 Chưa bắt đầu |
| 7 | Statistics | 🔲 Chưa bắt đầu |
| 8 | Profile & Settings | 🔲 Chưa bắt đầu |
| 9 | Passcode | 🔲 Chưa bắt đầu |
| 10 | Polish & QA | 🔲 Chưa bắt đầu |