package com.elkbledom.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "BleManager"
private const val GATT_TIMEOUT_MS = 10_000L
private const val COMMAND_RETRY_COUNT = 2
private const val COMMAND_RETRY_DELAY_MS = 50L
private const val DEVICE_NAME_PREFIX = "ELK-BLEDOM"

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

/**
 * Android BLE manager for ELK-BLEDOM LED strips.
 *
 * Supports connecting to up to 2 strips simultaneously. GATT operations
 * are serialized per-connection using a [Mutex] to avoid Android BLE stack
 * issues with concurrent operations. Each command is sent twice with a
 * 50ms gap for reliability, matching the behaviour observed in the Lotus
 * Lantern reference app.
 *
 * Permissions (BLUETOOTH, BLUETOOTH_ADMIN, ACCESS_FINE_LOCATION for
 * SDK < 31) are expected to be requested and granted at the Activity level.
 */
@SuppressLint("MissingPermission")
class BleManager(context: Context) {

    private val appContext: Context = context.applicationContext
    private val bluetoothManager: BluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // --- Public state flows ---

    private val _scanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _connectionStates = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, ConnectionState>> = _connectionStates.asStateFlow()

    val target = MutableStateFlow(Target.ALL)

    // --- Internal connection bookkeeping ---

    /** Address of the first strip that connected (STRIP1). */
    private var strip1Address: String? = null

    /** Address of the second strip that connected (STRIP2). */
    private var strip2Address: String? = null

    /** Active GATT connections keyed by device address. */
    private val gattMap = mutableMapOf<String, BluetoothGatt>()

    /** Discovered write characteristics keyed by device address. */
    private val writeCharMap = mutableMapOf<String, BluetoothGattCharacteristic>()

    /** Per-connection mutex to serialize GATT operations. */
    private val gattMutexMap = mutableMapOf<String, Mutex>()

    /**
     * Pending continuations for service discovery, keyed by device address.
     * The GATT callback resumes these when onServicesDiscovered fires.
     */
    private val serviceDiscoveryContinuations =
        mutableMapOf<String, CancellableContinuation<Boolean>>()

    /**
     * Pending continuations for characteristic writes, keyed by device address.
     * The GATT callback resumes these when onCharacteristicWrite fires.
     */
    private val writeContinuations =
        mutableMapOf<String, CancellableContinuation<Boolean>>()

    // -----------------------------------------------------------------------
    // Scanning
    // -----------------------------------------------------------------------

    private var scanCallback: ScanCallback? = null

