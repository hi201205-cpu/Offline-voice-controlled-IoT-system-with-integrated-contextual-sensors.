# GIẢI THUẬT, MÃ GIẢ VÀ LƯU ĐỒ THUẬT TOÁN HỆ THỐNG

## 1. THUẬT TOÁN VÒNG LẶP CHÍNH (MAIN EXECUTION LOOP & DUAL-CORE SCHEDULING)

```mermaid
flowchart TD
    A([Khởi động ESP32-S3]) --> B[Khởi tạo GPIO, I2S0, UART1, ADC, LEDC PWM]
    B --> C[Nạp cấu hình Wi-Fi & Mã PIN Salted SHA-256 từ NVS Flash]
    C --> D[Khởi tạo Edge Impulse Engine - Arena 12KB]
    D --> E[Tạo Task Firebase trên Core 0: xTaskCreatePinnedToCore]
    E --> F([Vòng lặp loop trên Core 1])
    F --> G[Đọc mẫu âm thanh từ I2S DMA Ring Buffer]
    G --> H{Năng lượng VAD > 7.000.000?}
    H -- Đúng --> I[Chạy suy luận run_classifier_continuous]
    I --> J{Confidence >= 0.70 & Nhãn hợp lệ?}
    J -- Đúng --> K[Thực thi lệnh GPIO tức thì & Đẩy Event vào s_fbQueue]
    J -- Sai --> L[Bỏ qua]
    H -- Sai --> L
    L --> M{Đến chu kỳ đọc cảm biến?}
    M -- Chu kỳ 2s --> N[Đọc Nhiệt độ & Độ ẩm DHT22]
    M -- Chu kỳ 500ms --> O[Đọc Quang trở LDR qua ADC1]
    M -- Liên tục --> P[Đọc UART Radar mmWave LD2410S]
    N --> Q[Đánh giá Động cơ Tự động hóa Rule Engine]
    O --> Q
    P --> Q
    M -- Chưa đến hạn --> Q
    K --> Q
    Q --> R[Quét Ma trận Bàn phím 4x3 & Nút mở cửa]
    R --> S[Kiểm tra Công tắc hành trình & Cập nhật Servo Cửa]
    S --> T[vTaskDelay 1ms]
    T --> F
```

### Mã giả Vòng lặp chính (`src/main.cpp`):
```text
ALGORITHM MainLoop_Core1:
BEGIN
    // 1. Xử lý âm thanh & suy luận TinyML
    audio_samples = I2S_Read_DMA();
    current_energy = Calculate_RMS_Energy(audio_samples);
    
    IF current_energy > 7000000 THEN
        result = run_classifier_continuous(audio_buffer);
        IF result.max_confidence >= 0.70 AND result.label != "unknown" THEN
            Execute_Local_Action(result.label);
            Queue_Send(s_fbQueue, EVENT_VOICE, result.label, result.max_confidence);
        END IF
    END IF
    
    // 2. Thu thập dữ liệu cảm biến định kỳ
    IF CurrentTime - LastSensorRead >= 2000ms THEN
        temp = DHT22_Read_Temperature();
        humid = DHT22_Read_Humidity();
        ldr = ADC_Read_LDR();
        presence = Radar_Read_Presence();
        Evaluate_Automation_Rules(temp, humid, ldr, presence);
        Queue_Send(s_fbQueue, EVENT_TELEMETRY, temp, humid, ldr, presence);
        LastSensorRead = CurrentTime;
    END IF
    
    // 3. Quét bảo mật cửa
    key = Keypad_Scan_Matrix();
    IF key IS_VALID THEN
        Process_Keypad_Input(key);
    END IF
    Check_Door_Limits_And_Timeout();
    
    Yield_CPU(1ms);
END
```

---

## 2. THUẬT TOÁN NHẬN DẠNG GIỌNG NÓI KWS LIÊN TỤC VỚI VAD

```mermaid
flowchart TD
    A[Mẫu âm thanh 16kHz] --> B[Sliding Buffer 16.000 mẫu = 1s]
    B --> C[Tính năng lượng khung RMS]
    C --> D{Năng lượng > 7.000.000?}
    D -- Không --> E[Giữ nguyên trạng thái IDLE]
    D -- Có --> F[Trích xuất 650 đặc trưng MFCC 13x50]
    F --> G[Mô hình INT8 CNN - 8 Operators]
    G --> H[Vector xác suất 10 phần tử]
    H --> I{Max Prob >= 0.70 và != 'unknown'?}
    I -- Không --> J[Hủy kết quả, chờ khung trượt kế tiếp]
    I -- Có --> K[Xác nhận từ khóa Tiếng Việt]
    K --> L[Kích hoạt ngay chân GPIO tương ứng]
    K --> M[Gửi log lên Firebase RTDB]
```

---

## 3. THUẬT TOÁN BẢO MẬT CỬA: SALTED SHA-256 VÀ CHỐNG BRUTE-FORCE

