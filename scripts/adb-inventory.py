#!/usr/bin/env python3
"""
ADB Device Inventory for WitnessMark

Lists all connected ADB devices with their status, Android version,
and WitnessMark Beacon app version.

Usage:
    python3 scripts/adb-inventory.py              # List connected devices
    python3 scripts/adb-inventory.py --connect    # Connect to known devices first
    python3 scripts/adb-inventory.py --json       # JSON output for scripting
    python3 scripts/adb-inventory.py --install-all # Install APK to all devices
"""

import argparse
import subprocess
import json
import sys
import os
import re
import time
import signal
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, asdict
from typing import Optional, List

# ANSI color codes
GREEN = "\033[92m"
YELLOW = "\033[93m"
BLUE = "\033[94m"
RED = "\033[91m"
GRAY = "\033[90m"
RESET = "\033[0m"
BOLD = "\033[1m"
DIM = "\033[2m"

# ANSI cursor control
CLEAR_SCREEN = "\033[2J"
CURSOR_HOME = "\033[H"
HIDE_CURSOR = "\033[?25l"
SHOW_CURSOR = "\033[?25h"

# App packages
PACKAGE_BEACON = "com.witnessmark.beacon_app"
PACKAGE_REFLECTOR = "com.witnessmark.pennant_receiver"

# Known devices with their roles and expected IPs
# Port changes on reconnect, so we scan common ports
KNOWN_DEVICES = {
    "Pixel 10 Pro": {
        "ip": "192.168.86.178",
        "role": "beacon",
        "model_match": ["Pixel 10 Pro", "caiman"],
    },
    "Pixel 2 XL": {
        "ip": "192.168.86.24",
        "role": "beacon",
        "model_match": ["Pixel 2 XL", "taimen"],
    },
    "Pixel 2 (cracked)": {
        "ip": "192.168.86.20",
        "role": "beacon",
        "model_match": ["Pixel 2", "walleye"],
    },
    "Pixel 2 #2": {
        "ip": "192.168.86.250",
        "role": "beacon",
        "model_match": ["Pixel 2", "walleye"],
    },
    "BOOX Go6 (Reflector)": {
        "serial": "DAA41CD6",
        "role": "reflector",
        "model_match": ["Go6", "BOOX", "gta4lwifi"],
    },
    "H96 Max (Reflector)": {
        "ip": "192.168.86.23",
        "role": "reflector",
        "model_match": ["H96 Max", "H96Max", "rk3328"],
    },
    "P2CLEAR (Reflector)": {
        "ip": "192.168.86.25",
        "role": "reflector",
        "model_match": ["Pixel 2", "walleye"],
    },
}


@dataclass
class DeviceInfo:
    """Information about a connected ADB device"""
    address: str
    name: str
    model: str
    android_version: str
    app_version: Optional[str]
    app_state: str
    role: str
    online: bool
    screen_on: bool           # True if display is on
    unlocked: bool            # True if keyguard not showing
    charging: Optional[str]   # "USB", "AC", "Wireless", "Dock", or None
    battery_level: int        # 0-100, or -1 if no battery
    doze_mode: bool           # True if device is in Doze/idle
    serial: str               # Device serial number for deduplication
    conn_type: str            # "net" or "usb"

    def to_dict(self):
        return asdict(self)


def run_adb(args: List[str], timeout: int = 5) -> tuple[bool, str]:
    """Run an ADB command and return (success, output)"""
    try:
        result = subprocess.run(
            ["adb"] + args,
            capture_output=True,
            text=True,
            timeout=timeout
        )
        return result.returncode == 0, result.stdout.strip()
    except subprocess.TimeoutExpired:
        return False, "timeout"
    except Exception as e:
        return False, str(e)


def get_connected_devices() -> List[str]:
    """Get list of connected device addresses"""
    success, output = run_adb(["devices"])
    if not success:
        return []

    devices = []
    for line in output.split("\n")[1:]:  # Skip header
        if "\tdevice" in line:
            addr = line.split("\t")[0]
            devices.append(addr)
    return devices


def get_device_prop(address: str, prop: str) -> Optional[str]:
    """Get a device property via getprop"""
    success, output = run_adb(["-s", address, "shell", "getprop", prop])
    return output if success and output else None


