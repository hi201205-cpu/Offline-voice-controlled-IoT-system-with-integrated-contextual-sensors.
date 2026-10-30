#include "firebase_manager.h"
#include "led_relay.h"
#include "automation.h"
#include "door_manager.h"
#include <FirebaseESP32.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <time.h>
#include <sys/time.h>
#include <math.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/queue.h"

#if DEBUG_FIRMWARE_METRICS
#define FB_TIMED(label, expression) ([&]() { \
    uint32_t startedMs = millis(); \
    auto result = (expression); \
    LOG("[FB_TIME] %s duration=%lu ms result=%d\n", label, \
        static_cast<unsigned long>(millis() - startedMs), \
        static_cast<int>(result)); \
    return result; \
}())
#else
#define FB_TIMED(label, expression) (expression)
#endif

// ── Cấu trúc message ────────────────────────────────────────
struct FbUpdateMsg {
    bool          isHeartbeat;
    bool          isReconnect;
    bool          isSensor;
    bool          isSensorSnapshot;
    bool          isDeviceState;
    bool          isAutoLog;
    bool          isDoorEvent;
    bool          isDoorResult;
    bool          isAutoStatus;
    bool          isAutoPending;
    bool          isAutoPendingCleanup;
    int           label;
    bool          relay1;
    bool          relay2;
    float         temperature;
    float         humidity;
    time_t        timestamp;
    time_t        expiresAt;
    bool          clearRemoteCmd;
    CommandSource source;
    bool          garden;
    bool          door;
    bool          curtain;
    bool          sensorValid;
    bool          measureDesiredLatency;
    uint32_t      desiredReceivedMs;
    bool          motion;
    bool          isDark;
    uint8_t       lightLevel;
    time_t        lastMotionTimestamp;
    bool          accepted;
    char          presence[12];
    char          action[24];
    char          reason[128];
    char          eventSource[12];
    char          who[81];
    char          ruleId[RULE_ID_LEN];
    char          ruleName[RULE_NAME_LEN];
    char          statusState[20];
    char          lastResult[128];
    char          timeoutAction[16];
};

struct VoiceLogRecord {
    int       label;
    float     confidence;
    bool      executed;
    time_t    timestamp;
};

struct DeviceCommand {
    uint8_t device;
    int     label;
    bool    boolValue;
    bool    curtainOpen;
    bool    clearRemoteCmd;
    uint32_t receivedMs;
};

struct DoorRemoteCommand {
    int64_t timestampMs;
    uint32_t receivedMs;
    bool hasTimestamp;
    char action[24];
    char source[16];
};


// ── Hằng số ─────────────────────────────────────────────────
#define FB_QUEUE_DEPTH      16
#define FB_VOICE_LOG_QUEUE_DEPTH 8
#define FB_DEVICE_COMMAND_QUEUE_DEPTH 8
#define FB_TASK_STACK_SIZE  16384
#define FB_TASK_PRIORITY    1
#define FB_HTTP_TIMEOUT_MS  4000
#define FB_HEARTBEAT_MS     30000
#define FB_REINIT_RETRY_MS  5000
#define WIFI_RETRY_MIN_MS   5000UL
#define WIFI_RETRY_MAX_MS   60000UL

// ── Biến nội bộ ─────────────────────────────────────────────
static uint32_t      s_lastReconnectMs     = 0;
static uint32_t      s_wifiAttemptCount    = 0;
static uint32_t      s_lastHeartbeatMs     = 0;
static uint32_t      s_lastFirebaseAttemptMs = 0;
static bool          s_wasConnected        = false;
static bool          s_voiceEnabled        = true;
static bool          s_syncedVoiceEnabled  = true;
static volatile bool s_firebaseReady       = false;
static volatile bool s_needFirebaseReinit  = false;
static bool          s_prevRelay1          = false;
static bool          s_prevRelay2          = false;
static bool          s_lightState          = false;
static bool          s_fanState            = false;
static bool          s_curtainOpen         = false;
static bool          s_gardenState         = false;
static bool          s_doorState           = false;
static volatile bool s_deviceStateDirty    = false;
static bool          s_stateRestoreAttempted = false;
static volatile bool s_commandsMayApply = false;
static bool          s_deviceStateSynced = false;
static bool          s_syncedLightState = false;
static bool          s_syncedFanState = false;
static bool          s_syncedGardenState = false;
static bool          s_syncedDoorState = false;
static bool          s_syncedCurtainState = false;
static bool          s_syncedKeypadLocked = false;
static DeviceCommand s_curtainCommandInFlight = {};
static bool          s_curtainCommandPending = false;
static uint32_t      s_lastConfigReadMs    = 0;
static char          s_pendingResetReason[96] = {};
static uint32_t      s_pendingResetUptimeMs = 0;
static bool          s_hasPendingResetReport = false;

void firebase_record_reset_event(const char* reason, uint32_t lastUptimeMs) {
    if (reason) {
        snprintf(s_pendingResetReason, sizeof(s_pendingResetReason), "%s", reason);
    }
    s_pendingResetUptimeMs = lastUptimeMs;
    s_hasPendingResetReport = true;
}

bool firebase_voice_enabled() {
    return s_voiceEnabled;
}

void firebase_set_voice_enabled(bool enabled) {
    if (s_voiceEnabled != enabled) {
        s_voiceEnabled = enabled;
        s_deviceStateDirty = true;
    }
}

static uint32_t      s_lastPendingReadMs   = 0;
static uint32_t      s_lastPasscodeSyncMs  = 0;
static bool          s_passcodeMetadataNeedsSync = true;
static uint32_t      s_lastAutoPendingReadMs = 0;

static const char* LABEL_NAMES[] = {
    "bat_den", "bat_het", "bat_quat", "noise",
    "tat_den", "tat_het", "tat_quat", "unknown"
};

static const char* wifi_status_name(wl_status_t status) {
    switch (status) {
        case WL_IDLE_STATUS:     return "IDLE";
        case WL_NO_SSID_AVAIL:   return "SSID_NOT_FOUND";
        case WL_SCAN_COMPLETED:  return "SCAN_COMPLETED";
        case WL_CONNECTED:      return "CONNECTED";
        case WL_CONNECT_FAILED: return "AUTH_OR_CONNECT_FAILED";
        case WL_CONNECTION_LOST:return "CONNECTION_LOST";
        case WL_DISCONNECTED:   return "DISCONNECTED";
        default:                return "UNKNOWN";
    }
}

static void wifi_event_callback(arduino_event_id_t event,
                                arduino_event_info_t info) {
#if DEBUG_FIRMWARE_METRICS
    if (event == ARDUINO_EVENT_WIFI_STA_DISCONNECTED) {
        LOG("[WIFI_EVENT] disconnected reason=%u (%s)\n",
            static_cast<unsigned>(info.wifi_sta_disconnected.reason),
            WiFi.disconnectReasonName(
                static_cast<wifi_err_reason_t>(
                    info.wifi_sta_disconnected.reason)));
    } else if (event == ARDUINO_EVENT_WIFI_STA_GOT_IP) {
        LOG("[WIFI_EVENT] got_ip=%s\n", WiFi.localIP().toString().c_str());
    }
#else
    (void)event;
    (void)info;
#endif
}

static FirebaseData   s_fbCommandsStream;
static FirebaseData   s_fbWrite;
static FirebaseConfig s_fbConfig;
static FirebaseAuth   s_fbAuth;
static bool           s_fbInitialized = false;

static QueueHandle_t  s_fbQueue      = nullptr;
static QueueHandle_t  s_voiceLogQueue = nullptr;
static QueueHandle_t  s_deviceCommandQueue = nullptr;
static QueueHandle_t  s_doorCommandQueue = nullptr;
static TaskHandle_t   s_fbTaskHandle = nullptr;

// Khai báo trước các hàm nội bộ
static void firebase_task(void* pvParam);
static void commands_stream_callback(StreamData data);
static void stream_timeout_callback(bool t);
static void firebase_do_voice_log(const VoiceLogRecord& record);
static bool firebase_sync_device_state();
static void firebase_apply_device_command(const DeviceCommand& command);
static void firebase_load_automation_config();
static void firebase_poll_automation_pending();
static void firebase_poll_door_pending();
static void firebase_sync_passcode_metadata();
static void firebase_do_extension_event(const FbUpdateMsg& msg);
static bool enqueue_device_command(const DeviceCommand& command);

static void fb_http_reset() {
}

static bool fb_patch(const char* path, const char* body) {
    if (!s_firebaseReady || !wifi_connected() || !Firebase.ready()) return false;
    FirebaseJson json;
    json.setJsonData(body);
    return FB_TIMED("updateNode", Firebase.updateNode(s_fbWrite, path, json));
}

static bool fb_put(const char* path, const char* body) {
    if (!s_firebaseReady || !wifi_connected() || !Firebase.ready()) return false;
    FirebaseJson json;
    json.setJsonData(body);
    return FB_TIMED("setJSON", Firebase.setJSON(s_fbWrite, path, json));
}

static bool firebase_enqueue_update(const FbUpdateMsg& msg) {
    if (s_fbQueue == nullptr) return false;
    if (xQueueSend(s_fbQueue, &msg, 0) == pdTRUE) return true;

    FbUpdateMsg discarded = {};
    if (xQueueReceive(s_fbQueue, &discarded, 0) == pdTRUE) {
        LOG("[FB] Update queue full; dropped oldest item\n");
    }
    return xQueueSend(s_fbQueue, &msg, 0) == pdTRUE;
}

