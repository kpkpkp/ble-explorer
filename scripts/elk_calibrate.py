#!/usr/bin/env python3
"""ELK-BLEDOM Color Calibration System.

Photographs ELK-BLEDOM LED strips alongside a reference phone display to build
a per-channel correction LUT that compensates for the strips' color inaccuracy.

Camera bias cancels out because both subjects are captured in the same frame.

Devices:
    Pixel 10 Pro (192.168.86.178) — camera
    Pixel 2 XL   (192.168.86.24)  — reference display (shows solid colors)
    LED strips   (via dashboard)  — calibration subject

Usage:
    python elk_calibrate.py --setup          # Take test photo, draw region boxes
    python elk_calibrate.py                  # Run full calibration (~25 colors)
    python elk_calibrate.py --verify         # Calibrate + verify with corrected colors
    python elk_calibrate.py --brightness 128 # Calibrate at half brightness
    python elk_calibrate.py --extra-colors FF8800,AA00FF  # Additional test colors
    python elk_calibrate.py --resume         # Resume a crashed calibration
    python elk_calibrate.py --settle-time 3  # Seconds to wait after color change
"""

import argparse
import colorsys
import functools
import json
import math
import os
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path

import requests
from PIL import Image, ImageDraw, ImageOps

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
CAMERA_IP = "192.168.86.178"
REFERENCE_IP = "192.168.86.24"
DASHBOARD_URL = "http://localhost:8081"
DASHBOARD_LAN_URL = "http://192.168.86.21:8081"  # for phones to reach dashboard

# Fallback USB serial → IP mapping (when WiFi ADB unavailable)
KNOWN_SERIALS = {
    "192.168.86.178": ["caiman", "Pixel 10 Pro"],   # Pixel 10 Pro
    "192.168.86.24":  ["taimen", "Pixel 2 XL"],     # Pixel 2 XL
}

DEFAULT_REGIONS = {
    "reference": [0.0, 0.0, 0.5, 1.0],
    "strip": [0.5, 0.0, 1.0, 1.0],
}

# ---------------------------------------------------------------------------
# Retry decorator
# ---------------------------------------------------------------------------
def retry(max_attempts=3, delay=2):
    def decorator(func):
        @functools.wraps(func)
        def wrapper(*args, **kwargs):
            for attempt in range(max_attempts):
                try:
                    return func(*args, **kwargs)
                except Exception as e:
                    if attempt == max_attempts - 1:
                        raise
                    print(f"  Retry {attempt + 1}/{max_attempts - 1}: {e}")
                    time.sleep(delay)
        return wrapper
    return decorator


# ---------------------------------------------------------------------------
# ADB utilities
# ---------------------------------------------------------------------------
def run_adb(args, timeout=5):
    """Run an ADB command and return (success, output)."""
    try:
        result = subprocess.run(
            ["adb"] + args,
            capture_output=True,
            text=True,
            timeout=timeout,
        )
        return result.returncode == 0, result.stdout.strip()
    except subprocess.TimeoutExpired:
        return False, "timeout"
    except Exception as e:
        return False, str(e)


def find_device_address(ip):
    """Return the ADB address for a device identified by *ip*.

    Search order:
      1. Network-connected device whose address starts with *ip*
      2. Attempt ``adb connect <ip>:5555`` and recheck
      3. USB-connected device whose model matches KNOWN_SERIALS for this IP
    """
    ok, output = run_adb(["devices"])
    if ok:
        for line in output.splitlines():
            if line.startswith(ip) and "\tdevice" in line:
                return line.split("\t")[0]

    # Not connected via WiFi — try the default port
    run_adb(["connect", f"{ip}:5555"], timeout=5)
    time.sleep(1)
    ok, output = run_adb(["devices"])
    if ok:
        for line in output.splitlines():
            if line.startswith(ip) and "\tdevice" in line:
                return line.split("\t")[0]

    # Fallback: look for USB-connected device by model name
    model_hints = KNOWN_SERIALS.get(ip, [])
    if model_hints and ok:
        for line in output.splitlines():
            if "\tdevice" not in line:
                continue
            addr = line.split("\t")[0]
            if ":" in addr:
                continue  # skip network devices
            # Check model
            _, model = run_adb(["-s", addr, "shell", "getprop", "ro.product.model"])
            if any(hint.lower() in model.lower() for hint in model_hints):
                return addr

    return None


