// ============================================================
//  inference.cpp
//
//  Luồng dữ liệu:
//    audio_capture → callback(16000 mẫu mỗi 200ms)
//      → inference_run()
//        → run_classifier() [MFCC + model]
//          → kết quả label + confidence
// ============================================================

#include "inference.h"

// Tự động nhận diện header thư viện xuất từ Edge Impulse
#if __has_include(<KWS-Vietnamese-Research_inferencing.h>)
#include <KWS-Vietnamese-Research_inferencing.h>
#elif __has_include(<KWS-Vietnamese-command_inferencing.h>)
#include <KWS-Vietnamese-command_inferencing.h>
#else
#include <edge-impulse-sdk/classifier/ei_run_classifier.h>
#endif

// Kiểm tra WINDOW_SAMPLES phải khớp với model EI
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
// ============================================================

static int ei_audio_signal_get_data(size_t offset, size_t length, float *out) {
    numpy::int16_to_float(s_audioWindow + offset, out, length);
    return EIDSP_OK;
}

// ============================================================
//  inference_init
// ============================================================

void inference_init() {
    run_classifier_init();

    LOG("[INFERENCE] Model info:\n");
    LOG("  Classes   : %d\n",   EI_CLASSIFIER_LABEL_COUNT);
    LOG("  Frequency : %d Hz\n", EI_CLASSIFIER_FREQUENCY);
    LOG("  Window    : %d ms\n",
        EI_CLASSIFIER_RAW_SAMPLE_COUNT * 1000 / EI_CLASSIFIER_FREQUENCY);

#if EI_CLASSIFIER_CALIBRATION_ENABLED
    LOG("  Perf.Cal  : Enabled\n");
#else
    LOG("  Perf.Cal  : Disabled (Standard threshold mode: %.2f)\n", COMMAND_CONFIDENCE_THRESHOLD);
#endif

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
// ============================================================

bool inference_run(const int16_t *audioWindow, int *outLabel, float *outConf) {

    s_audioWindow = audioWindow;

    signal_t signal;
    signal.total_length = EI_CLASSIFIER_RAW_SAMPLE_COUNT;  // 16000
    signal.get_data     = &ei_audio_signal_get_data;

    ei_impulse_result_t result = {0};
    EI_IMPULSE_ERROR err = run_classifier(&signal, &result, false);

    if (err != EI_IMPULSE_OK) {
        LOG("[INFERENCE] Lỗi run_classifier: %d\n", err);
        return false;
    }

    // Log timing
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

    *outLabel = bestIndex;
    *outConf  = bestConf;

#if EI_CLASSIFIER_CALIBRATION_ENABLED
    const char *detectedLabel = result.postprocessed_output.perf_cal_output.detected_label;
    if (detectedLabel == nullptr || strcmp(result.classification[bestIndex].label, detectedLabel) != 0) {
        LOG("[INFERENCE] Calibration chưa xác nhận hoặc nhãn không khớp → bỏ qua\n");
        *outLabel = -1;
        *outConf  = 0.0f;
        return false;
    }
#endif

    // Nếu nhãn thắng là NOISE hoặc UNKNOWN -> bỏ qua ngay lập tức
    if (bestIndex == LABEL_NOISE || bestIndex == LABEL_UNKNOWN) {
        LOG("[INFERENCE] Noise hoặc Unknown (conf=%.1f%%) → bỏ qua\n", bestConf * 100.0f);
        *outLabel = -1;
        *outConf  = 0.0f;
        return false;
    }

    // Kiểm tra ngưỡng tin cậy tối thiểu
    if (bestConf < COMMAND_CONFIDENCE_THRESHOLD) {
        LOG("[INFERENCE] Độ tin cậy thấp (%.1f%% < %.1f%%) → bỏ qua\n",
            bestConf * 100.0f, COMMAND_CONFIDENCE_THRESHOLD * 100.0f);
        *outLabel = -1;
        *outConf  = 0.0f;
        return false;
    }

    return true;
}
