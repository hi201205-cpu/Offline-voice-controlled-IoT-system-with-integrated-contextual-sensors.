// ============================================================
//  audio_capture.cpp
//
//  Chức năng:
//    1. Capture I2S audio liên tục vào ring buffer
//    2. Cung cấp frame 20ms cho VAD
//    3. Cung cấp sliding window 1000ms / hop 200ms cho ASR

//  Tham số mặc định (trong config.h):
//    SAMPLE_RATE     = 16000 Hz
//    VAD_FRAME_MS    = 20  ms  → 320  mẫu / frame
//    WINDOW_MS       = 1000 ms → 16000 mẫu / window
//    HOP_MS          = 200  ms → 3200  mẫu / hop
//    RING_BUF_SAMPLES= 32000   (2 giây)
// ============================================================

#include "audio_capture.h"
#include "bandpass_filter.h"
#include "noise_reduction.h"
#include "config.h"
// ============================================================
//  BIẾN NỘI BỘ
// ============================================================

// --- Bộ lọc thông dải 300–3400Hz ---
static BandpassFilter s_bpf;

// --- Ring buffer (ghi liên tục, đọc bằng audio_get_window) ---
static int16_t  s_ringBuf[RING_BUF_SAMPLES];
static uint32_t s_ringHead = 0;          // Vị trí ghi tiếp theo

// --- Buffer tạm cho I2S (32-bit raw từ INMP441) ---
static int32_t  s_i2sRaw[VAD_FRAME_SAMPLES];

// --- Frame 16-bit sau shift — VAD đọc từ đây ---
static int16_t  s_frameBuf[VAD_FRAME_SAMPLES];

// --- Sliding window state machine ---
static AudioState          s_state       = AUDIO_STATE_IDLE;
static uint32_t            s_hopCounter  = 0;    // Số mẫu tích lũy từ lần gọi callback gần nhất
static uint32_t            s_windowIndex = 0;    // Thứ tự window (0, 1, 2, ...)
static uint32_t            s_silenceFrames = 0;  // Số frame im lặng liên tiếp
static WindowReadyCallback s_callback    = nullptr;

// VAD_SILENCE_FRAMES_THRESHOLD và VAD_ENERGY_THRESH lấy từ config.h

// ============================================================
//  VAD NỘI BỘ  (tính RMS energy của frame)
//  Trả về true nếu phát hiện giọng nói
// ============================================================

static bool vad_has_voice(const int16_t *frame, uint32_t len) {
    int64_t energy = 0;
    for (uint32_t i = 0; i < len; i++) {
        energy += (int64_t)frame[i] * frame[i];
    }
    energy /= len;   // mean square
    return energy > VAD_ENERGY_THRESH;
}

// ============================================================
//  audio_init
// ============================================================

void audio_init() {
    i2s_config_t cfg = {
        .mode                 = (i2s_mode_t)(I2S_MODE_MASTER | I2S_MODE_RX),
        .sample_rate          = SAMPLE_RATE,
        .bits_per_sample      = I2S_BITS_PER_SAMPLE_32BIT,
        .channel_format       = I2S_CHANNEL_FMT_ONLY_LEFT,
        .communication_format = I2S_COMM_FORMAT_STAND_I2S,
        .intr_alloc_flags     = ESP_INTR_FLAG_LEVEL1,
        .dma_buf_count        = DMA_BUF_COUNT,
        .dma_buf_len          = DMA_BUF_LEN,
        .use_apll             = false,
        .tx_desc_auto_clear   = false,
        .fixed_mclk           = 0
    };

    i2s_pin_config_t pins = {
        .bck_io_num   = PIN_I2S_SCK,
        .ws_io_num    = PIN_I2S_WS,
        .data_out_num = I2S_PIN_NO_CHANGE,
        .data_in_num  = PIN_I2S_DIN
    };

    ESP_ERROR_CHECK(i2s_driver_install(I2S_PORT, &cfg, 0, NULL));
    ESP_ERROR_CHECK(i2s_set_pin(I2S_PORT, &pins));
    i2s_zero_dma_buffer(I2S_PORT);

    // Xóa toàn bộ bộ nhớ nội bộ
    memset(s_ringBuf,  0, sizeof(s_ringBuf));
    memset(s_frameBuf, 0, sizeof(s_frameBuf));
    memset(s_i2sRaw,   0, sizeof(s_i2sRaw));

    bandpass_init(&s_bpf);   // khởi tạo bộ lọc 300–3400Hz
    noise_reduction_init();   // khởi tạo spectral subtraction

    s_ringHead      = 0;
    s_hopCounter    = 0;
    s_windowIndex   = 0;
    s_silenceFrames = 0;
    s_state         = AUDIO_STATE_IDLE;

    LOG("[AUDIO] I2S ready — SCK=%d WS=%d DIN=%d @ %dHz\n",
        PIN_I2S_SCK, PIN_I2S_WS, PIN_I2S_DIN, SAMPLE_RATE);
    LOG("[AUDIO] Window=%dms (%d mẫu)  Hop=%dms (%d mẫu)\n",
        WINDOW_MS, WINDOW_SAMPLES, HOP_MS, HOP_SAMPLES);
}

