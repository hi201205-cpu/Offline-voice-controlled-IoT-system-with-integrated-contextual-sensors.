"""
normalize_to_1s.py
------------------
Input : ./dataset_raw/<label>/<file>.wav  (any duration)
Output: ./dataset_1s/<label>/<file>.wav   (exactly 1s @ 16kHz)

Rules:
  - duration < 1s  -> trim silence both ends, then center-pad with zeros
  - duration > 1s  -> trim silence both ends, then center-crop
  - duration == 1s -> copy as-is

Install: pip install librosa soundfile numpy tqdm
"""

import sys
from pathlib import Path

import librosa
import numpy as np
import soundfile as sf
from tqdm import tqdm

# ── Config ────────────────────────────────────────────────────────────────────
INPUT_DIR  = r"D:\Do_an_1_source_code\dataset\data_test\data_cutted"   # root folder: INPUT_DIR/<label>/<file>.wav
OUTPUT_DIR = r"D:\Do_an_1_source_code\dataset\data_test\data_1s"    # output mirrors the same subfolder structure
SR         = 16000             # target sample rate (Hz)
TARGET     = SR * 1            # 16000 samples = exactly 1 second
TRIM_DB    = 20                # silence threshold for trimming (dB)
# ─────────────────────────────────────────────────────────────────────────────


def to_1s(audio: np.ndarray) -> np.ndarray:
    """Trim silence, then pad or center-crop to exactly TARGET samples."""
    # 1. trim leading / trailing silence
    trimmed, _ = librosa.effects.trim(audio, top_db=TRIM_DB)
    n = len(trimmed)

    if n == TARGET:
        return trimmed

    if n < TARGET:
        # center-pad with zeros
        pad   = TARGET - n
        left  = pad // 2
        right = pad - left
        return np.pad(trimmed, (left, right), mode="constant")

    # n > TARGET: center-crop
    excess = n - TARGET
    start  = excess // 2
    return trimmed[start : start + TARGET]


def main():
    input_dir  = Path(INPUT_DIR)
    output_dir = Path(OUTPUT_DIR)

    if not input_dir.exists():
        print(f"[ERROR] Input folder not found: {input_dir.resolve()}")
        sys.exit(1)

    wav_files = sorted(input_dir.rglob("*.wav"))
    if not wav_files:
        print(f"[ERROR] No .wav files found under: {input_dir.resolve()}")
        sys.exit(1)

    print(f"Found {len(wav_files)} file(s)  |  target = {TARGET} samples ({SR} Hz, 1s)")
    ok = skipped = error = 0

    for src in tqdm(wav_files, unit="file"):
        try:
            audio, _ = librosa.load(str(src), sr=SR, mono=True)
        except Exception as e:
            tqdm.write(f"[SKIP] {src.name}: {e}")
            error += 1
            continue

        # relative path keeps the label subfolder structure
        rel  = src.relative_to(input_dir)
        dst  = output_dir / rel
        dst.parent.mkdir(parents=True, exist_ok=True)

        normalized = to_1s(audio)
        sf.write(str(dst), normalized, SR, subtype="PCM_16")

        if len(audio) == TARGET:
            skipped += 1
        else:
            ok += 1

    total = ok + skipped + error
    print(f"\nDone — {total} files processed")
    print(f"  Adjusted : {ok}")
    print(f"  Already 1s (copied as-is): {skipped}")
    print(f"  Errors   : {error}")
    print(f"  Output   : {output_dir.resolve()}")


if __name__ == "__main__":
    main()