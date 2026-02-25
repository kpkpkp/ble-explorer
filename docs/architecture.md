# ELK Bridge — Architecture Documentation

## System Overview

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         USER INTERFACES                                  │
│                                                                          │
│   Android App (Kotlin/Compose)          Web Dashboard (Vanilla JS)       │
│   ┌────────────────────────┐            ┌─────────────────────────┐      │
│   │  DashboardScreen       │            │  Embedded in firmware    │      │
│   │  PatternEditor         │            │  PROGMEM (13KB)          │      │
│   │  ColorPicker           │            │  Polls /status @ 1s      │      │
│   │  SettingsScreen        │            │  16 color swatches       │      │
│   │  CalibrationWizard     │            │  27 effects, 11 presets  │      │
│   └────────┬───────────────┘            └───────────┬─────────────┘      │
│            │                                        │                    │
│     ┌──────┴──────┐                                 │                    │
│     │DeviceControl│ interface                       │                    │
│     ├─────────────┤                                 │                    │
│     │ BLE direct  │ HttpBridge │                    │                    │
│     └──┬──────────┴─────┬─────┘                    │                    │
│        │                │                           │                    │
└────────┼────────────────┼───────────────────────────┼────────────────────┘
         │                │                           │
    BLE GATT         HTTP/JSON                   HTTP/JSON
    (direct)         (WiFi)                      (WiFi)
         │                │                           │
         │                ▼                           ▼
         │    ┌───────────────────────────────────────────────┐
         │    │              ESP32-C3 BRIDGE                   │
         │    │                                               │
         │    │  ┌──────────┐  ┌───────────────────────────┐  │
         │    │  │WiFiManager│  │    ESPAsyncWebServer      │  │
         │    │  │ STA/AP    │  │    15 REST endpoints      │  │
         │    │  │ mDNS      │  │    JSON request/response  │  │
         │    │  │ UDP beacon│  │    CORS enabled            │  │
         │    │  └──────────┘  └──────────┬────────────────┘  │
         │    │                           │                    │
         │    │                ┌──────────▼────────────────┐   │
         │    │                │   CommandDispatcher        │   │
         │    │                │   JSON → 9-byte frames     │   │
         │    │                │   State tracking per strip  │   │
         │    │                └──────────┬────────────────┘   │
         │    │                           │                    │
         │    │  ┌──────────┐  ┌──────────▼────────────────┐   │
         │    │  │  Pattern  │  │     BLE Manager           │   │
         │    │  │  Engine   ├──►     (NimBLE client)       │   │
         │    │  │  millis() │  │     Max 3 connections     │   │
         │    │  │  ~30fps   │  │     Auto-reconnect        │   │
         │    │  └──────────┘  └──────────┬────────────────┘   │
         │    │                           │                    │
         │    │  ┌──────────┐             │                    │
         │    │  │StatusLed │  GPIO8      │                    │
         │    │  │(blue LED)│             │                    │
         │    │  └──────────┘             │                    │
         │    └───────────────────────────┼────────────────────┘
         │                                │
         │           BLE 5.0 GATT         │
         │           WRITE_NO_RESPONSE    │
         │           9-byte frames × 2    │
         │                                │
         ▼                                ▼
    ┌──────────────────────────────────────────┐
    │           ELK-BLEDOM LED STRIPS           │
    │                                          │
    │  Service: 0xFFF0                         │
    │  Write:   0xFFF3 (no response)           │
    │  Notify:  0xFFF4                         │
    │  Frame:   7E [id][cmd][sub][p1-p4][f] EF │
    │  Chipset: Tuya TY1067R24R44V05           │
    └──────────────────────────────────────────┘
```

## Languages & Runtimes

| Language | Where | Purpose | Version |
|----------|-------|---------|---------|
| C++ (Arduino) | Firmware | ESP32-C3 bridge | C++17 (RISC-V toolchain) |
| Kotlin | Android app | UI + BLE + HTTP client | 1.9.24 |
| JavaScript (ES5) | Web dashboard | Embedded browser UI | Vanilla, no build step |
| HTML/CSS | Web dashboard | Layout + styling | Inline in PROGMEM |
| Python 3 | Scripts | RE tooling, calibration, testing | 3.7+ |
| JSON | API layer | Request/response serialization | RFC 8259 |
| Bash/PowerShell | Scripts | Build, deploy, flash | — |

## Protocols & Transports

### BLE (Bluetooth Low Energy 5.0)

```
Phone/Bridge → GATT Write (no response) → ELK-BLEDOM strip

Service UUID:  0xFFF0
Write char:    0xFFF3  (WRITE_NO_RESPONSE, handle 0x0009)
Notify char:   0xFFF4

Frame format (9 bytes, fixed):
┌────┬────┬─────┬─────┬────┬────┬────┬────┬────┐
│ 7E │ id │ cmd │ sub │ p1 │ p2 │ p3 │ p4 │ EF │
└────┴────┴─────┴─────┴────┴────┴────┴────┴────┘

