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
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lean.data.LeanState
import com.example.lean.location.LocationData
import com.example.lean.recorder.ActiveRideSession
import com.example.lean.ui.components.CenteredToastOverlay
import com.example.lean.ui.components.LeanAngleDisplay
import com.example.lean.ui.components.LeanGauge
import com.example.lean.ui.theme.accentOrange
import com.example.lean.ui.theme.errorRed
import com.example.lean.ui.theme.primaryCyan
import com.example.lean.ui.theme.primaryLime
import com.example.lean.ui.theme.textMuted
import java.util.Locale

@Composable
fun LiveRideScreen(
    leanState: LeanState,
    sessionState: ActiveRideSession,
    locationData: LocationData,
    isGpsSettingEnabled: Boolean,
    onReCenterClick: () -> Unit,
    onEndRideConfirmed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showEndRideDialog by remember { mutableStateOf(false) }

    if (showEndRideDialog) {
        AlertDialog(
            onDismissRequest = { showEndRideDialog = false },
            title = {
                Text(
                    text = "END RIDE?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to finish and save this ride session?",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEndRideDialog = false
                        onEndRideConfirmed()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorRed)
                ) {
                    Text("END RIDE", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndRideDialog = false }) {
                    Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.errorRed)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LIVE RIDE RECORDING",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.errorRed
                    )
                }

                IconButton(onClick = onReCenterClick) {
                    Icon(
                        imageVector = Icons.Default.CompassCalibration,
                        contentDescription = "Re-Center Zero",
                        tint = MaterialTheme.colorScheme.primaryCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Current Lean Numeric Display (Theme-Aware)
            LeanAngleDisplay(
                angleDegrees = leanState.filteredAngleDegrees,
                directionText = leanState.directionText,
                displayAngleText = leanState.displayAngleText
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Visual Horizontal Lean Gauge (Theme-Aware)
            LeanGauge(
                currentAngleDegrees = leanState.filteredAngleDegrees,
                maxLeftDegrees = sessionState.maxLeftLean,
                maxRightDegrees = sessionState.maxRightLean
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Statistics Row Cards (Duration, Speed)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Duration Timer Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Duration",
                                tint = MaterialTheme.colorScheme.primaryCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("DURATION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.textMuted)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatLiveTimer(sessionState.durationMs),
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Speed Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Speed",
                                tint = MaterialTheme.colorScheme.primaryLime,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SPEED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.textMuted)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isGpsSettingEnabled && locationData.isGpsActive) {
                                String.format(Locale.US, "%.0f km/h", locationData.currentSpeedKmh)
                            } else "GPS Off",
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (locationData.isGpsActive) MaterialTheme.colorScheme.primaryLime else MaterialTheme.colorScheme.textMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Peak Lean Stats Row Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("MAX LEFT LEAN", fontSize = 11.sp, color = MaterialTheme.colorScheme.textMuted, fontWeight = FontWeight.Bold)
                        Text(
                            text = String.format(Locale.US, "%.1f°", sessionState.maxLeftLean),
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.accentOrange
                        )
                    }

                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outline))

                    Column {
                        Text("MAX RIGHT LEAN", fontSize = 11.sp, color = MaterialTheme.colorScheme.textMuted, fontWeight = FontWeight.Bold)
                        Text(
                            text = String.format(Locale.US, "%.1f°", sessionState.maxRightLean),
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primaryCyan
                        )
                    }

                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.outline))

                    Column {
                        Text("MAX ABSOLUTE", fontSize = 11.sp, color = MaterialTheme.colorScheme.textMuted, fontWeight = FontWeight.Bold)
                        Text(
                            text = String.format(Locale.US, "%.1f°", sessionState.maxAbsoluteLean),
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primaryLime
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // END RIDE Button (Requirement 7)
            Button(
                onClick = { showEndRideDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorRed),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "End Ride",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "END RIDE",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 1.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        CenteredToastOverlay(
            visible = leanState.showCenteredFeedback,
            message = "Re-Centered",
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp)
        )
    }
}

private fun formatLiveTimer(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}
