#!/usr/bin/env python3
"""BLE device scanner using Android phone via ADB.

Discovers BLE devices using a connected Android phone, categorizes them,
and identifies likely target devices for reverse engineering.

Requires: ADB connection to an Android phone with Bluetooth enabled.
For best results, run nRF Connect (or any BLE scanner app) on the phone
while this script monitors.

Usage:
    python3 scripts/ble_scan.py                          # Quick snapshot via dumpsys
    python3 scripts/ble_scan.py --live                   # Live monitoring via logcat (30s default)
    python3 scripts/ble_scan.py --live --duration 60     # Live monitoring for 60s
    python3 scripts/ble_scan.py --hci                    # Pull & parse HCI snoop log
    python3 scripts/ble_scan.py --device 192.168.86.24   # Target specific ADB device
    python3 scripts/ble_scan.py --json                   # Output as JSON
"""

import subprocess
import sys
import re
import time
import signal
import argparse
import json
import struct
from collections import defaultdict
from datetime import datetime
from pathlib import Path

# ---------------------------------------------------------------------------
# OUI / manufacturer prefixes (common BLE chip vendors)
# ---------------------------------------------------------------------------
OUI_DB = {
    # Major BLE chip vendors
    "00:1A:7D": "Shenzhen RF-star",
    "00:02:5B": "Cambridge Silicon Radio (Qualcomm)",
    "00:07:80": "Bluegiga (Silicon Labs)",
    "00:0B:57": "Silicon Labs",
    "00:12:6F": "Raylios (BLE modules)",
    "00:15:83": "IVTCORPORATION (BLE)",
    "00:1B:DC": "Vencer (Shenzhen)",
    "00:25:48": "Cypress Semiconductor",
    "00:26:AB": "SMC Networks",
    "38:3B:26": "Apple",
    "40:4E:36": "HTC",
    "4C:24:98": "Apple",
    "4C:57:CA": "Apple",
    "50:33:8B": "Texas Instruments",
    "54:4A:16": "Texas Instruments",
    "5C:31:3E": "Microchip (Atmel)",
    "5C:F3:70": "Espressif (ESP32)",
    "60:77:71": "Espressif (ESP32)",
    "68:27:19": "Texas Instruments (CC26xx)",
    "7C:01:0A": "Texas Instruments",
    "7C:EC:79": "Texas Instruments",
    "80:E1:26": "AzureWave (Broadcom)",
    "84:71:27": "Espressif (ESP32)",
    "98:4F:EE": "Espressif (ESP32)",
    "A4:CF:12": "Espressif (ESP32)",
    "AC:23:3F": "Shenzhen Minew",
    "B8:27:EB": "Raspberry Pi",
    "C4:4F:33": "Espressif (ESP32)",
    "C8:C9:A3": "Espressif (ESP32)",
    "CC:50:E3": "Espressif (ESP32)",
    "D8:A0:1D": "Espressif (ESP32)",
    "DC:A6:32": "Raspberry Pi",
    "E0:98:06": "Espressif (ESP32)",
    "E8:31:CD": "Espressif (ESP32)",
    "E8:DB:84": "Espressif (ESP32)",
    "F0:08:D1": "Espressif (ESP32)",
    "F4:12:FA": "Espressif (ESP32)",
    "FC:F5:C4": "Espressif (ESP32)",
    # Random/resolvable addresses start with specific bits
    # These won't match OUI - that's fine
}

# ---------------------------------------------------------------------------
# Name patterns for target device categories
# ---------------------------------------------------------------------------
TARGET_PATTERNS = [
    (re.compile(r"(?i)led|light|strip|pixel|fairy|twinkl|gover|govee|magic|wiz"),
     "String Lights"),
    (re.compile(r"(?i)board|matrix|sign|display|scroll|marquee|panel|vestaboard|tidbyt|divoom"),
     "Message Board"),
    (re.compile(r"(?i)badge|pin|lapel|anime|totem|pixel\s*art|amulet"),
     "Lapel Pin/Badge"),
    (re.compile(r"(?i)hdmi|cast|dongle|screen|mirror|anycast|chromecast|mirascreen"),
     "HDMI Dongle"),
    (re.compile(r"(?i)speaker|audio|buds|pods|earbuds|jbl|bose|sony|beats|headphone"),
     "Audio (not target)"),
    (re.compile(r"(?i)watch|band|fit|garmin|fitbit|mi\s*band|amazfit"),
     "Wearable (not target)"),
    (re.compile(r"(?i)keyboard|mouse|trackpad|logitech|k380"),
     "HID (not target)"),
    (re.compile(r"(?i)phone|pixel|galaxy|iphone|samsung|oneplus"),
     "Phone (not target)"),
]

