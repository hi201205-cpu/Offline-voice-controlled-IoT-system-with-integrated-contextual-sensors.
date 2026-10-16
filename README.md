# KWS Vietnamese Command — ESP32-S3

Hệ thống điều khiển thiết bị nhà thông minh (đèn, quạt) bằng **lệnh giọng nói tiếng Việt**, chạy **offline** trên ESP32-S3. Hỗ trợ thêm giám sát và điều khiển từ xa qua Firebase RTDB.

## Tính năng

- Nhận diện 6 lệnh tiếng Việt hoàn toàn offline (không cần internet)
- Điều khiển 3 relay độc lập (đèn phòng + quạt + đèn sân)
- VAD (Voice Activity Detection) tự động bật/tắt lắng nghe
- Đồng bộ trạng thái relay lên Firebase Realtime Database
- Nhận lệnh điều khiển từ xa qua Firebase (app di động)
- LED RGB phản ánh trạng thái hệ thống theo thời gian thực

## Lệnh hỗ trợ

| Lệnh | Hành động |
|---|---|
| "bật đèn" | Bật relay 1 (đèn) |
| "tắt đèn" | Tắt relay 1 (đèn) |
| "bật quạt" | Bật relay 2 (quạt) |
| "tắt quạt" | Tắt relay 2 (quạt) |
| "bật hết" | Bật cả đèn lẫn quạt |
| "tắt hết" | Tắt cả đèn lẫn quạt |

## Phần cứng

| Linh kiện | Mô tả |
|---|---|
| ESP32-S3 N16R8 | Vi điều khiển chính (16MB Flash, 8MB PSRAM) |
| INMP441 | Microphone I2S (SCK=6, WS=5, DIN=4) |
| LED RGB | Chỉ thị trạng thái (R=17, G=18, B=8) |
| Relay × 3 | Đèn phòng GPIO41, quạt GPIO42, đèn sân GPIO40 (active-low) |
| DHT11 | DATA GPIO16 |
| LD2410S | Giữ UART1: RX GPIO10, TX GPIO11 |
| Servo rèm / cửa | GPIO9 / GPIO39 |
| Keypad 3x4 | GPIO13, 14, 15, 21 (hàng); GPIO7, 12, 2 (cột) |
| Nút trong nhà | GPIO47, nút nối GPIO47 xuống GND |
| LDR analog | AO GPIO1 (ADC1) |

### Bảng chân GPIO và đấu dây

| Thiết bị | Tín hiệu | GPIO |
|---|---|---:|
| INMP441 | SCK/BCLK, WS/LRCLK, SD/DOUT | 6, 5, 4 |
| Relay 1 / 2 / 3 | IN1 / IN2 / IN3 | 41, 42, 40 |
| Servo rèm / cửa | Signal | 9, 39 |
| LED RGB trạng thái | R / G / B | 17, 18, 8 |
| Keypad | R1 / R2 / R3 / R4 | 13 / 14 / 15 / 21 |
| Keypad | C1 / C2 / C3 | 7 / 12 / 2 |
| DHT11 | DATA | 16 |
| LD2410S | UART1 RX / TX | 10 / 11 |
| Nút trong nhà | Một đầu nút; đầu kia GND | 47 |
| LDR | AO | 1 |

Keypad được quét trực tiếp, **không cần PCF8574**. Nối chung GND giữa ESP32,
cảm biến, relay và nguồn servo. Cấp servo từ nguồn 5V riêng đủ dòng (không lấy
dòng servo trực tiếp từ chân 3V3); giữ chung GND với ESP32. INMP441, DHT11,
LD2410S và ngõ AO phải dùng mức logic phù hợp 3.3V. LDR AO dùng cầu phân áp;
điều chỉnh `RAW_DARK` và `RAW_BRIGHT` trong `include/config.h` theo chiều và
dải ADC thực tế của module.

### Kiểm thử phần mở rộng

1. Che LDR liên tục ít nhất 30 giây: khi `light_level < dark_below`, relay đèn
   sân bật; chiếu sáng lại để vượt `bright_above` liên tục 30 giây thì relay tắt.
2. Khi presence đang `present`, để phòng trống: trạng thái lần lượt thành
   `leaving`, rồi `absent` sau `no_person_minutes`; đèn phòng và quạt đang bật
   sẽ tắt nếu không trong thời gian giữ quyền thủ công.
3. Web gửi lệnh thiết bị tại `/commands/light`, `/commands/fan` và
   `/commands/curtain` với payload `{value, id}` hiện có; firmware cũng tiếp
   nhận payload scalar cũ. Thiết bị báo trạng thái thực tế tại `/devices/state`;
   giao diện chờ actual khớp desired trước khi kết thúc trạng thái chờ.
   Firmware giữ stream tương thích cho app cũ gửi chuỗi lệnh tại
   `/devices/esp32-voice/remote_cmd`.
4. Gửi lệnh cửa tại `/commands/door` dạng `"close"` hoặc JSON
   `{"action":"open","ts":<epoch_milliseconds>,"src":"app"}`. Web có nút mở
   cửa và theo dõi trạng thái thực tại `/devices/state/door`; cửa tự đóng sau
   5 giây khi được mở từ web, keypad hoặc nút mở cửa bên trong. Servo cửa quay
   liên tục được chạy hai vòng theo thời gian cấu hình `DOOR_SERVO_TURN_MS`
   (mặc định 1440 ms, cần hiệu chỉnh theo servo/cơ cấu thực tế), rồi dừng bằng xung trung
   tính `DOOR_SERVO_STOP_US`. Servo rèm quay liên tục theo cùng cách: lệnh mở
   và đóng quay một vòng theo hai chiều ngược nhau, theo thời gian
   `CURTAIN_SERVO_TURN_MS`, rồi dừng bằng `CURTAIN_SERVO_STOP_US`. Trạng thái
   Firebase chỉ đổi sau khi chuyển động hoàn tất.
