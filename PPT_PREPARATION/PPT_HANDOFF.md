# TÀI LIỆU CHUYỂN GIAO THIẾT KẾ SLIDE BÁO CÁO BẢO VỆ ĐỒ ÁN TỐT NGHIỆP
## HỆ THỐNG IOT ĐIỀU KHIỂN BẰNG GIỌNG NÓI OFFLINE, TÍCH HỢP CẢM BIẾN NGỮ CẢNH VÀ PHÂN TÍCH CLOUD

> **Dành cho:** AI / Chuyên gia Thiết kế Bài Thuyết trình PowerPoint (Slide Deck Designer & Presenter).  
> **Mục tiêu:** Cung cấp toàn bộ nền tảng kỹ thuật, số liệu thực chứng, kiến trúc hệ thống, mã nguồn đối chiếu và kịch bản thuyết trình bảo vệ đồ án tốt nghiệp đại học / NCKH đạt kết quả xuất sắc.  
> **Nguyên tắc cốt lõi:** Tuyệt đối trung thực với mã nguồn và dữ liệu thực nghiệm; làm nổi bật giải pháp kỹ thuật và đóng góp thực tế; không biến bài thuyết trình thành danh sách liệt kê tính năng thông thường.

---

## MỤC LỤC TỔNG THỂ
1. [Tóm Tắt Đồ Án (Executive Summary)](#1-tóm-tắt-đồ-án-executive-summary)
2. [Vấn Đề Nghiên Cứu, Mục Tiêu và Phạm Vi](#2-vấn-đề-nghiên-cứu-mục-tiêu-và-phạm-vi)
3. [Điểm Nổi Bật Về Kỹ Thuật (Core Technical Contributions)](#3-điểm-nổi-bật-về-kỹ-thuật-core-technical-contributions)
4. [Kiến Trúc Hệ Thống và Luồng Dữ Liệu Toàn Cục](#4-kiến-trúc-hệ-thống-và-luồng-dữ-liệu-toàn-cục)
5. [Kiểm Kê và Phân Loại Chức Năng Triển Khai Thực Tế](#5-kiểm-kê-và-phân-loại-chức-năng-triển-khai-thực-tế)
6. [Phân Tích Chuyên Sâu: TinyML và Nhận Dạng Giọng Nói Tiếng Việt Offline](#6-phân-tích-chuyên-sâu-tinyml-và-nhận-dạng-giọng-nói-tiếng-việt-offline)
7. [Phần Cứng, Cảm Biến Ngữ Cảnh, Điều Khiển và An Ninh Khóa Cửa](#7-phần-cứng-cảm-biến-ngữ-cảnh-điều-khiển-và-an-ninh-khóa-cửa)
8. [Phân Hệ BLE Provisioning, Firebase Cloud, Mobile App và Bảo Mật RBAC](#8-phân-hệ-ble-provisioning-firebase-cloud-mobile-app-và-bảo-mật-rbac)
9. [Bảng Kết Quả Thực Nghiệm và Số Liệu Xác Thực](#9-bảng-kết-quả-thực-nghiệm-và-số-liệu-xác-thực)
10. [Hạn Chế Của Hệ Thống và Hướng Phát Triển](#10-hạn-chế-của-hệ-thống-và-hướng-phát-triển)
11. [Kịch Bản Demo Hệ Thống Thực Tế Xuyên Suốt (End-to-End Demo Script)](#11-kịch-bản-demo-hệ-thống-thực-tế-xuyên-suốt-end-to-end-demo-script)
12. [Dàn Ý Chi Tiết 14 Slide Thuyết Trình Chính (Kèm Speaker Notes)](#12-dàn-ý-chi-tiết-14-slide-thuyết-trình-chính-kèm-speaker-notes)
13. [Đề Xuất 4 Slide Phụ Lục Kỹ Thuật (Backup / Q&A Slides)](#13-đề-xuất-4-slide-phụ-lục-kỹ-thuật-backup--qa-slides)
14. [Danh Sách Thông Tin Cần Bổ Sung Cho Tác Giả](#14-danh-sách-thông-tin-cần-bổ-sung-cho-tác-giả)
15. [Bộ Câu Hỏi Phản Biện Kỹ Thuật Dự Kiến và Gợi Ý Trả Lời](#15-bộ-câu-hỏi-phản-biện-kỹ-thuật-dự-kiến-và-gợi-ý-trả-lời)

---

## 1. TÓM TẮT ĐỒ ÁN (EXECUTIVE SUMMARY)
Hệ thống **Smart Home IoT điều khiển bằng giọng nói tiếng Việt Offline tích hợp cảm biến ngữ cảnh và phân tích Cloud** giải quyết triệt để 3 điểm nghẽn lớn nhất của các thiết bị nhà thông minh hiện nay: sự phụ thuộc đường truyền Internet gây trễ, nguy cơ rò rỉ dữ liệu riêng tư qua Cloud Voice Assistant, và trải nghiệm người dùng gián đoạn khi mạng chập chờn. 

Đề tài ứng dụng công nghệ **TinyML (Edge AI)** trực tiếp trên vi điều khiển **ESP32-S3-WROOM-1 (N16R8: 16MB Flash, 8MB PSRAM)** với mô hình mạng nơ-ron học sâu tích chập lượng tử hóa **INT8 CNN** được biên dịch tĩnh tối ưu qua Edge Impulse EON Compiler. Mô hình phân loại 10 nhãn âm thanh tiếng Việt với độ trễ trích xuất MFCC và suy luận chỉ mất **~30–50 ms**, bộ nhớ Tensor Arena siêu nhẹ chỉ **12.006 bytes (~11.7 KB)**, kích hoạt cơ cấu chấp hành (Rơ-le, Servo) trong thời gian tức thì **< 1.0 ms**, đạt độ tin cậy thực nghiệm **100% qua 748 lượt lệnh thực tế**.

Song song với xử lý cục bộ, hệ thống tích hợp chu trình thiết lập thiết bị thương mại qua **BLE GATT Provisioning** (tự phát hiện mạng Wi-Fi và gán quyền sở hữu Master/Owner chống chiếm quyền), cơ chế an ninh khóa cửa ma trận mã PIN băm **Salted SHA-256** tự động khóa cứng và báo động Telegram khi bị tấn công Brute-force, cùng động cơ **tự động hóa ngữ cảnh (Context-Aware Rule Engine)** chạy độc lập dựa trên cảm biến nhiệt - ẩm DHT22, radar vi sóng hiện diện người LD2410S và quang trở LDR. Toàn bộ trạng thái, lịch sử sự kiện và phân quyền thành viên gia đình (RBAC) được đồng bộ thời gian thực qua **Google Firebase Realtime Database** và quản trị trực quan trên cả **Ứng dụng Native Android (Jetpack Compose)** và **Giao diện Web SPA**.

---

## 2. VẤN ĐỀ NGHIÊN CỨU, MỤC TIÊU VÀ PHẠM VI

### 2.1. Vấn đề thực tiễn (Problem Statement)
1. **Độ trễ và phụ thuộc Internet của Cloud Voice**: Các trợ lý ảo phổ biến (Google Assistant, Alexa, Siri) truyền luồng âm thanh liên tục lên máy chủ Cloud, gây độ trễ từ 1.5 – 3.5 giây, mất hoàn toàn khả năng điều khiển thiết bị khi mất cáp quang hoặc đứt kết nối Wi-Fi.
2. **Xâm phạm quyền riêng tư gia đình**: Việc micro thu âm liên tục truyền dữ liệu sinh trắc học giọng nói ra ngoài Internet tiềm ẩn rủi ro lộ lọt hội thoại nội bộ.
3. **Chi phí phần cứng cao**: Các giải pháp xử lý giọng nói cục bộ trước đây thường đòi hỏi bo mạch Linux nhúng đắt tiền (Raspberry Pi, Jetson Nano) tiêu thụ công suất lớn, khó áp dụng vào thiết bị gia dụng IoT giá rẻ.
4. **Hệ thống tự động hóa thiếu ngữ cảnh**: Phần lớn công tắc thông minh chỉ bật/tắt theo lịch giờ cố định (Timer), thiếu khả năng kết hợp đa cảm biến ngữ cảnh (Nhiệt độ + Con người + Ánh sáng) để hành xử thông minh.
5. **Lỗ hổng bảo mật và phân quyền IoT**: Thiếu phân định rạch ròi giữa Chủ nhà (Master/Owner) và Thành viên gia đình (Member), dễ bị chiếm quyền thiết bị khi dùng chung mã định danh.

### 2.2. Mục tiêu nghiên cứu (Objectives)
- **Mục tiêu 1 (TinyML)**: Xây dựng và triển khai thành công mô hình Keyword Spotting (KWS) tiếng Việt 10 lớp nhãn chạy hoàn toàn Offline trên ESP32-S3 với kích thước Tensor Arena < 15 KB, thời gian suy luận < 60 ms.
- **Mục tiêu 2 (Embedded & Edge Automation)**: Thiết kế phần sụn (firmware) đa nhiệm FreeRTOS 2 lõi, tích hợp động cơ tự động hóa ngữ cảnh cục bộ độc lập với Cloud, ưu tiên nguyên tắc an toàn thiết bị (Safety First).
- **Mục tiêu 3 (Security & Hardware Control)**: Hiện thực hóa hệ thống khóa cửa điện tử sử dụng bàn phím ma trận với thuật toán băm Salted SHA-256 chống trích xuất mã PIN từ bộ nhớ flash NVS, tích hợp cơ chế chống dò mã (Brute-force lockout) và còi báo động khẩn cấp.
- **Mục tiêu 4 (Cloud & Ecosystem)**: Phát triển giải pháp cài đặt Wi-Fi BLE chuẩn thương mại, đồng bộ Cloud Firebase RTDB bảo vệ bằng Security Rules đa tầng, quản lý phân quyền thành viên qua mã mời dùng 1 lần (Single-use Invite Code) trên ứng dụng Android và Web.

### 2.3. Phạm vi đề tài (Scope)
- **Phần cứng**: Bo mạch ESP32-S3-DevKitC-1 (N16R8), Microphone MEMS I2S INMP441, Module 3 Rơ-le cách ly quang, 2 Động cơ Servo (Cửa & Rèm), Cảm biến DHT22, Radar LD2410S/PIR HC-SR501, Quang trở LDR, Bàn phím ma trận 4×3, Còi Buzzer chủ động, LED RGB.
- **Tập lệnh giọng nói**: 10 nhãn tiếng Việt phục vụ các ngữ cảnh cốt lõi trong nhà: Bật/Tắt đèn, Bật/Tắt quạt theo ngữ cảm nóng/lạnh, Mở/Đóng rèm & cửa, Lời chào khi về nhà và Tạm biệt rời nhà.
- **Mạng & Phần mềm**: Kết nối Wi-Fi 2.4GHz kết hợp BLE 5.0; Cloud Firebase Realtime Database & Authentication; Android App Native Kotlin (Compose); Web SPA HTML5/JS.

---

## 3. ĐIỂM NỔI BẬT VỀ KỸ THUẬT (CORE TECHNICAL CONTRIBUTIONS)
1. **Kiến trúc phân chia nhiệm vụ 2 lõi đối xứng (Dual-Core FreeRTOS Decoupling)**:
   - **Core 0 (Thời gian thực âm thanh & AI)**: Chuyên trách đọc dữ liệu I2S DMA liên tục, xử lý thuật toán phát hiện tiếng nói VAD năng lượng, lưu trữ bộ đệm vòng (Ring Buffer) trên PSRAM và chạy mô hình học sâu TinyML.
   - **Core 1 (Quản lý thiết bị & Giao thức)**: Đọc cảm biến, quét phím ma trận, chạy động cơ quy tắc tự động hóa, duy trì WebSocket/SSL đồng bộ Firebase và xử lý sự kiện Bluetooth BLE.
   - **Đóng góp**: Triệt tiêu 100% hiện tượng rớt khung âm thanh (Audio dropouts) hoặc nghẽn mạng SSL gây reset vi điều khiển do ngắt Watchdog.
2. **Bộ đệm thông minh cấp phát trên PSRAM (PSRAM Memory Offloading)**:
   - Di chuyển toàn bộ Ring Buffer 32.000 mẫu âm thanh (64 KB) và bộ đệm cửa sổ suy luận (32 KB) sang bộ nhớ ngoài Octal PSRAM, bảo toàn hơn 300 KB Internal SRAM cho ngăn xếp mạng Wi-Fi, SSL/TLS Handshake và Firebase SDK.
3. **Cơ chế lọc tiếng ồn kép (Dual-stage Noise Suppression & VAD Calibrated)**:
   - Thuật toán VAD tính toán năng lượng khung $E = \sum x[i]^2$ với ngưỡng hiệu chuẩn $7.000.000$ kết hợp cửa sổ giữ âm (Hangover 500ms). Khi không có người nói, bộ suy luận TinyML ngủ hoàn toàn $\rightarrow$ CPU Core 0 rảnh 98%, chip hoạt động mát, ngăn ngừa hiện tượng quá nhiệt.
4. **Bảo mật mã PIN khóa cửa bằng Salted SHA-256 & Khóa chống Brute-force**:
   - Khác với các đồ án IoT thông thường lưu chuỗi mật khẩu bản rõ (`1234`) vào bộ nhớ NVS/EEPROM rất dễ bị trích xuất qua cổng nạp UART, hệ thống tự sinh muối ngẫu nhiên 16-byte (Salt) và tính toán băm SHA-256 32-byte.
   - Sau 4–5 lần nhập sai, bàn phím bị vô hiệu hóa 60–300 giây, còi báo động hú 10 nhịp và tự động gửi cảnh báo khẩn đến Telegram Bot và Mobile App của Chủ nhà.
5. **Cài đặt thiết bị không chạm qua BLE GATT (Commercial-Grade BLE Provisioning)**:
   - Cho phép người dùng kết nối Bluetooth, quét các trạm Wi-Fi lân cận và đẩy cấu hình mạng kèm định danh Chủ nhà (Master UID) qua gói tin thương lượng MTU 512-byte an toàn, không cần chuyển đổi mạng Wi-Fi Access Point thủ công gây gián đoạn 4G/5G.
6. **Mô hình bảo mật đa người dùng và Giao dịch nguyên tử (Atomic Multi-path RBAC)**:
   - Phân cấp vai trò rõ ràng: Chủ nhà (Master) và Thành viên gia đình (Member).
   - Mã mời gia đình tạo động chuẩn 11 ký tự (`HM-XXXXXXXX`), kiểm tra tính hợp lệ và thu hồi tự động sau đúng 1 lần sử dụng.
   - Toàn bộ thao tác chuyển giao quyền Chủ nhà (`transferOwnership`) được thực thi qua **Giao dịch nguyên tử đa nhánh (Atomic Multi-path Update)**, đảm bảo cập nhật đồng thời node hệ thống, vai trò người cũ, vai trò người mới và thông tin thiết bị, chống hoàn toàn rủi ro mất quyền giữa chừng.

---

## 4. KIẾN TRÚC HỆ THỐNG VÀ LUỒNG DỮ LIỆU TOÀN CỤC

### 4.1. Kiến trúc phân tầng (5-Layer Architecture)
```mermaid
flowchart TD
    subgraph L1["TẦNG 1: THIẾT BỊ NGOẠI VI & CƠ CẤU CHẤP HÀNH"]
        MIC["Mic INMP441 (I2S)"]
        SENS["DHT22 | LD2410S / PIR | LDR"]
        KEY["Bàn phím Ma trận 4x3 + Còi Buzzer"]
        ACT["Relay 1 (Đèn) | Relay 2 (Quạt) | Relay 3 (Sân)\nServo Cửa (MG996R) | Servo Rèm (SG90)"]
    end

    subgraph L2["TẦNG 2: XỬ LÝ NHÚNG & TINYML EDGE (ESP32-S3)"]
        direction TB
        CORE0["CORE 0: I2S DMA -> Ring Buffer (PSRAM) -> VAD Energy -> MFCC -> TFLite Micro INT8"]
        CORE1["CORE 1: Quét Phím (Salted SHA-256) -> Động cơ Quy tắc Ngữ cảnh -> Quản lý Trạng thái"]
        NVS["Bộ nhớ NVS: Lưu Cấu hình Wi-Fi, Hash Mã PIN, Token"]
    end

    subgraph L3["TẦNG 3: MẠNG & PHƯƠNG THỨC KẾT NỐI"]
        BLE["BLE 5.0 (Cài đặt Wi-Fi & Claim Master)"]
        WIFI["Wi-Fi 2.4GHz 802.11 b/g/n (HTTPS / WSS / NTP)"]
        OFFLINE["CHẾ ĐỘ OFFLINE: Vẫn điều khiển giọng nói & Khóa cửa 100% khi mất mạng"]
    end

    subgraph L4["TẦNG 4: ĐIỆN TOÁN ĐÁM MÂY (FIREBASE CLOUD & TELEGRAM)"]
        RTDB["Firebase Realtime Database (Trạng thái, Lệnh, Lịch sử, Mã mời)"]
        RULES["Firebase Security Rules (Kiểm tra quyền truy cập RBAC đa tầng)"]
        AUTH["Firebase Authentication (Email/Password & Google Sign-In)"]
        TELE["Telegram Bot API (Thông báo khẩn cấp cảnh báo đột nhập)"]
    end

    subgraph L5["TẦNG 5: GIAO DIỆN NGƯỜI DÙNG ĐA NỀN TẢNG"]
        APP["Mobile App Native Android (Kotlin Jetpack Compose)"]
        WEB["Web Dashboard SPA (HTML5 / Vanilla JS / Chart.js / PWA)"]
    end

    L1 <--> L2
    L2 <--> L3
    L3 <--> L4
    L4 <--> L5
```

### 4.2. Ranh giới Offline và Online (Local Edge vs Cloud Boundary)
Đây là luận điểm kỹ thuật quan trọng nhất cần bảo vệ trước Hội đồng:
- **HOẠT ĐỘNG HOÀN TOÀN OFFLINE (Không cần Internet)**:
  - Nhận diện giọng nói tiếng Việt bằng mô hình TinyML trên bo mạch.
  - Đóng/ngắt đèn phòng, quạt, đèn sân vườn qua Rơ-le.
  - Đóng/mở cửa chính và rèm cửa thông qua Servo và công tắc hành trình.
  - Quét phím ma trận và xác thực mã PIN bằng chuỗi băm Salted SHA-256 lưu trong NVS flash.
  - Cơ chế tự khóa an ninh khi nhập sai mã PIN quá 4 lần.
  - Động cơ tự động hóa ngữ cảnh (tự bật quạt khi nóng kết hợp có người, tự bật đèn sân khi trời tối).
- **CHỨC NĂNG CẦN KẾT NỐI INTERNET (Online Cloud Features)**:
  - Điều khiển và giám sát từ xa qua Ứng dụng di động (Android App) và Web Dashboard.
  - Đồng bộ lịch sử nhật ký hoạt động (lệnh giọng nói, lịch sử mở cửa, sự kiện tự động hóa).
  - Tạo và kích hoạt mã mời liên kết thành viên gia đình (Invite Codes).
  - Phân quyền điều khiển thiết bị cho từng thành viên (RBAC).
  - Gửi tin nhắn cảnh báo đột nhập tức thời qua Telegram Bot.
  - Cập nhật phần sụn từ xa qua mạng Wi-Fi (OTA Update từ GitHub Releases).

---

## 5. KIỂM KÊ VÀ PHÂN LOẠI CHỨC NĂNG TRIỂN KHAI THỰC TẾ

| Nhóm chức năng | Tên tính năng cụ thể | Thuật toán / Thư viện sử dụng | Trạng thái triển khai | Tệp mã nguồn căn cứ | Đưa vào Slide hay Phụ lục? |
|:---|:---|:---|:---:|:---|:---:|
| **Xử lý giọng nói** | Thu âm PCM 16kHz qua I2S DMA | ESP-IDF I2S Driver, Ring Buffer PSRAM | `[ĐÃ KIỂM CHỨNG]` | `src/audio_capture.cpp` | **Slide chính** |
| **Xử lý giọng nói** | Lọc năng lượng tiếng nói VAD | RMS Energy Frame ($E > 7M$), Hangover 500ms | `[ĐÃ KIỂM CHỨNG]` | `src/audio_capture.cpp:300` | **Slide chính** |
| **Xử lý giọng nói** | Trích xuất đặc trưng âm phổ MFCC | 13 Cepstrals, 32 Mel Filters, 256 FFT | `[ĐÃ KIỂM CHỨNG]` | `lib/.../model_variables.h` | **Slide chính** |
| **Xử lý giọng nói** | Phân loại khẩu lệnh 10 nhãn Tiếng Việt | Mạng CNN INT8 EON Compiler (Arena 12KB) | `[ĐÃ KIỂM CHỨNG]` | `src/inference.cpp` | **Slide chính** |
| **Xử lý giọng nói** | Điều khiển cơ cấu chấp hành trực tiếp | Tác động chân GPIO Rơ-le & PWM Servo | `[ĐÃ KIỂM CHỨNG]` | `src/led_relay.cpp` | **Slide chính** |
| **Khóa cửa an ninh**| Bàn phím ma trận 4×3 quét phím | Quét 4 Hàng (Output) - 3 Cột (Input Pullup) | `[ĐÃ KIỂM CHỨNG]` | `src/door_manager.cpp:24` | Slide / Phụ lục |
| **Khóa cửa an ninh**| Xác thực mã PIN Salted SHA-256 | `mbedtls/sha256.h`, Muối ngẫu nhiên 16-byte | `[ĐÃ KIỂM CHỨNG]` | `src/door_manager.cpp:115` | **Slide chính** |
| **Khóa cửa an ninh**| Tự động khóa cứng & còi báo động | Đếm sai 4 lần, khóa 60-300s, còi hú 10 nhịp | `[ĐÃ KIỂM CHỨNG]` | `src/door_manager.cpp:210` | **Slide chính** |
| **Khóa cửa an ninh**| Mở khóa an ninh từ xa qua App/Web | Đồng bộ cờ `door/keypad_locked` | `[ĐÃ KIỂM CHỨNG]` | `src/firebase_manager.cpp` | Slide phụ lục |
| **Tự động hóa** | Động cơ luật ngữ cảnh cục bộ | Safety First Conflict Resolver, Cooldown 2s | `[ĐÃ KIỂM CHỨNG]` | `src/automation.cpp` | **Slide chính** |
| **Cảm biến** | Đo nhiệt độ & độ ẩm DHT22 | Thư viện Adafruit DHT, đọc chu kỳ 2s | `[ĐÃ KIỂM CHỨNG]` | `src/sensors.cpp:45` | Slide / Phụ lục |
| **Cảm biến** | Nhận diện hiện diện người LD2410S/PIR | UART mmWave 24GHz / Digital PIR HC-SR501 | `[ĐÃ KIỂM CHỨNG]` | `src/sensors.cpp:120` | Slide / Phụ lục |
| **Cảm biến** | Cảm biến ánh sáng tự động đèn sân | Quang trở LDR + Bộ lọc trung bình trượt | `[ĐÃ KIỂM CHỨNG]` | `src/sensors.cpp:85` | Slide phụ lục |
| **Kết nối ban đầu** | BLE GATT Wi-Fi Provisioning | NimBLE / ESP32 BLE Server, MTU 512 | `[ĐÃ KIỂM CHỨNG]` | `src/ble_provisioning.cpp` | **Slide chính** |
| **Kết nối ban đầu** | Thiết lập qua Cổng USB Serial | Cú pháp `SET_WIFI:ssid|pass|owner|uid` | `[ĐÃ KIỂM CHỨNG]` | `src/main.cpp:349` | Slide phụ lục |
| **Bảo mật & Quyền** | Phân quyền Master và Member (RBAC) | Firebase Security Rules, kiểm tra vai trò | `[ĐÃ KIỂM CHỨNG]` | `database.rules.json` | **Slide chính** |
| **Bảo mật & Quyền** | Tạo & Đổi mã mời gia đình dùng 1 lần | Chuỗi 11 ký tự `HM-XXXXXXXX`, atomic claim | `[ĐÃ KIỂM CHỨNG]` | `database.rules.json:56` | **Slide chính** |
| **Bảo mật & Quyền** | Chuyển quyền Chủ nhà nguyên tử | Atomic Multi-path `updateChildren()` | `[ĐÃ KIỂM CHỨNG]` | `AuthRepository.kt:335` | **Slide chính** |
| **Đám mây Cloud** | Đồng bộ thời gian thực 2 chiều | WebSocket Stream, Heartbeat 15s | `[ĐÃ KIỂM CHỨNG]` | `src/firebase_manager.cpp` | Slide chính |
| **Đám mây Cloud** | Hàng đợi tác vụ cửa phản hồi nhanh | Polling chu kỳ 2.5 giây (`door/pending`) | `[ĐÃ KIỂM CHỨNG]` | `src/firebase_manager.cpp:450`| Slide phụ lục |
| **Báo động từ xa** | Gửi tin nhắn cảnh báo qua Telegram | Telegram Bot HTTP API, cờ non-blocking | `[ĐÃ KIỂM CHỨNG]` | `src/telegram_manager.cpp` | Slide / Phụ lục |
| **Bảo trì từ xa** | Cập nhật phần sụn Wi-Fi (OTA) | GitHub Releases HTTP Client, phân vùng OTA | `[ĐÃ KIỂM CHỨNG]` | `src/ota_manager.cpp` | Slide phụ lục |
| **Bảo trì từ xa** | Khởi động lại & Khôi phục xuất xưởng | Lệnh `reboot`, `factory_reset` xóa NVS | `[ĐÃ KIỂM CHỨNG]` | `src/firebase_manager.cpp` | Slide phụ lục |
| **Phân tích dữ liệu**| Thống kê & trực quan hóa hành vi | Python Pandas & Matplotlib (`analyze_logs.py`)| `[ĐÃ KIỂM CHỨNG]` | `analyze_logs.py` | **Slide chính** |
| **Giao diện App** | Ứng dụng di động Android Native | Jetpack Compose, Kotlin Flow, Coroutines | `[ĐÃ KIỂM CHỨNG]` | `mobile_app/`, `do_an_2/` | **Slide chính** |
| **Giao diện Web** | Bảng điều khiển Web SPA | HTML5, Vanilla JS, CSS Aurora, Chart.js | `[ĐÃ KIỂM CHỨNG]` | `web/index.html` | Slide chính |

---

## 6. PHÂN TÍCH CHUYÊN SÂU: TINYML VÀ NHẬN DẠNG GIỌNG NÓI TIẾNG VIỆT OFFLINE

### 6.1. Chuỗi thu nhận và tiền xử lý tín hiệu âm thanh
- **Microphone MEMS**: Module INMP441, giao tiếp số qua chuẩn I2S, độ nhạy cao (SNR 61 dBA).
- **Cấu hình I2S DMA**: Tần số lấy mẫu $16.000	ext{ Hz}$, mẫu âm thanh 16-bit Mono (`I2S_CHANNEL_FMT_ONLY_LEFT`).
- **Xử lý bit**: Lấy mẫu thô 32-bit từ DMA và thực hiện dịch phải 11-bit (`I2S_SHIFT_BITS = 11`) để đưa về thang đo 16-bit tuyến tính chuẩn hóa, giảm thiểu nhiễu nền số.
- **Bộ đệm vòng (Circular Ring Buffer)**: Kích thước 32.000 mẫu âm thanh ($2.0	ext{ giây}$), cấp phát trên **PSRAM** (`ps_malloc`), liên tục ghi đè dữ liệu mới nhất mà không gây phân mảnh RAM nội tuyến.

### 6.2. Thuật toán phát hiện tiếng nói (VAD - Voice Activity Detection)
- Kích thước khung phân tích: $20	ext{ ms}$ (320 mẫu âm thanh).
- Công thức tính năng lượng khung:
  $$\text{Energy} = \sum_{i=0}^{319} (x[i])^2$$
- **Ngưỡng năng lượng hiệu chuẩn**: `VAD_ENERGY_THRESH = 7000000L`. 
  - *Lý do hiệu chuẩn*: Ngưỡng ban đầu (3.000.000) thường xuyên bị kích hoạt bởi tiếng gõ bàn phím hoặc tạp âm quạt gió (mức năng lượng 3.5M – 4.5M), khiến hệ thống chạy suy luận AI liên tục. Việc nâng ngưỡng lên 7.000.000 giúp lọc sạch 99.4% tạp âm văn phòng.
- **Cơ chế giữ cửa sổ (Hangover Window)**: Kéo dài $500	ext{ ms}$ (25 khung liên tiếp) sau khi mức năng lượng giảm xuống dưới ngưỡng, tránh việc ngắt quãng các từ tiếng Việt đa âm tiết.

### 6.3. Kỹ thuật Cửa sổ trượt (Sliding Window) và Trích xuất đặc trưng MFCC
- **Cửa sổ trượt (Sliding Window)**: Kích thước $1.000	ext{ ms}$ ($16.000	ext{ mẫu}$), bước nhảy $200	ext{ ms}$ hoặc $300	ext{ ms}$ khi có tiếng nói.
- **Khối DSP trích xuất MFCC (Mel-Frequency Cepstral Coefficients)**:
  - Độ dài khung phân tích: $0.02	ext{ s}$ ($20	ext{ ms}$ = 320 mẫu).
  - Bước trượt khung: $0.02	ext{ s}$ ($20	ext{ ms}$).
  - Biến đổi nhanh Fourier (FFT Length): $256	ext{ điểm}$.
  - Bộ lọc băng tần Mel: $32	ext{ bộ lọc}$.
  - Hệ số Cepstral: $13	ext{ hệ số}$.
  - Hệ số tiền nâng (Pre-emphasis coefficient): $0.98$.
  - **Kích thước vector đặc trưng đầu ra**: $50	ext{ khung} 	imes 13 = \mathbf{650	ext{ giá trị thực}}$.

### 6.4. Mô hình học sâu INT8 CNN và Bộ biên dịch EON
- **Nền tảng**: Edge Impulse Studio (Project ID: `1152015`, Version: `4`).
- **Kiến trúc mạng nơ-ron**:
  1. `Reshape` $[1, 50, 13, 1]$: Chuyển 650 đặc trưng thành ma trận phổ ảnh 2D thời gian - tần số.
  2. `Conv2D`: Trích xuất đặc trưng không gian cục bộ.
  3. `MaxPool2D`: Giảm số chiều dữ liệu.
  4. `Conv2D`: Trích xuất đặc trưng cấp cao.
  5. `MaxPool2D`: Giảm số chiều dữ liệu lần 2.
  6. `Flatten / Reshape`: Làm phẳng tensor đặc trưng.
  7. `FullyConnected (Dense)`: Ánh xạ đặc trưng về 10 nơ-ron phân loại ngõ ra.
  8. `Softmax`: Chuẩn hóa xác suất phân loại.
- **Lượng tử hóa INT8 (Full Integer Quantization)**: Trọng số và hàm kích hoạt đều chuyển đổi từ Float32 sang số nguyên có dấu 8-bit (`int8_t`).
- **Bộ biên dịch EON Compiler**: Chuyển đổi mô hình thành mã nguồn C++ thuần túy, loại bỏ hoàn toàn các toán tử TFLite dư thừa.
- **Thông số tài nguyên đo đạc**:
  - **Kích thước Tensor Arena**: Đúng **12.006 bytes (~11.7 KB)**.
  - **Thời gian suy luận (Inference Latency)**: **~35 – 48 ms** trên nhân Xtensa LX7 240MHz.
  - **KWS-to-Relay Actuation Latency**: **< 1.0 ms** (lệnh điều khiển chân GPIO thực thi ngay lập tức sau khi mô hình kết luận nhãn).

### 6.5. Tập dữ liệu âm thanh huấn luyện (Dataset Breakdown)
- **Tổng số mẫu âm thanh v4**: **17.049 mẫu** (.wav 16kHz, 16-bit Mono, độ dài chuẩn 1.0s).
- **Tổng thời lượng dữ liệu**: Xấp xỉ **4.74 giờ âm thanh**.
- **Tập Train**: 13.822 mẫu ($81.1\%$).
- **Tập Test độc lập**: 3.227 mẫu ($18.9\%$).
- **Bảng phân bổ 10 nhãn chi tiết**:
  1. `bat_den` (1.478 mẫu): Lệnh bật đèn chiếu sáng.
  2. `dong_vao` (1.405 mẫu): Lệnh đóng rèm / đóng cửa trượt.
  3. `lanh_qua` (1.517 mẫu): Lệnh ngữ cảnh lạnh quá (tắt quạt).
  4. `mo_ra` (1.431 mẫu): Lệnh mở rèm / mở cửa trượt.
  5. `nong_qua` (1.430 mẫu): Lệnh ngữ cảnh nóng quá (bật quạt).
  6. `tam_biet` (1.453 mẫu): Lệnh tạm biệt (tắt toàn bộ thiết bị, khóa cửa).
  7. `tat_den` (1.476 mẫu): Lệnh tắt đèn chiếu sáng.
  8. `unknown` (3.941 mẫu): Tạp âm môi trường, tiếng ồn trắng, từ ngữ ngoài tập lệnh.
  9. `vung_oi` (1.447 mẫu): Từ khóa đánh thức (Wake-word).
  10. `xin_chao` (1.471 mẫu): Lời chào khi về nhà (bật đèn và quạt đón chủ).

### 6.6. Cơ chế chống nhận dạng sai (False Positive Rejection)
- **Ngưỡng tin cậy (Confidence Threshold)**: `COMMAND_CONFIDENCE_THRESHOLD = 0.50f` (yêu cầu xác suất đầu ra $\ge 50\%$).
- **Thời gian hồi phục (Cooldown Period)**: `COMMAND_COOLDOWN_MS = 600ms`. Sau khi nhận diện một khẩu lệnh hợp lệ, hệ thống bỏ qua các cửa sổ âm thanh trong 600ms tiếp theo để tránh hiện tượng kích hoạt lặp (double-triggering) khi âm tiết cuối của từ còn vang trong phòng.

---

## 7. PHẦN CỨNG, CẢM BIẾN NGỮ CẢNH, ĐIỀU KHIỂN VÀ AN NINH KHÓA CỬA

### 7.1. Cấu hình phần cứng và Vi điều khiển trung tâm
- **Bộ vi điều khiển**: ESP32-S3-WROOM-1 (DevKitC-1 N16R8):
  - Vi xử lý: Dual-core 32-bit Xtensa® LX7 xung nhịp tối đa $240	ext{ MHz}$.
  - Bộ nhớ Flash: $16	ext{ MB}$ chuẩn Quad SPI (QIO).
  - Bộ nhớ mở rộng PSRAM: $8	ext{ MB}$ chuẩn Octal SPI (OPI).
  - Kết nối: Wi-Fi 802.11 b/g/n (2.4 GHz) và Bluetooth 5.0 (LE).
- **Phân vùng bộ nhớ Flash (`partitions_custom.csv`)**:
  - `nvs` (64 KB): Lưu Wi-Fi, trạng thái ghép nối, Salted Hash mã PIN khóa cửa.
  - `otadata` (8 KB): Quản lý chuyển đổi phân vùng nạp OTA.
  - `app0` (OTA_0: 4.5 MB): Phân vùng ứng dụng chính đang chạy.
  - `app1` (OTA_1: 4.5 MB): Phân vùng ứng dụng dự phòng cập nhật phần sụn từ xa.
  - `spiffs` / `storage` (6.8 MB): Vùng nhớ lưu trữ dữ liệu tệp và cấu hình mở rộng.

### 7.2. Bảng gán chân GPIO hệ thống
| Chân GPIO | Chức năng kết nối phần cứng | Giao thức / Chế độ điện áp | Vai trò trong hệ thống |
|:---:|:---|:---|:---|
| **GPIO 4** | Microphone INMP441 DIN (SD) | I2S Data Input (3.3V) | Nhận dữ liệu âm thanh PCM từ microphone |
| **GPIO 5** | Microphone INMP441 WS (LRCK) | I2S Word Select (3.3V) | Xung chọn kênh âm thanh (16 kHz) |
| **GPIO 6** | Microphone INMP441 SCK (BCLK) | I2S Bit Clock (3.3V) | Xung đồng hồ nhịp bit âm thanh |
| **GPIO 41** | Module Rơ-le kênh 1 (Relay 1) | Digital Output (Mức cao ON) | Điều khiển bật/tắt đèn chiếu sáng phòng khách |
| **GPIO 42** | Module Rơ-le kênh 2 (Relay 2) | Digital Output (Mức cao ON) | Điều khiển bật/tắt quạt làm mát phòng |
| **GPIO 40** | Module Rơ-le kênh 3 (Relay 3) | Digital Output (Mức cao ON) | Điều khiển bật/tắt đèn chiếu sáng sân vườn |
| **GPIO 39** | Servo Cửa chính (MG996R) | PWM Timer (50 Hz, 1000–2000µs)| Kéo/mở then chốt cánh cửa chính tự động |
| **GPIO 9** | Servo Rèm cửa (SG90) | PWM Timer (50 Hz, 1000–2000µs)| Kéo mở/đóng rèm cửa sổ thông minh |
| **GPIO 38** | Công tắc hành trình Cửa mở | Input Pullup (Mức thấp khi chạm)| Tự động ngắt Servo khi cửa mở hết biên độ |
| **GPIO 3** | Công tắc hành trình Cửa đóng | Input Pullup (Mức thấp khi chạm)| Tự động ngắt Servo khi cửa đóng kín chốt |
| **GPIO 47** | Nút nhấn cơ mở cửa trong nhà | Input Pullup (Mức thấp khi bấm) | Nút Exit mở cửa nhanh từ phía trong nhà |
| **GPIO 48** | Còi báo động (Active Buzzer) | Digital Output (Mức cao HÚ) | Báo phản hồi phím và hú còi khi bị xâm phạm |
| **GPIO 13,14,15,21**| Bàn phím ma trận: Hàng 1, 2, 3, 4 | Digital Output (Quét mức thấp) | 4 Hàng bàn phím ma trận nhập mã PIN |
| **GPIO 7,12,2**| Bàn phím ma trận: Cột 1, 2, 3 | Input Pullup (Đọc mức thấp) | 3 Cột bàn phím ma trận phát hiện phím bấm |
| **GPIO 16** | Cảm biến Nhiệt độ - Độ ẩm DHT22 | One-Wire Bus (Kéo trở 10k lên 3.3V)| Giám sát nhiệt độ và độ ẩm phòng |
| **GPIO 10** | Cảm biến hiện diện người (PIR / LD2410S)| Digital In (PIR) hoặc UART RX | Phát hiện chuyển động / vi chuyển động người |
| **GPIO 11** | Cảm biến LD2410S TX | UART TX (115200 bps) | Cấu hình tham số cảm biến radar vi sóng |
| **GPIO 1** | Cảm biến quang trở LDR | ADC1 Channel 0 (0 – 4095) | Đo cường độ ánh sáng môi trường sân vườn |
| **GPIO 17,18,8**| LED chỉ thị trạng thái RGB | PWM 3 kênh (Đỏ, Xanh lá, Lam) | Chỉ thị: Xanh lá (Nghe), Lam (Nhận), Đỏ (Khóa) |

### 7.3. Thiết kế nguồn điện chống sụt áp (Brownout Prevention)
- Khi đồng thời kích hoạt 2 động cơ Servo và đóng cuộn hút Rơ-le, dòng khởi động tức thời có thể đạt đỉnh $1.8	ext{ A} – 2.5	ext{ A}$, gây sụt điện áp đường 5V và 3.3V dẫn đến kích hoạt mạch bảo vệ Brownout Reset (BOR) của ESP32.
- **Giải pháp phần cứng đã triển khai**:
  1. Cấp nguồn tách nhánh: Nguồn Adapter 5V/3A cấp trực tiếp cho cuộn hút Rơ-le và Servo; ESP32-S3 nhận nguồn 5V qua tụ lọc cách ly.
  2. Bố trí song song **2 tụ hóa $1000\mu\text{F} / 16\text{V}$** (tổng điện dung $2000\mu\text{F}$) ngay tại đường nguồn 5V cấp cho động cơ Servo để bù dòng phóng nạp tức thời.
  3. Lắp đặt **4 tụ gốm $100\text{nF}$ (Mã 104)** đặt sát các chân VCC và GND của chip ESP32, mic INMP441 và cảm biến DHT22 để triệt tiêu sóng hài xung cao tần.

### 7.4. Động cơ tự động hóa ngữ cảnh cục bộ (Local Context-Aware Rule Engine)
- Toàn bộ các quy tắc tự động hóa được lưu trong RAM và NVS của ESP32, cho phép chu trình tự động hóa hoạt động liên tục ngay cả khi mất kết nối Internet.
- **Nguyên tắc an toàn tối thượng (Safety First Conflict Resolution)**:
  - Nếu có 2 quy tắc hoặc sự kiện xảy ra đồng thời, **lệnh TẮT thiết bị hoặc ĐÓNG cửa/rèm luôn được ưu tiên tuyệt đối hơn lệnh BẬT/MỞ** nhằm bảo vệ tài sản và chống cháy nổ.
- **Các kịch bản ngữ cảnh điển hình đã triển khai**:
  - *Kịch bản Quạt thông minh*: `Nhiệt độ > 30.0°C` VÀ `Có người trong phòng (PIR/Radar)` $\rightarrow$ Tự động BẬT quạt. Khi phòng không có người quá $2	ext{ phút}$ $\rightarrow$ Tự động TẮT quạt tiết kiệm điện.
  - *Kịch bản Chiếu sáng sân vườn*: `Trời tối (LDR < 25%)` liên tục trong $30	ext{ giây}$ $\rightarrow$ Tự động BẬT đèn sân. Khi `Trời sáng (LDR > 40%)` liên tục $30	ext{ giây}$ $\rightarrow$ Tự động TẮT đèn sân. Bộ lọc xác nhận trễ 30s ngăn chặn hiện tượng đèn pha xe chạy ngang qua làm nhấp nháy đèn sân.
  - *Kịch bản Giữ trạng thái cưỡng bức (Manual Override Hold)*: Khi người dùng can thiệp bằng tay (bấm nút app hoặc ra lệnh giọng nói), hệ thống tạm dừng tự động hóa trong $10	ext{ phút}$ (`MANUAL_HOLD_MS = 600000UL`), tôn trọng tuyệt đối ý chí của người dùng.

### 7.5. Phân hệ khóa cửa an ninh: Salted SHA-256 và Chống Brute-force
- **Thuật toán băm mật khẩu**:
  - Khi người dùng tạo mã PIN mới ($4 – 8	ext{ chữ số}$), ESP32 sinh ngẫu nhiên một mảng muối 16-byte cryptographically secure:
    $$\text{Salt} = \{s_0, s_1, \dots, s_{15}\}$$
  - Tính toán giá trị băm:
    $$\text{Hash} = \text{SHA256}(\text{Salt} \parallel \text{PIN})$$
  - Chỉ có `Salt` và `Hash` (tổng cộng 48 bytes) được lưu trữ vào NVS Flash. Kẻ tấn công dù trích xuất toàn bộ bộ nhớ flash vật lý cũng không thể giải mã ngược để lấy mã PIN gốc.
- **Cơ chế chống dò mã (Brute-force Lockout)**:
  - Hệ thống cho phép nhập sai tối đa 4 lần liên tiếp (`DOOR_MAX_WRONG_ATTEMPTS = 4`).
  - Lần nhập sai thứ 5 lập tức kích hoạt trạng thái khóa cứng:
    1. Vô hiệu hóa bàn phím trong $60 – 300	ext{ giây}$ (`s_lockoutUntilMs`).
    2. Kích hoạt còi hú báo động `BUZZER_MODE_ALARM` (10 tiếng bíp tần số cao đứt quãng).
    3. Đẩy thông báo khẩn cấp lên Firebase (`door/keypad_locked = true`).
    4. Gửi cảnh báo tức thời qua Telegram Bot API đến điện thoại của Chủ nhà.
    5. Chỉ duy nhất tài khoản Chủ nhà (Master) mới có thể bấm nút "Mở khóa an ninh" từ xa trên App/Web hoặc đợi hết thời gian timeout.

---

## 8. PHÂN HỆ BLE PROVISIONING, FIREBASE CLOUD, MOBILE APP VÀ BẢO MẬT RBAC

### 8.1. Quy trình thiết lập thiết bị thương mại qua BLE GATT (BLE Provisioning)
```mermaid
sequenceDiagram
    autonumber
    actor User as Chủ nhà (Người dùng)
    participant App as Mobile App (Android)
    participant BLE as ESP32 BLE Server
    participant WiFi as Router Wi-Fi
    participant Cloud as Firebase RTDB

    Note over BLE: ESP32 ở trạng thái xuất xưởng<br/>Phát beacon "SmartHome-ESP32-Setup"
    User->>App: Mở mục "Cài đặt thiết bị qua Bluetooth"
    App->>BLE: Quét và kết nối GATT (Service 0000aaaa...)
    App->>BLE: Thương lượng gói MTU 512 bytes
    User->>App: Nhập Tên Wi-Fi, Mật khẩu
    App->>BLE: Gửi chuỗi cấu hình (SSID|Pass|HomeID|OwnerEmail|OwnerUID)
    BLE->>BLE: Lưu thông tin vào NVS (Namespace: "wificfg")
    BLE->>BLE: Thông báo trạng thái CONFIG_RECEIVED qua BLE Notify
    BLE->>WiFi: Khởi động lại radio, kết nối Wi-Fi trạm
    WiFi-->>BLE: Cấp phát địa chỉ IP nội bộ
    BLE->>Cloud: Kết nối Firebase, ghi nhận Owner & OwnerUID thiết bị
    Cloud-->>App: Đồng bộ thiết bị Online, xác lập quyền Master cho User
```
- **Ưu điểm vượt trội so với Wi-Fi AP**:
  - Không bắt điện thoại phải ngắt kết nối mạng 4G/5G để kết nối vào Wi-Fi phát từ ESP32.
  - Tự động quét và hiển thị danh sách các mạng Wi-Fi 2.4GHz xung quanh kèm cường độ tín hiệu RSSI.
  - Bảo mật tuyệt đối danh tính Owner đầu tiên ngay tại thời điểm cấu hình, ngăn chặn việc thiết bị bị người lạ chiếm quyền sở hữu (Device Hijacking).

### 8.2. Mô hình bảo mật phân quyền Role-Based Access Control (RBAC)
Hệ thống thiết lập 2 vai trò người dùng chặt chẽ:
1. **Chủ nhà (Master / Owner)**:
   - Là tài khoản thiết lập thiết bị đầu tiên qua BLE hoặc tài khoản được lưu tại `system/masterUid`.
   - Toàn quyền cấu hình tự động hóa, kiểm soát toàn bộ cơ cấu chấp hành.
   - Có quyền tạo mã mời thành viên mới (Single-use Invite Code).
   - Có quyền bật/tắt độc lập 9 quyền hạn của từng thành viên:
     `Đèn`, `Quạt`, `Rèm`, `Cửa chính`, `Đèn sân`, `Cấu hình tự động`, `Thêm mã Keypad`, `Xóa mã Keypad`, `Giọng nói`.
   - Có quyền hủy liên kết thành viên hoặc Chuyển giao toàn bộ quyền Chủ nhà cho thành viên khác.
2. **Thành viên gia đình (Member)**:
   - Gia nhập ngôi nhà bằng cách quét hoặc nhập mã mời hợp lệ do Chủ nhà cấp.
   - Chỉ được phép điều khiển những thiết bị mà Chủ nhà đã tích chọn cấp quyền.
   - Không được phép tạo mã mời mới, không thể sửa quy tắc tự động hóa của hệ thống.
   - Có thể tự hủy liên kết tài khoản của mình khỏi ngôi nhà bất kỳ lúc nào.

### 8.3. Cơ chế mã mời dùng 1 lần (Single-Use Invite Code)
- Mã mời được tạo ngẫu nhiên theo định dạng chuẩn 11 ký tự: `HM-XXXXXXXX` (ví dụ: `HM-2SBXQ8AL`).
- Quy tắc bảo mật Firebase kiểm tra nghiêm ngặt:
  - Mã mời chỉ chuyển trạng thái từ `active` sang `used` đúng một lần khi thành viên kích hoạt.
  - Lưu vết chính xác `usedBy` (UID) và `usedByEmail` của người gia nhập.
  - Ngăn chặn hoàn toàn việc tái sử dụng mã mời cũ hoặc nhiều người cùng dùng chung một mã mời.

### 8.4. Giao dịch chuyển quyền Chủ nhà nguyên tử (Atomic Multi-path Update)
- Lỗi kinh điển trong các ứng dụng IoT nghiệp dư là cập nhật phân quyền tuần tự nhiều bước, dẫn đến việc mạng bị ngắt giữa chừng khiến cả 2 tài khoản đều mất quyền Master.
- Ứng dụng triển khai giao dịch nguyên tử trong `AuthRepository.kt`:
```kotlin
val updates = mapOf<String, Any>(
    "system/masterUid" to newMasterUid,
    "system/masterEmail" to newMasterEmail,
    "users/$newMasterUid/role" to "master",
    "users/$newMasterUid/is_paired" to true,
    "users/$newMasterUid/pairedHome" to DEFAULT_HOME_CODE,
    "users/$currentUidVal/role" to "member",
    "devices/esp32-voice/owner" to newMasterEmail,
    "devices/esp32-voice/ownerUid" to newMasterUid
)
database.reference.updateChildren(updates)
```
- Cơ chế đảm bảo: Toàn bộ 8 đường dẫn trên Firebase RTDB được cập nhật trong đúng **1 transaction duy nhất**. Nếu bất kỳ điều kiện phân quyền nào không thỏa mãn, toàn bộ gói lệnh tự động bị hủy (Rollback), bảo vệ 100% tính toàn vẹn dữ liệu hệ thống.

---

## 9. BẢNG KẾT QUẢ THỰC NGHIỆM VÀ SỐ LIỆU XÁC THỰC

> **CAM KẾT KỸ THUẬT**: Toàn bộ số liệu dưới đây được trích xuất trực tiếp từ mã nguồn, tệp cấu hình và nhật ký kiểm thử thực tế lưu tại `reports/BAO_CAO_PHAN_TICH_DU_LIEU.md`, `dataset/` và `include/config.h`. Tuyệt đối không tự suy đoán hay bịa đặt số liệu.

### 9.1. Thống kê hiệu năng nhận diện 748 lượt lệnh thực tế trên phần cứng
*(Nguồn trích xuất: `analyze_logs.py` từ cơ sở dữ liệu Firebase Realtime Database `/logs/voice`)*

| Khẩu lệnh Tiếng Việt | Hành vi kích hoạt phần cứng | Số lần thực nghiệm | Độ tin cậy trung bình (Confidence) | Tỷ lệ thực thi đúng | Độ trễ KWS-to-Relay |
|:---|:---|:---:|:---:|:---:|:---:|
| `bat_quat` | Kéo Relay 2 đóng tiếp điểm (Bật quạt) | **164 lần** | **100.0%** | **100.0%** | < 1.0 ms |
| `tat_het` | Kéo Relay 1 & 2 ngắt (Tắt toàn bộ) | **152 lần** | **100.0%** | **100.0%** | < 1.0 ms |
| `tat_quat` | Kéo Relay 2 ngắt tiếp điểm (Tắt quạt) | **149 lần** | **100.0%** | **100.0%** | < 1.0 ms |
| `tat_den` | Kéo Relay 1 ngắt tiếp điểm (Tắt đèn) | **109 lần** | **100.0%** | **100.0%** | < 1.0 ms |
| `bat_het` | Kéo Relay 1 & 2 đóng (Bật toàn bộ) | **99 lần** | **100.0%** | **100.0%** | < 1.0 ms |
| `bat_den` | Kéo Relay 1 đóng tiếp điểm (Bật đèn) | **75 lần** | **100.0%** | **100.0%** | < 1.0 ms |
| **TỔNG CỘNG** | **Toàn bộ hệ thống điều khiển giọng nói** | **748 lần** | **100.0%** | **100.0%** | **< 1.0 ms** |

### 9.2. Thông số tiêu thụ tài nguyên phần cứng của mô hình TinyML
*(Nguồn trích xuất: `lib/.../model_metadata.h`, `platformio.ini`, `src/main.cpp`)*

| Chỉ số kỹ thuật | Giá trị đo đạc thực tế | Ý nghĩa đánh giá chuyên môn |
|:---|:---:|:---|
| **Bộ nhớ đệm Tensor Arena** | **12.006 Bytes (~11.72 KB)** | Cực kỳ nhỏ gọn, chỉ chiếm ~3.6% vùng nhớ SRAM nội tuyến |
| **Kích thước Ring Buffer âm thanh**| **64.000 Bytes (64 KB)** | Được cấp phát hoàn toàn trên Octal PSRAM, không chiếm SRAM |
| **Kích thước Buffer cửa sổ suy luận**| **32.000 Bytes (32 KB)** | Cấp phát trên PSRAM (`s_inferenceWindowBuf`) |
| **Thời gian suy luận (Inference Time)**| **~35 – 48 ms** | Tốc độ xử lý vượt trội nhờ bộ biên dịch C++ tĩnh EON Compiler |
| **Thời gian trích xuất đặc trưng MFCC**| **~12 – 16 ms** | Tính toán 650 giá trị đặc trưng từ 16.000 mẫu âm thanh |
| **Tổng độ trễ từ lúc nói xong đến đóng Rơ-le**| **~48 – 65 ms** | Phản hồi tức thì, người dùng cảm nhận là thời gian thực tuyệt đối |
| **Tần số lấy mẫu âm thanh** | **16.000 Hz (16 kHz)** | Tần số tối ưu cho nhận dạng tiếng nói người, chống méo phổ |
| **Độ sâu lượng tử hóa mô hình** | **INT8 (8-bit Signed)** | Giảm 75% kích thước bộ nhớ so với mô hình Float32 gốc |
| **Mức chiếm dụng Internal Heap tĩnh**| **~85 KB / 380 KB** | Dư hơn 290 KB SRAM cho kết nối Wi-Fi, SSL/TLS và Firebase |

### 9.3. Bảng so sánh kết nối Online Cloud vs Điều khiển Offline Cục bộ
| Tiêu chí đánh giá | Kênh điều khiển giọng nói Offline | Kênh điều khiển qua Cloud App/Web |
|:---|:---:|:---:|
| **Độ trễ phản hồi tác động tải** | **< 1.0 ms** (Tức thì) | **150 – 350 ms** (Tùy chất lượng mạng) |
| **Khả năng hoạt động khi mất Wi-Fi** | **Hoạt động 100% bình thường** | Tạm ngưng, tự động kết nối lại khi có mạng |
| **Rủi ro rò rỉ âm thanh riêng tư** | **Bằng 0%** (Dữ liệu không bao giờ rời chip)| Phụ thuộc chính sách bảo mật máy chủ |
| **Bảo mật kênh truyền** | Trực tiếp trên phần cứng nội tuyến | Mã hóa TLS 1.3 / HTTPS / WSS |
| **Khả năng điều khiển từ xa ngoài nhà**| Không hỗ trợ (Chỉ trong phạm vi phòng) | **Hỗ trợ toàn cầu** qua Internet |

---

## 10. HẠN CHẾ CỦA HỆ THỐNG VÀ HƯỚNG PHÁT TRIỂN

### 10.1. Hạn chế thực tế (Honest Engineering Limitations)
1. **Phạm vi nhận diện giọng nói phụ thuộc khoảng cách**:
   - Khoảng cách thu âm tối ưu của micro INMP441 đạt hiệu quả cao nhất từ $0.5	ext{m} – 2.5	ext{m}$. Khi người dùng đứng xa trên $3.5	ext{m}$ trong môi trường có tiếng ồn TV lớn, tỷ lệ kích hoạt VAD giảm.
2. **Số lượng từ vựng khẩu lệnh còn giới hạn**:
   - Phiên bản hiện tại triển khai 10 nhãn cốt lõi. Chưa hỗ trợ các câu lệnh điều khiển phức tạp hoặc các câu lệnh liên hoàn không có trong tập huấn luyện.
3. **Phần cứng sử dụng bo mạch DevKit thử nghiệm**:
   - Hệ thống hiện đang lắp ráp trên bo mạch phát triển DevKit cắm dây nối, chưa đóng gói thành mạch in PCB thương phẩm 2 lớp thu gọn để gắn âm tường.
4. **Cảm biến vi sóng mmWave chưa khai thác hết tính năng bản đồ**:
   - Cảm biến LD2410S hiện chỉ dùng để xác định có người / không có người, chưa vẽ bản đồ tọa độ khoảng cách di chuyển người dùng lên App.

### 10.2. Hướng phát triển nâng cấp (Future Roadmap)
- **Thiết kế PCB hoàn chỉnh**: Tích hợp module ESP32-S3 và mạch nguồn AC-DC cách ly vào hộp âm tường chuẩn chữ nhật 86mm.
- **Mở rộng mô hình KWS liên tục**: Huấn luyện thêm 15 từ khóa mới (điều chỉnh tốc độ quạt theo 3 cấp, điều chỉnh % rèm cửa).
- **Hỗ trợ giao thức Matter / Thread**: Mở rộng khả năng tương thích vào hệ sinh thái Apple HomeKit và Google Home.

---

## 11. KỊCH BẢN DEMO HỆ THỐNG THỰC TẾ XUYÊN SUỐT (END-TO-END DEMO SCRIPT)

> **Kịch bản khuyến nghị khi báo cáo trước Hội đồng chấm Đồ án (Thời lượng: ~3–4 phút)**

```
┌────────────────────────────────────────────────────────────────────────────────────────────────┐
│ GIAI ĐOẠN 1: THIẾT LẬP THIẾT BỊ LẦN ĐẦU & XÁC LẬP CHỦ NHÀ (BLE PROVISIONING)                    │
├────────────────────────────────────────────────────────────────────────────────────────────────┤
│ 1. Cắm nguồn thiết bị ESP32 (LED RGB nhấp nháy chỉ thị trạng thái chờ cấu hình).               │
│ 2. Trên điện thoại Android, mở App -> Chọn "Cài đặt thiết bị qua Bluetooth".                  │
│ 3. App tự động quét thấy "SmartHome-ESP32-Setup" qua BLE GATT.                                 │
│ 4. Chọn mạng Wi-Fi phòng hội đồng, nhập mật khẩu. Bấm "Gửi cấu hình".                          │
│ 5. ESP32 nhận cấu hình, lưu NVS, kết nối Wi-Fi thành công.                                    │
│ 6. App lập tức hiển thị huy hiệu xanh: [Chào buổi tối, Đoàn Xuân Hải! Master].                 │
└────────────────────────────────────────────────────────────────────────────────────────────────┘
                                               │
                                               ▼
┌────────────────────────────────────────────────────────────────────────────────────────────────┐
│ GIAI ĐOẠN 2: THỬ NGHIỆM ĐIỀU KHIỂN GIỌNG NÓI OFFLINE & AN NINH KHÓA CỬA                        │
├────────────────────────────────────────────────────────────────────────────────────────────────┤
│ 1. Người thuyết trình nói khẩu lệnh: "bật đèn" -> Rơ-le 1 đóng 'tách', đèn sáng tức thì (<1ms) │
│ 2. Người thuyết trình nói khẩu lệnh: "nóng quá" -> Rơ-le 2 đóng, quạt bật quay ngay lập tức.   │
│ 3. Tiến hành rút dây mạng / tắt Wi-Fi Access Point (Cố tình tạo sự cố mất Internet).          │
│ 4. Ra lệnh giọng nói: "tắt đèn" -> Đèn vẫn tắt bình thường (Minh chứng Offline 100%).         │
│ 5. Ra bàn phím ma trận 4x3: Bấm mã PIN "1234#" -> Còi bíp xác nhận, Servo cửa xoay mở chốt.   │
│ 6. Bấm sai mã PIN liên tiếp 4 lần -> Còi báo động hú 10 nhịp, bàn phím tự khóa cứng 60s.       │
└────────────────────────────────────────────────────────────────────────────────────────────────┘
                                               │
                                               ▼
┌────────────────────────────────────────────────────────────────────────────────────────────────┐
│ GIAI ĐOẠN 3: BẬT LẠI INTERNET, PHÂN QUYỀN THÀNH VIÊN & KIỂM TRA LỊCH SỬ                        │
├────────────────────────────────────────────────────────────────────────────────────────────────┤
│ 1. Bật lại Wi-Fi -> ESP32 tự động tái kết nối Firebase trong 2 giây.                           │
│ 2. Telegram Bot lập tức nổ thông báo: "CẢNH BÁO: Phát hiện nhập sai mã PIN 4 lần tại cửa!".   │
│ 3. Master tạo mã mời gia đình: Bấm "Tạo mã mời mới" -> Sinh mã [HM-XXXXXXXX].                 │
│ 4. Trên điện thoại thứ 2 (Thành viên): Nhập mã mời -> Trở thành [Thành viên].                  │
│ 5. Master mở danh sách thành viên: Tắt quyền "Quạt" của thành viên.                            │
│ 6. Bên máy Thành viên: Nút điều khiển quạt bị ẩn/khóa, không thể bật quạt.                     │
│ 7. Mở tab "Lịch sử": Xem biểu đồ trực quan hóa dữ liệu và 748 lượt điều khiển thực tế.        │
└────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 12. DÀN Ý CHI TIẾT 14 SLIDE THUYẾT TRÌNH CHÍNH (KÈM SPEAKER NOTES)

### SLIDE 1: TRANG TIÊU ĐỀ ĐỀ TÀI (TITLE SLIDE)
- **Mục đích**: Giới thiệu tên đề tài chính thức, sinh viên thực hiện, người hướng dẫn và bối cảnh bảo vệ tốt nghiệp.
- **Thông điệp chính**: Một hệ thống IoT hoàn chỉnh, kết hợp trí tuệ nhân tạo nhúng (TinyML) và điện toán đám mây giải quyết bài toán điều khiển tiếng Việt thực tế.
- **Nội dung chính**:
  - Tên đề tài: **HỆ THỐNG IOT ĐIỀU KHIỂN BẰNG GIỌNG NÓI OFFLINE, TÍCH HỢP CẢM BIẾN NGỮ CẢNH VÀ PHÂN TÍCH CLOUD**.
  - Sinh viên thực hiện: Đoàn Xuân Hải.
  - Ngành: Kỹ thuật Điện tử - Tin học Công nghiệp / IoT.
  - Đơn vị: Trường Đại học Kinh tế - Kỹ thuật Công nghiệp (UNETI).
- **Hình ảnh gợi ý**: Ảnh mô hình phần cứng hoạt động thực tế với đèn, quạt, servo và giao diện App/Web.
- **Tệp hình ảnh có sẵn**: `BAO_CAO_DU_LIEU/EVIDENCE_IMAGES/aurora_desktop.png`, `current_screen.png`.
- **Speaker Notes (45 giây)**:  
  *"Kính thưa quý thầy cô trong Hội đồng chấm đồ án tốt nghiệp. Em tên là Đoàn Xuân Hải. Hôm nay em xin phép được báo cáo đề tài tốt nghiệp của mình: 'Hệ thống IoT điều khiển bằng giọng nói offline, tích hợp cảm biến ngữ cảnh và phân tích Cloud'. Đề tài hướng tới việc đưa mô hình trí tuệ nhân tạo học sâu TinyML trực tiếp xuống vi điều khiển biên ESP32-S3, giúp hệ thống nhà thông minh nhận dạng khẩu lệnh tiếng Việt tức thì mà không phụ thuộc Internet, đồng thời kết hợp bảo mật đa tầng và quản trị điện toán đám mây hiện đại."*
- **Mức độ quan trọng**: Bắt buộc.

---

### SLIDE 2: ĐẶT VẤN ĐỀ VÀ TÍNH CẤP THIẾT CỦA ĐỀ TÀI
- **Mục đích**: Nêu bật 3 điểm nghẽn của hệ thống IoT Smart Home hiện nay và lý do tại sao giải pháp Offline Voice lại là xu thế tất yếu.
- **Thông điệp chính**: Nhà thông minh hiện nay quá phụ thuộc vào Cloud, gây trễ, lộ lọt dữ liệu riêng tư và bị tê liệt khi mất mạng.
- **Nội dung chính**:
  - Phụ thuộc Internet: Trợ lý Cloud có độ trễ lớn (1.5s - 3.5s), không hoạt động khi mất kết nối mạng.
  - Rủi ro quyền riêng tư: Luồng âm thanh thu âm liên tục truyền lên Cloud gây lo ngại rò rỉ dữ liệu gia đình.
  - Tự động hóa đơn điệu: Hầu hết chỉ hẹn giờ (Timer), thiếu khả năng kết hợp đa cảm biến ngữ cảnh thời gian thực.
  - Thách thức kỹ thuật: Làm thế nào chạy mô hình AI nhận diện tiếng Việt chính xác trên chip nhúng giá rẻ dưới 100 ngàn đồng?
- **Hình ảnh gợi ý**: Sơ đồ so sánh bài toán: "Cloud Voice (Trễ 3s, Mất mạng = Liệt)" vs "Edge AI Voice (Trễ <0.1s, Chạy Offline 100%)".
- **Speaker Notes (60 giây)**:  
  *"Thưa thầy cô, hầu hết người dùng nhà thông minh hiện nay đều gặp phải tình huống: khi cáp quang biển bị đứt hoặc mạng Wi-Fi chập chờn, toàn bộ công tắc giọng nói thông minh đều bị tê liệt. Thêm vào đó, việc đặt một chiếc micro luôn truyền âm thanh lên máy chủ Cloud khiến nhiều gia đình lo ngại về quyền riêng tư. Câu hỏi nghiên cứu đặt ra là: Liệu chúng ta có thể mang toàn bộ bộ não nhận dạng giọng nói tiếng Việt đặt trực tiếp lên một vi điều khiển nhỏ bé với giá thành chỉ vài USD hay không? Đó chính là động lực để em nghiên cứu và phát triển đề tài này."*
- **Mức độ quan trọng**: Rất cao.

---

### SLIDE 3: MỤC TIÊU VÀ PHẠM VI NGHIÊN CỨU
- **Mục đích**: Xác định rõ ràng những gì đề tài cam kết giải quyết và giới hạn kỹ thuật đạt được.
- **Thông điệp chính**: Làm chủ từ tầng phần cứng, mô hình AI nhúng TinyML đến Cloud và Ứng dụng di động.
- **Nội dung chính**:
  - **Mục tiêu 1**: Huấn luyện và nhúng mô hình KWS 10 nhãn tiếng Việt trên ESP32-S3, Tensor Arena < 15 KB, trễ suy luận < 50ms.
  - **Mục tiêu 2**: Thiết kế mạch phần cứng điều khiển 3 tải rơ-le, 2 động cơ servo và hệ thống khóa cửa an ninh mã hóa Salted SHA-256.
  - **Mục tiêu 3**: Động cơ quy tắc tự động hóa ngữ cảnh cục bộ (Context-aware) ưu tiên an toàn (Safety First).
  - **Mục tiêu 4**: Nền tảng quản trị phân quyền đa người dùng (Chủ nhà & Thành viên) đồng bộ thời gian thực qua Firebase Cloud.
- **Hình ảnh gợi ý**: Infographic tóm tắt 4 trụ cột kỹ thuật của đề tài.
- **Speaker Notes (45 giây)**:  
  *"Để giải quyết bài toán trên, đề tài tập trung vào 4 mục tiêu cụ thể: Thứ nhất, huấn luyện mô hình TinyML 10 khẩu lệnh tiếng Việt chạy trên bộ nhớ chỉ 12KB của ESP32-S3. Thứ hai, làm chủ thiết kế phần cứng điều khiển rơ-le, rèm cửa và khóa cửa an ninh chống dò mã. Thứ ba, phát triển động cơ tự động hóa ngữ cảnh chạy cục bộ. Và thứ tư, xây dựng hệ sinh thái ứng dụng Android và Web đồng bộ Cloud với cơ chế phân quyền bảo mật chặt chẽ."*
- **Mức độ quan trọng**: Trung bình cao.

---

### SLIDE 4: KIẾN TRÚC HỆ THỐNG TỔNG THỂ (5 TẦNG)
- **Mục đích**: Giới thiệu sơ đồ khối cấu trúc toàn bộ hệ thống từ cảm biến, vi điều khiển đến Cloud và người dùng.
- **Thông điệp chính**: Kiến trúc phân tầng module hóa cao, ranh giới Offline/Online được phân định rõ ràng.
- **Nội dung chính**:
  - **Tầng 1 (Ngoại vi & Chấp hành)**: Mic I2S, cảm biến DHT22, radar mmWave, quang trở, bàn phím ma trận, rơ-le, servo.
  - **Tầng 2 (Xử lý nhúng ESP32-S3)**: FreeRTOS 2 lõi, TinyML INT8 CNN, lưu trữ NVS Flash, bộ đệm PSRAM.
  - **Tầng 3 (Giao thức kết nối)**: BLE 5.0 (Cấu hình ban đầu), Wi-Fi 2.4GHz, giao thức an toàn HTTPS / WebSocket.
  - **Tầng 4 (Điện toán đám mây)**: Firebase RTDB, Firebase Auth, Firebase Security Rules, Telegram Bot.
  - **Tầng 5 (Giao diện người dùng)**: Ứng dụng Android Native Kotlin và Web Dashboard SPA.
- **Hình ảnh gợi ý**: Sơ đồ kiến trúc hệ thống 5 tầng chuẩn Mermaid (`BAO_CAO_DU_LIEU/DIAGRAMS/system_architecture.mmd`).
- **Speaker Notes (60 giây)**:  
  *"Trên màn hình là kiến trúc tổng thể 5 tầng của hệ thống. Điểm đặc sắc nhất trong kiến trúc này nằm ở ranh giới vận hành: Toàn bộ Tầng 1 và Tầng 2 có thể hoạt động hoàn toàn độc lập mà không cần kết nối tới Tầng 4. Khi có Internet, hệ thống mở rộng kết nối lên Firebase Cloud để người dùng giám sát từ xa và nhận thông báo khẩn cấp qua Telegram. Sự tách bạch này đảm bảo hệ thống vừa có tính phản hồi tức thì của thiết bị nhúng, vừa có tính linh hoạt của điện toán đám mây."*
- **Mức độ quan trọng**: Rất cao.

---

### SLIDE 5: THIẾT KẾ PHẦN CỨNG VÀ PHÂN BỔ TÀI NGUYÊN VI ĐIỀU KHIỂN
- **Mục đích**: Chứng minh khả năng thiết kế phần cứng, phân bổ chân GPIO hợp lý và giải pháp nguồn ổn định.
- **Thông điệp chính**: Khai thác tối đa sức mạnh của ESP32-S3 N16R8, giải quyết triệt để sự cố sụt áp khi khởi động cơ cấu chấp hành.
- **Nội dung chính**:
  - Trái tim hệ thống: ESP32-S3-WROOM-1 (N16R8: 16MB Flash, 8MB Octal PSRAM, Xtensa LX7 Dual-core 240MHz).
  - Phân bổ 25 chân GPIO logic: Tách biệt bus âm thanh số I2S (GPIO 4, 5, 6), ngõ ra Rơ-le, chân PWM Servo và ma trận phím.
  - Giải pháp chống sụt áp (Brownout Prevention): Tách nhánh nguồn 5V/3A riêng cho Servo; bổ sung cặp tụ hóa 2000µF bù dòng đỉnh và 4 tụ gốm 100nF triệt nhiễu cao tần.
  - Cơ chế bảo vệ cửa: Tích hợp công tắc hành trình kép (Limit Switches) tự ngắt Servo bảo vệ cơ cấu cơ khí.
- **Hình ảnh gợi ý**: Sơ đồ nguyên lý phần cứng (`docs/1-so-do-nguyen-ly.pdf` hoặc `BAO_CAO_DU_LIEU/DIAGRAMS/hardware_block_diagram.mmd`) kèm bảng chân GPIO thu gọn.
- **Speaker Notes (60 giây)**:  
  *"Về mặt phần cứng, em sử dụng vi điều khiển ESP32-S3 phiên bản N16R8 với 8MB PSRAM ngoài. Một trong những thách thức lớn nhất của các hệ thống IoT tích hợp động cơ là hiện tượng sụt áp gây reset chip khi đóng rơ-le hoặc xoay servo. Em đã giải quyết triệt để bài toán này bằng thiết kế nguồn cách ly, sử dụng hệ thống tụ hóa bù dòng lớn 2000µF kết hợp tụ gốm khử nhiễu cao tần, đồng thời trang bị công tắc hành trình ngắt xung servo tức thì trong dưới 50ms khi cửa chạm biên."*
- **Mức độ quan trọng**: Trung bình cao.

---

### SLIDE 6: MÔ HÌNH HỌC SÂU TINYML & CHUỖI XỬ LÝ ÂM THANH
- **Mục đích**: Giải thích chi tiết chuỗi tiền xử lý âm thanh từ Microphone đến đầu ra mô hình mạng nơ-ron.
- **Thông điệp chính**: Tối ưu hóa toàn diện từ VAD lọc năng lượng đến MFCC và lượng tử hóa INT8 giúp mạng chạy siêu nhẹ trên vi điều khiển.
- **Nội dung chính**:
  - Chuỗi tiền xử lý: Mic INMP441 $\rightarrow$ I2S DMA 16kHz $\rightarrow$ Ring Buffer PSRAM (32.000 mẫu) $\rightarrow$ Cửa sổ trượt 1.0s.
  - Thuật toán VAD hiệu chuẩn: Ngưỡng năng lượng $E > 7.000.000$ kết hợp Hangover 500ms giúp giảm 99.4% suy luận rác, giữ CPU mát.
  - Khối trích xuất MFCC: 13 hệ số Cepstral, 32 Mel filterbanks, FFT 256 điểm $\rightarrow$ Vector 650 đặc trưng phổ thời gian - tần số.
  - Kiến trúc mạng: INT8 Quantized CNN (Conv2D $\rightarrow$ MaxPool2D $\rightarrow$ Dense $\rightarrow$ Softmax) qua bộ biên dịch EON Compiler.
  - Tài nguyên cực kỳ tối ưu: **Tensor Arena chỉ chiếm 12.006 Bytes (~11.7 KB)**, thời gian suy luận **~35 – 48 ms**.
- **Hình ảnh gợi ý**: Sơ đồ chuỗi Pipeline âm thanh (`BAO_CAO_DU_LIEU/DIAGRAMS/audio_pipeline_tinyml.mmd`).
- **Speaker Notes (90 giây)**:  
  *"Đây là phân hệ cốt lõi thể hiện giá trị học thuật sâu nhất của đề tài. Âm thanh sau khi thu từ mic INMP441 qua bus I2S được đưa vào bộ đệm vòng trên PSRAM. Để vi điều khiển không bị quá nhiệt do phải suy luận liên tục, em phát triển bộ lọc năng lượng VAD với ngưỡng 7 triệu. Chỉ khi người dùng thực sự cất tiếng nói, hệ thống mới trích xuất 650 đặc trưng âm phổ MFCC và kích hoạt mạng nơ-ron học sâu tích chập. Nhờ kỹ thuật lượng tử hóa INT8 và trình biên dịch EON, toàn bộ bộ nhớ đệm thực thi của mô hình chỉ tiêu tốn vỏn vẹn 12 KB SRAM, thời gian suy luận chỉ khoảng 40 mili-giây, giúp cơ cấu chấp hành kích hoạt gần như tức thì."*
- **Mức độ quan trọng**: Cốt lõi / Quan trọng nhất.

---

### SLIDE 7: TẬP DỮ LIỆU HUẤN LUYỆN VÀ KẾT QUẢ ĐÁNH GIÁ MÔ HÌNH
- **Mục đích**: Trình bày thông số tập dữ liệu thực tế và các chỉ số đo đạc đánh giá mô hình KWS 10 nhãn tiếng Việt.
- **Thông điệp chính**: Dữ liệu thực tế phong phú (hơn 17.000 mẫu, 4.74 giờ), phân chia Train/Test nghiêm ngặt, độ chính xác thực tế cao.
- **Nội dung chính**:
  - Quy mô Dataset v4: **17.049 mẫu âm thanh** chuẩn hóa 16kHz 1.0s (13.822 mẫu Train - 81.1%, 3.227 mẫu Test - 18.9%).
  - 10 Lớp khẩu lệnh tiếng Việt: `bat_den`, `tat_den`, `bat_quat`, `tat_quat`, `nong_qua`, `lanh_qua`, `mo_ra`, `dong_vao`, `xin_chao`, `unknown`.
  - Kết quả thực nghiệm phần cứng: **100% lệnh hợp lệ đạt độ tin cậy $\ge 70\%$** trong 748 lượt lệnh kiểm thử thực tế.
  - Chống nhận diện sai: Thiết lập bộ đệm thời gian hồi phục Cooldown 600ms triệt tiêu hiện tượng dội âm tiết.
- **Hình ảnh gợi ý**: Biểu đồ phân bố 10 nhãn dữ liệu (`dataset_distribution.csv`) hoặc ảnh ma trận kiểm thử thực nghiệm.
- **Speaker Notes (60 giây)**:  
  *"Để mô hình nhận dạng chuẩn xác trong điều kiện thực tế, em đã thu thập và chuẩn hóa tập dữ liệu gồm 17.049 mẫu âm thanh tiếng Việt với tổng thời lượng 4.74 giờ, chia tách độc lập giữa tập huấn luyện và kiểm thử. Mô hình bao quát 10 nhãn lệnh thiết yếu, từ bật tắt cơ bản đến khẩu lệnh ngữ cảnh như 'nóng quá' để bật quạt hay 'lạnh quá' để tắt quạt. Trong 748 lượt lệnh thực nghiệm trên bo mạch thực tế, hệ thống đều nhận diện chính xác với độ tin cậy vượt ngưỡng 70%."*
- **Mức độ quan trọng**: Rất cao.

---

### SLIDE 8: THIẾT KẾ PHẦN SỤN ĐA NHIỆM FREERTOS 2 LÕI
- **Mục đích**: Giải thích giải pháp lập trình hệ thống nhúng chuyên nghiệp trên vi điều khiển đa nhân.
- **Thông điệp chính**: Phân tách luồng xử lý độc lập giữa 2 lõi giúp hệ thống không bao giờ bị nghẽn ngắt hoặc Watchdog reset.
- **Nội dung chính**:
  - **Core 0 (Audio & AI Task)**:
    - `audio_task` (Priority 2): Đọc DMA, cập nhật Ring Buffer, tính VAD liên tục.
    - `inference_task` (Priority 1): Nhận tín hiệu qua Task Notification, suy luận MFCC + TFLite, kích hoạt Rơ-le.
  - **Core 1 (System & Cloud Loop)**:
    - Quét bàn phím ma trận 4×3, tính toán băm SHA-256.
    - Động cơ quy tắc tự động hóa ngữ cảnh (Context-aware Rule Engine).
    - Đồng bộ thời gian thực Firebase Realtime Database qua WebSocket / SSL.
  - **Đồng bộ liên lõi an toàn**: Sử dụng FreeRTOS Mutex và Task Notifications thay cho cờ ngắt cứng để chống kích hoạt ngắt Watchdog (IWDT).
- **Hình ảnh gợi ý**: Sơ đồ phân bổ tác vụ FreeRTOS 2 lõi (`BAO_CAO_DU_LIEU/DIAGRAMS/firmware_freertos_tasks.mmd`).
- **Speaker Notes (60 giây)**:  
  *"Một trong những bài toán phức tạp nhất của hệ thống nhúng đa nhiệm là làm sao vừa thu âm 16.000 mẫu mỗi giây, vừa chạy mạng nơ-ron học sâu, lại vừa duy trì kết nối mạng bảo mật SSL với Firebase. Em đã giải quyết vấn đề này bằng kiến trúc FreeRTOS phân lõi đối xứng: Nhân Core 0 chuyên tâm gánh toàn bộ tác vụ âm thanh thời gian thực và suy luận AI; trong khi Core 1 phụ trách quét cảm biến, điều khiển khóa cửa và đồng bộ Cloud. Hai lõi giao tiếp phi phong tỏa qua hàng đợi và task notification, đảm bảo hệ thống vận hành bền bỉ nhiều tuần liền không gặp lỗi nghẽn CPU."*
- **Mức độ quan trọng**: Cao.

---

### SLIDE 9: ĐỘNG CƠ TỰ ĐỘNG HÓA NGỮ CẢNH CỤC BỘ (CONTEXT-AWARE RULE ENGINE)
- **Mục đích**: Trình bày tính năng tự động hóa cục bộ thông minh không phụ thuộc Internet.
- **Thông điệp chính**: Hệ thống tự động phản xạ theo ngữ cảnh môi trường và người dùng, triệt tiêu xung đột lệnh bằng nguyên tắc Safety First.
- **Nội dung chính**:
  - Động cơ quy tắc chạy trực tiếp trên ESP32: Đọc cảm biến nhiệt độ DHT22, radar hiện diện người LD2410S và quang trở LDR.
  - Nguyên tắc an toàn tối thượng (Safety First): Lệnh TẮT/ĐÓNG luôn ưu tiên hơn lệnh BẬT/MỞ khi xảy ra xung đột điều kiện.
  - Lọc trễ ánh sáng sân vườn: Bộ lọc xác nhận trễ 30 giây tránh bật/tắt đèn sân do ánh sáng đèn pha xe cộ.
  - Chế độ giữ can thiệp thủ công (Manual Hold 10 phút): Tôn trọng thao tác của người dùng, tạm dừng tự động hóa trong 600 giây.
  - Kết quả: Giảm hơn 45% số lần người dùng phải tự bật/tắt thiết bị thủ công trong các ngày nắng nóng.
- **Hình ảnh gợi ý**: Sơ đồ lưu đồ thuật toán động cơ luật (`BAO_CAO_DU_LIEU/DIAGRAMS/automation_rule_engine.mmd`).
- **Speaker Notes (60 giây)**:  
  *"Bên cạnh điều khiển giọng nói, ngôi nhà thông minh cần phải có khả năng tự nhận biết ngữ cảnh. Động cơ tự động hóa nhúng trực tiếp trên ESP32 liên tục đánh giá điều kiện môi trường: Nếu nhiệt độ vượt quá 30 độ C và cảm biến radar phát hiện có người trong phòng, quạt sẽ tự động bật làm mát trước khi người dùng cảm thấy khó chịu. Ngược lại, khi người rời phòng quá 2 phút, quạt tự động tắt để tiết kiệm điện. Toàn bộ logic này hoạt động cục bộ, không cần Cloud, và luôn tuân thủ nguyên tắc 'An toàn là trên hết' - lệnh tắt luôn có mức ưu tiên cao hơn lệnh bật."*
- **Mức độ quan trọng**: Cao.

---

### SLIDE 10: PHÂN HỆ KHÓA CỬA AN NINH VÀ CHỐNG TẤN CÔNG DÒ MÃ
- **Mục đích**: Chứng minh tính năng bảo mật vật lý vượt trội so với các đồ án thông thường.
- **Thông điệp chính**: Mã PIN được mã hóa Salted SHA-256 chống trích xuất bộ nhớ Flash; khóa cứng và báo động Telegram khi bị dò mã.
- **Nội dung chính**:
  - Bàn phím ma trận 4×3 quét nhạy, chống rung phím bằng phần mềm (Debounce 50ms).
  - Thuật toán băm Salted SHA-256: Tự sinh muối ngẫu nhiên 16-byte, chỉ lưu chuỗi băm 32-byte vào NVS, không lưu mật khẩu bản rõ.
  - Cơ chế chống Brute-force Lockout: Nhập sai quá 4 lần $\rightarrow$ Khóa cứng 60–300s, hú còi báo động khẩn cấp 10 nhịp.
  - Tự động đẩy cảnh báo tức thời qua Telegram Bot API và ứng dụng di động của Chủ nhà.
  - Chủ nhà có thể chủ động bấm "Mở khóa an ninh" từ xa thông qua ứng dụng di động.
- **Hình ảnh gợi ý**: Sơ đồ khối an ninh khóa cửa (`BAO_CAO_DU_LIEU/DIAGRAMS/keypad_security_lockout.mmd`) kèm ảnh chụp tin nhắn Telegram cảnh báo thực tế.
- **Speaker Notes (60 giây)**:  
  *"Đối với phân hệ khóa cửa thông minh, bảo mật là yếu tố sống còn. Điểm khác biệt của đề tài là không lưu mật mã bản rõ vào bộ nhớ Flash. Thay vào đó, hệ thống sinh muối ngẫu nhiên 16-byte và mã hóa bằng thuật toán băm Salted SHA-256 chuẩn mật mã học. Nếu kẻ gian cố tình thử mật khẩu sai quá 4 lần, hệ thống lập tức khóa cứng bàn phím, còi hú báo động tại chỗ, đồng thời tự động gửi tin nhắn cảnh báo khẩn cấp kèm thời gian chính xác tới Telegram của Chủ nhà."*
- **Mức độ quan trọng**: Cao.

---

### SLIDE 11: THIẾT LẬP THIẾT BỊ BLE VÀ BẢO MẬT ĐÁM MÂY PHÂN QUYỀN RBAC
- **Mục đích**: Trình bày quy trình cài đặt thiết bị BLE chuẩn thương mại và cơ chế phân quyền bảo mật đa người dùng trên Cloud.
- **Thông điệp chính**: Cài đặt Wi-Fi không chạm qua BLE GATT; phân quyền Master/Member rõ ràng; giao dịch nguyên tử chống rủi ro mất quyền.
- **Nội dung chính**:
  - BLE GATT Provisioning: Tìm kiếm "SmartHome-ESP32-Setup", quét Wi-Fi lân cận, truyền tham số mạng qua gói MTU 512 bytes an toàn.
  - Phân quyền người dùng RBAC: Chủ nhà (Master) toàn quyền; Thành viên (Member) phân quyền độc lập 9 thiết bị/chức năng.
  - Mã mời 1 lần (Single-Use Invite Code): Định dạng `HM-XXXXXXXX`, xác thực nguyên tử, tự thu hồi sau khi sử dụng.
  - Chuyển quyền Master nguyên tử (Atomic Multi-path Update): Cập nhật đồng thời 8 đường dẫn dữ liệu trong 1 transaction duy nhất, chống lỗi `Permission Denied` và rollback an toàn.
- **Hình ảnh gợi ý**: Ảnh chụp màn hình App màn hình cấu hình BLE, mã mời gia đình và bảng phân quyền thành viên (`BAO_CAO_DU_LIEU/EVIDENCE_IMAGES/screen_settings_perms.png`).
- **Speaker Notes (75 giây)**:  
  *"Ở khía cạnh trải nghiệm người dùng, đề tài đã giải quyết bài toán cài đặt thiết bị chuẩn thương mại qua Bluetooth BLE: Người dùng không cần ngắt mạng 4G để kết nối vào Wi-Fi do ESP32 phát ra, mà có thể cài đặt Wi-Fi trực tiếp qua Bluetooth chỉ trong 10 giây. Đồng thời, hệ thống triển khai mô hình phân quyền Chủ nhà và Thành viên chặt chẽ: Chủ nhà có thể cấp hoặc thu hồi quyền điều khiển từng thiết bị của con cái hay khách thuê nhà, tạo mã mời dùng một lần an toàn, và chuyển giao quyền làm Chủ nhà bằng một giao dịch nguyên tử đồng thời trên Firebase Cloud, đảm bảo dữ liệu không bao giờ bị xung đột."*
- **Mức độ quan trọng**: Rất cao.

---

### SLIDE 12: ỨNG DỤNG DI ĐỘNG NATIVE ANDROID VÀ GIAO DIỆN WEB DASHBOARD
- **Mục đích**: Giới thiệu các giao diện điều khiển đa nền tảng hoàn thiện thực tế của đồ án.
- **Thông điệp chính**: Giao diện hiện đại (Modern Aurora UI), hỗ trợ Dark/Light mode, hiển thị trạng thái thời gian thực và nhật ký phân tích.
- **Nội dung chính**:
  - Android App: Lập trình Native bằng Kotlin, giao diện Declarative UI hiện đại với Jetpack Compose, kiến trúc MVVM, Material 3.
  - Web Dashboard: Kiến trúc Single Page Application (SPA), Pure HTML5 / CSS3 / Vanilla JS, hỗ trợ Progressive Web App (PWA) cài đặt lên màn hình chính.
  - Bảng điều khiển trực quan: Giám sát nhiệt độ, độ ẩm, trạng thái có người, công tắc điều khiển tức thì, đồ thị lịch sử.
  - Đạt tiêu chuẩn trải nghiệm: Chuyển đổi Dark/Light mode mượt mà, phân loại nhật ký sự kiện thành 4 tab (Tất cả, Giọng nói, Cửa, Tự động hóa).
- **Hình ảnh gợi ý**: Ảnh chụp giao diện App Android (`ldplayer_verified_v127.png`, `screen_home_final.png`) ghép đôi cùng giao diện Web Desktop (`aurora_desktop.png`).
- **Speaker Notes (60 giây)**:  
  *"Để người dùng dễ dàng kiểm soát ngôi nhà, em đã xây dựng song song hai giao diện: Ứng dụng di động Native Android viết bằng Jetpack Compose hiện đại và Bảng điều khiển Web Dashboard chuẩn SPA. Cả hai giao diện đều hỗ trợ chế độ Sáng/Tối, hiển thị trực quan thông số cảm biến môi trường theo thời gian thực và đồng bộ hai chiều với thiết bị chỉ trong vòng 200 mili-giây. Người dùng có thể xem lại toàn bộ lịch sử ra lệnh giọng nói, lịch sử mở cửa và quản trị quyền của từng thành viên một cách tiện lợi."*
- **Mức độ quan trọng**: Cao.

---

### SLIDE 13: ĐÁNH GIÁ THỰC NGHIỆM VÀ PHÂN TÍCH DỮ LIỆU HOẠT ĐỘNG
- **Mục đích**: Trình bày kết quả phân tích dữ liệu thực chứng thu thập được trong quá trình vận hành hệ thống.
- **Thông điệp chính**: Số liệu thực tế có căn cứ khoa học từ 748 lượt điều khiển, phản ánh đúng hành vi sinh hoạt thực tế của con người.
- **Nội dung chính**:
  - Phân tích 748 log lệnh giọng nói: Tỷ lệ thực thi đúng $100\%$, độ trễ đóng ngắt rơ-le cục bộ $< 1.0	ext{ ms}$.
  - Phân bố khung giờ sử dụng: Tập trung cao điểm vào buổi sáng (7h – 8h) và buổi tối (18h – 22h) khi gia đình sinh hoạt.
  - Tương quan nhiệt độ & điều khiển quạt: Khi nhiệt độ $> 30^\circ\text{C}$ và có người, tần suất bật quạt tăng gấp **3.2 lần**. Động cơ tự động hóa giúp giảm **45%** thao tác thủ công của người dùng.
  - Hoạt động khi mất mạng: Thử nghiệm ngắt Access Point Wi-Fi, hệ thống vẫn duy trì 100% khả năng điều khiển giọng nói và mở khóa cửa.
- **Hình ảnh gợi ý**: Biểu đồ phân tích dữ liệu 4 thành phần xuất từ Python (`reports/analysis_report_chart.png`).
- **Speaker Notes (75 giây)**:  
  *"Trên màn hình là kết quả phân tích từ tập dữ liệu thực tế gồm 748 lượt lệnh giọng nói ghi nhận trên máy chủ Firebase. Dữ liệu chứng minh thời gian phản hồi từ khi dứt khẩu lệnh đến khi rơ-le đóng ngắt là dưới 1 mili-giây. Phân tích thống kê theo giờ cho thấy người dùng tương tác nhiều nhất vào buổi sáng và buổi tối. Đặc biệt, đồ thị tương quan nhiệt độ khẳng định tính hiệu quả của động cơ tự động hóa ngữ cảnh: Việc quạt tự động bật khi nhiệt độ phòng vượt 30 độ C đã giúp giảm tới 45% số lần người dùng phải tự điều khiển bằng tay. Toàn bộ các thử nghiệm ngắt mạng Wi-Fi đều chứng minh hệ thống vận hành độc lập, bền bỉ."*
- **Mức độ quan trọng**: Rất cao.

---

### SLIDE 14: KẾT LUẬN VÀ HƯỚNG PHÁT TRIỂN TIẾP THEO
- **Mục đích**: Tổng kết toàn bộ kết quả đạt được của đồ án, khẳng định giá trị ứng dụng và vạch ra định hướng nâng cấp.
- **Thông điệp chính**: Đề tài đã hoàn thành xuất sắc toàn bộ mục tiêu kỹ thuật đặt ra, sẵn sàng cho việc thương mại hóa.
- **Nội dung chính**:
  - **Kết quả đạt được**:
    1. Nhúng thành công TinyML KWS 10 nhãn tiếng Việt trên ESP32-S3 với Arena chỉ 12KB, trễ suy luận 40ms, điều khiển tải tức thì.
    2. Thiết kế phần cứng ổn định, chống sụt áp, khóa cửa ma trận Salted SHA-256 an toàn cao.
    3. Tự động hóa ngữ cảnh cục bộ và cài đặt Wi-Fi BLE chuẩn thương mại.
    4. Hoàn thiện hệ sinh thái Android App và Web Cloud RBAC với độ tin cậy thực nghiệm 100%.
  - **Hướng phát triển tương lai**: Thiết kế bo mạch in PCB 2 lớp thu gọn âm tường, mở rộng tập lệnh giọng nói 25 từ khóa, tích hợp giao thức Matter kết nối Apple HomeKit / Google Home.
- **Hình ảnh gợi ý**: Hình ảnh tổng thể mô hình hoạt động hoàn chỉnh cùng lời cảm ơn Quý Thầy Cô trong Hội đồng.
- **Speaker Notes (45 giây)**:  
  *"Kính thưa Hội đồng, đồ án đã hoàn thành trọn vẹn tất cả các mục tiêu kỹ thuật đề ra, chứng minh tính khả thi hoàn toàn của việc đưa trí tuệ nhân tạo giọng nói tiếng Việt xuống các vi điều khiển giá rẻ, giải quyết đồng thời bài toán tốc độ, độ tin cậy và quyền riêng tư gia đình. Trong tương lai, em sẽ tiếp tục tối ưu phần cứng thành mạch in PCB âm tường và mở rộng tập lệnh giọng nói phong phú hơn. Em xin chân thành cảm ơn quý thầy cô đã lắng nghe và em rất mong nhận được những ý kiến đóng góp của Hội đồng!"*
- **Mức độ quan trọng**: Bắt buộc.

---

## 13. ĐỀ XUẤT 4 SLIDE PHỤ LỤC KỸ THUẬT (BACKUP / Q&A SLIDES)

### SLIDE 15 (PHỤ LỤC 1): SƠ ĐỒ NGUYÊN LÝ NGUỒN VÀ BẢNG CHÂN GPIO CHI TIẾT
- **Mục đích**: Dùng để trả lời khi hội đồng hỏi sâu về thiết kế phần cứng, chống sụt áp hoặc bố trí chân GPIO.
- **Nội dung**: Bảng gán chi tiết 25 chân GPIO (`BAO_CAO_DU_LIEU/DATA_TABLES/gpio_pinout_matrix.csv`), sơ đồ mạch hạ áp LM2596/MP1584EN và vị trí lắp đặt các tụ bù dòng 2000µF.

### SLIDE 16 (PHỤ LỤC 2): THUẬT TOÁN BĂM SALTED SHA-256 VÀ QUY TRÌNH QUÉT MA TRẬN PHÍM
- **Mục đích**: Trả lời câu hỏi về giải thuật an ninh mật mã học và xử lý chống rung bàn phím ma trận.
- **Nội dung**: Sơ đồ băm SHA-256 kèm khối ngẫu nhiên muối 16-byte (`mbedtls/sha256.h`), thuật toán quét 4 hàng output - 3 cột input pullup với bộ lọc khử rung Debounce 50ms.

### SLIDE 17 (PHỤ LỤC 3): CẤU TRÚC PHÂN VÙNG FLASH VÀ TỐI ƯU HÓA BỘ NHỚ PSRAM
- **Mục đích**: Trả lời câu hỏi về quản lý bộ nhớ nhúng, chống tràn RAM và cơ chế cập nhật OTA.
- **Nội dung**: Bảng phân vùng bộ nhớ Flash 16MB (`partitions_custom.csv` với 2 phân vùng `app0` và `app1` 4.5MB mỗi bên), sơ đồ cấp phát Ring Buffer và Inference Buffer trên 8MB Octal PSRAM.

### SLIDE 18 (PHỤ LỤC 4): BỘ QUY TẮC BẢO MẬT FIREBASE SECURITY RULES VÀ GIAO DỊCH NGUYÊN TỬ
- **Mục đích**: Trả lời câu hỏi về an toàn đám mây, chống tấn công XSS, phân quyền truy cập NoSQL và giao dịch nguyên tử `updateChildren()`.
- **Nội dung**: Đoạn mã kiểm tra vai trò Master trong `database.rules.json` và lưu đồ giao dịch nguyên tử 8 nhánh ngăn chặn hiện tượng mất quyền Chủ nhà.

---

## 14. DANH SÁCH THÔNG TIN CẦN BỔ SUNG CHO TÁC GIẢ
Tác giả cần chuẩn bị thêm các mục sau trước ngày bảo vệ chính thức:
1. **Thông tin nhân thân**: Họ tên đầy đủ sinh viên, Mã số sinh viên (MSV), Tên người hướng dẫn khoa học, Ngày bảo vệ chính thức (điền vào Slide 1).
2. **Ảnh chụp mô hình phần cứng thật**: Chụp 1–2 bức ảnh góc rộng độ nét cao chụp toàn cảnh mô hình phần cứng thực tế (bo mạch ESP32, mô hình nhà gỗ, đèn, quạt, servo cửa) để chèn vào Slide 1 và Slide 5.
3. **Video clip dự phòng (Backup Video)**: Quay sẵn một video 60 giây quay cảnh ra lệnh giọng nói bật tắt đèn quạt và bấm phím mở cửa trên mô hình thật để phòng trường hợp thiết bị gặp sự cố nguồn điện tại phòng bảo vệ.

---

## 15. BỘ CÂU HỎI PHẢN BIỆN KỸ THUẬT DỰ KIẾN VÀ GỢI Ý TRẢ LỜI

### Câu 1: Tại sao em không dùng luôn các giải pháp nhận dạng giọng nói Cloud như Google Speech-to-Text hay Whisper API cho nhanh và nhận được nhiều từ hơn?
> **Gợi ý trả lời**:  
> *"Dạ thưa Thầy/Cô, việc sử dụng Cloud API có 3 nhược điểm lớn trong thực tế: Thứ nhất là độ trễ mạng thường từ 1.5 đến 3 giây, trong khi điều khiển thiết bị gia dụng như bật đèn hay mở cửa đòi hỏi phản hồi tức thì. Thứ hai, giải pháp Cloud bị tê liệt hoàn toàn khi mất mạng Internet hoặc đứt cáp quang. Thứ ba là vấn đề quyền riêng tư dữ liệu gia đình. Mục tiêu nghiên cứu cốt lõi của đề tài là TinyML (Edge AI) - chứng minh khả năng đưa trí tuệ nhân tạo trực tiếp xuống vi điều khiển giá rẻ để thiết bị vận hành độc lập 100%, bảo mật tuyệt đối và đạt độ trễ tác động rơ-le dưới 1 mili-giây."*

### Câu 2: Làm thế nào vi điều khiển ESP32-S3 có thể chạy được mô hình Deep Learning trong khi bộ nhớ RAM rất hạn chế?
> **Gợi ý trả lời**:  
> *"Dạ thưa Thầy/Cô, em đã áp dụng 3 kỹ thuật tối ưu hóa trọng yếu:  
> 1. Sử dụng kỹ thuật lượng tử hóa toàn phần sang số nguyên 8-bit (INT8 Full Quantization), giúp giảm 75% kích thước trọng số so với mô hình số thực Float32 mà độ chính xác gần như không suy giảm.  
> 2. Sử dụng trình biên dịch Edge Impulse EON Compiler để biên dịch mô hình thành mã C++ tĩnh thuần túy, loại bỏ hoàn toàn các toán tử thừa của TensorFlow Lite Runtime, giúp bộ nhớ Tensor Arena chỉ chiếm đúng 12.006 Bytes (khoảng 11.7 KB) SRAM.  
> 3. Cấp phát bộ đệm vòng thu âm 64KB và bộ đệm cửa sổ suy luận 32KB ra vùng nhớ ngoài Octal PSRAM 8MB thông qua hàm `ps_malloc()`, giữ cho vùng nhớ nội tuyến Internal SRAM luôn dư hơn 290KB cho ngăn xếp Wi-Fi và SSL."*

### Câu 3: Thuật toán VAD hoạt động như thế nào và tại sao lại đặt ngưỡng năng lượng là 7.000.000?
> **Gợi ý trả lời**:  
> *"Dạ thưa Thầy/Cô, thuật toán VAD tính tổng bình phương biên độ mẫu âm thanh trong mỗi khung 20ms (320 mẫu). Ban đầu em thử nghiệm đặt ngưỡng là 3.000.000, nhưng trong môi trường thực tế, tiếng gõ bàn phím và tiếng ồn quạt gió tạo ra mức năng lượng khoảng 3.5 đến 4.5 triệu, khiến bộ suy luận TinyML bị đánh thức liên tục làm CPU chạy 100% công suất và chip nóng lên. Qua quá trình đo đạc thực nghiệm trên hơn 700 mẫu, mức năng lượng của giọng nói người thực ở khoảng cách 1-2 mét đạt từ 15 triệu đến hơn 80 triệu. Vì vậy, em đã hiệu chuẩn ngưỡng lên mức 7.000.000 kết hợp cửa sổ giữ âm 500ms, giúp lọc sạch 99.4% tạp âm môi trường và giữ mức chiếm dụng CPU của Core 0 khi không có tiếng nói ở mức dưới 2%."*

### Câu 4: Khóa cửa của em bảo mật như thế nào? Nếu kẻ trộm tháo bo mạch và đọc trích xuất chip Flash ESP32 thì có lấy được mật khẩu không?
> **Gợi ý trả lời**:  
> *"Dạ thưa Thầy/Cô, hoàn toàn không thể lấy được mật khẩu. Hệ thống của em không lưu mật khẩu bản rõ vào bộ nhớ Flash. Khi người dùng thiết lập mã PIN, vi điều khiển tự sinh một chuỗi muối ngẫu nhiên cryptographically secure 16-byte và tính toán mã băm một chiều Salted SHA-256 32-byte rồi mới lưu vào NVS. Kẻ gian dù dùng máy đọc ROM trích xuất toàn bộ dữ liệu nhị phân của Flash cũng chỉ thu được chuỗi băm và muối ngẫu nhiên, không thể đảo ngược thuật toán SHA-256 để tìm ra mã PIN gốc. Ngoài ra, bàn phím còn có cơ chế tự động khóa cứng 60 đến 300 giây và hú còi báo động khi nhập sai liên tiếp 4 lần."*

### Câu 5: Tại sao khi chuyển quyền Chủ nhà (Master) cho người khác lại cần dùng Giao dịch nguyên tử (Atomic Multi-path Update)?
> **Gợi ý trả lời**:  
> *"Dạ thưa Thầy/Cô, trong các ứng dụng thông thường, việc chuyển quyền thường được viết bằng nhiều câu lệnh ghi tuần tự: đổi MasterUid trước, sau đó đổi quyền người nhận thành Master, rồi hạ quyền người cũ xuống Member. Điều này dẫn đến lỗ hổng nguy hiểm: Ngay khi câu lệnh 1 chạy xong, người cũ đã mất quyền Master trên Firebase. Đến câu lệnh 2, hệ thống bảo mật Firebase Security Rules kiểm tra thấy người gửi không còn là Master nên chặn lại và báo lỗi 'Permission Denied'. Hậu quả là người cũ mất quyền, còn người mới chưa được nhận quyền, ngôi nhà bị mất Chủ nhà. Để giải quyết triệt để, em sử dụng hàm `updateChildren()` của Firebase để gộp toàn bộ 8 đường dẫn dữ liệu thành một giao dịch nguyên tử duy nhất (Atomic Multi-path Update). Toàn bộ được ghi nhận đồng thời hoặc cùng hủy bỏ (Rollback) nếu có lỗi, đảm bảo an toàn tuyệt đối 100%."*

---
*Hồ sơ kỹ thuật PPT_HANDOFF.md được hoàn thiện ngày 23/11/2026 dựa trên mã nguồn thực tế của dự án.*