Commands:
  Color:       7E 07 05 03 [R] [G] [B] 00 EF
  Brightness:  7E 04 01 [level] 00 00 00 00 EF
  Power ON:    7E 04 04 01 FF FF FF 00 EF
  Power OFF:   7E 04 04 00 FF FF FF 00 EF
  Effect:      7E 05 03 [code] [speed] 00 00 00 EF
  Speed:       7E 04 02 [speed] 00 00 00 00 EF

Effect codes: 0x80-0x9A (27 effects)

Reliability: Each command sent 2× with 50ms gap (fire-and-forget)
Connection interval: 60-120ms (tuned for WiFi/BLE coexistence)
```

### HTTP REST API (JSON)

```
Client (App/Browser) → HTTP/JSON → ESP32-C3 (port 80)

Content-Type: application/json
CORS: Access-Control-Allow-Origin: *

Response format:
  Success: { "ok": true, "data": { ... } }
  Error:   { "ok": false, "error": "message" }

Endpoints (15 routes):
  GET  /              → Web dashboard (HTML)
  GET  /status        → Bridge + strip state (poll target)
  POST /api/color     → Set RGB color
  POST /api/brightness → Set brightness (0-100)
  POST /api/power     → Power on/off
  POST /api/effect    → Trigger built-in effect (0x80-0x9A)
  POST /api/speed     → Set effect speed (1-10)
  POST /api/colortemp → Set warm/cold white
  POST /api/raw       → Send raw 9-byte hex frame
  POST /api/pattern   → Start pattern playback (max 32 steps)
  POST /api/stop      → Stop pattern playback
  GET  /api/scan      → Async BLE scan (poll for results)
  POST /api/connect   → Connect to strip by MAC (async)
  POST /api/disconnect → Disconnect strip
  GET  /api/devices   → List connected strips
  POST /api/config    → Update WiFi/strip config (triggers reboot)
  POST /update        → OTA firmware update (multipart form)

Target selector (all command endpoints):
  "all"    → all connected strips
  "strip1" → first strip only
  "strip2" → second strip only
  "BE:67:..." → specific MAC address
```

### Discovery Protocols

```
mDNS:
  Hostname: elk-bridge.local
  Service:  _elk._tcp (port 80)
  Used by:  Android BridgeDiscovery, web browser

UDP Beacon:
  Port: 5555
  Interval: 5 seconds
  Payload: {"name":"ELK-Bridge","ip":"192.168.86.22"}
  Used by:  Android BridgeDiscovery (fallback for mDNS failures)

WiFi AP Fallback:
  SSID: ELK-Bridge-XXXX (last 4 of MAC)
  IP: 192.168.4.1 (captive portal)
  Used when: STA connection fails
```

## Libraries & Dependencies

### ESP32-C3 Firmware

| Library | Version | Purpose |
|---------|---------|---------|
| NimBLE-Arduino | ^1.4.0 | BLE 5.0 GATT client |
| ESPAsyncWebServer | ^1.2.3 | Async HTTP server |
| AsyncTCP | ^1.1.1 | TCP transport for web server |
| ArduinoJson | ^7.0.0 | JSON serialization/deserialization |
| Preferences (built-in) | — | NVS persistent storage |
| WiFi (built-in) | — | STA/AP WiFi management |
| ESPmDNS (built-in) | — | mDNS responder |
| Update (built-in) | — | OTA firmware updates |

### Android App

| Library | Version | Purpose |
|---------|---------|---------|
| Jetpack Compose BOM | 2024.02.00 | Declarative UI framework |
| Material 3 | (from BOM) | Design system |
| OkHttp | 4.12.0 | HTTP client for bridge mode |
| kotlinx-coroutines | 1.7.3 | Async/reactive programming |
| CameraX | 1.3.1 | Camera for calibration wizard |
| Lifecycle (runtime + viewmodel) | 2.7.0 | State management |
| Core KTX | 1.12.0 | Android extensions |
| Activity Compose | 1.8.2 | Activity integration |

### Python Scripts

| Library | Usage |
|---------|-------|
| Pillow (PIL) | Image processing for color calibration |
| requests | HTTP client for bridge API |
| bleak | Cross-platform BLE (when available) |
| argparse | CLI argument parsing |
| struct | Binary data parsing (HCI snoop) |

## Build Systems

### Firmware (PlatformIO)

```
Board:     lolin_c3_mini (ESP32-C3, RISC-V, 160MHz, 4MB flash)
Framework: Arduino
Monitor:   115200 baud (USB CDC)

Build flags:
  -DARDUINO_USB_CDC_ON_BOOT=1     (native USB serial)
  -DCONFIG_BT_NIMBLE_MAX_CONNECTIONS=3

Build:  pio run
Flash:  pio run -t upload                           (USB)
OTA:    curl -F "firmware=@.pio/build/esp32c3/firmware.bin" http://elk-bridge.local/update

