#!/usr/bin/env python3
"""ELK-BLEDOM LED Dashboard — FastAPI backend with BLE control and pattern engine.

Run from Windows Python (not WSL2 — needs Bluetooth hardware):
    pip install fastapi uvicorn[standard]
    python scripts/elk_dashboard.py

Then open http://localhost:8080 in a browser.
"""

import asyncio
import json
import logging
import os
import sys
import time
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.responses import HTMLResponse, JSONResponse
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
            {"color": "#F75F1C", "brightness": 100, "duration": 2.0, "transition": "cut", "fade_time": 0.5},
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

    @property
    def connected(self) -> dict[str, bool]:
        return {
            name: (mac in self._clients and self._clients[mac].is_connected)
            for name, mac in STRIPS.items()
        }

    async def connect(self, targets: list[str]):
        from bleak import BleakClient
        async with self._lock:
            for name in targets:
                mac = STRIPS.get(name)
                if not mac:
                    continue
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

    async def disconnect(self, targets: list[str]):
        async with self._lock:
            for name in targets:
                mac = STRIPS.get(name)
                if not mac:
                    continue
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


async def broadcast(msg: dict):
    dead = set()
    for ws in ws_clients:
        try:
            await ws.send_json(msg)
        except Exception:
            dead.add(ws)
    ws_clients.difference_update(dead)


pattern_engine = PatternEngine(ble_mgr, broadcast)
presets = load_presets()


async def broadcast_connection():
    await broadcast({"type": "connection", **ble_mgr.connected})


# --- HTML ---
@app.get("/", response_class=HTMLResponse)
async def index():
    html_path = Path(__file__).parent / "elk_dashboard.html"
    return HTMLResponse(html_path.read_text())


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


@app.post("/api/color")
async def api_color(body: dict):
    targets = resolve_targets(body)
    r, g, b = body["r"], body["g"], body["b"]
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
    _args = _p.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(message)s")
    print(f"ELK-BLEDOM Dashboard starting on http://0.0.0.0:{_args.port}")
    print("Open in browser to control LED strips.")
    uvicorn.run(app, host="0.0.0.0", port=_args.port, log_level="info")
