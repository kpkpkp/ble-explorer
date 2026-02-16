# ELK Bridge — Arduino Firmware Architecture

Target: ESP32-C3 SuperMini
Framework: Arduino ESP32 + NimBLE-Arduino + ESPAsyncWebServer

## System Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                        ESP32-C3 SuperMini                          │
│                                                                     │
│  ┌──────────┐    ┌──────────────┐    ┌───────────┐                 │
│  │  WiFi    │    │  HTTP Server │    │   OTA     │                 │
│  │  Manager │    │  (Async)     │    │  Updater  │                 │
│  │          │    │              │    │           │                 │
│  │ STA mode │    │ GET  /status │    │ /update   │                 │
│  │ mDNS:    │    │ POST /api/*  │    │ endpoint  │                 │
│  │ elk-     │    │ GET  /       │    └───────────┘                 │
│  │ bridge.  │    │ (dashboard)  │                                   │
│  │ local    │    └──────┬───────┘                                   │
│  └──────────┘           │                                           │
│                         │ JSON command                              │
│                         ▼                                           │
│                ┌────────────────┐                                   │
│                │   Command      │                                   │
│                │   Dispatcher   │◄──── validates, routes            │
│                └───┬───────┬───┘                                   │
│                    │       │                                        │
│           ┌────────┘       └────────┐                              │
│           ▼                         ▼                              │
│  ┌─────────────────┐    ┌────────────────┐                        │
│  │  Pattern Engine  │    │  BLE Manager   │                        │
│  │                  │    │                │                        │
│  │  step sequencer  │───►│  NimBLE client │                        │
│  │  fade interpolat.│    │  2 connections │                        │
│  │  loop control    │    │  GATT writes   │                        │
│  │  timing (millis) │    │  auto-reconnect│                        │
│  └──────────────────┘    └───────┬────────┘                        │
│                                  │                                  │
│  ┌──────────┐                    │ BLE 5.0                         │
│  │  Status  │◄── aggregates ─────┘                                 │
│  │  LED     │    connection +                                      │
│  │  GPIO8   │    playback state                                    │
│  └──────────┘                                                      │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
         │                                    │
         │ WiFi 2.4 GHz                       │ BLE 5.0
         │                                    │
         ▼                                    ▼
  ┌──────────────┐                  ┌──────────────────┐
  │ Home Network │                  │ ELK-BLEDOM0B     │
  │              │                  │ BE:67:00:35:04:9B│
  │ Android App  │                  ├──────────────────┤
  │ Web Browser  │                  │ ELK-BLEDOM       │
  │ Home Assist. │                  │ BE:67:00:35:11:78│
  └──────────────┘                  └──────────────────┘
```

## Module Breakdown

### 1. WiFi Manager

```
┌─────────────────────────────────────────┐
│              WiFiManager                 │
├─────────────────────────────────────────┤
│                                         │
│  setup()                                │
│  ├── Read SSID/pass from NVS           │
│  ├── WiFi.mode(WIFI_STA)               │
│  ├── WiFi.begin(ssid, pass)            │
│  ├── Wait for connection (10s timeout) │
│  ├── If fail → start AP mode           │
│  │    └── "ELK-Bridge-XXXX" (open)     │
│  │        192.168.4.1 → config portal  │
│  └── MDNS.begin("elk-bridge")          │
│      └── addService("_elk", "_tcp", 80)│
│                                         │
│  loop()                                 │
│  ├── Check WiFi.status()               │
│  ├── If disconnected → auto-reconnect  │
│  └── MDNS housekeeping                 │
│                                         │
│  State:                                 │
│  ├── enum { CONNECTING, CONNECTED,     │
│  │          AP_MODE, DISCONNECTED }     │
│  ├── ip_address : IPAddress            │
│  ├── rssi : int8_t                     │
│  └── uptime_ms : uint32_t             │
│                                         │
└─────────────────────────────────────────┘

  Fallback flow:

  Power on ──► Try STA ──► Connected ──► mDNS up
                 │                         │
                 │ timeout                 │ lost
                 ▼                         ▼
              AP Mode ◄──── retry fails ◄── Reconnect
              (config)                      (3 attempts)
```

### 2. HTTP Server (Async)

```
┌──────────────────────────────────────────────────────────────────┐
│                     ESPAsyncWebServer :80                         │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│  Route Table:                                                     │
│  ┌──────────────┬────────┬───────────────────────────────────┐   │
│  │ Path         │ Method │ Handler                           │   │
│  ├──────────────┼────────┼───────────────────────────────────┤   │
│  │ /            │ GET    │ Serve embedded HTML dashboard     │   │
│  │ /status      │ GET    │ → JSON: wifi, ble, playback state │   │
│  │ /api/color   │ POST   │ → Dispatcher::color(r,g,b)       │   │
│  │ /api/bright  │ POST   │ → Dispatcher::brightness(0-255)  │   │
│  │ /api/power   │ POST   │ → Dispatcher::power(on/off)      │   │
│  │ /api/effect  │ POST   │ → Dispatcher::effect(code,speed) │   │
│  │ /api/raw     │ POST   │ → Dispatcher::raw(9 bytes)       │   │
│  │ /api/pattern │ POST   │ → PatternEngine::play(steps)     │   │
│  │ /api/stop    │ POST   │ → PatternEngine::stop()          │   │
│  │ /api/scan    │ GET    │ → BleManager::scan()             │   │
│  │ /api/connect │ POST   │ → BleManager::connect(addr)      │   │
│  │ /api/discon  │ POST   │ → BleManager::disconnect(addr)   │   │
│  │ /api/config  │ POST   │ → Save WiFi creds to NVS         │   │
│  │ /update      │ POST   │ → OTA flash (multipart upload)   │   │
│  └──────────────┴────────┴───────────────────────────────────┘   │
│                                                                   │
│  Request flow:                                                    │
│                                                                   │
│  HTTP request ──► parse JSON body ──► validate fields             │
│       │                                    │                      │
│       │ malformed                           │ ok                  │
│       ▼                                    ▼                      │
│    400 + error JSON              dispatch to module               │
│                                        │                          │
│                              ┌─────────┴─────────┐               │
│                              │                    │               │
│                              ▼                    ▼               │
│                         immediate            enqueued             │
│                         (color,pwr)          (pattern,scan)       │
│                              │                    │               │
│                              ▼                    ▼               │
│                        200 + result          202 + accepted       │
│                                                                   │
│  Response format (all endpoints):                                 │
│  { "ok": true, "data": { ... } }                                │
│  { "ok": false, "error": "message" }                             │
│                                                                   │
│  CORS: Access-Control-Allow-Origin: *                             │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

### 3. BLE Manager (NimBLE)

```
┌──────────────────────────────────────────────────────────────────┐
│                        BleManager                                 │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│  Connections (max 2):                                             │
│  ┌────────────────────────────────────────────────────────┐      │
│  │ BleStrip                                                │      │
│  │ ├── address : String          "BE:67:00:35:04:9B"      │      │
│  │ ├── client  : NimBLEClient*   (NimBLE connection)      │      │
│  │ ├── writeChar : NimBLERemoteCharacteristic* (0xFFF3)   │      │
│  │ ├── state   : enum { DISCONNECTED, CONNECTING,         │      │
│  │ │                     DISCOVERING, READY, LOST }        │      │
│  │ ├── retries : uint8_t                                   │      │
│  │ └── lastSeen : uint32_t      (millis of last success)  │      │
│  └────────────────────────────────────────────────────────┘      │
│                                                                   │
│  Connection lifecycle:                                            │
│                                                                   │
│  connect(addr)                                                    │
│  ├── NimBLEDevice::createClient()                                │
│  ├── client->connect(addr)           ◄── 10s timeout             │
│  ├── client->getService(0xFFF0)                                  │
│  ├── svc->getCharacteristic(0xFFF3)                              │
│  ├── chr->writeType = WRITE_NR                                   │
│  └── state = READY                                               │
│                                                                   │
│  write(cmd[9], target)                                            │
│  ├── for each matching strip:                                    │
│  │   ├── if state != READY → skip                                │
│  │   ├── writeChar->writeValue(cmd, 9, false)  ×2               │
│  │   └── delay(50) between retries                               │
│  └── return success/fail                                         │
│                                                                   │
│  Auto-reconnect (checked every 5s in loop):                      │
│                                                                   │
│  READY ────── write fails ────► LOST                              │
│    ▲                              │                               │
│    │                              │ attempt reconnect             │
│    │                              ▼                               │
│    └──── success ◄──── CONNECTING                                 │
│                           │                                       │
│                           │ 3 failures                            │
│                           ▼                                       │
│                      DISCONNECTED                                 │
│                        (give up,                                  │
│                         await manual                              │
│                         /api/connect)                             │
│                                                                   │
│  scan()                                                           │
│  ├── NimBLEDevice::getScan()->start(5s)                          │
│  ├── Filter by name prefix "ELK-BLEDOM"                          │
│  └── Return list of { name, address, rssi }                      │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘

  Write timing (per strip, per command):

  ┌──────────┐  50ms  ┌──────────┐
  │ Write #1 │───────►│ Write #2 │  (reliability, matches Lotus Lantern)
  └──────────┘        └──────────┘
       9 bytes             9 bytes
    WRITE_NO_RESP       WRITE_NO_RESP

  Multi-strip write (target=ALL):

  Strip1: [ W1 ][ 50ms ][ W2 ]
  Strip2:                      [ W1 ][ 50ms ][ W2 ]
                                (sequential, not interleaved)
```

### 4. Command Dispatcher

```
┌──────────────────────────────────────────────────────────────────┐
│                      CommandDispatcher                            │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│  Translates high-level JSON commands into 9-byte ELK-BLEDOM      │
│  protocol frames and sends them via BleManager.                   │
│                                                                   │
│  ┌──────────┐     ┌────────────┐     ┌───────────┐              │
│  │ JSON in  │────►│ Build 9-   │────►│ BLE write │              │
│  │ {r,g,b}  │     │ byte frame │     │ to strips │              │
│  └──────────┘     └────────────┘     └───────────┘              │
│                                                                   │
│  color(r, g, b):                                                 │
│    → 7E 07 05 03 RR GG BB 10 EF                                 │
│                                                                   │
│  brightness(level):                                               │
│    → 7E 04 01 LL 01 FF FF 00 EF                                 │
│                                                                   │
│  power(on):                                                       │
│    → 7E 04 04 01 00 00 00 00 EF  (on)                           │
│    → 7E 04 04 00 00 00 FF 00 EF  (off)                          │
│                                                                   │
│  effect(code, speed):                                             │
│    → 7E 05 03 CC SS FF FF 00 EF                                 │
│                                                                   │
│  raw(hex_string):                                                 │
│    → parse hex → 9 bytes → write verbatim                        │
│                                                                   │
│  Target routing:                                                  │
│  ┌──────────────────────────────────────────┐                    │
│  │ JSON field "target" (optional):          │                    │
│  │   "all"    → write to both strips        │                    │
│  │   "strip1" → write to connection 0 only  │                    │
│  │   "strip2" → write to connection 1 only  │                    │
│  │   omitted  → defaults to "all"           │                    │
│  └──────────────────────────────────────────┘                    │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

### 5. Pattern Engine

```
┌──────────────────────────────────────────────────────────────────┐
│                       PatternEngine                               │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│  Non-blocking step sequencer driven by millis() in loop().        │
│  No threads — cooperative scheduling via state machine.           │
│                                                                   │
│  State:                                                           │
│  ├── steps[]     : PatternStep array (heap-allocated)            │
│  ├── stepCount   : uint8_t                                       │
│  ├── currentStep : uint8_t                                       │
│  ├── loop        : bool                                          │
│  ├── state       : enum { IDLE, HOLD, FADING }                  │
│  ├── stepStartMs : uint32_t                                      │
│  ├── prevColor   : RGB                                           │
│  └── currColor   : RGB                                           │
│                                                                   │
│  PatternStep:                                                     │
│  ├── r, g, b     : uint8_t                                      │
│  ├── brightness  : uint8_t  (0-100 → mapped to 0-255)           │
│  ├── durationMs  : uint16_t                                      │
│  ├── transition  : enum { CUT, FADE }                            │
│  └── fadeTimeMs  : uint16_t                                      │
│                                                                   │
│  Tick (called from loop()):                                       │
│                                                                   │
│  ┌──────┐                                                        │
│  │ IDLE │ ◄──────── stop() called                                │
│  └──┬───┘                                                        │
│     │ play(steps, loop)                                          │
│     ▼                                                             │
│  ┌──────────────────────────────────────────────┐                │
│  │ Start step[currentStep]                       │                │
│  │ ├── if transition == CUT                      │                │
│  │ │   ├── send color + brightness immediately   │                │
│  │ │   └── state = HOLD                          │                │
│  │ └── if transition == FADE                     │                │
│  │     ├── record prevColor                      │                │
│  │     └── state = FADING                        │                │
│  └──────────┬────────────────────┬───────────────┘                │
│             │                    │                                 │
│             ▼                    ▼                                 │
│  ┌──────────────┐     ┌──────────────────┐                       │
│  │     HOLD     │     │     FADING       │                       │
│  │              │     │                  │                        │
│  │ wait for     │     │ every ~33ms:     │                        │
│  │ durationMs   │     │ t = elapsed /    │                        │
│  │              │     │     fadeTimeMs    │                        │
│  │              │     │ lerp(prev,curr,t)│                        │
│  │              │     │ send interpolated│                        │
│  │              │     │ color + bright   │                        │
│  │              │     │                  │                        │
│  │              │     │ when t >= 1.0:   │                        │
│  │              │     │ state = HOLD     │                        │
│  │              │     │ reset timer for  │                        │
│  │              │     │ remaining hold   │                        │
│  └──────┬───────┘     └────────┬─────────┘                       │
│         │ duration elapsed      │                                 │
│         ▼                       │                                 │
│  ┌──────────────────────────────┘                                │
│  │ Advance to next step                                          │
│  │ ├── currentStep++                                             │
│  │ ├── if currentStep >= stepCount                               │
│  │ │   ├── if loop → currentStep = 0                             │
│  │ │   └── else → state = IDLE                                   │
│  │ └── goto "Start step"                                         │
│  └───────────────────────────────────────────────────────────────┘
│                                                                   │
│  Fade interpolation detail:                                       │
│                                                                   │
│  prevColor ──────────────────────► currColor                      │
│  t=0.0     t=0.33   t=0.66      t=1.0                           │
│  │          │         │           │                               │
│  ▼          ▼         ▼           ▼                               │
│  R: lerp(pR, cR, t) = pR + (cR - pR) * t                        │
│  G: lerp(pG, cG, t) = pG + (cG - pG) * t                        │
│  B: lerp(pB, cB, t) = pB + (cB - pB) * t                        │
│  Br: lerp(pBr, cBr, t)                                           │
│                                                                   │
│  ~30 BLE writes per second during fade (33ms interval)            │
│  Each write = 2x 9-byte frames with 50ms gap = ~100ms per strip  │
│  With 2 strips: ~200ms per fade frame → effective ~5 fps          │
│  Fade resolution is coarser with 2 strips but visually adequate   │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

### 6. Status LED

```
┌──────────────────────────────────────────────────────────────────┐
│                      StatusLed (GPIO8)                            │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│  Non-blocking blink patterns driven by millis() in loop().        │
│  LED is active-low (LOW = on, HIGH = off).                        │
│                                                                   │
│  ┌──────────────────────────┬──────────────────────────────────┐ │
│  │ System State             │ LED Pattern                      │ │
│  ├──────────────────────────┼──────────────────────────────────┤ │
│  │ WiFi connecting          │ Fast blink (100ms on/off)        │ │
│  │ WiFi connected, no BLE  │ Slow blink (1s on/off)           │ │
│  │ WiFi + BLE ready        │ Solid on                         │ │
│  │ AP mode (config portal) │ Double blink (2 quick, 1s pause) │ │
│  │ Pattern playing          │ Pulse (breathe effect)           │ │
│  │ OTA in progress          │ Very fast blink (50ms on/off)    │ │
│  │ Error                    │ Triple blink (3 quick, 2s pause) │ │
│  └──────────────────────────┴──────────────────────────────────┘ │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

### 7. Config / NVS Storage

```
┌──────────────────────────────────────────────────────────────────┐
│                     NVS Preferences                               │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│  Namespace: "elk-bridge"                                         │
│                                                                   │
│  ┌────────────────┬──────────┬────────────────────────────────┐  │
│  │ Key            │ Type     │ Description                    │  │
│  ├────────────────┼──────────┼────────────────────────────────┤  │
│  │ wifi_ssid      │ String   │ Home WiFi SSID                │  │
│  │ wifi_pass      │ String   │ Home WiFi password             │  │
│  │ strip1_addr    │ String   │ MAC of strip 1 (auto-connect) │  │
│  │ strip2_addr    │ String   │ MAC of strip 2 (auto-connect) │  │
│  │ last_color_r   │ uint8    │ Last set color R              │  │
│  │ last_color_g   │ uint8    │ Last set color G              │  │
│  │ last_color_b   │ uint8    │ Last set color B              │  │
│  │ last_bright    │ uint8    │ Last brightness 0-255          │  │
│  │ power_state    │ bool     │ Last power on/off              │  │
│  │ boot_count     │ uint32   │ Uptime tracking                │  │
│  └────────────────┴──────────┴────────────────────────────────┘  │
│                                                                   │
│  Write policy: only on user-initiated actions, never in loops.    │
│  Read: once at boot to restore last state.                        │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

## Arduino Sketch Structure

```
elk_bridge/
├── elk_bridge.ino          Main sketch: setup() + loop()
├── wifi_manager.h/.cpp     WiFi STA/AP, mDNS, reconnect
├── http_server.h/.cpp      Route definitions, JSON parsing
├── ble_manager.h/.cpp      NimBLE client, connect/write/scan
├── command_dispatch.h/.cpp  JSON → 9-byte protocol frames
├── pattern_engine.h/.cpp   Non-blocking step sequencer
├── elk_protocol.h          Protocol constants + frame builders
├── status_led.h/.cpp       GPIO8 blink patterns
├── config.h                Pin assignments, timeouts, defaults
└── html.h                  Embedded HTML dashboard (gzipped)
```

## Main Loop Timing

```
loop() — runs ~1000 iterations/sec when idle

  ┌─────────────────────────────────────────────────────────────┐
  │                    loop() iteration                          │
  │                                                              │
  │  ① WiFi check              ~0ms    (only acts every 5s)    │
  │  ② PatternEngine::tick()   ~0ms    (millis check, no-op    │
  │  │                                  unless time to act)     │
  │  │  └─ if FADING + 33ms elapsed:                            │
  │  │     └─ interpolate + BLE write  ~100-200ms               │
  │  ③ BLE reconnect check     ~0ms    (only acts every 5s)    │
  │  ④ StatusLed::tick()       ~0ms    (millis check)          │
  │  ⑤ yield()                          (feed watchdog)         │
  │                                                              │
  │  HTTP requests handled asynchronously by ESPAsyncWebServer  │
  │  — interrupt-driven, not polled in loop()                    │
  │                                                              │
  └─────────────────────────────────────────────────────────────┘

  Time budget per loop iteration:
  ┌──────────────────────────────────────────────────────────────┐
  │ Idle:    < 1ms total                                         │
  │ Fade:    ~200ms (2 strips × 2 writes × 50ms gap)            │
  │ Command: ~100ms (1 strip × 2 writes × 50ms gap)             │
  │ Scan:    5000ms (async, BLE stack handles callbacks)         │
  └──────────────────────────────────────────────────────────────┘
```

## Boot Sequence

```
setup()
  │
  ├── Serial.begin(115200)
  ├── StatusLed::init(GPIO8)                    LED: fast blink
  │
  ├── Preferences::begin("elk-bridge")
  │   └── load wifi_ssid, wifi_pass,
  │       strip1_addr, strip2_addr,
  │       last color/brightness/power
  │
  ├── WiFiManager::begin()
  │   ├── WiFi.mode(WIFI_STA)
  │   ├── WiFi.begin(ssid, pass)
  │   ├── wait up to 10s ...
  │   │   ├── connected?                        LED: slow blink
  │   │   │   └── MDNS.begin("elk-bridge")
  │   │   └── timeout?
  │   │       └── start AP: "ELK-Bridge-XXXX"  LED: double blink
  │   └── log IP address
  │
  ├── BleManager::init()
  │   ├── NimBLEDevice::init("ELK-Bridge")
  │   ├── NimBLEDevice::setPower(ESP_PWR_LVL_P9)  (+9 dBm)
  │   └── if saved addresses:
  │       ├── connect(strip1_addr)              LED: slow blink
  │       └── connect(strip2_addr)
  │           └── on success:                   LED: solid on
  │               restore last color/brightness/power
  │
  ├── HttpServer::begin()
  │   ├── register all routes
  │   ├── enable CORS
  │   └── server.begin()
  │
  ├── ArduinoOTA::begin()
  │
  └── log "ELK Bridge ready at elk-bridge.local"
      └── Serial + LED confirmation
```

## Memory Budget (estimated)

```
Flash (4 MB total):
┌──────────────────────────────────┬──────────┐
│ Component                        │ Size     │
├──────────────────────────────────┼──────────┤
│ Arduino core + WiFi + BLE stack  │ ~1.2 MB  │
│ NimBLE library                   │ ~200 KB  │
│ ESPAsyncWebServer                │ ~50 KB   │
│ Application code                 │ ~50 KB   │
│ Embedded HTML (gzipped)          │ ~10 KB   │
│ OTA partition (second app slot)  │ ~1.5 MB  │
│ NVS partition                    │ ~20 KB   │
│ Spiffs/LittleFS (unused)         │ ~900 KB  │
├──────────────────────────────────┼──────────┤
│ Total used                       │ ~3.0 MB  │
│ Free                             │ ~1.0 MB  │
└──────────────────────────────────┴──────────┘

RAM (400 KB SRAM):
┌──────────────────────────────────┬──────────┐
│ Component                        │ Size     │
├──────────────────────────────────┼──────────┤
│ WiFi buffers                     │ ~40 KB   │
│ BLE stack (NimBLE)               │ ~30 KB   │
│ HTTP server + request buffers    │ ~10 KB   │
│ Pattern engine (32 steps max)    │ ~0.5 KB  │
│ Application variables            │ ~2 KB    │
│ Stack                            │ ~8 KB    │
├──────────────────────────────────┼──────────┤
│ Total used                       │ ~90 KB   │
│ Free heap                        │ ~310 KB  │
└──────────────────────────────────┴──────────┘
```

## Library Dependencies

```
Platform: espressif32 (Arduino framework)
Board: esp32-c3-devkitm-1 (or esp32c3 in Arduino board manager)

Libraries:
├── NimBLE-Arduino  ^1.4.0    BLE client (smaller than default BLE stack)
├── ESPAsyncWebServer ^1.2.3  Non-blocking HTTP server
├── AsyncTCP        ^1.1.1    TCP layer for ESPAsyncWebServer
├── ArduinoJson     ^7.0.0    JSON parsing/serialization
├── ArduinoOTA      (built-in) Over-the-air updates
├── Preferences     (built-in) NVS key-value storage
├── WiFi            (built-in) WiFi STA/AP
└── ESPmDNS         (built-in) mDNS responder
```
