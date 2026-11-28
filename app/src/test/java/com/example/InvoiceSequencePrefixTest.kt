package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.QuotationRecord
import com.example.data.pref.AppSettingsManager
import com.example.data.remote.RemoteStoreProfile
import com.example.data.remote.StoreProfileSyncCodec
import com.example.ui.StockViewModel
import com.example.ui.screens.GstCalculatorScreen
import com.example.ui.screens.SettingsInvoiceSequenceDialog
import com.example.ui.theme.StockManagerTheme
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
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class InvoiceSequencePrefixTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var context: Context

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        context = app
        context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `formatDocumentNo handles padding separators and Indian financial year rollovers`() {
        val nov2026 = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
            set(2026, Calendar.NOVEMBER, 15, 10, 0, 0)
        }.timeInMillis
        val feb2027 = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
            set(2027, Calendar.FEBRUARY, 10, 10, 0, 0)
        }.timeInMillis
        val apr2027 = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
            set(2027, Calendar.APRIL, 1, 9, 0, 0)
        }.timeInMillis

        assertEquals("26-27", AppSettingsManager.currentIndianFyToken(nov2026))
        assertEquals("26-27", AppSettingsManager.currentIndianFyToken(feb2027))
        assertEquals("27-28", AppSettingsManager.currentIndianFyToken(apr2027))

        assertEquals(
            "INV-26-27/0043",
            AppSettingsManager.formatDocumentNo("INV-", 43, padDigits = 4, includeFy = true, fyToken = "26-27")
        )
        assertEquals(
            "SAH/26-27/007",
            AppSettingsManager.formatDocumentNo("SAH/", 7, padDigits = 3, includeFy = true, fyToken = "26-27")
        )
        assertEquals(
            "BILL-26-27/1043",
            AppSettingsManager.formatDocumentNo("BILL", 1043, padDigits = 0, includeFy = true, fyToken = "26-27")
        )
    }

    @Test
    fun `preview and consume skip collisions and reconcile advances past synced bills`() {
        val settings = AppSettingsManager(context)
        settings.setInvoiceSequence(prefix = "INV-", nextNum = 10, padDigits = 4, includeFy = false)

        val existing = listOf("INV-0010", "inv-0011", "OTHER-999")
        assertEquals("INV-0012", settings.previewNextInvoiceNo(existing))
        assertEquals("INV-0012", settings.consumeNextInvoiceNo(existing))
        assertEquals("INV-0013", settings.previewNextInvoiceNo(existing))

        // Reconcile past higher synced bills & quotes
        settings.setQuoteSequence(prefix = "EST-", nextNum = 1001, padDigits = 0)
        val changed = settings.reconcileWithExistingDocuments(
            billNos = listOf("INV-0010", "INV-0025", "LEGACY-9999"),
            quoteNos = listOf("EST-1001", "EST-1019")
        )
        assertTrue(changed)
        assertEquals("INV-0026", settings.previewNextInvoiceNo())
        assertEquals("EST-1020", settings.previewNextQuoteNo())
    }

    @Test
    fun `store profile sync codec roundtrips and preserves newer sequence config during partial banner update`() {
        val remoteNewer = RemoteStoreProfile(
            storeName = "Metro Hardware",
            invoicePrefix = "GST-",
            invoiceNextNum = 250,
            invoicePadDigits = 4,
            invoiceIncludeFy = true,
            invoiceSeqUpdatedAt = 2000L,
            quotePrefix = "QT-",
            quoteNextNum = 88,
            quotePadDigits = 3,
            updatedAt = 2000L
        )
        val payload = StoreProfileSyncCodec.toStoreProfilePayload(remoteNewer, "admin@store.com")
        val parsed = StoreProfileSyncCodec.parseFromStoreProfileRow(payload)
        assertNotNull(parsed)
        assertEquals("GST-", parsed!!.invoicePrefix)
        assertEquals(250, parsed.invoiceNextNum)
        assertEquals(4, parsed.invoicePadDigits)
        assertEquals(true, parsed.invoiceIncludeFy)
        assertEquals("QT-", parsed.quotePrefix)
        assertEquals(88, parsed.quoteNextNum)
        assertEquals(3, parsed.quotePadDigits)

        // Partial banner push from another stale device (invoiceSeqUpdatedAt older) must NOT clobber GST-
        val staleDeviceBannerPush = RemoteStoreProfile(
            bannerUrl = "https://cdn.example.com/banner2.jpg",
            invoicePrefix = "BILL-",
            invoiceNextNum = 1043,
            invoicePadDigits = 0,
            invoiceIncludeFy = false,
            invoiceSeqUpdatedAt = 500L,
            updatedAt = 3000L
        )
        val merged = StoreProfileSyncCodec.merge(remoteNewer, staleDeviceBannerPush)
        assertEquals("https://cdn.example.com/banner2.jpg", merged.bannerUrl)
        assertEquals("GST-", merged.invoicePrefix)
        assertEquals(250, merged.invoiceNextNum)
        assertEquals(4, merged.invoicePadDigits)
        assertEquals(true, merged.invoiceIncludeFy)

        // Newer admin sequence apply updates local AppSettingsManager
        val settings = AppSettingsManager(context)
        settings.setInvoiceSequence("BILL-", 1043, padDigits = 0, includeFy = false, updatedAt = 500L)
        settings.applyRemoteSequenceProfile(remoteNewer)
        val fy = AppSettingsManager.currentIndianFyToken()
        assertEquals("GST-$fy/0250", settings.previewNextInvoiceNo())
        assertEquals("QT-088", settings.previewNextQuoteNo())
    }

    @Test
    fun `settings invoice sequence dialog updates live preview detects collisions and submits full config`() {
        var savedPrefix = ""
        var savedNext = 0
        var savedPad = -1
        var savedFy = false
        var savedQPrefix = ""
        var savedQNext = 0

        val fyToken = AppSettingsManager.currentIndianFyToken()

        composeRule.setContent {
            StockManagerTheme {
                SettingsInvoiceSequenceDialog(
                    initialPrefix = "BILL-",
                    initialNextNum = 1043,
                    initialPadDigits = 0,
                    initialIncludeFy = false,
                    initialQuotePrefix = "EST-",
                    initialNextQuoteNum = 1001,
                    existingInvoiceNos = listOf("GST-0007", "GST-0008"),
                    onSaveDetailed = { prefix, nextNum, padDigits, includeFy, qPrefix, qNext, _ ->
                        savedPrefix = prefix
                        savedNext = nextNum
                        savedPad = padDigits
                        savedFy = includeFy
                        savedQPrefix = qPrefix
                        savedQNext = qNext
                    },
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("invoice_seq_prefix_input").performTextReplacement("GST-")
        composeRule.onNodeWithTag("invoice_seq_next_input").performTextReplacement("7")
        composeRule.onNodeWithTag("invoice_seq_pad_4").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Existing collision on GST-0007 shows jump button; tapping Use #9 resolves it
        composeRule.onNodeWithTag("invoice_seq_preview_text").assertTextContains("GST-0007")
        composeRule.onNodeWithTag("invoice_seq_jump_available").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("invoice_seq_preview_text").assertTextContains("GST-0009")

        composeRule.onNodeWithTag("invoice_seq_fy_switch").performScrollTo().performClick()
        composeRule.onNodeWithTag("quote_seq_prefix_input").performScrollTo().performTextReplacement("QT-")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("invoice_seq_preview_text").assertTextContains("GST-$fyToken/0009")
        composeRule.onNodeWithTag("quote_seq_preview_text").assertTextContains("QT-1001")

        composeRule.onNodeWithTag("invoice_seq_save_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals("GST-", savedPrefix)
        assertEquals(9, savedNext)
        assertEquals(4, savedPad)
        assertTrue(savedFy)
        assertEquals("QT-", savedQPrefix)
        assertEquals(1001, savedQNext)
    }

    @Test
    fun `gstCalculatorScreen reacts to prefix changes and quotation conversion uses sequential prefix`() {
        val dbField = AppDatabase::class.java.getDeclaredField("INSTANCE")
        dbField.isAccessible = true
        dbField.set(null, null)

        val vm = StockViewModel(app)
        vm.appSettingsManager.setInvoiceSequence("INV-", 505, padDigits = 4, includeFy = false)

        composeRule.setContent {
            StockManagerTheme {
                GstCalculatorScreen(viewModel = vm)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("gst_invoice_no_input").assertTextContains("INV-0505")

        // Prefilling a quotation keeps quotation link without inventing GST-EST-... prefix
        val sampleQuote = QuotationRecord(
            id = "q-1",
            quotationNo = "EST-1012",
            customerName = "Ramesh Traders",
            customerPhone = "9876543210",
            customerAddress = "Sector 9",
            date = "2026-11-27",
            itemsJson = "[]",
            subtotal = 1000.0,
            discount = 0.0,
            taxPercent = 18.0,
            taxAmount = 180.0,
            grandTotal = 1180.0,
            status = "Accepted",
            notes = "",
            createdAt = "2026-11-27T10:00:00Z"
        )
        composeRule.runOnUiThread {
            vm.openGstCalculatorWithQuotation(sampleQuote)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Converted from Estimate #EST-1012").assertIsDisplayed()
        composeRule.onNodeWithTag("gst_invoice_no_input").assertTextContains("INV-0505")

        // Non-admin user must be blocked from changing invoice sequence via ViewModel
        assertFalse(vm.isAdmin.value)
        vm.setInvoiceSequence("HACK-", 1)
        assertEquals("INV-", vm.invoicePrefix.value)
        assertEquals(505, vm.nextInvoiceNum.value)
    }
}
