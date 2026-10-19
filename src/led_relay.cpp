// ============================================================
//  led_relay.cpp
// ============================================================

#include "led_relay.h"

void automationNotifyManualControl(uint8_t relay);

// ============================================================
//  TRẠNG THÁI RELAY NỘI BỘ
// ============================================================

static bool s_relay1On = false;
static bool s_relay2On = false;
static bool s_relay3On = false;
static bool s_curtainOpen = false;
static bool s_curtainMotionActive = false;
static bool s_curtainMotionTargetOpen = false;
static uint32_t s_curtainMotionEndsAtMs = 0;
static bool s_doorOpen = false;
static constexpr uint8_t LED_R_LEDC_CHANNEL = 0;
static constexpr uint8_t LED_G_LEDC_CHANNEL = 1;
static constexpr uint8_t LED_B_LEDC_CHANNEL = 2;
static constexpr uint32_t LED_PWM_FREQUENCY = 5000;
static constexpr uint8_t LED_PWM_RESOLUTION = 8;
// Độ sáng ~18% (45 / 255): Mắt nhìn vẫn sáng rõ nhưng dòng điện giảm hơn 80%, chống sụt áp nguồn 3.3V
static constexpr uint32_t LED_BRIGHTNESS = 45;

static constexpr uint8_t SERVO_LEDC_CHANNEL = 7;
static constexpr uint8_t DOOR_SERVO_LEDC_CHANNEL = 6;
static constexpr uint32_t SERVO_PWM_FREQUENCY = 50;
static constexpr uint8_t SERVO_PWM_RESOLUTION = 14;
static uint8_t s_ledMode = 0;
static uint32_t s_ledModeStartedMs = 0;

// ============================================================
//  KHỞI TẠO
// ============================================================

void led_relay_init() {
    // Khởi tạo PWM cho 3 kênh LED RGB
    ledcSetup(LED_R_LEDC_CHANNEL, LED_PWM_FREQUENCY, LED_PWM_RESOLUTION);
    ledcAttachPin(PIN_LED_R, LED_R_LEDC_CHANNEL);

    ledcSetup(LED_G_LEDC_CHANNEL, LED_PWM_FREQUENCY, LED_PWM_RESOLUTION);
    ledcAttachPin(PIN_LED_G, LED_G_LEDC_CHANNEL);

    ledcSetup(LED_B_LEDC_CHANNEL, LED_PWM_FREQUENCY, LED_PWM_RESOLUTION);
    ledcAttachPin(PIN_LED_B, LED_B_LEDC_CHANNEL);

    // Set the inactive level before enabling output to avoid an active-low pulse.
    digitalWrite(PIN_RELAY_1, RELAY_OFF);
    digitalWrite(PIN_RELAY_2, RELAY_OFF);
    digitalWrite(PIN_RELAY_3, RELAY_OFF);
    pinMode(PIN_RELAY_1, OUTPUT);
    pinMode(PIN_RELAY_2, OUTPUT);
    pinMode(PIN_RELAY_3, OUTPUT);
    relay_set(PIN_RELAY_1, false);
    relay_set(PIN_RELAY_2, false);
    relay_set(PIN_RELAY_3, false);

    ledcSetup(SERVO_LEDC_CHANNEL, SERVO_PWM_FREQUENCY, SERVO_PWM_RESOLUTION);
    ledcAttachPin(PIN_CURTAIN_SERVO, SERVO_LEDC_CHANNEL);
    curtain_servo_restore(false);
    ledcSetup(DOOR_SERVO_LEDC_CHANNEL, SERVO_PWM_FREQUENCY, SERVO_PWM_RESOLUTION);
    ledcAttachPin(PIN_DOOR_SERVO, DOOR_SERVO_LEDC_CHANNEL);
    door_servo_stop(false);

    led_sleep();

    LOG("[LED_RELAY] Init done — curtain GPIO%d, door GPIO%d, LED PWM active\n",
        PIN_CURTAIN_SERVO, PIN_DOOR_SERVO);
}

// ============================================================
//  LED — HÀM CƠ BẢN
// ============================================================

