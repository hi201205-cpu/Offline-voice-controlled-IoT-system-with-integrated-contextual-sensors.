"""
=============================================================
  Đồ Án 1 - Tiền xử lý Audio + MFCC + Spectrogram
  Pipeline: Load → Resample → Mono → Bandpass → Pre-emphasis
            → Normalize → MFCC → Spectrogram
=============================================================
Cách dùng:
    python test_preprocessing.py --input_dir D:\Do_an_1_source_code\dataset\tat_den --output_dir output_

Yêu cầu:
    pip install librosa soundfile numpy scipy matplotlib python_speech_features
"""

import os
import argparse
import numpy as np
import librosa
import librosa.display
import soundfile as sf
import matplotlib.pyplot as plt
import matplotlib.gridspec as gridspec
from scipy.signal import butter, sosfilt
from python_speech_features import mfcc

# ─────────────────────────────────────────────
# THÔNG SỐ KỸ THUẬT (khớp với ESP32 pipeline)
# ─────────────────────────────────────────────
SAMPLE_RATE     = 16000   # Hz
DURATION        = 1.0     # giây
NUM_SAMPLES     = int(SAMPLE_RATE * DURATION)  # = 16000 mẫu

# Bộ lọc thông dải (bandpass)
BP_LOW          = 80      # Hz
BP_HIGH         = 7900    # Hz
BP_ORDER        = 4       # Butterworth bậc 4

# Pre-emphasis
PRE_EMPHASIS    = 0.97

# Chuẩn hóa biên độ
PEAK_TARGET     = 0.9

# MFCC (khớp với esp-tflite-micro)
FRAME_LEN       = 0.025   # 25ms
FRAME_STEP      = 0.010   # 10ms
NUM_MEL         = 26
NUM_MFCC        = 13
PREEMPH_IN_LIB  = 0.0    # Đặt 0 vì đã tự áp dụng pre-emphasis trước


# ─────────────────────────────────────────────
# CÁC HÀM XỬ LÝ
# ─────────────────────────────────────────────

def load_and_normalize_length(path, sr=SAMPLE_RATE):
    """Load file WAV, resample về 16kHz, chuyển mono, cắt/pad về đúng 1 giây."""
    audio, orig_sr = librosa.load(path, sr=sr, mono=True)

    # Pad nếu ngắn hơn 1 giây, cắt nếu dài hơn
    if len(audio) < NUM_SAMPLES:
        audio = np.pad(audio, (0, NUM_SAMPLES - len(audio)))
    else:
        audio = audio[:NUM_SAMPLES]

    return audio.astype(np.float32)


def bandpass_filter(audio, sr=SAMPLE_RATE, low=BP_LOW, high=BP_HIGH, order=BP_ORDER):
    """Bộ lọc thông dải Butterworth bậc 4 (80–8000 Hz)."""
    sos = butter(order, [low, high], btype='band', fs=sr, output='sos')
    return sosfilt(sos, audio).astype(np.float32)


def pre_emphasis(audio, alpha=PRE_EMPHASIS):
    """Pre-emphasis: y[t] = x[t] - alpha * x[t-1], áp dụng trên TOÀN clip."""
    return np.append(audio[0], audio[1:] - alpha * audio[:-1]).astype(np.float32)


def peak_normalize(audio, target=PEAK_TARGET):
    """Chuẩn hóa biên độ về peak = 0.9."""
    peak = np.max(np.abs(audio))
    if peak > 1e-6:
        audio = audio / peak * target
    return audio.astype(np.float32)


def extract_mfcc(audio, sr=SAMPLE_RATE):
    """
    Trích xuất MFCC bằng python_speech_features.
    preemph=0.0 vì đã tự áp dụng pre-emphasis trước.
    Kết quả: ma trận (98, 13).
    """
    features = mfcc(
        audio,
        samplerate=sr,
        winlen=FRAME_LEN,
        winstep=FRAME_STEP,
        numcep=NUM_MFCC,
        nfilt=NUM_MEL,
        preemph=PREEMPH_IN_LIB,   # = 0.0 vì đã tự làm trước
        appendEnergy=False
    )
    return features  # shape: (98, 13)


# ─────────────────────────────────────────────
# VẼ VÀ LƯU SPECTROGRAM
# ─────────────────────────────────────────────

