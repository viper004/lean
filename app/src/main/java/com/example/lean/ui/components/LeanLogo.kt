package com.example.lean.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.lean.R
import com.example.lean.ui.theme.isDark

@Composable
fun LeanLogo(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val isDark = MaterialTheme.colorScheme.isDark

    if (isDark) {
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "LEAN Logo",
            modifier = modifier.size(size)
        )
    } else {
        // In Light Mode, wrap the white artwork in a sleek black rounded container
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape((size.value * 0.22f).dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "LEAN Logo",
                modifier = Modifier
                    .size((size.value * 0.85f).dp)
                    .padding(2.dp)
            )
        }
    }
}
