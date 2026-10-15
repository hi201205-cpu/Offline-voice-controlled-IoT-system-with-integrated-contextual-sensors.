// ============================================================
//  inference.cpp
//
//  Luồng dữ liệu:
//    audio_capture → callback(16000 mẫu mỗi 200ms)
//      → inference_run()
//        → run_classifier() [MFCC + model + Performance Calibration]
//          → kết quả label + confidence
// ============================================================

#include "inference.h"
#include <KWS-Vietnamese-command_inferencing.h>

// Kiểm tra WINDOW_SAMPLES phải khớp với model EI
// Nếu sai → lỗi ngay lúc build, không phải lúc chạy
static_assert(
    WINDOW_SAMPLES == EI_CLASSIFIER_RAW_SAMPLE_COUNT,
    "WINDOW_SAMPLES trong config.h phải bằng EI_CLASSIFIER_RAW_SAMPLE_COUNT (16000)"
);

// ============================================================
//  Biến nội bộ
// ============================================================

static const int16_t *s_audioWindow = nullptr;

// ============================================================
//  Callback cấp dữ liệu cho EI DSP
//
//  EI không đọc buffer trực tiếp mà gọi callback này
//  để lấy từng đoạn float. Convert int16 → float tại đây.
// ============================================================

static int ei_audio_signal_get_data(size_t offset,size_t length,float *out) {
    numpy::int16_to_float(s_audioWindow + offset, out, length);
    return EIDSP_OK;
}

// ============================================================
//  inference_init
//
//  Bắt buộc gọi run_classifier_init() để khởi tạo
//  post-processing block, bao gồm Performance Calibration.
//  Nếu bỏ qua → output toàn 0.00 dù model chạy bình thường.
// ============================================================

void inference_init() {
    // Khởi tạo post-processing (Performance Calibration)
    // Bắt buộc dù dùng run_classifier() thông thường
    run_classifier_init();

    LOG("[INFERENCE] Model info:\n");
    LOG("  Classes   : %d\n",   EI_CLASSIFIER_LABEL_COUNT);
    LOG("  Frequency : %d Hz\n", EI_CLASSIFIER_FREQUENCY);
    LOG("  Window    : %d ms\n",
        EI_CLASSIFIER_RAW_SAMPLE_COUNT * 1000 / EI_CLASSIFIER_FREQUENCY);
    LOG("  Perf.Cal  : threshold=0.3166  suppression=864ms\n");

#if DEBUG_INFERENCE
    LOG("  Labels    : ");
    for (int i = 0; i < EI_CLASSIFIER_LABEL_COUNT; i++) {
        LOG("[%d]%s ", i, ei_classifier_inferencing_categories[i]);
    }
    LOG("\n");
#endif
}

// ============================================================
//  inference_run
//
//  Tham số:
//    audioWindow : 16000 mẫu PCM 16-bit (window 1000ms)
//                  lấy từ audio_get_window() của audio_capture
//    outLabel    : [ra] index class thắng
//    outConf     : [ra] confidence class thắng (0.0 – 1.0)
//
//  Trả về:
//    true  → nhận ra lệnh hợp lệ
//              (không phải noise, confidence > 0.01)
//    false → noise, dưới ngưỡng, hoặc lỗi inference
// ============================================================

bool inference_run(const int16_t *audioWindow, int *outLabel, float *outConf) {

    // Gắn window vào biến static để callback truy cập được
    s_audioWindow = audioWindow;

    // Tạo signal cho Edge Impulse
    signal_t signal;
    signal.total_length = EI_CLASSIFIER_RAW_SAMPLE_COUNT;  // 16000
    signal.get_data     = &ei_audio_signal_get_data;

    // Chạy MFCC + model + Performance Calibration
    ei_impulse_result_t result = {0};
    EI_IMPULSE_ERROR err = run_classifier(&signal, &result, false);

    if (err != EI_IMPULSE_OK) {
        LOG("[INFERENCE] Lỗi run_classifier: %d\n", err);
        return false;
    }

    const char *detectedLabel = result.postprocessed_output.perf_cal_output.detected_label;
    if (detectedLabel == nullptr) {
        *outLabel = -1;
        *outConf  = 0.0f;
        LOG("[INFERENCE] Calibration chưa xác nhận lệnh → bỏ qua\n");
        return false;
    }

    // Log timing — nếu DSP=0ms và Inf=0ms thì model không chạy
    LOG("[INFERENCE] DSP=%.1fms Inf=%.1fms | ",
        result.timing.dsp,
        result.timing.classification);

    for (int i = 0; i < EI_CLASSIFIER_LABEL_COUNT; i++) {
        LOG("%s=%.2f  ",
            result.classification[i].label,
            result.classification[i].value);
    }
    LOG("\n");

    // Tìm class có confidence cao nhất
    float bestConf  = 0.0f;
    int   bestIndex = 0;

    for (int i = 0; i < EI_CLASSIFIER_LABEL_COUNT; i++) {
        if (result.classification[i].value > bestConf) {
            bestConf  = result.classification[i].value;
            bestIndex = i;
        }
    }

    // Ghi kết quả ra ngoài
    *outLabel = bestIndex;
    *outConf  = bestConf;

    if (strcmp(result.classification[bestIndex].label, detectedLabel) != 0) {
        LOG("[INFERENCE] Nhãn calibration không khớp output → bỏ qua\n");
        *outLabel = -1;
        *outConf  = 0.0f;
        return false;
    }

    if (bestIndex == LABEL_NOISE || bestConf < 0.01f) {
        LOG("[INFERENCE] Noise hoặc confidence thấp → bỏ qua\n");
        *outLabel = -1;
        *outConf  = 0.0f;
        return false;
    }

    return true;
}