package com.elkbledom.control

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket

class BridgeDiscovery(context: Context) {
    private val TAG = "BridgeDiscovery"
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _bridges = MutableStateFlow<List<BridgeInfo>>(emptyList())
    val bridges: StateFlow<List<BridgeInfo>> = _bridges.asStateFlow()

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var beaconJob: Job? = null

    companion object {
        const val BEACON_PORT = 5555
    }

    data class BridgeInfo(
        val name: String,
        val host: String,
        val port: Int
    ) {
        val url: String get() = "http://$host:$port"
    }

    fun startDiscovery() {
        stopDiscovery()
        _bridges.value = emptyList()

        // Start mDNS discovery
        startNsdDiscovery()

        // Start UDP beacon listener as fallback
        startBeaconListener()
    }

    private fun startBeaconListener() {
        beaconJob = scope.launch {
            try {
                val socket = DatagramSocket(BEACON_PORT)
                socket.soTimeout = 1000
                val buf = ByteArray(512)
                Log.d(TAG, "UDP beacon listener started on port $BEACON_PORT")

                while (isActive) {
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        socket.receive(packet)
                        val data = String(packet.data, 0, packet.length)
                        val json = JSONObject(data)

                        if (json.optString("name") == "ELK-Bridge") {
                            val host = json.getString("ip")
                            val port = json.getInt("port")
                            Log.d(TAG, "Beacon from $host:$port")

                            val bridge = BridgeInfo(
                                name = json.getString("name"),
                                host = host,
                                port = port
                            )
                            // Add if not already present
                            val current = _bridges.value
                            if (current.none { it.host == host && it.port == port }) {
                                _bridges.value = current + bridge
                            }
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        // Normal — just loop and check isActive
                    }
                }
                socket.close()
            } catch (e: Exception) {
                Log.w(TAG, "Beacon listener error: ${e.message}")
            }
        }
    }

    private fun startNsdDiscovery() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "mDNS discovery started")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${serviceInfo.serviceName}")
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.w(TAG, "Resolve failed: $errorCode")
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val host = serviceInfo.host?.hostAddress ?: return
                        val port = serviceInfo.port
                        Log.d(TAG, "Resolved: $host:$port")
                        val bridge = BridgeInfo(
                            name = serviceInfo.serviceName,
                            host = host,
                            port = port
                        )
                        val current = _bridges.value
                        if (current.none { it.host == host && it.port == port }) {
                            _bridges.value = current + bridge
                        }
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "mDNS discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "mDNS discovery start failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "mDNS discovery stop failed: $errorCode")
            }
        }

        discoveryListener = listener
        nsdManager.discoverServices("_elk._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stopDiscovery() {
        beaconJob?.cancel()
        beaconJob = null

        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.w(TAG, "Stop discovery failed: ${e.message}")
            }
        }
        discoveryListener = null
    }
}
