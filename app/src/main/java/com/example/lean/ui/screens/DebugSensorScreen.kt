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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lean.data.LeanState
import com.example.lean.ui.theme.primaryLime
import com.example.lean.ui.theme.textMuted
import com.example.lean.ui.theme.warningAmber
import java.util.Locale

@Composable
fun DebugSensorScreen(
    state: LeanState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                    text = "SENSOR DIAGNOSTICS",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = 1.2.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card 1: Sensor Availability & Active Mode
            DebugCardHeader("HARDWARE AVAILABILITY & MODE")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DebugRow(
                        label = "Accelerometer",
                        value = if (state.isAccelAvailable) "PRESENT (ACTIVE)" else "NOT FOUND",
                        isHighlight = state.isAccelAvailable
                    )
                    DebugRow(
                        label = "Gyroscope",
                        value = if (state.isGyroAvailable) "PRESENT (ACTIVE)" else "UNAVAILABLE (Fallback)",
                        isHighlight = state.isGyroAvailable
                    )
                    DebugRow(
                        label = "Active Mode",
                        value = state.activeMode.displayName
                    )
                    DebugRow(
                        label = "Sensor Update Rate",
                        value = "${state.sensorFps} Hz"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card 2: Raw Accelerometer Data
            DebugCardHeader("ACCELEROMETER (m/s²)")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DebugRow("Raw X", String.format(Locale.US, "%.3f", state.rawAccel.first))
                    DebugRow("Raw Y", String.format(Locale.US, "%.3f", state.rawAccel.second))
                    DebugRow("Raw Z", String.format(Locale.US, "%.3f", state.rawAccel.third))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card 3: Raw Gyroscope Data
            DebugCardHeader("GYROSCOPE (rad/s)")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (state.isGyroAvailable) {
                        DebugRow("Gyro X", String.format(Locale.US, "%.4f", state.rawGyro.first))
                        DebugRow("Gyro Y", String.format(Locale.US, "%.4f", state.rawGyro.second))
                        DebugRow("Gyro Z", String.format(Locale.US, "%.4f", state.rawGyro.third))
                    } else {
                        Text(
                            text = "Gyroscope unavailable on this hardware. Sensor fusion fallback active.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.warningAmber,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card 4: Lean Calculations
            DebugCardHeader("ORIENTATION & LEAN ANGLES")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DebugRow("Is Calibrated Zero", if (state.isCalibrated) "YES (Reference Set)" else "NO (Press CENTER)")
                    DebugRow("Raw Lean Angle", String.format(Locale.US, "%.2f°", state.currentAngleDegrees))
                    DebugRow("Filtered Lean Angle", String.format(Locale.US, "%.2f°", state.filteredAngleDegrees), isHighlight = true)
                    DebugRow("Peak Left Angle", String.format(Locale.US, "%.1f°", state.maxLeftDegrees))
                    DebugRow("Peak Right Angle", String.format(Locale.US, "%.1f°", state.maxRightDegrees))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DebugCardHeader(title: String) {
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
private fun DebugRow(label: String, value: String, isHighlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = if (isHighlight) MaterialTheme.colorScheme.primaryLime else MaterialTheme.colorScheme.onSurface
        )
    }
}
