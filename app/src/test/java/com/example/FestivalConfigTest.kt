package com.example

import com.example.data.model.DynamicUiConfig
import com.example.data.model.FestivalConfig
import com.example.data.model.FestivalScheduleHelper
import com.example.ui.components.FestivalPreset
import com.example.ui.components.resolveFestivalStyle
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FestivalConfigTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(DynamicUiConfig::class.java)

    @Test
    fun `schedule dates activate only within inclusive start and end range`() {
        val scheduled = FestivalConfig(
            enabled = true,
            festivalName = "Diwali Special",
            greetingMessage = "Happy Diwali!",
            startDate = "2026-11-10",
            endDate = "2026-11-20",
            stylePreset = "diwali"
        )

        assertFalse(scheduled.isActiveOn("2026-11-09"))
        assertTrue(scheduled.isActiveOn("2026-11-10"))
        assertTrue(scheduled.isActiveOn("2026-11-19"))
        assertTrue(scheduled.isActiveOn("2026-11-20"))
        assertFalse(scheduled.isActiveOn("2026-11-21"))
        assertEquals("Nov 10 – Nov 20", scheduled.scheduleSummaryLabel())
    }

    @Test
    fun `disabled or malformed schedule dates do not activate festival`() {
        val disabled = FestivalConfig(
            enabled = false,
            festivalName = "Diwali",
            greetingMessage = "Happy Diwali!",
            startDate = "2026-11-01",
            endDate = "2026-11-30"
        )
        assertFalse(disabled.isActiveOn("2026-11-15"))

        val invalidDate = FestivalConfig(
            enabled = true,
            festivalName = "Diwali",
            greetingMessage = "Happy Diwali!",
            startDate = "2026-02-30"
        )
        assertNull(FestivalScheduleHelper.parseIsoDateKey("2026-02-30"))
        assertFalse(invalidDate.isActiveOn("2026-11-15"))
    }

    @Test
    fun `activeFestivals deduplicates legacy festival and filters expired carousel items`() {
        val diwali = FestivalConfig(
            enabled = true,
            festivalName = "Diwali Festival Special",
            greetingMessage = "Happy Diwali!",
            startDate = "2026-11-01",
            endDate = "2026-11-30",
            stylePreset = "diwali"
        )
        val holiExpired = FestivalConfig(
            enabled = true,
            festivalName = "Holi Offer",
            greetingMessage = "Happy Holi!",
            startDate = "2026-03-01",
            endDate = "2026-03-10",
            stylePreset = "holi"
        )
        val saleActive = FestivalConfig(
            enabled = true,
            festivalName = "Festive Paint Offer",
            greetingMessage = "10% off bulk paints",
            startDate = "2026-11-15",
            endDate = "2026-11-25",
            stylePreset = "sale"
        )

        val config = DynamicUiConfig(
            festival = diwali,
            festivals = listOf(diwali, holiExpired, saleActive)
        )

        val active = config.activeFestivals("2026-11-19")
        assertEquals(2, active.size)
        assertEquals("Diwali Festival Special", active[0].festivalName)
        assertEquals("Festive Paint Offer", active[1].festivalName)
    }

    @Test
    fun `stylePreset resolves defaults and respects custom overrides`() {
        val eidPresetOnly = FestivalConfig(
            enabled = true,
            festivalName = "Eid Mubarak",
            greetingMessage = "Eid wishes",
            stylePreset = "eid"
        )
        val resolvedEid = resolveFestivalStyle(eidPresetOnly)
        assertEquals(FestivalPreset.EID, resolvedEid.preset)
        assertEquals("🌙 EID MUBARAK", resolvedEid.badgeText)
        assertEquals(FestivalPreset.EID.primaryColor, resolvedEid.primaryColor)

        val diwaliCustom = FestivalConfig(
            enabled = true,
            festivalName = "Diwali",
            greetingMessage = "Happy Diwali",
            badgeText = "CUSTOM BADGE",
            accentColorHex = "#FF5722",
            stylePreset = "diwali"
        )
        val resolvedDiwali = resolveFestivalStyle(diwaliCustom)
        assertEquals(FestivalPreset.DIWALI, resolvedDiwali.preset)
        assertEquals("CUSTOM BADGE", resolvedDiwali.badgeText)
    }

    @Test
    fun `moshi parses multi-festival ui_config json and legacy json cleanly`() {
        val multiJson = """
            {
              "theme": { "storeTitle": "S.A.HARDWARE" },
              "festival": {
                "enabled": true,
                "festivalName": "Diwali Special",
                "greetingMessage": "Happy Diwali!",
                "stylePreset": "diwali",
                "startDate": "2026-11-01",
                "endDate": "2026-11-30"
              },
              "festivals": [
                {
                  "enabled": true,
                  "festivalName": "Diwali Special",
                  "greetingMessage": "Happy Diwali!",
                  "stylePreset": "diwali",
                  "startDate": "2026-11-01",
                  "endDate": "2026-11-30"
                },
                {
                  "enabled": true,
                  "festivalName": "Year End Clearance",
                  "greetingMessage": "Clearance deals!",
                  "stylePreset": "new_year",
                  "startDate": "2026-11-10",
                  "endDate": "2026-11-28"
                }
              ]
            }
        """.trimIndent()

        val parsed = adapter.fromJson(multiJson)!!
        val active = parsed.activeFestivals("2026-11-19")
        assertEquals(2, active.size)
        assertEquals(FestivalPreset.DIWALI, FestivalPreset.fromKey(active[0].stylePreset))
        assertEquals(FestivalPreset.NEW_YEAR, FestivalPreset.fromKey(active[1].stylePreset))

        val legacyJson = """
            {
              "festival": {
                "enabled": true,
                "festivalName": "Legacy Festival",
                "greetingMessage": "Warm wishes!"
              }
            }
        """.trimIndent()
        val parsedLegacy = adapter.fromJson(legacyJson)!!
        assertEquals(1, parsedLegacy.activeFestivals("2026-11-19").size)
    }
}