// ── Khởi động / khởi động lại Firebase ───────────────────────
// Hàm này chạy trong firebase_task ở Core 0, nên được phép chờ.
static void firebase_do_reinit() {
    if (!wifi_connected()) return;

    s_firebaseReady = false;
    s_commandsMayApply = false;
    s_deviceStateSynced = false;
    fb_http_reset();

    if (!s_fbInitialized) {
        s_fbConfig.database_url               = FIREBASE_HOST;
        s_fbConfig.signer.tokens.legacy_token = FIREBASE_AUTH;
        s_fbConfig.timeout.serverResponse     = 5000;
        s_fbConfig.timeout.socketConnection   = 5000;

        Firebase.begin(&s_fbConfig, &s_fbAuth);
        Firebase.reconnectWiFi(true);
        s_fbCommandsStream.setResponseSize(4096);
        s_fbWrite.setResponseSize(4096);
        s_fbInitialized = true;
    }

    uint32_t start = millis();
#if DEBUG_FIRMWARE_METRICS
    uint32_t readyWaitStartedMs = start;
#endif
    while (!Firebase.ready()) {
        if (millis() - start > 10000) {
            LOG("[FB] Init/Reinit timeout\n");
            return;
        }
        vTaskDelay(pdMS_TO_TICKS(300));
    }
#if DEBUG_FIRMWARE_METRICS
    LOG("[FB_TIME] Firebase ready wait=%lu ms\n",
        static_cast<unsigned long>(millis() - readyWaitStartedMs));
#endif

    Firebase.endStream(s_fbCommandsStream);
    bool commandsStreamStarted = FB_TIMED(
        "beginStream commands",
        Firebase.beginStream(s_fbCommandsStream, "/commands"));
    if (commandsStreamStarted) {
        Firebase.setStreamCallback(s_fbCommandsStream,
                                   commands_stream_callback,
                                   stream_timeout_callback,
                                   16384);
    } else {
        LOG("[FB] Commands stream loi: %s\n",
            s_fbCommandsStream.errorReason().c_str());
        return;
    }

    s_firebaseReady = true;

    if (!s_stateRestoreAttempted && !s_deviceStateDirty) {
        if (FB_TIMED("getBool devices/state/light",
                     Firebase.getBool(s_fbWrite, "/devices/state/light"))) {
            relay_control(1, s_fbWrite.boolData(), RELAY_SOURCE_SYNC);
        }
        if (FB_TIMED("getBool devices/state/fan",
                     Firebase.getBool(s_fbWrite, "/devices/state/fan"))) {
            relay_control(2, s_fbWrite.boolData(), RELAY_SOURCE_SYNC);
        }
        if (FB_TIMED("getString devices/state/curtain",
                     Firebase.getString(s_fbWrite, "/devices/state/curtain"))) {
            String storedCurtain = s_fbWrite.stringData();
            storedCurtain.toLowerCase();
            storedCurtain.trim();
            if (storedCurtain == "open" || storedCurtain == "close") {
                curtain_servo_restore(storedCurtain == "open");
            }
        }
        if (FB_TIMED("getBool devices/state/garden",
                     Firebase.getBool(s_fbWrite, "/devices/state/garden"))) {
            relay_control(3, s_fbWrite.boolData(), RELAY_SOURCE_SYNC);
        }
        if (FB_TIMED("getBool devices/state/voice",
                     Firebase.getBool(s_fbWrite, "/devices/state/voice"))) {
            s_voiceEnabled = s_fbWrite.boolData();
        }
    }
    s_stateRestoreAttempted = true;
    s_lightState = get_relay_state(1);
    s_fanState = get_relay_state(2);
    s_gardenState = get_relay_state(3);
    s_doorState = door_is_open();
    s_curtainOpen = curtain_is_open();
    firebase_sync_device_state();

    // Đồng bộ trạng thái relay thực lên Firebase sau khi init/reconnect
    bool r1 = get_relay_state(1);
    bool r2 = get_relay_state(2);

    char devicePath[128];
    snprintf(devicePath, sizeof(devicePath), "/devices/%s", FIREBASE_DEVICE_ID);

    char body[160];
    snprintf(body, sizeof(body),
        "{\"relay1\":%s,\"relay2\":%s,"
        "\"last_seen\":{\".sv\":\"timestamp\"},"
        "\"remote_cmd\":\"\"}",
        r1 ? "true" : "false",
        r2 ? "true" : "false");

    fb_patch(devicePath, body);

    s_prevRelay1 = r1;
    s_prevRelay2 = r2;
    s_commandsMayApply = true;
    s_lastConfigReadMs = millis() - AUTO_CONFIG_REFRESH_MS;
    s_passcodeMetadataNeedsSync = true;

    if (s_hasPendingResetReport) {
        char resetBody[256];
        snprintf(resetBody, sizeof(resetBody),
                 "{\"reason\":\"%s\",\"previous_uptime_ms\":%lu,\"ts\":{\".sv\":\"timestamp\"}}",
                 s_pendingResetReason, static_cast<unsigned long>(s_pendingResetUptimeMs));
        if (fb_patch("/debug/last_reset", resetBody)) {
            s_hasPendingResetReport = false;
            LOG("[RESET] Uploaded reset report to Firebase: reason=%s prev_uptime=%lu ms\n",
                s_pendingResetReason, static_cast<unsigned long>(s_pendingResetUptimeMs));
        }
    }
}

// ── firebase_do_update ───────────────────────────────────────
static void firebase_do_update(const FbUpdateMsg& msg) {
    if (!s_firebaseReady || !wifi_connected()) return;

    if (msg.isSensor) {
        if (!Firebase.ready() || msg.timestamp <= 1700000000) return;

        FirebaseJson json;
        if (msg.sensorValid) {
            json.set("temp", msg.temperature);
            json.set("hum", msg.humidity);
        } else {
            json.set("temp");
            json.set("hum");
        }
        json.set("ts", static_cast<uint32_t>(msg.timestamp));

        FirebaseJson history;
        if (msg.sensorValid) {
            history.set("temp", msg.temperature);
            history.set("hum", msg.humidity);
        } else {
            history.set("temp");
            history.set("hum");
        }
        history.set("motion", msg.motion);
        history.set("light_level", msg.lightLevel);

        if (msg.isSensorSnapshot) {
            json.set("motion", msg.motion);
            json.set("presence", msg.presence);
            json.set("last_motion_ts", static_cast<uint32_t>(msg.lastMotionTimestamp));
            json.set("light_level", msg.lightLevel);
            json.set("is_dark", msg.isDark);
            history.set("ts", static_cast<uint32_t>(msg.timestamp));
        }

        static uint32_t s_lastHistoryWriteMs = 0;
        bool shouldWriteHistory = (s_lastHistoryWriteMs == 0 || (millis() - s_lastHistoryWriteMs >= 60000UL));

        FirebaseJson sensorUpdate;
        sensorUpdate.set("sensors/latest", json);
        if (shouldWriteHistory) {
            s_lastHistoryWriteMs = millis();
            String historyJsonPath = String("sensors/history/") +
                                    static_cast<unsigned long>(msg.timestamp);
            FirebaseJson& historyToWrite = msg.isSensorSnapshot ? history : json;
            sensorUpdate.set(historyJsonPath, historyToWrite);
        }
        String sensorUpdateBody;
        if (!sensorUpdate.toString(sensorUpdateBody, false)) {
            LOG("[SENSOR] Could not serialize combined sensor update\n");
            return;
        }
        if (!fb_patch("/", sensorUpdateBody.c_str())) {
            LOG("[SENSOR] Combined latest/history update failed\n");
        }
        return;
    }

    if (msg.isDeviceState) {
        s_lightState = msg.relay1;
        s_fanState = msg.relay2;
        s_gardenState = msg.garden;
        s_doorState = msg.door;
        s_curtainOpen = msg.curtain;
        bool deviceStateUpdated = firebase_sync_device_state();

        char devicePath[128];
        snprintf(devicePath, sizeof(devicePath), "/devices/%s",
                 FIREBASE_DEVICE_ID);
        char stateBody[192];
        int stateBodyLength = snprintf(
            stateBody, sizeof(stateBody),
            "{\"relay1\":%s,\"relay2\":%s,"
            "\"last_seen\":{\".sv\":\"timestamp\"}%s}",
            msg.relay1 ? "true" : "false",
            msg.relay2 ? "true" : "false",
            msg.clearRemoteCmd ? ",\"remote_cmd\":\"\"" : "");
        bool legacyStateUpdated =
            stateBodyLength > 0 &&
            static_cast<size_t>(stateBodyLength) < sizeof(stateBody) &&
            fb_patch(devicePath, stateBody);
        if (!legacyStateUpdated) {
            LOG("[FB] Could not update legacy actual relay state\n");
        }

        bool stateChanged = msg.relay1 != s_prevRelay1 ||
                            msg.relay2 != s_prevRelay2;
        if (legacyStateUpdated) {
            s_prevRelay1 = msg.relay1;
            s_prevRelay2 = msg.relay2;
        }
        bool actualWriteSucceeded = deviceStateUpdated && legacyStateUpdated;
        if (msg.measureDesiredLatency && actualWriteSucceeded) {
#if DEBUG_FIRMWARE_METRICS
            LOG("[LATENCY] desired-to-actual-write=%lu ms\n",
                static_cast<unsigned long>(millis() - msg.desiredReceivedMs));
#endif
        } else if (msg.measureDesiredLatency) {
#if DEBUG_FIRMWARE_METRICS
            LOG("[LATENCY] desired-to-actual-write failed\n");
#endif
        }
        return;
    }

    if (msg.isAutoLog || msg.isDoorEvent || msg.isDoorResult ||
        msg.isAutoStatus || msg.isAutoPending || msg.isAutoPendingCleanup) {
        firebase_do_extension_event(msg);
        return;
    }

    if (msg.isHeartbeat) {
        char devicePath[128];
        snprintf(devicePath, sizeof(devicePath), "/devices/%s", FIREBASE_DEVICE_ID);
        fb_patch(devicePath, "{\"last_seen\":{\".sv\":\"timestamp\"}}");

        char healthBody[192];
        snprintf(healthBody, sizeof(healthBody),
                 "{\"heap_free\":%u,\"heap_min\":%u,\"uptime_s\":%lu,\"rssi\":%d,\"wifi\":%d}",
                 static_cast<unsigned>(ESP.getFreeHeap()),
                 static_cast<unsigned>(ESP.getMinFreeHeap()),
                 static_cast<unsigned long>(millis() / 1000UL),
                 WiFi.RSSI(), static_cast<int>(WiFi.status()));
        fb_patch("/debug/health", healthBody);
        return;
    }

    const char* r1 = msg.relay1 ? "true" : "false";
    const char* r2 = msg.relay2 ? "true" : "false";

    bool stateChanged = (msg.relay1 != s_prevRelay1 ||
                         msg.relay2 != s_prevRelay2);

    const char* labelName  = (msg.label >= 0 && msg.label < LABEL_COUNT)
                              ? LABEL_NAMES[msg.label] : "unknown";
    const char* sourceName = (msg.source == CMD_SOURCE_VOICE)
                              ? "voice" : "remote";

    s_lightState = msg.relay1;
    s_fanState = msg.relay2;
    firebase_sync_device_state();

    // [1] PATCH trạng thái tại root
    char stateBody[256];
    char* p   = stateBody;
    int   rem = sizeof(stateBody);
    int   n;

    n = snprintf(p, rem,
        "{"
        "\"devices/%s/relay1\":%s,"
        "\"devices/%s/relay2\":%s,"
        "\"devices/%s/last_seen\":{\".sv\":\"timestamp\"}",
        FIREBASE_DEVICE_ID, r1,
        FIREBASE_DEVICE_ID, r2,
        FIREBASE_DEVICE_ID);
    p += n;
    rem -= n;

    if (msg.clearRemoteCmd) {
        n = snprintf(p, rem,
            ",\"devices/%s/remote_cmd\":\"\"",
            FIREBASE_DEVICE_ID);
        p += n;
        rem -= n;
    }

    snprintf(p, rem, "}");

    if (fb_patch("/", stateBody)) {
    } else {
        LOG("[FB] Loi ghi state\n");
        return;
    }

    if (stateChanged) {
        s_prevRelay1 = msg.relay1;
        s_prevRelay2 = msg.relay2;
    }
}