# ---------------------------------------------------------------------------
# BLE device model
# ---------------------------------------------------------------------------
class BLEDevice:
    def __init__(self, mac, name=None, rssi=None):
        self.mac = mac.upper()
        self.name = name or ""
        self.rssi = rssi
        self.manufacturer = self._lookup_oui()
        self.category = self._categorize()
        self.services = []
        self.adv_data = {}
        self.seen_count = 1
        self.first_seen = datetime.now()
        self.last_seen = datetime.now()

    def _lookup_oui(self):
        prefix = self.mac[:8].upper()
        return OUI_DB.get(prefix)

    def _categorize(self):
        if self.name:
            for pattern, category in TARGET_PATTERNS:
                if pattern.search(self.name):
                    return category
        return None

    def _is_random_mac(self):
        """Check if MAC is random (BLE privacy). Bit 1 of first octet is set."""
        try:
            first_byte = int(self.mac.split(":")[0], 16)
            return bool(first_byte & 0x02)
        except (ValueError, IndexError):
            return False

    def update(self, name=None, rssi=None):
        if name and name != self.name:
            self.name = name
            self.category = self._categorize()
        if rssi is not None:
            self.rssi = rssi
        self.seen_count += 1
        self.last_seen = datetime.now()

    def to_dict(self):
        return {
            "mac": self.mac,
            "name": self.name,
            "rssi": self.rssi,
            "manufacturer": self.manufacturer,
            "category": self.category,
            "random_mac": self._is_random_mac(),
            "seen_count": self.seen_count,
            "services": self.services,
        }

    @property
    def sort_key(self):
        """Sort: targets first, then by RSSI (strongest first)."""
        is_target = self.category and "not target" not in self.category
        rssi = self.rssi if self.rssi is not None else -999
        return (not is_target, -rssi)

    def __repr__(self):
        parts = [self.mac]
        if self.name:
            parts.append(f'"{self.name}"')
        if self.rssi is not None:
            parts.append(f"{self.rssi}dBm")
        return f"BLEDevice({', '.join(parts)})"


# ---------------------------------------------------------------------------
# ADB helpers
# ---------------------------------------------------------------------------
def adb_cmd(args, device=None, timeout=10):
    """Run an ADB command, return (stdout, stderr, returncode)."""
    cmd = ["adb"]
    if device:
        cmd += ["-s", device]
    cmd += args if isinstance(args, list) else args.split()
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        return r.stdout, r.stderr, r.returncode
    except subprocess.TimeoutExpired:
        return "", "timeout", 1
    except FileNotFoundError:
        print("ERROR: 'adb' not found. Is Android SDK platform-tools in PATH?")
        sys.exit(1)


def check_device(device=None):
    """Verify ADB connection and Bluetooth state. Returns device serial."""
    out, err, rc = adb_cmd(["devices"], timeout=5)
    if rc != 0:
        print(f"ERROR: adb devices failed: {err}")
        sys.exit(1)

    lines = [l.strip() for l in out.strip().splitlines()[1:] if l.strip()]
    connected = []
    for line in lines:
        parts = line.split("\t")
        if len(parts) >= 2 and parts[1] == "device":
            connected.append(parts[0])

    if not connected:
        print("ERROR: No ADB devices connected.")
        print("  Connect via USB or WiFi: adb connect <ip>:5555")
        sys.exit(1)

    if device:
        # Try partial match (e.g., "192.168.86.24" matches "192.168.86.24:5555")
        matches = [d for d in connected if device in d]
        if not matches:
            print(f"ERROR: Device '{device}' not found. Connected: {connected}")
            sys.exit(1)
        serial = matches[0]
    else:
        serial = connected[0]
        if len(connected) > 1:
            print(f"NOTE: Multiple devices connected, using {serial}")
            print(f"      Specify with --device. Available: {connected}")

    # Check Bluetooth state
    out, _, _ = adb_cmd(["shell", "settings", "get", "global", "bluetooth_on"], device=serial)
    bt_on = out.strip() == "1"
    if not bt_on:
        print(f"WARNING: Bluetooth is OFF on {serial}")
        print("  Enabling Bluetooth...")
        adb_cmd(["shell", "svc", "bluetooth", "enable"], device=serial)
        time.sleep(2)
        out, _, _ = adb_cmd(["shell", "settings", "get", "global", "bluetooth_on"], device=serial)
        if out.strip() != "1":
            print("  Failed to enable. Please enable Bluetooth manually.")
            sys.exit(1)
        print("  Bluetooth enabled.")

    return serial


