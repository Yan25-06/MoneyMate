# MoneyMate — Cấu trúc dự án thu gọn

## Kiến trúc: MVVM + Repository Pattern

## So sánh Trước vs Sau thu gọn

| | Trước | Sau | Giảm |
|---|---|---|---|
| **Java files** | ~93 | ~55 | **-40%** |
| **Layout XML** | 40 | 17 | **-57%** |
| **Dependencies** | 12 | 7 | **-42%** |

---

## Cấu trúc Java (~55 files)

```
com.example.moneymate/
├── MainActivity.java                  ← Router (login or home)
│
├── models/
│   ├── TransactionType.java           (INCOME, EXPENSE)
│   └── WalletType.java                (CASH, BANK, E_WALLET)
│
├── data/
│   ├── local/
│   │   ├── AppDatabase.java           ← Room Database (5 entities, 5 DAOs)
│   │   ├── Converters.java
│   │   ├── entity/
│   │   │   ├── UserEntity.java
│   │   │   ├── TransactionEntity.java
│   │   │   ├── CategoryEntity.java
│   │   │   ├── BudgetEntity.java
│   │   │   └── WalletEntity.java
│   │   └── dao/
│   │       ├── UserDao.java
│   │       ├── TransactionDao.java
│   │       ├── CategoryDao.java
│   │       ├── BudgetDao.java
│   │       └── WalletDao.java
│   ├── remote/
│   │   └── FirebaseAuthHelper.java    ← Email/Password only
│   └── repository/
│       ├── AuthRepository.java
│       ├── UserRepository.java
│       ├── TransactionRepository.java
│       ├── CategoryRepository.java
│       ├── BudgetRepository.java
│       └── WalletRepository.java
│
├── di/
│   ├── AppContainer.java              ← Manual DI
│   └── MoneyMateApplication.java
│
├── utils/
│   ├── Constants.java
│   ├── CurrencyFormatter.java
│   ├── DateUtils.java
│   └── PrefsManager.java
│
└── ui/
    ├── auth/        LoginActivity, LoginFragment, RegisterFragment, AuthViewModel
    ├── main/        HomeActivity (BottomNav host)
    ├── home/        HomeFragment, HomeViewModel
    ├── transaction/ TransactionListFragment, AddEditTransactionFragment, TransactionAdapter, TransactionViewModel
    ├── category/    CategoryListFragment, AddEditCategoryFragment, CategoryAdapter, CategoryViewModel
    ├── budget/      BudgetListFragment, AddEditBudgetFragment, BudgetAdapter, BudgetViewModel
    ├── wallet/      WalletListFragment, AddEditWalletFragment, WalletAdapter, WalletViewModel
    ├── statistics/  StatisticsFragment, StatisticsViewModel
    ├── profile/     ProfileFragment, ProfileViewModel
    └── settings/    SettingsFragment, SettingsViewModel
```

---

## Layout XML (17 files)

```
activity_main.xml, activity_login.xml, activity_home.xml
fragment_login.xml, fragment_register.xml
fragment_home.xml
fragment_transaction_list.xml, fragment_add_edit_transaction.xml
fragment_category_list.xml, fragment_add_edit_category.xml
fragment_budget_list.xml, fragment_add_edit_budget.xml
fragment_wallet_list.xml, fragment_add_edit_wallet.xml
fragment_statistics.xml
fragment_profile.xml
fragment_settings.xml
item_transaction.xml, item_category.xml, item_budget.xml, item_wallet.xml
```

---

## Dependencies (build.gradle.kts)

| Thư viện | Mục đích |
|----------|----------|
| Room 2.6.1 | Local database |
| Firebase Auth | Đăng nhập Email/Password |
| Navigation 2.8.6 | Fragment navigation |
| Lifecycle 2.8.7 | ViewModel + LiveData |
| MPAndroidChart v3.1.0 | PieChart thống kê |
| Material Design 3 | UI Components |

---

## App Flow

```
MainActivity (router)
├── Đã login → HomeActivity
└── Chưa login → LoginActivity
      ├── Login (email/password)
      └── Register

HomeActivity — BottomNav 4 tabs
├── 🏠 Home (dashboard, balance, recent transactions)
├── 💰 Transactions (list, add, edit)
├── 📊 Statistics (PieChart theo danh mục)
└── ⚙️ Settings
      ├── Profile
      ├── Categories
      ├── Budgets
      └── Wallets
```

---

## Tính năng đã loại bỏ (có thể thêm sau)

- Firebase Firestore sync
- Google Sign-In
- Passcode login
- Giao dịch định kỳ (Recurring)
- Nhắc nhở (Reminders)
- Chuyển tiền giữa ví
- Tìm kiếm & lọc
- AI auto-fill
- WorkManager workers
- Glide (load ảnh avatar)
- BarChart
