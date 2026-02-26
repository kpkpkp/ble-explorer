package com.elkbledom

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.elkbledom.ble.BleManager
import com.elkbledom.calibration.ColorCalibration
import com.elkbledom.control.DeviceControl
import com.elkbledom.control.HttpBridgeClient
import com.elkbledom.pattern.PatternEngine
import com.elkbledom.pattern.PresetManager
import com.elkbledom.ui.CalibrationWizardScreen
import com.elkbledom.ui.DashboardScreen
import com.elkbledom.ui.SettingsDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private lateinit var deviceControl: DeviceControl
    private var bleManager: BleManager? = null
    private lateinit var calibration: ColorCalibration
    private lateinit var patternEngine: PatternEngine
    private lateinit var presetManager: PresetManager
    private lateinit var sessionState: SessionState

    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // Once permissions are resolved, attempt auto-reconnect
        if (grants.values.all { it }) {
            launchAutoReconnect()
        }
    }

    // Camera permission launcher — on grant, set flag to show wizard
    private var pendingCameraAction: (() -> Unit)? = null
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingCameraAction?.invoke()
        }
        pendingCameraAction = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        calibration = ColorCalibration(this)
        presetManager = PresetManager(this)
        sessionState = SessionState(this)

        // Allow intent extras to override persisted mode (for adb launch)
        val controlMode = intent.getStringExtra("control_mode") ?: sessionState.controlMode
        val bridgeUrl = intent.getStringExtra("bridge_url") ?: sessionState.bridgeUrl
        // Persist intent overrides so they stick for future launches
        if (intent.hasExtra("control_mode")) sessionState.controlMode = controlMode
        if (intent.hasExtra("bridge_url")) sessionState.bridgeUrl = bridgeUrl
        Log.i(TAG, "Mode: controlMode=$controlMode bridgeUrl=$bridgeUrl")

        if (controlMode == "bridge" && bridgeUrl != null) {
            Log.i(TAG, "Starting in BRIDGE mode -> $bridgeUrl")
            val bridge = HttpBridgeClient(bridgeUrl)
            deviceControl = bridge
            // Seed state before UI renders (~100ms on LAN, 2s max)
            runBlocking(Dispatchers.IO) {
                withTimeoutOrNull(2000L) { bridge.fetchInitialStatus() }
            }
            bridge.startPolling()
            // No BLE permissions needed in bridge mode
        } else {
            val mgr = BleManager(this)
            bleManager = mgr
            deviceControl = mgr
            startLedService()
            if (hasRequiredPermissions()) {
                launchAutoReconnect()
            } else {
                requestBlePermissions()
            }
        }

        patternEngine = PatternEngine(deviceControl, calibration)
        patternEngine.observeBridgeRestarts(sessionState)
        patternEngine.syncFromBridge()

        val isBridgeMode = controlMode == "bridge"

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
                var showSettings by remember { mutableStateOf(false) }
                var showCalibrationWizard by remember { mutableStateOf(false) }

                if (showCalibrationWizard) {
                    CalibrationWizardScreen(
                        deviceControl = deviceControl,
                        calibration = calibration,
                        onFinish = { showCalibrationWizard = false }
                    )
                } else {
                    DashboardScreen(
                        deviceControl = deviceControl,
                        calibration = calibration,
                        patternEngine = patternEngine,
                        presetManager = presetManager,
                        sessionState = sessionState,
                        isBridgeMode = isBridgeMode,
                        onShowSettings = { showSettings = true },
                        onStopService = { stopLedService() },
                        onCalibrate = {
                            requestCameraPermissionAndRun {
                                showCalibrationWizard = true
                            }
                        }
                    )

                    if (showSettings) {
                        SettingsDialog(
                            sessionState = sessionState,
                            onDismiss = { showSettings = false },
                            onSave = { mode, url ->
                                showSettings = false
                                sessionState.controlMode = mode
                                sessionState.bridgeUrl = url
                                // Restart activity to apply mode change
                                val intent = intent
                                finish()
                                startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        saveSession()
    }

    override fun onDestroy() {
        if (sessionState.powerOffOnExit && deviceControl is HttpBridgeClient) {
            Log.i(TAG, "Power-off-on-exit: sending power off")
            deviceControl.sendPower(false)
        }
        if (deviceControl is HttpBridgeClient) {
            (deviceControl as HttpBridgeClient).stopPolling()
        }
        super.onDestroy()
    }

    private fun requestCameraPermissionAndRun(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingCameraAction = action
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun saveSession() {
        val addresses = deviceControl.getConnectedAddresses()
        val playback = patternEngine.playbackState.value
        sessionState.save(
            addresses = addresses,
            preset = sessionState.lastPreset,
            loop = sessionState.lastLoop,
            playing = playback.state == "playing"
        )
        Log.d(TAG, "Session saved: ${addresses.size} devices, playing=${playback.state == "playing"}")
    }

    private fun launchAutoReconnect() {
        val savedAddresses = sessionState.connectedAddresses
        if (savedAddresses.isEmpty()) return

        Log.d(TAG, "Auto-reconnecting to ${savedAddresses.size} device(s)")
        activityScope.launch {
            deviceControl.autoReconnect(savedAddresses)

            // If playback was active and at least one device reconnected, resume it
            if (sessionState.wasPlaying && deviceControl.getConnectedAddresses().isNotEmpty()) {
                val presetName = sessionState.lastPreset
                val loop = sessionState.lastLoop
                val presets = presetManager.allPresets.value
                val steps = presets[presetName]
                if (!steps.isNullOrEmpty()) {
                    Log.d(TAG, "Resuming playback: $presetName (loop=$loop)")
                    patternEngine.play(steps, loop)
                }
            }
        }
    }

    private fun startLedService() {
        val intent = Intent(this, LedService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopLedService() {
        deviceControl.disconnectAll()
        stopService(Intent(this, LedService::class.java))
    }

    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBlePermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
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
