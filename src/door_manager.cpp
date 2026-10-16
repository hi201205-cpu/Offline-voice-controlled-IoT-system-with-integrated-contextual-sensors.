#include "door_manager.h"

#include <Preferences.h>
#include <mbedtls/sha256.h>
#include <sys/time.h>
#include <time.h>

#include "config.h"
#include "firebase_manager.h"
#include "led_relay.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"

namespace {
constexpr size_t kSaltSize = 16;
constexpr size_t kHashSize = 32;
constexpr uint8_t kRows = 4;
constexpr uint8_t kColumns = 3;
constexpr uint32_t kKeyDebounceMs = 50;
constexpr uint32_t kButtonDebounceMs = 35;
constexpr uint32_t kDoorAutoCloseMs = 5000;
constexpr char kKeyMap[kRows][kColumns] = {
    {'1', '2', '3'}, {'4', '5', '6'}, {'7', '8', '9'}, {'*', '0', '#'}
};
const uint8_t kRowPins[kRows] = {
    PIN_KEYPAD_ROW_1, PIN_KEYPAD_ROW_2, PIN_KEYPAD_ROW_3, PIN_KEYPAD_ROW_4
};
const uint8_t kColumnPins[kColumns] = {
    PIN_KEYPAD_COL_1, PIN_KEYPAD_COL_2, PIN_KEYPAD_COL_3
};

struct Passcode {
    char id[49];
    char name[81];
    uint8_t salt[kSaltSize];
    uint8_t hash[kHashSize];
};

Preferences s_preferences;
SemaphoreHandle_t s_nvsMutex = nullptr;
Passcode s_passcodes[DOOR_MAX_PASSCODES] = {};
size_t s_passcodeCount = 0;
char s_enteredCode[9] = {};
uint8_t s_enteredLength = 0;
uint8_t s_wrongAttempts = 0;
uint32_t s_lockoutUntilMs = 0;
bool s_lockoutActive = false;
char s_rawKey = 0;
char s_stableKey = 0;
uint32_t s_keyChangedMs = 0;
bool s_buttonRaw = true;
bool s_buttonStable = true;
uint32_t s_buttonChangedMs = 0;
bool s_enteredOverflow = false;
bool s_doorAutoCloseArmed = false;
uint32_t s_doorAutoCloseAtMs = 0;
bool s_doorMotionActive = false;
bool s_doorMotionTargetOpen = false;
bool s_doorPositionKnown = false;
uint32_t s_doorMotionEndsAtMs = 0;
bool s_doorMotionMeasuresLatency = false;
uint32_t s_doorMotionDesiredReceivedMs = 0;

bool lock_nvs() {
    return s_nvsMutex != nullptr &&
           xSemaphoreTake(s_nvsMutex, pdMS_TO_TICKS(20)) == pdTRUE;
}

void unlock_nvs() {
    xSemaphoreGive(s_nvsMutex);
}

bool code_is_valid(const char* code) {
    if (code == nullptr) return false;
    size_t length = strlen(code);
    if (length < 4 || length > 8) return false;
    for (size_t i = 0; i < length; ++i) {
        if (code[i] < '0' || code[i] > '9') return false;
    }
    return true;
}

size_t utf8_character_count(const char* text) {
    size_t count = 0;
    for (const unsigned char* p =
             reinterpret_cast<const unsigned char*>(text); *p; ++p) {
        if ((*p & 0xC0) != 0x80) ++count;
    }
    return count;
}

bool valid_name(const char* name) {
    return name != nullptr && name[0] != '\0' &&
           strlen(name) < sizeof(s_passcodes[0].name) &&
           utf8_character_count(name) <= 20;
}

bool valid_id(const char* id) {
    if (id == nullptr || id[0] == '\0' || strlen(id) >= sizeof(s_passcodes[0].id)) {
        return false;
    }
    for (const char* p = id; *p; ++p) {
        if (!(isalnum(static_cast<unsigned char>(*p)) || *p == '-' || *p == '_')) {
            return false;
        }
    }
    return true;
}

void set_error(char* error, size_t errorSize, const char* message) {
    if (error != nullptr && errorSize > 0) {
        snprintf(error, errorSize, "%s", message);
    }
}

bool set_door_state(bool open, bool measureDesiredLatency = false,
                    uint32_t desiredReceivedMs = 0) {
    if (s_doorMotionActive && s_doorMotionTargetOpen == open) return false;
    if (!s_doorMotionActive && s_doorPositionKnown &&
        door_is_open() == open) return false;

    uint32_t now = millis();
    door_servo_rotate(open);
    s_doorMotionActive = true;
    s_doorMotionTargetOpen = open;
    s_doorPositionKnown = false;
    s_doorMotionEndsAtMs = now + DOOR_SERVO_TURN_MS;
    s_doorMotionMeasuresLatency = measureDesiredLatency;
    s_doorMotionDesiredReceivedMs = desiredReceivedMs;
    s_doorAutoCloseArmed = false;
    return true;
}

void hash_code(const uint8_t salt[kSaltSize], const char* code,
               uint8_t output[kHashSize]) {
    uint8_t input[kSaltSize + 8];
    memcpy(input, salt, kSaltSize);
    size_t codeLength = strlen(code);
    memcpy(input + kSaltSize, code, codeLength);
    mbedtls_sha256(input, kSaltSize + codeLength, output, 0);
    memset(input, 0, sizeof(input));
}

bool constant_time_equal(const uint8_t* a, const uint8_t* b, size_t size) {
    uint8_t difference = 0;
    for (size_t i = 0; i < size; ++i) difference |= a[i] ^ b[i];
    return difference == 0;
}

void make_salt(uint8_t salt[kSaltSize]) {
    for (size_t i = 0; i < kSaltSize; i += sizeof(uint32_t)) {
        uint32_t random = esp_random();
        size_t count = min(sizeof(random), kSaltSize - i);
        memcpy(salt + i, &random, count);
    }
}

void slot_key(char* output, size_t size, const char* prefix, size_t slot) {
    snprintf(output, size, "%s%u", prefix, static_cast<unsigned>(slot));
}

bool persist_slot(size_t slot, const Passcode& passcode) {
    char key[8];
    slot_key(key, sizeof(key), "id", slot);
    bool ok = s_preferences.putString(key, passcode.id) > 0;
    slot_key(key, sizeof(key), "nm", slot);
    ok = s_preferences.putString(key, passcode.name) > 0 && ok;
    slot_key(key, sizeof(key), "sa", slot);
    ok = s_preferences.putBytes(key, passcode.salt, kSaltSize) == kSaltSize && ok;
    slot_key(key, sizeof(key), "ha", slot);
    ok = s_preferences.putBytes(key, passcode.hash, kHashSize) == kHashSize && ok;
    return ok;
}

void erase_slot(size_t slot) {
    char key[8];
    const char* prefixes[] = {"id", "nm", "sa", "ha"};
    for (const char* prefix : prefixes) {
        slot_key(key, sizeof(key), prefix, slot);
        s_preferences.remove(key);
    }
}

bool read_slot(size_t slot, Passcode* passcode) {
    char key[8];
    slot_key(key, sizeof(key), "id", slot);
    if (!s_preferences.isKey(key)) return false;
    String id = s_preferences.getString(key, "");
    if (!id.length()) return false;
    slot_key(key, sizeof(key), "nm", slot);
    if (!s_preferences.isKey(key)) return false;
    String name = s_preferences.getString(key, "");
    if (id.length() >= sizeof(passcode->id) ||
        name.length() >= sizeof(passcode->name)) return false;
    id.toCharArray(passcode->id, sizeof(passcode->id));
    name.toCharArray(passcode->name, sizeof(passcode->name));
    slot_key(key, sizeof(key), "sa", slot);
    if (!s_preferences.isKey(key)) return false;
    if (s_preferences.getBytes(key, passcode->salt, kSaltSize) != kSaltSize) {
        return false;
    }
    slot_key(key, sizeof(key), "ha", slot);
    if (!s_preferences.isKey(key)) return false;
    return s_preferences.getBytes(key, passcode->hash, kHashSize) == kHashSize;
}

void reload_passcodes() {
    s_passcodeCount = 0;
    for (size_t slot = 0; slot < DOOR_MAX_PASSCODES; ++slot) {
        Passcode entry = {};
        if (read_slot(slot, &entry)) {
            s_passcodes[s_passcodeCount++] = entry;
        }
    }
}

bool verify_code(const char* code, char* matchedName, size_t nameSize,
                 bool* unavailable) {
    if (unavailable != nullptr) *unavailable = false;
    if (!lock_nvs()) {
        if (unavailable != nullptr) *unavailable = true;
        return false;
    }
    reload_passcodes();
    bool matched = false;
    uint8_t computed[kHashSize];
    for (size_t i = 0; i < s_passcodeCount; ++i) {
        hash_code(s_passcodes[i].salt, code, computed);
        bool equal = constant_time_equal(computed, s_passcodes[i].hash, kHashSize);
        if (equal && !matched) {
            snprintf(matchedName, nameSize, "%s", s_passcodes[i].name);
            matched = true;
        }
    }
    memset(computed, 0, sizeof(computed));
    unlock_nvs();
    return matched;
}

void log_door_event(const char* action, const char* source,
                    const char* who, bool accepted, const char* reason) {
    firebase_queue_door_event(action, source, who, accepted, reason);
}

void open_door(const char* source, const char* who) {
    set_door_state(true);
    log_door_event("open", source, who, true, "");
}

void process_key(char key) {
    if (key == '*') {
        memset(s_enteredCode, 0, sizeof(s_enteredCode));
        s_enteredLength = 0;
        s_enteredOverflow = false;
        return;
    }
    if (key == '#') {
        if (s_enteredLength == 0) return;
        s_enteredCode[s_enteredLength] = '\0';
        if (s_lockoutActive &&
            static_cast<int32_t>(millis() - s_lockoutUntilMs) < 0) {
            memset(s_enteredCode, 0, sizeof(s_enteredCode));
            s_enteredLength = 0;
            return;
        }
        s_lockoutActive = false;

        char who[81] = {};
        bool nvsUnavailable = false;
        bool matched = !s_enteredOverflow &&
                       verify_code(s_enteredCode, who, sizeof(who),
                                   &nvsUnavailable);
        if (nvsUnavailable) return;
        if (matched) {
            s_wrongAttempts = 0;
            open_door("keypad", who);
        } else {
            if (++s_wrongAttempts >= DOOR_MAX_WRONG_ATTEMPTS) {
                s_wrongAttempts = 0;
                s_lockoutUntilMs = millis() + DOOR_LOCKOUT_MS;
                s_lockoutActive = true;
                log_door_event("locked", "keypad", "", false,
                               "Nhập sai 4 lần; khóa 60 giây");
            } else {
                log_door_event("denied", "keypad", "", false,
                               "Mã không đúng");
            }
        }
        memset(s_enteredCode, 0, sizeof(s_enteredCode));
        s_enteredLength = 0;
        s_enteredOverflow = false;
        return;
    }
    if (key >= '0' && key <= '9') {
        if (s_enteredLength < 8) s_enteredCode[s_enteredLength++] = key;
        else s_enteredOverflow = true;
    }
}

char scan_keypad() {
    for (uint8_t row = 0; row < kRows; ++row) {
        digitalWrite(kRowPins[row], LOW);
        for (uint8_t column = 0; column < kColumns; ++column) {
            if (digitalRead(kColumnPins[column]) == LOW) {
                digitalWrite(kRowPins[row], HIGH);
                return kKeyMap[row][column];
            }
        }
        digitalWrite(kRowPins[row], HIGH);
    }
    return 0;
}

void process_remote_command() {
    char action[8] = {};
    char source[8] = {};
    int64_t timestampMs = 0;
    bool hasTimestamp = false;
    uint32_t receivedAtMs = 0;
    if (!firebase_take_door_command(action, sizeof(action), &timestampMs,
                                    &hasTimestamp, source, sizeof(source),
                                    &receivedAtMs)) {
        return;
    }

    bool accepted = action[0] == 'c';
    const char* reason = "";
    if (strcmp(action, "open") == 0) {
        struct timeval now = {};
        gettimeofday(&now, nullptr);
        int64_t nowMs = static_cast<int64_t>(now.tv_sec) * 1000 +
                        now.tv_usec / 1000;
        if (now.tv_sec <= 1700000000 || !hasTimestamp ||
            llabs(nowMs - timestampMs) > 30000) {
            accepted = false;
            reason = "Giờ chưa đồng bộ hoặc timestamp quá hạn";
        } else {
            accepted = true;
        }
    } else if (strcmp(action, "close") == 0) {
        accepted = true;
    } else {
        accepted = false;
        reason = "Lệnh cửa không hợp lệ";
    }

    if (accepted) {
        bool open = strcmp(action, "open") == 0;
        set_door_state(open, true, receivedAtMs);
        LOG("[DOOR] %s source=%s\n", action, source);
    } else {
        LOG("[DOOR] reject %s source=%s: %s\n", action, source, reason);
    }
    firebase_queue_door_command_result(action, source, accepted, reason);
}
}  // namespace

