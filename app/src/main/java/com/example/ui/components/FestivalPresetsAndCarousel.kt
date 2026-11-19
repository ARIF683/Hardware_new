package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.FestivalConfig
import com.example.util.ImageModelHelper
import kotlin.math.cos
import kotlin.math.sin

enum class FestivalPreset(
    val key: String,
    val emoji: String,
    val defaultBadge: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    @param:DrawableRes val defaultBannerRes: Int? = null
) {
    DIWALI(
        key = "diwali",
        emoji = "🪔",
        defaultBadge = "🪔 DIWALI FEST",
        primaryColor = Color(0xFFD97706),
        secondaryColor = Color(0xFFB45309),
        defaultBannerRes = R.drawable.img_festival_diwali
    ),
    EID(
        key = "eid",
        emoji = "🌙",
        defaultBadge = "🌙 EID MUBARAK",
        primaryColor = Color(0xFF059669),
        secondaryColor = Color(0xFFD97706)
    ),
    HOLI(
        key = "holi",
        emoji = "🎨",
        defaultBadge = "🎨 HAPPY HOLI",
        primaryColor = Color(0xFFDB2777),
        secondaryColor = Color(0xFF7C3AED)
    ),
    CHRISTMAS(
        key = "christmas",
        emoji = "🎄",
        defaultBadge = "🎄 MERRY CHRISTMAS",
        primaryColor = Color(0xFF15803D),
        secondaryColor = Color(0xFFDC2626)
    ),
    NEW_YEAR(
        key = "new_year",
        emoji = "🎆",
        defaultBadge = "🎆 HAPPY NEW YEAR",
        primaryColor = Color(0xFF4F46E5),
        secondaryColor = Color(0xFFF59E0B)
    ),
    INDEPENDENCE_DAY(
        key = "independence_day",
        emoji = "🇮🇳",
        defaultBadge = "🇮🇳 JAI HIND",
        primaryColor = Color(0xFFEA580C),
        secondaryColor = Color(0xFF16A34A)
    ),
    SALE(
        key = "sale",
        emoji = "🏷️",
        defaultBadge = "🏷️ FESTIVE OFFER",
        primaryColor = Color(0xFFD97757),
        secondaryColor = Color(0xFFD97706)
    ),
    DEFAULT(
        key = "default",
        emoji = "✨",
        defaultBadge = "✨ FESTIVAL",
        primaryColor = Color(0xFFD97757),
        secondaryColor = Color(0xFFF59E0B)
    );

    companion object {
        fun fromKey(rawKey: String?): FestivalPreset {
            val normalized = rawKey.orEmpty().trim().lowercase()
            return when (normalized) {
                "diwali", "deepavali" -> DIWALI
                "eid", "eid_mubarak", "ramadan" -> EID
                "holi" -> HOLI
                "christmas", "xmas" -> CHRISTMAS
                "new_year", "newyear" -> NEW_YEAR
                "independence_day", "republic_day", "patriot" -> INDEPENDENCE_DAY
                "sale", "offer", "clearance" -> SALE
                else -> DEFAULT
            }
        }
    }
}

data class ResolvedFestivalStyle(
    val preset: FestivalPreset,
    val primaryColor: Color,
    val secondaryColor: Color,
    val badgeText: String
)

fun resolveFestivalStyle(festival: FestivalConfig): ResolvedFestivalStyle {
    val preset = FestivalPreset.fromKey(festival.stylePreset)
    val parsedColor = if (festival.accentColorHex.isNotBlank()) {
        try {
            Color(android.graphics.Color.parseColor(festival.accentColorHex.trim()))
        } catch (_: Exception) {
            null
        }
    } else {
        null
    }
    val primary = parsedColor ?: preset.primaryColor
    val secondary = if (parsedColor != null && preset == FestivalPreset.DEFAULT) {
        parsedColor.copy(alpha = 0.78f)
    } else {
        preset.secondaryColor
    }
    val badge = festival.badgeText.ifBlank { preset.defaultBadge }
    return ResolvedFestivalStyle(
        preset = preset,
        primaryColor = primary,
        secondaryColor = secondary,
        badgeText = badge
    )
}

