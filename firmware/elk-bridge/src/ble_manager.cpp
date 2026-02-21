#include "ble_manager.h"

void BleManager::init() {
    NimBLEDevice::init(BRIDGE_BLE_NAME);
    NimBLEDevice::setPower(ESP_PWR_LVL_P9);
    Serial.println("[BLE] Initialized, power +9 dBm");
}

bool BleManager::connect(const char* address) {
    // Check if already connected or in progress
    int idx = findStrip(address);
    if (idx >= 0) {
        BleStrip& existing = _strips[idx];
        if (existing.state == BleStrip::READY) {
            Serial.printf("[BLE] Already connected to %s\n", address);
            return true;
        }
        // Remove stale entry and reconnect
        disconnectStrip(existing);
        _strips.erase(_strips.begin() + idx);
    }

    if (_strips.size() >= BLE_MAX_STRIPS) {
        Serial.printf("[BLE] Max strips (%d) reached, cannot connect\n", BLE_MAX_STRIPS);
        return false;
    }

    BleStrip strip;
    strip.address = address;
    strip.state = BleStrip::CONNECTING;
    strip.retries = 0;
    strip.lastSeen = millis();

    _strips.push_back(strip);
    BleStrip& ref = _strips.back();

    if (connectStrip(ref)) {
        Serial.printf("[BLE] Connected to %s as strip%d\n", address, (int)_strips.size());
        return true;
    } else {
        Serial.printf("[BLE] Failed to connect to %s\n", address);
        _strips.pop_back();
        return false;
    }
}

bool BleManager::connectStrip(BleStrip& strip) {
    strip.state = BleStrip::CONNECTING;

    strip.client = NimBLEDevice::createClient();
    if (!strip.client) {
        Serial.println("[BLE] Failed to create client");
        strip.state = BleStrip::DISCONNECTED;
        return false;
    }

    strip.client->setConnectTimeout(BLE_CONNECT_TIMEOUT_MS / 1000);

    NimBLEAddress addr(strip.address);
    if (!strip.client->connect(addr)) {
        Serial.printf("[BLE] Connection to %s failed\n", strip.address.c_str());
        NimBLEDevice::deleteClient(strip.client);
        strip.client = nullptr;
        strip.state = BleStrip::DISCONNECTED;
        return false;
    }

    strip.state = BleStrip::DISCOVERING;
    Serial.printf("[BLE] Connected, discovering services on %s...\n", strip.address.c_str());

    NimBLERemoteService* svc = strip.client->getService(ELK_SERVICE_UUID);
    if (!svc) {
        Serial.printf("[BLE] Service 0xFFF0 not found on %s\n", strip.address.c_str());
        strip.client->disconnect();
        NimBLEDevice::deleteClient(strip.client);
        strip.client = nullptr;
        strip.state = BleStrip::DISCONNECTED;
        return false;
    }

    strip.writeChar = svc->getCharacteristic(ELK_WRITE_CHAR_UUID);
    if (!strip.writeChar) {
        Serial.printf("[BLE] Characteristic 0xFFF3 not found on %s\n", strip.address.c_str());
        strip.client->disconnect();
        NimBLEDevice::deleteClient(strip.client);
        strip.client = nullptr;
        strip.state = BleStrip::DISCONNECTED;
        return false;
    }

    strip.state = BleStrip::READY;
    strip.retries = 0;
    strip.lastSeen = millis();
    Serial.printf("[BLE] Strip %s ready (service+char discovered)\n", strip.address.c_str());
    return true;
}

bool BleManager::disconnect(const char* address) {
    int idx = findStrip(address);
    if (idx < 0) {
        Serial.printf("[BLE] Strip %s not found\n", address);
        return false;
    }

    disconnectStrip(_strips[idx]);
    _strips.erase(_strips.begin() + idx);
    Serial.printf("[BLE] Disconnected and removed %s\n", address);
    return true;
}

void BleManager::disconnectStrip(BleStrip& strip) {
    if (strip.client) {
        if (strip.client->isConnected()) {
            strip.client->disconnect();
        }
        NimBLEDevice::deleteClient(strip.client);
        strip.client = nullptr;
    }
    strip.writeChar = nullptr;
    strip.state = BleStrip::DISCONNECTED;
}

bool BleManager::writeToStrip(BleStrip& strip, const uint8_t* cmd) {
    if (strip.state != BleStrip::READY || !strip.writeChar) {
        return false;
    }

    if (!strip.client || !strip.client->isConnected()) {
        strip.state = BleStrip::LOST;
        return false;
    }

    for (int i = 0; i < COMMAND_RETRY_COUNT; i++) {
        if (!strip.writeChar->writeValue(cmd, 9, false)) {
            Serial.printf("[BLE] Write %d failed to %s\n", i + 1, strip.address.c_str());
            strip.state = BleStrip::LOST;
            return false;
        }
        if (i < COMMAND_RETRY_COUNT - 1) {
            delay(COMMAND_RETRY_DELAY_MS);
        }
    }

    strip.lastSeen = millis();
    return true;
}

