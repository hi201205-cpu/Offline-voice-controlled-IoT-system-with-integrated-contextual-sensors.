// ============================================================
//  bandpass_filter.cpp
//  Bộ lọc thông dải IIR Biquad — 300Hz đến 3400Hz @ 16kHz
//
//  Hệ số tính theo công thức Audio EQ Cookbook (R. Bristow-Johnson)
//  https://www.w3.org/TR/audio-eq-cookbook/
//
//  Butterworth Q = 0.7071 (= 1/√2) cho đáp ứng phẳng nhất trong dải thông
//
//  ── High-pass Butterworth fc = 300Hz ─────────────────────
//    ω0    = 2π × 300 / 16000 = 0.117810 rad
//    cos   = 0.993072
//    sin   = 0.117537
//    α     = sin / (2 × 0.7071) = 0.083090
//
//    b0 =  (1 + cos)/2 / a0  =  0.91992
//    b1 = -(1 + cos)   / a0  = -1.83984
//    b2 =  (1 + cos)/2 / a0  =  0.91992
//    a1 = -2×cos       / a0  = -1.83335
//    a2 =  (1 - α)     / a0  =  0.84677
//
//  ── Low-pass Butterworth fc = 3400Hz ─────────────────────
//    ω0    = 2π × 3400 / 16000 = 1.335177 rad
//    cos   = 0.233445
//    sin   = 0.972370
//    α     = sin / (2 × 0.7071) = 0.687500
//
//    b0 =  (1 - cos)/2 / a0  =  0.22728
//    b1 =  (1 - cos)   / a0  =  0.45455
//    b2 =  (1 - cos)/2 / a0  =  0.22728
//    a1 = -2×cos       / a0  = -0.27699
//    a2 =  (1 - α)     / a0  =  0.18516
// ============================================================

#include "bandpass_filter.h"

// ============================================================
//  Hệ số pre-computed (không tính lại mỗi lần init)
// ============================================================

// High-pass 300Hz @ 16kHz (Butterworth, Q=0.7071)
static const float HP_B0 =  0.91992f;
static const float HP_B1 = -1.83984f;
static const float HP_B2 =  0.91992f;
static const float HP_A1 = -1.83335f;
static const float HP_A2 =  0.84677f;

// Low-pass 3400Hz @ 16kHz (Butterworth, Q=0.7071)
static const float LP_B0 =  0.22728f;
static const float LP_B1 =  0.45455f;
static const float LP_B2 =  0.22728f;
static const float LP_A1 = -0.27699f;
static const float LP_A2 =  0.18516f;

// ============================================================
//  bandpass_init
// ============================================================

void bandpass_init(BandpassFilter *f) {
    // Nạp hệ số high-pass
    f->hp.b0 = HP_B0;  f->hp.b1 = HP_B1;  f->hp.b2 = HP_B2;
    f->hp.a1 = HP_A1;  f->hp.a2 = HP_A2;
    f->hp.w1 = 0.0f;   f->hp.w2 = 0.0f;

    // Nạp hệ số low-pass
    f->lp.b0 = LP_B0;  f->lp.b1 = LP_B1;  f->lp.b2 = LP_B2;
    f->lp.a1 = LP_A1;  f->lp.a2 = LP_A2;
    f->lp.w1 = 0.0f;   f->lp.w2 = 0.0f;
}

// ============================================================
//  biquad_process — 1 tầng Biquad, Direct Form II Transposed
// ============================================================

static inline float biquad_process(Biquad *bq, float x) {
    float w0 = x - bq->a1 * bq->w1 - bq->a2 * bq->w2;
    float y  = bq->b0 * w0 + bq->b1 * bq->w1 + bq->b2 * bq->w2;

    bq->w2 = bq->w1;
    bq->w1 = w0;

    return y;
}

// ============================================================
//  bandpass_process — 2 tầng nối tiếp: HP → LP
//
//  Clamp về [-32768, 32767] để tránh overflow khi cast về int16_t.
//  Với tín hiệu mic bình thường gain tổng ≈ 1.0, hiếm khi clamp.
// ============================================================

int16_t bandpass_process(BandpassFilter *f, int16_t sample) {
    float x = (float)sample;

    x = biquad_process(&f->hp, x);   // High-pass: cắt < 300Hz
    x = biquad_process(&f->lp, x);   // Low-pass:  cắt > 3400Hz

    // Clamp tránh overflow
    if      (x >  32767.0f) x =  32767.0f;
    else if (x < -32768.0f) x = -32768.0f;

    return (int16_t)x;
}

// ============================================================
//  bandpass_reset — xoá bộ nhớ, dùng khi bắt đầu phiên mới
// ============================================================

void bandpass_reset(BandpassFilter *f) {
    f->hp.w1 = 0.0f;  f->hp.w2 = 0.0f;
    f->lp.w1 = 0.0f;  f->lp.w2 = 0.0f;
}