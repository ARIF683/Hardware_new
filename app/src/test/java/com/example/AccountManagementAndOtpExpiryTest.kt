package com.example

import android.app.Application
import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.AuthManager
import com.example.ui.StockViewModel
import com.example.ui.components.ChangeEmailDialog
import com.example.ui.components.DeleteAccountOtpDialog
import com.example.ui.components.OtpCodeBoxesInput
import com.example.ui.screens.AuthGateScreen
import com.example.ui.screens.SettingsScreen
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
class AccountManagementAndOtpExpiryTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application

    @Before
    fun resetState() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("stock_manager_auth", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `otp expiry constants and formatting represent 5 minutes cleanly`() {
        assertEquals(300, AuthManager.OTP_EXPIRY_SECONDS)
        assertEquals(300_000L, AuthManager.OTP_EXPIRY_MS)
        assertEquals("05:00", AuthManager.formatOtpCountdown(300))
        assertEquals("01:05", AuthManager.formatOtpCountdown(65))
        assertEquals("00:00", AuthManager.formatOtpCountdown(-1))
    }

    @Test
    fun `expired otp is rejected across signup reset email-change and delete flows`() {
        app.getSharedPreferences("stock_manager_auth", Context.MODE_PRIVATE)
            .edit()
            .putString("current_user_email", "owner@store.com")
            .commit()

        val auth = AuthManager(app)
        auth.setOtpExpiresAtForTest(System.currentTimeMillis() - 10_000L)

        var okSignup = true
        var msgSignup: String? = null
        auth.verifySignupOtp("owner@store.com", "123456") { ok, msg ->
            okSignup = ok
            msgSignup = msg
        }
        assertFalse(okSignup)
        assertTrue(msgSignup?.contains("expired", ignoreCase = true) == true)

        var okReset = true
        var msgReset: String? = null
        auth.verifyPasswordResetAndUpdate("owner@store.com", "123456", "newpass123") { ok, msg ->
            okReset = ok
            msgReset = msg
        }
        assertFalse(okReset)
        assertTrue(msgReset?.contains("expired", ignoreCase = true) == true)

        var okChange = true
        var msgChange: String? = null
        auth.confirmChangeEmailOtp("newowner@store.com", "123456") { ok, msg ->
            okChange = ok
            msgChange = msg
        }
        assertFalse(okChange)
        assertTrue(msgChange?.contains("expired", ignoreCase = true) == true)

        var okDelete = true
        var msgDelete: String? = null
        auth.confirmDeleteAccountWithOtp("123456") { ok, msg ->
            okDelete = ok
            msgDelete = msg
        }
        assertFalse(okDelete)
        assertTrue(msgDelete?.contains("expired", ignoreCase = true) == true)
    }

    @Test
    fun `forgot password flow renders email input then otp timer and new password fields`() {
        val vm = StockViewModel(app)
        composeRule.setContent {
            StockManagerTheme(darkTheme = false) {
                AuthGateScreen(viewModel = vm)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("auth_forgot_password_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("auth_forgot_email_input").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_send_reset_otp_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("auth_back_to_signin_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `settings screen shows change email row and delete account button`() {
        app.getSharedPreferences("stock_manager_auth", Context.MODE_PRIVATE)
            .edit()
            .putString("current_user_email", "staff@store.com")
            .commit()

        val vm = StockViewModel(app)
        composeRule.setContent {
            StockManagerTheme(darkTheme = false) {
                SettingsScreen(viewModel = vm)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_lazy_column").performScrollToIndex(4)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_change_email_row").assertIsDisplayed()

        composeRule.onNodeWithTag("settings_lazy_column").performScrollToIndex(6)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_delete_account_button").assertIsDisplayed()
    }

    @Test
    fun `change email and delete account dialogs display inputs and action buttons`() {
        app.getSharedPreferences("stock_manager_auth", Context.MODE_PRIVATE)
            .edit()
            .putString("current_user_email", "user@store.com")
            .commit()

        val vm = StockViewModel(app)
        composeRule.setContent {
            StockManagerTheme(darkTheme = false) {
                ChangeEmailDialog(viewModel = vm, onDismiss = {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("change_email_new_input").assertIsDisplayed()
        composeRule.onNodeWithTag("change_email_send_otp_button").assertIsDisplayed()
        composeRule.onNodeWithTag("change_email_new_input").performTextInput("updated@store.com")
        composeRule.waitForIdle()
    }

    @Test
    fun `delete account otp dialog renders deletion warning and send otp button`() {
        app.getSharedPreferences("stock_manager_auth", Context.MODE_PRIVATE)
            .edit()
            .putString("current_user_email", "user@store.com")
            .commit()

        val vm = StockViewModel(app)
        composeRule.setContent {
            StockManagerTheme(darkTheme = false) {
                DeleteAccountOtpDialog(viewModel = vm, onDismiss = {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("delete_account_send_otp_button").assertIsDisplayed()
    }

    @Test
    fun `account deletion shows confirmation banner on auth gate screen after logout`() {
        app.getSharedPreferences("stock_manager_auth", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_authed", true)
            .putString("current_user_email", "deleted@store.com")
            .commit()

        val vm = StockViewModel(app)
        composeRule.setContent {
            val authed by vm.isAuthed.collectAsState()
            StockManagerTheme(darkTheme = false) {
                if (!authed) {
                    AuthGateScreen(viewModel = vm)
                }
            }
        }

        vm.authManager.setOtpExpiresAtForTest(System.currentTimeMillis() + 60_000L)
        var callbackDone = false
        var callbackSuccess = false
        var callbackMessage: String? = null
        vm.authManager.confirmDeleteAccountWithOtp("123456") { ok, msg ->
            callbackSuccess = ok
            callbackMessage = msg
            callbackDone = true
        }

        val deadline = System.currentTimeMillis() + 5_000L
        while (!callbackDone && System.currentTimeMillis() < deadline) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        composeRule.waitForIdle()

        assertTrue(callbackSuccess)
        assertTrue(callbackMessage?.contains("deleted", ignoreCase = true) == true)
        assertFalse(vm.isAuthed.value)

        composeRule.onNodeWithTag("auth_success_banner").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Account (deleted@store.com) has been permanently deleted.")
            .assertIsDisplayed()
    }

    @Test
    fun `otp box input renders exactly 6 boxes and limits entry to 6 digits`() {
        var currentOtp = ""
        composeRule.setContent {
            StockManagerTheme(darkTheme = false) {
                var code by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
                OtpCodeBoxesInput(
                    value = code,
                    onValueChange = {
                        code = it
                        currentOtp = it
                    },
                    label = "Verification Code (6-digit)",
                    testTag = "test_otp_input"
                )
            }
        }
        composeRule.waitForIdle()

        for (i in 0 until 6) {
            composeRule.onNodeWithTag("test_otp_input_box_$i", useUnmergedTree = true).assertIsDisplayed()
        }
        composeRule.onNodeWithTag("test_otp_input_box_6", useUnmergedTree = true).assertDoesNotExist()

        composeRule.onNodeWithTag("test_otp_input").performTextInput("12345678")
        composeRule.waitForIdle()
        assertEquals("123456", currentOtp)
    }

    @Test
    fun `non-6-digit otp like 8 digits is rejected across all verification methods`() {
        app.getSharedPreferences("stock_manager_auth", Context.MODE_PRIVATE)
            .edit()
            .putString("current_user_email", "owner@store.com")
            .commit()

        val auth = AuthManager(app)
        auth.setOtpExpiresAtForTest(System.currentTimeMillis() + 60_000L)

        var okSignup = true
        auth.verifySignupOtp("owner@store.com", "12345678") { ok, _ -> okSignup = ok }
        assertFalse(okSignup)

        var okReset = true
        auth.verifyPasswordResetAndUpdate("owner@store.com", "12345678", "newpass123") { ok, _ -> okReset = ok }
        assertFalse(okReset)

        var okChange = true
        auth.confirmChangeEmailOtp("new@store.com", "12345678") { ok, _ -> okChange = ok }
        assertFalse(okChange)

        var okDelete = true
        auth.confirmDeleteAccountWithOtp("12345678") { ok, _ -> okDelete = ok }
        assertFalse(okDelete)
    }
}
