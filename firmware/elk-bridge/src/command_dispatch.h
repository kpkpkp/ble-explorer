#pragma once

#include <Arduino.h>
#include <ArduinoJson.h>
#include <functional>
#include "ble_manager.h"
#include "pattern_engine.h"

class CommandDispatcher {
public:
    CommandDispatcher(BleManager& ble, PatternEngine& pattern)
        : _ble(ble), _pattern(pattern) {}

    bool handleColor(JsonObject doc, const char* target);
    bool handleBrightness(JsonObject doc, const char* target);
    bool handlePower(JsonObject doc, const char* target);
    bool handleEffect(JsonObject doc, const char* target);
    bool handleSpeed(JsonObject doc, const char* target);
    bool handleColorTemp(JsonObject doc, const char* target);
    bool handleRaw(JsonObject doc, const char* target);
    bool handlePattern(JsonObject doc, const char* target);
    bool handleStop(const char* target);

    static const char* parseTarget(JsonObject doc);

    String lastError;

private:
    BleManager&    _ble;
    PatternEngine& _pattern;

    void updateStrips(const char* target, std::function<void(BleStrip*)> fn);
    uint8_t hexCharToNibble(char c);
    bool parseHexString(const char* hex, uint8_t* out, size_t expectedLen);
};
