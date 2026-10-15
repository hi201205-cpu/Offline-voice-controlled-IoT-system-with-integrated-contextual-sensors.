#pragma once
// ============================================================
//  bandpass_filter.h
//  Bộ lọc thông dải IIR Biquad cho giọng nói
//
//  Dải thông: 300 Hz – 3 400 Hz  @  16 000 Hz
//    - High-pass 300Hz  : loại DC offset, rung động, tiếng ồn thấp
//    - Low-pass  3400Hz : loại nhiễu cao tần, aliasing
//
//  Cấu trúc: 2 tầng Biquad nối tiếp (Direct Form II Transposed)
//
//        x[n] ──► [HP Biquad] ──► [LP Biquad] ──► y[n]
//
//  Tại sao IIR Biquad?
//    - Chỉ 5 phép nhân + 4 phép cộng / mẫu / tầng
//    - Không tốn thêm RAM đáng kể (4 biến trạng thái)
//    - Đủ hiệu quả cho ESP32-S3 với FPU
//    - FIR cần 100–200 hệ số để đạt cùng độ dốc
// ============================================================

#include <stdint.h>

// ============================================================
//  Hệ số Biquad (Direct Form II Transposed)
//
//  Công thức:
//    w[n]  =  x[n]  - a1*w[n-1] - a2*w[n-2]
//    y[n]  =  b0*w[n] + b1*w[n-1] + b2*w[n-2]
//
//  Tất cả hệ số đã chuẩn hoá theo a0 (a0 = 1)
// ============================================================

typedef struct {
    float b0, b1, b2;   // hệ số feedforward
    float a1, a2;       // hệ số feedback (dấu đã đảo)
    float w1, w2;       // trạng thái bộ nhớ (delay line)
} Biquad;

// ============================================================
//  State của bộ lọc — 2 tầng nối tiếp
// ============================================================

typedef struct {
    Biquad hp;   // tầng High-pass  300Hz
    Biquad lp;   // tầng Low-pass  3400Hz
} BandpassFilter;

// ============================================================
//  API
// ============================================================

// Khởi tạo bộ lọc với hệ số mặc định (300–3400Hz @ 16kHz)
// Gọi 1 lần trong audio_init()
void bandpass_init(BandpassFilter *f);

// Lọc 1 mẫu PCM 16-bit, trả về mẫu đã lọc
// Gọi cho từng mẫu trong audio_capture_frame()
int16_t bandpass_process(BandpassFilter *f, int16_t sample);

// Reset trạng thái bộ nhớ (dùng khi bắt đầu phiên mới)
void bandpass_reset(BandpassFilter *f);