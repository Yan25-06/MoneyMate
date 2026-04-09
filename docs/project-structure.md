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
│   │   ├── AppDatabase.java                 ← Room singleton (8 entities, 8 DAOs)
│   │   ├── Converters.java                  ← TypeConverters: Enum↔String, Date↔Long
│   │   ├── entity/
│   │   │   ├── UserEntity.java
│   │   │   ├── WalletEntity.java
│   │   │   ├── CategoryEntity.java
│   │   │   ├── TransactionEntity.java
│   │   │   ├── BudgetEntity.java
│   │   │   ├── DebtEntity.java
│   │   │   ├── EventEntity.java
│   │   │   └── SyncMetadataEntity.java
│   │   ├── dao/
│   │   │   ├── UserDao.java
│   │   │   ├── WalletDao.java
│   │   │   ├── CategoryDao.java
│   │   │   ├── TransactionDao.java
│   │   │   ├── BudgetDao.java
│   │   │   ├── DebtDao.java
│   │   │   ├── EventDao.java
│   │   │   └── SyncMetadataDao.java
│   │   ├── dto/
│   │   │   ├── CategorySumDTO.java
│   │   │   ├── DailyTrendDTO.java
│   │   │   ├── NetIncomeDTO.java
│   │   │   └── WalletWithBalance.java
│   │   └── migrations/
│   │       ├── Migration7To8.java
│   │       ├── Migration8To9.java
│   │       ├── Migration9To10.java
│   │       ├── Migration10To11.java
│   │       ├── Migration11To12.java
│   │       └── Migration12To13.java
│   ├── remote/
│   │   └── FirebaseAuthHelper.java
│   └── repository/
│       ├── AuthRepository.java
│       ├── UserRepository.java
│       ├── WalletRepository.java
│       ├── CategoryRepository.java
│       ├── TransactionRepository.java
│       ├── BudgetRepository.java
│       ├── DebtRepository.java
│       ├── EventRepository.java
│       └── SyncMetadataRepository.java
│
├── di/
│   ├── AppContainer.java                    ← Manual DI + wiring các repository
│   ├── MoneyMateApplication.java            ← getAppContainer(), restore local state sau migration
│   └── MoneyMateWorkerFactory.java
│
├── utils/
│   ├── Constants.java
│   ├── CurrencyFormatter.java
│   ├── DateUtils.java
│   ├── DistinctLiveData.java
│   ├── ForegroundUiNotifier.java
│   ├── IconProvider.java
│   ├── LoadingHelper.java
│   ├── MoneyMateDatePickerHelper.java
│   ├── NotificationHelper.java
│   ├── PrefsManager.java
│   ├── TimeWindowUtils.java
│   └── WalletSelectorButtonHelper.java
│
├── workers/
│   ├── AIReceiptScannerWorker.java
│   ├── SyncRetryReceiver.java
│   ├── SyncScheduler.java
│   └── SyncWorker.java
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
    │   ├── HomeRecentTransactionAdapter.java
    │   ├── HomeTopSpendingAdapter.java
    │   ├── HomeViewModel.java
    │   └── HomeWalletAdapter.java
    ├── transaction/
    │   ├── TransactionListFragment.java
    │   ├── TransactionDetailFragment.java
    │   ├── AddEditTransactionFragment.java
    │   ├── TransactionAdapter.java
    │   ├── TransactionTimeGroupAdapter.java
    │   ├── TransactionViewModel.java
    │   ├── TransactionCategoryPickerFragment.java
    │   ├── TransactionCategoryPickerAdapter.java
    │   ├── TransactionCategoryPickerItem.java
    │   ├── TransactionCategoryPickerViewModel.java
    │   ├── CategoryIconPickerFragment.java
    │   ├── CategoryIconOnlyAdapter.java
    │   ├── ReportTransactionListFragment.java
    │   ├── ReportTransactionAdapter.java
    │   ├── ReportTransactionDayHeaderAdapter.java
    │   └── LedgerSectionHeaderAdapter.java
    ├── category/
    │   ├── CategoryListFragment.java
    │   ├── AddEditCategoryFragment.java
    │   ├── CategoryAdapter.java
    │   ├── CategoryIconAdapter.java
    │   ├── CategoryListItem.java
    │   ├── CategoryViewModel.java
    │   ├── CategoryWalletAdapter.java
    │   ├── ParentCategoryPickerFragment.java
    │   └── ParentCategoryPickerAdapter.java
    ├── budget/
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
    │   ├── WalletIconOnlyAdapter.java
    │   ├── WalletIconPickerFragment.java
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
    │   ├── StatisticsOverviewFragment.java
    │   ├── StatisticsDetailFragment.java
    │   ├── StatisticsCategoryDetailFragment.java
    │   ├── StatisticsCategoryDayDetailFragment.java
    │   ├── StatisticsCategoryDayDetailViewModel.java
    │   ├── StatisticsViewModel.java
    │   ├── StatisticsCategoryBreakdownAdapter.java
    │   ├── StatisticsPeriodSummaryAdapter.java
    │   ├── StatisticsDonutBreakdownView.java
    │   ├── StatisticsConnectorOverlayView.java
    │   ├── IncomeExpenseDetailFragment.java
    │   ├── IncomeExpenseDetailViewModel.java
    │   ├── CategoryReportFragment.java
    │   └── CategoryReportViewModel.java
    ├── profile/
    │   ├── ProfileFragment.java
    │   └── ProfileViewModel.java
    ├── settings/
    │   ├── SettingsFragment.java
    │   └── SettingsViewModel.java
    ├── security/
    │   ├── PasscodeFragment.java
    │   └── SecurityViewModel.java
    ├── ai/
    │   ├── AIAssistantFragment.java
    │   ├── AIReceiptScannerFragment.java
    │   └── AIViewModel.java
    └── common/
        ├── BaseListAdapter.java
        ├── DebounceableAndroidViewModel.java
        └── DebounceableViewModel.java