def save_spectrogram(audio_raw, audio_processed, mfcc_features, filename, out_dir, sr=SAMPLE_RATE):
    """Vẽ 4 panel: waveform gốc, waveform sau xử lý, mel spectrogram, MFCC."""
    fig = plt.figure(figsize=(14, 10), facecolor='#1a1a2e')
    fig.suptitle(f'Audio Analysis — {filename}', color='white', fontsize=14, fontweight='bold', y=0.98)

    gs = gridspec.GridSpec(2, 2, figure=fig, hspace=0.45, wspace=0.35)

    ax_color   = '#e0e0e0'
    grid_color = '#333355'

    def style_ax(ax, title):
        ax.set_facecolor('#0d0d1a')
        ax.set_title(title, color='white', fontsize=10, fontweight='bold', pad=8)
        ax.tick_params(colors=ax_color, labelsize=8)
        ax.spines[:].set_color(grid_color)
        ax.grid(True, color=grid_color, linewidth=0.5, alpha=0.7)

    t = np.linspace(0, DURATION, len(audio_raw))

    # Panel 1: Waveform gốc
    ax1 = fig.add_subplot(gs[0, 0])
    ax1.plot(t, audio_raw, color='#4fc3f7', linewidth=0.6, alpha=0.9)
    ax1.set_xlabel('Thời gian (s)', color=ax_color, fontsize=8)
    ax1.set_ylabel('Biên độ', color=ax_color, fontsize=8)
    style_ax(ax1, '① Waveform gốc (raw)')

    # Panel 2: Waveform sau xử lý
    ax2 = fig.add_subplot(gs[0, 1])
    ax2.plot(t, audio_processed, color='#81c784', linewidth=0.6, alpha=0.9)
    ax2.set_xlabel('Thời gian (s)', color=ax_color, fontsize=8)
    ax2.set_ylabel('Biên độ', color=ax_color, fontsize=8)
    style_ax(ax2, '② Waveform sau xử lý (bandpass + pre-emph + norm)')

    # Panel 3: Mel Spectrogram
    ax3 = fig.add_subplot(gs[1, 0])
    mel = librosa.feature.melspectrogram(y=audio_processed, sr=sr, n_mels=NUM_MEL,
                                          fmin=BP_LOW, fmax=BP_HIGH)
    mel_db = librosa.power_to_db(mel, ref=np.max)
    img3 = librosa.display.specshow(mel_db, sr=sr, x_axis='time', y_axis='mel',
                                     fmin=BP_LOW, fmax=BP_HIGH, ax=ax3, cmap='magma')
    ax3.set_xlabel('Thời gian (s)', color=ax_color, fontsize=8)
    ax3.set_ylabel('Tần số (Hz)', color=ax_color, fontsize=8)
    style_ax(ax3, f'③ Mel Spectrogram ({NUM_MEL} filters, 80–8000 Hz)')
    plt.colorbar(img3, ax=ax3, format='%+2.0f dB').ax.yaxis.set_tick_params(color=ax_color, labelcolor=ax_color)

    # Panel 4: MFCC
    ax4 = fig.add_subplot(gs[1, 1])
    img4 = ax4.imshow(mfcc_features.T, origin='lower', aspect='auto', cmap='coolwarm',
                      extent=[0, DURATION, 0, NUM_MFCC])
    ax4.set_xlabel('Thời gian (s)', color=ax_color, fontsize=8)
    ax4.set_ylabel('MFCC index', color=ax_color, fontsize=8)
    style_ax(ax4, f'④ MFCC ({NUM_MFCC} coeff × {mfcc_features.shape[0]} frames)')
    plt.colorbar(img4, ax=ax4).ax.yaxis.set_tick_params(color=ax_color, labelcolor=ax_color)

    # Thống kê nhỏ
    info = (f'Sample rate: {sr} Hz  |  Duration: {DURATION}s  |  '
            f'Frames: {mfcc_features.shape[0]}  |  MFCC shape: {mfcc_features.shape}')
    fig.text(0.5, 0.01, info, ha='center', color='#aaaaaa', fontsize=8)

    out_path = os.path.join(out_dir, f'{os.path.splitext(filename)[0]}_spectrogram.png')
    plt.savefig(out_path, dpi=120, bbox_inches='tight', facecolor=fig.get_facecolor())
    plt.close(fig)
    return out_path


# ─────────────────────────────────────────────
# HÀM CHÍNH
# ─────────────────────────────────────────────

