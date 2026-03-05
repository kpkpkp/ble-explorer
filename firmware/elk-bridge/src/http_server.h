#pragma once

#include <Arduino.h>
#include <ESPAsyncWebServer.h>
#include <ArduinoJson.h>
#include <Update.h>
#include <Preferences.h>
#include "config.h"
#include "wifi_manager.h"
#include "ble_manager.h"
#include "command_dispatch.h"
#include "pattern_engine.h"

class HttpServer {
public:
    HttpServer(WiFiManager& wifi, BleManager& ble,
               CommandDispatcher& dispatcher, PatternEngine& pattern,
               Preferences& prefs)
        : _wifi(wifi), _ble(ble), _dispatcher(dispatcher),
          _pattern(pattern), _prefs(prefs), _server(HTTP_PORT) {}

    void begin();

private:
    WiFiManager&       _wifi;
    BleManager&        _ble;
    CommandDispatcher&  _dispatcher;
    PatternEngine&     _pattern;
    Preferences&       _prefs;
    AsyncWebServer     _server;

    void addCorsHeaders(AsyncWebServerResponse* response);
    void sendOk(AsyncWebServerRequest* req, JsonDocument& doc);
    void sendOk(AsyncWebServerRequest* req);
    void sendError(AsyncWebServerRequest* req, int code, const char* msg);
    bool parseBody(AsyncWebServerRequest* req, JsonDocument& doc);

    // Route handlers
    void handleRoot(AsyncWebServerRequest* req);
    void handleStatus(AsyncWebServerRequest* req);
    void handleColor(AsyncWebServerRequest* req);
    void handleBrightness(AsyncWebServerRequest* req);
    void handlePower(AsyncWebServerRequest* req);
    void handleEffect(AsyncWebServerRequest* req);
    void handleSpeed(AsyncWebServerRequest* req);
    void handleColorTemp(AsyncWebServerRequest* req);
    void handleRaw(AsyncWebServerRequest* req);
    void handlePattern(AsyncWebServerRequest* req);
    void handleStop(AsyncWebServerRequest* req);
    void handleScan(AsyncWebServerRequest* req);
    void handleConnect(AsyncWebServerRequest* req);
    void handleDisconnect(AsyncWebServerRequest* req);
    void handleDevices(AsyncWebServerRequest* req);
    void handleConfig(AsyncWebServerRequest* req);
    void handleOTA(AsyncWebServerRequest* req, const String& filename,
                   size_t index, uint8_t* data, size_t len, bool final);
    void handleOTAResponse(AsyncWebServerRequest* req);
    void handleOptions(AsyncWebServerRequest* req);
    void handleGetPresets(AsyncWebServerRequest* req);
    void handleSavePreset(AsyncWebServerRequest* req);
    void handleDeletePreset(AsyncWebServerRequest* req);
};
