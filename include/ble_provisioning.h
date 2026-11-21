#pragma once

#include <Arduino.h>

// Service & Characteristic UUIDs (Chuẩn BLE GATT cho SmartHome ESP32 Provisioning)
#define BLE_PROV_SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define BLE_PROV_CHAR_CONFIG_UUID    "beb5483e-36e1-4688-b7f5-ea07361b26a8" // Write (SSID|PASS|HOME_ID|OWNER|UID)
#define BLE_PROV_CHAR_STATUS_UUID    "1c95d5e3-d8f7-413a-bf3d-7a2e5d7be87e" // Read & Notify

// Thời gian chờ ghép đôi Bluetooth tối đa: 10 phút (600 giây) nếu đã có Wi-Fi
#define BLE_PROV_TIMEOUT_MS          600000UL

// Trạng thái cấu hình BLE
enum BleProvStatus {
    BLE_PROV_IDLE = 0,
    BLE_PROV_ADVERTISING = 1,
    BLE_PROV_CLIENT_CONNECTED = 2,
    BLE_PROV_CONFIG_RECEIVED = 3,
    BLE_PROV_CONNECTING_WIFI = 4,
    BLE_PROV_SUCCESS = 5,
    BLE_PROV_FAILED = 6
};

// Khởi tạo hệ thống quản lý cấu hình (đọc NVS)
void ble_provisioning_init();

// Kiểm tra xem BLE setup mode có đang chạy không
bool ble_provisioning_is_active();

// Bật chế độ BLE Setup (Phát sóng Bluetooth SmartHome-ESP32-Setup trong 3 phút)
void ble_provisioning_start();

// Tắt hoàn toàn BLE để giải phóng RAM cho TinyML & Wi-Fi
void ble_provisioning_stop();

// Vòng lặp kiểm tra và xử lý trạng thái kết nối Wi-Fi trong khi ở chế độ Provisioning
void ble_provisioning_update();

// Kiểm tra đã có cấu hình Wi-Fi trong NVS chưa
bool ble_has_saved_wifi();

// Lấy thông tin Wi-Fi đã lưu từ NVS Preferences (trả về false nếu chưa từng lưu)
bool ble_get_saved_wifi(String& ssid, String& password, String& homeId, String* ownerEmail = nullptr, String* ownerUid = nullptr);

// Lưu thông tin Wi-Fi, HomeId, Owner và UID vào NVS Preferences
void ble_save_wifi(const String& ssid, const String& password, const String& homeId, const String& ownerEmail = "", const String& ownerUid = "");

// Xóa cấu hình Wi-Fi trong NVS (Network Reset khi giữ nút 5s)
void ble_clear_saved_wifi();

// Xóa sạch toàn bộ cấu hình xuất xưởng (Factory Reset)
void ble_factory_reset();
