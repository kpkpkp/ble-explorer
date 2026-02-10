package com.elkbledom.ui

import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.elkbledom.ble.BleManager
import com.elkbledom.ble.ConnectionState
import com.elkbledom.ble.Target
import com.elkbledom.ble.ElkProtocol
import com.elkbledom.calibration.ColorCalibration
import com.elkbledom.pattern.PatternEngine
import com.elkbledom.pattern.PatternStep
import com.elkbledom.pattern.PlaybackState
import com.elkbledom.pattern.PresetManager

private val BgColor = Color(0xFF1a1a2e)
private val CardColor = Color(0xFF16213e)
private val AccentColor = Color(0xFFe94560)

@Composable
fun DashboardScreen(
    bleManager: BleManager,
    calibration: ColorCalibration,
    patternEngine: PatternEngine,
    presetManager: PresetManager
) {
    val connectionStates by bleManager.connectionStates.collectAsState()
    val discoveredDevices by bleManager.discoveredDevices.collectAsState()
    val isScanning by bleManager.isScanning.collectAsState()
    val target by bleManager.target.collectAsState()
    val playbackState by patternEngine.playbackState.collectAsState()

    var red by remember { mutableIntStateOf(255) }
    var green by remember { mutableIntStateOf(255) }
    var blue by remember { mutableIntStateOf(255) }
    var brightness by remember { mutableFloatStateOf(100f) }
    var calibrationEnabled by remember { mutableStateOf(calibration.enabled) }
    var selectedEffect by remember { mutableStateOf("") }
    var effectSpeed by remember { mutableFloatStateOf(3f) }

    val (corrR, corrG, corrB) = if (calibrationEnabled) {
        calibration.apply(red, green, blue)
    } else {
        Triple(red, green, blue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Text(
            "ELK-BLEDOM",
            style = MaterialTheme.typography.headlineMedium,
            color = AccentColor
        )

        // --- Connection Bar ---
        SectionCard("Connection") {
            Button(
                onClick = { bleManager.scan() },
                enabled = !isScanning,
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Scanning...")
                } else {
                    Icon(Icons.Default.Search, contentDescription = "Scan")
                    Spacer(Modifier.width(4.dp))
                    Text("Scan")
                }
            }

            Spacer(Modifier.height(8.dp))

            if (discoveredDevices.isEmpty() && !isScanning) {
                Text("No devices found. Tap Scan.", color = Color.Gray)
            }

            discoveredDevices.forEach { device ->
                @Suppress("MissingPermission")
                val name = device.name ?: "Unknown"
                val address = device.address
                val state = connectionStates[address] ?: ConnectionState.DISCONNECTED
                DeviceRow(name, address, state,
                    onConnect = { bleManager.connect(device) },
                    onDisconnect = { bleManager.disconnect(address) }
                )
            }
        }

        // --- Target Selector ---
        SectionCard("Target") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Target.entries.forEach { t ->
                    FilterChip(
                        selected = target == t,
                        onClick = { bleManager.target.value = t },
                        label = { Text(t.name.replace("_", " ")) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentColor,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        // --- Power ---
        SectionCard("Power") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { bleManager.write(ElkProtocol.cmdPower(true)) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = "On")
                    Spacer(Modifier.width(4.dp))
                    Text("ON")
                }
                Button(
                    onClick = { bleManager.write(ElkProtocol.cmdPower(false)) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF666666))
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = "Off")
                    Spacer(Modifier.width(4.dp))
                    Text("OFF")
                }
            }
        }

        // --- Color Picker ---
        SectionCard("Color") {
            ColorPicker(
                red = red,
                green = green,
                blue = blue,
                correctedRed = corrR,
                correctedGreen = corrG,
                correctedBlue = corrB,
                calibrationEnabled = calibrationEnabled,
                onColorChange = { r, g, b ->
                    red = r; green = g; blue = b
                    val (cr, cg, cb) = if (calibrationEnabled) {
                        calibration.apply(r, g, b)
                    } else {
                        Triple(r, g, b)
                    }
                    bleManager.write(ElkProtocol.cmdColor(cr, cg, cb))
                }
            )
        }

        // --- Brightness ---
        SectionCard("Brightness") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BrightnessLow, contentDescription = null, tint = Color.Gray)
                Slider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    onValueChangeFinished = {
                        val level = (brightness * 255 / 100).toInt().coerceIn(0, 255)
                        bleManager.write(ElkProtocol.cmdBrightness(level))
                    },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = AccentColor,
                        activeTrackColor = AccentColor
                    )
                )
                Icon(Icons.Default.BrightnessHigh, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("${brightness.toInt()}%", color = Color.White)
            }
        }

        // --- Effects ---
        SectionCard("Effects") {
            EffectsSection(
                selectedEffect = selectedEffect,
                effectSpeed = effectSpeed,
                onEffectSelected = { name ->
                    selectedEffect = name
                    val code = ElkProtocol.EFFECTS[name] ?: return@EffectsSection
                    bleManager.write(ElkProtocol.cmdEffect(code, effectSpeed.toInt()))
                },
                onSpeedChange = { speed ->
                    effectSpeed = speed
                    if (selectedEffect.isNotEmpty()) {
                        val code = ElkProtocol.EFFECTS[selectedEffect] ?: return@EffectsSection
                        bleManager.write(ElkProtocol.cmdEffect(code, speed.toInt()))
                    }
                }
            )
        }

        // --- Patterns ---
        SectionCard("Patterns") {
            PatternEditor(
                presetManager = presetManager,
                playbackState = playbackState,
                onPlay = { steps, loop -> patternEngine.play(steps, loop) },
                onStop = { patternEngine.stop() }
            )
        }

        // --- Calibration ---
        SectionCard("Calibration") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Visual Calibration", color = Color.White)
                Switch(
                    checked = calibrationEnabled,
                    onCheckedChange = {
                        calibrationEnabled = it
                        calibration.enabled = it
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AccentColor,
                        checkedTrackColor = AccentColor.copy(alpha = 0.5f)
                    )
                )
            }
            if (calibrationEnabled) {
                val (er, eg, eb) = calibration.apply(255, 0, 0)
                Text(
                    "Example: #FF0000 → #${String.format("%02X%02X%02X", er, eg, eb)}",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = AccentColor,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}

@Composable
private fun DeviceRow(
    name: String,
    address: String,
    state: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(
                    when (state) {
                        ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                        ConnectionState.CONNECTING -> Color(0xFFFFC107)
                        ConnectionState.DISCONNECTED -> Color.Gray
                    }
                )
        )
        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Text(address, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        }

        when (state) {
            ConnectionState.CONNECTED -> {
                TextButton(onClick = onDisconnect) {
                    Text("Disconnect", color = Color.Gray)
                }
            }
            ConnectionState.CONNECTING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = AccentColor,
                    strokeWidth = 2.dp
                )
            }
            ConnectionState.DISCONNECTED -> {
                TextButton(onClick = onConnect) {
                    Text("Connect", color = AccentColor)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EffectsSection(
    selectedEffect: String,
    effectSpeed: Float,
    onEffectSelected: (String) -> Unit,
    onSpeedChange: (Float) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val effectNames = ElkProtocol.EFFECTS.keys.toList()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedEffect.ifEmpty { "Select effect..." },
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = AccentColor,
                unfocusedBorderColor = Color.Gray
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            effectNames.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onEffectSelected(name)
                        expanded = false
                    }
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Speed", color = Color.Gray, modifier = Modifier.width(50.dp))
        Slider(
            value = effectSpeed,
            onValueChange = onSpeedChange,
            valueRange = 1f..10f,
            steps = 8,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = AccentColor,
                activeTrackColor = AccentColor
            )
        )
        Text("${effectSpeed.toInt()}", color = Color.White, modifier = Modifier.width(24.dp))
    }
}
