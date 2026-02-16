# ESP32-C3 SuperMini — WiFi-to-BLE Bridge for ELK-BLEDOM

## Role in System
Dedicated bridge device placed near the ELK-BLEDOM LED strips. Receives
commands over WiFi (HTTP/MQTT) from the Android app or any client on the
local network, and forwards them as BLE GATT writes to the strips. Eliminates
the BLE range limitation by decoupling the user interface from the radio link.

## Hardware Summary

| Parameter | Value |
|-----------|-------|
| **SoC** | ESP32-C3 FN4 (Espressif) |
| **Architecture** | RISC-V 32-bit single-core @ 160 MHz |
| **Flash** | 4 MB onboard |
| **SRAM** | 400 KB |
| **ROM** | 384 KB |
| **WiFi** | 802.11 b/g/n (2.4 GHz), WPA3 |
| **Bluetooth** | BLE 5.0 |
| **USB** | USB-C (native USB serial, no external UART chip) |
| **Antenna** | PCB trace antenna (onboard) |
| **Dimensions** | 22.5 x 18 mm |
| **I/O Voltage** | 3.3V logic |
| **Input Voltage** | 5V via USB-C, or 3.3–6V on VIN |
| **3.3V Regulator** | Up to 500 mA output |
| **Onboard LED** | Blue on GPIO8 (user-controllable), Red power LED (always on) |
| **Buttons** | Reset, Boot (GPIO9) |

## Pinout

```
        USB-C
     ┌─────────┐
5V  ─┤         ├─ GPIO0  (ADC1_0)
GND ─┤         ├─ GPIO1  (ADC1_1)
3V3 ─┤         ├─ GPIO2  (ADC1_2, boot strapping)
IO10 ┤         ├─ GPIO3  (ADC1_3)
IO9  ┤  ESP32  ├─ GPIO4  (ADC1_4 / SPI SCK)
IO8  ┤   C3    ├─ GPIO5  (SPI MISO)
IO7  ┤         ├─ GPIO6  (SPI MOSI)
IO21 ┤         ├─ GPIO20 (UART0 RX)
     └─────────┘
```

**Total GPIO**: 11 usable (GPIO0–10, 20, 21)

| GPIO | Primary Function | Alt Functions | Notes |
|------|-----------------|---------------|-------|
| 0 | General I/O | ADC1_0 | Safe, no boot conflict |
| 1 | General I/O | ADC1_1 | Safe, no boot conflict |
| 2 | General I/O | ADC1_2 | Boot strapping pin — avoid pulldown at boot |
| 3 | General I/O | ADC1_3 | Safe |
| 4 | SPI SCK | ADC1_4 | Safe |
| 5 | SPI MISO | — | Safe |
| 6 | SPI MOSI | — | Safe |
| 7 | SPI SS | — | Safe |
| 8 | I2C SDA | Blue LED | LED is active-low; doubles as user LED |
| 9 | I2C SCL | Boot button | **Pulled high** — do not hold low at reset |
| 10 | General I/O | — | Safe, no boot conflict |
| 20 | UART0 RX | — | USB serial receive |
| 21 | UART0 TX | — | USB serial transmit |

**Safe GPIOs for this project** (no boot/flash side effects): **0, 1, 3, 10**

## Power Consumption

Measured at 3.3V supply, 25°C. Board-level figures include regulator + power LED overhead.

| Mode | SoC Current | Board Current (est.) | Notes |
|------|-------------|---------------------|-------|
| **Active WiFi TX** | ~240 mA peak | ~280 mA | Transmitting 802.11n |
| **Active WiFi RX** | ~70 mA | ~110 mA | Receiving / idle connected |
| **Active BLE TX** (0 dBm) | ~130 mA | ~170 mA | Transmitting BLE packets |
| **Active BLE RX** | ~95 mA | ~135 mA | Scanning / receiving |
| **Modem Sleep** | ~15 mA | ~55 mA | CPU active, radios off |
| **Light Sleep** | ~130 µA | ~500 µA | CPU paused, wake on timer/GPIO/WiFi |
| **Deep Sleep** | ~8 µA (SoC) / ~43 µA (board) | ~43 µA | RTC only; **remove power LED to reach 8 µA** |

### Battery Life Estimates (Bridge Role)

For our use case the bridge must maintain a WiFi connection to receive commands
and a BLE connection to 1-2 strips. Continuous dual-radio operation.