Flash budget: 1.3MB total, ~82.8% used (225KB free)
RAM budget:   320KB total, ~14.9% used
Free heap at runtime: ~119-129KB
```

### Android App (Gradle + Kotlin)

```
AGP:        8.7.3
Kotlin:     1.9.24
Min SDK:    26 (Android 8.0)
Target SDK: 34 (Android 14)
Java:       17

Build:   ./gradlew assembleDebug
Install: adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Firmware Module Architecture

```
elk_bridge.ino          Entry point
    │
    ├── setup()
    │   ├── WiFiManager::begin()      → STA connect or AP fallback
    │   ├── HttpServer::begin()       → Start web server BEFORE BLE
    │   ├── BleManager::begin()       → Init NimBLE
    │   ├── PatternEngine::begin()    → Load saved pattern from NVS
    │   └── BleManager::requestConnect() → Async connect saved strips
    │
    └── loop()  (single-core cooperative, ~1ms cycle)
        ├── WiFiManager::loop()       → Reconnect, beacon broadcast
        ├── BleManager::loop()        → Connect/reconnect state machine
        ├── PatternEngine::tick()     → Advance pattern, fade colors
        └── StatusLed::loop()         → Blink patterns

Data flow (command):
  HTTP POST → CommandDispatcher → elk_protocol frame → BleManager::write()
                                                        ├── Strip 1
                                                        └── Strip 2

Data flow (status poll):
  HTTP GET /status → WiFiManager state
                   → BleManager strip states (r,g,b,brightness,power,mode,effect)
                   → PatternEngine state (playing,step,preset,loop)
                   → System info (firmware,uptime,heap)
```

## Android App Transport Abstraction

```
                    DeviceControl (interface)
                    ┌──────────────────────┐
                    │ setColor(r,g,b)      │
                    │ setBrightness(level)  │
                    │ setPower(on)          │
                    │ setEffect(code,speed) │
                    │ playPattern(steps)    │
                    │ stop()               │
                    │ scan()               │
                    │ connect(address)     │
                    │ disconnect(address)  │
                    └──────┬───────────────┘
                           │
              ┌────────────┴────────────┐
              │                         │
    BleManager                  HttpBridgeClient
    (direct BLE)                (WiFi → bridge)
    ┌──────────────┐            ┌──────────────┐
    │ BluetoothGatt│            │ OkHttp 4.12  │
    │ WRITE_NO_RESP│            │ JSON bodies  │
    │ handle 0x0009│            │ /api/* routes │
    └──────┬───────┘            └──────┬───────┘
           │                           │
      BLE GATT                   HTTP/JSON
           │                           │
           ▼                           ▼
      ELK-BLEDOM               ESP32-C3 Bridge
       strip                         │
                                BLE GATT
                                     │
                                     ▼
                                ELK-BLEDOM
                                  strip
```

## State Synchronization

Both the Android app and web dashboard poll `/status` at 1-second intervals.
The bridge is the single source of truth for per-strip state.

```
State tracked per strip (BleStrip struct):
  lastR, lastG, lastB     → Current color
  lastBrightness           → Current brightness (0-100)
  lastPower                → Power on/off
  mode                     → 0=color, 1=effect, 2=pattern
  lastEffectCode           → Active effect (0x80-0x9A)
  lastEffectSpeed          → Effect speed (1-10)
  rssi                     → BLE signal strength

State tracked globally (PatternEngine):
  playing                  → Pattern active
  currentStep, totalSteps  → Progress
  loop                     → Repeat mode
  presetName               → Active preset name
  target                   → Target selector

UI interaction gating (web dashboard):
  Slider drag sets flag → poll skips that slider
  Slider release clears flag (200ms delay) → poll resumes sync
  Prevents "fighting" between user input and 1s status poll
```

## Hardware

| Component | Spec |
|-----------|------|
| ESP32-C3 SuperMini | RISC-V single-core @ 160MHz, 4MB flash, 320KB SRAM |
| WiFi | 802.11 b/g/n, 2.4GHz only |
| Bluetooth | BLE 5.0 (NimBLE stack) |
| Status LED | GPIO8, active-low, blue |
| USB | Native CDC (USB serial, no UART bridge chip) |
| Power | 5V USB-C |
| Antenna | PCB trace (weak — RSSI > -85 dBm required) |

## Key Constraints

1. **Single-core ESP32-C3**: BLE scan/connect must be async. Blocking in HTTP handler deadlocks TCP.
2. **Shared radio**: WiFi + BLE share one antenna. BLE connection interval tuned to 60-120ms to prevent WiFi starvation.
3. **No WSL2 Bluetooth**: All BLE operations via Android devices or ESP32-C3 bridge.
4. **Fire-and-forget BLE**: No ACK from strips. Commands sent 2× for reliability.
5. **Flash budget**: 1.3MB total, ~83% used. Web dashboard must stay compact.
6. **Pattern autonomy**: Bridge runs patterns independently of WiFi/app connection.
