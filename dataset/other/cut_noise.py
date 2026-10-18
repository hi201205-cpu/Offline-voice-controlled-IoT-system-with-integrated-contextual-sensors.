import wave
import os


def split_wav(input_path: str, output_dir: str, chunk_duration: float = 1.0):
    """
    Cắt một file WAV thành các đoạn nhỏ có độ dài chunk_duration giây.

    Args:
        input_path:     Đường dẫn đến file WAV đầu vào.
        output_dir:     Thư mục lưu các file WAV đầu ra.
        chunk_duration: Độ dài mỗi đoạn (giây), mặc định 1 giây.
    """
    os.makedirs(output_dir, exist_ok=True)

    with wave.open(input_path, "rb") as wav_file:
        n_channels     = wav_file.getnchannels()
        sample_width   = wav_file.getsampwidth()
        frame_rate     = wav_file.getframerate()
        total_frames   = wav_file.getnframes()
        total_duration = total_frames / frame_rate

        frames_per_chunk = int(frame_rate * chunk_duration)
        base_name = os.path.splitext(os.path.basename(input_path))[0]

        print(f"  🎵 {os.path.basename(input_path)}  |  {frame_rate} Hz  |  "
              f"{n_channels}ch  |  {total_duration:.2f}s")

        chunk_index = 0
        frames_read = 0

        while frames_read < total_frames:
            frames = wav_file.readframes(frames_per_chunk)
            if not frames:
                break

            chunk_frames = len(frames) // (sample_width * n_channels)
            chunk_actual_duration = chunk_frames / frame_rate

            output_filename = f"{base_name}_chunk_{chunk_index:04d}.wav"
            output_path = os.path.join(output_dir, output_filename)

            with wave.open(output_path, "wb") as out_wav:
                out_wav.setnchannels(n_channels)
                out_wav.setsampwidth(sample_width)
                out_wav.setframerate(frame_rate)
                out_wav.writeframes(frames)

            print(f"    ✅ {output_filename}  ({chunk_actual_duration:.3f}s)")
            frames_read += chunk_frames
            chunk_index += 1

    return chunk_index


def split_folder(input_folder: str, output_folder: str, chunk_duration: float = 1.0):
    """
    Duyệt toàn bộ file .wav trong input_folder và cắt từng file.
    Mỗi file sẽ có thư mục con riêng trong output_folder.

    Args:
        input_folder:   Thư mục chứa các file WAV đầu vào.
        output_folder:  Thư mục gốc lưu kết quả đầu ra.
        chunk_duration: Độ dài mỗi đoạn (giây), mặc định 1 giây.
    """
    if not os.path.isdir(input_folder):
        raise NotADirectoryError(f"Không tìm thấy thư mục: {input_folder}")

    wav_files = sorted([
        f for f in os.listdir(input_folder)
        if f.lower().endswith(".wav")
    ])

    if not wav_files:
        print(f"⚠️  Không tìm thấy file .wav nào trong '{input_folder}'.")
        return

    print(f"📂 Input folder : {input_folder}")
    print(f"📁 Output folder: {output_folder}")
    print(f"✂️  Độ dài mỗi đoạn: {chunk_duration}s")
    print(f"🔍 Tìm thấy {len(wav_files)} file .wav\n")
    print("─" * 60)

    total_chunks = 0

    for idx, filename in enumerate(wav_files, start=1):
        input_path = os.path.join(input_folder, filename)
        base_name  = os.path.splitext(filename)[0]
        output_dir = os.path.join(output_folder, base_name)  # thư mục con riêng

        print(f"\n[{idx}/{len(wav_files)}] Đang xử lý:")
        count = split_wav(input_path, output_dir, chunk_duration)
        print(f"  → Đã tạo {count} chunk vào '{output_dir}'")
        total_chunks += count

    print("\n" + "─" * 60)
    print(f"🎉 Hoàn thành! Tổng cộng {total_chunks} chunk từ {len(wav_files)} file.")


def main():
    # ── Chỉnh các giá trị bên dưới theo nhu cầu ──────────────────────────────
    INPUT_FOLDER   = r"D:\Do_an_1_source_code\dataset\0_raw\_noise"    # Thư mục chứa các file WAV đầu vào
    OUTPUT_FOLDER  = r"D:\Do_an_1_source_code\dataset\data_test\data_1s\noise"   # Thư mục lưu kết quả đầu ra
    CHUNK_DURATION = 1.0              # Độ dài mỗi đoạn (giây)
    # ─────────────────────────────────────────────────────────────────────────

    split_folder(INPUT_FOLDER, OUTPUT_FOLDER, CHUNK_DURATION)


if __name__ == "__main__":
    main()