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
│   │   ├── AppDatabase.java                 ← Room singleton (version 7, 7 entities)
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
│   │       ├── CategoryDao.java             ← countDefaultCategoriesByUid(), query hỗ trợ category ảo budget
│   │       ├── TransactionDao.java          ← query filter budget, detail, aggregate, các mục khác
│   │       ├── BudgetDao.java               ← query theo user, active, finished
│   │       ├── DebtDao.java
│   │       └── EventDao.java
│   ├── remote/
│   │   └── FirebaseAuthHelper.java
│   └── repository/
│       ├── AuthRepository.java
│       ├── UserRepository.java
│       ├── WalletRepository.java
│       ├── CategoryRepository.java          ← seedDefaults(), soft delete, background executor, category ảo cho budget
│       ├── TransactionRepository.java       ← CRUD + query phục vụ budget/detail/filter
│       ├── BudgetRepository.java
│       ├── DebtRepository.java
│       └── EventRepository.java
│
├── di/
│   ├── AppContainer.java                    ← Manual DI + wiring các repository
│   └── MoneyMateApplication.java            ← getAppContainer(), restore local state sau migration
│
├── utils/
│   ├── Constants.java                       ← Prefs keys, DefaultCategory, ID ảo cho budget
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
    ├── category/
    │   ├── CategoryListFragment.java
    │   ├── AddEditCategoryFragment.java
    │   ├── CategoryAdapter.java
    │   ├── CategoryIconAdapter.java
    │   └── CategoryViewModel.java
    ├── budget/                              ← ✅ Phase 6 hoàn thành
    │   ├── BudgetListFragment.java
    │   ├── BudgetFinishedFragment.java
    │   ├── BudgetDetailFragment.java
    │   ├── BudgetWalletPickerFragment.java
    │   ├── AddEditBudgetFragment.java
    │   ├── AddEditBudgetViewModel.java
    │   ├── BudgetAdapter.java
    │   ├── BudgetBreakdownAdapter.java
    │   ├── BudgetWalletPickerAdapter.java
    │   ├── BudgetStatisticsCalculator.java
    │   ├── BudgetProjectionChartView.java
    │   ├── BudgetArcProgressView.java
    │   ├── BudgetUIModel.java
    │   ├── BudgetUiUtils.java
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
    │   ├── SettingsFragment.java             ← navigation tới wallets/categories/budgets/statistics/debts/events
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
├── fragment_category_list.xml
├── fragment_add_edit_category.xml
├── fragment_budget_list.xml
├── fragment_budget_finished.xml
├── fragment_budget_detail.xml
├── fragment_budget_wallet_picker.xml
├── fragment_add_edit_budget.xml
├── fragment_wallet_list.xml
├── fragment_add_edit_wallet.xml
├── fragment_debt_list.xml
├── fragment_add_edit_debt.xml
├── fragment_event_list.xml
├── fragment_add_edit_event.xml
├── fragment_statistics.xml
├── fragment_profile.xml
├── fragment_settings.xml
├── fragment_passcode.xml
├── fragment_ai_assistant.xml
├── fragment_ai_receipt_scanner.xml
├── item_transaction.xml
├── item_category.xml
├── item_category_icon.xml
├── item_budget.xml
├── item_budget_breakdown.xml
├── item_budget_wallet_picker.xml
└── item_wallet.xml
```

---

## Drawables

```
res/drawable/
├── bg_circle_icon.xml
├── bg_circle_color_preview.xml
├── bg_budget_header_icon_button.xml
├── bg_budget_wallet_filter.xml
├── bg_budget_wallet_picker_selected.xml
├── bg_budget_wallet_selected_dot.xml
├── ic_category_food.xml
├── ic_category_transport.xml
├── ic_category_shopping.xml
├── ic_category_entertain.xml
├── ic_category_health.xml
├── ic_category_education.xml
├── ic_category_bill.xml
├── ic_category_house.xml
├── ic_category_travel.xml
├── ic_category_other.xml
├── ic_category_salary.xml
├── ic_category_bonus.xml
├── ic_category_invest.xml
├── ic_category_sale.xml
├── ic_category_gift.xml
├── ic_category_other_in.xml
├── outline_history_24.xml
├── outline_warning_amber_24.xml
└── ...
```

---

## Navigation

```
res/navigation/
├── nav_auth.xml     ← Login → Register → ForgotPassword
└── nav_main.xml     ← Home, Transactions, Budgets, Settings + tất cả sub-screens
                        Có thêm budget detail, finished budgets, wallet picker
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
├── 💼 Budgets
└── ⚙️ Settings
      ├── Profile
      ├── Security (Passcode)
      ├── Wallets
      ├── Categories
      ├── Budgets
      ├── Statistics
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
| 4 | Transaction CRUD | ✅ Hoàn thành |
| 5 | Home Dashboard | ✅ Cơ bản |
| 6 | Budget | ✅ Hoàn thành |
| 7 | Statistics | ⏳ Mục tiêu tiếp theo |
| 8 | Profile & Settings | 🔄 Đã có nền tảng |
| 9 | Passcode | 🔄 Đã có nền tảng |
| 10 | Polish & QA | 🔄 Đang diễn ra |
