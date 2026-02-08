# ELK-BLEDOM — BLE LED Strip Controller

## Discovery
- **Device Name**: ELK-BLEDOM0B
- **MAC Address**: BE:67:00:35:04:9B (also BE:67:00:35:11:78)
- **Discovered via**: nRF Connect on p2red (Pixel 2, HT7AZ1A00748)
- **Date**: 2026-02-07
- **Firmware ID**: `TY1067R24R44V05` (Tuya chipset, read from handle 0x0009)
- **MTU**: 63 bytes

## GATT Profile — Full Attribute Map

Only 9 ATT handles total. Extracted from HCI snoop via `scripts/gatt_discover.py`.

| Handle | Type | UUID | Description |
|--------|------|------|-------------|
| 0x0001 | Primary Service | 0x1800 | Generic Access |
| 0x0002 | Characteristic Decl | 0x2A00 | Device Name [READ] |
| 0x0003 | Characteristic Value | 0x2A00 | = device name string |
| 0x0004 | Primary Service | 0xFFF0 | LED Control (proprietary) |
| 0x0005 | Characteristic Decl | 0xFFF4 | Notify Status [NOTIFY] |
| 0x0006 | Characteristic Value | 0xFFF4 | notification payload |
| 0x0007 | Descriptor | 0x2902 | CCCD — enable/disable notifications |
| 0x0008 | Characteristic Decl | 0xFFF3 | Write Control [READ, WRITE_NO_RESP] |
| 0x0009 | Characteristic Value | 0xFFF3 | **all LED commands written here** |

Reading handle 0x0009 returns firmware ID: `TY1067R24R44V05` (Tuya-based).

## Protocol (ELK-BLEDOM family)

Sources: [FergusInLondon/ELK-BLEDOM](https://github.com/FergusInLondon/ELK-BLEDOM),
[dave-code-ruiz/elkbledom](https://github.com/dave-code-ruiz/elkbledom) (Home Assistant)

### Message Format (9 bytes)

| Byte | Value | Purpose |
|------|-------|---------|
| 1 | `0x7E` | Start marker |
| 2 | `0x00` or `0x07` | Identifier — community docs say 0x00, Lotus Lantern sends 0x07 |
| 3 | cmd | Command type |
| 4–7 | params | Parameters (`0xFF` when unused) |
| 8 | `0x00` or `0x10` | Flag byte — Lotus Lantern uses 0x10 for color commands |
| 9 | `0xEF` | End marker |

Write characteristic: `0xFFF3` (Write No Response), ATT handle `0x0009`
Notify characteristic: `0xFFF4` (Notify), ATT handle `0x0006`
Alt write UUID (some variants): `0xFFE1`
Alt notify UUID (some variants): `0xFFE2`

**Variant note**: This device (TY1067) accepts both `0x00` and `0x07` as byte 2.
Lotus Lantern (wl.smartled) always sends `0x07`. Both work.

### Commands

All commands confirmed via HCI snoop capture of Lotus Lantern (wl.smartled).
Byte[1] varies by command group (0x04, 0x05, 0x07, 0x08) — device accepts any value.

#### Set Color (RGB)
```
7E 07 05 03 RR GG BB 10 EF    (Lotus Lantern)
7E 00 05 03 RR GG BB 00 EF    (community docs — also works)
```

#### Set Brightness
```
7E 04 01 <brightness 0-255> <mode> FF FF 00 EF
```
mode=0x01 for normal, mode=0xFF for effect brightness.

#### Set Speed (current effect)
```
7E 04 02 <speed 1-255> FF FF FF 00 EF
```

#### Set Color Temperature
```
7E 00 05 02 <warm 0-100> <cold 0-100> 00 00 EF
```
warm + cold should sum to 100.

#### Set Single Color Channel
```
7E 00 05 01 <color 0-100> 00 00 00 EF
```

#### Power On/Off
```
7E 04 04 01 00 00 00 00 EF    (ON)
7E 04 04 00 00 00 FF 00 EF    (OFF)
7E 04 04 F0 00 01 FF 00 EF    (OFF, save state)
```

#### Set Effect
```
7E 05 03 <effect> <speed> FF FF 00 EF
```

#### Set Timer
```
7E 08 82 <hour> <min> 00 <action> <flags> EF
```
- hour/min in decimal (0x13 = 19, 0x34 = 52 → 19:52)
- action: 0x01=ON, 0x00=OFF
- flags: bit 7 (0x80) = timer enabled

### Effect Codes (complete)
| Code | Effect | Code | Effect |
|------|--------|------|--------|
| 0x80 | Static | 0x8D | Red pulse |
| 0x81 | Jump RGB | 0x8E | Green pulse |
| 0x82 | Jump RGBYCMW | 0x8F | Blue pulse |
| 0x83 | Crossfade RGB | 0x90 | Yellow pulse |
| 0x84 | Crossfade RGBYCMW | 0x91 | Cyan pulse |
| 0x85 | Breathe RGB | 0x92 | Magenta pulse |
| 0x86 | Breathe RGBYCMW | 0x93 | White pulse |
| 0x87 | Rainbow fade | 0x94 | RG chase |
| 0x88 | Rainbow pulse | 0x95 | RB chase |
| 0x89 | Rainbow strobe | 0x96 | GB chase |
| 0x8A | Color cycle | 0x97 | RG breathe |
| 0x8B | RGB chase | 0x98 | RB breathe |
| 0x8C | Rainbow chase | 0x99 | GB breathe |
| | | 0x9A | RGB breathe chase |

### MELK Variant Init Sequence
Some MELK-branded devices need initialization before accepting commands:
```
7E 07 83 ... (init 1)
7E 04 04 ... (init 2)
```

### App Parameters (from Lotus Lantern / iDeal LED RE)
| Parameter Key | Type | Range |
|---------------|------|-------|
| `bluetoothle.color` | byte[3] | RGB |
| `bluetoothle.brightness` | uint8 | 0–255 |
| `bluetoothle.light.mode` | uint8 | effect code |
| `bluetoothle.mode.change.speed` | uint8 | speed |
| `bluetoothle.light.on` | bool | 0/1 |
| `bluetoothle.color.warm` | uint8 | 0–100 |
| `bluetoothle.color.cold` | uint8 | 0–100 |
| `bluetoothle.streaming.external.mic.sensitive` | uint8 | 0–100 |
| `bluetoothle.streaming.external.mic.on.off` | bool | 0/1 |
| `bluetoothle.streaming.external.mic.eq.mode` | uint8 | 0–3 |

## Capture Workflow
1. Enable HCI snoop on p2red: Developer Options > Bluetooth HCI snoop log
2. Toggle Bluetooth OFF/ON to start capture
3. Open Lotus Lantern (`wl.smartled`), connect to ELK-BLEDOM0B
4. Exercise all features: colors, brightness, effects, timer, mic mode
5. Pull and decode: `python3 scripts/ble_capture.py --device 192.168.86.250:38772 --target BE:67:00:35:04:9B`

## Next Steps
1. [x] Enable HCI snoop on p2red and capture Lotus Lantern traffic
2. [x] Verify decoded commands match the protocol spec above — SET_COLOR confirmed: `7E 00 05 03 RR GG BB 00 EF`
3. [ ] Capture more commands (brightness, effects, power, timer, mic mode)
4. [x] Build a direct control script (`scripts/elk_control.py`)
5. [ ] Determine if this variant supports pixel-level addressing or only solid/effect modes
6. [ ] Test direct BLE control from a host with Bluetooth (Windows native or Linux w/ adapter)

## Category
**String Lights** — LED pixel array / strip controller