```

---

## Layout XML

```
res/layout/
├── activity_main.xml
├── activity_login.xml
├── activity_home.xml
│
├── fragment_login.xml
├── fragment_register.xml
├── fragment_forgot_password.xml
├── fragment_home.xml
├── fragment_transaction_list.xml
├── fragment_transaction_detail.xml
├── fragment_add_edit_transaction.xml
├── fragment_transaction_category_picker.xml
├── fragment_category_icon_picker.xml
├── fragment_report_transaction_list.xml
├── fragment_category_list.xml
├── fragment_add_edit_category.xml
├── fragment_parent_category_picker.xml
├── fragment_budget_list.xml
├── fragment_budget_finished.xml
├── fragment_budget_detail.xml
├── fragment_budget_wallet_picker.xml
├── fragment_add_edit_budget.xml
├── fragment_wallet_list.xml
├── fragment_wallet_icon_picker.xml
├── fragment_add_edit_wallet.xml
├── fragment_debt_list.xml
├── fragment_add_edit_debt.xml
├── fragment_event_list.xml
├── fragment_add_edit_event.xml
├── fragment_statistics.xml
├── fragment_statistics_detail.xml
├── fragment_statistics_category_detail.xml
├── fragment_statistics_leaf_category_report.xml
├── fragment_income_expense_detail.xml
├── fragment_category_report.xml
├── fragment_profile.xml
├── fragment_settings.xml
├── fragment_passcode.xml
├── fragment_ai_assistant.xml
├── fragment_ai_receipt_scanner.xml
│
├── dialog_category_action.xml
├── dialog_statistics_custom_range.xml
├── sheet_statistics_period_filter.xml
│
├── layout_statistics_compare_summary.xml
├── layout_statistics_group_preview.xml
├── layout_statistics_sticky_header.xml
│
├── item_transaction.xml
├── item_transaction_time_group_card.xml
├── item_transaction_time_group_row.xml
├── item_transaction_category_picker.xml
├── item_transaction_debt_picker.xml
├── item_report_transaction.xml
├── item_report_transaction_day_header.xml
├── item_ledger_section_header.xml
├── item_category.xml
├── item_category_icon.xml
├── item_category_icon_only.xml
├── item_category_add_new.xml
├── item_category_child_row.xml
├── item_category_hierarchy.xml
├── item_category_wallet.xml
├── item_parent_category_picker.xml
├── item_budget.xml
├── item_budget_breakdown.xml
├── item_budget_wallet_picker.xml
├── item_wallet.xml
├── item_wallet_dropdown.xml
├── item_wallet_icon_only.xml
├── item_home_recent_transaction.xml
├── item_home_top_spending.xml
├── item_home_wallet.xml
├── item_statistics_category_row.xml
├── item_statistics_period_summary.xml
└── item_moneymate_dropdown_option.xml
```

---

## Drawables

```
res/drawable/
├── bg_bottom_nav_shell.xml
├── bg_budget_currency_badge.xml
├── bg_budget_empty_icon.xml
├── bg_budget_header_icon_button.xml
├── bg_budget_today_chip.xml
├── bg_budget_wallet_filter.xml
├── bg_budget_wallet_picker_selected.xml
├── bg_budget_wallet_selected_dot.xml
├── bg_circle_icon.xml
├── bg_circle_color_preview.xml
├── bg_home_promo_banner.xml
├── bg_moneymate_dropdown_popup.xml
├── bg_popup_menu_light.xml
├── bg_statistics_callout_icon.xml
├── bg_statistics_compare_fill_blue.xml
├── bg_statistics_compare_fill_red.xml
├── bg_statistics_compare_summary.xml
├── bg_statistics_custom_date_field.xml
├── bg_statistics_dark_panel.xml
├── bg_statistics_dark_panel_inner.xml
├── bg_statistics_dark_sheet.xml
├── bg_statistics_dark_sheet_row.xml
├── bg_statistics_header_icon_button.xml
├── bg_statistics_legend_dot_average.xml
├── bg_statistics_legend_dot_current.xml
├── bg_statistics_period_nav_item.xml
├── bg_statistics_period_nav_item_selected.xml
├── bg_wallet_archived_badge.xml
│
├── ic_category_bill.xml
├── ic_category_bonus.xml
├── ic_category_camera.xml
├── ic_category_default.xml
├── ic_category_education.xml
├── ic_category_elearning.xml
├── ic_category_entertain.xml
├── ic_category_food.xml
├── ic_category_gift.xml
├── ic_category_headphone.xml
├── ic_category_health.xml
├── ic_category_house.xml
├── ic_category_invest.xml
├── ic_category_other.xml
├── ic_category_other_in.xml
├── ic_category_pig.xml
├── ic_category_salary.xml
├── ic_category_sale.xml
├── ic_category_shopping.xml
├── ic_category_spending.xml
├── ic_category_transport.xml
├── ic_category_trash.xml
├── ic_category_travel.xml
├── ic_category_water.xml
│
├── ic_wallet_bag.xml
├── ic_wallet_bank.xml
├── ic_wallet_card.xml
├── ic_wallet_cash.xml
├── ic_wallet_default.xml
├── ic_wallet_ewallet.xml
├── ic_wallet_home.xml
├── ic_wallet_receipt.xml
├── ic_wallet_safe.xml
│
├── ic_check_24.xml
├── ic_launcher_background.xml
├── ic_launcher_foreground.xml
├── ic_nav_budgets_filled.xml
├── ic_nav_home_filled.xml
├── ic_nav_placeholder.xml
├── ic_nav_settings_filled.xml
├── ic_nav_transactions_filled.xml
│
├── outline_account_balance_24.xml
├── outline_account_balance_wallet_24.xml
├── outline_account_tree_24.xml
├── outline_add_24.xml
├── outline_arrow_back_24.xml
├── outline_attach_money_24.xml
├── outline_bar_chart_24.xml
├── outline_calendar_today_24.xml
├── outline_close_24.xml
├── outline_content_copy_24.xml
├── outline_credit_card_24.xml
├── outline_delete_24.xml
├── outline_edit_24.xml
├── outline_history_24.xml
├── outline_home_24.xml
├── outline_more_horiz_24.xml
├── outline_notifications_24.xml
├── outline_payments_24.xml
├── outline_person_24.xml
├── outline_receipt_24.xml
├── outline_remove_24.xml
├── outline_search_24.xml
├── outline_settings_24.xml
├── outline_share_24.xml
├── outline_visibility_24.xml
├── outline_visibility_off_24.xml
└── outline_warning_amber_24.xml
```

---

## Color Resources

```
res/color/
├── budget_tab_text_color.xml
├── selector_bottom_nav_item_color.xml
├── selector_moneymate_field_hint.xml
├── selector_moneymate_field_stroke.xml
├── selector_transaction_field_hint.xml
└── selector_transaction_field_stroke.xml
```

---

## Animation

```
res/anim/
├── slide_in_left.xml
├── slide_in_right.xml
├── slide_in_up.xml
├── slide_out_down.xml
├── slide_out_left.xml
└── slide_out_right.xml
```

---

## Menu

```
res/menu/
├── bottom_nav_menu.xml
├── menu_budget_list.xml
├── menu_budget_toolbar.xml
├── menu_edit_category.xml
└── menu_wallet_item.xml
```

---

## Navigation

```
res/navigation/
├── nav_auth.xml     ← Login → Register → ForgotPassword
└── nav_main.xml     ← Home, Transactions, Budgets, Settings + tất cả sub-screens
```

---

## Values

```
res/values/
├── arrays.xml
├── colors.xml
├── dimens.xml
├── strings.xml
├── styles.xml
└── themes.xml

