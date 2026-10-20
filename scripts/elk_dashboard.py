#!/usr/bin/env python3
"""ELK-BLEDOM LED Dashboard — FastAPI backend with BLE control and pattern engine.

Run from Windows Python (not WSL2 — needs Bluetooth hardware):
    pip install fastapi uvicorn[standard]
    python scripts/elk_dashboard.py

Then open http://localhost:8080 in a browser.
"""

import asyncio
import colorsys
import json
import logging
import math
import os
import sys
import time
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.responses import HTMLResponse, JSONResponse, FileResponse
import uvicorn

# Import command builders from elk_control.py (same directory)
sys.path.insert(0, str(Path(__file__).parent))
from elk_control import cmd_color, cmd_brightness, cmd_effect, cmd_power, EFFECTS

logger = logging.getLogger("elk_dashboard")

# ---------------------------------------------------------------------------
# Known strips
# ---------------------------------------------------------------------------
STRIP1_MAC = "BE:67:00:35:04:9B"  # ELK-BLEDOM0B
STRIP2_MAC = "BE:67:00:35:11:78"  # ELK-BLEDOM

STRIPS = {
    "strip1": STRIP1_MAC,
    "strip2": STRIP2_MAC,
}

WRITE_CHAR = "0000fff3-0000-1000-8000-00805f9b34fb"

# ---------------------------------------------------------------------------
# Presets
# ---------------------------------------------------------------------------
PRESETS_PATH = Path(__file__).parent / "elk_presets.json"

