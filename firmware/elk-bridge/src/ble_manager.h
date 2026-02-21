#pragma once

#include <Arduino.h>
#include <NimBLEDevice.h>
#include <vector>
#include <string>
#include "config.h"
#include "elk_protocol.h"

struct ScanResult {
    std::string name;
    std::string address;
    int         rssi;
};

struct BleStrip {
    enum State {
        DISCONNECTED,
        CONNECTING,
        DISCOVERING,
        READY,
        LOST
    };

    std::string                 address;
    NimBLEClient*               client   = nullptr;
    NimBLERemoteCharacteristic* writeChar = nullptr;
    State                       state    = DISCONNECTED;
    uint8_t                     retries  = 0;
    uint32_t                    lastSeen = 0;

    const char* stateStr() const {
        switch (state) {
            case DISCONNECTED: return "disconnected";
            case CONNECTING:   return "connecting";
            case DISCOVERING:  return "discovering";
            case READY:        return "ready";
            case LOST:         return "lost";
            default:           return "unknown";
        }
    }
};

class BleManager {
public:
    void init();
    bool connect(const char* address);
    bool disconnect(const char* address);
    bool write(const uint8_t* cmd, const char* target = "all");
    bool scan(std::vector<ScanResult>& results);
    void loop();

    size_t     getStripCount() const { return _strips.size(); }
    BleStrip*  getStrip(int idx);
    bool       isAnyConnected() const;
    String     slotFor(const char* address) const;
    int        findStrip(const char* address) const;

private:
    std::vector<BleStrip> _strips;
    uint32_t              _lastReconnectCheck = 0;

    bool connectStrip(BleStrip& strip);
    void disconnectStrip(BleStrip& strip);
    bool writeToStrip(BleStrip& strip, const uint8_t* cmd);
};
