#include "automation.h"

#include <math.h>
#include <time.h>

#include "config.h"
#include "firebase_manager.h"
#include "led_relay.h"
#include "sensors.h"

static portMUX_TYPE s_configMux = portMUX_INITIALIZER_UNLOCKED;
static AutomationConfig s_autoConfig = {
    AUTO_DEFAULT_ENABLED, AUTO_DEFAULT_FAN_TEMP_ON, AUTO_DEFAULT_NO_PERSON_MIN
};
static GardenConfig s_gardenConfig = {
    false, GARDEN_DEFAULT_DARK_BELOW, GARDEN_DEFAULT_BRIGHT_ABOVE
};
static uint32_t s_manualHoldUntil[3] = {};
static bool s_manualHoldActive[3] = {};
static bool s_autoOwnsFan = false;
static bool s_gardenOn = false;
static PresenceState s_presence = PRESENCE_ABSENT;
static uint32_t s_presenceLossStartedMs = 0;
static bool s_presenceLossTimerStarted = false;
static bool s_absentLightPending = false;
static bool s_absentFanPending = false;
static time_t s_lastMotionTimestamp = 0;
static uint32_t s_darkStartedMs = 0;
static uint32_t s_brightStartedMs = 0;

static bool is_manual_hold(uint8_t relay, uint32_t now) {
    if (relay < 1 || relay > 2 || !s_manualHoldActive[relay]) return false;
    if (static_cast<int32_t>(now - s_manualHoldUntil[relay]) < 0) return true;
    s_manualHoldActive[relay] = false;
    return false;
}

static void publish_state() {
    firebase_queue_device_state(
        get_relay_state(1), get_relay_state(2), s_gardenOn,
        door_is_open(), curtain_is_open());
}

void automationInit() {
    s_gardenOn = get_relay_state(3);
    s_presence = PRESENCE_ABSENT;
    s_presenceLossTimerStarted = false;
    s_presenceLossStartedMs = millis();
}

void automationSetConfig(const AutomationConfig& config) {
    portENTER_CRITICAL(&s_configMux);
    s_autoConfig = config;
    portEXIT_CRITICAL(&s_configMux);
}

void automationSetGardenConfig(const GardenConfig& config) {
    bool changed;
    portENTER_CRITICAL(&s_configMux);
    changed = s_gardenConfig.manual != config.manual ||
              s_gardenConfig.darkBelow != config.darkBelow ||
              s_gardenConfig.brightAbove != config.brightAbove;
    s_gardenConfig = config;
    portEXIT_CRITICAL(&s_configMux);
    if (changed) {
        s_darkStartedMs = 0;
        s_brightStartedMs = 0;
    }
}

void automationSetGardenState(bool on) {
    bool changed = relay_control(3, on, RELAY_SOURCE_MANUAL);
    s_gardenOn = get_relay_state(3);
    if (changed) publish_state();
}

void automationNotifyManualControl(uint8_t relay) {
    if (relay < 1 || relay > 2) return;
    s_manualHoldUntil[relay] = millis() + MANUAL_HOLD_MS;
    s_manualHoldActive[relay] = true;
    if (relay == 2) s_autoOwnsFan = false;
}

bool automationGardenState() {
    return s_gardenOn;
}

PresenceState automationPresenceState() {
    return s_presence;
}

const char* automationPresenceName(PresenceState state) {
    switch (state) {
        case PRESENCE_PRESENT: return "present";
        case PRESENCE_LEAVING: return "leaving";
        default: return "absent";
    }
}

time_t automationLastMotionTimestamp() {
    return s_lastMotionTimestamp;
}

uint8_t automationDarkBelow() {
    portENTER_CRITICAL(&s_configMux);
    uint8_t value = s_gardenConfig.darkBelow;
    portEXIT_CRITICAL(&s_configMux);
    return value;
}

