#!/usr/bin/env python3
"""BLE protocol capture and analysis tool.

Pulls HCI snoop log from an Android device, parses BLE GATT writes,
and maps them to known protocol commands (e.g., ELK-BLEDOM LED strips).

Usage:
    python3 scripts/ble_capture.py                        # Pull & parse from default device
    python3 scripts/ble_capture.py --device 192.168.86.250:38772  # Specific device
    python3 scripts/ble_capture.py --file captures/btsnoop_hci.log  # Parse local file
    python3 scripts/ble_capture.py --target BE:67:00:35:04:9B      # Filter by device MAC
    python3 scripts/ble_capture.py --json                           # JSON output
"""

import subprocess
import sys
import struct
import argparse
import json
from pathlib import Path
from datetime import datetime, timedelta

# ---------------------------------------------------------------------------
# ELK-BLEDOM protocol decoder
# ---------------------------------------------------------------------------
ELKBLEDOM_WRITE_UUID = 0xFFF3
ELKBLEDOM_NOTIFY_UUID = 0xFFF4

def decode_elk_bledom(data):
    """Decode an ELK-BLEDOM command payload (9 bytes, 0x7E...0xEF)."""
    if len(data) < 9:
        return {"raw": data.hex(), "error": "too short"}
    if data[0] != 0x7E or data[8] != 0xEF:
        return {"raw": data.hex(), "error": "invalid framing"}

    cmd_byte = data[2]
    result = {"raw": data.hex(), "bytes": list(data)}

    if cmd_byte == 0x05 and data[3] == 0x03:
        # Change Color
        r, g, b = data[4], data[5], data[6]
        result["command"] = "SET_COLOR"
        result["color"] = {"r": r, "g": g, "b": b}
        result["hex_color"] = f"#{r:02x}{g:02x}{b:02x}"
        result["description"] = f"Set color to RGB({r}, {g}, {b}) = {result['hex_color']}"

    elif cmd_byte == 0x01:
        # Change Brightness
        brightness = data[3]
        mode = data[4]
        result["command"] = "SET_BRIGHTNESS"
        result["brightness"] = brightness
        result["brightness_pct"] = round(brightness / 255 * 100, 1)
        result["mode"] = mode
        result["description"] = f"Set brightness to {brightness} ({result['brightness_pct']}%)"

    elif cmd_byte == 0x05 and data[3] == 0x02:
        # Color Temperature
        warm = data[4]
        cold = data[5]
        result["command"] = "SET_COLOR_TEMP"
        result["warm"] = warm
        result["cold"] = cold
        result["description"] = f"Set color temp: warm={warm}, cold={cold}"

    elif cmd_byte == 0x05 and data[3] == 0x01:
        # Single Color
        color = data[4]
        result["command"] = "SET_SINGLE_COLOR"
        result["color_value"] = color
        result["description"] = f"Set single color channel: {color}"

    elif cmd_byte == 0x04:
        # Power on/off
        state = data[3]
        result["command"] = "POWER"
        if state == 0x01:
            result["on"] = True
            result["description"] = "Power ON"
        elif state == 0xF0:
            result["on"] = False
            result["description"] = "Power OFF (save state)"
        else:
            result["on"] = False
            result["description"] = f"Power OFF (state=0x{state:02x})"

    elif cmd_byte == 0x02:
        # Set speed (for current effect)
        speed = data[3]
        result["command"] = "SET_SPEED"
        result["speed"] = speed
        result["description"] = f"Set effect speed to {speed}"

    elif cmd_byte == 0x03:
        # Effect/Mode
        effect = data[3]
        speed = data[4]
        result["command"] = "SET_EFFECT"
        result["effect"] = effect
        result["speed"] = speed
        effect_names = {
            0x80: "Static", 0x81: "Jump RGB", 0x82: "Jump RGBYCMW",
            0x83: "Crossfade RGB", 0x84: "Crossfade RGBYCMW",
            0x85: "Breathe RGB", 0x86: "Breathe RGBYCMW",
            0x87: "Rainbow fade", 0x88: "Rainbow pulse",
            0x89: "Rainbow strobe", 0x8A: "Color cycle", 0x8B: "RGB chase",
            0x8C: "Rainbow chase", 0x8D: "Red pulse", 0x8E: "Green pulse",
            0x8F: "Blue pulse", 0x90: "Yellow pulse", 0x91: "Cyan pulse",
            0x92: "Magenta pulse", 0x93: "White pulse", 0x94: "RG chase",
            0x95: "RB chase", 0x96: "GB chase",
            0x97: "RG breathe", 0x98: "RB breathe", 0x99: "GB breathe",
            0x9A: "RGB breathe chase",
        }
        result["effect_name"] = effect_names.get(effect, f"Unknown (0x{effect:02x})")
        result["description"] = f"Set effect: {result['effect_name']}, speed={speed}"

    elif cmd_byte == 0x82:
        # Timer/schedule
        hour = data[3]
        minute = data[4]
        action = data[6]  # 0x01 = on, 0x00 = off
        flags = data[7]
        result["command"] = "SET_TIMER"
        result["hour"] = hour
        result["minute"] = minute
        result["action"] = "ON" if action == 0x01 else "OFF"
        result["flags"] = flags
        result["description"] = (f"Timer: {hour:02d}:{minute:02d} → "
                                 f"{'ON' if action == 0x01 else 'OFF'}"
                                 f"{' (enabled)' if flags & 0x80 else ' (disabled)'}")

    elif cmd_byte == 0x83:
        # Timing/schedule (legacy/alt format)
        result["command"] = "SET_TIMER"
        result["description"] = f"Timer command: {data[3:8].hex()}"

    else:
        result["command"] = "UNKNOWN"
        result["description"] = f"Unknown cmd=0x{cmd_byte:02x} id=0x{data[1]:02x}: {data.hex()}"

    return result