bool BleManager::write(const uint8_t* cmd, const char* target) {
    if (!target) target = "all";

    bool success = false;

    if (strcmp(target, "all") == 0) {
        for (auto& strip : _strips) {
            if (writeToStrip(strip, cmd)) {
                success = true;
            }
        }
    } else if (strncmp(target, "strip", 5) == 0) {
        // "strip1" -> index 0, "strip2" -> index 1, etc.
        int idx = atoi(target + 5) - 1;
        if (idx >= 0 && idx < (int)_strips.size()) {
            success = writeToStrip(_strips[idx], cmd);
        }
    } else {
        // Treat as MAC address
        int idx = findStrip(target);
        if (idx >= 0) {
            success = writeToStrip(_strips[idx], cmd);
        }
    }

    return success;
}

bool BleManager::scan(std::vector<ScanResult>& results) {
    Serial.println("[BLE] Starting scan...");
    results.clear();

    NimBLEScan* pScan = NimBLEDevice::getScan();
    pScan->setActiveScan(true);
    pScan->setDuplicateFilter(true);

    NimBLEScanResults scanResults = pScan->start(BLE_SCAN_DURATION_S, false);

    for (int i = 0; i < scanResults.getCount(); i++) {
        NimBLEAdvertisedDevice device = scanResults.getDevice(i);
        std::string name = device.getName();

        if (name.find(DEVICE_NAME_PREFIX) == 0) {
            ScanResult sr;
            sr.name    = name;
            sr.address = device.getAddress().toString();
            sr.rssi    = device.getRSSI();
            results.push_back(sr);
            Serial.printf("[BLE] Found: %s (%s) RSSI: %d\n",
                          sr.name.c_str(), sr.address.c_str(), sr.rssi);
        }
    }

    pScan->clearResults();
    Serial.printf("[BLE] Scan complete, %d devices found\n", (int)results.size());
    return true;
}

void BleManager::loop() {
    uint32_t now = millis();
    if (now - _lastReconnectCheck < BLE_RECONNECT_INTERVAL_MS) return;
    _lastReconnectCheck = now;

    for (auto& strip : _strips) {
        if (strip.state == BleStrip::READY) {
            // Check if the connection is still alive
            if (strip.client && !strip.client->isConnected()) {
                Serial.printf("[BLE] Strip %s lost connection\n", strip.address.c_str());
                strip.state = BleStrip::LOST;
                strip.retries = 0;
            }
        }

        if (strip.state == BleStrip::LOST) {
            if (strip.retries >= BLE_MAX_RECONNECT_RETRIES) {
                Serial.printf("[BLE] Strip %s max retries reached, giving up\n",
                              strip.address.c_str());
                strip.state = BleStrip::DISCONNECTED;
                if (strip.client) {
                    NimBLEDevice::deleteClient(strip.client);
                    strip.client = nullptr;
                }
                strip.writeChar = nullptr;
                continue;
            }

            strip.retries++;
            Serial.printf("[BLE] Reconnecting to %s (attempt %d/%d)...\n",
                          strip.address.c_str(), strip.retries, BLE_MAX_RECONNECT_RETRIES);

            // Clean up old client
            if (strip.client) {
                NimBLEDevice::deleteClient(strip.client);
                strip.client = nullptr;
            }
            strip.writeChar = nullptr;

            connectStrip(strip);
        }
    }
}

int BleManager::findStrip(const char* address) const {
    std::string addr(address);
    // NimBLE returns lowercase addresses
    for (auto& c : addr) c = tolower(c);

    for (int i = 0; i < (int)_strips.size(); i++) {
        std::string stripAddr = _strips[i].address;
        for (auto& c : stripAddr) c = tolower(c);
        if (stripAddr == addr) return i;
    }
    return -1;
}

BleStrip* BleManager::getStrip(int idx) {
    if (idx >= 0 && idx < (int)_strips.size()) {
        return &_strips[idx];
    }
    return nullptr;
}

bool BleManager::isAnyConnected() const {
    for (const auto& strip : _strips) {
        if (strip.state == BleStrip::READY) return true;
    }
    return false;
}

String BleManager::slotFor(const char* address) const {
    int idx = findStrip(address);
    if (idx >= 0) {
        return "strip" + String(idx + 1);
    }
    return "unknown";
}
