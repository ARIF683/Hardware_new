package com.example

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.AuthManager
import com.example.data.local.AppDatabase
import com.example.data.local.AuditLogEntity
import com.example.data.model.AuditLogRecord
import com.example.data.remote.SupabaseClient
import com.example.data.repository.StockRepository
import com.example.ui.CurrentScreen
import com.example.ui.StockViewModel
import com.example.ui.screens.AuditLogsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.StockManagerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuditLogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("stock_manager_auth", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        app.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        runBlocking(Dispatchers.IO) {
            AppDatabase.getDatabase(app).auditLogDao().clearAuditLogs()
        }
    }

    @After
    fun tearDown() {
        runBlocking(Dispatchers.IO) {
            AppDatabase.getDatabase(app).auditLogDao().clearAuditLogs()
        }
    }

    private fun seedSession(email: String, isAdmin: Boolean) {
        AuthManager.seedTestSessionForTest(app, email, isAdmin)
    }

    @Test
    fun `repository records audit logs locally and queues supabase upload on item add update stock and delete`() = runBlocking(Dispatchers.IO) {
        seedSession(email = "staff.worker@store.com", isAdmin = false)
        val inMemoryDb = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val auth = AuthManager(app)
            val unconfiguredSupabase = SupabaseClient(baseUrl = "", apiKey = "")
            val repo = StockRepository(
                context = app,
                database = inMemoryDb,
                supabaseClient = unconfiguredSupabase,
                authManager = auth
            )

            val added = repo.saveItem(
                id = null,
                name = "Asian Paints Primer 4L",
                code = "AP-PR-4L",
                barcode = "890123456",
                type = "Paint",
                brand = "Asian Paints",
                size = "4L",
                unit = "can",
                mrp = 950.0,
                cost = 700.0,
                price = 850.0,
                low = 5.0,
                aliases = "",
                openingQty = 10.0
            )

            repo.saveItem(
                id = added.id,
                name = "Asian Paints Primer 4L",
                code = "AP-PR-4L",
                barcode = "890123456",
                type = "Paint",
                brand = "Asian Paints",
                size = "4L",
                unit = "can",
                mrp = 950.0,
                cost = 710.0,
                price = 880.0,
                low = 5.0,
                aliases = "",
                openingQty = 10.0
            )

            repo.performTransaction(itemId = added.id, action = "in", qty = 4.0, note = "Supplier delivery")
            repo.deleteItem(added.id)

            val logs = withTimeout(5_000) {
                inMemoryDb.auditLogDao().getAllAuditLogs().first { it.size == 4 }
            }
            assertEquals(4, logs.size)
            assertTrue(logs.all { it.userEmail == "staff.worker@store.com" })

            val actions = logs.map { it.action }.toSet()
            assertTrue(actions.contains("ADD_ITEM"))
            assertTrue(actions.contains("UPDATE_ITEM"))
            assertTrue(actions.contains("STOCK_IN"))
            assertTrue(actions.contains("DELETE_ITEM"))

            val deleteLog = logs.first { it.action == "DELETE_ITEM" }
            assertTrue(deleteLog.details.contains("Asian Paints Primer 4L"))

            val queuedAuditOps = inMemoryDb.syncQueueDao().getAll().filter { it.type == "audit" }
            assertEquals(4, queuedAuditOps.size)
        } finally {
            inMemoryDb.close()
        }
    }

    @Test
    fun `supabaseClient inserts and fetches audit_logs with expected snake_case schema`() = runTest {
        var lastPostBody: String? = null
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            if (request.method == "POST") {
                val buf = Buffer()
                request.body?.writeTo(buf)
                lastPostBody = buf.readUtf8()
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(201)
                    .message("Created")
                    .body("".toResponseBody("application/json".toMediaType()))
                    .build()
            } else {
                val mockResp = JSONArray().put(
                    JSONObject()
                        .put("id", "audit_101")
                        .put("user_email", "admin@store.com")
                        .put("action", "UPDATE_ITEM")
                        .put("details", "Updated Hex Bolt M8")
                        .put("created_at", "2026-11-21T10:00:00.000Z")
                ).toString()
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(mockResp.toResponseBody("application/json".toMediaType()))
                    .build()
            }
        }

        val client = SupabaseClient(
            baseUrl = "https://example.supabase.co",
            apiKey = "test-anon-key",
            okHttpClient = OkHttpClient.Builder().addInterceptor(interceptor).build()
        ).apply {
            configureAuth(accessTokenProvider = { "test-jwt" })
        }

        client.insertAuditLog(
            AuditLogRecord(
                id = "audit_999",
                userEmail = "staff@store.com",
                action = "ADD_ITEM",
                details = "Added PVC Pipe 1 inch",
                createdAt = "2026-11-21T09:50:00.000Z"
            )
        )

        val postedArr = JSONArray(lastPostBody!!)
        val postedObj = postedArr.getJSONObject(0)
        assertEquals("audit_999", postedObj.getString("id"))
        assertEquals("staff@store.com", postedObj.getString("user_email"))
        assertEquals("ADD_ITEM", postedObj.getString("action"))
        assertEquals("Added PVC Pipe 1 inch", postedObj.getString("details"))

        val fetched = client.fetchAllAuditLogs()
        assertEquals(1, fetched.size)
        assertEquals("audit_101", fetched[0].id)
        assertEquals("admin@store.com", fetched[0].userEmail)
        assertEquals("UPDATE_ITEM", fetched[0].action)
        assertEquals("Updated Hex Bolt M8", fetched[0].details)
    }

    @Test
    fun `viewModel restricts openAuditLogs and allAuditLogs flow to admin users only`() {
        runBlocking(Dispatchers.IO) {
            AppDatabase.getDatabase(app).auditLogDao().insertAuditLog(
                AuditLogEntity(
                    id = "audit_secret_1",
                    userEmail = "staff@store.com",
                    action = "DELETE_ITEM",
                    details = "Deleted Steel Valve",
                    createdAt = "2026-11-21T08:00:00.000Z"
                )
            )
        }

        // Non-admin session cannot open AuditLogs or receive logs
        seedSession(email = "staff@store.com", isAdmin = false)
        val staffVm = StockViewModel(app)
        assertFalse(staffVm.authManager.isAdmin.value)
        staffVm.openAuditLogs()
        assertEquals(CurrentScreen.Main, staffVm.currentScreen.value)

        // Admin session can open AuditLogs and receives logs when collected
        seedSession(email = "owner@store.com", isAdmin = true)
        val adminVm = StockViewModel(app)
        assertTrue(adminVm.authManager.isAdmin.value)
        adminVm.openAuditLogs()
        assertEquals(CurrentScreen.AuditLogs, adminVm.currentScreen.value)

        var latestAdminLogs: List<AuditLogEntity> = emptyList()
        var latestStaffLogs: List<AuditLogEntity> = emptyList()
        composeRule.setContent {
            val aLogs by adminVm.allAuditLogs.collectAsState()
            val sLogs by staffVm.allAuditLogs.collectAsState()
            latestAdminLogs = aLogs
            latestStaffLogs = sLogs
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            latestAdminLogs.any { it.id == "audit_secret_1" }
        }
        assertTrue(latestStaffLogs.isEmpty())
        assertTrue(latestAdminLogs.any { it.id == "audit_secret_1" })
    }

    @Test
    fun `auditLogsScreen blocks non-admin and renders admin required card`() {
        runBlocking(Dispatchers.IO) {
            AppDatabase.getDatabase(app).auditLogDao().insertAuditLogs(
                listOf(
                    AuditLogEntity(
                        id = "audit_a1",
                        userEmail = "rahul@store.com",
                        action = "ADD_ITEM",
                        details = "Added new item: Brass Elbow 1/2 (Code: BE-05)",
                        createdAt = "2026-11-21T09:15:00.000Z"
                    )
                )
            )
        }

        seedSession(email = "rahul@store.com", isAdmin = false)
        val staffVm = StockViewModel(app)

        composeRule.setContent {
            StockManagerTheme(darkTheme = false) {
                AuditLogsScreen(viewModel = staffVm, onBack = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("audit_logs_admin_required_card").assertIsDisplayed()
        composeRule.onNodeWithText("Admin Access Required").assertIsDisplayed()
        composeRule.onNodeWithTag("audit_logs_lazy_column").assertDoesNotExist()
    }

    @Test
    fun `auditLogsScreen displays logs and filters for admin`() {
        runBlocking(Dispatchers.IO) {
            AppDatabase.getDatabase(app).auditLogDao().insertAuditLogs(
                listOf(
                    AuditLogEntity(
                        id = "audit_a1",
                        userEmail = "rahul@store.com",
                        action = "ADD_ITEM",
                        details = "Added new item: Brass Elbow 1/2 (Code: BE-05)",
                        createdAt = "2026-11-21T09:15:00.000Z"
                    ),
                    AuditLogEntity(
                        id = "audit_a2",
                        userEmail = "owner@store.com",
                        action = "DELETE_ITEM",
                        details = "Deleted item: Old Hose Pipe",
                        createdAt = "2026-11-21T09:30:00.000Z"
                    )
                )
            )
        }

        seedSession(email = "owner@store.com", isAdmin = true)
        val adminVm = StockViewModel(app)

        composeRule.setContent {
            StockManagerTheme(darkTheme = false) {
                AuditLogsScreen(viewModel = adminVm, onBack = {})
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            adminVm.allAuditLogs.value.size >= 2
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("audit_logs_lazy_column").assertIsDisplayed()
        composeRule.onNodeWithTag("audit_log_card_audit_a1").assertIsDisplayed()
        composeRule.onNodeWithTag("audit_log_card_audit_a2").assertIsDisplayed()

        // Filter to Deleted only
        composeRule.onNodeWithTag("audit_filter_deleted").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("audit_log_card_audit_a2").assertIsDisplayed()
        composeRule.onNodeWithTag("audit_log_card_audit_a1").assertDoesNotExist()

        // Reset to All and filter by search query
        composeRule.onNodeWithTag("audit_filter_all").performClick()
        composeRule.onNodeWithTag("audit_logs_search_input").performTextInput("Brass Elbow")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("audit_log_card_audit_a1").assertIsDisplayed()
        composeRule.onNodeWithTag("audit_log_card_audit_a2").assertDoesNotExist()
    }

    @Test
    fun `settingsScreen exposes audit logs entry only for admins`() {
        seedSession(email = "staff@store.com", isAdmin = false)
        val staffVm = StockViewModel(app)

        composeRule.setContent {
            StockManagerTheme(darkTheme = false) {
                SettingsScreen(viewModel = staffVm)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_audit_logs_row").assertDoesNotExist()
    }

    @Test
    fun `settingsScreen shows audit logs row and navigates when tapped by admin`() {
        seedSession(email = "owner@store.com", isAdmin = true)
        val adminVm = StockViewModel(app)

        composeRule.setContent {
            StockManagerTheme(darkTheme = false) {
                SettingsScreen(viewModel = adminVm)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasTestTag("settings_audit_logs_row"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_audit_logs_row").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        assertEquals(CurrentScreen.AuditLogs, adminVm.currentScreen.value)
    }
}
