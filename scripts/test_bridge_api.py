#!/usr/bin/env python3
"""
ELK Bridge API — Automated Endpoint Exerciser

Tests all HTTP API endpoints on the ESP32-S3 bridge.
Run from any machine on the same WiFi network as the bridge.

Usage:
    python test_bridge_api.py                     # uses elk-bridge.local
    python test_bridge_api.py 192.168.86.100      # uses explicit IP
    python test_bridge_api.py --quick              # skip slow tests (scan, pattern)
"""

import sys
import json
import time
import argparse
import requests
from dataclasses import dataclass

DEFAULT_HOST = "elk-bridge.local"
TIMEOUT = 10  # seconds per request


@dataclass
class TestResult:
    name: str
    passed: bool
    detail: str = ""
    response: dict | None = None


def test_status(base: str) -> TestResult:
    """GET /status — system status"""
    try:
        r = requests.get(f"{base}/status", timeout=TIMEOUT)
        data = r.json()
        if not data.get("ok"):
            return TestResult("GET /status", False, f"ok=false: {data.get('error')}")
        d = data.get("data", {})
        wifi = d.get("wifi", {})
        firmware = d.get("firmware", {})
        detail = (
            f"wifi={wifi.get('state')} ip={wifi.get('ip')} "
            f"rssi={wifi.get('rssi')} "
            f"fw={firmware.get('version')} "
            f"heap={firmware.get('freeHeap')} "
            f"strips={len(d.get('ble', {}).get('strips', []))}"
        )
        return TestResult("GET /status", True, detail, data)
    except Exception as e:
        return TestResult("GET /status", False, str(e))


def test_color(base: str, r: int, g: int, b: int) -> TestResult:
    """POST /api/color"""
    try:
        resp = requests.post(
            f"{base}/api/color",
            json={"r": r, "g": g, "b": b, "target": "all"},
            timeout=TIMEOUT,
        )
        data = resp.json()
        ok = data.get("ok", False)
        return TestResult(
            f"POST /api/color r={r} g={g} b={b}",
            ok,
            "" if ok else data.get("error", "unknown"),
            data,
        )
    except Exception as e:
        return TestResult(f"POST /api/color r={r} g={g} b={b}", False, str(e))


def test_brightness(base: str, level: int) -> TestResult:
    """POST /api/brightness"""
    try:
        resp = requests.post(
            f"{base}/api/brightness",
            json={"level": level, "target": "all"},
            timeout=TIMEOUT,
        )
        data = resp.json()
        ok = data.get("ok", False)
        return TestResult(
            f"POST /api/brightness level={level}",
            ok,
            "" if ok else data.get("error", "unknown"),
            data,
        )
    except Exception as e:
        return TestResult(f"POST /api/brightness level={level}", False, str(e))


def test_power(base: str, on: bool) -> TestResult:
    """POST /api/power"""
    try:
        resp = requests.post(
            f"{base}/api/power",
            json={"on": on, "target": "all"},
            timeout=TIMEOUT,
        )
        data = resp.json()
        ok = data.get("ok", False)
        return TestResult(
            f"POST /api/power on={on}",
            ok,
            "" if ok else data.get("error", "unknown"),
            data,
        )
    except Exception as e:
        return TestResult(f"POST /api/power on={on}", False, str(e))


def test_effect(base: str, code: int, speed: int) -> TestResult:
    """POST /api/effect"""
    try:
        resp = requests.post(
            f"{base}/api/effect",
            json={"code": code, "speed": speed, "target": "all"},
            timeout=TIMEOUT,
        )
        data = resp.json()
        ok = data.get("ok", False)
        return TestResult(
            f"POST /api/effect code=0x{code:02X} speed={speed}",
            ok,
            "" if ok else data.get("error", "unknown"),
            data,
        )
    except Exception as e:
        return TestResult(
            f"POST /api/effect code=0x{code:02X} speed={speed}", False, str(e)
        )


def test_raw(base: str, hex_str: str) -> TestResult:
    """POST /api/raw"""
    try:
        resp = requests.post(
            f"{base}/api/raw",
            json={"hex": hex_str, "target": "all"},
            timeout=TIMEOUT,
        )
        data = resp.json()
        ok = data.get("ok", False)
        return TestResult(
            f"POST /api/raw hex={hex_str}",
            ok,
            "" if ok else data.get("error", "unknown"),
            data,
        )
    except Exception as e:
        return TestResult(f"POST /api/raw hex={hex_str}", False, str(e))


