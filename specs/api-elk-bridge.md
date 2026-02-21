# ELK Bridge — HTTP API Contract v1

Shared contract between ESP32-S3 bridge firmware and Android/web clients.

**Base URL**: `http://elk-bridge.local` (mDNS) or `http://<ip>`
**Port**: 80
**Content-Type**: `application/json` (all POST bodies and responses)
**CORS**: `Access-Control-Allow-Origin: *`

## Response Format

All endpoints return:
```json
// Success
{ "ok": true, "data": { ... } }

// Error
{ "ok": false, "error": "human-readable message" }
```

## Discovery

- **mDNS hostname**: `elk-bridge.local`
- **mDNS service**: `_elk._tcp` port 80
- **AP fallback**: `ELK-Bridge-XXXX` (open), captive portal at `192.168.4.1`

---

## Endpoints

### `GET /status`

System status. Poll this to track bridge health and playback state.

**Response**:
```json
{
  "ok": true,
  "data": {
    "wifi": {
      "state": "connected",
      "ssid": "MyNetwork",
      "ip": "192.168.86.100",
      "rssi": -45
    },
    "ble": {
      "strips": [
        {
          "address": "BE:67:00:35:04:9B",
          "slot": "strip1",
          "state": "ready",
          "rssi": -55
        },
        {
          "address": "BE:67:00:35:11:78",
          "slot": "strip2",
          "state": "ready",
          "rssi": -60
        }
      ]
    },
    "playback": {
      "state": "playing",
      "currentStep": 3,
      "totalSteps": 8,
      "loop": true
    },
    "firmware": {
      "version": "1.0.0",
      "board": "esp32s3",
      "uptime": 3600,
      "freeHeap": 280000
    }
  }
}
```

BLE strip states: `"disconnected"`, `"connecting"`, `"discovering"`, `"ready"`, `"lost"`
Playback states: `"idle"`, `"playing"`

---

### `POST /api/color`

Set RGB color on targeted strip(s). **Preempts** a running pattern.

**Request**:
```json
{
  "r": 255,
  "g": 0,
  "b": 0,
  "target": "all"
}
```

**Fields**:
| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `r` | int | yes | — | Red 0-255 |
| `g` | int | yes | — | Green 0-255 |
| `b` | int | yes | — | Blue 0-255 |
| `target` | string | no | `"all"` | `"all"`, `"strip1"`, `"strip2"`, or MAC address |

**Response**: `{ "ok": true }`

---

### `POST /api/brightness`

Set brightness level. **Preempts** a running pattern.

**Request**:
```json
{
  "level": 80,
  "target": "all"
}
```

**Fields**:
| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `level` | int | yes | — | Brightness 0-100 (mapped to 0-255 for protocol) |
| `target` | string | no | `"all"` | Target selector |

**Response**: `{ "ok": true }`

---

### `POST /api/power`

Power on/off. **Preempts** a running pattern.

**Request**:
```json
{
  "on": true,
  "target": "all"
}
```

**Fields**:
| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `on` | bool | yes | — | `true` = power on, `false` = power off |
| `target` | string | no | `"all"` | Target selector |

**Response**: `{ "ok": true }`

---

### `POST /api/effect`

Trigger a built-in effect. **Preempts** a running pattern.

**Request**:
```json
{
  "code": 131,
  "speed": 3,
  "target": "all"
}
```

**Fields**:
| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `code` | int | yes | — | Effect code 0x80-0x9A (128-154) |
| `speed` | int | no | 3 | Speed 1-10 |
| `target` | string | no | `"all"` | Target selector |

**Response**: `{ "ok": true }`

---

### `POST /api/raw`

Send a raw 9-byte ELK-BLEDOM command. **Preempts** a running pattern.

**Request**:
```json
{
  "hex": "7E0005030000FF00EF",
  "target": "all"
}
```

**Fields**:
| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `hex` | string | yes | — | 18-character hex string (9 bytes) |
| `target` | string | no | `"all"` | Target selector |

**Response**: `{ "ok": true }`

---

### `POST /api/pattern`

Start pattern playback on the bridge. The bridge runs the pattern autonomously
(survives WiFi drops). Replaces any currently running pattern.