def get_app_version(address: str, package: str) -> Optional[str]:
    """Get the installed app version"""
    success, output = run_adb(
        ["-s", address, "shell", "dumpsys", "package", package],
        timeout=10
    )
    if not success:
        return None

    # Look for versionName
    for line in output.split("\n"):
        if "versionName=" in line:
            match = re.search(r"versionName=([^\s]+)", line)
            if match:
                return match.group(1)
    return None


def get_app_state(address: str, package: str) -> str:
    """Check if the app is running, installed, or not installed"""
    # Check if running
    success, output = run_adb(["-s", address, "shell", "pidof", package])
    if success and output:
        return "running"

    # Check if installed
    success, output = run_adb(["-s", address, "shell", "pm", "list", "packages", package])
    if success and package in output:
        return "stopped"

    return "not installed"


def get_power_state(address: str) -> tuple[bool, bool]:
    """Get power state: (screen_on, doze_mode)"""
    screen_on = False
    doze_mode = False

    # Check screen wakefulness
    success, output = run_adb(["-s", address, "shell", "dumpsys", "power"], timeout=5)
    if success:
        for line in output.split("\n"):
            if "mWakefulness=" in line:
                screen_on = "Awake" in line
                break

    # Check doze mode
    success, output = run_adb(["-s", address, "shell", "dumpsys", "deviceidle"], timeout=5)
    if success:
        for line in output.split("\n"):
            if "mState=" in line:
                doze_mode = "IDLE" in line
                break

    return screen_on, doze_mode


def get_lock_state(address: str) -> bool:
    """Check if device is unlocked. Returns True if unlocked."""
    success, output = run_adb(["-s", address, "shell", "dumpsys", "window", "policy"], timeout=5)
    if not success:
        return False

    # Check keyguard status - mIsShowing=false means unlocked
    for line in output.split("\n"):
        if "mIsShowing=" in line:
            return "false" in line.lower()

    return False


def get_battery_info(address: str) -> tuple[Optional[str], int]:
    """Get battery information: (charging_source, battery_level)

    Returns:
        charging_source: "USB", "AC", "Wireless", "Dock", or None if not charging
        battery_level: Battery percentage (0-100), or -1 if no battery
    """
    success, output = run_adb(["-s", address, "shell", "dumpsys", "battery"], timeout=5)
    if not success:
        return None, -1

    charging_source = None
    battery_level = -1

    for line in output.split("\n"):
        line = line.strip()

        # Check charging sources
        if "USB powered: true" in line:
            charging_source = "USB"
        elif "AC powered: true" in line:
            charging_source = "AC"
        elif "Wireless powered: true" in line:
            charging_source = "Wireless"
        elif "Dock powered: true" in line:
            charging_source = "Dock"

        # Parse battery level
        if line.startswith("level:"):
            try:
                battery_level = int(line.split(":")[1].strip())
            except (ValueError, IndexError):
                pass

    return charging_source, battery_level


def identify_device(address: str, model: str) -> tuple[str, str]:
    """Identify device name and role from model and IP/serial"""
    ip = address.split(":")[0] if ":" in address else address

    for name, info in KNOWN_DEVICES.items():
        # Match by serial (USB devices)
        if "serial" in info and info["serial"] == address:
            return name, info["role"]
        # Match by IP (network devices)
        if "ip" in info and info["ip"] == ip:
            return name, info["role"]

    # Second pass: match by model (less specific)
    for name, info in KNOWN_DEVICES.items():
        for model_match in info["model_match"]:
            if model_match.lower() in model.lower():
                # For IP-based devices, require IP match
                if "ip" in info and info["ip"] == ip:
                    return name, info["role"]
                # For serial-based devices, require serial match
                if "serial" in info and info["serial"] == address:
                    return name, info["role"]

    # Unknown device
    return model or address, "unknown"


