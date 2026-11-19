package com.example.data.model

import com.example.data.pref.FestivalStudioConfig
import java.util.Locale

data class AutoFestivalEntry(
    val festivalName: String,
    val greetingMessage: String,
    val badgeText: String,
    val accentColorHex: String,
    val stylePreset: String,
    val windowsByYear: Map<Int, Pair<String, String>> = emptyMap(),
    val fixedMonthDayWindow: Pair<String, String>? = null // "MM-DD" to "MM-DD"
)

object AutoFestivalCalendar {

    private val entries: List<AutoFestivalEntry> = listOf(
        // Fixed annual festivals
        AutoFestivalEntry(
            festivalName = "New Year Celebration",
            greetingMessage = "🎆 Happy New Year! Wishing you prosperous projects, full stock & strong sales all year long!",
            badgeText = "🎆 HAPPY NEW YEAR",
            accentColorHex = "#4F46E5",
            stylePreset = "new_year",
            fixedMonthDayWindow = "01-01" to "01-02"
        ),
        AutoFestivalEntry(
            festivalName = "Makar Sankranti & Pongal",
            greetingMessage = "🪁 Happy Makar Sankranti & Pongal! Wishing harvest joy and bright beginnings to every customer!",
            badgeText = "🪁 SANKRANTI",
            accentColorHex = "#D97706",
            stylePreset = "sale",
            fixedMonthDayWindow = "01-14" to "01-15"
        ),
        AutoFestivalEntry(
            festivalName = "Republic Day Special",
            greetingMessage = "🇮🇳 Happy Republic Day! Celebrating craftsmanship, strength & building a stronger nation together.",
            badgeText = "🇮🇳 REPUBLIC DAY",
            accentColorHex = "#EA580C",
            stylePreset = "independence_day",
            fixedMonthDayWindow = "01-25" to "01-26"
        ),
        AutoFestivalEntry(
            festivalName = "Independence Day Special",
            greetingMessage = "🇮🇳 Happy Independence Day! Saluting the spirit of building & progress across every home and site.",
            badgeText = "🇮🇳 INDEPENDENCE DAY",
            accentColorHex = "#EA580C",
            stylePreset = "independence_day",
            fixedMonthDayWindow = "08-14" to "08-15"
        ),
        AutoFestivalEntry(
            festivalName = "Gandhi Jayanti",
            greetingMessage = "🇮🇳 Remembering Mahatma Gandhi on Gandhi Jayanti — wishing peace, honesty & goodwill.",
            badgeText = "🇮🇳 GANDHI JAYANTI",
            accentColorHex = "#16A34A",
            stylePreset = "independence_day",
            fixedMonthDayWindow = "10-02" to "10-02"
        ),
        AutoFestivalEntry(
            festivalName = "Christmas Celebration",
            greetingMessage = "🎄 Merry Christmas! Wishing warmth, joy, and bright celebrations to your family & customers.",
            badgeText = "🎄 MERRY CHRISTMAS",
            accentColorHex = "#15803D",
            stylePreset = "christmas",
            fixedMonthDayWindow = "12-23" to "12-26"
        ),
        AutoFestivalEntry(
            festivalName = "Year-End Celebration",
            greetingMessage = "🎆 Year-End Greetings! Thank you for building with us all year long.",
            badgeText = "🎆 YEAR END",
            accentColorHex = "#4F46E5",
            stylePreset = "new_year",
            fixedMonthDayWindow = "12-31" to "12-31"
        ),

        // Major lunar / panchang Indian festivals (2026-2035)
        AutoFestivalEntry(
            festivalName = "Holi Festival of Colors",
            greetingMessage = "🎨 Happy Holi! May your paints, projects, and life shine with vibrant colors of joy!",
            badgeText = "🎨 HAPPY HOLI",
            accentColorHex = "#DB2777",
            stylePreset = "holi",
            windowsByYear = mapOf(
                2026 to ("2026-03-02" to "2026-03-05"),
                2027 to ("2027-03-20" to "2027-03-23"),
                2028 to ("2028-03-09" to "2028-03-12"),
                2029 to ("2029-02-26" to "2029-03-01"),
                2030 to ("2030-03-18" to "2030-03-21"),
                2031 to ("2031-03-07" to "2031-03-10"),
                2032 to ("2032-03-25" to "2032-03-28"),
                2033 to ("2033-03-14" to "2033-03-17"),
                2034 to ("2034-03-04" to "2034-03-07"),
                2035 to ("2035-03-22" to "2035-03-25")
            )
        ),
        AutoFestivalEntry(
            festivalName = "Eid-ul-Fitr Celebration",
            greetingMessage = "🌙 Eid Mubarak! May this blessed Eid bring peace, prosperity & togetherness to every home.",
            badgeText = "🌙 EID MUBARAK",
            accentColorHex = "#059669",
            stylePreset = "eid",
            windowsByYear = mapOf(
                2026 to ("2026-03-19" to "2026-03-22"),
                2027 to ("2027-03-08" to "2027-03-11"),
                2028 to ("2028-02-25" to "2028-02-28"),
                2029 to ("2029-02-13" to "2029-02-16"),
                2030 to ("2030-02-03" to "2030-02-06"),
                2031 to ("2031-01-23" to "2031-01-26"),
                2032 to ("2032-01-12" to "2032-01-15"),
                2033 to ("2033-12-22" to "2033-12-24"),
                2034 to ("2034-12-11" to "2034-12-14"),
                2035 to ("2035-11-30" to "2035-12-03")
            )
        ),
        AutoFestivalEntry(
            festivalName = "Eid-ul-Adha Mubarak",
            greetingMessage = "🌙 Eid-ul-Adha Mubarak! Wishing harmony, generosity & blessings to all families.",
            badgeText = "🌙 EID MUBARAK",
            accentColorHex = "#059669",
            stylePreset = "eid",
            windowsByYear = mapOf(
                2026 to ("2026-05-26" to "2026-05-29"),
                2027 to ("2027-05-15" to "2027-05-18"),
                2028 to ("2028-05-04" to "2028-05-07"),
                2029 to ("2029-04-23" to "2029-04-26"),
                2030 to ("2030-04-12" to "2030-04-15"),
                2031 to ("2031-04-01" to "2031-04-04"),
                2032 to ("2032-03-21" to "2032-03-24"),
                2033 to ("2033-03-10" to "2033-03-13"),
                2034 to ("2034-02-28" to "2034-03-03"),
                2035 to ("2035-02-17" to "2035-02-20")
            )
        ),
        AutoFestivalEntry(
            festivalName = "Ganesh Chaturthi",
            greetingMessage = "✨ Ganpati Bappa Morya! Wishing auspicious beginnings and steady growth to every project.",
            badgeText = "✨ GANESH CHATURTHI",
            accentColorHex = "#D97706",
            stylePreset = "diwali",
            windowsByYear = mapOf(
                2026 to ("2026-09-13" to "2026-09-15"),
                2027 to ("2027-09-03" to "2027-09-05"),
                2028 to ("2028-08-22" to "2028-08-24"),
                2029 to ("2029-09-10" to "2029-09-12"),
                2030 to ("2030-08-31" to "2030-09-02"),
                2031 to ("2031-08-20" to "2031-08-22"),
                2032 to ("2032-09-07" to "2032-09-09"),
                2033 to ("2033-08-27" to "2033-08-29"),
                2034 to ("2034-09-15" to "2034-09-17"),
                2035 to ("2035-09-04" to "2035-09-06")
            )
        ),
        AutoFestivalEntry(
            festivalName = "Dussehra & Navratri",
            greetingMessage = "✨ Happy Dussehra! Wishing victory, prosperity & bright new milestones for your store.",
            badgeText = "✨ HAPPY DUSSEHRA",
            accentColorHex = "#EA580C",
            stylePreset = "diwali",
            windowsByYear = mapOf(
                2026 to ("2026-10-19" to "2026-10-21"),
                2027 to ("2027-10-08" to "2027-10-10"),
                2028 to ("2028-09-26" to "2028-09-28"),
                2029 to ("2029-10-15" to "2029-10-17"),
                2030 to ("2030-10-04" to "2030-10-06"),
                2031 to ("2031-09-24" to "2031-09-26"),
                2032 to ("2032-10-12" to "2032-10-14"),
                2033 to ("2033-10-01" to "2033-10-03"),
                2034 to ("2034-10-20" to "2034-10-22"),
                2035 to ("2035-10-09" to "2035-10-11")
            )
        ),
        AutoFestivalEntry(
            festivalName = "Diwali Festival of Lights",
            greetingMessage = "🪔 Happy Diwali! May your business, home & projects shine bright with prosperity and abundance!",
            badgeText = "🪔 DIWALI FEST",
            accentColorHex = "#D97706",
            stylePreset = "diwali",
            windowsByYear = mapOf(
                2026 to ("2026-11-05" to "2026-11-11"),
                2027 to ("2027-10-26" to "2027-10-31"),
                2028 to ("2028-10-14" to "2028-10-19"),
                2029 to ("2029-11-02" to "2029-11-07"),
                2030 to ("2030-10-23" to "2030-10-28"),
                2031 to ("2031-11-11" to "2031-11-16"),
                2032 to ("2032-10-30" to "2032-11-04"),
                2033 to ("2033-10-19" to "2033-10-24"),
                2034 to ("2034-11-07" to "2034-11-12"),
                2035 to ("2035-10-27" to "2035-11-01")
            )
        )
    )

