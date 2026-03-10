#include "http_server.h"
#include "html.h"
#include <time.h>

void HttpServer::begin() {
    // CORS preflight handler for all routes
    _server.on("/*", HTTP_OPTIONS, [this](AsyncWebServerRequest* req) {
        handleOptions(req);
    });

    // Dashboard
    _server.on("/", HTTP_GET, [this](AsyncWebServerRequest* req) {
        handleRoot(req);
    });

    // Status
    _server.on("/status", HTTP_GET, [this](AsyncWebServerRequest* req) {
        handleStatus(req);
    });

    // Command routes
    _server.on("/api/color", HTTP_POST, [this](AsyncWebServerRequest* req) {
        handleColor(req);
    }, NULL, [](AsyncWebServerRequest* req, uint8_t* data, size_t len, size_t index, size_t total) {
        // Body handler: accumulate body into request param "body"
        if (index == 0) req->_tempObject = malloc(total + 1);
        if (req->_tempObject) {
            memcpy((uint8_t*)req->_tempObject + index, data, len);
            if (index + len == total) {
                ((char*)req->_tempObject)[total] = '\0';
            }
        }
    });

    _server.on("/api/brightness", HTTP_POST, [this](AsyncWebServerRequest* req) {
        handleBrightness(req);
    }, NULL, [](AsyncWebServerRequest* req, uint8_t* data, size_t len, size_t index, size_t total) {
        if (index == 0) req->_tempObject = malloc(total + 1);
        if (req->_tempObject) {
            memcpy((uint8_t*)req->_tempObject + index, data, len);
            if (index + len == total) ((char*)req->_tempObject)[total] = '\0';
        }
    });

    _server.on("/api/power", HTTP_POST, [this](AsyncWebServerRequest* req) {
        handlePower(req);
    }, NULL, [](AsyncWebServerRequest* req, uint8_t* data, size_t len, size_t index, size_t total) {
        if (index == 0) req->_tempObject = malloc(total + 1);
        if (req->_tempObject) {
            memcpy((uint8_t*)req->_tempObject + index, data, len);
            if (index + len == total) ((char*)req->_tempObject)[total] = '\0';
        }
    });

    _server.on("/api/effect", HTTP_POST, [this](AsyncWebServerRequest* req) {
        handleEffect(req);
    }, NULL, [](AsyncWebServerRequest* req, uint8_t* data, size_t len, size_t index, size_t total) {
        if (index == 0) req->_tempObject = malloc(total + 1);
        if (req->_tempObject) {
            memcpy((uint8_t*)req->_tempObject + index, data, len);
            if (index + len == total) ((char*)req->_tempObject)[total] = '\0';
        }
    });

    _server.on("/api/speed", HTTP_POST, [this](AsyncWebServerRequest* req) {
        handleSpeed(req);
    }, NULL, [](AsyncWebServerRequest* req, uint8_t* data, size_t len, size_t index, size_t total) {
        if (index == 0) req->_tempObject = malloc(total + 1);
        if (req->_tempObject) {
            memcpy((uint8_t*)req->_tempObject + index, data, len);
            if (index + len == total) ((char*)req->_tempObject)[total] = '\0';
        }
    });

    _server.on("/api/colortemp", HTTP_POST, [this](AsyncWebServerRequest* req) {
        handleColorTemp(req);
    }, NULL, [](AsyncWebServerRequest* req, uint8_t* data, size_t len, size_t index, size_t total) {
        if (index == 0) req->_tempObject = malloc(total + 1);
        if (req->_tempObject) {
            memcpy((uint8_t*)req->_tempObject + index, data, len);
            if (index + len == total) ((char*)req->_tempObject)[total] = '\0';
        }
    });

    _server.on("/api/raw", HTTP_POST, [this](AsyncWebServerRequest* req) {
        handleRaw(req);
    }, NULL, [](AsyncWebServerRequest* req, uint8_t* data, size_t len, size_t index, size_t total) {
        if (index == 0) req->_tempObject = malloc(total + 1);
        if (req->_tempObject) {
            memcpy((uint8_t*)req->_tempObject + index, data, len);
            if (index + len == total) ((char*)req->_tempObject)[total] = '\0';
        }
    });

    _server.on("/api/pattern", HTTP_POST, [this](AsyncWebServerRequest* req) {
        handlePattern(req);
    }, NULL, [](AsyncWebServerRequest* req, uint8_t* data, size_t len, size_t index, size_t total) {
        if (index == 0) req->_tempObject = malloc(total + 1);
        if (req->_tempObject) {
            memcpy((uint8_t*)req->_tempObject + index, data, len);
            if (index + len == total) ((char*)req->_tempObject)[total] = '\0';
        }
    });

    _server.on("/api/stop", HTTP_POST, [this](AsyncWebServerRequest* req) {
        handleStop(req);
    }, NULL, [](AsyncWebServerRequest* req, uint8_t* data, size_t len, size_t index, size_t total) {
        if (index == 0) req->_tempObject = malloc(total + 1);
        if (req->_tempObject) {
            memcpy((uint8_t*)req->_tempObject + index, data, len);
            if (index + len == total) ((char*)req->_tempObject)[total] = '\0';
        }
    });

    _server.on("/api/scan", HTTP_GET, [this](AsyncWebServerRequest* req) {
        handleScan(req);
    });

    _server.on("/api/connect", HTTP_POST, [this](AsyncWebServerRequest* req) {
        handleConnect(req);
    }, NULL, [](AsyncWebServerRequest* req, uint8_t* data, size_t len, size_t index, size_t total) {
        if (index == 0) req->_tempObject = malloc(total + 1);
        if (req->_tempObject) {
            memcpy((uint8_t*)req->_tempObject + index, data, len);
            if (index + len == total) ((char*)req->_tempObject)[total] = '\0';
        }
    });

    _server.on("/api/disconnect", HTTP_POST, [this](AsyncWebServerRequest* req) {
        handleDisconnect(req);
    }, NULL, [](AsyncWebServerRequest* req, uint8_t* data, size_t len, size_t index, size_t total) {
        if (index == 0) req->_tempObject = malloc(total + 1);
        if (req->_tempObject) {
            memcpy((uint8_t*)req->_tempObject + index, data, len);
            if (index + len == total) ((char*)req->_tempObject)[total] = '\0';
        }
    });

    _server.on("/api/devices", HTTP_GET, [this](AsyncWebServerRequest* req) {
        handleDevices(req);
    });

    // User presets
    _server.on("/api/presets", HTTP_GET, [this](AsyncWebServerRequest* req) {
        handleGetPresets(req);
    });

    _server.on("/api/presets", HTTP_POST, [this](AsyncWebServerRequest* req) {
        handleSavePreset(req);
    }, NULL, [](AsyncWebServerRequest* req, uint8_t* data, size_t len, size_t index, size_t total) {
        if (index == 0) req->_tempObject = malloc(total + 1);
        if (req->_tempObject) {
            memcpy((uint8_t*)req->_tempObject + index, data, len);
            if (index + len == total) ((char*)req->_tempObject)[total] = '\0';
        }
    });

    _server.on("/api/presets", HTTP_DELETE, [this](AsyncWebServerRequest* req) {
        handleDeletePreset(req);
    }, NULL, [](AsyncWebServerRequest* req, uint8_t* data, size_t len, size_t index, size_t total) {
        if (index == 0) req->_tempObject = malloc(total + 1);
        if (req->_tempObject) {
            memcpy((uint8_t*)req->_tempObject + index, data, len);
            if (index + len == total) ((char*)req->_tempObject)[total] = '\0';
        }
    });

    // Schedule
    _server.on("/api/schedule", HTTP_GET, [this](AsyncWebServerRequest* req) {
        handleGetSchedule(req);
    });

    _server.on("/api/schedule", HTTP_POST, [this](AsyncWebServerRequest* req) {
        handleSaveSchedule(req);
    }, NULL, [](AsyncWebServerRequest* req, uint8_t* data, size_t len, size_t index, size_t total) {
        if (index == 0) req->_tempObject = malloc(total + 1);
        if (req->_tempObject) {
            memcpy((uint8_t*)req->_tempObject + index, data, len);
            if (index + len == total) ((char*)req->_tempObject)[total] = '\0';
        }
    });

    _server.on("/api/schedule", HTTP_DELETE, [this](AsyncWebServerRequest* req) {
        handleDeleteSchedule(req);
    }, NULL, [](AsyncWebServerRequest* req, uint8_t* data, size_t len, size_t index, size_t total) {
        if (index == 0) req->_tempObject = malloc(total + 1);
        if (req->_tempObject) {
            memcpy((uint8_t*)req->_tempObject + index, data, len);
            if (index + len == total) ((char*)req->_tempObject)[total] = '\0';
        }
    });

    _server.on("/api/config", HTTP_POST, [this](AsyncWebServerRequest* req) {
        handleConfig(req);
    }, NULL, [](AsyncWebServerRequest* req, uint8_t* data, size_t len, size_t index, size_t total) {
        if (index == 0) req->_tempObject = malloc(total + 1);
        if (req->_tempObject) {
            memcpy((uint8_t*)req->_tempObject + index, data, len);
            if (index + len == total) ((char*)req->_tempObject)[total] = '\0';
        }
    });

    // Calibration
    _server.on("/api/calibration", HTTP_GET, [this](AsyncWebServerRequest* req) {
        handleGetCalibration(req);
    });
    _server.on("/api/calibration", HTTP_POST, [this](AsyncWebServerRequest* req) {
        handlePostCalibration(req);
    }, NULL, [](AsyncWebServerRequest* req, uint8_t* data, size_t len, size_t index, size_t total) {
        if (index == 0) req->_tempObject = malloc(total + 1);
        if (req->_tempObject) {
            memcpy((uint8_t*)req->_tempObject + index, data, len);
            if (index + len == total) ((char*)req->_tempObject)[total] = '\0';
        }
    });
    _server.on("/api/calibration", HTTP_DELETE, [this](AsyncWebServerRequest* req) {
        handleDeleteCalibration(req);
    });

    // OTA update
    _server.on("/update", HTTP_POST, [this](AsyncWebServerRequest* req) {
        handleOTAResponse(req);
    }, [this](AsyncWebServerRequest* req, const String& filename, size_t index,
              uint8_t* data, size_t len, bool final) {
        handleOTA(req, filename, index, data, len, final);
    });

    _server.begin();
    Serial.printf("[HTTP] Server started on port %d\n", HTTP_PORT);
}

