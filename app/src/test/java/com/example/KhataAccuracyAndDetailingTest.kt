package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.AuthManager
import com.example.data.local.AppDatabase
import com.example.data.model.GstBillLineItem
import com.example.data.model.GstBillRecord
import com.example.data.model.Item
import com.example.data.model.LedgerAccount
import com.example.data.model.LedgerEntry
import com.example.data.pref.BankingPreferenceManager
import com.example.data.remote.SupabaseClient
import com.example.data.repository.BillRowData
import com.example.data.repository.StockRepository
import com.example.ui.StockViewModel
import com.example.ui.screens.LedgerScreen
import com.example.ui.theme.StockManagerTheme
import com.example.util.DateTimeHelper
import com.example.util.InvoicePrintManager
import com.example.util.KhataAccountMeta
import com.example.util.KhataBalanceHelper
import com.example.util.KhataEntryMeta
import com.example.util.KhataMath
import com.example.util.KhataMetaCodec
import com.example.util.XlsExporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class KhataAccuracyAndDetailingTest {

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
        AuthManager.seedTestSessionForTest(app, "owner@store.com", isAdmin = true)
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

    private fun buildRepo(db: AppDatabase, isAdmin: Boolean = true): StockRepository {
        AuthManager.seedTestSessionForTest(app, if (isAdmin) "owner@store.com" else "staff@store.com", isAdmin = isAdmin)
        val auth = AuthManager(app)
        val supabase = SupabaseClient("", "")
        return StockRepository(app, db, supabase, auth)
    }

    private fun buildTestDb(): AppDatabase =
        Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    // ------------------------------------------------------------------
    // 1. Opening Balance Preservation & Chronological Backdated Recalculation
    // ------------------------------------------------------------------
    @Test
    fun `opening balance is preserved across entry edits and backdated entries recalculate chronologically`() = runTest {
        val db = buildTestDb()
        try {
            val repo = buildRepo(db)
            val acc = LedgerAccount(
                id = "cust-1",
                name = "Ramesh Builders",
                phone = "9876543210",
                type = "CUSTOMER",
                netBalance = 1500.55,
                creditLimit = 5000.0,
                createdAt = "2026-11-01T10:00:00Z",
                updatedAt = "2026-11-01T10:00:00Z"
            )
            repo.saveLedgerAccount(acc)

            // Add entry on Nov 10
            repo.addLedgerEntry("cust-1", "GAVE", 500.25, "2026-11-10", "Cement bags", "INV-10", paymentMode = "Credit Bill")
            // Backdate an entry on Nov 05
            repo.addLedgerEntry("cust-1", "GOT", 200.10, "2026-11-05", "Part UPI", "", paymentMode = "UPI", paymentRef = "UTR999")

            val entriesAsc = db.ledgerDao().getEntriesForAccountOnce("cust-1")
                .sortedWith(compareBy({ it.date }, { it.createdAt }))
            assertEquals(3, entriesAsc.size)
            assertTrue(KhataMetaCodec.parseEntryMeta(entriesAsc[0].description).isOpeningBalance)
            assertEquals(1500.55, entriesAsc[0].balanceAfter, 0.001)
            assertEquals("2026-11-05", entriesAsc[1].date)
            assertEquals(1300.45, entriesAsc[1].balanceAfter, 0.001)
            assertEquals("2026-11-10", entriesAsc[2].date)
            assertEquals(1800.70, entriesAsc[2].balanceAfter, 0.001)

            // Edit Nov 05 payment from 200.10 -> 300.10 and verify opening balance remains intact!
            repo.updateLedgerEntry(entriesAsc[1].copy(amount = 300.10))
            val updatedAcc = db.ledgerDao().getAccountById("cust-1")!!
            assertEquals(1700.70, updatedAcc.netBalance, 0.001)

            // Delete Nov 10 entry and verify balance drops cleanly to 1500.55 - 300.10 = 1200.45
            repo.deleteLedgerEntry(entriesAsc[2].id, "cust-1")
            val finalAcc = db.ledgerDao().getAccountById("cust-1")!!
            assertEquals(1200.45, finalAcc.netBalance, 0.001)
        } finally {
            db.close()
        }
    }

    // ------------------------------------------------------------------
    // 2. Supplier confirmBill settles Khata when paidInCash = true
    // ------------------------------------------------------------------
    @Test
    fun `confirmBill settles supplier Khata when paid in cash and records payable when on credit`() = runTest {
        val db = buildTestDb()
        try {
            val repo = buildRepo(db)
            // Paid in cash bill -> should net to 0.0 in Supplier Khata AND add expense in DailyCashflow
            repo.confirmBill(
                supplier = "Apex Pipes",
                billNo = "PUR-101",
                billDate = "2026-11-20",
                rows = listOf(BillRowData(id = "r1", name = "PVC Pipe 2\"", rate = 250.0, qty = 4.0, unit = "pcs")),
                paidInCash = true
            )
            val supplierCash = db.ledgerDao().getAllAccountsList().first { it.name == "Apex Pipes" }
            assertEquals("SUPPLIER", supplierCash.type)
            assertEquals(0.0, supplierCash.netBalance, 0.001)
            val supplierCashEntries = db.ledgerDao().getEntriesForAccountOnce(supplierCash.id)
            assertEquals(2, supplierCashEntries.size) // 1 GOT purchase + 1 GAVE cash settlement
            assertEquals("GOT", supplierCashEntries.first().type)
            assertEquals(1000.0, supplierCashEntries.first().balanceAfter, 0.001)
            assertEquals("GAVE", supplierCashEntries.last().type)
            assertEquals(0.0, supplierCashEntries.last().balanceAfter, 0.001)
            val cashflows = db.dailyCashflowDao().getAllCashflow().first()
            assertEquals(1, cashflows.size)
            assertEquals(1000.0, cashflows[0].amount, 0.001)

            // Credit purchase bill -> payable increases by 2000.0
            repo.confirmBill(
                supplier = "Birla Steel",
                billNo = "PUR-102",
                billDate = "2026-11-21",
                rows = listOf(BillRowData(id = "r2", name = "TMT Rod", rate = 500.0, qty = 4.0, unit = "pcs")),
                paidInCash = false
            )
            val supplierCredit = db.ledgerDao().getAllAccountsList().first { it.name == "Birla Steel" }
            assertEquals(2000.0, supplierCredit.netBalance, 0.001)
        } finally {
            db.close()
        }
    }

    // ------------------------------------------------------------------
    // 3. GST / POS Split Checkout posts ONLY unpaid credit to Khata & cash to Cashflow
    // ------------------------------------------------------------------
    @Test
    fun `saveAndFinalizeGstBill split payment posts paid cash to cashflow and credit to customer Khata`() = runTest {
        val db = buildTestDb()
        try {
            val repo = buildRepo(db)
            val item = Item(id = "itm-1", o = 1, name = "Wall Primer", unit = "ltr", qty = 10.0, price = 200.0)
            db.itemDao().insert(item)

            val lines = listOf(
                GstBillLineItem(
                    id = "itm-1",
                    name = "Wall Primer",
                    hsn = "3208",
                    qty = 5.0,
                    rate = 200.0,
                    taxRatePercent = 0.0,
                    taxableAmount = 1000.0,
                    taxAmount = 0.0,
                    totalAmount = 1000.0,
                    unit = "ltr"
                )
            )
            val bill = GstBillRecord(
                id = "bill-split-1",
                invoiceNo = "INV-77",
                invoiceDate = "2026-11-25",
                customerName = "Metro Interiors",
                customerPhone = "9988776655",
                customerGstin = "27ABCDE1234F1Z2",
                customerAddress = "Sector 5",
                storeGstin = "27STORE0000F1Z5",
                isInterState = false,
                itemsJson = InvoicePrintManager.gstItemsToJson(lines),
                taxableTotal = 1000.0,
                totalTaxAmount = 0.0,
                grandTotal = 1000.0,
                status = "Partial Khata",
                createdAt = "2026-11-25T10:00:00Z"
            )

            val res = repo.saveAndFinalizeGstBill(
                bill = bill,
                lineItems = lines,
                deductStock = true,
                recordCashflowSale = true,
                paymentMode = "Split (Cash + Khata)",
                creditAmountToKhata = 600.0,
                creditDueDate = "2026-12-10"
            )
            assertTrue(res.isSuccess)

            // Cashflow logs only ₹400 paid now
            val cashflows = db.dailyCashflowDao().getAllCashflow().first()
            assertEquals(1, cashflows.size)
            assertEquals(400.0, cashflows[0].amount, 0.001)
            assertEquals("Cash", cashflows[0].paymentMode)

            // Khata customer created with ₹600 due and due date 2026-12-10
            val khataAcc = db.ledgerDao().getAllAccountsList().first { it.name == "Metro Interiors" }
            assertEquals(600.0, khataAcc.netBalance, 0.001)
            val entries = db.ledgerDao().getEntriesForAccountOnce(khataAcc.id)
            assertEquals(1, entries.size)
            assertEquals("INV-77", entries[0].billRef)
            val meta = KhataMetaCodec.parseEntryMeta(entries[0].description)
            assertEquals("Split Credit", meta.paymentMode)
            assertEquals("2026-12-10", meta.dueDate)
        } finally {
            db.close()
        }
    }

    // ------------------------------------------------------------------
    // 4. Optional Daily Cashflow auto-sync on Khata collection + Audit Trail
    // ------------------------------------------------------------------
    @Test
    fun `khata collection with cashflow sync logs daily cashflow and records audit logs`() = runTest {
        val db = buildTestDb()
        try {
            val repo = buildRepo(db)
            repo.saveLedgerAccount(
                LedgerAccount(id = "c2", name = "Sohan Traders", type = "CUSTOMER", netBalance = 1000.0, createdAt = "2026-11-20", updatedAt = "2026-11-20")
            )
            repo.addLedgerEntry(
                accountId = "c2",
                type = "GOT",
                amount = 450.0,
                date = "2026-11-26",
                description = "Received UPI",
                billRef = "INV-12",
                paymentMode = "UPI",
                paymentRef = "UTR4455",
                alsoRecordCashflow = true
            )

            val acc = db.ledgerDao().getAccountById("c2")!!
            assertEquals(550.0, acc.netBalance, 0.001)

            val cashflows = db.dailyCashflowDao().getAllCashflow().first()
            assertEquals(1, cashflows.size)
            assertEquals("SALE", cashflows[0].type)
            assertEquals(450.0, cashflows[0].amount, 0.001)
            assertEquals("UPI", cashflows[0].paymentMode)

            val entry = db.ledgerDao().getEntriesForAccountOnce("c2").first { it.type == "GOT" }
            repo.deleteLedgerEntry(entry.id, "c2")
            assertTrue(db.dailyCashflowDao().getAllCashflow().first().isEmpty())
            val audits = db.auditLogDao().getAllAuditLogs().first()
            assertTrue(audits.any { it.action == "DELETE_KHATA_ENTRY" })
        } finally {
            db.close()
        }
    }

    // ------------------------------------------------------------------
    // 5. Non-Admin Delete Guard & Profile Edit Preserves Net Balance
    // ------------------------------------------------------------------
    @Test
    fun `nonAdmin cannot delete Khata account and editing profile preserves netBalance`() {
        val db = buildTestDb()
        try {
            val adminRepo = buildRepo(db, isAdmin = true)
            runBlocking {
                adminRepo.saveLedgerAccount(
                    LedgerAccount(id = "c3", name = "Vikram", type = "CUSTOMER", netBalance = 750.0, createdAt = "2026-11-01", updatedAt = "2026-11-01")
                )
                // Updating profile notes/phone with netBalance=0 in DTO must preserve calculated 750.0
                adminRepo.saveLedgerAccount(
                    LedgerAccount(id = "c3", name = "Vikram Hardware", phone = "9999999999", type = "CONTRACTOR", netBalance = 0.0, creditLimit = 3000.0, createdAt = "2026-11-01", updatedAt = "2026-11-02")
                )
                val updated = db.ledgerDao().getAccountById("c3")!!
                assertEquals(750.0, updated.netBalance, 0.001)
                assertEquals(3000.0, updated.creditLimit, 0.001)
                // Changing type CONTRACTOR -> SUPPLIER recalculates running balance under supplier convention
                adminRepo.saveLedgerAccount(updated.copy(type = "SUPPLIER"))
                val asSupplier = db.ledgerDao().getAccountById("c3")!!
                assertEquals(-750.0, asSupplier.netBalance, 0.001)
                adminRepo.saveLedgerAccount(asSupplier.copy(type = "CONTRACTOR"))
                assertEquals(750.0, db.ledgerDao().getAccountById("c3")!!.netBalance, 0.001)
            }

            // Non-admin ViewModel attempt to delete must be blocked
            AuthManager.seedTestSessionForTest(app, "staff@store.com", isAdmin = false)
            val staffVm = StockViewModel(app)
            val singletonDb = AppDatabase.getDatabase(app)
            runBlocking {
                singletonDb.ledgerDao().insertAccount(
                    LedgerAccount(id = "protected-acc", name = "Protected Party", type = "CUSTOMER", netBalance = 500.0, createdAt = "2026-11-01", updatedAt = "2026-11-01")
                )
                staffVm.deleteLedgerAccount("protected-acc")
                kotlinx.coroutines.delay(150)
                assertNotNull(singletonDb.ledgerDao().getAccountById("protected-acc"))
            }
        } finally {
            db.close()
        }
    }

    // ------------------------------------------------------------------
    // 6. Multi-page PDF Statement + Formula-Safe XLS Statement Export
    // ------------------------------------------------------------------
    @Test
    fun `multiPage ledger statement pdf paginates 50 entries and xls sanitizes formulas`() {
        val acc = LedgerAccount(
            id = "cust-pdf",
            name = "Big Contractor",
            phone = "9811122233",
            type = "CONTRACTOR",
            netBalance = 5000.0,
            creditLimit = 10000.0,
            notes = KhataMetaCodec.encodeAccountMeta(KhataAccountMeta(cleanNotes = "Main client", gstin = "27GSTIN0001Z1")),
            createdAt = "2026-01-01",
            updatedAt = "2026-11-26"
        )
        val entries = (1..50).map { idx ->
            LedgerEntry(
                id = "e-$idx",
                accountId = "cust-pdf",
                type = "GAVE",
                amount = 100.0,
                balanceAfter = idx * 100.0,
                date = "2026-11-${((idx - 1) % 28 + 1).toString().padStart(2, '0')}",
                description = KhataMetaCodec.encodeEntryMeta(
                    KhataEntryMeta(
                        cleanDescription = if (idx == 1) "=HYPERLINK(\"http://evil\")" else "Supply batch #$idx",
                        paymentMode = "Credit Bill",
                        siteName = "Site Alpha"
                    )
                ),
                billRef = "B-$idx",
                createdAt = "2026-11-01T00:${idx.toString().padStart(2, '0')}:00Z"
            )
        }

        val fullPlan = InvoicePrintManager.buildLedgerStatementPlan(
            account = acc,
            entries = entries
        )
        assertEquals(50, fullPlan.rows.size)
        assertTrue("Expected multi-page plan for 50 entries, got ${fullPlan.totalPages}", fullPlan.totalPages >= 3)
        assertEquals(5000.0, fullPlan.periodGave, 0.001)
        assertEquals(5000.0, fullPlan.closingForPeriod, 0.001)
        assertTrue(fullPlan.rows.first().tags.contains("Site:Site Alpha"))

        // Date-filtered statement must compute opening balance brought forward accurately
        val filteredPlan = InvoicePrintManager.buildLedgerStatementPlan(
            account = acc,
            entries = entries,
            fromDate = "2026-11-15",
            toDate = "2026-11-28"
        )
        assertTrue(filteredPlan.openingForPeriod > 0.0)
        assertEquals(5000.0, filteredPlan.closingForPeriod, 0.001)

        val xlsXml = XlsExporter.buildLedgerStatementWorkbookXml(acc, entries, "Apex Hardware")
        assertTrue(xlsXml.contains("&apos;=HYPERLINK"))
        assertTrue(xlsXml.contains("Site Alpha"))
        assertTrue(xlsXml.contains("Status: YOU GET"))
    }

    // ------------------------------------------------------------------
    // 7. WhatsApp Reminder Helper & Context-Aware Status
    // ------------------------------------------------------------------
    @Test
    fun `whatsApp reminder and status helper adapt cleanly for customer overdue vs supplier payable`() {
        val cust = LedgerAccount(id = "1", name = "A1 Builders", type = "CUSTOMER", netBalance = 2400.0, createdAt = "2026-11-01", updatedAt = "2026-11-01")
        val overdueEntry = LedgerEntry(
            id = "e1",
            accountId = "1",
            type = "GAVE",
            amount = 2400.0,
            balanceAfter = 2400.0,
            date = "2026-11-01",
            description = KhataMetaCodec.encodeEntryMeta(KhataEntryMeta(cleanDescription = "Tiles", dueDate = "2026-11-10")),
            billRef = "INV-88",
            createdAt = "2026-11-01"
        )
        assertTrue(KhataBalanceHelper.isEntryOverdue(cust, overdueEntry, todayIso = "2026-11-27"))
        val reminder = KhataBalanceHelper.buildWhatsAppReminderText(cust, "Apex Store", "apex@upi", listOf(overdueEntry), todayIso = "2026-11-27")
        assertTrue(reminder.contains("Rs. 2400.00"))
        assertTrue(reminder.contains("Overdue since 2026-11-10 (#INV-88)"))
        assertTrue(reminder.contains("apex@upi"))

        val sup = LedgerAccount(id = "2", name = "JSW Steel", type = "SUPPLIER", netBalance = 1500.0, createdAt = "2026-11-01", updatedAt = "2026-11-01")
        val supStatus = KhataBalanceHelper.statusFor(sup.type, sup.netBalance)
        assertEquals("YOU PAY", supStatus.label)
        assertFalse(supStatus.isPositiveHealth)
    }

    // ------------------------------------------------------------------
    // 8. Robolectric UI Test: Separate Customer/Contractor/Supplier Tabs, Badges & Role Guard
    // ------------------------------------------------------------------
    @Test
    fun `ledgerScreen renders contractor tab, overdue badge, and hides delete for nonAdmin`() {
        AuthManager.seedTestSessionForTest(app, "staff@store.com", isAdmin = false)
        val db = AppDatabase.getDatabase(app)
        runBlocking {
            db.ledgerDao().insertAccount(
                LedgerAccount(
                    id = "c-ui",
                    name = "Kiran Electrical",
                    phone = "9000011111",
                    type = "CUSTOMER",
                    netBalance = 6000.0,
                    creditLimit = 5000.0,
                    createdAt = "2026-11-01",
                    updatedAt = "2026-11-01"
                )
            )
            db.ledgerDao().insertEntry(
                LedgerEntry(
                    id = "e-ui",
                    accountId = "c-ui",
                    type = "GAVE",
                    amount = 6000.0,
                    balanceAfter = 6000.0,
                    date = "2026-11-01",
                    description = KhataMetaCodec.encodeEntryMeta(KhataEntryMeta(cleanDescription = "Cables", dueDate = "2026-11-05")),
                    billRef = "INV-1",
                    createdAt = "2026-11-01"
                )
            )
            db.ledgerDao().insertAccount(
                LedgerAccount(id = "con-ui", name = "Mehta Civil", type = "CONTRACTOR", netBalance = 1200.0, createdAt = "2026-11-01", updatedAt = "2026-11-01")
            )
        }

        BankingPreferenceManager(app)
        val vm = StockViewModel(app)
        composeRule.setContent {
            StockManagerTheme {
                LedgerScreen(viewModel = vm)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("khata_customers_tab").assertIsDisplayed()
        composeRule.onNodeWithTag("khata_contractors_tab").assertIsDisplayed()
        composeRule.onNodeWithTag("khata_suppliers_tab").assertIsDisplayed()
        composeRule.onNodeWithText("OVERDUE").assertIsDisplayed()
        composeRule.onNodeWithText("LIMIT EXCEEDED").assertIsDisplayed()

        // Open detail screen as non-admin staff
        composeRule.onNodeWithTag("khata_account_card_c-ui").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("khata_edit_account_button").assertIsDisplayed()
        composeRule.onNodeWithTag("khata_delete_account_button").assertDoesNotExist()
        composeRule.onNodeWithText("OVER LIMIT").assertIsDisplayed()
    }
}