// ============================================================
//  audio_capture_frame
//  1. i2s_read  → s_i2sRaw  (32-bit, 1 kênh trái)
//  2. >> I2S_SHIFT_BITS → s_frameBuf (16-bit)
//  3. Ghi s_frameBuf vào ring buffer (vòng tròn)
//  Trả về: số mẫu đọc được (0 = lỗi)
// ============================================================

uint32_t audio_capture_frame() {
    size_t bytesRead = 0;

    esp_err_t err = i2s_read(
        I2S_PORT,
        s_i2sRaw,
        VAD_FRAME_SAMPLES * sizeof(int32_t),
        &bytesRead,
        pdMS_TO_TICKS(50)          // timeout 50ms
    );

    if (err != ESP_OK || bytesRead == 0) return 0;

    uint32_t samplesRead = bytesRead / sizeof(int32_t);

    for (uint32_t i = 0; i < samplesRead; i++) {
        // Shift 32-bit → 16-bit (INMP441 dữ liệu nằm ở 24 bit cao)
        int16_t raw = (int16_t)(s_i2sRaw[i] >> I2S_SHIFT_BITS);

        // Lọc thông dải 300–3400Hz: loại DC, rung động, nhiễu cao tần
        int16_t filtered = bandpass_process(&s_bpf, raw);
        s_frameBuf[i] = filtered;
    }

    // Spectral subtraction — lọc tiếng ồn trước khi vào ring buffer
    // Sửa s_frameBuf in-place, chỉ có tác dụng sau ~600ms khởi động (NR_INIT_FRAMES)
    noise_reduction_apply(s_frameBuf, samplesRead);

    // Ghi vào ring buffer sau khi đã lọc
    for (uint32_t i = 0; i < samplesRead; i++) {
        s_ringBuf[s_ringHead] = s_frameBuf[i];
        s_ringHead = (s_ringHead + 1) % RING_BUF_SAMPLES;
    }

    return samplesRead;
}

// ============================================================
//  audio_get_window
//  Copy WINDOW_SAMPLES mẫu GẦN NHẤT từ ring buffer ra ngoài.
//
//  Vì ring buffer ghi vòng tròn, mẫu gần nhất nằm tại:
//    [head - WINDOW_SAMPLES] → [head - 1]  (mod RING_BUF_SAMPLES)
//
//  Ví dụ (head = 5, RING=10, WINDOW=6):
//    Cần lấy index: 9,0,1,2,3,4  → out[0..5]
//    idx = (5 + 10 - 6 + i) % 10 = (9 + i) % 10  ✓
// ============================================================

void audio_get_window(int16_t *out) {
    for (uint32_t i = 0; i < WINDOW_SAMPLES; i++) {
        uint32_t idx = (s_ringHead + RING_BUF_SAMPLES - WINDOW_SAMPLES + i)
                       % RING_BUF_SAMPLES;
        out[i] = s_ringBuf[idx];
    }
}

// ============================================================
//  audio_get_last_frame
//  Con trỏ đến frame 20ms vừa đọc (không copy, không alloc).
//  Chỉ hợp lệ đến lần gọi audio_capture_frame() tiếp theo.
// ============================================================

const int16_t* audio_get_last_frame() {
    return s_frameBuf;
}

// ============================================================
//  audio_set_window_callback
// ============================================================

void audio_set_window_callback(WindowReadyCallback cb) {
    s_callback = cb;
}

// ============================================================
//  audio_get_state
// ============================================================

AudioState audio_get_state() {
    return s_state;
}

