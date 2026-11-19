package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AnnouncementConfig
import com.example.data.model.AutoFestivalCalendar
import com.example.data.model.FestivalConfig
import com.example.data.pref.FestivalPreferenceManager
import com.example.data.pref.FestivalStudioCodec
import com.example.data.pref.FestivalStudioConfig
import com.example.data.remote.RemoteStoreProfile
import com.example.data.remote.StoreProfileSyncCodec
import com.example.ui.StockViewModel
import com.example.ui.components.FestivalCarouselSection
import com.example.ui.components.FestivalHeaderPill
import com.example.ui.screens.AnnouncementStudioDialog
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.StockManagerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutoFestivalAndStudioTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `auto festival calendar activates fixed and lunar Indian festivals automatically`() {
        val diwali = AutoFestivalCalendar.activeFestivalsOn("2026-11-08")
        assertTrue(diwali.any { it.stylePreset == "diwali" && it.festivalName.contains("Diwali", ignoreCase = true) })

        val christmas = AutoFestivalCalendar.activeFestivalsOn("2026-12-25")
        assertTrue(christmas.any { it.stylePreset == "christmas" })

        val independence = AutoFestivalCalendar.activeFestivalsOn("2026-08-15")
        assertTrue(independence.any { it.stylePreset == "independence_day" })

        val holi2027 = AutoFestivalCalendar.activeFestivalsOn("2027-03-21")
        assertTrue(holi2027.any { it.stylePreset == "holi" })

        val eid2026 = AutoFestivalCalendar.activeFestivalsOn("2026-03-20")
        assertTrue(eid2026.any { it.stylePreset == "eid" })
    }

    @Test
    fun `effective festival resolver respects OFF CUSTOM and AUTO modes plus deduplication`() {
        val customDiwali = FestivalConfig(
            enabled = true,
            festivalName = "S.A. Hardware Diwali Dhamaka",
            greetingMessage = "Flat 10% off paints!",
            stylePreset = "diwali",
            accentColorHex = "#D97706"
        )

        val autoStudio = FestivalStudioConfig(
            autoCalendarEnabled = true,
            applyFestivalThemeToApp = true,
            overrideMode = FestivalStudioConfig.MODE_AUTO,
            customFestival = customDiwali
        )
        val resolvedAuto = AutoFestivalCalendar.resolveEffectiveFestivals(
            studioConfig = autoStudio,
            isoDate = "2026-11-08"
        )
        // Admin custom Diwali covers preset "diwali", so auto-calendar Diwali shouldn't duplicate it
        assertEquals(1, resolvedAuto.size)
        assertEquals("S.A. Hardware Diwali Dhamaka", resolvedAuto[0].festivalName)

        // MODE_OFF hides everything
        val offStudio = autoStudio.copy(overrideMode = FestivalStudioConfig.MODE_OFF)
        val resolvedOff = AutoFestivalCalendar.resolveEffectiveFestivals(
            studioConfig = offStudio,
            isoDate = "2026-11-08"
        )
        assertEquals(0, resolvedOff.size)
        assertEquals(
            "#EA580C",
            AutoFestivalCalendar.resolveEffectiveThemeHex(offStudio, resolvedAuto, "#EA580C")
        )

        // App-wide theme override returns active festival hex when enabled
        assertEquals(
            "#D97706",
            AutoFestivalCalendar.resolveEffectiveThemeHex(autoStudio, resolvedAuto, "#EA580C")
        )

        // Manual theme kept when applyFestivalThemeToApp is false
        val noAppTint = autoStudio.copy(applyFestivalThemeToApp = false)
        assertEquals(
            "#EA580C",
            AutoFestivalCalendar.resolveEffectiveThemeHex(noAppTint, resolvedAuto, "#EA580C")
        )
    }

    @Test
    fun `announcement studio config including notice banner syncs via StoreProfileSyncCodec and preserves on merge`() {
        val studio = FestivalStudioConfig(
            autoCalendarEnabled = true,
            applyFestivalThemeToApp = false,
            overrideMode = FestivalStudioConfig.MODE_CUSTOM,
            customFestival = FestivalConfig(
                enabled = true,
                festivalName = "Eid Grand Sale",
                greetingMessage = "Eid Mubarak to all customers!",
                badgeText = "🌙 EID SPECIAL",
                accentColorHex = "#059669",
                bannerImageUrl = "https://cdn.example.com/eid.jpg",
                startDate = "2026-03-19",
                endDate = "2026-03-23",
                stylePreset = "eid"
            ),
            announcement = AnnouncementConfig(
                enabled = true,
                message = "Shop closed tomorrow for stock audit",
                severity = "warning",
                dismissible = false
            ),
            updatedAt = 987654321L
        )
        val studioJson = FestivalStudioCodec.toJsonString(studio)

        val remote = RemoteStoreProfile(
            storeName = "S.A.HARDWARE",
            festivalConfigJson = studioJson,
            updatedAt = 987654321L
        )
        val payload = StoreProfileSyncCodec.toPurchasesPayload(remote, "2026-11-19")
        val parsedProfile = StoreProfileSyncCodec.parseFromLines(payload.get("lines"))
        assertNotNull(parsedProfile)
        assertEquals(studioJson, parsedProfile!!.festivalConfigJson)

        val decodedStudio = FestivalStudioCodec.fromJsonString(parsedProfile.festivalConfigJson)
        assertNotNull(decodedStudio)
        assertEquals(studio.copy(customFestivals = studio.effectiveCustomFestivals()), decodedStudio)
        assertTrue(decodedStudio!!.announcement.enabled)
        assertEquals("Shop closed tomorrow for stock audit", decodedStudio.announcement.message)
        assertEquals("warning", decodedStudio.announcement.severity)
        assertEquals(false, decodedStudio.announcement.dismissible)

        // Unrelated bank update must not wipe festivalConfigJson
        val bankOnly = RemoteStoreProfile(bankName = "HDFC Bank", updatedAt = 999999999L)
        val merged = StoreProfileSyncCodec.merge(parsedProfile, bankOnly)
        assertEquals(studioJson, merged.festivalConfigJson)
        assertEquals("HDFC Bank", merged.bankName)

        val app = ApplicationProvider.getApplicationContext<Application>()
        val manager = FestivalPreferenceManager(app)
        manager.applyRemoteJson(merged.festivalConfigJson)
        assertEquals("Eid Grand Sale", manager.currentConfig.value.customFestival.festivalName)
        assertEquals("Shop closed tomorrow for stock audit", manager.currentConfig.value.announcement.message)
    }

    @Test
    fun `announcement studio option in Settings is hidden until Admin PIN is unlocked`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val authPrefs = app.getSharedPreferences("stock_manager_auth", android.content.Context.MODE_PRIVATE)
        authPrefs.edit().putBoolean("is_admin", false).commit()
        val lockedVm = StockViewModel(app)

        composeRule.setContent {
            StockManagerTheme(darkTheme = false) {
                SettingsScreen(viewModel = lockedVm)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasTestTag("settings_admin_access_row"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_admin_access_row").assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("settings_announcement_studio_row").fetchSemanticsNodes().size
        )
    }

    @Test
    fun `announcement studio option in Settings becomes visible once Admin mode is unlocked`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val authPrefs = app.getSharedPreferences("stock_manager_auth", android.content.Context.MODE_PRIVATE)
        authPrefs.edit().putBoolean("is_admin", true).commit()
        val unlockedVm = StockViewModel(app)

        composeRule.setContent {
            StockManagerTheme(darkTheme = false) {
                SettingsScreen(viewModel = unlockedVm)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasTestTag("settings_announcement_studio_row"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_announcement_studio_row").assertIsDisplayed()
    }

    @Test
    fun `festival carousel automatically advances to next festival card after 5 seconds`() {
        val festivals = listOf(
            FestivalConfig(
                enabled = true,
                festivalName = "Diwali Dhamaka",
                greetingMessage = "Wishing you a sparkling Diwali!",
                stylePreset = "diwali"
            ),
            FestivalConfig(
                enabled = true,
                festivalName = "Hardware Mega Sale",
                greetingMessage = "Flat 15% off paints and fittings all week!",
                stylePreset = "sale"
            )
        )

        composeRule.setContent {
            StockManagerTheme(darkTheme = false) {
                FestivalCarouselSection(festivals = festivals)
            }
        }

        composeRule.onNodeWithTag("festival_banner_card_0").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(6000L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("festival_banner_card_1").assertIsDisplayed()
    }

    @Test
    fun `festival header pill in Items and Billing shows full message and auto cycles after 5 seconds`() {
        val longSaleMessage = "Mega Monsoon & Diwali Hardware Sale — Extra 10% discount on all bulk pipe and paint orders today!"
        val festivals = listOf(
            FestivalConfig(
                enabled = true,
                festivalName = "Diwali",
                greetingMessage = longSaleMessage,
                stylePreset = "diwali"
            ),
            FestivalConfig(
                enabled = true,
                festivalName = "Clearance",
                greetingMessage = "Weekend Stock Clearance Live Now!",
                stylePreset = "sale"
            )
        )

        composeRule.setContent {
            StockManagerTheme(darkTheme = false) {
                FestivalHeaderPill(festivals = festivals)
            }
        }

        composeRule.onNodeWithTag("festival_header_pill").assertIsDisplayed()
        composeRule.onNodeWithText(longSaleMessage).assertIsDisplayed()
        composeRule.onNodeWithText("1/2").assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(5500L)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Weekend Stock Clearance Live Now!").assertIsDisplayed()
        composeRule.onNodeWithText("2/2").assertIsDisplayed()
    }

    @Test
    fun `multiple custom festivals activate together and hiding auto calendar festivals removes them cleanly`() {
        val card1 = FestivalConfig(
            enabled = true,
            festivalName = "Monsoon Paint Sale",
            greetingMessage = "15% off exterior emulsion!",
            stylePreset = "sale"
        )
        val card2 = FestivalConfig(
            enabled = true,
            festivalName = "Festive Pipe Offer",
            greetingMessage = "Special wholesale rates on PVC fittings!",
            stylePreset = "new_year"
        )

        val multiStudio = FestivalStudioConfig(
            autoCalendarEnabled = true,
            overrideMode = FestivalStudioConfig.MODE_AUTO,
            customFestivals = listOf(card1, card2),
            hiddenAutoFestivalKeys = listOf(AutoFestivalCalendar.autoFestivalKey("Diwali Festival of Lights"))
        )

        // Both custom cards show, while hidden built-in Diwali does NOT appear on 2026-11-08
        val resolved = AutoFestivalCalendar.resolveEffectiveFestivals(multiStudio, isoDate = "2026-11-08")
        assertEquals(2, resolved.size)
        assertTrue(resolved.any { it.festivalName == "Monsoon Paint Sale" })
        assertTrue(resolved.any { it.festivalName == "Festive Pipe Offer" })
        assertFalse(resolved.any { it.festivalName == "Diwali Festival of Lights" })

        // Deleting all custom cards leaves an empty list even across JSON serialization
        val emptyCustomStudio = multiStudio.copy(
            customFestival = FestivalConfig(),
            customFestivals = emptyList(),
            updatedAt = 555555L
        )
        val json = FestivalStudioCodec.toJsonString(emptyCustomStudio)
        val decoded = FestivalStudioCodec.fromJsonString(json)!!
        assertEquals(0, decoded.effectiveCustomFestivals().size)
        assertEquals(1, decoded.hiddenAutoFestivalKeys.size)
        assertEquals(0, AutoFestivalCalendar.resolveEffectiveFestivals(decoded, isoDate = "2026-11-08").size)
    }

    @Test
    fun `announcement timer activates strictly between start and end timestamps and syncs via json`() {
        val start = 1_700_000_000_000L
        val end = 1_700_010_000_000L
        val timedNotice = AnnouncementConfig(
            enabled = true,
            message = "Flash Inventory Audit 2PM - 5PM",
            severity = "warning",
            dismissible = true,
            startAtMillis = start,
            endAtMillis = end
        )

        assertFalse(timedNotice.isActiveAt(start - 1_000L))
        assertTrue(timedNotice.isActiveAt(start))
        assertTrue(timedNotice.isActiveAt(start + 5_000_000L))
        assertTrue(timedNotice.isActiveAt(end))
        assertFalse(timedNotice.isActiveAt(end + 1_000L))

        val studio = FestivalStudioConfig(
            announcement = timedNotice,
            updatedAt = 1_700_000_001_000L
        )
        val roundTrip = FestivalStudioCodec.fromJsonString(FestivalStudioCodec.toJsonString(studio))!!
        assertEquals(start, roundTrip.announcement.startAtMillis)
        assertEquals(end, roundTrip.announcement.endAtMillis)
    }

    @Test
    fun `announcement studio dialog allows adding two custom festival cards and deleting any card`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val authPrefs = app.getSharedPreferences("stock_manager_auth", android.content.Context.MODE_PRIVATE)
        authPrefs.edit().putBoolean("is_admin", true).commit()
        val vm = StockViewModel(app)

        composeRule.setContent {
            StockManagerTheme(darkTheme = false) {
                AnnouncementStudioDialog(viewModel = vm, onDismiss = {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("studio_add_custom_card_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("studio_add_custom_card_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("studio_custom_card_row_0").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("studio_custom_card_row_1").performScrollTo().assertIsDisplayed()

        // Delete first card cleanly leaves only row 0
        composeRule.onNodeWithTag("studio_delete_custom_0").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("studio_custom_card_row_0").performScrollTo().assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("studio_custom_card_row_1").fetchSemanticsNodes().size
        )
    }
}
