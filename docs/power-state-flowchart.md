# Power State Flowchart — How We Know Lights Are On/Off

## The Core Problem

ELK-BLEDOM strips are **fire-and-forget** (WRITE_NO_RESPONSE). We never get
confirmation that a power command was received. The bridge maintains a
**best-guess** state (`BleStrip::lastPower`) updated only when we believe a
command was successfully sent.

## State Storage

```
BleStrip::lastPower   (bool, default: false)
  │
  │  Written by:
  │  ├── CommandDispatcher::handlePower()   — user clicks toggle
  │  ├── Scheduler::tick()                  — schedule window open/close
  │  │
  │  Read by:
  │  └── HttpServer (GET /status)           — reports to all clients
  │        │
  │        └── JSON: strips[].power: true/false
```

## Write Paths — What Sets lastPower

```
                    ┌─────────────────────────────────┐
                    │     1. USER TOGGLE (dashboard)    │
                    └──────────────┬──────────────────┘
                                   │
                    pwCb.onchange → pw(checked)
                                   │
                    POST /api/power {on: true/false}
                                   │
                    HttpServer → CommandDispatcher::handlePower()
                                   │
                    ┌──────────────┴──────────────────┐
                    │  buildPowerCmd(on) → BLE write   │
                    │  if (ok):                        │
                    │    strip.lastPower = on    ◄──── │  STATE UPDATED
                    └─────────────────────────────────┘


                    ┌─────────────────────────────────┐
                    │  2. SCHEDULER — window opens      │
                    └──────────────┬──────────────────┘
                                   │
                    tick() every 2s, checks isEntryActive()
                                   │
                    entry !wasActive → isActive (transition detected)
                                   │
                    ┌──────────────┴──────────────────┐
                    │  pattern = "*":                   │
                    │    buildPowerCmd(true) → BLE      │
                    │    strip.lastPower = true  ◄──── │  STATE UPDATED
                    │                                  │
                    │  pattern = "Easter" (named):     │
                    │    buildPowerCmd(true) → BLE      │
                    │    strip.lastPower = true  ◄──── │  STATE UPDATED
                    │    loadUserPreset → pattern.play  │
                    └─────────────────────────────────┘


                    ┌─────────────────────────────────┐
                    │  3. SCHEDULER — window closes     │
                    └──────────────┬──────────────────┘
                                   │
                    entry wasActive → !isActive (transition detected)
                                   │
                    ┌──────────────┴──────────────────┐
                    │  pattern = "*":                   │
                    │    buildPowerCmd(false) → BLE     │
                    │    strip.lastPower = false ◄──── │  STATE UPDATED
                    │                                  │
                    │  pattern = named:                 │
                    │    pattern.stop()                 │
                    │    buildPowerCmd(false) → BLE     │
                    │    strip.lastPower = false ◄──── │  STATE UPDATED
                    └─────────────────────────────────┘


                    ┌─────────────────────────────────┐
                    │  4. BLE RECONNECT                 │
                    └──────────────┬──────────────────┘
                                   │
                    bleManager.connectState() == ASYNC_DONE
                                   │
                    scheduler.forceCheck()
                    (resets _activeFlags → next tick re-evaluates)
                                   │
                    ┌──────────────┴──────────────────┐
                    │  If inside schedule window:      │
                    │    → path 2 fires (power on)     │
                    │                                  │
                    │  If outside schedule window:     │
                    │    → no action, lastPower stays  │
                    │      false (default after boot)  │
                    └─────────────────────────────────┘


                    ┌─────────────────────────────────┐
                    │  5. BOOT                          │
                    └──────────────┬──────────────────┘
                                   │
                    BleStrip created with lastPower = false
                                   │
                    No power command sent until scheduler
                    or user acts. State is accurate.
```

## Read Path — How the Dashboard Learns Power State

