#include "color_cal.h"
#include <math.h>

void ColorCal::begin(Preferences& prefs) {
    _prefs = &prefs;

    // Load from NVS
    size_t blobSize = prefs.getBytesLength("cal_data");
    if (blobSize > 0 && blobSize <= sizeof(_points)) {
        prefs.getBytes("cal_data", _points, blobSize);
        _count = blobSize / sizeof(CalPoint);
        rebuildLevels();
        Serial.printf("[Cal] Loaded %d points from NVS (%d brightness levels)\n",
                      _count, _levelCount);
    } else {
        _count = 0;
        _levelCount = 0;
        Serial.println("[Cal] No calibration data in NVS");
    }
}

bool ColorCal::load(const CalPoint* pts, uint8_t count) {
    if (count > CAL_MAX_POINTS) return false;
    memcpy(_points, pts, count * sizeof(CalPoint));
    _count = count;
    rebuildLevels();
    Serial.printf("[Cal] Loaded %d points (%d brightness levels)\n", _count, _levelCount);
    return true;
}

void ColorCal::saveToNVS() {
    if (!_prefs) return;
    if (_count == 0) {
        _prefs->remove("cal_data");
        Serial.println("[Cal] Cleared NVS calibration");
    } else {
        _prefs->putBytes("cal_data", _points, _count * sizeof(CalPoint));
        Serial.printf("[Cal] Saved %d points to NVS (%d bytes)\n",
                      _count, _count * (int)sizeof(CalPoint));
    }
}

void ColorCal::clear() {
    _count = 0;
    _levelCount = 0;
    if (_prefs) _prefs->remove("cal_data");
    Serial.println("[Cal] Calibration cleared");
}

void ColorCal::rebuildLevels() {
    _levelCount = 0;
    for (uint8_t i = 0; i < _count; i++) {
        bool found = false;
        for (uint8_t j = 0; j < _levelCount; j++) {
            if (_levels[j] == _points[i].brightness) { found = true; break; }
        }
        if (!found && _levelCount < 10) {
            _levels[_levelCount++] = _points[i].brightness;
        }
    }
    // Sort levels ascending
    for (uint8_t i = 0; i < _levelCount; i++) {
        for (uint8_t j = i + 1; j < _levelCount; j++) {
            if (_levels[j] < _levels[i]) {
                uint8_t tmp = _levels[i];
                _levels[i] = _levels[j];
                _levels[j] = tmp;
            }
        }
    }
}

void ColorCal::apply(uint8_t r, uint8_t g, uint8_t b, uint8_t brightnessPercent,
                     uint8_t& outR, uint8_t& outG, uint8_t& outB) const {
    if (!enabled || _count == 0 || (r == 0 && g == 0 && b == 0)) {
        outR = r; outG = g; outB = b;
        return;
    }

    if (_levelCount <= 1) {
        // Single brightness level — just do hue interpolation
        float fr, fg, fb;
        applyAtBrightness(r, g, b, _levelCount > 0 ? _levels[0] : 100, fr, fg, fb);
        outR = (uint8_t)constrain((int)(fr + 0.5f), 0, 255);
        outG = (uint8_t)constrain((int)(fg + 0.5f), 0, 255);
        outB = (uint8_t)constrain((int)(fb + 0.5f), 0, 255);
        return;
    }

    // Clamp brightness to available range
    uint8_t bp = constrain(brightnessPercent, _levels[0], _levels[_levelCount - 1]);

    // Find bracketing brightness levels
    int loIdx = 0, hiIdx = _levelCount - 1;
    for (int i = 0; i < _levelCount; i++) {
        if (_levels[i] <= bp) loIdx = i;
        if (_levels[i] >= bp && hiIdx == _levelCount - 1) hiIdx = i;
    }
    // Fix hiIdx search
    hiIdx = _levelCount - 1;
    for (int i = 0; i < _levelCount; i++) {
        if (_levels[i] >= bp) { hiIdx = i; break; }
    }

    if (loIdx == hiIdx) {
        float fr, fg, fb;
        applyAtBrightness(r, g, b, _levels[loIdx], fr, fg, fb);
        outR = (uint8_t)constrain((int)(fr + 0.5f), 0, 255);
        outG = (uint8_t)constrain((int)(fg + 0.5f), 0, 255);
        outB = (uint8_t)constrain((int)(fb + 0.5f), 0, 255);
        return;
    }

    float t = (float)(bp - _levels[loIdx]) / (float)(_levels[hiIdx] - _levels[loIdx]);

    float loR, loG, loB, hiR, hiG, hiB;
    applyAtBrightness(r, g, b, _levels[loIdx], loR, loG, loB);
    applyAtBrightness(r, g, b, _levels[hiIdx], hiR, hiG, hiB);

    outR = (uint8_t)constrain((int)(loR * (1.0f - t) + hiR * t + 0.5f), 0, 255);
    outG = (uint8_t)constrain((int)(loG * (1.0f - t) + hiG * t + 0.5f), 0, 255);
    outB = (uint8_t)constrain((int)(loB * (1.0f - t) + hiB * t + 0.5f), 0, 255);
}

