#include "sensors.h"

#include <DHT.h>
#include <math.h>
#include <time.h>

#include "firebase_manager.h"
#include "config.h"
#include "automation.h"
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"
#include "freertos/task.h"

#define DHT_TYPE DHT11
#define SENSOR_READ_INTERVAL_MS 5000
#define SENSOR_PUSH_INTERVAL_MS 10000
#define SENSOR_TASK_STACK_SIZE 4096
#define SENSOR_TASK_PRIORITY 1

#define LD2410S_BUFFER_SIZE 96
#define LD2410S_MINIMAL_HEAD 0x6E
#define LD2410S_MINIMAL_TAIL 0x62

static_assert(RAW_DARK != RAW_BRIGHT,
              "RAW_DARK and RAW_BRIGHT must be different ADC values");

#include "freertos/semphr.h"

static DHT s_dht(PIN_DHT, DHT_TYPE);
static QueueHandle_t s_readQueue = nullptr;
static SemaphoreHandle_t s_sensorMutex = nullptr;

static bool sensor_lock(TickType_t waitTicks = portMAX_DELAY) {
    if (s_sensorMutex == nullptr) {
        s_sensorMutex = xSemaphoreCreateMutex();
    }
    return (s_sensorMutex != nullptr && xSemaphoreTake(s_sensorMutex, waitTicks) == pdTRUE);
}

static void sensor_unlock() {
    if (s_sensorMutex != nullptr) {
        xSemaphoreGive(s_sensorMutex);
    }
}

static float s_temperature = NAN;
static float s_humidity = NAN;
static bool s_hasValidReading = false;
static uint32_t s_lastReadRequestMs = 0;
static uint32_t s_lastPushMs = 0;
static bool s_readRequested = false;
static uint32_t s_bootMs = 0;
static bool s_rawPresenceDetected = false;
static bool s_presenceCandidate = false;
static uint32_t s_presenceCandidateSinceMs = 0;
static uint16_t s_lightSamples[GARDEN_FILTER_SAMPLES] = {};
static uint32_t s_lightSum = 0;
static uint8_t s_lightIndex = 0;
static uint8_t s_lightCount = 0;
static uint8_t s_lightLevel = 0;
static bool s_lightDataValid = false;
static uint32_t s_lastLightReadMs = 0;
static uint32_t s_lastLightLogMs = 0;
static bool s_forcePush = false;
static PresenceState s_lastPushedPresence = PRESENCE_ABSENT;
static bool s_lastPushedGarden = false;

#if ENABLE_LD2410S
static const uint8_t s_ld2410sStandardHead[] = {0xF4, 0xF3, 0xF2, 0xF1};
static const uint8_t s_ld2410sStandardTail[] = {0xF8, 0xF7, 0xF6, 0xF5};
static uint8_t s_ld2410sBuffer[LD2410S_BUFFER_SIZE];
static size_t s_ld2410sBufferSize = 0;
#endif

#if ENABLE_LD2410S || ENABLE_SR501
static bool s_presenceDetected = false;
static bool s_presenceDataValid = false;
static uint8_t s_presenceState = 0;
static uint16_t s_presenceDistanceCm = 0;
static bool s_lastLoggedPresence = false;
static uint16_t s_lastLoggedDistanceCm = 0;
static uint32_t s_lastPresenceLogMs = 0;
#endif

#if ENABLE_LD2410S

static void ld2410s_drop_front() {
    if (s_ld2410sBufferSize == 0) return;
    memmove(s_ld2410sBuffer, s_ld2410sBuffer + 1, --s_ld2410sBufferSize);
}

static bool ld2410s_matches_standard_prefix() {
    size_t compareSize = s_ld2410sBufferSize;
    if (compareSize > sizeof(s_ld2410sStandardHead)) {
        compareSize = sizeof(s_ld2410sStandardHead);
    }
    return memcmp(s_ld2410sBuffer, s_ld2410sStandardHead, compareSize) == 0;
}

static void ld2410s_accept_report(uint8_t state, uint16_t distanceCm) {
    if (state > 3 || distanceCm > 1200) return;

    s_presenceState = state;
    s_rawPresenceDetected = state >= 2;
    s_presenceDistanceCm = distanceCm;
    s_presenceDataValid = true;
}

