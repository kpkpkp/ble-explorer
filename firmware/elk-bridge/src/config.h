#pragma once

// --- Pin assignments ---
#define STATUS_LED_PIN          8

// --- BLE settings ---
#define BLE_MAX_STRIPS          3
#define BLE_RECONNECT_INTERVAL_MS  5000
#define BLE_CONNECT_TIMEOUT_MS  8000
#define BLE_MAX_RECONNECT_RETRIES  255
#define BLE_SCAN_DURATION_S     5

// --- WiFi settings ---
#define WIFI_TIMEOUT_MS         15000
#define WIFI_CHECK_INTERVAL_MS  10000
#define WIFI_MAX_RECONNECT_FAILURES 5

// --- Command reliability ---
#define COMMAND_RETRY_COUNT     2
#define COMMAND_RETRY_DELAY_MS  50

// --- Pattern engine ---
#define PATTERN_MAX_STEPS       32
#define FADE_INTERVAL_MS        50

// --- Device identification ---
#define DEVICE_NAME_PREFIX      "ELK-BLEDOM"
#define FIRMWARE_VERSION        "1.2.0"
#define NVS_NAMESPACE           "elk-bridge"
#define BRIDGE_BLE_NAME         "ESP32-StringLights"
#define BRIDGE_MDNS_NAME        "esp32-stringlights"
#define BRIDGE_AP_PREFIX        "StringLights-"

// --- HTTP server ---
#define HTTP_PORT               80

// --- UDP discovery beacon ---
#define BEACON_PORT             5555
#define BEACON_INTERVAL_MS      5000
