#include "automation.h"

#include <math.h>
#include <time.h>

#include "config.h"
#include "firebase_manager.h"
#include "led_relay.h"
#include "door_manager.h"
#include "sensors.h"

// Mutex bảo vệ danh sách quy tắc khi đồng bộ giữa Core 0 (Firebase) và Core 1 (Loop)
static portMUX_TYPE s_autoMux = portMUX_INITIALIZER_UNLOCKED;

static bool s_masterEnabled = true;
static AutoRule s_rules[MAX_AUTOMATION_RULES] = {};
static uint8_t s_ruleCount = 0;

// Trạng thái chờ xác nhận "Hỏi tôi trước"
static PendingConfirmation s_pendingList[MAX_AUTOMATION_RULES] = {};

// Quản lý presence & thời gian chuyển động phục vụ cảm biến và quy tắc
static PresenceState s_presence = PRESENCE_ABSENT;
static time_t s_lastMotionTimestamp = 0;
static bool s_gardenStateManual = false;
static uint32_t s_brightSinceMs = 0;
static uint32_t s_gardenManualUntilMs = 0;

// Cooldown tối thiểu giữa các lần kích hoạt quy tắc (tránh dội xung)
static const uint32_t RULE_COOLDOWN_MS = 2000UL;

static void publish_state() {
    firebase_queue_device_state(
        get_relay_state(1), get_relay_state(2), get_relay_state(3),
        door_is_open(), curtain_is_open());
}

void automationInit() {
    s_presence = PRESENCE_ABSENT;
    s_lastMotionTimestamp = time(nullptr);
    s_gardenStateManual = get_relay_state(3);
    s_ruleCount = 0;
    memset(s_rules, 0, sizeof(s_rules));
    memset(s_pendingList, 0, sizeof(s_pendingList));
}

void automationSetMasterEnabled(bool enabled) {
    portENTER_CRITICAL(&s_autoMux);
    s_masterEnabled = enabled;
    portEXIT_CRITICAL(&s_autoMux);
    LOG("[AUTO] Master switch: %s\n", enabled ? "BAT" : "TAT");
}

bool automationGetMasterEnabled() {
    portENTER_CRITICAL(&s_autoMux);
    bool enabled = s_masterEnabled;
    portEXIT_CRITICAL(&s_autoMux);
    return enabled;
}

void automationSetRules(const AutoRule* rules, uint8_t count) {
    portENTER_CRITICAL(&s_autoMux);
    if (rules == nullptr || count == 0) {
        s_ruleCount = 0;
        memset(s_rules, 0, sizeof(s_rules));
    } else {
        uint8_t newCount = (count > MAX_AUTOMATION_RULES) ? MAX_AUTOMATION_RULES : count;
        AutoRule tempRules[MAX_AUTOMATION_RULES] = {};
        for (uint8_t i = 0; i < newCount; ++i) {
            tempRules[i] = rules[i];
            for (uint8_t j = 0; j < s_ruleCount; ++j) {
                if (strcmp(rules[i].id, s_rules[j].id) == 0) {
                    tempRules[i].prevConditionMet = s_rules[j].prevConditionMet;
                    tempRules[i].lastFiredMs = s_rules[j].lastFiredMs;
                    break;
                }
            }
        }
        memcpy(s_rules, tempRules, sizeof(AutoRule) * newCount);
        s_ruleCount = newCount;
    }
    portEXIT_CRITICAL(&s_autoMux);
    LOG("[AUTO] Da cap nhat %d quy tac tu Firebase\n", s_ruleCount);
}

uint8_t automationGetRulesCount() {
    portENTER_CRITICAL(&s_autoMux);
    uint8_t count = s_ruleCount;
    portEXIT_CRITICAL(&s_autoMux);
    return count;
}

// Thực thi danh sách hành động cho thiết bị
static void execute_actions(const AutoAction* actions, uint8_t actionCount, const char* ruleName) {
    if (!actions || actionCount == 0) return;

    for (uint8_t i = 0; i < actionCount; ++i) {
        const AutoAction& act = actions[i];
        if (act.type == ACTION_TYPE_NOTIFY) {
            LOG("[AUTO] Thong bao quy tac: %s\n", ruleName ? ruleName : "");
            continue;
        }

        if (act.type == ACTION_TYPE_DEVICE) {
            switch (act.device) {
                case DEV_LIGHT: // Relay 1
                    relay_control(1, act.value, RELAY_SOURCE_AUTO);
                    LOG("[AUTO] Den phong -> %s\n", act.value ? "BAT" : "TAT");
                    break;
                case DEV_FAN: // Relay 2
                    relay_control(2, act.value, RELAY_SOURCE_AUTO);
                    LOG("[AUTO] Quat -> %s\n", act.value ? "BAT" : "TAT");
                    break;
                case DEV_YARD: // Relay 3
                    relay_control(3, act.value, RELAY_SOURCE_AUTO);
                    LOG("[AUTO] Den san -> %s\n", act.value ? "BAT" : "TAT");
                    break;
                case DEV_CURTAIN: // Servo rem
                    curtain_servo_set(act.value);
                    LOG("[AUTO] Rem cua -> %s\n", act.value ? "MO" : "DONG");
                    break;
                case DEV_DOOR: // Cua chinh
                    doorControl(act.value, "Tự động hóa");
                    LOG("[AUTO] Cua chinh -> %s\n", act.value ? "MO" : "DONG");
                    break;
                default:
                    break;
            }
        }
    }
    publish_state();
}

