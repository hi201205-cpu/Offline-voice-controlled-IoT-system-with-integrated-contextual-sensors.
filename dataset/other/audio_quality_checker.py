"""
Audio Dataset Quality Checker & Normalizer
For: ESP32 Voice Command Recognition

Features:
  1. Normalize audio files to 1s (trim silence -> pad/crop)
  2. Detect high-noise files (low SNR)
  3. Detect outliers vs the rest of the group (abnormal MFCC)
  4. Export CSV report + print summary to terminal

Install dependencies:
  pip install librosa numpy scipy scikit-learn soundfile pandas tqdm
"""

import os
import sys
import warnings
warnings.filterwarnings("ignore")

import numpy as np
import librosa
import soundfile as sf
import pandas as pd
from pathlib import Path
from tqdm import tqdm
from sklearn.preprocessing import StandardScaler
from sklearn.ensemble import IsolationForest

# ─────────────────────────────────────────────
#  CONFIG
# ─────────────────────────────────────────────
INPUT_DIR    = "./data_cutted"        # folder containing raw WAV files
OUTPUT_DIR   = "./dataset_normalized_TEST" # folder for normalized output
REPORT_CSV   = "./quality_report_TEST.csv" # report file path

TARGET_SEC   = 1.0    # target duration in seconds
SR           = 16000  # sample rate (Hz)

TRIM_TOP_DB  = 20     # silence trim threshold (dB); increase for noisy environments
MIN_DURATION = 0.5    # files shorter than this after trim are rejected (seconds)
MAX_DURATION = 2.0    # files longer than this trigger a warning (seconds)

SNR_THRESHOLD         = 10.0  # files below this SNR (dB) are flagged as noisy
OUTLIER_CONTAMINATION = 0.05  # expected outlier ratio (5%)

# Set FLAT_MODE = True if all WAV files are in one folder (no label subfolders)
FLAT_MODE = False

# ─────────────────────────────────────────────
#  AUDIO PROCESSING
# ─────────────────────────────────────────────

def load_audio(path: Path):
    """Load WAV file and resample to SR. Returns None on error."""
    try:
        audio, sr = librosa.load(str(path), sr=SR, mono=True)
        return audio, sr
    except Exception:
        return None


def trim_silence(audio: np.ndarray, top_db: int = TRIM_TOP_DB) -> np.ndarray:
    """Trim leading and trailing silence."""
    trimmed, _ = librosa.effects.trim(audio, top_db=top_db)
    return trimmed


def normalize_length(audio: np.ndarray, target_samples: int) -> np.ndarray:
    """
    Pad or center-crop audio to exactly target_samples.
    Padding: add zeros symmetrically on both sides (centers the audio).
    Cropping: keep the middle segment (center-crop).
    """
    n = len(audio)
    if n == target_samples:
        return audio
    if n < target_samples:
        pad_total = target_samples - n
        pad_left  = pad_total // 2
        pad_right = pad_total - pad_left
        return np.pad(audio, (pad_left, pad_right), mode='constant')
    else:
        excess = n - target_samples
        start  = excess // 2
        return audio[start: start + target_samples]


def normalize_amplitude(audio: np.ndarray) -> np.ndarray:
    """Peak-normalize to [-1, 1] with a small headroom."""
    peak = np.max(np.abs(audio))
    if peak > 0:
        audio = audio / peak
    return audio * 0.95


# ─────────────────────────────────────────────
#  QUALITY METRICS
# ─────────────────────────────────────────────

def estimate_snr(audio: np.ndarray, sr: int, noise_sec: float = 0.05) -> float:
    """
    Simple SNR estimate:
    - Noise  = RMS energy of the first noise_sec (usually pre-speech silence)
    - Signal = RMS energy of the full file
    Returns SNR in dB. Higher is better (>20dB = clean, <10dB = noisy).
    """
    noise_samples = int(noise_sec * sr)
    if len(audio) <= noise_samples:
        return 0.0
    noise_rms  = np.sqrt(np.mean(audio[:noise_samples] ** 2) + 1e-10)
    signal_rms = np.sqrt(np.mean(audio ** 2) + 1e-10)
    return float(20 * np.log10(signal_rms / noise_rms))