# ---------------------------------------------------------------------------
# Camera control
# ---------------------------------------------------------------------------
def launch_camera(device_addr):
    """Bring the camera to the foreground.

    Does NOT force-stop — just brings the existing camera activity to front.
    Only force-restarts on first call or if the camera is truly broken.
    """
    # Ensure screen is on and stays on
    run_adb(["-s", device_addr, "shell", "input", "keyevent", "KEYCODE_WAKEUP"])
    run_adb(["-s", device_addr, "shell", "svc", "power", "stayon", "true"])

    # Bring camera to front (no force-stop, no -W wait)
    run_adb(["-s", device_addr, "shell",
             "am", "start", "-a", "android.media.action.STILL_IMAGE_CAMERA"])
    time.sleep(1)

    # Dismiss error dialog if present
    _dismiss_camera_dialog(device_addr)


def _dismiss_camera_dialog(device_addr):
    """Check for and dismiss 'Something went wrong' dialog."""
    ok, _ = run_adb(["-s", device_addr, "shell",
                     "uiautomator", "dump", "/sdcard/_ui.xml"], timeout=3)
    if not ok:
        return
    ok2, xml = run_adb(["-s", device_addr, "shell", "cat", "/sdcard/_ui.xml"], timeout=3)
    if ok2 and "Continue anyway" in xml:
        run_adb(["-s", device_addr, "shell", "input", "tap", "540", "1300"])
        time.sleep(0.5)
        print("    (Dismissed camera error dialog)")


@retry(max_attempts=2, delay=1)
def take_photo(device_addr, output_dir):
    """Capture a photo and pull it to *output_dir*. Returns the local path.

    Optimised: ``ls -t | head -1`` for fast polling, 0.5 s intervals.
    On retry, dismisses any camera dialog before trying again.
    """
    # Get newest file before shutter (single filename, fast)
    ok, before_newest = run_adb(
        ["-s", device_addr, "shell", "ls", "-t", "/sdcard/DCIM/Camera/",
         "|", "head", "-1"], timeout=5
    )
    before_newest = before_newest.strip() if ok else ""

    # Press shutter (keyevent 27 = KEYCODE_CAMERA)
    run_adb(["-s", device_addr, "shell", "input", "keyevent", "27"])

    # Poll for a different newest file (0.5s intervals, up to 8s)
    new_file = None
    for _ in range(16):
        time.sleep(0.5)
        ok, after_newest = run_adb(
            ["-s", device_addr, "shell", "ls", "-t", "/sdcard/DCIM/Camera/",
             "|", "head", "-1"], timeout=5
        )
        if ok:
            after_newest = after_newest.strip()
            if after_newest and after_newest != before_newest:
                new_file = after_newest
                break

    if not new_file:
        # Before retrying, dismiss any dialog and re-focus camera
        _dismiss_camera_dialog(device_addr)
        run_adb(["-s", device_addr, "shell",
                 "am", "start", "-a", "android.media.action.STILL_IMAGE_CAMERA"])
        time.sleep(0.5)
        raise RuntimeError("No new photo detected after shutter press")

    remote = f"/sdcard/DCIM/Camera/{new_file}"
    local = str(Path(output_dir) / new_file)
    ok, _ = run_adb(["-s", device_addr, "pull", remote, local], timeout=15)
    if not ok:
        raise RuntimeError(f"Failed to pull {remote}")
    return local


# ---------------------------------------------------------------------------
# Reference display
# ---------------------------------------------------------------------------
REF_BRIGHTNESS = 80  # P2XL brightness when showing reference color (0-255)

