package com.elkbledom

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.elkbledom.ble.BleManager
import com.elkbledom.calibration.ColorCalibration
import com.elkbledom.pattern.PatternEngine
import com.elkbledom.pattern.PresetManager
import com.elkbledom.ui.DashboardScreen

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: BleManager
    private lateinit var calibration: ColorCalibration
    private lateinit var patternEngine: PatternEngine
    private lateinit var presetManager: PresetManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions granted or denied — BLE ops check at runtime */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bleManager = BleManager(this)
        calibration = ColorCalibration(this)
        patternEngine = PatternEngine(bleManager, calibration)
        presetManager = PresetManager(this)

        requestBlePermissions()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFe94560),
                    background = Color(0xFF1a1a2e),
                    surface = Color(0xFF16213e),
                    onPrimary = Color.White,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                DashboardScreen(
                    bleManager = bleManager,
                    calibration = calibration,
                    patternEngine = patternEngine,
                    presetManager = presetManager
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.disconnectAll()
    }

    private fun requestBlePermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            // Android 11 and below — need location for BLE scanning
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}
