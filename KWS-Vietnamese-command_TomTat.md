# Tóm Tắt Dự Án: Hệ Thống Điều Khiển Thiết Bị Bằng Giọng Nói Offline

## 1. Tổng Quan

Đây là đồ án xây dựng **hệ thống điều khiển thiết bị nhà thông minh (đèn, quạt) bằng lệnh giọng nói tiếng Việt**, chạy hoàn toàn **offline trên vi điều khiển**. Hệ thống có thêm tính năng **giám sát và điều khiển từ xa qua Firebase**, cho phép app di động theo dõi và gửi lệnh qua mạng.

---

## 2. Phần Cứng

| Thành phần | Chi tiết |
|---|---|
| **Vi điều khiển** | ESP32-S3 N16R8 (16MB Flash, 8MB PSRAM OPI) |
| **Microphone** | INMP441 (I2S, kết nối qua chân SCK=6, WS=5, DIN=4) |
| **LED trạng thái** | LED RGB (chân R=17, G=18, B=8) |
| **Relay** | 2 relay (chân 41, 42) — điều khiển đèn và quạt |
| **Framework** | Arduino trên PlatformIO |

---

## 3. Model AI — Keyword Spotting (KWS)

### 3.1 Thu Thập Dữ Liệu

Dữ liệu âm thanh được tự thu thập và gán nhãn cho **7 class**:

| Index | Label | Ý Nghĩa |
|---|---|---|
| 0 | `bat_den` | Bật đèn |
| 1 | `bat_het` | Bật hết (bật cả đèn lẫn quạt) |
| 2 | `bat_quat` | Bật quạt |
| 3 | `noise` | Tiếng ồn nền / không phải lệnh |
| 4 | `tat_den` | Tắt đèn |
| 5 | `tat_het` | Tắt hết (tắt cả đèn lẫn quạt) |
| 6 | `tat_quat` | Tắt quạt |

### 3.2 Huấn Luyện