static bool firebase_sync_device_state() {
    if (!s_firebaseReady || !wifi_connected() || !Firebase.ready()) return false;
    if (s_deviceStateSynced &&
        s_syncedLightState == s_lightState &&
        s_syncedFanState == s_fanState &&
        s_syncedGardenState == s_gardenState &&
        s_syncedDoorState == s_doorState &&
        s_syncedCurtainState == s_curtainOpen &&
        s_syncedVoiceEnabled == s_voiceEnabled &&
        s_syncedKeypadLocked == doorIsKeypadLocked()) return true;

    FirebaseJson stateJson;
    stateJson.set("light", s_lightState);
    stateJson.set("fan", s_fanState);
    stateJson.set("curtain", s_curtainOpen ? "open" : "close");
    stateJson.set("garden", s_gardenState);
    stateJson.set("yard", s_gardenState);
    stateJson.set("door", s_doorState);
    stateJson.set("voice", s_voiceEnabled);
    stateJson.set("keypad_locked", doorIsKeypadLocked());

    if (!FB_TIMED("setJSON devices/state",
                  Firebase.setJSON(s_fbWrite, "/devices/state", stateJson))) {
        LOG("[FB] Loi dong bo /devices/state: %s\n",
            s_fbWrite.errorReason().c_str());
        return false;
    }
    Firebase.setBool(s_fbWrite, "/door/keypad_locked", doorIsKeypadLocked());

    s_deviceStateSynced = true;
    s_syncedLightState = s_lightState;
    s_syncedFanState = s_fanState;
    s_syncedGardenState = s_gardenState;
    s_syncedDoorState = s_doorState;
    s_syncedCurtainState = s_curtainOpen;
    s_syncedVoiceEnabled = s_voiceEnabled;
    s_syncedKeypadLocked = doorIsKeypadLocked();
    return true;

}

static void firebase_queue_command_actual(const DeviceCommand& command,
                                          int label, bool clearRemoteCmd) {
    FbUpdateMsg update = {};
    update.isDeviceState = true;
    update.relay1 = get_relay_state(1);
    update.relay2 = get_relay_state(2);
    update.garden = get_relay_state(3);
    update.door = door_is_open();
    update.curtain = curtain_is_open();
    update.measureDesiredLatency = true;
    update.desiredReceivedMs = command.receivedMs;
    update.label = label;
    update.clearRemoteCmd = clearRemoteCmd;
    update.source = CMD_SOURCE_REMOTE;
    if (!firebase_enqueue_update(update)) {
        LOG("[FB] Could not queue actual state after desired command\n");
    }
}

static void handle_remote_command(String value) {
    value.toLowerCase();
    value.trim();
    value.replace("\"", "");
    if (value.length() == 0 || value == "null") return;

    int label = -1;
    if      (value == "bat_den")  label = LABEL_BAT_DEN;
    else if (value == "bat_het")  label = LABEL_BAT_HET;
    else if (value == "bat_quat") label = LABEL_BAT_QUAT;
    else if (value == "tat_den")  label = LABEL_TAT_DEN;
    else if (value == "tat_het")  label = LABEL_TAT_HET;
    else if (value == "tat_quat") label = LABEL_TAT_QUAT;
    if (label < 0) return;

    DeviceCommand command = {};
    command.device = 4;
    command.label = label;
    command.clearRemoteCmd = true;
    command.receivedMs = millis();
    enqueue_device_command(command);
}

static void firebase_apply_device_command(const DeviceCommand& command) {
    bool changed = false;
    int label = -1;
    LOG("[FB] Applying device command: device=%u value=%s\n",
        command.device,
        command.device == 2
            ? (command.curtainOpen ? "open" : "close")
            : (command.boolValue ? "true" : "false"));

    switch (command.device) {
        case 0:
            if (get_relay_state(1) == command.boolValue) return;
            label = command.boolValue ? LABEL_BAT_DEN : LABEL_TAT_DEN;
            execute_command_from(command.boolValue ? LABEL_BAT_DEN : LABEL_TAT_DEN,
                                 1.0f, RELAY_SOURCE_MANUAL);
            changed = true;
            break;

        case 1:
            if (get_relay_state(2) == command.boolValue) return;
            label = command.boolValue ? LABEL_BAT_QUAT : LABEL_TAT_QUAT;
            execute_command_from(command.boolValue ? LABEL_BAT_QUAT : LABEL_TAT_QUAT,
                                 1.0f, RELAY_SOURCE_MANUAL);
            changed = true;
            break;

        case 2:
            if (!curtain_servo_set(command.curtainOpen)) return;
            s_curtainCommandInFlight = command;
            s_curtainCommandPending = true;
            return;

        case 3:
            if (get_relay_state(3) == command.boolValue) return;
            automationSetGardenState(command.boolValue);
            changed = true;
            break;

        case 4:
        {
            if (command.label < 0 || command.label >= LABEL_COUNT ||
                command.label == LABEL_NOISE || command.label == LABEL_UNKNOWN) return;
            bool relay1Before = get_relay_state(1);
            bool relay2Before = get_relay_state(2);
            execute_command_from(command.label, 1.0f, RELAY_SOURCE_MANUAL);
            changed = relay1Before != get_relay_state(1) ||
                      relay2Before != get_relay_state(2);
            if (changed || command.clearRemoteCmd) {
                firebase_queue_command_actual(command, command.label,
                                              command.clearRemoteCmd);
            }
            return;
        }

        default:
            return;
    }

    if (changed) firebase_queue_command_actual(command, label, false);
}

static void firebase_do_voice_log(const VoiceLogRecord& record) {
    if (!s_firebaseReady || !wifi_connected() || !Firebase.ready() ||
        record.label < 0 || record.label >= LABEL_COUNT ||
        record.label == LABEL_NOISE || record.label == LABEL_UNKNOWN) {
        return;
    }

    struct timeval tv = {};
    gettimeofday(&tv, nullptr);
    uint64_t timestampMs = static_cast<uint64_t>(tv.tv_sec) * 1000ULL +
                           static_cast<uint64_t>(tv.tv_usec / 1000);
    if (timestampMs < 1700000000000ULL) {
        timestampMs = static_cast<uint64_t>(millis());
    }

    FirebaseJson json;
    json.set("keyword", LABEL_NAMES[record.label]);
    json.set("confidence", record.confidence);
    json.set("executed", record.executed);
    json.set("ts/.sv", "timestamp");

    char path[64];
    snprintf(path, sizeof(path), "/logs/voice/%llu",
             static_cast<unsigned long long>(timestampMs));

    if (!FB_TIMED("setJSON voice log", Firebase.setJSON(s_fbWrite, path, json))) {
        LOG("[VOICE LOG] Firebase write failed: %s\n",
            s_fbWrite.errorReason().c_str());
    }
}