// --- CORS ---

void HttpServer::addCorsHeaders(AsyncWebServerResponse* response) {
    response->addHeader("Access-Control-Allow-Origin", "*");
    response->addHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
    response->addHeader("Access-Control-Allow-Headers", "Content-Type");
}

void HttpServer::handleOptions(AsyncWebServerRequest* req) {
    AsyncWebServerResponse* response = req->beginResponse(204);
    addCorsHeaders(response);
    req->send(response);
}

// --- Response helpers ---

void HttpServer::sendOk(AsyncWebServerRequest* req, JsonDocument& doc) {
    doc["ok"] = true;
    String body;
    serializeJson(doc, body);
    AsyncWebServerResponse* response = req->beginResponse(200, "application/json", body);
    addCorsHeaders(response);
    req->send(response);
}

void HttpServer::sendOk(AsyncWebServerRequest* req) {
    JsonDocument doc;
    sendOk(req, doc);
}

void HttpServer::sendError(AsyncWebServerRequest* req, int code, const char* msg) {
    JsonDocument doc;
    doc["ok"] = false;
    doc["error"] = msg;
    String body;
    serializeJson(doc, body);
    AsyncWebServerResponse* response = req->beginResponse(code, "application/json", body);
    addCorsHeaders(response);
    req->send(response);
}

