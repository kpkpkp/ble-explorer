package com.elkbledom.ui

import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import android.view.WindowManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.elkbledom.calibration.CalibrationPoint
import com.elkbledom.calibration.ColorCalibration
import com.elkbledom.calibration.FrameAnalyzer
import com.elkbledom.calibration.NormalizedRect
import com.elkbledom.control.DeviceControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val BgColor = Color(0xFF1a1a2e)
private val CardColor = Color(0xFF16213e)
private val AccentColor = Color(0xFFe94560)

private enum class WizardPhase {
    SETUP,
    REGION_SELECT,
    CALIBRATING,
    RESULTS
}

private val TARGET_HUES = listOf(
    0f, 30f, 60f, 90f, 120f, 150f, 180f, 210f, 240f, 270f, 300f, 330f
)

private val BRIGHTNESS_LEVELS = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)

private fun hueToRgb(hueDeg: Float): Triple<Int, Int, Int> {
    val color = AndroidColor.HSVToColor(floatArrayOf(hueDeg, 1f, 1f))
    return Triple(
        AndroidColor.red(color),
        AndroidColor.green(color),
        AndroidColor.blue(color)
    )
}

private fun hueToColor(hueDeg: Float): Color {
    val (r, g, b) = hueToRgb(hueDeg)
    return Color(r, g, b)
}

data class CalibrationResult(
    val hueDeg: Float,
    val brightness: Int,
    val standardRgb: IntArray,
    val stripInputRgb: IntArray,
    val finalError: Float
) {
    val quality: String get() = when {
        finalError < 0.03f -> "Excellent"
        finalError < 0.06f -> "Good"
        finalError < 0.10f -> "Fair"
        else -> "Poor"
    }
}