# ---------------------------------------------------------------------------
# Scan methods
# ---------------------------------------------------------------------------
def scan_dumpsys(device):
    """Parse 'dumpsys bluetooth_manager' for device information."""
    devices = {}
    own_addr = None

    out, _, rc = adb_cmd(["shell", "dumpsys", "bluetooth_manager"], device=device, timeout=15)
    if rc != 0:
        print("WARNING: dumpsys bluetooth_manager failed")
        return devices

    # Get phone's own BT address to exclude it
    m = re.search(r"address:\s+([0-9A-Fa-f:]{17})", out)
    if m:
        own_addr = m.group(1).upper()

    # 1. Parse bonded devices
    in_bonded = False
    for line in out.splitlines():
        stripped = line.strip()
        if "Bonded devices:" in stripped:
            in_bonded = True
            continue
        if in_bonded:
            m = re.match(r"([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})\s*(.*)", stripped)
            if m:
                mac = m.group(1).upper()
                name = m.group(2).strip() or None
                if mac != own_addr:
                    devices[mac] = BLEDevice(mac, name)
            elif stripped and not re.match(r"[0-9A-Fa-f]{2}:", stripped):
                in_bonded = False

    # 2. Parse BluetoothDatabase Metadata entries (recent BLE devices seen)
    for m in re.finditer(
        r"\d{2}-\d{2}\s+[\d:.]+\s+\([^)]+\)\s+([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})\s+Metadata created",
        out,
    ):
        mac = m.group(1).upper()
        if mac != own_addr and mac not in devices:
            devices[mac] = BLEDevice(mac)

    # 3. Parse GATT Client Map connection entries (MAC with connection ID)
    for m in re.finditer(
        r":\s+([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})\s+\(\d+\)",
        out,
    ):
        mac = m.group(1).upper()
        if mac != own_addr and mac not in devices:
            devices[mac] = BLEDevice(mac)

    # 4. Skip catch-all MAC extraction — the patterns above cover the
    # structured sections. A blind regex picks up hex noise, key material,
    # and 00:00:00:00:00:00 placeholders.

    # 5. Extract scan statistics
    scan_stats = {}
    for m in re.finditer(
        r"LE scans \(started/stopped\)\s*:\s*(\d+)\s*/\s*(\d+)", out
    ):
        scan_stats["started"] = scan_stats.get("started", 0) + int(m.group(1))
        scan_stats["stopped"] = scan_stats.get("stopped", 0) + int(m.group(2))

    # Extract registered apps doing scanning
    scan_apps = re.findall(r"(\S+)\s+\(Registered\)", out)

    if scan_stats and not args_global.json_output:
        print(f"  BLE scan stats: {scan_stats.get('started', 0)} scans started, "
              f"{scan_stats.get('stopped', 0)} stopped")
    if scan_apps and not args_global.json_output:
        print(f"  Scanning apps: {', '.join(scan_apps)}")

    return devices


