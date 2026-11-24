#pragma once
#include <Arduino.h>

#define FIRMWARE_CURRENT_VERSION "1.0.4"

enum OtaState {
    OTA_STATE_IDLE = 0,
    OTA_STATE_STARTING,
    OTA_STATE_DOWNLOADING,
    OTA_STATE_FLASHING,
    OTA_STATE_SUCCESS,
    OTA_STATE_FAILED
};

typedef void (*OtaProgressCallback)(int percent, const char* status, const char* error);

void ota_init();
bool ota_is_running();
void ota_start(const String& binUrl, const String& targetVersion = "");
void ota_update();
OtaState ota_get_state();
int ota_get_progress();
const char* ota_get_error();
void ota_set_progress_callback(OtaProgressCallback cb);