static bool json_string(FirebaseJson& json, const char* key, String& value) {
    FirebaseJsonData data;
    if (!json.get(data, key) || !data.success) return false;
    value = data.stringValue;
    return true;
}

static bool json_number(FirebaseJson& json, const char* key, double& value) {
    FirebaseJsonData data;
    if (!json.get(data, key) || !data.success) return false;
    value = data.doubleValue;
    return true;
}

static AutoConditionType parse_condition_type(const String& condStr) {
    if (condStr == "temp_high") return COND_TEMP_HIGH;
    if (condStr == "temp_low") return COND_TEMP_LOW;
    if (condStr == "hum_high") return COND_HUM_HIGH;
    if (condStr == "hum_low") return COND_HUM_LOW;
    if (condStr == "no_motion") return COND_NO_MOTION;
    if (condStr == "motion") return COND_MOTION;
    if (condStr == "dark") return COND_DARK;
    if (condStr == "time") return COND_TIME;
    return COND_NONE;
}

static AutoTargetDevice parse_target_device(const String& devStr) {
    if (devStr == "light") return DEV_LIGHT;
    if (devStr == "fan") return DEV_FAN;
    if (devStr == "yard" || devStr == "garden") return DEV_YARD;
    if (devStr == "curtain") return DEV_CURTAIN;
    if (devStr == "door") return DEV_DOOR;
    if (devStr == "voice") return DEV_VOICE;
    return DEV_NONE;
}

static uint16_t parse_time_minutes(const String& timeStr) {
    int colon = timeStr.indexOf(':');
    if (colon < 0) return 0;
    int h = timeStr.substring(0, colon).toInt();
    int m = timeStr.substring(colon + 1).toInt();
    if (h < 0) h = 0; if (h > 23) h = 23;
    if (m < 0) m = 0; if (m > 59) m = 59;
    return static_cast<uint16_t>(h * 60 + m);
}

static bool parse_single_auto_rule(FirebaseJson& item, const char* fallbackId, AutoRule& r) {
    memset(&r, 0, sizeof(r));

    String idStr, nameStr, condStr, fromStr, toStr, modeStr, timeoutStr;
    double thresh = 0, waitMin = 3;
    FirebaseJsonData data;

    if (json_string(item, "id", idStr) && idStr.length() > 0) {
        snprintf(r.id, sizeof(r.id), "%s", idStr.c_str());
    } else if (fallbackId && strlen(fallbackId) > 0) {
        snprintf(r.id, sizeof(r.id), "%s", fallbackId);
    } else {
        return false;
    }

    if (json_string(item, "name", nameStr)) {
        snprintf(r.name, sizeof(r.name), "%s", nameStr.c_str());
    }

    if (json_string(item, "condition", condStr)) {
        r.condition = parse_condition_type(condStr);
    }

    if (json_number(item, "threshold", thresh)) {
        r.threshold = static_cast<float>(thresh);
    }

    if (json_string(item, "from", fromStr)) {
        r.fromMinutes = parse_time_minutes(fromStr);
    }
    if (json_string(item, "to", toStr)) {
        r.toMinutes = parse_time_minutes(toStr);
    }

    // Parse days array
    r.daysMask = 0;
    if (item.get(data, "days") && data.success && data.type == "array") {
        FirebaseJsonArray daysArr;
        daysArr.setJsonArrayData(data.stringValue);
        for (size_t d = 0; d < daysArr.size(); ++d) {
            FirebaseJsonData dayVal;
            if (daysArr.get(dayVal, d) && dayVal.success) {
                int dayNum = dayVal.intValue;
                if (dayNum >= 0 && dayNum <= 6) {
                    r.daysMask |= (1 << dayNum);
                }
            }
        }
    } else {
        r.daysMask = 0x7F; // Mặc định tất cả các ngày
    }

    // Parse actions
    r.actionCount = 0;
    if (item.get(data, "actions") && data.success && data.type == "array") {
        FirebaseJsonArray actArr;
        actArr.setJsonArrayData(data.stringValue);
        for (size_t a = 0; a < actArr.size() && r.actionCount < MAX_ACTIONS_PER_RULE; ++a) {
            FirebaseJsonData actItem;
            if (actArr.get(actItem, a) && actItem.success && actItem.type == "object") {
                FirebaseJson actJson;
                actJson.setJsonData(actItem.stringValue);
                String actType, actDev, actVal;
                json_string(actJson, "type", actType);
                json_string(actJson, "device", actDev);
                json_string(actJson, "value", actVal);

                AutoAction& action = r.actions[r.actionCount];
                if (actType == "notify") {
                    action.type = ACTION_TYPE_NOTIFY;
                    action.device = DEV_NONE;
                    action.value = true;
                    r.actionCount++;
                } else if (actType == "device") {
                    action.type = ACTION_TYPE_DEVICE;
                    action.device = parse_target_device(actDev);
                    action.value = (actVal == "on" || actVal == "open" || actVal == "true");
                    if (action.device != DEV_NONE) {
                        r.actionCount++;
                    }
                }
            }
        }
    }

    // Mode: auto vs ask
    if (json_string(item, "mode", modeStr)) {
        r.mode = (modeStr == "ask") ? MODE_ASK : MODE_AUTO;
    }

    if (json_number(item, "wait", waitMin)) {
        r.waitMinutes = static_cast<uint16_t>(waitMin);
        if (r.waitMinutes == 0) r.waitMinutes = 3;
    } else {
        r.waitMinutes = 3;
    }

    if (json_string(item, "timeout", timeoutStr)) {
        r.timeout = (timeoutStr == "none") ? TIMEOUT_NONE : TIMEOUT_AUTO;
    } else {
        r.timeout = TIMEOUT_AUTO;
    }

    if (item.get(data, "enabled") && data.success) {
        r.enabled = data.boolValue;
    } else {
        r.enabled = true;
    }

    return true;
}

static void firebase_load_automation_config() {
    if (!Firebase.ready()) return;

    // 1. Đồng bộ Master switch: /automation/enabled
    if (FB_TIMED("getBool /automation/enabled",
                 Firebase.getBool(s_fbWrite, "/automation/enabled"))) {
        if (s_fbWrite.dataType() == "boolean") {
            bool masterEnabled = s_fbWrite.boolData();
            automationSetMasterEnabled(masterEnabled);
        }
    }

    // 2. Đồng bộ danh sách Rules: /automation/rules
    if (FB_TIMED("getJSON /automation/rules",
                 Firebase.getJSON(s_fbWrite, "/automation/rules"))) {
        AutoRule parsedRules[MAX_AUTOMATION_RULES] = {};
        uint8_t ruleCount = 0;

        if (s_fbWrite.dataType() == "array") {
            FirebaseJsonArray arr = s_fbWrite.to<FirebaseJsonArray>();
            for (size_t i = 0; i < arr.size() && ruleCount < MAX_AUTOMATION_RULES; ++i) {
                FirebaseJsonData itemData;
                if (arr.get(itemData, i) && itemData.success && itemData.type == "object") {
                    FirebaseJson item;
                    item.setJsonData(itemData.stringValue);
                    char fallbackId[16];
                    snprintf(fallbackId, sizeof(fallbackId), "rule-%d", (int)i);
                    if (parse_single_auto_rule(item, fallbackId, parsedRules[ruleCount])) {
                        ruleCount++;
                    }
                }
            }
            automationSetRules(parsedRules, ruleCount);
        } else if (s_fbWrite.dataType() == "json") {
            FirebaseJson json = s_fbWrite.to<FirebaseJson>();
            size_t len = json.iteratorBegin();
            for (size_t i = 0; i < len && ruleCount < MAX_AUTOMATION_RULES; ++i) {
                int type = 0;
                String key;
                String value;
                json.iteratorGet(i, type, key, value);

                if (type == FirebaseJson::JSON_OBJECT) {
                    FirebaseJson item;
                    item.setJsonData(value);
                    if (parse_single_auto_rule(item, key.c_str(), parsedRules[ruleCount])) {
                        ruleCount++;
                    }
                }
            }
            json.iteratorEnd();
            automationSetRules(parsedRules, ruleCount);
        } else if (s_fbWrite.dataType() == "null") {
            // Không có quy tắc nào trên Firebase
            automationSetRules(nullptr, 0);
        }
    }

    s_lastConfigReadMs = millis();
}

static void firebase_poll_automation_pending() {
    if (!Firebase.ready()) return;

    if (FB_TIMED("getJSON /automation/pending",
                 Firebase.getJSON(s_fbWrite, "/automation/pending"))) {
        if (s_fbWrite.dataType() == "json") {
            FirebaseJson json = s_fbWrite.to<FirebaseJson>();
            size_t count = json.iteratorBegin();
            for (size_t i = 0; i < count; ++i) {
                int type = 0;
                String key;
                String value;
                json.iteratorGet(i, type, key, value);

                if (type == FirebaseJson::JSON_OBJECT) {
                    FirebaseJson item;
                    item.setJsonData(value);
                    String answer;
                    if (json_string(item, "answer", answer) && answer.length() > 0) {
                        LOG("[AUTO] Nhan phan hoi pending ruleId=%s: %s\n", key.c_str(), answer.c_str());
                        automationHandlePendingAnswer(key.c_str(), answer.c_str());
                    }
                }
            }
            json.iteratorEnd();
        }
    }
}

