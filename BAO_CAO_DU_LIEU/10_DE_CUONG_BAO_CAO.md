# ĐỀ CƯƠNG BÁO CÁO ĐỒ ÁN TỐT NGHIỆP ĐẠI HỌC (CHUẨN CHUYÊN NGÀNH)

Đề cương được biên soạn theo đúng cấu trúc chuẩn của Luận văn tốt nghiệp Kỹ sư / Cử nhân ngành Kỹ thuật Máy tính, Điện tử - Viễn thông, Công nghệ Thông tin và Hệ thống Nhúng & IoT:

---

## TRANG TIÊU ĐỀ VÀ THỦ TỤC
- TRANG BÌA CHÍNH (Tên trường, Khoa, Ngành, Tên đề tài, Giảng viên hướng dẫn, Sinh viên thực hiện, Năm học)
- TRANG BÌA PHỤ
- NHIỆM VỤ ĐỒ ÁN TỐT NGHIỆP
- LỜI CAM ĐOAN CỦA SINH VIÊN
- LỜI CẢM ƠN
- TÓM TẮT ĐỒ ÁN (ABSTRACT - TIẾNG VIỆT VÀ TIẾNG ANH)
- MỤC LỤC
- DANH MỤC CÁC TỪ VIẾT TẮT (ASR, CNN, DMA, DSP, GPIO, I2S, KWS, MFCC, NVS, PWM, RTDB, SPA, TinyML, UART, VAD)
- DANH MỤC CÁC BẢNG BIỂU
- DANH MỤC CÁC HÌNH VẼ VÀ ĐỒ THỊ

---

## MỞ ĐẦU
1. Tính cấp thiết của đề tài (Sự bùng nổ của IoT và bài toán quyền riêng tư, phụ thuộc đám mây của nhà thông minh).
2. Mục tiêu nghiên cứu của đề tài (Làm chủ công nghệ Edge AI/TinyML tiếng Việt và kiến trúc IoT tự phục hồi).
3. Đối tượng và phạm vi nghiên cứu (Vi điều khiển ESP32-S3, nhận dạng giọng nói Tiếng Việt 10 lớp, giao tiếp Firebase).
4. Phương pháp nghiên cứu (Nghiên cứu lý thuyết kết hợp thực nghiệm phần cứng và phát triển phần mềm toàn diện).
5. Ý nghĩa khoa học và thực tiễn của đề tài.
6. Bố cục của đồ án tốt nghiệp.

---

## CHƯƠNG 1: TỔNG QUAN VÀ CƠ SỞ LÝ THUYẾT
### 1.1. Tổng quan về Hệ thống Nhà thông minh (Smart Home)
- 1.1.1. Lịch sử phát triển và xu hướng Smart Home hiện đại.
- 1.1.2. Các hệ thống điều khiển giọng nói thương mại (Google Home, Amazon Alexa) và những rào cản kỹ thuật.
### 1.2. Công nghệ Học máy trên Thiết bị Biên (TinyML & Edge AI)
- 1.2.1. Khái niệm TinyML và sự chuyển dịch từ Cloud AI sang Edge AI.
- 1.2.2. Bài toán Nhận dạng Từ khóa Giọng nói (Keyword Spotting - KWS).
- 1.2.3. Các kỹ thuật lượng tử hóa mô hình học sâu (INT8 Quantization, Post-training Quantization).
### 1.3. Cơ sở lý thuyết Xử lý Tín hiệu Âm thanh và Trích xuất Đặc trưng MFCC
- 1.3.1. Phân khung (Framing) và hàm cửa sổ (Windowing).
- 1.3.2. Biến đổi Fourier nhanh (FFT) và Thang đo tần số Mel (Mel Scale Filterbanks).
- 1.3.3. Biến đổi Cosine rời rạc (DCT) và Trích xuất hệ số Cepstral (MFCC).
- 1.3.4. Thuật toán phát hiện tiếng nói VAD (Voice Activity Detection) dựa trên năng lượng tín hiệu.
### 1.4. Nền tảng Vi điều khiển ESP32-S3 và Hệ điều hành Thời gian thực FreeRTOS
- 1.4.1. Kiến trúc phần cứng Dual-Core Xtensa LX7 và tập lệnh tăng tốc Vector Instructions.
- 1.4.2. Khái niệm Task, Queue, Mutex và cơ chế lập lịch đa luồng trong FreeRTOS.
### 1.5. Nền tảng Điện toán Đám mây Google Firebase Realtime Database
- 1.5.1. Cơ chế truyền thông WebSocket Real-time Streaming.
- 1.5.2. Chính sách phân quyền bảo mật qua Firebase Security Rules.

