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
- Ẩn/hiện số dư tổng để bảo mật

### Quản lý Ví
- Tạo nhiều ví: Tiền mặt / Ngân hàng / Ví điện tử
- Theo dõi số dư từng ví với mô hình số dư động (base balance + biến động giao dịch)
- Chuyển tiền giữa các ví
- Đánh dấu ví loại trừ khỏi tổng số dư
- Vòng đời ví đầy đủ: **Active / Archived / Deleted (soft-delete)**
- Khôi phục ví đã archive, giữ nguyên lịch sử giao dịch khi archive/delete

### Giao dịch Thu – Chi
- Thêm / Sửa / Xóa giao dịch (Thu nhập / Chi tiêu / Chuyển khoản)
- Gán danh mục, chọn ví, đính kèm ghi chú
- Tìm kiếm & lọc giao dịch theo loại
- Số dư ví **tự động cập nhật** khi thêm / sửa / xóa giao dịch
- Liên kết giao dịch với khoản nợ hoặc sự kiện
- Quét hoá đơn tự động bằng camera (ML Kit + Gemini AI)
- Danh sách giao dịch nhóm theo mốc thời gian (card-style sections)
- Hỗ trợ drill-down từ màn Budget/Statistics sang danh sách giao dịch theo bộ lọc

### Danh mục
- Danh mục hệ thống mặc định sẵn có (16 danh mục: 10 Chi + 6 Thu)
- Tạo danh mục tùy chỉnh (Thu / Chi) với màu sắc riêng
- Không thể xóa danh mục mặc định
- Hỗ trợ icon picker và dữ liệu danh mục ảo phục vụ budget
- Hỗ trợ **cấu trúc cha-con (category hierarchy)**
- Soft-delete danh mục (kể cả cascade cho danh mục con trực tiếp)
- Giữ tên/icon danh mục đã xóa cho lịch sử giao dịch và thống kê

### Ngân sách
- Đặt hạn mức chi tiêu theo khoảng ngày / danh mục / ví
- Hỗ trợ `Tất cả các ví`, `Tất cả danh mục`, và `Các mục khác`
- Màn danh sách có 3 tab: `Tháng này`, `Tương lai`, `Thời gian khác`
- Có màn `Ngân sách đã kết thúc`
- Có màn chi tiết ngân sách với biểu đồ timeline, thống kê theo ngày và danh sách giao dịch đúng phạm vi

### Nợ / Mượn
- Ghi nhận các khoản cho vay (LEND) và đi vay (BORROW)
- Theo dõi số tiền còn lại (`remaining_amount`)
- Trạng thái: Đang nợ (ACTIVE) / Đã thanh toán (SETTLED)

### Sự kiện tài chính
- Tạo sự kiện có ngân sách riêng (sinh nhật, du lịch, ...)
- Gắn giao dịch vào sự kiện
- Theo dõi sự kiện đang hoạt động

### Thống kê & Báo cáo
- Dashboard tổng quan với lọc ví + khoảng thời gian
- Drill-down đầy đủ: Overview → Income/Expense detail → Group report → Single-category report
- Điều hướng nhanh tới danh sách giao dịch theo report/filter đã chọn
- Tối ưu truy vấn thống kê cho dữ liệu danh mục phân cấp

### Trợ lý AI (Gemini)
- Hỏi đáp phân tích chi tiêu bằng ngôn ngữ tự nhiên
- Quét và nhận diện văn bản từ hoá đơn (CameraX + ML Kit)

### Đồng bộ đám mây
- Lưu cục bộ với **Room Database** (SQLite) — hoạt động offline hoàn toàn
- Sao lưu & đồng bộ lên **Firebase Firestore**
- Soft-delete + `sync_status` để xử lý xung đột offline/online
- Đồng bộ nền bằng **WorkManager SyncWorker** cho wallets/categories/transactions/budgets/debts/events
- Có metadata đồng bộ để quản lý mốc thời gian sync và trạng thái đồng bộ thực tế

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

Room Database version **13** với **8 bảng**, áp dụng offline-first:

| Bảng | Mô tả | FK |
|---|---|---|
| `users` | Tài khoản người dùng (Firebase UID) | — |
| `wallets` | Ví tiền | → users |
| `categories` | Danh mục thu/chi (system + custom) | → users (nullable) |
| `transactions` | Giao dịch thu/chi/chuyển khoản | → wallets, categories, debts, events |
| `budgets` | Ngân sách theo khoảng ngày / ví / danh mục | → users, categories (nullable) |
| `debts` | Khoản nợ/mượn | → users |
| `events` | Sự kiện tài chính | → users |
| `sync_metadata` | Metadata phục vụ incremental sync và quản lý trạng thái đồng bộ | — |

Tất cả bảng nghiệp vụ chính có `sync_status (Int)` và `is_deleted (Boolean)` để hỗ trợ đồng bộ offline-first.

---

## Yêu cầu hệ thống

- **Android Studio** Ladybug (2024.2.1) trở lên
- **JDK** 11 trở lên
- **Android SDK** API 29+
- Tài khoản **Firebase** (để cấu hình Auth & Firestore)
- File `local.properties` với `sdk.dir` và `GEMINI_API_KEY`

---

## Trạng thái phát triển

### Hoàn thành ổn định ✅

| Module | Trạng thái hiện tại |
|---|---|
| Foundation | Hoàn tất kiến trúc MVVM + Repository + Manual DI, Room migration chain tới v13 |
| Authentication | Hoàn tất login/register/reset password, routing login state, khôi phục local user |
| Wallet | CRUD + archive/restore + soft-delete, đồng bộ hành vi với Budget/Transaction |
| Category | CRUD + seed mặc định + icon/color picker + category hierarchy + soft-delete |
| Transaction | CRUD đầy đủ, cập nhật số dư ví tự động, danh sách nhóm theo thời gian, hỗ trợ báo cáo/drill-down |
| Budget | Running/Finished budgets, wallet filter, detail chart/statistics, `Tất cả danh mục` và `Các mục khác` |
| Statistics | Drill-down report flow đầy đủ (overview/detail/group/single-category), điều hướng tới transaction list |

### Có nền tảng, tiếp tục hoàn thiện 🔄

| Module | Trạng thái hiện tại |
|---|---|
| Home Dashboard | Đã hoạt động tốt cho use case chính, tiếp tục polish UI/insight |
| Debt | Có entity/repository/viewmodel/flow cơ bản, cần hoàn thiện sâu repayment/report/reminder |
| Event | Có entity/repository/viewmodel/flow cơ bản, cần hoàn thiện detail/lifecycle/integration sâu |
| Profile & Settings | Có cấu trúc màn hình và luồng điều hướng, tiếp tục hoàn thiện tuỳ chọn hiển thị/hồ sơ |
| Passcode/Security | Có nền tảng passcode + security package, cần hoàn thiện lifecycle và UX lỗi |
| AI Assistant/Receipt | Có màn hình + pipeline cơ bản, tiếp tục tối ưu độ chính xác và UX xác nhận dữ liệu |
| Cloud Sync | Đã có SyncWorker + sync status model, tiếp tục harden conflict/retry/observability |

---

## Tài liệu

| File | Mô tả |
|---|---|
| [project-structure.md](docs/project-structure.md) | Cấu trúc file & kiến trúc chi tiết |
| [phases.md](docs/phases.md) | Kế hoạch triển khai và trạng thái hiện tại |
| [implementation-phases.md](docs/implementation-phases.md) | Lộ trình triển khai song song giữa các track |
| [copilot-instructions.md](.github/copilot-instructions.md) | Coding rules cho AI code generation |

---
