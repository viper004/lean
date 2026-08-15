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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lean.data.LeanSafetyRating
import com.example.lean.data.RideEntity
import com.example.lean.ui.theme.accentOrange
import com.example.lean.ui.theme.errorRed
import com.example.lean.ui.theme.isDark
import com.example.lean.ui.theme.primaryCyan
import com.example.lean.ui.theme.primaryLime
import com.example.lean.ui.theme.textMuted
import com.example.lean.ui.theme.warningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RideSummaryScreen(
    ride: RideEntity,
    isHistoricalView: Boolean = false,
    onDoneClick: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.isDark

    val rating = LeanSafetyRating.evaluate(ride.safetyWarningPercentage, ride.safetyCriticalPercentage)
    val ratingColor = when (rating) {
        LeanSafetyRating.GOOD -> MaterialTheme.colorScheme.primaryLime
        LeanSafetyRating.CAUTION -> MaterialTheme.colorScheme.warningAmber
        LeanSafetyRating.HIGH -> MaterialTheme.colorScheme.errorRed
    }

    val dateFormat = SimpleDateFormat("hh:mm a", Locale.US)
    val dateHeaderFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.US)

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
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isHistoricalView && onBackClick != null) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column {
                    Text(
                        text = if (isHistoricalView) "RIDE DETAILS" else "RIDE COMPLETE",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primaryLime,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = dateHeaderFormat.format(Date(ride.startTimeMs)),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Time & Duration Header Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("TOTAL DURATION", fontSize = 11.sp, color = MaterialTheme.colorScheme.textMuted, fontWeight = FontWeight.Bold)
                        Text(
                            text = formatDuration(ride.durationMs),
                            fontSize = 26.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primaryCyan
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("START / END", fontSize = 11.sp, color = MaterialTheme.colorScheme.textMuted, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${dateFormat.format(Date(ride.startTimeMs))} - ${dateFormat.format(Date(ride.endTimeMs))}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 1: Lean Angle Peaks
            SectionTitle("LEAN ANGLE PEAKS")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PeakCol("MAX LEFT", ride.maxLeftLean, MaterialTheme.colorScheme.accentOrange)
                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(MaterialTheme.colorScheme.outline))
                    PeakCol("MAX RIGHT", ride.maxRightLean, MaterialTheme.colorScheme.primaryCyan)
                    Box(modifier = Modifier.width(1.dp).height(36.dp).background(MaterialTheme.colorScheme.outline))
                    PeakCol("MAX ABSOLUTE", ride.maxAbsoluteLean, MaterialTheme.colorScheme.primaryLime)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 2: Ride Distribution
            SectionTitle("RIDE DISTRIBUTION")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Visual Horizontal Distribution Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (ride.straightPercentage > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(ride.straightPercentage.coerceAtLeast(1f))
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.primaryLime)
                            )
                        }
                        if (ride.leftLeanPercentage > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(ride.leftLeanPercentage.coerceAtLeast(1f))
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.accentOrange)
                            )
                        }
                        if (ride.rightLeanPercentage > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(ride.rightLeanPercentage.coerceAtLeast(1f))
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.primaryCyan)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DistCol("STRAIGHT", String.format(Locale.US, "%.1f%%", ride.straightPercentage), MaterialTheme.colorScheme.primaryLime)
                        DistCol("LEFT LEAN", String.format(Locale.US, "%.1f%%", ride.leftLeanPercentage), MaterialTheme.colorScheme.accentOrange)
                        DistCol("RIGHT LEAN", String.format(Locale.US, "%.1f%%", ride.rightLeanPercentage), MaterialTheme.colorScheme.primaryCyan)
                        DistCol("LEAN TIME", String.format(Locale.US, "%.1f%%", ride.leanPercentage), MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 3: Lean Safety Analysis
            SectionTitle("LEAN SAFETY ANALYSIS")
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Safety",
                                tint = ratingColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("RIDE SAFETY RATING", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ratingColor.copy(alpha = 0.2f))
                                .border(1.dp, ratingColor, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = rating.displayName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = ratingColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    SafetyRow("Preferred Range (< 30°)", String.format(Locale.US, "%.1f%%", ride.safetyPreferredPercentage), MaterialTheme.colorScheme.primaryLime)
                    SafetyRow("Warning Range (30°–40°)", String.format(Locale.US, "%.1f%%", ride.safetyWarningPercentage), MaterialTheme.colorScheme.warningAmber)
                    SafetyRow("Critical Range (40°+)", String.format(Locale.US, "%.1f%%", ride.safetyCriticalPercentage), MaterialTheme.colorScheme.errorRed)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 4: GPS Telemetry (if available)
            if (ride.isGpsEnabled || ride.distanceKm > 0) {
                SectionTitle("GPS TELEMETRY")
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PeakCol("DISTANCE", String.format(Locale.US, "%.1f km", ride.distanceKm), MaterialTheme.colorScheme.primaryLime)
                        Box(modifier = Modifier.width(1.dp).height(36.dp).background(MaterialTheme.colorScheme.outline))
                        PeakCol("AVG SPEED", String.format(Locale.US, "%.0f km/h", ride.averageSpeedKmh), MaterialTheme.colorScheme.primaryCyan)
                        Box(modifier = Modifier.width(1.dp).height(36.dp).background(MaterialTheme.colorScheme.outline))
                        PeakCol("MAX SPEED", String.format(Locale.US, "%.0f km/h", ride.maxSpeedKmh), MaterialTheme.colorScheme.accentOrange)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Safety Disclaimer Note
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    tint = MaterialTheme.colorScheme.textMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Personal riding analysis metric. Not safety-certified measurements.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.textMuted
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Actions: SAVE RIDE / DONE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isHistoricalView) {
                    Button(
                        onClick = onDoneClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) MaterialTheme.colorScheme.primaryLime else MaterialTheme.colorScheme.onBackground
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(54.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Done",
                                tint = MaterialTheme.colorScheme.background
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SAVE & DONE", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.background)
                        }
                    }
                } else {
                    Button(
                        onClick = onDoneClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) MaterialTheme.colorScheme.primaryCyan else MaterialTheme.colorScheme.onBackground
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    ) {
                        Text("BACK TO RIDES", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.background)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.textMuted,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun PeakCol(label: String, valueDegrees: Float, color: Color) {
    Column {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.textMuted, fontWeight = FontWeight.Bold)
        Text(
            text = String.format(Locale.US, "%.1f°", valueDegrees),
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun PeakCol(label: String, valueText: String, color: Color) {
    Column {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.textMuted, fontWeight = FontWeight.Bold)
        Text(
            text = valueText,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun DistCol(label: String, valueText: String, color: Color) {
    Column {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.textMuted, fontWeight = FontWeight.Bold)
        Text(
            text = valueText,
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun SafetyRow(label: String, pctText: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(pctText, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = color)
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
