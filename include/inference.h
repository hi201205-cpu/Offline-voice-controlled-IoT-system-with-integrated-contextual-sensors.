#pragma once
// ============================================================
//  inference.h
//  Nhiệm vụ:
//    - Nhận window 16000 mẫu từ audio_capture
//    - Chạy Edge Impulse run_classifier()
//    - Trả về label và confidence
// ============================================================

#include <Arduino.h>
#include "config.h"



// ============================================================
//  KHỞI TẠO — in thông tin model ra Serial
// ============================================================

void inference_init();

// ============================================================
//  CHẠY INFERENCE
//
//  Tham số:
//    audioWindow  : con trỏ đến WINDOW_SAMPLES mẫu int16
//                   (lấy từ audio_get_window())
//    outLabel     : [ra] index class thắng
//    outConf      : [ra] confidence class thắng (0.0 – 1.0)
//
//  Trả về:
//    true   nếu nhận ra lệnh hợp lệ (conf >= CONF_THRESHOLD
//           và không phải LABEL_NOISE)
//    false  nếu dưới ngưỡng hoặc là noise
// ============================================================

bool inference_run(const int16_t *audioWindow,
                   int           *outLabel,
                   float         *outConf);