```mermaid
flowchart TD
    A[Người dùng bấm phím trên Ma trận 4x3] --> B{Bấm phím gì?}
    B -- Phím [0-9] --> C[Ghi nhận vào PIN Buffer]
    B -- Phím '*' --> D[Xóa rỗng PIN Buffer]
    B -- Phím '#' --> E[Bắt đầu quy trình xác thực]
    
    E --> F{Hệ thống đang bị Khóa cứng 120s?}
    F -- Đang bị khóa --> G[Buzzer kêu còi từ chối, bỏ qua thao tác]
    F -- Không bị khóa --> H[Lấy Chuỗi Salt ngẫu nhiên từ NVS]
    H --> I[Tính toán SHA256: Input_PIN + Salt]
    I --> J{Khớp với Passcode Hash trong NVS?}
    
    J -- Đúng Hash --> K[Xác thực THÀNH CÔNG]
    K --> L[Reset wrong_attempts = 0]
    K --> M[Kích hoạt Servo mở cửa GPIO39 = 2000µs]
    K --> N[Hẹn giờ tự động đóng sau 5 giây]
    
    J -- Sai Hash --> O[wrong_attempts++]
    O --> P{wrong_attempts >= 4?}
    P -- Chưa đủ 4 lần --> Q[Buzzer bíp 2 tiếng cảnh báo nhập sai]
    P -- Đạt 4 lần liên tiếp --> R[KÍCH HOẠT KHÓA CỨNG BẢN PHÍM 120S]
    R --> S[Bật còi báo động Buzzer GPIO48 liên tục]
    R --> T[Gửi cờ keypad_locked = true lên Firebase]
    R --> U[Đếm ngược 120s: Khóa toàn bộ ngõ vào phím]
```

### Mã giả Quy trình Băm Salted SHA-256 (`src/door_controller.cpp`):
```text
FUNCTION Verify_Passcode(input_pin):
    salt = Read_From_NVS("pin_salt");
    computed_hash = SHA256(input_pin + salt);
    stored_hash = Read_From_NVS("pin_hash");
    
    IF computed_hash == stored_hash THEN
        wrong_count = 0;
        Open_Door_Servo();
        RETURN SUCCESS;
    ELSE
        wrong_count = wrong_count + 1;
        IF wrong_count >= 4 THEN
            lockout_until = CurrentTime + 120_SECONDS;
            Trigger_Buzzer_Alarm();
            Sync_Cloud_Lockout(TRUE);
        END IF
        RETURN FAILED;
    END IF
END FUNCTION
```

---

## 4. THUẬT TOÁN ĐỘNG CƠ TỰ ĐỘNG HÓA VÀ GIẢI QUYẾT XUNG ĐỘT (CONFLICT RESOLUTION)

Khi người dùng cấu hình đồng thời nhiều quy tắc tự động hóa ngữ cảnh, có thể xảy ra tình trạng quy tắc A yêu cầu BẬT quạt (do nhiệt độ > 30°C) nhưng quy tắc B lại yêu cầu TẮT quạt (do khung giờ đêm). Động cơ đã cài đặt thuật toán giải quyết xung đột tất định (Deterministic Conflict Resolution):

```mermaid
flowchart TD
    A[Thu thập dữ liệu cảm biến mới nhất] --> B[Duyệt qua danh sách N quy tắc]
    B --> C{Kiểm tra điều kiện quy tắc i?}
    C -- Thỏa mãn --> D[Thêm hành động vào danh sách Hành động Đề xuất]
    C -- Không thỏa mãn --> E[Bỏ qua]
    D --> F{Còn quy tắc nào chưa xét?}
    E --> F
    F -- Còn --> B
    F -- Hết --> G{Phát hiện xung đột trên cùng 1 thiết bị?}
    
    G -- Không xung đột --> H[Thực thi hành động bình thường]
    G -- Có xung đột --> I[ÁP DỤNG QUY TẮC AN TOÀN TRÊN HẾT: SAFETY FIRST]
    I --> J{Xung đột giữa lệnh BẬT/MỞ và TẮT/ĐÓNG?}
    J -- Có --> K[LỆNH TẮT / ĐÓNG LUÔN THẮNG TUYỆT ĐỐI]
    J -- Đồng cấp thao tác --> L[Ưu tiên Quy tắc có Thứ tự Khai báo Index nhỏ hơn]
    K --> M[Áp lệnh cuối cùng ra chân GPIO phần cứng]
    L --> M
    M --> N[Đồng bộ trạng thái thiết bị lên Firebase]
```

- **Nguyên lý An toàn Tuyệt đối (Safety First)**: Lệnh **TẮT** thiết bị điện hoặc **ĐÓNG** rèm/cửa luôn được trao quyền ưu tiên tối cao nhất so với lệnh BẬT/MỞ nhằm triệt tiêu hoàn toàn rủi ro cháy nổ do quá nhiệt hoặc rò rỉ an ninh khi người dùng vắng nhà.