bool HttpServer::parseBody(AsyncWebServerRequest* req, JsonDocument& doc) {
    if (!req->_tempObject) {
        sendError(req, 400, "Empty request body");
        return false;
    }

    const char* body = (const char*)req->_tempObject;
    DeserializationError err = deserializeJson(doc, body);

    if (err) {
        String msg = "JSON parse error: " + String(err.c_str());
        sendError(req, 400, msg.c_str());
        free(req->_tempObject);
        req->_tempObject = nullptr;
        return false;
    }

    free(req->_tempObject);
    req->_tempObject = nullptr;
    return true;
}

// --- Route handlers ---

void HttpServer::handleRoot(AsyncWebServerRequest* req) {
    AsyncWebServerResponse* response = req->beginResponse(200, "text/html", HTML_DASHBOARD);
    addCorsHeaders(response);
    req->send(response);
}

void HttpServer::handleStatus(AsyncWebServerRequest* req) {
    JsonDocument doc;
    JsonObject data = doc["data"].to<JsonObject>();

    // WiFi
    JsonObject wifi = data["wifi"].to<JsonObject>();
    switch (_wifi.getState()) {
        case WiFiManager::CONNECTED:    wifi["state"] = "CONNECTED"; break;
        case WiFiManager::AP_MODE:      wifi["state"] = "AP_MODE"; break;
        case WiFiManager::CONNECTING:   wifi["state"] = "CONNECTING"; break;
        default:                        wifi["state"] = "DISCONNECTED"; break;
    }
    wifi["ip"] = _wifi.getIP();
    wifi["rssi"] = _wifi.getRSSI();

    // BLE
    JsonObject ble = data["ble"].to<JsonObject>();
    int connected = 0;
    JsonArray strips = ble["strips"].to<JsonArray>();
    for (size_t i = 0; i < _ble.getStripCount(); i++) {
        BleStrip* s = _ble.getStrip(i);
        if (s) {
            JsonObject stripObj = strips.add<JsonObject>();
            stripObj["address"] = s->address.c_str();
            stripObj["state"] = s->stateStr();
            stripObj["slot"] = "strip" + String(i + 1);
            stripObj["rssi"] = s->rssi;
            String nameKey = "strip" + String(i + 1) + "_name";
            String name = _prefs.getString(nameKey.c_str(), "");
            if (name.length() > 0) {
                stripObj["name"] = name;
            }
            // Per-strip tracked state
            stripObj["r"] = s->lastR;
            stripObj["g"] = s->lastG;
            stripObj["b"] = s->lastB;
            stripObj["brightness"] = s->lastBrightness;
            stripObj["power"] = s->lastPower;
            const char* modeStr = s->mode == 1 ? "effect" : (s->mode == 2 ? "pattern" : "color");
            stripObj["mode"] = modeStr;
            stripObj["effectCode"] = s->lastEffectCode;
            stripObj["effectSpeed"] = s->lastEffectSpeed;
            if (s->state == BleStrip::READY) connected++;
        }
    }
    ble["connected"] = connected;
    ble["total"] = (int)_ble.getStripCount();

    // Pattern
    JsonObject pattern = data["pattern"].to<JsonObject>();
    pattern["state"] = _pattern.stateStr();
    pattern["playing"] = _pattern.isPlaying();
    pattern["currentStep"] = _pattern.getCurrentStep();
    pattern["totalSteps"] = _pattern.getTotalSteps();
    pattern["loop"] = _pattern.getLoop();
    pattern["presetName"] = _pattern.getPresetName();
    pattern["target"] = _pattern.getTarget();

    // Calibration
    JsonObject cal = data["calibration"].to<JsonObject>();
    cal["enabled"] = _cal.enabled;
    cal["points"] = _cal.getCount();

    // Time
    {
        struct tm t;
        time_t now = time(nullptr);
        localtime_r(&now, &t);
        if (t.tm_year > 100) {
            char timeBuf[32];
            strftime(timeBuf, sizeof(timeBuf), "%Y-%m-%d %H:%M:%S", &t);
            data["time"] = timeBuf;
            data["wday"] = t.tm_wday;
        }
        data["tz"] = _prefs.getString("tz_string", DEFAULT_TZ);
    }

    // Firmware
    data["firmware"] = FIRMWARE_VERSION;
    data["uptime"] = millis() / 1000;
    data["freeHeap"] = ESP.getFreeHeap();

    sendOk(req, doc);
}