def process_file(wav_path, out_audio_dir, out_spec_dir):
    """Xử lý 1 file WAV: tiền xử lý → lưu WAV → MFCC → Spectrogram."""
    filename = os.path.basename(wav_path)
    print(f'  Processing: {filename}')

    # 1. Load
    audio_raw = load_and_normalize_length(wav_path)

    # 2. Bandpass filter (80–8000 Hz, Butterworth bậc 4)
    audio = bandpass_filter(audio_raw)

    # 3. Pre-emphasis (α = 0.97) — áp dụng trên TOÀN clip trước MFCC
    audio = pre_emphasis(audio)

    # 4. Peak normalization về 0.9
    audio = peak_normalize(audio)

    # 5. Lưu file WAV đã xử lý
    wav_out = os.path.join(out_audio_dir, f'{os.path.splitext(filename)[0]}_processed.wav')
    sf.write(wav_out, audio, SAMPLE_RATE, subtype='PCM_16')

    # 6. Trích xuất MFCC (preemph=0 vì đã tự làm)
    mfcc_feat = extract_mfcc(audio)  # (98, 13)

    # 7. Lưu MFCC dưới dạng .npy
    npy_out = os.path.join(out_audio_dir, f'{os.path.splitext(filename)[0]}_mfcc.npy')
    np.save(npy_out, mfcc_feat)

    # 8. Vẽ & lưu Spectrogram
    spec_out = save_spectrogram(audio_raw, audio, mfcc_feat, filename, out_spec_dir)

    return wav_out, npy_out, spec_out, mfcc_feat.shape


def main():
    parser = argparse.ArgumentParser(description='Tiền xử lý audio cho Đồ Án 1 KWS')
    parser.add_argument('--input_dir',  type=str, required=True,
                        help='Folder chứa các file .wav đầu vào')
    parser.add_argument('--output_dir', type=str, default='output_test',
                        help='Folder lưu kết quả (mặc định: output_test)')
    args = parser.parse_args()

    # Tạo thư mục output
    out_audio_dir = os.path.join(args.output_dir, 'processed_audio')
    out_spec_dir  = os.path.join(args.output_dir, 'spectrograms')
    os.makedirs(out_audio_dir, exist_ok=True)
    os.makedirs(out_spec_dir,  exist_ok=True)

    # Tìm tất cả file WAV
    wav_files = sorted([
        os.path.join(args.input_dir, f)
        for f in os.listdir(args.input_dir)
        if f.lower().endswith('.wav')
    ])

    if not wav_files:
        print(f'[!] Không tìm thấy file .wav nào trong: {args.input_dir}')
        return

    print(f'\n{"="*55}')
    print(f'  Đồ Án 1 — Audio Preprocessing Pipeline')
    print(f'{"="*55}')
    print(f'  Input folder : {args.input_dir}')
    print(f'  Output folder: {args.output_dir}')
    print(f'  Files found  : {len(wav_files)}')
    print(f'{"="*55}')
    print(f'  Config: {SAMPLE_RATE}Hz | Mono | Bandpass {BP_LOW}–{BP_HIGH}Hz')
    print(f'          Pre-emphasis α={PRE_EMPHASIS} | Peak norm={PEAK_TARGET}')
    print(f'          MFCC: {NUM_MEL} Mel filters, {NUM_MFCC} coeff, frame {int(FRAME_LEN*1000)}ms/{int(FRAME_STEP*1000)}ms')
    print(f'{"="*55}\n')

    results = []
    for wav_path in wav_files:
        try:
            wav_out, npy_out, spec_out, shape = process_file(wav_path, out_audio_dir, out_spec_dir)
            results.append((os.path.basename(wav_path), shape, '✓'))
        except Exception as e:
            results.append((os.path.basename(wav_path), None, f'✗ {e}'))
            print(f'  [ERROR] {os.path.basename(wav_path)}: {e}')

    # Tóm tắt
    print(f'\n{"="*55}')
    print(f'  KẾT QUẢ XỬ LÝ')
    print(f'{"="*55}')
    for name, shape, status in results:
        shape_str = str(shape) if shape else 'N/A'
        print(f'  {status}  {name:<30} MFCC shape: {shape_str}')

    ok = sum(1 for _, _, s in results if s == '✓')
    print(f'\n  Thành công: {ok}/{len(results)} files')
    print(f'  Output audio : {out_audio_dir}/')
    print(f'  Output specs : {out_spec_dir}/')
    print(f'{"="*55}\n')


if __name__ == '__main__':
    main()