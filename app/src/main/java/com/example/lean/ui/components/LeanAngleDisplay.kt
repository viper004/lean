package com.example.lean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lean.ui.theme.accentOrange
import com.example.lean.ui.theme.isDark
import com.example.lean.ui.theme.primaryCyan
import com.example.lean.ui.theme.primaryLime
import com.example.lean.ui.theme.textMuted
import kotlin.math.abs

@Composable
fun LeanAngleDisplay(
    angleDegrees: Float,
    directionText: String,
    displayAngleText: String,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.isDark
    val angleAbs = abs(angleDegrees)

    val angleColor = if (isDark) {
        when {
            angleAbs > 35f -> MaterialTheme.colorScheme.accentOrange
            angleAbs > 20f -> MaterialTheme.colorScheme.primaryCyan
            else -> MaterialTheme.colorScheme.primaryLime
        }
    } else {
        // High contrast for sunlight readability in light mode
        when {
            angleAbs > 35f -> MaterialTheme.colorScheme.accentOrange
            angleAbs > 20f -> MaterialTheme.colorScheme.primaryCyan
            else -> MaterialTheme.colorScheme.onSurface
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
            .padding(vertical = 24.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "LEAN ANGLE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Main Angle Numeric Display
            Text(
                text = displayAngleText,
                fontSize = 72.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                color = angleColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            // LEFT / RIGHT Indicator Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "← LEFT",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (directionText == "LEFT") MaterialTheme.colorScheme.accentOrange else MaterialTheme.colorScheme.textMuted
                )

                Text(
                    text = directionText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 1.5.sp
                )

                Text(
                    text = "RIGHT →",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (directionText == "RIGHT") MaterialTheme.colorScheme.primaryCyan else MaterialTheme.colorScheme.textMuted
                )
            }
        }
    }
}
