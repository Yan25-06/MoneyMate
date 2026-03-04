# 💰 MoneyMate — Ứng dụng Quản lý Chi tiêu Cá nhân

> Giải pháp quản lý tài chính cá nhân toàn diện: theo dõi thu chi, quản lý nhiều ví, lập ngân sách và thống kê trực quan.

---

## 📋 Mục lục

- [Tính năng](#-tính-năng)
- [Tech Stack](#-tech-stack)
- [Kiến trúc](#-kiến-trúc)
- [Cấu trúc dự án](#-cấu-trúc-dự-án)
- [Yêu cầu hệ thống](#-yêu-cầu-hệ-thống)
- [Hướng dẫn cài đặt](#-hướng-dẫn-cài-đặt)
- [Kế hoạch triển khai](#-kế-hoạch-triển-khai)
- [Tài liệu](#-tài-liệu)

---

## ✨ Tính năng

### 🔐 Xác thực & Tài khoản
- Đăng ký / Đăng nhập bằng Email & Password
- Đăng nhập nhanh bằng **Passcode 6 số** (hoạt động offline)
- Đăng nhập bằng Google (Gmail)
- Ghi nhớ trạng thái đăng nhập
- Đổi mật khẩu & khôi phục mật khẩu

### 👛 Quản lý Ví
- Tạo nhiều ví: Tiền mặt / Ngân hàng / Ví điện tử
- Theo dõi số dư từng ví riêng biệt
- Chuyển tiền giữa các ví

### 💸 Giao dịch Thu – Chi
- Thêm / Sửa / Xóa giao dịch
- Gán danh mục, chọn ví, chọn ngày
- Tự động cập nhật số dư ví
- Tìm kiếm & lọc giao dịch

### 🏷️ Danh mục
- Danh mục mặc định sẵn có (Ăn uống, Di chuyển, Lương, ...)
- Tạo danh mục tùy chỉnh với biểu tượng riêng

### 📊 Ngân sách
- Đặt hạn mức chi tiêu theo tháng / theo danh mục
- Theo dõi % đã sử dụng với Progress Bar
- Cảnh báo khi chi tiêu vượt 80% và 100% ngân sách

### 📈 Thống kê & Báo cáo
- Biểu đồ tròn (PieChart) chi tiêu theo danh mục
- Biểu đồ cột (BarChart) so sánh 6 tháng gần nhất
- Thống kê tổng thu / chi theo ngày, tháng

### 👤 Hồ sơ & Cài đặt
- Chỉnh sửa tên hiển thị, ảnh đại diện
- Chọn đơn vị tiền tệ (VND / USD / EUR)
- Chuyển đổi Dark Mode / Light Mode
- Ẩn/hiện số dư tổng để bảo mật

### 🔄 Lưu trữ & Đồng bộ
- Lưu cục bộ với **Room Database** (SQLite)
- Sao lưu & đồng bộ lên **Firebase Firestore**
- Khôi phục dữ liệu khi đổi thiết bị

---

## 🛠 Tech Stack

| Thành phần | Công nghệ | Phiên bản |
|------------|-----------|-----------|
| Ngôn ngữ | Java | 11 |
| Min SDK | Android | API 29 (Android 10) |
| Target SDK | Android | API 36 |
| Local DB | Room (SQLite) | 2.6.1 |
| Auth & Cloud | Firebase | BOM 33.7.0 |
| Navigation | Navigation Component | 2.8.6 |
| Lifecycle | ViewModel + LiveData | 2.8.7 |
| Charts | MPAndroidChart | v3.1.0 |
| UI | Material Design 3 | 1.13.0 |
| View Binding | ViewBinding | — |

---

## 🏗 Kiến trúc

Dự án sử dụng **MVVM + Repository Pattern**:

```
┌─────────────────────────────────────────────────────┐
│                      UI Layer                       │
│         Fragment / Activity + ViewBinding           │
└──────────────────────┬──────────────────────────────┘
                       │ observe LiveData
┌──────────────────────▼──────────────────────────────┐
│                  ViewModel Layer                    │
│         (survive configuration changes)             │
└──────────────────────┬──────────────────────────────┘
                       │ call methods
┌──────────────────────▼──────────────────────────────┐
│                 Repository Layer                    │
│       (single source of truth, business logic)      │
└──────────┬───────────────────────────┬──────────────┘
           │                           │
┌──────────▼──────────┐   ┌────────────▼──────────────┐
│   Local (Room DB)   │   │  Remote (Firebase Auth /  │
│   DAOs + Entities   │   │        Firestore)         │
└─────────────────────┘   └───────────────────────────┘
```

**Nguyên tắc:**
- UI chỉ quan sát `LiveData`, không chứa business logic
- ViewModel không biết về Android framework
- Repository là nguồn dữ liệu duy nhất (offline-first)
- Manual DI qua `AppContainer` trong `Application` class

---

## 📁 Cấu trúc dự án

```
app/src/main/java/com/example/moneymate/
│
├── MainActivity.java              ← Router: Login hoặc Home
│
├── models/
│   ├── TransactionType.java       (INCOME / EXPENSE)
│   └── WalletType.java            (CASH / BANK / E_WALLET)
│
├── data/
│   ├── local/
│   │   ├── AppDatabase.java       ← Room singleton
│   │   ├── Converters.java        ← Date↔Long, Enum↔String
│   │   ├── entity/
│   │   │   ├── UserEntity.java
│   │   │   ├── WalletEntity.java
│   │   │   ├── CategoryEntity.java
│   │   │   ├── TransactionEntity.java
│   │   │   └── BudgetEntity.java
│   │   └── dao/
│   │       ├── UserDao.java
│   │       ├── WalletDao.java
│   │       ├── CategoryDao.java
│   │       ├── TransactionDao.java
│   │       └── BudgetDao.java
│   ├── remote/
│   │   └── FirebaseAuthHelper.java
│   └── repository/
│       ├── AuthRepository.java
│       ├── UserRepository.java
│       ├── WalletRepository.java
│       ├── CategoryRepository.java
│       ├── TransactionRepository.java
│       └── BudgetRepository.java
│
├── di/
│   ├── MoneyMateApplication.java  ← Application class
│   └── AppContainer.java          ← Manual DI container
│
├── utils/
│   ├── Constants.java
│   ├── PrefsManager.java
│   ├── CurrencyFormatter.java
│   └── DateUtils.java
│
└── ui/
    ├── auth/         LoginActivity, Login/RegisterFragment, AuthViewModel
    ├── main/         HomeActivity (BottomNavigationView host)
    ├── home/         HomeFragment, HomeViewModel
    ├── wallet/       WalletListFragment, AddEditWalletFragment, WalletAdapter, WalletViewModel
    ├── category/     CategoryListFragment, AddEditCategoryFragment, CategoryAdapter, CategoryViewModel
    ├── transaction/  TransactionListFragment, AddEditTransactionFragment, TransactionAdapter, TransactionViewModel
    ├── budget/       BudgetListFragment, AddEditBudgetFragment, BudgetAdapter, BudgetViewModel
    ├── statistics/   StatisticsFragment, StatisticsViewModel
    ├── profile/      ProfileFragment, ProfileViewModel
    └── settings/     SettingsFragment, SettingsViewModel
```

> 📄 Chi tiết đầy đủ: [`docs/project-structure.md`](docs/project-structure.md)

---

## ⚙️ Yêu cầu hệ thống

- **Android Studio** Hedgehog (2023.1.1) trở lên
- **JDK** 11 trở lên
- **Android SDK** API 29+
- Tài khoản **Firebase** (để cấu hình Auth & Firestore)
- Kết nối internet (để đăng ký tài khoản lần đầu)

---

## 🚀 Hướng dẫn cài đặt

### 1. Clone dự án

```bash
git clone https://github.com/Yan25-06/MoneyMate.git
cd MoneyMate
```

### 2. Cấu hình Firebase

1. Truy cập [Firebase Console](https://console.firebase.google.com/)
2. Tạo project mới → Thêm ứng dụng Android
3. Package name: `com.example.moneymate`
4. Tải file `google-services.json`
5. Đặt vào thư mục `app/`
6. Trong Firebase Console, bật:
   - **Authentication** → Email/Password
   - **Firestore Database** (chế độ test)
7. Mở `app/build.gradle.kts`, bỏ comment dòng:
   ```kotlin
   alias(libs.plugins.google.services)
   ```

### 3. Build & Run

```bash
# Build debug
./gradlew assembleDebug

# Cài đặt lên thiết bị/emulator
./gradlew installDebug
```

Hoặc mở trong **Android Studio** → **Run** (Shift + F10)

---

## 📅 Kế hoạch triển khai

| Phase | Tên | Trạng thái |
|-------|-----|-----------|
| 0 | Foundation (Room DB, DI, Utils) | 🔲 Chưa bắt đầu |
| 1 | Authentication (Đăng ký / Đăng nhập) | 🔲 Chưa bắt đầu |
| 2 | Wallet Management (Quản lý Ví) | 🔲 Chưa bắt đầu |
| 3 | Category Management (Danh mục) | 🔲 Chưa bắt đầu |
| 4 | Transaction Management (Giao dịch) | 🔲 Chưa bắt đầu |
| 5 | Home Dashboard (Trang chủ) | 🔲 Chưa bắt đầu |
| 6 | Budget Management (Ngân sách) | 🔲 Chưa bắt đầu |
| 7 | Statistics (Thống kê) | 🔲 Chưa bắt đầu |
| 8 | Profile & Settings (Hồ sơ) | 🔲 Chưa bắt đầu |
| 9 | Passcode Login (Đăng nhập nhanh) | 🔲 Chưa bắt đầu |
| 10 | Polish & QA (Hoàn thiện) | 🔲 Chưa bắt đầu |

> 📄 Chi tiết từng phase: [`docs/implementation-phases.md`](docs/implementation-phases.md)

---

## 📚 Tài liệu

| File | Mô tả |
|------|-------|
| [`docs/project-structure.md`](docs/project-structure.md) | Cấu trúc file & kiến trúc chi tiết |
| [`docs/implementation-phases.md`](docs/implementation-phases.md) | Kế hoạch triển khai theo từng phase |

---