def launch_reference_display(device_addr):
    """Open the /ref/live page on the reference phone (once per session).

    This page polls /ref/color every 500ms and updates its own background,
    so we never need to re-launch Chrome — just POST new colors to /ref/set.
    """
    url = f"{DASHBOARD_LAN_URL}/ref/live"

    # Max brightness, stay-on, no auto-brightness
    run_adb(["-s", device_addr, "shell",
             "settings", "put", "system", "screen_brightness", "255"])
    run_adb(["-s", device_addr, "shell",
             "svc", "power", "stayon", "true"])

    # Force-stop Chrome first so it opens a fresh tab
    run_adb(["-s", device_addr, "shell", "am", "force-stop", "com.android.chrome"])
    time.sleep(0.5)
    run_adb([
        "-s", device_addr, "shell",
        "am", "start", "-n",
        "com.android.chrome/com.google.android.apps.chrome.Main",
        "-a", "android.intent.action.VIEW",
        "-d", url,
    ])
    # Wait for page load, then scroll to hide toolbar
    time.sleep(3)
    run_adb(["-s", device_addr, "shell", "input", "swipe",
             "400", "800", "400", "200", "200"])
    time.sleep(0.5)
    # Enable immersive mode to hide nav bar
    run_adb(["-s", device_addr, "shell", "settings", "put", "global",
             "policy_control", "immersive.full=*"])


def set_reference_color(device_addr, r, g, b):
    """Set the reference phone's display color via the dashboard API.

    Fire-and-forget — the /ref/live page polls /ref/color every 500ms,
    so this just POSTs to /ref/set and the phone updates itself.
    """
    _fire_and_forget(f"{DASHBOARD_URL}/ref/set", {"r": r, "g": g, "b": b})


# ---------------------------------------------------------------------------
# Dashboard client (LED strips via HTTP)
# ---------------------------------------------------------------------------
import threading

def _fire_and_forget(url, payload):
    """POST in a background thread — never blocks the caller."""
    def _do():
        try:
            requests.post(url, json=payload, timeout=10)
        except Exception:
            pass
    threading.Thread(target=_do, daemon=True).start()


CALIBRATION_STRIP = "strip2"  # strip2 is in the camera frame

def set_strip_color(r, g, b):
    """Send color command, retry once on timeout. Returns True if ack'd.

    Targets the calibration strip only (not both) so one flaky BLE
    connection can't block the other.
    """
    for attempt in range(2):
        try:
            requests.post(f"{DASHBOARD_URL}/api/color",
                          json={"r": r, "g": g, "b": b, "targets": CALIBRATION_STRIP},
                          timeout=5)
            return True
        except requests.exceptions.Timeout:
            if attempt == 0:
                time.sleep(0.5)
                continue
            return False  # timed out twice
        except Exception:
            return False
    return False


def set_strip_brightness(level):
    """Set brightness on calibration strip."""
    try:
        requests.post(f"{DASHBOARD_URL}/api/brightness",
                      json={"level": level, "targets": CALIBRATION_STRIP},
                      timeout=5)
    except Exception:
        pass


@retry(max_attempts=3, delay=5)
def connect_strips():
    resp = requests.post(
        f"{DASHBOARD_URL}/api/connect",
        json={"targets": "both"},
        timeout=60,
    )
    resp.raise_for_status()


def dashboard_status():
    """Return the dashboard /api/status JSON or None."""
    try:
        resp = requests.get(f"{DASHBOARD_URL}/api/status", timeout=5)
        resp.raise_for_status()
        return resp.json()
    except Exception:
        return None


# ---------------------------------------------------------------------------
# Image analysis
# ---------------------------------------------------------------------------
def extract_region_color(image_path, region):
    """Return (r, g, b) average for a fractional *region* of the image.

    *region* is ``(x1, y1, x2, y2)`` in 0.0-1.0 fractions.  A 10 % margin
    is applied inward to avoid bezels/edges.
    """
    img = ImageOps.exif_transpose(Image.open(image_path)).convert("RGB")
    w, h = img.size
    x1, y1, x2, y2 = region

    # Convert fractions to pixels
    px1, py1, px2, py2 = x1 * w, y1 * h, x2 * w, y2 * h

    # Inset 10 % on each side
    rw = px2 - px1
    rh = py2 - py1
    margin_x = rw * 0.10
    margin_y = rh * 0.10
    px1 += margin_x
    py1 += margin_y
    px2 -= margin_x
    py2 -= margin_y

    crop = img.crop((int(px1), int(py1), int(px2), int(py2)))
    pixels = list(crop.getdata())
    n = len(pixels)
    if n == 0:
        return (0.0, 0.0, 0.0)
    r_avg = sum(p[0] for p in pixels) / n
    g_avg = sum(p[1] for p in pixels) / n
    b_avg = sum(p[2] for p in pixels) / n
    return (r_avg, g_avg, b_avg)


