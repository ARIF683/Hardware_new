package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.AuthManager
import com.example.data.remote.RemoteStoreProfile
import com.example.data.remote.StoreProfileSyncCodec
import com.example.ui.StockViewModel
import com.example.ui.components.AdminUserManagementDialog
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.StockManagerTheme
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdminRoleManagementTest {

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
        app.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `fresh auth manager has no hardcoded admin user`() {
        val auth = AuthManager(app)
        assertTrue(auth.registeredUsers.value.isEmpty())
        assertFalse(auth.isAdmin.value)
        assertNull(auth.currentUserEmail.value)
        assertNull(auth.getUserRolesJsonOrNull())
    }

    @Test
    fun `remote user roles sync grants admin status and updates current session`() {
        val auth = AuthManager(app)
        val prefs = app.getSharedPreferences("stock_manager_auth", Context.MODE_PRIVATE)
        prefs.edit().putString("current_user_email", "boss@hardware.com").commit()

        val authWithSession = AuthManager(app)
        assertEquals("boss@hardware.com", authWithSession.currentUserEmail.value)
        assertFalse(authWithSession.isAdmin.value)

        val remoteJson = """[{"email":"boss@hardware.com","is_admin":true},{"email":"staff@hardware.com","is_admin":false}]"""
        authWithSession.applyRemoteUserRoles(remoteJson, remoteUpdatedAt = 1000L)

        assertTrue(authWithSession.isAdmin.value)
        assertEquals(2, authWithSession.registeredUsers.value.size)
        assertTrue(authWithSession.registeredUsers.value.first { it.email == "boss@hardware.com" }.isAdmin)
        assertFalse(authWithSession.registeredUsers.value.first { it.email == "staff@hardware.com" }.isAdmin)

        // Older remote payload must not overwrite newer local changes
        authWithSession.upsertUser("clerk@hardware.com", false)
        authWithSession.applyRemoteUserRoles("""[{"email":"old@hardware.com","is_admin":true}]""", remoteUpdatedAt = 500L)
        assertEquals(3, authWithSession.registeredUsers.value.size)
        assertNull(authWithSession.registeredUsers.value.find { it.email == "old@hardware.com" })

        // Demoting & removing another user works cleanly
        authWithSession.setUserAdminRole("staff@hardware.com", true)
        assertTrue(authWithSession.registeredUsers.value.first { it.email == "staff@hardware.com" }.isAdmin)
        authWithSession.removeUser("clerk@hardware.com")
        assertNull(authWithSession.registeredUsers.value.find { it.email == "clerk@hardware.com" })
    }

    @Test
    fun `isMetadataAdmin detects Supabase app_metadata or user_metadata roles`() {
        val appMetaAdmin = JSONObject().put("app_metadata", JSONObject().put("is_admin", true))
        val appMetaRoleAdmin = JSONObject().put("app_metadata", JSONObject().put("role", "Admin"))
        val userMetaAdmin = JSONObject().put("user_metadata", JSONObject().put("role", "admin"))
        val staffUser = JSONObject().put("app_metadata", JSONObject().put("role", "staff"))

        assertTrue(AuthManager.isMetadataAdmin(appMetaAdmin))
        assertTrue(AuthManager.isMetadataAdmin(appMetaRoleAdmin))
        assertTrue(AuthManager.isMetadataAdmin(userMetaAdmin))
        assertFalse(AuthManager.isMetadataAdmin(staffUser))
        assertFalse(AuthManager.isMetadataAdmin(null))
    }

    @Test
    fun `StoreProfileSyncCodec serializes parses and merges userRolesJson`() {
        val rolesJson = """[{"email":"admin@shop.com","isAdmin":true}]"""
        val profile = RemoteStoreProfile(
            storeName = "Apex Hardware",
            userRolesJson = rolesJson,
            updatedAt = 2500L
        )
        val payload = StoreProfileSyncCodec.toJsonObject(profile)
        val parsed = StoreProfileSyncCodec.parseFromObject(payload)

        assertEquals("Apex Hardware", parsed?.storeName)
        assertEquals(rolesJson, parsed?.userRolesJson)
        assertEquals(2500L, parsed?.updatedAt)

        val merged = StoreProfileSyncCodec.merge(
            base = profile,
            override = RemoteStoreProfile(storeName = "Apex Depot", updatedAt = 3000L)
        )
        assertEquals("Apex Depot", merged.storeName)
        assertEquals(rolesJson, merged.userRolesJson)
        assertEquals(3000L, merged.updatedAt)
    }

    @Test
    fun `settings screen shows role row without admin pin controls`() {
        val vm = StockViewModel(app)
        composeRule.setContent {
            StockManagerTheme(darkTheme = false) {
                SettingsScreen(viewModel = vm)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_lazy_column")
            .performScrollToIndex(4)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_admin_access_row").assertIsDisplayed()
        composeRule.onNodeWithText("Change Admin PIN").assertDoesNotExist()
        composeRule.onNodeWithText("Unlock Admin Privileges").assertDoesNotExist()
    }

    @Test
    fun `admin user management dialog adds staff and toggles admin role`() {
        app.getSharedPreferences("stock_manager_auth", Context.MODE_PRIVATE)
            .edit()
            .putString("current_user_email", "owner@shop.com")
            .putBoolean("is_admin", true)
            .commit()

        val vm = StockViewModel(app)
        vm.authManager.upsertUser("owner@shop.com", true)

        composeRule.setContent {
            StockManagerTheme(darkTheme = false) {
                AdminUserManagementDialog(viewModel = vm, onDismiss = {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("admin_user_row_owner@shop.com").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_users_add_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("admin_user_email_input").performTextInput("cashier@shop.com")
        composeRule.onNodeWithTag("admin_user_confirm_add_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("admin_user_row_cashier@shop.com").assertIsDisplayed()
        assertFalse(vm.authManager.registeredUsers.value.first { it.email == "cashier@shop.com" }.isAdmin)

        // Promote cashier to Admin
        composeRule.onNodeWithTag("admin_user_toggle_cashier@shop.com").performClick()
        composeRule.waitForIdle()

        assertTrue(vm.authManager.registeredUsers.value.first { it.email == "cashier@shop.com" }.isAdmin)
    }
}
