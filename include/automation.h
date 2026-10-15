#pragma once

#include <Arduino.h>
#include <time.h>

enum PresenceState : uint8_t {
    PRESENCE_ABSENT = 0,
    PRESENCE_LEAVING = 1,
    PRESENCE_PRESENT = 2
};

struct AutomationConfig {
    bool enabled;
    float fanTempOn;
    uint16_t noPersonMinutes;
};

struct GardenConfig {
    bool manual;
    uint8_t darkBelow;
    uint8_t brightAbove;
};

void automationInit();
void automationUpdate();
void automationSetConfig(const AutomationConfig& config);
void automationSetGardenConfig(const GardenConfig& config);
void automationSetGardenState(bool on);
void automationNotifyManualControl(uint8_t relay);
bool automationGardenState();
PresenceState automationPresenceState();
const char* automationPresenceName(PresenceState state);
time_t automationLastMotionTimestamp();
uint8_t automationDarkBelow();