res/values-night/
└── themes.xml
```

---

## AndroidTest (Instrumentation Tests)

```
androidTest/
├── assets/db/
│   └── v8_pre_metadata.sql
└── java/com/group10/moneymate/
    ├── data/
    │   ├── local/
    │   │   ├── dao/
    │   │   │   ├── CategoryDaoHierarchyTest.java
    │   │   │   ├── SoftDeleteSyncStatusDaoTest.java
    │   │   │   ├── TransactionIndexExplainPlanTest.java
    │   │   │   └── WalletDaoTransactionalSoftDeleteTest.java
    │   │   └── migrations/
    │   │       ├── Migration8To9Test.java
    │   │       ├── Migration9To10Test.java
    │   │       └── Migration11To12Test.java
    │   └── repository/
    │       └── CategoryRepositoryHierarchyValidationTest.java
    ├── ui/
    │   ├── performance/
    │   │   ├── TransactionListPerformanceTest.java
    │   │   └── TransactionPaginationConcurrentInsertTest.java
    │   └── statistics/
    │       ├── IncomeExpenseDetailFragmentState3EspressoTest.java
    │       └── IncomeExpenseDetailViewModelDrillDownTest.java
    └── ExampleInstrumentedTest.java
```

---

## Unit Tests

```
test/java/com/group10/moneymate/
└── ExampleUnitTest.java
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
| 5 | Home Dashboard | ✅ Hoàn thành |
| 6 | Budget | ✅ Hoàn thành |
| 7 | Statistics | ✅ Hoàn thành |
| 8 | Profile & Settings | ✅ Hoàn thành |
| 9 | Passcode | ✅ Hoàn thành |
| 10 | Polish & QA | 🔄 Đang diễn ra |