# ---------------------------------------------------------------------------
# btsnoop HCI log parser
# ---------------------------------------------------------------------------
BTSNOOP_MAGIC = b"btsnoop\x00"

def parse_btsnoop(filepath, target_mac=None):
    """Parse a btsnoop HCI log file and extract GATT write operations."""
    with open(filepath, "rb") as f:
        data = f.read()

    if not data.startswith(BTSNOOP_MAGIC):
        print(f"ERROR: {filepath} is not a valid btsnoop file")
        return []

    # Header: 8 magic + 4 version + 4 datalink type = 16 bytes
    version = struct.unpack_from(">I", data, 8)[0]
    datalink = struct.unpack_from(">I", data, 12)[0]
    # H4 format (1002) has a packet type indicator byte before the HCI data
    is_h4 = (datalink == 1002)
    offset = 16

    packets = []
    connections = {}  # handle -> MAC address mapping
    gatt_writes = []
    pkt_count = 0

    while offset + 24 <= len(data):
        orig_len, incl_len, flags, drops = struct.unpack_from(">IIII", data, offset)
        ts_raw = struct.unpack_from(">Q", data, offset + 16)[0]
        offset += 24

        if offset + incl_len > len(data):
            break

        raw_pkt = data[offset:offset + incl_len]
        offset += incl_len
        pkt_count += 1

        # H4: first byte is packet type (0x01=cmd, 0x02=ACL, 0x04=event)
        if is_h4 and len(raw_pkt) >= 1:
            h4_type = raw_pkt[0]
            pkt_data = raw_pkt[1:]
        else:
            # H1 format: use flags to determine packet type
            is_received = flags & 1
            is_cmd_evt = flags & 2
            h4_type = 0x04 if (is_received and is_cmd_evt) else (0x02 if not is_cmd_evt else 0x01)
            pkt_data = raw_pkt

        # --- Parse HCI Event packets for connection mapping ---
        if h4_type == 0x04 and len(pkt_data) >= 2:
            evt_code = pkt_data[0]

            # LE Meta Event (0x3E)
            if evt_code == 0x3E and len(pkt_data) >= 4:
                subevent = pkt_data[2]
                # LE Connection Complete (0x01)
                if subevent == 0x01 and len(pkt_data) >= 13:
                    status = pkt_data[3]
                    if status == 0:
                        handle = struct.unpack_from("<H", pkt_data, 4)[0]
                        addr_bytes = pkt_data[7:13]
                        mac = ":".join(f"{b:02X}" for b in reversed(addr_bytes))
                        connections[handle] = mac
                # LE Enhanced Connection Complete (0x0A)
                elif subevent == 0x0A and len(pkt_data) >= 31:
                    status = pkt_data[3]
                    if status == 0:
                        handle = struct.unpack_from("<H", pkt_data, 4)[0]
                        # role(1) + peer_addr_type(1) + peer_addr(6)
                        addr_bytes = pkt_data[8:14]
                        mac = ":".join(f"{b:02X}" for b in reversed(addr_bytes))
                        connections[handle] = mac

        # --- Parse ACL data packets for GATT writes ---
        if h4_type == 0x02 and len(pkt_data) >= 9:
            # ACL header: handle(2) + length(2) + L2CAP header: length(2) + CID(2)
            handle_flags = struct.unpack_from("<H", pkt_data, 0)[0]
            handle = handle_flags & 0x0FFF
            acl_len = struct.unpack_from("<H", pkt_data, 2)[0]

            if len(pkt_data) < 4 + acl_len:
                continue

            l2cap_data = pkt_data[4:]
            if len(l2cap_data) < 4:
                continue

            l2cap_len = struct.unpack_from("<H", l2cap_data, 0)[0]
            l2cap_cid = struct.unpack_from("<H", l2cap_data, 2)[0]

            # ATT channel = CID 0x0004
            if l2cap_cid != 0x0004:
                continue

            att_data = l2cap_data[4:]
            if len(att_data) < 1:
                continue

            att_opcode = att_data[0]

            # ATT Write Request (0x12), Write Command (0x52)
            if att_opcode in (0x12, 0x52) and len(att_data) >= 3:
                att_handle = struct.unpack_from("<H", att_data, 1)[0]
                write_data = att_data[3:]

                device_mac = connections.get(handle, f"handle:{handle}")

                # Filter by target MAC if specified
                if target_mac and device_mac.upper() != target_mac.upper():
                    if not device_mac.startswith("handle:"):
                        continue

                # Convert timestamp (microseconds from 0000-01-01)
                # btsnoop epoch: 0000-01-01T00:00:00 UTC
                # Unix epoch offset in microseconds
                unix_epoch_offset = 0x00dcddb30f2f8000
                ts_us = ts_raw - unix_epoch_offset
                ts_sec = ts_us / 1_000_000
                try:
                    timestamp = datetime.utcfromtimestamp(ts_sec)
                except (OSError, ValueError):
                    timestamp = None

                write_info = {
                    "timestamp": timestamp.isoformat() if timestamp else None,
                    "device_mac": device_mac,
                    "connection_handle": handle,
                    "att_handle": att_handle,
                    "att_opcode": "WRITE_REQ" if att_opcode == 0x12 else "WRITE_CMD",
                    "data": write_data,
                    "data_hex": write_data.hex(),
                    "data_len": len(write_data),
                }

                # Try to decode as ELK-BLEDOM command
                if len(write_data) == 9 and write_data[0] == 0x7E and write_data[8] == 0xEF:
                    write_info["decoded"] = decode_elk_bledom(write_data)

                gatt_writes.append(write_info)

    print(f"Parsed {pkt_count} HCI packets")
    print(f"Found {len(connections)} BLE connections")
    print(f"Extracted {len(gatt_writes)} GATT write operations")
    if connections:
        print(f"Connected devices: {dict(connections)}")

    return gatt_writes


