package com.elkbledom.calibration

import android.content.Context
import android.graphics.Color
import com.elkbledom.R
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Calibration point from the hue × brightness calibration map.
 * Each point maps a standard RGB color at a given hue degree and brightness
 * to the strip input values that produce the visually closest match.
 */
data class CalibrationPoint(
    val deg: Float,
    val brightness: Int = 100,  // 10, 20, 30, ... 100
    val standard: IntArray,
    val stripInput: IntArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CalibrationPoint) return false
        return deg == other.deg &&
            brightness == other.brightness &&
            standard.contentEquals(other.standard) &&
            stripInput.contentEquals(other.stripInput)
    }

    override fun hashCode(): Int {
        var result = deg.hashCode()
        result = 31 * result + brightness
        result = 31 * result + standard.contentHashCode()
        result = 31 * result + stripInput.contentHashCode()
        return result
    }
}

/**
 * Visual color calibration system for ELK-BLEDOM LED strips.
 *
 * Maps standard RGB values to corrected strip RGB values using a
 * hue × brightness calibration map. The algorithm converts input to HSV,
 * interpolates scale factors between the two nearest calibration hue points
 * AND the two nearest brightness levels, and blends between hue-based and
 * white-based correction according to saturation.
 */
class ColorCalibration(private val context: Context) {

    /** Toggle calibration on or off. When disabled, [apply] returns input unchanged. */
    var enabled: Boolean = true

    /** Calibrated white point — strip input values that produce perceptual white. */
    private val WHITE_STRIP = Triple(75, 40, 15)

    private val prefs = context.getSharedPreferences("elk_calibration", Context.MODE_PRIVATE)

    /** Calibration points, sorted by (brightness, deg). */
    var points: List<CalibrationPoint>
        private set

    /** Whether custom calibration data is saved in SharedPreferences. */
    val isCustom: Boolean get() = prefs.contains("calibration_data")

    /** Distinct brightness levels present in the calibration data. */
    val brightnessLevels: List<Int> get() = points.map { it.brightness }.distinct().sorted()

    init {
        points = loadFromPrefs() ?: loadFromBundled()
    }

    /**
     * Save new calibration points to SharedPreferences.
     */
    fun save(newPoints: List<CalibrationPoint>) {
        points = newPoints.sortedWith(compareBy({ it.brightness }, { it.deg }))
        val json = JSONObject()
        points.forEachIndexed { index, pt ->
            val entry = JSONObject()
            entry.put("deg", pt.deg.toDouble())
            entry.put("brightness", pt.brightness)
            entry.put("standard", JSONArray(pt.standard.toList()))
            entry.put("strip_input", JSONArray(pt.stripInput.toList()))
            json.put("pt_$index", entry)
        }
        prefs.edit().putString("calibration_data", json.toString()).apply()
    }

    /**
     * Reset to bundled calibration, removing custom data.
     */
    fun reset() {
        prefs.edit().remove("calibration_data").apply()
        points = loadFromBundled()
    }

    private fun loadFromPrefs(): List<CalibrationPoint>? {
        val data = prefs.getString("calibration_data", null) ?: return null
        return parseCalibrationJson(data)
    }

    private fun loadFromBundled(): List<CalibrationPoint> {
        val raw = context.resources.openRawResource(R.raw.visual_calibration)
            .bufferedReader()
            .use { it.readText() }
        return parseCalibrationJson(raw)
    }

