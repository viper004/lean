package com.example.lean.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * State representing the lifecycle of the BLE connection to the ESP32 LEAN IoT sensor.
 */
enum class BleConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}

/**
 * Low-level Bluetooth Low Energy manager responsible for scanning, connecting,
 * discovering services, subscribing to GATT notifications, and parsing sensor telemetry packets
 * from the ESP32 LEAN IoT device.
 */
class LeanBleManager(private val context: Context) {

    companion object {
        private const val TAG = "LeanBleManager"

        const val TARGET_DEVICE_NAME = "LEAN_IOT"
        val SERVICE_UUID: UUID = UUID.fromString("7b8e0001-7a4d-4f2b-9c3a-6d1e5f000001")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("7b8e0002-7a4d-4f2b-9c3a-6d1e5f000001")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val DEFAULT_SCAN_TIMEOUT_MS = 15000L
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var bleScanner: BluetoothLeScanner? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    // Observable States
    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _latestPacket = MutableStateFlow<BlePacket?>(null)
    val latestPacket: StateFlow<BlePacket?> = _latestPacket.asStateFlow()

    private val _packetStream = MutableSharedFlow<BlePacket>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val packetStream: SharedFlow<BlePacket> = _packetStream.asSharedFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _connectedDeviceAddress = MutableStateFlow<String?>(null)
    val connectedDeviceAddress: StateFlow<String?> = _connectedDeviceAddress.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    private val scanTimeoutRunnable = Runnable {
        if (_connectionState.value == BleConnectionState.SCANNING) {
            Log.d(TAG, "Scan timed out after $DEFAULT_SCAN_TIMEOUT_MS ms")
            stopScan()
            _lastErrorMessage.value = "Device '$TARGET_DEVICE_NAME' not found"
        }
    }

    /**
     * Checks if all required runtime permissions for BLE operation are granted
     * according to the running Android OS version.
     */
    fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
            val connectGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            scanGranted && connectGranted
        } else {
            val fineLocation = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarseLocation = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            fineLocation || coarseLocation
        }
    }

    /**
     * Returns true if the device has Bluetooth hardware and Bluetooth is currently switched on.
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Initiates scanning for the LEAN IoT ESP32 device using low-latency scan settings
     * filtered by Service UUID and Device Name.
     *
     * @param timeoutMs Timeout in milliseconds after which scanning will automatically stop.
     */
    @SuppressLint("MissingPermission")
    fun startScan(timeoutMs: Long = DEFAULT_SCAN_TIMEOUT_MS) {
        if (!hasRequiredPermissions()) {
            _lastErrorMessage.value = "Missing Bluetooth runtime permissions"
            Log.e(TAG, "Cannot start scan: missing permissions")
            return
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _lastErrorMessage.value = "Bluetooth is disabled"
            Log.e(TAG, "Cannot start scan: Bluetooth is disabled or unavailable")
            return
        }

        if (_connectionState.value == BleConnectionState.SCANNING) {
            Log.d(TAG, "Scan already in progress")
            return
        }

        disconnect()

        bleScanner = adapter.bluetoothLeScanner
        if (bleScanner == null) {
            _lastErrorMessage.value = "Bluetooth LE scanner unavailable"
            Log.e(TAG, "BluetoothLeScanner is null")
            return
        }

        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build(),
            ScanFilter.Builder().setDeviceName(TARGET_DEVICE_NAME).build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            _connectionState.value = BleConnectionState.SCANNING
            _lastErrorMessage.value = null
            bleScanner?.startScan(filters, settings, scanCallback)
            Log.d(TAG, "Started BLE scan for '$TARGET_DEVICE_NAME'...")

            mainHandler.removeCallbacks(scanTimeoutRunnable)
            mainHandler.postDelayed(scanTimeoutRunnable, timeoutMs)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting BLE scan", e)
            _lastErrorMessage.value = "Permission denied: ${e.message}"
            _connectionState.value = BleConnectionState.DISCONNECTED
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting BLE scan", e)
            _lastErrorMessage.value = "Error starting scan: ${e.message}"
            _connectionState.value = BleConnectionState.DISCONNECTED
        }
    }

    /**
     * Stops any active Bluetooth LE scan.
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        mainHandler.removeCallbacks(scanTimeoutRunnable)
        if (_connectionState.value == BleConnectionState.SCANNING) {
            try {
                bleScanner?.stopScan(scanCallback)
                Log.d(TAG, "Stopped BLE scan")
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException stopping BLE scan: ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "Exception stopping BLE scan: ${e.message}")
            }
            _connectionState.value = BleConnectionState.DISCONNECTED
        }
    }

    /**
     * Connects to a target [BluetoothDevice].
     */
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        stopScan()

        if (!hasRequiredPermissions()) {
            _lastErrorMessage.value = "Missing Bluetooth runtime permissions"
            Log.e(TAG, "Cannot connect: missing permissions")
            return
        }

        try {
            _connectionState.value = BleConnectionState.CONNECTING
            _connectedDeviceAddress.value = device.address
            _connectedDeviceName.value = device.name ?: TARGET_DEVICE_NAME

            Log.d(TAG, "Connecting to GATT on device: ${device.address} (${device.name})...")
            bluetoothGatt = device.connectGatt(
                context,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException connecting to device ${device.address}", e)
            _lastErrorMessage.value = "Permission denied connecting to device"
            closeGatt()
            _connectionState.value = BleConnectionState.DISCONNECTED
        } catch (e: Exception) {
            Log.e(TAG, "Exception connecting to device ${device.address}", e)
            _lastErrorMessage.value = "Connection error: ${e.message}"
            closeGatt()
            _connectionState.value = BleConnectionState.DISCONNECTED
        }
    }

    /**
     * Connects to a remote device by MAC address.
     */
    fun connect(deviceAddress: String) {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _lastErrorMessage.value = "Bluetooth is disabled"
            return
        }
        val device = try {
            adapter.getRemoteDevice(deviceAddress)
        } catch (e: IllegalArgumentException) {
            _lastErrorMessage.value = "Invalid Bluetooth address: $deviceAddress"
            return
        }
        connect(device)
    }

    /**
     * Disconnects from the current GATT connection and resets connection state.
     */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopScan()
        if (_connectionState.value != BleConnectionState.DISCONNECTED) {
            _connectionState.value = BleConnectionState.DISCONNECTING
            try {
                bluetoothGatt?.disconnect()
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException during disconnect: ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "Exception during disconnect: ${e.message}")
            }
            closeGatt()
            _connectionState.value = BleConnectionState.DISCONNECTED
        }
    }

    /**
     * Completely cleans up all scanner, timer, and GATT resources.
     */
    fun cleanup() {
        mainHandler.removeCallbacksAndMessages(null)
        stopScan()
        disconnect()
    }

    @Synchronized
    private fun closeGatt() {
        try {
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Exception closing BluetoothGatt: ${e.message}")
        }
        bluetoothGatt = null
        _connectedDeviceAddress.value = null
        _connectedDeviceName.value = null
    }

    private fun processIncomingBytes(bytes: ByteArray) {
        val packet = BlePacket.parse(bytes)
        if (packet != null) {
            _latestPacket.value = packet
            _packetStream.tryEmit(packet)
        } else {
            Log.w(TAG, "Rejected malformed or unexpected BLE packet (${bytes.size} bytes)")
        }
    }

    // Scan Callback
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val device = result.device
            val scanRecord = result.scanRecord

            val deviceName = try { device.name } catch (e: SecurityException) { null }
            val recordName = scanRecord?.deviceName
            val serviceUuids = scanRecord?.serviceUuids

            val matchesUuid = serviceUuids?.any { it.uuid == SERVICE_UUID } == true
            val matchesName = (deviceName?.equals(TARGET_DEVICE_NAME, ignoreCase = true) == true) ||
                    (recordName?.equals(TARGET_DEVICE_NAME, ignoreCase = true) == true)

            if (matchesUuid || matchesName) {
                Log.d(TAG, "Discovered target device: ${device.address} ($deviceName). Initiating connection.")
                stopScan()
                connect(device)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error code: $errorCode")
            _lastErrorMessage.value = "Scan failed with error code $errorCode"
            _connectionState.value = BleConnectionState.DISCONNECTED
        }
    }

    // GATT Callback
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange: status=$status, newState=$newState")

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "GATT connection failure with status: $status")
                _lastErrorMessage.value = "Connection failed with GATT status $status"
                closeGatt()
                _connectionState.value = BleConnectionState.DISCONNECTED
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server. Initiating service discovery...")
                    _connectionState.value = BleConnectionState.CONNECTING
                    try {
                        gatt.discoverServices()
                    } catch (e: SecurityException) {
                        Log.e(TAG, "SecurityException discovering services", e)
                        _lastErrorMessage.value = "Permission denied discovering services"
                        closeGatt()
                        _connectionState.value = BleConnectionState.DISCONNECTED
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server")
                    closeGatt()
                    _connectionState.value = BleConnectionState.DISCONNECTED
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed with status: $status")
                _lastErrorMessage.value = "Service discovery failed (status $status)"
                closeGatt()
                _connectionState.value = BleConnectionState.DISCONNECTED
                return
            }

            Log.d(TAG, "GATT services discovered. Locating target service $SERVICE_UUID...")
            val service = gatt.getService(SERVICE_UUID)
            if (service == null) {
                Log.e(TAG, "Target LEAN IoT service not found: $SERVICE_UUID")
                _lastErrorMessage.value = "Target service not found on device"
                closeGatt()
                _connectionState.value = BleConnectionState.DISCONNECTED
                return
            }

            val characteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
            if (characteristic == null) {
                Log.e(TAG, "Target characteristic not found: $CHARACTERISTIC_UUID")
                _lastErrorMessage.value = "Target characteristic not found"
                closeGatt()
                _connectionState.value = BleConnectionState.DISCONNECTED
                return
            }

            try {
                // Enable local notification
                val notifyEnabled = gatt.setCharacteristicNotification(characteristic, true)
                if (!notifyEnabled) {
                    Log.e(TAG, "Failed to enable local characteristic notifications")
                    _lastErrorMessage.value = "Failed to enable characteristic notifications"
                    closeGatt()
                    _connectionState.value = BleConnectionState.DISCONNECTED
                    return
                }

                // Write CCCD (0x2902) to subscribe to remote notifications on ESP32
                val cccdDescriptor = characteristic.getDescriptor(CCCD_UUID)
                if (cccdDescriptor == null) {
                    Log.e(TAG, "CCCD descriptor 0x2902 not found on characteristic")
                    _lastErrorMessage.value = "Notification descriptor missing"
                    closeGatt()
                    _connectionState.value = BleConnectionState.DISCONNECTED
                    return
                }

                Log.d(TAG, "Writing CCCD descriptor to subscribe to notifications...")
                val writeInitiated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(
                        cccdDescriptor,
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    ) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    cccdDescriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(cccdDescriptor)
                }

                if (!writeInitiated) {
                    Log.e(TAG, "Failed to initiate descriptor write for notifications")
                    _lastErrorMessage.value = "Failed to initiate notification subscription"
                    closeGatt()
                    _connectionState.value = BleConnectionState.DISCONNECTED
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException enabling notifications", e)
                _lastErrorMessage.value = "Permission denied enabling notifications"
                closeGatt()
                _connectionState.value = BleConnectionState.DISCONNECTED
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (descriptor.uuid == CCCD_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "CCCD descriptor write success. Connection fully established!")
                    _connectionState.value = BleConnectionState.CONNECTED
                    _lastErrorMessage.value = null

                    // Request low-latency connection priority for smooth live gauge feedback
                    try {
                        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Permission denied for requestConnectionPriority: ${e.message}")
                    }
                } else {
                    Log.e(TAG, "CCCD descriptor write failed with status: $status")
                    _lastErrorMessage.value = "Failed to subscribe to notifications (status $status)"
                    closeGatt()
                    _connectionState.value = BleConnectionState.DISCONNECTED
                }
            }
        }

        // Android 13+ (API 33+) callback
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == CHARACTERISTIC_UUID) {
                processIncomingBytes(value)
            }
        }

        // Android <= 12 (API < 33) legacy callback
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                if (characteristic.uuid == CHARACTERISTIC_UUID) {
                    characteristic.value?.let { processIncomingBytes(it) }
                }
            }
        }
    }
}
