#pragma once

#include <Arduino.h>
#include <time.h>

// Giới hạn an toàn tài nguyên bộ nhớ ESP32
#define MAX_AUTOMATION_RULES 20
#define MAX_ACTIONS_PER_RULE 4
#define RULE_ID_LEN          48
#define RULE_NAME_LEN        80

enum PresenceState : uint8_t {
    PRESENCE_ABSENT = 0,
    PRESENCE_LEAVING = 1,
    PRESENCE_PRESENT = 2
};

enum AutoConditionType : uint8_t {
    COND_NONE = 0,
    COND_TEMP_HIGH,
    COND_TEMP_LOW,
    COND_HUM_HIGH,
    COND_HUM_LOW,
    COND_NO_MOTION,
    COND_MOTION,
    COND_DARK,
    COND_TIME
};

enum AutoActionType : uint8_t {
    ACTION_TYPE_NOTIFY = 0,
    ACTION_TYPE_DEVICE
};

enum AutoTargetDevice : uint8_t {
    DEV_NONE = 0,
    DEV_LIGHT,    // Đèn phòng (Relay 1)
    DEV_FAN,      // Quạt (Relay 2)
    DEV_YARD,     // Đèn sân (Relay 3)
    DEV_CURTAIN,  // Rèm cửa
    DEV_DOOR,     // Cửa chính
    DEV_VOICE     // Nhận diện giọng nói & LED
};

struct AutoAction {
    AutoActionType type;
    AutoTargetDevice device;
    bool value;   // true = on/open, false = off/close
};

enum AutoRuleMode : uint8_t {
    MODE_AUTO = 0, // Tự động làm luôn
    MODE_ASK       // Hỏi tôi trước
};

enum AutoTimeoutBehavior : uint8_t {
    TIMEOUT_NONE = 0, // Hết giờ: Không làm gì
    TIMEOUT_AUTO      // Hết giờ: Tự thực hiện
};

struct AutoRule {
    char id[RULE_ID_LEN];
    char name[RULE_NAME_LEN];
    AutoConditionType condition;
    float threshold;
    uint16_t fromMinutes; // Số phút tính từ 00:00 (vd: 18:00 = 1080)
    uint16_t toMinutes;   // Số phút tính từ 00:00 (vd: 22:00 = 1320)
    uint8_t daysMask;     // Bit 0 = CN, Bit 1 = T2, ..., Bit 6 = T7 (hoặc theo UI 0..6)
    AutoAction actions[MAX_ACTIONS_PER_RULE];
    uint8_t actionCount;
    AutoRuleMode mode;
    uint16_t waitMinutes; // Thời gian chờ xác nhận (mặc định 1, 3, 5, 10)
    AutoTimeoutBehavior timeout;
    bool enabled;

    // Runtime state (Edge-trigger & Cooldown)
    bool prevConditionMet;
    uint32_t lastFiredMs;
};

// Cấu trúc trạng thái báo cáo status lên Firebase (/automation/status/<ruleId>)
enum RuleRunState : uint8_t {
    STATE_WAITING = 0,
    STATE_PENDING_CONFIRM,
    STATE_RAN
};

struct RuleStatusReport {
    char ruleId[RULE_ID_LEN];
    RuleRunState state;
    time_t lastRunAt;
    char lastResult[64];
};

// Cấu trúc câu hỏi chờ xác nhận (/automation/pending/<ruleId>)
struct PendingConfirmation {
    char ruleId[RULE_ID_LEN];
    char ruleName[RULE_NAME_LEN];
    time_t createdAt;
    time_t expiresAt;
    AutoTimeoutBehavior timeout;
    AutoAction actions[MAX_ACTIONS_PER_RULE];
    uint8_t actionCount;
    bool active;
};

// Khởi tạo và cập nhật Rule Engine
void automationInit();
void automationUpdate();

// Nhận cấu hình từ Firebase Task
void automationSetMasterEnabled(bool enabled);
bool automationGetMasterEnabled();
void automationSetRules(const AutoRule* rules, uint8_t count);
uint8_t automationGetRulesCount();

// Xử lý phản hồi "Hỏi tôi trước" từ Firebase
void automationHandlePendingAnswer(const char* ruleId, const char* answer);

// Các hàm getter giữ lại tương thích cho sensors.cpp & led_relay.cpp
void automationNotifyManualControl(uint8_t relay);
void automationSetGardenState(bool on);
bool automationGardenState();
PresenceState automationPresenceState();
const char* automationPresenceName(PresenceState state);
time_t automationLastMotionTimestamp();
uint8_t automationDarkBelow();