static void ld2410s_process_buffer() {
    while (s_ld2410sBufferSize > 0) {
        if (s_ld2410sBuffer[0] == LD2410S_MINIMAL_HEAD) {
            if (s_ld2410sBufferSize < 5) return;

            if (s_ld2410sBuffer[4] == LD2410S_MINIMAL_TAIL) {
                uint16_t distanceCm = static_cast<uint16_t>(
                    s_ld2410sBuffer[2] |
                    (static_cast<uint16_t>(s_ld2410sBuffer[3]) << 8));
                ld2410s_accept_report(s_ld2410sBuffer[1], distanceCm);
                s_ld2410sBufferSize -= 5;
                memmove(s_ld2410sBuffer, s_ld2410sBuffer + 5, s_ld2410sBufferSize);
                continue;
            }

            ld2410s_drop_front();
            continue;
        }

        if (s_ld2410sBuffer[0] == s_ld2410sStandardHead[0]) {
            if (!ld2410s_matches_standard_prefix()) {
                ld2410s_drop_front();
                continue;
            }
            if (s_ld2410sBufferSize < sizeof(s_ld2410sStandardHead)) return;
            if (memcmp(s_ld2410sBuffer, s_ld2410sStandardHead,
                       sizeof(s_ld2410sStandardHead)) != 0) {
                ld2410s_drop_front();
                continue;
            }
            if (s_ld2410sBufferSize < 6) return;

            uint16_t dataLength = static_cast<uint16_t>(
                s_ld2410sBuffer[4] |
                (static_cast<uint16_t>(s_ld2410sBuffer[5]) << 8));
            size_t frameSize = 6U + dataLength + sizeof(s_ld2410sStandardTail);
            if (dataLength < 6 || frameSize > LD2410S_BUFFER_SIZE) {
                ld2410s_drop_front();
                continue;
            }
            if (s_ld2410sBufferSize < frameSize) return;

            const uint8_t* tail = s_ld2410sBuffer + 6 + dataLength;
            if (memcmp(tail, s_ld2410sStandardTail,
                       sizeof(s_ld2410sStandardTail)) == 0 &&
                s_ld2410sBuffer[6] == 0x01) {
                uint16_t distanceCm = static_cast<uint16_t>(
                    s_ld2410sBuffer[8] |
                    (static_cast<uint16_t>(s_ld2410sBuffer[9]) << 8));
                ld2410s_accept_report(s_ld2410sBuffer[7], distanceCm);
                s_ld2410sBufferSize -= frameSize;
                memmove(s_ld2410sBuffer, s_ld2410sBuffer + frameSize,
                        s_ld2410sBufferSize);
                continue;
            }

            ld2410s_drop_front();
            continue;
        }

        ld2410s_drop_front();
    }
}

static void ld2410s_read() {
    while (Serial1.available() > 0) {
        uint8_t byte = static_cast<uint8_t>(Serial1.read());
        if (s_ld2410sBufferSize >= LD2410S_BUFFER_SIZE) {
            ld2410s_drop_front();
        }
        s_ld2410sBuffer[s_ld2410sBufferSize++] = byte;
        ld2410s_process_buffer();
    }

    if (!s_presenceDataValid) return;

    uint32_t now = millis();
    bool distanceChanged = s_rawPresenceDetected &&
        (abs(static_cast<int>(s_presenceDistanceCm) -
             static_cast<int>(s_lastLoggedDistanceCm)) >= 10);
    if (s_presenceDetected != s_lastLoggedPresence || distanceChanged ||
        now - s_lastPresenceLogMs >= 1000) {
        LOG("[LD2410S] %s — state=%u distance=%u cm\n",
            s_rawPresenceDetected ? "Co nguoi" : "Khong co nguoi",
            s_presenceState, s_presenceDistanceCm);
        s_lastLoggedPresence = s_presenceDetected;
        s_lastLoggedDistanceCm = s_presenceDistanceCm;
        s_lastPresenceLogMs = now;
    }
}
#endif

static void sensor_task(void *parameter) {
    uint8_t request;

    for (;;) {
        if (xQueueReceive(s_readQueue, &request, portMAX_DELAY) != pdTRUE) {
            continue;
        }

        float temperature = s_dht.readTemperature();
        float humidity = s_dht.readHumidity();
        if (isnan(temperature) || isnan(humidity)) {
            LOG("[SENSOR] Loi doc DHT11; giu gia tri hop le gan nhat\n");
            continue;
        }

        if (sensor_lock(pdMS_TO_TICKS(50))) {
            s_temperature = temperature;
            s_humidity = humidity;
            s_hasValidReading = true;
            sensor_unlock();
        }

        LOG("Temp: %.0f C, Hum: %.0f %%\n", temperature, humidity);
    }
}

