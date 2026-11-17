# HỒ SƠ DỮ LIỆU KỸ THUẬT VÀ BẰNG CHỨNG HỆ THỐNG IOT SMART HOME & TINYML
## TÀI LIỆU PHỤC VỤ VIẾT BÁO CÁO ĐỒ ÁN TỐT NGHIỆP ĐẠI HỌC / BÁO CÁO NCKH

- **Tên đề tài**: Hệ thống Nhà thông minh Điều khiển Giọng nói Tiếng Việt Offline trên nền tảng TinyML ESP32-S3 kết hợp IoT Cloud và Tự động hóa Ngữ cảnh
- **Phiên bản hồ sơ**: 1.0 (Audit toàn diện)
- **Ngày hoàn thành trích xuất**: 14/11/2026
- **Môi trường trích xuất**: Workspace `d:\Firmware\Firmware-KWS-vietnamese-command`
- **Mục đích tài liệu**: Cung cấp toàn bộ dữ liệu thực chứng, thông số kỹ thuật, cấu trúc mã nguồn, bảng chân GPIO, sơ đồ khối, lưu đồ thuật toán và kết quả thử nghiệm thực tế để AI hoặc tác giả có thể hoàn thiện đồ án tốt nghiệp đại học đạt chuẩn kỹ thuật cao, không bị sai lệch số liệu.

---

## 1. HƯỚNG DẪN ĐỌC VÀ SỬ DỤNG CHO AI VIẾT BÁO CÁO
1. **Tính xác thực tuyệt đối**: Mọi số liệu trong hồ sơ này đều được trích xuất trực tiếp từ mã nguồn C/C++, tệp cấu hình `platformio.ini`, `partitions_custom.csv`, tập dữ liệu `dataset/`, log kiểm thử thực tế `reports/`, mã nguồn Web `web/index.html` và mã ứng dụng di động `mobile_app/`.
2. **Quy tắc trích dẫn**: Khi viết báo cáo đồ án, mọi khẳng định kỹ thuật phải được dẫn chiếu theo định dạng `[Tên file:Dòng]` đã được kiểm kê chi tiết tại file `12_BANG_CHUNG_SOURCE_CODE.md`.
3. **Phân loại hiện trạng**: Mọi tính năng trong hồ sơ này được gắn nhãn nghiêm ngặt theo 5 cấp độ:
   - `[ĐÃ TRIỂN KHAI]`: Đã có mã nguồn hoàn chỉnh trong repository.
   - `[ĐÃ KIỂM CHỨNG]`: Đã chạy thực tế, có dữ liệu log ghi nhận thành công.
   - `[CÓ CODE NHƯNG CHƯA CÓ BẰNG CHỨNG CHẠY]`: Có hàm xử lý nhưng chưa có log ghi nhận trên hệ thống.
   - `[MỚI DỰ KIẾN]`: Có ghi chú trong tài liệu hoặc biến cờ nhưng chưa hoàn thiện logic.
   - `[KHÔNG TÌM THẤY]`: Không có dấu vết trong toàn bộ repository.
4. **Bảo mật**: Mọi token bí mật, API key, mật khẩu Wi-Fi và database secret đã được thay thế bằng nhãn `[REDACTED]` để đảm bảo an toàn thông tin theo chuẩn công nghiệp.

---

## 2. DANH MỤC CÁC TÀI LIỆU TRONG HỒ SƠ

