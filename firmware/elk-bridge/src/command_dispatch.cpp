#include "command_dispatch.h"
#include "elk_protocol.h"

void CommandDispatcher::updateStrips(const char* target, std::function<void(BleStrip*)> fn) {
    for (size_t i = 0; i < _ble.getStripCount(); i++) {
        BleStrip* s = _ble.getStrip(i);
        if (!s || s->state != BleStrip::READY) continue;
        if (strcmp(target, "all") == 0 ||
            (strcmp(target, "strip1") == 0 && i == 0) ||
            (strcmp(target, "strip2") == 0 && i == 1) ||
            (strcmp(target, "strip3") == 0 && i == 2)) {
            fn(s);
        }
    }
}

bool CommandDispatcher::handleColor(JsonObject doc, const char* target) {
    if (!doc["r"].is<int>() || !doc["g"].is<int>() || !doc["b"].is<int>()) {
        lastError = "Missing r, g, or b fields";
        return false;
    }

    uint8_t r = doc["r"].as<uint8_t>();
    uint8_t g = doc["g"].as<uint8_t>();
    uint8_t b = doc["b"].as<uint8_t>();

    // Intent color (pre-calibration) for state reporting
    uint8_t ir = doc["ir"] | r;
    uint8_t ig = doc["ig"] | g;
    uint8_t ib = doc["ib"] | b;

    _pattern.stop();

    uint8_t cmd[9];
    buildColorCmd(r, g, b, cmd);
    bool ok = _ble.write(cmd, target);

    if (ok) {
        updateStrips(target, [ir, ig, ib](BleStrip* s) {
            s->lastR = ir; s->lastG = ig; s->lastB = ib;
            s->mode = 0;
        });
    } else {
        lastError = "BLE write failed";
    }

    Serial.printf("[Cmd] Color: (%d, %d, %d) intent=(%d,%d,%d) -> %s = %s\n", r, g, b, ir, ig, ib, target, ok ? "ok" : "fail");
    return ok;
}

bool CommandDispatcher::handleBrightness(JsonObject doc, const char* target) {
    if (!doc["level"].is<int>()) {
        lastError = "Missing level field";
        return false;
    }

    int level = doc["level"].as<int>();
    if (level < 0) level = 0;
    if (level > 100) level = 100;

    // Map 0-100 to 0-255
    uint8_t mapped = (uint8_t)((uint16_t)level * 255 / 100);

    // If pattern is playing, update its steps instead of stopping it
    if (_pattern.isPlaying()) {
        _pattern.setBrightness((uint8_t)level);
    } else {
        _pattern.stop();
    }

    uint8_t cmd[9];
    buildBrightnessCmd(mapped, cmd);
    bool ok = _ble.write(cmd, target);

    if (ok) {
        uint8_t lvl = (uint8_t)level;
        updateStrips(target, [lvl](BleStrip* s) {
            s->lastBrightness = lvl;
        });
    } else {
        lastError = "BLE write failed";
    }

    Serial.printf("[Cmd] Brightness: %d%% (%d) -> %s = %s\n", level, mapped, target, ok ? "ok" : "fail");
    return ok;
}

bool CommandDispatcher::handlePower(JsonObject doc, const char* target) {
    if (!doc["on"].is<bool>()) {
        lastError = "Missing 'on' field (boolean)";
        return false;
    }

    bool on = doc["on"].as<bool>();

    _pattern.stop();

    uint8_t cmd[9];
    buildPowerCmd(on, cmd);
    bool ok = _ble.write(cmd, target);

    if (ok) {
        updateStrips(target, [on](BleStrip* s) {
            s->lastPower = on;
        });
    } else {
        lastError = "BLE write failed";
    }

    Serial.printf("[Cmd] Power: %s -> %s = %s\n", on ? "ON" : "OFF", target, ok ? "ok" : "fail");
    return ok;
}

bool CommandDispatcher::handleEffect(JsonObject doc, const char* target) {
    if (!doc["code"].is<int>()) {
        lastError = "Missing code field";
        return false;
    }

    uint8_t code = doc["code"].as<uint8_t>();
    uint8_t speed = doc["speed"] | 3; // Default speed 3

    if (code < 0x80 || code > 0x9A) {
        lastError = "Effect code must be 0x80-0x9A";
        return false;
    }
    if (speed < 1) speed = 1;
    if (speed > 10) speed = 10;

    _pattern.stop();

    uint8_t cmd[9];
    buildEffectCmd(code, speed, cmd);
    bool ok = _ble.write(cmd, target);

    if (ok) {
        updateStrips(target, [code, speed](BleStrip* s) {
            s->lastEffectCode = code;
            s->lastEffectSpeed = speed;
            s->mode = 1;
        });
    } else {
        lastError = "BLE write failed";
    }

    Serial.printf("[Cmd] Effect: 0x%02X speed=%d -> %s = %s\n", code, speed, target, ok ? "ok" : "fail");
    return ok;
}

