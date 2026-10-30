package com.example.lean.ui.screens

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.lean.data.AppThemeMode
import com.example.lean.data.RidingProfile
import com.example.lean.data.SensorMode
import com.example.lean.data.SmoothingLevel
import com.example.lean.data.UserSettings
import com.example.lean.sensor.SensorHardwareType
import com.example.lean.ui.components.LeanLogo
import com.example.lean.ui.theme.errorRed
import com.example.lean.ui.theme.primaryCyan
import com.example.lean.ui.theme.primaryLime
import com.example.lean.ui.theme.textMuted
import com.example.lean.ui.theme.warningAmber
import java.util.Locale

private enum class PendingSmsAction {
    ENABLE_TOGGLE,
    TEST_SMS
}

private fun Context.findActivity(): ComponentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is ComponentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun SettingsScreen(
    settings: UserSettings,
    leanState: com.example.lean.data.LeanState? = null,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onSensorModeChange: (SensorMode) -> Unit,
    onRidingProfileChange: (RidingProfile) -> Unit = {},
    onSmoothingLevelChange: (SmoothingLevel) -> Unit,
    onKeepScreenAwakeChange: (Boolean) -> Unit,
    onLockOrientationChange: (Boolean) -> Unit,
    onStraightThresholdChange: (Float) -> Unit,
    onWarningThresholdChange: (Float) -> Unit,
    onCriticalThresholdChange: (Float) -> Unit,
    onGpsEnabledChange: (Boolean) -> Unit,
    onFallDetectionEnabledChange: (Boolean) -> Unit,
    onFallDetectionCriticalAngleChange: (Float) -> Unit,
    onEmergencySmsEnabledChange: (Boolean) -> Unit,
    onEmergencyPhoneNumberChange: (String) -> Unit,
    onTestEmergencySms: () -> Unit,
    onResetCalibration: () -> Unit,
    onResetPeak: () -> Unit,
    onResetSettings: (() -> Unit)? = null,
    onStartBleScan: () -> Unit = {},
    onStopBleScan: () -> Unit = {},
    onDisconnectBle: () -> Unit = {},
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDiagnostics by remember { mutableStateOf(false) }

    val blePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            val btAdapter = btManager?.adapter
            if (btAdapter == null || !btAdapter.isEnabled) {
                Toast.makeText(context, "Bluetooth is disabled. Please turn on Bluetooth.", Toast.LENGTH_LONG).show()
            } else {
                onSensorModeChange(SensorMode.EXTERNAL_BLE)
                onStartBleScan()
            }
        } else {
            Toast.makeText(context, "Bluetooth permissions are required to scan for LEAN_IOT.", Toast.LENGTH_LONG).show()
        }
    }

    fun requestBleScanOrPermissions() {
        val allGranted = blePermissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
        if (!allGranted) {
            blePermissionLauncher.launch(blePermissions)
        } else {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            val btAdapter = btManager?.adapter
            if (btAdapter == null || !btAdapter.isEnabled) {
                Toast.makeText(context, "Bluetooth is disabled. Please turn on Bluetooth in settings.", Toast.LENGTH_LONG).show()
            } else {
                onSensorModeChange(SensorMode.EXTERNAL_BLE)
                onStartBleScan()
            }
        }
    }

    var pendingSmsAction by remember { mutableStateOf<PendingSmsAction?>(null) }
    var showPermanentDenialDialog by remember { mutableStateOf(false) }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val prefs = context.getSharedPreferences("lean_sms_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("has_requested_sms", true).apply()

        if (isGranted) {
            when (pendingSmsAction) {
                PendingSmsAction.ENABLE_TOGGLE -> {
                    onEmergencySmsEnabledChange(true)
                }
                PendingSmsAction.TEST_SMS -> {
                    onEmergencySmsEnabledChange(true)
                    onTestEmergencySms()
                }
                null -> {}
            }
        } else {
            Toast.makeText(
                context,
                "SMS permission is required to automatically send an emergency SOS message.",
                Toast.LENGTH_LONG
            ).show()
        }
        pendingSmsAction = null
    }

    fun handleSmsPermissionCheck(action: PendingSmsAction) {
        val isGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (isGranted) {
            when (action) {
                PendingSmsAction.ENABLE_TOGGLE -> onEmergencySmsEnabledChange(true)
                PendingSmsAction.TEST_SMS -> onTestEmergencySms()
            }
        } else {
            val prefs = context.getSharedPreferences("lean_sms_prefs", Context.MODE_PRIVATE)
            val hasRequestedBefore = prefs.getBoolean("has_requested_sms", false)
            val activity = context.findActivity()
            val showRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.SEND_SMS)
            } ?: false

            if (hasRequestedBefore && !showRationale) {
                showPermanentDenialDialog = true
            } else {
                pendingSmsAction = action
                smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SETTINGS",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = 1.2.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 0: Appearance
            SettingsSectionHeader("APPEARANCE")

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    AppThemeMode.values().forEach { mode ->
                        val isSelected = (settings.themeMode == mode)
                        val icon = if (mode == AppThemeMode.LIGHT) Icons.Default.LightMode else Icons.Default.DarkMode

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                                )
                                .clickable { onThemeModeChange(mode) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onThemeModeChange(mode) },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primaryCyan)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = icon,
                                contentDescription = mode.displayName,
                                tint = if (isSelected) MaterialTheme.colorScheme.primaryCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = mode.displayName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (mode == AppThemeMode.LIGHT) "High contrast for bright outdoor sunlight" else "Minimal dark racing aesthetic",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 1: Hardware Availability
            SettingsSectionHeader("AVAILABLE HARDWARE SENSORS")

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SensorAvailabilityRow("Accelerometer", leanState?.accelHardwareType ?: SensorHardwareType.PHYSICAL)
                    SensorAvailabilityRow("Gyroscope", leanState?.gyroHardwareType ?: SensorHardwareType.LOGICAL)
                    SensorAvailabilityRow("Game Rotation Vector", leanState?.gameRotHardwareType ?: SensorHardwareType.FUSED)
                    SensorAvailabilityRow("Rotation Vector", leanState?.rotVecHardwareType ?: SensorHardwareType.FUSED)
                    SensorAvailabilityRow("Gravity", leanState?.gravityHardwareType ?: SensorHardwareType.FUSED)
                    SensorAvailabilityRow("Magnetometer", leanState?.magnetometerHardwareType ?: SensorHardwareType.UNAVAILABLE)
                    SensorAvailabilityRow("Linear Acceleration", leanState?.linearAccelHardwareType ?: SensorHardwareType.FUSED)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 2: Sensor Mode
            SettingsSectionHeader("SENSOR MODE")

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    SensorMode.values().forEach { mode ->
                        val isAvailable = when (mode) {
                            SensorMode.AUTOMATIC -> true
                            SensorMode.GAME_ROTATION_VECTOR -> leanState?.hasGameRotationVector ?: true
                            SensorMode.ROTATION_VECTOR -> leanState?.hasRotationVector ?: true
                            SensorMode.FUSED_GYRO_ACCEL -> (leanState?.isGyroAvailable ?: true) && (leanState?.isAccelAvailable ?: true)
                            SensorMode.ACCEL_ONLY -> leanState?.isAccelAvailable ?: true
                            SensorMode.EXTERNAL_BLE -> true
                        }

                        val isSelected = settings.sensorMode == mode

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                                .clickable(enabled = isAvailable) { onSensorModeChange(mode) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                enabled = isAvailable,
                                onClick = { if (isAvailable) onSensorModeChange(mode) },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primaryCyan)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.displayName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                                if (mode == SensorMode.AUTOMATIC) {
                                    Text(
                                        text = "Using: ${leanState?.activeSensorName ?: "Game Rotation Vector"}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primaryLime
                                    )
                                } else if (mode == SensorMode.FUSED_GYRO_ACCEL && leanState?.gyroHardwareType == SensorHardwareType.LOGICAL) {
                                    Text(
                                        text = "⚠ Physical gyroscope unavailable. Device is using a logical software gyroscope.",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.warningAmber
                                    )
                                } else if (mode == SensorMode.EXTERNAL_BLE) {
                                    val isConnected = leanState?.isExternalSensorConnected == true
                                    val connState = leanState?.bleConnectionState ?: com.example.lean.bluetooth.BleConnectionState.DISCONNECTED
                                    val statusText = if (isConnected) {
                                        "Connected: ${leanState?.bleDeviceName ?: "LEAN_IOT"}"
                                    } else {
                                        "ESP32 IoT BLE Telemetry (${connState.name})"
                                    }
                                    Text(
                                        text = statusText,
                                        fontSize = 12.sp,
                                        fontWeight = if (isConnected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isConnected) MaterialTheme.colorScheme.primaryLime else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = if (isAvailable) mode.description else "Unavailable on device hardware",
                                        fontSize = 12.sp,
                                        color = if (isAvailable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 2A-Profile: Operating Profile
            SettingsSectionHeader("OPERATING RIDING PROFILE")

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ProfileSettingCard(
                            profile = RidingProfile.SPORTS,
                            selected = settings.ridingProfile == RidingProfile.SPORTS,
                            icon = Icons.Default.Speed,
                            title = "SPORTS",
                            subtitle = "Track & performance (44Hz / 16Hz LPF)",
                            modifier = Modifier.weight(1f),
                            onClick = { onRidingProfileChange(RidingProfile.SPORTS) }
                        )

                        ProfileSettingCard(
                            profile = RidingProfile.CASUAL,
                            selected = settings.ridingProfile == RidingProfile.CASUAL,
                            icon = Icons.Default.DirectionsBike,
                            title = "CASUAL",
                            subtitle = "Daily & highway (21Hz / 6-7Hz LPF)",
                            modifier = Modifier.weight(1f),
                            onClick = { onRidingProfileChange(RidingProfile.CASUAL) }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val isBleActive = leanState?.activeMode == SensorMode.EXTERNAL_BLE
                    val syncText = when {
                        !isBleActive -> "Profile active on phone internal sensors"
                        leanState?.isExternalSensorConnected != true -> "Active on phone (ESP32 disconnected)"
                        leanState.isProfileSynchronizing || leanState.esp32RidingProfile != settings.ridingProfile ->
                            "Synchronizing with ESP32 sensor..."
                        else -> "Synchronized with ESP32 (${leanState.esp32RidingProfile?.displayName})"
                    }
                    val syncColor = when {
                        !isBleActive -> MaterialTheme.colorScheme.primaryCyan
                        leanState?.isExternalSensorConnected != true -> MaterialTheme.colorScheme.textMuted
                        leanState.isProfileSynchronizing || leanState.esp32RidingProfile != settings.ridingProfile ->
                            MaterialTheme.colorScheme.warningAmber
                        else -> MaterialTheme.colorScheme.primaryLime
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(syncColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = syncText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = syncColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 2B: ESP32 IoT Sensor (LEAN_IOT) Section
            SettingsSectionHeader("ESP32 IoT WIRELESS SENSOR (BLE)")

            val bleConnState = leanState?.bleConnectionState ?: com.example.lean.bluetooth.BleConnectionState.DISCONNECTED
            val isBleConnected = leanState?.isExternalSensorConnected == true
            val isBleScanning = bleConnState == com.example.lean.bluetooth.BleConnectionState.SCANNING
            val isBleConnecting = bleConnState == com.example.lean.bluetooth.BleConnectionState.CONNECTING
            val blePacket = leanState?.bleLastPacket

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header Row with Icon & Title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                isBleConnected -> Icons.Default.BluetoothConnected
                                isBleScanning -> Icons.AutoMirrored.Filled.BluetoothSearching
                                else -> Icons.Default.Bluetooth
                            },
                            contentDescription = "Bluetooth Status",
                            tint = when {
                                isBleConnected -> MaterialTheme.colorScheme.primaryLime
                                isBleScanning || isBleConnecting -> MaterialTheme.colorScheme.primaryCyan
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "LEAN_IOT Sensor",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "ESP32 MPU6050 BLE Peripheral (35ms)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Status Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        isBleConnected -> MaterialTheme.colorScheme.primaryLime.copy(alpha = 0.15f)
                                        isBleScanning || isBleConnecting -> MaterialTheme.colorScheme.primaryCyan.copy(alpha = 0.15f)
                                        leanState?.bleErrorMessage != null -> MaterialTheme.colorScheme.errorRed.copy(alpha = 0.15f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = when (bleConnState) {
                                    com.example.lean.bluetooth.BleConnectionState.CONNECTED -> "CONNECTED"
                                    com.example.lean.bluetooth.BleConnectionState.SCANNING -> "SCANNING"
                                    com.example.lean.bluetooth.BleConnectionState.CONNECTING -> "CONNECTING"
                                    com.example.lean.bluetooth.BleConnectionState.DISCONNECTING -> "DISCONNECTING"
                                    com.example.lean.bluetooth.BleConnectionState.DISCONNECTED -> {
                                        if (leanState?.bleErrorMessage != null) "ERROR" else "DISCONNECTED"
                                    }
                                },
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isBleConnected -> MaterialTheme.colorScheme.primaryLime
                                    isBleScanning || isBleConnecting -> MaterialTheme.colorScheme.primaryCyan
                                    leanState?.bleErrorMessage != null -> MaterialTheme.colorScheme.errorRed
                                    else -> MaterialTheme.colorScheme.textMuted
                                }
                            )
                        }
                    }

                    // Device Details Row (if connected)
                    if (isBleConnected && leanState?.bleDeviceAddress != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "DEVICE MAC",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.textMuted
                            )
                            Text(
                                text = leanState.bleDeviceAddress,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Live Telemetry Preview (when connected and packets arriving)
                    if (isBleConnected && blePacket != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("LIVE ANGLE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.textMuted)
                                    Text(
                                        text = String.format(Locale.US, "%.1f°", blePacket.leanAngleDegrees),
                                        fontSize = 18.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primaryCyan
                                    )
                                }
                                Column {
                                    Text("ROLL RATE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.textMuted)
                                    Text(
                                        text = String.format(Locale.US, "%.1f °/s", blePacket.gyroRollRateDps),
                                        fontSize = 18.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primaryLime
                                    )
                                }
                                Column {
                                    Text("PACKET SEQ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.textMuted)
                                    Text(
                                        text = "#${blePacket.sequenceId}",
                                        fontSize = 18.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Error Message Display (if any)
                    if (leanState?.bleErrorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.errorRed.copy(alpha = 0.1f))
                                .border(1.dp, MaterialTheme.colorScheme.errorRed.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.errorRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = leanState.bleErrorMessage,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.errorRed
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons (Scan / Stop / Disconnect)
                    when {
                        isBleConnected -> {
                            Button(
                                onClick = onDisconnectBle,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorRed),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text(
                                    text = "DISCONNECT SENSOR",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                        isBleScanning -> {
                            Button(
                                onClick = onStopBleScan,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.warningAmber),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text(
                                    text = "STOP SCANNING",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.background
                                )
                            }
                        }
                        isBleConnecting -> {
                            Button(
                                onClick = onDisconnectBle,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Text(
                                    text = "CANCEL CONNECTION",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        else -> {
                            Button(
                                onClick = { requestBleScanOrPermissions() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryCyan),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bluetooth,
                                    contentDescription = "Scan",
                                    tint = MaterialTheme.colorScheme.background,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SCAN & CONNECT TO LEAN_IOT",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.background
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Expandable Diagnostics Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .clickable { showDiagnostics = !showDiagnostics }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SENSOR HARDWARE DIAGNOSTICS",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (showDiagnostics) "Hide ▲" else "Details ▼",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primaryCyan
                        )
                    }

                    if (showDiagnostics && leanState != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        leanState.availableSensors.forEach { info ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = info.typeName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = info.displayStatusText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (info.hardwareType) {
                                            SensorHardwareType.PHYSICAL -> MaterialTheme.colorScheme.primaryLime
                                            SensorHardwareType.LOGICAL -> MaterialTheme.colorScheme.warningAmber
                                            SensorHardwareType.FUSED -> MaterialTheme.colorScheme.primaryCyan
                                            SensorHardwareType.UNAVAILABLE -> MaterialTheme.colorScheme.error
                                        }
                                    )
                                }
                                Text(
                                    text = "Name: ${info.name} | Vendor: ${info.vendor}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 3: Smoothing
            SettingsSectionHeader("SMOOTHING LEVEL")

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    SmoothingLevel.values().forEach { level ->
                        val isSelected = (settings.smoothingLevel == level)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                                .clickable { onSmoothingLevelChange(level) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSmoothingLevelChange(level) },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primaryCyan)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = level.displayName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val detail = when (level) {
                                    SmoothingLevel.LOW -> "Fastest response (alpha = 0.65). Best for aggressive entry."
                                    SmoothingLevel.MEDIUM -> "Balanced response (alpha = 0.80). Recommended default."
                                    SmoothingLevel.HIGH -> "Maximum stability (alpha = 0.92). Highly jitter resistant."
                                }
                                Text(
                                    text = detail,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 4: Ride Thresholds
            SettingsSectionHeader("RIDE ANALYSIS THRESHOLDS")

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ThresholdSlider(
                        title = "Straight Dead-Zone Threshold",
                        value = settings.straightThreshold,
                        range = 1f..10f,
                        unit = "°",
                        onValueChange = onStraightThresholdChange
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ThresholdSlider(
                        title = "Warning Threshold",
                        value = settings.warningThreshold,
                        range = 20f..45f,
                        unit = "°",
                        onValueChange = onWarningThresholdChange
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ThresholdSlider(
                        title = "Critical Threshold",
                        value = settings.criticalThreshold,
                        range = 30f..60f,
                        unit = "°",
                        onValueChange = onCriticalThresholdChange
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 5: Emergency Assistance
            SettingsSectionHeader("EMERGENCY ASSISTANCE (BETA)")
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Fall Detection", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Estimates possible falls based on lean angle and time.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        androidx.compose.material3.Switch(
                            checked = settings.isFallDetectionEnabled,
                            onCheckedChange = onFallDetectionEnabledChange,
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primaryCyan,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryCyan.copy(alpha = 0.5f)
                            )
                        )
                    }

                    if (settings.isFallDetectionEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        ThresholdSlider(
                            title = "Critical Angle",
                            value = settings.fallDetectionCriticalAngle,
                            range = 40f..85f,
                            unit = "°",
                            onValueChange = onFallDetectionCriticalAngleChange
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Confirmation Time: 10 seconds (Fixed)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Emergency SMS", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("Automatically text emergency contact.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            androidx.compose.material3.Switch(
                                checked = settings.isEmergencySmsEnabled,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        handleSmsPermissionCheck(PendingSmsAction.ENABLE_TOGGLE)
                                    } else {
                                        onEmergencySmsEnabledChange(false)
                                    }
                                },
                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primaryCyan,
                                    checkedTrackColor = MaterialTheme.colorScheme.primaryCyan.copy(alpha = 0.5f)
                                )
                            )
                        }

                        if (settings.isEmergencySmsEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            androidx.compose.material3.OutlinedTextField(
                                value = settings.emergencyPhoneNumber,
                                onValueChange = onEmergencyPhoneNumberChange,
                                label = { Text("Emergency Phone Number") },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth(),
                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primaryCyan,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                )
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    handleSmsPermissionCheck(PendingSmsAction.TEST_SMS)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("Test Emergency SMS", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            if (showPermanentDenialDialog) {
                AlertDialog(
                    onDismissRequest = { showPermanentDenialDialog = false },
                    title = {
                        Text(
                            text = "SMS Permission Required",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    text = {
                        Text(
                            text = "SMS permission is disabled for LEAN.\nPlease enable it in Android Settings to use Emergency SOS.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showPermanentDenialDialog = false
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null)
                                    )
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Unable to open Settings", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryCyan)
                        ) {
                            Text("Open Settings", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPermanentDenialDialog = false }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 6: Calibration & Settings Reset Controls
            SettingsSectionHeader("RESET CONTROLS")

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            try {
                                onResetCalibration()
                                Toast.makeText(context, "Calibration reset", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Unable to reset calibration", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Reset Zero Reference", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    }

                    Button(
                        onClick = {
                            try {
                                onResetPeak()
                                Toast.makeText(context, "Peak angles reset", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Unable to reset peak angles", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Reset Peak Angles", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Button(
                    onClick = {
                        try {
                            onResetSettings?.invoke()
                            Toast.makeText(context, "Settings reset successfully", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Unable to reset settings", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Reset All Settings to Default", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.warningAmber)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // About / LEAN Branding & Safety Disclaimer
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LeanLogo(size = 36.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "LEAN",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Take Your Corner",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Disclaimer",
                            tint = MaterialTheme.colorScheme.warningAmber,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "LEAN provides approximate sensor-based measurements for personal ride analysis.\n\nLean-angle measurements can be affected by phone mounting position, acceleration, braking, road conditions, sensor limitations, and other factors.\n\nThe displayed values should not be considered safety-certified measurements or instructions for motorcycle operation.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "LEAN • Take Your Corner v2.0 • Offline Ready",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.textMuted,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.textMuted,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SensorAvailabilityRow(label: String, hwType: SensorHardwareType) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        val (statusText, statusColor) = when (hwType) {
            SensorHardwareType.PHYSICAL -> Pair("● Available • Physical", MaterialTheme.colorScheme.primaryLime)
            SensorHardwareType.LOGICAL -> Pair("⚠ Logical sensor (No physical gyro)", MaterialTheme.colorScheme.warningAmber)
            SensorHardwareType.FUSED -> Pair("✓ Available • Fused sensor", MaterialTheme.colorScheme.primaryCyan)
            SensorHardwareType.UNAVAILABLE -> Pair("✕ Not available", MaterialTheme.colorScheme.error)
        }

        Text(
            text = statusText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = statusColor
        )
    }
}

@Composable
private fun ThresholdSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = String.format(Locale.US, "%.1f%s", value, unit),
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primaryLime
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primaryCyan,
                activeTrackColor = MaterialTheme.colorScheme.primaryCyan,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun ProfileSettingCard(
    profile: RidingProfile,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primaryCyan else MaterialTheme.colorScheme.outline
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryCyan.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (selected) MaterialTheme.colorScheme.primaryCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) MaterialTheme.colorScheme.primaryCyan else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
