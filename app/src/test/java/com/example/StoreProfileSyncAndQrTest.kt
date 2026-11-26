package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.data.pref.BankingInfo
import com.example.data.pref.BankingPreferenceManager
import com.example.data.pref.StoreProfilePreferenceManager
import com.example.data.remote.RemoteStoreProfile
import com.example.data.remote.StoreProfileSyncCodec
import com.example.ui.components.SavedPaymentQrCard
import com.example.ui.screens.SettingsProfileEditDialog
import com.example.ui.theme.StockManagerTheme
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StoreProfileSyncAndQrTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `store profile codec roundtrips banking qr signature store name location and role title`() {
        val original = RemoteStoreProfile(
            bannerUrl = "https://cdn.example.com/banner.jpg",
            logoUrl = "https://cdn.example.com/logo.jpg",
            storeName = "S.A. Hardware & Paints",
            location = "Main Market, Sector 4",
            roleTitle = "Store Owner",
            supervisorName = "Arif Khan",
            bankName = "HDFC Bank",
            accountHolder = "S.A. Hardware",
            accountNumber = "50200012345678",
            ifscCode = "HDFC0001234",
            branchName = "Sector 4",
            upiId = "sahardware@okhdfcbank",
            signatureUrl = "https://cdn.example.com/sig.png",
            qrCodeUrl = "https://cdn.example.com/qr.png",
            updatedAt = 123456789L
        )

        val storeProfilePayload = StoreProfileSyncCodec.toStoreProfilePayload(original, "owner@store.com")
        val parsed = StoreProfileSyncCodec.parseFromStoreProfileRow(storeProfilePayload)

        assertNotNull(parsed)
        assertEquals(original.bannerUrl, parsed!!.bannerUrl)
        assertEquals(original.logoUrl, parsed.logoUrl)
        assertEquals(original.storeName, parsed.storeName)
        assertEquals(original.location, parsed.location)
        assertEquals(original.roleTitle, parsed.roleTitle)
        assertEquals(original.supervisorName, parsed.supervisorName)
        assertEquals(original.bankName, parsed.bankName)
        assertEquals(original.accountHolder, parsed.accountHolder)
        assertEquals(original.accountNumber, parsed.accountNumber)
        assertEquals(original.ifscCode, parsed.ifscCode)
        assertEquals(original.branchName, parsed.branchName)
        assertEquals(original.upiId, parsed.upiId)
        assertEquals(original.signatureUrl, parsed.signatureUrl)
        assertEquals(original.qrCodeUrl, parsed.qrCodeUrl)
        assertEquals(original.updatedAt, parsed.updatedAt)
    }

    @Test
    fun `store profile codec merge preserves existing remote fields during partial update`() {
        val remoteExisting = RemoteStoreProfile(
            storeName = "Bharat Hardware",
            location = "Station Road",
            roleTitle = "Manager",
            bankName = "SBI",
            accountNumber = "123456",
            upiId = "bharat@sbi",
            qrCodeUrl = "https://cdn.example.com/qr.png",
            signatureUrl = "https://cdn.example.com/sig.png",
            updatedAt = 100L
        )
        val bannerOnlyUpdate = RemoteStoreProfile(
            bannerUrl = "https://cdn.example.com/new-banner.jpg",
            updatedAt = 200L
        )

        val merged = StoreProfileSyncCodec.merge(remoteExisting, bannerOnlyUpdate)
        assertEquals("https://cdn.example.com/new-banner.jpg", merged.bannerUrl)
        assertEquals("Bharat Hardware", merged.storeName)
        assertEquals("Station Road", merged.location)
        assertEquals("Manager", merged.roleTitle)
        assertEquals("SBI", merged.bankName)
        assertEquals("https://cdn.example.com/qr.png", merged.qrCodeUrl)
        assertEquals("https://cdn.example.com/sig.png", merged.signatureUrl)
    }

    @Test
    fun `legacy banner only payload leaves banking and identity fields null so local data is untouched`() {
        val legacyLines = JSONArray().apply {
            put(JSONObject().apply {
                put("bannerUrl", "https://cdn.example.com/b.jpg")
                put("avatarUrl", "https://cdn.example.com/l.jpg")
            })
        }
        val parsed = StoreProfileSyncCodec.parseFromLines(legacyLines.toString())
        assertNotNull(parsed)
        assertEquals("https://cdn.example.com/b.jpg", parsed!!.bannerUrl)
        assertEquals("https://cdn.example.com/l.jpg", parsed.logoUrl)
        assertNull(parsed.bankName)
        assertNull(parsed.qrCodeUrl)
        assertNull(parsed.storeName)
    }

    @Test
    fun `remote banking qr signature and store identity persist to local managers`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bankManager = BankingPreferenceManager(context)
        val identityManager = StoreProfilePreferenceManager(context)

        val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val pngBytes = ByteArrayOutputStream().use { bos ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
            bos.toByteArray()
        }

        bankManager.applyRemoteBankDetails(
            bankName = "ICICI Bank",
            accountHolder = "Metro Tools",
            accountNumber = "99887766",
            ifscCode = "icic0000123",
            branchName = "City Center",
            upiId = "metrotools@icici"
        )
        val qrRes = bankManager.saveQrCodeFromBytes(pngBytes, "https://cdn.example.com/qr-sync.png")
        val sigRes = bankManager.saveSignatureFromBytes(pngBytes, "https://cdn.example.com/sig-sync.png")
        identityManager.applyRemoteStoreIdentity(
            storeName = "Metro Tools Co.",
            supervisorName = "Raza",
            roleTitle = "Branch Lead",
            location = "City Center Depot"
        )

        assertTrue(qrRes.isSuccess)
        assertTrue(sigRes.isSuccess)

        val reloadedBank = bankManager.reloadFromPrefs()
        assertEquals("ICICI Bank", reloadedBank.bankName)
        assertEquals("ICIC0000123", reloadedBank.ifscCode)
        assertEquals("metrotools@icici", reloadedBank.upiId)
        assertTrue(!reloadedBank.qrCodePath.isNullOrBlank() && File(reloadedBank.qrCodePath!!).exists())
        assertTrue(!reloadedBank.signaturePath.isNullOrBlank() && File(reloadedBank.signaturePath!!).exists())
        assertEquals("https://cdn.example.com/qr-sync.png", reloadedBank.qrCodeUrl)
        assertEquals("https://cdn.example.com/sig-sync.png", reloadedBank.signatureUrl)

        val reloadedIdentity = identityManager.reloadFromPrefs()
        assertEquals("Metro Tools Co.", reloadedIdentity.storeName)
        assertEquals("Branch Lead", reloadedIdentity.roleTitle)
        assertEquals("City Center Depot", reloadedIdentity.location)
    }

    @Test
    fun `saved payment qr card displays saved app qr and hides placeholder when unset`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val qrFile = File(context.filesDir, "test_saved_qr.png")
        val bmp = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        qrFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val savedBanking = BankingInfo(
            bankName = "HDFC Bank",
            accountNumber = "5020001234",
            upiId = "realstore@okhdfcbank",
            qrCodePath = qrFile.absolutePath
        )

        composeRule.setContent {
            StockManagerTheme {
                SavedPaymentQrCard(
                    bankingInfo = savedBanking,
                    amount = 2450.0
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("saved_payment_qr_image").assertIsDisplayed()
        composeRule.onNodeWithTag("saved_payment_upi_text").assertTextContains("realstore@okhdfcbank", substring = true)
    }

    @Test
    fun `profile edit dialog collects store name operator role title and location`() {
        var savedStore = ""
        var savedName = ""
        var savedRole = ""
        var savedDepot = ""

        composeRule.setContent {
            StockManagerTheme {
                SettingsProfileEditDialog(
                    initialStoreName = "Old Store",
                    initialName = "Shahazim",
                    initialRole = "Supervisor",
                    initialDepot = "Old Depot",
                    onSave = { store, name, role, depot ->
                        savedStore = store
                        savedName = name
                        savedRole = role
                        savedDepot = depot
                    },
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("profile_store_name_input").performTextReplacement("Apex Hardware Hub")
        composeRule.onNodeWithTag("profile_role_title_input").performTextReplacement("Managing Director")
        composeRule.onNodeWithTag("profile_location_input").performTextReplacement("Ring Road, Surat")
        composeRule.onNodeWithTag("profile_save_button").performClick()
        composeRule.waitForIdle()

        assertEquals("Apex Hardware Hub", savedStore)
        assertEquals("Shahazim", savedName)
        assertEquals("Managing Director", savedRole)
        assertEquals("Ring Road, Surat", savedDepot)
    }
}