# ---------------------------------------------------------------------------
# Display
# ---------------------------------------------------------------------------
RESET = "\033[0m"
BOLD = "\033[1m"
GREEN = "\033[32m"
YELLOW = "\033[33m"
CYAN = "\033[36m"
DIM = "\033[2m"
RED = "\033[31m"
MAGENTA = "\033[35m"

def color_block(r, g, b):
    """ANSI true-color block showing the actual color."""
    return f"\033[48;2;{r};{g};{b}m    {RESET}"

def display_writes(writes, json_output=False):
    """Display captured GATT writes in a readable format."""
    if not writes:
        print("\nNo GATT writes found.")
        print("Make sure HCI snoop logging was enabled BEFORE using the app.")
        return

    if json_output:
        out = []
        for w in writes:
            entry = {k: v for k, v in w.items() if k != "data"}
            out.append(entry)
        print(json.dumps(out, indent=2, default=str))
        return

    print(f"\n{'=' * 80}")
    print(f" GATT Write Capture — {len(writes)} operations")
    print(f"{'=' * 80}\n")

    decoded_count = 0
    for i, w in enumerate(writes):
        ts = w["timestamp"][:19] if w.get("timestamp") else "?"
        mac = w["device_mac"]
        opcode = w["att_opcode"]
        handle = w["att_handle"]
        hex_data = w["data_hex"]

        decoded = w.get("decoded")
        if decoded:
            decoded_count += 1
            cmd = decoded.get("command", "?")
            desc = decoded.get("description", "")

            # Color-code by command type
            if cmd == "SET_COLOR":
                c = decoded["color"]
                block = color_block(c["r"], c["g"], c["b"])
                print(f"  {DIM}{ts}{RESET}  {GREEN}{cmd:18s}{RESET}  "
                      f"{desc}  {block}")
            elif cmd == "POWER":
                color = GREEN if decoded["on"] else RED
                print(f"  {DIM}{ts}{RESET}  {color}{cmd:18s}{RESET}  {desc}")
            elif cmd == "SET_BRIGHTNESS":
                print(f"  {DIM}{ts}{RESET}  {YELLOW}{cmd:18s}{RESET}  {desc}")
            elif cmd == "SET_EFFECT":
                print(f"  {DIM}{ts}{RESET}  {MAGENTA}{cmd:18s}{RESET}  {desc}")
            else:
                print(f"  {DIM}{ts}{RESET}  {CYAN}{cmd:18s}{RESET}  {desc}")
        else:
            print(f"  {DIM}{ts}{RESET}  {DIM}RAW WRITE{RESET}          "
                  f"handle=0x{handle:04x}  {hex_data}")

    print(f"\n{'─' * 80}")
    print(f"  Total: {len(writes)} writes  |  Decoded: {decoded_count}  |  "
          f"Raw: {len(writes) - decoded_count}")

    # Generate summary of unique commands
    commands = {}
    for w in writes:
        decoded = w.get("decoded")
        if decoded:
            cmd = decoded.get("command", "UNKNOWN")
            commands[cmd] = commands.get(cmd, 0) + 1

    if commands:
        print(f"\n  Command summary:")
        for cmd, count in sorted(commands.items(), key=lambda x: -x[1]):
            print(f"    {cmd:20s}  {count:4d}x")