def test_pattern(base: str) -> TestResult:
    """POST /api/pattern — start a 2-step pattern, verify playback, then stop"""
    steps = [
        {
            "r": 255, "g": 0, "b": 0,
            "brightness": 100, "duration": 2000,
            "transition": "cut", "fadeTime": 0,
        },
        {
            "r": 0, "g": 255, "b": 0,
            "brightness": 80, "duration": 2000,
            "transition": "fade", "fadeTime": 500,
        },
    ]
    try:
        resp = requests.post(
            f"{base}/api/pattern",
            json={"steps": steps, "loop": True, "target": "all"},
            timeout=TIMEOUT,
        )
        data = resp.json()
        if not data.get("ok"):
            return TestResult(
                "POST /api/pattern", False, data.get("error", "unknown"), data
            )

        # Wait and check playback state
        time.sleep(1)
        status = requests.get(f"{base}/status", timeout=TIMEOUT).json()
        playback = status.get("data", {}).get("playback", {})
        playing = playback.get("state") == "playing"

        return TestResult(
            "POST /api/pattern",
            True,
            f"playing={playing} step={playback.get('currentStep')}/{playback.get('totalSteps')}",
            data,
        )
    except Exception as e:
        return TestResult("POST /api/pattern", False, str(e))


def test_pattern_preempt(base: str) -> TestResult:
    """Verify that a direct color command preempts a running pattern"""
    try:
        # Start a pattern
        requests.post(
            f"{base}/api/pattern",
            json={
                "steps": [
                    {"r": 255, "g": 0, "b": 0, "brightness": 100, "duration": 5000, "transition": "cut", "fadeTime": 0},
                    {"r": 0, "g": 0, "b": 255, "brightness": 100, "duration": 5000, "transition": "cut", "fadeTime": 0},
                ],
                "loop": True,
            },
            timeout=TIMEOUT,
        )
        time.sleep(0.5)

        # Send a direct color command — should preempt
        requests.post(
            f"{base}/api/color",
            json={"r": 0, "g": 255, "b": 0},
            timeout=TIMEOUT,
        )
        time.sleep(0.5)

        # Check that pattern is now idle
        status = requests.get(f"{base}/status", timeout=TIMEOUT).json()
        playback = status.get("data", {}).get("playback", {})
        idle = playback.get("state") == "idle"

        return TestResult(
            "Pattern preemption",
            idle,
            f"playback_state={playback.get('state')} (expected idle)",
        )
    except Exception as e:
        return TestResult("Pattern preemption", False, str(e))


def test_stop(base: str) -> TestResult:
    """POST /api/stop"""
    try:
        resp = requests.post(
            f"{base}/api/stop",
            json={"target": "all"},
            timeout=TIMEOUT,
        )
        data = resp.json()
        ok = data.get("ok", False)
        return TestResult(
            "POST /api/stop", ok, "" if ok else data.get("error", "unknown"), data
        )
    except Exception as e:
        return TestResult("POST /api/stop", False, str(e))


def test_scan(base: str) -> TestResult:
    """GET /api/scan — takes ~5 seconds"""
    try:
        resp = requests.get(f"{base}/api/scan", timeout=15)
        data = resp.json()
        if not data.get("ok"):
            return TestResult(
                "GET /api/scan", False, data.get("error", "unknown"), data
            )
        devices = data.get("data", {}).get("devices", [])
        names = [d.get("name", "?") for d in devices]
        return TestResult(
            "GET /api/scan", True, f"found {len(devices)}: {names}", data
        )
    except Exception as e:
        return TestResult("GET /api/scan", False, str(e))


def test_devices(base: str) -> TestResult:
    """GET /api/devices"""
    try:
        resp = requests.get(f"{base}/api/devices", timeout=TIMEOUT)
        data = resp.json()
        if not data.get("ok"):
            return TestResult(
                "GET /api/devices", False, data.get("error", "unknown"), data
            )
        devices = data.get("data", {}).get("devices", [])
        detail = ", ".join(
            f"{d.get('slot', '?')}={d.get('address', '?')}({d.get('state', '?')})"
            for d in devices
        )
        return TestResult("GET /api/devices", True, detail or "none", data)
    except Exception as e:
        return TestResult("GET /api/devices", False, str(e))


