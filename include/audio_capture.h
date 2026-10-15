#pragma once
// ============================================================
//  audio_capture.h
//  Capture I2S audio + Ring buffer + Sliding window
// ============================================================

#include <Arduino.h>
#include <driver/i2s.h>
#include "config.h"

// ============================================================
//   Capture & Ring Buffer
// ============================================================

// Khởi tạo I2S và ring buffer
void audio_init();

// Đọc 1 frame (VAD_FRAME_MS = 20ms) → ghi vào ring buffer
// Trả về số mẫu thực sự đọc được (0 nếu lỗi)
uint32_t audio_capture_frame();

// Lấy WINDOW_SAMPLES mẫu gần nhất từ ring buffer
// out: buffer đầu ra, kích thước >= WINDOW_SAMPLES (16 000)
void audio_get_window(int16_t *out);

// Lấy frame 20ms vừa đọc (con trỏ nội bộ, không copy)
// Dùng trong VAD để tính energy
// KHÔNG giữ con trỏ này qua lần gọi audio_capture_frame() tiếp theo
const int16_t* audio_get_last_frame();

// ============================================================
//  API — Sliding Window State Machine
// ============================================================

// Callback được gọi mỗi khi có 1 window sẵn sàng
// window       : WINDOW_SAMPLES mẫu PCM 16-bit @ SAMPLE_RATE Hz
// window_index : thứ tự window trong phiên hiện tại (0, 1, 2, ...)
typedef void (*WindowReadyCallback)(const int16_t *slice,   // 4000 mẫu
                                    uint32_t       slice_index);

// Đăng ký callback — gọi 1 lần trước vòng lặp chính
void audio_set_window_callback(WindowReadyCallback cb);

// Vòng lặp chính — gọi liên tục từ FreeRTOS task hoặc Arduino loop()
// Mỗi lần gọi xử lý đúng 1 frame VAD_FRAME_MS (20ms)
void audio_sliding_window_loop();

// Trạng thái hiện tại của state machine
typedef enum {
    AUDIO_STATE_IDLE      = 0,  // Chờ, không có giọng nói
    AUDIO_STATE_LISTENING = 1,  // Đang lắng nghe, phát sinh window
} AudioState;

AudioState audio_get_state();