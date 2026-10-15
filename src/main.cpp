// ============================================================
//  main.cpp — Voice Control ESP32-S3
//
//  Pipeline:
//    audio_sliding_window_loop()
//      └─ [VAD] ──► on_window_ready() callback
//                               └─ inference_run()
//                                    └─ execute_command()
// ============================================================

#include <Arduino.h>
#include <esp_attr.h>
#include <esp_system.h>
#include "config.h"
#include "audio_capture.h"
#include "led_relay.h"
#include "inference.h"
#include "firebase_manager.h"
#include "sensors.h"
#include "automation.h"
#include "door_manager.h"

static uint32_t s_lastCmdMs = 0;
static uint32_t s_lastHealthLogMs = 0;
static constexpr uint32_t RESET_CHECKPOINT_MAGIC = 0x4B575352;
RTC_DATA_ATTR static uint32_t s_resetCheckpointMagic = 0;
RTC_DATA_ATTR static uint32_t s_resetUptimeCheckpointMs = 0;

static const char* reset_reason_name(esp_reset_reason_t reason) {
    switch (reason) {
        case ESP_RST_POWERON: return "power-on";
        case ESP_RST_EXT: return "external";
        case ESP_RST_SW: return "software";
        case ESP_RST_PANIC: return "panic";
        case ESP_RST_INT_WDT: return "interrupt-watchdog";
        case ESP_RST_TASK_WDT: return "task-watchdog";
        case ESP_RST_WDT: return "watchdog";
        case ESP_RST_DEEPSLEEP: return "deep-sleep";
        case ESP_RST_BROWNOUT: return "brownout";
        case ESP_RST_SDIO: return "sdio";
        default: return "other";
    }
}


// ============================================================
//  CALLBACK — được audio_capture gọi mỗi khi có window mới
//
//  Chạy trong cùng task với audio_sliding_window_loop(),
//  KHÔNG được block lâu (tối đa vài chục ms).
//
//  Thứ tự xử lý:
//    [1] Kiểm tra cooldown → bỏ qua nếu còn trong cooldown
//    [2] Chạy inference trên window 1000ms
//    [3] Kết quả hợp lệ → execute_command()
//    [4] Không nhận ra → trở về sleep
// ============================================================

// ============================================================
//  RAM Monitor — in thông tin bộ nhớ thực tế lúc runtime
// ============================================================

void print_memory_info() {
    LOG("\n===== MEMORY INFO =====\n");

    // ── Internal RAM ────────────────────────────────────────
    LOG("Internal RAM:\n");
    LOG("  Total      : %6d bytes (%.1f KB)\n",
        ESP.getHeapSize(),
        ESP.getHeapSize() / 1024.0f);
    LOG("  Used       : %6d bytes (%.1f KB)\n",
        ESP.getHeapSize() - ESP.getFreeHeap(),
        (ESP.getHeapSize() - ESP.getFreeHeap()) / 1024.0f);
    LOG("  Free       : %6d bytes (%.1f KB)\n",
        ESP.getFreeHeap(),
        ESP.getFreeHeap() / 1024.0f);
    LOG("  Min free   : %6d bytes (%.1f KB)\n",
        ESP.getMinFreeHeap(),
        ESP.getMinFreeHeap() / 1024.0f);
    LOG("  Largest block: %6d bytes (%.1f KB)\n",
        ESP.getMaxAllocHeap(),
        ESP.getMaxAllocHeap() / 1024.0f);

    // ── PSRAM ──────────────────────────────────────
    if (ESP.getPsramSize() > 0) {
        LOG("PSRAM:\n");
        LOG("  Total      : %6d bytes (%.1f KB)\n",
            ESP.getPsramSize(),
            ESP.getPsramSize() / 1024.0f);
        LOG("  Free       : %6d bytes (%.1f KB)\n",
            ESP.getFreePsram(),
            ESP.getFreePsram() / 1024.0f);
        LOG("  Min free   : %6d bytes (%.1f KB)\n",
            ESP.getMinFreePsram(),
            ESP.getMinFreePsram() / 1024.0f);
    } else {
        LOG("PSRAM       : không phát hiện\n");
    }

    // ── Stack của loop() task ───────────────────────────────
    LOG("Stack (loop task):\n");
    LOG("  High water mark: %d bytes\n",
        uxTaskGetStackHighWaterMark(NULL));

    LOG("=======================\n\n");
}

