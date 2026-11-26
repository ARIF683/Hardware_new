package com.example

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.AuthManager
import com.example.data.auth.SecureTokenStore
import com.example.data.local.AppDatabase
import com.example.data.model.ColorTintLog
import com.example.data.model.DailyCashflowRecord
import com.example.data.model.GstBillLineItem
import com.example.data.model.GstBillRecord
import com.example.data.model.Item
import com.example.data.model.LedgerAccount
import com.example.data.model.LedgerEntry
import com.example.data.model.QuotationRecord
import com.example.data.model.SyncQueueItem
import com.example.data.pref.BankingPreferenceManager
import com.example.data.remote.AppUpdateManager
import com.example.data.remote.RemoteStoreProfile
import com.example.data.remote.StoreProfileSyncCodec
import com.example.data.remote.SupabaseClient
import com.example.data.remote.UpdateStatus
import com.example.data.repository.StockRepository
import com.example.notifications.DailyReminderScheduler
import com.example.ui.StockViewModel
import com.example.ui.screens.GstCalculatorScreen
import com.example.ui.theme.StockManagerTheme
import com.example.util.DateTimeHelper
import com.example.util.GeminiBillScanner
import com.example.util.InvoicePrintManager
import com.example.util.SpreadsheetSanitizer
import com.example.util.XlsExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecurityAndBugFixesReport2Test {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        listOf(
            "stock_manager_auth",
            "settings_prefs",
            "banking_signature_pref",
            "gemini_prefs",
            "store_profile_prefs",
            "tint_machine_prefs",
            "daily_reminder_prefs"
        ).forEach { name ->
            app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        clearSingletonDb()
    }

    @After
    fun tearDown() {
        clearSingletonDb()
    }

    private fun clearSingletonDb() {
        java.util.concurrent.Executors.newSingleThreadExecutor().use { exec ->
            exec.submit {
                AppDatabase.getDatabase(app).clearAllTables()
            }.get()
        }
    }

    private fun buildInMemoryDb(): AppDatabase =
        Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    // ------------------------------------------------------------------
    // 1. StoreProfileSyncCodec never serializes or parses role data
    // ------------------------------------------------------------------
    @Test
    fun `storeProfileSyncCodec strips injected role payloads and omits role keys`() {
        val profile = RemoteStoreProfile(
            storeName = "Verified Hardware",
            location = "Main Market",
            updatedAt = 5000L
        )
        val payload = StoreProfileSyncCodec.toStoreProfilePayload(profile, "owner@store.com")
        assertEquals(StoreProfileSyncCodec.PROFILE_CLIENT_ID, payload.getString("id"))
        val innerJson = JSONObject(payload.getString("profile_json"))
        assertFalse(innerJson.has("userRolesJson"))
        assertFalse(innerJson.has("user_roles"))

        val maliciousInner = JSONObject(innerJson.toString())
            .put("userRolesJson", """[{"email":"hacker@evil.com","isAdmin":true}]""")
            .put("user_roles", """[{"email":"hacker@evil.com","is_admin":true}]""")
        val maliciousRow = JSONObject()
            .put("id", StoreProfileSyncCodec.PROFILE_CLIENT_ID)
            .put("profile_json", maliciousInner.toString())
            .put("updated_at", 9000L)

        val parsed = StoreProfileSyncCodec.parseFromStoreProfileRow(maliciousRow)
        assertNotNull(parsed)
        assertEquals("Verified Hardware", parsed!!.storeName)
        assertEquals(5000L, parsed.updatedAt)
    }

    // ------------------------------------------------------------------
    // 2. SupabaseClient fails closed without JWT & blocks realtime
    // ------------------------------------------------------------------
    @Test
    fun `supabaseClient fails closed when accessTokenProvider is blank for REST and realtime`() = runTest {
        val networkCalls = AtomicInteger(0)
        val okHttp = OkHttpClient.Builder()
            .addInterceptor { chain ->
                networkCalls.incrementAndGet()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("[]".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val client = SupabaseClient(
            baseUrl = "https://example.supabase.co",
            apiKey = "anon-key",
            okHttpClient = okHttp
        ).apply {
            configureAuth(accessTokenProvider = { "" })
        }

        var failed = false
        try {
            client.fetchAllItems()
        } catch (e: IllegalStateException) {
            failed = e.message?.contains("Authenticated user session required", ignoreCase = true) == true
        }
        assertTrue("Expected IllegalStateException when JWT is blank", failed)
        assertEquals("No HTTP call should be sent without JWT", 0, networkCalls.get())

        var realtimeConnected = true
        val ws = client.connectRealtime(
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            onStatusChanged = { realtimeConnected = it },
            onItemChanged = { _, _, _ -> },
            onClosedOrFailed = {}
        )
        assertNull(ws)
        assertFalse(realtimeConnected)

        client.configureAuth(accessTokenProvider = { "valid-user-jwt" })
        val items = client.fetchAllItems()
        assertTrue(items.isEmpty())
        assertEquals(1, networkCalls.get())
    }

    // ------------------------------------------------------------------
    // 3. AuthManager rejects unencrypted plaintext session flags & wipes on logout
    // ------------------------------------------------------------------
    @Test
    fun `authManager ignores forged plaintext prefs and clears sensitive state on logout`() {
        val authPrefs = app.getSharedPreferences("stock_manager_auth", Context.MODE_PRIVATE)
        authPrefs.edit()
            .putBoolean("is_authed", true)
            .putBoolean("is_admin", true)
            .putString("current_user_email", "forged@evil.com")
            .commit()

        val unauthed = AuthManager(app)
        assertFalse(unauthed.isAuthed.value)
        assertFalse(unauthed.isAdmin.value)
        assertNull(unauthed.currentUserEmail.value)
        assertTrue(SecureTokenStore.isRobolectricRuntime())

        // Now seed valid encrypted session + sensitive prefs and test logout cleanup
        AuthManager.seedTestSessionForTest(app, "owner@store.com", isAdmin = true)
        val authed = AuthManager(app)
        assertTrue(authed.isAuthed.value)
        assertTrue(authed.isAdmin.value)

        BankingPreferenceManager(app).saveBankDetails("HDFC", "Owner", "1234", "HDFC0001", "Main", "u@ok")
        app.getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE).edit().putString("x", "y").commit()
        app.getSharedPreferences("store_profile_prefs", Context.MODE_PRIVATE).edit().putString("s", "n").commit()
        app.getSharedPreferences("tint_machine_prefs", Context.MODE_PRIVATE).edit().putString("t", "z").commit()

        var clearedCallbackInvoked = false
        authed.onSessionClearedListener = { clearedCallbackInvoked = true }
        authed.logout(revokeServer = false)

        assertTrue(clearedCallbackInvoked)
        assertFalse(authed.isAuthed.value)
        assertFalse(authed.isAdmin.value)
        assertNull(authed.currentUserEmail.value)
        assertEquals("", authed.activeAccessToken())
        assertEquals("", BankingPreferenceManager(app).reloadFromPrefs().bankName)
        assertTrue(app.getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE).all.isEmpty())
        assertTrue(app.getSharedPreferences("store_profile_prefs", Context.MODE_PRIVATE).all.isEmpty())
        assertTrue(app.getSharedPreferences("tint_machine_prefs", Context.MODE_PRIVATE).all.isEmpty())
    }

    // ------------------------------------------------------------------
    // 4. Formula injection neutralization (CSV & XLS)
    // ------------------------------------------------------------------
    @Test
    fun `spreadsheetSanitizer and XlsExporter neutralize leading formula characters`() {
        assertEquals("\"'=CMD|'/C calc'!A0\"", SpreadsheetSanitizer.csvEscape("=CMD|'/C calc'!A0"))
        assertEquals("\"'+1+1\"", SpreadsheetSanitizer.csvEscape("+1+1"))
        assertEquals("\"'-2+3\"", SpreadsheetSanitizer.csvEscape("-2+3"))
        assertEquals("\"'@SUM(1,1)\"", SpreadsheetSanitizer.csvEscape("@SUM(1,1)"))
        assertEquals("\"Normal Bolt\"", SpreadsheetSanitizer.csvEscape("Normal Bolt"))

        val dangerousItem = Item(
            id = "f-1",
            code = "=HYPERLINK(\"http://evil\",\"SKU\")",
            name = "@SUM(A1:A5)",
            price = 100.0,
            qty = 2.0
        )
        val xml = XlsExporter.buildInventoryWorkbookXml(listOf(dangerousItem))
        assertTrue(xml.contains("&apos;=HYPERLINK"))
        assertTrue(xml.contains("&apos;@SUM(A1:A5)"))
    }

    // ------------------------------------------------------------------
    // 5. AppUpdateManager path traversal & SHA-256 enforcement
    // ------------------------------------------------------------------
    @Test
    fun `appUpdateManager blocks path traversal URLs and missing sha256 checksum`() = runTest {
        assertFalse(AppUpdateManager.isAllowedDownloadUrl("https://github.com/ARIF683/Hardware_new/releases/download/../../evil.apk"))
        assertFalse(AppUpdateManager.isAllowedDownloadUrl("https://github.com/ARIF683/Hardware_new/releases/download/v1\\app.apk"))
        assertTrue(AppUpdateManager.isAllowedDownloadUrl("https://github.com/ARIF683/Hardware_new/releases/download/v1.0.5/app.apk"))

        val mgr = AppUpdateManager(app)
        mgr.downloadAndInstall(
            downloadUrl = "https://github.com/ARIF683/Hardware_new/releases/download/v1.0.5/app.apk",
            expectedSha256 = "not-a-valid-sha256"
        )
        assertTrue(mgr.status.value is UpdateStatus.Error)
        assertTrue((mgr.status.value as UpdateStatus.Error).message.contains("SHA-256", ignoreCase = true))
    }

    // ------------------------------------------------------------------
    // 6. BankingPreferenceManager rejects HTTP URLs & enforces dimension limits
    // ------------------------------------------------------------------
    @Test
    fun `bankingPreferenceManager blocks insecure http URLs and oversized bytes`() = runBlocking {
        val mgr = BankingPreferenceManager(app)
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { bos ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
            bos.toByteArray()
        }

        val httpQr = mgr.saveQrCodeFromBytes(bytes, "http://insecure.example.com/qr.png")
        val httpSig = mgr.saveSignatureFromBytes(bytes, "http://insecure.example.com/sig.png")
        assertTrue(httpQr.isFailure)
        assertTrue(httpSig.isFailure)

        val tooBig = ByteArray(6 * 1024 * 1024)
        assertNull(mgr.decodeBoundedBitmap(tooBig))
    }

    // ------------------------------------------------------------------
    // 7. GeminiBillScanner edge proxy URL construction
    // ------------------------------------------------------------------
    @Test
    fun `geminiBillScanner builds Supabase edge proxy URL cleanly`() {
        assertEquals(
            "https://proj.supabase.co/functions/v1/gemini-bill-scan",
            GeminiBillScanner.buildEdgeProxyUrl("\"https://proj.supabase.co/\"")
        )
    }

    // ------------------------------------------------------------------
    // 8. SupabaseClient pagination (>1000 rows) & batch chunking (150 items)
    // ------------------------------------------------------------------
    @Test
    fun `supabaseClient paginates fetchAllQuotations past 1000 rows and chunks upsertItems at 150`() = runTest {
        val batchSizes = mutableListOf<Int>()
        val pageOffsets = mutableListOf<String>()

        val interceptor = Interceptor { chain ->
            val req = chain.request()
            if (req.url.encodedPath.endsWith("/items") && req.method == "POST") {
                val buf = Buffer()
                req.body?.writeTo(buf)
                batchSizes.add(JSONArray(buf.readUtf8()).length())
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(201)
                    .message("Created")
                    .body("".toResponseBody("application/json".toMediaType()))
                    .build()
            } else if (req.url.encodedPath.endsWith("/quotations") && req.method == "GET") {
                val offset = req.url.queryParameter("offset") ?: "0"
                pageOffsets.add(offset)
                val count = if (offset == "0") 1000 else 5
                val arr = JSONArray()
                for (i in 0 until count) {
                    arr.put(
                        JSONObject()
                            .put("id", "q_${offset}_$i")
                            .put("quotation_no", "EST-$i")
                            .put("customer_name", "Cust $i")
                            .put("date", "2026-11-25")
                            .put("items_json", "[]")
                            .put("subtotal", 10.0)
                            .put("discount", 0.0)
                            .put("tax_percent", 0.0)
                            .put("tax_amount", 0.0)
                            .put("grand_total", 10.0)
                            .put("created_at", "2026-11-25T10:00:00Z")
                    )
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(arr.toString().toResponseBody("application/json".toMediaType()))
                    .build()
            } else {
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("[]".toResponseBody("application/json".toMediaType()))
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

        val quotations = client.fetchAllQuotations()!!
        assertEquals(1005, quotations.size)
        assertEquals(listOf("0", "1000"), pageOffsets)

        val manyItems = (1..320).map { Item(id = "it-$it", name = "Bolt $it", price = 10.0, qty = 1.0) }
        client.upsertItems(manyItems)
        assertEquals(listOf(150, 150, 20), batchSizes)
    }

    // ------------------------------------------------------------------
    // 9. StockRepository preserves queued local edits/deletes on loadFromRemote & clears optional fields
    // ------------------------------------------------------------------
    @Test
    fun `stockRepository preserves pending edits on loadFromRemote, syncs gstBills, and clears optional item fields`() = runTest {
        val db = buildInMemoryDb()
        try {
            val interceptor = Interceptor { chain ->
                val req = chain.request()
                val body = when {
                    req.url.encodedPath.endsWith("/items") && req.method == "GET" -> {
                        // Remote still has stale copy of it-local-edit and it-pending-del
                        JSONArray()
                            .put(JSONObject().put("id", "it-local-edit").put("name", "Old Remote Name").put("qty", 1.0).put("price", 10.0))
                            .put(JSONObject().put("id", "it-pending-del").put("name", "Should Stay Deleted").put("qty", 2.0).put("price", 20.0))
                            .toString()
                    }
                    req.url.encodedPath.endsWith("/gst_bills") && req.method == "GET" -> {
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("id", "gst-rem-1")
                                    .put("invoice_no", "BILL-700")
                                    .put("invoice_date", "2026-11-25")
                                    .put("customer_name", "Walk-in")
                                    .put("items_json", "[]")
                                    .put("taxable_total", 100.0)
                                    .put("total_tax_amount", 18.0)
                                    .put("grand_total", 118.0)
                                    .put("created_at", "2026-11-25T10:00:00Z")
                            )
                            .toString()
                    }
                    req.url.encodedPath.endsWith("/color_tint_logs") && req.method == "GET" -> {
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("tint_record_id", "tint-rem-1")
                                    .put("product_name", "Apex")
                                    .put("shade_name", "Cream")
                                    .put("base_code", "W1")
                                    .put("tint_timestamp_iso", "2026-11-25T10:00:00Z")
                            )
                            .toString()
                    }
                    req.method == "POST" || req.method == "DELETE" || req.method == "PATCH" -> {
                        // Simulate offline/503 during queue flush so pending ops stay queued
                        return@Interceptor Response.Builder()
                            .request(req)
                            .protocol(Protocol.HTTP_1_1)
                            .code(503)
                            .message("Offline")
                            .body("""{"error":"offline"}""".toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                    else -> "[]"
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }

            val client = SupabaseClient(
                baseUrl = "https://example.supabase.co",
                apiKey = "anon-key",
                okHttpClient = OkHttpClient.Builder().addInterceptor(interceptor).build()
            ).apply {
                configureAuth(accessTokenProvider = { "test-jwt" })
            }

            // Seed local edited item + queue its upsert + queue delete of another item
            val localItem = Item(
                id = "it-local-edit",
                name = "Fresh Local Name",
                qty = 50.0,
                price = 99.0,
                shelfLocation = "Aisle-2",
                nfcTagId = "NFC-99",
                secondaryUnit = "box",
                conversionFactor = 10.0,
                secondaryPrice = 900.0
            )
            db.itemDao().insert(localItem)
            db.syncQueueDao().insert(
                SyncQueueItem(
                    type = "upsert",
                    payloadJson = JSONArray().put(JSONObject().put("id", "it-local-edit")).toString(),
                    timestamp = 1L
                )
            )
            db.syncQueueDao().insert(
                SyncQueueItem(
                    type = "delete",
                    payloadJson = JSONArray().put("it-pending-del").toString(),
                    timestamp = 2L
                )
            )

            AuthManager.seedTestSessionForTest(app, "owner@store.com", isAdmin = true)
            val repo = StockRepository(app, database = db, supabaseClient = client, authManager = AuthManager(app))
            repo.loadFromRemote()

            // Pending local edit survives and pending delete stays absent
            val fetchedEdited = db.itemDao().getItemByIdOnce("it-local-edit")
            assertEquals("Fresh Local Name", fetchedEdited?.name)
            assertNull(db.itemDao().getItemByIdOnce("it-pending-del"))

            // Remote GST bill & color tint log synced into Room
            assertEquals(1, db.gstBillDao().getAllGstBillsList().size)
            assertEquals("BILL-700", db.gstBillDao().getAllGstBillsList().first().invoiceNo)
            assertEquals(1, db.colorTintDao().getAllTintLogsList().size)
            assertEquals("tint-rem-1", db.colorTintDao().getAllTintLogsList().first().tintRecordId)

            // Explicitly clearing optional fields persists nulls/defaults when preserveExistingOptionalFields = false
            repo.saveItem(
                id = "it-local-edit",
                name = "Fresh Local Name",
                code = "",
                barcode = "",
                type = "",
                brand = "",
                size = "",
                unit = "pcs",
                mrp = null,
                cost = 50.0,
                price = 99.0,
                low = 5.0,
                aliases = "",
                openingQty = 50.0,
                nfcTagId = null,
                shelfLocation = null,
                secondaryUnit = null,
                conversionFactor = 1.0,
                secondaryPrice = 0.0,
                preserveExistingOptionalFields = false
            )
            val clearedItem = db.itemDao().getItemByIdOnce("it-local-edit")!!
            assertNull(clearedItem.shelfLocation)
            assertNull(clearedItem.nfcTagId)
            assertNull(clearedItem.secondaryUnit)
            assertEquals(0.0, clearedItem.secondaryPrice, 0.001)
        } finally {
            db.close()
        }
    }

    // ------------------------------------------------------------------
    // 10. GST discount reconciliation & GstCalculatorScreen UI + single-blank-row start
    // ------------------------------------------------------------------
    @Test
    fun `distributeDiscountAcrossGstLines reconciles discounted tax and totals accurately`() {
        val raw = listOf(
            GstBillLineItem(id = "1", name = "Paint", qty = 2.0, rate = 500.0, taxRatePercent = 18.0, taxableAmount = 1000.0, taxAmount = 180.0, totalAmount = 1180.0),
            GstBillLineItem(id = "2", name = "Brush", qty = 5.0, rate = 100.0, taxRatePercent = 12.0, taxableAmount = 500.0, taxAmount = 60.0, totalAmount = 560.0)
        )
        val discounted = InvoicePrintManager.distributeDiscountAcrossGstLines(raw, discount = 150.0)
        assertEquals(1350.0, discounted.sumOf { it.taxableAmount }, 0.01)
        // 10% discount -> 900 * 18% (162) + 450 * 12% (54) = 216
        assertEquals(216.0, discounted.sumOf { it.taxAmount }, 0.01)
        assertEquals(1566.0, discounted.sumOf { it.totalAmount }, 0.01)
    }

    @Test
    fun `gstCalculatorScreen starts with default blank row and renders gst_discount_input`() {
        AuthManager.seedTestSessionForTest(app, "owner@store.com", isAdmin = true)
        val vm = StockViewModel(app)

        composeRule.setContent {
            StockManagerTheme(darkTheme = false) {
                GstCalculatorScreen(viewModel = vm)
            }
        }
        composeRule.waitForIdle()

        val listNode = composeRule.onAllNodes(hasScrollToIndexAction()).onFirst()
        listNode.performScrollToNode(hasTestTag("gst_row_qty_0"))
        composeRule.onNodeWithTag("gst_row_qty_0", useUnmergedTree = true).assertIsDisplayed()

        listNode.performScrollToNode(hasTestTag("gst_discount_input"))
        composeRule.onNodeWithTag("gst_discount_input", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("gst_discount_input", useUnmergedTree = true).performTextReplacement("50")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("50", useUnmergedTree = true).assertIsDisplayed()
    }

    // ------------------------------------------------------------------
    // 11. ViewModel RBAC blocks non-admin writes
    // ------------------------------------------------------------------
    @Test
    fun `viewModel blocks non-admin destructive and store profile mutations`() {
        AuthManager.seedTestSessionForTest(app, "staff@store.com", isAdmin = false)
        val vm = StockViewModel(app)

        vm.saveStoreIdentity("Hacked Store", "Hacker", "Boss", "Nowhere")
        assertFalse(vm.storeIdentity.value.storeName == "Hacked Store")

        vm.saveBankDetails("Evil Bank", "Hacker", "0000", "EVIL0001", "Branch", "hack@upi")
        assertEquals("", BankingPreferenceManager(app).reloadFromPrefs().bankName)
    }

    // ------------------------------------------------------------------
    // 12. Scheduler midnight window & DateTimeHelper normalization & quotation path safety
    // ------------------------------------------------------------------
    @Test
    fun `scheduler midnight wrap and date normalization and safe PDF filenames work cleanly`() {
        val tz = TimeZone.getTimeZone("Asia/Kolkata")
        val lateNightCal = Calendar.getInstance(tz).apply {
            set(2026, Calendar.NOVEMBER, 25, 22, 15, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val trigger = DailyReminderScheduler.computeNextDayTriggerMillis(lateNightCal.timeInMillis, tz)
        val cal = Calendar.getInstance(tz).apply { timeInMillis = trigger }
        assertEquals(26, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(19, cal.get(Calendar.HOUR_OF_DAY))

        assertEquals("2026-11-25", DateTimeHelper.normalizeDateToIso("25/11/2026"))

        val outputDir = File(app.cacheDir, "invoices")
        val pdfFile = InvoicePrintManager.safePdfOutputFile(
            outputDir = outputDir,
            prefix = "Quotation",
            unsafeIdentifier = "../../etc/passwd",
            fallbackSlug = "Estimate"
        )
        val expectedParent = outputDir.canonicalPath
        assertTrue(pdfFile.canonicalPath.startsWith(expectedParent + File.separator))
        assertFalse(pdfFile.name.contains(".."))
    }
}
