# Bug: Lights Turn Back On After Scheduler Powers Them Off

## Description

After the scheduler turns lights off at the end of a schedule window (e.g., 00:01),
the lights come back on by themselves. Observed after changing the schedule and
watching the lights correctly go out, then seeing them reappear.

## Reproduction

1. Configure a schedule entry: 18:00–00:01, pattern `*` (power timer), all days
2. Wait for 00:01 — lights turn off correctly
3. Wait a few minutes — lights turn back on with no user interaction

## Root Cause

**Two reinforcing bugs in the BLE reconnect handler (`elk_bridge.ino:128–141`):**

### Bug 1: Unconditional power-on after every BLE reconnect

```cpp
// elk_bridge.ino:129-133  (runs on EVERY BLE connect/reconnect)
uint8_t powerOnCmd[9];
buildPowerCmd(true, powerOnCmd);
bleManager.write(powerOnCmd, addr.c_str());
```

When a strip drops BLE and auto-reconnects (common at RSSI -84 dBm), this code
sends `power ON` regardless of what the scheduler decided. The strips are at
marginal signal strength, so BLE drops are frequent.

### Bug 2: Scheduler bypasses strip state tracking

```cpp
// scheduler.cpp:142-143  (turn on)
uint8_t cmd[9];
buildPowerCmd(true, cmd);
ble.write(cmd, "all");    // ← direct BLE write, no state update

// scheduler.cpp:161-162  (turn off)
buildPowerCmd(false, cmd);
ble.write(cmd, "all");    // ← lastPower still shows true!
```

The scheduler calls `ble.write()` directly instead of going through
`CommandDispatcher::handlePower()`. This means `BleStrip::lastPower` is never
updated. Even if the reconnect handler checked `lastPower`, it would read the
wrong value.

### Bug 3: Pattern restore on reconnect ignores scheduler

```cpp
// elk_bridge.ino:138-140
} else {
    patternEngine.loadFromNVS(bleManager);  // starts a saved pattern → lights on
}
```

If a pattern was saved from a previous session, reconnect restores it —
overriding the scheduler's off state.

### Failure sequence

```
18:00  Scheduler: entry active → power ON          _activeFlags = 0x01
00:01  Scheduler: entry inactive → power OFF        _activeFlags = 0x00
                  (lastPower still true — not updated)
00:15  BLE strip drops (marginal RSSI)              state → LOST
00:20  BLE auto-reconnect succeeds                  state → READY
       → elk_bridge.ino sends power ON              ← BUG: lights back on
       → loadFromNVS may start a pattern            ← BUG: also turns on
00:50  Scheduler tick: isActive=false, wasActive=false
       → no transition detected → no action         ← lights stay on
       (stays on until 18:00 next day when the cycle repeats)
```

## Proposed Fix

### 1. Remove unconditional power-on from reconnect handler

The ELK-BLEDOM strips maintain their physical state (LEDs on/off, color) across
BLE disconnects — the BLE link is just a control channel. No need to force
power on.

### 2. Scheduler updates strip state when it sends commands

Route scheduler power commands through state tracking so `lastPower` stays
accurate:

```cpp
// In scheduler tick, when turning on:
ble.write(cmd, "all");
for (size_t i = 0; i < ble.getStripCount(); i++) {
    BleStrip* s = ble.getStrip(i);
    if (s) s->lastPower = true;
}

// When turning off:
ble.write(cmd, "all");
for (size_t i = 0; i < ble.getStripCount(); i++) {
    BleStrip* s = ble.getStrip(i);
    if (s) s->lastPower = false;
}
```

### 3. On reconnect, force a scheduler re-evaluation

Add a `forceCheck()` method to Scheduler that resets `_activeFlags` and
`_lastCheck`, so the next `tick()` re-detects active entries and applies the
correct state (power on if inside window, no action if outside).

```cpp
// scheduler.h
void forceCheck() { _activeFlags = 0; _lastCheck = 0; }
```

Call it from the reconnect handler instead of the unconditional power-on:

```cpp
// elk_bridge.ino — on BLE reconnect success
scheduler.forceCheck();  // re-apply correct state on next tick
```

### 4. Guard pattern restore behind scheduler check

Only restore saved pattern if no schedule entry is currently inactive
(i.e., don't restore a pattern if the scheduler says lights should be off):

```cpp
if (!patternEngine.isPlaying() && !scheduler.shouldBeOff()) {
    patternEngine.loadFromNVS(bleManager);
}
```

Or simpler: let `forceCheck()` handle it — if the scheduler is active, it will
start the pattern; if not, lights stay off.

## Files to Modify

| File | Change |
|------|--------|
| `firmware/elk-bridge/src/elk_bridge.ino` | Remove unconditional power-on, add `scheduler.forceCheck()` on reconnect, guard pattern restore |
| `firmware/elk-bridge/src/scheduler.h` | Add `forceCheck()` method |
| `firmware/elk-bridge/src/scheduler.cpp` | Update strip state (`lastPower`) when sending power commands |

## Testing Strategy

1. **Schedule OFF → reconnect**: Set schedule to expire in 2 minutes. After
   lights go off, power-cycle a strip (pull USB briefly). Verify lights do NOT
   come back on after BLE reconnects.

2. **Schedule ON → reconnect**: While inside a schedule window, power-cycle a
   strip. Verify lights DO come back on (scheduler re-fires via `forceCheck`).

3. **No schedule → reconnect**: With no schedule entries, manually set a color,
   power-cycle a strip. Verify strip reconnects without unwanted state changes.

4. **Boot outside window**: Reboot bridge outside schedule window. Verify lights
   stay off after both strips connect.

5. **Status accuracy**: After scheduler turns lights off, verify `/status`
   reports `"power": false` for all strips.
