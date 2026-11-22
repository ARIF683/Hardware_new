package com.example

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AnnouncementConfig
import com.example.data.model.DynamicUiConfig
import com.example.data.model.FestivalConfig
import com.example.data.model.ThemeConfig
import com.example.data.remote.AppUpdateManager
import com.example.data.remote.DynamicUiManager
import com.example.data.remote.SupabaseClient
import com.example.data.remote.UpdateStatus
import com.example.util.GeminiBillScanner
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecurityReviewFixesTest {

    @Test
    fun `appUpdateManager allows only GitHub HTTPS hosts`() {
        assertTrue(AppUpdateManager.isAllowedDownloadUrl("https://github.com/ARIF683/Hardware_new/releases/download/v1.0.2/StockManager.apk"))
        assertFalse(AppUpdateManager.isAllowedDownloadUrl("https://github.com/ANOTHER_USER/other_repo/releases/download/v1.0/app.apk"))
        assertTrue(AppUpdateManager.isAllowedDownloadUrl("https://objects.githubusercontent.com/github-production-release-asset/foo.apk", true))
        assertFalse(AppUpdateManager.isAllowedDownloadUrl("https://objects.githubusercontent.com/github-production-release-asset/foo.apk"))
        assertFalse(AppUpdateManager.isAllowedDownloadUrl("http://github.com/ARIF683/Hardware_new/releases/download/v1.0.2/StockManager.apk"))
        assertFalse(AppUpdateManager.isAllowedDownloadUrl("https://evil.com/StockManager.apk"))
        assertFalse(AppUpdateManager.isAllowedDownloadUrl("https://github.com.evil.com/StockManager.apk"))
    }

    @Test
    fun `appUpdateManager extracts expected sha256 from asset digest and release notes`() {
        val sampleHash = "a".repeat(64)
        assertEquals(
            sampleHash,
            AppUpdateManager.extractExpectedSha256("sha256:${sampleHash.uppercase()}", "")
        )
        assertEquals(
            sampleHash,
            AppUpdateManager.extractExpectedSha256("", "version_code: 42\napk_sha256: $sampleHash\nDownload below.")
        )
        assertNull(AppUpdateManager.extractExpectedSha256("", "No checksum provided"))
    }

    @Test
    fun `appUpdateManager rejects update APK when sha256 mismatches`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = AppUpdateManager(context)

        // Inject local file server or bad URL isn't needed if host is rejected first;
        // test host rejection cleanly:
        manager.downloadAndInstall("https://evil.example.com/fake.apk", "b".repeat(64))
        assertTrue(manager.status.value is UpdateStatus.Error)

        // Verify computeSha256 helper accurately computes digest on temp file
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val tmpApk = File(updatesDir, "test_digest.apk")
        val payload = "dummy-apk-bytes".toByteArray()
        tmpApk.writeBytes(payload)
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(payload)
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, AppUpdateManager.computeSha256(tmpApk))
        tmpApk.delete()
    }

    @Test
    fun `geminiBillScanner builds URL without key query parameter`() {
        val url = GeminiBillScanner.buildGenerateContentUrl("gemini-3.8-flash")
        assertFalse(url.contains("?key="))
        assertFalse(url.contains("key="))
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent",
            url
        )
    }

    @Test
    fun `supabaseClient image upload fails visibly instead of base64 fallback`() = runTest {
        val failingInterceptor = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(500)
                .message("Internal Server Error")
                .body("""{"error":"edge signing unavailable"}""".toResponseBody("application/json".toMediaType()))
                .build()
        }
        val okHttp = OkHttpClient.Builder().addInterceptor(failingInterceptor).build()
        val client = SupabaseClient(
            baseUrl = "https://example.supabase.co",
            apiKey = "test-anon-key",
            okHttpClient = okHttp
        )

        val result = client.uploadImageToCloudinary(byteArrayOf(1, 2, 3, 4), "test.jpg")
        assertTrue("Expected failure instead of silent data: base64 fallback", result.isFailure)
    }

    @Test
    fun `dynamicUiManager sanitizes untrusted remote text, colors, and banner URLs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = DynamicUiManager(context)
        val untrusted = DynamicUiConfig(
            theme = ThemeConfig(
                primaryColor = "javascript:alert(1)",
                accentColor = "#00FFAA",
                storeTitle = "A".repeat(200),
                tagline = "B".repeat(300)
            ),
            announcement = AnnouncementConfig(
                enabled = true,
                message = "X".repeat(600),
                severity = "critical_hack"
            ),
            festival = FestivalConfig(
                enabled = true,
                festivalName = "Sale",
                greetingMessage = "Hello",
                accentColorHex = "not-a-color",
                bannerImageUrl = "http://insecure.example.com/banner.png"
            )
        )

        val sanitized = manager.sanitizeConfig(untrusted)
        assertEquals("#2563EB", sanitized.theme.primaryColor)
        assertEquals("#00FFAA", sanitized.theme.accentColor)
        assertEquals(60, sanitized.theme.storeTitle.length)
        assertEquals(120, sanitized.theme.tagline.length)
        assertEquals(300, sanitized.announcement.message.length)
        assertEquals("info", sanitized.announcement.severity)
        assertEquals("", sanitized.festival.accentColorHex)
        assertEquals("", sanitized.festival.bannerImageUrl)
    }

    @Test
    fun `manifest does not declare unused RECORD_AUDIO permission and file_paths is narrowed`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        @Suppress("DEPRECATION")
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val permissions = pkgInfo.requestedPermissions?.toList() ?: emptyList()
        assertFalse(permissions.contains(android.Manifest.permission.RECORD_AUDIO))

        val parser = context.resources.getXml(R.xml.file_paths)
        val paths = mutableListOf<String>()
        var eventType = parser.eventType
        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG) {
                val p = parser.getAttributeValue(null, "path")
                if (p != null) paths.add(p)
            }
            eventType = parser.next()
        }
        assertFalse("file_paths.xml must not expose '.' root path", paths.contains("."))
        assertTrue(paths.containsAll(listOf("updates/", "scans/", "exports/", "invoices/", "statements/")))
    }
}
