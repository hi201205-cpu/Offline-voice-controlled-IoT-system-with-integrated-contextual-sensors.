# BÁO CÁO PHẢN BIỆN HỆ THỐNG VÀ BỘ CÂU HỎI BẢO VỆ ĐỒ ÁN

## 1. NĂM ƯU ĐIỂM KỸ THUẬT VƯỢT TRỘI (TECHNICAL STRENGTHS)
1. **Khả năng hoạt động Ngoại tuyến Hoàn toàn (True Offline Edge AI)**:
   - Mô hình TinyML KWS chạy 100% cục bộ trên vi điều khiển ESP32-S3, triệt tiêu sự phụ thuộc vào Internet, đảm bảo quyền riêng tư tuyệt đối cho không gian sống và thời gian phản hồi phần cứng dưới 1.0 ms.
2. **Kiến trúc Firmware Đa luồng FreeRTOS Chuẩn mực**:
   - Tách biệt Core 1 cho các tác vụ thời gian thực cứng (lấy mẫu âm thanh DMA, suy luận nơ-ron, quét phím bảo mật) và Core 0 cho truyền thông mạng. Giao tiếp qua hàng đợi `s_fbQueue` giúp hệ thống không bao giờ bị nghẽn (non-blocking).
3. **An ninh Phần cứng Cấp cao (Hardware Hardened Security)**:
   - Không lưu mã PIN dạng chuỗi rõ (Plaintext) mà sử dụng mã băm Salted SHA-256 lưu trong NVS. Cơ chế phần cứng khóa cứng 120 giây và kích hoạt còi báo động sau 4 lần nhập sai ngăn chặn triệt để tấn công duyệt cạn (Brute-force).
4. **Động cơ Tự động hóa Thông minh với Nguyên lý An toàn Tuyệt đối (Safety First)**:
   - Thuật toán giải quyết xung đột đảm bảo các lệnh TẮT thiết bị và ĐÓNG cửa luôn có độ ưu tiên cao nhất, loại trừ hoàn toàn nguy cơ cháy nổ hoặc mất an ninh.
5. **Thiết kế Nguồn điện Chống Sụt áp (Brownout Resilient)**:
   - Giải pháp lọc nguồn dung lượng lớn (1000µF + 100nF) kết hợp tách nhánh nguồn riêng cho động cơ Servo giải quyết triệt để bài toán sụt áp kinh điển của các hệ thống nhúng IoT khi tải cảm ứng hoạt động.

---

## 2. BỐN GIỚI HẠN KỸ THUẬT VÀ ĐÁNH ĐỔI KIẾN TRÚC (LIMITATIONS & TRADE-OFFS)
1. **Giới hạn số lượng từ vựng nhận dạng (Vocabulary Bottleneck)**:
   - Do giới hạn bộ nhớ SRAM và năng lực tính toán của vi điều khiển biên, mô hình hiện tại chỉ nhận dạng cố định 10 từ khóa khẩu lệnh, chưa thể xử lý ngôn ngữ tự nhiên tự do (Free-form Natural Language Processing).
2. **Ảnh hưởng của môi trường ồn lớn (Acoustic Noise Sensitivity)**:
   - Sử dụng 01 micro đơn kênh INMP441 (không có mảng micro Beamforming). Khi môi trường có tiếng ồn cực lớn (SNR < 5dB) hoặc tiếng nói của nhiều người cùng lúc, tỷ lệ nhận diện có thể suy giảm.
3. **Điểm lỗi tập trung phần cứng (Single Point of Failure)**:
   - Toàn bộ thiết bị trong nhà (3 rơ-le, 2 servo, cảm biến) kết nối về một bo mạch ESP32-S3 duy nhất. Nếu bo mạch này gặp sự cố phần cứng, toàn bộ hệ thống tự động sẽ ngừng hoạt động.
4. **Độ trễ lấy mẫu của cảm biến nhiệt độ DHT22**:
   - DHT22 yêu cầu chu kỳ đọc tối thiểu 2.0 giây, chậm hơn so với các dòng cảm biến I2C công nghiệp (như SHT31, BME280 có tần số lấy mẫu lên đến 10 - 50Hz).

---

