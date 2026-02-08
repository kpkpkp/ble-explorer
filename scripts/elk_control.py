#!/usr/bin/env python3
"""Direct BLE control for ELK-BLEDOM LED strip controllers.

Sends commands directly to ELK-BLEDOM LED strips via BLE GATT writes.
Protocol reverse-engineered from Lotus Lantern (wl.smartled) HCI captures.

Requirements:
    pip install bleak

Platform notes:
    - Windows: Works natively (WinRT BLE backend)
    - Linux: Requires BlueZ and a Bluetooth adapter
    - WSL2: No direct BLE — run from Windows Python or use --adb mode
    - macOS: Works natively (CoreBluetooth backend)

Usage:
    python3 scripts/elk_control.py scan                    # Find ELK-BLEDOM devices
    python3 scripts/elk_control.py color FF0000            # Set red
    python3 scripts/elk_control.py color 00FF00 --mac BE:67:00:35:04:9B
    python3 scripts/elk_control.py brightness 128          # 50% brightness
    python3 scripts/elk_control.py effect rainbow_fade 3   # Effect with speed
    python3 scripts/elk_control.py on                      # Power on
    python3 scripts/elk_control.py off                     # Power off
    python3 scripts/elk_control.py --adb <serial> color FF0000  # Via Android phone
"""

import asyncio
import argparse
import struct
import sys

# ---------------------------------------------------------------------------
# ELK-BLEDOM Protocol
# ---------------------------------------------------------------------------
SERVICE_UUID = "0000fff0-0000-1000-8000-00805f9b34fb"
WRITE_CHAR_UUID = "0000fff3-0000-1000-8000-00805f9b34fb"
NOTIFY_CHAR_UUID = "0000fff4-0000-1000-8000-00805f9b34fb"

EFFECTS = {
    "static": 0x80,
    "jump_rgb": 0x81,
    "jump_rgbycmw": 0x82,
    "crossfade_rgb": 0x83,
    "crossfade_rgbycmw": 0x84,
    "breathe_rgb": 0x85,
    "breathe_rgbycmw": 0x86,
    "rainbow_fade": 0x87,
    "rainbow_pulse": 0x88,
    "rainbow_strobe": 0x89,
    "color_cycle": 0x8A,
    "rgb_chase": 0x8B,
    "rainbow_chase": 0x8C,
    "red_pulse": 0x8D,
    "green_pulse": 0x8E,
    "blue_pulse": 0x8F,
    "yellow_pulse": 0x90,
    "cyan_pulse": 0x91,
    "magenta_pulse": 0x92,
    "white_pulse": 0x93,
    "rg_chase": 0x94,
    "rb_chase": 0x95,
    "gb_chase": 0x96,
    "rg_breathe": 0x97,
    "rb_breathe": 0x98,
    "gb_breathe": 0x99,
    "rgb_breathe_chase": 0x9A,
}


def cmd_color(r, g, b):
    """Set RGB color."""
    return bytes([0x7E, 0x00, 0x05, 0x03, r, g, b, 0x00, 0xEF])


def cmd_brightness(level):
    """Set brightness (0-255)."""
    return bytes([0x7E, 0x00, 0x01, level, 0x00, 0x00, 0x00, 0x00, 0xEF])


def cmd_color_temp(warm, cold):
    """Set color temperature (warm + cold = 100)."""
    return bytes([0x7E, 0x00, 0x05, 0x02, warm, cold, 0x00, 0x00, 0xEF])


def cmd_power(on):
    """Power on or off."""
    state = 0x01 if on else 0x00
    return bytes([0x7E, 0x00, 0x04, state, 0x00, 0x00, 0x00, 0x00, 0xEF])


def cmd_effect(effect_code, speed=3):
    """Set effect mode with speed (1=slow, 255=fast)."""
    return bytes([0x7E, 0x00, 0x03, effect_code, speed, 0x00, 0x00, 0x00, 0xEF])


# ---------------------------------------------------------------------------
# BLE backend (bleak)
# ---------------------------------------------------------------------------
async def scan_devices(timeout=5):
    """Scan for ELK-BLEDOM devices."""
    from bleak import BleakScanner

    print(f"Scanning for BLE devices ({timeout}s)...")
    devices = await BleakScanner.discover(timeout=timeout)

    elk_devices = []
    for d in devices:
        name = d.name or ""
        if "ELK-BLEDOM" in name.upper() or "MELK" in name.upper():
            elk_devices.append(d)
            print(f"  Found: {d.address}  {name}  RSSI={d.rssi}")

    if not elk_devices:
        print("  No ELK-BLEDOM devices found.")
        print("  Make sure the LED strip is powered on and in range.")
    else:
        print(f"\n  {len(elk_devices)} ELK-BLEDOM device(s) found.")
    return elk_devices


async def send_command(mac, command_bytes, retries=2):
    """Send a command to an ELK-BLEDOM device via BLE."""
    from bleak import BleakClient

    print(f"Connecting to {mac}...")
    async with BleakClient(mac, timeout=10) as client:
        if not client.is_connected:
            print(f"  Failed to connect to {mac}")
            return False

        print(f"  Connected. Sending: {command_bytes.hex()}")
        for attempt in range(retries + 1):
            try:
                await client.write_gatt_char(WRITE_CHAR_UUID, command_bytes, response=False)
                print(f"  Sent successfully.")
                return True
            except Exception as e:
                if attempt < retries:
                    print(f"  Write failed, retrying... ({e})")
                    await asyncio.sleep(0.1)
                else:
                    print(f"  Write failed: {e}")
                    return False