static bool firebase_write_passcode_metadata(const char* id, const char* name) {
    char path[96];
    snprintf(path, sizeof(path), "/door/passcodes/%s", id);
    FirebaseJson metadata;
    metadata.set("name", name);
    metadata.set("created_ts/.sv", "timestamp");
    if (!FB_TIMED("setJSON door/passcodes",
                  Firebase.setJSON(s_fbWrite, path, metadata))) {
        LOG("[DOOR] Metadata write failed: %s\n",
            s_fbWrite.errorReason().c_str());
        return false;
    }
    return true;
}

static void firebase_sync_passcode_metadata() {
    if (!Firebase.ready() || !s_passcodeMetadataNeedsSync) return;
    uint32_t nowMs = millis();
    if (nowMs - s_lastPasscodeSyncMs < 5000UL) return;
    s_lastPasscodeSyncMs = nowMs;

    FirebaseJson remote;
    bool remoteExists = FB_TIMED("getJSON door/passcodes",
                                 Firebase.getJSON(s_fbWrite, "/door/passcodes"));
    if (!remoteExists) {
        LOG("[DOOR] Read passcode metadata failed: %s\n",
            s_fbWrite.errorReason().c_str());
        return;
    }
    if (s_fbWrite.dataType() != "null") {
        remote = s_fbWrite.to<FirebaseJson>();
        size_t count = remote.iteratorBegin();
        for (size_t i = 0; i < count; ++i) {
            int type = 0;
            String id;
            String value;
            remote.iteratorGet(i, type, id, value);
            bool found = false;
            for (size_t index = 0; index < doorPasscodeCount(); ++index) {
                DoorPasscodeMetadata local = {};
                if (doorPasscodeGet(index, &local) &&
                    id == local.id) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                char path[96];
                snprintf(path, sizeof(path), "/door/passcodes/%s", id.c_str());
                if (!FB_TIMED("deleteNode door/passcodes",
                              Firebase.deleteNode(s_fbWrite, path))) {
                    LOG("[DOOR] Remove stale metadata failed: %s\n",
                        s_fbWrite.errorReason().c_str());
                }
                vTaskDelay(pdMS_TO_TICKS(10));
            }
        }
        remote.iteratorEnd();
    }

    for (size_t index = 0; index < doorPasscodeCount(); ++index) {
        DoorPasscodeMetadata local = {};
        if (!doorPasscodeGet(index, &local)) continue;
        FirebaseJsonData value;
        String lookup = String(local.id) + "/name";
        bool exists = remote.get(value, lookup);
        if (!exists || !value.success) {
            firebase_write_passcode_metadata(local.id, local.name);
            vTaskDelay(pdMS_TO_TICKS(10));
        }
    }
    s_passcodeMetadataNeedsSync = false;
}

static void firebase_poll_door_pending() {
    if (!Firebase.ready()) return;
    FirebaseJson pending;
    if (!FB_TIMED("getJSON door/pending",
                  Firebase.getJSON(s_fbWrite, "/door/pending"))) {
        LOG("[DOOR] Pending read failed: %s\n",
            s_fbWrite.errorReason().c_str());
        return;
    }
    if (s_fbWrite.dataType() == "null") return;

    pending = s_fbWrite.to<FirebaseJson>();
    size_t count = pending.iteratorBegin();
    for (size_t i = 0; i < count; ++i) {
        int type = 0;
        String id;
        String value;
        pending.iteratorGet(i, type, id, value);
        if (type != FirebaseJson::JSON_OBJECT) continue;

        FirebaseJson item;
        if (!item.setJsonData(value)) continue;
        String operation;
        if (!json_string(item, "op", operation) || operation.length() == 0) {
            char cleanupPath[128];
            snprintf(cleanupPath, sizeof(cleanupPath), "/door/pending/%s", id.c_str());
            Firebase.deleteNode(s_fbWrite, cleanupPath);
            vTaskDelay(pdMS_TO_TICKS(10));
            continue;
        }
        String name;
        String code;
        String target;
        char error[96] = {};
        bool processed = false;
        if (operation == "add") {
            json_string(item, "name", name);
            json_string(item, "code", code);
            processed = doorPasscodeAdd(id.c_str(), name.c_str(),
                                        code.c_str(), error, sizeof(error));
            if (processed) {
                DoorPasscodeMetadata metadata = {};
                bool metadataFound = false;
                for (size_t index = 0; index < doorPasscodeCount(); ++index) {
                    if (doorPasscodeGet(index, &metadata) &&
                        id == metadata.id) {
                        metadataFound = firebase_write_passcode_metadata(
                            metadata.id, metadata.name);
                        break;
                    }
                }
                if (!metadataFound) {
                    processed = false;
                    snprintf(error, sizeof(error),
                             "Không đồng bộ được metadata");
                }
            }
            code = "";
        } else if (operation == "delete") {
            json_string(item, "target", target);
            bool validTarget = target.length() > 0 && target.length() < 49;
            for (size_t targetIndex = 0;
                 validTarget && targetIndex < target.length(); ++targetIndex) {
                char character = target[targetIndex];
                validTarget = (character >= 'a' && character <= 'z') ||
                              (character >= 'A' && character <= 'Z') ||
                              (character >= '0' && character <= '9') ||
                              character == '-' || character == '_';
            }
            bool targetExists = false;
            for (size_t index = 0;
                 validTarget && index < doorPasscodeCount(); ++index) {
                DoorPasscodeMetadata metadata = {};
                if (doorPasscodeGet(index, &metadata) &&
                    target == metadata.id) {
                    targetExists = true;
                    break;
                }
            }
            if (!validTarget) {
                snprintf(error, sizeof(error), "ID không hợp lệ");
            } else if (targetExists) {
                processed = doorPasscodeDelete(target.c_str(), error,
                                               sizeof(error));
            } else {
                char metadataPath[96];
                snprintf(metadataPath, sizeof(metadataPath),
                         "/door/passcodes/%s", target.c_str());
                bool metadataExists =
                    FB_TIMED("getJSON door/passcode metadata",
                             Firebase.getJSON(s_fbWrite, metadataPath)) &&
                    s_fbWrite.dataType() != "null";
                if (metadataExists) {
                    processed = FB_TIMED(
                        "deleteNode door/passcode metadata",
                        Firebase.deleteNode(s_fbWrite, metadataPath));
                    if (!processed) {
                        snprintf(error, sizeof(error),
                                 "Không xóa được metadata");
                    }
                } else {
                    snprintf(error, sizeof(error), "Không tìm thấy mã");
                }
            }
            if (processed) {
                char metadataPath[96];
                snprintf(metadataPath, sizeof(metadataPath),
                         "/door/passcodes/%s", target.c_str());
                if (!FB_TIMED("deleteNode door/passcodes",
                              Firebase.deleteNode(s_fbWrite, metadataPath))) {
                    processed = false;
                    snprintf(error, sizeof(error),
                             "Không xóa được metadata");
                    LOG("[DOOR] Metadata delete failed: %s\n",
                        s_fbWrite.errorReason().c_str());
                }
            }
        } else {
            snprintf(error, sizeof(error), "Thao tác không hợp lệ");
        }

        char pendingPath[128];
        snprintf(pendingPath, sizeof(pendingPath),
                 "/door/pending/%s", id.c_str());
        FB_TIMED("deleteNode door/pending",
                 Firebase.deleteNode(s_fbWrite, pendingPath));
        if (!processed && error[0]) {
            LOG("[DOOR] Pending op failed for %s: %s\n", id.c_str(), error);
        }
        vTaskDelay(pdMS_TO_TICKS(20));
    }
    pending.iteratorEnd();
}