def scan_logcat(device, duration=30, callback=None):
    """Monitor logcat for BLE scan events in real-time.

    Requires a BLE scanner app (e.g., nRF Connect) to be actively scanning
    on the phone. This script passively reads the log output.
    """
    devices = {}

    # BLE-related logcat tags — cast a wide net
    tags = [
        "BtGatt.GattService:V",
        "BtGatt.ScanManager:V",
        "BtGatt.ContextMap:V",
        "BluetoothLeScanner:V",
        "BluetoothAdapter:V",
        "bt_btif:V",
        "bt_btm:V",
        "bt_btm_ble:V",
        "BtGatt.ScanHelper:V",
        "*:S",  # silence everything else
    ]

    print(f"Monitoring logcat for BLE events ({duration}s)...")
    print("TIP: Open nRF Connect on the phone and start a scan for best results.")
    print()

    cmd = ["adb"]
    if device:
        cmd += ["-s", device]
    cmd += ["logcat", "-v", "time", "-T", "1"]
    cmd += tags

    # Clear logcat first for clean results
    adb_cmd(["logcat", "-c"], device=device)

    import select
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    start = time.time()

    # Patterns to extract BLE device info from logcat
    mac_pattern = re.compile(r"([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})")
    rssi_pattern = re.compile(r"(?:rssi|RSSI)[=:\s]+(-?\d+)")
    name_pattern = re.compile(r"(?:name|Name|device_name)[=:\s]+['\"]?([^'\"=,\]\n]+)")

    try:
        while time.time() - start < duration:
            # Non-blocking read with select
            ready, _, _ = select.select([proc.stdout], [], [], 1.0)
            if not ready:
                elapsed = int(time.time() - start)
                if not args_global.json_output and elapsed % 5 == 0 and elapsed > 0:
                    print(f"  ... {elapsed}s / {duration}s  ({len(devices)} devices so far)")
                continue
            line = proc.stdout.readline()
            if not line:
                continue

            mac_match = mac_pattern.search(line)
            if not mac_match:
                continue

            mac = mac_match.group(1).upper()
            rssi_match = rssi_pattern.search(line)
            name_match = name_pattern.search(line)

            rssi = int(rssi_match.group(1)) if rssi_match else None
            name = name_match.group(1).strip() if name_match else None
            # Filter out garbage names
            if name and (len(name) < 2 or name.lower() in ("null", "none", "n/a")):
                name = None

            if mac in devices:
                devices[mac].update(name=name, rssi=rssi)
            else:
                dev = BLEDevice(mac, name, rssi)
                devices[mac] = dev
                if callback:
                    callback(dev)
                elif not args_global.json_output:
                    indicator = ""
                    if dev.category and "not target" not in (dev.category or ""):
                        indicator = " *** TARGET ***"
                    print(f"  NEW: {mac}  {name or '(unnamed)':30s}  "
                          f"{'%d dBm' % rssi if rssi else '?':>8s}{indicator}")

    except KeyboardInterrupt:
        pass
    finally:
        proc.terminate()
        proc.wait()

    return devices


# ---------------------------------------------------------------------------
# HCI snoop log parsing
# ---------------------------------------------------------------------------
BTSNOOP_HEADER = b"btsnoop\x00"
HCI_TYPE_ACL = 2
HCI_TYPE_EVT = 4

# HCI LE Meta Event subevent codes
EVT_LE_ADVERTISING_REPORT = 0x02

