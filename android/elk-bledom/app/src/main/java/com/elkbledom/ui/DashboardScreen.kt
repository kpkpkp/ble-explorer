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
import com.elkbledom.control.DiscoveredDevice
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
    isBridgeMode: Boolean = false,
    onShowSettings: () -> Unit = {},
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

    // Overflow menu and dialog state (bridge mode)
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showBridgeInfoDialog by remember { mutableStateOf(false) }
    var showScanDialog by remember { mutableStateOf(false) }
    var showBehaviorDialog by remember { mutableStateOf(false) }

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
        // --- Header row with overflow menu ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "ELK-BLEDOM",
                style = MaterialTheme.typography.titleLarge,
                color = AccentColor,
                modifier = Modifier.weight(1f)
            )
            if (isBridgeMode) {
                Box {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Menu", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Bridge Info") },
                            onClick = { showOverflowMenu = false; showBridgeInfoDialog = true },
                            leadingIcon = { Icon(Icons.Default.Info, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Scan for Strips") },
                            onClick = { showOverflowMenu = false; showScanDialog = true },
                            leadingIcon = { Icon(Icons.Default.Search, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Behavior") },
                            onClick = { showOverflowMenu = false; showBehaviorDialog = true },
                            leadingIcon = { Icon(Icons.Default.Tune, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = { showOverflowMenu = false; onShowSettings() },
                            leadingIcon = { Icon(Icons.Default.Settings, null) }
                        )
                    }
                }
            }
        }

        // --- Strip status bar (bridge mode only) ---
        if (isBridgeMode) {
            StripStatusBar(
                connectionStates = connectionStates,
                target = target,
                deviceControl = deviceControl
            )
        }

        // --- Connection card (BLE mode only) ---
        if (!isBridgeMode) {
            // Bridge status card (BLE mode shows inline if available)
            bridgeStatusVal?.let { status ->
                SectionCard("Bridge") {
                    Text("${status.firmwareVersion} | ${status.wifiIP} | ${status.wifiRSSI}dBm",
                        color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    Text("Uptime: ${status.uptime}s | Heap: ${status.freeHeap / 1024}KB",
                        color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            }

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

    // --- Dialogs (bridge mode) ---
    if (showBridgeInfoDialog) {
        BridgeInfoDialog(
            bridgeStatus = bridgeStatusVal,
            onDismiss = { showBridgeInfoDialog = false }
        )
    }

    if (showScanDialog) {
        ScanDialog(
            deviceControl = deviceControl,
            connectionStates = connectionStates,
            discoveredDevices = discoveredDevices,
            isScanning = isScanning,
            onDismiss = { showScanDialog = false }
        )
    }

    if (showBehaviorDialog) {
        BehaviorDialog(
            sessionState = sessionState,
            onDismiss = { showBehaviorDialog = false }
        )
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

@Composable
private fun StripStatusBar(
    connectionStates: Map<String, ConnectionState>,
    target: Target,
    deviceControl: DeviceControl
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (connectionStates.isEmpty()) {
            Text(
                "No strips connected",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            val addresses = connectionStates.keys.toList()
            addresses.forEachIndexed { index, address ->
                val state = connectionStates[address] ?: ConnectionState.DISCONNECTED
                val stripTarget = when (index) {
                    0 -> Target.STRIP1
                    1 -> Target.STRIP2
                    else -> Target.ALL
                }
                val isSelected = target == stripTarget
                val shortAddr = if (address.length >= 8) "..${address.takeLast(5)}" else address
                val dotColor = when (state) {
                    ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                    ConnectionState.CONNECTING -> Color(0xFFFFC107)
                    ConnectionState.DISCONNECTED -> Color.Gray
                }

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        deviceControl.target.value = if (isSelected) Target.ALL else stripTarget
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "STRIP${index + 1} ($shortAddr)",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    },
                    modifier = Modifier.height(32.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentColor,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
    }
}

@Composable
private fun BridgeInfoDialog(
    bridgeStatus: com.elkbledom.control.BridgeStatus?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardColor,
        title = { Text("Bridge Info", color = AccentColor) },
        text = {
            if (bridgeStatus != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoRow("Firmware", bridgeStatus.firmwareVersion)
                    InfoRow("WiFi IP", bridgeStatus.wifiIP)
                    InfoRow("SSID", bridgeStatus.wifiSSID)
                    InfoRow("RSSI", "${bridgeStatus.wifiRSSI} dBm")
                    InfoRow("Uptime", "${bridgeStatus.uptime}s")
                    InfoRow("Free Heap", "${bridgeStatus.freeHeap / 1024} KB")
                    if (bridgeStatus.playbackState != "idle") {
                        InfoRow("Playback", "${bridgeStatus.playbackState} " +
                            "(step ${bridgeStatus.playbackStep + 1}/${bridgeStatus.playbackTotal})")
                    }
                }
            } else {
                Text("Bridge not responding", color = Color.Gray)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = AccentColor)
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ScanDialog(
    deviceControl: DeviceControl,
    connectionStates: Map<String, ConnectionState>,
    discoveredDevices: List<DiscoveredDevice>,
    isScanning: Boolean,
    onDismiss: () -> Unit
) {
    // Auto-trigger scan on open
    LaunchedEffect(Unit) {
        deviceControl.scan()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardColor,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Scan for Strips", color = AccentColor, modifier = Modifier.weight(1f))
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = AccentColor,
                        strokeWidth = 2.dp
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Connected strips
                if (connectionStates.isNotEmpty()) {
                    Text("Connected", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    connectionStates.forEach { (address, state) ->
                        val slot = deviceControl.slotFor(address)
                        DeviceRow(
                            name = slot ?: address,
                            address = address,
                            state = state,
                            onConnect = { },
                            onDisconnect = { deviceControl.disconnect(address) }
                        )
                    }
                }

                // Discovered (not already connected)
                val connectedAddrs = connectionStates.keys
                val newDevices = discoveredDevices.filter { it.address !in connectedAddrs }
                if (newDevices.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Discovered", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    newDevices.forEach { device ->
                        DeviceRow(
                            name = device.name,
                            address = device.address,
                            state = ConnectionState.DISCONNECTED,
                            onConnect = { deviceControl.connect(device.address) },
                            onDisconnect = { }
                        )
                    }
                }

                if (!isScanning && discoveredDevices.isEmpty() && connectionStates.isEmpty()) {
                    Text(
                        "No devices found",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isScanning) {
                    TextButton(onClick = { deviceControl.scan() }) {
                        Text("Rescan", color = AccentColor)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close", color = Color.Gray)
                }
            }
        }
    )
}

@Composable
private fun BehaviorDialog(
    sessionState: SessionState,
    onDismiss: () -> Unit
) {
    var autoResume by remember { mutableStateOf(sessionState.autoResumePattern) }
    var powerOff by remember { mutableStateOf(sessionState.powerOffOnExit) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardColor,
        title = { Text("Behavior", color = AccentColor) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-resume pattern", color = Color.White,
                            style = MaterialTheme.typography.bodyMedium)
                        Text("Re-send pattern to bridge after it restarts",
                            color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = autoResume,
                        onCheckedChange = {
                            autoResume = it
                            sessionState.autoResumePattern = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentColor,
                            checkedTrackColor = AccentColor.copy(alpha = 0.5f)
                        )
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Power off on exit", color = Color.White,
                            style = MaterialTheme.typography.bodyMedium)
                        Text("Turn off all strips when app closes",
                            color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = powerOff,
                        onCheckedChange = {
                            powerOff = it
                            sessionState.powerOffOnExit = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentColor,
                            checkedTrackColor = AccentColor.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = AccentColor)
            }
        }
    )
}
