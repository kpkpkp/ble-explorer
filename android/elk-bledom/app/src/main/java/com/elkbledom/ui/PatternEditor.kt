package com.elkbledom.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.elkbledom.pattern.PatternStep
import com.elkbledom.pattern.PlaybackState
import com.elkbledom.pattern.PresetManager

private val AccentColor = Color(0xFFe94560)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternEditor(
    presetManager: PresetManager,
    playbackState: PlaybackState,
    onPlay: (List<PatternStep>, Boolean) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allPresets by presetManager.allPresets.collectAsState()
    var selectedPreset by remember { mutableStateOf("Christmas") }
    var loop by remember { mutableStateOf(true) }
    var presetExpanded by remember { mutableStateOf(false) }
    val isPlaying = playbackState.state == "playing"

    // Working copy of steps (editable)
    val steps = remember { mutableStateListOf<PatternStep>() }
    var initialized by remember { mutableStateOf(false) }
    if (!initialized && allPresets.isNotEmpty()) {
        steps.clear()
        steps.addAll(allPresets[selectedPreset] ?: emptyList())
        initialized = true
    }

    // Dialog states
    var showSaveDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCopyDialog by remember { mutableStateOf(false) }
    var showBulkEdit by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // === A. Preset toolbar ===
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Dropdown
            ExposedDropdownMenuBox(
                expanded = presetExpanded,
                onExpandedChange = { presetExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = selectedPreset,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(presetExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AccentColor,
                        unfocusedBorderColor = Color.Gray
                    ),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = presetExpanded,
                    onDismissRequest = { presetExpanded = false }
                ) {
                    allPresets.keys.forEach { name ->
                        DropdownMenuItem(
                            text = {
                                Row {
                                    Text(name)
                                    if (!presetManager.isBuiltin(name)) {
                                        Spacer(Modifier.width(4.dp))
                                        Text("*", color = AccentColor)
                                    }
                                }
                            },
                            onClick = {
                                selectedPreset = name
                                steps.clear()
                                steps.addAll(allPresets[name] ?: emptyList())
                                presetExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Preset action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            SmallActionButton("Save", Icons.Default.Save) { showSaveDialog = true }
            SmallActionButton("Copy", Icons.Default.ContentCopy) { showCopyDialog = true }
            SmallActionButton("Rename", Icons.Default.Edit) { showRenameDialog = true }
            SmallActionButton("Del", Icons.Default.Delete) { showDeleteConfirm = true }
        }

        Spacer(Modifier.height(8.dp))

        // === B. Step list (editable) ===
        LazyColumn(
            modifier = Modifier.heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(steps.toList(), key = { i, _ -> i }) { index, step ->
                val isActive = isPlaying && playbackState.currentStep == index + 1
                StepRow(
                    index = index,
                    step = step,
                    isActive = isActive,
                    isFirst = index == 0,
                    isLast = index == steps.lastIndex,
                    onUpdate = { steps[index] = it },
                    onMoveUp = {
                        if (index > 0) {
                            val item = steps.removeAt(index)
                            steps.add(index - 1, item)
                        }
                    },
                    onMoveDown = {
                        if (index < steps.lastIndex) {
                            val item = steps.removeAt(index)
                            steps.add(index + 1, item)
                        }
                    },
                    onRemove = { steps.removeAt(index) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // === C. Add Step + Bulk Edit ===
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    steps.add(PatternStep("#FF0000", 100, 2.0f, "cut", 0.5f))
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentColor),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(AccentColor)
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Step")
            }

            TextButton(onClick = { showBulkEdit = !showBulkEdit }) {
                Text(
                    if (showBulkEdit) "Hide Bulk Edit" else "Bulk Edit",
                    color = Color.Gray
                )
            }
        }

        // Bulk edit panel
        AnimatedVisibility(visible = showBulkEdit) {
            BulkEditPanel(
                onApply = { brightness, duration, transition, fadeTime ->
                    for (i in steps.indices) {
                        var s = steps[i]
                        if (brightness != null) s = s.copy(brightness = brightness)
                        if (duration != null) s = s.copy(duration = duration)
                        if (transition != null) s = s.copy(transition = transition)
                        if (fadeTime != null) s = s.copy(fadeTime = fadeTime)
                        steps[i] = s
                    }
                }
            )
        }

        Spacer(Modifier.height(8.dp))

        // === D. Playback controls ===
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isPlaying) {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop")
                    Spacer(Modifier.width(4.dp))
                    Text("Stop")
                }
            } else {
                Button(
                    onClick = {
                        if (steps.isNotEmpty()) onPlay(steps.toList(), loop)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                    Spacer(Modifier.width(4.dp))
                    Text("Play")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = loop,
                    onCheckedChange = { loop = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = AccentColor,
                        uncheckedColor = Color.Gray
                    )
                )
                Text("Loop", color = Color.White)
            }

            if (isPlaying) {
                Text(
                    "Step ${playbackState.currentStep}/${playbackState.totalSteps}",
                    color = AccentColor,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }

    // === Dialogs ===
    if (showSaveDialog) {
        TextInputDialog(
            title = "Save Preset",
            initialValue = selectedPreset,
            onConfirm = { name ->
                presetManager.save(name, steps.toList())
                selectedPreset = name
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false }
        )
    }

    if (showCopyDialog) {
        TextInputDialog(
            title = "Copy Preset",
            initialValue = "$selectedPreset Copy",
            onConfirm = { name ->
                presetManager.save(name, steps.toList())
                selectedPreset = name
                showCopyDialog = false
            },
            onDismiss = { showCopyDialog = false }
        )
    }

    if (showRenameDialog) {
        TextInputDialog(
            title = "Rename Preset",
            initialValue = selectedPreset,
            onConfirm = { name ->
                presetManager.rename(selectedPreset, name)
                selectedPreset = name
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete \"$selectedPreset\"?") },
            text = {
                if (presetManager.isBuiltin(selectedPreset)) {
                    Text("This is a built-in preset. Deleting will restore it to defaults.")
                } else {
                    Text("This custom preset will be permanently removed.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    presetManager.delete(selectedPreset)
                    selectedPreset = allPresets.keys.firstOrNull() ?: "Christmas"
                    steps.clear()
                    steps.addAll(allPresets[selectedPreset] ?: emptyList())
                    showDeleteConfirm = false
                }) { Text("Delete", color = AccentColor) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// === Step Row ===
@Composable
private fun StepRow(
    index: Int,
    step: PatternStep,
    isActive: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onUpdate: (PatternStep) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    val bg = if (isActive) Color(0xFF2a2a4e) else Color(0xFF1a1a35)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        // Top row: index + color + hex + actions
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "${index + 1}",
                color = if (isActive) AccentColor else Color.Gray,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(20.dp)
            )

            // Color swatch
            val c = parseHexColor(step.color)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(c)
            )

            Spacer(Modifier.width(6.dp))

            // Hex color field
            var hexText by remember(step.color) { mutableStateOf(step.color.trimStart('#')) }
            OutlinedTextField(
                value = hexText,
                onValueChange = { v: String ->
                    val filtered = v.filter { c: Char -> c.isLetterOrDigit() }.take(6).uppercase()
                    hexText = filtered
                    if (filtered.length == 6) {
                        onUpdate(step.copy(color = "#$filtered"))
                    }
                },
                modifier = Modifier
                    .width(90.dp)
                    .height(40.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                singleLine = true,
                label = { Text("#", color = Color.Gray, style = MaterialTheme.typography.bodySmall) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentColor,
                    unfocusedBorderColor = Color(0xFF444444)
                ),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
            )

            Spacer(Modifier.weight(1f))

            // Action icons
            IconButton(onClick = onMoveUp, enabled = !isFirst, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, "Up",
                    tint = if (isFirst) Color(0xFF333333) else Color.Gray,
                    modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onMoveDown, enabled = !isLast, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, "Down",
                    tint = if (isLast) Color(0xFF333333) else Color.Gray,
                    modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, "Remove", tint = Color(0xFFCC4444), modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(4.dp))

        // Bottom row: brightness, duration, transition, fade time
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Brightness
            CompactNumberField(
                label = "Brt",
                value = step.brightness.toString(),
                suffixText = "%",
                onValueChange = {
                    it.toIntOrNull()?.coerceIn(0, 100)?.let { v ->
                        onUpdate(step.copy(brightness = v))
                    }
                },
                modifier = Modifier.width(58.dp)
            )

            // Duration
            CompactNumberField(
                label = "Dur",
                value = String.format("%.1f", step.duration),
                suffixText = "s",
                onValueChange = {
                    it.toFloatOrNull()?.coerceIn(0.1f, 60f)?.let { v ->
                        onUpdate(step.copy(duration = v))
                    }
                },
                modifier = Modifier.width(58.dp)
            )

            // Transition toggle
            val isFade = step.transition == "fade"
            FilterChip(
                selected = isFade,
                onClick = {
                    onUpdate(step.copy(transition = if (isFade) "cut" else "fade"))
                },
                label = {
                    Text(
                        if (isFade) "Fade" else "Cut",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                modifier = Modifier.height(28.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentColor.copy(alpha = 0.3f),
                    selectedLabelColor = AccentColor
                )
            )

            // Fade time (only if fade)
            if (isFade) {
                CompactNumberField(
                    label = "Fade",
                    value = String.format("%.1f", step.fadeTime),
                    suffixText = "s",
                    onValueChange = {
                        it.toFloatOrNull()?.coerceIn(0.1f, 10f)?.let { v ->
                            onUpdate(step.copy(fadeTime = v))
                        }
                    },
                    modifier = Modifier.width(58.dp)
                )
            }
        }
    }
}

// === Compact number input ===
@Composable
private fun CompactNumberField(
    label: String,
    value: String,
    suffixText: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember(value) { mutableStateOf(value) }

    Column(modifier = modifier) {
        Text(label, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        OutlinedTextField(
            value = text,
            onValueChange = { v: String ->
                text = v
                onValueChange(v)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
            singleLine = true,
            suffix = @Composable { Text(suffixText, color = Color.Gray, style = MaterialTheme.typography.labelSmall) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentColor,
                unfocusedBorderColor = Color(0xFF444444)
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
    }
}

// === Bulk Edit Panel ===
@Composable
private fun BulkEditPanel(onApply: (Int?, Float?, String?, Float?) -> Unit) {
    var brightness by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var transition by remember { mutableStateOf("") }
    var fadeTime by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1a1a35), RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        Text("Apply to all steps:", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            CompactNumberField("Brt%", brightness, "%",
                onValueChange = { brightness = it },
                modifier = Modifier.width(58.dp))
            CompactNumberField("Dur", duration, "s",
                onValueChange = { duration = it },
                modifier = Modifier.width(58.dp))

            Column(modifier = Modifier.width(58.dp)) {
                Text("Trans", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                Row {
                    FilterChip(
                        selected = transition == "cut",
                        onClick = { transition = if (transition == "cut") "" else "cut" },
                        label = { Text("C", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(28.dp)
                    )
                    FilterChip(
                        selected = transition == "fade",
                        onClick = { transition = if (transition == "fade") "" else "fade" },
                        label = { Text("F", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            CompactNumberField("Fade", fadeTime, "s",
                onValueChange = { fadeTime = it },
                modifier = Modifier.width(58.dp))
        }
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = {
                onApply(
                    brightness.toIntOrNull()?.coerceIn(0, 100),
                    duration.toFloatOrNull()?.coerceIn(0.1f, 60f),
                    transition.ifEmpty { null },
                    fadeTime.toFloatOrNull()?.coerceIn(0.1f, 10f)
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            Text("Apply", style = MaterialTheme.typography.labelMedium)
        }
    }
}

// === Small action button ===
@Composable
private fun SmallActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(32.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

// === Text input dialog ===
@Composable
private fun TextInputDialog(
    title: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled = text.isNotBlank()
            ) { Text("OK", color = AccentColor) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun parseHexColor(hex: String): Color {
    val h = hex.trimStart('#')
    return try {
        Color(
            red = h.substring(0, 2).toInt(16) / 255f,
            green = h.substring(2, 4).toInt(16) / 255f,
            blue = h.substring(4, 6).toInt(16) / 255f
        )
    } catch (_: Exception) {
        Color.Black
    }
}