    private fun AutoFestivalEntry.windowForYear(year: Int): Pair<String, String>? {
        fixedMonthDayWindow?.let { (startMd, endMd) ->
            return "$year-$startMd" to "$year-$endMd"
        }
        return windowsByYear[year]
    }

    private fun AutoFestivalEntry.toFestivalConfig(window: Pair<String, String>): FestivalConfig =
        FestivalConfig(
            enabled = true,
            festivalName = festivalName,
            greetingMessage = greetingMessage,
            badgeText = badgeText,
            accentColorHex = accentColorHex,
            bannerImageUrl = "",
            startDate = window.first,
            endDate = window.second,
            stylePreset = stylePreset
        )

    fun activeFestivalsOn(isoDate: String = FestivalScheduleHelper.todayIsoDate()): List<FestivalConfig> {
        val dateKey = FestivalScheduleHelper.parseIsoDateKey(isoDate) ?: return emptyList()
        val year = dateKey / 10000
        return entries.mapNotNull { entry ->
            val window = entry.windowForYear(year) ?: return@mapNotNull null
            val cfg = entry.toFestivalConfig(window)
            if (cfg.isActiveOn(isoDate)) cfg else null
        }
    }

    fun nextUpcomingFestival(isoDate: String = FestivalScheduleHelper.todayIsoDate()): FestivalConfig? {
        val todayKey = FestivalScheduleHelper.parseIsoDateKey(isoDate) ?: return null
        val year = todayKey / 10000
        val candidates = mutableListOf<Pair<Int, FestivalConfig>>()
        for (y in year..(year + 1)) {
            for (entry in entries) {
                val window = entry.windowForYear(y) ?: continue
                val startKey = FestivalScheduleHelper.parseIsoDateKey(window.first) ?: continue
                val endKey = FestivalScheduleHelper.parseIsoDateKey(window.second) ?: continue
                if (endKey >= todayKey) {
                    candidates += startKey to entry.toFestivalConfig(window)
                }
            }
        }
        return candidates.minByOrNull { it.first }?.second
    }

