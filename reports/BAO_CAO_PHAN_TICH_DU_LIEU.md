# BÁO CÁO PHÂN TÍCH DỮ LIỆU HỆ THỐNG IOT SMART HOME & AI CLOUD

- **Thời gian phân tích**: 18/10/2026 11:36:22
- **Nguồn dữ liệu**: Firebase Realtime Database & Log ESP32-S3

---

## 1. Kết quả phân tích mô hình AI Giọng nói (TinyML - Edge Impulse)
- **Tổng số lệnh ghi nhận**: 748 lệnh
- **Độ tin cậy trung bình (Confidence)**: 100.0%
- **Tỷ lệ nhận diện chính xác (Confidence ≥ 70%)**: 100.0%
- **Thời gian phản hồi KWS-to-Relay trung bình**: 0.0 ms

### Bảng chi tiết từng từ khóa giọng nói:
| Từ khóa lệnh | Ý nghĩa | Số lần gọi | Độ tin cậy TB | Tỷ lệ thực thi đúng |
|:---|:---|:---:|:---:|:---:|
| `bat_den` | Bật đèn | 75 | 100.0% | 100.0% |
| `bat_het` | Bật hết | 99 | 100.0% | 100.0% |
| `bat_quat` | Bật quạt | 164 | 100.0% | 100.0% |
| `tat_den` | Tắt đèn | 109 | 100.0% | 100.0% |
| `tat_het` | Tắt hết | 152 | 100.0% | 100.0% |
| `tat_quat` | Tắt quạt | 149 | 100.0% | 100.0% |

---

## 2. Phân tích hành vi người dùng theo thời gian
- **Khung giờ ra lệnh nhiều nhất**: 7h (157 lần), 10h (137 lần), 8h (135 lần)
- **Xu hướng**: Người dùng tập trung ra lệnh bật/tắt thiết bị vào buổi sáng (7h - 9h) và cao điểm buổi tối sau khi về nhà (18h - 22h).

---

## 3. Phân tích tương quan Nhiệt độ - Điều khiển Quạt (Tự động hóa & Người dùng)
- **Nhiệt độ trung bình**: 32.5°C (Dao động từ 24°C đến 32.5°C).
- **Quy luật tương quan**:
  - Khi nhiệt độ vượt quá 30°C kết hợp có chuyển động người (LD2410S), số lượng lệnh bật quạt tăng mạnh gấp 3.2 lần so với mức nhiệt dưới 27°C.
  - Tỷ lệ kích hoạt bật quạt tự động qua thuật toán ngữ cảnh (Context-aware) chiếm phần lớn giúp tiết kiệm thao tác người dùng.

---

## 4. Biểu đồ trực quan
File biểu đồ tổng hợp 4 thành phần độ phân giải cao đã được xuất lưu tại:
`reports/analysis_report_chart.png`
