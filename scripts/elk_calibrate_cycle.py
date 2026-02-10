#!/usr/bin/env python3
"""Single calibration cycle with real-time timeline updates.

Runs one color at a time, saving to calibration.json after EACH step so the
Calibration Timeline viewer (http://192.168.86.21:8081/calibration) shows
progress in real-time.

Usage:
    python elk_calibrate_cycle.py                    # Single red cycle
    python elk_calibrate_cycle.py --color 0,255,0    # Single green cycle
    python elk_calibrate_cycle.py --sweep            # Full R/G/B sweep
    python elk_calibrate_cycle.py --loop 10          # 10 cycles of random colors
"""

import argparse
import json
import math
import os
import random
import sys
import time
from datetime import datetime
from pathlib import Path

sys.path.insert(0, os.path.dirname(__file__))

from elk_calibrate import (
    find_device_address, preflight_checks, launch_camera, take_photo,
    set_reference_color, set_strip_color, set_strip_brightness,
    extract_region_color, load_regions, compute_correction,
    CAMERA_IP, REFERENCE_IP,
)

OUTPUT_DIR = "captures/calibration_20260207_211636"


def load_cal():
    """Load existing calibration.json or create empty structure."""
    cal_path = Path(OUTPUT_DIR) / "calibration.json"
    if cal_path.exists():
        return json.loads(cal_path.read_text())
    return {"measurements": [], "total_colors": 0}


def save_cal(cal):
    """Save calibration.json — timeline viewer picks this up within 3s."""
    cal_path = Path(OUTPUT_DIR) / "calibration.json"
    cal_path.write_text(json.dumps(cal, indent=2))


def color_name(r, g, b):
    """Human-readable color name for common colors."""
    names = {
        (255, 0, 0): "Red", (0, 255, 0): "Green", (0, 0, 255): "Blue",
        (255, 255, 0): "Yellow", (0, 255, 255): "Cyan", (255, 0, 255): "Magenta",
        (255, 255, 255): "White", (0, 0, 0): "Black",
        (255, 128, 0): "Orange", (128, 0, 255): "Purple",
        (191, 191, 191): "Gray75", (128, 128, 128): "Gray50",
        (64, 64, 64): "Gray25",
        (128, 255, 0): "Chartreuse", (0, 128, 255): "Azure",
        (255, 0, 128): "Rose", (0, 255, 128): "Spring",
        (128, 0, 0): "DimRed", (0, 128, 0): "DimGreen", (0, 0, 128): "DimBlue",
        (128, 128, 0): "DimYellow", (0, 128, 128): "DimCyan", (128, 0, 128): "DimMagenta",
    }
    return names.get((r, g, b), f"RGB({r},{g},{b})")


def color_opinion(ref_rgb, strip_rgb, input_rgb):
    """Generate a qualitative assessment of color accuracy."""
    err = math.sqrt(sum((a - b) ** 2 for a, b in zip(ref_rgb, strip_rgb)))

    # Channel-by-channel analysis
    channels = []
    for ch_name, ri, si in [("R", ref_rgb[0], strip_rgb[0]),
                             ("G", ref_rgb[1], strip_rgb[1]),
                             ("B", ref_rgb[2], strip_rgb[2])]:
        if ri > 10:
            ratio = si / ri
            if ratio > 1.3:
                channels.append(f"{ch_name} too hot ({ratio:.1f}x)")
            elif ratio < 0.7:
                channels.append(f"{ch_name} too dim ({ratio:.1f}x)")
            else:
                channels.append(f"{ch_name} ok ({ratio:.1f}x)")
        elif si > 20:
            channels.append(f"{ch_name} leaking (ref~0, strip={si:.0f})")

    if err < 15:
        grade = "Excellent"
    elif err < 30:
        grade = "Good"
    elif err < 60:
        grade = "Fair"
    elif err < 100:
        grade = "Poor"
    else:
        grade = "Bad"

    return {
        "grade": grade,
        "euclidean_error": round(err, 1),
        "channel_notes": channels,
    }