---

## CHƯƠNG 2: THIẾT KẾ KIẾN TRÚC HỆ THỐNG
### 2.1. Phân tích Yêu cầu Kỹ thuật của Hệ thống
- 2.1.1. Yêu cầu chức năng (Điều khiển giọng nói, tự động hóa cảm biến, khóa an ninh, giám sát đám mây).
- 2.1.2. Yêu cầu phi chức năng (Độ trễ < 50ms, tính độc lập khi mất mạng, độ ổn định nguồn điện, bảo mật mã PIN).
### 2.2. Kiến trúc Hệ thống Phân tầng (5 Tầng)
- 2.2.1. Tầng Ngoại vi & Chấp hành (Peripherals & Actuators).
- 2.2.2. Tầng Xử lý Nhúng Cục bộ (Edge Computing Hub).
- 2.2.3. Tầng Truyền thông Mạng (Communication & Networking).
- 2.2.4. Tầng Điện toán Đám mây (Cloud Platform).
- 2.2.5. Tầng Ứng dụng Người dùng (Client Applications).
### 2.3. Phân bổ và Thiết kế Nguồn Điện Chống Hiện tượng Sụt áp (Brownout Prevention)
- 2.3.1. Tính toán dòng đỉnh tiêu thụ của các cơ cấu chấp hành (Servo, Relay, Wi-Fi TX).
- 2.3.2. Sơ đồ mạch lọc nguồn dung lượng lớn (1000µF + 100nF) và phân nhánh Common Ground.
### 2.4. Quy hoạch Bộ nhớ Flash và Phân vùng ESP32-S3 (Custom Partition Table)

---

## CHƯƠNG 3: THIẾT KẾ PHẦN CỨNG VÀ XÂY DỰNG MÔ HÌNH HỌC SÂU TINYML
### 3.1. Thiết kế Phần cứng và Sơ đồ Đấu nối Chi tiết
- 3.1.1. Module thu âm kỹ thuật số I2S INMP441 MEMS.
- 3.1.2. Cụm cảm biến môi trường: Cảm biến vi sóng mmWave LD2410S, DHT22 và quang trở LDR.
- 3.1.3. Cụm cơ cấu chấp hành: Module 3-Channel Relay, Động cơ Servo Cửa trượt và Rèm cửa.
- 3.1.4. Phân hệ an ninh: Bàn phím ma trận 4x3, công tắc hành trình, nút bấm cơ mở cửa và Buzzer.
- 3.1.5. Bảng gán 25 chân GPIO hoàn chỉnh (Master Pinout Matrix).
### 3.2. Xây dựng Tập dữ liệu Giọng nói Tiếng Việt (Dataset Engineering)
- 3.2.1. Thu thập dữ liệu âm thanh thực tế với 10 nhãn khẩu lệnh.
- 3.2.2. Tiền xử lý dữ liệu: Chuẩn hóa độ dài 1.0s, tần số 16kHz 16-bit Mono PCM, lọc nhiễu nền.
- 3.2.3. Thống kê và phân chia tập Train (13.822 mẫu) và tập Test (3.227 mẫu).
### 3.3. Thiết kế và Huấn luyện Mô hình Học sâu (Deep Learning Model)
- 3.3.1. Cấu hình khối trích xuất đặc trưng MFCC (13 hệ số x 50 khung = 650 giá trị).
- 3.3.2. Kiến trúc mạng nơ-ron tích chập 2D (2D-CNN Architecture) tối ưu hóa cho vi điều khiển.
- 3.3.3. Lượng tử hóa mô hình sang INT8 và tối ưu hóa qua EON Compiler của Edge Impulse.
- 3.3.4. Đánh giá dung lượng mô hình và Tensor Arena (12.006 bytes).

---

