package com.example.drivepool.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun StorageProgressBar(
    usedPercent: Float,
    formattedUsed: String,
    formattedTotal: String,
    modifier: Modifier = Modifier,
    barHeight: Dp = 10.dp,
    showLabels: Boolean = true,
    isBufferProtected: Boolean = false
) {
    val animatedPercent by animateFloatAsState(
        targetValue = (usedPercent / 100f).coerceIn(0f, 1f),
        label = "StoragePercent"
    )

    val progressColor = when {
        isBufferProtected -> Color(0xFFE37400) // Amber / Warning
        usedPercent > 92f -> Color(0xFFD93025) // Critical Red
        usedPercent > 75f -> Color(0xFFF29900) // Orange
        else -> Color(0xFF1A73E8)             // Google Blue
    }

    Column(modifier = modifier) {
        if (showLabels) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$formattedUsed used of $formattedTotal",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = String.format("%.1f%%", usedPercent),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = progressColor
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(barHeight / 2))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedPercent)
                    .height(barHeight)
                    .clip(RoundedCornerShape(barHeight / 2))
                    .background(progressColor)
            )
        }
    }
}