@Composable
fun CalibrationWizardScreen(
    deviceControl: DeviceControl,
    calibration: ColorCalibration,
    onFinish: () -> Unit
) {
    val connectionStates by deviceControl.connectionStates.collectAsState()
    val hasConnectedStrip = connectionStates.values.any { it == com.elkbledom.control.ConnectionState.CONNECTED }
    var phase by remember { mutableStateOf(WizardPhase.SETUP) }

    val activity = LocalContext.current as? android.app.Activity
    DisposableEffect(phase) {
        if (phase == WizardPhase.CALIBRATING || phase == WizardPhase.REGION_SELECT) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    var stripRegion by remember { mutableStateOf<NormalizedRect?>(null) }
    var results by remember { mutableStateOf<List<CalibrationResult>>(emptyList()) }
    var currentHueIndex by remember { mutableIntStateOf(0) }
    var currentBrightnessIndex by remember { mutableIntStateOf(0) }
    var currentIteration by remember { mutableIntStateOf(0) }
    var sweepColor by remember { mutableStateOf(Color.Black) }
    var statusText by remember { mutableStateOf("") }

    // White reference correction factors per brightness level
    var whiteRef by remember { mutableStateOf<Map<Int, FloatArray>>(emptyMap()) }

    val analyzer = remember { FrameAnalyzer() }
    val scope = rememberCoroutineScope()

    when (phase) {
        WizardPhase.SETUP -> SetupPhase(
            onStart = { phase = WizardPhase.REGION_SELECT },
            onCancel = onFinish,
            hasConnectedStrip = hasConnectedStrip
        )

        WizardPhase.REGION_SELECT -> RegionSelectPhase(
            analyzer = analyzer,
            stripRegion = stripRegion,
            onStripTap = { stripRegion = it },
            onBegin = {
                phase = WizardPhase.CALIBRATING
                scope.launch {
                    // Phase 1: White reference at each brightness
                    statusText = "Measuring white reference..."
                    val ref = measureWhiteReference(
                        deviceControl = deviceControl,
                        analyzer = analyzer,
                        stripRegion = stripRegion!!,
                        onProgress = { brIdx ->
                            currentBrightnessIndex = brIdx
                            statusText = "White ref: ${BRIGHTNESS_LEVELS[brIdx]}%"
                        }
                    )
                    whiteRef = ref

                    // Phase 2: Hue × brightness sweep
                    statusText = "Calibrating..."
                    results = runCalibrationSweep(
                        deviceControl = deviceControl,
                        calibration = calibration,
                        analyzer = analyzer,
                        stripRegion = stripRegion!!,
                        whiteCorrection = ref,
                        onProgress = { brIdx, hueIdx, iteration, color, status ->
                            currentBrightnessIndex = brIdx
                            currentHueIndex = hueIdx
                            currentIteration = iteration
                            sweepColor = color
                            statusText = status
                        }
                    )
                    phase = WizardPhase.RESULTS
                }
            },
            onCancel = onFinish
        )

        WizardPhase.CALIBRATING -> CalibratingPhase(
            currentHueIndex = currentHueIndex,
            currentBrightnessIndex = currentBrightnessIndex,
            currentIteration = currentIteration,
            totalHues = TARGET_HUES.size,
            totalBrightness = BRIGHTNESS_LEVELS.size,
            sweepColor = sweepColor,
            statusText = statusText,
            onCancel = onFinish
        )

        WizardPhase.RESULTS -> ResultsPhase(
            results = results,
            onApply = {
                val points = results.map { r ->
                    CalibrationPoint(
                        deg = r.hueDeg,
                        brightness = r.brightness,
                        standard = r.standardRgb,
                        stripInput = r.stripInputRgb
                    )
                }
                calibration.save(points)
                // Sync to bridge
                scope.launch {
                    val bridge = deviceControl as? com.elkbledom.control.HttpBridgeClient
                    if (bridge != null) {
                        val ok = bridge.syncCalibration(points)
                        android.util.Log.i("CalWizard", "Bridge sync: $ok (${points.size} pts)")
                    }
                }
                onFinish()
            },
            onDiscard = onFinish
        )
    }
}

@Composable
private fun SetupPhase(
    onStart: () -> Unit,
    onCancel: () -> Unit,
    hasConnectedStrip: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Camera Calibration",
            style = MaterialTheme.typography.headlineSmall,
            color = AccentColor
        )
        Spacer(Modifier.height(24.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = CardColor),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Setup Instructions", color = Color.White,
                    style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(12.dp))
                InstructionStep("1", "Point the rear camera at a white surface lit by the strip")
                Spacer(Modifier.height(8.dp))
                InstructionStep("2", "Draw a circle around the lit area to sample")
                Spacer(Modifier.height(8.dp))
                InstructionStep("3", "Keep the phone steady during the sweep")
                Spacer(Modifier.height(16.dp))
                Text(
                    "Use a white surface (paper, wall, card) near the strip so the " +
                    "camera reads the strip's light color cleanly. 12 hues at 10 " +
                    "brightness levels = 120 calibration points.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (!hasConnectedStrip) {
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3E1E1E)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "No LED strip connected. Connect a strip via the bridge before calibrating.",
                    color = Color(0xFFFF8A80),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel) {
                Text("Cancel", color = Color.Gray)
            }
            Button(
                onClick = onStart,
                enabled = hasConnectedStrip,
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
            ) {
                Text("Start")
            }
        }
    }
}

