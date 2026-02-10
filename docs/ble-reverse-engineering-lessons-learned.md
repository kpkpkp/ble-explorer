# BLE Discovery & Reverse Engineering — Lessons Learned

**Date:** 2026-02-08
**Project:** BLE Explorer — reverse-engineering BLE protocols for consumer display devices
**Target device:** ELK-BLEDOM LED string lights (Tuya chipset, TY1067R24R44V05)

---

## 1. Capturing BLE Traffic from Android

### The HCI snoop log is your best friend — when you can get it

Android's Bluetooth HCI snoop log captures every packet between the phone and BLE devices. It's the raw material for protocol reverse engineering.

**Enabling it:**
- Use **Developer Options > Enable Bluetooth HCI snoop log** in the UI
- The `settings put secure bluetooth_hci_log 1` command is unreliable — sometimes it doesn't actually start capture
- Toggle Bluetooth off/on after enabling to ensure capture starts

**Extracting it:**
- Direct `adb pull /data/misc/bluetooth/logs/btsnoop_hci.log` fails with permission denied on modern Android
- **Workaround:** Generate a full bugreport (`adb bugreport`) and extract the log from the ZIP at `FS/data/misc/bluetooth/logs/btsnoop_hci.log`
- Bugreports are large (50-200MB) and slow to generate (~2 minutes) — plan accordingly

**Lesson:** Always verify capture is working by doing a known BLE action, pulling the log, and confirming packets appear. Don't discover hours later that logging wasn't actually active.

## 2. Parsing HCI Snoop Logs

### Format matters — Android Pixel uses H4

The btsnoop file format has a header declaring the datalink type:
- **Datalink 1001:** Standard HCI (no packet type byte)
- **Datalink 1002:** H4 format — first byte after the record header is the packet type indicator

Pixel 2 uses **H4 format (1002)**. The first byte of each packet payload is:
- `0x01` = HCI Command
- `0x02` = ACL Data (this is where GATT writes live)
- `0x04` = HCI Event

**Record structure:**
```
[4 bytes: original length]
[4 bytes: included length]
[4 bytes: flags — bit 0: 0=sent 1=received, bit 1: 0=data 1=command]
[4 bytes: cumulative drops]
[8 bytes: timestamp — microseconds since 0000-01-01T00:00:00 UTC]
[payload...]
```

**Lesson:** Get the datalink type right first. Parsing with the wrong type silently produces garbage — fields are offset by one byte and nothing aligns.

### BLE addresses are little-endian

MAC addresses in HCI packets are stored in **reverse byte order**. If the device advertises as `BE:67:00:35:04:9B`, it appears in the packet as `9B:04:35:00:67:BE`.

**Lesson:** Always reverse the 6 address bytes before displaying or comparing. This trips you up every time you forget.

### Connection events have variants

- **LE Connection Complete** (subevent `0x01`): MAC address at bytes [7:13] after the event header
- **LE Enhanced Connection Complete** (subevent `0x0A`): MAC address at bytes [8:14] — there's an extra `role` and `address_type` byte before the address

Modern Android uses Enhanced Connection Complete. If you only handle `0x01`, you'll miss all connections.

**Lesson:** Handle both subevent types. Build a connection handle → MAC address mapping table from these events, then use it to attribute GATT writes to specific devices.

## 3. Finding GATT Writes in the Traffic

### The packet nesting

To find a BLE GATT write in an HCI snoop capture, you unwrap several layers:

```
HCI ACL Packet (type 0x02)
  └─ L2CAP Frame (channel 0x0004 = ATT)
       └─ ATT Protocol
            ├─ Write Request (opcode 0x12) — expects response
            └─ Write Command (opcode 0x52) — fire-and-forget (WRITE_NO_RESP)
```

The ELK-BLEDOM uses **Write Without Response** (opcode `0x52`) to characteristic handle `0x0009` (FFF3). This is the fast path — no round-trip ACK, which is why the Lotus Lantern app sends each command 4-8 times for reliability.

**Lesson:** Filter for ATT opcode `0x52` on L2CAP channel `0x0004`. The handle number in the write tells you which characteristic was written. Cross-reference with the GATT table from nRF Connect.

### Matching handles to characteristics

