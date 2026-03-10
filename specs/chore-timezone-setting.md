# Chore: Configurable Timezone with NTP Auto-Sync

## Problem
Timezone is hardcoded as MST/MDT (`configTime(-7*3600, 3600, ...)`) in
`elk_bridge.ino`. The scheduler depends on correct local time for day-of-week
and time-of-day matching. If the bridge is deployed outside Denver, schedules
break.

## Solution
1. Store timezone as a POSIX TZ string in NVS (key: `tz_string`)
2. Default to `MST7MDT,M3.2.0,M11.1.0` (Denver, US Mountain with DST rules)
3. Apply via `setenv("TZ", ...) + tzset()` before `configTime()`
4. Expose in `/api/config` POST and `/status` GET
5. Add timezone dropdown to web dashboard settings section
6. Re-sync NTP on timezone change (no reboot needed)

## POSIX TZ String Format
The ESP32 IDF uses POSIX TZ strings, which encode UTC offset + DST rules:
- `MST7MDT,M3.2.0,M11.1.0` = UTC-7, DST in effect Mar 2nd Sun to Nov 1st Sun
- `EST5EDT,M3.2.0,M11.1.0` = UTC-5, Eastern
- `CST6CDT,M3.2.0,M11.1.0` = UTC-6, Central
- `PST8PDT,M3.2.0,M11.1.0` = UTC-8, Pacific
- `UTC0` = No offset, no DST

This is cleaner than separate offset + DST boolean because the TZ string
encodes the full DST transition rules.

## Files Modified
- `firmware/elk-bridge/src/elk_bridge.ino` — load TZ from NVS, apply before NTP
- `firmware/elk-bridge/src/http_server.cpp` — accept `tz` in config POST, report in status
- `firmware/elk-bridge/src/html.h` — timezone dropdown in settings section
- `firmware/elk-bridge/src/config.h` — default TZ constant

## Testing
- Set timezone via `/api/config` POST `{"tz":"EST5EDT,M3.2.0,M11.1.0"}`
- Verify `/status` reports new timezone
- Verify `/api/schedule` GET returns correct local time
- Verify schedule triggers at correct local times
- Reboot bridge, verify TZ persists from NVS

## Rollback
NVS key `tz_string` — if absent, falls back to Denver default. No breaking
changes to existing NVS data.