@Composable
private fun InstructionStep(number: String, text: String) {
    Row {
        Text(
            "$number.",
            color = AccentColor,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(24.dp)
        )
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RegionSelectPhase(
    analyzer: FrameAnalyzer,
    stripRegion: NormalizedRect?,
    onStripTap: (NormalizedRect) -> Unit,
    onBegin: () -> Unit,
    onCancel: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // Track drag gesture for circle drawing
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top bar: prompt + buttons (accessible near top of phone)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgColor.copy(alpha = 0.9f))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (stripRegion == null) "Draw a circle around the sample area"
                    else "Region set. Tap again to redraw.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, "Cancel", tint = Color.White)
                }
            }
            if (stripRegion != null) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onBegin,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
                ) {
                    Text("Begin Calibration")
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        // Rear camera preview with draw-circle gesture
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build().also {
                                it.setAnalyzer(Executors.newSingleThreadExecutor(), analyzer)
                            }

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Draw-circle overlay
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragStart = offset
                                dragCurrent = offset
                            },
                            onDrag = { change, _ ->
                                dragCurrent = change.position
                            },
                            onDragEnd = {
                                val start = dragStart
                                val end = dragCurrent
                                if (start != null && end != null) {
                                    val cx = (start.x + end.x) / 2f
                                    val cy = (start.y + end.y) / 2f
                                    val dx = end.x - start.x
                                    val dy = end.y - start.y
                                    val radiusPx = sqrt(dx * dx + dy * dy) / 2f
                                    val minDim = minOf(size.width, size.height).toFloat()
                                    if (radiusPx > 10f && minDim > 0f) {
                                        onStripTap(
                                            NormalizedRect(
                                                cx = cx / size.width,
                                                cy = cy / size.height,
                                                radius = radiusPx / minDim
                                            )
                                        )
                                    }
                                }
                                dragStart = null
                                dragCurrent = null
                            },
                            onDragCancel = {
                                dragStart = null
                                dragCurrent = null
                            }
                        )
                    }
            ) {
                // Draw live drag circle (while dragging)
                val start = dragStart
                val end = dragCurrent
                if (start != null && end != null) {
                    val cx = (start.x + end.x) / 2f
                    val cy = (start.y + end.y) / 2f
                    val dx = end.x - start.x
                    val dy = end.y - start.y
                    val r = sqrt(dx * dx + dy * dy) / 2f
                    drawCircle(
                        color = Color(0xFFFF9800),
                        radius = r,
                        center = Offset(cx, cy),
                        style = Stroke(width = 3f)
                    )
                    drawCircle(
                        color = Color(0x30FF9800),
                        radius = r,
                        center = Offset(cx, cy)
                    )
                }

                // Draw confirmed region marker (orange)
                if (dragStart == null) {
                    stripRegion?.let { region ->
                        val cx = region.cx * size.width
                        val cy = region.cy * size.height
                        val r = region.radius * minOf(size.width, size.height)
                        drawCircle(
                            color = Color(0xFFFF9800),
                            radius = r,
                            center = Offset(cx, cy),
                            style = Stroke(width = 3f)
                        )
                        drawCircle(
                            color = Color(0x40FF9800),
                            radius = r,
                            center = Offset(cx, cy)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalibratingPhase(
    currentHueIndex: Int,
    currentBrightnessIndex: Int,
    currentIteration: Int,
    totalHues: Int,
    totalBrightness: Int,
    sweepColor: Color,
    statusText: String,
    onCancel: () -> Unit
) {
    val hueName = when (currentHueIndex) {
        0 -> "Red"; 1 -> "Orange"; 2 -> "Yellow"; 3 -> "Chartreuse"
        4 -> "Green"; 5 -> "Spring"; 6 -> "Cyan"; 7 -> "Azure"
        8 -> "Blue"; 9 -> "Violet"; 10 -> "Magenta"; 11 -> "Rose"
        else -> "Hue $currentHueIndex"
    }
    val brightnessLevel = if (currentBrightnessIndex < BRIGHTNESS_LEVELS.size)
        BRIGHTNESS_LEVELS[currentBrightnessIndex] else 100

    val totalSteps = totalHues * totalBrightness
    val currentStep = currentBrightnessIndex * totalHues + currentHueIndex
    val progress = (currentStep.toFloat() + currentIteration / 4f) / totalSteps

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        // All progress info at the TOP so it's visible when phone is propped up
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = AccentColor,
                trackColor = CardColor
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Step ${currentStep + 1} / $totalSteps",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(4.dp))
            Text(
                statusText,
                color = Color(0xFFFF9800),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "$hueName @ ${brightnessLevel}% — iter ${currentIteration + 1}/4",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            // Color swatch
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(sweepColor, RoundedCornerShape(8.dp))
                    .border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onCancel) {
                Text("Cancel", color = Color.Gray)
            }
        }
    }
}

@Composable
private fun ResultsPhase(
    results: List<CalibrationResult>,
    onApply: () -> Unit,
    onDiscard: () -> Unit
) {
    // Group by brightness for summary
    val byBrightness = results.groupBy { it.brightness }
    val overallQuality = results.map { it.finalError }.average().let { avg ->
        when {
            avg < 0.03 -> "Excellent"
            avg < 0.06 -> "Good"
            avg < 0.10 -> "Fair"
            else -> "Poor"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .padding(16.dp)
    ) {
        Text(
            "Calibration Results",
            style = MaterialTheme.typography.headlineSmall,
            color = AccentColor
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${results.size} points across ${BRIGHTNESS_LEVELS.size} brightness levels",
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "Overall: $overallQuality",
            color = when (overallQuality) {
                "Excellent" -> Color(0xFF4CAF50)
                "Good" -> Color(0xFF8BC34A)
                "Fair" -> Color(0xFFFFC107)
                else -> Color(0xFFFF5722)
            },
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Summary per brightness level
            byBrightness.keys.sorted().forEach { br ->
                val brResults = byBrightness[br] ?: return@forEach
                val avgErr = brResults.map { it.finalError }.average()
                val brQuality = when {
                    avgErr < 0.03 -> "Excellent"
                    avgErr < 0.06 -> "Good"
                    avgErr < 0.10 -> "Fair"
                    else -> "Poor"
                }
                val qualityColor = when (brQuality) {
                    "Excellent" -> Color(0xFF4CAF50)
                    "Good" -> Color(0xFF8BC34A)
                    "Fair" -> Color(0xFFFFC107)
                    else -> Color(0xFFFF5722)
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = CardColor),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${br}% brightness", color = Color.White,
                                style = MaterialTheme.typography.labelMedium)
                            Text(brQuality, color = qualityColor,
                                style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(4.dp))
                        // Color swatches row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            brResults.forEach { r ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(16.dp)
                                        .background(
                                            Color(
                                                r.stripInputRgb[0],
                                                r.stripInputRgb[1],
                                                r.stripInputRgb[2]
                                            ),
                                            RoundedCornerShape(2.dp)
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
        ) {
            OutlinedButton(onClick = onDiscard) {
                Text("Discard", color = Color.Gray)
            }
            Button(
                onClick = onApply,
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
            ) {
                Text("Apply (${results.size} pts)")
            }
        }
    }
}

/**
 * Measure white reference at each brightness level.
 * Sets strip to white (255,255,255), samples camera at each brightness.
 * Returns map: brightness% -> [R, G, B] camera readings.
 */
private suspend fun measureWhiteReference(
    deviceControl: DeviceControl,
    analyzer: FrameAnalyzer,
    stripRegion: NormalizedRect,
    onProgress: (brightnessIndex: Int) -> Unit
): Map<Int, FloatArray> = withContext(Dispatchers.Default) {
    val ref = mutableMapOf<Int, FloatArray>()

    // Set white color
    deviceControl.sendColorSuspend(255, 255, 255)
    delay(300)

    for ((brIdx, br) in BRIGHTNESS_LEVELS.withIndex()) {
        withContext(Dispatchers.Main) { onProgress(brIdx) }
        deviceControl.sendBrightnessSuspend(br)
        delay(1000)  // let strip + camera settle

        val sample = analyzer.sampleRegion(stripRegion, 8)
        ref[br] = sample
    }

    ref
}

/**
 * Run the full calibration sweep: 10 brightness levels × 12 hues.
 * Uses white reference to correct for camera bias at each brightness.
 */
private suspend fun runCalibrationSweep(
    deviceControl: DeviceControl,
    calibration: ColorCalibration,
    analyzer: FrameAnalyzer,
    stripRegion: NormalizedRect,
    whiteCorrection: Map<Int, FloatArray>,
    onProgress: (brIdx: Int, hueIdx: Int, iteration: Int, color: Color, status: String) -> Unit
): List<CalibrationResult> = withContext(Dispatchers.Default) {
    val results = mutableListOf<CalibrationResult>()

    for ((brIdx, br) in BRIGHTNESS_LEVELS.withIndex()) {
        // Set brightness for this level
        deviceControl.sendBrightnessSuspend(br)
        delay(300)

        val whiteRef = whiteCorrection[br] ?: floatArrayOf(255f, 255f, 255f)

        for ((hueIdx, hueDeg) in TARGET_HUES.withIndex()) {
            val (stdR, stdG, stdB) = hueToRgb(hueDeg)
            val standardRgb = intArrayOf(stdR, stdG, stdB)

            // Initial guess: use existing calibration or identity
            val existing = calibration.apply(stdR, stdG, stdB, br)
            var trialR = existing.first
            var trialG = existing.second
            var trialB = existing.third

            var bestError = Float.MAX_VALUE
            var bestTrial = intArrayOf(trialR, trialG, trialB)

            val hueName = when (hueIdx) {
                0 -> "Red"; 1 -> "Orange"; 2 -> "Yellow"; 3 -> "Chartreuse"
                4 -> "Green"; 5 -> "Spring"; 6 -> "Cyan"; 7 -> "Azure"
                8 -> "Blue"; 9 -> "Violet"; 10 -> "Magenta"; 11 -> "Rose"
                else -> "?"
            }

            for (iter in 0 until 4) {
                withContext(Dispatchers.Main) {
                    onProgress(
                        brIdx, hueIdx, iter, hueToColor(hueDeg),
                        "$hueName @ ${br}%"
                    )
                }

                // Send trial color
                deviceControl.sendColorSuspend(trialR, trialG, trialB)
                delay(1200)

                // Sample strip
                val stripSample = analyzer.sampleRegion(stripRegion, 5)

                // Apply white correction: normalize camera reading by white reference
                // If white ref reads (200, 180, 160), then camera has warm bias.
                // Corrected = sample * (255 / whiteRef) per channel
                val corrected = floatArrayOf(
                    if (whiteRef[0] > 1f) stripSample[0] * 255f / whiteRef[0] else stripSample[0],
                    if (whiteRef[1] > 1f) stripSample[1] * 255f / whiteRef[1] else stripSample[1],
                    if (whiteRef[2] > 1f) stripSample[2] * 255f / whiteRef[2] else stripSample[2],
                )

                // Compare corrected camera reading to target color proportions
                val targetSum = (stdR + stdG + stdB).toFloat().coerceAtLeast(1f)
                val correctedSum = (corrected[0] + corrected[1] + corrected[2]).coerceAtLeast(1f)

                if (correctedSum < 10f) continue  // too dark to analyze

                val targetProp = floatArrayOf(
                    stdR / targetSum,
                    stdG / targetSum,
                    stdB / targetSum
                )
                val stripProp = floatArrayOf(
                    corrected[0] / correctedSum,
                    corrected[1] / correctedSum,
                    corrected[2] / correctedSum
                )

                val error = maxOf(
                    abs(targetProp[0] - stripProp[0]),
                    abs(targetProp[1] - stripProp[1]),
                    abs(targetProp[2] - stripProp[2])
                )

                if (error < bestError) {
                    bestError = error
                    bestTrial = intArrayOf(trialR, trialG, trialB)
                }

                if (error < 0.05f) break

                // Adjust trial per channel
                val trialArr = intArrayOf(trialR, trialG, trialB)
                for (ch in 0..2) {
                    val diff = targetProp[ch] - stripProp[ch]
                    val step = max(2, (abs(diff) * trialArr[ch] * 1.5f).roundToInt())
                    if (diff > 0) {
                        trialArr[ch] = (trialArr[ch] + step).coerceIn(0, 255)
                    } else if (diff < 0) {
                        trialArr[ch] = (trialArr[ch] - step).coerceIn(0, 255)
                    }
                }
                trialR = trialArr[0]
                trialG = trialArr[1]
                trialB = trialArr[2]
            }

            results.add(
                CalibrationResult(
                    hueDeg = hueDeg,
                    brightness = br,
                    standardRgb = standardRgb,
                    stripInputRgb = bestTrial,
                    finalError = bestError
                )
            )
        }
    }

    results
}
