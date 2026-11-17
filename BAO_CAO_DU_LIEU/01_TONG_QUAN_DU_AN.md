# TỔNG QUAN DỰ ÁN HỆ THỐNG IOT SMART HOME VÀ TINYML

## 1. TÊN ĐỀ TÀI VÀ THÔNG TIN CHUNG
- **Tên đề tài chính thức**: *Nghiên cứu và Triển khai Hệ thống Nhà thông minh Điều khiển Giọng nói Tiếng Việt Cục bộ bằng TinyML trên Vi điều khiển ESP32-S3 kết hợp IoT Cloud và Tự động hóa Đa ngữ cảnh*
- **Tên rút gọn**: *SmartHome-KWS (Vietnamese Keyword Spotting for Smart Home)*
- **Nền tảng vi điều khiển cốt lõi**: Espressif ESP32-S3 (Kiến trúc Dual-Core Xtensa LX7 @ 240MHz, tích hợp Vector Instructions, 16MB Flash, 8MB PSRAM OPI).
- **Môi trường phát triển**: Visual Studio Code, PlatformIO Core, ESP-IDF FreeRTOS & Arduino Core framework.

---

## 2. BỐI CẢNH VÀ VẤN ĐỀ THỰC TẾ CẦN GIẢI QUYẾT

### 2.1. Hạn chế của các hệ thống Smart Home thương mại hiện nay
1. **Phụ thuộc hoàn toàn vào kết nối Internet (Cloud-tethered)**:
   - Các trợ lý ảo phổ biến như Google Assistant, Amazon Alexa, Apple Siri bắt buộc phải stream âm thanh người dùng lên máy chủ đám mây để nhận dạng qua các mô hình ASR (Automatic Speech Recognition) khổng lồ.
   - Khi mạng Internet bị gián đoạn, mất Wi-Fi hoặc máy chủ gặp sự cố, hệ thống hoàn toàn tê liệt, người dùng không thể điều khiển các thiết bị cơ bản như đèn, quạt, cửa sổ.
2. **Độ trễ phản hồi cao (High Latency)**:
   - Quy trình nén âm thanh, truyền gói tin qua mạng WAN, giải mã tại máy chủ, suy luận ASR, xử lý NLU (Natural Language Understanding) và gửi gói tin điều khiển trở lại thiết bị mất từ 800ms đến 2500ms. Điều này gây cảm giác phản hồi chậm chạp cho người dùng trong không gian sống.
3. **Mối lo ngại nghiêm trọng về Quyền riêng tư (Privacy Concerns)**:
   - Việc micro liên tục thu âm và truyền dữ liệu âm thanh giọng nói trong không gian riêng tư của gia đình lên Cloud tạo ra rủi ro bị rò rỉ dữ liệu, bị nghe lén hoặc bị phân tích dữ liệu hành vi trái phép.
4. **Hệ thống tự động hóa đơn lập, thiếu ngữ cảnh thông minh**:
   - Phần lớn thiết bị giá rẻ trên thị trường chỉ hoạt động dựa trên cảm biến đơn lẻ (ví dụ: cảm biến chuyển động PIR hồng ngoại chỉ nhận biết khi người chuyển động mạnh, khi người ngồi đọc sách hoặc ngủ sẽ tự động ngắt điện gây phiền toái).
5. **Rủi ro an ninh đối với hệ thống khóa cửa thông minh**:
   - Sử dụng mật khẩu số truyền thống không mã hóa hoặc lưu trực tiếp trên bộ nhớ flash dễ bị đọc trộm khi thiết bị bị tháo rời hoặc bị tấn công duyệt cạn (brute-force).

---

## 3. MỤC TIÊU VÀ ĐÓNG GÓP CỐT LÕI CỦA ĐỀ TÀI

### 3.1. Mục tiêu kỹ thuật
1. **Thiết kế hệ thống nhận dạng giọng nói Tiếng Việt Offline hoàn toàn (TinyML Edge AI)**:
   - Huấn luyện và lượng tử hóa mô hình mạng nơ-ron học sâu (Deep Neural Network) nhận dạng từ khóa giọng nói Tiếng Việt (Keyword Spotting - KWS) chạy trực tiếp trên vi điều khiển ESP32-S3 mà không cần kết nối Internet.
   - Thời gian suy luận cục bộ < 50ms, độ trễ tác động ra rơ-le tức thì < 1ms, độ chính xác nhận diện thực nghiệm đạt 100% đối với các lệnh hợp lệ với ngưỡng tin cậy >= 70%.
2. **Xây dựng động cơ Tự động hóa Ngữ cảnh (Context-Aware Automation Engine)**:
   - Kết hợp đa cảm biến: Cảm biến vi sóng mmWave Radar LD2410S 24GHz (nhận diện vi chuyển động nhịp thở của người ngay cả khi ngồi yên), cảm biến nhiệt độ - độ ẩm DHT22, cảm biến quang trở LDR và đồng hồ thời gian thực NTP.
   - Triển khai thuật toán giải quyết xung đột trạng thái phần cứng (Conflict Resolution) với nguyên tắc An toàn là trên hết (Safety-First: Lệnh TẮT/ĐÓNG luôn ưu tiên hơn lệnh BẬT/MỞ).