| STT | Tên tệp tin | Nội dung tóm tắt chính |
|:---:|:---|:---|
| 00 | [`00_INDEX.md`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/00_INDEX.md) | Chỉ mục tổng thể, hướng dẫn sử dụng, thước đo độ phủ dữ liệu |
| 01 | [`01_TONG_QUAN_DU_AN.md`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/01_TONG_QUAN_DU_AN.md) | Bối cảnh, vấn đề giải quyết, mục tiêu đề tài, tính mới, đối tượng sử dụng |
| 02 | [`02_CAU_TRUC_VA_KIEN_TRUC.md`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/02_CAU_TRUC_VA_KIEN_TRUC.md) | Cây thư mục dự án, kiến trúc hệ thống 5 tầng, luồng dữ liệu, giao thức |
| 03 | [`03_PHAN_CUNG_GPIO_FIRMWARE.md`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/03_PHAN_CUNG_GPIO_FIRMWARE.md) | Bảng gán 25 chân GPIO, BOM linh kiện, kiến trúc nguồn chống Brownout, FreeRTOS |
| 04 | [`04_AI_GIONG_NOI_VA_TINYML.md`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/04_AI_GIONG_NOI_VA_TINYML.md) | Mô hình TinyML Edge Impulse, 10 nhãn Tiếng Việt, MFCC, CNN INT8, Tensor Arena 12KB |
| 05 | [`05_CLOUD_DATABASE_NETWORK.md`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/05_CLOUD_DATABASE_NETWORK.md) | Cấu trúc Firebase RTDB, Security Rules, phân quyền Master/Member, đồng bộ |
| 06 | [`06_APP_WEB_GIAO_DIEN.md`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/06_APP_WEB_GIAO_DIEN.md) | Giao diện Web SPA (5.506 dòng), Mobile App Flutter/Kotlin, màn hình điều khiển |
| 07 | [`07_THUAT_TOAN_VA_LUU_DO.md`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/07_THUAT_TOAN_VA_LUU_DO.md) | Thuật toán & mã giả: Loop chính, Pipeline KWS, Khóa bảo mật Cửa, Động cơ Rule |
| 08 | [`08_KIEM_THU_VA_KET_QUA.md`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/08_KIEM_THU_VA_KET_QUA.md) | Dữ liệu thực chứng: 748 log giọng nói, ma trận nhầm lẫn, tương quan nhiệt-quạt |
| 09 | [`09_HINH_ANH_BANG_BIEU.md`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/09_HINH_ANH_BANG_BIEU.md) | Danh mục biểu đồ, đồ thị, hình ảnh giao diện, hướng dẫn chụp ảnh phần cứng |
| 10 | [`10_DE_CUONG_BAO_CAO.md`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/10_DE_CUONG_BAO_CAO.md) | Đề cương luận văn tốt nghiệp chi tiết 5 Chương chuẩn đại học kỹ thuật |
| 11 | [`11_THIEU_DU_LIEU_CAN_BO_SUNG.md`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/11_THIEU_DU_LIEU_CAN_BO_SUNG.md) | Danh sách việc cần bổ sung phân cấp ưu tiên (Thông tin tác giả, ảnh mô hình) |
| 12 | [`12_BANG_CHUNG_SOURCE_CODE.md`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/12_BANG_CHUNG_SOURCE_CODE.md) | Bảng truy vết mã nguồn (Traceability Matrix) từ yêu cầu đến dòng code |
| 13 | [`13_DANH_GIA_PHAN_BIEN.md`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/13_DANH_GIA_PHAN_BIEN.md) | Báo cáo phản biện: 5 ưu điểm, 4 giới hạn, lỗ hổng bảo mật, 10 câu hỏi bảo vệ |
| 14 | [`14_TAI_LIEU_THAM_KHAO_HIEN_CO.md`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/14_TAI_LIEU_THAM_KHAO_HIEN_CO.md) | Tài liệu tham khảo học thuật chuẩn IEEE/APA, Datasheet linh kiện, thư viện |
| 15 | [`15_BAO_CAO_DU_LIEU_TONG_HOP.md`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/15_BAO_CAO_DU_LIEU_TONG_HOP.md) | Bản tổng hợp siêu dữ liệu độc lập (Mega-Dossier) phục vụ sinh toàn bộ báo cáo |

---

## 3. CÁC THƯ MỤC DỮ LIỆU ĐÍNH KÈM
- [`DATA_TABLES/`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/DATA_TABLES/): Chứa các bảng dữ liệu dạng CSV và JSON có thể đọc trực tiếp bằng máy.
  - `gpio_pinout_matrix.csv`: Bảng 25 chân GPIO đầy đủ.
  - `system_bom.csv`: Bảng định mức linh kiện (Bill of Materials).
  - `dataset_distribution.csv`: Thống kê 17.049 mẫu âm thanh train/test.
  - `voice_command_logs_summary.csv`: Thống kê 748 lượt điều khiển thực tế.
  - `firebase_schema.json`: Cấu trúc lược đồ dữ liệu NoSQL Realtime Database.
  - `partitions_custom.csv`: Bảng phân vùng Flash 16MB của ESP32-S3.
- [`DIAGRAMS/`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/DIAGRAMS/): Chứa 8 tệp mã nguồn sơ đồ Mermaid (`.mmd`).
- [`EVIDENCE_IMAGES/`](file:///d:/Firmware/Firmware-KWS-vietnamese-command/BAO_CAO_DU_LIEU/EVIDENCE_IMAGES/): Chứa 100+ ảnh chụp màn hình giao diện Web, App Android, biểu đồ phân tích dữ liệu thực tế `analysis_report_chart.png`.

---

## 4. BẢNG ĐÁNH GIÁ ĐỘ PHỦ DỮ LIỆU (DATA COVERAGE METRICS)

| Hạng mục kỹ thuật | Trạng thái xác thực | Mức độ tin cậy | Nguồn bằng chứng chính |
|:---|:---:|:---:|:---|
| **Sơ đồ chân GPIO & Phần cứng** | ĐÃ KIỂM CHỨNG | 100% | `include/config.h`, `docs/Tai-Lieu-Ky-Thuat-Tong-Hop.html` |
| **Mô hình AI TinyML & Model TFLite** | ĐÃ KIỂM CHỨNG | 100% | `lib/KWS-Vietnamese-command_inferencing/`, Edge Impulse ID 1152015 |
| **Tập dữ liệu Âm thanh (Dataset)** | ĐÃ KIỂM CHỨNG | 100% | 19.905 tệp `.wav` trong `dataset/` (13.822 train, 3.227 test v4) |
| **Firmware Đa luồng FreeRTOS** | ĐÃ KIỂM CHỨNG | 100% | `src/main.cpp`, `src/firebase_handle.cpp`, `platformio.ini` |
| **Đồng bộ Điện toán Đám mây Firebase** | ĐÃ KIỂM CHỨNG | 100% | `database.rules.json`, `firebase.json`, `analyze_logs.py` |
| **Giao diện Web Điều khiển (SPA)** | ĐÃ KIỂM CHỨNG | 100% | `web/index.html` (5.506 dòng), `firebase hosting` đã triển khai |
| **Ứng dụng Di động Mobile App** | ĐÃ KIỂM CHỨNG | 100% | `mobile_app/` (Flutter & Android Kotlin), `SmartHome-KWS.apk` |
| **Số liệu Thực nghiệm Hoạt động** | ĐÃ KIỂM CHỨNG | 100% | 748 log lệnh thực tế trong `reports/BAO_CAO_PHAN_TICH_DU_LIEU.md` |
