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
- [x] `AppDatabase` hiện đã lên **v7** với `fallbackToDestructiveMigration`
- [x] `Converters.java`: Date + 5 Enum converters
- [x] `AppContainer.java`: Manual DI cho 8 Repository
- [x] Tất cả Fragment shell và ViewModel shell
- [x] `nav_main.xml` + `nav_auth.xml` đầy đủ
- [x] `strings.xml` đầy đủ string resources

---

## Phase 1 — Track 3: Authentication (Dev 3) ✅ Cơ bản hoàn thành
**Thời gian:** Ngày 1–3  
**Package:** `ui/auth`, `data/remote/FirebaseAuthHelper`

### Nhiệm vụ:
1. Hoàn thiện `LoginFragment` — đăng nhập Firebase Auth (email/password)
2. Hoàn thiện `RegisterFragment` — đăng ký tài khoản + lưu `UserEntity` vào Room
3. `AuthViewModel` — xử lý LiveData trạng thái Auth
4. `MainActivity` — routing: kiểm tra trạng thái login → điều hướng về `nav_auth` hoặc `nav_main`
5. Xử lý lỗi: email không hợp lệ, sai mật khẩu, không có mạng

### Cập nhật mới:
- Đã có logic seed category mặc định khi vào app
- Đã có logic tự phục hồi local user record để tránh crash sau khi Room recreate

---

## Phase 2 — Track 1: Wallet CRUD (Dev 1) ✅ Hoàn thành
**Thời gian:** Ngày 1–4  
**Package:** `ui/wallet`

### Cập nhật mới:
- Wallet flow đã được chuẩn hóa validate/input
- Budget hiện dùng dữ liệu Wallet cho wallet filter, wallet picker và redirect tạo ví

---

## Phase 3 — Track 1: Category CRUD (Dev 1) ✅ Hoàn thành
**Thời gian:** Ngày 2–5  
**Package:** `ui/category`

### Cập nhật mới:
- Đã thêm icon picker
- Đã có đủ drawable cho default categories
- Có category ảo riêng cho Budget: `Các mục khác`

---

## Phase 4 — Track 1: Transaction CRUD (Dev 1) ✅ Hoàn thành
**Thời gian:** Ngày 3–7  
**Package:** `ui/transaction`

### Cập nhật mới:
- Hỗ trợ ghi chú tiếng Việt
- Format số tiền realtime lúc nhập
- Có thêm query read-only phục vụ Budget:
  - tổng chi theo budget
  - transaction list theo budget
  - query động cho `Các mục khác`

---

## Phase 5 — Track 1: Event Management (Dev 1) 🔄 Có nền tảng
**Thời gian:** Ngày 6–8  
**Package:** `ui/event`

### Ghi chú hiện tại:
- package và navigation đã có
- không phải trọng tâm của sprint hiện tại

---

## Phase 6 — Track 2: Budget Management (Dev 2) ✅ Hoàn thành
**Thời gian:** Ngày 1–5  
**Package:** `ui/budget`

### Nhiệm vụ đã hoàn tất:
1. `BudgetListFragment` + `BudgetAdapter`
2. `AddEditBudgetFragment`
3. `BudgetViewModel`
4. Query read-only từ `TransactionDao`
5. Progress / warning / detail / finished budgets

### Cập nhật lớn đã hoàn thành:
- running budgets + finished budgets
- wallet filter cho cả 2 màn
- wallet picker riêng
- 3 tab `Tháng này / Tương lai / Thời gian khác`
- budget detail + chart + statistics calculator
- `Tất cả danh mục`
- `Các mục khác`
- empty state + auto redirect tạo ví
- xóa budget không xóa transaction

### File / layout đã mở rộng:
- `fragment_budget_list.xml`
- `fragment_budget_finished.xml`
- `fragment_budget_detail.xml`
- `fragment_budget_wallet_picker.xml`
- `item_budget.xml`
- `item_budget_breakdown.xml`
- `item_budget_wallet_picker.xml`

---

## Phase 7 — Track 2: Statistics (Dev 2) ⏳ Mục tiêu tiếp theo
**Thời gian:** Ngày 3–7  
**Package:** `ui/statistics`

### Nhiệm vụ:
1. `StatisticsFragment` — PieChart (chi tiêu theo danh mục) + BarChart (thu/chi theo tháng)
2. Dùng thư viện MPAndroidChart (`com.github.PhilJay:MPAndroidChart:v3.1.0`)
3. Bộ lọc thời gian: tuần / tháng / năm / tùy chọn
4. Dữ liệu: đọc từ `TransactionDao` (READ-ONLY)
5. `StatisticsViewModel` — LiveData aggregation

### Lưu ý hiện tại:
- Đây là phase ưu tiên tiếp theo
- Có thể tái sử dụng nhiều logic từ Budget:
  - time range
  - format tiền
  - empty state
  - aggregate theo wallet/category

---

## Phase 8 — Track 2: Debt Management (Dev 2) 🔄 Có nền tảng
**Thời gian:** Ngày 5–9  
**Package:** `ui/debt`

### Ghi chú hiện tại:
- package và màn hình cơ bản đã có
- chưa phải trọng tâm trước Statistics

---

## Phase 9 — Track 2: Cloud Sync (Dev 2) 🔲 Chưa ưu tiên
**Thời gian:** Ngày 8–11  
**Package:** `data/repository` (sync logic)

### Ghi chú hiện tại:
- kiến trúc offline-first đã có nền
- chưa phải ưu tiên bằng Statistics

---

## Phase 10 — Track 3: Settings & Profile (Dev 3) 🔄 Có nền tảng
**Thời gian:** Ngày 3–6  
**Package:** `ui/settings`, `ui/profile`

### Ghi chú hiện tại:
- Settings đang là nơi điều hướng vào nhiều module chính của app

---

## Phase 11 — Track 3: Security (Passcode + Biometric) (Dev 3) 🔄 Có nền tảng
**Thời gian:** Ngày 5–9  
**Package:** `ui/security`

---

## Phase 12 — Track 3: AI Assistant (Dev 3) 🔄 Có nền tảng
**Thời gian:** Ngày 7–12  
**Package:** `ui/ai`

---

## Sơ đồ Phụ thuộc (Dependency Graph)

```
Phase 0 (Foundation)
    ├── Track 1 (Dev 1): Phase 2 → Phase 3 → Phase 4 → Phase 5
    ├── Track 2 (Dev 2): Phase 6 ✅ → Phase 7
    │                    Phase 8 → Phase 9
    └── Track 3 (Dev 3): Phase 1 → Phase 10 → Phase 11 → Phase 12
```

**Điểm tích hợp hiện tại:**
- Budget đã làm thay đổi thêm các package shared:
  - `data/local/dao`
  - `data/repository`
  - `di`
  - `ui/transaction`
  - `ui/wallet`
  - `ui/category`
  - `res/`

---

## Bảng Timeline

| Giai đoạn hiện tại | Track 1 | Track 2 | Track 3 |
|------|-------|-------|-------|
| Hiện tại | Ổn định Wallet/Category/Transaction/Event | Hoàn tất Budget, chuyển sang Statistics | Duy trì Auth/Settings/Security/AI |

---

## Quy tắc Git Workflow

1. **Main branch**: `main` — chỉ merge khi build pass + review
2. **Feature branches**: `feature/dev1-wallet`, `feature/dev2-budget`, `feature/dev3-auth`
3. **Commit convention**: `feat(wallet): add soft delete`, `fix(budget): fix month filter`
4. **Không commit vào `main` trực tiếp**
5. **`local.properties` và `google-services.json` KHÔNG được commit vào Git**
