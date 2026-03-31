#pragma once

#include <Arduino.h>
#include <Preferences.h>
#include "config.h"
#include "ble_manager.h"
#include "pattern_engine.h"
#include "elk_protocol.h"

#define SCHEDULE_MAX_ENTRIES 16
#define SCHEDULE_CHECK_INTERVAL_MS 2000

struct ScheduleEntry {
    bool    enabled;
    uint8_t days;        // bitmask: bit0=Sun, bit1=Mon, ..., bit6=Sat (tm_wday)
    uint8_t startHour;   // 0-23
    uint8_t startMin;    // 0-59
    uint8_t endHour;     // 0-23
    uint8_t endMin;      // 0-59
    char    pattern[64]; // preset name, or "*" for power-only timer
};

class Scheduler {
public:
    void begin(Preferences& prefs);
    void tick(BleManager& ble, PatternEngine& pattern);

    uint8_t getEntryCount() const { return _count; }
    const ScheduleEntry* getEntry(uint8_t idx) const;

    bool addEntry(const ScheduleEntry& entry);
    bool updateEntry(uint8_t idx, const ScheduleEntry& entry);
    bool removeEntry(uint8_t idx);

    void saveToNVS();
    void loadFromNVS();

    bool isTimeValid() const;
    void forceCheck() { _activeFlags = 0; _lastCheck = 0; _enforceState = true; }

private:
    Preferences*  _prefs = nullptr;
    ScheduleEntry _entries[SCHEDULE_MAX_ENTRIES];
    uint8_t       _count = 0;
    uint32_t      _lastCheck = 0;
    uint16_t      _activeFlags = 0;   // bitmask: which entries were active last tick

    bool _enforceState = false;

    bool isEntryActive(const ScheduleEntry& entry, struct tm& now) const;
    bool loadUserPreset(const char* name, PatternStep* steps, uint8_t& stepCount);
};