void HttpServer::handleColor(AsyncWebServerRequest* req) {
    JsonDocument doc;
    if (!parseBody(req, doc)) return;
    JsonObject obj = doc.as<JsonObject>();
    const char* target = CommandDispatcher::parseTarget(obj);
    if (_dispatcher.handleColor(obj, target)) {
        sendOk(req);
    } else {
        sendError(req, 400, _dispatcher.lastError.c_str());
    }
}

void HttpServer::handleBrightness(AsyncWebServerRequest* req) {
    JsonDocument doc;
    if (!parseBody(req, doc)) return;
    JsonObject obj = doc.as<JsonObject>();
    const char* target = CommandDispatcher::parseTarget(obj);
    if (_dispatcher.handleBrightness(obj, target)) {
        sendOk(req);
    } else {
        sendError(req, 400, _dispatcher.lastError.c_str());
    }
}

void HttpServer::handlePower(AsyncWebServerRequest* req) {
    JsonDocument doc;
    if (!parseBody(req, doc)) return;
    JsonObject obj = doc.as<JsonObject>();
    const char* target = CommandDispatcher::parseTarget(obj);
    if (_dispatcher.handlePower(obj, target)) {
        sendOk(req);
    } else {
        sendError(req, 400, _dispatcher.lastError.c_str());
    }
}

void HttpServer::handleEffect(AsyncWebServerRequest* req) {
    JsonDocument doc;
    if (!parseBody(req, doc)) return;
    JsonObject obj = doc.as<JsonObject>();
    const char* target = CommandDispatcher::parseTarget(obj);
    if (_dispatcher.handleEffect(obj, target)) {
        sendOk(req);
    } else {
        sendError(req, 400, _dispatcher.lastError.c_str());
    }
}

void HttpServer::handleSpeed(AsyncWebServerRequest* req) {
    JsonDocument doc;
    if (!parseBody(req, doc)) return;
    JsonObject obj = doc.as<JsonObject>();
    const char* target = CommandDispatcher::parseTarget(obj);
    if (_dispatcher.handleSpeed(obj, target)) {
        sendOk(req);
    } else {
        sendError(req, 400, _dispatcher.lastError.c_str());
    }
}

void HttpServer::handleColorTemp(AsyncWebServerRequest* req) {
    JsonDocument doc;
    if (!parseBody(req, doc)) return;
    JsonObject obj = doc.as<JsonObject>();
    const char* target = CommandDispatcher::parseTarget(obj);
    if (_dispatcher.handleColorTemp(obj, target)) {
        sendOk(req);
    } else {
        sendError(req, 400, _dispatcher.lastError.c_str());
    }
}

void HttpServer::handleRaw(AsyncWebServerRequest* req) {
    JsonDocument doc;
    if (!parseBody(req, doc)) return;
    JsonObject obj = doc.as<JsonObject>();
    const char* target = CommandDispatcher::parseTarget(obj);
    if (_dispatcher.handleRaw(obj, target)) {
        sendOk(req);
    } else {
        sendError(req, 400, _dispatcher.lastError.c_str());
    }
}

void HttpServer::handlePattern(AsyncWebServerRequest* req) {
    JsonDocument doc;
    if (!parseBody(req, doc)) return;
    JsonObject obj = doc.as<JsonObject>();
    const char* target = CommandDispatcher::parseTarget(obj);
    if (_dispatcher.handlePattern(obj, target)) {
        sendOk(req);
    } else {
        sendError(req, 400, _dispatcher.lastError.c_str());
    }
}