static void firebase_do_extension_event(const FbUpdateMsg& msg) {
    if (!Firebase.ready()) return;

    if (msg.isDoorResult) {
        if (!FB_TIMED("deleteNode commands/door",
                      Firebase.deleteNode(s_fbWrite, "/commands/door"))) {
            LOG("[DOOR] Command delete failed: %s\n",
                s_fbWrite.errorReason().c_str());
        }
    }

    if (msg.isAutoStatus) {
        char statusPath[128];
        snprintf(statusPath, sizeof(statusPath), "/automation/status/%s", msg.ruleId);
        FirebaseJson statusJson;
        statusJson.set("state", msg.statusState);
        if (msg.timestamp > 0) {
            statusJson.set("lastRunAt", static_cast<uint32_t>(msg.timestamp));
        }
        if (msg.lastResult[0]) {
            statusJson.set("lastResult", msg.lastResult);
        }
        FB_TIMED("setJSON /automation/status",
                 Firebase.setJSON(s_fbWrite, statusPath, statusJson));
        return;
    }

    if (msg.isAutoPending) {
        char pendingPath[128];
        snprintf(pendingPath, sizeof(pendingPath), "/automation/pending/%s", msg.ruleId);
        FirebaseJson pendingJson;
        pendingJson.set("ruleId", msg.ruleId);
        pendingJson.set("ruleName", msg.ruleName);
        pendingJson.set("createdAt", static_cast<uint32_t>(msg.timestamp));
        pendingJson.set("expiresAt", static_cast<uint32_t>(msg.expiresAt));
        pendingJson.set("timeout", msg.timeoutAction);
        FB_TIMED("setJSON /automation/pending",
                 Firebase.setJSON(s_fbWrite, pendingPath, pendingJson));
        return;
    }

    if (msg.isAutoPendingCleanup) {
        char pendingPath[128];
        snprintf(pendingPath, sizeof(pendingPath), "/automation/pending/%s", msg.ruleId);
        FB_TIMED("deleteNode /automation/pending",
                 Firebase.deleteNode(s_fbWrite, pendingPath));
        return;
    }

    if (!msg.isAutoLog && !msg.isDoorEvent) return;
    struct timeval now = {};
    gettimeofday(&now, nullptr);
    uint64_t timestampMs = static_cast<uint64_t>(now.tv_sec) * 1000ULL +
                           static_cast<uint64_t>(now.tv_usec / 1000);
    char path[128];
    FirebaseJson json;
    if (msg.isAutoLog) {
        json.set("action", msg.action);
        json.set("reason", msg.reason);
        if (isnan(msg.temperature)) json.set("temp");
        else json.set("temp", msg.temperature);
        json.set("level", msg.lightLevel);
        json.set("ts/.sv", "timestamp");
        snprintf(path, sizeof(path), "/logs/auto/%llu",
                 static_cast<unsigned long long>(timestampMs));
    } else {
        json.set("action", msg.action);
        json.set("source", msg.eventSource);
        json.set("who", msg.who);
        json.set("accepted", msg.accepted);
        if (msg.reason[0]) json.set("reason", msg.reason);
        json.set("ts/.sv", "timestamp");
        snprintf(path, sizeof(path), "/logs/door/%llu",
                 static_cast<unsigned long long>(timestampMs));
    }

    if (!FB_TIMED("setJSON extension log",
                  Firebase.setJSON(s_fbWrite, path, json))) {
        LOG("[FB] Firebase write failed: %s\n",
            s_fbWrite.errorReason().c_str());
    }
    if (msg.isDoorEvent && !msg.accepted) {
        char alertPath[128];
        snprintf(alertPath, sizeof(alertPath), "/alerts/door/%llu",
                 static_cast<unsigned long long>(timestampMs));
        if (!FB_TIMED("setJSON door alert",
                      Firebase.setJSON(s_fbWrite, alertPath, json))) {
            LOG("[DOOR] Alert write failed: %s\n",
                s_fbWrite.errorReason().c_str());
        }
    }
}

// ── WiFi ─────────────────────────────────────────────────────
bool wifi_init() {
    WiFi.onEvent(wifi_event_callback);
    WiFi.mode(WIFI_STA);
    WiFi.setSleep(false);
    WiFi.setAutoReconnect(true);
    // Hạ công suất phát xuống 15dBm để giảm dòng đỉnh, bảo vệ nguồn không sụt áp gây reset ESP32
    WiFi.setTxPower(WIFI_POWER_15dBm);

    LOG("[WIFI] Bat dau ket noi WiFi: %s ...\n", WIFI_SSID);
    // Bắt đầu kết nối bằng thông tin config
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

    s_lastReconnectMs = millis();
    s_wifiAttemptCount = 0;
    return true;
}

// Hàm này không block lâu, nên có thể được gọi trong firebase_task.
void wifi_loop() {
    wl_status_t status = WiFi.status();
    bool connected = (status == WL_CONNECTED);

    if (connected && !s_wasConnected) {
        s_wasConnected = true;
        s_wifiAttemptCount = 0;
        configTime(7 * 3600, 0, "pool.ntp.org");
        s_needFirebaseReinit = true;
        LOG("[WIFI] Da ket noi — IP=%s RSSI=%d dBm — yeu cau Firebase init/reinit\n",
            WiFi.localIP().toString().c_str(), WiFi.RSSI());
        return;
    }

    if (!connected && s_wasConnected) {
        s_wasConnected       = false;
        s_lastReconnectMs    = millis();
        s_firebaseReady      = false;
        s_commandsMayApply  = false;
        s_deviceStateSynced = false;
        s_needFirebaseReinit = false;
        s_lastFirebaseAttemptMs = 0;
        fb_http_reset();
        LOG("[WIFI] Mat ket noi — status=%d (%s)\n",
            static_cast<int>(status), wifi_status_name(status));
    }

    if (connected) return;

    uint32_t now = millis();
    uint32_t retryDelayMs = WIFI_RETRY_MIN_MS;
    uint32_t exponent = s_wifiAttemptCount < 4 ? s_wifiAttemptCount : 4;
    retryDelayMs <<= exponent;
    if (retryDelayMs > WIFI_RETRY_MAX_MS) retryDelayMs = WIFI_RETRY_MAX_MS;
    if (now - s_lastReconnectMs < retryDelayMs) return;

    LOG("[WIFI] Thu ket noi lai lan %lu...\n",
        static_cast<unsigned long>(s_wifiAttemptCount + 1));
    if (!WiFi.reconnect()) {
        LOG("[WIFI] reconnect() khong khoi tao duoc; se thu lai theo backoff\n");
    }
    s_lastReconnectMs = now;
    s_wifiAttemptCount++;
}

bool wifi_connected() {
    return WiFi.status() == WL_CONNECTED;
}

// ── handle_remote_command ────────────────────────────────────
static bool enqueue_device_command(const DeviceCommand& command) {
    if (s_deviceCommandQueue == nullptr) return false;
    if (xQueueSend(s_deviceCommandQueue, &command, 0) == pdTRUE) return true;

    DeviceCommand discarded = {};
    xQueueReceive(s_deviceCommandQueue, &discarded, 0);
    if (xQueueSend(s_deviceCommandQueue, &command, 0) == pdTRUE) {
        LOG("[FB] Device command queue full; dropped oldest command\n");
        return true;
    }

    LOG("[FB] Device command queue full; command dropped\n");
    return false;
}

static bool enqueue_json_device_command(FirebaseJson& json, const char* key,
                                        uint8_t device) {
    FirebaseJsonData value;
    if (!json.get(value, key) || !value.success) return false;

    if (value.type == "object") {
        FirebaseJson commandJson;
        if (!value.getJSON(commandJson)) {
            LOG("[FB] Invalid command payload for %s\n", key);
            return false;
        }
        if (!commandJson.get(value, "value") || !value.success) {
            LOG("[FB] Invalid command payload for %s\n", key);
            return false;
        }
    }

    DeviceCommand command = {};
    command.device = device;
    command.receivedMs = millis();
    if (device == 2) {
        if (value.type != "string") return false;
        String curtain = value.stringValue;
        curtain.toLowerCase();
        curtain.trim();
        if (curtain != "open" && curtain != "close") return false;
        command.curtainOpen = curtain == "open";
    } else {
        if (value.type != "boolean") return false;
        command.boolValue = value.boolValue;
    }

    LOG("[FB] Received /commands/%s (%s)\n", key,
        device == 2
            ? (command.curtainOpen ? "open" : "close")
            : (command.boolValue ? "true" : "false"));
    return enqueue_device_command(command);
}

// ── Stream callbacks ─────────────────────────────────────────

