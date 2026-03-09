# MoneyMate — Ứng dụng Quản lý Chi tiêu Cá nhân

> Giải pháp quản lý tài chính cá nhân toàn diện: theo dõi thu chi, quản lý nhiều ví, lập ngân sách, ghi nợ mượn, thống kê trực quan và trợ lý AI.

---

## Mục lục

- [Tính năng](#tính-năng)
- [Tech Stack](#tech-stack)
- [Kiến trúc](#kiến-trúc)
- [Cấu trúc dự án](#cấu-trúc-dự-án)
- [Cơ sở dữ liệu](#cơ-sở-dữ-liệu)
- [Yêu cầu hệ thống](#yêu-cầu-hệ-thống)
- [Hướng dẫn cài đặt](#hướng-dẫn-cài-đặt)
- [Trạng thái phát triển](#trạng-thái-phát-triển)
- [Tài liệu](#tài-liệu)

---

## Tính năng

### Xác thực & Bảo mật
- Đăng ký / Đăng nhập bằng Email & Password (Firebase Auth)
- Đăng nhập nhanh bằng **Passcode 6 số** (hoạt động offline)
- Xác thực sinh trắc học (vân tay / Face ID) qua Biometric API
- Ẩn/hiện số dư tổng để bảo mật

### Quản lý Ví
- Tạo nhiều ví: Tiền mặt / Ngân hàng / Ví điện tử
- Theo dõi số dư từng ví riêng biệt
- Chuyển tiền giữa các ví
- Đánh dấu ví loại trừ khỏi tổng số dư

### Giao dịch Thu – Chi
- Thêm / Sửa / Xóa giao dịch (Thu nhập / Chi tiêu / Chuyển khoản)
- Gán danh mục, chọn ví, đính kèm ghi chú và ảnh
- Liên kết giao dịch với khoản nợ hoặc sự kiện
- Quét hoá đơn tự động bằng camera (ML Kit + Gemini AI)
- Tìm kiếm & lọc giao dịch

### Danh mục
- Danh mục hệ thống mặc định sẵn có
- Tạo danh mục tùy chỉnh (Thu / Chi) với màu sắc riêng

### Ngân sách
- Đặt hạn mức chi tiêu theo tháng / theo danh mục
- Ngưỡng cảnh báo linh hoạt (`alert_threshold`)
- Theo dõi % đã sử dụng

### Nợ / Mượn
- Ghi nhận các khoản cho vay (LEND) và đi vay (BORROW)
- Theo dõi số tiền còn lại (`remaining_amount`)
- Trạng thái: Đang nợ (ONGOING) / Đã thanh toán (SETTLED)

### Sự kiện tài chính
- Tạo sự kiện có ngân sách riêng (sinh nhật, du lịch, ...)
- Gắn giao dịch vào sự kiện
- Theo dõi sự kiện đang hoạt động

### Thống kê & Báo cáo
- Biểu đồ tròn (PieChart) chi tiêu theo danh mục
- Biểu đồ cột (BarChart) so sánh thu/chi theo tháng

### Trợ lý AI (Gemini)
- Hỏi đáp phân tích chi tiêu bằng ngôn ngữ tự nhiên
- Quét và nhận diện văn bản từ hoá đơn (CameraX + ML Kit)

### Đồng bộ đám mây
- Lưu cục bộ với **Room Database** (SQLite) — hoạt động offline hoàn toàn
- Sao lưu & đồng bộ lên **Firebase Firestore**
- Soft-delete + `sync_status` để xử lý xung đột offline/online

---

## Tech Stack

| Thành phần | Công nghệ | Phiên bản |
|---|---|---|
| Ngôn ngữ | Java | 11 |
| Min SDK | Android | API 29 (Android 10) |
| Target SDK | Android | API 36 |
| Build System | Gradle (Kotlin DSL) | 9.0.1 |
| Local DB | Room (SQLite) | 2.8.4 |
| Auth & Cloud | Firebase | BOM 34.10.0 |
| Cloud DB | Firebase Firestore | (via BOM) |
| Navigation | Jetpack Navigation + Safe Args | 2.9.7 |
| Lifecycle | ViewModel + LiveData | 2.10.0 |
| Charts | MPAndroidChart | v3.1.0 |
| UI | Material Design 3 | 1.13.0 |
| Biometric | AndroidX Biometric | 1.1.0 |
| Background | WorkManager | 2.10.1 |
| Camera | CameraX (core/camera2/lifecycle/view) | 1.4.2 |
| OCR | ML Kit Text Recognition | 16.0.1 |
| AI | Google Generative AI (Gemini) | 0.9.0 |

---

## Kiến trúc

Dự án sử dụng **MVVM + Repository Pattern + Manual DI**:

```
┌─────────────────────────────────────────────────────┐
│                      UI Layer                       │
│       Fragment / Activity + ViewBinding             │
└──────────────────────┬──────────────────────────────┘
                       │ observe LiveData
┌──────────────────────▼──────────────────────────────┐
│                  ViewModel Layer                    │
│         (survive configuration changes)             │
└──────────────────────┬──────────────────────────────┘
                       │ call methods
┌──────────────────────▼──────────────────────────────┐
│                 Repository Layer                    │
│       (single source of truth, offline-first)       │
└──────────┬───────────────────────────┬──────────────┘
           │                           │
┌──────────▼──────────┐   ┌────────────▼──────────────┐
│   Local (Room DB)   │   │   Remote (Firebase Auth / │
│   DAOs + Entities   │   │         Firestore)        │
└─────────────────────┘   └───────────────────────────┘
```

**Nguyên tắc:**
- UI chỉ quan sát `LiveData`, không chứa business logic
- ViewModel không phụ thuộc vào Android framework
- Repository là nguồn dữ liệu duy nhất (offline-first)
- Manual DI qua `AppContainer` trong `Application` class

---

## Cấu trúc dự án

```
app/src/main/java/com/group10/moneymate/
│
├── MainActivity.java              ← Router: Login hoặc Home
│
├── models/
│   ├── TransactionType.java       (INCOME / EXPENSE / TRANSFER)
│   ├── WalletType.java            (CASH / BANK / E_WALLET)
│   ├── CategoryType.java          (INCOME / EXPENSE)
│   ├── DebtType.java              (LEND / BORROW)
│   ├── DebtStatus.java            (ONGOING / SETTLED)
│   └── SyncStatus.java            (SYNCED / PENDING_UPLOAD / PENDING_DELETE)
│
├── data/
│   ├── local/
│   │   ├── AppDatabase.java       ← Room singleton (version 3)
│   │   ├── Converters.java        ← TypeConverters: Enum↔String, Date↔Long
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
│   │       ├── CategoryDao.java
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
│       ├── CategoryRepository.java
│       ├── TransactionRepository.java
│       ├── BudgetRepository.java
│       ├── DebtRepository.java
│       └── EventRepository.java
│
├── di/
│   ├── MoneyMateApplication.java  ← Application class
│   └── AppContainer.java          ← Manual DI (8 repositories)
│
├── utils/
│   ├── Constants.java
│   ├── PrefsManager.java
│   ├── CurrencyFormatter.java
│   └── DateUtils.java
│
└── ui/
    ├── auth/       LoginActivity, Login/RegisterFragment, AuthViewModel
    ├── main/       HomeActivity (BottomNavigationView host)
    ├── home/       HomeFragment, HomeViewModel
    ├── wallet/     WalletListFragment, AddEditWalletFragment, WalletAdapter, WalletViewModel
    ├── category/   AddEditCategoryFragment
    ├── transaction/ (scaffolded)
    ├── budget/     BudgetListFragment, AddEditBudgetFragment, BudgetAdapter, BudgetViewModel
    ├── debt/       DebtListFragment, AddEditDebtFragment, DebtViewModel
    ├── event/      EventListFragment, AddEditEventFragment, EventViewModel
    ├── statistics/ StatisticsFragment
    ├── profile/    ProfileFragment
    ├── settings/   SettingsFragment
    ├── ai/         AIAssistantFragment, AIReceiptScannerFragment, AIViewModel
    └── security/   PasscodeFragment, SecurityViewModel
```

---

## Cơ sở dữ liệu

Room Database version **3** với **7 bảng**, áp dụng offline-first:

| Bảng | Mô tả | FK |
|---|---|---|
| `users` | Tài khoản người dùng (Firebase UID) | — |
| `wallets` | Ví tiền | → users |
| `categories` | Danh mục thu/chi (system + custom) | → users (nullable) |
| `transactions` | Giao dịch thu/chi/chuyển khoản | → wallets, categories, debts, events |
| `budgets` | Ngân sách theo tháng/danh mục | → users, categories (nullable) |
| `debts` | Khoản nợ/mượn | → users |
| `events` | Sự kiện tài chính | → users |

Tất cả bảng có `sync_status (Int)` và `is_deleted (Boolean)` để hỗ trợ đồng bộ offline-first.

---

## Yêu cầu hệ thống

- **Android Studio** Ladybug (2024.2.1) trở lên
- **JDK** 11 trở lên
- **Android SDK** API 29+
- Tài khoản **Firebase** (để cấu hình Auth & Firestore)
- File `local.properties` với `sdk.dir` và `GEMINI_API_KEY`

---

## Hướng dẫn cài đặt

### 1. Clone dự án

```bash
git clone https://github.com/Yan25-06/MoneyMate.git
cd MoneyMate
```

### 2. Cấu hình `local.properties`

Tạo file `local.properties` ở thư mục gốc (nếu chưa có):

```properties
sdk.dir=C\:\\Users\\<your-username>\\AppData\\Local\\Android\\Sdk
GEMINI_API_KEY=your_gemini_api_key_here
```

### 3. Cấu hình Firebase

1. Truy cập [Firebase Console](https://console.firebase.google.com/)
2. Tạo project → Thêm ứng dụng Android
3. Package name: `com.group10.moneymate`
4. Tải file `google-services.json` → đặt vào thư mục `app/`
5. Bật **Authentication** (Email/Password) và **Firestore Database**

### 4. Build & Run

```bash
# Windows
.\gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

Hoặc mở trong **Android Studio** → **Run** (Shift + F10)

---

## Trạng thái phát triển

### Phase 0 — Foundation & Scaffolding ✅ Hoàn thành

| Mục | Trạng thái |
|---|---|
| Cấu hình build.gradle.kts (tất cả dependencies) | ✅ |
| AndroidManifest (CAMERA, BIOMETRIC, NETWORK permissions) | ✅ |
| Room DB v3: 7 entities, 7 DAOs, Converters | ✅ |
| FK + Index đầy đủ trên tất cả entities | ✅ |
| Nullability (@Nullable) chính xác theo spec | ✅ |
| 8 Repositories + AppContainer (Manual DI) | ✅ |
| UI Scaffolding: 14 packages, tất cả Fragment/ViewModel shells | ✅ |
| Navigation graph (nav_main.xml) với 7 fragments mới | ✅ |
| BUILD SUCCESSFUL | ✅ |

### Phase 1-3 — Feature Development

| Phase | Nội dung | Trạng thái |
|---|---|---|
| 1 | Authentication (Đăng ký / Đăng nhập / Passcode) | 🔲 Chưa bắt đầu |
| 2 | Wallet + Category + Transaction + Event (Dev 1) | 🔲 Chưa bắt đầu |
| 3 | Budget + Debt + Statistics + Cloud Sync (Dev 2) | 🔲 Chưa bắt đầu |
| 4 | Settings + Security + AI Assistant (Dev 3) | 🔲 Chưa bắt đầu |
| 5 | Integration, QA & Polish | 🔲 Chưa bắt đầu |

> Chi tiết lịch 14 ngày cho 3 developer: [`docs/implementation-phases.md`](docs/implementation-phases.md)

---

## Tài liệu

| File | Mô tả |
|---|---|
| [`docs/project-structure.md`](docs/project-structure.md) | Cấu trúc file & kiến trúc chi tiết |
| [`docs/implementation-phases.md`](docs/implementation-phases.md) | Kế hoạch Parallel Tracks Agile cho 3 developers / 14 ngày |

---