void led_off() {
    ledcWrite(LED_R_LEDC_CHANNEL, 0);
    ledcWrite(LED_G_LEDC_CHANNEL, 0);
    ledcWrite(LED_B_LEDC_CHANNEL, 0);
}

static void led_set(bool r, bool g, bool b) {
    ledcWrite(LED_R_LEDC_CHANNEL, r ? LED_BRIGHTNESS : 0);
    ledcWrite(LED_G_LEDC_CHANNEL, g ? LED_BRIGHTNESS : 0);
    ledcWrite(LED_B_LEDC_CHANNEL, b ? LED_BRIGHTNESS : 0);
}

// ============================================================
//  LED — TRẠNG THÁI HỆ THỐNG
// ============================================================

void led_sleep() {
    s_ledMode = 0;
    led_set(0, 0, 0); // Chưa nói / lúc chờ: TẮT LED HOÀN TOÀN để tiết kiệm điện và chống sụt áp
}

void led_listening() {
    s_ledMode = 1;
    led_set(0, 1, 0); // Có tiếng động / phát hiện giọng nói (VAD ON): Bật xanh lá dịu
}

void led_executing() {
    s_ledMode = 2;
    s_ledModeStartedMs = millis();
    led_set(0, 0, 1); // Đang thực thi lệnh: Sáng xanh dương
}

void led_error() {
    s_ledMode = 3;
    s_ledModeStartedMs = millis();
    led_set(1, 0, 0); // Lỗi: Chớp đỏ
}

void led_relay_update(bool listening) {
    uint32_t elapsed = millis() - s_ledModeStartedMs;
    if (s_ledMode == 2 && elapsed >= 500) {
        s_ledMode = listening ? 1 : 0;
        if (listening) {
            led_listening();
        } else {
            led_sleep();
        }
    }
    if (s_ledMode == 3) {
        if (elapsed >= 900) {
            s_ledMode = listening ? 1 : 0;
            if (listening) {
                led_listening();
            } else {
                led_sleep();
            }
        } else {
            uint8_t phase = static_cast<uint8_t>(elapsed / 150);
            led_set((phase % 2) == 0, 0, 0);
            return;
        }
    }
    if (s_ledMode == 0 && listening) {
        led_listening();
    } else if (s_ledMode == 1 && !listening) {
        led_sleep();
    }
}

// ============================================================
//  RELAY
// ============================================================

void relay_set(uint8_t pin, bool on) {
    digitalWrite(pin, on ? RELAY_ON : RELAY_OFF);
}

bool relay_control(uint8_t relay, bool on, RelayCommandSource source) {
    bool* state = nullptr;
    uint8_t pin = 0;
    switch (relay) {
        case 1: state = &s_relay1On; pin = PIN_RELAY_1; break;
        case 2: state = &s_relay2On; pin = PIN_RELAY_2; break;
        case 3: state = &s_relay3On; pin = PIN_RELAY_3; break;
        default: return false;
    }

    bool changed = (*state != on);
    if (!changed) return false;
    relay_set(pin, on);
    *state = on;
    if (source == RELAY_SOURCE_MANUAL) automationNotifyManualControl(relay);
    return changed;
}

static void curtain_servo_write_pulse(uint32_t pulseUs) {
    const uint32_t periodUs = 1000000 / SERVO_PWM_FREQUENCY;
    const uint32_t maxDuty = (1UL << SERVO_PWM_RESOLUTION) - 1;
    const uint32_t duty = (pulseUs * maxDuty) / periodUs;
    ledcWrite(SERVO_LEDC_CHANNEL, duty);
}

bool curtain_servo_set(bool open) {
    if (s_curtainMotionActive && s_curtainMotionTargetOpen == open) return false;
    if (!s_curtainMotionActive && s_curtainOpen == open) return false;

    curtain_servo_write_pulse(open ? CURTAIN_SERVO_OPEN_US : CURTAIN_SERVO_CLOSE_US);
    s_curtainMotionActive = true;
    s_curtainMotionTargetOpen = open;
    s_curtainMotionEndsAtMs = millis() + CURTAIN_SERVO_TURN_MS;
    return true;
}

