#pragma once
// ============================================================
//  led_relay.h
// ============================================================

#include <Arduino.h>
#include "config.h"

enum RelayCommandSource : uint8_t {
    RELAY_SOURCE_MANUAL = 0,
    RELAY_SOURCE_AUTO = 1,
    RELAY_SOURCE_SYNC = 2
};

// ============================================================
//  KHỞI TẠO
// ============================================================

void led_relay_init();
void led_relay_update(bool listening);
bool curtain_servo_set(bool open);
bool curtain_servo_update();
void curtain_servo_restore(bool open);
void door_servo_rotate(bool opening);
void door_servo_stop(bool open);
bool curtain_is_open();
bool door_is_open();

// ============================================================
//  LED — trạng thái hệ thống
//
//  🔴 Đỏ nhấp nháy  : lỗi (I2S, inference...)
//  ⚪ Trắng          : sleep — chờ tiếng nói
//  🟢 Xanh lá        : VAD phát hiện giọng nói, đang lắng nghe
//  🔵 Xanh dương     : đang thực thi lệnh (relay đang đóng/mở)
// ============================================================

void led_sleep();     // ⚪ trắng mờ — idle
void led_listening(); // 🟢 xanh lá — VAD active
void led_executing(); // 🔵 xanh dương — đang thực thi lệnh
void led_error();     // 🔴 đỏ nhấp nháy (blocking 3 lần)

void led_off();       // tắt hoàn toàn (dùng nội bộ)

// ============================================================
//  RELAY
// ============================================================

void relay_set(uint8_t pin, bool on);
bool relay_control(uint8_t relay, bool on, RelayCommandSource source);

// ============================================================
//  THỰC THI LỆNH
//  Gọi sau khi inference trả về label hợp lệ
// ============================================================

bool execute_command(int label, float confidence, uint32_t detectedAtMs = 0,
                     uint32_t* relayLatencyMs = nullptr);
bool execute_command_from(int label, float confidence, RelayCommandSource source,
                          uint32_t detectedAtMs = 0,
                          uint32_t* relayLatencyMs = nullptr);

bool get_relay_state(int relay); 