bool doorControl(bool open, const char* source) {
    bool changed = set_door_state(open);
    if (changed) {
        log_door_event(open ? "open" : "close", source ? source : "automation", "", true, "");
    }
    return changed;
}

void doorInit() {
    pinMode(PIN_DOOR_LIMIT_OPEN, INPUT_PULLUP);
    pinMode(PIN_DOOR_LIMIT_CLOSED, INPUT_PULLUP);
    bool openLimitReached = digitalRead(PIN_DOOR_LIMIT_OPEN) == HIGH;
    bool closedLimitReached = digitalRead(PIN_DOOR_LIMIT_CLOSED) == HIGH;
    s_doorPositionKnown = openLimitReached != closedLimitReached;
    if (s_doorPositionKnown) {
        door_servo_stop(openLimitReached);
    } else if (openLimitReached && closedLimitReached) {
        LOG("[DOOR] Both limit switches active at startup\n");
    }

    s_nvsMutex = xSemaphoreCreateMutex();
    if (s_nvsMutex == nullptr || !s_preferences.begin("doorcodes", false)) {
        LOG("[DOOR] NVS init failed\n");
        return;
    }

    if (lock_nvs()) {
        reload_passcodes();
        if (s_passcodeCount == 0) {
            if (code_is_valid(DOOR_PASSWORD)) {
                Passcode initial = {};
                snprintf(initial.id, sizeof(initial.id), "default");
                snprintf(initial.name, sizeof(initial.name), "Mặc định");
                make_salt(initial.salt);
                hash_code(initial.salt, DOOR_PASSWORD, initial.hash);
                persist_slot(0, initial);
                reload_passcodes();
            } else {
                LOG("[DOOR] DOOR_PASSWORD must be 4-8 digits\n");
            }
        }
        unlock_nvs();
    }

    for (uint8_t row = 0; row < kRows; ++row) {
        pinMode(kRowPins[row], OUTPUT);
        digitalWrite(kRowPins[row], HIGH);
    }
    for (uint8_t column = 0; column < kColumns; ++column) {
        pinMode(kColumnPins[column], INPUT_PULLUP);
    }
    pinMode(PIN_DOOR_BUTTON, INPUT_PULLUP);
}

