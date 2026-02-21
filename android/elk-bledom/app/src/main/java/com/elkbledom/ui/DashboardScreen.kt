package com.elkbledom.ui

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
import com.elkbledom.SessionState
import com.elkbledom.control.ConnectionState
import com.elkbledom.control.DeviceControl
import com.elkbledom.control.Target
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
    deviceControl: DeviceControl,
    calibration: ColorCalibration,
    patternEngine: PatternEngine,
    presetManager: PresetManager,
    sessionState: SessionState,
    onStopService: () -> Unit = {}
) {
    val connectionStates by deviceControl.connectionStates.collectAsState()
    val discoveredDevices by deviceControl.discoveredDevices.collectAsState()
    val isScanning by deviceControl.isScanning.collectAsState()
    val target by deviceControl.target.collectAsState()
    val playbackState by patternEngine.playbackState.collectAsState()
    val bridgeStatusVal by deviceControl.bridgeStatus.collectAsState()

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
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Title
        Text("ELK-BLEDOM", style = MaterialTheme.typography.titleLarge, color = AccentColor)

        // --- Bridge Status (only shown in bridge mode) ---
        bridgeStatusVal?.let { status ->
            SectionCard("Bridge") {
                Text("${status.firmwareVersion} | ${status.wifiIP} | ${status.wifiRSSI}dBm",
                    color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                Text("Uptime: ${status.uptime}s | Heap: ${status.freeHeap / 1024}KB",
                    color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }

        // --- Connection ---
        SectionCard("Connection") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { deviceControl.scan() },
                    enabled = !isScanning,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Scanning...", style = MaterialTheme.typography.labelMedium)
                    } else {
                        Icon(Icons.Default.Search, "Scan", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Scan", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (connectionStates.values.any { it == ConnectionState.CONNECTED }) {
                    Button(
                        onClick = onStopService,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF666666)),
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Default.Close, "Stop", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Stop All", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            val discoveredAddresses = discoveredDevices.map { it.address }.toSet()
            val reconnectedAddresses = connectionStates.keys.filter { it !in discoveredAddresses }
            if (discoveredDevices.isEmpty() && reconnectedAddresses.isEmpty() && !isScanning) {
                Text(
                    "No devices found. Tap Scan.", color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            // Show auto-reconnected devices (not from scan)
            reconnectedAddresses.forEach { address ->
                val state = connectionStates[address] ?: ConnectionState.DISCONNECTED
                val slot = deviceControl.slotFor(address)
                DeviceRow(slot ?: address, address, state,
                    onConnect = { },
                    onDisconnect = {
                        deviceControl.disconnect(address)
                        sessionState.connectedAddresses = deviceControl.getConnectedAddresses()
                    }
                )
            }
            // Show scanned devices
            discoveredDevices.forEach { device ->
                val name = device.name
                val address = device.address
                val state = connectionStates[address] ?: ConnectionState.DISCONNECTED
                DeviceRow(name, address, state,
                    onConnect = { deviceControl.connect(device.address) },
                    onDisconnect = {
                        deviceControl.disconnect(address)
                        sessionState.connectedAddresses = deviceControl.getConnectedAddresses()
                    }
                )
            }
        }

        // --- Target + Power (compact bar) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardColor, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Target.entries.forEach { t ->
                FilterChip(
                    selected = target == t,
                    onClick = { deviceControl.target.value = t },
                    label = { Text(t.name.replace("_", " "), style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(28.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentColor,
                        selectedLabelColor = Color.White
                    )
                )
                Spacer(Modifier.width(4.dp))
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { deviceControl.sendPower(true) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) { Text("ON", style = MaterialTheme.typography.labelSmall) }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = { deviceControl.sendPower(false) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF666666)),
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) { Text("OFF", style = MaterialTheme.typography.labelSmall) }
        }

        // --- Color + Brightness (merged) ---
        SectionCard("Color") {
            ColorPicker(
                red = red, green = green, blue = blue,
                correctedRed = corrR, correctedGreen = corrG, correctedBlue = corrB,
                calibrationEnabled = calibrationEnabled,
                onColorChange = { r, g, b ->
                    red = r; green = g; blue = b
                    val (cr, cg, cb) = if (calibrationEnabled) {
                        calibration.apply(r, g, b)
                    } else {
                        Triple(r, g, b)
                    }
                    deviceControl.sendColor(cr, cg, cb)
                }
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BrightnessLow, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                Slider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    onValueChangeFinished = {
                        deviceControl.sendBrightness(brightness.toInt())
                    },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = AccentColor, activeTrackColor = AccentColor
                    )
                )
                Icon(Icons.Default.BrightnessHigh, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("${brightness.toInt()}%", color = Color.White, style = MaterialTheme.typography.labelMedium)
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
                    deviceControl.sendEffect(code, effectSpeed.toInt())
                },
                onSpeedChange = { speed ->
                    effectSpeed = speed
                    if (selectedEffect.isNotEmpty()) {
                        val code = ElkProtocol.EFFECTS[selectedEffect] ?: return@EffectsSection
                        deviceControl.sendEffect(code, speed.toInt())
                    }
                }
            )
        }

        // --- Patterns ---
        SectionCard("Patterns") {
            PatternEditor(
                presetManager = presetManager,
                playbackState = playbackState,
                initialPreset = sessionState.lastPreset,
                onPlay = { steps, loop, presetName ->
                    patternEngine.play(steps, loop)
                    sessionState.lastPreset = presetName
                    sessionState.lastLoop = loop
                    sessionState.wasPlaying = true
                    sessionState.connectedAddresses = deviceControl.getConnectedAddresses()
                },
                onStop = {
                    patternEngine.stop()
                    sessionState.wasPlaying = false
                }
            )
        }

        // --- Calibration ---
        SectionCard("Calibration") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Visual Calibration", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = calibrationEnabled,
                    onCheckedChange = { calibrationEnabled = it; calibration.enabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AccentColor,
                        checkedTrackColor = AccentColor.copy(alpha = 0.5f)
                    )
                )
            }
            if (calibrationEnabled) {
                val (er, eg, eb) = calibration.apply(255, 0, 0)
                Text(
                    "Example: #FF0000 -> #${String.format("%02X%02X%02X", er, eg, eb)}",
                    color = Color.Gray, style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = AccentColor,
                modifier = Modifier.padding(bottom = 4.dp)
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(10.dp).clip(CircleShape).background(
                when (state) {
                    ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                    ConnectionState.CONNECTING -> Color(0xFFFFC107)
                    ConnectionState.DISCONNECTED -> Color.Gray
                }
            )
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = Color.White, style = MaterialTheme.typography.bodySmall)
            Text(address, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        }
        when (state) {
            ConnectionState.CONNECTED ->
                TextButton(onClick = onDisconnect) {
                    Text("Disconnect", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                }
            ConnectionState.CONNECTING ->
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AccentColor, strokeWidth = 2.dp)
            ConnectionState.DISCONNECTED ->
                TextButton(onClick = onConnect) {
                    Text("Connect", color = AccentColor, style = MaterialTheme.typography.labelSmall)
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

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedEffect.ifEmpty { "Select effect..." },
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            textStyle = MaterialTheme.typography.bodySmall,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = AccentColor, unfocusedBorderColor = Color.Gray
            ),
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            effectNames.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onEffectSelected(name); expanded = false }
                )
            }
        }
    }

    Spacer(Modifier.height(4.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Speed", color = Color.Gray, modifier = Modifier.width(42.dp),
            style = MaterialTheme.typography.labelSmall)
        Slider(
            value = effectSpeed,
            onValueChange = onSpeedChange,
            valueRange = 1f..10f,
            steps = 8,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = AccentColor, activeTrackColor = AccentColor)
        )
        Text("${effectSpeed.toInt()}", color = Color.White, modifier = Modifier.width(20.dp),
            style = MaterialTheme.typography.labelMedium)
    }
}
