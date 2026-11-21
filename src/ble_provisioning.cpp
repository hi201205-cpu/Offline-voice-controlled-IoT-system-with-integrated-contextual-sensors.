#include "ble_provisioning.h"
#include <Preferences.h>
#include <WiFi.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <cJSON.h>
#include <nvs_flash.h>
#include <atomic>
#include <freertos/event_groups.h>

namespace {
constexpr char kName[] = "SmartHome-ESP32-Setup";
constexpr size_t kMaxPayload = 512;
constexpr uint32_t kWifiTimeoutMs = 25000;
constexpr EventBits_t kAdvData = 1, kScanResponse = 2;

Preferences s_blePref;
BLEServer* s_server = nullptr;
BLECharacteristic* s_status = nullptr;
BLE2902* s_cccd = nullptr;
StaticEventGroup_t s_gapStorage;
EventGroupHandle_t s_gapEvents = nullptr;

// The Wi-Fi task owns setup, advertising and the Wi-Fi transaction. Bluedroid
// callbacks assemble one message; Ready transfers ownership to the Wi-Fi task.
enum class Job { Empty, Ready, Processing };
std::atomic<Job> s_job{Job::Empty};
std::string s_message;
std::string s_partial; // Bluedroid task only
std::atomic<bool> s_active{false}, s_connected{false}, s_advertise{false};
std::atomic<uint32_t> s_idleSince{0};
bool s_started = false, s_dataConfigured = false;
uint32_t s_retryAfter = 0;
bool s_hadSavedWifi = false;

void setStatus(BleProvStatus status) {
    uint8_t value = static_cast<uint8_t>(status);
    s_status->setValue(&value, 1);
    if (s_connected.load()) s_status->notify();
}

class ServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* server, esp_ble_gatts_cb_param_t* param) override {
        s_partial.clear();
        s_cccd->setNotifications(false);
        s_connected = true;
        Serial.printf("[BLE_PROV] GATT connected t=%lu id=%u\n", millis(), param->connect.conn_id);
        if (!s_active) server->disconnect(param->connect.conn_id);
    }
    void onDisconnect(BLEServer*, esp_ble_gatts_cb_param_t* param) override {
        s_partial.clear();
        s_connected = false;
        s_idleSince = millis();
        s_advertise = true;
        Serial.printf("[BLE_PROV] GATT disconnected t=%lu id=%u reason=0x%02x\n",
                      millis(), param->disconnect.conn_id, param->disconnect.reason);
    }
    void onMtuChanged(BLEServer*, esp_ble_gatts_cb_param_t* param) override {
        Serial.printf("[BLE_PROV] MTU id=%u mtu=%u\n", param->mtu.conn_id, param->mtu.mtu);
    }
};

// A chunk can end at an escaped quote, a literal '}' or inside UTF-8.
// Look for the closing top-level brace outside strings. cJSON validates the
// completed frame. The legacy pipe protocol is exactly one write per message.
bool hasJsonEnd(const std::string& bytes) {
    bool quoted = false, escaped = false;
    int depth = 0;
    for (char c : bytes) {
        if (quoted) {
            if (escaped) escaped = false;
            else if (c == '\\') escaped = true;
            else if (c == '"') quoted = false;
        } else if (c == '"') {
            quoted = true;
        } else if (c == '{') {
            ++depth;
        } else if (c == '}' && --depth == 0) {
            return true;
        }
    }
    return false;
}

class ConfigCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* characteristic) override {
        if (!s_active || !s_connected || s_job.load() != Job::Empty) return;
        const std::string chunk = characteristic->getValue();
        if (chunk.empty()) return;
        if (chunk.size() > kMaxPayload - s_partial.size() || chunk.find('\0') != std::string::npos) {
            s_partial.clear(); // Hand an invalid frame to the worker for a FAILED response.
        } else {
            s_partial += chunk;
            if (s_partial.front() == '{' && !hasJsonEnd(s_partial)) {
                if (s_partial.size() < kMaxPayload) return;
                s_partial.clear();
            }
        }
        s_message.swap(s_partial);
        s_partial.clear();
        s_job.store(Job::Ready);
    }
};