void HttpServer::handleStop(AsyncWebServerRequest* req) {
    // Stop doesn't need a body, but accept one for target
    String targetStr = "all";
    if (req->_tempObject) {
        JsonDocument doc;
        const char* body = (const char*)req->_tempObject;
        if (deserializeJson(doc, body) == DeserializationError::Ok) {
            JsonObject obj = doc.as<JsonObject>();
            targetStr = CommandDispatcher::parseTarget(obj);
        }
        free(req->_tempObject);
        req->_tempObject = nullptr;
    }
    _dispatcher.handleStop(targetStr.c_str());
    sendOk(req);
}

void HttpServer::handleScan(AsyncWebServerRequest* req) {
    auto state = _ble.scanState();

    if (state == BleManager::ASYNC_DONE) {
        // Results ready — return them and reset
        JsonDocument doc;
        JsonObject data = doc["data"].to<JsonObject>();
        data["scanning"] = false;
        JsonArray devices = data["devices"].to<JsonArray>();
        for (const auto& r : _ble.scanResults()) {
            JsonObject dev = devices.add<JsonObject>();
            dev["name"] = r.name.c_str();
            dev["address"] = r.address.c_str();
            dev["rssi"] = r.rssi;
        }
        _ble.clearScanResults();
        sendOk(req, doc);
    } else {
        // Start scan if idle, or report in-progress
        if (state == BleManager::ASYNC_IDLE) {
            _ble.requestScan();
        }
        JsonDocument doc;
        doc["data"]["scanning"] = true;
        doc["data"]["devices"].to<JsonArray>();
        sendOk(req, doc);
    }
}

void HttpServer::handleConnect(AsyncWebServerRequest* req) {
    JsonDocument doc;
    if (!parseBody(req, doc)) return;

    if (!doc["address"].is<const char*>()) {
        sendError(req, 400, "Missing address field");
        return;
    }

    const char* address = doc["address"].as<const char*>();

    // Check if already connected
    int existing = _ble.findStrip(address);
    if (existing >= 0) {
        BleStrip* s = _ble.getStrip(existing);
        if (s && s->state == BleStrip::READY) {
            sendOk(req);
            return;
        }
    }

    // Look up address type from last scan results
    uint8_t addrType = 0;
    for (const auto& sr : _ble.scanResults()) {
        if (strcasecmp(sr.address.c_str(), address) == 0) {
            addrType = sr.addrType;
            break;
        }
    }

    // Start async connect — returns immediately, client polls /status
    _ble.requestConnect(address, addrType);
    JsonDocument resp;
    resp["data"]["connecting"] = true;
    sendOk(req, resp);
}

void HttpServer::handleDisconnect(AsyncWebServerRequest* req) {
    JsonDocument doc;
    if (!parseBody(req, doc)) return;

    if (!doc["address"].is<const char*>()) {
        sendError(req, 400, "Missing address field");
        return;
    }

    const char* address = doc["address"].as<const char*>();

    // Remove from NVS
    int idx = _ble.findStrip(address);
    if (idx >= 0) {
        String key = "strip" + String(idx + 1) + "_addr";
        _prefs.remove(key.c_str());
    }

    if (_ble.disconnect(address)) {
        sendOk(req);
    } else {
        sendError(req, 404, "Device not found");
    }
}

void HttpServer::handleDevices(AsyncWebServerRequest* req) {
    JsonDocument doc;
    JsonObject data = doc["data"].to<JsonObject>();
    JsonArray strips = data["strips"].to<JsonArray>();

    for (size_t i = 0; i < _ble.getStripCount(); i++) {
        BleStrip* s = _ble.getStrip(i);
        if (s) {
            JsonObject obj = strips.add<JsonObject>();
            obj["address"] = s->address.c_str();
            obj["state"] = s->stateStr();
            obj["slot"] = "strip" + String(i + 1);
        }
    }

    data["count"] = (int)_ble.getStripCount();
    sendOk(req, doc);
}

// --- User Presets (NVS) ---
// Keys: up0_n/up0_d .. up7_n/up7_d, up_cnt
// Binary per-step: r(1) g(1) b(1) br(1) durMs(2 LE) tr(1) fadeMs(2 LE) = 9 bytes

#define USER_PRESET_MAX 8

