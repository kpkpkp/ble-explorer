package com.elkbledom.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
    initialPreset: String = "St. Patrick's Day",
    onPlay: (List<PatternStep>, Boolean, String) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allPresets by presetManager.allPresets.collectAsState()
    var selectedPreset by remember { mutableStateOf(initialPreset) }
    var loop by remember { mutableStateOf(true) }
    var presetExpanded by remember { mutableStateOf(false) }
    val isPlaying = playbackState.state == "playing"

    val steps = remember { mutableStateListOf<PatternStep>() }
    var initialized by remember { mutableStateOf(false) }
    if (!initialized && allPresets.isNotEmpty()) {
        steps.clear()
        steps.addAll(allPresets[selectedPreset] ?: emptyList())
        initialized = true
    }

    var showSaveDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCopyDialog by remember { mutableStateOf(false) }
    var showBulkEdit by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // === Preset selector ===
        ExposedDropdownMenuBox(
            expanded = presetExpanded,
            onExpandedChange = { presetExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedPreset,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(presetExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                textStyle = MaterialTheme.typography.bodySmall,
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

        Spacer(Modifier.height(6.dp))

        // === Step list (single-row per step) ===
        LazyColumn(
            modifier = Modifier.heightIn(max = 300.dp),
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

        Spacer(Modifier.height(6.dp))

        // === Add Step + Bulk Edit ===
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { steps.add(PatternStep("#FF0000", 100, 2.0f, "cut", 0.5f)) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentColor),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = SolidColor(AccentColor)
                ),
                modifier = Modifier.height(30.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Step", style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = { showBulkEdit = !showBulkEdit }) {
                Text(
                    if (showBulkEdit) "Hide Bulk Edit" else "Bulk Edit",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

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

        Spacer(Modifier.height(6.dp))

        // === Playback controls ===
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isPlaying) {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Icon(Icons.Default.Stop, "Stop", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Stop", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Button(
                    onClick = { if (steps.isNotEmpty()) onPlay(steps.toList(), loop, selectedPreset) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier.height(34.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, "Play", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Play", style = MaterialTheme.typography.labelMedium)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = loop,
                    onCheckedChange = { loop = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = AccentColor,
                        uncheckedColor = Color.Gray
                    ),
                    modifier = Modifier.size(30.dp)
                )
                Text("Loop", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            if (isPlaying) {
                Text(
                    "${playbackState.currentStep}/${playbackState.totalSteps}",
                    color = AccentColor,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }

    // === Dialogs ===
    if (showSaveDialog) {
        TextInputDialog("Save Preset", selectedPreset,
            onConfirm = { name ->
                presetManager.save(name, steps.toList())
                selectedPreset = name
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false }
        )
    }
    if (showCopyDialog) {
        TextInputDialog("Copy Preset", "$selectedPreset Copy",
            onConfirm = { name ->
                presetManager.save(name, steps.toList())
                selectedPreset = name
                showCopyDialog = false
            },
            onDismiss = { showCopyDialog = false }
        )
    }
    if (showRenameDialog) {
        TextInputDialog("Rename Preset", selectedPreset,
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
                Text(
                    if (presetManager.isBuiltin(selectedPreset))
                        "This is a built-in preset. Deleting will restore it to defaults."
                    else "This custom preset will be permanently removed."
                )
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

// === Inline editable field using BasicTextField (compact, visible text) ===
@Composable
private fun InlineField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    prefix: String = "",
    suffix: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    filter: ((String) -> String)? = null
) {
    var text by remember(value) { mutableStateOf(value) }
    Row(
        modifier = modifier
            .height(24.dp)
            .background(Color(0xFF111128), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (prefix.isNotEmpty()) {
            Text(prefix, color = Color(0xFF666688), style = MaterialTheme.typography.labelSmall)
        }
        BasicTextField(
            value = text,
            onValueChange = { v: String ->
                val processed = filter?.invoke(v) ?: v
                text = processed
                onValueChange(processed)
            },
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.labelMedium.copy(color = Color.White),
            singleLine = true,
            cursorBrush = SolidColor(AccentColor),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                capitalization = capitalization
            )
        )
        if (suffix.isNotEmpty()) {
            Text(suffix, color = Color(0xFF666688), style = MaterialTheme.typography.labelSmall)
        }
    }
}

// === Single-row step ===
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
    val c = parseHexColor(step.color)
    val isFade = step.transition == "fade"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            "${index + 1}",
            color = if (isActive) AccentColor else Color.Gray,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(14.dp)
        )
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(c)
        )
        InlineField(
            value = step.color.trimStart('#'),
            onValueChange = { filtered ->
                if (filtered.length == 6) {
                    onUpdate(step.copy(color = "#$filtered"))
                }
            },
            prefix = "#",
            modifier = Modifier.width(62.dp),
            capitalization = KeyboardCapitalization.Characters,
            filter = { it.filter { ch: Char -> ch.isLetterOrDigit() }.take(6).uppercase() }
        )
        InlineField(
            value = step.brightness.toString(),
            onValueChange = {
                it.toIntOrNull()?.coerceIn(0, 100)?.let { v ->
                    onUpdate(step.copy(brightness = v))
                }
            },
            suffix = "%",
            modifier = Modifier.width(40.dp),
            keyboardType = KeyboardType.Number
        )
        InlineField(
            value = String.format("%.1f", step.duration),
            onValueChange = {
                it.toFloatOrNull()?.coerceIn(0.1f, 60f)?.let { v ->
                    onUpdate(step.copy(duration = v))
                }
            },
            suffix = "s",
            modifier = Modifier.width(36.dp),
            keyboardType = KeyboardType.Decimal
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (isFade) AccentColor.copy(alpha = 0.25f) else Color(0xFF252540))
                .clickable { onUpdate(step.copy(transition = if (isFade) "cut" else "fade")) }
                .padding(horizontal = 5.dp, vertical = 3.dp)
        ) {
            Text(
                if (isFade) "F" else "C",
                color = if (isFade) AccentColor else Color.Gray,
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (isFade) {
            InlineField(
                value = String.format("%.1f", step.fadeTime),
                onValueChange = {
                    it.toFloatOrNull()?.coerceIn(0.1f, 10f)?.let { v ->
                        onUpdate(step.copy(fadeTime = v))
                    }
                },
                suffix = "s",
                modifier = Modifier.width(34.dp),
                keyboardType = KeyboardType.Decimal
            )
        }
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Default.KeyboardArrowUp, "Up",
            tint = if (isFirst) Color(0xFF222233) else Color.Gray,
            modifier = Modifier.size(20.dp).clickable(enabled = !isFirst) { onMoveUp() }
        )
        Icon(
            Icons.Default.KeyboardArrowDown, "Down",
            tint = if (isLast) Color(0xFF222233) else Color.Gray,
            modifier = Modifier.size(20.dp).clickable(enabled = !isLast) { onMoveDown() }
        )
        Icon(
            Icons.Default.Close, "Remove",
            tint = Color(0xFFCC4444),
            modifier = Modifier.size(16.dp).clickable { onRemove() }
        )
    }
}

// === Compact number field (for bulk edit panel) ===
@Composable
private fun CompactNumberField(
    label: String,
    value: String,
    suffixText: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(label, color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        InlineField(
            value = value,
            onValueChange = onValueChange,
            suffix = suffixText,
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Decimal
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
                        modifier = Modifier.height(24.dp)
                    )
                    FilterChip(
                        selected = transition == "fade",
                        onClick = { transition = if (transition == "fade") "" else "fade" },
                        label = { Text("F", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(24.dp)
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
            modifier = Modifier.height(28.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) { Text("Apply", style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun SmallActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(28.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

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