    /**
     * Start a BLE scan filtered by the "ELK-BLEDOM" name prefix. Discovered
     * devices are emitted through [discoveredDevices]. The scan automatically
     * stops after [timeout] milliseconds.
     */
    fun scan(timeout: Long = 5000) {
        val leScanner = scanner
        if (leScanner == null) {
            Log.w(TAG, "BluetoothLeScanner not available")
            return
        }
        if (_scanning.value) {
            Log.d(TAG, "Scan already in progress")
            return
        }

        _discoveredDevices.value = emptyList()
        val seen = mutableSetOf<String>()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val address = device.address
                if (address in seen) return
                val name = result.scanRecord?.deviceName ?: device.name ?: return
                if (!name.startsWith(DEVICE_NAME_PREFIX)) return
                seen.add(address)
                Log.d(TAG, "Discovered $name ($address)")
                _discoveredDevices.value = _discoveredDevices.value + device
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed with error code $errorCode")
                _scanning.value = false
            }
        }
        scanCallback = callback

        // Use an empty filter list so all advertisements are delivered, then
        // filter by name in onScanResult. ELK-BLEDOM devices do not always
        // include their name in the advertising data fields that ScanFilter
        // checks, so a name-based ScanFilter can miss them.
        val filters = listOf(
            ScanFilter.Builder().build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        _scanning.value = true
        leScanner.startScan(filters, settings, callback)
        Log.d(TAG, "Scan started (timeout=${timeout}ms)")

        scope.launch {
            delay(timeout)
            stopScan()
        }
    }

    /** Stop an in-progress scan. Safe to call even if no scan is running. */
    fun stopScan() {
        val leScanner = scanner
        val cb = scanCallback
        if (leScanner != null && cb != null) {
            leScanner.stopScan(cb)
            scanCallback = null
            Log.d(TAG, "Scan stopped")
        }
        _scanning.value = false
    }

    // -----------------------------------------------------------------------
    // Connection
    // -----------------------------------------------------------------------

    /**
     * Connect to an ELK-BLEDOM device. Performs GATT connection, service
     * discovery, and locates the write characteristic on service 0xFFF0.
     * The result is reflected in [connectionStates].
     */
    fun connect(device: BluetoothDevice) {
        val address = device.address
        if (gattMap.containsKey(address)) {
            Log.w(TAG, "Already connected to $address")
            return
        }

        updateConnectionState(address, ConnectionState.CONNECTING)

        scope.launch {
            // Step 1 -- establish the GATT connection
            val gatt = connectGatt(device)
            if (gatt == null) {
                Log.e(TAG, "GATT connection failed for $address")
                updateConnectionState(address, ConnectionState.DISCONNECTED)
                return@launch
            }

            // Step 2 -- discover services
            val discovered = discoverServices(gatt, address)
            if (!discovered) {
                Log.e(TAG, "Service discovery failed for $address")
                gatt.close()
                updateConnectionState(address, ConnectionState.DISCONNECTED)
                return@launch
            }

            // Step 3 -- locate the ELK-BLEDOM service and write characteristic
            val service = gatt.getService(ElkProtocol.SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "Service ${ElkProtocol.SERVICE_UUID} not found on $address")
                gatt.close()
                updateConnectionState(address, ConnectionState.DISCONNECTED)
                return@launch
            }

            val writeChar = service.getCharacteristic(ElkProtocol.WRITE_CHAR_UUID)
            if (writeChar == null) {
                Log.e(TAG, "Write char ${ElkProtocol.WRITE_CHAR_UUID} not found on $address")
                gatt.close()
                updateConnectionState(address, ConnectionState.DISCONNECTED)
                return@launch
            }

            writeChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE

            // Store connection artefacts
            gattMap[address] = gatt
            writeCharMap[address] = writeChar
            gattMutexMap[address] = Mutex()

        // Assign a strip slot (first-come order)
        if (strip1Address == null) {
            strip1Address = address
            Log.d(TAG, "Assigned $address as STRIP1")
        } else if (strip2Address == null && address != strip1Address) {
            strip2Address = address
            Log.d(TAG, "Assigned $address as STRIP2")
        }

            updateConnectionState(address, ConnectionState.CONNECTED)
            Log.i(TAG, "Connected to $address")
        }
    }

    /**
     * Disconnect and close the GATT connection for the given [address].
     */
    fun disconnect(address: String) {
        val gatt = gattMap.remove(address)
        writeCharMap.remove(address)
        gattMutexMap.remove(address)

        if (gatt != null) {
            gatt.disconnect()
            gatt.close()
            Log.i(TAG, "Disconnected from $address")
        }

        // Clear strip slot so it can be reused
        if (strip1Address == address) strip1Address = null
        if (strip2Address == address) strip2Address = null

        updateConnectionState(address, ConnectionState.DISCONNECTED)
    }

    /** Disconnect all connected devices. */
    fun disconnectAll() {
        gattMap.keys.toList().forEach { disconnect(it) }
    }

    /** Returns the MAC addresses of all currently connected devices. */
    fun getConnectedAddresses(): List<String> =
        _connectionStates.value
            .filter { it.value == ConnectionState.CONNECTED }
            .keys.toList()

    /**
     * Connect to a device by its MAC address without scanning. Uses
     * [BluetoothAdapter.getRemoteDevice] to obtain a [BluetoothDevice]
     * and delegates to [connect].
     */
    fun connectByAddress(address: String) {
        val adapter = bluetoothAdapter ?: run {
            Log.w(TAG, "BluetoothAdapter not available for connectByAddress")
            return
        }
        try {
            val device = adapter.getRemoteDevice(address)
            connect(device)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid address for connectByAddress: $address", e)
        }
    }

    /**
     * Attempt to reconnect to a list of previously-connected addresses with
     * retry + exponential backoff. Emits state updates so the UI shows
     * progress. Returns when all addresses are connected or retries are
     * exhausted.
     */
    suspend fun autoReconnect(addresses: List<String>, maxRetries: Int = 3) {
        if (addresses.isEmpty()) return
        val pending = addresses.toMutableSet()

        for (attempt in 0 until maxRetries) {
            if (pending.isEmpty()) break
            val backoffMs = 2000L * (1L shl attempt) // 2s, 4s, 8s
            if (attempt > 0) {
                Log.d(TAG, "Auto-reconnect retry $attempt, backoff ${backoffMs}ms")
                delay(backoffMs)
            }

            for (address in pending.toList()) {
                connectByAddress(address)
            }

            // Wait for connections to settle
            delay(GATT_TIMEOUT_MS + 1000)

            // Remove addresses that are now connected
            val connected = getConnectedAddresses().toSet()
            pending.removeAll(connected)
        }

        if (pending.isNotEmpty()) {
            Log.w(TAG, "Auto-reconnect: failed to connect to $pending")
        }
    }

    // -----------------------------------------------------------------------
    // Writing commands
    // -----------------------------------------------------------------------

    /**
     * Write a 9-byte ELK-BLEDOM command to the targeted strip(s).
     *
     * The command is sent [COMMAND_RETRY_COUNT] times per connection with a
     * [COMMAND_RETRY_DELAY_MS] gap between retries for reliability. Which
     * connections receive the command is determined by [target]:
     * - [Target.ALL] -- every connected device.
     * - [Target.STRIP1] -- only the first connected device.
     * - [Target.STRIP2] -- only the second connected device.
     *
     * GATT writes for each connection are serialized by its [Mutex].
     */
    fun write(command: ByteArray) {
        scope.launch {
            writeInternal(command)
        }
    }

    suspend fun writeInternal(command: ByteArray) {
        val targetAddresses = when (target.value) {
            Target.ALL -> gattMap.keys.toList()
            Target.STRIP1 -> listOfNotNull(strip1Address).filter { it in gattMap }
            Target.STRIP2 -> listOfNotNull(strip2Address).filter { it in gattMap }
        }

        if (targetAddresses.isEmpty()) {
            return
        }

        for (address in targetAddresses) {
            val gatt = gattMap[address] ?: continue
            val char = writeCharMap[address] ?: continue
            val mutex = gattMutexMap[address] ?: continue

            mutex.withLock {
                repeat(COMMAND_RETRY_COUNT) { attempt ->
                    if (attempt > 0) delay(COMMAND_RETRY_DELAY_MS)
                    writeCharacteristic(gatt, char, command, address)
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Shared GATT callback
    // -----------------------------------------------------------------------

    /**
     * A single [BluetoothGattCallback] instance is created per device in
     * [connectGatt]. It dispatches events to the appropriate pending
     * continuation so that `connect`, `discoverServices`, and `write` can
     * each be expressed as suspend functions.
     */
    private fun createGattCallback(
        address: String,
        connectionContinuation: CancellableContinuation<BluetoothGatt?>,
    ): BluetoothGattCallback {
        return object : BluetoothGattCallback() {

            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.d(TAG, "GATT connected to $address")
                            connectionContinuation.safeResume(gatt)
                        } else {
                            Log.w(TAG, "GATT connect status=$status for $address")
                            gatt.close()
                            connectionContinuation.safeResume(null)
                        }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.w(TAG, "GATT disconnected from $address (status=$status)")
                        // If service discovery is pending, fail it
                        serviceDiscoveryContinuations.remove(address)?.safeResume(false)
                        // If a write is pending, fail it
                        writeContinuations.remove(address)?.safeResume(false)
                        // Update public state (may already be DISCONNECTED if we
                        // initiated the disconnect, but the update is idempotent)
                        if (gattMap.containsKey(address)) {
                            // Unexpected disconnect -- clean up
                            gattMap.remove(address)
                            writeCharMap.remove(address)
                            gattMutexMap.remove(address)
                            if (strip1Address == address) strip1Address = null
                            if (strip2Address == address) strip2Address = null
                            gatt.close()
                            updateConnectionState(address, ConnectionState.DISCONNECTED)
                        }
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val success = status == BluetoothGatt.GATT_SUCCESS
                Log.d(TAG, "Services discovered on $address (success=$success)")
                serviceDiscoveryContinuations.remove(address)?.safeResume(success)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                val success = status == BluetoothGatt.GATT_SUCCESS
                writeContinuations.remove(address)?.safeResume(success)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Internal GATT helpers (suspend wrappers around callbacks)
    // -----------------------------------------------------------------------

    /**
     * Establish a GATT connection, returning the [BluetoothGatt] instance on
     * success or `null` on failure / timeout.
     */
    private suspend fun connectGatt(device: BluetoothDevice): BluetoothGatt? {
        return withTimeoutOrNull(GATT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val callback = createGattCallback(device.address, cont)
                val gatt = device.connectGatt(
                    appContext,
                    false,
                    callback,
                    BluetoothDevice.TRANSPORT_LE,
                )
                cont.invokeOnCancellation {
                    gatt?.close()
                }
            }
        }
    }

    /**
     * Run service discovery on an already-connected GATT instance. Returns
     * `true` if discovery completed successfully within [GATT_TIMEOUT_MS].
     */
    private suspend fun discoverServices(gatt: BluetoothGatt, address: String): Boolean {
        return withTimeoutOrNull(GATT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                serviceDiscoveryContinuations[address] = cont
                cont.invokeOnCancellation {
                    serviceDiscoveryContinuations.remove(address)
                }
                if (!gatt.discoverServices()) {
                    serviceDiscoveryContinuations.remove(address)
                    cont.safeResume(false)
                }
            }
        } ?: false
    }

    /**
     * Write [value] to [characteristic] on [gatt].
     *
     * Uses [BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE]. The Android
     * BLE stack may or may not invoke `onCharacteristicWrite` for no-response
     * writes depending on the device and Android version, so we use a short
     * timeout and treat a timeout as success (the bytes have already been
     * handed to the controller).
     */
    @Suppress("DEPRECATION")
    private suspend fun writeCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        address: String,
    ): Boolean {
        return withTimeoutOrNull(2_000L) {
            suspendCancellableCoroutine { cont ->
                writeContinuations[address] = cont
                cont.invokeOnCancellation {
                    writeContinuations.remove(address)
                }
                characteristic.value = value
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                val initiated = gatt.writeCharacteristic(characteristic)
                if (!initiated) {
                    writeContinuations.remove(address)
                    Log.w(TAG, "writeCharacteristic initiation failed for $address")
                    cont.safeResume(false)
                }
                // If the callback fires, the continuation will be resumed
                // from onCharacteristicWrite. If it never fires (common for
                // WRITE_TYPE_NO_RESPONSE), the 2-second timeout will expire
                // and we return true below.
            }
        } ?: true // timeout with no-response write is acceptable
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    private fun updateConnectionState(address: String, state: ConnectionState) {
        val current = _connectionStates.value.toMutableMap()
        if (state == ConnectionState.DISCONNECTED) {
            current.remove(address)
        } else {
            current[address] = state
        }
        _connectionStates.value = current
    }

    /**
     * Safe resume that checks the continuation has not already been resumed
     * or cancelled, avoiding [IllegalStateException].
     */
    private fun <T> CancellableContinuation<T>.safeResume(value: T) {
        if (isActive) resume(value)
    }
}
