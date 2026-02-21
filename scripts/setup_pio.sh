#!/bin/bash
# Setup PlatformIO in a local venv for building ESP32-S3 firmware
set -e

VENV_DIR="$(dirname "$0")/../.venv"

if [ ! -d "$VENV_DIR" ]; then
    echo "Creating venv at $VENV_DIR..."
    python3 -m venv "$VENV_DIR"
fi

echo "Installing PlatformIO..."
"$VENV_DIR/bin/pip" install platformio

echo "Done. PlatformIO at: $VENV_DIR/bin/pio"
"$VENV_DIR/bin/pio" --version
