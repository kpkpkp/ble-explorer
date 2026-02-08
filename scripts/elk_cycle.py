#!/usr/bin/env python3
"""Run a red-white-blue color cycle on two ELK-BLEDOM strips.

Strip 1: R W B R W B ...
Strip 2: R B W R B W ...
10 cycles each.
"""
import asyncio
from bleak import BleakClient

WRITE_CHAR = "0000fff3-0000-1000-8000-00805f9b34fb"

STRIP1 = "BE:67:00:35:04:9B"  # ELK-BLEDOM0B
STRIP2 = "BE:67:00:35:11:78"  # ELK-BLEDOM08

RED   = bytes([0x7E, 0x00, 0x05, 0x03, 0xFF, 0x00, 0x00, 0x00, 0xEF])
WHITE = bytes([0x7E, 0x00, 0x05, 0x03, 0xFF, 0xFF, 0xFF, 0x00, 0xEF])
BLUE  = bytes([0x7E, 0x00, 0x05, 0x03, 0x00, 0x00, 0xFF, 0x00, 0xEF])

# Strip 1 pattern: R W B
PATTERN1 = [RED, WHITE, BLUE]
# Strip 2 pattern: R B W
PATTERN2 = [RED, BLUE, WHITE]

STEP_DELAY = 0.5  # seconds between color changes
DURATION = 120    # seconds total


async def main():
    import time

    print("Connecting to ELK-BLEDOM strips...")
    clients = {}
    for mac in [STRIP1, STRIP2]:
        try:
            print(f"  Connecting to {mac}...")
            client = BleakClient(mac, timeout=20)
            await client.connect()
            clients[mac] = client
            print(f"  {mac} connected!")
        except Exception as e:
            print(f"  {mac} not available: {e}")

    if not clients:
        print("Could not connect to any strips.")
        return

    macs = list(clients.keys())
    print(f"\n{len(macs)} strip(s) ready. Running for {DURATION}s...\n")

    COLOR_NAMES = {id(RED): "RED", id(WHITE): "WHITE", id(BLUE): "BLUE"}
    # Assign patterns: first strip gets RWB, second gets RBW
    patterns = [PATTERN1, PATTERN2]

    start = time.time()
    cycle = 0
    try:
        while time.time() - start < DURATION:
            cycle += 1
            for step in range(3):
                for i, mac in enumerate(macs):
                    color = patterns[i % 2][step]
                    await clients[mac].write_gatt_char(WRITE_CHAR, color, response=False)
                elapsed = time.time() - start
                remaining = DURATION - elapsed
                labels = []
                for i, mac in enumerate(macs):
                    c = patterns[i % 2][step]
                    cn = COLOR_NAMES[id(c)]
                    labels.append(f"s{i+1}={cn:<5s}")
                print(f"  cycle {cycle:3d}  {' '.join(labels)}  "
                      f"[{elapsed:.0f}s / {DURATION}s]")
                if remaining <= 0:
                    break
                await asyncio.sleep(STEP_DELAY)

        print(f"\nDone! {cycle} cycles in {time.time()-start:.0f}s")
    finally:
        for client in clients.values():
            await client.disconnect()


if __name__ == "__main__":
    asyncio.run(main())
