package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StartupSmokeRobolectricTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `main activity launches splash and settles without crashing`() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("splash_screen").assertIsDisplayed()

            repeat(4) {
                composeRule.mainClock.advanceTimeBy(600)
                composeRule.waitForIdle()
            }
        }
    }

    @Test
    fun `authenticated screens render billing pos estimate and gst calculator`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("stock_manager_auth", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_authed", true)
            .commit()

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitForIdle()
            repeat(5) {
                composeRule.mainClock.advanceTimeBy(600)
                composeRule.waitForIdle()
            }

            composeRule.onNodeWithTag("nav_tab_billing").assertIsDisplayed().performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("billing_tab_estimates").assertIsDisplayed().performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("billing_tab_gst_bills").assertIsDisplayed().performClick()
            composeRule.waitForIdle()
        }
    }
}
