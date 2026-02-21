#!/bin/bash
# Build and OTA-deploy ELK Bridge firmware
#
# Usage:
#   ./scripts/deploy.sh                    # auto-discover via UDP beacon
#   ./scripts/deploy.sh 192.168.86.22      # explicit IP
#   ./scripts/deploy.sh elk-bridge.local   # mDNS name
#   BUILD_ONLY=1 ./scripts/deploy.sh       # just build, don't flash

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
FW_DIR="$PROJECT_DIR/firmware/elk-bridge"
VENV_PIO="$PROJECT_DIR/.venv/bin/pio"
BUILD_DIR="$FW_DIR/.pio/build/esp32c3"
FW_BIN="$BUILD_DIR/firmware.bin"

# --- Build ---
echo "=== Building firmware ==="
"$VENV_PIO" run -d "$FW_DIR" 2>&1 | tail -5

if [ ! -f "$FW_BIN" ]; then
    echo "ERROR: firmware.bin not found at $FW_BIN"
    exit 1
fi

FW_SIZE=$(stat -c%s "$FW_BIN" 2>/dev/null || stat -f%z "$FW_BIN" 2>/dev/null)
echo "Firmware: $FW_BIN ($FW_SIZE bytes)"

if [ "${BUILD_ONLY:-0}" = "1" ]; then
    echo "BUILD_ONLY set, skipping deploy."
    exit 0
fi

# --- Find bridge ---
BRIDGE_HOST="${1:-}"

if [ -z "$BRIDGE_HOST" ]; then
    echo ""
    echo "=== Discovering bridge ==="
    # Try mDNS first
    if BRIDGE_HOST=$(getent hosts elk-bridge.local 2>/dev/null | awk '{print $1}'); then
        if [ -n "$BRIDGE_HOST" ]; then
            echo "Found via mDNS: $BRIDGE_HOST"
        fi
    fi

    # Fall back to checking known status endpoint
    if [ -z "$BRIDGE_HOST" ]; then
        echo "mDNS failed, trying curl probe on recent IPs..."
        for ip in 192.168.86.22 192.168.86.21 192.168.86.23 192.168.86.24 192.168.86.25 192.168.86.26; do
            if curl -s --connect-timeout 1 "http://$ip/status" 2>/dev/null | grep -q '"ok":true'; then
                BRIDGE_HOST="$ip"
                echo "Found bridge at $BRIDGE_HOST"
                break
            fi
        done
    fi

    if [ -z "$BRIDGE_HOST" ]; then
        echo "ERROR: Could not find bridge. Pass IP as argument:"
        echo "  $0 192.168.86.22"
        exit 1
    fi
fi

# --- Verify bridge is reachable ---
echo ""
echo "=== Checking bridge at $BRIDGE_HOST ==="
STATUS=$(curl -s --connect-timeout 3 "http://$BRIDGE_HOST/status" 2>/dev/null)
if [ -z "$STATUS" ]; then
    echo "ERROR: Bridge not responding at http://$BRIDGE_HOST/status"
    exit 1
fi

CURRENT_VER=$(echo "$STATUS" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('firmware','?'))" 2>/dev/null || echo "?")
UPTIME=$(echo "$STATUS" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('uptime','?'))" 2>/dev/null || echo "?")
HEAP=$(echo "$STATUS" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('freeHeap','?'))" 2>/dev/null || echo "?")
echo "Current firmware: v$CURRENT_VER  uptime: ${UPTIME}s  heap: $HEAP"

# --- Upload ---
echo ""
echo "=== Uploading firmware ($FW_SIZE bytes) ==="
RESPONSE=$(curl -s --connect-timeout 5 --max-time 60 \
    -F "firmware=@$FW_BIN" \
    "http://$BRIDGE_HOST/update" 2>&1)

if echo "$RESPONSE" | grep -q '"ok":true'; then
    echo "Upload successful! Bridge is rebooting..."
    echo ""
    echo "Waiting for bridge to come back..."
    sleep 5
    for i in $(seq 1 12); do
        if curl -s --connect-timeout 2 "http://$BRIDGE_HOST/status" 2>/dev/null | grep -q '"ok":true'; then
            NEW_STATUS=$(curl -s "http://$BRIDGE_HOST/status")
            NEW_VER=$(echo "$NEW_STATUS" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('firmware','?'))" 2>/dev/null || echo "?")
            NEW_HEAP=$(echo "$NEW_STATUS" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('freeHeap','?'))" 2>/dev/null || echo "?")
            echo "Bridge is back! firmware=v$NEW_VER heap=$NEW_HEAP"
            exit 0
        fi
        echo -n "."
        sleep 2
    done
    echo ""
    echo "WARNING: Bridge did not come back within 30s. Check manually."
    exit 1
else
    echo "ERROR: Upload failed"
    echo "Response: $RESPONSE"
    exit 1
fi