# ---------------------------------------------------------------------------
# ADB helpers
# ---------------------------------------------------------------------------
def adb_pull_snoop(device=None):
    """Pull HCI snoop log from Android device."""
    cmd_base = ["adb"]
    if device:
        cmd_base += ["-s", device]

    snoop_paths = [
        "/data/misc/bluetooth/logs/btsnoop_hci.log",
        "/data/log/bt/btsnoop_hci.log",
        "/sdcard/btsnoop_hci.log",
        "/data/misc/bluetooth/btsnoop_hci.log",
    ]

    local_dir = Path("captures")
    local_dir.mkdir(exist_ok=True)
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    local_path = local_dir / f"btsnoop_{ts}.log"

    for remote_path in snoop_paths:
        try:
            r = subprocess.run(
                cmd_base + ["pull", remote_path, str(local_path)],
                capture_output=True, text=True, timeout=30,
            )
            if r.returncode == 0:
                size = local_path.stat().st_size
                print(f"Pulled HCI snoop log from {remote_path}")
                print(f"  Saved to: {local_path} ({size:,} bytes)")
                return local_path
        except (subprocess.TimeoutExpired, FileNotFoundError):
            continue

    # Direct pull failed (permission denied) — try via bugreport
    print("Direct pull failed. Trying via bugreport (takes ~60s)...")
    br_path = local_dir / f"bugreport_{ts}.zip"
    try:
        r = subprocess.run(
            cmd_base + ["bugreport", str(br_path)],
            capture_output=True, text=True, timeout=180,
        )
        if r.returncode == 0 and br_path.exists():
            import zipfile
            with zipfile.ZipFile(br_path, "r") as zf:
                for name in zf.namelist():
                    if "btsnoop_hci.log" in name:
                        snoop_data = zf.read(name)
                        with open(local_path, "wb") as f:
                            f.write(snoop_data)
                        size = len(snoop_data)
                        print(f"Extracted HCI snoop log from bugreport")
                        print(f"  Saved to: {local_path} ({size:,} bytes)")
                        return local_path
            print("Bugreport pulled but no snoop log inside.")
            print("BT may not have been toggled after enabling HCI snoop.")
    except (subprocess.TimeoutExpired, FileNotFoundError, Exception) as e:
        print(f"Bugreport extraction failed: {e}")

    print()
    print("To enable HCI snoop logging:")
    print("  1. On the phone: Settings > Developer Options")
    print("  2. Set 'Bluetooth HCI snoop log' to Enabled")
    print("  3. Toggle Bluetooth OFF then ON")
    print("  4. Use the LED app (change colors, effects, etc.)")
    print("  5. Re-run this script")
    return None


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
def main():
    parser = argparse.ArgumentParser(
        description="BLE protocol capture & analysis via HCI snoop log",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Workflow:
  1. Enable HCI snoop log in Developer Options on the Android phone
  2. Toggle Bluetooth OFF/ON to start logging
  3. Use the proprietary app (Lotus Lantern, etc.) — change colors, effects
  4. Run this script to pull the log and decode all commands

Examples:
  %(prog)s --device 192.168.86.250:38772
  %(prog)s --file captures/btsnoop_20260207.log
  %(prog)s --target BE:67:00:35:04:9B
        """,
    )
    parser.add_argument("--device", "-d", help="ADB device serial or IP")
    parser.add_argument("--file", "-f", help="Local btsnoop_hci.log file to parse")
    parser.add_argument("--target", "-t",
                        help="Filter by target BLE device MAC address")
    parser.add_argument("--json", "-j", dest="json_output", action="store_true",
                        help="Output as JSON")
    args = parser.parse_args()

    if args.file:
        filepath = Path(args.file)
        if not filepath.exists():
            print(f"ERROR: File not found: {filepath}")
            sys.exit(1)
    else:
        filepath = adb_pull_snoop(args.device)
        if not filepath:
            sys.exit(1)

    writes = parse_btsnoop(filepath, target_mac=args.target)
    display_writes(writes, json_output=args.json_output)

    # Save decoded results
    if writes and not args.json_output:
        out_dir = Path("captures")
        out_dir.mkdir(exist_ok=True)
        ts = datetime.now().strftime("%Y%m%d_%H%M%S")
        out_path = out_dir / f"decoded_{ts}.json"
        out_data = []
        for w in writes:
            entry = {k: v for k, v in w.items() if k != "data"}
            out_data.append(entry)
        with open(out_path, "w") as f:
            json.dump(out_data, f, indent=2, default=str)
        print(f"\n  Decoded results saved to {out_path}")


if __name__ == "__main__":
    main()
