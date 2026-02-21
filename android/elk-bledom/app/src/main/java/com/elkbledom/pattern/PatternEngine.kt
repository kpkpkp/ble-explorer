package com.elkbledom.pattern

import com.elkbledom.control.DeviceControl
import com.elkbledom.control.HttpBridgeClient
import com.elkbledom.control.PatternStepData
import com.elkbledom.calibration.ColorCalibration
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext

data class PatternStep(
    val color: String,
    val brightness: Int,
    val duration: Float,
    val transition: String,
    val fadeTime: Float
)

data class PlaybackState(
    val state: String = "stopped",
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    val currentColor: String = "#000000",
    val loop: Boolean = false
)

class PatternEngine(
    private val deviceControl: DeviceControl,
    private val calibration: ColorCalibration
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var playbackJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    fun play(pattern: List<PatternStep>, loop: Boolean) {
        stop()
        _playbackState.value = PlaybackState(
            state = "playing",
            totalSteps = pattern.size,
            loop = loop
        )

        if (deviceControl is HttpBridgeClient) {
            // Bridge mode: send pattern to bridge and monitor
            val stepsData = pattern.map { step ->
                val (r, g, b) = parseColor(step.color)
                val (cr, cg, cb) = calibration.apply(r, g, b)
                PatternStepData(
                    r = cr, g = cg, b = cb,
                    brightness = step.brightness,
                    duration = (step.duration * 1000).toInt(),
                    transition = step.transition,
                    fadeTime = (step.fadeTime * 1000).toInt()
                )
            }
            deviceControl.sendPattern(stepsData, loop)
            // Monitor bridge playback status
            playbackJob = scope.launch {
                while (isActive) {
                    val status = deviceControl.bridgeStatus.value
                    if (status != null) {
                        _playbackState.value = _playbackState.value.copy(
                            state = if (status.playbackState == "idle") "stopped" else "playing",
                            currentStep = status.playbackStep,
                            totalSteps = status.playbackTotal,
                            loop = status.playbackLoop
                        )
                        if (status.playbackState == "idle") break
                    }
                    delay(500)
                }
            }
        } else {
            // BLE direct mode: run pattern locally
            playbackJob = scope.launch {
                runPattern(pattern, loop)
            }
        }
    }

    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        if (deviceControl is HttpBridgeClient) {
            deviceControl.stopPattern()
        }
        _playbackState.value = _playbackState.value.copy(
            state = "stopped",
            currentStep = 0
        )
    }

    private fun parseColor(hexColor: String): Triple<Int, Int, Int> {
        val h = hexColor.trimStart('#')
        return Triple(
            h.substring(0, 2).toInt(16),
            h.substring(2, 4).toInt(16),
            h.substring(4, 6).toInt(16)
        )
    }

    private fun brightnessToByte(pct: Int): Int =
        (pct * 255 / 100).coerceIn(0, 255)

    private suspend fun sendColorAndBrightness(r: Int, g: Int, b: Int, brightnessPct: Int) {
        val (cr, cg, cb) = calibration.apply(r, g, b)
        deviceControl.sendColorSuspend(cr, cg, cb)
        deviceControl.sendBrightnessSuspend(brightnessPct)
    }

    private suspend fun fade(
        r1: Int, g1: Int, b1: Int,
        r2: Int, g2: Int, b2: Int,
        br1: Int, br2: Int,
        fadeTime: Float
    ) {
        val steps = maxOf(1, (fadeTime / 0.033f).toInt())
        for (i in 0 until steps) {
            if (!currentCoroutineContext().isActive) return
            val t = (i + 1).toFloat() / steps
            val r = (r1 + (r2 - r1) * t).toInt()
            val g = (g1 + (g2 - g1) * t).toInt()
            val b = (b1 + (b2 - b1) * t).toInt()
            val (cr, cg, cb) = calibration.apply(r, g, b)
            deviceControl.sendColorSuspend(cr, cg, cb)
            if (kotlin.math.abs(br2 - br1) > 2 || i == steps - 1) {
                val br = (br1 + (br2 - br1) * t).toInt()
                deviceControl.sendBrightnessSuspend(br)
            }
            delay(33)
        }
    }

    private suspend fun runPattern(pattern: List<PatternStep>, loop: Boolean) {
        try {
            var prevR = 0; var prevG = 0; var prevB = 0; var prevBr = 100
            while (currentCoroutineContext().isActive) {
                for ((i, step) in pattern.withIndex()) {
                    if (!currentCoroutineContext().isActive) return
                    val (r, g, b) = parseColor(step.color)
                    val br = step.brightness

                    _playbackState.value = _playbackState.value.copy(
                        currentStep = i + 1,
                        currentColor = step.color
                    )

                    if (step.transition == "fade" && step.fadeTime > 0) {
                        fade(prevR, prevG, prevB, r, g, b, prevBr, br, step.fadeTime)
                    } else {
                        sendColorAndBrightness(r, g, b, br)
                    }

                    prevR = r; prevG = g; prevB = b; prevBr = br

                    delay((step.duration * 1000).toLong())
                }
                if (!loop) break
            }
        } finally {
            _playbackState.value = _playbackState.value.copy(
                state = "stopped",
                currentStep = 0
            )
        }
    }
}