void sensorsInit() {
    s_dht.begin();
    analogReadResolution(12);
    analogSetPinAttenuation(PIN_GARDEN_LIGHT, ADC_11db);
    pinMode(PIN_GARDEN_LIGHT, INPUT);
    s_bootMs = millis();
    s_presenceCandidateSinceMs = s_bootMs;

#if ENABLE_LD2410S
    Serial1.begin(LD2410S_BAUD_RATE, SERIAL_8N1,
                  LD2410S_RX_PIN, LD2410S_TX_PIN);
#endif

#if ENABLE_SR501
    pinMode(PIN_SR501, INPUT);
    s_presenceDataValid = true;
    s_presenceDetected = (digitalRead(PIN_SR501) == HIGH);
    s_presenceState = s_presenceDetected ? 2 : 0;
    s_presenceDistanceCm = 0;
    LOG("[SR501] PIR sensor ready — OUT=GPIO%d (initial=%s)\n",
        PIN_SR501, s_presenceDetected ? "HIGH" : "LOW");
#endif

    s_readQueue = xQueueCreate(1, sizeof(uint8_t));
    if (s_readQueue == nullptr) {
        LOG("[SENSOR] Khong tao duoc read queue\n");
        return;
    }

    BaseType_t taskCreated = xTaskCreatePinnedToCore(
        sensor_task,
        "sensor_task",
        SENSOR_TASK_STACK_SIZE,
        nullptr,
        SENSOR_TASK_PRIORITY,
        nullptr,
        0);

    if (taskCreated != pdPASS) {
        vQueueDelete(s_readQueue);
        s_readQueue = nullptr;
        LOG("[SENSOR] Khong tao duoc sensor task\n");
        return;
    }

    LOG("[SENSOR] DHT11 ready — DATA=GPIO%d\n", PIN_DHT);
#if ENABLE_LD2410S
    LOG("[LD2410S] UART ready — RX=GPIO%d TX=GPIO%d baud=%lu\n",
        LD2410S_RX_PIN, LD2410S_TX_PIN,
        static_cast<unsigned long>(LD2410S_BAUD_RATE));
#endif
}

void sensorsUpdate() {
    uint32_t now = millis();

    if (s_readQueue != nullptr &&
        (!s_readRequested || now - s_lastReadRequestMs >= SENSOR_READ_INTERVAL_MS)) {
        uint8_t request = 1;
        if (xQueueOverwrite(s_readQueue, &request) == pdPASS) {
            s_lastReadRequestMs = now;
            s_readRequested = true;
        } else {
            LOG("[SENSOR] Khong gui duoc yeu cau doc DHT11\n");
        }
    }

#if ENABLE_LD2410S
    ld2410s_read();

    if (s_presenceDataValid) {
        bool candidate = s_rawPresenceDetected;
        if (candidate != s_presenceCandidate) {
            s_presenceCandidate = candidate;
            s_presenceCandidateSinceMs = now;
        } else if (now - s_bootMs >= 5000 &&
                   now - s_presenceCandidateSinceMs >= 500 &&
                   s_presenceDetected != candidate) {
            s_presenceDetected = candidate;
        }
    }
#elif ENABLE_SR501
    bool motion = (digitalRead(PIN_SR501) == HIGH);
    s_rawPresenceDetected = motion;
    s_presenceDataValid = true;
    s_presenceDistanceCm = 0;

    if (motion != s_presenceCandidate) {
        s_presenceCandidate = motion;
        s_presenceCandidateSinceMs = now;
    } else if (now - s_presenceCandidateSinceMs >= 150 && s_presenceDetected != motion) {
        s_presenceDetected = motion;
        s_presenceState = motion ? 2 : 0;
        LOG("[SR501] %s (GPIO %d = %s)\n",
            motion ? "Co nguoi (Motion detected)" : "Khong co nguoi (Idle)",
            PIN_SR501, motion ? "HIGH" : "LOW");
    }
#endif

    if (now - s_lastLightReadMs >= GARDEN_SAMPLE_INTERVAL_MS) {
        s_lastLightReadMs = now;
        uint16_t raw = static_cast<uint16_t>(analogRead(PIN_GARDEN_LIGHT));
        if (s_lightCount == GARDEN_FILTER_SAMPLES) {
            s_lightSum -= s_lightSamples[s_lightIndex];
        } else {
            s_lightCount++;
        }
        s_lightSamples[s_lightIndex] = raw;
        s_lightSum += raw;
        s_lightIndex = static_cast<uint8_t>(
            (s_lightIndex + 1) % GARDEN_FILTER_SAMPLES);

        uint16_t average = static_cast<uint16_t>(s_lightSum / s_lightCount);
        int32_t scaled = (static_cast<int32_t>(average) - RAW_DARK) * 100L /
                         (RAW_BRIGHT - RAW_DARK);
        if (scaled < 0) scaled = 0;
        if (scaled > 100) scaled = 100;
        s_lightLevel = static_cast<uint8_t>(scaled);
        s_lightDataValid = true;

        if (now - s_lastLightLogMs >= 10000) {
            LOG("[LIGHT] light_level=%u raw_avg=%u\n",
                s_lightLevel, average);
            s_lastLightLogMs = now;
        }
    }
}