| Power Source | Capacity | WiFi+BLE Active | WiFi Keep-Alive + BLE Idle | Deep Sleep (wake every 30s) |
|-------------|----------|-----------------|---------------------------|---------------------------|
| USB power bank (20 Ah, 5V) | ~13 Ah @ 3.3V | ~2 days | ~5 days | ~8 months |
| 3x AA alkaline | ~2.4 Ah | ~8 hours | ~20 hours | ~2 months |
| 18650 Li-Ion (3.4 Ah) | ~3.4 Ah | ~12 hours | ~28 hours | ~3 months |
| **USB wall adapter** | **Unlimited** | **Forever** | **Forever** | **Forever** |

**Recommendation**: USB wall adapter. The LED strips themselves require mains power,
so an outlet is always nearby. Battery operation is impractical for always-on WiFi bridging.

### Power LED Removal (for battery use)

The red power LED draws ~2 mA continuously and cannot be disabled in software.
For battery-powered deep-sleep applications, desolder the LED or cut its trace.
This reduces deep-sleep current from ~43 µA to ~8 µA.

## BLE Capabilities (as GATT Client)

| Feature | Supported |
|---------|-----------|
| BLE version | 5.0 |
| Role | Central (client) + Peripheral (server) |
| Max simultaneous connections | **3** (ESP-IDF default, configurable up to 8) |
| GATT client | Yes — connect, discover services, write characteristics |
| Write No Response | Yes — required for ELK-BLEDOM 0xFFF3 |
| TX power | -12 to +21 dBm (configurable) |
| Typical BLE range (indoor) | 10-30m with PCB antenna |
| Connection interval | Configurable, min 7.5ms |

Sufficient for our 2-strip setup. Can handle both ELK-BLEDOM0B and ELK-BLEDOM
simultaneously with one spare connection slot.

## WiFi Capabilities

| Feature | Supported |
|---------|-----------|
| Standards | 802.11 b/g/n |
| Band | 2.4 GHz only |
| Mode | Station (STA) + Access Point (AP), simultaneous |
| Security | WPA/WPA2/WPA3 |
| Max TX power | +21 dBm |
| Typical range (indoor) | 30-50m |
| mDNS | Yes (via ESP-IDF/Arduino) |
| OTA updates | Yes (via HTTP or MQTT) |

## Firmware Options

### Option A: Arduino + AsyncWebServer (fastest to prototype)
- Arduino ESP32 core has mature BLE client (`NimBLE-Arduino`)
- `ESPAsyncWebServer` for HTTP API
- ~100 lines for a working bridge
- OTA update via ArduinoOTA

### Option B: ESP-IDF + NimBLE (maximum control)
- Official Espressif framework, C-based
- Full control over power management, BLE parameters
- Steeper learning curve

### Option C: ESPHome (zero custom code)
- YAML-based configuration
- Native BLE client component
- Auto-integrates with Home Assistant
- Supports `ble_client` → `output` → `light` pipeline
- Limitation: pattern sequencing would need HA automations

### Option D: MicroPython (Python on-device)
- `aioble` library for BLE client
- `microdot` or raw sockets for HTTP
- Familiar language, but slower execution and higher RAM usage
- BLE client support less mature than Arduino/NimBLE

**Recommended**: Option A for standalone bridge, Option C if using Home Assistant.

## Bridge Architecture

```
┌──────────────┐         WiFi          ┌──────────────────┐
│  Android App │ ◄──── HTTP/JSON ────► │  ESP32-C3 Bridge  │
│  (anywhere   │   (local network)     │  (near strips)    │
│   on WiFi)   │                       │                   │
└──────────────┘                       │  ┌─────────────┐  │
                                       │  │ BLE Client  │  │
┌──────────────┐                       │  │ Connection 1 ├──┼──► ELK-BLEDOM0B (strip1)
│  Web Browser │ ◄──── HTTP/JSON ────► │  │ Connection 2 ├──┼──► ELK-BLEDOM  (strip2)
│  (any device)│                       │  └─────────────┘  │
└──────────────┘                       └──────────────────┘
                                          powered by USB
                                          near the strips
```

### HTTP API (proposed)

