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
        _everConnected = true;
        _reconnectFailures = 0;
        Serial.printf("[WiFi] Connected! IP: %s RSSI: %d dBm\n",
                      WiFi.localIP().toString().c_str(), WiFi.RSSI());
        startMDNS();
    } else {
        // Start AP+STA mode: AP for local access, keep trying STA in loop()
        Serial.println("[WiFi] STA connection failed, starting AP+STA mode");
        _state = CONNECTING;
        _reconnectFailures = 0;
        startAPSTA();
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

void WiFiManager::startAPSTA() {
    _apName = buildAPName();

    WiFi.mode(WIFI_AP_STA);
    WiFi.softAP(_apName.c_str());
    WiFi.begin(_ssid, _pass);

    Serial.printf("[WiFi] AP+STA mode: AP '%s' at %s, trying STA '%s'...\n",
                  _apName.c_str(), WiFi.softAPIP().toString().c_str(), _ssid);
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

void WiFiManager::sendBeacon() {
    if (_state != CONNECTED) return;

    IPAddress broadcastIP = WiFi.localIP();
    broadcastIP[3] = 255; // subnet broadcast

    uint8_t mac[6];
    WiFi.macAddress(mac);
    char macStr[18];
    snprintf(macStr, sizeof(macStr), "%02X:%02X:%02X:%02X:%02X:%02X",
             mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);

    char beacon[192];
    snprintf(beacon, sizeof(beacon),
             "{\"name\":\"ESP32-StringLights\",\"ip\":\"%s\",\"port\":%d,"
             "\"mac\":\"%s\",\"version\":\"%s\",\"uptime\":%lu}",
             WiFi.localIP().toString().c_str(), HTTP_PORT,
             macStr, FIRMWARE_VERSION, millis() / 1000);

    _udp.beginPacket(broadcastIP, BEACON_PORT);
    _udp.write((const uint8_t*)beacon, strlen(beacon));
    _udp.endPacket();
}

void WiFiManager::loop() {
    uint32_t now = millis();

    // Send UDP beacon
    if (_state == CONNECTED && (now - _lastBeacon >= BEACON_INTERVAL_MS)) {
        _lastBeacon = now;
        sendBeacon();
    }

    if (now - _lastCheck < WIFI_CHECK_INTERVAL_MS) return;
    _lastCheck = now;

    if (_state == CONNECTED) {
        if (WiFi.status() != WL_CONNECTED) {
            Serial.printf("[WiFi] Connection lost (was connected for %lus)\n",
                          (millis() - _lastCheck) / 1000);
            _state = CONNECTING;
            _reconnectFailures = 0;
            WiFi.disconnect();
            WiFi.begin(_ssid, _pass);
        }
    } else if (_state == CONNECTING) {
        if (WiFi.status() == WL_CONNECTED) {
            _state = CONNECTED;
            _everConnected = true;
            _reconnectFailures = 0;
            // Switch from AP+STA to STA-only to free resources
            if (WiFi.getMode() == WIFI_AP_STA) {
                WiFi.softAPdisconnect(true);
                WiFi.mode(WIFI_STA);
                Serial.println("[WiFi] Switched from AP+STA to STA mode");
            }
            Serial.printf("[WiFi] Connected! IP: %s RSSI: %d dBm\n",
                          WiFi.localIP().toString().c_str(), WiFi.RSSI());
            startMDNS();
        } else {
            _reconnectFailures++;
            Serial.printf("[WiFi] Reconnect attempt %d...\n", _reconnectFailures);
            WiFi.disconnect(false);
            WiFi.begin(_ssid, _pass);
        }
    }
    // In AP_MODE: no SSID configured, user must set via /api/config
}

String WiFiManager::getIP() const {
    if (_state == CONNECTED) {
        return WiFi.localIP().toString();
    } else if (_state == AP_MODE || _state == CONNECTING) {
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
