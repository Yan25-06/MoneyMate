# MoneyMate – Kế hoạch Triển khai Song song (Parallel Tracks)

> **Mô hình:** Agile Parallel Tracks — 3 Developer, 14 ngày  
> **Nguyên tắc Không Conflict:** Mỗi Dev sở hữu riêng một tập package. Không ai được sửa file của Dev khác trừ khi qua PR đã được review.

---

## Phân chia Chức năng Theo Track

| Track | Developer | Chức năng phụ trách | Package sở hữu |
|-------|-----------|---------------------|----------------|
| Track 1 | **Dev 1** | Wallet · Category · Transaction · Event | `ui/wallet`, `ui/category`, `ui/transaction`, `ui/event` |
| Track 2 | **Dev 2** | Budget · Statistics · Debt · Cloud Sync | `ui/budget`, `ui/statistics`, `ui/debt` |
| Track 3 | **Dev 3** | Auth · Settings · Security · AI Assistant | `ui/auth`, `ui/settings`, `ui/security`, `ui/ai` |

> **Quy tắc bất biến cho Dev 2:** Chỉ được đọc (read-only query) trên bảng `transactions`. Không được INSERT/UPDATE/DELETE transaction trực tiếp. Mọi thay đổi transaction phải thông qua `TransactionRepository` của Dev 1.

---

## Phase 0 — Foundation & Scaffolding ✅ (Đã hoàn thành)
**Thực hiện bởi:** Tech Lead / Senior Dev  
**Output:**
- [x] `app/build.gradle.kts`: Đầy đủ dependencies (Room, Firebase, Navigation Safe Args, CameraX, ML Kit, Gemini AI, Biometric, WorkManager)
- [x] `AndroidManifest.xml`: INTERNET, NETWORK_STATE, CAMERA, USE_BIOMETRIC, uses-feature camera
- [x] `BuildConfig.GEMINI_API_KEY` lấy từ `local.properties` (không hard-code)
- [x] 7 Room Entity: User, Wallet, Category, Transaction, Budget, Debt, Event
- [x] 7 Room DAO với soft-delete pattern (`is_deleted`, `sync_status`)
- [x] `AppDatabase` v2 với `fallbackToDestructiveMigration`
- [x] `Converters.java`: Date + 5 Enum converters
- [x] `AppContainer.java`: Manual DI cho 8 Repository
- [x] Tất cả Fragment shell và ViewModel shell
- [x] `nav_main.xml` + `nav_auth.xml` đầy đủ
- [x] `strings.xml` đầy đủ string resources

---

## Phase 1 — Track 3: Authentication (Dev 3)
**Thời gian:** Ngày 1–3  
**Package:** `ui/auth`, `data/remote/FirebaseAuthHelper`

### Nhiệm vụ:
1. Hoàn thiện `LoginFragment` — đăng nhập Firebase Auth (email/password)
2. Hoàn thiện `RegisterFragment` — đăng ký tài khoản + lưu `UserEntity` vào Room
3. `AuthViewModel` — xử lý LiveData trạng thái Auth
4. `MainActivity` — routing: kiểm tra trạng thái login → điều hướng về `nav_auth` hoặc `nav_main`
5. Xử lý lỗi: email không hợp lệ, sai mật khẩu, không có mạng

### Layout cần hoàn thiện:
- `fragment_login.xml`, `fragment_register.xml`

---

## Phase 2 — Track 1: Wallet CRUD (Dev 1)
**Thời gian:** Ngày 1–4  
**Package:** `ui/wallet`

### Nhiệm vụ:
1. `WalletListFragment` + `WalletAdapter` — hiển thị danh sách ví (RecyclerView)
2. `AddEditWalletFragment` — form thêm/sửa ví (tên, số dư ban đầu, loại, màu)
3. `WalletViewModel` — LiveData list, thêm/sửa/xóa mềm
4. Khi xóa: soft-delete (`is_deleted = 1`, `sync_status = PENDING_DELETE`)
5. Cập nhật số dư ví khi Transaction được ghi (sẽ kết hợp với Phase 4)

### Layout cần hoàn thiện:
- `fragment_wallet_list.xml`, `fragment_add_edit_wallet.xml`, `item_wallet.xml`

---

## Phase 3 — Track 1: Category CRUD (Dev 1)
**Thời gian:** Ngày 2–5  
**Package:** `ui/category`

### Nhiệm vụ:
1. `CategoryListFragment` + `CategoryAdapter` — hiển thị danh mục (tab INCOME/EXPENSE)
2. `AddEditCategoryFragment` — form thêm/sửa danh mục (tên, icon, màu, loại)
3. `CategoryViewModel` — LiveData list, CRUD
4. Seed dữ liệu mặc định: Gọi một lần khi `is_default` count = 0 (trong Application hoặc DAO)
5. Không cho phép xóa danh mục mặc định

