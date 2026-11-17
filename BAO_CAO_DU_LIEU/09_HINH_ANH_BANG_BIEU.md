# DANH MỤC HÌNH ẢNH, SƠ ĐỒ VÀ BẢNG BIỂU DỰ ÁN

## 1. DANH MỤC SƠ ĐỒ NGUYÊN LÝ VÀ BẢN VẼ KỸ THUẬT CÓ SẴN TRONG PROJECT
Các tệp tài liệu thiết kế phần cứng hoàn chỉnh định dạng HTML và PDF lưu trữ tại thư mục `docs/`:
1. **Sơ đồ nguyên lý điện tử tổng thể**:
   - `docs/1-so-do-nguyen-ly.html` & `docs/1-so-do-nguyen-ly.pdf` (220 KB)
   - Thể hiện chi tiết sơ đồ mạch nối ESP32-S3 với INMP441, DHT22, LD2410S, LDR, Module 3 Relay, 2 Servo, Buzzer, LED RGB và Bàn phím ma trận 4x3.
2. **Lưu đồ giải thuật toàn hệ thống**:
   - `docs/2-luu-do-thuat-toan.html` & `docs/2-luu-do-thuat-toan.pdf` (260 KB)
3. **Bản đồ đấu nối chân phần cứng (Hardware Pinout Diagram)**:
   - `docs/3-chan-ket-noi-phan-cung.html` & `docs/3-chan-ket-noi-phan-cung.pdf` (256 KB)
4. **Bảng định mức vật tư và danh sách linh kiện (BOM)**:
   - `docs/4-danh-sach-linh-kien.html` & `docs/4-danh-sach-linh-kien.pdf` (212 KB)
5. **Tài liệu kỹ thuật tổng hợp toàn diện**:
   - `docs/Tai-Lieu-Ky-Thuat-Tong-Hop.html` & `docs/Tai-Lieu-Ky-Thuat-Tong-Hop.pdf` (454 KB)

---

## 2. BIỂU ĐỒ VÀ ĐỒ THỊ THỰC CHỨNG DỮ LIỆU LỚN (EVIDENCE CHARTS)
Tệp biểu đồ tổng hợp 4 phân hệ độ phân giải cao được sinh tự động từ tệp `analyze_logs.py`:
- **Đường dẫn tệp**: `reports/analysis_report_chart.png` (Đã sao lưu tại `BAO_CAO_DU_LIEU/EVIDENCE_IMAGES/analysis_report_chart.png`).
- **Nội dung 4 khung đồ thị (Subplots)**:
  1. *Subplot 1 (Góc trên bên trái)*: Biểu đồ cột thể hiện phân bố số lượng gọi của từng từ khóa khẩu lệnh giọng nói (164 `bat_quat`, 152 `tat_het`, 149 `tat_quat`, 109 `tat_den`, 99 `bat_het`, 75 `bat_den`).
  2. *Subplot 2 (Góc trên bên phải)*: Biểu đồ đường thể hiện tần suất người dùng ra lệnh theo 24 khung giờ trong ngày (đỉnh cao lúc 7h, 8h, 10h sáng và 18h - 22h tối).
  3. *Subplot 3 (Góc dưới bên trái)*: Biểu đồ phân tán (Scatter Plot) kết hợp đường hồi quy thể hiện mối tương quan chặt chẽ giữa nhiệt độ môi trường và quyết định bật quạt làm mát.
  4. *Subplot 4 (Góc dưới bên phải)*: Biểu đồ phân phối độ tin cậy (Confidence Histogram) chứng minh 100% lệnh thực thi đều đạt độ tin cậy tối đa.

---

## 3. DANH MỤC HÌNH ẢNH GIAO DIỆN NGƯỜI DÙNG (UI SCREENSHOTS)
Hơn 100 ảnh chụp màn hình thực tế của hệ thống đã được lưu trữ sẵn trong thư mục `BAO_CAO_DU_LIEU/EVIDENCE_IMAGES/`:
- **Giao diện Web SPA Desktop & Mobile**:
  - `aurora_desktop.png`: Giao diện Dashboard phong cách Aurora Glassmorphism trên màn hình máy tính.
  - `aurora_mobile.png`: Giao diện Dashboard hiển thị tối ưu trên màn hình điện thoại thông minh.
  - `screen_dashboard_light_desktop.png` & `screen_dashboard_dark_desktop.png`: Chế độ Sáng / Tối của Bảng điều khiển.
  - `screen_automation_light_desktop.png`: Giao diện tạo quy tắc tự động hóa If-This-Then-That.
  - `door_tab.png`: Giao diện kiểm soát an ninh cửa, danh sách mã PIN và cờ cảnh báo khóa cứng.
  - `screen_settings_members.png`: Giao diện quản lý phân quyền thành viên gia đình và sinh mã mời.
  - `chart_new.png`, `chart_scrub.png`: Đồ thị giám sát lịch sử nhiệt ẩm thời gian thực.
- **Giao diện Ứng dụng Di động Android**:
  - `app_verified_home_light.png`: Màn hình chính ứng dụng Android hiển thị các thẻ cảm biến và công tắc thiết bị.
  - `app_verified_history.png`: Màn hình xem lại lịch sử các sự kiện điều khiển và telemetry.
  - `app_verified_master_online.png`: Trạng thái kết nối trực tuyến của tài khoản Chủ nhà Master.

---

## 4. HƯỚNG DẪN DÀNH CHO TÁC GIẢ: BỔ SUNG ẢNH CHỤP MÔ HÌNH THỰC TẾ
Để báo cáo đồ án đạt điểm tối đa từ hội đồng phản biện, tác giả cần tự chụp và chèn thêm 4 bức ảnh mô hình thực tế sau vào báo cáo:
1. **Hình ảnh tổng thể mô hình sa bàn (Smart Home Prototype Model)**: Chụp góc nghiêng 45 độ thấy rõ toàn bộ mô hình ngôi nhà (hộp mica hoặc gỗ), vị trí gắn cửa trượt, rèm cửa sổ, bóng đèn trần và quạt làm mát.
2. **Hình ảnh bo mạch điều khiển trung tâm**: Chụp cận cảnh bo mạch ESP32-S3-DevKitC-1 gắn trên breadboard hoặc mạch in PCB, dây nối sang micro INMP441 và module rơ-le cách ly.
3. **Hình ảnh bố trí các cảm biến**: Chụp góc micro hướng ra ngoài để thu âm thanh, cảm biến radar mmWave LD2410S đặt tại phòng khách và cảm biến nhiệt ẩm DHT22.
4. **Hình ảnh cụm cơ cấu bảo mật cửa**: Chụp bàn phím ma trận 4x3 đặt phía ngoài cửa, động cơ Servo kéo chốt trượt và 2 công tắc hành trình giới hạn.
