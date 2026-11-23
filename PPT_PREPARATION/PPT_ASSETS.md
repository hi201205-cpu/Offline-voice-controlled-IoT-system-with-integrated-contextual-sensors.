# DANH MỤC TÀI NGUYÊN HÌNH ẢNH, SƠ ĐỒ VÀ DỮ LIỆU BÁO CÁO (PPT_ASSETS.md)
## HỆ THỐNG IOT ĐIỀU KHIỂN BẰNG GIỌNG NÓI OFFLINE, TÍCH HỢP CẢM BIẾN NGỮ CẢNH VÀ PHÂN TÍCH CLOUD

> **Mục đích:** Cung cấp danh mục toàn diện và đường dẫn chính xác của tất cả các tài nguyên (Sơ đồ kỹ thuật, Biểu đồ thực nghiệm, Ảnh chụp giao diện Web/App, Bảng dữ liệu CSV, Tài liệu PDF) có trong dự án để AI hoặc người thiết kế slide có thể nhúng trực tiếp vào bài thuyết trình PowerPoint bảo vệ đồ án tốt nghiệp.

---

## 1. BẢNG TỔNG HỢP NHANH TÀI NGUYÊN THEO SLIDE

| Số Slide | Tiêu đề Slide thuyết trình | Tài nguyên hình ảnh / Sơ đồ tương ứng | Tệp dữ liệu / Mã nguồn căn cứ |
|:---:|:---|:---|:---|
| **Slide 1** | Tiêu đề đề tài & Giới thiệu | `aurora_desktop.png` + Ảnh chụp mô hình thật | `BAO_CAO_DU_LIEU/EVIDENCE_IMAGES/aurora_desktop.png` |
| **Slide 2** | Đặt vấn đề & Tính cấp thiết | Sơ đồ so sánh Cloud Voice vs Edge AI Voice | `BAO_CAO_DU_LIEU/01_TONG_QUAN_DU_AN.md` |
| **Slide 3** | Mục tiêu & Phạm vi nghiên cứu | Infographic 4 trụ cột kỹ thuật của đề tài | `BAO_CAO_DU_LIEU/00_INDEX.md` |
| **Slide 4** | Kiến trúc hệ thống 5 tầng | `system_architecture.mmd` | `BAO_CAO_DU_LIEU/DIAGRAMS/system_architecture.mmd` |
| **Slide 5** | Phần cứng & Thiết kế nguồn | `hardware_block_diagram.mmd` + Sơ đồ nguyên lý | `docs/1-so-do-nguyen-ly.pdf`, `system_bom.csv` |
| **Slide 6** | Mô hình TinyML & Audio Pipeline| `audio_pipeline_tinyml.mmd` | `lib/KWS-Vietnamese-command_inferencing/` |
| **Slide 7** | Dataset & Kết quả đánh giá | Biểu đồ phân bố 10 nhãn dữ liệu âm thanh | `BAO_CAO_DU_LIEU/DATA_TABLES/dataset_distribution.csv`|
| **Slide 8** | Phần sụn FreeRTOS 2 lõi | `firmware_freertos_tasks.mmd` | `src/main.cpp`, `src/audio_capture.cpp` |
| **Slide 9** | Tự động hóa ngữ cảnh cục bộ | `automation_rule_engine.mmd` + Giao diện Rule | `BAO_CAO_DU_LIEU/DIAGRAMS/automation_rule_engine.mmd` |
| **Slide 10**| Khóa cửa an ninh & Chống dò mã| `keypad_security_lockout.mmd` + Cảnh báo Telegram| `src/door_manager.cpp`, `src/telegram_manager.cpp` |
| **Slide 11**| Cài đặt BLE & Phân quyền RBAC | Ảnh chụp cài đặt BLE + Bảng phân quyền thành viên | `ldplayer_members_screen.png`, `screen_settings_perms.png`|
| **Slide 12**| Ứng dụng Android & Web SPA | Ghép đôi App (`screen_home_final.png`) & Web (`aurora_desktop.png`) | `BAO_CAO_DU_LIEU/EVIDENCE_IMAGES/` |
| **Slide 13**| Đánh giá thực nghiệm & Số liệu | Biểu đồ 4 thành phần `analysis_report_chart.png` | `reports/analysis_report_chart.png`, `analyze_logs.py` |
| **Slide 14**| Kết luận & Hướng phát triển | Ảnh chụp mô hình tổng thể hoàn thiện | `current_screen.png`, `BAO_CAO_DU_LIEU/EVIDENCE_IMAGES/` |
| **Phụ lục 1**| Sơ đồ nguyên lý & Bảng GPIO | Bảng ma trận 25 chân GPIO (`gpio_pinout_matrix.csv`)| `docs/3-chan-ket-noi-phan-cung.pdf` |
| **Phụ lục 2**| Giải thuật Salted SHA-256 | Sơ đồ khối băm mật khẩu Salted SHA-256 | `src/door_manager.cpp:115` |
| **Phụ lục 3**| Phân vùng Flash & PSRAM | Bảng phân vùng Flash 16MB (`partitions_custom.csv`) | `partitions_custom.csv`, `platformio.ini` |
| **Phụ lục 4**| Firebase Rules & Giao dịch nguyên tử| Sơ đồ giao dịch nguyên tử `updateChildren` | `database.rules.json`, `AuthRepository.kt` |