def query_device(address: str) -> DeviceInfo:
    """Query a single device for all information"""
    # Get basic properties
    model = get_device_prop(address, "ro.product.model") or "Unknown"
    android = get_device_prop(address, "ro.build.version.release") or "?"
    serial = get_device_prop(address, "ro.serialno") or address

    # Determine connection type (network vs USB)
    conn_type = "net" if ":" in address and not address.startswith("adb-") else "usb"

    # Identify device first to determine which package to check
    name, role = identify_device(address, model)

    # Use appropriate package based on role
    package = PACKAGE_REFLECTOR if role == "reflector" else PACKAGE_BEACON

    # Get app info
    app_version = get_app_version(address, package)
    app_state = get_app_state(address, package)

    # Get state information
    screen_on, doze_mode = get_power_state(address)
    unlocked = get_lock_state(address)
    charging, battery_level = get_battery_info(address)

    return DeviceInfo(
        address=address,
        name=name,
        model=model,
        android_version=android,
        app_version=app_version,
        app_state=app_state,
        role=role,
        online=True,
        screen_on=screen_on,
        unlocked=unlocked,
        charging=charging,
        battery_level=battery_level,
        doze_mode=doze_mode,
        serial=serial,
        conn_type=conn_type
    )


def try_connect(ip: str, port_range: tuple = (30000, 50000)) -> Optional[str]:
    """Try to connect to a device, scanning ports if needed"""
    # First check if already connected
    devices = get_connected_devices()
    for dev in devices:
        if ip in dev:
            return dev

    # Try connecting with stored port (from recent connections)
    for known in KNOWN_DEVICES.values():
        if known["ip"] == ip:
            break

    # Try common port ranges
    ports_to_try = [5555, 37000, 38000, 39000, 40000, 41000, 42000, 43000, 44000, 45000, 46000, 47000]

    for port in ports_to_try:
        addr = f"{ip}:{port}"
        success, _ = run_adb(["connect", addr], timeout=2)
        if success:
            # Verify connection
            devices = get_connected_devices()
            if any(ip in d for d in devices):
                return addr

    return None


def connect_known_devices() -> List[str]:
    """Attempt to connect to all known devices"""
    connected = []
    print(f"{BLUE}Connecting to known devices...{RESET}\n")

    with ThreadPoolExecutor(max_workers=4) as executor:
        futures = {
            executor.submit(try_connect, info["ip"]): name
            for name, info in KNOWN_DEVICES.items()
            if "ip" in info  # Skip USB-only devices like BOOX
        }

        for future in as_completed(futures):
            name = futures[future]
            try:
                result = future.result()
                if result:
                    print(f"  {GREEN}✓{RESET} {name}: {result}")
                    connected.append(result)
                else:
                    print(f"  {RED}✗{RESET} {name}: offline")
            except Exception as e:
                print(f"  {RED}✗{RESET} {name}: {e}")

    print()
    return connected


def get_latest_apk() -> Optional[str]:
    """Find the latest built APK"""
    apk_path = os.path.expanduser(
        "~/witnessmark_beacon_app/build/app/outputs/flutter-apk/app-debug.apk"
    )
    if os.path.exists(apk_path):
        return apk_path
    return None


def install_to_all(devices: List[DeviceInfo]) -> None:
    """Install APK to all connected devices"""
    apk_path = get_latest_apk()
    if not apk_path:
        print(f"{RED}Error: No APK found. Run 'flutter build apk --debug' first.{RESET}")
        return

    print(f"{BLUE}Installing {os.path.basename(apk_path)} to all devices...{RESET}\n")

    for device in devices:
        if not device.online:
            print(f"  {GRAY}⊘{RESET} {device.name}: offline")
            continue

        print(f"  {YELLOW}⋯{RESET} {device.name}: installing...", end="", flush=True)
        success, output = run_adb(
            ["-s", device.address, "install", "-r", apk_path],
            timeout=60
        )
        if success:
            print(f"\r  {GREEN}✓{RESET} {device.name}: installed    ")
        else:
            print(f"\r  {RED}✗{RESET} {device.name}: {output[:30]}")

    print()


def truncate_addr(addr: str, max_len: int = 22) -> str:
    """Truncate address with ellipsis if too long"""
    if len(addr) <= max_len:
        return addr
    return addr[:max_len - 1] + "…"


def format_app_state(state: str) -> str:
    """Format app state for display"""
    if state == "not installed":
        return "absent"
    return state


