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
- Gán danh mục, chọn ví, đính kèm ghi chú
- Tìm kiếm & lọc giao dịch theo loại
- Số dư ví **tự động cập nhật** khi thêm / sửa / xóa giao dịch
- Liên kết giao dịch với khoản nợ hoặc sự kiện
- Quét hoá đơn tự động bằng camera (ML Kit + Gemini AI)

### Danh mục
- Danh mục hệ thống mặc định sẵn có (16 danh mục: 10 Chi + 6 Thu)
- Tạo danh mục tùy chỉnh (Thu / Chi) với màu sắc riêng
- Không thể xóa danh mục mặc định

### Ngân sách
- Đặt hạn mức chi tiêu theo tháng / theo danh mục
- Ngưỡng cảnh báo linh hoạt (`alert_threshold`)
- Theo dõi % đã sử dụng

### Nợ / Mượn
- Ghi nhận các khoản cho vay (LEND) và đi vay (BORROW)
- Theo dõi số tiền còn lại (`remaining_amount`)
- Trạng thái: Đang nợ (ACTIVE) / Đã thanh toán (SETTLED)

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

[project-structure.md](docs/project-structure.md)

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

```properties
sdk.dir=C\:\\Users\\<your-username>\\AppData\\Local\\Android\\Sdk
GEMINI_API_KEY=your_gemini_api_key_here
```

### 3. Cấu hình Firebase

1. Truy cập [Firebase Console](https://console.firebase.google.com/)
2. Tạo project → Thêm ứng dụng Android
3. Package name: `com.group10.moneymate`
4. Tải file `google-services.json` → đặt vào thư mục `app/`
5. Bật **Authentication** (Email/Password, Anonymous) và **Firestore Database**

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
| 8 Repositories + AppContainer (Manual DI) | ✅ |
| UI Scaffolding: tất cả Fragment/ViewModel shells | ✅ |
| Navigation graph (nav_main.xml, nav_auth.xml) | ✅ |
| BUILD SUCCESSFUL | ✅ |

### Phase 1 — Authentication ✅ Hoàn thành

| Mục | Trạng thái |
|---|---|
| `FirebaseAuthHelper` — signUp, signIn, signInAnonymously, resetPassword, updateDisplayName | ✅ |
| `AuthRepository` — login, register, loginAnonymously, sendPasswordReset, saveUid, setLoggedIn | ✅ |
| `UserRepository` — getUser, insertUser, updateUser, deleteUser (databaseWriteExecutor) | ✅ |
| `AuthViewModel` — AndroidViewModel, AuthState enum, login/register/anonymous/resetPassword | ✅ |
| `MainActivity` — router Firebase auth state → HomeActivity / LoginActivity | ✅ |
| `LoginActivity` — host nav_auth, redirect nếu đã login | ✅ |
| `LoginFragment` — ViewBinding, loading state, seed categories sau auth | ✅ |
| `RegisterFragment` — ViewBinding, client validation, loading state, seed categories sau auth | ✅ |
| `ForgotPasswordFragment` — ViewBinding, email validation, success/error state | ✅ |
| `nav_auth.xml` — Login → Register → ForgotPassword với slide animations | ✅ |
| `fragment_login.xml` — Material3 style, til_email, til_password | ✅ |
| `fragment_register.xml` — Material3 style, đầy đủ 4 input fields | ✅ |

### Phase 3 — Category ✅ Hoàn thành

| Mục | Trạng thái |
|---|---|
| `Constants.java` — DefaultCategory, getDefaultCategories() (16 mục) | ✅ |
| `CategoryRepository` — seedDefaults(), soft delete, databaseWriteExecutor | ✅ |
| `AppContainer` — seedDefaultCategoriesIfNeeded(), getAppContainer() | ✅ |
| `MoneyMateApplication` — getAppContainer() | ✅ |
| `PrefsManager` — getUid(), saveUid(), isLoggedIn(), setLoggedIn() | ✅ |
| `CategoryDao` — countDefaultCategoriesByUid() | ✅ |
| `CategoryViewModel` — AndroidViewModel, switchMap filter by type | ✅ |
| `CategoryAdapter` — ListAdapter + DiffUtil, click/delete listeners | ✅ |
| `CategoryListFragment` — TabLayout, RecyclerView, FAB, Safe Args | ✅ |
| `AddEditCategoryFragment` — Add/Edit mode, color picker, validation | ✅ |
| `fragment_category_list.xml` — CoordinatorLayout + TabLayout + FAB | ✅ |
| `fragment_add_edit_category.xml` — form đầy đủ | ✅ |
| `item_category.xml` — icon, tên, badge mặc định, nút xóa | ✅ |
| `bg_circle_icon.xml`, `bg_circle_color_preview.xml` | ✅ |
| `SettingsFragment` — navigation tới tất cả destinations | ✅ |
| `fragment_settings.xml` — đầy đủ sections và navigation buttons | ✅ |

### Phase 4 — Transaction CRUD ✅ Hoàn thành

| Mục | Trạng thái |
|---|---|
| `TransactionRepository` — databaseWriteExecutor, soft delete, applyBalanceChange() tự động cập nhật số dư ví | ✅ |
| `AppContainer` — cập nhật TransactionRepository nhận thêm WalletDao | ✅ |
| `TransactionViewModel` — AndroidViewModel, filter by type (switchMap), search, CRUD | ✅ |
| `TransactionAdapter` — ListAdapter + DiffUtil, màu amount (xanh/đỏ/xanh dương), static ViewHolder | ✅ |
| `TransactionListFragment` — RecyclerView, FAB, empty state, long-click confirm delete | ✅ |
| `AddEditTransactionFragment` — Add/Edit mode, category chip picker, wallet dropdown, DatePicker, validation | ✅ |
| `fragment_transaction_list.xml` — empty state TextView, paddingTop tránh che status bar | ✅ |
| `fragment_add_edit_transaction.xml` — type toggle, chip group, wallet dropdown, date field | ✅ |
| `item_transaction.xml` — MaterialCardView, amount + màu, date, type badge | ✅ |
| `nav_main.xml` — thêm argument `transactionId` (nullable) cho addEditTransactionFragment | ✅ |

### Các Phase còn lại

| Phase | Nội dung | Trạng thái |
|---|---|---|
| 5 | Home Dashboard | 🔲 Chưa bắt đầu |
| 6 | Budget | 🔲 Chưa bắt đầu |
| 7 | Statistics | 🔲 Chưa bắt đầu |
| 8 | Profile & Settings | 🔲 Chưa bắt đầu |
| 9 | Passcode | 🔲 Chưa bắt đầu |
| 10 | Polish & QA | 🔲 Chưa bắt đầu |

---

## Tài liệu

| File | Mô tả |
|---|---|
| [project-structure.md](docs/project-structure.md) | Cấu trúc file & kiến trúc chi tiết |
| [phases.md](docs/phases.md) | Kế hoạch triển khai 10 phases |
| [copilot-instructions.md](.github/copilot-instructions.md) | Coding rules cho AI code generation |

---