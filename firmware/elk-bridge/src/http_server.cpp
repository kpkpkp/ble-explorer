#include "http_server.h"
#include "html.h"

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

    _server.on("/api/config", HTTP_POST, [this](AsyncWebServerRequest* req) {
        handleConfig(req);
    }, NULL, [](AsyncWebServerRequest* req, uint8_t* data, size_t len, size_t index, size_t total) {
        if (index == 0) req->_tempObject = malloc(total + 1);
        if (req->_tempObject) {
            memcpy((uint8_t*)req->_tempObject + index, data, len);
            if (index + len == total) ((char*)req->_tempObject)[total] = '\0';
        }
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
    response->addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
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
    std::vector<ScanResult> results;
    _ble.scan(results);

    JsonDocument doc;
    JsonObject data = doc["data"].to<JsonObject>();
    JsonArray devices = data["devices"].to<JsonArray>();

    for (const auto& r : results) {
        JsonObject dev = devices.add<JsonObject>();
        dev["name"] = r.name.c_str();
        dev["address"] = r.address.c_str();
        dev["rssi"] = r.rssi;
    }

    sendOk(req, doc);
}

void HttpServer::handleConnect(AsyncWebServerRequest* req) {
    JsonDocument doc;
    if (!parseBody(req, doc)) return;

    if (!doc["address"].is<const char*>()) {
        sendError(req, 400, "Missing address field");
        return;
    }

    const char* address = doc["address"].as<const char*>();
    if (_ble.connect(address)) {
        // Save to NVS
        int idx = _ble.findStrip(address);
        if (idx >= 0) {
            String key = "strip" + String(idx + 1) + "_addr";
            _prefs.putString(key.c_str(), address);
        }
        sendOk(req);
    } else {
        sendError(req, 500, "Failed to connect to device");
    }
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

void HttpServer::handleConfig(AsyncWebServerRequest* req) {
    JsonDocument doc;
    if (!parseBody(req, doc)) return;

    bool changed = false;

    if (doc["wifi_ssid"].is<const char*>()) {
        _prefs.putString("wifi_ssid", doc["wifi_ssid"].as<const char*>());
        changed = true;
    }
    if (doc["wifi_pass"].is<const char*>()) {
        _prefs.putString("wifi_pass", doc["wifi_pass"].as<const char*>());
        changed = true;
    }
    if (doc["strip1_addr"].is<const char*>()) {
        _prefs.putString("strip1_addr", doc["strip1_addr"].as<const char*>());
        changed = true;
    }
    if (doc["strip2_addr"].is<const char*>()) {
        _prefs.putString("strip2_addr", doc["strip2_addr"].as<const char*>());
        changed = true;
    }

    if (changed) {
        JsonDocument resp;
        resp["data"]["message"] = "Config saved. Rebooting in 2 seconds...";
        sendOk(req, resp);

        // Schedule reboot
        delay(2000);
        ESP.restart();
    } else {
        sendError(req, 400, "No config fields provided");
    }
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