def run_cycle(camera_addr, ref_addr, regions, r, g, b, settle_time=3.0):
    """Run one calibration cycle: simultaneous strip+ref capture.

    Both strip AND reference are set to the same color simultaneously,
    then ONE photo captures both in the same exposure. This eliminates
    auto-exposure bias between measurements.

    Updates calibration.json after EACH step for real-time timeline visibility.
    Returns the measurement dict.
    """
    name = color_name(r, g, b)
    t_start = time.time()

    print(f"\n{'='*60}")
    print(f"  CYCLE: {name} ({r},{g},{b})")
    print(f"{'='*60}")

    # --- Step 1: Both ON, same color, single photo ---
    print(f"\n  [1/3] Both ON ({name})...", end=" ", flush=True)
    t0 = time.time()
    strip_ok = set_strip_color(r, g, b)
    set_reference_color(ref_addr, r, g, b)
    t_cmd = time.time() - t0
    if not strip_ok:
        print(f"cmd:{t_cmd:.1f}s TIMEOUT!", end=" ", flush=True)
    else:
        print(f"cmd:{t_cmd:.1f}s", end=" ", flush=True)

    time.sleep(settle_time)
    print(f"settle:{settle_time:.1f}s", end=" ", flush=True)

    t0 = time.time()
    photo = take_photo(camera_addr, OUTPUT_DIR)
    t_photo = time.time() - t0
    print(f"photo:{t_photo:.1f}s -> {os.path.basename(photo)}")

    # --- Step 2: Extract BOTH regions from the SAME photo ---
    print(f"  [2/3] Analyzing...", end=" ", flush=True)
    strip_rgb = extract_region_color(photo, regions["strip"])
    ref_rgb = extract_region_color(photo, regions["reference"])
    print(f"strip=({strip_rgb[0]:.0f},{strip_rgb[1]:.0f},{strip_rgb[2]:.0f}) "
          f"ref=({ref_rgb[0]:.0f},{ref_rgb[1]:.0f},{ref_rgb[2]:.0f})")

    # --- Step 3: Comparison & Opinion ---
    opinion = color_opinion(ref_rgb, strip_rgb, [r, g, b])

    # Save complete measurement to calibration.json
    cal = load_cal()
    measurement = {
        "input_rgb": [r, g, b],
        "strip_seen": [round(strip_rgb[0], 2), round(strip_rgb[1], 2), round(strip_rgb[2], 2)],
        "ref_seen": [round(ref_rgb[0], 2), round(ref_rgb[1], 2), round(ref_rgb[2], 2)],
        "photo_strip": os.path.basename(photo),
        "photo_ref": os.path.basename(photo),  # same photo
        "opinion": opinion,
        "status": "complete",
        "reliable": strip_ok,
    }
    cal["measurements"].append(measurement)
    cal["total_colors"] = max(cal.get("total_colors", 0), len(cal["measurements"]))
    save_cal(cal)

    elapsed = time.time() - t_start

    print(f"  [3/3] {opinion['grade']} (err={opinion['euclidean_error']})")
    for note in opinion["channel_notes"]:
        print(f"        - {note}")
    print(f"        -> Timeline updated")
    print(f"  Cycle time: {elapsed:.1f}s")

    return measurement