void doorUpdate() {
    process_remote_command();

    uint32_t now = millis();
    if (s_doorMotionActive) {
        bool targetLimitReached =
            digitalRead(s_doorMotionTargetOpen ? PIN_DOOR_LIMIT_OPEN
                                               : PIN_DOOR_LIMIT_CLOSED) == HIGH;
        bool motionTimedOut =
            static_cast<int32_t>(now - s_doorMotionEndsAtMs) >= 0;
        if (targetLimitReached || motionTimedOut) {
            bool open = s_doorMotionTargetOpen;
            bool measureLatency = s_doorMotionMeasuresLatency;
            uint32_t desiredReceivedMs = s_doorMotionDesiredReceivedMs;
            s_doorMotionActive = false;
            s_doorMotionMeasuresLatency = false;
            s_doorMotionDesiredReceivedMs = 0;
            if (targetLimitReached) {
                s_doorPositionKnown = true;
                door_servo_stop(open);
                firebase_queue_device_state(
                    get_relay_state(1), get_relay_state(2),
                    get_relay_state(3), open, curtain_is_open(),
                    measureLatency, desiredReceivedMs);
                LOG("[DOOR] Reached %s limit\n", open ? "open" : "closed");
                if (open) {
                    s_doorAutoCloseArmed = true;
                    s_doorAutoCloseAtMs = now + kDoorAutoCloseMs;
                }
            } else {
                door_servo_stop(door_is_open());
                s_doorAutoCloseArmed = false;
                LOG("[DOOR] Motion timed out before reaching %s limit\n",
                    open ? "open" : "closed");
            }
        }
    }

    if (s_doorAutoCloseArmed &&
        static_cast<int32_t>(now - s_doorAutoCloseAtMs) >= 0) {
        s_doorAutoCloseArmed = false;
        if (set_door_state(false)) LOG("[DOOR] Auto-closed after 5 seconds\n");
    }

    char key = scan_keypad();
    if (key != s_rawKey) {
        s_rawKey = key;
        s_keyChangedMs = now;
    } else if (key != s_stableKey &&
               now - s_keyChangedMs >= kKeyDebounceMs) {
        s_stableKey = key;
        if (s_stableKey != 0) process_key(s_stableKey);
    }

    bool button = digitalRead(PIN_DOOR_BUTTON) == HIGH;
    if (button != s_buttonRaw) {
        s_buttonRaw = button;
        s_buttonChangedMs = now;
    } else if (button != s_buttonStable &&
               now - s_buttonChangedMs >= kButtonDebounceMs) {
        s_buttonStable = button;
        if (!s_buttonStable) open_door("button", "");
    }
}

