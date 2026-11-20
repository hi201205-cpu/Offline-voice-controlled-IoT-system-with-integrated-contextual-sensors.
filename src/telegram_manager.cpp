#include "telegram_manager.h"
#include "config.h"
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/queue.h"

#define TELEGRAM_MAX_MSG_LEN 512
#define TELEGRAM_QUEUE_SIZE  8

static QueueHandle_t s_telegramQueue = nullptr;
static TaskHandle_t s_telegramTaskHandle = nullptr;

bool telegram_is_configured() {
#if defined(ENABLE_TELEGRAM_NOTIFICATIONS) && ENABLE_TELEGRAM_NOTIFICATIONS
    return (strlen(TELEGRAM_BOT_TOKEN) > 10 && strlen(TELEGRAM_CHAT_ID) > 3);
#else
    return false;
#endif
}

static void telegram_worker_task(void* pvParameters) {
    char msg[TELEGRAM_MAX_MSG_LEN];

    while (true) {
        if (xQueueReceive(s_telegramQueue, msg, portMAX_DELAY) == pdTRUE) {
            if (!telegram_is_configured()) {
                continue;
            }

            // Đợi có kết nối Wi-Fi (tối đa 15 giây)
            int waitCount = 0;
            while (WiFi.status() != WL_CONNECTED && waitCount < 15) {
                vTaskDelay(pdMS_TO_TICKS(1000));
                waitCount++;
            }

            if (WiFi.status() != WL_CONNECTED) {
                LOG("[Telegram] Khong co WiFi, bo qua tin nhan\n");
                continue;
            }

            // Tạo JSON payload chuẩn
            String payload = "{\"chat_id\":\"" + String(TELEGRAM_CHAT_ID) + "\",\"text\":\"";
            for (size_t i = 0; i < strlen(msg); i++) {
                char c = msg[i];
                if (c == '"') payload += "\\\"";
                else if (c == '\\') payload += "\\\\";
                else if (c == '\n') payload += "\\n";
                else if (c == '\r') continue;
                else payload += c;
            }
            payload += "\",\"parse_mode\":\"Markdown\"}";

            String url = "https://api.telegram.org/bot" + String(TELEGRAM_BOT_TOKEN) + "/sendMessage";

            WiFiClientSecure client;
            client.setInsecure(); // Bo qua kiem tra chung chi SSL cua Telegram de tiet kiem bo nho
            client.setTimeout(8000);

            HTTPClient https;
            if (https.begin(client, url)) {
                https.addHeader("Content-Type", "application/json");
                https.setTimeout(8000);
                int httpCode = https.POST(payload);

                if (httpCode == HTTP_CODE_OK || httpCode == 200) {
                    LOG("[Telegram] Da gui thong bao thanh cong!\n");
                } else {
                    LOG("[Telegram] Gui that bai, ma loi HTTP: %d\n", httpCode);
                }
                https.end();
            } else {
                LOG("[Telegram] Khong the khoi tao HTTPS toi api.telegram.org\n");
            }

            vTaskDelay(pdMS_TO_TICKS(500)); // Nghi ngan tranh spam rate limit
        }
    }
}

void telegram_init() {
    if (s_telegramQueue == nullptr) {
        s_telegramQueue = xQueueCreate(TELEGRAM_QUEUE_SIZE, TELEGRAM_MAX_MSG_LEN);
    }

    if (s_telegramTaskHandle == nullptr && s_telegramQueue != nullptr) {
        xTaskCreatePinnedToCore(
            telegram_worker_task,
            "telegram_task",
            6144,
            nullptr,
            1, // Muc uu tien thap, khong tranh chap voi I2S/Inference
            &s_telegramTaskHandle,
            0  // Chay tren Core 0 cung WiFi
        );
        LOG("[Telegram] Khoi tao module thong bao Telegram thanh cong\n");
    }
}

bool telegram_send_alert(const char* message) {
    if (!telegram_is_configured() || s_telegramQueue == nullptr || message == nullptr) {
        return false;
    }

    char buf[TELEGRAM_MAX_MSG_LEN];
    strncpy(buf, message, sizeof(buf) - 1);
    buf[sizeof(buf) - 1] = '\0';

    // Gui bat dong bo (non-blocking) vao queue, timeout = 0
    BaseType_t res = xQueueSend(s_telegramQueue, buf, 0);
    if (res != pdTRUE) {
        LOG("[Telegram] Hang doi thong bao day, bo qua\n");
        return false;
    }
    return true;
}
