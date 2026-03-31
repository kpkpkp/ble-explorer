#include "scheduler.h"

void Scheduler::begin(Preferences& prefs) {
    _prefs = &prefs;
    loadFromNVS();
}

const ScheduleEntry* Scheduler::getEntry(uint8_t idx) const {
    if (idx >= _count) return nullptr;
    return &_entries[idx];
}

bool Scheduler::addEntry(const ScheduleEntry& entry) {
    if (_count >= SCHEDULE_MAX_ENTRIES) return false;
    _entries[_count++] = entry;
    saveToNVS();
    return true;
}

bool Scheduler::updateEntry(uint8_t idx, const ScheduleEntry& entry) {
    if (idx >= _count) return false;
    _entries[idx] = entry;
    saveToNVS();
    return true;
}

bool Scheduler::removeEntry(uint8_t idx) {
    if (idx >= _count) return false;
    for (uint8_t i = idx; i < _count - 1; i++) {
        _entries[i] = _entries[i + 1];
    }
    _count--;
    _activeFlags = 0; // reset to avoid stale transitions
    saveToNVS();
    return true;
}

bool Scheduler::isTimeValid() const {
    struct tm t;
    time_t now = time(nullptr);
    localtime_r(&now, &t);
    return t.tm_year > 100; // year > 2000
}

bool Scheduler::isEntryActive(const ScheduleEntry& entry, struct tm& now) const {
    if (!entry.enabled) return false;

    uint16_t nowMins   = now.tm_hour * 60 + now.tm_min;
    uint16_t startMins = entry.startHour * 60 + entry.startMin;
    uint16_t endMins   = entry.endHour * 60 + entry.endMin;

    if (startMins <= endMins) {
        // Same-day span (e.g., 18:00 - 23:00)
        if (nowMins >= startMins && nowMins < endMins) {
            return (entry.days & (1 << now.tm_wday)) != 0;
        }
    } else {
        // Overnight span (e.g., 22:00 - 06:00)
        if (nowMins >= startMins) {
            // Evening part — check today
            return (entry.days & (1 << now.tm_wday)) != 0;
        } else if (nowMins < endMins) {
            // Morning part — check yesterday
            int yesterday = (now.tm_wday + 6) % 7;
            return (entry.days & (1 << yesterday)) != 0;
        }
    }
    return false;
}

bool Scheduler::loadUserPreset(const char* name, PatternStep* steps, uint8_t& stepCount) {
    if (!_prefs) return false;

    uint8_t count = _prefs->getUChar("up_cnt", 0);
    for (uint8_t i = 0; i < count && i < 8; i++) {
        char nk[8];
        snprintf(nk, sizeof(nk), "up%d_n", i);
        String pname = _prefs->getString(nk, "");
        if (pname != name) continue;

        char dk[8];
        snprintf(dk, sizeof(dk), "up%d_d", i);
        uint8_t buf[512];
        size_t len = _prefs->getBytes(dk, buf, sizeof(buf));
        if (len < 1) return false;

        stepCount = buf[0];
        if (stepCount == 0 || 1 + stepCount * 9 > len) return false;
        if (stepCount > PATTERN_MAX_STEPS) stepCount = PATTERN_MAX_STEPS;

        size_t pos = 1;
        for (uint8_t s = 0; s < stepCount; s++) {
            steps[s].r = buf[pos++];
            steps[s].g = buf[pos++];
            steps[s].b = buf[pos++];
            steps[s].brightness = buf[pos++];
            steps[s].durationMs = buf[pos] | (buf[pos + 1] << 8);
            pos += 2;
            steps[s].transition = (PatternStep::Transition)buf[pos++];
            steps[s].fadeTimeMs = buf[pos] | (buf[pos + 1] << 8);
            pos += 2;
        }
        return true;
    }
    return false;
}