# ---------------------------------------------------------------------------
# Calibration math
# ---------------------------------------------------------------------------
def compute_correction(measurements):
    """Build a per-channel 256-entry correction LUT from measurements.

    For each channel the ratio ``strip_seen / ref_seen`` is computed at every
    sampled input level.  Piecewise linear interpolation fills the full 0-255
    range.  ``corrected = clamp(requested / ratio, 0, 255)``.
    """
    lut = {}
    for ch, idx in [("r", 0), ("g", 1), ("b", 2)]:
        # Collect (input_level, ratio) pairs
        points = []
        for m in measurements:
            inp = m["input_rgb"][idx]
            ref = m["ref_seen"][idx]
            strip = m["strip_seen"][idx]
            if ref < 10:
                continue  # skip near-black noise
            ratio = strip / ref
            points.append((inp, ratio))

        # Sort by input level and deduplicate (average ratios at same level)
        points.sort(key=lambda p: p[0])
        deduped = {}
        for level, ratio in points:
            deduped.setdefault(level, []).append(ratio)
        sorted_levels = sorted(deduped.keys())
        sorted_ratios = [sum(deduped[l]) / len(deduped[l]) for l in sorted_levels]

        # Piecewise linear interpolation across 0-255
        table = [0] * 256
        for i in range(256):
            if not sorted_levels:
                # No data — identity
                table[i] = i
                continue

            # Interpolate ratio
            if i <= sorted_levels[0]:
                ratio = sorted_ratios[0]
            elif i >= sorted_levels[-1]:
                ratio = sorted_ratios[-1]
            else:
                # Find surrounding points
                for k in range(len(sorted_levels) - 1):
                    if sorted_levels[k] <= i <= sorted_levels[k + 1]:
                        t = ((i - sorted_levels[k])
                             / (sorted_levels[k + 1] - sorted_levels[k]))
                        ratio = sorted_ratios[k] + t * (sorted_ratios[k + 1] - sorted_ratios[k])
                        break

            # corrected = requested / ratio
            if ratio > 0:
                corrected = i / ratio
            else:
                corrected = i
            table[i] = max(0, min(255, int(round(corrected))))

        lut[ch] = table
    return lut


# ---------------------------------------------------------------------------
# Test color generation
# ---------------------------------------------------------------------------
def generate_test_colors(extra_hex=None):
    """Return a list of (r, g, b) tuples covering ~25 calibration points."""
    colors = []

    # Black + white
    colors.append((0, 0, 0))
    colors.append((255, 255, 255))

    # 3 grays
    for v in (191, 128, 64):
        colors.append((v, v, v))

    # 12 hue sweep (S=1, V=1, hue at 30-degree steps)
    for deg in range(0, 360, 30):
        r, g, b = colorsys.hsv_to_rgb(deg / 360.0, 1.0, 1.0)
        colors.append((int(r * 255), int(g * 255), int(b * 255)))

    # 6 half-saturation primary/secondary (R,Y,G,C,B,M at S=0.5, V=1)
    for deg in (0, 60, 120, 180, 240, 300):
        r, g, b = colorsys.hsv_to_rgb(deg / 360.0, 0.5, 1.0)
        colors.append((int(r * 255), int(g * 255), int(b * 255)))

    # Extra colors from CLI
    if extra_hex:
        for h in extra_hex.split(","):
            h = h.strip().lstrip("#")
            if len(h) == 6:
                colors.append((int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)))

    return colors


# ---------------------------------------------------------------------------
# Persistence helpers
# ---------------------------------------------------------------------------
def save_json(path, data):
    Path(path).write_text(json.dumps(data, indent=2))


