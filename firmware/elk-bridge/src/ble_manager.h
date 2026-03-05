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
    uint8_t     addrType;
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
    uint8_t                     addrType = 0; // 0=public, 1=random
    NimBLEClient*               client   = nullptr;
    NimBLERemoteCharacteristic* writeChar = nullptr;
    State                       state    = DISCONNECTED;
    uint8_t                     retries  = 0;
    uint32_t                    lastSeen = 0;
    int                         rssi     = 0; // last polled RSSI (dBm), 0 = unknown

    // Tracked device state (updated by CommandDispatcher + PatternEngine)
    uint8_t lastR = 0, lastG = 0, lastB = 0;   // user-intended (pre-calibration) color
    uint8_t lastBrightness = 100;               // 0-100
    bool    lastPower = true;
    uint8_t lastEffectCode = 0;                 // 0=none, 0x80-0x9A=active
    uint8_t lastEffectSpeed = 3;                // 1-10
    uint8_t lastWarm = 0, lastCold = 0;         // color temp
    uint8_t mode = 0;                           // 0=color, 1=effect, 2=pattern

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
    // Async operation states (safe for cross-task communication on single-core)
    enum AsyncOp { ASYNC_IDLE, ASYNC_PENDING, ASYNC_RUNNING, ASYNC_DONE };

    void init();
    bool connect(const char* address, uint8_t addrType = 0);
    bool disconnect(const char* address);
    bool write(const uint8_t* cmd, const char* target = "all");
    bool writeSingle(const uint8_t* cmd, const char* target = "all");
    bool scan(std::vector<ScanResult>& results);
    void loop();

    // --- Async scan (non-blocking, for HTTP handlers) ---
    void     requestScan();
    AsyncOp  scanState() const { return _scanOp; }
    const std::vector<ScanResult>& scanResults() const { return _asyncScanResults; }
    void     clearScanResults() { _scanOp = ASYNC_IDLE; }

    // --- Async connect (non-blocking, for HTTP handlers) ---
    void        requestConnect(const char* address, uint8_t addrType = 0);
    AsyncOp     connectState() const { return _connectOp; }
    bool        connectSucceeded() const { return _connectOk; }
    const std::string& connectAddress() const { return _pendingAddr; }
    void        clearConnectResult() { _connectOp = ASYNC_IDLE; }

    size_t     getStripCount() const { return _strips.size(); }
    BleStrip*  getStrip(int idx);
    bool       isAnyConnected() const;
    String     slotFor(const char* address) const;
    int        findStrip(const char* address) const;

private:
    std::vector<BleStrip> _strips;
    uint32_t              _lastReconnectCheck = 0;

    // Async state
    volatile AsyncOp           _scanOp    = ASYNC_IDLE;
    std::vector<ScanResult>    _asyncScanResults;
    volatile AsyncOp           _connectOp = ASYNC_IDLE;
    std::string                _pendingAddr;
    uint8_t                    _pendingAddrType = 0;
    bool                       _connectOk = false;

    bool connectStrip(BleStrip& strip);
    void disconnectStrip(BleStrip& strip);
    bool writeToStrip(BleStrip& strip, const uint8_t* cmd);
};