nRF Connect (or any GATT explorer) shows the attribute handle for each characteristic. For ELK-BLEDOM:
- Handle `0x0009` = FFF3 (write control) — where commands go
- Handle `0x0006` = FFF4 (notify status)
- Handle `0x0007` = CCCD (Client Characteristic Configuration Descriptor)

**Lesson:** Do a GATT discovery with nRF Connect first and screenshot/save the handle table. You need this mapping to interpret raw HCI captures.

## 4. Decoding a Proprietary Protocol

### Start with the companion app

The Lotus Lantern app (Android) is the manufacturer's controller. The reverse engineering approach:

1. **Install the app**, pair the device
2. **Enable HCI snoop**, perform known actions one at a time:
   - Turn on → capture
   - Set red → capture
   - Set green → capture
   - Set brightness 50% → capture
   - Set effect "rainbow" → capture
3. **Diff the captures** — the changing bytes between "set red" and "set green" are the color fields

### The ELK-BLEDOM 9-byte protocol

Every command follows the same envelope:
```
7E  00  05  03  RR  GG  BB  00  EF
│   │   │   │   │   │   │   │   └─ End marker
│   │   │   │   │   │   │   └─── Flag byte (usually 0x00 or 0x10)
│   │   │   │   └───┴───┴────── Parameters (vary by command)
│   │   │   └──────────────────── Subcommand
│   │   └──────────────────────── Command type
│   └──────────────────────────── Identifier (0x00 or 0x07, device accepts both)
└──────────────────────────────── Start marker
```

**How we decoded each command type:**

| Action | Observed change | Decoded field |
|--------|----------------|---------------|
| Red → Green → Blue | Bytes 4-6 changed: `FF0000` → `00FF00` → `0000FF` | Bytes 4-6 = RGB |
| Brightness 0→100% | Byte 3 changed: `00` → `64` | Byte 3 = brightness (0-100) |
| Effect selection | Byte 3 changed to `80`-`9A` | Byte 3 = effect code |
| Power on/off | Byte 3: `01` vs `00` | Byte 3 = power state |

**Lesson:** Systematic single-variable testing is the fastest path. Change exactly one thing in the app, capture, compare. The protocol usually becomes obvious after 5-10 captures.

### The Lotus Lantern redundancy pattern

The companion app sends every command **4-8 times** in quick succession to both connected strips. This is a reliability measure for cheap BLE hardware — if any one packet gets through, the command succeeds.

**Lesson:** When you see repeated identical writes in a capture, that's intentional redundancy, not a sequence or handshake. Your implementation can send once, but should retry on failure.

## 5. The GATT Service/Characteristic Architecture

### Service 0xFFF0 — the control plane

ELK-BLEDOM uses a single proprietary service (0xFFF0) with two characteristics:

| UUID | Properties | Purpose |
|------|-----------|---------|
| FFF3 | READ, WRITE_NO_RESP | **Command input** — all control commands go here |
| FFF4 | NOTIFY | **Status output** — device sends state changes here |

The WRITE_NO_RESP property is key — it means the BLE stack doesn't wait for an ATT-level acknowledgment. Commands are fire-and-forget at the protocol level, which is fast but unreliable.

**Lesson:** Enable notifications on FFF4 (write `0x01 0x00` to the CCCD at handle 0x0007) to receive state feedback. This isn't strictly necessary for control but helps verify the device received commands.

### MTU negotiation

The device reports MTU 63 bytes. With a 9-byte command protocol, this is never a bottleneck. But for devices with larger payloads (images, firmware), MTU matters critically.

**Lesson:** Always check the negotiated MTU. Android will negotiate the highest both sides support, but the default is only 23 bytes (20 usable after ATT header). Request a higher MTU explicitly if needed.

## 6. Platform Constraints

### WSL2 has NO Bluetooth

WSL2 does not expose host Bluetooth hardware. Period. No amount of driver installation or USB passthrough (for built-in adapters) fixes this.

**Workarounds:**
- **Windows native Python** with `bleak` library — works with WinRT BLE backend
- **Android phone as BLE proxy** — send commands via ADB or HTTP to a dashboard app
- **Linux VM with USB BLE dongle** — passthrough a dedicated USB adapter

**Lesson:** Discovered this after installing bleak and wondering why `BleakScanner.discover()` returned nothing. Check your platform before writing BLE code.