## 3. CÁC LỖ HỔNG BẢO MẬT VÀ GIẢI PHÁP NÂNG CẤP
- **Nguy cơ trích xuất bộ nhớ NVS**: Hiện tại phân vùng NVS chưa kích hoạt cờ mã hóa phần cứng Flash Encryption. Kẻ tấn công có thiết bị đọc ROM vật lý có thể dump được chuỗi băm Salted SHA-256 để dò ngược mã PIN. *Khắc phục: Kích hoạt tính năng Flash Encryption và Secure Boot v2 tích hợp sẵn trong ESP32-S3.*
- **Ủy quyền Database Secret**: Mã script `analyze_logs.py` đang sử dụng Database Secret để truy cập dữ liệu phục vụ nghiên cứu. *Khắc phục: Chuyển đổi sang Firebase Admin SDK với Service Account OAuth 2.0.*

---

## 4. TOP 10 CÂU HỎI THƯỜNG GẶP CỦA HỘI ĐỒNG PHẢN BIỆN VÀ GỢI Ý TRẢ LỜI

### Câu 1: Tại sao nhóm không dùng các API nhận dạng giọng nói đám mây như Google Cloud Speech-to-Text để nhận dạng được nhiều từ hơn?
> **Gợi ý trả lời**: Mục tiêu cốt lõi của đề tài là giải quyết bài toán **Quyền riêng tư (Privacy)** và **Tính độc lập thời gian thực (Real-time Edge Autonomy)**. Các API Cloud yêu cầu truyền âm thanh liên tục ra ngoài Internet, tạo nguy cơ lộ lọt dữ liệu đời tư và hoàn toàn tê liệt khi mất mạng. Giải pháp TinyML trên ESP32-S3 cho phép hệ thống vận hành 100% không cần Internet, độ trễ phản hồi rơ-le tức thì < 1ms, chi phí phần cứng siêu rẻ và hoàn toàn làm chủ công nghệ.

### Câu 2: Quá trình lượng tử hóa mô hình sang INT8 có làm suy giảm độ chính xác của mạng nơ-ron không?
> **Gợi ý trả lời**: Quá trình lượng tử hóa Post-training Quantization (PTQ) từ Float32 sang INT8 sử dụng tập dữ liệu đại diện (Representative Dataset) từ tập huấn luyện để định cỡ khoảng giá trị kích hoạt. Nhờ sử dụng 32 bộ lọc Mel và 13 hệ số MFCC với thông tin âm phổ tập trung cao, sai số lượng tử hóa là không đáng kể, trong khi giúp giảm kích thước mô hình đi 4 lần và giảm thời gian suy luận trên CPU xuống hơn 3 lần nhờ tập lệnh mở rộng Vector Instructions của chip Xtensa LX7.

### Câu 3: Làm thế nào để giải quyết vấn đề ESP32-S3 bị nóng khi chạy mô hình học sâu liên tục?
> **Gợi ý trả lời**: Nhóm đã thiết kế bộ lọc phát hiện hoạt động giọng nói **VAD (Voice Activity Detection)** dựa trên việc tính năng lượng RMS của tín hiệu âm thanh trước khi đưa vào mạng nơ-ron. Ngưỡng năng lượng được tối ưu hóa ở mức **7.000.000**. Khi môi trường yên tĩnh hoặc tiếng ồn thấp, vi điều khiển hoàn toàn không chạy khối MFCC và CNN, giữ mức tải CPU Core 1 gần như 0%, nhiệt độ chip duy trì bình thường ở mức 38°C - 42°C.

### Câu 4: Khi xảy ra sụt áp (Brownout) do động cơ Servo kéo dòng lớn, hệ thống xử lý như thế nào?
> **Gợi ý trả lời**: Nhóm đã triển khai giải pháp phần cứng toàn diện: sử dụng nguồn Adapter chuyển mạch xung 5V/3A độc lập, bổ sung tụ hóa dung lượng cao 1000µF/16V mắc song song với tụ gốm 100nF ngay tại thanh ray cấp nguồn 5V của Servo và Rơ-le, đồng thời tách riêng đường mass và đường nguồn điều khiển để triệt tiêu nhiễu xung nhọn.

### Câu 5: Tại sao hệ thống lại sử dụng cảm biến mmWave Radar LD2410S thay vì cảm biến hồng ngoại thụ động PIR truyền thống?
> **Gợi ý trả lời**: Cảm biến PIR truyền thống hoạt động dựa trên sự thay đổi bức xạ nhiệt khi người chuyển động lớn. Khi người ngồi làm việc, đọc sách hoặc ngủ yên, PIR sẽ báo nhầm là không có người và tự động tắt quạt/đèn. Cảm biến vi sóng mmWave LD2410S hoạt động ở tần số 24GHz với thuật toán FMCW radar cực kỳ nhạy, có khả năng phát hiện được vi chuyển động của lồng ngực khi thở, đảm bảo nhận diện chính xác trạng thái hiện diện của con người.

