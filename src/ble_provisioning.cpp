#include "ble_provisioning.h"
#include <Preferences.h>
#include <WiFi.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <nvs_flash.h>
#include "config.h"

static Preferences s_blePref;
static bool s_bleActive = false;
static BleProvStatus s_currentStatus = BLE_PROV_IDLE;
static BLEServer* s_pServer = nullptr;
static BLECharacteristic* s_pStatusChar = nullptr;
static BLECharacteristic* s_pConfigChar = nullptr;

static String s_incomingSsid = "";
static String s_incomingPass = "";
static String s_incomingHome = "";
static String s_incomingOwner = "";
static String s_incomingUid = "";
static bool s_hasNewCredentials = false;
static uint32_t s_connectStartMs = 0;
static uint32_t s_advStartMs = 0;

// Callback khi Client BLE kết nối hoặc ngắt kết nối
class BleProvServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) override {
        Serial.println("[BLE_PROV] Client da ket noi vao ESP32 qua Bluetooth!");
        s_currentStatus = BLE_PROV_CLIENT_CONNECTED;
        if (s_pStatusChar) {
            uint8_t st = (uint8_t)s_currentStatus;
            s_pStatusChar->setValue(&st, 1);
            s_pStatusChar->notify();
        }
    }

    void onDisconnect(BLEServer* pServer) override {
        Serial.println("[BLE_PROV] Client ngat ket noi Bluetooth.");
        if (s_bleActive) {
            s_currentStatus = BLE_PROV_ADVERTISING;
            s_advStartMs = millis(); // Reset lại timer chờ nếu client ngắt kết nối giữa chừng
            BLEDevice::startAdvertising();
        }
    }
};

// Callback khi App gửi thông tin Wi-Fi qua Characteristic
class BleProvConfigCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* pCharacteristic) override {
        std::string rxVal = pCharacteristic->getValue();
        if (rxVal.length() == 0) return;

        String payload = String(rxVal.c_str());
        payload.trim();
        Serial.printf("[BLE_PROV] Nhan cau hinh Wi-Fi & Quyen: %s\n", payload.c_str());

        String ssid = "";
        String pass = "";
        String home = "HOME-2026-ESP32";
        String owner = "";
        String uid = "";

        if (payload.startsWith("{") && payload.endsWith("}")) {
            int ssidIdx = payload.indexOf("\"ssid\"");
            if (ssidIdx >= 0) {
                int start = payload.indexOf("\"", ssidIdx + 6) + 1;
                int end = payload.indexOf("\"", start);
                if (start > 0 && end > start) ssid = payload.substring(start, end);
            }
            int passIdx = payload.indexOf("\"pass\"");
            if (passIdx >= 0) {
                int start = payload.indexOf("\"", passIdx + 6) + 1;
                int end = payload.indexOf("\"", start);
                if (start > 0 && end > start) pass = payload.substring(start, end);
            }
            int homeIdx = payload.indexOf("\"home\"");
            if (homeIdx >= 0) {
                int start = payload.indexOf("\"", homeIdx + 6) + 1;
                int end = payload.indexOf("\"", start);
                if (start > 0 && end > start) home = payload.substring(start, end);
            }
            int ownerIdx = payload.indexOf("\"owner\"");
            if (ownerIdx >= 0) {
                int start = payload.indexOf("\"", ownerIdx + 7) + 1;
                int end = payload.indexOf("\"", start);
                if (start > 0 && end > start) owner = payload.substring(start, end);
            }
            int uidIdx = payload.indexOf("\"uid\"");
            if (uidIdx >= 0) {
                int start = payload.indexOf("\"", uidIdx + 5) + 1;
                int end = payload.indexOf("\"", start);
                if (start > 0 && end > start) uid = payload.substring(start, end);
            }
        } else {
            // Pipe-delimited: SSID|PASSWORD|HOME_ID|OWNER|UID
            int p1 = payload.indexOf('|');
            int p2 = (p1 >= 0) ? payload.indexOf('|', p1 + 1) : -1;
            int p3 = (p2 >= 0) ? payload.indexOf('|', p2 + 1) : -1;
            int p4 = (p3 >= 0) ? payload.indexOf('|', p3 + 1) : -1;

            if (p1 >= 0) {
                ssid = payload.substring(0, p1);
                if (p2 >= 0) {
                    pass = payload.substring(p1 + 1, p2);
                    if (p3 >= 0) {
                        home = payload.substring(p2 + 1, p3);
                        if (p4 >= 0) {
                            owner = payload.substring(p3 + 1, p4);
                            uid = payload.substring(p4 + 1);
                        } else {
                            owner = payload.substring(p3 + 1);
                        }
                    } else {
                        home = payload.substring(p2 + 1);
                    }
                } else {
                    pass = payload.substring(p1 + 1);
                }
            } else {
                ssid = payload;
            }
        }

