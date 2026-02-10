#!/usr/bin/env python3
"""Quick BLE adapter check — scan all devices."""
import asyncio
from bleak import BleakScanner

async def main():
    print("Checking BLE adapter and scanning all devices (10s)...")
    try:
        devices = await BleakScanner.discover(timeout=10)
        print(f"\nFound {len(devices)} BLE device(s):")
        for d in devices:
            name = d.name or "(no name)"
            print(f"  {d.address}  {name}")
    except Exception as e:
        print(f"BLE error: {e}")
        print("Is Bluetooth enabled on this PC?")

asyncio.run(main())