// Xử lý phản hồi "Hỏi tôi trước"
void automationHandlePendingAnswer(const char* ruleId, const char* answer) {
    if (!ruleId || !answer) return;

    portENTER_CRITICAL(&s_autoMux);
    for (uint8_t i = 0; i < MAX_AUTOMATION_RULES; ++i) {
        if (s_pendingList[i].active && strcmp(s_pendingList[i].ruleId, ruleId) == 0) {
            if (strcmp(answer, "agree") == 0) {
                LOG("[AUTO] Nguoi dung DONG Y rule: %s\n", s_pendingList[i].ruleName);
                execute_actions(s_pendingList[i].actions, s_pendingList[i].actionCount, s_pendingList[i].ruleName);
                time_t now = time(nullptr);
                firebase_queue_automation_status(ruleId, "ran", now, "Đã thực hiện (người dùng đồng ý)");
                firebase_queue_auto_log(s_pendingList[i].ruleName, "Nguoi dung dong y",
                                        sensorsTemperature(), sensorsLightLevel());
            } else if (strcmp(answer, "skip") == 0) {
                LOG("[AUTO] Nguoi dung BO QUA rule: %s\n", s_pendingList[i].ruleName);
                time_t now = time(nullptr);
                firebase_queue_automation_status(ruleId, "waiting", now, "Đã bỏ qua");
            }
            s_pendingList[i].active = false;
            firebase_queue_automation_pending_cleanup(ruleId);
            break;
        }
    }
    portEXIT_CRITICAL(&s_autoMux);
}

// Kiểm tra điều kiện thời gian (UTC+7)
static bool check_time_condition(uint16_t fromMin, uint16_t toMin, uint8_t daysMask) {
    time_t now = time(nullptr);
    if (now <= 1700000000) return false; // Chưa đồng bộ NTP

    struct tm timeinfo;
    localtime_r(&now, &timeinfo);

    // timeinfo.tm_wday: 0 = CN, 1 = T2, ..., 6 = T7
    // Trong Web UI: T2(index 0), T3(1), T4(2), T5(3), T6(4), T7(5), CN(6)
    int uiDay = (timeinfo.tm_wday == 0) ? 6 : (timeinfo.tm_wday - 1);
    if (!(daysMask & (1 << uiDay))) {
        return false;
    }

    uint16_t currentMin = timeinfo.tm_hour * 60 + timeinfo.tm_min;
    if (fromMin <= toMin) {
        return (currentMin >= fromMin && currentMin <= toMin);
    } else {
        // Khung giờ vắt qua nửa đêm (vd: 22:00 -> 05:00)
        return (currentMin >= fromMin || currentMin <= toMin);
    }
}

// Đánh giá điều kiện của 1 quy tắc
static bool evaluate_condition(const AutoRule& rule, float temp, float hum,
                                bool motion, time_t lastMotion, uint8_t lightLevel) {
    switch (rule.condition) {
        case COND_TEMP_HIGH:
            return (!isnan(temp) && temp > rule.threshold);
        case COND_TEMP_LOW:
            return (!isnan(temp) && temp < rule.threshold);
        case COND_HUM_HIGH:
            return (!isnan(hum) && hum > rule.threshold);
        case COND_HUM_LOW:
            return (!isnan(hum) && hum < rule.threshold);
        case COND_MOTION:
            return motion;
        case COND_NO_MOTION: {
            time_t now = time(nullptr);
            if (now <= 1700000000 || lastMotion <= 1700000000) return false;
            uint32_t diffSec = (now > lastMotion) ? (now - lastMotion) : 0;
            return (diffSec >= static_cast<uint32_t>(rule.threshold * 60.0f));
        }
        case COND_DARK:
            return sensorsLightValid() && (lightLevel < 25);
        case COND_TIME:
            return check_time_condition(rule.fromMinutes, rule.toMinutes, rule.daysMask);
        default:
            return false;
    }
}