        if (ssid.length() > 0) {
            s_incomingSsid = ssid;
            s_incomingPass = pass;
            s_incomingHome = home;
            s_incomingOwner = owner;
            s_incomingUid = uid;
            s_hasNewCredentials = true;
            s_currentStatus = BLE_PROV_CONFIG_RECEIVED;

            if (s_pStatusChar) {
                uint8_t st = (uint8_t)s_currentStatus;
                s_pStatusChar->setValue(&st, 1);
                s_pStatusChar->notify();
            }
        }
    }
};

void ble_provisioning_init() {
    s_blePref.begin("wificfg", false);
}

bool ble_provisioning_is_active() {
    return s_bleActive;
}

void ble_provisioning_start() {
    if (s_bleActive) return;

    Serial.println("\n=======================================================");
    Serial.println("  [BLE_PROV] KICH HOAT CHE DO CAI DAT WI-FI QUA BLUETOOTH");
    Serial.println("  Tên thiết bị BLE: SmartHome-ESP32-Setup");
    Serial.println("  Thời gian chờ: 3 phút (180 giây)");
    Serial.println("=======================================================\n");

    s_bleActive = true;
    s_currentStatus = BLE_PROV_ADVERTISING;
    s_hasNewCredentials = false;
    s_advStartMs = millis();

    // Ngắt Wi-Fi tạm thời để tập trung radio cho BLE
    WiFi.disconnect(true);

    BLEDevice::init("SmartHome-ESP32-Setup");
    s_pServer = BLEDevice::createServer();
    s_pServer->setCallbacks(new BleProvServerCallbacks());

    BLEService* pService = s_pServer->createService(BLE_PROV_SERVICE_UUID);

    // Characteristic ghi cấu hình (Write)
    s_pConfigChar = pService->createCharacteristic(
        BLE_PROV_CHAR_CONFIG_UUID,
        BLECharacteristic::PROPERTY_WRITE
    );
    s_pConfigChar->setCallbacks(new BleProvConfigCallbacks());

    // Characteristic thông báo trạng thái (Read & Notify)
    s_pStatusChar = pService->createCharacteristic(
        BLE_PROV_CHAR_STATUS_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
    );
    s_pStatusChar->addDescriptor(new BLE2902());
    uint8_t initialStatus = (uint8_t)BLE_PROV_ADVERTISING;
    s_pStatusChar->setValue(&initialStatus, 1);

    pService->start();

    BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(BLE_PROV_SERVICE_UUID);
    pAdvertising->setScanResponse(true);
    pAdvertising->setMinPreferred(0x06); // functions that help with iPhone connections
    pAdvertising->setMinPreferred(0x12);
    BLEDevice::startAdvertising();

    Serial.println("[BLE_PROV] Dang phat song BLE beacon... Hay mo App de ket noi.");
}

void ble_provisioning_stop() {
    if (!s_bleActive) return;

    Serial.println("[BLE_PROV] Dung che do BLE Provisioning va giai phong RAM...");
    s_bleActive = false;
    s_currentStatus = BLE_PROV_IDLE;

    if (s_pServer) {
        BLEDevice::getAdvertising()->stop();
        BLEDevice::deinit(true);
        s_pServer = nullptr;
        s_pStatusChar = nullptr;
        s_pConfigChar = nullptr;
    }

    // Tắt hoàn toàn radio Bluetooth
    btStop();
    Serial.println("[BLE_PROV] Da tat Bluetooth, giai phong 100% RAM cho TinyML & Wi-Fi!");
}