bool curtain_servo_update() {
    if (!s_curtainMotionActive ||
        static_cast<int32_t>(millis() - s_curtainMotionEndsAtMs) < 0) {
        return false;
    }

    curtain_servo_write_pulse(CURTAIN_SERVO_STOP_US);
    s_curtainOpen = s_curtainMotionTargetOpen;
    s_curtainMotionActive = false;
    return true;
}

void curtain_servo_restore(bool open) {
    curtain_servo_write_pulse(CURTAIN_SERVO_STOP_US);
    s_curtainOpen = open;
    s_curtainMotionActive = false;
}

static void door_servo_write_pulse(uint32_t pulseUs) {
    const uint32_t periodUs = 1000000 / SERVO_PWM_FREQUENCY;
    const uint32_t maxDuty = (1UL << SERVO_PWM_RESOLUTION) - 1;
    const uint32_t duty = (pulseUs * maxDuty) / periodUs;
    ledcWrite(DOOR_SERVO_LEDC_CHANNEL, duty);
}

void door_servo_rotate(bool opening) {
    door_servo_write_pulse(opening ? DOOR_SERVO_OPEN_US : DOOR_SERVO_CLOSE_US);
}

void door_servo_stop(bool open) {
    door_servo_write_pulse(DOOR_SERVO_STOP_US);
    s_doorOpen = open;
}

bool curtain_is_open() {
    return s_curtainOpen;
}

bool door_is_open() {
    return s_doorOpen;
}
// ============================================================
//  ĐỌC TRẠNG THÁI RELAY
//  Dùng bởi firebase_manager để tính r1/r2 đúng khi ghi Firebase.
// ============================================================

bool get_relay_state(int relay) {
    if (relay == 1) return s_relay1On;
    if (relay == 2) return s_relay2On;
    if (relay == 3) return s_relay3On;
    return false;
}

// ============================================================
//  THỰC THI LỆNH
// ============================================================

bool execute_command(int label, float confidence, uint32_t detectedAtMs,
                     uint32_t* relayLatencyMs) {
    return execute_command_from(label, confidence, RELAY_SOURCE_MANUAL,
                                detectedAtMs, relayLatencyMs);
}

bool execute_command_from(int label, float confidence, RelayCommandSource source,
                          uint32_t detectedAtMs, uint32_t* relayLatencyMs) {
    led_executing();
    bool latencyRecorded = false;
    auto setRelay = [&](uint8_t relay, bool on) {
        if (relayLatencyMs != nullptr && !latencyRecorded) {
            *relayLatencyMs = millis() - detectedAtMs;
            latencyRecorded = true;
        }
        relay_control(relay, on, source);
    };

    switch (label) {

        case LABEL_BAT_DEN:
            setRelay(1, true);
            LOG("[CMD] BAT DEN  → RELAY_1 BẬT  (conf=%.1f%%)\n", confidence * 100);
            break;

        case LABEL_TAT_DEN:
            setRelay(1, false);
            LOG("[CMD] TAT DEN  → RELAY_1 TẮT  (conf=%.1f%%)\n", confidence * 100);
            break;

        case LABEL_BAT_QUAT:
            setRelay(2, true);
            LOG("[CMD] BAT QUAT → RELAY_2 BẬT  (conf=%.1f%%)\n", confidence * 100);
            break;

        case LABEL_TAT_QUAT:
            setRelay(2, false);
            LOG("[CMD] TAT QUAT → RELAY_2 TẮT  (conf=%.1f%%)\n", confidence * 100);
            break;

        case LABEL_BAT_HET:
            setRelay(1, true);
            setRelay(2, true);
            LOG("[CMD] BAT HET  → RELAY_1+2 BẬT (conf=%.1f%%)\n", confidence * 100);
            break;

        case LABEL_TAT_HET:
            setRelay(1, false);
            setRelay(2, false);
            LOG("[CMD] TAT HET  → RELAY_1+2 TẮT (conf=%.1f%%)\n", confidence * 100);
            break;

        default:
            led_sleep();
            return false;
    }

    return true;
}