| Endpoint | Method | Body | Action |
|----------|--------|------|--------|
| `GET /status` | GET | — | Connection state, firmware version |
| `POST /api/color` | POST | `{"r":255,"g":0,"b":0}` | Set RGB color |
| `POST /api/brightness` | POST | `{"level":128}` | Set brightness (0-255) |
| `POST /api/power` | POST | `{"on":true}` | Power on/off |
| `POST /api/effect` | POST | `{"code":129,"speed":3}` | Set effect + speed |
| `POST /api/raw` | POST | `{"hex":"7E0705030000FF10EF"}` | Send raw 9-byte command |
| `POST /api/pattern` | POST | `{"steps":[...],"loop":true}` | Play pattern sequence |
| `POST /api/stop` | POST | — | Stop pattern playback |
| `GET /api/scan` | GET | — | Scan for ELK-BLEDOM devices |
| `POST /api/connect` | POST | `{"address":"BE:67:00:35:04:9B"}` | Connect to device |
| `POST /api/disconnect` | POST | `{"address":"BE:67:00:35:04:9B"}` | Disconnect device |

### mDNS Discovery

The bridge advertises itself as `elk-bridge.local` on the network. The Android
app can discover it via mDNS service type `_elk._tcp` without hardcoding an IP.

## Programming / Flashing

### First Flash (USB)
```bash
# Arduino CLI
arduino-cli compile --fqbn esp32:esp32:esp32c3 elk_bridge/
arduino-cli upload --fqbn esp32:esp32:esp32c3 -p /dev/ttyACM0 elk_bridge/

# ESP-IDF
idf.py set-target esp32c3
idf.py build flash monitor

# ESPHome
esphome run elk-bridge.yaml
```

### Subsequent Updates (OTA)
```bash
# Arduino OTA
arduino-cli upload --fqbn esp32:esp32:esp32c3 -p elk-bridge.local elk_bridge/

# ESPHome OTA
esphome run elk-bridge.yaml  # auto-detects OTA if already flashed
```

### Boot Mode
Hold **Boot** button (GPIO9) while pressing **Reset** to enter download mode.
Not needed with native USB — the USB-CDC interface handles reset automatically.

## Bill of Materials

| Item | Qty | Unit Cost | Notes |
|------|-----|-----------|-------|
| ESP32-C3 SuperMini | 1 | ~$2-3 | AliExpress, various sellers |
| USB-C cable | 1 | ~$1 | Power + initial flash |
| USB wall adapter (5V 1A) | 1 | ~$3 | Any phone charger works |
| **Total** | | **~$6-7** | |

Optional: 3D-printed enclosure, header pins (included with most boards).

## Known Gotchas

1. **Power LED wastes 2 mA** — desolder for battery use
2. **GPIO2 is a boot strapping pin** — do not pull low at boot
3. **GPIO9 is the boot button** — do not hold low at reset or it enters download mode
4. **PCB antenna range** — adequate for same-room placement near strips, but not through multiple walls
5. **2.4 GHz congestion** — both WiFi and BLE share 2.4 GHz; on a crowded channel the bridge may see latency. Use WiFi channel 1 or 11 to minimize overlap with BLE advertising channels (37, 38, 39)
6. **BLE + WiFi coexistence** — the ESP32-C3 has a single radio with time-division multiplexing for WiFi and BLE. Heavy WiFi traffic can delay BLE operations. For our use case (small infrequent commands) this is not a problem
7. **No 5 GHz WiFi** — 2.4 GHz only. Ensure the home router has a 2.4 GHz SSID available
8. **Flash wear** — avoid writing to flash in tight loops (e.g., don't persist every color command). NVS has ~100k write cycles per sector

## References

- [ESP32-C3 SuperMini Pinout & Specs — espboards.dev](https://www.espboards.dev/esp32/esp32-c3-super-mini/)
- [ESP32-C3 SuperMini Datasheet — Mischianti](https://mischianti.org/esp32-c3-super-mini-high-resolution-pinout-datasheet-and-specs/)
- [ESP32-C3 Series Datasheet — Espressif (official)](https://www.espressif.com/sites/default/files/documentation/esp32-c3_datasheet_en.pdf)
- [ESP32-C3 Power Modes — Espressif IoT Book](https://espressif.github.io/esp32-c3-book-en/chapter_12/12.2/12.2.4.html)
- [Getting Started — Random Nerd Tutorials](https://randomnerdtutorials.com/getting-started-esp32-c3-super-mini/)
- [ESPHome Board Config — sudo.is](https://www.sudo.is/docs/esphome/boards/esp32c3supermini/)
