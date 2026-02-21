#pragma once

#include <Arduino.h>

class StatusLed {
public:
    enum Pattern {
        OFF,
        FAST_BLINK,     // Connecting (100ms on/off)
        SLOW_BLINK,     // WiFi only, no BLE (1s on/off)
        SOLID,          // Ready (WiFi + BLE)
        DOUBLE_BLINK,   // AP mode (2 quick, 1s pause)
        PULSE,          // Pattern playing (breathe)
        TRIPLE_BLINK,   // Error (3 quick, 2s pause)
        VERY_FAST_BLINK // OTA in progress (50ms on/off)
    };

    void begin(uint8_t pin);
    void tick();
    void setPattern(Pattern p);
    Pattern getPattern() const { return _pattern; }

private:
    uint8_t  _pin = 0;
    Pattern  _pattern = OFF;
    uint32_t _lastToggle = 0;
    uint8_t  _blinkPhase = 0;
    bool     _ledOn = false;

    void setLed(bool on);
    void tickFastBlink();
    void tickSlowBlink();
    void tickDoubleBlink();
    void tickTripleBlink();
    void tickPulse();
    void tickVeryFastBlink();
};
