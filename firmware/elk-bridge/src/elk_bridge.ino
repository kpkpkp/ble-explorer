/*
 * ELK Bridge — WiFi-to-BLE bridge for ELK-BLEDOM LED strips
 *
 * Target: ESP32-S3 SuperMini (Lolin S3 Mini)
 * Framework: Arduino + NimBLE + ESPAsyncWebServer
 *
 * Connects to WiFi, discovers and connects to ELK-BLEDOM BLE LED strips,
 * and exposes an HTTP JSON API for color/brightness/effect/pattern control.
 */

#include <Preferences.h>

#include "config.h"
#include "wifi_manager.h"
#include "ble_manager.h"
#include "pattern_engine.h"
#include "command_dispatch.h"
#include "http_server.h"
#include "status_led.h"

// --- Global instances ---
WiFiManager       wifiManager;
BleManager        bleManager;
PatternEngine     patternEngine;
CommandDispatcher dispatcher(bleManager, patternEngine);
Preferences       prefs;
HttpServer        httpServer(wifiManager, bleManager, dispatcher, patternEngine, prefs);
StatusLed         statusLed;

void setup() {
    Serial.begin(115200);
    delay(500); // Let USB CDC settle
    Serial.println();
    Serial.println("================================");
    Serial.println("  ELK Bridge v" FIRMWARE_VERSION);
    Serial.println("================================");

    // --- Status LED ---
    statusLed.begin(STATUS_LED_PIN);
    statusLed.setPattern(StatusLed::FAST_BLINK);

    // --- Load config from NVS ---
    prefs.begin(NVS_NAMESPACE, false);
    String ssid    = prefs.getString("wifi_ssid", "");
    String pass    = prefs.getString("wifi_pass", "");
    String strip1  = prefs.getString("strip1_addr", "");
    String strip2  = prefs.getString("strip2_addr", "");

    Serial.printf("[NVS] WiFi SSID: '%s'\n", ssid.c_str());
    Serial.printf("[NVS] Strip 1: '%s'\n", strip1.c_str());
    Serial.printf("[NVS] Strip 2: '%s'\n", strip2.c_str());

    // --- WiFi ---
    wifiManager.begin(ssid.c_str(), pass.c_str());

    if (wifiManager.isConnected()) {
        statusLed.setPattern(StatusLed::SLOW_BLINK);
    } else {
        statusLed.setPattern(StatusLed::DOUBLE_BLINK);
    }

    // --- BLE ---
    bleManager.init();

    if (strip1.length() > 0) {
        Serial.printf("[Boot] Connecting to strip 1: %s\n", strip1.c_str());
        bleManager.connect(strip1.c_str());
    }
    if (strip2.length() > 0) {
        Serial.printf("[Boot] Connecting to strip 2: %s\n", strip2.c_str());
        bleManager.connect(strip2.c_str());
    }

    if (bleManager.isAnyConnected()) {
        statusLed.setPattern(StatusLed::SOLID);
    }

    // --- HTTP server ---
    httpServer.begin();

    Serial.println();
    Serial.printf("ELK Bridge ready at http://%s/\n", wifiManager.getIP().c_str());
    Serial.printf("mDNS: http://%s.local/\n", BRIDGE_MDNS_NAME);
    Serial.printf("Free heap: %d bytes\n", ESP.getFreeHeap());
    Serial.println("================================");
}

void loop() {
    wifiManager.loop();
    patternEngine.tick(bleManager);
    bleManager.loop();

    // Update LED based on current state
    updateStatusLed();

    statusLed.tick();
    yield();
}

void updateStatusLed() {
    // Pattern playing takes priority
    if (patternEngine.isPlaying()) {
        if (statusLed.getPattern() != StatusLed::PULSE) {
            statusLed.setPattern(StatusLed::PULSE);
        }
        return;
    }

    // Then check connection states
    if (bleManager.isAnyConnected() && wifiManager.isConnected()) {
        if (statusLed.getPattern() != StatusLed::SOLID) {
            statusLed.setPattern(StatusLed::SOLID);
        }
    } else if (wifiManager.isConnected()) {
        if (statusLed.getPattern() != StatusLed::SLOW_BLINK) {
            statusLed.setPattern(StatusLed::SLOW_BLINK);
        }
    } else if (wifiManager.getState() == WiFiManager::AP_MODE) {
        if (statusLed.getPattern() != StatusLed::DOUBLE_BLINK) {
            statusLed.setPattern(StatusLed::DOUBLE_BLINK);
        }
    } else if (wifiManager.getState() == WiFiManager::CONNECTING) {
        if (statusLed.getPattern() != StatusLed::FAST_BLINK) {
            statusLed.setPattern(StatusLed::FAST_BLINK);
        }
    }
}