---

## 2. DANH MỤC BIỂU ĐỒ & KẾT QUẢ THỰC NGHIỆM THỰC TẾ (REAL EXPERIMENTAL ASSETS)

### 2.1. Biểu đồ phân tích dữ liệu 4 thành phần
- **Tên tài nguyên**: Biểu đồ phân tích tổng hợp dữ liệu hoạt động thực tế (4-in-1 Data Analysis Chart).
- **Đường dẫn file**: [`reports/analysis_report_chart.png`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/reports/analysis_report_chart.png)
- **Nội dung thể hiện**:
  1. *Subplot 1*: Biểu đồ cột phân bố tần suất ra lệnh giọng nói theo 24 giờ trong ngày (Cao điểm 7h–8h sáng và 18h–22h tối).
  2. *Subplot 2*: Biểu đồ độ tin cậy trung bình (Confidence) của từng từ khóa lệnh (Đạt 100% trong 748 lượt lệnh).
  3. *Subplot 3*: Biểu đồ tương quan giữa nhiệt độ môi trường phòng và sự kiện bật/tắt quạt (Nhiệt độ > 30°C kích hoạt tăng vọt gấp 3.2 lần).
  4. *Subplot 4*: Biểu đồ tròn cơ cấu các loại lệnh giọng nói được thực thi nhiều nhất (`bat_quat`, `tat_het`, `tat_quat`, `tat_den`, `bat_het`, `bat_den`).
- **Slide phù hợp**: **Slide 13 (Đánh giá thực nghiệm và phân tích dữ liệu hoạt động)**.
- **Trạng thái**: Có thể sử dụng ngay (Độ phân giải cao 300 DPI, nền sáng rõ nét).
- **Mức độ bằng chứng**: **BẰNG CHỨNG THỰC TẾ 100%** (Sinh từ tệp `analyze_logs.py` phân tích 748 bản ghi từ Firebase Cloud).

---

## 3. DANH MỤC SƠ ĐỒ KIẾN TRÚC & LƯU ĐỒ THUẬT TOÁN (MERMAID DIAGRAMS)

