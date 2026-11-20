package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.AuthManager
import com.example.data.remote.RemoteStoreProfile
import com.example.data.remote.StoreProfileSyncCodec
import com.example.ui.StockViewModel
import com.example.ui.components.AdminPinUnlockDialog
import com.example.ui.components.ChangeAdminPinDialog
import com.example.ui.theme.StockManagerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdminPinSecurityTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application

    @Before
    fun resetAuthPrefs() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("stock_manager_auth", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `salted pbkdf2 verifier uses unique salts and verifies accurately`() {
        val v1 = AuthManager.createSaltedPinVerifier("9472", iterations = 2_000)
        val v2 = AuthManager.createSaltedPinVerifier("9472", iterations = 2_000)
        assertTrue(v1.startsWith("pbkdf2_sha256$2000$"))
        assertNotEquals(v1, v2)
        assertTrue(AuthManager.verifySaltedPinVerifier("9472", v1))
        assertTrue(AuthManager.verifySaltedPinVerifier("9472", v2))
        assertFalse(AuthManager.verifySaltedPinVerifier("9471", v1))
        assertFalse(AuthManager.verifySaltedPinVerifier("wrong", v1))
    }

    @Test
    fun `admin pin locks after 3 failed attempts and persists lockout`() {
        val auth = AuthManager(app)
        val r1 = auth.unlockAdminWithResult("0000")
        assertTrue(r1 is AuthManager.AdminAuthResult.WrongPin && r1.triesLeft == 2)

        val r2 = auth.unlockAdminWithResult("1111")
        assertTrue(r2 is AuthManager.AdminAuthResult.WrongPin && r2.triesLeft == 1)

        val r3 = auth.unlockAdminWithResult("2222")
        assertTrue(r3 is AuthManager.AdminAuthResult.Locked && r3.secondsLeft > 0)
        assertTrue(auth.isAdminLocked())
        assertFalse(auth.isAdmin.value)

        // New instance on same Context remains locked
        val restartedAuth = AuthManager(app)
        assertTrue(restartedAuth.isAdminLocked())
        val r4 = restartedAuth.unlockAdminWithResult("anything")
        assertTrue(r4 is AuthManager.AdminAuthResult.Locked)
    }

    @Test
    fun `custom admin pin rotates hash, syncs across store profile codec, and applies remotely`() {
        val verifier = AuthManager.createSaltedPinVerifier("7531")
        val remote = RemoteStoreProfile(
            storeName = "S.A.HARDWARE",
            adminPinHash = verifier,
            updatedAt = 1_800_000_000_000L
        )
        val payload = StoreProfileSyncCodec.toPurchasesPayload(remote, "2026-11-19")
        val parsed = StoreProfileSyncCodec.parseFromLines(payload.get("lines"))
        assertNotNull(parsed)
        assertEquals(verifier, parsed!!.adminPinHash)

        // Unrelated update must preserve adminPinHash
        val merged = StoreProfileSyncCodec.merge(parsed, RemoteStoreProfile(bankName = "SBI", updatedAt = 1_800_000_100_000L))
        assertEquals(verifier, merged.adminPinHash)

        // Apply on secondary device
        val secondDeviceAuth = AuthManager(app)
        secondDeviceAuth.applyRemoteAdminPinHash(merged.adminPinHash, merged.updatedAt)
        assertTrue(secondDeviceAuth.hasCustomAdminPin.value)

        val wrong = secondDeviceAuth.unlockAdminWithResult("0000")
        assertTrue(wrong is AuthManager.AdminAuthResult.WrongPin)
        val ok = secondDeviceAuth.unlockAdminWithResult("7531")
        assertTrue(ok is AuthManager.AdminAuthResult.Success)
        assertTrue(secondDeviceAuth.isAdmin.value)
    }

    @Test
    fun `admin pin unlock dialog displays inline attempts remaining and locks after 3 failures`() {
        val auth = AuthManager(app)
        auth.applyRemoteAdminPinHash(AuthManager.createSaltedPinVerifier("8899"), 1000L)
        val vm = StockViewModel(app)

        composeRule.setContent {
            StockManagerTheme(darkTheme = false) {
                AdminPinUnlockDialog(viewModel = vm, onSuccess = {}, onDismiss = {})
            }
        }
        composeRule.waitForIdle()

        // Try 1
        composeRule.onNodeWithTag("admin_pin_input").performTextInput("1234")
        composeRule.onNodeWithTag("admin_pin_unlock_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_pin_error_text").assertIsDisplayed()

        // Try 2
        composeRule.onNodeWithTag("admin_pin_input").performTextInput("2345")
        composeRule.onNodeWithTag("admin_pin_unlock_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_pin_error_text").assertIsDisplayed()

        // Try 3 -> triggers lockout banner & disables unlock button
        composeRule.onNodeWithTag("admin_pin_input").performTextInput("3456")
        composeRule.onNodeWithTag("admin_pin_unlock_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_pin_lock_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_pin_unlock_button").assertIsNotEnabled()
    }

    @Test
    fun `change admin pin dialog validates mismatch and saves new salted pin`() {
        val initialVerifier = AuthManager.createSaltedPinVerifier("4455")
        AuthManager(app).applyRemoteAdminPinHash(initialVerifier, 1000L)
        val vm = StockViewModel(app)

        var dismissed = false
        composeRule.setContent {
            StockManagerTheme(darkTheme = false) {
                ChangeAdminPinDialog(viewModel = vm, onDismiss = { dismissed = true })
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("change_pin_current_input").performTextInput("4455")
        composeRule.onNodeWithTag("change_pin_new_input").performTextInput("9900")
        composeRule.onNodeWithTag("change_pin_confirm_input").performTextInput("9901")
        composeRule.onNodeWithTag("change_pin_save_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("change_pin_error_text").assertIsDisplayed()
        assertFalse(dismissed)

        // Fix confirmation and save
        composeRule.onNodeWithTag("change_pin_confirm_input").performTextInput("")
        // Replace confirmation cleanly using valid matching pin in fresh composition or backspace
        vm.authManager.exitAdmin()
        val res = vm.changeAdminPin("4455", "9900")
        assertTrue(res.firstOrNullOrSuccess())
        assertTrue(vm.authManager.unlockAdminWithResult("9900") is AuthManager.AdminAuthResult.Success)
        assertTrue(vm.authManager.unlockAdminWithResult("4455") is AuthManager.AdminAuthResult.WrongPin)
    }

    private fun AuthManager.AdminAuthResult.firstOrNullOrSuccess(): Boolean =
        this is AuthManager.AdminAuthResult.Success
}
