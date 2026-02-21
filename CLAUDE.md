# BLE Explorer — Project Prime

## What This Is

A monorepo for reverse-engineering and controlling consumer BLE display/lighting
devices. The first target device is **ELK-BLEDOM** LED string lights. Future
devices (LED signboards, etc.) will live alongside under a shared architecture.

Two control surfaces:
- **Native Android app** (`android/elk-bledom/`) — dual-mode: direct BLE or WiFi bridge
- **ESP32-S3 WiFi-to-BLE bridge** (`firmware/elk-bridge/`) — HTTP JSON API, autonomous pattern playback

## Architecture

```
  Android App (WiFi)              ESP32-S3 Bridge
  ┌─────────────────┐            ┌───────────────────┐
  │ DashboardScreen  │  HTTP/JSON │ ESPAsyncWebServer  │
  │ DeviceControl ◄──┼───────────►│ CommandDispatcher  │
  │  ├─ BleManager   │  (WiFi)   │ PatternEngine      │
  │  └─ HttpBridge   │           │ BleManager(NimBLE) │
  │ PatternEngine    │           │ WiFiManager+mDNS   │
  │ ColorCalibration │           │ StatusLed          │
  │ PresetManager    │           └───────┬────────────┘
  └─────────────────┘                   │ BLE 5.0
                                        ▼
                                 ELK-BLEDOM strips
```

### Android App
```
Android App (Kotlin, Jetpack Compose)
├── control/
│   ├── DeviceControl     — Transport interface (BLE or HTTP)
│   ├── HttpBridgeClient  — Bridge mode: HTTP → ESP32-S3
│   └── BridgeDiscovery   — mDNS discovery of bridges
├── ble/
│   ├── BleManager        — BLE GATT client, implements DeviceControl
│   └── ElkProtocol       — 9-byte command frames
├── pattern/
│   ├── PatternEngine     — Dual-mode: local coroutines (BLE) or bridge-side (HTTP)
│   ├── PresetManager     — Built-in + user-created presets (SharedPrefs JSON)
│   └── Presets           — Default pattern definitions
├── calibration/
│   └── ColorCalibration  — 12-point hue LUT for strip color correction
├── ui/
│   ├── DashboardScreen   — Single-screen UI, takes DeviceControl
│   ├── SettingsScreen    — Mode selector (BLE/Bridge) + bridge URL config
│   ├── PatternEditor     — Inline step editor with bulk edit, preset CRUD
│   └── ColorPicker       — Swatch grid + RGB sliders + hex input
├── MainActivity          — Mode routing, permissions, session lifecycle
├── SessionState          — Persists connections + playback + control mode
└── LedService            — Foreground service + wake lock
```

### ESP32-S3 Firmware
```
firmware/elk-bridge/
├── platformio.ini          PlatformIO config (lolin_s3_mini)
└── src/
    ├── elk_bridge.ino      setup() + loop()
    ├── config.h            Pins, timeouts, version
    ├── wifi_manager.h/cpp  STA/AP fallback, mDNS, reconnect
    ├── http_server.h/cpp   ESPAsyncWebServer routes, JSON, CORS, OTA
    ├── ble_manager.h/cpp   NimBLE client, multi-strip, auto-reconnect
    ├── elk_protocol.h      9-byte frame builders (port of ElkProtocol.kt)
    ├── command_dispatch.h/cpp  JSON → frames → BLE writes
    ├── pattern_engine.h/cpp    millis() state machine, preemption
    ├── status_led.h/cpp    Non-blocking blink patterns
    └── html.h              Embedded web dashboard
```

## Key Constraints

- **WSL2 has no Bluetooth** — all BLE operations happen on Android devices or
  the ESP32-S3 bridge, never on the dev machine.
- **Target phone is SDK 30** (Android 11, Pixel 2 "p2cracked") — legacy BLE perms.
- **ELK-BLEDOM protocol is fire-and-forget** — WRITE_NO_RESPONSE on 0xFFF3.
  Commands sent 2x with 50ms gap for reliability.
- **ESP32-S3 dual-core** — cooperative loop architecture (no blocking in loop).
  Pattern fading at ~30fps per strip. WiFi on core 0, app on core 1.

## Build & Deploy

```bash
# --- Android App ---
cd android/elk-bledom
ANDROID_HOME=~/Android/Sdk ./gradlew assembleDebug

adb -s adb-FA7981A04986-XlCcYu._adb-tls-connect._tcp install -r \
  app/build/outputs/apk/debug/app-debug.apk

adb -s adb-FA7981A04986-XlCcYu._adb-tls-connect._tcp logcat -s BleManager:V PatternEngine:V MainActivity:V HttpBridge:V

# --- ESP32-S3 Firmware ---
cd firmware/elk-bridge
pio run -t upload          # USB flash
# or OTA:
curl -F "firmware=@.pio/build/lolin_s3_mini/firmware.bin" http://elk-bridge.local/update

# --- Test bridge API ---
python scripts/test_bridge_api.py              # mDNS
python scripts/test_bridge_api.py 192.168.86.X # explicit IP
```

## Devices

| Name | ID | Role |
|------|------|------|
| p2cracked | `adb-FA7981A04986-XlCcYu._adb-tls-connect._tcp` | APK test target (SDK 30) |
| p2red | `192.168.86.250:38772` (WiFi) | HCI snoop capture device |
| p2clear | `192.168.86.25:5555` (WiFi) | Camera "eye on the lights" |
| ELK-BLEDOM0B | `BE:67:00:35:04:9B` | LED strip 1 |
| ELK-BLEDOM | `BE:67:00:35:11:78` | LED strip 2 (in camera frame) |
| ELK Bridge | `elk-bridge.local` | ESP32-S3 WiFi-to-BLE bridge |

## Specs & Docs

- `specs/device-elk-bledom.md` — Full GATT profile, protocol commands, effect codes
- `specs/device-esp32c3-supermini.md` — Original C3 hardware spec (reference)
- `specs/device-esp32s3-supermini.md` — S3 hardware spec, pinout, power budget
- `specs/firmware-elk-bridge.md` — Bridge firmware architecture (Arduino + NimBLE)
- `specs/api-elk-bridge.md` — HTTP API contract (all endpoints, request/response)

## Design Principles

1. **Device-agnostic where possible** — pattern sequencing, preset management,
   session persistence, and calibration are not ELK-BLEDOM-specific. The
   `DeviceControl` interface abstracts transport (BLE vs HTTP).

2. **Protocol layer is a thin adapter** — `ElkProtocol.kt` / `elk_protocol.h`
   are pure functions mapping (command, params) → byte frames.

3. **Non-blocking everywhere** — Android uses coroutines with timeouts. ESP32
   uses millis()-based state machines. No blocking calls in loop().

4. **Resilience over correctness** — BLE is unreliable. Commands sent 2x.
   Connections auto-reconnect. Session state persists. Bridge runs patterns
   autonomously (survives WiFi drops). Individual commands preempt patterns.

5. **Compact UI for small screens** — Pixel 2 (5" 1080p). Every pixel counts.
   Single-screen dashboard, inline editing, no navigation drawer.

6. **Dual-mode transport** — The app works in both BLE direct and WiFi bridge
   modes via the `DeviceControl` interface. BLE mode: phone connects directly.
   Bridge mode: phone sends HTTP to ESP32-S3 which handles BLE. Mode persists
   in `SessionState.controlMode`.