### Layout cần hoàn thiện:
- `fragment_category_list.xml`, `fragment_add_edit_category.xml`, `item_category.xml`

---

## Phase 4 — Track 1: Transaction CRUD (Dev 1)
**Thời gian:** Ngày 3–7  
**Package:** `ui/transaction`

### Nhiệm vụ:
1. `TransactionListFragment` + `TransactionAdapter` — danh sách giao dịch, filter theo ví/danh mục/ngày
2. `AddEditTransactionFragment` — form thêm/sửa (số tiền, loại, danh mục, ví, ghi chú, ảnh)
3. Hỗ trợ loại TRANSFER: chọn `to_wallet_id`, trừ ví nguồn, cộng ví đích
4. Sau khi lưu Transaction: cập nhật `WalletEntity.balance` tương ứng
5. `TransactionViewModel` — LiveData danh sách, recent 5 giao dịch cho Home

### Layout cần hoàn thiện:
- `fragment_transaction_list.xml`, `fragment_add_edit_transaction.xml`, `item_transaction.xml`

---

## Phase 5 — Track 1: Event Management (Dev 1)
**Thời gian:** Ngày 6–8  
**Package:** `ui/event`

### Nhiệm vụ:
1. `EventListFragment` + Adapter — danh sách sự kiện (active/all)
2. `AddEditEventFragment` — form thêm/sửa sự kiện (tên, ngân sách, ngày bắt đầu/kết thúc)
3. `EventViewModel`
4. Khi tạo Transaction, cho phép gắn `event_id` (dropdown picker)

### Layout cần hoàn thiện:
- `fragment_event_list.xml`, `fragment_add_edit_event.xml`

---

## Phase 6 — Track 2: Budget Management (Dev 2)
**Thời gian:** Ngày 1–5  
**Package:** `ui/budget`

### Nhiệm vụ:
1. `BudgetListFragment` + `BudgetAdapter` — hiển thị ngân sách tháng và progress bar
2. `AddEditBudgetFragment` — form chọn danh mục, hạn mức, ngưỡng cảnh báo (%), tháng/năm
3. `BudgetViewModel` — tính `spentAmount` bằng cách query READ-ONLY từ `TransactionDao`:
   ```java
   transactionDao.getTotalExpenseByCategorySync(userId, categoryId, startDate, endDate)
   ```
4. Hiển thị cảnh báo khi `spentAmount >= limitAmount * alertThreshold`
5. Không được ghi vào bảng `transactions`

### Layout cần hoàn thiện:
- `fragment_budget_list.xml`, `fragment_add_edit_budget.xml`, `item_budget.xml`

---

## Phase 7 — Track 2: Statistics (Dev 2)
**Thời gian:** Ngày 3–7  
**Package:** `ui/statistics`

### Nhiệm vụ:
1. `StatisticsFragment` — PieChart (chi tiêu theo danh mục) + BarChart (thu/chi theo tháng)
2. Dùng thư viện MPAndroidChart (`com.github.PhilJay:MPAndroidChart:v3.1.0`)
3. Bộ lọc thời gian: tuần / tháng / năm / tùy chọn
4. Dữ liệu: đọc từ `TransactionDao` (READ-ONLY)
5. `StatisticsViewModel` — LiveData aggregation

### Layout cần hoàn thiện:
- `fragment_statistics.xml`

---

## Phase 8 — Track 2: Debt Management (Dev 2)
**Thời gian:** Ngày 5–9  
**Package:** `ui/debt`

### Nhiệm vụ:
1. `DebtListFragment` + Adapter — tab LEND / BORROW, hiển thị trạng thái và số tiền còn lại
2. `AddEditDebtFragment` — form (tên người, loại, số tiền, hạn trả, ghi chú)
3. `DebtViewModel` — tổng cho vay / đi vay chưa thanh toán
4. Thanh toán một phần: cập nhật `remaining_amount`, đổi `status = SETTLED` khi `remaining_amount = 0`
5. Link transaction với debt: khi tạo giao dịch trả nợ, gắn `debt_id` (phối hợp với Dev 1)

### Layout cần hoàn thiện:
- `fragment_debt_list.xml`, `fragment_add_edit_debt.xml`

---

## Phase 9 — Track 2: Cloud Sync (Dev 2)
**Thời gian:** Ngày 8–11  
**Package:** `data/repository` (sync logic)

### Nhiệm vụ:
1. `SyncWorker extends Worker` — WorkManager Worker để đồng bộ ngầm
2. Logic: Lấy các record có `sync_status != SYNCED` → upload/delete lên Firestore → cập nhật `sync_status = SYNCED`
3. Lên lịch đồng bộ tự động (PeriodicWorkRequest, interval 15 phút)
4. `CloudSyncRepository` — xử lý conflict resolution (server-wins hoặc client-wins theo `updated_at`)
5. Hiển thị trạng thái sync trong Settings (last sync time)

---

