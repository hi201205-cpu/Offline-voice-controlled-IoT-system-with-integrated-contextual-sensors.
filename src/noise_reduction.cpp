// ============================================================
//  noise_reduction.cpp — Spectral Subtraction
//  Custom FFT radix-2 
// ============================================================

#include "noise_reduction.h"
#include <Arduino.h>
#include "config.h"
#include <math.h>
#include <string.h>

#define NR_FFT_SIZE    512
#define NR_HALF        (NR_FFT_SIZE / 2)
#define NR_ALPHA       0.98f
#define NR_BETA        1.5f
#define NR_GAMMA       0.1f
#define NR_INIT_FRAMES 30

static float s_re[NR_FFT_SIZE];
static float s_im[NR_FFT_SIZE];
static float s_noiseProfile[NR_HALF];

static bool  s_initialized = false;
static int   s_initCount   = 0;

// ── Radix-2 FFT/IFFT ─────────────────────────────────────────
static void _fft(float* re, float* im, int n, bool inverse) {
    int j = 0;
    for (int i = 1; i < n; i++) {
        int bit = n >> 1;
        while (j & bit) { j ^= bit; bit >>= 1; }
        j ^= bit;
        if (i < j) {
            float t;
            t = re[i]; re[i] = re[j]; re[j] = t;
            t = im[i]; im[i] = im[j]; im[j] = t;
        }
    }
    for (int len = 2; len <= n; len <<= 1) {
        float ang = (inverse ? 1.0f : -1.0f) * 2.0f * (float)M_PI / len;
        float wRe = cosf(ang), wIm = sinf(ang);
        for (int i = 0; i < n; i += len) {
            float curRe = 1.0f, curIm = 0.0f;
            for (int k = 0; k < len / 2; k++) {
                float uRe = re[i+k], uIm = im[i+k];
                float vRe = re[i+k+len/2]*curRe - im[i+k+len/2]*curIm;
                float vIm = re[i+k+len/2]*curIm + im[i+k+len/2]*curRe;
                re[i+k]       = uRe + vRe; im[i+k]       = uIm + vIm;
                re[i+k+len/2] = uRe - vRe; im[i+k+len/2] = uIm - vIm;
                float nr = curRe*wRe - curIm*wIm;
                curIm = curRe*wIm + curIm*wRe;
                curRe = nr;
            }
        }
    }
    if (inverse) {
        for (int i = 0; i < n; i++) { re[i] /= n; im[i] /= n; }
    }
}

static void _load(const int16_t* frame, uint32_t len) {
    uint32_t n = (len < NR_FFT_SIZE) ? len : NR_FFT_SIZE;
    for (uint32_t i = 0; i < n; i++) {
        s_re[i] = frame[i] / 32768.0f; s_im[i] = 0.0f;
    }
    for (uint32_t i = n; i < NR_FFT_SIZE; i++) {
        s_re[i] = s_im[i] = 0.0f;
    }
}

// ── Public API ────────────────────────────────────────────────
void noise_reduction_init() {
    memset(s_noiseProfile, 0, sizeof(s_noiseProfile));
    memset(s_re, 0, sizeof(s_re));
    memset(s_im, 0, sizeof(s_im));
    s_initialized = false;
    s_initCount   = 0;
    LOG("[NR] Spectral Subtraction ready (FFT=%d)\n", NR_FFT_SIZE);
}

void noise_reduction_update(const int16_t* frame, uint32_t len) {
    _load(frame, len);
    _fft(s_re, s_im, NR_FFT_SIZE, false);

    if (!s_initialized) {
        for (int k = 0; k < NR_HALF; k++) {
            float mag = sqrtf(s_re[k]*s_re[k] + s_im[k]*s_im[k]);
            s_noiseProfile[k] += mag / NR_INIT_FRAMES;
        }
        if (++s_initCount >= NR_INIT_FRAMES) {
            s_initialized = true;
            LOG("[NR] Noise profile san sang\n");
        }
    } else {
        for (int k = 0; k < NR_HALF; k++) {
            float mag = sqrtf(s_re[k]*s_re[k] + s_im[k]*s_im[k]);
            float alpha = (mag < s_noiseProfile[k]) ? NR_ALPHA : 0.998f;
            s_noiseProfile[k] = alpha*s_noiseProfile[k] + (1.0f-alpha)*mag;
        }
    }
}

void noise_reduction_apply(int16_t* frame, uint32_t len) {
    if (!s_initialized) return;
    uint32_t n = (len < NR_FFT_SIZE) ? len : NR_FFT_SIZE;

    _load(frame, len);
    _fft(s_re, s_im, NR_FFT_SIZE, false);

    for (int k = 0; k < NR_HALF; k++) {
        float mag = sqrtf(s_re[k]*s_re[k] + s_im[k]*s_im[k]);
        if (mag < 1e-10f) continue;
        float newMag = mag - NR_BETA * s_noiseProfile[k];
        float floor  = NR_GAMMA * mag;
        if (newMag < floor) newMag = floor;
        float scale = newMag / mag;
        s_re[k] *= scale; s_im[k] *= scale;
        if (k > 0) {
            s_re[NR_FFT_SIZE-k] =  s_re[k];
            s_im[NR_FFT_SIZE-k] = -s_im[k];
        }
    }
    s_im[NR_HALF] = 0.0f;

    _fft(s_re, s_im, NR_FFT_SIZE, true);

    for (uint32_t i = 0; i < n; i++) {
        float val = s_re[i] * 32768.0f;
        if      (val >  32767.0f) val =  32767.0f;
        else if (val < -32768.0f) val = -32768.0f;
        frame[i] = (int16_t)val;
    }
}