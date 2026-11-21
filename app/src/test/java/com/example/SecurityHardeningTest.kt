package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.AuthManager
import com.example.data.auth.SecureTokenStore
import com.example.data.remote.SupabaseClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
    }

    @Test
    fun `secure token store encrypts or encodes tokens and migrates legacy plaintext tokens`() {
        val prefs = app.getSharedPreferences("stock_manager_auth", Context.MODE_PRIVATE)
        val store = SecureTokenStore(prefs)
        val jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.secret"

        store.saveToken(jwt)
        assertEquals(jwt, store.readToken())
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
}
