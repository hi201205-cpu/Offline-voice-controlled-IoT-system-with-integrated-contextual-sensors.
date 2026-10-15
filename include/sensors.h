#pragma once

#include <Arduino.h>

void sensorsInit();
void sensorsUpdate();
void sensorsPush();

float sensorsTemperature();
float sensorsHumidity();
bool sensorsDataValid();
bool sensorsPresenceDetected();
bool sensorsPresenceDataValid();
uint8_t sensorsPresenceState();
uint16_t sensorsPresenceDistanceCm();
uint8_t sensorsLightLevel();
bool sensorsLightValid();
void sensorsRequestPush();
