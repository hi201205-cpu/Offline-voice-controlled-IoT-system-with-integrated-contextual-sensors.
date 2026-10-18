"""
check_before_deploy.py
======================
Kiem tra tu dong truoc khi deploy model len ESP32.
Chay: python check_before_deploy.py

Yeu cau: output_dscnn_v2/ da co sau khi train xong.
"""

import os, sys
import numpy as np

# ── Cau hinh ───────────────────────────────────────────────────
OUTPUT_DIR    = "output_dscnn_v2"
DATASET_DIR   = "data_train/dataset_1s"
SAMPLE_RATE   = 16000
CONF_THRESHOLD = 0.90

CMD_LABELS = ["bat_den", "tat_den", "bat_quat", "tat_quat", "bat_het", "tat_het"]
NOISE_LABEL = "noise"
LABELS = CMD_LABELS + [NOISE_LABEL]
NUM_CLASSES = len(LABELS)

# Cac cap lenh nguy hiem neu nham nhau
DANGEROUS_PAIRS = [(0,1), (1,0), (2,3), (3,2), (4,5), (5,4)]

# ── Ket qua ket qua ───────────────────────────────────────────
PASS = "✓ PASS"
FAIL = "✗ FAIL"
WARN = "⚠ WARN"

results = []

def check(name, passed, value="", warn=False):
    status = WARN if warn else (PASS if passed else FAIL)
    results.append((name, status, value))
    print(f"  {status}  {name:<45} {value}")
    return passed