Tất cả các sơ đồ dưới đây được lưu trữ dưới dạng mã nguồn mở Mermaid (.mmd) trong thư mục [`BAO_CAO_DU_LIEU/DIAGRAMS/`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/DIAGRAMS/). Người thiết kế slide có thể mở trực tiếp tại [mermaid.live](https://mermaid.live) để xuất file ảnh PNG/SVG trong suốt chất lượng cao chèn vào PowerPoint:

### 3.1. Sơ đồ Kiến trúc hệ thống 5 tầng
- **Tệp nguồn**: [`BAO_CAO_DU_LIEU/DIAGRAMS/system_architecture.mmd`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/DIAGRAMS/system_architecture.mmd)
- **Nội dung**: Thể hiện đầy đủ 5 tầng logic: Ngoại vi $\leftrightarrow$ ESP32-S3 $\leftrightarrow$ Mạng kết nối $\leftrightarrow$ Firebase Cloud $\leftrightarrow$ Ứng dụng Client.
- **Slide phù hợp**: **Slide 4 (Kiến trúc hệ thống tổng thể)**.
- **Tính chất**: Sơ đồ kiến trúc chính thức của đồ án.

### 3.2. Sơ đồ Phần cứng và Sơ đồ khối kết nối
- **Tệp nguồn**: [`BAO_CAO_DU_LIEU/DIAGRAMS/hardware_block_diagram.mmd`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/DIAGRAMS/hardware_block_diagram.mmd)
- **Nội dung**: Bố trí vi điều khiển ESP32-S3, microphone I2S, 3 Rơ-le, 2 Servo, DHT22, LD2410S, quang trở, bàn phím ma trận, còi buzzer và các đường nguồn cách ly 5V / 3.3V.
- **Slide phù hợp**: **Slide 5 (Thiết kế phần cứng và phân bổ tài nguyên)**.
- **Tính chất**: Sơ đồ kỹ thuật phần cứng chuẩn hóa.

### 3.3. Sơ đồ Chuỗi xử lý âm thanh & TinyML Pipeline
- **Tệp nguồn**: [`BAO_CAO_DU_LIEU/DIAGRAMS/audio_pipeline_tinyml.mmd`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/DIAGRAMS/audio_pipeline_tinyml.mmd)
- **Nội dung**: Dòng chảy tín hiệu từ Mic INMP441 $\rightarrow$ I2S DMA $\rightarrow$ Ring Buffer PSRAM $\rightarrow$ Bộ lọc VAD Energy $\rightarrow$ Khối DSP MFCC (650 đặc trưng) $\rightarrow$ Mạng INT8 CNN $\rightarrow$ Ngưỡng tin cậy $\rightarrow$ Đóng ngắt Rơ-le.
- **Slide phù hợp**: **Slide 6 (Mô hình học sâu TinyML & Chuỗi xử lý âm thanh)**.
- **Tính chất**: Trọng tâm học thuật của đề tài.

### 3.4. Sơ đồ Phân chia tác vụ FreeRTOS 2 lõi
- **Tệp nguồn**: [`BAO_CAO_DU_LIEU/DIAGRAMS/firmware_freertos_tasks.mmd`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/DIAGRAMS/firmware_freertos_tasks.mmd)
- **Nội dung**: Thể hiện đối xứng Core 0 (Âm thanh I2S, VAD, TFLite Inference) và Core 1 (Sensors, Keypad, Rule Engine, Firebase WSS loop) cùng các cơ chế đồng bộ FreeRTOS Mutex và Task Notifications.
- **Slide phù hợp**: **Slide 8 (Thiết kế phần sụn đa nhiệm FreeRTOS 2 lõi)**.
- **Tính chất**: Bằng chứng kỹ thuật về năng lực lập trình nhúng chuyên sâu.

### 3.5. Sơ đồ Lưu đồ thuật toán Động cơ tự động hóa ngữ cảnh
- **Tệp nguồn**: [`BAO_CAO_DU_LIEU/DIAGRAMS/automation_rule_engine.mmd`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/DIAGRAMS/automation_rule_engine.mmd)
- **Nội dung**: Thuật toán đánh giá điều kiện đa cảm biến (Nhiệt độ + Radar người + Ánh sáng) và giải quyết xung đột theo nguyên tắc "An toàn là trên hết (Safety First)".
- **Slide phù compliance**: **Slide 9 (Động cơ tự động hóa ngữ cảnh cục bộ)**.

### 3.6. Sơ đồ Khối an ninh khóa cửa và Chống dò mã
- **Tệp nguồn**: [`BAO_CAO_DU_LIEU/DIAGRAMS/keypad_security_lockout.mmd`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/DIAGRAMS/keypad_security_lockout.mmd)
- **Nội dung**: Quy trình băm Salted SHA-256 so khớp mã PIN, cơ chế đếm số lần nhập sai, khóa cứng 60–300s, hú còi báo động và gửi thông báo khẩn cấp qua Telegram Bot.
- **Slide phù hợp**: **Slide 10 (Phân hệ khóa cửa an ninh và chống tấn công dò mã)**.

### 3.7. Sơ đồ Đồng bộ Cloud và Giao thức dữ liệu
- **Tệp nguồn**: [`BAO_CAO_DU_LIEU/DIAGRAMS/firebase_sync_flow.mmd`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/DIAGRAMS/firebase_sync_flow.mmd)
- **Nội dung**: Luồng đồng bộ trạng thái hai chiều giữa ESP32, Firebase RTDB và các ứng dụng người dùng, cơ chế Optimistic UI và khôi phục khi mất mạng.
- **Slide phù hợp**: **Slide 11 hoặc Slide 12**.

---

## 4. DANH MỤC ẢNH CHỤP GIAO DIỆN ỨNG DỤNG THỰC TẾ (REAL UI SCREENSHOTS)

Tất cả các ảnh chụp màn hình dưới đây được lưu trong thư mục [`BAO_CAO_DU_LIEU/EVIDENCE_IMAGES/`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/EVIDENCE_IMAGES/):

### 4.1. Màn hình chính Ứng dụng Di động Android Native (Home Dashboard)
- **Tệp ảnh**: [`screen_home_final.png`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/EVIDENCE_IMAGES/screen_home_final.png) hoặc [`ldplayer_home_light_final.png`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/EVIDENCE_IMAGES/ldplayer_home_light_final.png)
- **Nội dung**: Hiển thị lời chào cá nhân hóa "Chào buổi tối, Đoàn Xuân Hải! [Master]", trạng thái thiết bị ESP32 Online, bảng đo nhiệt độ/độ ẩm, thẻ phát hiện người thời gian thực, các công tắc điều khiển bật/tắt tức thì Đèn phòng, Quạt, Rèm, Cửa, Đèn sân.
- **Slide phù hợp**: **Slide 12 (Ứng dụng di động Native Android và Giao diện Web Dashboard)**.
- **Trạng thái**: Sử dụng ngay (Ảnh chụp từ ứng dụng thực tế chạy trên Android emulator).

### 4.2. Giao diện Web SPA Dashboard (Máy tính & Di động)
- **Tệp ảnh Desktop**: [`aurora_desktop.png`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/EVIDENCE_IMAGES/aurora_desktop.png) (1920x1080)
- **Tệp ảnh Mobile**: [`aurora_mobile.png`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/EVIDENCE_IMAGES/aurora_mobile.png)
- **Nội dung**: Giao diện Web công nghệ SPA Aurora UI hiện đại, tích hợp bảng điều khiển thiết bị, biểu đồ trực quan cảm biến và bảng nhật ký.
- **Slide phù hợp**: **Slide 1 (Tiêu đề)** và **Slide 12 (Giao diện người dùng)**.
- **Trạng thái**: Sử dụng ngay.

### 4.3. Màn hình Phân quyền Thành viên Gia đình (RBAC) & Mã mời
- **Tệp ảnh**: [`ldplayer_members_screen.png`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/EVIDENCE_IMAGES/ldplayer_members_screen.png) và [`screen_settings_perms.png`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/EVIDENCE_IMAGES/screen_settings_perms.png)
- **Nội dung**: 
  - Phần "Quản lý mã mời & Thành viên": Nút "Tạo mã mời mới (Dùng 1 lần)" kèm mã vừa tạo dạng `HM-XXXXXXXX`.
  - Danh sách thành viên liên kết: Hiển thị 2 thành viên gia đình thực tế (`Doan Hai - haixuan201205@gmail.com` và `Toàn Đoàn Công - doancongtoan712005@gmail.com`).
  - Hộp kiểm phân quyền độc lập 9 quyền: Đèn, Quạt, Rèm, Cửa chính, Đèn sân, Cấu hình tự động, Thêm mật khẩu Keypad, Giọng nói.
  - Các nút hành động: "Chuyển quyền Chủ nhà cho thành viên" và "Hủy liên kết thành viên này".
- **Slide phù hợp**: **Slide 11 (Thiết lập thiết bị BLE và Bảo mật đám mây phân quyền RBAC)**.
- **Trạng thái**: Sử dụng ngay.

### 4.4. Màn hình Quản lý Mật khẩu Khóa cửa (Keypad Passcode Management)
- **Tệp ảnh**: [`ldplayer_verified_v127.png`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/EVIDENCE_IMAGES/ldplayer_verified_v127.png) và [`pass_expanded.png`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/EVIDENCE_IMAGES/pass_expanded.png)
- **Nội dung**: Quản lý danh sách các mã PIN mở cửa của từng thành viên, nút thêm mã PIN mới (4 - 8 chữ số), hiển thị trạng thái bàn phím an toàn / bị khóa cứng.
- **Slide phù hợp**: **Slide 10 (Khóa cửa an ninh)** hoặc **Slide 11**.
- **Trạng thái**: Sử dụng ngay.

### 4.5. Màn hình Nhật ký Hoạt động Thời gian thực (Activity History)
- **Tệp ảnh**: [`history_real_screen.png`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/EVIDENCE_IMAGES/history_real_screen.png) hoặc [`ld_history_tab.png`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/EVIDENCE_IMAGES/ld_history_tab.png)
- **Nội dung**: Danh sách nhật ký sự kiện chi tiết được phân loại theo 4 tab: Tất cả, Giọng nói (kèm nhãn từ khóa và độ tin cậy %), Cửa (kèm định danh người mở), Tự động hóa (kèm tên quy tắc kích hoạt).
- **Slide phù hợp**: **Slide 12 hoặc Slide 13**.
- **Trạng thái**: Sử dụng ngay.

---

## 5. BẢNG DỮ LIỆU CẤU TRÚC VÀ ĐẶC TẢ PHẦN CỨNG (CSV & JSON TABLES)

Các bảng dữ liệu thô chuẩn hóa lưu trong [`BAO_CAO_DU_LIEU/DATA_TABLES/`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/DATA_TABLES/):

1. **Bảng định mức linh kiện (Bill of Materials)**:
   - [`BAO_CAO_DU_LIEU/DATA_TABLES/system_bom.csv`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/DATA_TABLES/system_bom.csv)
   - Chứa danh mục 21 linh kiện, mô-đun, thông số kỹ thuật, số lượng và vai trò chức năng trong hệ thống.
   - *Phù hợp: Slide 5 và Slide Phụ lục 1*.
2. **Bảng ma trận chân GPIO đầy đủ**:
   - [`BAO_CAO_DU_LIEU/DATA_TABLES/gpio_pinout_matrix.csv`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/DATA_TABLES/gpio_pinout_matrix.csv)
   - Chứa thông tin 25 chân GPIO của ESP32-S3, chức năng điện áp, giao thức giao tiếp và cơ chế chống xung đột phần cứng.
   - *Phù hợp: Slide Phụ lục 1*.
3. **Bảng phân bố tập dữ liệu âm thanh 10 nhãn**:
   - [`BAO_CAO_DU_LIEU/DATA_TABLES/dataset_distribution.csv`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/DATA_TABLES/dataset_distribution.csv)
   - Thống kê chi tiết số lượng mẫu Train (13.822) và Test (3.227) cho 10 nhãn khẩu lệnh tiếng Việt.
   - *Phù hợp: Slide 7*.
4. **Bảng phân vùng bộ nhớ Flash 16MB**:
   - [`BAO_CAO_DU_LIEU/DATA_TABLES/partitions_custom.csv`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/DATA_TABLES/partitions_custom.csv)
   - Cấu trúc phân vùng: NVS, Otadata, OTA_0 (4.5MB), OTA_1 (4.5MB), Spiffs (6.8MB).
   - *Phù hợp: Slide Phụ lục 3*.
5. **Lược đồ cấu trúc cơ sở dữ liệu Firebase NoSQL**:
   - [`BAO_CAO_DU_LIEU/DATA_TABLES/firebase_schema.json`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/DATA_TABLES/firebase_schema.json)
   - Đặc tả cấu trúc các node: `system`, `users`, `invites`, `devices`, `commands`, `door`, `logs`, `config`.
   - *Phù hợp: Slide Phụ lục 4*.

---

## 6. DANH MỤC TÀI LIỆU KỸ THUẬT PDF & BÁO CÁO GỐC (DOCUMENTS & SCHEMATICS)

Các tài liệu kỹ thuật hoàn chỉnh định dạng HTML và PDF được lưu trong thư mục [`docs/`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/docs/):
1. **Sơ đồ nguyên lý mạch điện tử**:
   - [`docs/1-so-do-nguyen-ly.pdf`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/docs/1-so-do-nguyen-ly.pdf) (Bản vẽ khổ A4 chuẩn công nghiệp).
2. **Sơ đồ lưu đồ thuật toán hệ thống**:
   - [`docs/2-luu-do-thuat-toan.pdf`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/docs/2-luu-do-thuat-toan.pdf).
3. **Bảng chỉ dẫn đấu nối chân phần cứng**:
   - [`docs/3-chan-ket-noi-phan-cung.pdf`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/docs/3-chan-ket-noi-phan-cung.pdf).
4. **Bảng danh mục linh kiện chi tiết**:
   - [`docs/4-danh-sach-linh-kien.pdf`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/docs/4-danh-sach-linh-kien.pdf).
5. **Tài liệu kỹ thuật tổng hợp toàn diện**:
   - [`docs/Tai-Lieu-Ky-Thuat-Tong-Hop.pdf`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/docs/Tai-Lieu-Ky-Thuat-Tong-Hop.pdf) (Bộ tài liệu thuyết minh kỹ thuật đồ án dài hơn 50 trang).

---

## 7. HƯỚNG DẪN TRÍCH XUẤT VÀ SỬ DỤNG CHO AI TẠO POWERPOINT

1. **Quy tắc bố cục màu sắc slide đề xuất**:
   - Tone màu chủ đạo (Primary Accent): **Cyan / Teal** (`#00C9A7` hoặc `#0ea5e9` - Phù hợp chủ đề IoT Hiện đại & AI Edge).
   - Màu phụ trợ (Secondary Accent): **Indigo / Dark Blue** (`#1e293b` hoặc `#0f172a` cho Header, Nền thẻ kỹ thuật).
   - Màu chỉ thị trạng thái: Xanh lá (`#22c55e` cho Master / Online / Lệnh đúng), Đỏ (`#ef4444` cho Cảnh báo / Khóa cứng / Sai mật khẩu).
2. **Quy tắc về nội dung văn bản**:
   - Mỗi slide chỉ chứa từ **3 đến 5 gạch đầu dòng ngắn gọn**, tập trung vào thông điệp kỹ thuật.
   - Tuyệt đối không dán cả đoạn văn lý thuyết dài dòng lên màn hình. Toàn bộ phần giải thích sâu đã được chuẩn bị sẵn trong mục **Speaker Notes** của tệp `PPT_HANDOFF.md`.
3. **Quy tắc chèn hình ảnh và bằng chứng**:
   - Ưu tiên tối đa: **1 Slide = 1 Sơ đồ kỹ thuật hoặc Biểu đồ số liệu thực chứng lớn ở bên phải / trung tâm**, các ý phân tích ngắn gọn ở bên trái.
   - Nhấp vào các liên kết file trong danh mục trên để mở tệp tương ứng trong workspace và đưa vào slide.

---
*Tài liệu PPT_ASSETS.md được tổng hợp ngày 23/11/2026 đồng bộ với bộ hồ sơ PPT_HANDOFF.md.*
