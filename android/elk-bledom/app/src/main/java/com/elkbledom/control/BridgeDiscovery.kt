package com.elkbledom.control

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BridgeDiscovery(context: Context) {
    private val TAG = "BridgeDiscovery"
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _bridges = MutableStateFlow<List<BridgeInfo>>(emptyList())
    val bridges: StateFlow<List<BridgeInfo>> = _bridges.asStateFlow()

    private var discoveryListener: NsdManager.DiscoveryListener? = null

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

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "Discovery started")
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
                        _bridges.value = _bridges.value + bridge
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: $errorCode")
            }
        }

        discoveryListener = listener
        nsdManager.discoverServices("_elk._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stopDiscovery() {
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