def extract_mfcc_features(audio: np.ndarray, sr: int) -> np.ndarray:
    """
    Extract MFCC + delta + delta-delta, flattened into a feature vector.
    Used for comparing files against each other (outlier detection).
    """
    mfcc   = librosa.feature.mfcc(y=audio, sr=sr, n_mfcc=13, n_fft=512, hop_length=160)
    delta  = librosa.feature.delta(mfcc)
    delta2 = librosa.feature.delta(mfcc, order=2)
    return np.concatenate([
        np.mean(mfcc,   axis=1), np.std(mfcc,   axis=1),
        np.mean(delta,  axis=1), np.std(delta,  axis=1),
        np.mean(delta2, axis=1), np.std(delta2, axis=1),
    ])


def compute_spectral_stats(audio: np.ndarray, sr: int) -> dict:
    """Compute additional spectral statistics for quality assessment."""
    stft = np.abs(librosa.stft(audio, n_fft=512))
    return {
        "spectral_centroid":  round(float(np.mean(librosa.feature.spectral_centroid(S=stft, sr=sr))), 1),
        "spectral_bandwidth": round(float(np.mean(librosa.feature.spectral_bandwidth(S=stft, sr=sr))), 1),
        "zero_crossing_rate": round(float(np.mean(librosa.feature.zero_crossing_rate(audio))), 4),
        "rms_energy":         round(float(np.sqrt(np.mean(audio ** 2))), 4),
    }


# ─────────────────────────────────────────────
#  MAIN PIPELINE
# ─────────────────────────────────────────────

def collect_files(input_dir: Path) -> list:
    """Collect all WAV files with their label (parent folder name)."""
    files = []
    if FLAT_MODE:
        for f in sorted(input_dir.glob("*.wav")):
            files.append({"path": f, "label": "unknown"})
    else:
        for label_dir in sorted(input_dir.iterdir()):
            if label_dir.is_dir():
                for f in sorted(label_dir.glob("*.wav")):
                    files.append({"path": f, "label": label_dir.name})
    return files


