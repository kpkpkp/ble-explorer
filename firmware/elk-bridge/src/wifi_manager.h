#pragma once

#include <Arduino.h>
#include <WiFi.h>
#include <ESPmDNS.h>
#include "config.h"

class WiFiManager {
public:
    enum State {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        AP_MODE
    };

    void begin(const char* ssid, const char* pass);
    void loop();

    State       getState() const { return _state; }
    String      getIP() const;
    int8_t      getRSSI() const;
    bool        isConnected() const { return _state == CONNECTED; }
    const char* getSSID() const { return _ssid; }
    String      getAPName() const { return _apName; }

private:
    State    _state = DISCONNECTED;
    char     _ssid[64] = {0};
    char     _pass[64] = {0};
    String   _apName;
    uint32_t _lastCheck = 0;
    uint8_t  _reconnectFailures = 0;

    void startSTA();
    void startAP();
    void startMDNS();
    String buildAPName();
};