void sensorsPush() {
    uint32_t now = millis();
    PresenceState presence = automationPresenceState();
    bool gardenOn = automationGardenState();
    bool changed = presence != s_lastPushedPresence ||
                   gardenOn != s_lastPushedGarden;
    bool periodic = now - s_lastPushMs >= SENSOR_PUSH_INTERVAL_MS;
    bool urgent = (changed || s_forcePush) && (now - s_lastPushMs >= 2000UL || s_lastPushMs == 0);
    if (!periodic && !urgent) return;

    float temperature;
    float humidity;
    bool hasValidReading;

    if (sensor_lock(pdMS_TO_TICKS(50))) {
        temperature = s_temperature;
        humidity = s_humidity;
        hasValidReading = s_hasValidReading;
        sensor_unlock();
    } else {
        temperature = s_temperature;
        humidity = s_humidity;
        hasValidReading = s_hasValidReading;
    }

    if (!s_lightDataValid) return;

    time_t timestamp = time(nullptr);
    if (timestamp <= 1700000000) return;

    bool queued = firebase_queue_sensor_snapshot(
        temperature, humidity, hasValidReading,
        automationPresenceName(presence), presence == PRESENCE_PRESENT,
        automationLastMotionTimestamp(), s_lightLevel,
        s_lightLevel < automationDarkBelow(), timestamp);
    if (queued) {
        s_lastPushMs = now;
        s_lastPushedPresence = presence;
        s_lastPushedGarden = gardenOn;
        s_forcePush = false;
    }
}

float sensorsTemperature() {
    float temperature = s_temperature;
    if (sensor_lock(pdMS_TO_TICKS(50))) {
        temperature = s_temperature;
        sensor_unlock();
    }
    return temperature;
}

float sensorsHumidity() {
    float humidity = s_humidity;
    if (sensor_lock(pdMS_TO_TICKS(50))) {
        humidity = s_humidity;
        sensor_unlock();
    }
    return humidity;
}

bool sensorsDataValid() {
    bool valid = s_hasValidReading;
    if (sensor_lock(pdMS_TO_TICKS(50))) {
        valid = s_hasValidReading;
        sensor_unlock();
    }
    return valid;
}

bool sensorsPresenceDetected() {
#if ENABLE_LD2410S || ENABLE_SR501
    return s_presenceDetected;
#else
    return false;
#endif
}

bool sensorsPresenceDataValid() {
#if ENABLE_LD2410S || ENABLE_SR501
    return s_presenceDataValid;
#else
    return false;
#endif
}

uint8_t sensorsPresenceState() {
#if ENABLE_LD2410S || ENABLE_SR501
    return s_presenceState;
#else
    return 0;
#endif
}

uint16_t sensorsPresenceDistanceCm() {
#if ENABLE_LD2410S || ENABLE_SR501
    return s_presenceDistanceCm;
#else
    return 0;
#endif
}

uint8_t sensorsLightLevel() {
    return s_lightLevel;
}

bool sensorsLightValid() {
    return s_lightDataValid;
}

void sensorsRequestPush() {
    s_forcePush = true;
}