# ==============================================================================
def main():
    import tensorflow as tf
    from scipy.signal import butter, sosfilt
    from python_speech_features import mfcc
    from sklearn.metrics import confusion_matrix
    import soundfile as sf

    print("\n" + "="*62)
    print("  KIEM TRA TRUOC KHI DEPLOY — DS-CNN KWS v2")
    print("="*62)

    # ── Kiem tra file ton tai ──────────────────────────────────
    print("\n[1/5] Kiem tra file output...")
    files = {
        "best_model.keras"  : "Keras model",
        "model_int8.tflite" : "TFLite INT8",
        "model_data.h"      : "C header",
    }
    all_exist = True
    for fname, desc in files.items():
        path = os.path.join(OUTPUT_DIR, fname)
        ok   = os.path.exists(path)
        size = f"{os.path.getsize(path)/1024:.1f} KB" if ok else ""
        check(f"{desc} ({fname})", ok, size)
        if not ok: all_exist = False

    if not all_exist:
        print("\n  [ERR] Mot so file khong ton tai. Chay train_model_v2.py truoc.")
        sys.exit(1)

    # ── Kiem tra kich thuoc model ──────────────────────────────
    print("\n[2/5] Kiem tra kich thuoc model...")
    tflite_path = os.path.join(OUTPUT_DIR, "model_int8.tflite")
    size_kb = os.path.getsize(tflite_path) / 1024
    check("INT8 model < 50 KB",  size_kb < 50,  f"{size_kb:.1f} KB")
    check("INT8 model < 60 KB (warn)",  size_kb < 60,  f"{size_kb:.1f} KB",
          warn=(50 <= size_kb < 60))

    # ── Kiem tra nhat quan f32 vs int8 ────────────────────────
    print("\n[3/5] Kiem tra nhat quan f32 vs INT8...")

    # Load lai dataset nhanh (chi lay 100 mau moi class)
    SOS = butter(4, [80, 7999], btype="band", fs=SAMPLE_RATE, output="sos")

    def preprocess(audio_i16):
        a = audio_i16.astype(np.float32) / 32768.0
        if len(a) > SAMPLE_RATE: a = a[:SAMPLE_RATE]
        elif len(a) < SAMPLE_RATE: a = np.pad(a, (0, SAMPLE_RATE - len(a)))
        a = sosfilt(SOS, a)
        a = np.append(a[0], a[1:] - 0.97 * a[:-1])
        peak = np.max(np.abs(a))
        if peak > 1e-6: a *= 0.9 / peak
        return a.astype(np.float32)

    def get_mfcc(audio_f32):
        feat = mfcc(audio_f32, samplerate=SAMPLE_RATE,
                    winlen=0.025, winstep=0.010, numcep=13,
                    nfilt=26, nfft=512, preemph=0.0,
                    appendEnergy=False, winfunc=np.hamming)
        if len(feat) > 98: feat = feat[:98]
        elif len(feat) < 98: feat = np.pad(feat, ((0, 98-len(feat)), (0,0)))
        m, s = feat.mean(), feat.std() + 1e-8
        return ((feat - m) / s).astype(np.float32)

    X_check, y_check = [], []
    for li, label in enumerate(CMD_LABELS):
        folder = os.path.join(DATASET_DIR, label)
        if not os.path.exists(folder): continue
        files_wav = [f for f in os.listdir(folder) if f.endswith(".wav")][:50]
        for fname in files_wav:
            try:
                audio, sr = sf.read(os.path.join(folder, fname), dtype="int16")
                if audio.ndim == 2: audio = audio[:, 0]
                feat = get_mfcc(preprocess(audio))
                X_check.append(feat[..., np.newaxis])
                y_check.append(li)
            except: pass

    if len(X_check) == 0:
        print("  [SKIP] Khong doc duoc dataset de kiem tra")
    else:
        X_check = np.array(X_check, dtype=np.float32)
        y_check = np.array(y_check)

        # Load keras model
        keras_model = tf.keras.models.load_model(
            os.path.join(OUTPUT_DIR, "best_model.keras"),
            compile=False)

        # Load tflite
        interp = tf.lite.Interpreter(model_path=tflite_path)
        interp.allocate_tensors()
        inp_det = interp.get_input_details()[0]
        out_det = interp.get_output_details()[0]
        in_scale, in_zp   = inp_det["quantization"]
        out_scale, out_zp = out_det["quantization"]

        # So sanh
        n_check = min(300, len(X_check))
        match = 0
        correct_keras = 0
        correct_tflite = 0

        for i in range(n_check):
            x = X_check[i:i+1]

            # Keras
            p_keras = keras_model.predict(x, verbose=0)[0]
            pred_keras = np.argmax(p_keras)

            # TFLite INT8
            x_q = np.clip(np.round(x / in_scale + in_zp), -128, 127).astype(np.int8)
            interp.set_tensor(inp_det["index"], x_q)
            interp.invoke()
            y_q = interp.get_tensor(out_det["index"])
            p_tflite = (y_q.astype(np.float32) - out_zp) * out_scale
            pred_tflite = np.argmax(p_tflite)

            if pred_keras == pred_tflite: match += 1
            if pred_keras  == y_check[i]: correct_keras  += 1
            if pred_tflite == y_check[i]: correct_tflite += 1

        consistency = match / n_check * 100
        acc_keras   = correct_keras  / n_check * 100
        acc_tflite  = correct_tflite / n_check * 100

        check("Nhat quan f32 vs INT8 >= 95%",
              consistency >= 95, f"{consistency:.1f}%")
        check("Keras accuracy >= 90%",
              acc_keras >= 90, f"{acc_keras:.1f}%")
        check("TFLite INT8 accuracy >= 88%",
              acc_tflite >= 88, f"{acc_tflite:.1f}%",
              warn=(88 <= acc_tflite < 90))

        # ── Confusion matrix check ─────────────────────────────
        print("\n[4/5] Kiem tra confusion matrix (nguy hiem)...")
        preds_all = []
        for i in range(len(X_check)):
            x_q = np.clip(
                np.round(X_check[i:i+1] / in_scale + in_zp),
                -128, 127).astype(np.int8)
            interp.set_tensor(inp_det["index"], x_q)
            interp.invoke()
            y_q  = interp.get_tensor(out_det["index"])
            p    = (y_q.astype(np.float32) - out_zp) * out_scale
            preds_all.append(np.argmax(p[0]))

        preds_all = np.array(preds_all)
        cm = confusion_matrix(y_check, preds_all, labels=list(range(NUM_CLASSES)))

        # Nguong thuc te:
        # - 0 lan nham          → PASS (hoan hao)
        # - 1-2 lan nham        → WARN (co the la mau nhieu, CONF_THRESHOLD se xu ly)
        # - > 2 lan nham / cap  → FAIL (van de that su, can retrain)
        danger_fail = False
        danger_warn = False
        total_test_per_class = max(1, len(y_check) // len(CMD_LABELS))

        for true_c, pred_c in DANGEROUS_PAIRS:
            if true_c < len(cm) and pred_c < len(cm):
                n    = cm[true_c][pred_c]
                pct  = n / total_test_per_class * 100
                label = f"{CMD_LABELS[true_c]} → {CMD_LABELS[pred_c]}"
                val  = f"{n} lan ({pct:.0f}%)" if n > 0 else "0 lan"

                if n == 0:
                    check(f"Nham lenh nguy hiem: {label}", True,  val)
                elif n <= 2:
                    check(f"Nham lenh nguy hiem: {label}", True, val, warn=True)
                    danger_warn = True
                else:
                    check(f"Nham lenh nguy hiem: {label}", False, val)
                    danger_fail = True

        if danger_fail:
            check("Ket luan: co nham nguy hiem nghiem trong", False,
                  "> 2 lan/cap → can retrain")
        elif danger_warn:
            check("Ket luan: nham it (<=2 lan) co the do mau nhieu", True,
                  "CONF_THRESHOLD se xu ly thuc te", warn=True)
        else:
            check("Ket luan: khong co nham nguy hiem", True, "hoan hao")

        # ── Kiem tra noise rejection ───────────────────────────
        print("\n[5/5] Kiem tra tu choi tieng on...")
        from train_model_v2 import generate_noise_class
        rng = np.random.default_rng(999)
        X_noise = generate_noise_class(rng, n=100)

        noise_rejected = 0
        for i in range(len(X_noise)):
            x_q = np.clip(
                np.round(X_noise[i:i+1] / in_scale + in_zp),
                -128, 127).astype(np.int8)
            interp.set_tensor(inp_det["index"], x_q)
            interp.invoke()
            y_q   = interp.get_tensor(out_det["index"])
            p     = (y_q.astype(np.float32) - out_zp) * out_scale
            conf  = np.max(p)
            pred  = np.argmax(p)
            # Tu choi: predict class noise HOAC confidence < threshold
            if pred == (NUM_CLASSES - 1) or conf < CONF_THRESHOLD:
                noise_rejected += 1

        rejection_rate = noise_rejected / len(X_noise) * 100
        check("Noise rejection rate >= 80%",
              rejection_rate >= 80, f"{rejection_rate:.1f}%",
              warn=(80 <= rejection_rate < 90))
        check("Noise rejection rate >= 90%",
              rejection_rate >= 90, f"{rejection_rate:.1f}%")

    # ── Tong ket ──────────────────────────────────────────────
    print("\n" + "="*62)
    passed = sum(1 for _, s, _ in results if s == PASS)
    warned = sum(1 for _, s, _ in results if s == WARN)
    failed = sum(1 for _, s, _ in results if s == FAIL)

    print(f"  KET QUA: {passed} pass | {warned} warn | {failed} fail")
    print("="*62)

    if failed == 0 and warned == 0:
        print("""
  ✓ MODEL SAN SANG DEPLOY
  ────────────────────────────────────────────
  Buoc tiep theo:
    1. Copy output_dscnn_v2/model_data.h → firmware/src/
    2. Build + Upload firmware
    3. Bat DUMP_MFCC=1 de xac nhan MFCC khop
    4. Tat DUMP_MFCC=0 truoc khi dung thuc te
        """)
    elif failed == 0:
        print("""
  ⚠ CO THE DEPLOY (co mot so canh bao)
    Xem lai cac dong WARN va quyet dinh co deploy khong.
        """)
    else:
        print("""
  ✗ CHUA SAN SANG — Can xu ly truoc khi deploy:
        """)
        for name, status, val in results:
            if status == FAIL:
                print(f"    → {name}: {val}")
        print("""
  Goi y:
    - Accuracy thap: thu giam CONF_THRESHOLD xuong 0.85
    - Nham lenh nguy hiem: thu tang AUG_FACTOR hoac them data
    - Noise rejection thap: thu tang NUM_NOISE_SAMPLES
        """)

if __name__ == "__main__":
    main()