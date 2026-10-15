#pragma once
// ============================================================
//  firebase_manager.h
//  Tích hợp WiFi + Firebase RTDB
// ============================================================

#include <Arduino.h>
#include <WiFi.h>
#include <time.h>
#include "config.h"

typedef enum {
    CMD_SOURCE_VOICE  = 0,
    CMD_SOURCE_REMOTE = 1,
} CommandSource;



// ── WiFi ────────────────────────────────────────────────────
bool wifi_init();
void wifi_loop();
bool wifi_connected();

// ── Firebase ─────────────────────────────────────────────────
void firebase_manager_start();
void firebase_init();
void firebase_loop();
void firebase_update(int label, bool relay1, bool relay2, CommandSource source);
bool firebase_queue_sensor_data(float temperature, float humidity, time_t timestamp);
bool firebase_queue_sensor_snapshot(float temperature, float humidity, bool valid,
                                    const char* presence, bool motion,
                                    time_t lastMotionTimestamp, uint8_t lightLevel,
                                    bool isDark, time_t timestamp);
bool firebase_queue_voice_log(int label, float confidence, bool executed, time_t timestamp);
bool firebase_connected();
void firebase_manager_loop();
bool firebase_take_door_command(char* action, size_t actionSize, int64_t* timestampMs,
                                bool* hasTimestamp, char* source, size_t sourceSize,
                                uint32_t* receivedAtMs = nullptr);
void firebase_queue_door_command_result(const char* action, const char* source,
                                        bool accepted, const char* reason);
void firebase_queue_door_event(const char* action, const char* source,
                               const char* who, bool accepted,
                               const char* reason);
bool firebase_take_device_command(uint8_t* device, bool* boolValue,
                                  bool* curtainOpen);
void firebase_queue_device_state(bool light, bool fan, bool garden,
                                 bool door, bool curtain,
                                 bool measureDesiredLatency = false,
                                 uint32_t desiredReceivedMs = 0);
void firebase_queue_auto_log(const char* action, const char* reason,
                             float temperature, uint8_t lightLevel);
