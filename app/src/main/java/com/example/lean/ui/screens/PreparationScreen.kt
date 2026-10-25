package com.example.lean.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lean.data.LeanState
import com.example.lean.location.LocationData
import com.example.lean.ui.theme.isDark
import com.example.lean.ui.theme.primaryCyan
import com.example.lean.ui.theme.primaryLime
import com.example.lean.ui.theme.textMuted
import com.example.lean.ui.theme.warningAmber

@Composable
fun PreparationScreen(
    leanState: LeanState,
    locationData: LocationData,
    isGpsSettingEnabled: Boolean,
    onCenterAndStartClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.isDark

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
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
                    text = "RIDE PREPARATION",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = 1.2.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Title Banner
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "READY TO RIDE",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primaryLime,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Position your motorcycle straight and ensure your phone is securely mounted before starting.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Phone / ESP32 Mounting Notice Card
            val isBleMode = leanState.activeMode == com.example.lean.data.SensorMode.EXTERNAL_BLE
            val canStart = if (isBleMode) leanState.isExternalSensorConnected else true

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isBleMode) Icons.Default.BluetoothConnected else Icons.Default.PhonelinkSetup,
                        contentDescription = if (isBleMode) "ESP32 Sensor" else "Mount Phone",
                        tint = MaterialTheme.colorScheme.primaryCyan,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = if (isBleMode) "ESP32 IoT Sensor" else "Phone Mounting",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isBleMode) {
                                "Ensure your ESP32 sensor is powered on and secured to the motorcycle frame or handlebar. Pressing CENTER & START will define current upright motorcycle position as 0°."
                            } else {
                                "Any orientation is supported. Pressing CENTER & START will define the current position as 0° reference."
                            },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isBleMode && !canStart) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.warningAmber, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BluetoothDisabled,
                            contentDescription = "Sensor Disconnected",
                            tint = MaterialTheme.colorScheme.warningAmber,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "ESP32 Sensor Disconnected",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.warningAmber
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "An active connection to LEAN_IOT is required to start a ride in External BLE mode. Please turn on your sensor and connect in Settings.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // System Status
            Text(
                text = "SYSTEM STATUS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.textMuted,
                letterSpacing = 1.5.sp,
                modifier = Modifier.align(Alignment.Start).padding(start = 4.dp, bottom = 8.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (isBleMode) {
                        val bleStatus = when (leanState.bleConnectionState) {
                            com.example.lean.bluetooth.BleConnectionState.CONNECTED -> "CONNECTED (${leanState.bleDeviceName ?: "LEAN_IOT"})"
                            com.example.lean.bluetooth.BleConnectionState.CONNECTING -> "CONNECTING..."
                            com.example.lean.bluetooth.BleConnectionState.SCANNING -> "SCANNING..."
                            com.example.lean.bluetooth.BleConnectionState.DISCONNECTING -> "DISCONNECTING"
                            com.example.lean.bluetooth.BleConnectionState.DISCONNECTED -> "DISCONNECTED"
                        }
                        StatusRow(
                            label = "ESP32 BLE SENSOR",
                            statusText = bleStatus,
                            isActive = leanState.isExternalSensorConnected,
                            warning = !leanState.isExternalSensorConnected
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        StatusRow(
                            label = "TELEMETRY STREAM",
                            statusText = if (leanState.bleLastPacket != null && leanState.isExternalSensorConnected) "ACTIVE (#${leanState.bleLastPacket.sequenceId})" else "WAITING",
                            isActive = leanState.isExternalSensorConnected && leanState.bleLastPacket != null,
                            warning = !leanState.isExternalSensorConnected || leanState.bleLastPacket == null
                        )
                    } else {
                        StatusRow(
                            label = "GYROSCOPE",
                            statusText = if (leanState.isGyroAvailable) "ACTIVE" else "UNAVAILABLE (Fallback)",
                            isActive = leanState.isGyroAvailable,
                            warning = !leanState.isGyroAvailable
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        StatusRow(
                            label = "ACCELEROMETER",
                            statusText = if (leanState.isAccelAvailable) "ACTIVE" else "UNAVAILABLE",
                            isActive = leanState.isAccelAvailable
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    StatusRow(
                        label = "GPS",
                        statusText = if (!isGpsSettingEnabled) "OFF (Disabled in Settings)" else locationData.statusMessage,
                        isActive = isGpsSettingEnabled && locationData.isGpsActive,
                        warning = !isGpsSettingEnabled || !locationData.isGpsActive
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // CENTER & START Button (Requirement 6)
            Button(
                onClick = { if (canStart) onCenterAndStartClick() },
                enabled = canStart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) MaterialTheme.colorScheme.primaryCyan else MaterialTheme.colorScheme.onBackground,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .border(
                        width = if (isDark || !canStart) 0.dp else 2.dp,
                        color = MaterialTheme.colorScheme.onBackground,
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (!canStart) Icons.Default.Warning else Icons.Default.CompassCalibration,
                        contentDescription = "Center and Start",
                        tint = if (canStart) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.textMuted,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (!canStart) "CONNECT ESP32 TO START" else "CENTER & START",
                        fontSize = if (!canStart) 16.sp else 20.sp,
                        fontWeight = FontWeight.Black,
                        color = if (canStart) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.textMuted,
                        letterSpacing = 1.2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    statusText: String,
    isActive: Boolean,
    warning: Boolean = false
) {
    val activeColor = MaterialTheme.colorScheme.primaryLime
    val warningColor = MaterialTheme.colorScheme.warningAmber
    val mutedColor = MaterialTheme.colorScheme.textMuted

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isActive) activeColor else if (warning) warningColor else mutedColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusText,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (isActive) activeColor else if (warning) warningColor else mutedColor
            )
        }
    }
}
