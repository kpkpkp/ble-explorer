# ELK-BLEDOM Color Calibration — Lessons Learned

**Date:** 2026-02-08
**Session scope:** Visual and camera-assisted color calibration of ELK-BLEDOM LED strip (strip2) using P2XL OLED as reference display and P10 Pro as camera.

---

## 1. Night Light Will Ruin Everything

The P2XL had **Night Light enabled** (`night_display_activated = 1`), which applies a warm orange filter over the entire display. Every color we tried to match was shifted — cyan looked like "puce," green looked yellowish, and we spent significant time chasing our tails adjusting RGB values to compensate for a display filter we didn't know was on.

**Lesson:** Before using any phone as a color reference, disable all display color adjustments:
```bash
adb shell settings put secure night_display_activated 0
adb shell settings put secure accessibility_display_daltonizer_enabled 0
# Also check: adaptive brightness, color mode (natural/boosted), blue light filters
```

**Time wasted:** ~30 minutes of interactive color tuning with wrong reference.

## 2. Screenshot ≠ What the User Sees

Android's `screencap` captures the framebuffer pixel values *before* display processing. A screenshot of the P2XL showed perfect cyan (0, 255, 255) while the user was looking at a warm-shifted mess. The screenshot gave us false confidence that the reference was correct.

**Lesson:** Screenshots verify what the GPU is rendering, not what the display panel is outputting. Display filters (Night Light, color correction, color temperature) happen at the hardware/compositor level and are invisible to `screencap`. When a user says the display doesn't look right, believe them — check display settings rather than taking another screenshot.

## 3. Camera-Based Calibration Has Fundamental Limits

### Brightness mismatch
The P2XL OLED at any useful brightness vastly outshines the LED strip. Through the P10 camera, the reference region reads (165-190) per channel while the strip reads (5-100). This 2-3x brightness gap means:
- Absolute RGB matching is impossible — the algorithm maxes out strip channels trying to match reference brightness
- Even proportion-based matching struggles because the strip's dim readings have poor signal-to-noise

### Camera spectral bias ≠ human vision
The camera's Bayer filter and the human eye respond differently to narrow-band LED spectra:
- Camera sees the strip's white as **blue/cyan dominant** (R=69, G=103, B=126)
- User sees the strip's white as **pink/red dominant**
- These are contradictory — optimizing for the camera produces colors that look wrong to the eye

### Reference washes out in camera
At P2XL brightness high enough for colors to be visible to the user, the camera sees the reference as nearly white. The proportion data for the reference clusters around (0.32, 0.34, 0.34) regardless of the actual color being displayed — losing all hue information.

**Lesson:** Camera-in-the-loop calibration works best for relative comparisons (is channel X going up or down?) but cannot be trusted for absolute color matching against an OLED display. The spectral characteristics are too different.

## 4. The Strip's Color Space Is Tiny

The final calibration map reveals just how compressed the strip's usable range is:

| Standard Color | Standard RGB | Strip Input |
|---------------|-------------|-------------|
| Red | (255, 0, 0) | (20, 7, 3) |
| Orange | (255, 127, 0) | (107, 27, 9) |
| Yellow | (255, 255, 0) | (101, 27, 11) |
| Chartreuse | (127, 255, 0) | (40, 23, 5) |
| Green | (0, 255, 0) | (13, 6, 3) |
| Cyan | (0, 255, 255) | (0, 50, 25) |
| Blue | (0, 0, 255) | (0, 0, 25) |
| Violet | (127, 0, 255) | (35, 13, 7) |
| Magenta | (255, 0, 255) | (54, 15, 7) |

Key observations:
- **Red is king:** When red is in the standard color, the strip's red input dominates (20-107). When red is absent, strip values are tiny.
- **Green and blue are extremely compressed:** Most values are 3-27. The strip's G/B LEDs overpower at low inputs.
- **Orange vs Yellow:** Visually confirmed as nearly identical on the strip — (255,20,0) vs (255,21,0). Only 1 unit of green separates them.
- **White is (75, 40, 15):** Massively reduced from (255, 255, 255). Red still needs to be 2-5x the other channels to appear balanced.

**Lesson:** These strips have roughly 5-6 bits of useful color resolution, not 8. Any color correction LUT will be mapping a large input space to a small output space, with many standard colors collapsing to nearly identical strip inputs.

## 5. BLE Reliability Requires Active Management

