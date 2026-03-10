#pragma once

#include <Arduino.h>
#include <Preferences.h>

/**
 * Bridge-side color calibration — 2D LUT (hue × brightness).
 * Up to 120 points (12 hues × 10 brightness levels).
 * Stored as NVS blob.
 */

#define CAL_MAX_POINTS 120

struct CalPoint {
    uint16_t deg;       // hue degree 0-359
    uint8_t  brightness; // 10, 20, ... 100
    uint8_t  stdR, stdG, stdB;
    uint8_t  calR, calG, calB;
};

class ColorCal {
public:
    void begin(Preferences& prefs);

    /** Replace calibration data. Returns false if count > CAL_MAX_POINTS. */
    bool load(const CalPoint* pts, uint8_t count);

    /** Save current calibration to NVS. */
    void saveToNVS();

    /** Clear calibration (passthrough mode). */
    void clear();

    /** Apply calibration: maps (r, g, b) at given brightness to corrected values. */
    void apply(uint8_t r, uint8_t g, uint8_t b, uint8_t brightnessPercent,
               uint8_t& outR, uint8_t& outG, uint8_t& outB) const;

    bool    isLoaded() const { return _count > 0; }
    uint8_t getCount() const { return _count; }
    const CalPoint* getPoints() const { return _points; }

    bool enabled = true;

private:
    Preferences* _prefs = nullptr;
    CalPoint _points[CAL_MAX_POINTS];
    uint8_t  _count = 0;

    // Distinct brightness levels found in data
    uint8_t  _levels[10];
    uint8_t  _levelCount = 0;

    void rebuildLevels();

    void applyAtBrightness(uint8_t r, uint8_t g, uint8_t b, uint8_t brLevel,
                           float& outR, float& outG, float& outB) const;
};
