package com.example.lean.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lean.data.AppThemeMode
import com.example.lean.data.SensorMode
import com.example.lean.data.SmoothingLevel
import com.example.lean.data.UserSettings
import com.example.lean.sensor.SensorHardwareType
import com.example.lean.ui.components.LeanLogo
import com.example.lean.ui.theme.primaryCyan
import com.example.lean.ui.theme.primaryLime
import com.example.lean.ui.theme.textMuted
import com.example.lean.ui.theme.warningAmber
import java.util.Locale

@Composable
fun SettingsScreen(
    settings: UserSettings,
    leanState: com.example.lean.data.LeanState? = null,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onSensorModeChange: (SensorMode) -> Unit,
    onSmoothingLevelChange: (SmoothingLevel) -> Unit,
    onKeepScreenAwakeChange: (Boolean) -> Unit,
    onLockOrientationChange: (Boolean) -> Unit,
    onStraightThresholdChange: (Float) -> Unit,
    onWarningThresholdChange: (Float) -> Unit,
    onCriticalThresholdChange: (Float) -> Unit,
    onGpsEnabledChange: (Boolean) -> Unit,
    onResetCalibration: () -> Unit,
    onResetPeak: () -> Unit,
    onResetSettings: (() -> Unit)? = null,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDiagnostics by remember { mutableStateOf(false) }

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

            // Section 5: Calibration & Settings Reset Controls
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
