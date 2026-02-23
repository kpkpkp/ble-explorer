/*
 * ELK Bridge — WiFi-to-BLE bridge for ELK-BLEDOM LED strips
 *
 * Target: ESP32-C3 SuperMini (Lolin C3 Mini)
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
String            bootStrip2;  // deferred strip2 connect address
uint32_t          lastHeapLog = 0;

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

    // --- Pattern engine (needs NVS reference) ---
    patternEngine.begin(prefs);

    // --- HTTP server (start BEFORE BLE connects to stay reachable) ---
    httpServer.begin();

    // --- Restore saved pattern from NVS (after HTTP so bridge is reachable) ---
    patternEngine.loadFromNVS(bleManager);

    // --- Deferred BLE connect (non-blocking, runs in loop()) ---
    if (strip1.length() > 0) {
        Serial.printf("[Boot] Queuing strip 1 for async connect: %s\n", strip1.c_str());
        bleManager.requestConnect(strip1.c_str());
    }
    if (strip2.length() > 0) {
        bootStrip2 = strip2;  // connect after strip1 finishes
    }

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

    // Save BLE address to NVS after async connect completes
    if (bleManager.connectState() == BleManager::ASYNC_DONE) {
        if (bleManager.connectSucceeded()) {
            const std::string& addr = bleManager.connectAddress();
            int idx = bleManager.findStrip(addr.c_str());
            if (idx >= 0) {
                String key = "strip" + String(idx + 1) + "_addr";
                prefs.putString(key.c_str(), addr.c_str());
                Serial.printf("[NVS] Saved %s as %s\n", addr.c_str(), key.c_str());
            }

            // Affirm strip state: power on so commands are accepted
            uint8_t powerOnCmd[9];
            buildPowerCmd(true, powerOnCmd);
            bleManager.write(powerOnCmd, addr.c_str());
            Serial.printf("[Boot] Sent power-on to %s\n", addr.c_str());

            // Re-kick saved pattern if one was loaded but strips weren't connected yet
            if (patternEngine.isPlaying()) {
                Serial.println("[Boot] Pattern already playing, strip will receive on next tick");
            } else {
                // Try restoring saved pattern now that a strip is connected
                patternEngine.loadFromNVS(bleManager);
            }
        }
        bleManager.clearConnectResult();

        // Queue strip2 after strip1 finishes
        if (bootStrip2.length() > 0) {
            Serial.printf("[Boot] Queuing strip 2 for async connect: %s\n", bootStrip2.c_str());
            bleManager.requestConnect(bootStrip2.c_str());
            bootStrip2 = "";
        }
    }

    // Periodic heap monitor (every 30s)
    uint32_t now = millis();
    if (now - lastHeapLog >= 30000) {
        lastHeapLog = now;
        Serial.printf("[Heap] free=%d min=%d strips=%d uptime=%lus\n",
                      ESP.getFreeHeap(), ESP.getMinFreeHeap(),
                      (int)bleManager.getStripCount(), now / 1000);
    }

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
