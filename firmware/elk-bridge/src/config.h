#pragma once

// --- Pin assignments ---
#define STATUS_LED_PIN          8

// --- BLE settings ---
#define BLE_MAX_STRIPS          6
#define BLE_RECONNECT_INTERVAL_MS  5000
#define BLE_CONNECT_TIMEOUT_MS  15000
#define BLE_MAX_RECONNECT_RETRIES  3
#define BLE_SCAN_DURATION_S     5

// --- WiFi settings ---
#define WIFI_TIMEOUT_MS         10000
#define WIFI_CHECK_INTERVAL_MS  5000
#define WIFI_MAX_RECONNECT_FAILURES 3

// --- Command reliability ---
#define COMMAND_RETRY_COUNT     2
#define COMMAND_RETRY_DELAY_MS  50

// --- Pattern engine ---
#define PATTERN_MAX_STEPS       32
#define FADE_INTERVAL_MS        33

// --- Device identification ---
#define DEVICE_NAME_PREFIX      "ELK-BLEDOM"
#define FIRMWARE_VERSION        "1.0.0"
#define NVS_NAMESPACE           "elk-bridge"
#define BRIDGE_BLE_NAME         "ELK-Bridge"
#define BRIDGE_MDNS_NAME        "elk-bridge"
#define BRIDGE_AP_PREFIX        "ELK-Bridge-"

// --- HTTP server ---
#define HTTP_PORT               80

// --- UDP discovery beacon ---
#define BEACON_PORT             5555
#define BEACON_INTERVAL_MS      5000
