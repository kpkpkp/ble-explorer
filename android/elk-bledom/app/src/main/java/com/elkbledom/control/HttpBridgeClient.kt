package com.elkbledom.control

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class HttpBridgeClient(private val baseUrl: String) : DeviceControl {
    private val TAG = "HttpBridge"
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val JSON_TYPE = "application/json".toMediaType()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    override val connectionStates: StateFlow<Map<String, ConnectionState>> = _connectionStates.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    override val target = MutableStateFlow(Target.ALL)

    private val _bridgeStatus = MutableStateFlow<BridgeStatus?>(null)
    override val bridgeStatus: StateFlow<BridgeStatus?> = _bridgeStatus.asStateFlow()

    private val _stripRssi = MutableStateFlow<Map<String, Int>>(emptyMap())
    override val stripRssi: StateFlow<Map<String, Int>> = _stripRssi.asStateFlow()

    private val _stripNames = MutableStateFlow<Map<String, String>>(emptyMap())
    override val stripNames: StateFlow<Map<String, String>> = _stripNames.asStateFlow()

    private val _stripDeviceStates = MutableStateFlow<Map<String, StripDeviceState>>(emptyMap())
    override val stripDeviceStates: StateFlow<Map<String, StripDeviceState>> = _stripDeviceStates.asStateFlow()

    private var statusPollJob: Job? = null
    private var _lastKnownUptime: Long = -1

    private val _bridgeRestarted = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val bridgeRestarted: SharedFlow<Unit> = _bridgeRestarted.asSharedFlow()

    fun startPolling() {
        statusPollJob?.cancel()
        statusPollJob = scope.launch {
            while (isActive) {
                try {
                    pollStatus()
                } catch (e: Exception) {
                    Log.w(TAG, "Status poll failed: ${e.message}")
                }
                delay(1000)
            }
        }
    }

    fun stopPolling() {
        statusPollJob?.cancel()
        statusPollJob = null
    }

    private fun pollStatus() {
        val response = get("/status") ?: return
        val data = response.optJSONObject("data") ?: return

        val wifi = data.optJSONObject("wifi")
        val pattern = data.optJSONObject("pattern")
        val uptime = data.optLong("uptime", 0)
        _bridgeStatus.value = BridgeStatus(
            wifiState = wifi?.optString("state", "unknown") ?: "unknown",
            wifiSSID = wifi?.optString("ssid", "") ?: "",
            wifiIP = wifi?.optString("ip", "") ?: "",
            wifiRSSI = wifi?.optInt("rssi", 0) ?: 0,
            firmwareVersion = data.optString("firmware", "?"),
            uptime = uptime,
            freeHeap = data.optLong("freeHeap", 0),
            playbackState = pattern?.optString("state", "idle") ?: "idle",
            playbackStep = pattern?.optInt("currentStep", 0) ?: 0,
            playbackTotal = pattern?.optInt("totalSteps", 0) ?: 0,
            playbackLoop = pattern?.optBoolean("loop", false) ?: false,
            presetName = pattern?.optString("presetName", "") ?: ""
        )

        // Detect bridge restart: uptime went backwards
        if (_lastKnownUptime > 0 && uptime < _lastKnownUptime) {
            Log.w(TAG, "Bridge restarted! uptime=$uptime < lastKnown=$_lastKnownUptime")
            _bridgeRestarted.tryEmit(Unit)
        }
        _lastKnownUptime = uptime

        val ble = data.optJSONObject("ble")
        val strips = ble?.optJSONArray("strips")
        if (strips != null) {
            val states = mutableMapOf<String, ConnectionState>()
            val rssiMap = mutableMapOf<String, Int>()
            val nameMap = mutableMapOf<String, String>()
            val deviceStates = mutableMapOf<String, StripDeviceState>()
            for (i in 0 until strips.length()) {
                val strip = strips.getJSONObject(i)
                val addr = strip.getString("address")
                val state = when (strip.getString("state")) {
                    "ready" -> ConnectionState.CONNECTED
                    "connecting", "discovering" -> ConnectionState.CONNECTING
                    else -> ConnectionState.DISCONNECTED
                }
                states[addr] = state
                val rssi = strip.optInt("rssi", 0)
                if (rssi != 0) rssiMap[addr] = rssi
                val name = strip.optString("name", "")
                if (name.isNotEmpty()) nameMap[addr] = name
                // Parse per-strip device state
                if (strip.has("r")) {
                    deviceStates[addr] = StripDeviceState(
                        r = strip.optInt("r", 255),
                        g = strip.optInt("g", 255),
                        b = strip.optInt("b", 255),
                        brightness = strip.optInt("brightness", 100),
                        power = strip.optBoolean("power", true),
                        effectCode = strip.optInt("effectCode", 0),
                        effectSpeed = strip.optInt("effectSpeed", 3),
                        mode = strip.optString("mode", "color")
                    )
                }
            }
            _connectionStates.value = states
            _stripRssi.value = rssiMap
            _stripNames.value = nameMap
            _stripDeviceStates.value = deviceStates
        }
    }

    override fun scan(timeout: Long) {
        _isScanning.value = true
        scope.launch {
            try {
                // First call triggers async scan on bridge
                get("/api/scan")

                // Poll until scan completes (~5s scan + WiFi recovery on shared radio)
                for (attempt in 1..12) {
                    delay(2000)
                    val response = get("/api/scan") ?: continue
                    val data = response.optJSONObject("data") ?: continue
                    val scanning = data.optBoolean("scanning", true)

                    if (!scanning) {
                        val devices = data.optJSONArray("devices")
                        if (devices != null) {
                            val list = mutableListOf<DiscoveredDevice>()
                            for (i in 0 until devices.length()) {
                                val d = devices.getJSONObject(i)
                                list.add(DiscoveredDevice(
                                    name = d.getString("name"),
                                    address = d.getString("address"),
                                    rssi = d.optInt("rssi", 0)
                                ))
                            }
                            _discoveredDevices.value = list
                        }
                        return@launch
                    }
                }
                Log.w(TAG, "Scan timed out after polling")
            } catch (e: Exception) {
                Log.e(TAG, "Scan failed: ${e.message}")
            } finally {
                _isScanning.value = false
            }
        }
    }

    override fun connect(address: String) {
        post("/api/connect", JSONObject().put("address", address))
    }

    override fun disconnect(address: String) {
        post("/api/disconnect", JSONObject().put("address", address))
    }

    override fun disconnectAll() {
        getConnectedAddresses().forEach { disconnect(it) }
    }

    override suspend fun autoReconnect(addresses: List<String>, maxRetries: Int) {
        addresses.forEach { connect(it) }
    }

    override fun getConnectedAddresses(): List<String> =
        _connectionStates.value.filter { it.value == ConnectionState.CONNECTED }.keys.toList()

    override fun slotFor(address: String): String? {
        val connected = _connectionStates.value.keys.toList()
        val idx = connected.indexOf(address)
        return if (idx >= 0) "ELK-BLEDOM STRIP${idx + 1}" else null
    }

    private fun targetString(): String = when (target.value) {
        Target.ALL -> "all"
        Target.STRIP1 -> "strip1"
        Target.STRIP2 -> "strip2"
    }

    override fun sendColor(r: Int, g: Int, b: Int, intentR: Int, intentG: Int, intentB: Int) {
        val json = JSONObject()
            .put("r", r).put("g", g).put("b", b)
            .put("target", targetString())
        if (intentR != r || intentG != g || intentB != b) {
            json.put("ir", intentR).put("ig", intentG).put("ib", intentB)
        }
        post("/api/color", json)
    }

    override fun sendBrightness(level: Int) {
        post("/api/brightness", JSONObject()
            .put("level", level)
            .put("target", targetString()))
    }

    override fun sendPower(on: Boolean) {
        post("/api/power", JSONObject()
            .put("on", on)
            .put("target", targetString()))
    }

    override fun sendEffect(code: Int, speed: Int) {
        post("/api/effect", JSONObject()
            .put("code", code).put("speed", speed)
            .put("target", targetString()))
    }

    override fun sendSpeed(speed: Int) {
        post("/api/speed", JSONObject()
            .put("speed", speed)
            .put("target", targetString()))
    }

    override fun sendColorTemp(warm: Int, cold: Int) {
        post("/api/colortemp", JSONObject()
            .put("warm", warm).put("cold", cold)
            .put("target", targetString()))
    }

    override fun sendRaw(command: ByteArray) {
        val hex = command.joinToString("") { "%02X".format(it) }
        post("/api/raw", JSONObject()
            .put("hex", hex)
            .put("target", targetString()))
    }

    override suspend fun sendColorSuspend(r: Int, g: Int, b: Int) {
        sendColor(r, g, b)
    }

    override suspend fun sendBrightnessSuspend(level: Int) {
        sendBrightness(level)
    }

    override fun sendPattern(steps: List<PatternStepData>, loop: Boolean, name: String) {
        val stepsArray = JSONArray()
        for (step in steps) {
            stepsArray.put(JSONObject()
                .put("r", step.r).put("g", step.g).put("b", step.b)
                .put("brightness", step.brightness)
                .put("duration", step.duration)
                .put("transition", step.transition)
                .put("fadeTime", step.fadeTime))
        }
        val json = JSONObject()
            .put("steps", stepsArray)
            .put("loop", loop)
            .put("target", targetString())
        if (name.isNotEmpty()) json.put("name", name)
        post("/api/pattern", json)
    }

    override fun stopPattern() {
        post("/api/stop", JSONObject().put("target", targetString()))
    }

    fun saveStripNames(names: Map<String, String>) {
        val addresses = _connectionStates.value.keys.toList()
        val json = JSONObject()
        addresses.forEachIndexed { index, addr ->
            val name = names[addr] ?: return@forEachIndexed
            json.put("strip${index + 1}_name", name)
        }
        if (json.length() > 0) {
            post("/api/config", json)
        }
    }

    // HTTP helpers
    private fun get(path: String): JSONObject? {
        return try {
            val request = Request.Builder().url("$baseUrl$path").build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            if (body != null) JSONObject(body) else null
        } catch (e: Exception) {
            Log.e(TAG, "GET $path failed: ${e.message}")
            null
        }
    }

    private fun post(path: String, json: JSONObject) {
        scope.launch {
            try {
                val body = json.toString().toRequestBody(JSON_TYPE)
                val request = Request.Builder().url("$baseUrl$path").post(body).build()
                client.newCall(request).execute().close()
            } catch (e: Exception) {
                Log.e(TAG, "POST $path failed: ${e.message}")
            }
        }
    }
}
