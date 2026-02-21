#include "wifi_manager.h"

void WiFiManager::begin(const char* ssid, const char* pass) {
    if (ssid) strncpy(_ssid, ssid, sizeof(_ssid) - 1);
    if (pass) strncpy(_pass, pass, sizeof(_pass) - 1);

    if (strlen(_ssid) == 0) {
        Serial.println("[WiFi] No SSID configured, starting AP mode");
        startAP();
        return;
    }

    startSTA();
}

void WiFiManager::startSTA() {
    _state = CONNECTING;
    Serial.printf("[WiFi] Connecting to '%s'...\n", _ssid);

    WiFi.mode(WIFI_STA);
    WiFi.begin(_ssid, _pass);

    uint32_t start = millis();
    while (WiFi.status() != WL_CONNECTED && (millis() - start) < WIFI_TIMEOUT_MS) {
        delay(100);
    }

    if (WiFi.status() == WL_CONNECTED) {
        _state = CONNECTED;
        _reconnectFailures = 0;
        Serial.printf("[WiFi] Connected! IP: %s RSSI: %d dBm\n",
                      WiFi.localIP().toString().c_str(), WiFi.RSSI());
        startMDNS();
    } else {
        Serial.println("[WiFi] STA connection failed, falling back to AP mode");
        startAP();
    }
}

void WiFiManager::startAP() {
    _state = AP_MODE;
    _apName = buildAPName();

    WiFi.mode(WIFI_AP);
    WiFi.softAP(_apName.c_str());

    Serial.printf("[WiFi] AP mode: '%s' at %s\n",
                  _apName.c_str(), WiFi.softAPIP().toString().c_str());
    startMDNS();
}

void WiFiManager::startMDNS() {
    if (MDNS.begin(BRIDGE_MDNS_NAME)) {
        MDNS.addService("_elk", "_tcp", HTTP_PORT);
        MDNS.addService("http", "tcp", HTTP_PORT);
        Serial.printf("[WiFi] mDNS: %s.local\n", BRIDGE_MDNS_NAME);
    } else {
        Serial.println("[WiFi] mDNS failed to start");
    }
}

String WiFiManager::buildAPName() {
    uint8_t mac[6];
    WiFi.macAddress(mac);
    char buf[32];
    snprintf(buf, sizeof(buf), "%s%02X%02X", BRIDGE_AP_PREFIX, mac[4], mac[5]);
    return String(buf);
}

void WiFiManager::loop() {
    uint32_t now = millis();
    if (now - _lastCheck < WIFI_CHECK_INTERVAL_MS) return;
    _lastCheck = now;

    if (_state == CONNECTED) {
        if (WiFi.status() != WL_CONNECTED) {
            Serial.println("[WiFi] Connection lost, attempting reconnect...");
            _state = CONNECTING;
            _reconnectFailures = 0;
        }
    } else if (_state == CONNECTING) {
        if (WiFi.status() == WL_CONNECTED) {
            _state = CONNECTED;
            _reconnectFailures = 0;
            Serial.printf("[WiFi] Reconnected! IP: %s\n",
                          WiFi.localIP().toString().c_str());
        } else {
            _reconnectFailures++;
            Serial.printf("[WiFi] Reconnect attempt %d/%d...\n",
                          _reconnectFailures, WIFI_MAX_RECONNECT_FAILURES);

            if (_reconnectFailures >= WIFI_MAX_RECONNECT_FAILURES) {
                Serial.println("[WiFi] Max reconnect failures, switching to AP mode");
                startAP();
            } else {
                WiFi.disconnect();
                WiFi.begin(_ssid, _pass);
            }
        }
    }
    // In AP_MODE: stay in AP, user must configure via /api/config
}

String WiFiManager::getIP() const {
    if (_state == CONNECTED) {
        return WiFi.localIP().toString();
    } else if (_state == AP_MODE) {
        return WiFi.softAPIP().toString();
    }
    return "0.0.0.0";
}

int8_t WiFiManager::getRSSI() const {
    if (_state == CONNECTED) {
        return WiFi.RSSI();
    }
    return 0;
}
