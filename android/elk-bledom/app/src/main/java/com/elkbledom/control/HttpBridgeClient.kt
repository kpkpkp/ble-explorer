package com.elkbledom.control

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
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

    private var statusPollJob: Job? = null

    fun startPolling() {
        statusPollJob?.cancel()
        statusPollJob = scope.launch {
            while (isActive) {
                try {
                    pollStatus()
                } catch (e: Exception) {
                    Log.w(TAG, "Status poll failed: ${e.message}")
                }
                delay(2000)
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
        val playback = data.optJSONObject("playback")
        val firmware = data.optJSONObject("firmware")
        _bridgeStatus.value = BridgeStatus(
            wifiState = wifi?.optString("state", "unknown") ?: "unknown",
            wifiSSID = wifi?.optString("ssid", "") ?: "",
            wifiIP = wifi?.optString("ip", "") ?: "",
            wifiRSSI = wifi?.optInt("rssi", 0) ?: 0,
            firmwareVersion = firmware?.optString("version", "?") ?: "?",
            uptime = firmware?.optLong("uptime", 0) ?: 0,
            freeHeap = firmware?.optLong("freeHeap", 0) ?: 0,
            playbackState = playback?.optString("state", "idle") ?: "idle",
            playbackStep = playback?.optInt("currentStep", 0) ?: 0,
            playbackTotal = playback?.optInt("totalSteps", 0) ?: 0,
            playbackLoop = playback?.optBoolean("loop", false) ?: false
        )

        val ble = data.optJSONObject("ble")
        val strips = ble?.optJSONArray("strips")
        if (strips != null) {
            val states = mutableMapOf<String, ConnectionState>()
            for (i in 0 until strips.length()) {
                val strip = strips.getJSONObject(i)
                val addr = strip.getString("address")
                val state = when (strip.getString("state")) {
                    "ready" -> ConnectionState.CONNECTED
                    "connecting", "discovering" -> ConnectionState.CONNECTING
                    else -> ConnectionState.DISCONNECTED
                }
                states[addr] = state
            }
            _connectionStates.value = states
        }
    }

    override fun scan(timeout: Long) {
        _isScanning.value = true
        scope.launch {
            try {
                val response = get("/api/scan")
                val data = response?.optJSONObject("data")
                val devices = data?.optJSONArray("devices")
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

    override fun sendColor(r: Int, g: Int, b: Int) {
        post("/api/color", JSONObject()
            .put("r", r).put("g", g).put("b", b)
            .put("target", targetString()))
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

    override fun sendPattern(steps: List<PatternStepData>, loop: Boolean) {
        val stepsArray = JSONArray()
        for (step in steps) {
            stepsArray.put(JSONObject()
                .put("r", step.r).put("g", step.g).put("b", step.b)
                .put("brightness", step.brightness)
                .put("duration", step.duration)
                .put("transition", step.transition)
                .put("fadeTime", step.fadeTime))
        }
        post("/api/pattern", JSONObject()
            .put("steps", stepsArray)
            .put("loop", loop)
            .put("target", targetString()))
    }

    override fun stopPattern() {
        post("/api/stop", JSONObject().put("target", targetString()))
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
