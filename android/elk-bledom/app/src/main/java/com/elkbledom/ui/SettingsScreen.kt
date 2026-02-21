package com.elkbledom.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.elkbledom.SessionState
import com.elkbledom.control.BridgeDiscovery

private val AccentColor = Color(0xFFe94560)
private val CardColor = Color(0xFF16213e)

@Composable
fun SettingsDialog(
    sessionState: SessionState,
    bridgeDiscovery: BridgeDiscovery? = null,
    onDismiss: () -> Unit,
    onSave: (mode: String, bridgeUrl: String?) -> Unit
) {
    var selectedMode by remember { mutableStateOf(sessionState.controlMode) }
    var bridgeUrl by remember { mutableStateOf(sessionState.bridgeUrl ?: "") }
    var testResult by remember { mutableStateOf<String?>(null) }

    val bridges by bridgeDiscovery?.bridges?.collectAsState() ?: remember { mutableStateOf(emptyList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardColor,
        title = {
            Text("Control Mode", color = AccentColor)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Mode selector
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedMode == "ble",
                        onClick = { selectedMode = "ble" },
                        colors = RadioButtonDefaults.colors(selectedColor = AccentColor)
                    )
                    Text("BLE Direct", color = Color.White, modifier = Modifier.padding(start = 4.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedMode == "bridge",
                        onClick = { selectedMode = "bridge" },
                        colors = RadioButtonDefaults.colors(selectedColor = AccentColor)
                    )
                    Text("WiFi Bridge", color = Color.White, modifier = Modifier.padding(start = 4.dp))
                }

                if (selectedMode == "bridge") {
                    // Bridge URL input
                    OutlinedTextField(
                        value = bridgeUrl,
                        onValueChange = { bridgeUrl = it },
                        label = { Text("Bridge URL", color = Color.Gray) },
                        placeholder = { Text("http://192.168.x.x:80", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AccentColor,
                            unfocusedBorderColor = Color.Gray
                        )
                    )

                    // mDNS discovered bridges
                    if (bridges.isNotEmpty()) {
                        Text("Discovered bridges:", color = Color.Gray,
                            style = MaterialTheme.typography.labelSmall)
                        bridges.forEach { bridge ->
                            TextButton(
                                onClick = { bridgeUrl = bridge.url },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("${bridge.name} (${bridge.url})",
                                    color = AccentColor,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    // Discover button
                    if (bridgeDiscovery != null) {
                        Button(
                            onClick = { bridgeDiscovery.startDiscovery() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444444)),
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text("Discover Bridges", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    // Test result
                    testResult?.let {
                        Text(it, color = if (it.startsWith("OK")) Color(0xFF4CAF50) else Color(0xFFFF5252),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Restart notice
                Text(
                    "App will restart to apply mode change.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val url = if (selectedMode == "bridge" && bridgeUrl.isNotBlank()) bridgeUrl else null
                    onSave(selectedMode, url)
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        }
    )
}
