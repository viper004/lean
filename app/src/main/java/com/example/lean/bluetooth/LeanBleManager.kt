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
import com.example.lean.data.RidingProfile
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
 * Calibration status reported by the ESP32 IoT sensor.
 *
 * Status protocol:
 * - 0x00 = Idle / Uncalibrated
 * - 0x01 = Calibrating (collecting stationary sensor samples)
 * - 0x02 = Calibration successful (reference zero & gyro bias locked)
 * - 0x03 = Calibration failed (sensor unstable or vehicle moved)
 */
enum class BleCalibrationStatus(val code: Int) {
    IDLE(0x00),
    CALIBRATING(0x01),
    SUCCESS(0x02),
    FAILED(0x03);

    companion object {
        fun fromByte(b: Byte): BleCalibrationStatus = when (b.toInt() and 0xFF) {
            0x01 -> CALIBRATING
            0x02 -> SUCCESS
            0x03 -> FAILED
            else -> IDLE
        }
    }
}

/**
 * Low-level Bluetooth Low Energy manager responsible for scanning, connecting,
 * discovering services, subscribing to GATT notifications, sending calibration requests,
 * and parsing sensor telemetry packets from the ESP32 LEAN IoT device.
 */
class LeanBleManager(private val context: Context) {

    companion object {
        private const val TAG = "LeanBleManager"

        const val TARGET_DEVICE_NAME = "LEAN_IOT"
        val SERVICE_UUID: UUID = UUID.fromString("7b8e0001-7a4d-4f2b-9c3a-6d1e5f000001")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("7b8e0002-7a4d-4f2b-9c3a-6d1e5f000001")
        val CALIBRATION_COMMAND_UUID: UUID = UUID.fromString("7b8e0003-7a4d-4f2b-9c3a-6d1e5f000001")
        val CALIBRATION_STATUS_UUID: UUID = UUID.fromString("7b8e0004-7a4d-4f2b-9c3a-6d1e5f000001")
        val MODE_COMMAND_UUID: UUID = UUID.fromString("7b8e0005-7a4d-4f2b-9c3a-6d1e5f000001")
        val MODE_STATUS_UUID: UUID = UUID.fromString("7b8e0006-7a4d-4f2b-9c3a-6d1e5f000001")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val DEFAULT_SCAN_TIMEOUT_MS = 15000L
        private const val CALIBRATION_TIMEOUT_MS = 6000L
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var bleScanner: BluetoothLeScanner? = null

    private var telemetryCharacteristic: BluetoothGattCharacteristic? = null
    private var calibrationCommandCharacteristic: BluetoothGattCharacteristic? = null
    private var calibrationStatusCharacteristic: BluetoothGattCharacteristic? = null
    private var modeCommandCharacteristic: BluetoothGattCharacteristic? = null
    private var modeStatusCharacteristic: BluetoothGattCharacteristic? = null
    private var pendingDesiredProfile: RidingProfile? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    // Observable States
    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _calibrationStatus = MutableStateFlow(BleCalibrationStatus.IDLE)
    val calibrationStatus: StateFlow<BleCalibrationStatus> = _calibrationStatus.asStateFlow()

    private val _esp32RidingProfile = MutableStateFlow<RidingProfile?>(null)
    val esp32RidingProfile: StateFlow<RidingProfile?> = _esp32RidingProfile.asStateFlow()

    private val _isProfileSynchronizing = MutableStateFlow(false)
    val isProfileSynchronizing: StateFlow<Boolean> = _isProfileSynchronizing.asStateFlow()

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

    private var calibrationTimeoutRunnable: Runnable? = null

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
     * Requests the ESP32 to initiate automatic calibration (0x01).
     *
     * Calibration lasts approximately 2 seconds on the ESP32.
     * The ESP32 will notify progress (0x01 = Calibrating) and outcome (0x02 = Success, 0x03 = Failed).
     */
    @SuppressLint("MissingPermission")
    fun requestCalibration(): Boolean {
        val gatt = bluetoothGatt
        val commandChar = calibrationCommandCharacteristic
        if (gatt == null || commandChar == null || _connectionState.value != BleConnectionState.CONNECTED) {
            Log.e(TAG, "Cannot request calibration: BLE not connected or calibration command characteristic missing")
            _lastErrorMessage.value = "Cannot calibrate: sensor not connected"
            return false
        }

        _calibrationStatus.value = BleCalibrationStatus.CALIBRATING
        _lastErrorMessage.value = null

        val cmdBytes = byteArrayOf(0x01)
        val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                commandChar,
                cmdBytes,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            commandChar.value = cmdBytes
            @Suppress("DEPRECATION")
            commandChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(commandChar)
        }

        if (!success) {
            Log.e(TAG, "Failed to initiate characteristic write for calibration request")
            _calibrationStatus.value = BleCalibrationStatus.FAILED
            _lastErrorMessage.value = "Failed to send calibration command to sensor"
            return false
        }

        Log.d(TAG, "Calibration command 0x01 dispatched to ESP32. Awaiting result...")

        // Guard timeout in case of unexpected sensor freeze or packet loss
        cancelCalibrationTimeout()
        val timeoutRunnable = Runnable {
            if (_calibrationStatus.value == BleCalibrationStatus.CALIBRATING) {
                Log.w(TAG, "Calibration timed out after $CALIBRATION_TIMEOUT_MS ms")
                _calibrationStatus.value = BleCalibrationStatus.FAILED
                _lastErrorMessage.value = "Calibration timed out. Please keep stationary and retry."
            }
        }
        calibrationTimeoutRunnable = timeoutRunnable
        mainHandler.postDelayed(timeoutRunnable, CALIBRATION_TIMEOUT_MS)

        return true
    }