void Scheduler::tick(BleManager& ble, PatternEngine& pattern) {
    if (_count == 0) return;

    // Enforce power-off on reconnect even before NTP syncs —
    // strips boot into default-on state and need immediate correction.
    // Retries 3x at 2s intervals to overcome marginal RSSI.
    if (_enforceCount > 0 && !isTimeValid()) {
        Serial.printf("[Sched] Enforce: NTP not synced, powering off (%d left)\n", _enforceCount);
        uint8_t cmd[9];
        buildPowerCmd(false, cmd);
        ble.write(cmd, "all");
        for (size_t s = 0; s < ble.getStripCount(); s++) {
            BleStrip* st = ble.getStrip(s);
            if (st) st->lastPower = false;
        }
        _enforceCount--;
        return;
    }

    if (!isTimeValid()) return;

    uint32_t now = millis();
    if (now - _lastCheck < SCHEDULE_CHECK_INTERVAL_MS) return;
    _lastCheck = now;

    struct tm t;
    time_t tnow = time(nullptr);
    localtime_r(&tnow, &t);

    uint16_t newFlags = 0;

    for (uint8_t i = 0; i < _count; i++) {
        if (isEntryActive(_entries[i], t)) {
            newFlags |= (1 << i);
        }
    }

    // Detect transitions
    for (uint8_t i = 0; i < _count; i++) {
        uint16_t mask = (1 << i);
        bool wasActive = (_activeFlags & mask) != 0;
        bool isActive  = (newFlags & mask) != 0;

        if (isActive && !wasActive) {
            // Entry just became active
            const ScheduleEntry& e = _entries[i];
            Serial.printf("[Sched] Entry %d active: %s\n", i, e.pattern);

            if (strcmp(e.pattern, "*") == 0) {
                // Power timer — turn on
                uint8_t cmd[9];
                buildPowerCmd(true, cmd);
                ble.write(cmd, "all");
                for (size_t s = 0; s < ble.getStripCount(); s++) {
                    BleStrip* st = ble.getStrip(s);
                    if (st) st->lastPower = true;
                }
            } else {
                // Named pattern — power on first, then load and play
                uint8_t cmd[9];
                buildPowerCmd(true, cmd);
                ble.write(cmd, "all");
                for (size_t s = 0; s < ble.getStripCount(); s++) {
                    BleStrip* st = ble.getStrip(s);
                    if (st) st->lastPower = true;
                }
                PatternStep steps[PATTERN_MAX_STEPS];
                uint8_t stepCount = 0;
                if (loadUserPreset(e.pattern, steps, stepCount)) {
                    pattern.play(steps, stepCount, true, "all", e.pattern);
                } else {
                    Serial.printf("[Sched] Preset '%s' not found\n", e.pattern);
                }
            }
        } else if (!isActive && wasActive) {
            // Entry just became inactive
            const ScheduleEntry& e = _entries[i];
            Serial.printf("[Sched] Entry %d inactive: %s\n", i, e.pattern);

            if (strcmp(e.pattern, "*") == 0) {
                // Power timer — turn off
                uint8_t cmd[9];
                buildPowerCmd(false, cmd);
                ble.write(cmd, "all");
                for (size_t s = 0; s < ble.getStripCount(); s++) {
                    BleStrip* st = ble.getStrip(s);
                    if (st) st->lastPower = false;
                }
            } else {
                // Stop pattern and power off
                pattern.stop();
                uint8_t cmd[9];
                buildPowerCmd(false, cmd);
                ble.write(cmd, "all");
                for (size_t s = 0; s < ble.getStripCount(); s++) {
                    BleStrip* st = ble.getStrip(s);
                    if (st) st->lastPower = false;
                }
            }
        }
    }

    // After forceCheck: if nothing is active, enforce power off
    // (strips may have booted into default-on state)
    if (_enforceCount > 0 && newFlags == 0) {
        Serial.printf("[Sched] Enforce: no active entries, powering off (%d left)\n", _enforceCount);
        uint8_t cmd[9];
        buildPowerCmd(false, cmd);
        ble.write(cmd, "all");
        for (size_t s = 0; s < ble.getStripCount(); s++) {
            BleStrip* st = ble.getStrip(s);
            if (st) st->lastPower = false;
        }
        pattern.stop();
        _enforceCount--;
    } else if (_enforceCount > 0 && newFlags != 0) {
        _enforceCount = 0; // schedule is active, stop enforcing off
    }

    _activeFlags = newFlags;
}

// --- NVS persistence ---
// Format: [count:1] then per entry:
//   [enabled:1][days:1][startH:1][startM:1][endH:1][endM:1][patLen:1][pat:N]

void Scheduler::saveToNVS() {
    if (!_prefs) return;

    uint8_t buf[2048];
    size_t pos = 0;
    buf[pos++] = _count;

    for (uint8_t i = 0; i < _count; i++) {
        const ScheduleEntry& e = _entries[i];
        uint8_t patLen = (uint8_t)strlen(e.pattern);
        if (pos + 7 + patLen > sizeof(buf)) break;

        buf[pos++] = e.enabled ? 1 : 0;
        buf[pos++] = e.days;
        buf[pos++] = e.startHour;
        buf[pos++] = e.startMin;
        buf[pos++] = e.endHour;
        buf[pos++] = e.endMin;
        buf[pos++] = patLen;
        memcpy(&buf[pos], e.pattern, patLen);
        pos += patLen;
    }

    _prefs->putBytes("sched_data", buf, pos);
    Serial.printf("[Sched] Saved %d entries (%d bytes)\n", _count, pos);
}

void Scheduler::loadFromNVS() {
    if (!_prefs) return;

    uint8_t buf[2048];
    size_t len = _prefs->getBytes("sched_data", buf, sizeof(buf));
    if (len < 1) {
        _count = 0;
        return;
    }

    size_t pos = 0;
    _count = buf[pos++];
    if (_count > SCHEDULE_MAX_ENTRIES) _count = SCHEDULE_MAX_ENTRIES;

    for (uint8_t i = 0; i < _count; i++) {
        if (pos + 7 > len) { _count = i; break; }

        _entries[i].enabled   = buf[pos++] != 0;
        _entries[i].days      = buf[pos++];
        _entries[i].startHour = buf[pos++];
        _entries[i].startMin  = buf[pos++];
        _entries[i].endHour   = buf[pos++];
        _entries[i].endMin    = buf[pos++];

        uint8_t patLen = buf[pos++];
        if (pos + patLen > len || patLen >= sizeof(_entries[i].pattern)) {
            _count = i;
            break;
        }
        memcpy(_entries[i].pattern, &buf[pos], patLen);
        _entries[i].pattern[patLen] = '\0';
        pos += patLen;
    }

    Serial.printf("[Sched] Loaded %d entries from NVS\n", _count);
}
