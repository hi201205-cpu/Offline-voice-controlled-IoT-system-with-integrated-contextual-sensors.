#pragma once
// ============================================================
//  config.example.h
//
//  ĐÂY LÀ FILE MẪU — KHÔNG CHỨA THÔNG TIN THẬT
//
//  Cách dùng:
//    1. Copy file này thành include/config.h
//    2. Điền WiFi SSID, password và Firebase credentials của bạn
//    3. File include/config.h đã được .gitignore — không bao giờ
//       bị commit lên repository
// ============================================================

// ╔══════════════════════════════════════════════════════════╗
// ║  1. CHÂN GPIO                                           ║
// ╚══════════════════════════════════════════════════════════╝

#define PIN_I2S_SCK     6
#define PIN_I2S_WS      5
#define PIN_I2S_DIN     4

#define PIN_LED_R      17
#define PIN_LED_G      18
#define PIN_LED_B       8

// GPIO plan for the ESP32-S3-N16R8.
#define PIN_RELAY_3    40
#define PIN_DOOR_SERVO 39
// NC limit switches connect each GPIO to GND; INPUT_PULLUP reads HIGH at limit.
#define PIN_DOOR_LIMIT_OPEN 38
#define PIN_DOOR_LIMIT_CLOSED 3
#define PIN_GARDEN_LIGHT 1
#define PIN_DOOR_BUTTON 47
// Cấu hình mức tích cực nút bấm mở cửa GPIO 47:
// - Module nút bấm 3 chân (VCC, GND, OUT/SIG) thông dụng: OUT = LOW khi nhả, HIGH khi bấm -> chọn HIGH (1)
// - Nút nhấn cơ 2 chân nối GND (INPUT_PULLUP): OUT = HIGH khi nhả, LOW khi bấm -> chọn LOW (0)
#define DOOR_BUTTON_ACTIVE_LEVEL HIGH
#define PIN_BUZZER      48
#define PIN_KEYPAD_ROW_1 13
#define PIN_KEYPAD_ROW_2 14
#define PIN_KEYPAD_ROW_3 15
#define PIN_KEYPAD_ROW_4 21
#define PIN_KEYPAD_COL_1 7
#define PIN_KEYPAD_COL_2 12
#define PIN_KEYPAD_COL_3 2

#define PIN_DHT        16
#define PIN_SR501      10  // Chân OUT cảm biến PIR HC-SR501 (mức HIGH khi có người, LOW khi không có)
#define PIN_LD2410S_RX 10
#define PIN_LD2410S_TX 11

#define PIN_RELAY_1    41
#define PIN_RELAY_2    42
#define RELAY_ON       HIGH
#define RELAY_OFF      LOW
#define PIN_CURTAIN_SERVO 9
#define CURTAIN_SERVO_STOP_US 1500
#define CURTAIN_SERVO_OPEN_US 1000
#define CURTAIN_SERVO_CLOSE_US 2000
#define CURTAIN_SERVO_TURN_MS 720UL

#define ENABLE_SR501         1
#define ENABLE_LD2410S       0
#define LD2410S_RX_PIN      PIN_LD2410S_RX
#define LD2410S_TX_PIN      PIN_LD2410S_TX
#define LD2410S_BAUD_RATE   115200

// LDR calibration values depend on the module/divider orientation.
#define RAW_DARK             3500
#define RAW_BRIGHT           500

#define GARDEN_DEFAULT_MODE          "auto"
#define GARDEN_DEFAULT_DARK_BELOW    25
#define GARDEN_DEFAULT_BRIGHT_ABOVE  40
#define GARDEN_SAMPLE_INTERVAL_MS    1000UL
#define GARDEN_FILTER_SAMPLES        10
#define GARDEN_CONFIRM_MS            30000UL

#define AUTO_DEFAULT_ENABLED         true
#define AUTO_DEFAULT_FAN_TEMP_ON     30.0f
#define AUTO_DEFAULT_NO_PERSON_MIN   2
#define AUTO_CONFIG_REFRESH_MS       30000UL
#define MANUAL_HOLD_MS               600000UL

#define DOOR_PASSWORD "1234"
#define DOOR_MAX_PASSCODES 10
#define DOOR_MAX_WRONG_ATTEMPTS 4
#define DOOR_LOCKOUT_MS 60000UL
#define DOOR_SERVO_STOP_US 1500
#define DOOR_SERVO_OPEN_US 2000
#define DOOR_SERVO_CLOSE_US 1000
#define DOOR_SERVO_TURN_MS 1440UL

