package com.example

import com.example.data.model.Item
import com.example.data.remote.SupabaseClient
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SupabaseClientTest {

    @Test
    fun `upsertItems strips missing PGRST204 columns and caches them across retries`() = runTest {
        val missingColumns = setOf("conversion_factor", "secondary_price")
        val recordedBodies = mutableListOf<String>()

        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val buffer = Buffer()
            request.body?.writeTo(buffer)
            val bodyStr = buffer.readUtf8()
            recordedBodies.add(bodyStr)

            val arr = JSONArray(bodyStr)
            val firstObj = arr.getJSONObject(0)
            val missing = missingColumns.firstOrNull { firstObj.has(it) }

            if (missing != null) {
                val errJson =
                    """{"code":"PGRST204","details":null,"hint":null,"message":"Could not find the '$missing' column of 'items' in the schema cache"}"""
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(400)
                    .message("Bad Request")
                    .body(errJson.toResponseBody("application/json".toMediaType()))
                    .build()
            } else {
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(201)
                    .message("Created")
                    .body("".toResponseBody("application/json".toMediaType()))
                    .build()
            }
        }

        val okHttp = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val client = SupabaseClient(
            baseUrl = "https://example.supabase.co",
            apiKey = "test-key",
            okHttpClient = okHttp
        )

        val item = Item(
            id = "item-1",
            o = 1,
            name = "Hex Bolt M10",
            price = 12.0,
            qty = 50.0,
            conversionFactor = 24.0,
            secondaryPrice = 260.0
        )

        // First upsert discovers and strips conversion_factor + secondary_price
        client.upsertItems(listOf(item))
        assertEquals(3, recordedBodies.size)

        val acceptedFirstPayload = JSONArray(recordedBodies.last()).getJSONObject(0)
        assertFalse(acceptedFirstPayload.has("conversion_factor"))
        assertFalse(acceptedFirstPayload.has("secondary_price"))
        assertTrue(acceptedFirstPayload.has("id"))
        assertEquals("Hex Bolt M10", acceptedFirstPayload.getString("name"))

        // Second upsert succeeds immediately on first try without missing columns
        recordedBodies.clear()
        client.upsertItems(listOf(item.copy(qty = 45.0)))
        assertEquals(1, recordedBodies.size)
        val cachedPayload = JSONArray(recordedBodies.single()).getJSONObject(0)
        assertFalse(cachedPayload.has("conversion_factor"))
        assertFalse(cachedPayload.has("secondary_price"))
        assertEquals(45.0, cachedPayload.getDouble("qty"), 0.001)
    }

    @Test
    fun `patchItem strips missing PGRST204 column and retries`() = runTest {
        val recordedPatches = mutableListOf<String>()

        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val buffer = Buffer()
            request.body?.writeTo(buffer)
            val bodyStr = buffer.readUtf8()
            recordedPatches.add(bodyStr)

            val obj = JSONObject(bodyStr)
            if (obj.has("conversion_factor")) {
                val errJson =
                    """{"code":"PGRST204","details":null,"hint":null,"message":"Could not find the 'conversion_factor' column of 'items' in the schema cache"}"""
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(400)
                    .message("Bad Request")
                    .body(errJson.toResponseBody("application/json".toMediaType()))
                    .build()
            } else {
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(204)
                    .message("No Content")
                    .body("".toResponseBody("application/json".toMediaType()))
                    .build()
            }
        }

        val okHttp = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val client = SupabaseClient(
            baseUrl = "https://example.supabase.co",
            apiKey = "test-key",
            okHttpClient = okHttp
        )

        client.patchItem("item-1", """{"qty":30.0,"conversion_factor":12.0}""")
        assertEquals(2, recordedPatches.size)
        val retried = JSONObject(recordedPatches.last())
        assertFalse(retried.has("conversion_factor"))
        assertEquals(30.0, retried.getDouble("qty"), 0.001)
    }
}
