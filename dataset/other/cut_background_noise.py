"""
cut_background_noise.py
────────────────────────
Cắt các file WAV dài trong thư mục background noise thành các đoạn 1 giây.
Tổng số file đầu ra không vượt quá MAX_CLIPS.

Cách dùng:
    python cut_background_noise.py

Chỉnh 3 biến bên dưới cho phù hợp với máy bạn.
"""

import os
import math
import sys
import soundfile as sf
import numpy as np

sys.stdout.reconfigure(encoding='utf-8')

# ── Cấu hình ──────────────────────────────────────────────────────────────────
INPUT_DIR   = r"C:\Users\ASUS\Downloads\Compressed\speech_commands_v0.02\_background_noise_"      # Thư mục chứa các file WAV dài
OUTPUT_DIR  = r"dataset_1s\unknown"       # Thư mục đầu ra
SAMPLE_RATE = 16000                   # Hz — khớp với pipeline hệ thống
MAX_CLIPS   = 200                     # Tổng số file đầu ra tối đa
# ─────────────────────────────────────────────────────────────────────────────

def cut_noise_files():
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    # Lấy danh sách file WAV đầu vào
    wav_files = sorted([
        f for f in os.listdir(INPUT_DIR) if f.lower().endswith(".wav")
    ])

    if not wav_files:
        print(f"✗ Không tìm thấy file WAV trong '{INPUT_DIR}'")
        return

    print(f"Tim thay {len(wav_files)} file trong '{INPUT_DIR}'")

    # ── Đếm tổng số giây khả dụng từ tất cả file ────────────────────────────
    file_info = []
    total_seconds = 0

    for fname in wav_files:
        fpath = os.path.join(INPUT_DIR, fname)
        try:
            info      = sf.info(fpath)
            duration  = info.duration                      # giây
            n_clips   = int(math.floor(duration))          # số đoạn 1s có thể cắt
            if n_clips > 0:
                file_info.append((fname, fpath, n_clips, info.samplerate))
                total_seconds += n_clips
                print(f"  {fname:<40} {duration:.1f}s  →  {n_clips} đoạn")
        except Exception as e:
            print(f"  ✗ Lỗi đọc {fname}: {e}")

    if total_seconds == 0:
        print("✗ Không có dữ liệu khả dụng.")
        return

    print(f"\nTổng đoạn khả dụng : {total_seconds}")
    print(f"Giới hạn đầu ra    : {MAX_CLIPS}")

    # ── Tính số clip lấy từ mỗi file (phân bổ đều) ──────────────────────────
    # Mỗi file lấy tỷ lệ tương ứng với thời lượng của nó
    clips_per_file = []
    remaining = MAX_CLIPS

    for i, (fname, fpath, n_clips, sr) in enumerate(file_info):
        if i == len(file_info) - 1:
            # File cuối lấy phần còn lại để đảm bảo tổng đúng MAX_CLIPS
            quota = remaining
        else:
            quota = min(n_clips, round(MAX_CLIPS * n_clips / total_seconds))
        quota = min(quota, n_clips, remaining)
        clips_per_file.append(quota)
        remaining -= quota

    # ── Cắt file ────────────────────────────────────────────────────────────
    print("\nĐang cắt file...")
    clip_idx = 0

    for (fname, fpath, n_clips, sr), quota in zip(file_info, clips_per_file):
        if quota <= 0:
            continue

        try:
            audio, orig_sr = sf.read(fpath, dtype="int16")

            # Stereo → mono (lấy kênh trái)
            if audio.ndim == 2:
                audio = audio[:, 0]

            # Resample về 16kHz nếu cần
            if orig_sr != SAMPLE_RATE:
                # Resample đơn giản bằng cách dùng soundfile + scipy
                from scipy.signal import resample_poly
                from math import gcd
                g   = gcd(SAMPLE_RATE, orig_sr)
                up  = SAMPLE_RATE // g
                dn  = orig_sr // g
                audio = resample_poly(audio, up, dn).astype(np.int16)
                print(f"  Resample {orig_sr}→{SAMPLE_RATE} Hz: {fname}")

            total_samples = len(audio)
            clip_len      = SAMPLE_RATE   # 1 giây = 16000 mẫu

            # Tính bước nhảy để lấy đủ quota clip phân bố đều trong file
            if quota >= n_clips:
                # Lấy hết tất cả clip liên tiếp
                starts = [i * clip_len for i in range(quota)]
            else:
                # Lấy quota clip phân bố đều trong file
                step   = (n_clips - 1) / max(quota - 1, 1)
                starts = [int(round(i * step)) * clip_len for i in range(quota)]

            saved = 0
            for start in starts:
                end = start + clip_len
                if end > total_samples:
                    break

                clip     = audio[start:end]
                out_name = f"unknown_{clip_idx:04d}.wav"
                out_path = os.path.join(OUTPUT_DIR, out_name)
                sf.write(out_path, clip, SAMPLE_RATE, subtype="PCM_16")

                clip_idx += 1
                saved    += 1

            print(f"  ✓ {fname:<40} → {saved} file")

        except Exception as e:
            print(f"  ✗ Lỗi xử lý {fname}: {e}")

    print(f"\n✓ Hoàn thành: {clip_idx} file đã lưu vào '{OUTPUT_DIR}'")


if __name__ == "__main__":
    cut_noise_files()