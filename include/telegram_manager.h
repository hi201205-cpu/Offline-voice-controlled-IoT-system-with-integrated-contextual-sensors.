#pragma once
#include <Arduino.h>

/**
 * @brief Khởi tạo hệ thống thông báo Telegram bất đồng bộ (FreeRTOS Task & Queue)
 */
void telegram_init();

/**
 * @brief Gửi thông báo khẩn cấp tới Telegram (Non-blocking, đẩy vào Queue xử lý ngầm)
 * @param message Nội dung thông báo (hỗ trợ Markdown)
 * @return true nếu đẩy vào hàng đợi thành công
 */
bool telegram_send_alert(const char* message);

/**
 * @brief Kiểm tra trạng thái cấu hình Telegram
 */
bool telegram_is_configured();
