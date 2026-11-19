package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FestivalConfig
import kotlinx.coroutines.delay

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

    LaunchedEffect(festivals.size, selectedIndex) {
        if (festivals.size > 1) {
            delay(5000L)
            selectedIndex = (selectedIndex + 1) % festivals.size
        }
    }

    val safeIndex = selectedIndex.coerceIn(0, festivals.lastIndex)
    val current = festivals[safeIndex]
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
            .testTag("festival_header_pill")
            .then(
                if (festivals.size > 1) {
                    Modifier.clickable { selectedIndex = (safeIndex + 1) % festivals.size }
                } else {
                    Modifier
                }
            )
    ) {
        AnimatedContent(
            targetState = safeIndex,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "festivalHeaderPillTransition"
        ) { displayIndex ->
            val item = festivals[displayIndex.coerceIn(0, festivals.lastIndex)]
            val itemResolved = remember(item) { resolveFestivalStyle(item) }
            val itemColor = itemResolved.primaryColor
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = itemColor
                ) {
                    Text(
                        text = itemResolved.badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = item.greetingMessage.ifBlank { item.festivalName },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            initialDelayMillis = 600,
                            repeatDelayMillis = 1200,
                            velocity = 36.dp
                        )
                        .testTag("festival_header_pill_message")
                )
                if (festivals.size > 1) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = itemColor.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = "${displayIndex + 1}/${festivals.size}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = itemColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