def print_table(devices: List[DeviceInfo]) -> None:
    """Print devices in a formatted table"""
    if not devices:
        print(f"{YELLOW}No devices connected.{RESET}")
        print(f"\nTip: Run with --connect to connect to known devices")
        return

    # Column widths (fixed for cleaner output)
    col_role = 10
    col_state = 8
    col_pwr = 4    # Power/lock icons
    col_bat = 5    # Battery percentage
    col_name = max(15, max(len(d.name) for d in devices) + 2)
    col_addr = 18  # Shortened to keep table width reasonable
    col_android = 8
    col_version = 12

    # Header
    header = (
        f"│ {'Role':<{col_role}}"
        f"│ {'State':<{col_state}}"
        f"│ {'Pwr':<{col_pwr}}"
        f"│ {'Bat':<{col_bat}}"
        f"│ {'Device':<{col_name}}"
        f"│ {'Address':<{col_addr}}"
        f"│ {'Android':<{col_android}}"
        f"│ {'App Ver':<{col_version}}│"
    )

    # Top border
    print(f"┌{'─' * (col_role + 1)}┬{'─' * (col_state + 1)}┬{'─' * (col_pwr + 1)}┬{'─' * (col_bat + 1)}┬{'─' * (col_name + 1)}┬{'─' * (col_addr + 1)}┬{'─' * (col_android + 1)}┬{'─' * (col_version + 1)}┐")

    # Header row
    print(f"{BOLD}{header}{RESET}")

    # Separator
    print(f"├{'─' * (col_role + 1)}┼{'─' * (col_state + 1)}┼{'─' * (col_pwr + 1)}┼{'─' * (col_bat + 1)}┼{'─' * (col_name + 1)}┼{'─' * (col_addr + 1)}┼{'─' * (col_android + 1)}┼{'─' * (col_version + 1)}┤")

    # Data rows
    for d in devices:
        # Color coding
        if not d.online:
            name_color = GRAY
            state_color = GRAY
        elif d.app_state == "running":
            state_color = GREEN
            name_color = ""
        elif d.app_state == "stopped":
            state_color = YELLOW
            name_color = ""
        else:
            state_color = RED
            name_color = ""

        role_color = GREEN if d.role == "beacon" else (YELLOW if d.role == "reflector" else GRAY)
        version = d.app_version or "--"
        state_display = format_app_state(d.app_state)
        addr_display = truncate_addr(d.address, col_addr)

        # Power state indicator: [lock][screen][doze][charging]
        # Lock and screen are fixed positions, doze and charging are optional suffixes
        pwr_icon = ""
        pwr_icon += "U" if d.unlocked else "L"
        pwr_icon += "*" if d.screen_on else "."
        pwr_icon += "z" if d.doze_mode else ""
        pwr_icon += "+" if d.charging else ""

        # Battery level
        if d.battery_level == -1:
            bat_display = "AC"
            bat_color = ""
        else:
            bat_display = f"{d.battery_level}%"
            if d.battery_level >= 50:
                bat_color = GREEN
            elif d.battery_level >= 20:
                bat_color = YELLOW
            else:
                bat_color = RED

        print(
            f"│ {role_color}{d.role:<{col_role}}{RESET}"
            f"│ {state_color}{state_display:<{col_state}}{RESET}"
            f"│ {pwr_icon:<{col_pwr}}"
            f"│ {bat_color}{bat_display:<{col_bat}}{RESET}"
            f"│ {name_color}{d.name:<{col_name}}{RESET}"
            f"│ {addr_display:<{col_addr}}"
            f"│ {d.android_version:<{col_android}}"
            f"│ {version:<{col_version}}│"
        )

    # Bottom border
    print(f"└{'─' * (col_role + 1)}┴{'─' * (col_state + 1)}┴{'─' * (col_pwr + 1)}┴{'─' * (col_bat + 1)}┴{'─' * (col_name + 1)}┴{'─' * (col_addr + 1)}┴{'─' * (col_android + 1)}┴{'─' * (col_version + 1)}┘")

    # Summary
    online = sum(1 for d in devices if d.online)
    running = sum(1 for d in devices if d.app_state == "running")
    print(f"\n{DIM}{online} device(s) connected, {running} running app{RESET}")