### Android as BLE proxy — the dashboard approach

We built a FastAPI dashboard (`elk_dashboard.py`) running on Windows Python that:
1. Connects to strips via bleak on Windows
2. Exposes HTTP API (`/api/color`, `/api/brightness`, etc.)
3. Scripts in WSL send HTTP requests to control the strips

This adds latency (~50-200ms per command) but works reliably and supports multiple simultaneous clients.

**Lesson:** When you can't do BLE directly, an HTTP-to-BLE bridge is a practical architecture. The FastAPI + bleak combo is solid on Windows.

## 7. BLE Connection Reliability

### These cheap devices drop connections

The ELK-BLEDOM strips disconnect unpredictably — sometimes after minutes, sometimes after hours. The BLE connection just dies with no clean disconnection event.

**Mitigation:**
- Auto-reconnect loop in the dashboard (retries every 5 seconds)
- Check connection status before sending commands
- Target specific strips (`"targets": "strip2"`) rather than broadcasting to "both" — a slow/dead strip blocks commands to the healthy one

### Commands succeed despite timeouts

A BLE write that "times out" at the HTTP level often still executed on the device. The command was sent, the device acted on it, but the acknowledgment (or lack thereof, with WRITE_NO_RESP) didn't propagate back through the stack in time.

**Lesson:** A timeout doesn't mean failure. If you're doing automated sequences, don't treat timeouts as hard failures — add a brief delay and continue. Verify the result (via camera or notification) rather than assuming the worst.

### Command pacing matters

Sending commands too fast (< 50ms apart) causes dropped packets. The BLE radio needs time between transmissions. The Lotus Lantern app spaces its redundant writes ~20-50ms apart.

**Lesson:** Add 50-100ms delays between commands. For critical commands (color changes during calibration), wait 1-2 seconds for the strip to stabilize before measuring the result.

## 8. Device Discovery and Identification

### Advertising names are your primary filter

BLE devices advertise their name in the advertising data (type `0x09` = Complete Local Name). The ELK-BLEDOM strips advertise as:
- `ELK-BLEDOM0B` (strip 1)
- `ELK-BLEDOM` (strip 2)

### MAC addresses reveal the manufacturer

The OUI (first 3 bytes of MAC) identifies the chip vendor:
- `BE:67:00` — appears to be a Tuya/generic Chinese BLE SoC
- Known OUIs: Espressif (`AC:0B:FB`, `24:6F:28`), Texas Instruments (`D0:B5:C2`), Nordic Semi (`F8:F0:05`)

### Random vs public addresses

If bit 1 of the first MAC byte is set, it's a random address that may change. The ELK-BLEDOM uses a public (static) address, which is convenient for reconnection.

**Lesson:** Store both the device name pattern and MAC address. Reconnect by MAC when possible (faster), fall back to name-based scanning when the device has been power-cycled.

## 9. Tools of the Trade

### Essential tools used in this project

| Tool | Purpose | Platform |
|------|---------|----------|
| **nRF Connect** (Android) | GATT exploration, service/characteristic discovery, manual writes | Android |
| **Lotus Lantern** | Reference app — capture its traffic to decode the protocol | Android |
| **Wireshark** | Inspect btsnoop captures with BLE dissectors | All |
| **bleak** (Python) | Cross-platform BLE library for programmatic control | Windows/Linux/macOS |
| **ADB** | Pull snoop logs, bugreports, remote control Android devices | All |
| **FastAPI + uvicorn** | HTTP-to-BLE bridge dashboard | Windows (for BLE) |

### nRF Connect workflow

1. **Scan** — find device by name
2. **Connect** — establishes BLE link
3. **Explore services** — expand each service, note UUIDs and handles
4. **Read characteristics** — see current values
5. **Enable notifications** — subscribe to FFF4 to watch device state
6. **Write test values** — manually send `7E000503FF000000EF` (red) to FFF3 to verify control

**Lesson:** nRF Connect is indispensable for the initial discovery phase. Screenshot everything — you'll reference those handle numbers constantly during capture analysis.

## 10. Protocol Reverse Engineering Methodology

### The systematic approach that worked