// ============================================================
//  audio_sliding_window_loop
//
//  Gọi liên tục từ FreeRTOS task hoặc Arduino loop().
//  Mỗi lần gọi xử lý 1 frame 20ms.
//
//  State machine:
//
//  IDLE ──[voice detected]──► LISTENING
//          s_hopCounter = 0
//          s_windowIndex = 0
//          s_silenceFrames = 0
//
//  LISTENING:
//    - Tích lũy hop_counter += samplesRead (20ms = 320 mẫu)
//    - Mỗi khi hop_counter >= HOP_SAMPLES (3200 = 200ms):
//        → gọi audio_get_window() lấy 1000ms gần nhất
//        → gọi callback(window, window_index++)
//        → hop_counter -= HOP_SAMPLES  (KHÔNG reset về 0,
//          giữ phần dư để hop chính xác)
//    - Đếm frame im lặng liên tiếp
//    - Nếu im lặng >= VAD_SILENCE_FRAMES_THRESHOLD → về IDLE
//
//  Tại sao hop_counter -= HOP_SAMPLES thay vì = 0?
//    Giả sử HOP_SAMPLES=3200, mỗi frame=320 mẫu (10 frame/hop).
//    Nếu reset về 0: drift tích lũy theo thời gian.
//    Nếu trừ đi: hop chính xác tuyệt đối dù frame size không
//    chia hết HOP_SAMPLES.
// ============================================================

void audio_sliding_window_loop() {
    // ── 1. Capture 1 frame 20ms ──────────────────────────────
    uint32_t samplesRead = audio_capture_frame();
    if (samplesRead == 0) return;   // I2S timeout, thử lại sau

    const int16_t *frame = audio_get_last_frame();

    // ── 2. VAD ───────────────────────────────────────────────
    bool hasVoice = vad_has_voice(frame, samplesRead);

    // ── 3. State machine ─────────────────────────────────────
    switch (s_state) {

        // ── IDLE: chờ giọng nói ──────────────────────────────
        case AUDIO_STATE_IDLE:
            // Cập nhật noise profile liên tục khi không có giọng nói
            if (!hasVoice) {
                noise_reduction_update(frame, samplesRead);
            }  else {
                s_state         = AUDIO_STATE_LISTENING;
                s_hopCounter    = 0;
                s_windowIndex   = 0;
                s_silenceFrames = 0;
                bandpass_reset(&s_bpf);   // xoá bộ nhớ filter khi bắt đầu phiên mới
                LOG("[AUDIO] VAD ON → LISTENING (window=%dms hop=%dms hangover=%dms)\n",
                    WINDOW_MS, HOP_MS, VAD_HANG_OVER_MS);
            }
            break;

        // ── LISTENING: sliding window ─────────────────────────
        case AUDIO_STATE_LISTENING:

            // Đếm im lặng / giọng nói
            if (hasVoice) {
                s_silenceFrames = 0;   // reset bộ đếm im lặng
            } else {
                s_silenceFrames++;
            }

            // Tích lũy hop counter
            s_hopCounter += samplesRead;

            // Phát sinh window mỗi HOP_SAMPLES mẫu
            while (s_hopCounter >= HOP_SAMPLES) {
                s_hopCounter -= HOP_SAMPLES;   // giữ phần dư

                if (s_callback != nullptr) {
                    // Buffer tĩnh để tránh alloc trên heap
                    static int16_t s_windowBuf[WINDOW_SAMPLES];

                    // Lấy WINDOW_SAMPLES (16000) mẫu gần nhất từ ring buffer
                    audio_get_window(s_windowBuf);

                    LOG("[AUDIO] Window #%lu ready (hop=%dms)\n",
                        (unsigned long)s_windowIndex, HOP_MS);

                    s_callback(s_windowBuf, s_windowIndex);
                    s_windowIndex++;
                }
            }

            // Im lặng đủ lâu → về IDLE0
            if (s_silenceFrames >= VAD_SILENCE_FRAMES_THRESHOLD) {
                LOG("[AUDIO] Silence %dms → IDLE (windows=%lu)\n",
                    (int)(s_silenceFrames * VAD_FRAME_MS),
                    (unsigned long)s_windowIndex);
                s_state         = AUDIO_STATE_IDLE;
                s_hopCounter    = 0;
                s_windowIndex   = 0;
                s_silenceFrames = 0;
            }
            break;

        default:
            s_state = AUDIO_STATE_IDLE;
            break;
    }
}