package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.AuthManager
import com.example.ui.StockViewModel
import com.example.ui.screens.AuthGateScreen
import com.example.ui.theme.StockManagerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthDeepLinkAndSignupTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application

    @Before
    fun resetPrefs() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("stock_manager_auth", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `isAuthDeepLink matches hardwarestock auth-callback only`() {
        assertTrue(
            AuthManager.isAuthDeepLink(
                Uri.parse("hardwarestock://auth-callback#access_token=abc&type=signup")
            )
        )
        assertTrue(
            AuthManager.isAuthDeepLink(
                Uri.parse("hardwarestock://auth-callback?token_hash=xyz&type=signup")
            )
        )
        assertFalse(AuthManager.isAuthDeepLink(Uri.parse("https://example.com/auth-callback")))
        assertFalse(AuthManager.isAuthDeepLink(Uri.parse("content://media/external/file/123")))
        assertFalse(AuthManager.isAuthDeepLink(null))
    }

    @Test
    fun `parseEncodedParams decodes fragment and query values`() {
        val parsed = AuthManager.parseEncodedParams(
            "access_token=tok_123&error_description=Email+link+is+invalid&type=signup"
        )
        assertEquals("tok_123", parsed["access_token"])
        assertEquals("Email link is invalid", parsed["error_description"])
        assertEquals("signup", parsed["type"])
    }

    @Test
    fun `handleAuthDeepLink surfaces error description from redirect url`() {
        val auth = AuthManager(app)
        val uri = Uri.parse(
            "hardwarestock://auth-callback#error=access_denied&error_description=Email+link+is+invalid+or+has+expired"
        )
        auth.handleAuthDeepLink(uri)
        assertEquals("Email link is invalid or has expired", auth.authDeepLinkError.value)
        assertFalse(auth.isAuthed.value)
    }

    @Test
    fun `auth gate screen shows sign in and create account without otp fields`() {
        val vm = StockViewModel(app)
        composeRule.setContent {
            StockManagerTheme(darkTheme = false) {
                AuthGateScreen(viewModel = vm)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Stock").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_tab_signin").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_tab_signup").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_email_input").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_password_input").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_submit_button").performScrollTo().assertIsDisplayed()

        // Switch to Create Account tab -> stays on Email + Password without OTP box
        composeRule.onNodeWithTag("auth_tab_signup").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Create Password (min 8 chars)").assertIsDisplayed()
        composeRule.onNodeWithText("6-Digit Email OTP Code").assertDoesNotExist()
    }

    @Test
    fun `isEmailConfirmed requires non-null timestamp and friendlySupabaseError explains unverified email`() {
        val unconfirmed = org.json.JSONObject()
            .put("email", "test@store.com")
            .put("email_confirmed_at", org.json.JSONObject.NULL)
        val confirmed = org.json.JSONObject()
            .put("email", "test@store.com")
            .put("email_confirmed_at", "2026-11-20T10:00:00Z")

        assertFalse(AuthManager.isEmailConfirmed(unconfirmed))
        assertFalse(AuthManager.isEmailConfirmed(null))
        assertTrue(AuthManager.isEmailConfirmed(confirmed))

        val err = AuthManager.friendlySupabaseError("""{"msg":"Email not confirmed"}""", 400)
        assertTrue(err.contains("not verified", ignoreCase = true))
    }

    @Test
    fun `pending verification shows otp input and blocks invalid or empty otp`() {
        app.getSharedPreferences("stock_manager_auth", Context.MODE_PRIVATE)
            .edit()
            .putString("pending_signup_email", "newuser@store.com")
            .commit()

        val vm = StockViewModel(app)
        composeRule.setContent {
            StockManagerTheme(darkTheme = false) {
                AuthGateScreen(viewModel = vm)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("auth_otp_input").performScrollTo().assertIsDisplayed()
        for (i in 0 until 6) {
            composeRule.onNodeWithTag("auth_otp_input_box_$i", useUnmergedTree = true).assertIsDisplayed()
        }
        composeRule.onNodeWithTag("auth_otp_input_box_6", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("auth_verify_otp_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("auth_resend_otp_button").performScrollTo().assertIsDisplayed()

        // Submit without OTP -> blocked
        composeRule.onNodeWithTag("auth_verify_otp_button").performClick()
        composeRule.waitForIdle()
        assertFalse(vm.isAuthed.value)

        // Direct short OTP call -> rejected
        var verified = true
        var message: String? = null
        vm.authManager.verifySignupOtp("newuser@store.com", "123") { ok, msg ->
            verified = ok
            message = msg
        }
        assertFalse(verified)
        assertTrue(message?.contains("OTP", ignoreCase = true) == true)
        assertFalse(vm.isAuthed.value)
    }
}