def load_json(path):
    return json.loads(Path(path).read_text())


def setup_output_dir():
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    d = Path("captures") / f"calibration_{ts}"
    d.mkdir(parents=True, exist_ok=True)
    return d


def load_regions(output_dir):
    rpath = Path(output_dir) / "regions.json"
    if rpath.exists():
        return load_json(rpath)
    return dict(DEFAULT_REGIONS)


def save_partial(output_dir, measurements):
    save_json(Path(output_dir) / "calibration.json", {"measurements": measurements})


def load_partial(output_dir):
    p = Path(output_dir) / "calibration.json"
    if p.exists():
        data = load_json(p)
        return data.get("measurements", [])
    return []


def find_latest_output_dir():
    """Find the most recent calibration output directory for --resume."""
    captures = Path("captures")
    if not captures.exists():
        return None
    dirs = sorted(captures.glob("calibration_*"), reverse=True)
    return dirs[0] if dirs else None


def already_measured(measurements, color):
    for m in measurements:
        if m["input_rgb"] == list(color):
            return True
    return False


# ---------------------------------------------------------------------------
# Pre-flight checks
# ---------------------------------------------------------------------------
def preflight_checks(camera_addr, ref_addr):
    """Verify all devices are reachable before starting."""
    print("Pre-flight checks:")

    # Camera phone
    ok, out = run_adb(["-s", camera_addr, "shell", "echo", "ok"])
    if not ok:
        sys.exit(f"  FAIL: Camera ({camera_addr}) not responding")
    print(f"  Camera  ({camera_addr}): OK")

    # Reference phone
    ok, out = run_adb(["-s", ref_addr, "shell", "echo", "ok"])
    if not ok:
        sys.exit(f"  FAIL: Reference ({ref_addr}) not responding")
    # Disable auto-brightness so we control it precisely
    run_adb(["-s", ref_addr, "shell",
             "settings", "put", "system", "screen_brightness_mode", "0"])
    print(f"  Reference ({ref_addr}): OK (auto-brightness off)")

    # Check if /ref/live is already polling (skip slow Chrome restart)
    try:
        resp = requests.get(f"{DASHBOARD_URL}/ref/color", timeout=1)
        # If we get a response, the endpoint exists — check if P2XL is polling
        # by looking at dashboard logs. For now, just assume it's up.
        print(f"  Reference display: /ref/set endpoint ready")
    except Exception:
        print("  Launching reference display (/ref/live)...")
        launch_reference_display(ref_addr)
        print(f"  Reference display: live (polling /ref/color)")

    # Dashboard
    status = dashboard_status()
    if status is None:
        sys.exit(f"  FAIL: Dashboard at {DASHBOARD_URL} not reachable")
    print(f"  Dashboard: OK")

    # Strips — report status (don't try to connect here, it's slow)
    conn = status.get("connection", {})
    connected = [k for k, v in conn.items() if v]
    if connected:
        print(f"  Strips: {', '.join(connected)} connected")
    else:
        print("  Strips: NOT connected — connect via dashboard UI or they'll auto-reconnect")

    print()


