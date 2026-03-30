# MoneyMate Project Status And Next Steps

## 1. Context

MoneyMate is an Android personal finance app built with:

- Java
- MVVM + Repository
- Room as the offline-first local source of truth
- Manual DI through `AppContainer`
- ViewBinding

The current codebase follows the revamp direction described in:

- `AGENT.md`
- `.codex/planning/CORE_ARCHITECTURE_REVAMP_V1_1.md`
- `.codex/planning/PROJECT.md`
- `.codex/planning/STATE.md`

Core rules already applied in the project:

- non-destructive migration mindset
- soft-delete instead of hard delete for important entities
- wallet lifecycle support: active / archived / deleted
- category hierarchy with parent-child structure
- statistics drill-down based on category hierarchy
- UI state driven from Room `LiveData`

## 2. Current Project State

At the current branch state, the project has already gone through a large revamp across data, UI, and navigation layers.

### 2.1. Data And Domain State

- Room database has been extended to support the revamp baseline.
- Category hierarchy logic is now first-class in DAO / Repository / UI.
- Soft-delete flows have been implemented for Category and Wallet.
- Wallet archive / restore has been implemented.
- Dynamic wallet balance has been introduced through query-based calculation instead of mutating a stored runtime balance.
- Statistics queries have been heavily refactored to support overview, expense/income detail, group report, and single-category report behavior.
- Budget queries and wallet filtering have been aligned with wallet archive/delete states.

### 2.2. UI / UX State

- Home screen has been redesigned and expanded.
- Transaction list has been reworked into grouped card-style sections by time bucket.
- Statistics module now supports a deeper report flow:
  - overview
  - expense/income detail
  - group report
  - single-category report
- Wallet management now distinguishes archived wallets visually and behaviorally.
- Category management has been expanded with hierarchy picking and improved edit/delete behavior.
- Custom date picker, dialog theming, wallet/category icon handling, and several screen-specific headers have been improved.

### 2.3. Quality / Validation State

- Multiple DAO / repository / ViewModel / Espresso tests were added on this branch.
- The app currently builds successfully with:
  - `:app:compileDebugJavaWithJavac`
  - `:app:assembleDebug`

## 3. Major Functional Areas Already Advanced On This Branch

### 3.1. Statistics

- implemented drill-down report flow
- separated logic for overview, grouped report, and single-category report
- fixed trend navigation and category-based routing
- restored richer UI elements while keeping new behavior
- improved SQL aggregation for parent-child category scenarios

### 3.2. Wallet

- soft-delete wallet without deleting historical transactions
- archive / restore wallet flow
- wallet picker behavior aligned with lifecycle states
- wallet list and wallet-related UI updated with archive indicators
- dynamic wallet balance calculation now reacts to transaction changes

### 3.3. Category

- soft-delete category flow with cascade to direct children
- create/edit selection flow excludes deleted categories where appropriate
- history/statistics still keep deleted category names/icons for old transactions

### 3.4. Transaction

- create/edit flow aligned with archived/deleted wallet and category rules
- transaction history and report screens improved
- grouped transaction list UI added
- statistics-to-transaction-list drill-down refined

### 3.5. Budget

- budget list/detail/add-edit flows expanded
- archive-aware wallet behavior added
- budgets tied to archived wallets remain visible in history
- active wallet filtering is used for creating new budgets

## 4. Important Architectural Notes For Future Work

- `WalletEntity.balance` is currently treated as the wallet's base / initial amount, while current balance is computed dynamically from transactions.
- Wallet archive means:
  - no new transaction/budget selection for archived wallet
  - historical data remains visible
  - archived wallet money still contributes where required unless explicitly excluded by feature rules
- Wallet delete means:
  - wallet becomes hidden from normal global views
  - historical transaction rows stay in database
- Category delete means:
  - category is soft-deleted
  - old transaction history and statistics can still reference that category

## 5. Known Remaining Product Areas

The following functional areas still need dedicated implementation or completion work:

### 5.1. Event Feature

Needed work:

- event entity flow review
- add/edit event screen completion
- event-based transaction grouping/reporting
- event detail and event lifecycle UX
- archive/delete rules for events
- integration between event and statistics/budget/history

### 5.2. Debt Feature

Needed work:

- debt domain flow audit
- borrow/lend transaction integration validation
- debt detail screen refinement
- repayment flow
- overdue / closed state logic
- debt-related reporting and reminders

### 5.3. Display Settings And Profile

Needed work:

- profile screen completion
- editable user profile data flow
- display settings persistence
- currency / locale / number format preferences
- hide/show balance preferences across the app
- theme / appearance consistency audit

### 5.4. Passcode Feature

Needed work:

- passcode creation flow
- passcode confirmation / change / reset flow
- app lock lifecycle handling
- foreground/background unlock behavior
- secure preference storage
- UX for wrong-attempt handling

### 5.5. AI For Voice / Image Transaction Creation

Needed work:

- define AI architecture and provider integration
- voice-to-transaction parsing flow
- image / receipt capture to transaction parsing
- confidence review UI before save
- fallback/manual correction UX
- cost, privacy, and offline behavior rules

### 5.6. Sync Feature

Needed work:

- full sync contract audit against current Room schema
- upload/download conflict resolution
- deleted / archived entity sync behavior
- retry and backoff strategy
- offline queue observability
- recovery and reconciliation tools for stale local state

## 6. Recommended Next Phase Order

Suggested order for future delivery:

1. Sync hardening
2. Debt completion
3. Event completion
4. Profile and display settings
5. Passcode
6. AI-assisted transaction creation

Reason:

- sync correctness protects all current modules
- debt and event are domain features that depend on stable data rules
- settings and security should land on top of a stable data model
- AI input should be added after the transaction pipeline is stable and trustworthy

## 7. Suggested Handoff Notes

Before continuing future work, the next developer or agent should:

1. Read `AGENT.md`.
2. Read `.codex/planning/CORE_ARCHITECTURE_REVAMP_V1_1.md`.
3. Review wallet lifecycle rules:
   - active
   - archived
   - deleted
4. Review category hierarchy and statistics drill-down behavior.
5. Re-run `:app:assembleDebug` before starting a new feature branch.
