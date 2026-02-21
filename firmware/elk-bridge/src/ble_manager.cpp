#include "ble_manager.h"

void BleManager::init() {
    NimBLEDevice::init(BRIDGE_BLE_NAME);
    NimBLEDevice::setPower(ESP_PWR_LVL_P9);
    Serial.println("[BLE] Initialized, power +9 dBm");
}

bool BleManager::connect(const char* address, uint8_t addrType) {
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
    strip.addrType = addrType;
    strip.state = BleStrip::CONNECTING;
    strip.retries = 0;
    strip.lastSeen = millis();

    _strips.push_back(strip);
    BleStrip& ref = _strips.back();

    Serial.printf("[BLE] Connecting to %s (addrType=%d)...\n", address, addrType);
    if (connectStrip(ref)) {
        Serial.printf("[BLE] Connected to %s as strip%d, heap=%d\n",
                      address, (int)_strips.size(), ESP.getFreeHeap());
        return true;
    }

    Serial.printf("[BLE] Failed to connect to %s\n", address);
    _strips.pop_back();
    return false;
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

    NimBLEAddress addr(strip.address, strip.addrType);
    bool connected = strip.client->connect(addr);

    if (!connected) {
        Serial.printf("[BLE] Connection to %s failed (addrType=%d)\n",
                      strip.address.c_str(), strip.addrType);
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

    // Relax BLE connection interval to free radio time for WiFi (shared radio on ESP32-C3)
    // Params: minInterval, maxInterval (units of 1.25ms), latency, timeout (units of 10ms)
    // 48=60ms, 96=120ms → BLE only uses radio every 60-120ms instead of default 7.5-15ms
    strip.client->updateConnParams(48, 96, 0, 400);

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
            sr.name     = name;
            sr.address  = device.getAddress().toString();
            sr.rssi     = device.getRSSI();
            sr.addrType = device.getAddress().getType();
            results.push_back(sr);
            Serial.printf("[BLE] Found: %s (%s) RSSI: %d addrType: %d\n",
                          sr.name.c_str(), sr.address.c_str(), sr.rssi,
                          sr.addrType);
        }
    }

    pScan->clearResults();
    Serial.printf("[BLE] Scan complete, %d devices found\n", (int)results.size());
    return true;
}

void BleManager::requestScan() {
    if (_scanOp == ASYNC_IDLE || _scanOp == ASYNC_DONE) {
        _scanOp = ASYNC_PENDING;
    }
}

void BleManager::requestConnect(const char* address, uint8_t addrType) {
    if (_connectOp == ASYNC_IDLE || _connectOp == ASYNC_DONE) {
        _pendingAddr = address;
        _pendingAddrType = addrType;
        _connectOp = ASYNC_PENDING;
    }
}

void BleManager::loop() {
    // --- Process async scan (runs in loopTask, WiFi/TCP can preempt) ---
    if (_scanOp == ASYNC_PENDING) {
        _scanOp = ASYNC_RUNNING;
        _asyncScanResults.clear();
        scan(_asyncScanResults);
        _scanOp = ASYNC_DONE;
    }

    // --- Process async connect (runs in loopTask, WiFi/TCP can preempt) ---
    if (_connectOp == ASYNC_PENDING) {
        _connectOp = ASYNC_RUNNING;
        _connectOk = connect(_pendingAddr.c_str(), _pendingAddrType);
        _connectOp = ASYNC_DONE;
    }

    // --- Reconnect logic (non-blocking, uses async queue) ---
    // Skip reconnect checks while an async operation is in progress
    if (_connectOp != ASYNC_IDLE && _connectOp != ASYNC_DONE) return;
    if (_scanOp != ASYNC_IDLE && _scanOp != ASYNC_DONE) return;

    uint32_t now = millis();
    if (now - _lastReconnectCheck < BLE_RECONNECT_INTERVAL_MS) return;
    _lastReconnectCheck = now;

    // Check connection health
    for (auto& strip : _strips) {
        if (strip.state == BleStrip::READY) {
            if (strip.client && !strip.client->isConnected()) {
                Serial.printf("[BLE] Strip %s lost connection\n", strip.address.c_str());
                strip.state = BleStrip::LOST;
                strip.retries = 0;
                disconnectStrip(strip);
                strip.state = BleStrip::LOST; // restore after disconnectStrip sets DISCONNECTED
            }
        }
    }

    // Attempt reconnect for ONE lost strip per iteration (via async queue)
    for (auto it = _strips.begin(); it != _strips.end(); ) {
        if (it->state == BleStrip::LOST) {
            if (it->retries >= BLE_MAX_RECONNECT_RETRIES) {
                Serial.printf("[BLE] Strip %s max retries (%d), removing\n",
                              it->address.c_str(), BLE_MAX_RECONNECT_RETRIES);
                disconnectStrip(*it);
                it = _strips.erase(it);
                continue;
            }
            it->retries++;
            Serial.printf("[BLE] Queuing reconnect for %s (attempt %d/%d), heap=%d\n",
                          it->address.c_str(), it->retries, BLE_MAX_RECONNECT_RETRIES,
                          ESP.getFreeHeap());
            requestConnect(it->address.c_str(), it->addrType);
            // Remove the LOST entry — async connect will re-add if successful
            disconnectStrip(*it);
            it = _strips.erase(it);
            return; // Only one reconnect per loop cycle
        } else {
            ++it;
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
