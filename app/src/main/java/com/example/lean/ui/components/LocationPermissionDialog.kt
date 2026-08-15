package com.example.lean.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lean.ui.theme.isDark
import com.example.lean.ui.theme.primaryCyan
import com.example.lean.ui.theme.textMuted

@Composable
fun LocationPermissionDialog(
    onAllowClick: () -> Unit,
    onDenyClick: () -> Unit,
    onDismissRequest: () -> Unit = onDenyClick
) {
    val isDark = MaterialTheme.colorScheme.isDark

    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = null,
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Location Permission",
                    tint = MaterialTheme.colorScheme.primaryCyan,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "LOCATION ACCESS REQUIRED",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 1.1.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "LEAN needs access to your location to calculate speed, distance, and log GPS telemetry during your motorcycle rides.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAllowClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) MaterialTheme.colorScheme.primaryCyan else MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "ALLOW",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.background,
                    letterSpacing = 1.sp
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDenyClick,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "DENY",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.textMuted,
                    letterSpacing = 1.sp
                )
            }
        }
    )
}