- **Nền tảng:** [Edge Impulse](https://edgeimpulse.com) — project tên `KWS-Vietnamese-command`, owner `nguyenthao01`, deploy version 21
- **Đầu vào model:** 16.000 mẫu PCM 16-bit (tương đương 1 giây âm thanh @ 16kHz)
- **Tiền xử lý:** MFCC (Mel-frequency cepstral coefficients) — do Edge Impulse SDK thực hiện
- **Engine inference:** TensorFlow Lite Micro (INT8 quantized)
- **Arena size:** 8.876 bytes (rất nhỏ, chạy được trên SRAM)
- **Performance Calibration:** Threshold = 0.3166, Suppression = 864ms (tự động lọc kết quả)

---

## 4. Kiến Trúc Phần Mềm

### 4.1 Cấu Trúc Thư Mục

```
src/
├── main.cpp              — Entry point, callback xử lý kết quả inference
├── audio_capture.cpp     — Thu âm I2S, ring buffer, VAD, sliding window
├── bandpass_filter.cpp   — Bộ lọc IIR Biquad thông dải 300–3400Hz
├── noise_reduction.cpp   — Giảm nhiễu nền bằng Spectral Subtraction
├── inference.cpp         — Chạy model Edge Impulse
├── led_relay.cpp         — Điều khiển LED RGB và relay
└── firebase_manager.cpp  — Kết nối WiFi và Firebase RTDB

include/
├── config.h              — Tất cả hằng số cấu hình (pin, tham số âm thanh, Firebase)
└── ...

lib/
└── KWS-Vietnamese-command_inferencing/  — Thư viện model xuất từ Edge Impulse
    ├── src/model-parameters/            — Metadata, variables của model
    └── src/edge-impulse-sdk/            — SDK: TFLite Micro, DSP (MFCC, FFT)
```

### 4.2 Pipeline Xử Lý Âm Thanh

```
[INMP441 Microphone]
        │ I2S, 32-bit raw, 16kHz
        ▼
[audio_capture_frame()] — mỗi 20ms (320 mẫu)
        │ >> 11 bit → int16
        │ Bộ lọc Bandpass 300–3400Hz (IIR Biquad 2 tầng)
        │ Spectral Subtraction (giảm nhiễu nền)
        ▼
[Ring Buffer] — 32.000 mẫu = 2 giây
        │
        ▼
[VAD — Voice Activity Detection]
  - Tính năng lượng RMS² của frame 20ms
  - Ngưỡng: VAD_ENERGY_THRESH = 10.000.000
  - Nếu im lặng > 500ms → trở về IDLE
        │ Có giọng nói
        ▼
[State: LISTENING] — Tích lũy hop_counter
  - Mỗi 200ms (HOP_SAMPLES = 3.200 mẫu):
        │
        ▼
[Sliding Window] — Cắt 16.000 mẫu (1 giây) từ ring buffer
        │
        ▼
[on_window_ready() callback]
        │
        ▼
[inference_run()] — chạy trên window 1 giây
  - int16 → float
  - MFCC (Edge Impulse DSP)
  - TFLite Micro inference (INT8)
  - Performance Calibration (lọc thêm)
        │ label + confidence
        ▼
[execute_command()] — Điều khiển relay + LED
        │
        ▼
[firebase_update()] — Gửi trạng thái lên Firebase RTDB
```

### 4.3 Cơ Chế Sliding Window

| Tham số | Giá trị | Ý nghĩa |
|---|---|---|
| `WINDOW_MS` | 1000ms | Độ dài cửa sổ âm thanh gửi vào model |
| `WINDOW_SAMPLES` | 16.000 mẫu | = SAMPLE_RATE × WINDOW_MS / 1000 |
| `HOP_MS` | 200ms | Bước nhảy giữa các window liên tiếp |
| `HOP_SAMPLES` | 3.200 mẫu | Overlap 800ms giữa các window |
| `VAD_FRAME_MS` | 20ms | Độ dài mỗi frame để tính VAD |
| `RING_BUF_SECONDS` | 2 giây | Kích thước ring buffer (32.000 mẫu) |

---

## 5. Các Module Chi Tiết

### 5.1 audio_capture.cpp

**Chức năng chính:**
- Khởi tạo I2S với cấu hình: Master/RX, 32-bit, kênh trái, 8 DMA buffer × 256 frame
- Đọc liên tục từ mic INMP441, dịch bit sang int16 (>> 11 bit)
- Áp dụng bộ lọc bandpass → spectral subtraction → ghi vào ring buffer vòng tròn
- State machine IDLE/LISTENING dựa trên VAD năng lượng
- Khi LISTENING: đếm hop, mỗi 200ms cắt window 1 giây và gọi callback

### 5.2 bandpass_filter.cpp

- **Kiểu lọc:** IIR Biquad 2 tầng nối tiếp (Direct Form II Transposed)
- **Dải thông:** 300Hz (High-pass) — 3400Hz (Low-pass) @ 16kHz
- **Lý do:** Dải tần giọng nói chuẩn, loại DC offset và nhiễu cao tần
- **Hiệu quả:** 5 phép nhân + 4 phép cộng mỗi mẫu (phù hợp với FPU của ESP32-S3)

### 5.3 noise_reduction.cpp

- **Phương pháp:** Spectral Subtraction
- **Cách dùng:** Khi IDLE → cập nhật noise profile; luôn áp dụng để trừ nền nhiễu
- Xử lý in-place trên frame trước khi đưa vào ring buffer

### 5.4 inference.cpp

- Import thư viện `KWS-Vietnamese-command_inferencing.h` (xuất từ Edge Impulse)
- `inference_init()`: Gọi `run_classifier_init()` để kích hoạt Performance Calibration
- `inference_run()`:
  1. Đặt con trỏ window vào biến static để callback DSP truy cập
  2. Tạo `signal_t` cho Edge Impulse với `get_data` callback (int16 → float)
  3. Gọi `run_classifier()` — tự động thực hiện MFCC + TFLite inference + Post-processing
  4. Tìm class có confidence cao nhất
  5. Lọc: bỏ qua nếu là `LABEL_NOISE` hoặc confidence < 0.01
- **Static assert:** Đảm bảo `WINDOW_SAMPLES == EI_CLASSIFIER_RAW_SAMPLE_COUNT` lúc compile

### 5.5 led_relay.cpp

Điều khiển LED RGB và relay theo trạng thái hệ thống:

| Màu LED | Trạng thái |
|---|---|
| ⚪ Trắng mờ | IDLE — chờ tiếng nói |
| 🟢 Xanh lá | VAD phát hiện giọng nói, đang lắng nghe |
| 🔵 Xanh dương | Đang thực thi lệnh |
| 🔴 Đỏ nhấp nháy | Lỗi (I2S, inference…) |

Hàm `execute_command(label, confidence)` cập nhật relay và LED đồng thời.

### 5.6 firebase_manager.cpp

**Kiến trúc bất đồng bộ với FreeRTOS:**
- `firebase_task` chạy trên **Core 0**, queue depth = 4 message
- Main loop (Core 1) chỉ `xQueueSend()` — không bao giờ block

**Tính năng:**
- Ghi trạng thái relay và lịch sử lệnh lên Firebase RTDB
- Stream lắng nghe path `/devices/esp32-voice/remote_cmd` → nhận lệnh từ xa (app di động)
- Heartbeat mỗi 30 giây (ghi `last_seen` bằng server timestamp)
- Tự động reconnect WiFi (thử lại mỗi 5 giây), reinit Firebase sau khi WiFi khôi phục
- Persistent SSL connection (giảm latency từ ~2s xuống ~100ms mỗi lần ghi)
- Ghi log chi tiết từng lệnh tại path `/devices/esp32-voice/logs/{timestamp}`

**Cấu trúc dữ liệu trên Firebase RTDB:**
```
/devices/esp32-voice/
  ├── relay1         : bool
  ├── relay2         : bool
  ├── last_command   : string (tên lệnh)
  ├── last_seen      : timestamp (server)
  ├── remote_cmd     : string (lệnh từ app, tự xóa sau khi thực hiện)
  └── logs/
      └── {millis}   : { command, source, relay1, relay2, ts }
```

---

## 6. Luồng Điều Khiển Chính (main.cpp)

```
setup():
  → psramInit()
  → led_relay_init()
  → audio_init()        — I2S + ring buffer + bộ lọc
  → inference_init()    — TFLite Micro + Performance Calibration
  → wifi_init()
  → firebase_init()     — Kết nối RTDB + bắt đầu stream
  → audio_set_window_callback(on_window_ready)

loop() — mỗi ~20ms:
  → wifi_loop()         — theo dõi kết nối WiFi
  → firebase_loop()     — gửi heartbeat định kỳ
  → audio_sliding_window_loop()   — đọc 1 frame, VAD, cập nhật state
  → Cập nhật LED nếu trạng thái thay đổi
```

**Callback `on_window_ready(window, index)`** (gọi từ audio_capture khi có window mới):
1. Kiểm tra cooldown 1000ms (tránh lệnh liên tiếp quá nhanh)
2. Gọi `inference_run()` → nhận `label` + `confidence`
3. Nếu hợp lệ: cập nhật `s_relay1/s_relay2` → `execute_command()` → `firebase_update()`
4. Khôi phục LED trạng thái LISTENING sau khi thực thi

---

## 7. Cấu Hình Quan Trọng (config.h)

| Nhóm | Tham số | Giá trị |
|---|---|---|
| I2S | `SAMPLE_RATE` | 16.000 Hz |
| I2S | `I2S_SHIFT_BITS` | 11 (chuyển 32-bit raw → 16-bit) |
| VAD | `VAD_ENERGY_THRESH` | 10.000.000 (RMS²) |
| VAD | `VAD_HANG_OVER_MS` | 500ms im lặng trước khi tắt |
| Window | `WINDOW_MS / HOP_MS` | 1000ms / 200ms |
| Cooldown | `COMMAND_COOLDOWN_MS` | 1000ms |
| WiFi | `WIFI_RECONNECT_MS` | 5000ms |
| Firebase | `FB_HEARTBEAT_MS` | 30.000ms |

---

## 8. Build & Triển Khai

- **IDE:** PlatformIO (VS Code)
- **Target board:** `esp32-s3-devkitc-1`
- **Platform:** `espressif32 @ ^7.0.0`
- **Memory type:** `qio_opi` (flash QIO + PSRAM OPI)
- **Partition:** Custom (`partitions_custom.csv`) — cần cho firmware lớn
- **Thư viện ngoài:** `mobizt/Firebase ESP32 Client @ ^4.4.0`
- **Build flags quan trọng:**
  - `-DBOARD_HAS_PSRAM` — khai báo PSRAM
  - `-DCONFIG_ARDUINO_LOOP_STACK_SIZE=65536` — tăng stack cho inference
  - `-O2` — tối ưu tốc độ

---

## 9. Điểm Nổi Bật Kỹ Thuật

1. **Chạy offline hoàn toàn** — model TFLite Micro nhúng trực tiếp vào firmware, không cần server
2. **Pipeline âm thanh đầy đủ** — bandpass filter → spectral subtraction → VAD → sliding window → MFCC → inference
3. **FreeRTOS đúng cách** — Firebase chạy trên Core 0, audio + inference trên Core 1, giao tiếp qua queue
4. **Sliding window với overlap** — giúp bắt lệnh ngay cả khi người dùng nói không đúng thời điểm hop
5. **Dual mode** — điều khiển bằng giọng nói local VÀ điều khiển từ xa qua app (Firebase)
6. **Persistent SSL connection** — tái dùng socket SSL để giảm latency Firebase từ ~2s → ~100ms

---

## 10. Hạn Chế & Hướng Phát Triển

- Mô hình chỉ nhận 7 lớp lệnh cố định (tiếng Việt)
- Cần điều chỉnh `VAD_ENERGY_THRESH` theo môi trường âm thanh thực tế
- Firebase authentication dùng Legacy Token (cần nâng cấp lên Service Account cho production)
- Chưa có cơ chế OTA (Over-the-Air update) firmware