## CHƯƠNG 4: PHÁT TRIỂN PHẦN MỀM NHÚNG VÀ HỆ THỐNG ĐIỀU KHIỂN
### 4.1. Kiến trúc Firmware FreeRTOS Đa nhân trên ESP32-S3
- 4.1.1. Phân bổ nhiệm vụ: Core 1 (Xử lý âm thanh, AI, Phần cứng) và Core 0 (Truyền thông Cloud).
- 4.1.2. Cơ chế truyền thông liên tác vụ không nghẽn qua Hàng đợi `s_fbQueue`.
### 4.2. Triển khai Chuỗi Xử lý Âm thanh và Nhận diện KWS Thời gian thực
- 4.2.1. Thu âm qua DMA I2S và Cửa sổ trượt 1000ms với bước nhảy 200ms.
- 4.2.2. Thuật toán VAD lọc năng lượng âm thanh tiết kiệm năng lượng vi điều khiển.
### 4.3. Động cơ Tự động hóa Đa ngữ cảnh và Thuật toán Giải quyết Xung đột
- 4.3.1. Đánh giá đa điều kiện (Nhiệt độ, mmWave, LDR, NTP Time).
- 4.3.2. Giải thuật giải quyết xung đột tất định với nguyên tắc An toàn là trên hết (Safety First).
### 4.4. Hệ thống Kiểm soát Cửa Bảo mật Cao
- 4.4.1. Giải thuật băm Salted SHA-256 lưu trữ trong NVS Flash.
- 4.4.2. Cơ chế khóa cứng phần cứng 120s và cảnh báo còi khi nhập sai 4 lần.
### 4.5. Phát triển Giao diện Ứng dụng Web Single Page Application (SPA)
- 4.5.1. Thiết kế giao diện Dashboard theo phong cách Aurora Glassmorphism (5.506 dòng JS/HTML/CSS).
- 4.5.2. Phân hệ quản trị tự động hóa và quản lý mã PIN cửa.
- 4.5.3. Phân quyền người dùng Master/Member và cơ chế sinh mã lời mời.
### 4.6. Phát triển Ứng dụng Di động Android và Flutter

---

## CHƯƠNG 5: THỬ NGHIỆM THỰC TẾ, ĐÁNH GIÁ KẾT QUẢ VÀ BÀN LUẬN
### 5.1. Kịch bản Thử nghiệm và Môi trường Thực nghiệm
### 5.2. Kết quả Đánh giá Mô hình TinyML qua 748 Lệnh Thực tế trên Firebase
- 5.2.1. Độ chính xác nhận diện và ma trận nhầm lẫn của từng từ khóa khẩu lệnh.
- 5.2.2. Thời gian phản hồi điều khiển (Local Latency vs Cloud Latency).
### 5.3. Phân tích Dữ liệu Hành vi Người dùng và Quy luật Tương quan Cảm biến
- 5.3.1. Biểu đồ phân bố tần suất ra lệnh theo 24 giờ trong ngày.
- 5.3.2. Mối tương quan giữa nhiệt độ môi trường và hành vi điều khiển quạt làm mát.
### 5.4. Đánh giá Độ tin cậy của Hệ thống khi Xảy ra Sự cố Mạng (Failover & Self-healing)
### 5.5. Đánh giá So sánh với các Hệ thống Smart Home Hiện có

---

## KẾT LUẬN VÀ HƯỚNG PHÁT TRIỂN
1. Các kết quả đã đạt được của đề tài.
2. Những đóng góp mới của đề tài về mặt học thuật và kỹ thuật thực tiễn.
3. Những hạn chế còn tồn tại.
4. Hướng phát triển tiếp theo (Tích hợp nhận dạng cử chỉ bàn tay Gesture, phát triển mạng BLE Mesh, cá nhân hóa giọng nói Speaker Verification).

---

## TÀI LIỆU THAM KHẢO
## PHỤ LỤC
- Phụ lục 1: Toàn văn Bảng gán chân GPIO và Sơ đồ nối dây.
- Phụ lục 2: Trích đoạn mã nguồn Firmware cốt lõi (`src/main.cpp`, `src/door_controller.cpp`).
- Phụ lục 3: Cấu hình phân quyền bảo mật Firebase `database.rules.json`.