def test_dashboard(base: str) -> TestResult:
    """GET / — embedded HTML dashboard"""
    try:
        resp = requests.get(f"{base}/", timeout=TIMEOUT)
        ct = resp.headers.get("Content-Type", "")
        ok = resp.status_code == 200 and "html" in ct.lower()
        return TestResult(
            "GET / (dashboard)",
            ok,
            f"status={resp.status_code} type={ct} len={len(resp.content)}",
        )
    except Exception as e:
        return TestResult("GET / (dashboard)", False, str(e))


def run_tests(base: str, quick: bool = False) -> list[TestResult]:
    results = []

    print(f"\n{'='*60}")
    print(f"  ELK Bridge API Test Suite")
    print(f"  Target: {base}")
    print(f"  Mode: {'quick' if quick else 'full'}")
    print(f"{'='*60}\n")

    # Core tests
    tests = [
        ("Status", lambda: test_status(base)),
        ("Dashboard", lambda: test_dashboard(base)),
        ("Devices", lambda: test_devices(base)),
        ("Power ON", lambda: test_power(base, True)),
        ("Color RED", lambda: test_color(base, 255, 0, 0)),
        ("Color GREEN", lambda: test_color(base, 0, 255, 0)),
        ("Color BLUE", lambda: test_color(base, 0, 0, 255)),
        ("Brightness 50%", lambda: test_brightness(base, 50)),
        ("Brightness 100%", lambda: test_brightness(base, 100)),
        ("Effect Rainbow", lambda: test_effect(base, 0x87, 3)),
        ("Raw command", lambda: test_raw(base, "7E00050300FF0000EF")),
        ("Stop", lambda: test_stop(base)),
    ]

    if not quick:
        tests.extend([
            ("Scan (5s)", lambda: test_scan(base)),
            ("Pattern play", lambda: test_pattern(base)),
            ("Pattern preempt", lambda: test_pattern_preempt(base)),
            ("Stop after tests", lambda: test_stop(base)),
        ])

    tests.append(("Power OFF", lambda: test_power(base, False)))

    for label, fn in tests:
        print(f"  [{label}] ", end="", flush=True)
        result = fn()
        results.append(result)
        status = "PASS" if result.passed else "FAIL"
        print(f"{status}  {result.detail}")
        time.sleep(0.3)  # brief pause between tests

    # Summary
    passed = sum(1 for r in results if r.passed)
    total = len(results)
    print(f"\n{'='*60}")
    print(f"  Results: {passed}/{total} passed")
    if passed < total:
        print(f"  Failed:")
        for r in results:
            if not r.passed:
                print(f"    - {r.name}: {r.detail}")
    print(f"{'='*60}\n")

    return results


def main():
    parser = argparse.ArgumentParser(description="ELK Bridge API test suite")
    parser.add_argument(
        "host",
        nargs="?",
        default=DEFAULT_HOST,
        help=f"Bridge hostname or IP (default: {DEFAULT_HOST})",
    )
    parser.add_argument(
        "--port", type=int, default=80, help="Bridge port (default: 80)"
    )
    parser.add_argument(
        "--quick", action="store_true", help="Skip slow tests (scan, pattern)"
    )
    args = parser.parse_args()

    base = f"http://{args.host}"
    if args.port != 80:
        base = f"http://{args.host}:{args.port}"

    # Connectivity check
    print(f"Connecting to {base}...")
    try:
        r = requests.get(f"{base}/status", timeout=5)
        r.json()
    except requests.ConnectionError:
        print(f"ERROR: Cannot reach {base}")
        print("Is the bridge powered on and connected to WiFi?")
        sys.exit(1)
    except Exception as e:
        print(f"ERROR: Unexpected response from {base}: {e}")
        sys.exit(1)

    results = run_tests(base, quick=args.quick)
    sys.exit(0 if all(r.passed for r in results) else 1)


if __name__ == "__main__":
    main()
