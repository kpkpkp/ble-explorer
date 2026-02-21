# ESP32-S3 SuperMini — WiFi-to-BLE Bridge for ELK-BLEDOM

## Role in System
Dedicated bridge device placed near the ELK-BLEDOM LED strips. Receives
commands over WiFi (HTTP JSON API) from the Android app or any client on the
local network, and forwards them as BLE GATT writes to the strips. Runs pattern
playback autonomously — survives WiFi drops. Replaces the originally planned
ESP32-C3 with the S3 for dual-core performance and more BLE connections.

## Hardware Summary

| Parameter | Value |
|-----------|-------|
| **SoC** | ESP32-S3 (Espressif) |
| **Architecture** | Dual-core Xtensa LX7 @ 240 MHz |
| **Flash** | 8-16 MB onboard (variant-dependent) |
| **SRAM** | 512 KB |
| **ROM** | 384 KB |
| **PSRAM** | Optional (some variants include 2-8 MB) |
| **WiFi** | 802.11 b/g/n (2.4 GHz), WPA3 |
| **Bluetooth** | BLE 5.0 |
| **USB** | USB-C native (USB OTG, no external UART chip) |
| **Antenna** | PCB trace antenna (onboard) |
| **Dimensions** | ~25.4 x 18 mm |
| **I/O Voltage** | 3.3V logic |
| **Input Voltage** | 5V via USB-C, or 3.3-5.5V on VIN |
| **3.3V Regulator** | Up to 500 mA output |
| **Onboard LED** | Blue on GPIO47 (user-controllable), power LED |
| **Buttons** | Reset, Boot (GPIO0) |

## Why S3 Over C3

| Feature | ESP32-C3 | ESP32-S3 | Benefit |
|---------|----------|----------|---------|
| Cores | 1 (RISC-V) | 2 (Xtensa LX7) | WiFi on core 0, BLE+app on core 1 |
| Clock | 160 MHz | 240 MHz | Smoother fade interpolation |
| SRAM | 400 KB | 512 KB | More headroom for patterns |
| BLE connections | 3 default (8 max) | Up to 12 simultaneous | Room for future devices |
| Flash | 4 MB | 8-16 MB | OTA + embedded web dashboard |
| USB | CDC-only | USB OTG | Future expansion |

For our use case (2 strips, HTTP API, pattern playback), both work fine. The S3
gives headroom for adding more BLE devices without firmware changes.

## Pinout

```
        USB-C
     +-----------+
5V  -|           |- GPIO1  (ADC1_0)
GND -|           |- GPIO2  (ADC1_1)
3V3 -|           |- GPIO3  (ADC1_2)
IO4  |           |- GPIO5  (ADC1_4)
IO6  |  ESP32-S3 |- GPIO7  (ADC1_6)
IO8  |  SuperMini|- GPIO9  (ADC1_8)
IO10 |           |- GPIO11
IO12 |           |- GPIO13 (ADC2_2)
IO14 |           |- GPIO15
IO16 |           |- GPIO17
IO18 |           |- GPIO21
     +-----------+
```

**Total GPIO**: ~45 available (varies by variant), ~21 broken out on SuperMini

| GPIO | Function on Bridge | Notes |
|------|--------------------|-------|
| 0 | Boot button | Hold low at reset → download mode |
| 47 | Status LED | Blue LED (active-low on most variants) |
| 19 | USB D- | Native USB |
| 20 | USB D+ | Native USB |
| Others | Unused | Available for expansion |

**Safe GPIOs for this project**: Most GPIOs are safe. Avoid GPIO0 (boot),
GPIO19/20 (USB), and any GPIOs used by PSRAM if populated (typically 26-32).

## Power Consumption

Measured at 3.3V supply, 25°C. Dual-core draws slightly more than C3.

| Mode | SoC Current | Board Current (est.) | Notes |
|------|-------------|---------------------|-------|
| **Active WiFi TX** | ~310 mA peak | ~350 mA | Transmitting 802.11n |
| **Active WiFi RX** | ~100 mA | ~140 mA | Receiving / idle connected |
| **Active BLE TX** (0 dBm) | ~170 mA | ~210 mA | Transmitting BLE packets |
| **Active BLE RX** | ~120 mA | ~160 mA | Scanning / receiving |
| **Modem Sleep** | ~25 mA | ~65 mA | CPU active, radios off |
| **Light Sleep** | ~240 µA | ~700 µA | CPU paused, wake on timer/GPIO/WiFi |
| **Deep Sleep** | ~10 µA (SoC) / ~50 µA (board) | ~50 µA | RTC only |