3. **Phát triển Hệ thống Kiểm soát Ra vào Bảo mật Cao (Smart Door Security)**:
   - Xác thực mã PIN qua bàn phím ma trận 4x3 bằng giải thuật băm Salted SHA-256 lưu trong NVS Flash.
   - Cơ chế phần cứng chống Brute-force: Tự động khóa cứng bàn phím trong 120 giây và kích hoạt còi báo động khi nhập sai liên tiếp 4 lần.
4. **Đồng bộ Đám mây Song hành (Dual-Sync Cloud IoT)**:
   - Kết nối hai chiều với Google Firebase Realtime Database qua FreeRTOS Task độc lập trên Core 0, không làm gián đoạn chu kỳ lấy mẫu âm thanh I2S và suy luận TinyML trên Core 1.
   - Hệ thống hoạt động độc lập (Autonomous Edge): Khi mất mạng, toàn bộ chức năng giọng nói, cảm biến, tự động hóa và bảo mật cửa vẫn hoạt động bình thường 100%. Khi có mạng trở lại, hệ thống tự động đồng bộ trạng thái lên đám mây.
5. **Xây dựng Hệ sinh thái Đa nền tảng cho Người dùng**:
   - Giao diện Web SPA hiện đại (Single Page Application, responsive, theme đổi màu Aurora, quản trị người dùng phân quyền Master/Member).
   - Ứng dụng di động Native Android (Kotlin) và Cross-platform (Flutter), cho phép giám sát và cấu hình từ xa.

---

## 4. BẢNG PHÂN TÍCH TÍNH NĂNG: CỐT LÕI VÀ BỔ TRỢ

| STT | Nhóm chức năng | Phân loại | Hiện trạng kỹ thuật | File mã nguồn minh chứng |
|:---:|:---|:---:|:---:|:---|
| 1 | Nhận diện giọng nói Tiếng Việt Offline | **Cốt lõi** | `[ĐÃ KIỂM CHỨNG]` | `lib/KWS-Vietnamese-command_inferencing/`, `src/main.cpp` |
| 2 | Bộ lọc VAD tiết kiệm năng lượng | **Cốt lõi** | `[ĐÃ KIỂM CHỨNG]` | `src/main.cpp` (Ngưỡng RMS 7.000.000) |
| 3 | Tự động hóa đa ngữ cảnh (Temp, mmWave, LDR) | **Cốt lõi** | `[ĐÃ KIỂM CHỨNG]` | `src/main.cpp`, `src/automation_engine.cpp` |
| 4 | Thuật toán giải quyết xung đột an toàn | **Cốt lõi** | `[ĐÃ TRIỂN KHAI]` | `src/automation_engine.cpp:85-130` |
| 5 | Khóa cửa Salted SHA-256 & Chống dò mã | **Cốt lõi** | `[ĐÃ KIỂM CHỨNG]` | `src/door_controller.cpp`, `src/main.cpp` |
| 6 | FreeRTOS Đa nhân (Dual-Core Task Separation) | **Cốt lõi** | `[ĐÃ KIỂM CHỨNG]` | `src/main.cpp:115-180`, `src/firebase_handle.cpp` |
| 7 | Đồng bộ thời gian thực Firebase RTDB | **Cốt lõi** | `[ĐÃ KIỂM CHỨNG]` | `src/firebase_handle.cpp`, `web/index.html` |
| 8 | Quản trị phân quyền Master/Member | **Cốt lõi** | `[ĐÃ KIỂM CHỨNG]` | `database.rules.json`, `web/index.html:1200-1450` |
| 9 | Giao diện Web SPA responsive (Shopee/Aurora style)| **Bổ trợ** | `[ĐÃ KIỂM CHỨNG]` | `web/index.html` (5.506 dòng) |
| 10 | Ứng dụng di động Android & Flutter | **Bổ trợ** | `[ĐÃ KIỂM CHỨNG]` | `mobile_app/`, `docs/SmartHome-KWS.apk` |
| 11 | Hệ thống log phân tích hành vi người dùng Python | **Bổ trợ** | `[ĐÃ KIỂM CHỨNG]` | `analyze_logs.py`, `reports/BAO_CAO_PHAN_TICH_DU_LIEU.md` |
| 12 | Điều khiển cử chỉ bàn tay (Gesture) | **Bổ trợ** | `[MỚI DỰ KIẾN]` | Đang mua linh kiện, chuẩn bị mở rộng |

---

## 5. ĐỐI TƯỢNG SỬ DỤNG VÀ PHẠM VI ỨNG DỤNG
- **Người cao tuổi và người khuyết tật vận động**: Dễ dàng điều khiển thiết bị trong nhà bằng các câu lệnh Tiếng Việt tự nhiên, ngắn gọn mà không cần thao tác bấm nút vật lý hay sử dụng smartphone phức tạp.
- **Hộ gia đình hiện đại (Smart Living)**: Cần không gian sống tự động hóa thông minh (quạt tự bật khi nóng và có người, rèm tự mở vào ban ngày, đèn sân tự sáng vào ban đêm).
- **Yêu cầu bảo mật và an toàn dữ liệu cao**: Các phòng làm việc, căn hộ riêng tư không muốn dữ liệu âm thanh bị truyền lên máy chủ của các tập đoàn công nghệ lớn.
- **Khu vực có hạ tầng mạng Internet không ổn định**: Hệ thống vẫn vận hành 100% không gian sống ngay cả khi đường truyền Internet cáp quang bị gián đoạn.
