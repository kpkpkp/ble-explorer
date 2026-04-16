# ble-explorer

A toolkit for reverse-engineering BLE (Bluetooth Low Energy) protocols on consumer display devices — smart badges, LED name tags, and similar gadgets.

## Overview

ble-explorer started as a project to understand and control consumer BLE display devices programmatically. Rather than relying on vendor apps with limited functionality, this toolkit provides full protocol analysis and control through an ESP32-S3 WiFi-to-BLE bridge architecture.

## Architecture

```
┌──────────────┐     WiFi/HTTP     ┌──────────────────┐     BLE GATT     ┌─────────────────┐
│  Android App │ ◄──────────────► │   ESP32-S3        │ ◄──────────────► │  Display Device  │
│  (Kotlin)    │                   │   Bridge Firmware │                   │  (target)        │
├──────────────┤                   ├──────────────────┤                   └─────────────────┘
│  Python      │                   │  NimBLE stack    │
│  Scripts     │                   │  HTTP server     │
└──────────────┘                   │  OTA support     │
                                   └──────────────────┘
```

The ESP32-S3 acts as a bridge: it connects to BLE display devices and exposes control via WiFi/HTTP, enabling programmatic interaction and protocol analysis from any device on the network.

## Components

| Directory | Description |
|-----------|-------------|
| `firmware/` | ESP32-S3 bridge firmware (C/C++, ESP-IDF, NimBLE) |
| `android/` | Android companion app (Kotlin) |
| `scripts/` | Python analysis and automation scripts |
| `docs/` | Protocol documentation and findings |
| `specs/` | Architecture specs and feature plans |

## Key Features

- **WiFi-to-BLE bridge** — control BLE devices over HTTP from any network client
- **Power management** — toggle device power, preserve display patterns across power cycles
- **OTA firmware updates** — flash new firmware to the bridge over WiFi
- **Preset system** — save and recall display configurations (e.g., named presets)
- **Scheduled operations** — timed restore and pattern cycling
- **Web dashboard** — browser-based control interface

## Tech Stack

- **Kotlin** — Android companion app
- **Python** — Protocol analysis, automation scripts
- **C/C++** — ESP32-S3 firmware
- **ESP-IDF** — Espressif IoT Development Framework
- **NimBLE** — BLE stack for ESP32
- **BLE GATT** — Generic Attribute Profile for device communication

## Part of WitnessMark

This project is part of the broader [WitnessMark](https://github.com/kpkpkp) ecosystem — a multi-platform IoT system for cryptographically verified location and time attestation. The BLE protocol knowledge developed here directly informs the WitnessMark reflector firmware.
