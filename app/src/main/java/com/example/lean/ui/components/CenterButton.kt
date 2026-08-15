package com.example.lean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lean.ui.theme.DarkBackground
import com.example.lean.ui.theme.LightBackground
import com.example.lean.ui.theme.LightTextPrimary
import com.example.lean.ui.theme.PrimaryCyan
import com.example.lean.ui.theme.PrimaryLime
import com.example.lean.ui.theme.isDark

@Composable
fun CenterButton(
    onCenterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.isDark

    val containerColor = if (isDark) DarkBackground else LightBackground
    val textColor = if (isDark) DarkBackground else LightTextPrimary
    val borderColor = if (isDark) PrimaryCyan else LightTextPrimary

    Button(
        onClick = onCenterClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(64.dp)
            .border(2.dp, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = textColor
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 6.dp,
            pressedElevation = 2.dp
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(
                    brush = if (isDark) {
                        Brush.horizontalGradient(colors = listOf(PrimaryCyan, PrimaryLime))
                    } else {
                        Brush.horizontalGradient(colors = listOf(Color(0xFFFFFFFF), Color(0xFFF0F0F0)))
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "CENTER (SET ZERO)",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                color = textColor
            )
        }
    }
}