**Request**:
```json
{
  "steps": [
    {
      "r": 255,
      "g": 0,
      "b": 0,
      "brightness": 100,
      "duration": 2000,
      "transition": "cut",
      "fadeTime": 0
    },
    {
      "r": 0,
      "g": 255,
      "b": 0,
      "brightness": 80,
      "duration": 2000,
      "transition": "fade",
      "fadeTime": 500
    }
  ],
  "loop": true,
  "target": "all"
}
```

**Step fields**:
| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `r` | int | yes | — | Red 0-255 |
| `g` | int | yes | — | Green 0-255 |
| `b` | int | yes | — | Blue 0-255 |
| `brightness` | int | no | 100 | Brightness 0-100 |
| `duration` | int | yes | — | Hold time in milliseconds |
| `transition` | string | no | `"cut"` | `"cut"` or `"fade"` |
| `fadeTime` | int | no | 0 | Fade duration in milliseconds (only for `"fade"`) |

**Pattern fields**:
| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `steps` | array | yes | — | Array of step objects (max 32) |
| `loop` | bool | no | `false` | Repeat pattern after last step |
| `target` | string | no | `"all"` | Target selector |

**Response**: `{ "ok": true }`

---

### `POST /api/stop`

Stop pattern playback.

**Request**:
```json
{
  "target": "all"
}
```

**Response**: `{ "ok": true }`

---

### `GET /api/scan`

Scan for ELK-BLEDOM devices nearby. Scan runs for 5 seconds.

**Response**:
```json
{
  "ok": true,
  "data": {
    "devices": [
      {
        "name": "ELK-BLEDOM0B",
        "address": "BE:67:00:35:04:9B",
        "rssi": -55
      },
      {
        "name": "ELK-BLEDOM",
        "address": "BE:67:00:35:11:78",
        "rssi": -60
      }
    ]
  }
}
```

---

### `POST /api/connect`

Connect to an ELK-BLEDOM device by MAC address.

**Request**:
```json
{
  "address": "BE:67:00:35:04:9B"
}
```

**Response**: `{ "ok": true }`

Connection proceeds asynchronously. Poll `GET /status` to track progress.

---

### `POST /api/disconnect`

Disconnect from an ELK-BLEDOM device.

**Request**:
```json
{
  "address": "BE:67:00:35:04:9B"
}
```

**Response**: `{ "ok": true }`

---

### `GET /api/devices`

List connected strips with detailed state.

**Response**:
```json
{
  "ok": true,
  "data": {
    "devices": [
      {
        "address": "BE:67:00:35:04:9B",
        "name": "ELK-BLEDOM0B",
        "slot": "strip1",
        "state": "ready",
        "rssi": -55
      }
    ]
  }
}
```

---

### `POST /api/config`

Update WiFi credentials. Bridge reboots after saving.

**Request**:
```json
{
  "wifi_ssid": "MyNetwork",
  "wifi_pass": "MyPassword"
}
```

**Response**: `{ "ok": true }` (sent before reboot)

---

### `POST /update`

OTA firmware update. Accepts multipart form upload of a `.bin` file.

**Request**: `Content-Type: multipart/form-data`, field name `firmware`

**Response**: `{ "ok": true }` (sent before reboot)

---

### `GET /`

Serves embedded HTML dashboard (gzipped from `html.h`).

---

## Target Selector

The `target` field accepted by command endpoints supports:

| Value | Behavior |
|-------|----------|
| `"all"` | Send to all connected strips (default) |
| `"strip1"` | Send to first connected strip only |
| `"strip2"` | Send to second connected strip only |
| `"BE:67:..."` | Send to specific MAC address |

## Pattern Preemption

Any **direct command** (`/api/color`, `/api/brightness`, `/api/power`, `/api/effect`,
`/api/raw`) **stops** a running pattern before executing. This lets users override
a running pattern without explicitly calling `/api/stop` first.

The bridge sets pattern state to `"idle"` and sends the direct command immediately.

## Rate Limiting

The bridge does not enforce rate limits, but clients should throttle rapid commands
(e.g., color picker drag) to ~10 requests/second. Each BLE write takes ~100ms per
strip (2x write with 50ms gap), so commands faster than this will queue up.

## Error Codes

| Status | Meaning |
|--------|---------|
| 200 | Success |
| 400 | Bad request (missing/invalid fields) |
| 404 | Unknown endpoint |
| 500 | Internal error (BLE write failed, etc.) |
