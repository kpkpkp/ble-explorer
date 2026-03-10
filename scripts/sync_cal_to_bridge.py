#!/usr/bin/env python3
"""Sync calibration data from Android SharedPrefs XML to bridge API."""

import json
import sys
import xml.etree.ElementTree as ET
import requests

BRIDGE_URL = "http://192.168.86.22"

def main():
    if len(sys.argv) < 2:
        print("Usage: sync_cal_to_bridge.py <elk_calibration.xml>")
        sys.exit(1)

    tree = ET.parse(sys.argv[1])
    root = tree.getroot()

    cal_str = None
    for s in root.findall("string"):
        if s.get("name") == "calibration_data":
            cal_str = s.text
            break

    if not cal_str:
        print("No calibration_data found in XML")
        sys.exit(1)

    cal = json.loads(cal_str)
    points = []
    for key in sorted(cal.keys(), key=lambda k: int(k.split("_")[1])):
        pt = cal[key]
        points.append({
            "deg": int(pt["deg"]),
            "brightness": pt.get("brightness", 100),
            "standard": list(pt["standard"]),
            "strip_input": list(pt["strip_input"]),
        })

    print(f"Parsed {len(points)} calibration points")

    payload = {"points": points, "enabled": True}
    r = requests.post(f"{BRIDGE_URL}/api/calibration", json=payload, timeout=10)
    print(f"Bridge response: {r.status_code} {r.text}")

if __name__ == "__main__":
    main()