### Câu 6: Làm thế nào hệ thống giải quyết xung đột khi có nhiều quy tắc tự động hóa trái ngược nhau?
> **Gợi ý trả lời**: Động cơ tự động hóa đã cài đặt thuật toán giải quyết xung đột tất định (Deterministic Conflict Resolution) với nguyên tắc **An toàn là trên hết (Safety First)**: Khi có xung đột giữa lệnh BẬT/MỞ và lệnh TẮT/ĐÓNG trên cùng một tải thiết bị, lệnh TẮT/ĐÓNG luôn được ưu tiên thực thi tuyệt đối. Nếu hai quy tắc cùng loại lệnh, quy tắc có chỉ số Index khai báo nhỏ hơn sẽ được ưu tiên.

### Câu 7: Cơ chế bảo mật mật mã mở cửa của hệ thống hoạt động như thế nào? Có bị lộ khi mất điện không?
> **Gợi ý trả lời**: Hệ thống không bao giờ lưu trữ mật khẩu ở dạng văn bản rõ (plaintext). Chuỗi PIN người dùng nhập vào được kết hợp với một chuỗi ngẫu nhiên (Salt) và băm bằng giải thuật mật mã SHA-256. Chuỗi băm này được lưu trữ trong phân vùng bộ nhớ cố định NVS Flash của ESP32-S3 nên không bao giờ bị mất khi cúp điện. Khi xác thực, vi điều khiển chỉ so sánh giá trị băm. Ngoài ra, cơ chế khóa cứng 120 giây sau 4 lần nhập sai ngăn chặn hoàn toàn việc dò mã bằng máy tính.

### Câu 8: Việc phân chia tác vụ giữa 2 lõi CPU trong FreeRTOS được thực hiện như thế nào để không làm mất gói âm thanh I2S?
> **Gợi ý trả lời**: Core 1 được dành riêng cho tác vụ thời gian thực phần cứng: đọc dữ liệu từ bộ đệm DMA của I2S, tính toán năng lượng VAD, chạy suy luận TinyML và quét ma trận phím. Core 0 chạy tác vụ truyền thông `firebaseTask`. Khi Core 1 có sự kiện cần gửi lên đám mây, nó chỉ thực hiện lệnh đẩy gói tin vào Hàng đợi `s_fbQueue` (thao tác mất < 2µs) rồi quay lại chu kỳ lấy mẫu âm thanh ngay lập tức, không bao giờ bị ảnh hưởng bởi độ trễ truyền gói tin mạng của Core 0.

### Câu 9: Ứng dụng Web SPA quản lý trạng thái thiết bị như thế nào khi có độ trễ mạng?
> **Gợi ý trả lời**: Ứng dụng Web sử dụng cơ chế lắng nghe sự kiện đẩy (Event-driven Listeners) thông qua Firebase JS SDK v10 kết nối WebSocket. Đồng thời, giao diện cài đặt cơ chế cập nhật lạc quan (Optimistic UI Update) kết hợp cơ chế kiểm tra đồng bộ thực từ node `devices/state`. Khi người dùng bật công tắc trên Web, giao diện hiển thị trạng thái chờ và chỉ xác nhận hoàn tất khi ESP32-S3 phản hồi xác nhận trạng thái phần cứng thực tế qua Cloud.

### Câu 10: Chi phí phần cứng của toàn bộ hệ thống là bao nhiêu? Có khả thi để thương mại hóa không?
> **Gợi ý trả lời**: Tổng chi phí phần cứng của toàn bộ hệ thống thử nghiệm chỉ khoảng **450.000 - 650.000 VNĐ** (trong đó chip ESP32-S3 N16R8 chiếm ~120.000đ, Micro INMP441 ~30.000đ, Radar LD2410S ~80.000đ, cụm Relay và Servo ~150.000đ). Với mức chi phí linh kiện này, sản phẩm hoàn toàn có tiềm năng thương mại hóa cao, mang lại giải pháp Smart Home Edge AI giá rẻ, bảo mật vượt trội cho mọi gia đình Việt Nam.
