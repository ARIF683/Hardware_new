package com.example.data.model

import com.squareup.moshi.JsonClass
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@JsonClass(generateAdapter = true)
data class DynamicUiConfig(
    val theme: ThemeConfig = ThemeConfig(),
    val announcement: AnnouncementConfig = AnnouncementConfig(),
    val features: FeaturesConfig = FeaturesConfig(),
    val festival: FestivalConfig = FestivalConfig(),
    val festivals: List<FestivalConfig> = emptyList()
) {
    fun activeFestivals(isoDate: String = FestivalScheduleHelper.todayIsoDate()): List<FestivalConfig> {
        val fromList = festivals.filter { it.isActiveOn(isoDate) }
        if (!festival.isActiveOn(isoDate)) return fromList

        val legacyKey = festival.dedupKey()
        val alreadyPresent = fromList.any { it.dedupKey() == legacyKey }
        return if (alreadyPresent) fromList else listOf(festival) + fromList
    }
}

@JsonClass(generateAdapter = true)
data class ThemeConfig(
    val primaryColor: String = "#2563EB",
    val accentColor: String = "#1D4ED8",
    val storeTitle: String = "S.A.HARDWARE",
    val tagline: String = "Real-time Inventory & Smart Billing"
)

@JsonClass(generateAdapter = true)
data class AnnouncementConfig(
    val enabled: Boolean = false,
    val message: String = "",
    val severity: String = "info", // "info", "warning", "success", "alert"
    val dismissible: Boolean = true
)

@JsonClass(generateAdapter = true)
data class FeaturesConfig(
    val showQuickActions: Boolean = true,
    val showLowStockAlert: Boolean = true,
    val showRecentTransactions: Boolean = true,
    val allowDirectBilling: Boolean = true
)

@JsonClass(generateAdapter = true)
data class FestivalConfig(
    val enabled: Boolean = false,
    val festivalName: String = "",
    val greetingMessage: String = "",
    val badgeText: String = "",
    val accentColorHex: String = "",
    val bannerImageUrl: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val stylePreset: String = "default"
) {
    fun isActiveOn(isoDate: String = FestivalScheduleHelper.todayIsoDate()): Boolean {
        if (!enabled) return false
        if (greetingMessage.isBlank() && festivalName.isBlank()) return false

        val todayKey = FestivalScheduleHelper.parseIsoDateKey(isoDate) ?: return true
        val startKey = if (startDate.isBlank()) null else FestivalScheduleHelper.parseIsoDateKey(startDate)
        val endKey = if (endDate.isBlank()) null else FestivalScheduleHelper.parseIsoDateKey(endDate)

        if (startDate.isNotBlank() && startKey == null) return false
        if (endDate.isNotBlank() && endKey == null) return false

        if (startKey != null && todayKey < startKey) return false
        if (endKey != null && todayKey > endKey) return false
        return true
    }

    fun scheduleSummaryLabel(): String? {
        val startFormatted = FestivalScheduleHelper.formatShortDate(startDate)
        val endFormatted = FestivalScheduleHelper.formatShortDate(endDate)
        return when {
            startFormatted != null && endFormatted != null -> "$startFormatted – $endFormatted"
            endFormatted != null -> "Ends $endFormatted"
            startFormatted != null -> "From $startFormatted"
            else -> null
        }
    }

    internal fun dedupKey(): String =
        "${festivalName.trim().lowercase(Locale.ROOT)}|${greetingMessage.trim().lowercase(Locale.ROOT)}"
}

object FestivalScheduleHelper {
    private val isoRegex = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

    fun todayIsoDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    fun parseIsoDateKey(isoDate: String): Int? {
        val trimmed = isoDate.trim()
        val match = isoRegex.matchEntire(trimmed) ?: return null
        val year = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val day = match.groupValues[3].toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null

        val cal = Calendar.getInstance(Locale.US).apply {
            isLenient = false
            clear()
            set(year, month - 1, day)
        }
        return try {
            cal.time // validates real calendar dates (e.g. rejects Feb 30 / Nov 31)
            year * 10000 + month * 100 + day
        } catch (_: Exception) {
            null
        }
    }

    fun formatShortDate(isoDate: String): String? {
        val key = parseIsoDateKey(isoDate) ?: return null
        val year = key / 10000
        val month = (key / 100) % 100
        val day = key % 100
        val cal = Calendar.getInstance(Locale.US).apply {
            clear()
            set(year, month - 1, day)
        }
        return SimpleDateFormat("MMM d", Locale.US).format(cal.time)
    }
}
