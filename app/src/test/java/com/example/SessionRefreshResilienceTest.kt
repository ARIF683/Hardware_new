package com.example

import android.app.Application
import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.AuthManager
import com.example.data.remote.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SessionRefreshResilienceTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("stock_manager_auth", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun fakeJwtWithExpSeconds(expEpochSeconds: Long): String {
        val header = Base64.encodeToString(
            """{"alg":"HS256","typ":"JWT"}""".toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        val payload = Base64.encodeToString(
            """{"sub":"user-123","exp":$expEpochSeconds}""".toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return "$header.$payload.sig"
    }

    private fun jsonResponse(request: okhttp3.Request, code: Int, body: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "ERR")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    @Test
    fun `concurrent 401 failures only refresh token once across threads and instances`() = runBlocking {
        val expiredJwt = fakeJwtWithExpSeconds(1_700_000_000L)
        val freshJwt = fakeJwtWithExpSeconds(4_000_000_000L)
        val refreshCalls = AtomicInteger(0)

        val sharedHttp = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                when {
                    url.contains("/auth/v1/token?grant_type=refresh_token") -> {
                        val callNum = refreshCalls.incrementAndGet()
                        // Slight delay increases contention across parallel callers
                        Thread.sleep(60)
                        if (callNum == 1) {
                            jsonResponse(
                                req,
                                200,
                                """{"access_token":"$freshJwt","refresh_token":"refresh-2","expires_in":3600}"""
                            )
                        } else {
                            // Simulate Supabase Refresh Token Reuse error if client ever double-refreshes
                            jsonResponse(
                                req,
                                400,
                                """{"error":"invalid_grant","error_description":"Invalid Refresh Token: Already Used"}"""
                            )
                        }
                    }
                    url.contains("/rest/v1/items") -> {
                        val authHeader = req.header("Authorization").orEmpty()
                        if (authHeader == "Bearer $freshJwt") {
                            jsonResponse(req, 200, "[]")
                        } else {
                            jsonResponse(req, 401, """{"message":"JWT expired"}""")
                        }
                    }
                    else -> jsonResponse(req, 404, "{}")
                }
            })
            .build()

        val authPrimary = AuthManager(app).apply {
            supabaseConfigOverrideForTests = "https://mock.supabase.co" to "anon-key"
            httpClient = sharedHttp
            seedTestSession(
                email = "owner@store.com",
                isAdmin = true,
                accessToken = expiredJwt,
                refreshToken = "refresh-1",
                expiresAtMillis = System.currentTimeMillis() - 10_000L
            )
        }
        // Second AuthManager instance (simulating DailyReminderScheduler / background worker)
        val authWorker = AuthManager(app).apply {
            supabaseConfigOverrideForTests = "https://mock.supabase.co" to "anon-key"
            httpClient = sharedHttp
        }

        val client = SupabaseClient(
            baseUrl = "https://mock.supabase.co",
            apiKey = "anon-key",
            okHttpClient = sharedHttp
        ).apply {
            configureAuth(
                accessTokenProvider = { authPrimary.activeAccessToken() },
                onRefreshSession = { authPrimary.refreshSessionBlocking() },
                onRefreshSessionWithToken = { failed -> authPrimary.refreshSessionBlocking(failed) }
            )
        }

        // Launch concurrent Supabase REST calls + concurrent background AuthManager refresh
        val job1 = async(Dispatchers.IO) { client.fetchAllItems() }
        val job2 = async(Dispatchers.IO) { client.fetchAllItems() }
        val job3 = async(Dispatchers.IO) { authWorker.refreshSessionBlocking(failedAccessToken = expiredJwt) }

        val res1 = job1.await()
        val res2 = job2.await()
        val res3 = job3.await()
        ShadowLooper.idleMainLooper()

        assertTrue(res1.isEmpty())
        assertTrue(res2.isEmpty())
        assertTrue(res3)
        assertEquals("Refresh endpoint must be hit exactly once across parallel 401s", 1, refreshCalls.get())
        assertEquals(freshJwt, authPrimary.activeAccessToken())
        assertEquals("refresh-2", authPrimary.activeRefreshToken())
        assertTrue(authPrimary.isAuthed.value)
    }

    @Test
    fun `proactive ensureValidSession refreshes near-expiry tokens and skips fresh tokens`() {
        val nowMs = 1_800_000_000_000L
        val nearExpiryJwt = fakeJwtWithExpSeconds((nowMs + 120_000L) / 1000L)
        val renewedJwt = fakeJwtWithExpSeconds((nowMs + 3_600_000L) / 1000L)
        val refreshCalls = AtomicInteger(0)

        val auth = AuthManager(app).apply {
            supabaseConfigOverrideForTests = "https://mock.supabase.co" to "anon-key"
            wallClockNowMs = { nowMs }
            httpClient = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    refreshCalls.incrementAndGet()
                    jsonResponse(
                        chain.request(),
                        200,
                        """{"access_token":"$renewedJwt","refresh_token":"refresh-new","expires_in":3600}"""
                    )
                })
                .build()
            seedTestSession(
                email = "owner@store.com",
                isAdmin = true,
                accessToken = nearExpiryJwt,
                refreshToken = "refresh-old"
            )
        }

        // Token expires in 2 mins (< 5-min leeway) -> should proactively refresh once
        assertTrue(auth.ensureValidSessionBlocking())
        assertEquals(1, refreshCalls.get())
        assertEquals(renewedJwt, auth.activeAccessToken())
        assertEquals(nowMs + 3_600_000L, auth.activeTokenExpiresAtMs())

        // Token is now valid for 60 mins -> subsequent proactive checks must make 0 network calls
        assertTrue(auth.ensureValidSessionBlocking())
        assertTrue(auth.refreshSessionBlocking())
        assertEquals(1, refreshCalls.get())
    }

    @Test
    fun `transient network failure during role verification does not force logout`() {
        val expiredJwt = fakeJwtWithExpSeconds(1_700_000_000L)
        val auth = AuthManager(app).apply {
            supabaseConfigOverrideForTests = "https://mock.supabase.co" to "anon-key"
            httpClient = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    val url = chain.request().url.toString()
                    when {
                        url.contains("/auth/v1/user") ->
                            jsonResponse(chain.request(), 401, """{"message":"JWT expired"}""")
                        url.contains("/auth/v1/token") ->
                            throw IOException("Simulated poor mobile network")
                        else ->
                            jsonResponse(chain.request(), 503, "{}")
                    }
                })
                .build()
            seedTestSession(
                email = "owner@store.com",
                isAdmin = true,
                accessToken = expiredJwt,
                refreshToken = "refresh-1",
                expiresAtMillis = System.currentTimeMillis() - 60_000L
            )
        }

        val latch = CountDownLatch(1)
        auth.refreshCurrentUserRole { _ -> latch.countDown() }
        val deadline = System.currentTimeMillis() + 3_000L
        while (latch.count > 0 && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(15)
        }
        ShadowLooper.idleMainLooper()

        assertTrue("Transient IOException on refresh must NOT log user out", auth.isAuthed.value)
    }

    @Test
    fun `genuine server refresh token revocation signs out and preserves last email`() {
        val auth = AuthManager(app).apply {
            supabaseConfigOverrideForTests = "https://mock.supabase.co" to "anon-key"
            httpClient = OkHttpClient.Builder()
                .addInterceptor(Interceptor { chain ->
                    jsonResponse(
                        chain.request(),
                        400,
                        """{"error":"invalid_grant","error_description":"Invalid Refresh Token: Expired"}"""
                    )
                })
                .build()
            seedTestSession(
                email = "owner@store.com",
                isAdmin = true,
                accessToken = "old-jwt",
                refreshToken = "revoked-refresh",
                expiresAtMillis = System.currentTimeMillis() - 5_000L
            )
        }

        val executor = Executors.newSingleThreadExecutor()
        val refreshed = executor.submit<Boolean> { auth.refreshSessionBlocking("old-jwt") }.get(5, TimeUnit.SECONDS)
        executor.shutdown()
        ShadowLooper.idleMainLooper()

        assertFalse(refreshed)
        assertFalse(auth.isAuthed.value)
        assertEquals("owner@store.com", auth.getLastSignedInEmail())
        assertTrue(auth.authDeepLinkError.value?.contains("expired", ignoreCase = true) == true)
    }
}