void HttpServer::handleGetPresets(AsyncWebServerRequest* req) {
    JsonDocument doc;
    JsonArray arr = doc["data"].to<JsonArray>();

    uint8_t count = _prefs.getUChar("up_cnt", 0);
    for (uint8_t i = 0; i < count && i < USER_PRESET_MAX; i++) {
        char nk[8], dk[8];
        snprintf(nk, sizeof(nk), "up%d_n", i);
        snprintf(dk, sizeof(dk), "up%d_d", i);

        String name = _prefs.getString(nk, "");
        if (name.length() == 0) continue;

        uint8_t buf[512];
        size_t len = _prefs.getBytes(dk, buf, sizeof(buf));
        if (len < 1) continue;

        uint8_t stepCount = buf[0];
        if (stepCount == 0 || 1 + stepCount * 9 > len) continue;

        JsonObject preset = arr.add<JsonObject>();
        preset["name"] = name;
        JsonArray steps = preset["steps"].to<JsonArray>();

        size_t pos = 1;
        for (uint8_t s = 0; s < stepCount; s++) {
            JsonObject step = steps.add<JsonObject>();
            step["r"] = buf[pos]; step["g"] = buf[pos+1]; step["b"] = buf[pos+2];
            step["br"] = buf[pos+3];
            step["dur"] = (uint16_t)(buf[pos+4] | (buf[pos+5] << 8));
            step["tr"] = buf[pos+6] ? "fade" : "cut";
            step["fade"] = (uint16_t)(buf[pos+7] | (buf[pos+8] << 8));
            pos += 9;
        }
    }

    sendOk(req, doc);
}

void HttpServer::handleSavePreset(AsyncWebServerRequest* req) {
    JsonDocument doc;
    if (!parseBody(req, doc)) return;

    if (!doc["name"].is<const char*>() || !doc["steps"].is<JsonArray>()) {
        sendError(req, 400, "Missing name or steps");
        return;
    }

    const char* name = doc["name"].as<const char*>();
    JsonArray steps = doc["steps"].as<JsonArray>();
    uint8_t stepCount = steps.size();

    if (stepCount == 0 || stepCount > PATTERN_MAX_STEPS) {
        sendError(req, 400, "Invalid step count");
        return;
    }

    // Check if preset with this name already exists (overwrite)
    uint8_t count = _prefs.getUChar("up_cnt", 0);
    int slot = -1;
    for (uint8_t i = 0; i < count && i < USER_PRESET_MAX; i++) {
        char nk[8];
        snprintf(nk, sizeof(nk), "up%d_n", i);
        String existing = _prefs.getString(nk, "");
        if (existing == name) { slot = i; break; }
    }

    if (slot < 0) {
        if (count >= USER_PRESET_MAX) {
            sendError(req, 400, "Max 8 user presets reached");
            return;
        }
        slot = count;
    }

    // Encode binary
    uint8_t buf[1 + PATTERN_MAX_STEPS * 9];
    buf[0] = stepCount;
    size_t pos = 1;
    for (JsonObject s : steps) {
        buf[pos++] = s["r"] | 0;
        buf[pos++] = s["g"] | 0;
        buf[pos++] = s["b"] | 0;
        buf[pos++] = s["br"] | 0;
        uint16_t dur = s["dur"] | 0;
        buf[pos++] = dur & 0xFF;
        buf[pos++] = (dur >> 8) & 0xFF;
        const char* tr = s["tr"] | "cut";
        buf[pos++] = (strcmp(tr, "fade") == 0) ? 1 : 0;
        uint16_t fade = s["fade"] | 0;
        buf[pos++] = fade & 0xFF;
        buf[pos++] = (fade >> 8) & 0xFF;
    }

    char nk[8], dk[8];
    snprintf(nk, sizeof(nk), "up%d_n", slot);
    snprintf(dk, sizeof(dk), "up%d_d", slot);
    _prefs.putString(nk, name);
    _prefs.putBytes(dk, buf, pos);

    if (slot >= count) {
        _prefs.putUChar("up_cnt", slot + 1);
    }

    Serial.printf("[Presets] Saved '%s' (%d steps) to slot %d\n", name, stepCount, slot);
    sendOk(req);
}

void HttpServer::handleDeletePreset(AsyncWebServerRequest* req) {
    JsonDocument doc;
    if (!parseBody(req, doc)) return;

    if (!doc["name"].is<const char*>()) {
        sendError(req, 400, "Missing name");
        return;
    }

    const char* name = doc["name"].as<const char*>();
    uint8_t count = _prefs.getUChar("up_cnt", 0);
    int slot = -1;

    for (uint8_t i = 0; i < count && i < USER_PRESET_MAX; i++) {
        char nk[8];
        snprintf(nk, sizeof(nk), "up%d_n", i);
        String existing = _prefs.getString(nk, "");
        if (existing == name) { slot = i; break; }
    }

    if (slot < 0) {
        sendError(req, 404, "Preset not found");
        return;
    }

    // Shift remaining presets down
    for (uint8_t i = slot; i < count - 1; i++) {
        char srcN[8], srcD[8], dstN[8], dstD[8];
        snprintf(srcN, sizeof(srcN), "up%d_n", i + 1);
        snprintf(srcD, sizeof(srcD), "up%d_d", i + 1);
        snprintf(dstN, sizeof(dstN), "up%d_n", i);
        snprintf(dstD, sizeof(dstD), "up%d_d", i);

        String n = _prefs.getString(srcN, "");
        _prefs.putString(dstN, n.c_str());

        uint8_t buf[512];
        size_t len = _prefs.getBytes(srcD, buf, sizeof(buf));
        if (len > 0) _prefs.putBytes(dstD, buf, len);
    }

    // Remove last slot
    char lastN[8], lastD[8];
    snprintf(lastN, sizeof(lastN), "up%d_n", count - 1);
    snprintf(lastD, sizeof(lastD), "up%d_d", count - 1);
    _prefs.remove(lastN);
    _prefs.remove(lastD);
    _prefs.putUChar("up_cnt", count - 1);

    Serial.printf("[Presets] Deleted '%s' from slot %d\n", name, slot);
    sendOk(req);
}