def parse_hci_snoop(device):
    """Pull and parse the Bluetooth HCI snoop log from the device."""
    devices = {}

    # Common snoop log locations on different Android versions
    snoop_paths = [
        "/data/misc/bluetooth/logs/btsnoop_hci.log",
        "/data/log/bt/btsnoop_hci.log",
        "/sdcard/btsnoop_hci.log",
        "/data/misc/bluetooth/btsnoop_hci.log",
    ]

    local_path = Path("captures/btsnoop_hci.log")
    local_path.parent.mkdir(parents=True, exist_ok=True)

    pulled = False
    for remote_path in snoop_paths:
        _, err, rc = adb_cmd(
            ["pull", remote_path, str(local_path)], device=device, timeout=30
        )
        if rc == 0:
            print(f"Pulled HCI snoop log from {remote_path}")
            pulled = True
            break

    if not pulled:
        # Try via bugreport extraction (root not needed)
        print("Could not pull HCI snoop log directly.")
        print()
        print("To enable HCI snoop logging:")
        print("  1. Settings > Developer Options > Enable Bluetooth HCI snoop log")
        print("  2. Toggle Bluetooth OFF then ON")
        print("  3. Run your BLE scan (nRF Connect)")
        print("  4. Re-run this script with --hci")
        return devices

    # Parse the btsnoop file
    try:
        with open(local_path, "rb") as f:
            data = f.read()
    except IOError as e:
        print(f"ERROR: Cannot read {local_path}: {e}")
        return devices

    if not data.startswith(BTSNOOP_HEADER):
        print(f"ERROR: {local_path} is not a valid btsnoop file")
        return devices

    # btsnoop header: 16 bytes (8 magic + 4 version + 4 datalink type)
    offset = 16

    pkt_count = 0
    adv_count = 0

    while offset + 24 <= len(data):
        # Packet record header: 24 bytes
        # 4: original length, 4: included length, 4: packet flags,
        # 4: cumulative drops, 8: timestamp (microseconds since epoch)
        orig_len, incl_len, flags, drops, ts = struct.unpack_from(
            ">IIIII", data, offset
        )
        # timestamp is actually 8 bytes, re-read properly
        orig_len, incl_len, flags, drops = struct.unpack_from(">IIII", data, offset)
        ts = struct.unpack_from(">Q", data, offset + 16)[0]
        offset += 24

        if offset + incl_len > len(data):
            break

        pkt_data = data[offset : offset + incl_len]
        offset += incl_len
        pkt_count += 1

        # We want HCI events (type 4), specifically LE Advertising Reports
        # Flags bit 0: 0=sent, 1=received. Bit 1: 0=data, 1=cmd/evt
        is_received = flags & 1
        is_event = flags & 2

        if not (is_received and is_event):
            continue
        if len(pkt_data) < 3:
            continue

        # HCI Event: event_code(1) + param_len(1) + params...
        event_code = pkt_data[0]
        param_len = pkt_data[1]

        # LE Meta Event = 0x3E
        if event_code != 0x3E:
            continue
        if len(pkt_data) < 4:
            continue

        subevent = pkt_data[2]
        if subevent != EVT_LE_ADVERTISING_REPORT:
            continue

        adv_count += 1

        # Parse LE Advertising Report
        # num_reports(1) + for each report:
        #   event_type(1) + address_type(1) + address(6) + data_length(1)
        #   + data(N) + rssi(1 signed)
        try:
            num_reports = pkt_data[3]
            rpt_offset = 4

            for _ in range(num_reports):
                if rpt_offset + 9 > len(pkt_data):
                    break
                evt_type = pkt_data[rpt_offset]
                addr_type = pkt_data[rpt_offset + 1]
                # Address is 6 bytes, little-endian
                addr_bytes = pkt_data[rpt_offset + 2 : rpt_offset + 8]
                mac = ":".join(f"{b:02X}" for b in reversed(addr_bytes))
                data_len = pkt_data[rpt_offset + 8]
                rpt_offset += 9

                adv_raw = pkt_data[rpt_offset : rpt_offset + data_len]
                rpt_offset += data_len

                rssi = None
                if rpt_offset < len(pkt_data):
                    rssi_byte = pkt_data[rpt_offset]
                    rssi = rssi_byte if rssi_byte < 128 else rssi_byte - 256
                    rpt_offset += 1

                # Parse advertising data structures
                name = _parse_adv_name(adv_raw)

                if mac in devices:
                    devices[mac].update(name=name, rssi=rssi)
                else:
                    devices[mac] = BLEDevice(mac, name, rssi)

        except (IndexError, struct.error):
            continue

    print(f"Parsed {pkt_count} HCI packets, {adv_count} advertising reports")
    print(f"Found {len(devices)} unique BLE devices")
    return devices


def _parse_adv_name(adv_data):
    """Extract device name from BLE advertising data."""
    offset = 0
    while offset < len(adv_data):
        if offset >= len(adv_data):
            break
        length = adv_data[offset]
        if length == 0:
            break
        if offset + length >= len(adv_data):
            break
        ad_type = adv_data[offset + 1]
        ad_data = adv_data[offset + 2 : offset + 1 + length]

        # 0x08 = Shortened Local Name, 0x09 = Complete Local Name
        if ad_type in (0x08, 0x09):
            try:
                return ad_data.decode("utf-8", errors="replace").strip("\x00")
            except Exception:
                pass

        offset += 1 + length
    return None


# ---------------------------------------------------------------------------
# Display
# ---------------------------------------------------------------------------
RESET = "\033[0m"
BOLD = "\033[1m"
GREEN = "\033[32m"
YELLOW = "\033[33m"
RED = "\033[31m"
CYAN = "\033[36m"
DIM = "\033[2m"

