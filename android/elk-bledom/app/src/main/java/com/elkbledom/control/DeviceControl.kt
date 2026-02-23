package com.elkbledom.control

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

enum class Target {
    ALL,
    STRIP1,
    STRIP2,
}

data class DiscoveredDevice(
    val name: String,
    val address: String,
    val rssi: Int = 0
)

data class BridgeStatus(
    val wifiState: String,
    val wifiSSID: String,
    val wifiIP: String,
    val wifiRSSI: Int,
    val firmwareVersion: String,
    val uptime: Long,
    val freeHeap: Long,
    val playbackState: String,
    val playbackStep: Int,
    val playbackTotal: Int,
    val playbackLoop: Boolean
)

data class PatternStepData(
    val r: Int,
    val g: Int,
    val b: Int,
    val brightness: Int,
    val duration: Int,
    val transition: String,
    val fadeTime: Int
)

interface DeviceControl {
    val connectionStates: StateFlow<Map<String, ConnectionState>>
    val discoveredDevices: StateFlow<List<DiscoveredDevice>>
    val isScanning: StateFlow<Boolean>
    val target: MutableStateFlow<Target>

    fun scan(timeout: Long = 5000)
    fun connect(address: String)
    fun disconnect(address: String)
    fun disconnectAll()
    suspend fun autoReconnect(addresses: List<String>, maxRetries: Int = 3)
    fun getConnectedAddresses(): List<String>
    fun slotFor(address: String): String?

    fun sendColor(r: Int, g: Int, b: Int)
    fun sendBrightness(level: Int)
    fun sendPower(on: Boolean)
    fun sendEffect(code: Int, speed: Int)
    fun sendSpeed(speed: Int)
    fun sendColorTemp(warm: Int, cold: Int)
    fun sendRaw(command: ByteArray)
    suspend fun sendColorSuspend(r: Int, g: Int, b: Int)
    suspend fun sendBrightnessSuspend(level: Int)

    fun sendPattern(steps: List<PatternStepData>, loop: Boolean)
    fun stopPattern()

    val bridgeStatus: StateFlow<BridgeStatus?>
    val stripRssi: StateFlow<Map<String, Int>>  // address → dBm
}