```
1. DISCOVER    — nRF Connect → GATT table, handles, UUIDs
2. CAPTURE     — Enable HCI snoop → use companion app → pull log
3. ISOLATE     — One action at a time, diff captures
4. DECODE      — Identify envelope (start/end markers), then fields
5. VERIFY      — Send decoded commands manually via nRF Connect
6. IMPLEMENT   — Build Python control library
7. ITERATE     — Discover edge cases via exhaustive testing
```

### What made ELK-BLEDOM easy to decode

- **Fixed-length messages** (9 bytes) — no length field parsing needed
- **Clear start/end markers** (`0x7E` / `0xEF`) — easy to spot in hex dumps
- **Minimal state** — each command is self-contained, no session setup required
- **No encryption** — commands are plaintext (common for cheap consumer BLE)
- **Responsive to raw writes** — no authentication handshake needed

### What would make a device harder

- Variable-length messages with length fields or TLV encoding
- Encrypted or obfuscated payloads (bonding/pairing required)
- Stateful protocols (must send init sequence before commands work)
- Fragmented writes across multiple MTU-sized chunks
- Proprietary 128-bit UUIDs with no documentation

**Lesson:** Start with the easiest target to build your tooling and methodology. Then apply the same approach to harder devices.

## 11. From Reverse Engineering to Production Control

### The progression

```
HCI capture → decoded protocol → nRF Connect manual test → Python script →
  → direct BLE control (elk_control.py)
  → HTTP dashboard (elk_dashboard.py)
  → pattern engine
  → calibration system
  → automated color matching
```

Each step built on the previous. The protocol knowledge from step 1 powered everything else.

### Abstracting the protocol

The command builders (`cmd_color()`, `cmd_brightness()`, etc.) encapsulate the protocol knowledge in one place. Every higher-level system (dashboard, calibration, patterns) calls these builders rather than constructing raw bytes.

**Lesson:** Invest time in a clean command abstraction early. When you discover a protocol correction later (e.g., byte 1 can be `0x07` not just `0x00`), you fix it in one place.

## 12. Physical Setup Matters

### You need the actual hardware in front of you

BLE reverse engineering is inherently physical. You need:
- The target device powered on and in range
- A BLE-capable device to interact with it
- Often a second device to capture traffic
- Visual confirmation that commands actually worked

### The multi-device orchestration challenge

This project ended up coordinating 4+ devices:
- Pixel 2 (p2red) — HCI capture, BLE scanning
- ELK-BLEDOM strips — the targets
- Windows PC — running bleak for BLE control
- Pixel 10 Pro — camera for calibration
- Pixel 2 XL — reference display for calibration

Each device has its own connectivity quirks (WiFi ADB drops, BLE disconnects, camera app quirks).

**Lesson:** Document every device's IP, MAC, serial number, and connection method. Build an inventory early (`adb-inventory.py`). You will forget which Pixel 2 is which.

---

## Appendix: Quick Reference

### ELK-BLEDOM Command Cheat Sheet
```
SET COLOR:      7E 00 05 03 RR GG BB 00 EF
BRIGHTNESS:     7E 04 01 LL MM FF FF 00 EF   (LL=level, MM=mode)
POWER ON:       7E 04 04 01 00 00 00 00 EF
POWER OFF:      7E 04 04 00 00 00 FF 00 EF
EFFECT:         7E 05 03 EE SS FF FF 00 EF   (EE=effect 0x80-0x9A, SS=speed)
COLOR TEMP:     7E 00 05 02 WW CC 00 00 EF   (WW=warm, CC=cold, 0-100)
```

### Key Files
| File | Purpose |
|------|---------|
| `specs/device-elk-bledom.md` | Full protocol specification |
| `scripts/ble_capture.py` | HCI snoop parser + ELK-BLEDOM decoder |
| `scripts/ble_scan.py` | BLE device discovery via ADB |
| `scripts/elk_control.py` | Direct BLE control library |
| `scripts/elk_dashboard.py` | HTTP-to-BLE bridge + UI |
| `scripts/elk_calibrate.py` | Calibration system library |

### HCI Snoop Quick Parse
```python
# Datalink 1002 (H4): first byte = packet type
# 0x02 = ACL data → L2CAP → ATT
# ATT opcode 0x52 = Write Command (no response)
# ATT opcode 0x12 = Write Request (expects response)
# L2CAP channel 0x0004 = ATT bearer
# BLE MAC addresses: reverse 6 bytes for display
```
