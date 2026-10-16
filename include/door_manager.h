#pragma once

#include <Arduino.h>
#include <stddef.h>

struct DoorPasscodeMetadata {
    char id[49];
    char name[81];
};

void doorInit();
void doorUpdate();
size_t doorPasscodeCount();
bool doorPasscodeGet(size_t index, DoorPasscodeMetadata* metadata);
bool doorPasscodeAdd(const char* id, const char* name, const char* code,
                     char* error, size_t errorSize);
bool doorPasscodeDelete(const char* id, char* error, size_t errorSize);
bool doorControl(bool open, const char* source = "automation");

