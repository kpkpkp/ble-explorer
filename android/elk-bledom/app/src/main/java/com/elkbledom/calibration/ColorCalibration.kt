package com.elkbledom.calibration

import android.content.Context
import android.graphics.Color
import com.elkbledom.R
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Calibration point from the 12-hue visual calibration map.
 * Each point maps a standard RGB color at a given hue degree
 * to the strip input values that produce the visually closest match.
 */
data class CalibrationPoint(
    val deg: Float,
    val standard: IntArray,
    val stripInput: IntArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CalibrationPoint) return false
        return deg == other.deg &&
            standard.contentEquals(other.standard) &&
            stripInput.contentEquals(other.stripInput)
    }

    override fun hashCode(): Int {
        var result = deg.hashCode()
        result = 31 * result + standard.contentHashCode()
        result = 31 * result + stripInput.contentHashCode()
        return result
    }
}

/**
 * Visual color calibration system for ELK-BLEDOM LED strips.
 *
 * Maps standard RGB values to corrected strip RGB values using a 12-point
 * hue calibration map loaded from res/raw/visual_calibration.json. The algorithm
 * converts input to HSV, interpolates scale factors between the two nearest
 * calibration hue points, and blends between hue-based and white-based correction
 * according to saturation.
 *
 * Port of Python `_apply_visual_calibration` from elk_dashboard.py.
 */
class ColorCalibration(context: Context) {

    /** Toggle calibration on or off. When disabled, [apply] returns input unchanged. */
    var enabled: Boolean = true

    /** Calibrated white point — strip input values that produce perceptual white. */
    private val WHITE_STRIP = Triple(75, 40, 15)

    /** 12-point hue calibration map, sorted ascending by degree. */
    private val points: List<CalibrationPoint>

    init {
        val raw = context.resources.openRawResource(R.raw.visual_calibration)
            .bufferedReader()
            .use { it.readText() }
        val json = JSONObject(raw)
        val list = mutableListOf<CalibrationPoint>()
        for (name in json.keys()) {
            val entry = json.getJSONObject(name)
            val deg = entry.getDouble("deg").toFloat()
            val stdArr = entry.getJSONArray("standard")
            val stripArr = entry.getJSONArray("strip_input")
            list.add(
                CalibrationPoint(
                    deg = deg,
                    standard = IntArray(3) { stdArr.getInt(it) },
                    stripInput = IntArray(3) { stripArr.getInt(it) },
                )
            )
        }
        list.sortBy { it.deg }
        points = list
    }

    /**
     * Map standard RGB to corrected strip RGB.
     *
     * @param r Red channel 0-255
     * @param g Green channel 0-255
     * @param b Blue channel 0-255
     * @return Corrected (r, g, b) triple, each channel clamped to 0-255
     */
    fun apply(r: Int, g: Int, b: Int): Triple<Int, Int, Int> {
        if (!enabled) return Triple(r, g, b)
        if (r == 0 && g == 0 && b == 0) return Triple(0, 0, 0)

        // Convert to HSV using Android's Color utility.
        // Color.RGBToHSV produces hsv[0] in 0..360, hsv[1] and hsv[2] in 0..1.
        val hsv = FloatArray(3)
        Color.RGBToHSV(r, g, b, hsv)
        val hueDeg = hsv[0]  // 0..360
        val s = hsv[1]       // 0..1

        // Find the two bracketing calibration points by hue.
        val pts = points
        var lo = pts.last()   // wrap: last point (330) is "below" 0
        var hi = pts.first()  // wrap: first point (0) is "above" 330
        for (i in pts.indices) {
            if (pts[i].deg <= hueDeg) {
                lo = pts[i]
                hi = pts[(i + 1) % pts.size]
            } else {
                break
            }
        }

        // Interpolation factor between lo and hi.
        var loDeg = lo.deg
        var hiDeg = hi.deg
        if (hiDeg <= loDeg) {
            hiDeg += 360f  // wrap around
        }
        val span = hiDeg - loDeg
        val t: Float
        if (span == 0f) {
            t = 0f
        } else {
            var d = hueDeg - loDeg
            if (d < 0f) d += 360f
            t = (d / span).coerceIn(0f, 1f)
        }

        // Compute per-channel scale factors from a calibration point.
        fun scales(pt: CalibrationPoint): FloatArray {
            val sc = FloatArray(3)
            for (i in 0..2) {
                sc[i] = if (pt.standard[i] > 0) {
                    pt.stripInput[i].toFloat() / pt.standard[i].toFloat()
                } else {
                    if (pt.stripInput[i] > 0) pt.stripInput[i].toFloat() / 255f else 0f
                }
            }
            return sc
        }

        val loSc = scales(lo)
        val hiSc = scales(hi)

        // Lerp scale factors.
        val sc = FloatArray(3) { loSc[it] * (1f - t) + hiSc[it] * t }

        // Apply hue-based correction (full saturation path).
        val hueR = r * sc[0]
        val hueG = g * sc[1]
        val hueB = b * sc[2]

        // Apply white correction (desaturated path).
        val whiteR = r * WHITE_STRIP.first / 255f
        val whiteG = g * WHITE_STRIP.second / 255f
        val whiteB = b * WHITE_STRIP.third / 255f

        // Blend between hue correction and white correction based on saturation.
        val outR = hueR * s + whiteR * (1f - s)
        val outG = hueG * s + whiteG * (1f - s)
        val outB = hueB * s + whiteB * (1f - s)

        return Triple(
            outR.roundToInt().coerceIn(0, 255),
            outG.roundToInt().coerceIn(0, 255),
            outB.roundToInt().coerceIn(0, 255),
        )
    }
}
