#pragma once

#include <Arduino.h>
#include <Preferences.h>
#include "config.h"
#include "ble_manager.h"

class ColorCal;  // forward declaration

struct PatternStep {
    uint8_t  r;
    uint8_t  g;
    uint8_t  b;
    uint8_t  brightness;   // 0-100
    uint16_t durationMs;
    enum Transition { CUT, FADE } transition;
    uint16_t fadeTimeMs;
};

class PatternEngine {
public:
    enum State {
        IDLE,
        HOLD,
        FADING
    };

    void begin(Preferences& prefs, ColorCal* cal = nullptr);
    void play(PatternStep* steps, uint8_t count, bool loop, const char* target = "all", const char* presetName = "");
    void stop();
    void tick(BleManager& ble);

    void saveToNVS();
    void loadFromNVS(BleManager& ble);
    void clearNVS();

    bool    isPlaying() const { return _state != IDLE; }
    uint8_t getCurrentStep() const { return _currentStep; }
    uint8_t getTotalSteps() const { return _stepCount; }
    State   getState() const { return _state; }
    const char* getTarget() const { return _target; }
    const char* getPresetName() const { return _presetName; }
    bool        getLoop() const { return _loop; }

    // Update brightness on all steps without stopping playback
    void setBrightness(uint8_t brightness) {
        for (uint8_t i = 0; i < _stepCount; i++) {
            _steps[i].brightness = brightness;
        }
        _currBr = brightness;
    }

    const char* stateStr() const {
        switch (_state) {
            case IDLE:   return "idle";
            case HOLD:   return "hold";
            case FADING: return "fading";
            default:     return "unknown";
        }
    }

private:
    Preferences* _prefs = nullptr;
    BleManager*  _ble = nullptr;
    ColorCal*    _cal = nullptr;
    PatternStep _steps[PATTERN_MAX_STEPS];
    uint8_t     _stepCount = 0;
    uint8_t     _currentStep = 0;
    bool        _loop = false;
    State       _state = IDLE;
    char        _target[32] = "all";
    char        _presetName[64] = "";

    uint32_t    _stepStartMs = 0;
    uint32_t    _fadeStartMs = 0;
    uint32_t    _lastFadeSend = 0;

    // Previous color for fade interpolation
    uint8_t     _prevR = 0, _prevG = 0, _prevB = 0, _prevBr = 0;
    // Current target color
    uint8_t     _currR = 0, _currG = 0, _currB = 0, _currBr = 0;

    uint8_t     _fadeBrSent = 255;  // track brightness during fade to skip redundant writes

    void startStep(BleManager& ble);
    void sendColor(BleManager& ble, uint8_t r, uint8_t g, uint8_t b, uint8_t brightness);
    void sendColorFade(BleManager& ble, uint8_t r, uint8_t g, uint8_t b, uint8_t brightness);
    uint8_t lerp8(uint8_t a, uint8_t b, float t);
};