async def send_to_all(command_bytes, target_mac=None, scan_timeout=5):
    """Send a command to one or all ELK-BLEDOM devices."""
    if target_mac:
        return await send_command(target_mac, command_bytes)

    devices = await scan_devices(timeout=scan_timeout)
    if not devices:
        return False

    results = []
    for d in devices:
        ok = await send_command(d.address, command_bytes)
        results.append(ok)
    return all(results)


# ---------------------------------------------------------------------------
# ADB backend (send via Android phone)
# ---------------------------------------------------------------------------
def adb_send_command(serial, mac, command_bytes):
    """Send a BLE GATT write via an Android phone using a helper script.

    Pushes a minimal Python script to Termux on the phone, or uses
    the am broadcast approach with a BLE write helper app.

    For now, generates the nRF Connect macro format for manual replay.
    """
    import subprocess

    hex_data = command_bytes.hex()
    print(f"ADB mode: generating command for {mac}")
    print(f"  Data: {hex_data}")
    print()
    print("  To send via nRF Connect on the phone:")
    print(f"  1. Connect to {mac}")
    print(f"  2. Find service 0xFFF0 > characteristic 0xFFF3")
    print(f"  3. Write value: {hex_data}")
    print()
    print("  Or use the Tasker/Automate BLE plugin with:")
    print(f"    Service UUID:  {SERVICE_UUID}")
    print(f"    Char UUID:     {WRITE_CHAR_UUID}")
    print(f"    Write type:    NO_RESPONSE")
    print(f"    Value (hex):   {hex_data}")
    return True


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------
def parse_color(color_str):
    """Parse a hex color string (RRGGBB or #RRGGBB) to (r, g, b)."""
    color_str = color_str.lstrip("#")
    if len(color_str) != 6:
        raise ValueError(f"Invalid color '{color_str}'. Use RRGGBB format.")
    r = int(color_str[0:2], 16)
    g = int(color_str[2:4], 16)
    b = int(color_str[4:6], 16)
    return r, g, b


def main():
    parser = argparse.ArgumentParser(
        description="Control ELK-BLEDOM LED strips via BLE",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--mac", "-m", help="Target device MAC address")
    parser.add_argument("--adb", metavar="SERIAL",
                        help="Send via Android phone (ADB serial or IP)")

    sub = parser.add_subparsers(dest="command", help="Command to send")

    # scan
    sub.add_parser("scan", help="Scan for ELK-BLEDOM devices")

    # color
    p_color = sub.add_parser("color", help="Set RGB color (hex: FF0000)")
    p_color.add_argument("hex_color", help="Color in RRGGBB or #RRGGBB format")

    # brightness
    p_bright = sub.add_parser("brightness", help="Set brightness (0-255)")
    p_bright.add_argument("level", type=int, help="Brightness 0-255")

    # effect
    p_effect = sub.add_parser("effect", help="Set effect mode")
    p_effect.add_argument("name", choices=list(EFFECTS.keys()),
                          help="Effect name")
    p_effect.add_argument("speed", type=int, nargs="?", default=3,
                          help="Speed 1-255 (default: 3)")

    # color_temp
    p_temp = sub.add_parser("temp", help="Set color temperature")
    p_temp.add_argument("warm", type=int, help="Warm value 0-100")
    p_temp.add_argument("cold", type=int, nargs="?",
                        help="Cold value 0-100 (default: 100-warm)")

    # power
    sub.add_parser("on", help="Power on")
    sub.add_parser("off", help="Power off")

    # raw
    p_raw = sub.add_parser("raw", help="Send raw hex bytes")
    p_raw.add_argument("hex_data", help="Raw hex string (e.g. 7e0005030000ff00ef)")

    args = parser.parse_args()

    if not args.command:
        parser.print_help()
        sys.exit(1)

    # Build command bytes
    if args.command == "scan":
        asyncio.run(scan_devices())
        return

    if args.command == "color":
        r, g, b = parse_color(args.hex_color)
        data = cmd_color(r, g, b)
        print(f"Setting color to RGB({r}, {g}, {b}) = #{r:02x}{g:02x}{b:02x}")

    elif args.command == "brightness":
        level = max(0, min(255, args.level))
        data = cmd_brightness(level)
        print(f"Setting brightness to {level} ({level/255*100:.0f}%)")

    elif args.command == "effect":
        code = EFFECTS[args.name]
        speed = max(1, min(255, args.speed))
        data = cmd_effect(code, speed)
        print(f"Setting effect: {args.name} (0x{code:02x}), speed={speed}")

    elif args.command == "temp":
        warm = max(0, min(100, args.warm))
        cold = args.cold if args.cold is not None else (100 - warm)
        data = cmd_color_temp(warm, cold)
        print(f"Setting color temp: warm={warm}, cold={cold}")

    elif args.command == "on":
        data = cmd_power(True)
        print("Powering ON")

    elif args.command == "off":
        data = cmd_power(False)
        print("Powering OFF")

    elif args.command == "raw":
        data = bytes.fromhex(args.hex_data)
        print(f"Sending raw: {data.hex()}")

    else:
        parser.print_help()
        sys.exit(1)

    print(f"  Command bytes: {data.hex()}")

    # Send via appropriate backend
    if args.adb:
        adb_send_command(args.adb, args.mac or "BE:67:00:35:04:9B", data)
    else:
        asyncio.run(send_to_all(data, target_mac=args.mac))


if __name__ == "__main__":
    main()