- Strip2 disconnects frequently and needs reconnection via `/api/connect`
- Commands timeout (~5-10s) but often still execute — the ack is slow, not the command
- Fire-and-forget HTTP loses commands silently; semi-synchronous with 5s timeout + retry is minimum
- Targeting "both" strips causes strip1's slow ack to block strip2

**Lesson:** Always check connection status before operations. Use targeted commands (`"targets": "strip2"`). Build retry logic into any automated sweep. A "TIMEOUT" status doesn't mean the command failed — it means we don't know.

## 6. Closed-Loop Matching Works, With Caveats

The proportion-based closed-loop algorithm (`_auto_match.py`) converged for 11 of 12 hues:
- Compares `R/(R+G+B)` proportions between strip and reference regions
- Adjusts strip inputs to shift proportions toward reference
- Dampened adjustments (50% of computed change) prevent oscillation
- Tolerance of 0.04 max proportion difference

**What worked well:**
- Fast convergence for red-dominant colors (2-5 iterations)
- Blue matched on first try (strip at low values naturally matches the dim blue reference proportions)

**What struggled:**
- Cyan (0.123 error) — the strip's G+B glow always reads as ~(0.04, 0.48, 0.48) proportions, far from the reference's ~(0.31, 0.35, 0.35). The algorithm adds red to compensate, which defeats the purpose.
- Green and spring — similar issue where the strip's green LED dominates proportions

**Lesson:** Proportion matching converges to the camera's version of "correct," which may differ from human-perceived color. The most reliable results come from colors where the strip and reference are in similar brightness ranges.

## 7. Reference Display Brightness Is Critical

We went through multiple brightness levels on the P2XL:
- **128 (50%):** Way too bright — washes out camera, overwhelms strip visually
- **80 (31%):** Still too bright for blues/purples through camera
- **64 (25%):** Reasonable for bright colors, too bright for darks
- **45 (18%):** Good visual match for cyan
- **32 (12%):** Better for camera comparison
- **15 (6%):** Colors barely visible but closer to strip brightness

There is no single correct brightness. Different colors need different reference brightness to match the strip's output, because the strip's brightness varies wildly by color (red is bright, blue is dim).

**Lesson:** Consider per-color reference brightness adjustment, or accept that visual comparison requires the user to mentally compensate for brightness differences. Alternatively, use the camera only for hue direction and rely on user judgment for the final call.

## 8. Visual Calibration > Camera Calibration for These LEDs

The three colors calibrated by direct user feedback in the prior session were more reliable than the camera-matched values:

| Color | User-visual | Camera-matched |
|-------|------------|----------------|
| Orange | (255, 20, 0) | (107, 27, 9) |
| Yellow | (255, 21, 0) | (101, 27, 11) |
| White | (75, 40, 15) | — |
| Cyan | (0, 25, 40) [pre-Night Light fix] | (20, 17, 8) |

The camera-matched orange and yellow have much lower red values than what the user visually confirmed. This reflects the camera's red-insensitivity to the strip's narrow-band red LEDs.

**Lesson:** For consumer LED strips with narrow spectral peaks, human visual tuning (with a known-good reference display) is more reliable than camera-based matching. The camera should assist (confirming trends, detecting spikes) but not dictate final values.

## 9. Process Recommendations for Next Time

1. **Pre-flight checks:** Night Light off, auto-brightness off, display color mode set to "Natural," connection to both strips verified
2. **Set reference brightness per-color:** Dim for bright colors (red, white), brighter for dark colors (blue, violet)
3. **Use camera for coarse direction, user for fine tuning:** Camera says "too much blue" → reduce B → user says "that's it"
4. **Save known-good values incrementally:** Don't rely on one final sweep — save each confirmed color immediately
5. **Test with Night Light toggled:** If the user will view strips in warm ambient light, the calibration should account for that context
6. **Accept the hardware limits:** These strips can produce maybe 50-100 distinguishable colors, not 16 million. Design the UI/experience accordingly.

---

## Appendix: Scripts Created

| Script | Purpose |
|--------|---------|
| `_find_cyan.py` | Sweep G/B for cyan matching |
| `_find_green.py` | Smooth transition from cyan to green |
| `_bounce_cyan.py` | A/B comparison of cyan candidates |
| `_set_color.py` | Set strip to a single color |
| `_compare_photo.py` | Take photo, report strip vs reference RGB |
| `_tune_cyan.py` | Fine-tune cyan with camera feedback |
| `_color_wheel.py` | 12-hue sweep with estimates (open-loop) |
| `_auto_match.py` | Closed-loop hue matching via camera proportions |