// ╔══════════════════════════════════════════════════════════╗
// ║  2. CẤU HÌNH I2S                                        ║
// ╚══════════════════════════════════════════════════════════╝

#define I2S_PORT        I2S_NUM_0
#define SAMPLE_RATE     16000
#define I2S_SHIFT_BITS  11            // 32-bit raw → 16-bit: >> 11
#define DMA_BUF_COUNT   8
#define DMA_BUF_LEN     256

// ╔══════════════════════════════════════════════════════════╗
// ║  3. RING BUFFER                                         ║
// ╚══════════════════════════════════════════════════════════╝

#define RING_BUF_SECONDS    2
#define RING_BUF_SAMPLES    (SAMPLE_RATE * RING_BUF_SECONDS)      // 32 000

// ╔══════════════════════════════════════════════════════════╗
// ║  4. VAD (Voice Activity Detection)                      ║
// ╚══════════════════════════════════════════════════════════╝

#define VAD_FRAME_MS                20
#define VAD_FRAME_SAMPLES           (SAMPLE_RATE * VAD_FRAME_MS / 1000)   // 320
#define VAD_ENERGY_THRESH           10000000L
#define VAD_HANG_OVER_MS            500
#define VAD_SILENCE_FRAMES_THRESHOLD  (VAD_HANG_OVER_MS / VAD_FRAME_MS)  // 40 frame

// ╔══════════════════════════════════════════════════════════╗
// ║  5. SLIDING WINDOW                                      ║
// ╚══════════════════════════════════════════════════════════╝

#define WINDOW_MS           1000
#define WINDOW_SAMPLES      (SAMPLE_RATE * WINDOW_MS / 1000)       // 16 000
#define HOP_MS              200
#define HOP_SAMPLES         (SAMPLE_RATE * HOP_MS / 1000)          // 3200

// ╔══════════════════════════════════════════════════════════╗
// ║  6. NHÃN LỆNH                                           ║
// ║  Thứ tự phải khớp với label trong project Edge Impulse  ║
// ╚══════════════════════════════════════════════════════════╝

#define LABEL_BAT_DEN      0
#define LABEL_BAT_HET      1
#define LABEL_BAT_QUAT     2
#define LABEL_NOISE        3
#define LABEL_TAT_DEN      4
#define LABEL_TAT_HET      5
#define LABEL_TAT_QUAT     6

// ╔══════════════════════════════════════════════════════════╗
// ║  7. NGƯỠNG INFERENCE & COOLDOWN                         ║
// ╚══════════════════════════════════════════════════════════╝

#define COMMAND_COOLDOWN_MS 1000

// ╔══════════════════════════════════════════════════════════╗
// ║  8. FIREBASE & WIFI  ← THAY BẰNG THÔNG TIN THẬT CỦA BẠN ║
// ╚══════════════════════════════════════════════════════════╝

#define WIFI_SSID           "YOUR_WIFI_SSID"
#define WIFI_PASSWORD       "YOUR_WIFI_PASSWORD"
#define WIFI_TIMEOUT_MS     20000
#define WIFI_RECONNECT_MS   5000

// Firebase Realtime Database
// Lấy tại: Firebase Console → Project Settings → Service Accounts
#define FIREBASE_HOST       "YOUR_PROJECT_ID-default-rtdb.asia-southeast1.firebasedatabase.app"
#define FIREBASE_AUTH       "YOUR_FIREBASE_DATABASE_SECRET"
#define FIREBASE_DEVICE_ID  "esp32-voice"

// Path trên RTDB
#define FB_PATH_RELAY1      "/devices/" FIREBASE_DEVICE_ID "/relay1"
#define FB_PATH_RELAY2      "/devices/" FIREBASE_DEVICE_ID "/relay2"
#define FB_PATH_LAST_CMD    "/devices/" FIREBASE_DEVICE_ID "/last_command"
#define FB_PATH_TIMESTAMP   "/devices/" FIREBASE_DEVICE_ID "/timestamp"
#define FB_PATH_REMOTE_CMD  "/devices/" FIREBASE_DEVICE_ID "/remote_cmd"
#define FB_PATH_LOGS        "/logs/" FIREBASE_DEVICE_ID

// ╔══════════════════════════════════════════════════════════╗
// ║  9. DEBUG                                               ║
// ╚══════════════════════════════════════════════════════════╝
#include <Arduino.h>
#define DEBUG_SERIAL    1
#define DEBUG_INFERENCE 1
#define DEBUG_VAD       0
#define DEBUG_FIRMWARE_METRICS 1

#if DEBUG_SERIAL
  #define LOG(...)  Serial.printf(__VA_ARGS__)
#else
  #define LOG(...)
#endif

