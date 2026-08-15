package com.example.lean.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.example.lean.ui.theme.accentOrange
import com.example.lean.ui.theme.isDark
import com.example.lean.ui.theme.primaryCyan
import com.example.lean.ui.theme.primaryLime

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun LeanGauge(
    currentAngleDegrees: Float,
    maxLeftDegrees: Float,
    maxRightDegrees: Float,
    modifier: Modifier = Modifier,
    maxScaleAngle: Float = 60f
) {
    val isDark = MaterialTheme.colorScheme.isDark
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryCyanColor = MaterialTheme.colorScheme.primaryCyan
    val accentOrangeColor = MaterialTheme.colorScheme.accentOrange
    val primaryLimeColor = MaterialTheme.colorScheme.primaryLime
    val pillColor = if (isDark) Color.White else Color(0xFF111111)

    val animatedAngle by animateFloatAsState(
        targetValue = currentAngleDegrees,
        animationSpec = tween(durationMillis = 80),
        label = "gaugeAngle"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .padding(horizontal = 16.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            val width = size.width
            val height = size.height
            val centerY = height * 0.45f
            val barHeight = 16.dp.toPx()

            val zeroX = width / 2f
            val halfBarWidth = (width / 2f) - 30.dp.toPx()

            // Draw track background
            drawRoundRect(
                color = surfaceVariant,
                topLeft = Offset(zeroX - halfBarWidth, centerY - barHeight / 2f),
                size = Size(halfBarWidth * 2f, barHeight),
                cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f)
            )

            // Draw tick marks (-45, -30, -15, 0, +15, +30, +45)
            val ticks = listOf(-45f, -30f, -15f, 0f, 15f, 30f, 45f)
            val textPaint = android.graphics.Paint().apply {
                color = onSurfaceVariantColor.toArgb()
                textSize = 11.dp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }

            ticks.forEach { tick ->
                val fraction = tick / maxScaleAngle
                val tickX = zeroX + fraction * halfBarWidth
                val isMajor = tick == 0f || abs(tick) == 30f || abs(tick) == 45f
                val tickHeight = if (isMajor) 14.dp.toPx() else 8.dp.toPx()
                val tickColor = if (tick == 0f) primaryCyanColor else outlineColor

                drawLine(
                    color = tickColor,
                    start = Offset(tickX, centerY + barHeight / 2f + 4.dp.toPx()),
                    end = Offset(tickX, centerY + barHeight / 2f + 4.dp.toPx() + tickHeight),
                    strokeWidth = if (tick == 0f) 3.dp.toPx() else 1.5f.dp.toPx()
                )

                if (isMajor) {
                    val label = if (tick == 0f) "0°" else "${abs(tick.toInt())}°"
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        tickX,
                        centerY + barHeight / 2f + 4.dp.toPx() + tickHeight + 14.dp.toPx(),
                        textPaint
                    )
                }
            }

            // Draw peak indicators (small tick marks at max left and max right)
            if (maxLeftDegrees > 0.5f) {
                val peakLeftFraction = min(maxLeftDegrees, maxScaleAngle) / maxScaleAngle
                val peakLeftX = zeroX - peakLeftFraction * halfBarWidth
                drawLine(
                    color = accentOrangeColor.copy(alpha = 0.9f),
                    start = Offset(peakLeftX, centerY - barHeight / 2f - 4.dp.toPx()),
                    end = Offset(peakLeftX, centerY + barHeight / 2f + 4.dp.toPx()),
                    strokeWidth = 2.5f.dp.toPx()
                )
            }

            if (maxRightDegrees > 0.5f) {
                val peakRightFraction = min(maxRightDegrees, maxScaleAngle) / maxScaleAngle
                val peakRightX = zeroX + peakRightFraction * halfBarWidth
                drawLine(
                    color = primaryCyanColor.copy(alpha = 0.9f),
                    start = Offset(peakRightX, centerY - barHeight / 2f - 4.dp.toPx()),
                    end = Offset(peakRightX, centerY + barHeight / 2f + 4.dp.toPx()),
                    strokeWidth = 2.5f.dp.toPx()
                )
            }

            // Draw current active lean fill bar
            val clampedAngle = max(-maxScaleAngle, min(maxScaleAngle, animatedAngle))
            val angleFraction = clampedAngle / maxScaleAngle
            val activeX = zeroX + angleFraction * halfBarWidth

            val barColor = when {
                abs(clampedAngle) > 35f -> accentOrangeColor
                abs(clampedAngle) > 20f -> primaryCyanColor
                else -> primaryLimeColor
            }

            if (abs(clampedAngle) > 0.2f) {
                val fillLeft = min(zeroX, activeX)
                val fillWidth = abs(activeX - zeroX)

                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(fillLeft, centerY - barHeight / 2f + 2.dp.toPx()),
                    size = Size(fillWidth, barHeight - 4.dp.toPx()),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }

            // Draw dynamic indicator pill / cursor at active position
            val pillWidth = 8.dp.toPx()
            val pillHeight = barHeight + 10.dp.toPx()
            drawRoundRect(
                color = pillColor,
                topLeft = Offset(activeX - pillWidth / 2f, centerY - pillHeight / 2f),
                size = Size(pillWidth, pillHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
        }
    }
}