bool CommandDispatcher::handleSpeed(JsonObject doc, const char* target) {
    if (!doc["speed"].is<int>()) {
        lastError = "Missing speed field";
        return false;
    }

    int speed = doc["speed"].as<int>();
    if (speed < 1) speed = 1;
    if (speed > 255) speed = 255;

    uint8_t cmd[9];
    buildSpeedCmd((uint8_t)speed, cmd);
    bool ok = _ble.write(cmd, target);

    if (ok) {
        uint8_t sp = (uint8_t)speed;
        updateStrips(target, [sp](BleStrip* s) {
            s->lastEffectSpeed = sp;
        });
    } else {
        lastError = "BLE write failed";
    }

    Serial.printf("[Cmd] Speed: %d -> %s = %s\n", speed, target, ok ? "ok" : "fail");
    return ok;
}

bool CommandDispatcher::handleColorTemp(JsonObject doc, const char* target) {
    if (!doc["warm"].is<int>() || !doc["cold"].is<int>()) {
        lastError = "Missing warm or cold fields";
        return false;
    }

    int warm = doc["warm"].as<int>();
    int cold = doc["cold"].as<int>();
    if (warm < 0) warm = 0;
    if (warm > 100) warm = 100;
    if (cold < 0) cold = 0;
    if (cold > 100) cold = 100;

    _pattern.stop();

    uint8_t cmd[9];
    buildColorTempCmd((uint8_t)warm, (uint8_t)cold, cmd);
    bool ok = _ble.write(cmd, target);

    if (ok) {
        uint8_t w = (uint8_t)warm, c = (uint8_t)cold;
        updateStrips(target, [w, c](BleStrip* s) {
            s->lastWarm = w;
            s->lastCold = c;
            s->mode = 0;
        });
    } else {
        lastError = "BLE write failed";
    }

    Serial.printf("[Cmd] ColorTemp: warm=%d cold=%d -> %s = %s\n", warm, cold, target, ok ? "ok" : "fail");
    return ok;
}

bool CommandDispatcher::handleRaw(JsonObject doc, const char* target) {
    if (!doc["hex"].is<const char*>()) {
        lastError = "Missing hex field";
        return false;
    }

    const char* hex = doc["hex"].as<const char*>();
    uint8_t cmd[9];

    if (!parseHexString(hex, cmd, 9)) {
        lastError = "Invalid hex string (expected 18 hex chars for 9 bytes)";
        return false;
    }

    _pattern.stop();

    bool ok = _ble.write(cmd, target);

    if (!ok) {
        lastError = "BLE write failed";
    }

    Serial.printf("[Cmd] Raw: %s -> %s = %s\n", hex, target, ok ? "ok" : "fail");
    return ok;
}

bool CommandDispatcher::handlePattern(JsonObject doc, const char* target) {
    if (!doc["steps"].is<JsonArray>()) {
        lastError = "Missing steps array";
        return false;
    }

    JsonArray stepsArr = doc["steps"].as<JsonArray>();
    uint8_t count = stepsArr.size();

    if (count == 0) {
        lastError = "Empty steps array";
        return false;
    }
    if (count > PATTERN_MAX_STEPS) {
        lastError = "Too many steps (max " + String(PATTERN_MAX_STEPS) + ")";
        return false;
    }

    bool loop = doc["loop"] | true;

    PatternStep steps[PATTERN_MAX_STEPS];
    int i = 0;
    for (JsonObject stepObj : stepsArr) {
        steps[i].r          = stepObj["r"] | 0;
        steps[i].g          = stepObj["g"] | 0;
        steps[i].b          = stepObj["b"] | 0;
        steps[i].brightness = stepObj["brightness"] | 100;
        steps[i].durationMs = stepObj["duration"] | 1000;

        const char* trans = stepObj["transition"] | "cut";
        if (strcmp(trans, "fade") == 0) {
            steps[i].transition = PatternStep::FADE;
        } else {
            steps[i].transition = PatternStep::CUT;
        }

        steps[i].fadeTimeMs = stepObj["fadeTime"] | 500;
        i++;
    }

    const char* name = doc["name"] | "";
    _pattern.play(steps, count, loop, target, name);

    Serial.printf("[Cmd] Pattern: %d steps, loop=%d, target=%s, name=%s\n", count, loop, target, name);
    return true;
}

bool CommandDispatcher::handleStop(const char* target) {
    _pattern.stop();
    updateStrips(target, [](BleStrip* s) {
        s->mode = 0;
    });
    Serial.printf("[Cmd] Stop -> %s\n", target);
    return true;
}

const char* CommandDispatcher::parseTarget(JsonObject doc) {
    if (doc["target"].is<const char*>()) {
        return doc["target"].as<const char*>();
    }
    return "all";
}

uint8_t CommandDispatcher::hexCharToNibble(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return 10 + c - 'a';
    if (c >= 'A' && c <= 'F') return 10 + c - 'A';
    return 0xFF;
}

bool CommandDispatcher::parseHexString(const char* hex, uint8_t* out, size_t expectedLen) {
    size_t hexLen = strlen(hex);
    if (hexLen != expectedLen * 2) return false;

    for (size_t i = 0; i < expectedLen; i++) {
        uint8_t hi = hexCharToNibble(hex[i * 2]);
        uint8_t lo = hexCharToNibble(hex[i * 2 + 1]);
        if (hi == 0xFF || lo == 0xFF) return false;
        out[i] = (hi << 4) | lo;
    }
    return true;
}