### Power Recommendation

**USB wall adapter** (5V 1A). The LED strips require mains power, so an outlet
is always nearby. The S3 draws ~350mA peak which is well within any USB adapter.
Battery operation is impractical for always-on WiFi+BLE bridging.

## BLE Capabilities (as GATT Client)

| Feature | Supported |
|---------|-----------|
| BLE version | 5.0 |
| Role | Central (client) + Peripheral (server) |
| Max simultaneous connections | **12** (NimBLE on S3) |
| GATT client | Yes — connect, discover services, write characteristics |
| Write No Response | Yes — required for ELK-BLEDOM 0xFFF3 |
| TX power | -12 to +21 dBm (configurable) |
| Typical BLE range (indoor) | 10-30m with PCB antenna |
| Connection interval | Configurable, min 7.5ms |

12 simultaneous BLE connections means we can control many strips/devices
from a single bridge without firmware changes.

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
| OTA updates | Yes (via HTTP) |

## Bridge Architecture

```
+----------------+         WiFi          +--------------------+
|  Android App   | <---- HTTP/JSON ----> |  ESP32-S3 Bridge   |
|  (anywhere     |   (local network)     |  (near strips)     |
|   on WiFi)     |                       |                    |
+----------------+                       |  +---------------+ |
                                         |  | BLE Client    | |
+----------------+                       |  | Connection 1  +-+--> ELK-BLEDOM0B (strip1)
|  Web Browser   | <---- HTTP/JSON ----> |  | Connection 2  +-+--> ELK-BLEDOM  (strip2)
|  (any device)  |                       |  | ...up to 12   | |
+----------------+                       |  +---------------+ |
                                         +--------------------+
                                            powered by USB
                                            near the strips
```

## Programming / Flashing

### First Flash (USB)
```bash
# PlatformIO (recommended)
cd firmware/elk-bridge
pio run -t upload

# Arduino CLI
arduino-cli compile --fqbn esp32:esp32:lolin_s3_mini elk_bridge/
arduino-cli upload --fqbn esp32:esp32:lolin_s3_mini -p /dev/ttyACM0 elk_bridge/
```

### Subsequent Updates (OTA)
```bash
curl -F "firmware=@.pio/build/lolin_s3_mini/firmware.bin" http://elk-bridge.local/update
```

### Boot Mode
Hold **Boot** button (GPIO0) while pressing **Reset** to enter download mode.
With native USB, the USB-CDC interface usually handles reset automatically.

## Bill of Materials

| Item | Qty | Unit Cost | Notes |
|------|-----|-----------|-------|
| ESP32-S3 SuperMini | 1 | ~$3-5 | AliExpress, various sellers |
| USB-C cable | 1 | ~$1 | Power + initial flash |
| USB wall adapter (5V 1A) | 1 | ~$3 | Any phone charger works |
| **Total** | | **~$7-9** | |

## Known Gotchas

1. **GPIO0 is the boot button** — do not hold low at reset or it enters download mode
2. **PSRAM GPIOs** — if the variant has PSRAM, GPIOs 26-32 may be occupied
3. **Higher power draw** — dual-core S3 draws ~25% more than C3 in active mode; irrelevant on USB power
4. **PCB antenna range** — adequate for same-room placement near strips
5. **2.4 GHz only** — ensure the router has a 2.4 GHz SSID available
6. **BLE + WiFi coexistence** — S3 has improved coexistence over C3 (dual-core helps), but heavy WiFi can still delay BLE operations
7. **Flash wear** — avoid writing to NVS in tight loops (~100k write cycles per sector)
8. **USB-CDC on S3** — some boards need `USB_CDC_ON_BOOT=1` in build flags for serial monitor over native USB

## References

- [ESP32-S3 Datasheet — Espressif (official)](https://www.espressif.com/sites/default/files/documentation/esp32-s3_datasheet_en.pdf)
- [ESP32-S3 Technical Reference Manual](https://www.espressif.com/sites/default/files/documentation/esp32-s3_technical_reference_manual_en.pdf)
- [Lolin S3 Mini — Wemos Wiki](https://www.wemos.cc/en/latest/s3/s3_mini.html)
- [ESP32-S3 SuperMini Pinout — espboards.dev](https://www.espboards.dev/esp32/esp32-s3-super-mini/)