struct Credentials {
    String ssid, pass, home = "HOME-2026-ESP32", owner, uid;
};

bool parseCredentials(const std::string& payload, Credentials& out) {
    if (payload.empty()) return false;
    if (payload.front() == '{') {
        cJSON* json = cJSON_ParseWithOpts(payload.c_str(), nullptr, true);
        if (!json) return false;
        auto field = [json](const char* key, String& dest) {
            auto* item = cJSON_GetObjectItemCaseSensitive(json, key);
            if (!item) return true;
            if (!cJSON_IsString(item)) return false;
            dest = item->valuestring;
            return true;
        };
        bool valid = field("ssid", out.ssid) && field("pass", out.pass) &&
                     field("home", out.home) && field("owner", out.owner) && field("uid", out.uid);
        cJSON_Delete(json);
        if (!valid) return false;
    } else {
        String* fields[] = {&out.ssid, &out.pass, &out.home, &out.owner, &out.uid};
        size_t start = 0;
        for (size_t i = 0; i < 5; ++i) {
            const size_t end = i == 4 ? std::string::npos : payload.find('|', start);
            *fields[i] = payload.substr(start, end - start).c_str();
            if (end == std::string::npos) break;
            start = end + 1;
        }
    }
    return out.ssid.length() > 0 && out.ssid.length() <= 32 && out.pass.length() <= 64;
}

void onGap(esp_gap_ble_cb_event_t event, esp_ble_gap_cb_param_t* param) {
    if (event == ESP_GAP_BLE_ADV_DATA_RAW_SET_COMPLETE_EVT &&
        param->adv_data_raw_cmpl.status == ESP_BT_STATUS_SUCCESS) {
        xEventGroupSetBits(s_gapEvents, kAdvData);
    } else if (event == ESP_GAP_BLE_SCAN_RSP_DATA_RAW_SET_COMPLETE_EVT &&
               param->scan_rsp_data_raw_cmpl.status == ESP_BT_STATUS_SUCCESS) {
        xEventGroupSetBits(s_gapEvents, kScanResponse);
    } else if (event == ESP_GAP_BLE_ADV_START_COMPLETE_EVT) {
        Serial.printf("[BLE_PROV] ADV_IND t=%lu status=%u\n", millis(), param->adv_start_cmpl.status);
        if (param->adv_start_cmpl.status != ESP_BT_STATUS_SUCCESS) s_advertise = true;
    }
}