def query_all_devices() -> List[DeviceInfo]:
    """Query all connected devices and return deduplicated list"""
    addresses = get_connected_devices()
    if not addresses:
        return []

    # Query all devices in parallel
    devices: List[DeviceInfo] = []
    with ThreadPoolExecutor(max_workers=len(addresses)) as executor:
        futures = {executor.submit(query_device, addr): addr for addr in addresses}
        for future in as_completed(futures):
            try:
                devices.append(future.result())
            except Exception:
                pass  # Skip failed queries in watch mode

    # Deduplicate by serial - prefer network connections over USB
    seen_serials: dict[str, DeviceInfo] = {}
    for d in devices:
        if d.serial in seen_serials:
            existing = seen_serials[d.serial]
            # Prefer network over USB, and known role over unknown
            if d.conn_type == "net" and existing.conn_type == "usb":
                seen_serials[d.serial] = d
            elif d.role != "unknown" and existing.role == "unknown":
                seen_serials[d.serial] = d
        else:
            seen_serials[d.serial] = d
    devices = list(seen_serials.values())

    # Sort by role (beacon first, then reflector) then name
    role_order = {"beacon": 0, "reflector": 1, "unknown": 2}
    devices.sort(key=lambda d: (role_order.get(d.role, 99), d.name))

    return devices


def run_watch_mode(interval: float) -> None:
    """Run in watch mode with live updates"""
    # Handle Ctrl+C gracefully
    def signal_handler(sig, frame):
        print(SHOW_CURSOR, end="")
        print(f"\n{DIM}Watch mode ended.{RESET}")
        sys.exit(0)

    signal.signal(signal.SIGINT, signal_handler)

    print(HIDE_CURSOR, end="")
    try:
        while True:
            # Clear and move to home
            print(CURSOR_HOME + CLEAR_SCREEN, end="")

            # Query and display
            devices = query_all_devices()
            if devices:
                print_table(devices)
            else:
                print(f"{YELLOW}No devices connected.{RESET}")
                print(f"\nTip: Connect devices with 'adb connect <ip>:<port>'")

            # Show legend and refresh info
            print(f"\n{DIM}Pwr: U unlocked  L locked  * on  . off  z doze  + charging{RESET}")
            print(f"{DIM}Refreshing every {interval}s · Ctrl+C to exit{RESET}", end="", flush=True)

            time.sleep(interval)
    finally:
        print(SHOW_CURSOR, end="")


def main():
    parser = argparse.ArgumentParser(
        description="ADB Device Inventory for WitnessMark",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Pwr column legend:
  U  unlocked        L  locked
  *  screen on       .  screen off
  z  doze mode       +  charging

Examples:
  %(prog)s                   List connected devices
  %(prog)s --watch           Live updating display (Ctrl+C to exit)
  %(prog)s --connect         Connect to known devices first
  %(prog)s --json            Output as JSON
  %(prog)s --install-all     Install APK to all devices
        """
    )
    parser.add_argument(
        "--connect", "-c",
        action="store_true",
        help="Attempt to connect to known devices before listing"
    )
    parser.add_argument(
        "--watch", "-w",
        action="store_true",
        help="Live updating display (refreshes every 2s, Ctrl+C to exit)"
    )
    parser.add_argument(
        "--interval",
        type=float,
        default=2.0,
        metavar="SEC",
        help="Refresh interval for watch mode (default: 2.0)"
    )
    parser.add_argument(
        "--json", "-j",
        action="store_true",
        help="Output as JSON instead of table"
    )
    parser.add_argument(
        "--install-all", "-i",
        action="store_true",
        help="Install latest APK to all connected devices"
    )

    args = parser.parse_args()

    # Connect to known devices if requested
    if args.connect:
        connect_known_devices()

    # Watch mode - continuous updates
    if args.watch:
        run_watch_mode(args.interval)
        return

    # Single query mode
    devices = query_all_devices()
    if not devices:
        if args.json:
            print(json.dumps({"devices": [], "error": "No devices connected"}))
        else:
            print(f"{YELLOW}No devices connected.{RESET}")
            print(f"\nTip: Run with --connect to connect to known devices")
            print(f"     Or manually: adb connect <ip>:<port>")
        sys.exit(1)

    # Output
    if args.json:
        print(json.dumps({"devices": [d.to_dict() for d in devices]}, indent=2))
    else:
        print_table(devices)

    # Install if requested
    if args.install_all:
        print()
        install_to_all(devices)


if __name__ == "__main__":
    main()
