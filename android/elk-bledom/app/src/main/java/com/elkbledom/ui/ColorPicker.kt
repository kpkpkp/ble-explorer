package com.elkbledom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

private val SWATCH_COLORS = listOf(
    0xFFFF0000, 0xFFFF7F00, 0xFFFFFF00, 0xFF7FFF00,
    0xFF00FF00, 0xFF00FF7F, 0xFF00FFFF, 0xFF007FFF,
    0xFF0000FF, 0xFF7F00FF, 0xFFFF00FF, 0xFFFF007F,
    0xFFFFFFFF, 0xFFCCCCCC, 0xFF888888, 0xFF000000,
)

@Composable
fun ColorPicker(
    red: Int,
    green: Int,
    blue: Int,
    correctedRed: Int,
    correctedGreen: Int,
    correctedBlue: Int,
    calibrationEnabled: Boolean,
    onColorChange: (Int, Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var hexInput by remember(red, green, blue) {
        mutableStateOf(String.format("%02X%02X%02X", red, green, blue))
    }

    Column(modifier = modifier) {
        // Color swatch grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier.height(80.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(SWATCH_COLORS) { colorLong ->
                val color = Color(colorLong)
                val r = ((colorLong shr 16) and 0xFF).toInt()
                val g = ((colorLong shr 8) and 0xFF).toInt()
                val b = (colorLong and 0xFF).toInt()
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (r == red && g == green && b == blue) 2.dp else 0.dp,
                            color = Color.White,
                            shape = CircleShape
                        )
                        .clickable { onColorChange(r, g, b) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // RGB sliders
        SliderRow("R", red, Color(0xFFFF4444)) { onColorChange(it, green, blue) }
        SliderRow("G", green, Color(0xFF44FF44)) { onColorChange(red, it, blue) }
        SliderRow("B", blue, Color(0xFF4444FF)) { onColorChange(red, green, it) }

        Spacer(Modifier.height(8.dp))

        // Hex input + preview swatches
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = hexInput,
                onValueChange = { value ->
                    val filtered = value.filter { it.isLetterOrDigit() }.take(6).uppercase()
                    hexInput = filtered
                    if (filtered.length == 6) {
                        try {
                            val r = filtered.substring(0, 2).toInt(16)
                            val g = filtered.substring(2, 4).toInt(16)
                            val b = filtered.substring(4, 6).toInt(16)
                            onColorChange(r, g, b)
                        } catch (_: NumberFormatException) {}
                    }
                },
                label = { Text("#") },
                modifier = Modifier.width(120.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFe94560),
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color(0xFFe94560),
                    unfocusedLabelColor = Color.Gray
                )
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Input", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(red, green, blue))
                )
            }

            if (calibrationEnabled) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Output", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(correctedRed, correctedGreen, correctedBlue))
                    )
                }
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Int,
    color: Color,
    onValueChange: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            label,
            color = color,
            modifier = Modifier.width(20.dp),
            style = MaterialTheme.typography.labelMedium
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color
            )
        )
        Text(
            String.format("%3d", value),
            color = Color.White,
            modifier = Modifier.width(36.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}
