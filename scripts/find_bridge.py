#!/usr/bin/env python3
"""Listen for ELK-Bridge UDP beacon on port 5555.

The bridge broadcasts a JSON packet every 5 seconds on the LAN.
Run this script to discover the bridge's IP address.

Usage:
    python scripts/find_bridge.py           # listen until found (10s timeout)
    python scripts/find_bridge.py --wait 30 # listen for up to 30 seconds
"""

import socket
import json
import sys
import time

BEACON_PORT = 5555
DEFAULT_TIMEOUT = 10


def find_bridge(timeout: float = DEFAULT_TIMEOUT) -> dict | None:
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(("", BEACON_PORT))
    sock.settimeout(1.0)

    deadline = time.time() + timeout
    print(f"Listening for ELK-Bridge beacon on UDP port {BEACON_PORT}...")

    try:
        while time.time() < deadline:
            try:
                data, addr = sock.recvfrom(512)
                try:
                    info = json.loads(data.decode())
                    if info.get("name") == "ELK-Bridge":
                        print(f"\nFound bridge at {addr[0]}:")
                        print(f"  IP:      {info.get('ip')}")
                        print(f"  Port:    {info.get('port')}")
                        print(f"  MAC:     {info.get('mac')}")
                        print(f"  Version: {info.get('version')}")
                        print(f"  Uptime:  {info.get('uptime')}s")
                        print(f"\n  URL: http://{info.get('ip')}:{info.get('port')}/")
                        return info
                except json.JSONDecodeError:
                    pass
            except socket.timeout:
                print(".", end="", flush=True)
    finally:
        sock.close()

    print("\nNo bridge found within timeout.")
    return None


if __name__ == "__main__":
    timeout = DEFAULT_TIMEOUT
    if "--wait" in sys.argv:
        idx = sys.argv.index("--wait")
        if idx + 1 < len(sys.argv):
            timeout = float(sys.argv[idx + 1])

    result = find_bridge(timeout)
    sys.exit(0 if result else 1)