static void commands_stream_callback(StreamData data) {
    String path = data.dataPath();
    if (data.eventType() == "keep-alive") return;
    if (data.dataType() == "null") return;

    LOG("[FB] Commands event: stream=%s path=%s type=%s event=%s\n",
        data.streamPath().c_str(), path.c_str(), data.dataType().c_str(),
        data.eventType().c_str());

    if (path.startsWith("/commands/")) {
        path.remove(0, 10);
    } else if (path.startsWith("/")) {
        path.remove(0, 1);
    }
    if (path == "commands") path = "";

    if (path.length() == 0 && data.dataType() == "json") {
        FirebaseJson commands = data.to<FirebaseJson>();
        enqueue_json_device_command(commands, "light", 0);
        enqueue_json_device_command(commands, "fan", 1);
        enqueue_json_device_command(commands, "curtain", 2);
        enqueue_json_device_command(commands, "garden", 3);
        enqueue_json_device_command(commands, "yard", 3);

        FirebaseJsonData doorData;
        if (commands.get(doorData, "door") && doorData.success) {
            DoorRemoteCommand command = {};
            command.receivedMs = millis();
            if (doorData.type == "string") {
                String value = doorData.stringValue;
                value.toLowerCase();
                value.trim();
                snprintf(command.action, sizeof(command.action), "%s", value.c_str());
                snprintf(command.source, sizeof(command.source), "web");
            } else if (doorData.type == "json") {
                FirebaseJson doorJson;
                doorData.getJSON(doorJson);
                FirebaseJsonData val;
                if (doorJson.get(val, "action") && val.success) {
                    String action = val.stringValue;
                    action.toLowerCase();
                    snprintf(command.action, sizeof(command.action), "%s", action.c_str());
                } else {
                    snprintf(command.action, sizeof(command.action), "invalid");
                }
                if (doorJson.get(val, "ts") && val.success) {
                    command.timestampMs = val.doubleValue;
                    command.hasTimestamp = true;
                }
                if (doorJson.get(val, "src") && val.success && val.stringValue.length() > 0) {
                    snprintf(command.source, sizeof(command.source), "%s", val.stringValue.c_str());
                } else {
                    snprintf(command.source, sizeof(command.source), "app");
                }
            }
            if (s_doorCommandQueue != nullptr) {
                xQueueSend(s_doorCommandQueue, &command, 0);
            }
        }
        return;
    }

    if (path == "door") {
        DoorRemoteCommand command = {};
        command.receivedMs = millis();
        if (data.dataType() == "string") {
            String value = data.stringData();
            value.toLowerCase();
            value.trim();
            snprintf(command.action, sizeof(command.action), "%s",
                     value.c_str());
            snprintf(command.source, sizeof(command.source), "web");
        } else if (data.dataType() == "json") {
            FirebaseJson json = data.to<FirebaseJson>();
            FirebaseJsonData value;
            if (json.get(value, "action") && value.success) {
                String action = value.stringValue;
                action.toLowerCase();
                snprintf(command.action, sizeof(command.action), "%s",
                         action.c_str());
            } else {
                snprintf(command.action, sizeof(command.action), "invalid");
            }
            if (json.get(value, "ts") && value.success) {
                command.timestampMs = value.doubleValue;
                command.hasTimestamp = true;
            }
            if (json.get(value, "src") && value.success && value.stringValue.length() > 0) {
                snprintf(command.source, sizeof(command.source), "%s", value.stringValue.c_str());
            } else {
                snprintf(command.source, sizeof(command.source), "web");
            }
        } else {
            snprintf(command.action, sizeof(command.action), "invalid");
            snprintf(command.source, sizeof(command.source), "web");
        }

        if (s_doorCommandQueue == nullptr ||
            xQueueSend(s_doorCommandQueue, &command, 0) != pdTRUE) {
            DoorRemoteCommand discarded = {};
            if (s_doorCommandQueue != nullptr) {
                xQueueReceive(s_doorCommandQueue, &discarded, 0);
            }
            if (s_doorCommandQueue == nullptr ||
                xQueueSend(s_doorCommandQueue, &command, 0) != pdTRUE) {
                LOG("[DOOR] Remote command queue full; command dropped\n");
            }
        }
        return;
    }

    uint8_t device = 0;
    if (path == "light") device = 0;
    else if (path == "fan") device = 1;
    else if (path == "curtain") device = 2;
    else if (path == "garden" || path == "yard") device = 3;
    else if (path == "voice") {
        bool val = true;
        if (data.dataType() == "boolean") val = data.boolData();
        else if (data.dataType() == "string") val = (data.stringData() == "true" || data.stringData() == "on");
        else if (data.dataType() == "json") {
            FirebaseJson j = data.to<FirebaseJson>();
            FirebaseJsonData d;
            if (j.get(d, "value") && d.success) val = d.boolValue;
        }
        firebase_set_voice_enabled(val);
        firebase_queue_device_state(get_relay_state(1), get_relay_state(2), get_relay_state(3), door_is_open(), curtain_is_open());
        LOG("[VOICE] Remote voice enabled changed: %s\n", val ? "ON" : "OFF");
        return;
    }
    else return;

    FirebaseJson commandJson;
    if (data.dataType() == "json") {
        commandJson = data.to<FirebaseJson>();
    } else {
        if (data.dataType() == "boolean") {
            commandJson.set("value", data.boolData());
        } else if (data.dataType() == "string") {
            commandJson.set("value", data.stringData());
        } else {
            return;
        }
    }
    enqueue_json_device_command(commandJson, "value", device);
}

static void stream_timeout_callback(bool timeout) {
    if (timeout) {
        LOG("[FB] Commands stream keepalive timeout, resuming...\n");
    }
}

// ── firebase_task ────────────────────────────────────────────
// Toàn bộ WiFi + Firebase init/loop chạy ở đây, trên Core 0.
static void firebase_task(void* pvParam) {
    FbUpdateMsg msg;

    LOG("[FB] firebase_task khoi dong (Core %d)\n", xPortGetCoreID());

    // Khởi chạy một lần; Arduino WiFi quản lý reconnect tự động.
    wifi_init();
    LOG("[WIFI] Bat dau ket noi lan 1 tren Core %d\n", xPortGetCoreID());

    for (;;) {
        // 1. Quản lý WiFi / reconnect
        wifi_loop();

        // 2. Nếu WiFi vừa kết nối hoặc stream timeout → init/reinit Firebase
        uint32_t now = millis();
        bool firebaseRetryDue = s_firebaseReady
            ? s_needFirebaseReinit
            : (!s_lastFirebaseAttemptMs ||
               now - s_lastFirebaseAttemptMs >= FB_REINIT_RETRY_MS);
        if (wifi_connected() && firebaseRetryDue) {
            s_needFirebaseReinit = false;
            firebase_do_reinit();
            s_lastFirebaseAttemptMs = millis();
        }

        bool updateHandled = false;
        if (s_firebaseReady && wifi_connected() &&
            xQueueReceive(s_fbQueue, &msg, 0) == pdTRUE) {
            firebase_do_update(msg);
            updateHandled = true;
        }

        if (s_firebaseReady && wifi_connected() && !updateHandled &&
            uxQueueMessagesWaiting(s_fbQueue) == 0) {
            uint32_t now = millis();
            if (now - s_lastConfigReadMs >= AUTO_CONFIG_REFRESH_MS) {
                s_lastConfigReadMs = now;
                firebase_load_automation_config();
                vTaskDelay(pdMS_TO_TICKS(50));
            } else if (now - s_lastAutoPendingReadMs >= 5000UL) {
                s_lastAutoPendingReadMs = now;
                firebase_poll_automation_pending();
                vTaskDelay(pdMS_TO_TICKS(50));
            } else if (now - s_lastPendingReadMs >= 5000UL) {
                s_lastPendingReadMs = now;
                firebase_poll_door_pending();
                vTaskDelay(pdMS_TO_TICKS(50));
            } else if (s_passcodeMetadataNeedsSync) {
                firebase_sync_passcode_metadata();
                vTaskDelay(pdMS_TO_TICKS(50));
            }
        }

        // 3. Heartbeat định kỳ
        if (s_firebaseReady && wifi_connected() && !updateHandled &&
            uxQueueMessagesWaiting(s_fbQueue) == 0) {
            uint32_t now = millis();
            if (now - s_lastHeartbeatMs >= FB_HEARTBEAT_MS) {
                s_lastHeartbeatMs = now;

                FbUpdateMsg hb = {};
                hb.isHeartbeat = true;
                firebase_do_update(hb);
            }
        }

        VoiceLogRecord voiceLog;
        if (!updateHandled && uxQueueMessagesWaiting(s_fbQueue) == 0 &&
            s_voiceLogQueue != nullptr &&
            xQueueReceive(s_voiceLogQueue, &voiceLog, 0) == pdTRUE) {
            firebase_do_voice_log(voiceLog);
        }

        vTaskDelay(pdMS_TO_TICKS(10));
    }
}

// ── firebase_manager_start ───────────────────────────────────
// Hàm duy nhất nên gọi trong setup().
void firebase_manager_start() {
    if (s_fbTaskHandle != nullptr) {
        LOG("[FB] Manager da chay roi\n");
        return;
    }

    if (!s_fbQueue) {
        s_fbQueue = xQueueCreate(FB_QUEUE_DEPTH, sizeof(FbUpdateMsg));
        if (!s_fbQueue) {
            LOG("[FB] Khong tao duoc queue\n");
            return;
        }
    }

    if (!s_voiceLogQueue) {
        s_voiceLogQueue = xQueueCreate(FB_VOICE_LOG_QUEUE_DEPTH, sizeof(VoiceLogRecord));
        if (!s_voiceLogQueue) {
            LOG("[FB] Khong tao duoc voice log queue\n");
            return;
        }
    }

    if (!s_deviceCommandQueue) {
        s_deviceCommandQueue = xQueueCreate(
            FB_DEVICE_COMMAND_QUEUE_DEPTH, sizeof(DeviceCommand));
        if (!s_deviceCommandQueue) {
            LOG("[FB] Khong tao duoc device command queue\n");
            return;
        }
    }

    if (!s_doorCommandQueue) {
        s_doorCommandQueue = xQueueCreate(4, sizeof(DoorRemoteCommand));
        if (!s_doorCommandQueue) {
            LOG("[FB] Khong tao duoc door command queue\n");
            return;
        }
    }

    BaseType_t ok = xTaskCreatePinnedToCore(
        firebase_task,
        "firebase_task",
        FB_TASK_STACK_SIZE,
        nullptr,
        FB_TASK_PRIORITY,
        &s_fbTaskHandle,
        0       // Core 0
    );

    if (ok != pdPASS) {
        LOG("[FB] Khong tao duoc firebase_task\n");
        s_fbTaskHandle = nullptr;
        return;
    }

    LOG("[FB] Manager started — setup khong bi block\n");
}

// Giữ lại để tương thích code cũ.
// Nếu chỗ nào vẫn gọi firebase_init(), nó cũng chỉ start task, không block nữa.
void firebase_init() {
    firebase_manager_start();
}

// Giữ lại để tương thích code cũ.
// Nếu main.cpp đã bỏ firebase_loop(), task vẫn tự gửi heartbeat.
void firebase_loop() {
    if (!s_fbQueue) return;

    uint32_t now = millis();
    if (now - s_lastHeartbeatMs < FB_HEARTBEAT_MS) return;

    s_lastHeartbeatMs = now;

    FbUpdateMsg hb = {};
    hb.isHeartbeat = true;
    firebase_enqueue_update(hb);
}