void ColorCal::applyAtBrightness(uint8_t r, uint8_t g, uint8_t b, uint8_t brLevel,
                                 float& outR, float& outG, float& outB) const {
    // Convert input to HSV for hue
    float rf = r / 255.0f, gf = g / 255.0f, bf = b / 255.0f;
    float maxC = fmaxf(rf, fmaxf(gf, bf));
    float minC = fminf(rf, fminf(gf, bf));
    float delta = maxC - minC;

    float hue = 0;
    if (delta > 0.001f) {
        if (maxC == rf) hue = 60.0f * fmodf((gf - bf) / delta + 6.0f, 6.0f);
        else if (maxC == gf) hue = 60.0f * ((bf - rf) / delta + 2.0f);
        else hue = 60.0f * ((rf - gf) / delta + 4.0f);
    }
    float sat = (maxC > 0.001f) ? delta / maxC : 0;

    // Collect points at this brightness level
    // Find bracketing hue points
    const CalPoint* lo = nullptr;
    const CalPoint* hi = nullptr;
    const CalPoint* first = nullptr;
    const CalPoint* last = nullptr;

    for (uint8_t i = 0; i < _count; i++) {
        if (_points[i].brightness != brLevel) continue;
        if (!first || _points[i].deg < first->deg) first = &_points[i];
        if (!last || _points[i].deg > last->deg) last = &_points[i];

        if (_points[i].deg <= (uint16_t)hue) {
            if (!lo || _points[i].deg > lo->deg) lo = &_points[i];
        }
        if (_points[i].deg >= (uint16_t)hue) {
            if (!hi || _points[i].deg < hi->deg) hi = &_points[i];
        }
    }

    if (!first) { outR = r; outG = g; outB = b; return; }

    // Wrap around
    if (!lo) lo = last;   // hue < first point, wrap to last
    if (!hi) hi = first;  // hue > last point, wrap to first

    // Compute scale factors
    auto scales = [](const CalPoint* pt, float sc[3]) {
        for (int i = 0; i < 3; i++) {
            uint8_t std = (i == 0) ? pt->stdR : (i == 1) ? pt->stdG : pt->stdB;
            uint8_t cal = (i == 0) ? pt->calR : (i == 1) ? pt->calG : pt->calB;
            sc[i] = (std > 0) ? (float)cal / (float)std : ((cal > 0) ? cal / 255.0f : 0);
        }
    };

    float loSc[3], hiSc[3];
    scales(lo, loSc);
    scales(hi, hiSc);

    // Interpolation factor
    float loDeg = lo->deg;
    float hiDeg = hi->deg;
    if (hiDeg <= loDeg) hiDeg += 360.0f;
    float span = hiDeg - loDeg;
    float t = 0;
    if (span > 0) {
        float d = hue - loDeg;
        if (d < 0) d += 360.0f;
        t = constrain(d / span, 0.0f, 1.0f);
    }

    float sc[3];
    for (int i = 0; i < 3; i++) {
        sc[i] = loSc[i] * (1.0f - t) + hiSc[i] * t;
    }

    // Hue-corrected path
    float hR = r * sc[0], hG = g * sc[1], hB = b * sc[2];

    // White path (desaturated)
    // Use a rough white correction: average of scale factors at this brightness
    float wAvg = (sc[0] + sc[1] + sc[2]) / 3.0f;
    float wR = r * wAvg, wG = g * wAvg, wB = b * wAvg;

    // Blend by saturation
    outR = hR * sat + wR * (1.0f - sat);
    outG = hG * sat + wG * (1.0f - sat);
    outB = hB * sat + wB * (1.0f - sat);
}
