#include "pattern_engine.h"
#include "elk_protocol.h"

void PatternEngine::play(PatternStep* steps, uint8_t count, bool loop, const char* target) {
    if (count == 0 || count > PATTERN_MAX_STEPS) return;

    _stepCount = count;
    _loop = loop;
    _currentStep = 0;

    memcpy(_steps, steps, sizeof(PatternStep) * count);

    if (target) {
        strncpy(_target, target, sizeof(_target) - 1);
        _target[sizeof(_target) - 1] = '\0';
    } else {
        strcpy(_target, "all");
    }

    // Initialize previous color to black
    _prevR = 0; _prevG = 0; _prevB = 0; _prevBr = 0;

    Serial.printf("[Pattern] Playing %d steps, loop=%d, target=%s\n", count, loop, _target);

    // Don't call startStep here -- tick() will handle it on the next call
    // by detecting state transition
    _state = IDLE; // Momentarily idle, tick() will start first step
    _state = HOLD; // Force immediate start
    _stepStartMs = millis();
    _fadeStartMs = millis();
    _lastFadeSend = 0;

    // Actually start the first step immediately
    // We need a BleManager reference, so we set up state and let the first
    // tick() call handle the initial send. Use a flag approach instead.
    _currentStep = 0;

    // Store target color
    _currR = _steps[0].r;
    _currG = _steps[0].g;
    _currB = _steps[0].b;
    _currBr = _steps[0].brightness;

    if (_steps[0].transition == PatternStep::FADE && _steps[0].fadeTimeMs > 0) {
        _state = FADING;
        _fadeStartMs = millis();
        _lastFadeSend = 0;
    } else {
        _state = HOLD;
        _stepStartMs = millis();
        // The actual BLE write will happen on the first tick()
        _lastFadeSend = 0; // Signal that we need initial send
    }
}

void PatternEngine::stop() {
    _state = IDLE;
    _stepCount = 0;
    _currentStep = 0;
    Serial.println("[Pattern] Stopped");
}

void PatternEngine::tick(BleManager& ble) {
    if (_state == IDLE) return;

    uint32_t now = millis();

    if (_state == HOLD) {
        // Send initial color on first tick after entering HOLD
        if (_lastFadeSend == 0) {
            sendColor(ble, _currR, _currG, _currB, _currBr);
            _lastFadeSend = now;
            _stepStartMs = now;
        }

        // Check if hold duration has elapsed
        if (now - _stepStartMs >= _steps[_currentStep].durationMs) {
            // Save current color as previous for next step's fade
            _prevR = _currR;
            _prevG = _currG;
            _prevB = _currB;
            _prevBr = _currBr;

            // Advance to next step
            _currentStep++;
            if (_currentStep >= _stepCount) {
                if (_loop) {
                    _currentStep = 0;
                } else {
                    _state = IDLE;
                    Serial.println("[Pattern] Playback complete");
                    return;
                }
            }

            startStep(ble);
        }
    } else if (_state == FADING) {
        // Calculate interpolation progress
        uint32_t elapsed = now - _fadeStartMs;
        uint16_t fadeTime = _steps[_currentStep].fadeTimeMs;

        if (fadeTime == 0) fadeTime = 1; // Avoid division by zero

        float t = (float)elapsed / (float)fadeTime;
        if (t > 1.0f) t = 1.0f;

        // Only send at FADE_INTERVAL_MS intervals
        if (now - _lastFadeSend >= FADE_INTERVAL_MS) {
            uint8_t r  = lerp8(_prevR, _currR, t);
            uint8_t g  = lerp8(_prevG, _currG, t);
            uint8_t b  = lerp8(_prevB, _currB, t);
            uint8_t br = lerp8(_prevBr, _currBr, t);
            sendColor(ble, r, g, b, br);
            _lastFadeSend = now;
        }

        // Fade complete?
        if (t >= 1.0f) {
            // Transition to HOLD for remaining duration
            _state = HOLD;
            _stepStartMs = now;
            _lastFadeSend = 1; // Non-zero so we don't re-send on HOLD entry
        }
    }
}

void PatternEngine::startStep(BleManager& ble) {
    PatternStep& step = _steps[_currentStep];

    _currR = step.r;
    _currG = step.g;
    _currB = step.b;
    _currBr = step.brightness;

    if (step.transition == PatternStep::FADE && step.fadeTimeMs > 0) {
        _state = FADING;
        _fadeStartMs = millis();
        _lastFadeSend = 0;
    } else {
        // CUT: send immediately, then hold
        _state = HOLD;
        _lastFadeSend = 0; // Signal initial send needed
        _stepStartMs = millis();
    }
}

void PatternEngine::sendColor(BleManager& ble, uint8_t r, uint8_t g, uint8_t b, uint8_t brightness) {
    uint8_t cmd[9];

    // Send color command
    buildColorCmd(r, g, b, cmd);
    ble.write(cmd, _target);

    // Send brightness command (map 0-100 to 0-255)
    uint8_t mappedBr = (uint8_t)((uint16_t)brightness * 255 / 100);
    buildBrightnessCmd(mappedBr, cmd);
    ble.write(cmd, _target);
}

uint8_t PatternEngine::lerp8(uint8_t a, uint8_t b, float t) {
    return (uint8_t)((float)a + ((float)b - (float)a) * t);
}
