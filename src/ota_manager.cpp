#include "ota_manager.h"
#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <Update.h>
#include "config.h"

static OtaState s_otaState = OTA_STATE_IDLE;
static int s_otaProgress = 0;
static String s_otaUrl = "";
static String s_otaVersion = "";
static char s_otaErrorMsg[128] = "";
static bool s_otaRequested = false;
static OtaProgressCallback s_progressCb = nullptr;

void ota_set_progress_callback(OtaProgressCallback cb) {
    s_progressCb = cb;
}

static void notify_progress(int percent, const char* status, const char* error = nullptr) {
    if (s_progressCb) {
        s_progressCb(percent, status, error);
    }
}

void ota_init() {
    s_otaState = OTA_STATE_IDLE;
    s_otaProgress = 0;
    s_otaErrorMsg[0] = '\0';
}

bool ota_is_running() {
    return (s_otaState == OTA_STATE_STARTING ||
            s_otaState == OTA_STATE_DOWNLOADING ||
            s_otaState == OTA_STATE_FLASHING);
}

OtaState ota_get_state() {
    return s_otaState;
}

int ota_get_progress() {
    return s_otaProgress;
}

const char* ota_get_error() {
    return s_otaErrorMsg;
}

void ota_start(const String& binUrl, const String& targetVersion) {
    if (ota_is_running()) return;
    s_otaUrl = binUrl;
    s_otaVersion = targetVersion;
    s_otaRequested = true;
    s_otaState = OTA_STATE_STARTING;
    s_otaProgress = 0;
    s_otaErrorMsg[0] = '\0';
    LOG("[OTA] Yeu cau cap nhat Firmware tu xa: %s (v%s)\n", binUrl.c_str(), targetVersion.c_str());
    notify_progress(0, "starting", nullptr);
}