```
    ESP32-C3 Bridge                           Browser Dashboard
    ──────────────                            ──────────────────

    GET /status
    ├── for each strip:                       poll() every 1s
    │   └── "power": strip.lastPower   ──────►  S.strips[i].power
    │                                              │
    │                                         syncUI()
    │                                              │
    │                                         var st0 = rStrip()
    │                                         $('pwCb').checked = st0.power
    │                                              │
    │                                         ┌────┴────────────────┐
    │                                         │  Toggle switch:     │
    │                                         │  checked = ON       │
    │                                         │  unchecked = OFF    │
    │                                         │  green bg = ON      │
    │                                         │  gray bg = OFF      │
    │                                         └─────────────────────┘
    │
    │                                         updFav()
    │                                              │
    │                                         ┌────┴────────────────┐
    │                                         │  power && color?    │
    │                                         │  yes → colored icon │
    │                                         │  no  → default globe│
    │                                         └─────────────────────┘
```

## Schedule Guard — When Toggle Is Disabled

```
    guardPw()  — called from syncUI() (every 1s),
                 loadSched(), saveScE(), uScTime(), uScPat()

    ┌──────────────────────────────────────┐
    │  Is any schedule entry currently     │
    │  active (time in window + day match)?│
    └──────────────┬───────────────────────┘
                   │
           ┌───────┴───────┐
           │               │
          YES              NO
           │               │
    ┌──────┴──────┐  ┌─────┴──────┐
    │ pwCb.disabled│  │ pwCb.enabled│
    │ = true      │  │ = false     │
    │             │  │             │
    │ Tooltip:    │  │ Tooltip:    │
    │ "Managed by │  │ (none)      │
    │  schedule"  │  │             │
    │             │  │             │
    │ Dimmed 35%  │  │ Full opacity│
    └─────────────┘  └─────────────┘

    Active check (client-side, mirrors scheduler.cpp logic):

    for each SC entry:
      enabled? AND day matches scWday?
        startMins <= endMins (same-day):
          now >= start AND now < end → ACTIVE
        startMins > endMins (overnight):
          now >= start OR now < end → ACTIVE
```

## Edge Cases & Known Limitations

```
    ┌──────────────────────────────────────────────────────────┐
    │ LIMITATION: No read-back from strips                      │
    │                                                           │
    │ If someone uses Lotus Lantern or nRF Connect to power    │
    │ off a strip directly, the bridge doesn't know.           │
    │ lastPower stays true. Toggle shows ON incorrectly.       │
    │                                                           │
    │ Mitigation: None possible — WRITE_NO_RESPONSE protocol.  │
    │ Could enable FFF4 notifications, but strips don't send   │
    │ power state changes on that characteristic.               │
    └──────────────────────────────────────────────────────────┘

    ┌──────────────────────────────────────────────────────────┐
    │ LIMITATION: Physical power cycle                          │
    │                                                           │
    │ If a strip is unplugged and replugged, it boots into an  │
    │ unknown state (usually off). Bridge lastPower is stale   │
    │ until BLE reconnects and scheduler re-evaluates via      │
    │ forceCheck().                                             │
    │                                                           │
    │ After reconnect:                                          │
    │   Inside schedule → scheduler powers on → state correct  │
    │   Outside schedule → lastPower = false (boot default)    │
    │     → state correct (strip is off, we say off)            │
    └──────────────────────────────────────────────────────────┘

    ┌──────────────────────────────────────────────────────────┐
    │ TIMING: Scheduler granularity                             │
    │                                                           │
    │ Scheduler checks every 2s (SCHEDULE_CHECK_INTERVAL_MS).  │
    │ Worst case: 2s + BLE write (~200ms for 2 strips).        │
    │ Dashboard poll is 1s, so toggle may lag 1-3s behind       │
    │ the physical state change.                                │
    └──────────────────────────────────────────────────────────┘
```

## Summary of State Flow

```
    SOURCES OF TRUTH            STATE STORAGE          UI CONTROLS
    ──────────────────          ─────────────          ───────────

    User toggle ──────┐
                      ├──► BleStrip::lastPower ──► /status JSON
    Scheduler tick ───┤         (per strip)            │
                      │                                │
    Boot default ─────┘    default: false          poll() 1s
      (false)                                          │
                                                  ┌────┴────┐
                                                  │ syncUI  │
                                                  ├─────────┤
                                                  │ Toggle   │ ← checked = power
                                                  │ Favicon  │ ← color if power+rgb
                                                  │ Guard    │ ← disabled if sched
                                                  │ Dev list │ ← "OFF" badge
                                                  └─────────┘
```
