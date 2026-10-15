#pragma once
// ============================================================
//  noise_reduction.h — Spectral Subtraction

//    Tách noise profile từ frame khi IDLE, áp dụng spectral subtraction cho mọi frame
//    audio_init()       → noise_reduction_init()
//    IDLE frame         → noise_reduction_update(frame, len)
//    mọi frame          → noise_reduction_apply(frame, len)
// ============================================================
#include <stdint.h>

// Khởi tạo — gọi 1 lần trong audio_init()
void noise_reduction_init();

// Cập nhật noise profile — chỉ gọi khi AUDIO_STATE_IDLE
void noise_reduction_update(const int16_t* frame, uint32_t len);

// Áp dụng spectral subtraction — gọi sau bandpass, trước ring buffer
// Sửa frame IN-PLACE
void noise_reduction_apply(int16_t* frame, uint32_t len);