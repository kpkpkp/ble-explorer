#!/usr/bin/env python3
"""Quick 3-color test of the calibration pipeline."""

import sys
import os
sys.path.insert(0, os.path.dirname(__file__))

from elk_calibrate import (
    find_device_address, preflight_checks, launch_camera, take_photo,
    set_reference_color, set_strip_color, set_strip_brightness,
    extract_region_color, load_regions, CAMERA_IP, REFERENCE_IP,
)
import time
import json

OUTPUT_DIR = "captures/calibration_20260207_211636"

camera_addr = find_device_address(CAMERA_IP)
ref_addr = find_device_address(REFERENCE_IP)
if not camera_addr or not ref_addr:
    sys.exit("Devices not found")

preflight_checks(camera_addr, ref_addr)
regions = load_regions(OUTPUT_DIR)
print(f"Regions: {json.dumps(regions, indent=2)}")

set_strip_brightness(255)
launch_camera(camera_addr)

test_colors = [
    ("Red",   255, 0, 0),
    ("Green", 0, 255, 0),
    ("Blue",  0, 0, 255),
]

measurements = []
for name, r, g, b in test_colors:
    print(f"\n--- {name} ({r},{g},{b}) ---")

    # Phase 1: strip shows color, P2XL dark
    print("  Phase 1: strip ON, ref OFF")
    set_reference_color(ref_addr, 0, 0, 0)
    set_strip_color(r, g, b)
    time.sleep(3)
    strip_photo = take_photo(camera_addr, OUTPUT_DIR)
    strip_rgb = extract_region_color(strip_photo, regions["strip"])
    print(f"  Strip seen: ({strip_rgb[0]:.0f}, {strip_rgb[1]:.0f}, {strip_rgb[2]:.0f})")

    # Phase 2: strip off, P2XL shows color
    print("  Phase 2: strip OFF, ref ON")
    set_strip_color(0, 0, 0)
    set_reference_color(ref_addr, r, g, b)
    time.sleep(3)
    ref_photo = take_photo(camera_addr, OUTPUT_DIR)
    ref_rgb = extract_region_color(ref_photo, regions["reference"])
    print(f"  Ref seen:   ({ref_rgb[0]:.0f}, {ref_rgb[1]:.0f}, {ref_rgb[2]:.0f})")

    measurements.append({
        "input_rgb": [r, g, b],
        "strip_seen": [round(strip_rgb[0], 2), round(strip_rgb[1], 2), round(strip_rgb[2], 2)],
        "ref_seen": [round(ref_rgb[0], 2), round(ref_rgb[1], 2), round(ref_rgb[2], 2)],
        "photo_strip": os.path.basename(strip_photo),
        "photo_ref": os.path.basename(ref_photo),
    })

# Save so the timeline viewer picks it up
cal_path = os.path.join(OUTPUT_DIR, "calibration.json")
with open(cal_path, "w") as f:
    json.dump({"measurements": measurements, "total_colors": 3}, f, indent=2)

print("\n=== Summary ===")
for m in measurements:
    inp = m["input_rgb"]
    ref = m["ref_seen"]
    strip = m["strip_seen"]
    print(f"  RGB({inp[0]:3d},{inp[1]:3d},{inp[2]:3d})  "
          f"ref=({ref[0]:6.1f},{ref[1]:6.1f},{ref[2]:6.1f})  "
          f"strip=({strip[0]:6.1f},{strip[1]:6.1f},{strip[2]:6.1f})")

print(f"\nSaved to {cal_path}")
print("Check timeline at http://192.168.86.21:8081/calibration")
