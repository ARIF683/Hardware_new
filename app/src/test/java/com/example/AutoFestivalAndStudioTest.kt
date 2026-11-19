package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AutoFestivalCalendar
import com.example.data.model.DynamicUiConfig
import com.example.data.model.FestivalConfig
import com.example.data.pref.FestivalPreferenceManager
import com.example.data.pref.FestivalStudioCodec
import com.example.data.pref.FestivalStudioConfig
import com.example.data.remote.RemoteStoreProfile
import com.example.data.remote.StoreProfileSyncCodec
import com.example.ui.StockViewModel
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.StockManagerTheme
import org.junit.Assert.assertEquals
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
            remoteUiConfig = DynamicUiConfig(),
            isoDate = "2026-11-08"
        )
        // Admin custom Diwali covers preset "diwali", so auto-calendar Diwali shouldn't duplicate it
        assertEquals(1, resolvedAuto.size)
        assertEquals("S.A. Hardware Diwali Dhamaka", resolvedAuto[0].festivalName)

        // MODE_OFF hides everything
        val offStudio = autoStudio.copy(overrideMode = FestivalStudioConfig.MODE_OFF)
        val resolvedOff = AutoFestivalCalendar.resolveEffectiveFestivals(
            studioConfig = offStudio,
            remoteUiConfig = DynamicUiConfig(),
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
    fun `festival studio config syncs via StoreProfileSyncCodec and preserves during partial merges`() {
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
        assertEquals(studio, decodedStudio)

        // Unrelated bank update must not wipe festivalConfigJson
        val bankOnly = RemoteStoreProfile(bankName = "HDFC Bank", updatedAt = 999999999L)
        val merged = StoreProfileSyncCodec.merge(parsedProfile, bankOnly)
        assertEquals(studioJson, merged.festivalConfigJson)
        assertEquals("HDFC Bank", merged.bankName)

        val app = ApplicationProvider.getApplicationContext<Application>()
        val manager = FestivalPreferenceManager(app)
        manager.applyRemoteJson(merged.festivalConfigJson)
        assertEquals("Eid Grand Sale", manager.currentConfig.value.customFestival.festivalName)
    }

    @Test
    fun `festival studio option in Settings is hidden until Admin PIN is unlocked`() {
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
            composeRule.onAllNodesWithTag("settings_festival_studio_row").fetchSemanticsNodes().size
        )
    }

    @Test
    fun `festival studio option in Settings becomes visible once Admin mode is unlocked`() {
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
            .performScrollToNode(hasTestTag("settings_festival_studio_row"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_festival_studio_row").assertIsDisplayed()
    }
}
