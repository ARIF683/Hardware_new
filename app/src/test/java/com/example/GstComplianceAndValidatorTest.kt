package com.example

import android.app.Application
import android.content.Context
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.AuthManager
import com.example.data.model.GstBillRecord
import com.example.data.pref.StoreProfilePreferenceManager
import com.example.data.remote.RemoteStoreProfile
import com.example.data.remote.StoreProfileSyncCodec
import com.example.ui.StockViewModel
import com.example.ui.screens.BankingAndSignatureContent
import com.example.ui.theme.StockManagerTheme
import com.example.util.GstinComplianceHelper
import com.example.util.Gstr1Bucket
import com.example.util.InvoicePrintManager
import com.example.util.StoreGstMode
import com.example.util.XlsExporter
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GstComplianceAndValidatorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        StoreProfilePreferenceManager(app).reloadFromPrefs()
    }

    @After
    fun tearDown() {
        app.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        StoreProfilePreferenceManager(app).reloadFromPrefs()
    }

    private fun validGstinFor(prefix14: String): String {
        val check = GstinComplianceHelper.computeCheckDigit(prefix14)
        requireNotNull(check) { "Invalid 14-char prefix: $prefix14" }
        return "$prefix14$check"
    }

    @Test
    fun `offline gstin validator checks mod36 checksum state code pan and interstate`() {
        val maharashtraGstin = validGstinFor("27AABCU9603R1Z")
        val delhiGstin = validGstinFor("07AABCU9603R1Z")

        val mhRes = GstinComplianceHelper.validateGstin("  $maharashtraGstin  ".lowercase())
        assertTrue(mhRes.isValid)
        assertEquals("27", mhRes.stateCode)
        assertEquals("Maharashtra", mhRes.stateName)
        assertEquals("AABCU9603R", mhRes.pan)
        assertNull(mhRes.errorReason)

        val dlRes = GstinComplianceHelper.validateGstin(delhiGstin)
        assertTrue(dlRes.isValid)
        assertEquals("07", dlRes.stateCode)
        assertEquals("Delhi", dlRes.stateName)

        // Corrupt last checksum character
        val wrongChar = if (maharashtraGstin.last() == 'A') 'B' else 'A'
        val badChecksum = maharashtraGstin.dropLast(1) + wrongChar
        val badRes = GstinComplianceHelper.validateGstin(badChecksum)
        assertFalse(badRes.isValid)
        assertTrue(badRes.errorReason?.contains("checksum", ignoreCase = true) == true)

        // Invalid Indian state code 99
        val badStatePrefix = "99AABCU9603R1Z"
        val badState = badStatePrefix + GstinComplianceHelper.computeCheckDigit(badStatePrefix)
        val badStateRes = GstinComplianceHelper.validateGstin(badState)
        assertFalse(badStateRes.isValid)
        assertTrue(badStateRes.errorReason?.contains("state code", ignoreCase = true) == true)

        // Short length & invalid PAN
        assertFalse(GstinComplianceHelper.validateGstin("27AABCU").isValid)
        assertFalse(GstinComplianceHelper.validateGstin("2712345678901Z5").isValid)

        // Intra-State vs Inter-State auto detection
        assertFalse(GstinComplianceHelper.resolveInterState(maharashtraGstin, "27", fallback = true))
        assertTrue(GstinComplianceHelper.resolveInterState(maharashtraGstin, delhiGstin, fallback = false))
        assertTrue(GstinComplianceHelper.resolveInterState("27", delhiGstin, fallback = false))
    }

    @Test
    fun `store profile sync codec and prefs roundtrip gst modes and clear gstin when unregistered`() {
        val manager = StoreProfilePreferenceManager(app)
        val validMhGstin = validGstinFor("27AABCS1234F1Z")

        manager.saveStoreGstConfig(
            gstMode = StoreGstMode.REGULAR.code,
            storeGstin = validMhGstin,
            storeStateCode = "27"
        )
        var identity = manager.reloadFromPrefs()
        assertEquals(StoreGstMode.REGULAR, identity.gstModeEnum)
        assertEquals(validMhGstin, identity.storeGstin)
        assertEquals("27", identity.effectiveStateCode)

        // Switch to Composition keeps GSTIN and state
        manager.saveStoreGstConfig(
            gstMode = StoreGstMode.COMPOSITION.code,
            storeGstin = validMhGstin,
            storeStateCode = "27"
        )
        identity = manager.reloadFromPrefs()
        assertEquals(StoreGstMode.COMPOSITION, identity.gstModeEnum)
        assertEquals(validMhGstin, identity.storeGstin)
        assertEquals("27", identity.effectiveStateCode)

        // Switch to Unregistered clears GSTIN but keeps home state code
        manager.saveStoreGstConfig(
            gstMode = StoreGstMode.UNREGISTERED.code,
            storeGstin = validMhGstin,
            storeStateCode = "29"
        )
        identity = manager.reloadFromPrefs()
        assertEquals(StoreGstMode.UNREGISTERED, identity.gstModeEnum)
        assertEquals("", identity.storeGstin)
        assertEquals("29", identity.effectiveStateCode)

        // Remote sync codec roundtrip
        val remote = RemoteStoreProfile(
            storeName = "Azim Hardware",
            storeGstMode = StoreGstMode.COMPOSITION.code,
            storeGstin = validMhGstin,
            storeStateCode = "27",
            updatedAt = 999L
        )
        val payload = StoreProfileSyncCodec.toStoreProfilePayload(remote, "owner@store.com")
        val decoded = StoreProfileSyncCodec.parseFromStoreProfileRow(payload)
        assertNotNull(decoded)
        assertEquals(StoreGstMode.COMPOSITION.code, decoded!!.storeGstMode)
        assertEquals(validMhGstin, decoded.storeGstin)
        assertEquals("27", decoded.storeStateCode)
    }

    @Test
    fun `rule 46 and section 269st cash limit helpers enforce exact legal thresholds`() {
        // Under 50,000 -> Rule 46 address not required
        assertFalse(GstinComplianceHelper.isRule46AddressRequired(49_999.0, "", StoreGstMode.REGULAR))
        // 50,000+ without customer GSTIN in REGULAR mode -> required
        assertTrue(GstinComplianceHelper.isRule46AddressRequired(50_000.0, "", StoreGstMode.REGULAR))
        // With customer GSTIN -> B2B already has GSTIN, B2C Rule 46 address prompt not triggered
        assertFalse(GstinComplianceHelper.isRule46AddressRequired(75_000.0, validGstinFor("27AABCU9603R1Z"), StoreGstMode.REGULAR))
        // Unregistered / Composition retail bills do not trigger Rule 46 GST tax-invoice block
        assertFalse(GstinComplianceHelper.isRule46AddressRequired(75_000.0, "", StoreGstMode.UNREGISTERED))
        assertFalse(GstinComplianceHelper.isRule46AddressRequired(75_000.0, "", StoreGstMode.COMPOSITION))

        assertNotNull(GstinComplianceHelper.rule46MissingFieldsMessage("Cash Customer", ""))
        assertNotNull(GstinComplianceHelper.rule46MissingFieldsMessage("Ramesh Verma", ""))
        assertNotNull(GstinComplianceHelper.rule46MissingFieldsMessage("Cash / Retail Customer", "Pune, MH"))
        assertNull(GstinComplianceHelper.rule46MissingFieldsMessage("Ramesh Verma", "MG Road, Pune, Maharashtra"))

        // Section 269ST: strictly >= 2,00,000
        assertFalse(GstinComplianceHelper.isCashLimitExceeded(199_999.0))
        assertTrue(GstinComplianceHelper.isCashLimitExceeded(200_000.0))
        assertTrue(GstinComplianceHelper.isCashLimitExceeded(350_000.0))
    }

    @Test
    fun `gstr1 classifier and excel exporter label b2b b2cl b2cs comp and non gst accurately`() {
        val storeGstin = validGstinFor("27AABCS1234F1Z")
        val buyerGstin = validGstinFor("27AABCU9603R1Z")

        val b2bBill = GstBillRecord(
            id = "b1", invoiceNo = "INV-1", invoiceDate = "2026-11-28",
            customerName = "Metro Builders", customerGstin = buyerGstin,
            customerAddress = "Mumbai, MH", storeGstin = storeGstin,
            isInterState = false, itemsJson = "[]",
            taxableTotal = 10_000.0, totalTaxAmount = 1_800.0, grandTotal = 11_800.0,
            createdAt = "2026-11-28T10:00:00Z"
        )
        val b2clBill = GstBillRecord(
            id = "b2", invoiceNo = "INV-2", invoiceDate = "2026-11-28",
            customerName = "Interstate Buyer", customerGstin = "",
            customerAddress = "Connaught Place, Delhi", storeGstin = storeGstin,
            isInterState = true, itemsJson = "[]",
            taxableTotal = 250_000.0, totalTaxAmount = 45_000.0, grandTotal = 295_000.0,
            createdAt = "2026-11-28T10:01:00Z"
        )
        val b2csBill = GstBillRecord(
            id = "b3", invoiceNo = "INV-3", invoiceDate = "2026-11-28",
            customerName = "Walk-in", customerGstin = "",
            customerAddress = "Pune", storeGstin = storeGstin,
            isInterState = false, itemsJson = "[]",
            taxableTotal = 1_000.0, totalTaxAmount = 180.0, grandTotal = 1_180.0,
            createdAt = "2026-11-28T10:02:00Z"
        )
        val compBill = GstBillRecord(
            id = "b4", invoiceNo = "BOS-1", invoiceDate = "2026-11-28",
            customerName = "Retail", storeGstin = storeGstin,
            itemsJson = "[]", taxableTotal = 5_000.0, totalTaxAmount = 0.0, grandTotal = 5_000.0,
            createdAt = "2026-11-28T10:03:00Z"
        )
        val nonGstBill = GstBillRecord(
            id = "b5", invoiceNo = "MEMO-1", invoiceDate = "2026-11-28",
            customerName = "Cash Customer", storeGstin = "",
            itemsJson = "[]", taxableTotal = 8_000.0, totalTaxAmount = 0.0, grandTotal = 8_000.0,
            createdAt = "2026-11-28T10:04:00Z"
        )

        assertEquals(Gstr1Bucket.B2B, GstinComplianceHelper.classifyGstr1Bucket(b2bBill, StoreGstMode.REGULAR))
        assertEquals(Gstr1Bucket.B2CL, GstinComplianceHelper.classifyGstr1Bucket(b2clBill, StoreGstMode.REGULAR))
        assertEquals(Gstr1Bucket.B2CS, GstinComplianceHelper.classifyGstr1Bucket(b2csBill, StoreGstMode.REGULAR))
        assertEquals(Gstr1Bucket.COMPOSITION, GstinComplianceHelper.classifyGstr1Bucket(compBill, StoreGstMode.COMPOSITION))
        assertEquals(Gstr1Bucket.NON_GST, GstinComplianceHelper.classifyGstr1Bucket(nonGstBill, StoreGstMode.UNREGISTERED))

        val xml = XlsExporter.buildGstr1WorkbookXml(
            bills = listOf(b2bBill, b2clBill, b2csBill, compBill),
            storeName = "S.A. Hardware",
            storeGstMode = StoreGstMode.REGULAR
        )
        assertTrue(xml.contains("GSTR-1 Bucket"))
        assertTrue(xml.contains("Customer Address"))
        assertTrue(xml.contains("B2B"))
        assertTrue(xml.contains("B2CL"))
        assertTrue(xml.contains("B2CS"))
        assertTrue(xml.contains("Connaught Place, Delhi"))
    }

    @Test
    fun `csv exporter and gst mode inference produce compliant headers and buckets across modes`() {
        assertEquals(StoreGstMode.REGULAR, GstinComplianceHelper.inferGstMode(validGstinFor("27AABCS1234F1Z"), 180.0))
        assertEquals(StoreGstMode.UNREGISTERED, GstinComplianceHelper.inferGstMode("", 0.0))
        assertEquals(StoreGstMode.COMPOSITION, GstinComplianceHelper.inferGstMode(validGstinFor("27AABCS1234F1Z"), 0.0, StoreGstMode.COMPOSITION))

        assertEquals("TAX INVOICE", StoreGstMode.REGULAR.docTitle)
        assertEquals("BILL OF SUPPLY", StoreGstMode.COMPOSITION.docTitle)
        assertEquals("RETAIL BILL / CASH MEMO", StoreGstMode.UNREGISTERED.docTitle)

        val storeGstin = validGstinFor("27AABCS1234F1Z")
        val buyerGstin = validGstinFor("07AABCU9603R1Z")
        val bills = listOf(
            GstBillRecord(
                id = "c1", invoiceNo = "INV-101", invoiceDate = "28/11/2026",
                customerName = "Delhi Infra", customerGstin = buyerGstin,
                customerAddress = "Sector 18, Rohini, Delhi", storeGstin = storeGstin,
                isInterState = true, itemsJson = "[]",
                taxableTotal = 10_000.0, totalTaxAmount = 1_800.0, grandTotal = 11_800.0,
                createdAt = "2026-11-28T10:00:00Z"
            ),
            GstBillRecord(
                id = "c2", invoiceNo = "INV-102", invoiceDate = "28/11/2026",
                customerName = "Walk-in", customerGstin = "",
                customerAddress = "Nashik, Maharashtra", storeGstin = storeGstin,
                isInterState = false, itemsJson = "[]",
                taxableTotal = 5_000.0, totalTaxAmount = 900.0, grandTotal = 5_900.0,
                createdAt = "2026-11-28T10:05:00Z"
            )
        )

        val regularCsv = InvoicePrintManager.buildGstr1CsvContent(bills, "S.A. Hardware", StoreGstMode.REGULAR)
        assertTrue(regularCsv.contains("GSTR-1 Bucket"))
        assertTrue(regularCsv.contains("Customer Address"))
        assertTrue(regularCsv.contains(",\"B2B\","))
        assertTrue(regularCsv.contains(",\"B2CS\","))
        assertTrue(regularCsv.contains("Sector 18, Rohini, Delhi"))
        assertTrue(regularCsv.contains("Inter-State (IGST)"))

        val compCsv = InvoicePrintManager.buildGstr1CsvContent(
            listOf(bills[1].copy(totalTaxAmount = 0.0, grandTotal = 5_000.0)),
            "S.A. Hardware",
            StoreGstMode.COMPOSITION
        )
        assertTrue(compCsv.contains(",\"COMP\","))
    }

    @Test
    fun `banking and gst settings ui validates gstin live and toggles unregistered mode`() {
        AuthManager.seedTestSessionForTest(app, "owner@store.com", isAdmin = true)
        val vm = StockViewModel(app)

        composeRule.setContent {
            StockManagerTheme {
                LazyColumn {
                    item {
                        BankingAndSignatureContent(viewModel = vm)
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val validGstin = validGstinFor("07AABCU9603R1Z")
        val invalidGstin = validGstin.dropLast(1) + (if (validGstin.last() == 'Z') 'A' else 'Z')

        composeRule.onNodeWithTag("store_gstin_input").performScrollTo()
        composeRule.onNodeWithTag("store_gstin_input").performTextReplacement(invalidGstin)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("store_gstin_error_text").assertIsDisplayed()

        composeRule.onNodeWithTag("store_gstin_input").performTextReplacement(validGstin)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("store_gstin_valid_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("store_gstin_valid_badge").onChild().assertTextContains("Delhi", substring = true)

        composeRule.onNodeWithTag("gst_mode_UNREGISTERED").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("store_gstin_input").assertDoesNotExist()
    }
}
