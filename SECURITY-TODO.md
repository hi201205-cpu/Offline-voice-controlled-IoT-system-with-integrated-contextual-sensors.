# Danh Sách Kiểm Tra & Hướng Dẫn Bảo Mật Thủ Công (SECURITY-TODO)

Tài liệu này tổng hợp các nhiệm vụ bảo mật không thể cấu hình tự động hoàn toàn bằng mã nguồn, bắt buộc người quản trị hệ thống và kỹ sư phần cứng (ESP32) phải thực hiện thủ công trong Firebase Console và môi trường phát triển.

---

## 1. Cấu hình Firebase Console (Thực hiện thủ công)

- [ ] **1.1. Cập nhật Rules cho Firebase Realtime Database:**
  - Sao chép toàn bộ nội dung trong file `database.rules.json`.
  - Mở **Firebase Console** > chọn dự án > **Realtime Database** > Chuyển sang tab **Rules**.
  - Dán nội dung Rules mới và sử dụng công cụ **Rules Playground** để kiểm tra:
    - Thử đọc/ghi khi chưa xác thực `auth === null` (kết quả mong đợi: `denied`).
    - Thử tài khoản người dùng thường tự ghi vào `users/{uid}/role` hoặc `system` (kết quả mong đợi: `denied`).
    - Thử tài khoản thành viên tự ghi vào `automation/rules` hoặc `automation/enabled` (kết quả mong đợi: `denied` – chỉ Master được ghi).
    - Thử thành viên đọc `door/passcodeOwners` (kết quả mong đợi: `denied` – chỉ Master được đọc).
    - Thử thành viên xóa mật khẩu keypad của người khác tại `door/pending` (kết quả mong đợi: `denied` do không trùng `door/passcodeOwners/{target}.uid`).
    - Thử ghi vào `commands/door` khi tài khoản chưa được liên kết (`pairedHome`) hoặc bị thu hồi quyền `door` (kết quả mong đợi: `denied`).
  - Nhấn **Publish** để áp dụng bộ Rules mới.

- [ ] **1.2. Kích hoạt Firebase App Check:**
  - Trong Firebase Console > Menu **App Check** > Bấm **Get Started**.
  - Đăng ký ứng dụng Web với **reCAPTCHA Enterprise** hoặc **reCAPTCHA v3**.
  - Đăng ký ứng dụng Android với nhà cung cấp chứng thực **Play Integrity**.
  - Bật tính năng **Enforce** cho **Realtime Database** để ngăn chặn triệt để các công cụ tự động, script Python, Postman, hoặc hacker gọi API trực tiếp vào database.

- [ ] **1.3. Giới hạn Web API Key theo Domain (HTTP Referrers):**
  - Mở **Google Cloud Console** (cùng Google Project ID với Firebase) > **APIs & Services** > **Credentials**.
  - Tìm khóa API của Web client (thường có tên dạng *Browser key* hoặc *Auto-created by Firebase*).
  - Mục **Application restrictions** > Chọn **Websites (HTTP referrers)**.
  - Thêm các domain triển khai hợp lệ của bạn (ví dụ: `https://your-domain.web.app/*`, `https://your-domain.firebaseapp.com/*`, và `http://localhost:*` khi chạy thử nghiệm nội bộ).
  - Mục **API restrictions** > Chỉ cho phép các API thực sự cần thiết: *Firebase Realtime Database API*, *Identity Toolkit API*, *Token Service API*.

- [ ] **1.4. Bật tính năng Email Enumeration Protection:**
  - Trong Firebase Console > **Authentication** > Tab **Settings** > Mục **User actions**.
  - Tích chọn **Email enumeration protection (recommended)**.
  - Tính năng này giúp các phản hồi khi đăng ký/đăng nhập/quên mật khẩu không tiết lộ liệu một email đã tồn tại trong hệ thống hay chưa, ngăn chặn kẻ xấu dò quét danh sách tài khoản gia đình.

- [ ] **1.5. Yêu cầu xác minh Email cho tài khoản Master:**
  - Bộ Rules mới yêu cầu: `auth.token.email === 'xhai2005@gmail.com' && auth.token.email_verified === true`.
  - Mở Firebase Console > **Authentication** > **Users** > Kiểm tra cột *Email verified* của tài khoản `xhai2005@gmail.com`.
  - Nếu chưa xác minh, hãy gửi email xác minh qua giao diện Web hoặc Firebase Console và nhấp vào liên kết xác nhận để nhận quyền Master hợp lệ.

---

## 2. Quản lý Bí Mật & Kiểm Tra Firmware ESP32

- [ ] **2.1. Bảo vệ `FIREBASE_AUTH` (Database Secret) trong Firmware:**
  - Bảo đảm rằng file chứa Database Secret (ví dụ `include/config.h` hoặc `platformio.ini`) luôn nằm trong file `.gitignore`.
  - Kiểm tra lịch sử git bằng lệnh: `git log -S "FIREBASE_AUTH"`.
  - **Lưu ý khẩn cấp:** Nếu `FIREBASE_AUTH` từng bị commit vào git repository công khai hoặc chia sẻ ra ngoài, hãy vào **Firebase Console** > **Project Settings** > **Service accounts** > **Database secrets** để bấm **Revoke** (thu hồi) và sinh một secret mới, sau đó nạp lại vào ESP32.
  - **Định hướng tương lai:** Database Secret hiện đã được Google đánh dấu lỗi thời (deprecated). Về lâu dài, hãy chuyển ESP32 sang sử dụng xác thực bằng Firebase Service Account hoặc Custom Token qua REST API / Google OAuth2.

- [ ] **2.2. Kiểm tra Timestamp `ts` trong Firmware ESP32 (Chống phát lại - Anti-Replay):**
  - Trong file `src/firebase_manager.cpp`, khi ESP32 nhận lệnh điều khiển cửa qua `/commands/door`:
    ```cpp
    // Đề xuất bổ sung kiểm tra timestamp còn mới (trong khoảng 15-30 giây)
    if (command.hasTimestamp) {
        int64_t nowMs = getEpochTimeMs();
        if (abs(nowMs - (int64_t)command.timestampMs) > 30000) {
            LOG("[DOOR] Từ chối lệnh mở cửa đã hết hạn: ts=%lld, now=%lld\n",
                (long long)command.timestampMs, (long long)nowMs);
            return;
        }
    }
    ```

- [ ] **2.3. Khử trùng chuỗi `id` và lưu `createdBy` khi ghi metadata Keypad:**
  - Trong file `src/firebase_manager.cpp` (hàm `firebase_write_passcode_metadata`):
    - Khi nhận lệnh thêm mật mã từ `door/pending/{opId}`, firmware nên đọc thêm trường `createdBy` từ `door/passcodeOwners/{opId}` hoặc gán metadata `createdBy` vào `door/passcodes/{id}` để hệ thống dễ dàng truy xuất nguồn gốc.
  - Trong file `src/door_manager.cpp`, kiểm tra chuỗi `id` được truyền vào làm khóa đường dẫn `/door/passcodes/{id}`:
    - Bảo đảm `id` chỉ gồm các ký tự an toàn `[a-zA-Z0-9_-]` và có độ dài nhỏ hơn 48 ký tự để không làm tràn bộ đệm NVS của chip ESP32.

---
*Tài liệu được cập nhật theo bộ Rules và logic phân quyền nâng cao Nhà thông minh IoT ESP32.*