// --- Schedule API ---

void HttpServer::handleGetSchedule(AsyncWebServerRequest* req) {
    JsonDocument doc;
    JsonObject data = doc["data"].to<JsonObject>();
    data["timeValid"] = _scheduler.isTimeValid();

    // Report current time
    struct tm t;
    time_t now = time(nullptr);
    localtime_r(&now, &t);
    if (t.tm_year > 100) {
        char buf[20];
        snprintf(buf, sizeof(buf), "%04d-%02d-%02d %02d:%02d",
                 t.tm_year + 1900, t.tm_mon + 1, t.tm_mday, t.tm_hour, t.tm_min);
        data["time"] = buf;
        data["wday"] = t.tm_wday;
    }

    JsonArray arr = data["entries"].to<JsonArray>();
    for (uint8_t i = 0; i < _scheduler.getEntryCount(); i++) {
        const ScheduleEntry* e = _scheduler.getEntry(i);
        if (!e) continue;
        JsonObject obj = arr.add<JsonObject>();
        obj["enabled"] = e->enabled;
        obj["days"] = e->days;
        obj["startHour"] = e->startHour;
        obj["startMin"] = e->startMin;
        obj["endHour"] = e->endHour;
        obj["endMin"] = e->endMin;
        obj["pattern"] = e->pattern;
    }
    sendOk(req, doc);
}

void HttpServer::handleSaveSchedule(AsyncWebServerRequest* req) {
    JsonDocument doc;
    if (!parseBody(req, doc)) return;

    ScheduleEntry entry;
    entry.enabled   = doc["enabled"] | true;
    entry.days      = doc["days"] | 0;
    entry.startHour = doc["startHour"] | 0;
    entry.startMin  = doc["startMin"] | 0;
    entry.endHour   = doc["endHour"] | 0;
    entry.endMin    = doc["endMin"] | 0;

    const char* pat = doc["pattern"] | "*";
    strncpy(entry.pattern, pat, sizeof(entry.pattern) - 1);
    entry.pattern[sizeof(entry.pattern) - 1] = '\0';

    if (doc["index"].is<int>()) {
        int idx = doc["index"].as<int>();
        if (!_scheduler.updateEntry(idx, entry)) {
            sendError(req, 400, "Invalid index");
            return;
        }
    } else {
        if (!_scheduler.addEntry(entry)) {
            sendError(req, 400, "Max 16 schedule entries reached");
            return;
        }
    }
    sendOk(req);
}

void HttpServer::handleDeleteSchedule(AsyncWebServerRequest* req) {
    JsonDocument doc;
    if (!parseBody(req, doc)) return;

    if (!doc["index"].is<int>()) {
        sendError(req, 400, "Missing index");
        return;
    }

    int idx = doc["index"].as<int>();
    if (!_scheduler.removeEntry(idx)) {
        sendError(req, 404, "Entry not found");
        return;
    }
    sendOk(req);
}

void HttpServer::handleConfig(AsyncWebServerRequest* req) {
    JsonDocument doc;
    if (!parseBody(req, doc)) return;

    bool needsReboot = false;
    bool changed = false;

    if (doc["wifi_ssid"].is<const char*>()) {
        _prefs.putString("wifi_ssid", doc["wifi_ssid"].as<const char*>());
        needsReboot = true;
        changed = true;
    }
    if (doc["wifi_pass"].is<const char*>()) {
        _prefs.putString("wifi_pass", doc["wifi_pass"].as<const char*>());
        needsReboot = true;
        changed = true;
    }
    if (doc["strip1_addr"].is<const char*>()) {
        _prefs.putString("strip1_addr", doc["strip1_addr"].as<const char*>());
        needsReboot = true;
        changed = true;
    }
    if (doc["strip2_addr"].is<const char*>()) {
        _prefs.putString("strip2_addr", doc["strip2_addr"].as<const char*>());
        needsReboot = true;
        changed = true;
    }

    // Timezone (no reboot needed)
    if (doc["tz"].is<const char*>()) {
        const char* tz = doc["tz"].as<const char*>();
        _prefs.putString("tz_string", tz);
        setenv("TZ", tz, 1);
        tzset();
        configTime(0, 0, "pool.ntp.org", "time.nist.gov");
        Serial.printf("[Config] Timezone set to: %s\n", tz);
        changed = true;
    }

    // Strip names don't require reboot
    if (doc["strip1_name"].is<const char*>()) {
        _prefs.putString("strip1_name", doc["strip1_name"].as<const char*>());
        changed = true;
    }
    if (doc["strip2_name"].is<const char*>()) {
        _prefs.putString("strip2_name", doc["strip2_name"].as<const char*>());
        changed = true;
    }

    if (!changed) {
        sendError(req, 400, "No config fields provided");
        return;
    }

    if (needsReboot) {
        JsonDocument resp;
        resp["data"]["message"] = "Config saved. Rebooting in 2 seconds...";
        sendOk(req, resp);
        delay(2000);
        ESP.restart();
    } else {
        JsonDocument resp;
        resp["data"]["message"] = "Config saved.";
        sendOk(req, resp);
    }
}