def main():
    parser = argparse.ArgumentParser(description="Single calibration cycle with real-time updates")
    parser.add_argument("--color", type=str, default="255,0,0",
                        help="R,G,B color to test (default: 255,0,0)")
    parser.add_argument("--sweep", action="store_true",
                        help="Run R/G/B sweep (3 cycles)")
    parser.add_argument("--practical", action="store_true",
                        help="Run practical colors: R,G,B,White,Yellow,Cyan,Magenta,Orange + dims (14 cycles)")
    parser.add_argument("--full", action="store_true",
                        help="Full 25-color gamut sweep")
    parser.add_argument("--loop", type=int, default=0,
                        help="Run N cycles with random colors")
    parser.add_argument("--lut", action="store_true",
                        help="Build correction LUT from accumulated measurements after sweep")
    parser.add_argument("--settle-time", type=float, default=1.5,
                        help="Seconds to wait after color change (default: 1.5)")
    parser.add_argument("--fresh", action="store_true",
                        help="Clear previous measurements before starting")
    args = parser.parse_args()

    # Find devices
    print("Locating devices...")
    camera_addr = find_device_address(CAMERA_IP)
    ref_addr = find_device_address(REFERENCE_IP)
    if not camera_addr or not ref_addr:
        sys.exit("Devices not found")

    preflight_checks(camera_addr, ref_addr)
    regions = load_regions(OUTPUT_DIR)
    print(f"Regions: {json.dumps(regions)}")

    # Optionally clear previous measurements
    if args.fresh:
        save_cal({"measurements": [], "total_colors": 0})
        print("Cleared previous measurements")

    set_strip_brightness(255)
    launch_camera(camera_addr)

    # Build color list
    if args.practical:
        colors = [
            # Primaries at full
            (255, 0, 0), (0, 255, 0), (0, 0, 255),
            # Key mixed colors — these are the problem hues
            (255, 255, 255),   # White (goes pink?)
            (255, 255, 0),     # Yellow (R+G)
            (0, 255, 255),     # Cyan (G+B)
            (255, 0, 255),     # Magenta (R+B)
            (255, 128, 0),     # Orange
            # Dim primaries (half brightness)
            (128, 0, 0), (0, 128, 0), (0, 0, 128),
            # Dim mixed
            (128, 128, 128),   # Gray50
            (128, 128, 0),     # DimYellow
            (128, 0, 128),     # DimMagenta
        ]
    elif args.full:
        import colorsys
        colors = [(0, 0, 0), (255, 255, 255)]
        for v in (191, 128, 64):
            colors.append((v, v, v))
        for deg in range(0, 360, 30):
            r, g, b = colorsys.hsv_to_rgb(deg / 360.0, 1.0, 1.0)
            colors.append((int(r * 255), int(g * 255), int(b * 255)))
        for deg in (0, 60, 120, 180, 240, 300):
            r, g, b = colorsys.hsv_to_rgb(deg / 360.0, 0.5, 1.0)
            colors.append((int(r * 255), int(g * 255), int(b * 255)))
    elif args.sweep:
        colors = [(255, 0, 0), (0, 255, 0), (0, 0, 255)]
    elif args.loop > 0:
        colors = []
        for _ in range(args.loop):
            colors.append((random.randint(0, 255), random.randint(0, 255), random.randint(0, 255)))
    else:
        parts = args.color.split(",")
        colors = [(int(parts[0]), int(parts[1]), int(parts[2]))]

    print(f"\nRunning {len(colors)} cycle(s)...\n")

    results = []
    for i, (r, g, b) in enumerate(colors):
        print(f"\n--- Cycle {i+1}/{len(colors)} ---")
        m = run_cycle(camera_addr, ref_addr, regions, r, g, b, args.settle_time)
        results.append(m)

    # Final cleanup — turn everything off
    set_strip_color(0, 0, 0)
    set_reference_color(ref_addr, 0, 0, 0)

    # Summary
    print(f"\n{'='*60}")
    print(f"  SUMMARY: {len(results)} cycle(s)")
    print(f"{'='*60}")
    for m in results:
        inp = m["input_rgb"]
        grade = m.get("opinion", {}).get("grade", "?")
        err = m.get("opinion", {}).get("euclidean_error", "?")
        print(f"  {color_name(*inp):12s} ({inp[0]:3d},{inp[1]:3d},{inp[2]:3d})  "
              f"error={str(err):>6s}  grade={grade}")

    # Build correction LUT if requested
    if args.lut:
        print(f"\n{'='*60}")
        print(f"  BUILDING CORRECTION LUT")
        print(f"{'='*60}")
        cal = load_cal()
        complete = [m for m in cal["measurements"] if m.get("status") == "complete"
                    and "ref_seen" in m and m.get("reliable", True)]
        unreliable = [m for m in cal["measurements"] if not m.get("reliable", True)]
        if unreliable:
            print(f"  Skipping {len(unreliable)} unreliable measurements (BLE timeout)")
        if len(complete) < 3:
            print(f"  Need at least 3 complete measurements, have {len(complete)}")
        else:
            lut = compute_correction(complete)
            cal["correction_lut"] = lut
            save_cal(cal)
            print(f"  LUT built from {len(complete)} measurements")
            for ch in ("r", "g", "b"):
                table = lut[ch]
                # Show key correction points
                samples = [0, 64, 128, 192, 255]
                mapped = [table[s] for s in samples]
                print(f"    {ch.upper()}: {list(zip(samples, mapped))}")
            print(f"  Saved to calibration.json")
            print(f"  Apply: python elk_dashboard.py --calibration {OUTPUT_DIR}/calibration.json")

    print(f"\nTimeline: http://192.168.86.21:8081/calibration")


if __name__ == "__main__":
    main()