void automationUpdate() {
    AutomationConfig autoConfig;
    GardenConfig gardenConfig;
    portENTER_CRITICAL(&s_configMux);
    autoConfig = s_autoConfig;
    gardenConfig = s_gardenConfig;
    portEXIT_CRITICAL(&s_configMux);

    uint32_t now = millis();
    s_gardenOn = get_relay_state(3);
    bool detected = sensorsPresenceDataValid() && sensorsPresenceDetected();

    if (detected) {
        if (s_presence != PRESENCE_PRESENT) {
            s_presence = PRESENCE_PRESENT;
            s_presenceLossTimerStarted = false;
            s_absentLightPending = false;
            s_absentFanPending = false;
            s_lastMotionTimestamp = time(nullptr);
            LOG("[PRESENCE] present\n");
            sensorsRequestPush();
        }
    } else if (s_presence == PRESENCE_PRESENT) {
        s_presence = PRESENCE_LEAVING;
        s_presenceLossStartedMs = now;
        s_presenceLossTimerStarted = true;
        LOG("[PRESENCE] leaving\n");
        sensorsRequestPush();
    } else if (s_presence == PRESENCE_ABSENT &&
               sensorsPresenceDataValid() &&
               !s_presenceLossTimerStarted) {
        s_presence = PRESENCE_LEAVING;
        s_presenceLossStartedMs = now;
        s_presenceLossTimerStarted = true;
        LOG("[PRESENCE] leaving\n");
        sensorsRequestPush();
    } else if (s_presence == PRESENCE_LEAVING &&
               s_presenceLossTimerStarted &&
               now - s_presenceLossStartedMs >=
                   static_cast<uint32_t>(autoConfig.noPersonMinutes) * 60000UL) {
        s_presence = PRESENCE_ABSENT;
        LOG("[PRESENCE] absent\n");
        sensorsRequestPush();

        s_absentLightPending = autoConfig.enabled;
        s_absentFanPending = autoConfig.enabled;
    }

    if (s_presence == PRESENCE_ABSENT && autoConfig.enabled) {
        if (s_absentLightPending) {
            if (!get_relay_state(1)) {
                s_absentLightPending = false;
            } else if (!is_manual_hold(1, now)) {
                execute_command_from(LABEL_TAT_DEN, 1.0f, RELAY_SOURCE_AUTO);
                s_absentLightPending = false;
                LOG("AUTO: light_off (no_person)\n");
                firebase_queue_auto_log("light_off", "no_person",
                                        sensorsTemperature(), sensorsLightLevel());
                publish_state();
            }
        }
        if (s_absentFanPending) {
            if (!get_relay_state(2)) {
                s_absentFanPending = false;
            } else if (!is_manual_hold(2, now)) {
                execute_command_from(LABEL_TAT_QUAT, 1.0f, RELAY_SOURCE_AUTO);
                s_absentFanPending = false;
                s_autoOwnsFan = false;
                LOG("AUTO: fan_off (no_person)\n");
                firebase_queue_auto_log("fan_off", "no_person",
                                        sensorsTemperature(), sensorsLightLevel());
                publish_state();
            }
        }
    }

    float temperature = sensorsTemperature();
    if (autoConfig.enabled && !isnan(temperature) &&
        !is_manual_hold(2, now)) {
        if (s_presence == PRESENCE_PRESENT &&
            temperature >= autoConfig.fanTempOn &&
            !get_relay_state(2)) {
            execute_command_from(LABEL_BAT_QUAT, 1.0f, RELAY_SOURCE_AUTO);
            s_autoOwnsFan = true;
            LOG("AUTO: fan_on (temp_high_occupied)\n");
            firebase_queue_auto_log("fan_on", "temp_high_occupied",
                                    temperature, sensorsLightLevel());
            publish_state();
        } else if (s_autoOwnsFan &&
                   temperature <= autoConfig.fanTempOn - 2.0f &&
                   get_relay_state(2)) {
            execute_command_from(LABEL_TAT_QUAT, 1.0f, RELAY_SOURCE_AUTO);
            s_autoOwnsFan = false;
            LOG("AUTO: fan_off (temp_low)\n");
            firebase_queue_auto_log("fan_off", "temp_low",
                                    temperature, sensorsLightLevel());
            publish_state();
        }
    }

    if (gardenConfig.manual || !sensorsLightValid()) {
        s_darkStartedMs = 0;
        s_brightStartedMs = 0;
        return;
    }

    uint8_t level = sensorsLightLevel();
    if (level < gardenConfig.darkBelow) {
        s_brightStartedMs = 0;
        if (s_darkStartedMs == 0) s_darkStartedMs = now;
        if (now - s_darkStartedMs >= GARDEN_CONFIRM_MS && !s_gardenOn) {
            if (relay_control(3, true, RELAY_SOURCE_AUTO)) {
                s_gardenOn = true;
                LOG("AUTO: garden_on (dark)\n");
                firebase_queue_auto_log("garden_on", "dark",
                                        temperature, level);
                publish_state();
            }
            s_darkStartedMs = now;
        }
    } else if (level > gardenConfig.brightAbove) {
        s_darkStartedMs = 0;
        if (s_brightStartedMs == 0) s_brightStartedMs = now;
        if (now - s_brightStartedMs >= GARDEN_CONFIRM_MS && s_gardenOn) {
            if (relay_control(3, false, RELAY_SOURCE_AUTO)) {
                s_gardenOn = false;
                LOG("AUTO: garden_off (bright)\n");
                firebase_queue_auto_log("garden_off", "bright",
                                        temperature, level);
                publish_state();
            }
            s_brightStartedMs = now;
        }
    } else {
        s_darkStartedMs = 0;
        s_brightStartedMs = 0;
    }
}

