package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import kotlinx.coroutines.launch
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.AuthManager
import com.example.data.local.AppDatabase
import com.example.data.model.DailyCashflowRecord
import com.example.data.model.GstBillLineItem
import com.example.data.model.GstBillRecord
import com.example.data.model.Item
import com.example.data.model.LedgerAccount
import com.example.data.model.LedgerEntry
import com.example.data.model.QuotationRecord
import com.example.data.model.SyncQueueItem
import com.example.data.pref.AppSettingsManager
import com.example.data.remote.SupabaseClient
import com.example.data.repository.BillRowData
import com.example.data.repository.StockRepository
import com.example.notifications.DailyReminderScheduler
import com.example.ui.NavigationTab
import com.example.ui.StockViewModel
import com.example.ui.screens.CashflowRowCard
import com.example.ui.screens.GstCalculatorScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ItemDetailScreen
import com.example.ui.screens.ItemsScreen
import com.example.ui.screens.QuickBillingPosScreen
import com.example.ui.screens.parseCsvItems
import com.example.ui.theme.StockManagerTheme
import com.example.util.DateTimeHelper
import com.example.util.InvoicePrintManager
import com.example.util.ParsedTintRow
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
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
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w320dp-h569dp-xhdpi")
class AuditFixesRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Application

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("store_profile_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("tint_machine_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("daily_reminder_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun clearSingletonDb() {
        java.util.concurrent.Executors.newSingleThreadExecutor().use { exec ->
            exec.submit {
                AppDatabase.getDatabase(context).clearAllTables()
            }.get()
        }
    }

    @After
    fun tearDown() {
        clearSingletonDb()
    }

    private fun buildInMemoryDb(): AppDatabase {
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    private fun buildMockClient(handler: (okhttp3.Request, String) -> Pair<Int, String>): SupabaseClient {
        val okHttp = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val buf = Buffer()
                req.body?.writeTo(buf)
                val bodyText = buf.readUtf8()
                val (code, respBody) = handler(req, bodyText)
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code in 200..299) "OK" else "Error")
                    .body(respBody.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()
        return SupabaseClient(
            baseUrl = "https://example.supabase.co",
            apiKey = "test-anon-key",
            okHttpClient = okHttp
        )
    }

    // ------------------------------------------------------------------
    // 1. Khata fetch returns null on error & loadFromRemote preserves data
    // ------------------------------------------------------------------
    @Test
    fun `fetchAllLedgerAccounts and Entries return null on HTTP 500 and loadFromRemote preserves local Khata`() = runTest {
        val client = buildMockClient { req, _ ->
            when {
                req.url.encodedPath.endsWith("/ledger_accounts") -> 500 to """{"message":"db down"}"""
                req.url.encodedPath.endsWith("/ledger_entries") -> 500 to """{"message":"db down"}"""
                req.url.encodedPath.endsWith("/items") -> 200 to "[]"
                req.url.encodedPath.endsWith("/transactions") -> 200 to "[]"
                req.url.encodedPath.endsWith("/quotations") -> 200 to "[]"
                req.url.encodedPath.endsWith("/daily_cashflow") -> 200 to "[]"
                req.url.encodedPath.endsWith("/store_profiles") -> 200 to "[]"
                else -> 200 to "[]"
            }
        }

        assertNull(client.fetchAllLedgerAccounts())
        assertNull(client.fetchAllLedgerEntries())

        val db = buildInMemoryDb()
        try {
            val acc = LedgerAccount(
                id = "khata-acc-1",
                name = "Ramesh Builders",
                type = "CUSTOMER",
                netBalance = 1500.0,
                createdAt = "2026-11-24T10:00:00Z",
                updatedAt = "2026-11-24T10:00:00Z"
            )
            val entry = LedgerEntry(
                id = "khata-entry-1",
                accountId = acc.id,
                type = "GAVE",
                amount = 1500.0,
                balanceAfter = 1500.0,
                date = "2026-11-24",
                createdAt = "2026-11-24T10:00:00Z"
            )
            db.ledgerDao().insertAccount(acc)
            db.ledgerDao().insertEntry(entry)

            val repo = StockRepository(context, database = db, supabaseClient = client, authManager = AuthManager(context))
            repo.loadFromRemote()

            assertEquals(1, db.ledgerDao().getAllAccountsList().size)
            assertEquals("Ramesh Builders", db.ledgerDao().getAllAccountsList().first().name)
            assertEquals(1, db.ledgerDao().getAllEntriesList().size)
        } finally {
            db.close()
        }
    }

    // ------------------------------------------------------------------
    // 2. Offline sync queue preserves transient HTTP 503 ops & drops corrupt JSON
    // ------------------------------------------------------------------
    @Test
    fun `flushQueue preserves transient 503 operations and removes only corrupt JSON payloads`() = runTest {
        val db = buildInMemoryDb()
        try {
            val client = buildMockClient { _, _ -> 503 to """{"message":"Service Unavailable"}""" }
            val validItem = Item(id = "item-sync-1", name = "PVC Pipe 1\"", qty = 20.0, price = 150.0)
            val validJson = client.let {
                val arr = listOf(validItem)
                com.squareup.moshi.Moshi.Builder()
                    .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                    .build()
                    .adapter<List<Item>>(
                        com.squareup.moshi.Types.newParameterizedType(List::class.java, Item::class.java)
                    ).toJson(arr)
            }

            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            cm.activeNetwork?.let { net ->
                cm.getNetworkCapabilities(net)?.let { caps ->
                    org.robolectric.Shadows.shadowOf(caps).addCapability(
                        android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
                    )
                }
            }

            // Place corrupt JSON first so it is permanently dropped without blocking later ops,
            // followed by a valid upsert that fails with transient 503 and stays queued.
            db.syncQueueDao().insert(SyncQueueItem(type = "patch", payloadJson = "{not-valid-json", timestamp = 1L))
            db.syncQueueDao().insert(SyncQueueItem(type = "upsert", payloadJson = validJson, timestamp = 2L))

            val repo = StockRepository(context, database = db, supabaseClient = client, authManager = AuthManager(context))
            repo.flushQueue()

            val remaining = db.syncQueueDao().getAll()
            assertEquals(1, remaining.size)
            assertEquals("upsert", remaining.first().type)
            assertTrue("Expected Pending 1 status after 503, got ${repo.syncStatus.value}", repo.syncStatus.value.startsWith("Pending 1"))
        } finally {
            db.close()
        }
    }

    // ------------------------------------------------------------------
    // 3a. saveAndFinalizeGstBill deducts duplicate item rows & logs SALE cashflow
    // ------------------------------------------------------------------
    @Test
    fun `saveAndFinalizeGstBill deducts stock cumulatively for duplicate items and records SALE cashflow`() = runTest {
        val db = buildInMemoryDb()
        try {
            val client = buildMockClient { _, _ -> 200 to "[]" }
            val pipe = Item(id = "pipe-1", name = "CPVC Pipe", qty = 10.0, cost = 80.0, price = 120.0)
            db.itemDao().insertAll(listOf(pipe))

            val repo = StockRepository(context, database = db, supabaseClient = client, authManager = AuthManager(context))
            val bill = GstBillRecord(
                id = "bill-gst-1",
                invoiceNo = "BILL-1043",
                invoiceDate = "24/11/2026",
                customerName = "Walk-in",
                itemsJson = "[]",
                taxableTotal = 360.0,
                totalTaxAmount = 64.8,
                grandTotal = 424.8,
                createdAt = "2026-11-24T12:00:00.000Z"
            )
            val lines = listOf(
                GstBillLineItem(id = "pipe-1", name = "CPVC Pipe", qty = 2.0, rate = 120.0, taxRatePercent = 18.0, totalAmount = 283.2),
                GstBillLineItem(id = "pipe-1", name = "CPVC Pipe", qty = 1.5, rate = 120.0, taxRatePercent = 18.0, totalAmount = 212.4)
            )
            val res = repo.saveAndFinalizeGstBill(
                bill = bill,
                lineItems = lines,
                deductStock = true,
                recordCashflowSale = true,
                paymentMode = "UPI"
            )
            assertTrue(res.isSuccess)
            assertEquals(2, res.getOrNull())

            val updated = db.itemDao().getItemByIdOnce("pipe-1")!!
            assertEquals(6.5, updated.qty, 0.001)

            val savedBills = db.gstBillDao().getAllGstBillsList()
            assertEquals(1, savedBills.size)

            val cashflow = db.dailyCashflowDao().getAllCashflowList()
            assertEquals(1, cashflow.size)
            assertEquals("SALE", cashflow.first().type)
            assertEquals(424.8, cashflow.first().amount, 0.001)
            assertEquals("2026-11-24", cashflow.first().date)
            assertEquals("UPI", cashflow.first().paymentMode)
        } finally {
            db.close()
        }
    }

    // ------------------------------------------------------------------
    // 3b. processSalesReturn matches by name fallback & reports unmatched items
    // ------------------------------------------------------------------
    @Test
    fun `processSalesReturn matches items by name fallback and includes tax in credit note toast`() {
        clearSingletonDb()
        val db = AppDatabase.getDatabase(context)
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            db.itemDao().insertAll(
                listOf(Item(id = "real-id-42", name = "Wall Putty 20kg", qty = 5.0, cost = 400.0, price = 500.0))
            )
        }

        val vm = StockViewModel(context)
        val capturedToasts = mutableListOf<String>()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)
        val collectJob = scope.launch {
            vm.toastEvent.collect { msg ->
                capturedToasts.add(msg)
            }
        }
        composeRule.waitForIdle()

        val line = GstBillLineItem(
            id = "legacy-random-uuid",
            name = "Wall Putty 20kg",
            qty = 2.0,
            rate = 500.0,
            taxRatePercent = 18.0,
            totalAmount = 1180.0
        )
        val bill = GstBillRecord(
            id = "bill-ret-1",
            invoiceNo = "BILL-1050",
            invoiceDate = "2026-11-24",
            customerName = "Test Buyer",
            itemsJson = InvoicePrintManager.gstItemsToJson(listOf(line)),
            taxableTotal = 1000.0,
            totalTaxAmount = 180.0,
            grandTotal = 1180.0,
            createdAt = "2026-11-24T12:00:00Z"
        )

        vm.processSalesReturn(bill, returnMap = mapOf("legacy-random-uuid" to 1.0), restock = true, reason = "Extra")
        composeRule.waitUntil(5000L) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            capturedToasts.any { it.contains("Credit Note", ignoreCase = true) }
        }
        collectJob.cancel()

        val updated = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            db.itemDao().getItemByIdOnce("real-id-42")!!
        }
        assertEquals(6.0, updated.qty, 0.001)
        assertTrue(capturedToasts.any { it.contains("590.00") && it.contains("restocked", ignoreCase = true) })
    }

    // ------------------------------------------------------------------
    // 4. AppDatabase migrations 1->2->3->4 and 7->8->9 preserve rows
    // ------------------------------------------------------------------
    @Test
    fun `appDatabase migrations 1 to 2 to 3 to 4 and 7 to 8 to 9 preserve existing rows without data loss`() {
        val dbName = "migration_verify_${System.nanoTime()}.db"
        val factory = FrameworkSQLiteOpenHelperFactory()

        // Create v1 schema manually with a sample item, then migrate through 1->2->3->4
        val helperV1 = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS items (
                                id TEXT PRIMARY KEY NOT NULL,
                                o INTEGER NOT NULL DEFAULT 0,
                                code TEXT NOT NULL DEFAULT '',
                                barcode TEXT NOT NULL DEFAULT '',
                                name TEXT NOT NULL,
                                cost REAL NOT NULL DEFAULT 0.0,
                                price REAL NOT NULL DEFAULT 0.0,
                                type TEXT NOT NULL DEFAULT '',
                                brand TEXT NOT NULL DEFAULT '',
                                size TEXT NOT NULL DEFAULT '',
                                unit TEXT NOT NULL DEFAULT 'pcs',
                                mrp REAL,
                                qty REAL NOT NULL DEFAULT 0.0,
                                aliases TEXT NOT NULL DEFAULT '',
                                low REAL NOT NULL DEFAULT 0.0,
                                updated_at TEXT NOT NULL DEFAULT ''
                            )
                            """.trimIndent()
                        )
                        db.execSQL("INSERT INTO items (id, name, qty) VALUES ('it-mig-1', 'Primer 4L', 12.0)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                }).build()
        )
        helperV1.writableDatabase.use { db ->
            AppDatabase.MIGRATION_1_2.migrate(db)
            AppDatabase.MIGRATION_2_3.migrate(db)
            AppDatabase.MIGRATION_3_4.migrate(db)
            AppDatabase.MIGRATION_4_5.migrate(db)
            AppDatabase.MIGRATION_5_6.migrate(db)
            AppDatabase.MIGRATION_6_7.migrate(db)
            AppDatabase.MIGRATION_7_8.migrate(db)
            AppDatabase.MIGRATION_8_9.migrate(db)

            db.query("SELECT name, qty FROM items WHERE id = 'it-mig-1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Primer 4L", c.getString(0))
                assertEquals(12.0, c.getDouble(1), 0.001)
            }
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='supplier_purchases'").use { c ->
                assertFalse("MIGRATION_7_8 should drop supplier_purchases", c.moveToFirst())
            }
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='audit_logs'").use { c ->
                assertTrue("MIGRATION_8_9 should create audit_logs", c.moveToFirst())
            }
        }
        helperV1.close()
        context.deleteDatabase(dbName)
    }

    // ------------------------------------------------------------------
    // 5. deleteAllData queues remote clears, upsertCashflow empty PATCH->POST, clearTransactions client_id
    // ------------------------------------------------------------------
    @Test
    fun `deleteAllData enqueues all remote clear operations and upsertCashflow handles empty PATCH`() = runTest {
        val methodsAndPaths = mutableListOf<String>()
        val client = buildMockClient { req, _ ->
            methodsAndPaths.add("${req.method} ${req.url.encodedPath}?${req.url.encodedQuery.orEmpty()}")
            when {
                req.url.encodedPath.endsWith("/daily_cashflow") && req.url.encodedQuery?.contains("on_conflict") == true ->
                    400 to """{"code":"42P10","message":"no unique constraint"}"""
                req.method == "PATCH" && req.url.encodedPath.endsWith("/daily_cashflow") -> 200 to "[]"
                req.method == "POST" && req.url.encodedPath.endsWith("/daily_cashflow") -> 201 to ""
                req.method == "DELETE" && req.url.encodedQuery == "id=not.is.null" &&
                    req.url.encodedPath.endsWith("/transactions") -> 400 to """{"message":"column id does not exist"}"""
                else -> 200 to "[]"
            }
        }

        // upsertCashflow empty PATCH triggers POST insert fallback
        client.upsertCashflow(
            listOf(
                DailyCashflowRecord(
                    id = "cf-patch-1",
                    type = "SALE",
                    amount = 200.0,
                    title = "Counter",
                    category = "Sale",
                    date = "2026-11-24",
                    createdAt = "2026-11-24T10:00:00Z"
                )
            )
        )
        assertTrue(methodsAndPaths.any { it.startsWith("PATCH /rest/v1/daily_cashflow") })
        assertTrue(methodsAndPaths.any { it.startsWith("POST /rest/v1/daily_cashflow") })

        // clearTransactions falls back to client_id=not.is.null
        methodsAndPaths.clear()
        client.clearTransactions()
        assertTrue(methodsAndPaths.any { it.contains("client_id=not.is.null") })

        // deleteAllData clears local tables & invokes all remote deletes
        val db = buildInMemoryDb()
        try {
            db.quotationDao().insert(
                QuotationRecord(id = "q1", quotationNo = "EST-1", customerName = "A", date = "2026-11-24", itemsJson = "[]", subtotal = 0.0, grandTotal = 0.0, createdAt = "2026-11-24T00:00:00Z")
            )
            db.dailyCashflowDao().insert(
                DailyCashflowRecord(id = "cf1", type = "SALE", amount = 100.0, category = "Sale", date = "2026-11-24", createdAt = "2026-11-24T00:00:00Z")
            )
            val failingClient = buildMockClient { _, _ -> 503 to "offline" }
            val repo = StockRepository(context, database = db, supabaseClient = failingClient, authManager = AuthManager(context))
            repo.deleteAllData()

            assertTrue(db.quotationDao().getAllQuotationsList().isEmpty())
            assertTrue(db.dailyCashflowDao().getAllCashflowList().isEmpty())
            val queuedOps = db.syncQueueDao().getAll().map { it.type }.toSet()
            assertTrue(queuedOps.containsAll(setOf("deleteAll", "deleteAllCashflow", "deleteAllQuotations", "deleteAllLedgerAccounts", "deleteAllLedgerEntries")))
        } finally {
            db.close()
        }
    }

    // ------------------------------------------------------------------
    // 6 & 7. reloadFromDatabase silent vs toast; confirmBill ISO date; processColorMachineImport
    // ------------------------------------------------------------------
    @Test
    fun `confirmBill normalizes dd-MM-yyyy date and color machine auto-create enqueues upsert`() = runTest {
        val db = buildInMemoryDb()
        try {
            val failingClient = buildMockClient { _, _ -> 503 to "offline" }
            val repo = StockRepository(context, database = db, supabaseClient = failingClient, authManager = AuthManager(context))

            repo.confirmBill(
                supplier = "Apex Paints",
                billNo = "INV-99",
                billDate = "24/11/2026",
                rows = listOf(BillRowData(name = "Thinner 1L", rate = 90.0, qty = 5.0)),
                paidInCash = true
            )

            val cf = db.dailyCashflowDao().getAllCashflowList().single()
            assertEquals("2026-11-24", cf.date)
            assertEquals("EXPENSE", cf.type)

            val tintRow = ParsedTintRow(
                tintRecordId = "tint-101",
                dealerCode = "D1",
                dealerName = "Store",
                machineType = "Corob",
                shadeCode = "S1",
                productName = "Tractor Emulsion",
                shadeName = "Ivory",
                baseCode = "W1",
                canFactor = "4 LIT",
                litersPerCan = 4.0,
                noOfCans = 1,
                totalLiters = 4.0,
                tintDateRaw = "24/11/2026",
                tintTimeRaw = "10:00",
                tintTimestampIso = "2026-11-24T04:30:00.000Z",
                tintDisplayDateTime = "24/11/2026 10:00",
                colorantUsed = "Y2",
                colorantQuantity = "20ml",
                deductQty = 2.0
            )
            val tintResult = repo.processColorMachineImport(
                rows = listOf(tintRow),
                manualItemMappings = emptyMap(),
                autoCreateMissing = true
            )
            assertTrue(tintResult.isSuccess)
            val ops = db.syncQueueDao().getAll().map { it.type }
            assertTrue("Expected auto-created tint item to enqueue upsert", ops.contains("upsert"))
            val tintCreatedItem = db.itemDao().getAllItemsList().first { it.code == "W1" }
            assertEquals(0.0, tintCreatedItem.qty, 0.001)
        } finally {
            db.close()
        }
    }

    @Test
    fun `reloadFromDatabase with showToast false emits no toast`() = runTest {
        val vm = StockViewModel(context)
        val emitted = mutableListOf<String>()
        val job = async { vm.toastEvent.collect { emitted.add(it) } }
        vm.reloadFromDatabase(showToast = false)
        Thread.sleep(150)
        job.cancel()
        assertTrue(emitted.none { it.contains("Reloaded") })
    }

    // ------------------------------------------------------------------
    // 8. DateTimeHelper UTC -> IST local date & time conversion
    // ------------------------------------------------------------------
    @Test
    fun `dateTimeHelper converts UTC midnight boundary accurately into local IST date and time`() {
        val ist = TimeZone.getTimeZone("Asia/Kolkata")
        val utcIso = "2026-11-24T19:00:00Z" // +5:30 = 2026-11-25 00:30 AM IST

        assertEquals("2026-11-25", DateTimeHelper.utcIsoToLocalDateKey(utcIso, ist))
        assertEquals("2026-11", DateTimeHelper.utcIsoToLocalMonthKey(utcIso, ist))
        val timeLabel = DateTimeHelper.utcIsoToLocalTimeLabel(utcIso, ist)
        assertTrue("Expected 12:30 AM in IST, got '$timeLabel'", timeLabel.startsWith("12:30", ignoreCase = true))
        assertEquals("2026-11-24", DateTimeHelper.normalizeDateToIso("24/11/2026"))
        assertEquals("2026-11-24", DateTimeHelper.normalizeDateToIso("2026-11-24T08:00:00Z"))
    }

    // ------------------------------------------------------------------
    // 9. UI: CashflowRowCard & ItemDetailScreen single-line badges/buttons at 320dp
    // ------------------------------------------------------------------
    @Test
    fun `cashflowRowCard keeps amount single-line on 320dp`() {
        composeRule.setContent {
            StockManagerTheme(darkTheme = false) {
                CashflowRowCard(
                    record = DailyCashflowRecord(
                        id = "cf-ui-1",
                        type = "SALE",
                        amount = 12450.50,
                        title = "Very Long Contractor Cashflow Title For Narrow Width Wrapping Check",
                        category = "Sale",
                        paymentMode = "UPI",
                        date = "2026-11-24",
                        note = "Paid in full at counter",
                        createdAt = "2026-11-24T10:00:00Z"
                    ),
                    onEdit = {},
                    onDelete = {}
                )
            }
        }
        composeRule.waitForIdle()
        val amountBounds = composeRule.onNodeWithText("+₹12,450.50", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Expected cashflow amount height (${amountBounds.height}) to remain single-line", amountBounds.height <= 56f)
    }

    @Test
    fun `itemDetailScreen keeps action buttons single-line on 320dp`() {
        clearSingletonDb()
        val db = AppDatabase.getDatabase(context)
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            db.itemDao().insertAll(
                listOf(Item(id = "det-1", name = "Asian Paints Apex 20L Bucket", qty = 8.0, low = 5.0, price = 3200.0, cost = 2600.0))
            )
        }
        val vm = StockViewModel(context)

        composeRule.setContent {
            StockManagerTheme(darkTheme = false) {
                ItemDetailScreen(
                    viewModel = vm,
                    itemId = "det-1",
                    onEditItem = {},
                    onOpenTransactionDialog = { _, _ -> }
                )
            }
        }
        composeRule.waitUntil(5000L) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            composeRule.onAllNodes(hasTestTag("detail_adjust_stock_button"), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        val adjustBtn = composeRule.onNodeWithTag("detail_adjust_stock_button", useUnmergedTree = true)
        val reorderBtn = composeRule.onNodeWithTag("detail_reorder_button", useUnmergedTree = true)
        adjustBtn.assertIsDisplayed()
        reorderBtn.assertIsDisplayed()
        val adjBounds = composeRule.onNodeWithText("Adjust Stock", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val reoBounds = composeRule.onNodeWithText("Reorder Stock", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Expected Adjust Stock label height (${adjBounds.height}) <= 56px", adjBounds.height <= 56f)
        assertTrue("Expected Reorder Stock label height (${reoBounds.height}) <= 56px", reoBounds.height <= 56f)
    }

    // ------------------------------------------------------------------
    // 10. UI: GstCalculatorScreen decimal qty input ("0.5") & adding rows without key crash
    // ------------------------------------------------------------------
    @Test
    fun `gstCalculatorScreen preserves decimal typing 0_5 and adds multiple rows without key crash`() {
        clearSingletonDb()
        val vm = StockViewModel(context)

        composeRule.setContent {
            StockManagerTheme(darkTheme = false) {
                GstCalculatorScreen(viewModel = vm)
            }
        }
        composeRule.waitForIdle()

        val listNode = composeRule.onAllNodes(hasScrollToIndexAction()).onFirst()
        listNode.performScrollToIndex(2)
        composeRule.waitForIdle()

        val addItemBtn = composeRule.onNodeWithText("Add Item", useUnmergedTree = true)
        addItemBtn.performClick()
        composeRule.waitForIdle()
        addItemBtn.performClick()
        composeRule.waitForIdle()

        listNode.performScrollToNode(hasTestTag("gst_row_qty_0"))
        composeRule.waitForIdle()
        val qtyField = composeRule.onNodeWithTag("gst_row_qty_0", useUnmergedTree = true)
        qtyField.performTextReplacement("0.5")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("0.5", useUnmergedTree = true).assertIsDisplayed()
    }

    // ------------------------------------------------------------------
    // 11. UI: HomeScreen dynamic greeting, period filter empty state, low stock nav filter
    // ------------------------------------------------------------------
    @Test
    fun `homeScreen displays saved supervisor name, empty period state, and sets low filter on tile click`() {
        clearSingletonDb()
        val db = AppDatabase.getDatabase(context)
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            db.itemDao().insertAll(
                listOf(Item(id = "low-1", name = "Flush Bolt", qty = 3.0, low = 5.0, price = 40.0))
            )
            db.transactionDao().insertAll(
                listOf(
                    com.example.data.model.TransactionRecord(
                        clientId = "old-tx-1",
                        itemId = "low-1",
                        itemName = "Flush Bolt",
                        action = "in",
                        qty = 3.0,
                        balance = 3.0,
                        note = "Old purchase",
                        createdAt = "2024-01-10T10:00:00Z"
                    )
                )
            )
        }
        val vm = StockViewModel(context)
        vm.saveStoreIdentity(
            storeName = "Metro Hardware",
            supervisorName = "Arif",
            roleTitle = "Manager",
            location = "Mumbai"
        )

        composeRule.setContent {
            StockManagerTheme(darkTheme = false) {
                HomeScreen(viewModel = vm)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Arif!", useUnmergedTree = true).assertIsDisplayed()
        val listNode = composeRule.onAllNodes(hasScrollToIndexAction()).onFirst()
        listNode.performScrollToNode(hasTestTag("home_overview_period_filter"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_overview_period_filter").assertIsDisplayed()
        composeRule.onNodeWithText("No transactions in selected period.", useUnmergedTree = true).assertIsDisplayed()

        listNode.performScrollToNode(hasTestTag("overview_low_stock"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("overview_low_stock").performClick()
        assertEquals(NavigationTab.ITEMS, vm.currentTab.value)
        assertEquals("low", vm.requestedStockStatusFilter.value)
    }

    // ------------------------------------------------------------------
    // 12. UI: ItemsScreen brand-only pill click & requested low-stock filter
    // ------------------------------------------------------------------
    @Test
    fun `itemsScreen brand-only category pill shows matching brand items and consumes requestedStockStatusFilter`() {
        clearSingletonDb()
        val db = AppDatabase.getDatabase(context)
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            db.itemDao().insertAll(
                listOf(
                    Item(id = "b-1", name = "Astral Coupler", type = "", brand = "Astral", qty = 2.0, low = 5.0, price = 30.0),
                    Item(id = "b-2", name = "Finolex Tee", type = "Plumbing", brand = "Finolex", qty = 50.0, low = 5.0, price = 40.0)
                )
            )
        }
        val vm = StockViewModel(context)
        vm.requestedStockStatusFilter.value = "low"

        composeRule.setContent {
            StockManagerTheme(darkTheme = false) {
                ItemsScreen(viewModel = vm, onOpenItemForm = {})
            }
        }
        composeRule.waitForIdle()

        // Low stock request applied -> Astral Coupler visible, Finolex Tee filtered out
        assertNull(vm.requestedStockStatusFilter.value)
        composeRule.onNodeWithText("Astral Coupler", useUnmergedTree = true).assertIsDisplayed()

        // Clicking brand-only pill "Astral" sets groupBy = "brand" and still displays Astral Coupler
        composeRule.onNodeWithTag("items_category_pill_astral").performClick()
        composeRule.waitForIdle()
        assertEquals("brand", vm.groupBy.value)
        composeRule.onNodeWithText("Astral Coupler", useUnmergedTree = true).assertIsDisplayed()
    }

    // ------------------------------------------------------------------
    // 13. CSV quoted commas, Settings wiring, Quick POS GST slabs, and Alarm next-day schedule
    // ------------------------------------------------------------------
    @Test
    fun `parseCsvItems handles quoted commas, AppSettingsManager increments invoice numbers, and scheduler arms tomorrow`() {
        val csv = "Code,Barcode,Name,Cost,Price,Type,Brand,Size,Unit,MRP,Qty,Aliases\n" +
            "SKU-1,890123,\"Valve, Brass 1/2\"\"\",100,150,Plumbing,Finolex,1/2,pcs,180,25,\"brass,valve\""
        val parsed = parseCsvItems(csv)
        assertEquals(1, parsed.size)
        assertEquals("Valve, Brass 1/2\"", parsed.first().name)
        assertEquals(100.0, parsed.first().cost, 0.001)
        assertEquals(150.0, parsed.first().price, 0.001)
        assertEquals("brass,valve", parsed.first().aliases)

        val settings = AppSettingsManager(context)
        settings.setInvoiceSequence("INV-", 2001)
        assertEquals("INV-2001", settings.previewNextInvoiceNo())
        assertEquals("INV-2001", settings.consumeNextInvoiceNo())
        assertEquals("INV-2002", settings.previewNextInvoiceNo())

        settings.setLowStockDefaultThreshold(15)
        val noCustomLowItem = Item(id = "x", name = "Nail 2\"", qty = 10.0, low = 0.0)
        assertTrue(settings.isLowStockOnly(noCustomLowItem))

        // Early wakeup before 19:00 must schedule tomorrow 19:00 via computeNextDayTriggerMillis
        val tz = TimeZone.getTimeZone("Asia/Kolkata")
        val earlyWakeCal = Calendar.getInstance(tz).apply {
            set(2026, Calendar.NOVEMBER, 24, 18, 59, 59)
            set(Calendar.MILLISECOND, 0)
        }
        val nextDayTrigger = DailyReminderScheduler.computeNextDayTriggerMillis(earlyWakeCal.timeInMillis, tz)
        val nextDayCal = Calendar.getInstance(tz).apply { timeInMillis = nextDayTrigger }
        assertEquals(25, nextDayCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(19, nextDayCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, nextDayCal.get(Calendar.MINUTE))
    }

    @Test
    fun `quickBillingPosScreen exposes selectable GST slabs including 0 and 12 percent`() {
        clearSingletonDb()
        val vm = StockViewModel(context)

        composeRule.setContent {
            StockManagerTheme(darkTheme = false) {
                QuickBillingPosScreen(viewModel = vm, isEmbeddedInHub = true)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pos_gst_slab_0").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("pos_gst_slab_12").assertIsDisplayed().performClick()
    }
}