# ---------------------------------------------------------------------------
# Setup mode
# ---------------------------------------------------------------------------
def run_setup(camera_addr, ref_addr, output_dir, args):
    """Take a test photo with distinctive colors and draw region boxes."""
    print("=== Setup Mode ===\n")

    # Set distinctive colors
    print("Setting reference display to green, strips to red...")
    set_reference_color(ref_addr, 0, 255, 0)
    set_strip_color(255, 0, 0)
    time.sleep(args.settle_time)

    # Launch camera and capture
    launch_camera(camera_addr)
    print("Taking test photo...")
    photo_path = take_photo(camera_addr, str(output_dir))
    print(f"  Saved: {photo_path}")

    # Draw region rectangles
    regions = dict(DEFAULT_REGIONS)
    img = ImageOps.exif_transpose(Image.open(photo_path))
    draw = ImageDraw.Draw(img)
    w, h = img.size

    for name, (x1, y1, x2, y2) in regions.items():
        px1, py1, px2, py2 = int(x1 * w), int(y1 * h), int(x2 * w), int(y2 * h)
        color = (0, 255, 0) if name == "reference" else (255, 0, 0)
        for offset in range(3):  # thicker lines
            draw.rectangle(
                [px1 + offset, py1 + offset, px2 - offset, py2 - offset],
                outline=color,
            )

    preview_path = str(Path(output_dir) / "regions_preview.jpg")
    img.save(preview_path, quality=90)
    print(f"  Preview: {preview_path}")

    # Save region definitions
    regions_path = str(Path(output_dir) / "regions.json")
    save_json(regions_path, regions)
    print(f"  Regions: {regions_path}")

    print(
        "\nReview regions_preview.jpg.  If the reference display and strip"
        "\nare not in the expected halves, edit regions.json with adjusted"
        "\nfractional coordinates (x1, y1, x2, y2) and re-run calibration."
    )


