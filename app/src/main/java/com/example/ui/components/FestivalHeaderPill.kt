package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val activeList = remember(festivalConfig) {
        if (festivalConfig.isActiveOn()) listOf(festivalConfig) else emptyList()
    }
    FestivalHeaderPill(festivals = activeList, modifier = modifier)
}

@Composable
fun FestivalHeaderPill(
    festivals: List<FestivalConfig>,
    modifier: Modifier = Modifier
) {
    if (festivals.isEmpty()) return

    var selectedIndex by remember(festivals.size) { mutableIntStateOf(0) }
    val current = festivals[selectedIndex.coerceIn(0, festivals.lastIndex)]
    val resolved = remember(current) { resolveFestivalStyle(current) }
    val festColor = resolved.primaryColor

    val pillShape = RoundedCornerShape(10.dp)
    Surface(
        shape = pillShape,
        color = festColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, festColor.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(pillShape)
            .then(
                if (festivals.size > 1) {
                    Modifier.clickable { selectedIndex = (selectedIndex + 1) % festivals.size }
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = festColor
            ) {
                Text(
                    text = resolved.badgeText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Text(
                text = current.greetingMessage.ifBlank { current.festivalName },
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (festivals.size > 1) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = festColor.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = "${selectedIndex + 1}/${festivals.size}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = festColor,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