// --- Calibration ---

void HttpServer::handleGetCalibration(AsyncWebServerRequest* req) {
    JsonDocument doc;
    doc["ok"] = true;
    doc["data"]["enabled"] = _cal.enabled;
    doc["data"]["count"] = _cal.getCount();

    JsonArray arr = doc["data"]["points"].to<JsonArray>();
    const CalPoint* pts = _cal.getPoints();
    for (uint8_t i = 0; i < _cal.getCount(); i++) {
        JsonObject pt = arr.add<JsonObject>();
        pt["deg"] = pts[i].deg;
        pt["brightness"] = pts[i].brightness;
        JsonArray std = pt["standard"].to<JsonArray>();
        std.add(pts[i].stdR); std.add(pts[i].stdG); std.add(pts[i].stdB);
        JsonArray cal = pt["strip_input"].to<JsonArray>();
        cal.add(pts[i].calR); cal.add(pts[i].calG); cal.add(pts[i].calB);
    }

    sendOk(req, doc);
}

void HttpServer::handlePostCalibration(AsyncWebServerRequest* req) {
    JsonDocument doc;
    if (!parseBody(req, doc)) return;

    if (!doc["points"].is<JsonArray>()) {
        sendError(req, 400, "Missing points array");
        return;
    }

    JsonArray arr = doc["points"].as<JsonArray>();
    uint8_t count = arr.size();
    if (count > CAL_MAX_POINTS) {
        sendError(req, 400, "Too many points (max 120)");
        return;
    }

    CalPoint pts[CAL_MAX_POINTS];
    int i = 0;
    for (JsonObject pt : arr) {
        pts[i].deg = pt["deg"] | 0;
        pts[i].brightness = pt["brightness"] | 100;
        JsonArray std = pt["standard"].as<JsonArray>();
        JsonArray cal = pt["strip_input"].as<JsonArray>();
        if (std.size() >= 3 && cal.size() >= 3) {
            pts[i].stdR = std[0]; pts[i].stdG = std[1]; pts[i].stdB = std[2];
            pts[i].calR = cal[0]; pts[i].calG = cal[1]; pts[i].calB = cal[2];
        }
        i++;
    }

    if (!_cal.load(pts, count)) {
        sendError(req, 400, "Failed to load calibration");
        return;
    }

    if (doc["enabled"].is<bool>()) {
        _cal.enabled = doc["enabled"].as<bool>();
    }

    _cal.saveToNVS();

    JsonDocument resp;
    resp["ok"] = true;
    resp["data"]["message"] = "Calibration saved";
    resp["data"]["count"] = count;
    sendOk(req, resp);
}

void HttpServer::handleDeleteCalibration(AsyncWebServerRequest* req) {
    _cal.clear();
    sendOk(req);
}

void HttpServer::handleOTA(AsyncWebServerRequest* req, const String& filename,
                           size_t index, uint8_t* data, size_t len, bool final) {
    if (index == 0) {
        Serial.printf("[OTA] Begin: %s\n", filename.c_str());
        if (!Update.begin(UPDATE_SIZE_UNKNOWN)) {
            Update.printError(Serial);
        }
    }

    if (Update.isRunning()) {
        if (Update.write(data, len) != len) {
            Update.printError(Serial);
        }
    }

    if (final) {
        if (Update.end(true)) {
            Serial.printf("[OTA] Success! %u bytes written\n", index + len);
        } else {
            Update.printError(Serial);
        }
    }
}

void HttpServer::handleOTAResponse(AsyncWebServerRequest* req) {
    JsonDocument doc;
    if (Update.hasError()) {
        doc["ok"] = false;
        doc["error"] = "OTA update failed";
        String body;
        serializeJson(doc, body);
        AsyncWebServerResponse* response = req->beginResponse(500, "application/json", body);
        addCorsHeaders(response);
        req->send(response);
    } else {
        doc["ok"] = true;
        doc["data"]["message"] = "Update successful. Rebooting...";
        String body;
        serializeJson(doc, body);
        AsyncWebServerResponse* response = req->beginResponse(200, "application/json", body);
        addCorsHeaders(response);
        req->send(response);
        delay(1000);
        ESP.restart();
    }
}