## Phase 10 — Track 3: Settings & Profile (Dev 3)
**Thời gian:** Ngày 3–6  
**Package:** `ui/settings`, `ui/profile`

### Nhiệm vụ:
1. `ProfileFragment` — hiển thị/sửa tên, email, avatar, đơn vị tiền tệ, ngôn ngữ
2. `SettingsFragment` — dark mode toggle, date format, hide balance toggle, đổi passcode, đăng xuất
3. `SettingsViewModel`, `ProfileViewModel`
4. Lưu cài đặt vào `UserEntity` và `PrefsManager`
5. Dark mode: apply `AppCompatDelegate.setDefaultNightMode()`

### Layout cần hoàn thiện:
- `fragment_settings.xml`, `fragment_profile.xml`

---

## Phase 11 — Track 3: Security (Passcode + Biometric) (Dev 3)
**Thời gian:** Ngày 5–9  
**Package:** `ui/security`

### Nhiệm vụ:
1. `PasscodeFragment` — màn hình nhập PIN 4-6 chữ số (keypad custom)
2. Lưu passcode: hash SHA-256, lưu vào `UserEntity.hashedPasscode`
3. Xác minh passcode khi mở app (nếu đã kích hoạt)
4. `BiometricHelper` — wrap `BiometricPrompt`, hỗ trợ fingerprint/face
5. `SecurityViewModel` — quản lý trạng thái xác thực
6. Tùy chọn trong Settings: bật/tắt passcode, bật/tắt biometric

### Layout cần hoàn thiện:
- `fragment_passcode.xml`

---

## Phase 12 — Track 3: AI Assistant (Dev 3)
**Thời gian:** Ngày 7–12  
**Package:** `ui/ai`

### Nhiệm vụ:
1. **AIAssistantFragment** — Chatbot với Gemini API:
   - Giao diện chat (RecyclerView + input EditText)
   - Gọi `GenerativeModel` từ `com.google.ai.client.generativeai`
   - API Key lấy từ `BuildConfig.GEMINI_API_KEY`
   - Streaming responses với coroutines/executor
2. **AIReceiptScannerFragment** — Quét hóa đơn:
   - CameraX Preview + ImageCapture
   - ML Kit Text Recognition (`TextRecognizer`)
   - Parse kết quả → tự điền form AddEditTransaction
3. `AIViewModel` — quản lý chat history, kết quả OCR

### Bảo mật:
- `BuildConfig.GEMINI_API_KEY` lấy từ `local.properties`
- Thêm `GEMINI_API_KEY=your_key_here` vào `local.properties` (KHÔNG commit vào Git)
- Thêm `local.properties` vào `.gitignore`

### Layout cần hoàn thiện:
- `fragment_ai_assistant.xml`, `fragment_ai_receipt_scanner.xml`

---

## Sơ đồ Phụ thuộc (Dependency Graph)

```
Phase 0 (Foundation)
    ├── Track 1 (Dev 1): Phase 2 → Phase 3 → Phase 4 → Phase 5
    ├── Track 2 (Dev 2): Phase 6 ──(đọc Transaction)──→ Phase 7
    │                    Phase 8 ──(link debt_id)──────→ Phase 9
    └── Track 3 (Dev 3): Phase 1 → Phase 10 → Phase 11 → Phase 12
```

**Điểm tích hợp:**
- Dev 2 / Phase 6 cần `TransactionDao.getTotalExpenseByCategorySync` → Dev 1 hoàn thành Phase 4 trước
- Dev 2 / Phase 8 cần `debt_id` trong AddEditTransaction → Dev 1 hoàn thành Phase 4 trước
- Dev 3 / Phase 12 (Scanner) cần tích hợp với `AddEditTransactionFragment` của Dev 1 → phối hợp cuối Sprint

---

## Bảng Timeline

| Ngày | Dev 1 | Dev 2 | Dev 3 |
|------|-------|-------|-------|
| 1–2 | Wallet CRUD | Budget form | Auth (Login/Register) |
| 3–4 | Category CRUD | Budget logic + progress | Settings + Profile |
| 5–6 | Transaction (INCOME/EXPENSE) | Statistics (Charts) | Passcode setup |
| 7–8 | Transaction (TRANSFER + ảnh) | Debt CRUD | Biometric |
| 9–10 | Event CRUD | Cloud Sync Worker | AI Chat (Gemini) |
| 11–12 | Integration & polish | Sync testing | AI Receipt Scanner |
| 13–14 | Code review / Bug fix | Code review / Bug fix | Code review / Bug fix |

---

## Quy tắc Git Workflow

1. **Main branch**: `main` — chỉ merge khi build pass + review
2. **Feature branches**: `feature/dev1-wallet`, `feature/dev2-budget`, `feature/dev3-auth`
3. **Commit convention**: `feat(wallet): add soft delete`, `fix(budget): fix month filter`
4. **Không commit vào `main` trực tiếp**
5. **`local.properties` và `google-services.json` KHÔNG được commit vào Git**