/*
 * Xử lý xung đột (Deterministic Conflict Handling):
 * Nếu hai quy tắc cùng kích hoạt trong cùng một chu kỳ và điều khiển cùng một thiết bị:
 * 1. Ưu tiên An Toàn: Lệnh TẮT / ĐÓNG (false) được ưu tiên hơn lệnh BẬT / MỞ (true).
 * 2. Ưu tiên Chỉ số: Nếu cùng giá trị hoặc không có sự khác biệt về an toàn, quy tắc đứng trước (index nhỏ hơn) được ưu tiên.
 * Ghi log rõ ràng lý do xung đột ra Serial.
 */
static void resolve_conflicts(AutoAction finalActions[5], bool actionSet[5]) {
    for (uint8_t d = 0; d < 5; ++d) {
        actionSet[d] = false;
    }
}

void automationUpdate() {
    uint32_t nowMs = millis();
    time_t nowTime = time(nullptr);

    // Cập nhật trạng thái chuyển động & hiện diện
    bool detected = sensorsPresenceDataValid() && sensorsPresenceDetected();
    if (detected) {
        if (s_presence != PRESENCE_PRESENT) {
            s_presence = PRESENCE_PRESENT;
            s_lastMotionTimestamp = nowTime;
            sensorsRequestPush();
        } else {
            s_lastMotionTimestamp = nowTime;
        }
    } else {
        if (s_presence == PRESENCE_PRESENT) {
            s_presence = PRESENCE_LEAVING;
            sensorsRequestPush();
        }
    }

    // 1. Kiểm tra timeout cho danh sách "Hỏi tôi trước" (Pending)
    for (uint8_t i = 0; i < MAX_AUTOMATION_RULES; ++i) {
        if (s_pendingList[i].active) {
            if (nowTime >= s_pendingList[i].expiresAt) {
                LOG("[AUTO] Pending timeout cho rule: %s\n", s_pendingList[i].ruleName);
                if (s_pendingList[i].timeout == TIMEOUT_AUTO) {
                    LOG("[AUTO] Timeout -> Tu dong thuc hien hanh dong\n");
                    execute_actions(s_pendingList[i].actions, s_pendingList[i].actionCount, s_pendingList[i].ruleName);
                    firebase_queue_automation_status(s_pendingList[i].ruleId, "ran", nowTime, "Đã tự thực hiện khi hết giờ");
                    firebase_queue_auto_log(s_pendingList[i].ruleName, "Tu dong thuc hien (timeout)",
                                            sensorsTemperature(), sensorsLightLevel());
                } else {
                    LOG("[AUTO] Timeout -> Khong lam gi\n");
                    firebase_queue_automation_status(s_pendingList[i].ruleId, "waiting", nowTime, "Hết thời gian chờ (không làm gì)");
                }
                s_pendingList[i].active = false;
                firebase_queue_automation_pending_cleanup(s_pendingList[i].ruleId);
            }
        }
    }

    // 2. Nếu Master Switch tắt -> không chạy quy tắc nào
    if (!automationGetMasterEnabled()) {
        return;
    }

    float temp = sensorsTemperature();
    float hum = sensorsHumidity();
    uint8_t light = sensorsLightLevel();

    // Tự động tắt đèn sân khi trời sáng trở lại (ngưỡng sáng >= 60% ổn định trong 5 giây)
    if (get_relay_state(3) && (int32_t)(nowMs - s_gardenManualUntilMs) >= 0) {
        if (sensorsLightValid() && light >= 60) {
            if (s_brightSinceMs == 0) {
                s_brightSinceMs = nowMs;
            } else if (nowMs - s_brightSinceMs >= 5000UL) {
                relay_control(3, false, RELAY_SOURCE_AUTO);
                publish_state();
                LOG("[AUTO] Troi da sang (do sang=%u%% >= 60%%) -> Tu dong tat den san\n", light);
                firebase_queue_auto_log("Tu dong tat den san", "Troi sang", temp, light);
                s_brightSinceMs = 0;
            }
        } else {
            s_brightSinceMs = 0;
        }
    } else {
        s_brightSinceMs = 0;
    }

    // Mảng lưu trữ hành động hợp nhất sau xử lý xung đột
    // 0: LIGHT, 1: FAN, 2: YARD, 3: CURTAIN, 4: DOOR
    AutoAction mergedActions[5];
    bool hasMergedAction[5] = {false, false, false, false, false};
    char triggeredRuleNames[5][RULE_NAME_LEN];

    portENTER_CRITICAL(&s_autoMux);
    for (uint8_t i = 0; i < s_ruleCount; ++i) {
        AutoRule& r = s_rules[i];
        if (!r.enabled) {
            r.prevConditionMet = false;
            continue;
        }

        bool condMet = evaluate_condition(r, temp, hum, detected, s_lastMotionTimestamp, light);

        // Edge-trigger: Kích hoạt khi chuyển từ false -> true và đã qua cooldown
        if (condMet && !r.prevConditionMet && (nowMs - r.lastFiredMs >= RULE_COOLDOWN_MS)) {
            r.lastFiredMs = nowMs;
            LOG("[AUTO] Rule kich hoat: %s (mode=%s)\n", r.name, r.mode == MODE_ASK ? "ask" : "auto");

            if (r.mode == MODE_ASK) {
                // Tạo câu hỏi chờ xác nhận
                uint16_t waitSec = r.waitMinutes * 60;
                time_t expires = nowTime + waitSec;

                // Lưu vào pending list của firmware
                for (uint8_t p = 0; p < MAX_AUTOMATION_RULES; ++p) {
                    if (!s_pendingList[p].active || strcmp(s_pendingList[p].ruleId, r.id) == 0) {
                        s_pendingList[p].active = true;
                        snprintf(s_pendingList[p].ruleId, sizeof(s_pendingList[p].ruleId), "%s", r.id);
                        snprintf(s_pendingList[p].ruleName, sizeof(s_pendingList[p].ruleName), "%s", r.name);
                        s_pendingList[p].createdAt = nowTime;
                        s_pendingList[p].expiresAt = expires;
                        s_pendingList[p].timeout = r.timeout;
                        s_pendingList[p].actionCount = r.actionCount;
                        memcpy(s_pendingList[p].actions, r.actions, sizeof(AutoAction) * r.actionCount);
                        break;
                    }
                }

                // Gửi lên Firebase: /automation/pending và /automation/status
                firebase_queue_automation_pending(r.id, r.name, nowTime, expires,
                                                  (r.timeout == TIMEOUT_AUTO) ? "auto" : "none");
                firebase_queue_automation_status(r.id, "pending_confirm", nowTime, "Đang chờ người dùng xác nhận");
            } else {
                // Mode AUTO: gom hành động để kiểm tra xung đột
                for (uint8_t a = 0; a < r.actionCount; ++a) {
                    const AutoAction& act = r.actions[a];
                    if (act.type == ACTION_TYPE_DEVICE) {
                        uint8_t devIdx = 0;
                        if (act.device == DEV_LIGHT) devIdx = 0;
                        else if (act.device == DEV_FAN) devIdx = 1;
                        else if (act.device == DEV_YARD) devIdx = 2;
                        else if (act.device == DEV_CURTAIN) devIdx = 3;
                        else if (act.device == DEV_DOOR) devIdx = 4;
                        else continue;

                        if (hasMergedAction[devIdx]) {
                            // Xung đột: cùng điều khiển 1 thiết bị trong cùng lượt
                            if (mergedActions[devIdx].value != act.value) {
                                LOG("[AUTO CONFLICT] Xung dot tren thiet bi %d giua '%s' va '%s'! Uu tien lenh TAT/DONG vi an toan.\n",
                                    act.device, triggeredRuleNames[devIdx], r.name);
                                // Ưu tiên false (tắt/đóng) vì an toàn
                                if (!act.value) {
                                    mergedActions[devIdx] = act;
                                    snprintf(triggeredRuleNames[devIdx], sizeof(triggeredRuleNames[devIdx]), "%s", r.name);
                                }
                            }
                        } else {
                            mergedActions[devIdx] = act;
                            hasMergedAction[devIdx] = true;
                            snprintf(triggeredRuleNames[devIdx], sizeof(triggeredRuleNames[devIdx]), "%s", r.name);
                        }
                    } else if (act.type == ACTION_TYPE_NOTIFY) {
                        LOG("[AUTO] Thong bao: %s\n", r.name);
                    }
                }

                // Cập nhật trạng thái "ran"
                firebase_queue_automation_status(r.id, "ran", nowTime, "Đã chạy tự động");
                firebase_queue_auto_log(r.name, "Tu dong chay", temp, light);
            }
        }

        // Re-arm edge-trigger khi điều kiện trở về false
        r.prevConditionMet = condMet;
    }
    portEXIT_CRITICAL(&s_autoMux);

    // 3. Thực thi các lệnh đã giải quyết xung đột
    for (uint8_t d = 0; d < 5; ++d) {
        if (hasMergedAction[d]) {
            execute_actions(&mergedActions[d], 1, triggeredRuleNames[d]);
        }
    }
}

// Các hàm getter giữ lại tương thích với sensors.cpp và led_relay.cpp
void automationNotifyManualControl(uint8_t relay) {
    // Không khóa cứng, ghi nhận log
    LOG("[AUTO] Phat hien dieu khien thu cong relay %d\n", relay);
}

void automationSetGardenState(bool on) {
    s_gardenManualUntilMs = millis() + 30000UL;
    relay_control(3, on, RELAY_SOURCE_MANUAL);
    publish_state();
}

bool automationGardenState() {
    return get_relay_state(3);
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
    return 25; // Ngưỡng tham khảo mặc định
}
