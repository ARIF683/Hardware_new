package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FestivalConfig

@Composable
fun FestivalHeaderPill(
    festivalConfig: FestivalConfig,
    modifier: Modifier = Modifier
) {
    if (!festivalConfig.enabled || festivalConfig.greetingMessage.isBlank()) return

    val festColor = remember(festivalConfig.accentColorHex) {
        try {
            Color(android.graphics.Color.parseColor(festivalConfig.accentColorHex))
        } catch (_: Exception) {
            Color(0xFFD97757)
        }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = festColor.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, festColor.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = festColor
                ) {
                    Text(
                        text = festivalConfig.badgeText.ifBlank { "✨ FEST" },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = festivalConfig.greetingMessage,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
