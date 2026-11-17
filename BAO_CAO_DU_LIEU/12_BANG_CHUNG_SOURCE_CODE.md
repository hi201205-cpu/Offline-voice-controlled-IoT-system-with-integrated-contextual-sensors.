# BẢNG TRUY VẾT MÃ NGUỒN VÀ BẰNG CHỨNG KỸ THUẬT (TRACEABILITY MATRIX)

Bảng dưới đây thiết lập mối liên kết chính xác 1-1 giữa từng khẳng định kỹ thuật, chức năng hệ thống với đường dẫn tệp tin và số dòng mã nguồn tương ứng trong repository:

| STT | Khẳng định kỹ thuật / Tính năng | Vị trí tệp mã nguồn | Dòng mã (Line) | Bằng chứng mã nguồn cụ thể |
|:---:|:---|:---|:---:|:---|
| 1 | Cấu hình phần cứng ESP32-S3 N16R8, PSRAM Octal | `platformio.ini` | Lines 12-25 | `board = esp32-s3-devkitc-1`, `board_build.arduino.memory_type = qio_opi`, `build_flags = -DBOARD_HAS_PSRAM` |
| 2 | Bảng phân vùng Flash 16MB tùy chỉnh | `partitions_custom.csv` | Lines 1-8 | `app0, app, ota_0, 0x10000, 0x700000` (7MB), `app1` (7MB), `spiffs` (~1.875MB) |
| 3 | Định nghĩa 25 chân GPIO phần cứng | `include/config.h` | Lines 17-71 | `I2S_SCK_PIN = 6`, `I2S_WS_PIN = 5`, `I2S_SD_PIN = 4`, `RELAY_LIGHT = 41`, `DHTPIN = 16`, `BUZZER_PIN = 48` |
| 4 | Thu âm số qua I2S DMA Ring Buffer | `src/audio_provider.cpp` | Lines 25-85 | Cấu hình `i2s_config_t`, `i2s_pin_config_t`, khởi tạo `i2s_driver_install()` |
| 5 | Lọc năng lượng âm thanh VAD (Ngưỡng 7.000.000) | `src/main.cpp` | Lines 88-105 | Tính tổng bình phương biên độ mẫu âm thanh: `if (energy > 7000000) { ... }` |
| 6 | Suy luận liên tục Edge Impulse KWS | `src/main.cpp` | Lines 120-165 | Gọi hàm `run_classifier_continuous(&signal, &result)` |
| 7 | Kiến trúc mạng CNN INT8 và 8 Toán tử | `lib/KWS-Vietnamese-command_inferencing/src/tflite-learners/default_model_compiled.cpp` | Lines 45-120 | Khởi tạo mảng toán tử: `OP_RESHAPE`, `OP_CONV_2D`, `OP_MAX_POOL_2D`, `OP_FULLY_CONNECTED`, `OP_SOFTMAX` |
| 8 | Dung lượng Tensor Arena đúng 12.006 Bytes | `lib/KWS-Vietnamese-command_inferencing/src/model-parameters/model_variables.h` | Lines 30-45 | Khai báo hằng số `EI_CLASSIFIER_ALLOCATION_STATIC` và kích thước bộ nhớ đệm suy luận |
| 9 | Danh sách 10 nhãn khẩu lệnh Tiếng Việt | `lib/KWS-Vietnamese-command_inferencing/src/model-parameters/model_metadata.h` | Lines 20-35 | `ei_classifier_inferencing_categories[] = { "bat_den", "dong_vao", ..., "xin_chao" }` |
| 10 | Phân bổ đa nhân FreeRTOS (Core 1 & Core 0) | `src/main.cpp` | Lines 180-210 | `xTaskCreatePinnedToCore(firebaseTask, "fbTask", 8192, NULL, 1, &fbTaskHandle, 0)` |
| 11 | Hàng đợi liên luồng truyền sự kiện không nghẽn | `src/main.cpp` | Lines 60-75 | Khai báo `QueueHandle_t s_fbQueue = xQueueCreate(10, sizeof(EventMsg_t))` |
| 12 | Kết nối Stream WebSocket Firebase RTDB | `src/firebase_handle.cpp` | Lines 45-110 | Khởi tạo `Firebase.RTDB.beginStream(&stream, "/commands")` trên Core 0 |
| 13 | Cơ chế băm mật khẩu Salted SHA-256 | `src/door_controller.cpp` | Lines 70-115 | Đọc chuỗi muối từ NVS, tính `mbedtls_sha256()`, so sánh hash chuỗi PIN |
| 14 | Khóa cứng 120 giây và còi báo động khi sai 4 lần | `src/door_controller.cpp` | Lines 130-175 | Đếm `wrong_attempts >= 4`, kích hoạt còi `BUZZER_PIN`, gửi `keypad_locked = true` |
| 15 | Động cơ tự động hóa giải quyết xung đột An toàn | `src/automation_engine.cpp` | Lines 85-140 | Lệnh `OFF` / `CLOSE` luôn ghi đè lệnh `ON` / `OPEN` trên cùng một thiết bị chấp hành |
| 16 | Phân quyền bảo mật Firebase Rules Master/Member | `database.rules.json` | Lines 1-95 | Phân quyền chi tiết cho `xhai2005@gmail.com` và ràng buộc `pairedHome === 'HOME-2026-ESP32'` |
| 17 | Giao diện Web SPA responsive (5.506 dòng code) | `web/index.html` | Lines 1-5506 | Triển khai Dashboard, Automation Rules, Passcode Manager, Toast giọng nói |
| 18 | Phân tích 748 log lệnh giọng nói thực tế | `analyze_logs.py` & `reports/BAO_CAO_PHAN_TICH_DU_LIEU.md` | Lines 1-399 | Kết nối Firebase REST API, trích xuất DataFrame, phân tích tương quan và vẽ đồ thị |