def process_dataset(input_dir: Path, output_dir: Path) -> pd.DataFrame:
    target_samples = int(TARGET_SEC * SR)
    files = collect_files(input_dir)

    if not files:
        print(f"[ERROR] No WAV files found in: {input_dir}")
        sys.exit(1)

    print(f"\nFound {len(files)} WAV files")
    print(f"Target: {TARGET_SEC}s ({target_samples} samples) @ {SR}Hz\n")

    records     = []   # one dict per file
    feat_list   = []   # MFCC feature vectors (only OK files), parallel with feat_to_rec
    feat_to_rec = []   # feat_to_rec[i] = index in records for feat_list[i]

    # ── Pass 1: load, normalize, save, collect metrics ──
    for item in tqdm(files, desc="Processing", unit="file"):
        path  = item["path"]
        label = item["label"]

        result = load_audio(path)
        if result is None:
            records.append({
                "file": path.name, "label": label,
                "status": "ERROR_LOAD",
                "original_duration": None, "trimmed_duration": None,
                "snr_db": None, "issue": "Failed to load file",
                "spectral_centroid": None, "spectral_bandwidth": None,
                "zero_crossing_rate": None, "rms_energy": None,
                "is_outlier": None, "outlier_score": None,
            })
            continue

        audio_raw, _ = result
        original_dur = round(len(audio_raw) / SR, 3)

        audio_trimmed = trim_silence(audio_raw)
        trimmed_dur   = len(audio_trimmed) / SR

        if trimmed_dur < MIN_DURATION:
            records.append({
                "file": path.name, "label": label,
                "status": "REJECTED_TOO_SHORT",
                "original_duration": original_dur,
                "trimmed_duration":  round(trimmed_dur, 3),
                "snr_db": None,
                "issue": f"Only {trimmed_dur:.2f}s after trim (min={MIN_DURATION}s)",
                "spectral_centroid": None, "spectral_bandwidth": None,
                "zero_crossing_rate": None, "rms_energy": None,
                "is_outlier": None, "outlier_score": None,
            })
            continue

        audio_norm = normalize_length(audio_trimmed, target_samples)
        audio_norm = normalize_amplitude(audio_norm)

        out_dir = output_dir / label
        out_dir.mkdir(parents=True, exist_ok=True)
        sf.write(str(out_dir / path.name), audio_norm, SR, subtype="PCM_16")

        snr        = estimate_snr(audio_norm, SR)
        spec_stats = compute_spectral_stats(audio_norm, SR)
        mfcc_feat  = extract_mfcc_features(audio_norm, SR)

        issues = []
        if snr < SNR_THRESHOLD:
            issues.append(f"Low SNR ({snr:.1f}dB)")
        if original_dur > MAX_DURATION:
            issues.append(f"Long source file ({original_dur:.2f}s)")
        if trimmed_dur < 0.7:
            issues.append(f"Short voice after trim ({trimmed_dur:.2f}s)")

        feat_to_rec.append(len(records))   # this feature maps to the record we're about to append
        feat_list.append(mfcc_feat)

        records.append({
            "file": path.name, "label": label,
            "status": "OK",
            "original_duration": original_dur,
            "trimmed_duration":  round(trimmed_dur, 3),
            "snr_db":            round(snr, 1),
            "issue":             "; ".join(issues),
            **spec_stats,
            "is_outlier":    None,
            "outlier_score": None,
        })

    # ── Pass 2: outlier detection ──
    if len(feat_list) < 5:
        print("Not enough OK files for outlier detection (need >= 5).")
        return pd.DataFrame(records)

    print("\nRunning outlier detection...")

    feat_matrix = np.array(feat_list, dtype=np.float32)        # shape (N, F)
    scaler      = StandardScaler()
    feat_scaled = scaler.fit_transform(feat_matrix)             # shape (N, F)

    # Global outlier detection
    iso_global   = IsolationForest(contamination=OUTLIER_CONTAMINATION,
                                   random_state=42, n_estimators=200)
    global_pred  = iso_global.fit_predict(feat_scaled)          # -1 = outlier
    global_score = iso_global.score_samples(feat_scaled)        # lower = more anomalous

    # Per-label outlier detection
    # Build mapping: label -> list of positions in feat_list
    label_to_feat_positions = {}
    for feat_pos, rec_idx in enumerate(feat_to_rec):
        lbl = records[rec_idx]["label"]
        label_to_feat_positions.setdefault(lbl, []).append(feat_pos)

    per_label_flag = np.zeros(len(feat_list), dtype=bool)

    for lbl, feat_positions in label_to_feat_positions.items():
        if len(feat_positions) < 5:
            continue
        # feat_positions is a plain Python list of ints -> valid numpy index
        lbl_feats     = feat_scaled[feat_positions]
        contamination = min(OUTLIER_CONTAMINATION,
                            max(0.01, 1.0 / len(feat_positions)))
        iso_lbl  = IsolationForest(contamination=contamination,
                                   random_state=42, n_estimators=100)
        lbl_pred = iso_lbl.fit_predict(lbl_feats)
        for feat_pos, pred in zip(feat_positions, lbl_pred):
            if pred == -1:
                per_label_flag[feat_pos] = True

    # Write results back into records
    for feat_pos, rec_idx in enumerate(feat_to_rec):
        is_out = bool(global_pred[feat_pos] == -1) or bool(per_label_flag[feat_pos])
        score  = round(float(global_score[feat_pos]), 4)
        records[rec_idx]["is_outlier"]    = is_out
        records[rec_idx]["outlier_score"] = score
        if is_out:
            note     = "Abnormal MFCC features"
            existing = records[rec_idx]["issue"]
            records[rec_idx]["issue"] = (existing + "; " + note) if existing else note

    return pd.DataFrame(records)