void createServer() {
    s_gapEvents = xEventGroupCreateStatic(&s_gapStorage);
    BLEDevice::init(kName);
    BLEDevice::setCustomGapHandler(onGap);
    BLEDevice::setMTU(512);
    s_server = BLEDevice::createServer();
    static ServerCallbacks serverCallbacks;
    s_server->setCallbacks(&serverCallbacks);
    auto* service = s_server->createService(BLE_PROV_SERVICE_UUID);
    auto* config = service->createCharacteristic(BLE_PROV_CHAR_CONFIG_UUID,
        BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR);
    static ConfigCallbacks configCallbacks;
    config->setCallbacks(&configCallbacks);
    s_status = service->createCharacteristic(BLE_PROV_CHAR_STATUS_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
    s_cccd = new BLE2902();
    s_status->addDescriptor(s_cccd);
    service->start();
}

void advertise() {
    auto* adv = BLEDevice::getAdvertising();
    if (!s_dataConfigured) {
        BLEAdvertisementData primary, scanResponse;
        primary.setFlags(ESP_BLE_ADV_FLAG_GEN_DISC | ESP_BLE_ADV_FLAG_BREDR_NOT_SPT);
        primary.setCompleteServices(BLEUUID(BLE_PROV_SERVICE_UUID));
        scanResponse.setName(kName);
        xEventGroupClearBits(s_gapEvents, kAdvData | kScanResponse);
        adv->setAdvertisementData(primary);
        adv->setScanResponseData(scanResponse);
        const auto bits = xEventGroupWaitBits(s_gapEvents, kAdvData | kScanResponse,
                                              pdFALSE, pdTRUE, pdMS_TO_TICKS(2000));
        if ((bits & (kAdvData | kScanResponse)) != (kAdvData | kScanResponse)) {
            Serial.println("[BLE_PROV] Advertising data setup timed out");
            s_advertise = true;
            return;
        }
        s_dataConfigured = true;
    }
    uint8_t value = BLE_PROV_ADVERTISING;
    s_status->setValue(&value, 1);
    esp_ble_adv_params_t params{};
    params.adv_int_min = 0x20; // 20–40 ms, in 0.625 ms units
    params.adv_int_max = 0x40;
    params.adv_type = ADV_TYPE_IND;
    params.own_addr_type = BLE_ADDR_TYPE_PUBLIC;
    params.channel_map = ADV_CHNL_ALL;
    params.adv_filter_policy = ADV_FILTER_ALLOW_SCAN_ANY_CON_ANY;
    const esp_err_t err = esp_ble_gap_start_advertising(&params);
    if (err != ESP_OK) {
        Serial.printf("[BLE_PROV] Advertising request rejected: %d\n", err);
        s_advertise = true;
    }
}

void processMessage() {
    s_job = Job::Processing;
    Credentials wifi;
    bool valid = parseCredentials(s_message, wifi);
    s_message.clear();
    if (valid) {
        setStatus(BLE_PROV_CONFIG_RECEIVED);
        setStatus(BLE_PROV_CONNECTING_WIFI);
        WiFi.begin(wifi.ssid.c_str(), wifi.pass.c_str());
        const uint32_t started = millis();
        while (WiFi.status() != WL_CONNECTED && millis() - started < kWifiTimeoutMs) delay(100);
        if (WiFi.status() == WL_CONNECTED) {
            ble_save_wifi(wifi.ssid, wifi.pass, wifi.home, wifi.owner, wifi.uid);
            String ssid, pass, home, owner, uid;
            if (ble_get_saved_wifi(ssid, pass, home, &owner, &uid) &&
                ssid == wifi.ssid && pass == wifi.pass && home == wifi.home &&
                (!wifi.owner.length() || owner == wifi.owner) && (!wifi.uid.length() || uid == wifi.uid)) {
                setStatus(BLE_PROV_SUCCESS);
                const uint32_t notified = millis();
                while (s_connected && millis() - notified < 1500) delay(20);
                ESP.restart();
                return;
            }
            Serial.println("[BLE_PROV] Failed to verify saved Wi-Fi settings");
        }
        WiFi.disconnect(true);
    }
    setStatus(BLE_PROV_FAILED);
    s_job = Job::Empty;
}
} // namespace

void ble_provisioning_init() {
    s_blePref.begin("wificfg", false);
}

bool ble_provisioning_is_active() {
    return s_active;
}

void ble_provisioning_start() {
    // Called from both the button task and the Wi-Fi task.
    s_active = true;
}

void ble_provisioning_stop() {
    s_active = false;
    if (s_server) {
        BLEDevice::getAdvertising()->stop();
        if (s_connected) s_server->disconnect(s_server->getConnId());
    }
    // Keep the same server for another setup window. On this Arduino core
    // deinit(true) releases controller memory until the next reboot.
    s_started = false;
}

void ble_provisioning_update() {
    if (!s_active) return;
    if (!s_started) {
        WiFi.disconnect(true);
        if (!s_server) createServer();
        s_hadSavedWifi = ble_has_saved_wifi();
        s_idleSince = millis();
        s_advertise = true;
        s_retryAfter = millis();
        s_started = true;
    }
    if (s_job == Job::Ready) processMessage();
    if (s_connected || s_job != Job::Empty) return;
    if (s_hadSavedWifi && millis() - s_idleSince.load() > BLE_PROV_TIMEOUT_MS && ble_has_saved_wifi()) {
        ble_provisioning_stop();
        return;
    }
    if (static_cast<int32_t>(millis() - s_retryAfter) >= 0 && s_advertise.exchange(false)) {
        s_retryAfter = millis() + 500;
        advertise();
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