@Composable
fun FestivalCarouselSection(
    festivals: List<FestivalConfig>,
    modifier: Modifier = Modifier
) {
    if (festivals.isEmpty()) return

    if (festivals.size == 1) {
        FestivalBannerCard(
            festival = festivals.first(),
            pageIndex = 0,
            totalPages = 1,
            modifier = modifier.fillMaxWidth()
        )
        return
    }

    val pagerState = rememberPagerState(pageCount = { festivals.size })
    val coroutineScope = rememberCoroutineScope()
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(festivals.size, isDragged) {
        if (festivals.size > 1 && !isDragged) {
            while (true) {
                delay(5000L)
                val nextPage = (pagerState.currentPage + 1) % festivals.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("festival_carousel_pager")
        ) { page ->
            FestivalBannerCard(
                festival = festivals[page],
                pageIndex = page,
                totalPages = festivals.size,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("festival_carousel_dots"),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(festivals.size) { index ->
                val isSelected = pagerState.currentPage == index
                val style = resolveFestivalStyle(festivals[index])
                val dotWidth by animateDpAsState(
                    targetValue = if (isSelected) 22.dp else 7.dp,
                    label = "festivalDotWidth"
                )
                val dotColor by animateColorAsState(
                    targetValue = if (isSelected) style.primaryColor else MaterialTheme.colorScheme.outlineVariant,
                    label = "festivalDotColor"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(7.dp)
                        .width(dotWidth)
                        .clip(CircleShape)
                        .background(dotColor)
                        .clickable {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        }
                )
            }
        }
    }
}

@Composable
fun FestivalBannerCard(
    festival: FestivalConfig,
    pageIndex: Int,
    totalPages: Int,
    modifier: Modifier = Modifier
) {
    val resolved = remember(festival) { resolveFestivalStyle(festival) }
    val festColor = resolved.primaryColor
    val scheduleText = remember(festival.startDate, festival.endDate) { festival.scheduleSummaryLabel() }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, festColor.copy(alpha = 0.45f)),
        modifier = modifier.testTag("festival_banner_card_$pageIndex")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            festColor.copy(alpha = 0.14f),
                            resolved.secondaryColor.copy(alpha = 0.06f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = festColor
                    ) {
                        Text(
                            text = resolved.badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (festival.festivalName.isNotBlank()) {
                            Text(
                                text = festival.festivalName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = festColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (totalPages > 1) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = festColor.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "${pageIndex + 1}/$totalPages",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = festColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                if (festival.greetingMessage.isNotBlank()) {
                    Text(
                        text = festival.greetingMessage,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (scheduleText != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = "Schedule window",
                            tint = festColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = scheduleText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val shouldShowVisual =
                    festival.bannerImageUrl.isNotBlank() || resolved.preset != FestivalPreset.DEFAULT
                if (shouldShowVisual) {
                    FestivalBannerArtwork(
                        festival = festival,
                        resolved = resolved,
                        pageIndex = pageIndex,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FestivalBannerArtwork(
    festival: FestivalConfig,
    resolved: ResolvedFestivalStyle,
    pageIndex: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val resolvedModel = remember(festival.bannerImageUrl) {
        ImageModelHelper.resolveImageModel(festival.bannerImageUrl)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, resolved.primaryColor.copy(alpha = 0.3f)),
        modifier = modifier.testTag("festival_banner_image_$pageIndex")
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (resolvedModel != null) {
                val request = remember(resolvedModel) {
                    ImageRequest.Builder(context)
                        .data(resolvedModel)
                        .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android) StockManager/1.0")
                        .crossfade(true)
                        .build()
                }
                val painter = rememberAsyncImagePainter(model = request)
                val state = painter.state

                if (state !is AsyncImagePainter.State.Success) {
                    FestivalPresetFallbackArtwork(festival = festival, resolved = resolved)
                }

                Image(
                    painter = painter,
                    contentDescription = festival.festivalName.ifBlank { resolved.badgeText },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                FestivalPresetFallbackArtwork(festival = festival, resolved = resolved)
            }
        }
    }
}

@Composable
private fun FestivalPresetFallbackArtwork(
    festival: FestivalConfig,
    resolved: ResolvedFestivalStyle
) {
    val defaultRes = resolved.preset.defaultBannerRes
    if (defaultRes != null) {
        Image(
            painter = painterResource(id = defaultRes),
            contentDescription = festival.festivalName.ifBlank { resolved.badgeText },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        resolved.primaryColor.copy(alpha = 0.24f),
                        resolved.secondaryColor.copy(alpha = 0.14f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        FestivalPresetCanvasMotif(
            preset = resolved.preset,
            primaryColor = resolved.primaryColor,
            secondaryColor = resolved.secondaryColor,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Text(text = resolved.preset.emoji, fontSize = 28.sp)
            Text(
                text = festival.festivalName.ifBlank { resolved.badgeText },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = resolved.primaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FestivalPresetCanvasMotif(
    preset: FestivalPreset,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        when (preset) {
            FestivalPreset.HOLI -> {
                drawCircle(primaryColor.copy(alpha = 0.22f), radius = h * 0.42f, center = Offset(w * 0.16f, h * 0.25f))
                drawCircle(secondaryColor.copy(alpha = 0.20f), radius = h * 0.36f, center = Offset(w * 0.84f, h * 0.72f))
                drawCircle(Color(0xFF0EA5E9).copy(alpha = 0.18f), radius = h * 0.24f, center = Offset(w * 0.76f, h * 0.20f))
                drawCircle(Color(0xFFF59E0B).copy(alpha = 0.18f), radius = h * 0.20f, center = Offset(w * 0.24f, h * 0.80f))
            }
            FestivalPreset.EID -> {
                val moonCenter = Offset(w * 0.82f, h * 0.35f)
                drawCircle(secondaryColor.copy(alpha = 0.28f), radius = h * 0.22f, center = moonCenter)
                drawCircle(primaryColor.copy(alpha = 0.16f), radius = h * 0.18f, center = Offset(moonCenter.x + h * 0.08f, moonCenter.y - h * 0.04f))
                listOf(Offset(w * 0.15f, h * 0.28f), Offset(w * 0.26f, h * 0.68f), Offset(w * 0.68f, h * 0.22f)).forEach { pt ->
                    drawCircle(secondaryColor.copy(alpha = 0.35f), radius = 4.dp.toPx(), center = pt)
                }
            }
            FestivalPreset.NEW_YEAR -> {
                val origin = Offset(w * 0.82f, h * 0.32f)
                for (i in 0 until 10) {
                    val angle = (i * 36.0) * Math.PI / 180.0
                    val end = Offset(
                        x = origin.x + (cos(angle) * h * 0.35f).toFloat(),
                        y = origin.y + (sin(angle) * h * 0.35f).toFloat()
                    )
                    drawLine(
                        color = secondaryColor.copy(alpha = 0.35f),
                        start = origin,
                        end = end,
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                drawCircle(primaryColor.copy(alpha = 0.18f), radius = h * 0.30f, center = Offset(w * 0.14f, h * 0.78f))
            }
            FestivalPreset.INDEPENDENCE_DAY -> {
                drawArc(
                    color = Color(0xFFEA580C).copy(alpha = 0.24f),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(-w * 0.1f, h * 0.15f),
                    size = androidx.compose.ui.geometry.Size(w * 1.2f, h * 1.2f),
                    style = Stroke(width = 14.dp.toPx())
                )
                drawArc(
                    color = Color(0xFF16A34A).copy(alpha = 0.22f),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(-w * 0.1f, h * 0.35f),
                    size = androidx.compose.ui.geometry.Size(w * 1.2f, h * 1.2f),
                    style = Stroke(width = 14.dp.toPx())
                )
            }
            else -> {
                drawCircle(
                    color = primaryColor.copy(alpha = 0.16f),
                    radius = h * 0.45f,
                    center = Offset(w * 0.12f, h * 0.2f)
                )
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.18f),
                    radius = h * 0.38f,
                    center = Offset(w * 0.88f, h * 0.82f)
                )
            }
        }
    }
}