5. Thêm mã bằng `/door/pending/<id> = {"op":"add","name":"...","code":"<4-8 digits>"}`,
   rồi xóa bằng `{"op":"delete","target":"<id>"}`. Mã chỉ được lưu dạng băm có
   salt trong NVS; không xem hoặc ghi mã vào Serial. Đặt mã mặc định lần đầu
   trong `DOOR_PASSWORD` của `include/config.h`.

## Cài đặt

### 1. Yêu cầu

- [PlatformIO](https://platformio.org/) (VS Code extension hoặc CLI)
- Python 3.x

### 2. Clone repository

```bash
git clone https://github.com/YOUR_USERNAME/Firmware-KWS-vietnamese-command.git
cd Firmware-KWS-vietnamese-command
```

### 3. Tạo file cấu hình

```bash
cp include/config.example.h include/config.h
```

Mở `include/config.h` và điền thông tin của bạn:

```c
#define WIFI_SSID       "tên_wifi_của_bạn"
#define WIFI_PASSWORD   "mật_khẩu_wifi"
#define FIREBASE_HOST "do-an-2-f791a-default-rtdb.asia-southeast1.firebasedatabase.app"
#define FIREBASE_AUTH   "your_firebase_database_secret"
```

> **Lưu ý:** `config.h` đã được thêm vào `.gitignore` — file này sẽ không bao giờ được commit lên repository.

### 4. Lấy Firebase credentials

1. Vào [Firebase Console](https://console.firebase.google.com/)
2. Chọn project → **Project Settings** → tab **Service accounts**
3. Kéo xuống phần **Database secrets** → copy secret token
4. Vào **Realtime Database** → copy URL database

### 5. Build & Upload

```bash
# Build
pio run

# Upload firmware
pio run --target upload

# Mở Serial Monitor
pio device monitor
```

## Cấu trúc thư mục

```
├── include/
│   ├── config.example.h    ← File mẫu (commit lên git)
│   ├── config.h            ← File thật (KHÔNG commit — trong .gitignore)
│   ├── audio_capture.h
│   ├── bandpass_filter.h
│   ├── noise_reduction.h
│   ├── inference.h
│   ├── led_relay.h
│   └── firebase_manager.h
├── src/
│   ├── main.cpp
│   ├── audio_capture.cpp
│   ├── bandpass_filter.cpp
│   ├── noise_reduction.cpp
│   ├── inference.cpp
│   ├── led_relay.cpp
│   └── firebase_manager.cpp
├── lib/
│   └── KWS-Vietnamese-command_inferencing/   ← Model xuất từ Edge Impulse
├── platformio.ini
└── README.md
```

## Pipeline xử lý âm thanh

```
[Mic INMP441] → I2S 32-bit → Bandpass 300–3400Hz → Spectral Subtraction
    → Ring Buffer 2s → VAD (RMS energy) → Sliding Window 1s/hop 200ms
    → MFCC (Edge Impulse DSP) → TFLite Micro INT8 → Label + Confidence
    → Relay control + Firebase update
```

## Model AI

- **Nền tảng:** [Edge Impulse](https://edgeimpulse.com)
- **Kiến trúc:** TensorFlow Lite Micro (INT8 quantized)
- **Input:** 16.000 mẫu PCM 16-bit (1 giây @ 16kHz)
- **DSP:** MFCC
- **Classes:** 7 (bat_den, tat_den, bat_quat, tat_quat, bat_het, tat_het, noise)

## Tự động hóa linh hoạt (Automation Rule Engine)

Firmware thực thi tự động hóa độc lập dựa 100% trên các quy tắc cấu hình từ giao diện Web thông qua Firebase RTDB:
- `/automation/enabled`: Công tắc tổng (Master switch). Khi tắt, không quy tắc nào được thực thi.
- `/automation/rules`: Danh sách các quy tắc (tối đa 20 quy tắc lưu trong RAM).
- `/automation/pending/<ruleId>`: Hàng đợi xác nhận cho chế độ "Hỏi tôi trước". Chờ người dùng phản hồi `agree` (đồng ý thực hiện), `skip` (bỏ qua), hoặc tự động xử lý khi hết thời gian chờ theo `timeout` (`auto` hoặc `none`).
- `/automation/status/<ruleId>`: Báo cáo trạng thái quy tắc (`waiting`, `pending_confirm`, `ran`), thời điểm và kết quả chạy gần nhất.

### Xử lý xung đột quy tắc (Deterministic Conflict Priority)
Nếu hai quy tắc cùng lúc thỏa mãn điều kiện và điều khiển cùng một thiết bị sang hai trạng thái trái ngược nhau trong cùng một chu kỳ quét:
1. **Ưu tiên An Toàn (Safety First):** Lệnh **TẮT / ĐÓNG** (`off`/`close`) luôn được ưu tiên hơn lệnh **BẬT / MỞ** (`on`/`open`) để đảm bảo an toàn thiết bị và tiết kiệm điện năng.
2. **Ưu tiên Thứ tự Quy tắc:** Nếu cùng trạng thái hoặc độ an toàn tương đương, quy tắc đứng trước trong danh sách (chỉ số nhỏ hơn) được ưu tiên thực thi.
3. Toàn bộ xung đột đều được ghi log chi tiết ra Serial Monitor.

## License

MIT