# ---------------------------------------------------------------------------
# Verification
# ---------------------------------------------------------------------------
def run_verification(camera_addr, ref_addr, output_dir, regions, lut, args):
    """Verify the LUT by sending corrected colors and measuring the result."""
    print("\n=== Verification ===\n")

    verify_colors = [
        ("Red",     (255, 0, 0)),
        ("Green",   (0, 255, 0)),
        ("Blue",    (0, 0, 255)),
        ("Cyan",    (0, 255, 255)),
        ("Magenta", (255, 0, 255)),
        ("Yellow",  (255, 255, 0)),
    ]

    results = []
    for name, (r, g, b) in verify_colors:
        # Apply LUT correction
        cr = lut["r"][r]
        cg = lut["g"][g]
        cb = lut["b"][b]

        # Photo 1: corrected strip color, reference black
        set_reference_color(ref_addr, 0, 0, 0)
        set_strip_color(cr, cg, cb)
        time.sleep(args.settle_time)
        strip_photo = take_photo(camera_addr, str(output_dir))
        strip_rgb = extract_region_color(strip_photo, regions["strip"])

        # Photo 2: strip off, reference shows target color
        set_strip_color(0, 0, 0)
        set_reference_color(ref_addr, r, g, b)
        time.sleep(args.settle_time)
        ref_photo = take_photo(camera_addr, str(output_dir))
        ref_rgb = extract_region_color(ref_photo, regions["reference"])

        dist = math.sqrt(
            (ref_rgb[0] - strip_rgb[0]) ** 2
            + (ref_rgb[1] - strip_rgb[1]) ** 2
            + (ref_rgb[2] - strip_rgb[2]) ** 2
        )

        results.append({
            "name": name,
            "target_rgb": [r, g, b],
            "corrected_rgb": [cr, cg, cb],
            "ref_seen": [round(v, 1) for v in ref_rgb],
            "strip_seen": [round(v, 1) for v in strip_rgb],
            "euclidean_error": round(dist, 2),
            "photo": os.path.basename(photo_path),
        })
        print(f"  {name:8s}: error={dist:6.1f}  "
              f"ref=({ref_rgb[0]:.0f},{ref_rgb[1]:.0f},{ref_rgb[2]:.0f}) "
              f"strip=({strip_rgb[0]:.0f},{strip_rgb[1]:.0f},{strip_rgb[2]:.0f})")

    save_json(Path(output_dir) / "verification.json", results)

    # Summary table
    avg_err = sum(r["euclidean_error"] for r in results) / len(results)
    print(f"\n  Average error: {avg_err:.1f}")
    print(f"  Results saved to {output_dir / 'verification.json'}")


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------
def parse_args():
    parser = argparse.ArgumentParser(
        description="ELK-BLEDOM Color Calibration System",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--setup", action="store_true",
                        help="Take test photo and draw region boxes")
    parser.add_argument("--verify", action="store_true",
                        help="Run verification after calibration")
    parser.add_argument("--resume", action="store_true",
                        help="Resume a previous calibration run")
    parser.add_argument("--brightness", type=int, default=255,
                        help="Strip brightness 0-255 (default: 255)")
    parser.add_argument("--extra-colors", type=str, default=None,
                        help="Comma-separated hex colors to add (e.g. FF8800,AA00FF)")
    parser.add_argument("--settle-time", type=float, default=2.0,
                        help="Seconds to wait after color change (default: 2)")
    return parser.parse_args()


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
def main():
    args = parse_args()

    # Resolve ADB addresses
    print("Locating devices...")
    camera_addr = find_device_address(CAMERA_IP)
    if not camera_addr:
        sys.exit(f"Camera ({CAMERA_IP}) not found on ADB")
    ref_addr = find_device_address(REFERENCE_IP)
    if not ref_addr:
        sys.exit(f"Reference display ({REFERENCE_IP}) not found on ADB")

    # Pre-flight
    preflight_checks(camera_addr, ref_addr)

    # Output directory
    if args.resume:
        output_dir = find_latest_output_dir()
        if output_dir is None:
            sys.exit("No previous calibration directory found to resume")
        print(f"Resuming from {output_dir}\n")
    else:
        output_dir = setup_output_dir()

    # Setup mode exits early
    if args.setup:
        run_setup(camera_addr, ref_addr, output_dir, args)
        return

    # Load regions
    regions = load_regions(output_dir)

    # Generate test colors
    colors = generate_test_colors(args.extra_colors)
    print(f"Test colors: {len(colors)}")

    # Set brightness
    set_strip_brightness(args.brightness)

    # Launch camera
    launch_camera(camera_addr)

    # Load partial measurements if resuming
    measurements = []
    if args.resume:
        measurements = load_partial(output_dir)
        if measurements:
            print(f"Loaded {len(measurements)} existing measurements\n")

    # Calibration loop — two photos per color to avoid cross-contamination:
    #   1) Strip ON + P2XL black  → measure strip color
    #   2) Strip OFF + P2XL shows color → measure reference color
    print("=== Calibration ===\n")
    for color in colors:
        if already_measured(measurements, color):
            continue
        r, g, b = color

        # Photo 1: strip shows color, reference is black
        set_reference_color(ref_addr, 0, 0, 0)
        set_strip_color(r, g, b)
        time.sleep(args.settle_time)
        strip_photo = take_photo(camera_addr, str(output_dir))
        strip_rgb = extract_region_color(strip_photo, regions["strip"])

        # Photo 2: strip off, reference shows color
        set_strip_color(0, 0, 0)
        set_reference_color(ref_addr, r, g, b)
        time.sleep(args.settle_time)
        ref_photo = take_photo(camera_addr, str(output_dir))
        ref_rgb = extract_region_color(ref_photo, regions["reference"])

        measurement = {
            "input_rgb": [r, g, b],
            "ref_seen": [round(ref_rgb[0], 2), round(ref_rgb[1], 2), round(ref_rgb[2], 2)],
            "strip_seen": [round(strip_rgb[0], 2), round(strip_rgb[1], 2), round(strip_rgb[2], 2)],
            "photo_strip": os.path.basename(strip_photo),
            "photo_ref": os.path.basename(ref_photo),
        }
        measurements.append(measurement)
        save_partial(output_dir, measurements)

        print(f"  [{len(measurements)}/{len(colors)}] RGB({r},{g},{b}): "
              f"ref=({ref_rgb[0]:.0f},{ref_rgb[1]:.0f},{ref_rgb[2]:.0f}) "
              f"strip=({strip_rgb[0]:.0f},{strip_rgb[1]:.0f},{strip_rgb[2]:.0f})")

    # Compute correction LUT
    lut = compute_correction(measurements)

    # Save full results
    result = {
        "measurements": measurements,
        "correction_lut": lut,
        "brightness": args.brightness,
        "timestamp": datetime.now().isoformat(),
    }
    save_json(Path(output_dir) / "calibration.json", result)

    print(f"\nCalibration complete! LUT saved to {output_dir / 'calibration.json'}")

    # Optional verification pass
    if args.verify:
        run_verification(camera_addr, ref_addr, output_dir, regions, lut, args)


if __name__ == "__main__":
    main()
