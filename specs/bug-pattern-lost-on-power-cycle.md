# Bug: Pattern Lost on Power Cycle

## Description

After a power cycle, the bridge fails to restore and resume the previously
playing pattern. The schedule window opens (18:00) and powers on the strips,
but they just show solid color — no pattern playback.

## Root Cause

Two reinforcing bugs in the boot → reconnect → enforce-off path:

### Bug 1: enforce-off calls pattern.stop() which clears NVS

```
Boot → loadFromNVS() → play("Easter dimmed") → pattern saved to NVS
Strip connects → forceCheck() → enforce-off fires (outside schedule)
  → pattern.stop()
    → clearNVS()        ← DESTROYS the saved pattern permanently
```

`stop()` (pattern_engine.cpp:88) calls `clearNVS()`, deleting `pat_active`
and `pat_data`. The enforce-off only needs to power off strips — it should
NOT destroy the saved pattern.

### Bug 2: `*` timer doesn't restore saved pattern

When the `*` schedule entry becomes active at 18:00, it only sends
`buildPowerCmd(true)`. It doesn't check for or restore any NVS-saved
pattern. The strips power on to whatever color they were last showing.

## Proposed Fix

### Fix 1: enforce-off should NOT stop the pattern engine

Replace `pattern.stop()` in enforce-off with just setting state to IDLE
without clearing NVS. Or better: don't touch the pattern at all during
enforce-off — just send power-off to strips.

In `scheduler.cpp`, both enforce-off blocks: remove `pattern.stop()`.

### Fix 2: `*` timer restores saved pattern on activate

When a `*` entry becomes active, after sending power-on, also call
`pattern.loadFromNVS(ble)` to restore any saved pattern. If no pattern
is saved, strips just power on (current behavior).

```cpp
if (strcmp(e.pattern, "*") == 0) {
    buildPowerCmd(true, cmd);
    ble.write(cmd, "all");
    // Also restore saved pattern if one exists
    pattern.loadFromNVS(ble);
}
```

## Files to Modify

| File | Change |
|------|--------|
| `firmware/elk-bridge/src/scheduler.cpp` | Remove pattern.stop() from enforce-off, add loadFromNVS to `*` activate |

## Testing

1. Set a pattern playing, power cycle outside schedule → pattern stays in NVS
2. Schedule window opens → power on + pattern resumes
3. Schedule window closes → power off, pattern stays in NVS for next cycle
4. Manual stop via dashboard → pattern cleared from NVS (existing behavior)
