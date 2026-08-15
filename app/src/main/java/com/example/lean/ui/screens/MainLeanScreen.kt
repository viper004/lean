package com.example.lean.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lean.data.LeanState
import com.example.lean.ui.components.CenterButton
import com.example.lean.ui.components.CenteredToastOverlay
import com.example.lean.ui.components.LeanAngleDisplay
import com.example.lean.ui.components.LeanGauge
import com.example.lean.ui.components.PeakLeanCard
import com.example.lean.ui.components.SensorStatusChip

@Composable
fun MainLeanScreen(
    state: LeanState,
    onCenterClick: () -> Unit,
    onResetPeakClick: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDebug: () -> Unit,
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "LEAN ANGLE METER",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (state.isCalibrated) "Calibrated Zero" else "Tap CENTER to Calibrate",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(onClick = onNavigateToDebug) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Debug Screen",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Lean Angle Numeric Display
            LeanAngleDisplay(
                angleDegrees = state.filteredAngleDegrees,
                directionText = state.directionText,
                displayAngleText = state.displayAngleText
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Visual Horizontal Lean Gauge
            LeanGauge(
                currentAngleDegrees = state.filteredAngleDegrees,
                maxLeftDegrees = state.maxLeftDegrees,
                maxRightDegrees = state.maxRightDegrees
            )

            Spacer(modifier = Modifier.height(16.dp))

            // CENTER Calibration Button
            CenterButton(
                onCenterClick = onCenterClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Peak Lean Tracker Card
            PeakLeanCard(
                maxLeftDegrees = state.maxLeftDegrees,
                maxRightDegrees = state.maxRightDegrees,
                onResetPeakClick = onResetPeakClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Sensor Diagnostic Chip
            SensorStatusChip(
                isGyroAvailable = state.isGyroAvailable,
                isAccelAvailable = state.isAccelAvailable,
                activeMode = state.activeMode,
                warningMessage = state.feedbackMessage ?: if (!state.isGyroAvailable) "Gyroscope unavailable — using accelerometer mode." else null,
                errorMessage = if (!state.isAccelAvailable) "Accelerometer unavailable!" else null
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Success Toast Overlay
        CenteredToastOverlay(
            visible = state.showCenteredFeedback,
            message = "Centered",
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp)
        )
    }
}