    fun resolveEffectiveFestivals(
        studioConfig: FestivalStudioConfig,
        remoteUiConfig: DynamicUiConfig,
        isoDate: String = FestivalScheduleHelper.todayIsoDate()
    ): List<FestivalConfig> {
        if (studioConfig.overrideMode == FestivalStudioConfig.MODE_OFF) {
            return emptyList()
        }

        val combined = mutableListOf<FestivalConfig>()
        val seenKeys = mutableSetOf<String>()

        fun addUnique(cfg: FestivalConfig) {
            val presetKey = cfg.stylePreset.trim().lowercase(Locale.ROOT)
            val nameKey = cfg.festivalName.trim().lowercase(Locale.ROOT)
            val key = if (presetKey.isNotBlank() && presetKey != "default") {
                "preset:$presetKey:$nameKey"
            } else {
                "name:$nameKey"
            }
            if (seenKeys.add(key)) {
                combined += cfg
            }
        }

        // 1. Supabase Admin Custom Festival (highest priority)
        if (studioConfig.customFestival.isActiveOn(isoDate)) {
            addUnique(studioConfig.customFestival)
        }

        if (studioConfig.overrideMode != FestivalStudioConfig.MODE_CUSTOM) {
            // 2. GitHub DynamicUiConfig festivals (if configured)
            remoteUiConfig.activeFestivals(isoDate).forEach { addUnique(it) }

            // 3. Built-in Automatic Indian Festival Calendar
            if (studioConfig.autoCalendarEnabled) {
                activeFestivalsOn(isoDate).forEach { autoCfg ->
                    val autoPreset = autoCfg.stylePreset.trim().lowercase(Locale.ROOT)
                    val alreadyCoveredByPreset = combined.any {
                        it.stylePreset.trim().lowercase(Locale.ROOT) == autoPreset && autoPreset != "default"
                    }
                    if (!alreadyCoveredByPreset) {
                        addUnique(autoCfg)
                    }
                }
            }
        }

        return combined
    }

    fun resolveEffectiveThemeHex(
        studioConfig: FestivalStudioConfig,
        activeFestivals: List<FestivalConfig>,
        manualThemeHex: String
    ): String {
        if (!studioConfig.applyFestivalThemeToApp ||
            studioConfig.overrideMode == FestivalStudioConfig.MODE_OFF ||
            activeFestivals.isEmpty()
        ) {
            return manualThemeHex
        }

        val primary = activeFestivals.first()
        val explicitHex = primary.accentColorHex.trim()
        if (isValidHexColor(explicitHex)) return explicitHex

        return when (primary.stylePreset.trim().lowercase(Locale.ROOT)) {
            "diwali", "deepavali" -> "#D97706"
            "eid", "eid_mubarak", "ramadan" -> "#059669"
            "holi" -> "#DB2777"
            "christmas", "xmas" -> "#15803D"
            "new_year", "newyear" -> "#4F46E5"
            "independence_day", "republic_day", "patriot" -> "#EA580C"
            "sale", "offer", "clearance" -> "#D97757"
            else -> "#D97757"
        }
    }

    private fun isValidHexColor(hex: String): Boolean {
        return Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$").matches(hex)
    }
}
