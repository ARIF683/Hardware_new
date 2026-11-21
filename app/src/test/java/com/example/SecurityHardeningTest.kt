package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.AuthManager
import com.example.data.auth.SecureTokenStore
import com.example.data.remote.SupabaseClient
import com.example.util.GeminiBillScanner
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecurityHardeningTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("stock_manager_auth", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        app.getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `secure token store encrypts or encodes access and refresh tokens and migrates legacy plaintext`() {
        val prefs = app.getSharedPreferences("stock_manager_auth", Context.MODE_PRIVATE)
        val store = SecureTokenStore(prefs)
        val jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.secret"
        val refresh = "refresh_tok_abc_123"

        store.saveSessionTokens(jwt, refresh)
        assertEquals(jwt, store.readToken())
        assertEquals(refresh, store.readRefreshToken())
        // Ensure plaintext legacy preference key is not present
        assertNull(prefs.getString("supabase_access_token", null))
        val rawStored = prefs.getString("supabase_access_token_enc", null).orEmpty()
        assertFalse(rawStored.contains(jwt))

        // Verify legacy plaintext migration
        prefs.edit()
            .clear()
            .putString("supabase_access_token", jwt)
            .commit()
        assertEquals(jwt, store.readToken())
        assertNull(prefs.getString("supabase_access_token", null))

        store.clearToken()
        assertEquals("", store.readToken())
        assertEquals("", store.readRefreshToken())
    }

    @Test
    fun `gemini custom api key is stored encrypted and migrated from legacy plaintext`() {
        val prefs = app.getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)
        val customKey = "AIzaSyExampleKey1234567890"

        GeminiBillScanner.setSavedCustomApiKey(app, customKey)
        assertNull(prefs.getString("custom_gemini_api_key", null))
        assertEquals(customKey, GeminiBillScanner.getSavedCustomApiKey(app))

        // Verify legacy plaintext key is migrated and removed
        prefs.edit().clear().putString("custom_gemini_api_key", customKey).commit()
        assertEquals(customKey, GeminiBillScanner.getSavedCustomApiKey(app))
        assertNull(prefs.getString("custom_gemini_api_key", null))

        GeminiBillScanner.setSavedCustomApiKey(app, "")
        assertEquals("", GeminiBillScanner.getSavedCustomApiKey(app))
    }

    @Test
    fun `strict email validation rejects injection payloads and whitespace`() {
        assertTrue(AuthManager.isValidEmail("owner@store.com"))
        assertTrue(AuthManager.isValidEmail("staff+pos@hardware-shop.in"))

        assertFalse(AuthManager.isValidEmail("admin@store.com' OR 1=1--"))
        assertFalse(AuthManager.isValidEmail("user@store.com\nBcc:evil@x.com"))
        assertFalse(AuthManager.isValidEmail("notanemail"))
        assertFalse(AuthManager.isValidEmail("bad@localhost"))
        assertFalse(AuthManager.isValidEmail(""))
    }

    @Test
    fun `password validation requires at least 8 chars and preserves spaces without trimming`() {
        assertEquals(8, AuthManager.MIN_PASSWORD_LENGTH)
        assertFalse(AuthManager.isValidPassword("1234567"))
        assertFalse(AuthManager.isValidPassword("abc123"))
        assertTrue(AuthManager.isValidPassword("12345678"))
        assertTrue(AuthManager.isValidPassword(" pass 8 "))
    }

    @Test
    fun `resend otp cooldown blocks rapid repeated otp requests`() {
        val auth = AuthManager(app)
        auth.setResendCooldownForTest(System.currentTimeMillis() + 45_000L)
        assertTrue(auth.getResendCooldownRemainingSeconds() in 1..60)

        var resendOk = true
        var resendMsg: String? = null
        auth.resendConfirmationEmail("owner@store.com") { ok, msg ->
            resendOk = ok
            resendMsg = msg
        }
        assertFalse(resendOk)
        assertTrue(resendMsg?.contains("wait", ignoreCase = true) == true)

        var resetOk = true
        var resetMsg: String? = null
        auth.requestPasswordResetOtp("owner@store.com") { ok, msg ->
            resetOk = ok
            resetMsg = msg
        }
        assertFalse(resetOk)
        assertTrue(resetMsg?.contains("wait", ignoreCase = true) == true)
    }

    @Test
    fun `brute force lockout activates after 5 failed attempts and blocks sign in and otp`() {
        val auth = AuthManager(app)
        assertFalse(auth.isLocked())

        runBlocking {
            repeat(AuthManager.MAX_AUTH_TRIES - 1) {
                val hint = auth.recordFailedAuthAttempt()
                assertFalse(auth.isLocked())
                assertTrue(hint?.contains("left", ignoreCase = true) == true)
            }
            val lockMsg = auth.recordFailedAuthAttempt()
            assertTrue(auth.isLocked())
            assertTrue(lockMsg?.contains("Locked", ignoreCase = true) == true)
            assertTrue(auth.getRemainingSeconds() in 1..60)
        }

        var loginOk = true
        var loginMsg: String? = null
        auth.signInEmailPassword("user@store.com", "password123") { ok, msg ->
            loginOk = ok
            loginMsg = msg
        }
        assertFalse(loginOk)
        assertTrue(loginMsg?.contains("Too many failed attempts", ignoreCase = true) == true)

        var otpOk = true
        var otpMsg: String? = null
        auth.verifySignupOtp("user@store.com", "123456") { ok, msg ->
            otpOk = ok
            otpMsg = msg
        }
        assertFalse(otpOk)
        assertTrue(otpMsg?.contains("Too many failed attempts", ignoreCase = true) == true)

        runBlocking {
            auth.resetAuthFailures()
        }
        assertFalse(auth.isLocked())
        assertEquals(0, auth.getRemainingSeconds())
    }

    @Test
    fun `supabase client rejects insecure non-local http urls while allowing https and localhost`() {
        val remoteHttps = SupabaseClient(baseUrl = "https://abc.supabase.co", apiKey = "anon-key")
        assertTrue(remoteHttps.isConfigured())

        val remoteHttp = SupabaseClient(baseUrl = "http://abc.supabase.co", apiKey = "anon-key")
        assertFalse(remoteHttp.isConfigured())

        val localTest = SupabaseClient(baseUrl = "http://localhost:8080", apiKey = "anon-key")
        assertTrue(localTest.isConfigured())
    }

    @Test
    fun `supabase client sends user jwt bearer header and retries once on 401 after session refresh`() = runBlocking {
        val authHeaders = mutableListOf<String>()
        var currentJwt = "expired-user-jwt"
        val callCount = AtomicInteger(0)

        val interceptor = Interceptor { chain ->
            val req = chain.request()
            authHeaders.add(req.header("Authorization").orEmpty())
            val attempt = callCount.incrementAndGet()
            if (attempt == 1) {
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body("""{"message":"JWT expired"}""".toResponseBody("application/json".toMediaType()))
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

        val okHttp = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val client = SupabaseClient(
            baseUrl = "https://example.supabase.co",
            apiKey = "public-anon-key",
            okHttpClient = okHttp
        )
        client.configureAuth(
            accessTokenProvider = { currentJwt },
            onRefreshSession = {
                currentJwt = "fresh-user-jwt"
                true
            }
        )

        val items = client.fetchAllItems()
        assertTrue(items.isEmpty())
        assertEquals(listOf("Bearer expired-user-jwt", "Bearer fresh-user-jwt"), authHeaders)
    }

    @Test
    fun `unconfigured production fallback is disabled when requireConfiguredInTests is true`() {
        val auth = AuthManager(app)
        auth.requireConfiguredInTests = true
        assertFalse(auth.allowOfflineTestFallback())

        if (!auth.isSupabaseConfigured()) {
            var done = false
            var okResult = true
            var errResult: String? = null
            auth.signInEmailPassword("owner@store.com", "password123") { ok, msg ->
                okResult = ok
                errResult = msg
                done = true
            }
            val deadline = System.currentTimeMillis() + 3_000L
            while (!done && System.currentTimeMillis() < deadline) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(15)
            }
            assertTrue(done)
            assertFalse(okResult)
            assertTrue(errResult?.contains("not configured", ignoreCase = true) == true)
            assertFalse(auth.isAuthed.value)
        }
    }
}
