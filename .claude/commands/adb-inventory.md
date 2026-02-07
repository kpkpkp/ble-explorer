---
description: List connected ADB devices with app versions and roles
argument-hint: "[--connect] [--json] [--install-all]"
allowed-tools: [Bash]
---

# /adb-inventory - ADB Device Inventory

Lists all connected ADB devices with their status, Android version, BLE Explorer app version, and role.

## Arguments

- `--watch` or `-w`: Live updating display (Ctrl+C to exit)
- `--interval SEC`: Refresh interval for watch mode (default: 2.0)
- `--connect` or `-c`: Attempt to connect to known devices before listing
- `--json` or `-j`: Output as JSON instead of table
- `--install-all` or `-i`: Install latest APK to all connected devices

## Execute

Run the inventory script with any provided arguments:

```bash
python3 scripts/adb-inventory.py $ARGUMENTS
```

## Known Devices

| Device | IP/Serial | Role |
|--------|-----------|------|
| Pixel 10 Pro | 192.168.86.178 | dev-phone |
| Pixel 2 XL | 192.168.86.24 | test-target |
| Pixel 2 (cracked) | 192.168.86.20 | test-target |
| Pixel 2 #2 | 192.168.86.250 | test-target |

## Output Columns

| Column | Description |
|--------|-------------|
| Role | `dev-phone`, `test-target`, or `unknown` |
| State | `running`, `stopped`, or `absent` |
| Pwr | Power state icons (see below) |
| Bat | Battery percentage (0-100%) or `AC` for mains-powered devices |
| Device | Friendly name (identified by IP/serial) |
| Address | ADB address (IP:port or serial) |
| Android | Android version |
| App Ver | BLE Explorer app version |

**Note:** Devices connected via both WiFi and USB are automatically deduplicated, preferring WiFi connections (which have friendly names).

## Power State Icons

The Pwr column shows the device's power and lock state in a compact format.

**Format**: `[lock][screen][doze][charging]`

Fixed positions (always shown):
- **Lock**: `U` (unlocked) or `L` (locked)
- **Screen**: `*` (on) or `.` (off)

Optional suffixes:
- **Doze**: `z` (doze mode)
- **Charging**: `+` (charging)

**Examples**:
- `U*+` - Unlocked, screen on, charging
- `L.` - Locked, screen off
- `U*z` - Unlocked, screen on, doze mode
- `L.z+` - Locked, screen off, doze mode, charging

## Color Coding

- **Green**: App running, dev-phone role
- **Yellow**: App stopped, test-target role
- **Red**: App not installed, or battery < 20%
- **Gray**: Unknown role
- **Battery**: Green >= 50%, Yellow 20-49%, Red < 20%

## Examples

```bash
# Basic inventory
/adb-inventory

# Live updating display (updates every 2s)
/adb-inventory --watch

# Faster refresh (every 0.5s)
/adb-inventory --watch --interval 0.5

# Connect to known devices first, then list
/adb-inventory --connect

# Get JSON for scripting
/adb-inventory --json

# Install APK to all devices
/adb-inventory --install-all
```