void firebase_manager_loop() {
    if (curtain_servo_update() && s_curtainCommandPending) {
        firebase_queue_command_actual(s_curtainCommandInFlight, -1, false);
        s_curtainCommandPending = false;
    }
    if (!s_commandsMayApply) return;
    DeviceCommand latest[5] = {};
    bool pending[5] = {};
    DeviceCommand command = {};
    while (s_deviceCommandQueue != nullptr &&
           xQueueReceive(s_deviceCommandQueue, &command, 0) == pdTRUE) {
        if (command.device >= 5) continue;
        latest[command.device] = command;
        pending[command.device] = true;
    }
    for (uint8_t device = 0; device < 5; ++device) {
        if (pending[device]) {
            firebase_apply_device_command(latest[device]);
        }
    }
}

bool firebase_take_door_command(char* action, size_t actionSize,
                                int64_t* timestampMs, bool* hasTimestamp,
                                char* source, size_t sourceSize,
                                uint32_t* receivedAtMs) {
    if (s_doorCommandQueue == nullptr) return false;
    DoorRemoteCommand command = {};
    if (xQueueReceive(s_doorCommandQueue, &command, 0) != pdTRUE) return false;
    if (action != nullptr && actionSize > 0) {
        snprintf(action, actionSize, "%s", command.action);
    }
    if (source != nullptr && sourceSize > 0) {
        snprintf(source, sourceSize, "%s", command.source);
    }
    if (timestampMs != nullptr) *timestampMs = command.timestampMs;
    if (hasTimestamp != nullptr) *hasTimestamp = command.hasTimestamp;
    if (receivedAtMs != nullptr) *receivedAtMs = command.receivedMs;
    return true;
}

// ── firebase_update ──────────────────────────────────────────
void firebase_update(int label, bool relay1, bool relay2, CommandSource source) {
    s_deviceStateDirty = true;
    if (!s_fbQueue || !s_firebaseReady || !wifi_connected()) return;

    FbUpdateMsg msg = {};
    msg.label  = label;
    msg.relay1 = relay1;
    msg.relay2 = relay2;
    msg.source = source;

    firebase_enqueue_update(msg);
}

bool firebase_connected() {
    return s_firebaseReady && wifi_connected();
}

bool firebase_queue_sensor_data(float temperature, float humidity, time_t timestamp) {
    if (!s_fbQueue || !s_firebaseReady || !wifi_connected()) return false;

    FbUpdateMsg msg = {};
    msg.isSensor = true;
    msg.temperature = temperature;
    msg.humidity = humidity;
    msg.timestamp = timestamp;
    msg.sensorValid = true;

    return firebase_enqueue_update(msg);
}

bool firebase_queue_sensor_snapshot(float temperature, float humidity, bool valid,
                                    const char* presence, bool motion,
                                    time_t lastMotionTimestamp, uint8_t lightLevel,
                                    bool isDark, time_t timestamp) {
    if (!s_fbQueue || !s_firebaseReady || !wifi_connected() ||
        timestamp <= 1700000000) return false;

    FbUpdateMsg msg = {};
    msg.isSensor = true;
    msg.isSensorSnapshot = true;
    msg.sensorValid = valid;
    msg.temperature = temperature;
    msg.humidity = humidity;
    msg.timestamp = timestamp;
    msg.motion = motion;
    msg.lightLevel = lightLevel;
    msg.isDark = isDark;
    msg.lastMotionTimestamp = lastMotionTimestamp;
    snprintf(msg.presence, sizeof(msg.presence), "%s",
             presence != nullptr ? presence : "absent");
    return firebase_enqueue_update(msg);
}

void firebase_queue_device_state(bool light, bool fan, bool garden,
                                 bool door, bool curtain,
                                 bool measureDesiredLatency,
                                 uint32_t desiredReceivedMs) {
    s_deviceStateDirty = true;
    if (!s_firebaseReady || !wifi_connected()) return;
    FbUpdateMsg msg = {};
    msg.isDeviceState = true;
    msg.relay1 = light;
    msg.relay2 = fan;
    msg.garden = garden;
    msg.door = door;
    msg.curtain = curtain;
    msg.measureDesiredLatency = measureDesiredLatency;
    msg.desiredReceivedMs = desiredReceivedMs;
    firebase_enqueue_update(msg);
}

void firebase_queue_auto_log(const char* action, const char* reason,
                             float temperature, uint8_t lightLevel) {
    if (!s_firebaseReady || !wifi_connected()) return;
    FbUpdateMsg msg = {};
    msg.isAutoLog = true;
    msg.temperature = temperature;
    msg.lightLevel = lightLevel;
    snprintf(msg.action, sizeof(msg.action), "%s", action ? action : "");
    snprintf(msg.reason, sizeof(msg.reason), "%s", reason ? reason : "");
    if (!firebase_enqueue_update(msg)) {
        LOG("[FB] Could not queue automatic log\n");
    }
}

void firebase_queue_door_event(const char* action, const char* source,
                               const char* who, bool accepted,
                               const char* reason) {
    if (!s_firebaseReady || !wifi_connected()) return;
    FbUpdateMsg msg = {};
    msg.isDoorEvent = true;
    msg.accepted = accepted;
    snprintf(msg.action, sizeof(msg.action), "%s", action ? action : "");
    snprintf(msg.eventSource, sizeof(msg.eventSource), "%s",
             source ? source : "");
    snprintf(msg.who, sizeof(msg.who), "%s", who ? who : "");
    snprintf(msg.reason, sizeof(msg.reason), "%s", reason ? reason : "");
    if (!firebase_enqueue_update(msg)) {
        LOG("[FB] Could not queue door event\n");
    }
}

void firebase_set_keypad_locked(bool locked) {
    (void)locked;
    s_deviceStateDirty = true;
    if (!s_fbQueue || !s_firebaseReady || !wifi_connected()) return;
    FbUpdateMsg msg = {};
    msg.isDeviceState = true;
    if (!firebase_enqueue_update(msg)) {
        LOG("[FB] Could not queue keypad lock state\n");
    }
}

void firebase_queue_door_command_result(const char* action, const char* source,

                                        bool accepted, const char* reason) {
    if (!s_firebaseReady || !wifi_connected()) return;
    FbUpdateMsg msg = {};
    msg.isDoorResult = true;
    msg.isDoorEvent = !accepted; // Nếu thành công thì Web/App đã ghi logs/web; không ghi đè thêm logs/door
    msg.accepted = accepted;
    snprintf(msg.action, sizeof(msg.action), "%s", action ? action : "");
    snprintf(msg.eventSource, sizeof(msg.eventSource), "%s",
             source ? source : "");
    snprintf(msg.reason, sizeof(msg.reason), "%s", reason ? reason : "");
    if (!firebase_enqueue_update(msg)) {
        LOG("[FB] Could not queue door command result\n");
    }
}

bool firebase_queue_voice_log(int label, float confidence, bool executed, time_t timestamp) {
    if (label < 0 || label >= LABEL_COUNT || label == LABEL_NOISE || label == LABEL_UNKNOWN ||
        confidence < 0.0f || confidence > 1.0f) {
        return false;
    }

    LOG("VOICE LOG: %s %.2f\n", LABEL_NAMES[label], confidence);

    if (!s_voiceLogQueue || !s_firebaseReady || !wifi_connected()) {
        return false;
    }

    VoiceLogRecord record = {};
    record.label = label;
    record.confidence = confidence;
    record.executed = executed;
    record.timestamp = timestamp;

    if (xQueueSend(s_voiceLogQueue, &record, 0) != pdTRUE) {
        VoiceLogRecord discarded;
        if (xQueueReceive(s_voiceLogQueue, &discarded, 0) == pdTRUE) {
            LOG("[VOICE LOG] Queue full; dropped oldest record\n");
        }
        if (xQueueSend(s_voiceLogQueue, &record, 0) != pdTRUE) {
            LOG("[VOICE LOG] Queue full; could not enqueue record\n");
            return false;
        }
    }

    return true;
}

void firebase_queue_automation_status(const char* ruleId, const char* state,
                                      time_t lastRunAt, const char* lastResult) {
    if (!ruleId || !state) return;
    FbUpdateMsg msg = {};
    msg.isAutoStatus = true;
    snprintf(msg.ruleId, sizeof(msg.ruleId), "%s", ruleId);
    snprintf(msg.statusState, sizeof(msg.statusState), "%s", state);
    msg.timestamp = lastRunAt;
    if (lastResult) {
        snprintf(msg.lastResult, sizeof(msg.lastResult), "%s", lastResult);
    }
    firebase_enqueue_update(msg);
}

void firebase_queue_automation_pending(const char* ruleId, const char* ruleName,
                                       time_t createdAt, time_t expiresAt,
                                       const char* timeoutAction) {
    if (!ruleId) return;
    FbUpdateMsg msg = {};
    msg.isAutoPending = true;
    snprintf(msg.ruleId, sizeof(msg.ruleId), "%s", ruleId);
    if (ruleName) snprintf(msg.ruleName, sizeof(msg.ruleName), "%s", ruleName);
    msg.timestamp = createdAt;
    msg.expiresAt = expiresAt;
    if (timeoutAction) snprintf(msg.timeoutAction, sizeof(msg.timeoutAction), "%s", timeoutAction);
    firebase_enqueue_update(msg);
}

void firebase_queue_automation_pending_cleanup(const char* ruleId) {
    if (!ruleId) return;
    FbUpdateMsg msg = {};
    msg.isAutoPendingCleanup = true;
    snprintf(msg.ruleId, sizeof(msg.ruleId), "%s", ruleId);
    firebase_enqueue_update(msg);
}
