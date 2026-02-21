#include "status_led.h"

void StatusLed::begin(uint8_t pin) {
    _pin = pin;
    pinMode(_pin, OUTPUT);
    setLed(false);
}

void StatusLed::setPattern(Pattern p) {
    _pattern = p;
    _blinkPhase = 0;
    _lastToggle = millis();

    if (p == SOLID) {
        setLed(true);
    } else if (p == OFF) {
        setLed(false);
    }
}

void StatusLed::setLed(bool on) {
    _ledOn = on;
    // Active-low: LOW = on, HIGH = off
    digitalWrite(_pin, on ? LOW : HIGH);
}

void StatusLed::tick() {
    switch (_pattern) {
        case FAST_BLINK:     tickFastBlink(); break;
        case SLOW_BLINK:     tickSlowBlink(); break;
        case DOUBLE_BLINK:   tickDoubleBlink(); break;
        case TRIPLE_BLINK:   tickTripleBlink(); break;
        case PULSE:          tickPulse(); break;
        case VERY_FAST_BLINK: tickVeryFastBlink(); break;
        case SOLID:          // handled in setPattern
        case OFF:            // handled in setPattern
        default:             break;
    }
}

void StatusLed::tickFastBlink() {
    uint32_t now = millis();
    if (now - _lastToggle >= 100) {
        _lastToggle = now;
        setLed(!_ledOn);
    }
}

void StatusLed::tickSlowBlink() {
    uint32_t now = millis();
    if (now - _lastToggle >= 1000) {
        _lastToggle = now;
        setLed(!_ledOn);
    }
}

void StatusLed::tickVeryFastBlink() {
    uint32_t now = millis();
    if (now - _lastToggle >= 50) {
        _lastToggle = now;
        setLed(!_ledOn);
    }
}

// Double blink: on-100ms-off-100ms-on-100ms-off-1000ms
void StatusLed::tickDoubleBlink() {
    uint32_t now = millis();
    uint32_t elapsed = now - _lastToggle;

    switch (_blinkPhase) {
        case 0: // LED on, first blink
            if (elapsed >= 100) { setLed(false); _blinkPhase = 1; _lastToggle = now; }
            break;
        case 1: // LED off, gap between blinks
            if (elapsed >= 100) { setLed(true); _blinkPhase = 2; _lastToggle = now; }
            break;
        case 2: // LED on, second blink
            if (elapsed >= 100) { setLed(false); _blinkPhase = 3; _lastToggle = now; }
            break;
        case 3: // LED off, long pause
            if (elapsed >= 1000) { setLed(true); _blinkPhase = 0; _lastToggle = now; }
            break;
    }
}

// Triple blink: on-off-on-off-on-off-pause
void StatusLed::tickTripleBlink() {
    uint32_t now = millis();
    uint32_t elapsed = now - _lastToggle;

    switch (_blinkPhase) {
        case 0: // on
            if (elapsed >= 100) { setLed(false); _blinkPhase = 1; _lastToggle = now; }
            break;
        case 1: // off
            if (elapsed >= 100) { setLed(true); _blinkPhase = 2; _lastToggle = now; }
            break;
        case 2: // on
            if (elapsed >= 100) { setLed(false); _blinkPhase = 3; _lastToggle = now; }
            break;
        case 3: // off
            if (elapsed >= 100) { setLed(true); _blinkPhase = 4; _lastToggle = now; }
            break;
        case 4: // on
            if (elapsed >= 100) { setLed(false); _blinkPhase = 5; _lastToggle = now; }
            break;
        case 5: // off, long pause
            if (elapsed >= 2000) { setLed(true); _blinkPhase = 0; _lastToggle = now; }
            break;
    }
}

// Pulse / breathe: ramp brightness using analogWrite if available, else toggle
void StatusLed::tickPulse() {
    uint32_t now = millis();
    // Simple triangle wave over 2000ms period
    uint32_t phase = (now % 2000);
    uint8_t brightness;
    if (phase < 1000) {
        brightness = (uint8_t)(phase * 255 / 1000);
    } else {
        brightness = (uint8_t)((2000 - phase) * 255 / 1000);
    }
    // Active-low: invert duty cycle
    analogWrite(_pin, 255 - brightness);
}