size_t doorPasscodeCount() {
    if (!lock_nvs()) return 0;
    size_t count = s_passcodeCount;
    unlock_nvs();
    return count;
}

bool doorPasscodeGet(size_t index, DoorPasscodeMetadata* metadata) {
    if (metadata == nullptr || !lock_nvs()) return false;
    bool found = index < s_passcodeCount;
    if (found) {
        snprintf(metadata->id, sizeof(metadata->id), "%s", s_passcodes[index].id);
        snprintf(metadata->name, sizeof(metadata->name), "%s", s_passcodes[index].name);
    }
    unlock_nvs();
    return found;
}

bool doorPasscodeAdd(const char* id, const char* name, const char* code,
                     char* error, size_t errorSize) {
    if (!valid_id(id)) {
        set_error(error, errorSize, "ID không hợp lệ");
        return false;
    }
    if (!valid_name(name)) {
        set_error(error, errorSize, "Tên trống hoặc quá 20 ký tự");
        return false;
    }
    if (!code_is_valid(code)) {
        set_error(error, errorSize, "Mã phải gồm 4-8 chữ số");
        return false;
    }
    if (!lock_nvs()) {
        set_error(error, errorSize, "NVS đang bận");
        return false;
    }

    reload_passcodes();
    size_t freeSlot = DOOR_MAX_PASSCODES;
    for (size_t slot = 0; slot < DOOR_MAX_PASSCODES; ++slot) {
        Passcode existing = {};
        if (!read_slot(slot, &existing)) {
            if (freeSlot == DOOR_MAX_PASSCODES) freeSlot = slot;
            continue;
        }
        if (strcmp(existing.id, id) == 0) {
            uint8_t retryHash[kHashSize];
            hash_code(existing.salt, code, retryHash);
            bool sameRequest = strcmp(existing.name, name) == 0 &&
                               constant_time_equal(retryHash, existing.hash,
                                                   kHashSize);
            memset(retryHash, 0, sizeof(retryHash));
            unlock_nvs();
            if (sameRequest) return true;
            set_error(error, errorSize, "ID đã tồn tại");
            return false;
        }
        uint8_t candidate[kHashSize];
        hash_code(existing.salt, code, candidate);
        bool duplicate = constant_time_equal(candidate, existing.hash, kHashSize);
        memset(candidate, 0, sizeof(candidate));
        if (duplicate) {
            unlock_nvs();
            set_error(error, errorSize, "Mã đã được sử dụng");
            return false;
        }
    }
    if (s_passcodeCount >= DOOR_MAX_PASSCODES ||
        freeSlot == DOOR_MAX_PASSCODES) {
        unlock_nvs();
        set_error(error, errorSize, "Đã đủ 10 mã");
        return false;
    }

    Passcode entry = {};
    snprintf(entry.id, sizeof(entry.id), "%s", id);
    snprintf(entry.name, sizeof(entry.name), "%s", name);
    make_salt(entry.salt);
    hash_code(entry.salt, code, entry.hash);
    bool saved = persist_slot(freeSlot, entry);
    if (saved) reload_passcodes();
    unlock_nvs();
    if (!saved) set_error(error, errorSize, "Không lưu được NVS");
    return saved;
}

bool doorPasscodeDelete(const char* id, char* error, size_t errorSize) {
    if (!valid_id(id)) {
        set_error(error, errorSize, "ID không hợp lệ");
        return false;
    }
    if (!lock_nvs()) {
        set_error(error, errorSize, "NVS đang bận");
        return false;
    }

    reload_passcodes();
    if (s_passcodeCount <= 1) {
        unlock_nvs();
        set_error(error, errorSize, "Phải giữ lại ít nhất một mã");
        return false;
    }

    size_t slotToDelete = DOOR_MAX_PASSCODES;
    for (size_t slot = 0; slot < DOOR_MAX_PASSCODES; ++slot) {
        Passcode entry = {};
        if (read_slot(slot, &entry) && strcmp(entry.id, id) == 0) {
            slotToDelete = slot;
            break;
        }
    }
    if (slotToDelete == DOOR_MAX_PASSCODES) {
        unlock_nvs();
        set_error(error, errorSize, "Không tìm thấy mã");
        return false;
    }

    erase_slot(slotToDelete);
    reload_passcodes();
    unlock_nvs();
    return true;
}