# ─────────────────────────────────────────────
#  REPORT
# ─────────────────────────────────────────────

def print_report(df: pd.DataFrame):
    sep = "-" * 60
    print(f"\n{'='*60}")
    print(" DATASET QUALITY REPORT")
    print(f"{'='*60}")

    total    = len(df)
    ok       = (df["status"] == "OK").sum()
    rejected = (df["status"] != "OK").sum()
    noisy    = ((df["status"] == "OK") & (df["snr_db"] < SNR_THRESHOLD)).sum()
    outliers = ((df["status"] == "OK") & (df["is_outlier"] == True)).sum()

    print(f"  Total files        : {total}")
    print(f"  OK (normalized)    : {ok}  ({ok/total*100:.1f}%)")
    print(f"  Rejected           : {rejected}")
    print(f"  Noisy (SNR<{SNR_THRESHOLD}dB)  : {noisy}")
    print(f"  MFCC outliers      : {outliers}")

    print(f"\n{sep}")
    print("  STATS PER LABEL")
    print(sep)
    ok_df = df[df["status"] == "OK"]
    for lbl, g in ok_df.groupby("label"):
        n       = len(g)
        snr_avg = g["snr_db"].mean()
        n_noisy = (g["snr_db"] < SNR_THRESHOLD).sum()
        n_out   = (g["is_outlier"] == True).sum()
        flag    = " (!)" if (n_noisy > n * 0.3 or n_out > n * 0.2) else ""
        print(f"  [{lbl:20s}] {n:4d} files | avg SNR={snr_avg:5.1f}dB | "
              f"noisy={n_noisy} | outlier={n_out}{flag}")

    problem = df[
        (df["status"] == "OK") &
        ((df["snr_db"] < SNR_THRESHOLD) | (df["is_outlier"] == True))
    ].copy()

    if len(problem) > 0:
        print(f"\n{sep}")
        print(f"  FILES TO REVIEW ({len(problem)} files)")
        print(sep)
        for _, row in problem.sort_values("outlier_score").head(20).iterrows():
            print(f"  [{row['label']:15s}] {row['file']:35s} | "
                  f"SNR={row['snr_db']:5.1f}dB | "
                  f"outlier={row['is_outlier']} | {row['issue']}")
        if len(problem) > 20:
            print(f"  ... and {len(problem)-20} more (see CSV)")

    rejected_df = df[df["status"] != "OK"]
    if len(rejected_df) > 0:
        print(f"\n{sep}")
        print(f"  REJECTED FILES ({len(rejected_df)} files)")
        print(sep)
        for _, row in rejected_df.iterrows():
            print(f"  [{row['label']:15s}] {row['file']:35s} | {row['issue']}")

    print(f"\n{'='*60}")
    print(f"  Full report : {REPORT_CSV}")
    print(f"  Output dir  : {OUTPUT_DIR}/")
    print(f"{'='*60}\n")


# ─────────────────────────────────────────────
#  ENTRY POINT
# ─────────────────────────────────────────────

def main():
    input_dir  = Path(INPUT_DIR)
    output_dir = Path(OUTPUT_DIR)

    if not input_dir.exists():
        print(f"[ERROR] Folder not found: {input_dir}")
        print(f"        Create {INPUT_DIR}/ and place WAV files inside.")
        print(f"        Expected structure: {INPUT_DIR}/<label>/<file>.wav")
        sys.exit(1)

    output_dir.mkdir(parents=True, exist_ok=True)
    df = process_dataset(input_dir, output_dir)
    df.to_csv(REPORT_CSV, index=False, encoding="utf-8-sig")
    print_report(df)


if __name__ == "__main__":
    main()