BUILTIN_PRESETS = {
    "American Flag": {
        "steps": [
            {"color": "#B31942", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#FFFFFF", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#0A3161", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
        ]
    },
    "Utah State": {
        "steps": [
            {"color": "#071D49", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#FFFFFF", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#AA0200", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#FFB81D", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
        ]
    },
    "South Jordan": {
        "steps": [
            {"color": "#005E2C", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#1A5276", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#FFFFFF", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#B87333", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
        ]
    },
    "4th of July": {
        "steps": [
            {"color": "#C41F40", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#FFFFFF", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#1E2D60", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
        ]
    },
    "Pioneer Day": {
        "steps": [
            {"color": "#071D49", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#FFFFFF", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#AA0200", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#FFB81D", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#8B5E3C", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#F5E6C8", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
        ]
    },
    "Christmas": {
        "steps": [
            {"color": "#C30F16", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#165B33", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#F8B229", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#FFFFFF", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
        ]
    },
    "Halloween": {
        "steps": [
            {"color": "#FF9A00", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#CC2200", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#000000", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#881EE4", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#85E21F", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
        ]
    },
    "Mardi Gras": {
        "steps": [
            {"color": "#880085", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#FFD700", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#00A651", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
        ]
    },
    "Pride Rainbow": {
        "steps": [
            {"color": "#E40303", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#FF8C00", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#FFED00", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#008026", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#004DFF", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
            {"color": "#750787", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
        ]
    },
}


def load_presets() -> dict:
    if PRESETS_PATH.exists():
        try:
            return json.loads(PRESETS_PATH.read_text())
        except (json.JSONDecodeError, OSError):
            pass
    # Seed with built-in presets
    save_presets(BUILTIN_PRESETS)
    return dict(BUILTIN_PRESETS)


def save_presets(presets: dict):
    PRESETS_PATH.write_text(json.dumps(presets, indent=2))


# ---------------------------------------------------------------------------
# BLE Connection Manager
# ---------------------------------------------------------------------------
class BLEConnectionManager:
    def __init__(self):
        self._clients: dict[str, "BleakClient"] = {}
        self._lock = asyncio.Lock()
        self._wanted: set[str] = set()  # MACs user wants connected
        self._reconnect_task: Optional[asyncio.Task] = None
        self._on_reconnect = None  # callback for connection state changes

    @property
    def connected(self) -> dict[str, bool]:
        return {
            name: (mac in self._clients and self._clients[mac].is_connected)
            for name, mac in STRIPS.items()
        }

    def _start_reconnect_loop(self):
        if self._reconnect_task and not self._reconnect_task.done():
            return
        self._reconnect_task = asyncio.create_task(self._reconnect_loop())

    async def _reconnect_loop(self):
        """Background loop: retry connecting to wanted-but-disconnected strips."""
        from bleak import BleakClient
        while self._wanted:
            await asyncio.sleep(5)
            for mac in list(self._wanted):
                client = self._clients.get(mac)
                if client and client.is_connected:
                    continue
                try:
                    logger.info(f"Auto-reconnecting {mac}...")
                    client = BleakClient(mac, timeout=15)
                    await client.connect()
                    self._clients[mac] = client
                    logger.info(f"  {mac} auto-reconnected!")
                    if self._on_reconnect:
                        await self._on_reconnect()
                except Exception as e:
                    logger.warning(f"  Auto-reconnect {mac} failed: {e}")

    async def connect(self, targets: list[str]):
        from bleak import BleakClient
        async with self._lock:
            for name in targets:
                mac = STRIPS.get(name)
                if not mac:
                    continue
                self._wanted.add(mac)
                if mac in self._clients and self._clients[mac].is_connected:
                    continue
                try:
                    logger.info(f"Connecting to {name} ({mac})...")
                    client = BleakClient(mac, timeout=20)
                    await client.connect()
                    self._clients[mac] = client
                    logger.info(f"  {name} connected!")
                except Exception as e:
                    logger.error(f"  {name} connection failed: {e}")
                    raise
        self._start_reconnect_loop()

    async def disconnect(self, targets: list[str]):
        async with self._lock:
            for name in targets:
                mac = STRIPS.get(name)
                if not mac:
                    continue
                self._wanted.discard(mac)
                client = self._clients.pop(mac, None)
                if client:
                    try:
                        await client.disconnect()
                    except Exception:
                        pass
                    logger.info(f"  {name} disconnected")

    async def write(self, targets: list[str], data: bytes,
                    reconnect: bool = True) -> list[str]:
        """Write to targeted strips concurrently.

        Args:
            reconnect: If True, attempt reconnect on failure (slow).
                       If False, skip disconnected strips (fast, for patterns).

        Returns list of error messages (empty = all OK).
        """
        macs = [STRIPS[t] for t in targets if t in STRIPS]
        errors = []

        async def _write_one(mac: str):
            client = self._clients.get(mac)
            if not client or not client.is_connected:
                if not reconnect:
                    return  # skip silently in fast mode
                from bleak import BleakClient
                try:
                    logger.info(f"Reconnecting {mac}...")
                    client = BleakClient(mac, timeout=20)
                    await client.connect()
                    self._clients[mac] = client
                except Exception as e:
                    logger.error(f"Reconnect failed for {mac}: {e}")
                    errors.append(f"{mac}: {e}")
                    return
            try:
                await client.write_gatt_char(WRITE_CHAR, data, response=False)
            except Exception as e:
                logger.error(f"Write failed for {mac}: {e}")
                errors.append(f"{mac}: {e}")
                self._clients.pop(mac, None)

        await asyncio.gather(*[_write_one(m) for m in macs])
        if errors and self._wanted:
            self._start_reconnect_loop()
        return errors

    async def disconnect_all(self):
        await self.disconnect(list(STRIPS.keys()))


# ---------------------------------------------------------------------------
# Pattern Engine
# ---------------------------------------------------------------------------
class PatternEngine:
    def __init__(self, ble: BLEConnectionManager, broadcast):
        self._ble = ble
        self._broadcast = broadcast
        self._task: Optional[asyncio.Task] = None
        self._stop_event = asyncio.Event()
        self.state = "stopped"  # stopped | playing
        self.current_step = 0
        self.total_steps = 0
        self.current_color = "#000000"
        self.loop = False

    async def play(self, targets: list[str], pattern: list[dict], loop: bool):
        await self.stop()
        self._stop_event.clear()
        self.loop = loop
        self.total_steps = len(pattern)
        self.state = "playing"
        self._task = asyncio.create_task(self._run(targets, pattern, loop))

    async def stop(self):
        if self._task and not self._task.done():
            self._stop_event.set()
            try:
                await asyncio.wait_for(self._task, timeout=2.0)
            except (asyncio.TimeoutError, asyncio.CancelledError):
                self._task.cancel()
        self._task = None
        self.state = "stopped"
        self.current_step = 0
        await self._broadcast({"type": "playback", "state": "stopped",
                                "step": 0, "total": self.total_steps,
                                "color": self.current_color, "loop": self.loop})

    def _parse_color(self, hex_color: str) -> tuple[int, int, int]:
        h = hex_color.lstrip("#")
        return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)

    def _brightness_to_byte(self, pct: int) -> int:
        return max(0, min(255, int(pct * 255 / 100)))

    async def _send_color_and_brightness(self, targets, r, g, b, brightness_pct):
        r, g, b = _apply_lut(r, g, b)
        await self._ble.write(targets, cmd_color(r, g, b), reconnect=False)
        await self._ble.write(targets, cmd_brightness(self._brightness_to_byte(brightness_pct)),
                              reconnect=False)

    async def _fade(self, targets, r1, g1, b1, r2, g2, b2, br1, br2, fade_time):
        steps = max(1, int(fade_time / 0.03))
        for i in range(steps):
            if self._stop_event.is_set():
                return
            t = (i + 1) / steps
            r = int(r1 + (r2 - r1) * t)
            g = int(g1 + (g2 - g1) * t)
            b = int(b1 + (b2 - b1) * t)
            br = int(br1 + (br2 - br1) * t)
            await self._ble.write(targets, cmd_color(r, g, b), reconnect=False)
            if abs(br - br1) > 2 or i == steps - 1:
                await self._ble.write(targets, cmd_brightness(self._brightness_to_byte(br)),
                                      reconnect=False)
            await asyncio.sleep(0.03)

    async def _run(self, targets, pattern, loop):
        try:
            prev_r, prev_g, prev_b, prev_br = 0, 0, 0, 100
            while True:
                for i, step in enumerate(pattern):
                    if self._stop_event.is_set():
                        return
                    self.current_step = i + 1
                    r, g, b = self._parse_color(step["color"])
                    br = step.get("brightness", 100)
                    self.current_color = step["color"]

                    # Broadcast status FIRST (instant UI update)
                    await self._broadcast({
                        "type": "playback", "state": "playing",
                        "step": self.current_step, "total": self.total_steps,
                        "color": self.current_color, "loop": self.loop,
                    })

                    # Then send to BLE (non-blocking, no reconnect)
                    if step.get("transition") == "fade" and step.get("fade_time", 0) > 0:
                        await self._fade(targets, prev_r, prev_g, prev_b, r, g, b,
                                         prev_br, br, step["fade_time"])
                    else:
                        await self._send_color_and_brightness(targets, r, g, b, br)

                    prev_r, prev_g, prev_b, prev_br = r, g, b, br

                    # Hold for duration (interruptible)
                    try:
                        await asyncio.wait_for(self._stop_event.wait(),
                                               timeout=step.get("duration", 2.0))
                        return  # stop was requested
                    except asyncio.TimeoutError:
                        pass  # duration elapsed normally

                if not loop:
                    break
        except Exception as e:
            logger.error(f"Pattern playback error: {e}")
            await self._broadcast({"type": "error", "message": str(e)})
        finally:
            self.state = "stopped"
            await self._broadcast({"type": "playback", "state": "stopped",
                                    "step": 0, "total": self.total_steps,
                                    "color": self.current_color, "loop": self.loop})


# ---------------------------------------------------------------------------
# FastAPI App
# ---------------------------------------------------------------------------
app = FastAPI(title="ELK-BLEDOM Dashboard")
ble_mgr = BLEConnectionManager()
ws_clients: set[WebSocket] = set()
calibration_lut: Optional[dict] = None  # legacy per-channel LUT
calibration_map: Optional[list] = None   # visual calibration: list of {deg, standard, strip_input}
ref_current_color: list[int] = [0, 0, 0]  # current reference display color (set via /ref/set)


async def broadcast(msg: dict):
    dead = set()
    for ws in ws_clients:
        try:
            await ws.send_json(msg)
        except Exception:
            dead.add(ws)
    ws_clients.difference_update(dead)


pattern_engine = PatternEngine(ble_mgr, broadcast)
ble_mgr._on_reconnect = lambda: broadcast_connection()
presets = load_presets()


async def broadcast_connection():
    await broadcast({"type": "connection", **ble_mgr.connected})


# --- HTML ---
@app.get("/", response_class=HTMLResponse)
async def index():
    html_path = Path(__file__).parent / "elk_dashboard.html"
    return HTMLResponse(html_path.read_text())


@app.get("/ref", response_class=HTMLResponse)
async def ref_color(r: int = 0, g: int = 0, b: int = 0):
    """Full-screen solid color page for calibration reference display (static)."""
    return HTMLResponse(
        f"<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
        f"<meta name='theme-color' content='rgb({r},{g},{b})'>"
        f"</head><body style='margin:0;padding:0;background:rgb({r},{g},{b});"
        f"width:100vw;height:300vh;overflow:hidden'></body></html>"
    )


@app.get("/ref/live", response_class=HTMLResponse)
async def ref_live():
    """Self-updating full-screen color page. Polls /ref/color every 500ms."""
    return HTMLResponse(
        "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
        "<meta name='theme-color' content='#000000'>"
        "</head><body style='margin:0;padding:0;background:#000;"
        "width:100vw;height:300vh;overflow:hidden'>"
        "<script>"
        "async function poll(){"
        "try{const r=await fetch('/ref/color');const c=await r.json();"
        "const s=`rgb(${c.r},${c.g},${c.b})`;"
        "document.body.style.background=s;"
        "document.querySelector('meta[name=theme-color]').content=s;"
        "}catch(e){}"
        "setTimeout(poll,500);}"
        "poll();"
        "</script></body></html>"
    )


@app.get("/ref/color")
async def ref_get_color():
    """Return the current reference display color."""
    global ref_current_color
    return {"r": ref_current_color[0], "g": ref_current_color[1], "b": ref_current_color[2]}


@app.post("/ref/set")
async def ref_set_color(body: dict):
    """Set the reference display color (the /ref/live page will pick it up)."""
    global ref_current_color
    ref_current_color = [body.get("r", 0), body.get("g", 0), body.get("b", 0)]
    return {"ok": True, "color": ref_current_color}


# --- Calibration viewer ---
@app.get("/calibration", response_class=HTMLResponse)
async def calibration_viewer():
    html_path = Path(__file__).parent / "elk_calibration_viewer.html"
    return HTMLResponse(html_path.read_text())


@app.get("/calibration/data")
async def calibration_data():
    """Return calibration measurements, photos list, and LUT for the viewer."""
    captures = Path(__file__).parent.parent / "captures"
    # Find latest calibration dir
    cal_dirs = sorted(captures.glob("calibration_*"), reverse=True) if captures.exists() else []
    if not cal_dirs:
        return JSONResponse({"measurements": [], "photos": [], "total_colors": 0},
                            headers={"Cache-Control": "no-store"})
    cal_dir = cal_dirs[0]

    # Load calibration.json (partial or complete)
    cal_path = cal_dir / "calibration.json"
    cal = {}
    if cal_path.exists():
        cal = json.loads(cal_path.read_text())

    # List all JPG photos in the directory
    photos = sorted(
        [f.name for f in cal_dir.glob("*.jpg")] + [f.name for f in cal_dir.glob("*.MP.jpg")],
    )

    # Load verification if present
    ver_path = cal_dir / "verification.json"
    verification = None
    if ver_path.exists():
        verification = json.loads(ver_path.read_text())

    return JSONResponse({
        "measurements": cal.get("measurements", []),
        "correction_lut": cal.get("correction_lut"),
        "total_colors": cal.get("total_colors", 23),
        "brightness": cal.get("brightness"),
        "timestamp": cal.get("timestamp"),
        "complete": "correction_lut" in cal,
        "verification": verification,
        "photos": photos,
        "dir": cal_dir.name,
    }, headers={"Cache-Control": "no-store"})


@app.get("/calibration/photo/{filename}")
async def calibration_photo(filename: str):
    """Serve a captured photo from the latest calibration directory."""
    captures = Path(__file__).parent.parent / "captures"
    cal_dirs = sorted(captures.glob("calibration_*"), reverse=True) if captures.exists() else []
    if not cal_dirs:
        return JSONResponse({"error": "no calibration directory"}, 404)
    photo_path = cal_dirs[0] / filename
    if not photo_path.exists():
        return JSONResponse({"error": "photo not found"}, 404)
    return FileResponse(str(photo_path), media_type="image/jpeg")


# --- Status ---
@app.get("/api/status")
async def get_status():
    return {
        "connection": ble_mgr.connected,
        "playback": {
            "state": pattern_engine.state,
            "step": pattern_engine.current_step,
            "total": pattern_engine.total_steps,
            "color": pattern_engine.current_color,
            "loop": pattern_engine.loop,
        },
    }


# --- Effects list ---
@app.get("/api/effects")
async def get_effects():
    return list(EFFECTS.keys())


# --- Presets ---
@app.get("/api/presets")
async def get_presets():
    return presets


@app.post("/api/presets")
async def save_preset(body: dict):
    name = body.get("name", "").strip()
    pattern = body.get("pattern")
    if not name or not pattern:
        return JSONResponse({"error": "name and pattern required"}, 400)
    presets[name] = {"steps": pattern}
    save_presets(presets)
    return {"ok": True}


@app.delete("/api/presets/{name}")
async def delete_preset(name: str):
    if name in presets:
        del presets[name]
        save_presets(presets)
    return {"ok": True}


# --- Helpers ---
def resolve_targets(body: dict) -> list[str]:
    t = body.get("targets", "both")
    if t == "both":
        return ["strip1", "strip2"]
    if isinstance(t, list):
        return t
    return [t]


# --- Connect / Disconnect ---
@app.post("/api/connect")
async def api_connect(body: dict):
    targets = resolve_targets(body)
    try:
        await ble_mgr.connect(targets)
        await broadcast_connection()
        return {"ok": True, "connection": ble_mgr.connected}
    except Exception as e:
        await broadcast_connection()
        return JSONResponse({"error": str(e), "connection": ble_mgr.connected}, 500)


@app.post("/api/disconnect")
async def api_disconnect(body: dict):
    targets = resolve_targets(body)
    await ble_mgr.disconnect(targets)
    await broadcast_connection()
    return {"ok": True, "connection": ble_mgr.connected}


# --- Direct Controls ---
async def _ble_result(errors: list[str]):
    """Return OK or error response based on BLE write results."""
    if errors:
        await broadcast({"type": "error", "message": "; ".join(errors)})
        return JSONResponse({"ok": False, "errors": errors}, 200)
    return {"ok": True}


def _apply_lut(r, g, b):
    """Apply color correction using visual calibration map (HSV interpolation).

    Falls back to legacy per-channel LUT if no visual calibration loaded.
    """
    if calibration_map:
        return _apply_visual_calibration(r, g, b)
    if calibration_lut:
        r = calibration_lut["r"][r]
        g = calibration_lut["g"][g]
        b = calibration_lut["b"][b]
    return r, g, b


# White calibration point (from visual tuning)
_WHITE_STRIP = (75, 40, 15)


def _apply_visual_calibration(r, g, b):
    """Map standard RGB to corrected strip RGB using 12-hue calibration data.

    Approach:
    1. Convert input to HSV
    2. Find two nearest calibration hues, interpolate per-channel scale factors
    3. Scale by saturation (low sat → blend toward white correction)
    4. Scale by value (brightness)
    """
    if r == 0 and g == 0 and b == 0:
        return (0, 0, 0)

    h, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
    hue_deg = h * 360.0

    # Find the two bracketing calibration points by hue
    pts = calibration_map  # sorted by deg
    lo = pts[-1]  # wrap: last point (330°) is "below" 0°
    hi = pts[0]   # wrap: first point (0°) is "above" 330°
    for i in range(len(pts)):
        if pts[i]["deg"] <= hue_deg:
            lo = pts[i]
            hi = pts[(i + 1) % len(pts)]
        else:
            break

    # Interpolation factor between lo and hi
    lo_deg = lo["deg"]
    hi_deg = hi["deg"]
    if hi_deg <= lo_deg:
        hi_deg += 360  # wrap
    span = hi_deg - lo_deg
    if span == 0:
        t = 0.0
    else:
        d = hue_deg - lo_deg
        if d < 0:
            d += 360
        t = d / span
    t = max(0.0, min(1.0, t))

    # Compute per-channel scale factors from each calibration point
    def scales(pt):
        std = pt["standard"]
        strip = pt["strip_input"]
        sc = []
        for i in range(3):
            if std[i] > 0:
                sc.append(strip[i] / std[i])
            else:
                sc.append(strip[i] / 255.0 if strip[i] > 0 else 0.0)
        return sc

    lo_sc = scales(lo)
    hi_sc = scales(hi)

    # Lerp scale factors
    sc = [lo_sc[i] * (1 - t) + hi_sc[i] * t for i in range(3)]

    # Apply hue-based correction at full saturation
    hue_r = r * sc[0]
    hue_g = g * sc[1]
    hue_b = b * sc[2]

    # White correction for desaturated colors
    white_r = r * _WHITE_STRIP[0] / 255.0
    white_g = g * _WHITE_STRIP[1] / 255.0
    white_b = b * _WHITE_STRIP[2] / 255.0

    # Blend between hue correction and white correction based on saturation
    out_r = hue_r * s + white_r * (1 - s)
    out_g = hue_g * s + white_g * (1 - s)
    out_b = hue_b * s + white_b * (1 - s)

    return (
        max(0, min(255, int(round(out_r)))),
        max(0, min(255, int(round(out_g)))),
        max(0, min(255, int(round(out_b)))),
    )


@app.post("/api/calibration")
async def api_calibration(body: dict):
    """Hot-reload calibration data without restarting dashboard.

    Accepts:
      - {"path": "visual_calibration.json"} — load visual calibration map
      - {"correction_lut": {...}} — legacy per-channel LUT
      - {"action": "clear"} — disable all correction
    """
    global calibration_lut, calibration_map
    if "path" in body:
        import json as _json
        try:
            with open(body["path"]) as f:
                cal = _json.load(f)
            # Detect format: visual calibration has color names as keys with "deg"
            if isinstance(cal, dict) and any("deg" in v for v in cal.values() if isinstance(v, dict)):
                # Visual calibration map
                calibration_map = sorted(
                    [{"deg": v["deg"], "standard": v["standard"], "strip_input": v["strip_input"]}
                     for v in cal.values() if isinstance(v, dict) and "deg" in v],
                    key=lambda p: p["deg"]
                )
                calibration_lut = None
                return {"ok": True, "status": "visual_calibration_loaded",
                        "points": len(calibration_map), "path": body["path"]}
            # Legacy: correction_lut inside a calibration.json
            calibration_lut = cal.get("correction_lut")
            calibration_map = None
            if calibration_lut:
                return {"ok": True, "status": "legacy_lut_loaded", "path": body["path"]}
            return {"ok": False, "error": "Unrecognized calibration format"}
        except Exception as e:
            return JSONResponse({"ok": False, "error": str(e)}, 400)
    elif "correction_lut" in body:
        calibration_lut = body["correction_lut"]
        calibration_map = None
        return {"ok": True, "status": "legacy_lut_loaded"}
    elif body.get("action") == "clear":
        calibration_lut = None
        calibration_map = None
        return {"ok": True, "status": "cleared"}
    return JSONResponse({"ok": False, "error": "Provide path, correction_lut, or action=clear"}, 400)


@app.get("/api/calibration")
async def api_calibration_status():
    """Check calibration status and preview corrections for test colors."""
    if calibration_map:
        # Show what a few standard colors map to
        samples = {}
        for name, rgb in [("red", (255,0,0)), ("green", (0,255,0)), ("blue", (0,0,255)),
                          ("white", (255,255,255)), ("cyan", (0,255,255)), ("orange", (255,128,0))]:
            corrected = _apply_visual_calibration(*rgb)
            samples[name] = {"input": list(rgb), "corrected": list(corrected)}
        return {"active": True, "type": "visual", "points": len(calibration_map),
                "samples": samples}
    if calibration_lut:
        return {"active": True, "type": "legacy_lut",
                "r_255": calibration_lut["r"][255],
                "g_255": calibration_lut["g"][255],
                "b_255": calibration_lut["b"][255]}
    return {"active": False}


@app.post("/api/color")
async def api_color(body: dict):
    targets = resolve_targets(body)
    r, g, b = _apply_lut(body["r"], body["g"], body["b"])
    await pattern_engine.stop()
    errs = await ble_mgr.write(targets, cmd_color(r, g, b))
    return await _ble_result(errs)


@app.post("/api/brightness")
async def api_brightness(body: dict):
    targets = resolve_targets(body)
    level = body["level"]
    await pattern_engine.stop()
    errs = await ble_mgr.write(targets, cmd_brightness(level))
    return await _ble_result(errs)


@app.post("/api/effect")
async def api_effect(body: dict):
    targets = resolve_targets(body)
    effect_name = body["effect"]
    speed = body.get("speed", 3)
    code = EFFECTS.get(effect_name)
    if code is None:
        return JSONResponse({"error": f"Unknown effect: {effect_name}"}, 400)
    await pattern_engine.stop()
    errs = await ble_mgr.write(targets, cmd_effect(code, speed))
    return await _ble_result(errs)


@app.post("/api/power")
async def api_power(body: dict):
    targets = resolve_targets(body)
    on = body["on"]
    errs = await ble_mgr.write(targets, cmd_power(on))
    return await _ble_result(errs)


# --- Pattern ---
@app.post("/api/pattern/play")
async def api_pattern_play(body: dict):
    targets = resolve_targets(body)
    pattern = body["pattern"]
    loop = body.get("loop", False)
    await pattern_engine.play(targets, pattern, loop)
    return {"ok": True}


@app.post("/api/pattern/stop")
async def api_pattern_stop():
    await pattern_engine.stop()
    return {"ok": True}


# --- WebSocket ---
@app.websocket("/ws")
async def websocket_endpoint(ws: WebSocket):
    await ws.accept()
    ws_clients.add(ws)
    # Send initial state
    await ws.send_json({"type": "connection", **ble_mgr.connected})
    await ws.send_json({
        "type": "playback", "state": pattern_engine.state,
        "step": pattern_engine.current_step, "total": pattern_engine.total_steps,
        "color": pattern_engine.current_color, "loop": pattern_engine.loop,
    })
    try:
        while True:
            await ws.receive_text()  # keep alive, ignore client messages
    except WebSocketDisconnect:
        pass
    finally:
        ws_clients.discard(ws)


# --- Shutdown ---
@app.on_event("shutdown")
async def shutdown():
    await pattern_engine.stop()
    await ble_mgr.disconnect_all()


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    import argparse as _ap
    _p = _ap.ArgumentParser()
    _p.add_argument("--port", type=int, default=8080)
    _p.add_argument("--calibration", type=str, default=None,
                     help="Path to calibration.json with correction LUT")
    _args = _p.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(message)s")
    if _args.calibration:
        with open(_args.calibration) as f:
            _cal = json.load(f)
        # Auto-detect format
        if isinstance(_cal, dict) and any("deg" in v for v in _cal.values() if isinstance(v, dict)):
            calibration_map = sorted(
                [{"deg": v["deg"], "standard": v["standard"], "strip_input": v["strip_input"]}
                 for v in _cal.values() if isinstance(v, dict) and "deg" in v],
                key=lambda p: p["deg"]
            )
            print(f"Loaded visual calibration ({len(calibration_map)} hues) from {_args.calibration}")
        else:
            calibration_lut = _cal.get("correction_lut")
            if calibration_lut:
                print(f"Loaded legacy calibration LUT from {_args.calibration}")
            else:
                print(f"WARNING: No calibration data found in {_args.calibration}")
    print(f"ELK-BLEDOM Dashboard starting on http://0.0.0.0:{_args.port}")
    print("Open in browser to control LED strips.")
    uvicorn.run(app, host="0.0.0.0", port=_args.port, log_level="info")
