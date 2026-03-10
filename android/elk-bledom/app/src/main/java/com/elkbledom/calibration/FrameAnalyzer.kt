package com.elkbledom.calibration

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.delay
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Normalized rectangular region for sampling camera frames.
 * Coordinates are 0..1 relative to image dimensions.
 */
data class NormalizedRect(
    val cx: Float,
    val cy: Float,
    val radius: Float = 0.05f
)

/**
 * CameraX ImageAnalysis.Analyzer that captures YUV_420_888 frames
 * and provides region-based RGB sampling for calibration.
 *
 * Uses the rear camera — no X-flip needed (rear camera is not mirrored).
 */
class FrameAnalyzer : ImageAnalysis.Analyzer {

    @Volatile
    private var latestY: ByteArray? = null
    @Volatile
    private var latestU: ByteArray? = null
    @Volatile
    private var latestV: ByteArray? = null
    @Volatile
    private var latestWidth: Int = 0
    @Volatile
    private var latestHeight: Int = 0
    @Volatile
    private var latestURowStride: Int = 0
    @Volatile
    private var latestVRowStride: Int = 0
    @Volatile
    private var latestUPixelStride: Int = 0
    @Volatile
    private var latestVPixelStride: Int = 0

    override fun analyze(image: ImageProxy) {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        latestWidth = image.width
        latestHeight = image.height
        latestURowStride = uPlane.rowStride
        latestVRowStride = vPlane.rowStride
        latestUPixelStride = uPlane.pixelStride
        latestVPixelStride = vPlane.pixelStride

        latestY = toByteArray(yPlane.buffer)
        latestU = toByteArray(uPlane.buffer)
        latestV = toByteArray(vPlane.buffer)

        image.close()
    }

    private fun toByteArray(buffer: ByteBuffer): ByteArray {
        val copy = buffer.duplicate()
        copy.rewind()
        val arr = ByteArray(copy.remaining())
        copy.get(arr)
        return arr
    }

    /**
     * Sample average RGB from a circular region across multiple frames.
     * Front camera X-flip is applied automatically.
     *
     * @param region Normalized region to sample
     * @param frameCount Number of frames to average
     * @return FloatArray of [R, G, B] each 0-255
     */
    suspend fun sampleRegion(region: NormalizedRect, frameCount: Int = 5): FloatArray {
        var totalR = 0.0
        var totalG = 0.0
        var totalB = 0.0
        var totalPixels = 0

        repeat(frameCount) {
            delay(100) // wait for fresh frame

            val yBuf = latestY ?: return floatArrayOf(0f, 0f, 0f)
            val uBuf = latestU ?: return floatArrayOf(0f, 0f, 0f)
            val vBuf = latestV ?: return floatArrayOf(0f, 0f, 0f)
            val w = latestWidth
            val h = latestHeight
            val uRowStride = latestURowStride
            val vRowStride = latestVRowStride
            val uPixelStride = latestUPixelStride
            val vPixelStride = latestVPixelStride

            if (w == 0 || h == 0) return floatArrayOf(0f, 0f, 0f)

            // Rear camera: no X-flip needed
            val pixCx = (region.cx * w).roundToInt().coerceIn(0, w - 1)
            val pixCy = (region.cy * h).roundToInt().coerceIn(0, h - 1)
            val pixRadius = (region.radius * minOf(w, h)).roundToInt().coerceAtLeast(1)

            val x0 = (pixCx - pixRadius).coerceAtLeast(0)
            val x1 = (pixCx + pixRadius).coerceAtMost(w - 1)
            val y0 = (pixCy - pixRadius).coerceAtLeast(0)
            val y1 = (pixCy + pixRadius).coerceAtMost(h - 1)
            val r2 = pixRadius * pixRadius

            for (py in y0..y1) {
                for (px in x0..x1) {
                    val dx = px - pixCx
                    val dy = py - pixCy
                    if (dx * dx + dy * dy > r2) continue

                    val yVal = (yBuf[py * w + px].toInt() and 0xFF)
                    val uvRow = py / 2
                    val uvCol = px / 2
                    val uIdx = uvRow * uRowStride + uvCol * uPixelStride
                    val vIdx = uvRow * vRowStride + uvCol * vPixelStride

                    if (uIdx >= uBuf.size || vIdx >= vBuf.size) continue

                    val uVal = (uBuf[uIdx].toInt() and 0xFF) - 128
                    val vVal = (vBuf[vIdx].toInt() and 0xFF) - 128

                    // BT.601 YUV→RGB
                    val rr = (yVal + 1.402 * vVal).coerceIn(0.0, 255.0)
                    val gg = (yVal - 0.344136 * uVal - 0.714136 * vVal).coerceIn(0.0, 255.0)
                    val bb = (yVal + 1.772 * uVal).coerceIn(0.0, 255.0)

                    totalR += rr
                    totalG += gg
                    totalB += bb
                    totalPixels++
                }
            }
        }

        if (totalPixels == 0) return floatArrayOf(0f, 0f, 0f)
        return floatArrayOf(
            (totalR / totalPixels).toFloat(),
            (totalG / totalPixels).toFloat(),
            (totalB / totalPixels).toFloat()
        )
    }
}