    /**
     * Resets calibration state back to IDLE.
     */
    fun resetCalibrationState() {
        cancelCalibrationTimeout()
        _calibrationStatus.value = BleCalibrationStatus.IDLE
    }

    private fun cancelCalibrationTimeout() {
        calibrationTimeoutRunnable?.let {
            mainHandler.removeCallbacks(it)
            calibrationTimeoutRunnable = null
        }
    }

    /**
     * Disconnects from the current GATT connection and resets connection state.
     */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopScan()
        cancelCalibrationTimeout()
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
        cancelCalibrationTimeout()
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
        telemetryCharacteristic = null
        calibrationCommandCharacteristic = null
        calibrationStatusCharacteristic = null
        modeCommandCharacteristic = null
        modeStatusCharacteristic = null
        _connectedDeviceAddress.value = null
        _connectedDeviceName.value = null
        _esp32RidingProfile.value = null
        _isProfileSynchronizing.value = false
        cancelCalibrationTimeout()
    }

    /**
     * Sends a command to ESP32 to switch sensor operating mode to [profile].
     * Marks profile as synchronizing until confirmed by ESP32 mode status notification.
     */
    @SuppressLint("MissingPermission")
    fun setRidingProfile(profile: RidingProfile): Boolean {
        pendingDesiredProfile = profile
        val gatt = bluetoothGatt
        val cmdChar = modeCommandCharacteristic
        if (gatt == null || cmdChar == null || _connectionState.value != BleConnectionState.CONNECTED) {
            Log.d(TAG, "Cannot send mode to ESP32: not connected (queued for connection)")
            return false
        }

        _isProfileSynchronizing.value = true
        val cmdBytes = byteArrayOf(profile.id)
        val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                cmdChar,
                cmdBytes,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            cmdChar.value = cmdBytes
            @Suppress("DEPRECATION")
            cmdChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(cmdChar)
        }

        if (!success) {
            Log.e(TAG, "Failed to initiate mode write to ESP32 characteristic")
            _isProfileSynchronizing.value = false
        } else {
            Log.d(TAG, "Dispatched mode command to ESP32: ${profile.displayName} (0x%02X)".format(profile.id))
        }
        return success
    }

    fun setDesiredRidingProfile(profile: RidingProfile) {
        pendingDesiredProfile = profile
        if (_connectionState.value == BleConnectionState.CONNECTED) {
            setRidingProfile(profile)
        }
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

    private fun processIncomingCalibrationStatus(bytes: ByteArray) {
        if (bytes.isNotEmpty()) {
            val status = BleCalibrationStatus.fromByte(bytes[0])
            Log.d(TAG, "Received ESP32 calibration status update: $status (0x%02X)".format(bytes[0]))
            if (status == BleCalibrationStatus.SUCCESS || status == BleCalibrationStatus.FAILED) {
                cancelCalibrationTimeout()
            }
            _calibrationStatus.value = status
        }
    }

    private fun processIncomingModeStatus(bytes: ByteArray) {
        if (bytes.isNotEmpty()) {
            val confirmed = RidingProfile.fromId(bytes[0])
            Log.d(TAG, "Received ESP32 confirmed operating mode: ${confirmed.displayName} (0x%02X)".format(bytes[0]))
            _esp32RidingProfile.value = confirmed
            _isProfileSynchronizing.value = false

            val desired = pendingDesiredProfile
            if (desired != null && desired != confirmed) {
                Log.d(TAG, "ESP32 mode (${confirmed.displayName}) differs from desired (${desired.displayName}), updating...")
                setRidingProfile(desired)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeDescriptorCompat(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
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
                if (_calibrationStatus.value == BleCalibrationStatus.CALIBRATING) {
                    _calibrationStatus.value = BleCalibrationStatus.FAILED
                }
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
                    if (_calibrationStatus.value == BleCalibrationStatus.CALIBRATING) {
                        _calibrationStatus.value = BleCalibrationStatus.FAILED
                        _lastErrorMessage.value = "Sensor disconnected during calibration"
                    }
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

            // Cache characteristic references
            telemetryCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
            calibrationCommandCharacteristic = service.getCharacteristic(CALIBRATION_COMMAND_UUID)
            calibrationStatusCharacteristic = service.getCharacteristic(CALIBRATION_STATUS_UUID)
            modeCommandCharacteristic = service.getCharacteristic(MODE_COMMAND_UUID)
            modeStatusCharacteristic = service.getCharacteristic(MODE_STATUS_UUID)

            val telemetryChar = telemetryCharacteristic
            if (telemetryChar == null) {
                Log.e(TAG, "Target telemetry characteristic not found: $CHARACTERISTIC_UUID")
                _lastErrorMessage.value = "Telemetry characteristic not found"
                closeGatt()
                _connectionState.value = BleConnectionState.DISCONNECTED
                return
            }

            try {
                // Step 1: Subscribe to telemetry notifications sequentially
                val notifyEnabled = gatt.setCharacteristicNotification(telemetryChar, true)
                if (!notifyEnabled) {
                    Log.e(TAG, "Failed to enable local characteristic notifications for telemetry")
                    _lastErrorMessage.value = "Failed to enable characteristic notifications"
                    closeGatt()
                    _connectionState.value = BleConnectionState.DISCONNECTED
                    return
                }

                val telemetryCccd = telemetryChar.getDescriptor(CCCD_UUID)
                if (telemetryCccd == null) {
                    Log.e(TAG, "CCCD descriptor 0x2902 not found on telemetry characteristic")
                    _lastErrorMessage.value = "Notification descriptor missing"
                    closeGatt()
                    _connectionState.value = BleConnectionState.DISCONNECTED
                    return
                }

                Log.d(TAG, "Writing CCCD descriptor to subscribe to telemetry notifications...")
                val writeInitiated = writeDescriptorCompat(
                    gatt, telemetryCccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                )

                if (!writeInitiated) {
                    Log.e(TAG, "Failed to initiate descriptor write for telemetry notifications")
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
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e(TAG, "CCCD descriptor write failed with status: $status")
                    _lastErrorMessage.value = "Failed to subscribe to notifications (status $status)"
                    closeGatt()
                    _connectionState.value = BleConnectionState.DISCONNECTED
                    return
                }

                val charUuid = descriptor.characteristic?.uuid
                if (charUuid == CHARACTERISTIC_UUID) {
                    Log.d(TAG, "Telemetry CCCD write success. Subscribing to calibration status...")

                    val statusChar = calibrationStatusCharacteristic
                    if (statusChar != null) {
                        try {
                            gatt.setCharacteristicNotification(statusChar, true)
                            val statusCccd = statusChar.getDescriptor(CCCD_UUID)
                            if (statusCccd != null) {
                                val writeNext = writeDescriptorCompat(
                                    gatt, statusCccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                )
                                if (writeNext) {
                                    // Await onDescriptorWrite for CALIBRATION_STATUS_UUID
                                    return
                                }
                            }
                        } catch (e: SecurityException) {
                            Log.w(TAG, "SecurityException subscribing to calibration status", e)
                        }
                    }

                    // If status char subscription is not needed or absent, continue to mode status
                    subscribeToModeStatus(gatt)
                } else if (charUuid == CALIBRATION_STATUS_UUID) {
                    Log.d(TAG, "Calibration status CCCD write success. Subscribing to mode status...")
                    subscribeToModeStatus(gatt)
                } else if (charUuid == MODE_STATUS_UUID) {
                    Log.d(TAG, "Mode status CCCD write success. Connection fully established!")
                    finishConnectionSetup(gatt)
                }
            }
        }

        @SuppressLint("MissingPermission")
        private fun subscribeToModeStatus(gatt: BluetoothGatt) {
            val modeChar = modeStatusCharacteristic
            if (modeChar != null) {
                try {
                    gatt.setCharacteristicNotification(modeChar, true)
                    val modeCccd = modeChar.getDescriptor(CCCD_UUID)
                    if (modeCccd != null) {
                        val writeNext = writeDescriptorCompat(
                            gatt, modeCccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        )
                        if (writeNext) {
                            return
                        }
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "SecurityException subscribing to mode status", e)
                }
            }
            finishConnectionSetup(gatt)
        }

        @SuppressLint("MissingPermission")
        private fun finishConnectionSetup(gatt: BluetoothGatt) {
            _connectionState.value = BleConnectionState.CONNECTED
            _lastErrorMessage.value = null

            // Request low-latency connection priority for smooth live gauge feedback
            try {
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
            } catch (e: SecurityException) {
                Log.w(TAG, "Permission denied for requestConnectionPriority: ${e.message}")
            }

            // Read current operating mode from ESP32
            val modeChar = modeStatusCharacteristic
            if (modeChar != null) {
                try {
                    gatt.readCharacteristic(modeChar)
                    Log.d(TAG, "Initiated read for initial ESP32 operating mode")
                } catch (e: SecurityException) {
                    Log.w(TAG, "SecurityException reading mode characteristic: ${e.message}")
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (characteristic.uuid == CALIBRATION_COMMAND_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Calibration command successfully written to ESP32 characteristic")
                } else {
                    Log.e(TAG, "Failed to write calibration command to ESP32: status $status")
                    cancelCalibrationTimeout()
                    _calibrationStatus.value = BleCalibrationStatus.FAILED
                    _lastErrorMessage.value = "Failed to send calibration command (status $status)"
                }
            } else if (characteristic.uuid == MODE_COMMAND_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Mode command successfully written to ESP32 characteristic")
                } else {
                    Log.e(TAG, "Failed to write mode command to ESP32: status $status")
                    _isProfileSynchronizing.value = false
                }
            }
        }

        // Android 13+ (API 33+) read callback
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == MODE_STATUS_UUID) {
                processIncomingModeStatus(value)
            }
        }

        // Android <= 12 (API < 33) legacy read callback
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && status == BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.uuid == MODE_STATUS_UUID) {
                    val bytes = characteristic.value ?: byteArrayOf()
                    processIncomingModeStatus(bytes)
                }
            }
        }

        // Android 13+ (API 33+) notification callback
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            when (characteristic.uuid) {
                CHARACTERISTIC_UUID -> processIncomingBytes(value)
                CALIBRATION_STATUS_UUID -> processIncomingCalibrationStatus(value)
                MODE_STATUS_UUID -> processIncomingModeStatus(value)
            }
        }

        // Android <= 12 (API < 33) legacy notification callback
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                val bytes = characteristic.value ?: return
                when (characteristic.uuid) {
                    CHARACTERISTIC_UUID -> processIncomingBytes(bytes)
                    CALIBRATION_STATUS_UUID -> processIncomingCalibrationStatus(bytes)
                    MODE_STATUS_UUID -> processIncomingModeStatus(bytes)
                }
            }
        }
    }
}