void ble_provisioning_update() {
    if (!s_bleActive) return;

    // Tự động tắt phát sóng BLE sau 3 phút nếu không có thiết bị kết nối
    if (s_currentStatus == BLE_PROV_ADVERTISING && (millis() - s_advStartMs > BLE_PROV_TIMEOUT_MS)) {
        Serial.println("[BLE_PROV] Timeout 3 phut (180s) khong co thiet bi nao ket noi. Tu dong tat BLE!");
        ble_provisioning_stop();
        return;
    }

    if (s_hasNewCredentials) {
        s_hasNewCredentials = false;
        Serial.printf("[BLE_PROV] Thu nghiem ket noi WiFi moi: '%s' ...\n", s_incomingSsid.c_str());

        s_currentStatus = BLE_PROV_CONNECTING_WIFI;
        if (s_pStatusChar) {
            uint8_t st = (uint8_t)s_currentStatus;
            s_pStatusChar->setValue(&st, 1);
            s_pStatusChar->notify();
        }

        WiFi.disconnect(true);
        delay(100);
        WiFi.begin(s_incomingSsid.c_str(), s_incomingPass.c_str());
        s_connectStartMs = millis();

        // Chờ kết nối tối đa 12 giây
        bool connected = false;
        while (millis() - s_connectStartMs < 12000) {
            if (WiFi.status() == WL_CONNECTED) {
                connected = true;
                break;
            }
            delay(200);
        }

        if (connected) {
            Serial.printf("[BLE_PROV] Ket noi thanh cong! IP: %s\n", WiFi.localIP().toString().c_str());
            ble_save_wifi(s_incomingSsid, s_incomingPass, s_incomingHome, s_incomingOwner, s_incomingUid);

            s_currentStatus = BLE_PROV_SUCCESS;
            if (s_pStatusChar) {
                uint8_t st = (uint8_t)s_currentStatus;
                s_pStatusChar->setValue(&st, 1);
                s_pStatusChar->notify();
            }

            // Chờ 1.5 giây để Client nhận được gói tin notify trước khi tắt BLE
            delay(1500);
            ble_provisioning_stop();

            // Khởi động lại vi điều khiển để nạp cấu hình mới mượt mà
            Serial.println("[BLE_PROV] Khoi dong lai ESP32 de vao che do hoat dong chinh...");
            delay(500);
            ESP.restart();
        } else {
            Serial.println("[BLE_PROV] Ket noi that bai! Sai SSID hoac Mat khau.");
            s_currentStatus = BLE_PROV_FAILED;
            if (s_pStatusChar) {
                uint8_t st = (uint8_t)s_currentStatus;
                s_pStatusChar->setValue(&st, 1);
                s_pStatusChar->notify();
            }
        }
    }
}

bool ble_has_saved_wifi() {
    return s_blePref.isKey("ssid") && (s_blePref.getString("ssid", "").length() > 0);
}

bool ble_get_saved_wifi(String& ssid, String& password, String& homeId, String* ownerEmail, String* ownerUid) {
    if (!ble_has_saved_wifi()) return false;
    ssid = s_blePref.getString("ssid", "");
    password = s_blePref.getString("pass", "");
    homeId = s_blePref.getString("home", "HOME-2026-ESP32");
    if (ownerEmail != nullptr) {
        *ownerEmail = s_blePref.getString("owner", "");
    }
    if (ownerUid != nullptr) {
        *ownerUid = s_blePref.getString("uid", "");
    }
    return (ssid.length() > 0);
}

void ble_save_wifi(const String& ssid, const String& password, const String& homeId, const String& ownerEmail, const String& ownerUid) {
    s_blePref.putString("ssid", ssid);
    s_blePref.putString("pass", password);
    s_blePref.putString("home", homeId);
    if (ownerEmail.length() > 0) {
        s_blePref.putString("owner", ownerEmail);
    }
    if (ownerUid.length() > 0) {
        s_blePref.putString("uid", ownerUid);
    }
    Serial.printf("[BLE_PROV] Da luu cau hinh WiFi vao NVS: SSID='%s', Home='%s', Owner='%s', UID='%s'\n",
                  ssid.c_str(), homeId.c_str(), ownerEmail.c_str(), ownerUid.c_str());
}

void ble_clear_saved_wifi() {
    s_blePref.remove("ssid");
    s_blePref.remove("pass");
    Serial.println("[BLE_PROV] Da xoa cau hinh WiFi (Network Reset). Giu nguyen Quyen & Du lieu ngoi nha.");
}

void ble_factory_reset() {
    s_blePref.clear();
    nvs_flash_erase();
    nvs_flash_init();
    Serial.println("[FACTORY_RESET] Da xoa sach toan bo NVS partition (Tro ve trang thai xuat xuong 100%)!");
}