static void execute_ota_download(const String& url) {
    if (WiFi.status() != WL_CONNECTED) {
        snprintf(s_otaErrorMsg, sizeof(s_otaErrorMsg), "Wi-Fi chua ket noi");
        s_otaState = OTA_STATE_FAILED;
        notify_progress(0, "failed", s_otaErrorMsg);
        LOG("[OTA] That bai: %s\n", s_otaErrorMsg);
        return;
    }

    LOG("\n=======================================================\n");
    LOG("  [OTA] BAT DAU QUA TRINH CAP NHAT FIRMWARE QUA WI-FI\n");
    LOG("  URL: %s\n", url.c_str());
    LOG("=======================================================\n");

    s_otaState = OTA_STATE_DOWNLOADING;
    s_otaProgress = 0;
    notify_progress(0, "downloading", nullptr);

    WiFiClientSecure* secureClient = new WiFiClientSecure();
    if (!secureClient) {
        snprintf(s_otaErrorMsg, sizeof(s_otaErrorMsg), "Khong du RAM khoi tao SSL Client");
        s_otaState = OTA_STATE_FAILED;
        notify_progress(0, "failed", s_otaErrorMsg);
        return;
    }
    secureClient->setInsecure(); // Bo qua kiem tra chung chi SSL de tai tu GitHub/Server

    HTTPClient https;
    https.setFollowRedirects(HTTPC_FORCE_FOLLOW_REDIRECTS);
    https.setTimeout(30000);

    if (!https.begin(*secureClient, url)) {
        snprintf(s_otaErrorMsg, sizeof(s_otaErrorMsg), "Khong the ket noi den URL Firmware");
        s_otaState = OTA_STATE_FAILED;
        notify_progress(0, "failed", s_otaErrorMsg);
        delete secureClient;
        return;
    }

#ifdef GITHUB_OTA_TOKEN
    if (strlen(GITHUB_OTA_TOKEN) > 0) {
        https.addHeader("Authorization", String("token ") + GITHUB_OTA_TOKEN);
        https.addHeader("User-Agent", "ESP32-OTA-Client");
    }
#endif

    int httpCode = https.GET();
    if (httpCode != HTTP_CODE_OK) {
        snprintf(s_otaErrorMsg, sizeof(s_otaErrorMsg), "HTTP loi ma: %d", httpCode);
        s_otaState = OTA_STATE_FAILED;
        notify_progress(0, "failed", s_otaErrorMsg);
        https.end();
        delete secureClient;
        LOG("[OTA] That bai: %s\n", s_otaErrorMsg);
        return;
    }

    int contentLength = https.getSize();
    LOG("[OTA] Do dai file Firmware: %d bytes\n", contentLength);

    if (contentLength <= 0) {
        snprintf(s_otaErrorMsg, sizeof(s_otaErrorMsg), "Kich thuoc file Firmware khong hop le");
        s_otaState = OTA_STATE_FAILED;
        notify_progress(0, "failed", s_otaErrorMsg);
        https.end();
        delete secureClient;
        return;
    }

    bool canBegin = Update.begin(contentLength, U_FLASH);
    if (!canBegin) {
        snprintf(s_otaErrorMsg, sizeof(s_otaErrorMsg), "Khong du dung luong phan vung OTA (Loi: %d)", Update.getError());
        s_otaState = OTA_STATE_FAILED;
        notify_progress(0, "failed", s_otaErrorMsg);
        https.end();
        delete secureClient;
        LOG("[OTA] That bai: %s\n", s_otaErrorMsg);
        return;
    }

    Update.onProgress([](size_t current, size_t total) {
        if (total > 0) {
            int percent = (int)((current * 100) / total);
            if (percent != s_otaProgress) {
                s_otaProgress = percent;
                notify_progress(s_otaProgress, "flashing", nullptr);
                if (s_otaProgress % 10 == 0) {
                    LOG("[OTA] Tien trinh nap Flash: %d%% (%u / %u bytes)\n", s_otaProgress, current, total);
                }
            }
        }
    });

    s_otaState = OTA_STATE_FLASHING;
    WiFiClient* stream = https.getStreamPtr();
    size_t written = Update.writeStream(*stream);

    if (written == (size_t)contentLength) {
        LOG("[OTA] Da ghi thanh cong %u bytes vao phan vung OTA moi.\n", written);
    } else {
        snprintf(s_otaErrorMsg, sizeof(s_otaErrorMsg), "Chi ghi duoc %u/%d bytes", written, contentLength);
        s_otaState = OTA_STATE_FAILED;
        notify_progress(0, "failed", s_otaErrorMsg);
        LOG("[OTA] That bai: %s\n", s_otaErrorMsg);
        Update.abort();
        https.end();
        delete secureClient;
        return;
    }

    if (Update.end()) {
        if (Update.isFinished()) {
            s_otaState = OTA_STATE_SUCCESS;
            s_otaProgress = 100;
            notify_progress(100, "success", nullptr);
            LOG("\n=======================================================\n");
            LOG("  [OTA] CAP NHAT FIRMWARE THANH CONG 100%!\n");
            LOG("  He thong se tu dong khoi dong lai vao Firmware moi...\n");
            LOG("=======================================================\n");
            delay(1500);
            ESP.restart();
        } else {
            snprintf(s_otaErrorMsg, sizeof(s_otaErrorMsg), "Update.end() khong hoan tat duoc");
            s_otaState = OTA_STATE_FAILED;
            notify_progress(0, "failed", s_otaErrorMsg);
        }
    } else {
        snprintf(s_otaErrorMsg, sizeof(s_otaErrorMsg), "Loi ket thuc ghi Flash: %d", Update.getError());
        s_otaState = OTA_STATE_FAILED;
        notify_progress(0, "failed", s_otaErrorMsg);
    }

    https.end();
    delete secureClient;
}

void ota_update() {
    if (s_otaRequested) {
        s_otaRequested = false;
        execute_ota_download(s_otaUrl);
    }
}