    /**
     * Map standard RGB to corrected strip RGB, accounting for brightness.
     *
     * @param r Red channel 0-255
     * @param g Green channel 0-255
     * @param b Blue channel 0-255
     * @param brightnessPercent Current brightness level 0-100
     * @return Corrected (r, g, b) triple, each channel clamped to 0-255
     */
    fun apply(r: Int, g: Int, b: Int, brightnessPercent: Int = 100): Triple<Int, Int, Int> {
        if (!enabled) return Triple(r, g, b)
        if (r == 0 && g == 0 && b == 0) return Triple(0, 0, 0)

        val levels = brightnessLevels
        if (levels.isEmpty()) return Triple(r, g, b)

        // If only one brightness level (legacy data), use hue-only interpolation
        if (levels.size == 1) {
            return applyAtBrightness(r, g, b, levels[0])
        }

        // Find bracketing brightness levels
        val bp = brightnessPercent.coerceIn(levels.first(), levels.last())
        val loIdx = levels.indexOfLast { it <= bp }
        val hiIdx = levels.indexOfFirst { it >= bp }

        if (loIdx == hiIdx || loIdx < 0 || hiIdx < 0) {
            return applyAtBrightness(r, g, b, levels[loIdx.coerceAtLeast(0)])
        }

        val loB = levels[loIdx]
        val hiB = levels[hiIdx]
        val t = if (hiB == loB) 0f else (bp - loB).toFloat() / (hiB - loB).toFloat()

        val loResult = applyAtBrightness(r, g, b, loB)
        val hiResult = applyAtBrightness(r, g, b, hiB)

        return Triple(
            (loResult.first * (1f - t) + hiResult.first * t).roundToInt().coerceIn(0, 255),
            (loResult.second * (1f - t) + hiResult.second * t).roundToInt().coerceIn(0, 255),
            (loResult.third * (1f - t) + hiResult.third * t).roundToInt().coerceIn(0, 255),
        )
    }

    /**
     * Apply hue interpolation at a specific brightness level.
     */
    private fun applyAtBrightness(r: Int, g: Int, b: Int, brightness: Int): Triple<Int, Int, Int> {
        val pts = points.filter { it.brightness == brightness }
        if (pts.isEmpty()) {
            // Fallback: use all points (legacy single-brightness data)
            return applyWithPoints(r, g, b, points)
        }
        return applyWithPoints(r, g, b, pts)
    }

    private fun applyWithPoints(r: Int, g: Int, b: Int, pts: List<CalibrationPoint>): Triple<Int, Int, Int> {
        if (pts.isEmpty()) return Triple(r, g, b)

        val hsv = FloatArray(3)
        Color.RGBToHSV(r, g, b, hsv)
        val hueDeg = hsv[0]
        val s = hsv[1]

        // Find the two bracketing calibration points by hue.
        var lo = pts.last()
        var hi = pts.first()
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
            hiDeg += 360f
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
        val sc = FloatArray(3) { loSc[it] * (1f - t) + hiSc[it] * t }

        val hueR = r * sc[0]
        val hueG = g * sc[1]
        val hueB = b * sc[2]

        val whiteR = r * WHITE_STRIP.first / 255f
        val whiteG = g * WHITE_STRIP.second / 255f
        val whiteB = b * WHITE_STRIP.third / 255f

        val outR = hueR * s + whiteR * (1f - s)
        val outG = hueG * s + whiteG * (1f - s)
        val outB = hueB * s + whiteB * (1f - s)

        return Triple(
            outR.roundToInt().coerceIn(0, 255),
            outG.roundToInt().coerceIn(0, 255),
            outB.roundToInt().coerceIn(0, 255),
        )
    }

    companion object {
        fun parseCalibrationJson(jsonStr: String): List<CalibrationPoint> {
            val json = JSONObject(jsonStr)
            val list = mutableListOf<CalibrationPoint>()
            for (name in json.keys()) {
                val entry = json.getJSONObject(name)
                val deg = entry.getDouble("deg").toFloat()
                val brightness = if (entry.has("brightness")) entry.getInt("brightness") else 100
                val stdArr = entry.getJSONArray("standard")
                val stripArr = entry.getJSONArray("strip_input")
                list.add(
                    CalibrationPoint(
                        deg = deg,
                        brightness = brightness,
                        standard = IntArray(3) { stdArr.getInt(it) },
                        stripInput = IntArray(3) { stripArr.getInt(it) },
                    )
                )
            }
            list.sortWith(compareBy({ it.brightness }, { it.deg }))
            return list
        }
    }
}
