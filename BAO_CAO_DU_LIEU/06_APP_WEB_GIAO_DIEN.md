# ỨNG DỤNG ĐIỀU KHIỂN WEB VÀ MOBILE APP

## 1. ỨNG DỤNG WEB SINGLE PAGE APPLICATION (SPA)

### 1.1. Kiến trúc kỹ thuật
- **Vị trí tệp nguồn**: `web/index.html` (Quy mô **5.506 dòng mã** HTML, CSS hiện đại và Vanilla JavaScript thuần túy).
- **Mô hình kiến trúc**: Single Page Application (SPA), không sử dụng framework cồng kềnh (React/Vue/Angular), giúp tốc độ tải trang gần như tức thời (< 500ms) và không có độ trễ runtime overhead.
- **Phong cách thiết kế giao diện**:
  - Giao diện lấy cảm hứng từ phong cách cao cấp Aurora Glassmorphism kết hợp bố cục quản trị Shopee Style.
  - Hỗ trợ chuyển đổi mượt mà giữa chế độ Sáng (Light Mode) và Tối (Dark Mode).
  - Tương thích hoàn hảo (Fully Responsive) trên cả màn hình máy tính bàn (Desktop) và thiết bị di động (Mobile).

### 1.2. Các phân hệ màn hình chức năng chính
1. **Bảng điều khiển tổng quan (Dashboard View)**:
   - Thẻ hiển thị nhiệt độ, độ ẩm môi trường thời gian thực kèm cảnh báo bằng màu sắc trực quan.
   - Thẻ giám sát cảm biến hiện diện mmWave LD2410S (hiển thị trạng thái Có người / Vắng mặt và khoảng cách chi tiết).
   - Hệ thống công tắc chuyển mạch (Toggle Switches) điều khiển tức thì: Đèn phòng, Quạt làm mát, Đèn sân vườn, Rèm cửa tự động và Cửa trượt chính.
   - Đồ thị diễn tiến nhiệt độ - độ ẩm 24 giờ và 7 ngày qua tích hợp thư viện Canvas/Chart.
2. **Phân hệ Quản trị Tự động hóa (Automation Rules Builder)**:
   - Cho phép người dùng tự thiết lập các kịch bản If-This-Then-That linh hoạt mà không cần lập trình lại firmware.
   - Thiết lập điều kiện phối hợp: Nhiệt độ vượt ngưỡng AND Có người hiện diện AND Trời tối (LDR) AND Khung giờ xác định.
   - Đặt hành động thực thi: Bật quạt, Đóng rèm, Bật đèn sân.
3. **Phân hệ Kiểm soát Ra vào & Cửa (Door & Security View)**:
   - Quản lý danh sách mã PIN ra vào nhà, hiển thị trạng thái đã băm Salted SHA-256.
   - Cảnh báo trực quan khi bàn phím phần cứng bị khóa cứng do nhập sai 4 lần kèm đồng hồ đếm ngược 120 giây.
   - Nút mở khóa khẩn cấp từ xa dành riêng cho tài khoản Chủ nhà (Master).
4. **Phân hệ Quản lý Thành viên Gia đình (Family Members Management)**:
   - Sinh mã mời thành viên gia đình (Invite Code Generator).
   - Bảng phân quyền chi tiết (Checkboxes): Cho phép hoặc hạn chế từng thành viên đối với quyền bật đèn, bật quạt, mở cửa hoặc điều khiển giọng nói.
5. **Thông báo Sự kiện Giọng nói (Real-time Voice Toast Notification)**:
   - Khi có người ra lệnh giọng nói tại chỗ trên ESP32-S3, một thông báo Toast dạng nổi sẽ xuất hiện ngay lập tức trên giao diện Web hiển thị từ khóa vừa nhận diện và độ tin cậy tương ứng.

---

## 2. ỨNG DỤNG DI ĐỘNG (MOBILE APPS)

Dự án cung cấp 2 giải pháp ứng dụng di động song hành:

### 2.1. Ứng dụng Android Native (Kotlin)
- **Vị trí mã nguồn**: `mobile_app/app/`
- **Công nghệ**: Kotlin, Android Jetpack, Firebase Android SDK.
- **Bản cài đặt thành phẩm**: `docs/SmartHome-KWS.apk` (Dung lượng: **24.4 MB**).
- **Tính năng nổi bật**: Tối ưu hóa hiệu năng phần cứng Android, hỗ trợ thông báo đẩy chạy nền (Push Notifications) khi phát hiện đột nhập hoặc nhập sai mã PIN cửa 4 lần.

### 2.2. Ứng dụng Đa nền tảng (Flutter Cross-Platform)
- **Vị trí mã nguồn**: `mobile_app/lib/`
- **Công nghệ**: Google Flutter Framework (Dart), thư viện `firebase_database`, `firebase_auth`.
- **Cấu trúc màn hình**:
  - `main.dart`: Khởi tạo luồng ứng dụng và lắng nghe trạng thái đăng nhập.
  - Các màn hình: `HomeScreen`, `DeviceControlScreen`, `AutomationScreen`, `SecurityDoorScreen`, `ProfileScreen`.