def display_results(devices, json_output=False):
    """Display sorted, categorized scan results."""
    if not devices:
        print("\nNo BLE devices found.")
        return

    dev_list = sorted(devices.values(), key=lambda d: d.sort_key)

    if json_output:
        print(json.dumps([d.to_dict() for d in dev_list], indent=2))
        return

    # Group by category
    targets = [d for d in dev_list if d.category and "not target" not in d.category]
    known_non = [d for d in dev_list if d.category and "not target" in d.category]
    unknown = [d for d in dev_list if not d.category]

    print(f"\n{'=' * 78}")
    print(f" BLE Scan Results — {len(dev_list)} devices found")
    print(f"{'=' * 78}")

    if targets:
        print(f"\n{GREEN}{BOLD}  POTENTIAL TARGETS ({len(targets)}){RESET}")
        print(f"  {'MAC':17s}  {'Name':30s}  {'RSSI':>6s}  {'Category'}")
        print(f"  {'─' * 17}  {'─' * 30}  {'─' * 6}  {'─' * 20}")
        for d in targets:
            rssi_str = f"{d.rssi}dB" if d.rssi is not None else "?"
            print(f"  {GREEN}{d.mac}{RESET}  {d.name:30s}  {rssi_str:>6s}  {d.category}")
            if d.manufacturer:
                print(f"  {DIM}{'':17s}  chip: {d.manufacturer}{RESET}")

    if known_non:
        print(f"\n{DIM}  KNOWN NON-TARGETS ({len(known_non)}){RESET}")
        print(f"  {'MAC':17s}  {'Name':30s}  {'RSSI':>6s}  {'Category'}")
        print(f"  {'─' * 17}  {'─' * 30}  {'─' * 6}  {'─' * 20}")
        for d in known_non:
            rssi_str = f"{d.rssi}dB" if d.rssi is not None else "?"
            print(f"  {DIM}{d.mac}  {d.name:30s}  {rssi_str:>6s}  {d.category}{RESET}")

    if unknown:
        print(f"\n{YELLOW}  UNCLASSIFIED ({len(unknown)}){RESET}")
        print(f"  {'MAC':17s}  {'Name':30s}  {'RSSI':>6s}  {'Manufacturer':20s}  Seen")
        print(f"  {'─' * 17}  {'─' * 30}  {'─' * 6}  {'─' * 20}  {'─' * 4}")
        for d in unknown:
            rssi_str = f"{d.rssi}dB" if d.rssi is not None else "?"
            mfr = d.manufacturer or ("random" if d._is_random_mac() else "")
            print(f"  {d.mac}  {d.name or '(unnamed)':30s}  {rssi_str:>6s}  {mfr:20s}  {d.seen_count:>3d}x")

    # Summary
    print(f"\n{'─' * 78}")
    random_count = sum(1 for d in dev_list if d._is_random_mac())
    print(f"  Total: {len(dev_list)} devices  |  "
          f"Targets: {len(targets)}  |  "
          f"Random MACs: {random_count}  |  "
          f"Named: {sum(1 for d in dev_list if d.name)}")

    if targets:
        print(f"\n{BOLD}  Next steps:{RESET}")
        print(f"  1. Open nRF Connect on phone, connect to target device")
        print(f"  2. Browse services/characteristics and note UUIDs")
        print(f"  3. Enable HCI snoop log, capture a session, then:")
        print(f"     python3 scripts/ble_scan.py --hci")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
args_global = None  # Set in main() for callback access

def main():
    global args_global

    parser = argparse.ArgumentParser(
        description="BLE device scanner via Android phone + ADB",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  %(prog)s                           Quick snapshot from dumpsys
  %(prog)s --live                    Monitor logcat for 30s
  %(prog)s --live --duration 60      Monitor for 60s
  %(prog)s --hci                     Parse HCI snoop log
  %(prog)s --device 192.168.86.24    Use specific device
  %(prog)s --json                    JSON output
        """,
    )
    parser.add_argument("--device", "-d", help="ADB device serial or IP")
    parser.add_argument("--live", "-l", action="store_true",
                        help="Live monitoring via logcat (run nRF Connect on phone)")
    parser.add_argument("--duration", "-t", type=int, default=30,
                        help="Duration for live scan in seconds (default: 30)")
    parser.add_argument("--hci", action="store_true",
                        help="Pull and parse HCI snoop log")
    parser.add_argument("--json", "-j", dest="json_output", action="store_true",
                        help="Output as JSON")
    args = parser.parse_args()
    args_global = args

    serial = check_device(args.device)
    print(f"Using device: {serial}")

    all_devices = {}

    if args.hci:
        # Parse HCI snoop log
        hci_devices = parse_hci_snoop(serial)
        all_devices.update(hci_devices)

    elif args.live:
        # Live logcat monitoring
        logcat_devices = scan_logcat(serial, duration=args.duration)
        all_devices.update(logcat_devices)

    else:
        # Default: dumpsys snapshot
        dump_devices = scan_dumpsys(serial)
        all_devices.update(dump_devices)

    display_results(all_devices, json_output=args.json_output)

    # Save results
    if all_devices:
        out_dir = Path("captures")
        out_dir.mkdir(exist_ok=True)
        ts = datetime.now().strftime("%Y%m%d_%H%M%S")
        out_path = out_dir / f"scan_{ts}.json"
        with open(out_path, "w") as f:
            json.dump([d.to_dict() for d in all_devices.values()], f, indent=2)
        if not args.json_output:
            print(f"\n  Results saved to {out_path}")


if __name__ == "__main__":
    main()