static void on_window_ready(const int16_t *window, uint32_t window_index) {
    uint32_t now = millis();

    LOG("🔵 Window #%lu → inference\n", (unsigned long)window_index);

    // ── [1] Cooldown ──────────────────────────────────────
    if ((now - s_lastCmdMs) < COMMAND_COOLDOWN_MS) {
        LOG("⏳ Cooldown, bỏ qua window #%lu\n", (unsigned long)window_index);
        return;
    }

    // ── [2] Inference ─────────────────────────────────────
    int   label = -1;
    float conf  = 0.0f;
    bool  valid = inference_run(window, &label, &conf);

    // ── [3] Kết quả ───────────────────────────────────────
    if (valid) {
        uint32_t detectedAtMs = millis();
        time_t detectedAt = time(nullptr);
        uint32_t relayLatencyMs = 0;
        bool executed = execute_command(label, conf, detectedAtMs, &relayLatencyMs);
        LOG("[LATENCY] KWS-to-relay=%lu ms\n",
            static_cast<unsigned long>(relayLatencyMs));
        firebase_queue_voice_log(label, conf, executed, detectedAt);
        s_lastCmdMs = millis();
        firebase_update(label, get_relay_state(1), get_relay_state(2), CMD_SOURCE_VOICE);
    } else {
        LOG("### Không nhận ra (best conf=%.1f%%)\n", conf * 100.0f);
    }
}

// ============================================================
//  SETUP
// ============================================================

void setup() {
    Serial.begin(115200);
    
    // Chờ Serial Monitor kết nối (chỉ cần khi debug)
    // Timeout 3 giây để không block mãi khi không có PC
    uint32_t t = millis();
    while (!Serial && (millis() - t) < 3000);

#if DEBUG_FIRMWARE_METRICS
    bool checkpointValid = s_resetCheckpointMagic == RESET_CHECKPOINT_MAGIC;
    Serial.printf("[RESET] reason=%s previous_uptime_checkpoint_ms=%lu%s\n",
                  reset_reason_name(esp_reset_reason()),
                  static_cast<unsigned long>(
                      checkpointValid ? s_resetUptimeCheckpointMs : 0),
                  checkpointValid ? "" : " (RTC checkpoint unavailable)");
    s_resetCheckpointMagic = RESET_CHECKPOINT_MAGIC;
#endif
    
    if (!psramInit()) {
        LOG("PSRAM init FAILED — kiểm tra board definition!\n");
    } else {
        LOG("PSRAM OK: %d KB\n", ESP.getPsramSize() / 1024);
    }

    LOG("Internal heap: %d KB\n", ESP.getHeapSize() / 1024);

    LOG("\n============================================\n");
    LOG("   Voice Control  —  ESP32-S3             \n");
    LOG("   on / off / go / stop                   \n");
    LOG("   Window=%dms  Hop=%dms  Hangover=%dms   \n",
        WINDOW_MS, HOP_MS, VAD_HANG_OVER_MS);
    LOG("============================================\n");

    led_relay_init();
    audio_init();
    inference_init();
    sensorsInit();
    doorInit();
    automationInit();
    firebase_manager_start();

    // Đăng ký callback — phải gọi trước audio_sliding_window_loop()
    audio_set_window_callback(on_window_ready);

    LOG("\n***  Sẵn sàng — đang chờ giọng nói...\n\n");
}

// ============================================================
//  LOOP
//
//  Mỗi vòng = 1 frame 20ms.
//  audio_sliding_window_loop() lo toàn bộ:
//    - Đọc mic → ring buffer
//    - VAD
//    - Khi có giọng: gọi on_window_ready() mỗi HOP_MS
//    - Khi im lặng đủ lâu: trở về IDLE
//
//  main.cpp chỉ cần:
//    - Cập nhật LED theo trạng thái IDLE / LISTENING
//    - Bắt lỗi I2S
// ============================================================

void loop() {
#if DEBUG_FIRMWARE_METRICS
    uint32_t loopStartedMs = millis();
#endif

    sensorsUpdate();
    firebase_manager_loop();
    doorUpdate();
    automationUpdate();
    sensorsPush();

    AudioState prevState = audio_get_state();

    // ── Chạy 1 frame pipeline (20ms) ──────────────────────
    audio_sliding_window_loop();

    // ── Cập nhật LED theo trạng thái ──────────────────────
    AudioState curState = audio_get_state();

    if (curState != prevState) {
        if (curState == AUDIO_STATE_LISTENING) {
            LOG("🟢 VAD ON\n");
        } else {
            LOG("⚪ VAD OFF\n");
        }
    }
    led_relay_update(curState == AUDIO_STATE_LISTENING);

#if DEBUG_FIRMWARE_METRICS
    uint32_t now = millis();
    if (now - s_lastHealthLogMs >= 5000UL) {
        s_lastHealthLogMs = now;
        s_resetUptimeCheckpointMs = now;
        Serial.printf(
            "[HEALTH] uptime=%lu ms wifi=%d rssi=%d dBm channel=%u "
            "heap_free=%u heap_min=%u heap_largest=%u\n",
            static_cast<unsigned long>(now), static_cast<int>(WiFi.status()),
            WiFi.RSSI(), static_cast<unsigned>(WiFi.channel()),
            static_cast<unsigned>(ESP.getFreeHeap()),
            static_cast<unsigned>(ESP.getMinFreeHeap()),
            static_cast<unsigned>(ESP.getMaxAllocHeap()));
    }
    uint32_t loopDurationMs = millis() - loopStartedMs;
    if (loopDurationMs > 200UL) {
        Serial.printf("[LOOP] warning duration=%lu ms\n",
                      static_cast<unsigned long>(loopDurationMs));
    }
#endif
}
