package com.example.data.remote

import android.util.Log
import com.example.data.model.ColorTintLog
import com.example.data.model.DailyCashflowRecord
import com.example.data.model.GstBillRecord
import com.example.data.model.Item
import com.example.data.model.LedgerAccount
import com.example.data.model.LedgerEntry
import com.example.data.model.QuotationRecord
import com.example.data.model.PurchaseRecord
import com.example.data.model.TransactionRecord
import com.example.util.DateTimeHelper
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class SupabaseClient(
    baseUrl: String = com.example.BuildConfig.SUPABASE_URL,
    apiKey: String = com.example.BuildConfig.SUPABASE_KEY,
    private val okHttpClient: OkHttpClient = defaultOkHttpClient()
) {
    private val baseUrl: String = baseUrl.trim().trim('"', '\'').trimEnd('/')
    private val apiKey: String = apiKey.trim().trim('"', '\'')
    private val realtimeSeq = AtomicLong(0L)
    companion object {
        private const val PAGE_SIZE = 1000
        private const val BATCH_SIZE = 150
        private const val MAX_IMAGE_DOWNLOAD_BYTES = 5 * 1024 * 1024

        private fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val tag = "SupabaseClient"
    @Volatile
    private var loggedUnconfigured = false
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val unsupportedItemColumns = ConcurrentHashMap.newKeySet<String>()
    private val pgrst204ColumnRegex = Regex("""Could not find the '([^']+)' column""")

    class SafeStringAdapter {
        @com.squareup.moshi.FromJson
        fun fromJson(reader: com.squareup.moshi.JsonReader): String {
            if (reader.peek() == com.squareup.moshi.JsonReader.Token.NULL) {
                reader.nextNull<Unit>()
                return ""
            }
            return reader.nextString()
        }

        @com.squareup.moshi.ToJson
        fun toJson(writer: com.squareup.moshi.JsonWriter, value: String?) {
            writer.value(value ?: "")
        }
    }

    val moshi: Moshi = Moshi.Builder()
        .add(SafeStringAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val itemListAdapter = moshi.adapter<List<Item>>(
        Types.newParameterizedType(List::class.java, Item::class.java)
    ).serializeNulls()
    private val txListAdapter = moshi.adapter<List<TransactionRecord>>(
        Types.newParameterizedType(List::class.java, TransactionRecord::class.java)
    ).serializeNulls()
    private val cashflowListAdapter = moshi.adapter<List<DailyCashflowRecord>>(
        Types.newParameterizedType(List::class.java, DailyCashflowRecord::class.java)
    ).serializeNulls()
    private val quotationListAdapter = moshi.adapter<List<QuotationRecord>>(
        Types.newParameterizedType(List::class.java, QuotationRecord::class.java)
    ).serializeNulls()
    private val ledgerAccountListAdapter = moshi.adapter<List<LedgerAccount>>(
        Types.newParameterizedType(List::class.java, LedgerAccount::class.java)
    ).serializeNulls()
    private val ledgerEntryListAdapter = moshi.adapter<List<LedgerEntry>>(
        Types.newParameterizedType(List::class.java, LedgerEntry::class.java)
    ).serializeNulls()
    private val purchaseAdapter = moshi.adapter(PurchaseRecord::class.java).serializeNulls()

    // WebSocket client with NO read timeout (0) so it never drops long-lived subscriptions
    private val wsHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private fun isHttpsOrLocalTestUrl(url: String): Boolean {
        if (url.startsWith("https://", ignoreCase = true)) return true
        return url.startsWith("http://localhost", ignoreCase = true) ||
            url.startsWith("http://127.0.0.1", ignoreCase = true)
    }

    fun isConfigured(): Boolean =
        isHttpsOrLocalTestUrl(baseUrl) &&
            !baseUrl.contains("YOUR_SUPABASE_URL") &&
            apiKey.isNotBlank() &&
            !apiKey.contains("YOUR_SUPABASE_KEY")

    @Volatile
    private var accessTokenProvider: (() -> String)? = null

    @Volatile
    private var onRefreshSession: (() -> Boolean)? = null

    @Volatile
    private var onRefreshSessionWithToken: ((String) -> Boolean)? = null

    fun configureAuth(
        accessTokenProvider: () -> String,
        onRefreshSession: (() -> Boolean)? = null,
        onRefreshSessionWithToken: ((String) -> Boolean)? = null
    ) {
        this.accessTokenProvider = accessTokenProvider
        this.onRefreshSession = onRefreshSession
        this.onRefreshSessionWithToken = onRefreshSessionWithToken
    }

    private fun activeBearerToken(): String {
        val userToken = accessTokenProvider?.invoke()?.trim().orEmpty()
        if (userToken.isBlank()) {
            throw IllegalStateException("Authenticated user session required.")
        }
        return userToken
    }

    private fun executeSupabaseCall(request: Request): Response {
        val sentToken = request.header("Authorization").orEmpty().removePrefix("Bearer ").trim()
        val firstResp = okHttpClient.newCall(request).execute()
        if (firstResp.code == 401 && sentToken.isNotBlank()) {
            val latestToken = accessTokenProvider?.invoke()?.trim().orEmpty()
            val refreshedOk = when {
                latestToken.isNotBlank() && latestToken != sentToken -> true
                onRefreshSessionWithToken != null -> onRefreshSessionWithToken?.invoke(sentToken) == true
                else -> onRefreshSession?.invoke() == true
            }
            if (refreshedOk) {
                firstResp.close()
                val refreshedReq = request.newBuilder()
                    .header("Authorization", "Bearer ${activeBearerToken()}")
                    .build()
                return okHttpClient.newCall(refreshedReq).execute()
            }
        }
        return firstResp
    }

    private fun newRequestBuilder(path: String): Request.Builder {
        if (!isConfigured() && !path.startsWith("http")) {
            throw IllegalStateException("Supabase credentials missing. Configure SUPABASE_URL and SUPABASE_KEY in Secrets.")
        }
        val url = if (path.startsWith("http")) path else "$baseUrl/rest/v1/$path"
        return Request.Builder()
            .url(url)
            .addHeader("apikey", apiKey)
            .header("Authorization", "Bearer ${activeBearerToken()}")
            .addHeader("Content-Type", "application/json")
    }

    suspend fun fetchAllItems(): List<Item> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Item>()
        var offset = 0
        while (true) {
            val req = newRequestBuilder("items?select=*&order=o.asc&limit=1000&offset=$offset")
                .get()
                .build()
            executeSupabaseCall(req).use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string() ?: "HTTP ${resp.code}"
                    throw Exception("Failed to load items: $err")
                }
                val body = resp.body?.string() ?: "[]"
                val page = itemListAdapter.fromJson(body) ?: emptyList()
                result.addAll(page)
                if (page.size < 1000) return@withContext result
            }
            offset += 1000
        }
        result
    }

    suspend fun fetchRecentTransactions(limit: Int = 500): List<TransactionRecord> = withContext(Dispatchers.IO) {
        val req = newRequestBuilder("transactions?select=*&order=created_at.desc&limit=$limit")
            .get()
            .build()
        executeSupabaseCall(req).use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string() ?: "HTTP ${resp.code}"
                throw Exception("Failed to load transactions: $err")
            }
            val body = resp.body?.string() ?: "[]"
            txListAdapter.fromJson(body) ?: emptyList()
        }
    }

    private fun extractMissingPgrst204Column(errorBody: String): String? {
        if (!errorBody.contains("PGRST204")) return null
        return pgrst204ColumnRegex.find(errorBody)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private fun stripUnsupportedColumns(obj: JSONObject) {
        for (col in unsupportedItemColumns) {
            obj.remove(col)
        }
    }

    suspend fun upsertItems(items: List<Item>): Unit = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext
        items.chunked(BATCH_SIZE).forEach { chunk ->
            val jsonArray = JSONArray(itemListAdapter.toJson(chunk))
            val maxAttempts = 20
            var chunkDone = false
            for (attempt in 1..maxAttempts) {
                if (unsupportedItemColumns.isNotEmpty()) {
                    for (i in 0 until jsonArray.length()) {
                        jsonArray.optJSONObject(i)?.let { stripUnsupportedColumns(it) }
                    }
                }
                val req = newRequestBuilder("items?on_conflict=id")
                    .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                    .post(jsonArray.toString().toRequestBody(jsonMediaType))
                    .build()
                executeSupabaseCall(req).use { resp ->
                    if (resp.isSuccessful) {
                        chunkDone = true
                    } else {
                        val err = resp.body?.string() ?: "HTTP ${resp.code}"
                        val missingCol = extractMissingPgrst204Column(err)
                        if (
                            missingCol != null &&
                            missingCol != "id" &&
                            missingCol != "name" &&
                            unsupportedItemColumns.add(missingCol)
                        ) {
                            Log.w(tag, "Supabase 'items' lacks column '$missingCol' (PGRST204); stripping and retrying (attempt $attempt)")
                        } else {
                            throw Exception("Failed to upsert items: $err")
                        }
                    }
                }
                if (chunkDone) break
            }
            if (!chunkDone) {
                throw Exception("Failed to upsert items chunk after $maxAttempts attempts")
            }
        }
    }

    suspend fun patchItem(id: String, patchJson: String): Unit = withContext(Dispatchers.IO) {
        val patchObj = JSONObject(patchJson)
        val encodedId = java.net.URLEncoder.encode(id, "UTF-8")
        val maxAttempts = 20
        for (attempt in 1..maxAttempts) {
            if (unsupportedItemColumns.isNotEmpty()) {
                stripUnsupportedColumns(patchObj)
            }
            if (patchObj.length() == 0) return@withContext
            val req = newRequestBuilder("items?id=eq.$encodedId")
                .addHeader("Prefer", "return=minimal")
                .patch(patchObj.toString().toRequestBody(jsonMediaType))
                .build()
            executeSupabaseCall(req).use { resp ->
                if (resp.isSuccessful) return@withContext
                val err = resp.body?.string() ?: "HTTP ${resp.code}"
                val missingCol = extractMissingPgrst204Column(err)
                if (
                    missingCol != null &&
                    missingCol != "id" &&
                    missingCol != "name" &&
                    unsupportedItemColumns.add(missingCol)
                ) {
                    Log.w(tag, "Supabase 'items' lacks column '$missingCol' during patch (PGRST204); stripping and retrying (attempt $attempt)")
                } else {
                    throw Exception("Failed to patch item $id: $err")
                }
            }
        }
    }

    suspend fun deleteItems(ids: List<String>): Unit = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        ids.chunked(BATCH_SIZE).forEach { chunk ->
            val idsFormatted = chunk.joinToString(",") { java.net.URLEncoder.encode(it, "UTF-8") }
            val req = newRequestBuilder("items?id=in.($idsFormatted)")
                .delete()
                .build()
            executeSupabaseCall(req).use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string() ?: "HTTP ${resp.code}"
                    throw Exception("Failed to delete items: $err")
                }
            }
        }
    }

    suspend fun deleteAllItems(): Unit = withContext(Dispatchers.IO) {
        val req = newRequestBuilder("items?id=not.is.null")
            .delete()
            .build()
        executeSupabaseCall(req).use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string() ?: "HTTP ${resp.code}"
                throw Exception("Failed to delete all items: $err")
            }
        }
    }

    suspend fun insertTransactions(transactions: List<TransactionRecord>): Unit = withContext(Dispatchers.IO) {
        if (transactions.isEmpty()) return@withContext
        val json = txListAdapter.toJson(transactions)

        // 1. Try plain POST first (standard PostgREST insert without on_conflict to avoid Postgres 42P10 error)
        var req = newRequestBuilder("transactions")
            .addHeader("Prefer", "return=minimal")
            .post(json.toRequestBody(jsonMediaType))
            .build()
        var firstErr = ""
        executeSupabaseCall(req).use { resp ->
            if (resp.isSuccessful) return@withContext
            firstErr = resp.body?.string() ?: "HTTP ${resp.code}"
        }
        Log.w(tag, "First attempt to insert transactions failed: $firstErr. Retrying with fallback payload...")

        // 2. Fallback: If table has id column instead of or in addition to client_id
        try {
            val jsonArray = org.json.JSONArray()
            for (tx in transactions) {
                val obj = org.json.JSONObject().apply {
                    put("id", tx.clientId)
                    put("client_id", tx.clientId)
                    if (tx.itemId != null) put("item_id", tx.itemId)
                    put("item_name", tx.itemName)
                    put("action", tx.action)
                    put("qty", tx.qty)
                    put("balance", tx.balance)
                    put("note", tx.note)
                    put("unit", tx.unit)
                    put("created_at", tx.createdAt)
                }
                jsonArray.put(obj)
            }
            req = newRequestBuilder("transactions")
                .addHeader("Prefer", "return=minimal")
                .post(jsonArray.toString().toRequestBody(jsonMediaType))
                .build()
            executeSupabaseCall(req).use { resp ->
                if (resp.isSuccessful) return@withContext
            }

            // 3. Fallback: Strip client_id completely if Supabase table does not have client_id column
            val jsonArrayNoClientId = org.json.JSONArray()
            for (tx in transactions) {
                val obj = org.json.JSONObject().apply {
                    if (tx.itemId != null) put("item_id", tx.itemId)
                    put("item_name", tx.itemName)
                    put("action", tx.action)
                    put("qty", tx.qty)
                    put("balance", tx.balance)
                    put("note", tx.note)
                    put("unit", tx.unit)
                    put("created_at", tx.createdAt)
                }
                jsonArrayNoClientId.put(obj)
            }
            req = newRequestBuilder("transactions")
                .addHeader("Prefer", "return=minimal")
                .post(jsonArrayNoClientId.toString().toRequestBody(jsonMediaType))
                .build()
            executeSupabaseCall(req).use { resp ->
                if (resp.isSuccessful) return@withContext
                val finalErr = resp.body?.string() ?: "HTTP ${resp.code}"
                throw Exception("Failed to insert transactions: $finalErr")
            }
        } catch (e: Exception) {
            throw Exception("Failed to insert transactions: ${e.message ?: firstErr}")
        }
    }

    suspend fun clearTransactions(): Unit = withContext(Dispatchers.IO) {
        val reqId = newRequestBuilder("transactions?id=not.is.null")
            .delete()
            .build()
        val firstFailedMsg = executeSupabaseCall(reqId).use { resp ->
            if (resp.isSuccessful) return@withContext
            resp.body?.string() ?: "HTTP ${resp.code}"
        }
        val reqClientId = newRequestBuilder("transactions?client_id=not.is.null")
            .delete()
            .build()
        executeSupabaseCall(reqClientId).use { resp ->
            if (!resp.isSuccessful) {
                val secondErr = resp.body?.string() ?: "HTTP ${resp.code}"
                throw Exception("Failed to clear transactions: $firstFailedMsg / $secondErr")
            }
        }
    }

    suspend fun insertPurchase(purchase: PurchaseRecord): Unit = withContext(Dispatchers.IO) {
        val json = purchaseAdapter.toJson(purchase)
        val req = newRequestBuilder("purchases")
            .addHeader("Prefer", "return=minimal")
            .post(json.toRequestBody(jsonMediaType))
            .build()
        executeSupabaseCall(req).use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string() ?: "HTTP ${resp.code}"
                throw Exception("Failed to record purchase: $err")
            }
        }
    }

    suspend fun fetchAllCashflow(): List<DailyCashflowRecord> = withContext(Dispatchers.IO) {
        val list = mutableListOf<DailyCashflowRecord>()
        var offset = 0
        while (true) {
            val req = newRequestBuilder("daily_cashflow?select=*&order=date.desc,id.asc&limit=$PAGE_SIZE&offset=$offset")
                .get()
                .build()
            executeSupabaseCall(req).use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string() ?: "HTTP ${resp.code}"
                    throw Exception("Failed to fetch cashflow: $err")
                }
                val body = resp.body?.string() ?: "[]"
                val jsonArr = JSONArray(body)
                for (i in 0 until jsonArr.length()) {
                    list.add(parseCashflowFromDb(jsonArr.getJSONObject(i)))
                }
                if (jsonArr.length() < PAGE_SIZE) return@withContext list
            }
            offset += PAGE_SIZE
        }
        list
    }

    suspend fun upsertCashflow(records: List<DailyCashflowRecord>): Unit = withContext(Dispatchers.IO) {
        if (records.isEmpty()) return@withContext

        var includeTitle = true
        var includePaymentMode = true

        fun buildPayload(rec: DailyCashflowRecord, incTitle: Boolean, incPaymentMode: Boolean): JSONObject {
            val cleanType = rec.type.uppercase()
            val cleanTitle = rec.title.ifBlank { rec.category.ifBlank { if (cleanType == "SALE") "Sale" else "Expense" } }
            val cleanCategory = rec.category.ifBlank { cleanTitle }
            return JSONObject().apply {
                put("id", rec.id)
                put("date", rec.date.ifEmpty { DateTimeHelper.localDateKey() })
                put("type", cleanType)
                if (incTitle) {
                    put("title", cleanTitle)
                }
                put("category", cleanCategory)
                put("amount", rec.amount)
                if (incPaymentMode) {
                    put("payment_mode", rec.paymentMode.ifBlank { "Cash" })
                }
                put("note", rec.note)
                put("created_at", rec.createdAt.ifEmpty { DateTimeHelper.nowIso() })
            }
        }

        fun makeJsonArray(incTitle: Boolean, incPaymentMode: Boolean): JSONArray {
            val jsonArray = JSONArray()
            for (rec in records) {
                jsonArray.put(buildPayload(rec, incTitle, incPaymentMode))
            }
            return jsonArray
        }

        try {
            // 1. Try batch UPSERT with title and payment_mode on_conflict=id
            var req = newRequestBuilder("daily_cashflow?on_conflict=id")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(makeJsonArray(includeTitle, includePaymentMode).toString().toRequestBody(jsonMediaType))
                .build()
            var err1 = ""
            executeSupabaseCall(req).use { resp ->
                if (resp.isSuccessful) return@withContext
                err1 = resp.body?.string() ?: "HTTP ${resp.code}"
            }
            Log.w(tag, "Upsert cashflow batch attempt 1 failed: $err1")

            if (err1.contains("PGRST204")) {
                if (err1.contains("title")) includeTitle = false
                if (err1.contains("payment")) includePaymentMode = false
            }

            // 2. Retry batch UPSERT with adjusted schema flags
            req = newRequestBuilder("daily_cashflow?on_conflict=id")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(makeJsonArray(includeTitle, includePaymentMode).toString().toRequestBody(jsonMediaType))
                .build()
            var err2 = ""
            executeSupabaseCall(req).use { resp ->
                if (resp.isSuccessful) return@withContext
                err2 = resp.body?.string() ?: "HTTP ${resp.code}"
            }
            Log.w(tag, "Upsert cashflow batch attempt 2 failed: $err2")
            if (err2.contains("PGRST204")) {
                if (err2.contains("title")) includeTitle = false
                if (err2.contains("payment")) includePaymentMode = false
            }

            // 3. Per-record fallback: try UPSERT, then PATCH (update), then POST (insert)
            var successCount = 0
            var lastSingleErr: String? = null
            for (rec in records) {
                try {
                    var obj = buildPayload(rec, includeTitle, includePaymentMode)

                    // Try 3a: Single record UPSERT with on_conflict=id
                    var singleReq = newRequestBuilder("daily_cashflow?on_conflict=id")
                        .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                        .post(JSONArray().put(obj).toString().toRequestBody(jsonMediaType))
                        .build()
                    var handled = false
                    var sErr = ""
                    executeSupabaseCall(singleReq).use { singleResp ->
                        if (singleResp.isSuccessful) {
                            successCount++
                            handled = true
                        } else {
                            sErr = singleResp.body?.string() ?: "HTTP ${singleResp.code}"
                        }
                    }
                    if (handled) continue

                    if (sErr.contains("PGRST204")) {
                        if (sErr.contains("title")) includeTitle = false
                        if (sErr.contains("payment")) includePaymentMode = false
                        obj = buildPayload(rec, includeTitle, includePaymentMode)
                        // Retry 3a
                        singleReq = newRequestBuilder("daily_cashflow?on_conflict=id")
                            .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                            .post(JSONArray().put(obj).toString().toRequestBody(jsonMediaType))
                            .build()
                        executeSupabaseCall(singleReq).use { retryResp ->
                            if (retryResp.isSuccessful) {
                                successCount++
                                handled = true
                            }
                        }
                        if (handled) continue
                    }

                    // Try 3b: Single record PATCH only counts if at least 1 row was matched & updated
                    val patchReq = newRequestBuilder("daily_cashflow?id=eq.${java.net.URLEncoder.encode(rec.id, "UTF-8")}")
                        .addHeader("Prefer", "return=representation")
                        .patch(obj.toString().toRequestBody(jsonMediaType))
                        .build()
                    executeSupabaseCall(patchReq).use { patchResp ->
                        if (patchResp.isSuccessful) {
                            val patchBody = patchResp.body?.string() ?: "[]"
                            val updatedRows = runCatching { JSONArray(patchBody).length() }.getOrDefault(0)
                            if (updatedRows > 0) {
                                successCount++
                                handled = true
                            }
                        }
                    }
                    if (handled) continue

                    // Try 3c: Single record POST (insert)
                    val postReq = newRequestBuilder("daily_cashflow")
                        .addHeader("Prefer", "return=minimal")
                        .post(JSONArray().put(obj).toString().toRequestBody(jsonMediaType))
                        .build()
                    executeSupabaseCall(postReq).use { postResp ->
                        if (postResp.isSuccessful) {
                            successCount++
                        } else {
                            val err = postResp.body?.string() ?: "HTTP ${postResp.code}"
                            Log.w(tag, "Single cashflow record ${rec.id} upsert error: $err")
                            lastSingleErr = err
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed single cashflow upsert for ${rec.id}", e)
                    lastSingleErr = e.message
                }
            }

            if (successCount < records.size) {
                throw Exception("Cashflow sync incomplete ($successCount/${records.size} synced): ${lastSingleErr ?: err1}")
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to upsert cashflow records", e)
            throw e
        }
    }

    suspend fun deleteCashflow(ids: List<String>): Unit = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        try {
            val idsFormatted = ids.joinToString(",") { java.net.URLEncoder.encode(it, "UTF-8") }
            var req = newRequestBuilder("daily_cashflow?id=in.($idsFormatted)")
                .delete()
                .build()
            var firstErr = ""
            var firstCode = 0
            executeSupabaseCall(req).use { resp ->
                if (resp.isSuccessful) return@withContext
                firstCode = resp.code
                firstErr = resp.body?.string() ?: "HTTP ${resp.code}"
            }

            if (ids.size == 1) {
                val singleId = java.net.URLEncoder.encode(ids[0], "UTF-8")
                req = newRequestBuilder("daily_cashflow?id=eq.$singleId")
                    .delete()
                    .build()
                executeSupabaseCall(req).use { resp ->
                    if (resp.isSuccessful) return@withContext
                    firstCode = resp.code
                    firstErr = resp.body?.string() ?: "HTTP ${resp.code}"
                }
            }

            if (firstCode == 404 || firstErr.contains("PGRST200") || firstErr.contains("does not exist")) {
                return@withContext
            }
            throw Exception("Failed to delete cashflow: $firstErr")
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("PGRST200") || msg.contains("404") || msg.contains("does not exist")) {
                return@withContext
            }
            throw e
        }
    }

    // ==================== QUOTATIONS SYNC ====================
    suspend fun fetchAllQuotations(): List<QuotationRecord>? = withContext(Dispatchers.IO) {
        try {
            val list = mutableListOf<QuotationRecord>()
            var offset = 0
            while (true) {
                val req = newRequestBuilder("quotations?select=*&order=created_at.desc,id.asc&limit=$PAGE_SIZE&offset=$offset")
                    .get()
                    .build()
                executeSupabaseCall(req).use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val body = resp.body?.string() ?: "[]"
                    val jsonArr = JSONArray(body)
                    for (i in 0 until jsonArr.length()) {
                        list.add(parseQuotationFromDb(jsonArr.getJSONObject(i)))
                    }
                    if (jsonArr.length() < PAGE_SIZE) return@withContext list
                }
                offset += PAGE_SIZE
            }
            list
        } catch (e: Exception) {
            Log.w(tag, "Failed to fetch quotations", e)
            null
        }
    }

    suspend fun upsertQuotations(records: List<QuotationRecord>): Unit = withContext(Dispatchers.IO) {
        if (records.isEmpty()) return@withContext
        try {
            val jsonArray = JSONArray()
            for (q in records) {
                val obj = JSONObject().apply {
                    put("id", q.id)
                    put("quotation_no", q.quotationNo)
                    put("customer_name", q.customerName)
                    if (q.customerPhone.isNotBlank()) put("customer_phone", q.customerPhone.trim()) else put("customer_phone", JSONObject.NULL)
                    if (q.customerAddress.isNotBlank()) put("customer_address", q.customerAddress.trim()) else put("customer_address", JSONObject.NULL)

                    val safeDate = when {
                        q.date.isNotBlank() && q.date.matches(Regex("""\d{4}-\d{2}-\d{2}""")) -> q.date.trim()
                        else -> DateTimeHelper.localDateKey()
                    }
                    put("date", safeDate)

                    if (q.validUntil.isNotBlank() && q.validUntil.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
                        put("valid_until", q.validUntil.trim())
                    } else {
                        put("valid_until", JSONObject.NULL)
                    }

                    val itemsArray = try {
                        JSONArray(q.itemsJson)
                    } catch (e: Exception) {
                        Log.w(tag, "Invalid quotation itemsJson for ${q.id}: ${e.message}")
                        JSONArray()
                    }
                    put("items", itemsArray)
                    put("subtotal", q.subtotal)
                    put("discount", q.discount)
                    put("tax_percent", q.taxPercent)
                    put("tax_amount", q.taxAmount)
                    put("grand_total", q.grandTotal)
                    put("status", q.status.ifBlank { "Draft" })
                    if (q.notes.isNotBlank()) put("notes", q.notes.trim()) else put("notes", JSONObject.NULL)

                    val safeCreatedAt = when {
                        q.createdAt.isNotBlank() -> q.createdAt.trim()
                        else -> DateTimeHelper.nowIso()
                    }
                    put("created_at", safeCreatedAt)
                }
                jsonArray.put(obj)
            }

            val req = newRequestBuilder("quotations?on_conflict=id")
                .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                .post(jsonArray.toString().toRequestBody(jsonMediaType))
                .build()
            var bulkOk = false
            var bulkErr = ""
            executeSupabaseCall(req).use { resp ->
                bulkOk = resp.isSuccessful
                if (!bulkOk) {
                    bulkErr = resp.body?.string() ?: "HTTP ${resp.code}"
                }
            }
            if (!bulkOk) {
                Log.e(tag, "Failed bulk upsert quotations: $bulkErr, trying per-record fallback")

                for (i in 0 until jsonArray.length()) {
                    val singleObj = jsonArray.getJSONObject(i)
                    val singleReq = newRequestBuilder("quotations?on_conflict=id")
                        .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                        .post(JSONArray().put(singleObj).toString().toRequestBody(jsonMediaType))
                        .build()
                    executeSupabaseCall(singleReq).use { singleResp ->
                        if (!singleResp.isSuccessful) {
                            val sErr = singleResp.body?.string() ?: "HTTP ${singleResp.code}"
                            Log.e(tag, "Failed single quotation upsert: $sErr")
                            throw Exception("Failed to upsert quotation: $sErr")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to upsert quotations", e)
            throw e
        }
    }

    suspend fun deleteQuotations(ids: List<String>): Unit = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        ids.chunked(BATCH_SIZE).forEach { chunk ->
            val idsFormatted = chunk.joinToString(",") { java.net.URLEncoder.encode(it, "UTF-8") }
            val req = newRequestBuilder("quotations?id=in.($idsFormatted)")
                .delete()
                .build()
            executeSupabaseCall(req).use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string() ?: "HTTP ${resp.code}"
                    Log.e(tag, "Failed to delete quotations: $err")
                    throw Exception("Failed to delete quotations: $err")
                }
            }
        }
    }

    suspend fun deleteAllCashflow(): Unit = withContext(Dispatchers.IO) {
        val req = newRequestBuilder("daily_cashflow?id=not.is.null")
            .delete()
            .build()
        executeSupabaseCall(req).use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string() ?: "HTTP ${resp.code}"
                throw Exception("Failed to delete all cashflow: $err")
            }
        }
    }

    suspend fun deleteAllQuotations(): Unit = withContext(Dispatchers.IO) {
        val req = newRequestBuilder("quotations?id=not.is.null")
            .delete()
            .build()
        executeSupabaseCall(req).use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string() ?: "HTTP ${resp.code}"
                throw Exception("Failed to delete all quotations: $err")
            }
        }
    }

    // ==================== LEDGER ACCOUNTS SYNC ====================
    suspend fun fetchAllLedgerAccounts(): List<LedgerAccount>? = withContext(Dispatchers.IO) {
        try {
            val list = mutableListOf<LedgerAccount>()
            var offset = 0
            while (true) {
                val req = newRequestBuilder("ledger_accounts?select=*&order=name.asc,id.asc&limit=$PAGE_SIZE&offset=$offset")
                    .get()
                    .build()
                executeSupabaseCall(req).use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(tag, "Failed to fetch ledger accounts (HTTP ${resp.code})")
                        return@withContext null
                    }
                    val body = resp.body?.string() ?: "[]"
                    val jsonArr = JSONArray(body)
                    for (i in 0 until jsonArr.length()) {
                        list.add(parseLedgerAccountFromDb(jsonArr.getJSONObject(i)))
                    }
                    if (jsonArr.length() < PAGE_SIZE) return@withContext list
                }
                offset += PAGE_SIZE
            }
            list
        } catch (e: Exception) {
            Log.w(tag, "Failed to fetch ledger accounts", e)
            null
        }
    }

    suspend fun upsertLedgerAccounts(records: List<LedgerAccount>): Unit = withContext(Dispatchers.IO) {
        if (records.isEmpty()) return@withContext
        records.chunked(BATCH_SIZE).forEach { chunk ->
            val jsonArray = JSONArray()
            for (acc in chunk) {
                val obj = JSONObject().apply {
                    put("id", acc.id)
                    put("name", acc.name)
                    put("phone", acc.phone)
                    put("address", acc.address)
                    put("type", acc.type)
                    put("net_balance", acc.netBalance)
                    put("credit_limit", acc.creditLimit)
                    put("notes", acc.notes)
                    put("created_at", acc.createdAt)
                    put("updated_at", acc.updatedAt)
                }
                jsonArray.put(obj)
            }
            val req = newRequestBuilder("ledger_accounts?on_conflict=id")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(jsonArray.toString().toRequestBody(jsonMediaType))
                .build()
            executeSupabaseCall(req).use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string() ?: "HTTP ${resp.code}"
                    Log.e(tag, "Failed to upsert ledger accounts: $err")
                    throw Exception("Failed to upsert ledger accounts: $err")
                }
            }
        }
    }

    suspend fun deleteLedgerAccounts(ids: List<String>): Unit = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        ids.chunked(BATCH_SIZE).forEach { chunk ->
            val idsFormatted = chunk.joinToString(",") { java.net.URLEncoder.encode(it, "UTF-8") }
            val req = newRequestBuilder("ledger_accounts?id=in.($idsFormatted)")
                .delete()
                .build()
            executeSupabaseCall(req).use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string() ?: "HTTP ${resp.code}"
                    Log.e(tag, "Failed to delete ledger accounts: $err")
                    throw Exception("Failed to delete ledger accounts: $err")
                }
            }
        }
    }

    // ==================== STORE PROFILE & BANKING SYNC ====================
    suspend fun fetchStoreProfile(): RemoteStoreProfile? = withContext(Dispatchers.IO) {
        try {
            val req = newRequestBuilder("store_profile?id=eq.${StoreProfileSyncCodec.PROFILE_CLIENT_ID}&limit=1")
                .get()
                .build()
            executeSupabaseCall(req).use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: "[]"
                val jsonArr = JSONArray(body)
                if (jsonArr.length() > 0) {
                    val obj = jsonArr.getJSONObject(0)
                    return@withContext StoreProfileSyncCodec.parseFromStoreProfileRow(obj)
                }
                null
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to fetch store profile from Supabase", e)
            null
        }
    }

    suspend fun saveStoreProfile(profile: RemoteStoreProfile, updatedBy: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = StoreProfileSyncCodec.toStoreProfilePayload(profile, updatedBy)
            val req = newRequestBuilder("store_profile?on_conflict=id")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            executeSupabaseCall(req).use { resp ->
                if (!resp.isSuccessful) {
                    Log.e(tag, "Failed to save store profile: ${resp.body?.string()}")
                    false
                } else {
                    Log.d(tag, "Successfully saved store profile & banking details to Supabase")
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to save store profile", e)
            false
        }
    }

    suspend fun downloadImageBytes(urlOrDataUri: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val trimmed = urlOrDataUri.trim()
            if (trimmed.isBlank()) return@withContext null
            if (trimmed.startsWith("data:image", ignoreCase = true)) {
                val commaIdx = trimmed.indexOf(',')
                if (commaIdx <= 0 || commaIdx >= trimmed.length - 1) return@withContext null
                val b64 = trimmed.substring(commaIdx + 1)
                if (b64.length > MAX_IMAGE_DOWNLOAD_BYTES * 2) return@withContext null
                val decoded = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                return@withContext if (decoded.size <= MAX_IMAGE_DOWNLOAD_BYTES) decoded else null
            }
            if (trimmed.startsWith("https://", ignoreCase = true)) {
                val req = Request.Builder().url(trimmed).get().build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val body = resp.body ?: return@withContext null
                    if (body.contentLength() > MAX_IMAGE_DOWNLOAD_BYTES) return@withContext null
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    var total = 0
                    body.byteStream().use { input ->
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            total += read
                            if (total > MAX_IMAGE_DOWNLOAD_BYTES) return@withContext null
                            out.write(buf, 0, read)
                        }
                    }
                    return@withContext out.toByteArray()
                }
            }
            null
        } catch (e: Exception) {
            Log.w(tag, "Failed to download remote image bytes", e)
            null
        }
    }

    suspend fun deleteAllLedgerAccounts(): Unit = withContext(Dispatchers.IO) {
        val req = newRequestBuilder("ledger_accounts?id=not.is.null")
            .delete()
            .build()
        executeSupabaseCall(req).use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string() ?: "HTTP ${resp.code}"
                throw Exception("Failed to delete all ledger accounts: $err")
            }
        }
    }

    suspend fun deleteAllLedgerEntries(): Unit = withContext(Dispatchers.IO) {
        val req = newRequestBuilder("ledger_entries?id=not.is.null")
            .delete()
            .build()
        executeSupabaseCall(req).use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string() ?: "HTTP ${resp.code}"
                throw Exception("Failed to delete all ledger entries: $err")
            }
        }
    }

    // ==================== LEDGER ENTRIES SYNC ====================
    suspend fun fetchAllLedgerEntries(): List<LedgerEntry>? = withContext(Dispatchers.IO) {
        try {
            val list = mutableListOf<LedgerEntry>()
            var offset = 0
            while (true) {
                val req = newRequestBuilder("ledger_entries?select=*&order=created_at.desc,id.asc&limit=$PAGE_SIZE&offset=$offset")
                    .get()
                    .build()
                executeSupabaseCall(req).use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(tag, "Failed to fetch ledger entries (HTTP ${resp.code})")
                        return@withContext null
                    }
                    val body = resp.body?.string() ?: "[]"
                    val jsonArr = JSONArray(body)
                    for (i in 0 until jsonArr.length()) {
                        list.add(parseLedgerEntryFromDb(jsonArr.getJSONObject(i)))
                    }
                    if (jsonArr.length() < PAGE_SIZE) return@withContext list
                }
                offset += PAGE_SIZE
            }
            list
        } catch (e: Exception) {
            Log.w(tag, "Failed to fetch ledger entries", e)
            null
        }
    }

    suspend fun upsertLedgerEntries(records: List<LedgerEntry>): Unit = withContext(Dispatchers.IO) {
        if (records.isEmpty()) return@withContext
        records.chunked(BATCH_SIZE).forEach { chunk ->
            val jsonArray = JSONArray()
            for (entry in chunk) {
                val obj = JSONObject().apply {
                    put("id", entry.id)
                    put("account_id", entry.accountId)
                    put("type", entry.type)
                    put("amount", entry.amount)
                    put("balance_after", entry.balanceAfter)
                    put("date", entry.date)
                    put("description", entry.description)
                    put("bill_ref", entry.billRef)
                    put("created_at", entry.createdAt)
                }
                jsonArray.put(obj)
            }
            val req = newRequestBuilder("ledger_entries?on_conflict=id")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(jsonArray.toString().toRequestBody(jsonMediaType))
                .build()
            executeSupabaseCall(req).use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string() ?: "HTTP ${resp.code}"
                    Log.e(tag, "Failed to upsert ledger entries: $err")
                    throw Exception("Failed to upsert ledger entries: $err")
                }
            }
        }
    }

    suspend fun deleteLedgerEntries(ids: List<String>): Unit = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        ids.chunked(BATCH_SIZE).forEach { chunk ->
            val idsFormatted = chunk.joinToString(",") { java.net.URLEncoder.encode(it, "UTF-8") }
            val req = newRequestBuilder("ledger_entries?id=in.($idsFormatted)")
                .delete()
                .build()
            executeSupabaseCall(req).use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string() ?: "HTTP ${resp.code}"
                    Log.e(tag, "Failed to delete ledger entries: $err")
                    throw Exception("Failed to delete ledger entries: $err")
                }
            }
        }
    }

    // ==================== GST BILLS SYNC ====================
    suspend fun fetchAllGstBills(): List<GstBillRecord>? = withContext(Dispatchers.IO) {
        try {
            val list = mutableListOf<GstBillRecord>()
            var offset = 0
            while (true) {
                val req = newRequestBuilder("gst_bills?select=*&order=created_at.desc,id.asc&limit=$PAGE_SIZE&offset=$offset")
                    .get()
                    .build()
                executeSupabaseCall(req).use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(tag, "Failed to fetch gst bills (HTTP ${resp.code})")
                        return@withContext null
                    }
                    val body = resp.body?.string() ?: "[]"
                    val jsonArr = JSONArray(body)
                    for (i in 0 until jsonArr.length()) {
                        list.add(parseGstBillFromDb(jsonArr.getJSONObject(i)))
                    }
                    if (jsonArr.length() < PAGE_SIZE) return@withContext list
                }
                offset += PAGE_SIZE
            }
            list
        } catch (e: Exception) {
            Log.w(tag, "Failed to fetch gst bills", e)
            null
        }
    }

    suspend fun upsertGstBills(records: List<GstBillRecord>): Unit = withContext(Dispatchers.IO) {
        if (records.isEmpty()) return@withContext
        records.chunked(BATCH_SIZE).forEach { chunk ->
            val jsonArray = JSONArray()
            for (bill in chunk) {
                val obj = JSONObject().apply {
                    put("id", bill.id)
                    put("invoice_no", bill.invoiceNo)
                    put("invoice_date", bill.invoiceDate)
                    put("customer_name", bill.customerName)
                    put("customer_phone", bill.customerPhone)
                    put("customer_gstin", bill.customerGstin)
                    put("customer_address", bill.customerAddress)
                    put("store_gstin", bill.storeGstin)
                    put("is_inter_state", bill.isInterState)
                    put("items_json", bill.itemsJson)
                    put("taxable_total", bill.taxableTotal)
                    put("total_tax_amount", bill.totalTaxAmount)
                    put("grand_total", bill.grandTotal)
                    put("transporter_id", bill.transporterId)
                    put("vehicle_no", bill.vehicleNo)
                    if (bill.quotationId != null) put("quotation_id", bill.quotationId) else put("quotation_id", JSONObject.NULL)
                    if (bill.quotationNo != null) put("quotation_no", bill.quotationNo) else put("quotation_no", JSONObject.NULL)
                    if (bill.pdfPath != null) put("pdf_path", bill.pdfPath) else put("pdf_path", JSONObject.NULL)
                    put("status", bill.status)
                    put("created_at", bill.createdAt)
                }
                jsonArray.put(obj)
            }
            val req = newRequestBuilder("gst_bills?on_conflict=id")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(jsonArray.toString().toRequestBody(jsonMediaType))
                .build()
            executeSupabaseCall(req).use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string() ?: "HTTP ${resp.code}"
                    Log.e(tag, "Failed to upsert gst bills: $err")
                    throw Exception("Failed to upsert gst bills: $err")
                }
            }
        }
    }

    suspend fun deleteGstBills(ids: List<String>): Unit = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        ids.chunked(BATCH_SIZE).forEach { chunk ->
            val idsFormatted = chunk.joinToString(",") { java.net.URLEncoder.encode(it, "UTF-8") }
            val req = newRequestBuilder("gst_bills?id=in.($idsFormatted)")
                .delete()
                .build()
            executeSupabaseCall(req).use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string() ?: "HTTP ${resp.code}"
                    throw Exception("Failed to delete gst bills: $err")
                }
            }
        }
    }

    suspend fun deleteAllGstBills(): Unit = withContext(Dispatchers.IO) {
        val req = newRequestBuilder("gst_bills?id=not.is.null")
            .delete()
            .build()
        executeSupabaseCall(req).use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string() ?: "HTTP ${resp.code}"
                throw Exception("Failed to delete all gst bills: $err")
            }
        }
    }

    // ==================== COLOR TINT LOGS SYNC ====================
    suspend fun fetchAllColorTintLogs(): List<ColorTintLog>? = withContext(Dispatchers.IO) {
        try {
            val list = mutableListOf<ColorTintLog>()
            var offset = 0
            while (true) {
                val req = newRequestBuilder("color_tint_logs?select=*&order=tint_timestamp.desc,tint_record_id.asc&limit=$PAGE_SIZE&offset=$offset")
                    .get()
                    .build()
                executeSupabaseCall(req).use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(tag, "Failed to fetch color tint logs (HTTP ${resp.code})")
                        return@withContext null
                    }
                    val body = resp.body?.string() ?: "[]"
                    val jsonArr = JSONArray(body)
                    for (i in 0 until jsonArr.length()) {
                        list.add(parseColorTintLogFromDb(jsonArr.getJSONObject(i)))
                    }
                    if (jsonArr.length() < PAGE_SIZE) return@withContext list
                }
                offset += PAGE_SIZE
            }
            list
        } catch (e: Exception) {
            Log.w(tag, "Failed to fetch color tint logs", e)
            null
        }
    }

    suspend fun upsertColorTintLogs(records: List<ColorTintLog>): Unit = withContext(Dispatchers.IO) {
        if (records.isEmpty()) return@withContext
        records.chunked(BATCH_SIZE).forEach { chunk ->
            val jsonArray = JSONArray()
            for (logRec in chunk) {
                val obj = JSONObject().apply {
                    put("tint_record_id", logRec.tintRecordId)
                    put("product_name", logRec.productName)
                    put("shade_name", logRec.shadeName)
                    put("shade_code", logRec.shadeCode)
                    put("base_code", logRec.baseCode)
                    put("can_factor", logRec.canFactor)
                    put("liters", logRec.liters)
                    put("no_of_cans", logRec.noOfCans)
                    put("tint_date", logRec.tintDate)
                    put("tint_time", logRec.tintTime)
                    put("tint_timestamp", logRec.tintTimestamp)
                    if (logRec.matchedItemId != null) put("matched_item_id", logRec.matchedItemId) else put("matched_item_id", JSONObject.NULL)
                    if (logRec.matchedItemName != null) put("matched_item_name", logRec.matchedItemName) else put("matched_item_name", JSONObject.NULL)
                    put("qty_deducted", logRec.qtyDeducted)
                    put("processed_at", logRec.processedAt)
                    put("colorant_used", logRec.colorantUsed)
                    put("colorant_quantity", logRec.colorantQuantity)
                }
                jsonArray.put(obj)
            }
            val req = newRequestBuilder("color_tint_logs?on_conflict=tint_record_id")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(jsonArray.toString().toRequestBody(jsonMediaType))
                .build()
            executeSupabaseCall(req).use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string() ?: "HTTP ${resp.code}"
                    Log.e(tag, "Failed to upsert color tint logs: $err")
                    throw Exception("Failed to upsert color tint logs: $err")
                }
            }
        }
    }

    suspend fun deleteColorTintLogs(ids: List<String>): Unit = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        ids.chunked(BATCH_SIZE).forEach { chunk ->
            val idsFormatted = chunk.joinToString(",") { java.net.URLEncoder.encode(it, "UTF-8") }
            val req = newRequestBuilder("color_tint_logs?tint_record_id=in.($idsFormatted)")
                .delete()
                .build()
            executeSupabaseCall(req).use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string() ?: "HTTP ${resp.code}"
                    throw Exception("Failed to delete color tint logs: $err")
                }
            }
        }
    }

    suspend fun deleteAllColorTintLogs(): Unit = withContext(Dispatchers.IO) {
        val req = newRequestBuilder("color_tint_logs?tint_record_id=not.is.null")
            .delete()
            .build()
        executeSupabaseCall(req).use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string() ?: "HTTP ${resp.code}"
                throw Exception("Failed to delete all color tint logs: $err")
            }
        }
    }

    fun parseItemFromDb(obj: JSONObject): Item {
        val mrpVal = if (obj.has("mrp") && !obj.isNull("mrp")) {
            val v = obj.optDouble("mrp")
            if (v.isNaN()) null else v
        } else null

        val priceVal = when {
            obj.has("price") && !obj.isNull("price") -> obj.optDouble("price", 0.0)
            obj.has("selling_price") && !obj.isNull("selling_price") -> obj.optDouble("selling_price", 0.0)
            obj.has("sellingPrice") && !obj.isNull("sellingPrice") -> obj.optDouble("sellingPrice", 0.0)
            else -> 0.0
        }

        return Item(
            id = obj.optString("id"),
            o = obj.optInt("o", 0),
            name = obj.optString("name"),
            code = obj.optString("code", ""),
            barcode = obj.optString("barcode", ""),
            type = obj.optString("type", ""),
            brand = obj.optString("brand", ""),
            size = obj.optString("size", ""),
            aliases = obj.optString("aliases", ""),
            mrp = mrpVal,
            cost = obj.optDouble("cost", 0.0),
            price = priceVal,
            qty = obj.optDouble("qty", 0.0),
            low = obj.optDouble("low", 0.0),
            unit = obj.optString("unit", "pcs").ifEmpty { "pcs" },
            imageUrl = if (obj.has("image_url") && !obj.isNull("image_url")) obj.optString("image_url").ifEmpty { null } else null,
            shelfLocation = if (obj.has("shelf_location") && !obj.isNull("shelf_location")) obj.optString("shelf_location").ifEmpty { null } else null,
            nfcTagId = if (obj.has("nfc_tag_id") && !obj.isNull("nfc_tag_id")) obj.optString("nfc_tag_id").ifEmpty { null } else null,
            secondaryUnit = if (obj.has("secondary_unit") && !obj.isNull("secondary_unit")) obj.optString("secondary_unit").ifEmpty { null } else null,
            conversionFactor = if (obj.has("conversion_factor") && !obj.isNull("conversion_factor")) obj.optDouble("conversion_factor", 1.0).takeIf { it > 0 } ?: 1.0 else 1.0,
            secondaryPrice = if (obj.has("secondary_price") && !obj.isNull("secondary_price")) obj.optDouble("secondary_price", 0.0) else 0.0,
            updatedAt = obj.optString("updated_at", "")
        )
    }

    fun parseCashflowFromDb(obj: JSONObject): DailyCashflowRecord {
        val cat = obj.optString("category", "General")
        val titleVal = obj.optString("title", cat).ifBlank { cat }
        val dateVal = obj.optString("date", obj.optString("entry_date", obj.optString("entryDate", "")))
        return DailyCashflowRecord(
            id = obj.optString("id"),
            type = obj.optString("type", "SALE"),
            amount = obj.optDouble("amount", 0.0),
            title = titleVal,
            category = cat,
            paymentMode = obj.optString("payment_mode", obj.optString("paymentMode", "Cash")).ifEmpty { "Cash" },
            date = dateVal,
            note = obj.optString("note", ""),
            createdAt = obj.optString("created_at", obj.optString("createdAt", ""))
        )
    }

    fun parseQuotationFromDb(obj: JSONObject): QuotationRecord {
        val itemsArray = when {
            obj.has("items") && !obj.isNull("items") -> obj.get("items").toString()
            obj.has("items_json") && !obj.isNull("items_json") -> obj.get("items_json").toString()
            else -> "[]"
        }
        fun optSafe(key: String, fallback: String = ""): String {
            if (obj.isNull(key) || !obj.has(key)) return fallback
            val v = obj.optString(key, fallback)
            return if (v == "null") fallback else v
        }
        return QuotationRecord(
            id = optSafe("id"),
            quotationNo = optSafe("quotation_no").ifEmpty { optSafe("quotationNo") },
            customerName = optSafe("customer_name").ifEmpty { optSafe("customerName") },
            customerPhone = optSafe("customer_phone").ifEmpty { optSafe("customerPhone") },
            customerAddress = optSafe("customer_address").ifEmpty { optSafe("customerAddress") },
            date = optSafe("date"),
            validUntil = optSafe("valid_until").ifEmpty { optSafe("validUntil") },
            itemsJson = itemsArray,
            subtotal = obj.optDouble("subtotal", 0.0),
            discount = obj.optDouble("discount", 0.0),
            taxPercent = obj.optDouble("tax_percent", obj.optDouble("taxPercent", 0.0)),
            taxAmount = obj.optDouble("tax_amount", obj.optDouble("taxAmount", 0.0)),
            grandTotal = obj.optDouble("grand_total", obj.optDouble("grandTotal", 0.0)),
            status = optSafe("status", "Draft"),
            notes = optSafe("notes"),
            createdAt = optSafe("created_at").ifEmpty { optSafe("createdAt") }
        )
    }

    fun parseLedgerAccountFromDb(obj: JSONObject): LedgerAccount {
        return LedgerAccount(
            id = obj.optString("id"),
            name = obj.optString("name"),
            phone = obj.optString("phone", ""),
            address = obj.optString("address", ""),
            type = obj.optString("type", "CUSTOMER"),
            netBalance = obj.optDouble("net_balance", obj.optDouble("netBalance", 0.0)),
            creditLimit = obj.optDouble("credit_limit", obj.optDouble("creditLimit", 0.0)),
            notes = obj.optString("notes", ""),
            createdAt = obj.optString("created_at", obj.optString("createdAt", "")),
            updatedAt = obj.optString("updated_at", obj.optString("updatedAt", ""))
        )
    }

    fun parseLedgerEntryFromDb(obj: JSONObject): LedgerEntry {
        return LedgerEntry(
            id = obj.optString("id"),
            accountId = obj.optString("account_id", obj.optString("accountId", "")),
            type = obj.optString("type", "GAVE"),
            amount = obj.optDouble("amount", 0.0),
            balanceAfter = obj.optDouble("balance_after", obj.optDouble("balanceAfter", 0.0)),
            date = obj.optString("date", ""),
            description = obj.optString("description", ""),
            billRef = obj.optString("bill_ref", obj.optString("billRef", "")),
            createdAt = obj.optString("created_at", obj.optString("createdAt", ""))
        )
    }

    fun parseGstBillFromDb(obj: JSONObject): GstBillRecord {
        fun optSafe(key: String, fallback: String = ""): String {
            if (obj.isNull(key) || !obj.has(key)) return fallback
            val v = obj.optString(key, fallback)
            return if (v == "null") fallback else v
        }
        return GstBillRecord(
            id = optSafe("id"),
            invoiceNo = optSafe("invoice_no").ifEmpty { optSafe("invoiceNo") },
            invoiceDate = optSafe("invoice_date").ifEmpty { optSafe("invoiceDate") },
            customerName = optSafe("customer_name").ifEmpty { optSafe("customerName") },
            customerPhone = optSafe("customer_phone").ifEmpty { optSafe("customerPhone") },
            customerGstin = optSafe("customer_gstin").ifEmpty { optSafe("customerGstin") },
            customerAddress = optSafe("customer_address").ifEmpty { optSafe("customerAddress") },
            storeGstin = optSafe("store_gstin").ifEmpty { optSafe("storeGstin") },
            isInterState = if (obj.has("is_inter_state")) obj.optBoolean("is_inter_state", false) else obj.optBoolean("isInterState", false),
            itemsJson = optSafe("items_json").ifEmpty { optSafe("itemsJson", "[]") },
            taxableTotal = obj.optDouble("taxable_total", obj.optDouble("taxableTotal", 0.0)),
            totalTaxAmount = obj.optDouble("total_tax_amount", obj.optDouble("totalTaxAmount", 0.0)),
            grandTotal = obj.optDouble("grand_total", obj.optDouble("grandTotal", 0.0)),
            transporterId = optSafe("transporter_id").ifEmpty { optSafe("transporterId") },
            vehicleNo = optSafe("vehicle_no").ifEmpty { optSafe("vehicleNo") },
            quotationId = optSafe("quotation_id").ifEmpty { optSafe("quotationId") }.takeIf { it.isNotBlank() },
            quotationNo = optSafe("quotation_no").ifEmpty { optSafe("quotationNo") }.takeIf { it.isNotBlank() },
            pdfPath = optSafe("pdf_path").ifEmpty { optSafe("pdfPath") }.takeIf { it.isNotBlank() },
            status = optSafe("status", "Generated"),
            createdAt = optSafe("created_at").ifEmpty { optSafe("createdAt") }
        )
    }

    fun parseColorTintLogFromDb(obj: JSONObject): ColorTintLog {
        fun optSafe(key: String, fallback: String = ""): String {
            if (obj.isNull(key) || !obj.has(key)) return fallback
            val v = obj.optString(key, fallback)
            return if (v == "null") fallback else v
        }
        return ColorTintLog(
            tintRecordId = optSafe("tint_record_id").ifEmpty { optSafe("tintRecordId") },
            productName = optSafe("product_name").ifEmpty { optSafe("productName") },
            shadeName = optSafe("shade_name").ifEmpty { optSafe("shadeName") },
            shadeCode = optSafe("shade_code").ifEmpty { optSafe("shadeCode") },
            baseCode = optSafe("base_code").ifEmpty { optSafe("baseCode") },
            canFactor = optSafe("can_factor").ifEmpty { optSafe("canFactor") },
            liters = obj.optDouble("liters", 0.0),
            noOfCans = obj.optInt("no_of_cans", obj.optInt("noOfCans", 1)),
            tintDate = optSafe("tint_date").ifEmpty { optSafe("tintDate") },
            tintTime = optSafe("tint_time").ifEmpty { optSafe("tintTime") },
            tintTimestamp = optSafe("tint_timestamp").ifEmpty { optSafe("tintTimestamp") },
            matchedItemId = optSafe("matched_item_id").ifEmpty { optSafe("matchedItemId") }.takeIf { it.isNotBlank() },
            matchedItemName = optSafe("matched_item_name").ifEmpty { optSafe("matchedItemName") }.takeIf { it.isNotBlank() },
            qtyDeducted = obj.optDouble("qty_deducted", obj.optDouble("qtyDeducted", 0.0)),
            processedAt = optSafe("processed_at").ifEmpty { optSafe("processedAt") },
            colorantUsed = optSafe("colorant_used").ifEmpty { optSafe("colorantUsed") },
            colorantQuantity = optSafe("colorant_quantity").ifEmpty { optSafe("colorantQuantity") }
        )
    }

    // Realtime WebSocket support matching Phoenix channels protocol and Hardware_website
    fun connectRealtime(
        coroutineScope: CoroutineScope,
        onStatusChanged: (Boolean) -> Unit,
        onItemChanged: (type: String, item: Item?, oldId: String?) -> Unit,
        onCashflowChanged: ((type: String, cashflow: DailyCashflowRecord?, oldId: String?) -> Unit)? = null,
        onQuotationChanged: ((type: String, quotation: QuotationRecord?, oldId: String?) -> Unit)? = null,
        onLedgerAccountChanged: ((type: String, account: LedgerAccount?, oldId: String?) -> Unit)? = null,
        onLedgerEntryChanged: ((type: String, entry: LedgerEntry?, oldId: String?) -> Unit)? = null,
        onGstBillChanged: ((type: String, bill: GstBillRecord?, oldId: String?) -> Unit)? = null,
        onColorTintLogChanged: ((type: String, log: ColorTintLog?, oldId: String?) -> Unit)? = null,
        onStoreProfileChanged: ((RemoteStoreProfile) -> Unit)? = null,
        onClosedOrFailed: () -> Unit
    ): WebSocket? {
        if (!isConfigured()) {
            if (!loggedUnconfigured) {
                loggedUnconfigured = true
                Log.w(tag, "Supabase URL/Key not configured. Running locally until SUPABASE_URL and SUPABASE_KEY are set in AI Studio Secrets.")
            }
            onStatusChanged(false)
            return null
        }
        val userToken = accessTokenProvider?.invoke()?.trim().orEmpty()
        if (userToken.isBlank()) {
            Log.w(tag, "Realtime connection aborted: authenticated user JWT required.")
            onStatusChanged(false)
            return null
        }
        val connSeq = realtimeSeq.incrementAndGet()
        val wsUrl = baseUrl.replace("https://", "wss://").replace("http://", "ws://") +
            "/realtime/v1/websocket?apikey=$apiKey&vsn=1.0.0"
        val request = Request.Builder()
            .url(wsUrl)
            .header("Authorization", "Bearer $userToken")
            .build()

        var webSocket: WebSocket? = null
        var heartbeatJob: Job? = null

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (realtimeSeq.get() != connSeq) {
                    try {
                        webSocket.close(1000, "superseded")
                    } catch (e: Exception) {
                        Log.d(tag, "Failed closing superseded realtime WS", e)
                    }
                    return
                }
                Log.d(tag, "Realtime WS connected to Supabase")
                val currentToken = accessTokenProvider?.invoke()?.trim().orEmpty().ifBlank { userToken }
                val tables = listOf(
                    "items",
                    "store_profile",
                    "daily_cashflow",
                    "quotations",
                    "ledger_accounts",
                    "ledger_entries",
                    "gst_bills",
                    "color_tint_logs"
                )
                tables.forEach { table ->
                    val payloadObj = JSONObject().apply {
                        put("access_token", currentToken)
                        put("config", JSONObject().apply {
                            put("broadcast", JSONObject().apply { put("self", false) })
                            put("presence", JSONObject().apply { put("key", "") })
                            put("postgres_changes", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("event", "*")
                                    put("schema", "public")
                                    put("table", table)
                                })
                            })
                        })
                    }
                    val joinMsg = JSONObject().apply {
                        put("topic", "realtime:public:$table")
                        put("event", "phx_join")
                        put("ref", "join_${table}_${System.currentTimeMillis()}")
                        put("payload", payloadObj)
                    }
                    webSocket.send(joinMsg.toString())
                }

                // Start Phoenix heartbeat loop every 20 seconds
                heartbeatJob?.cancel()
                heartbeatJob = coroutineScope.launch {
                    var hbCounter = 1L
                    while (isActive && realtimeSeq.get() == connSeq) {
                        delay(20_000)
                        if (realtimeSeq.get() != connSeq) break
                        val hbMsg = JSONObject().apply {
                            put("topic", "phoenix")
                            put("event", "heartbeat")
                            put("payload", JSONObject())
                            put("ref", "hb_${hbCounter++}")
                        }
                        webSocket.send(hbMsg.toString())
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (realtimeSeq.get() != connSeq) return
                try {
                    val root = JSONObject(text)
                    val event = root.optString("event")
                    val payload = root.optJSONObject("payload")
                    val topic = root.optString("topic")

                    if (event == "phx_reply" && topic.startsWith("realtime:")) {
                        val status = payload?.optString("status")
                        if (status == "ok") {
                            Log.d(tag, "Subscribed to $topic successfully")
                            onStatusChanged(true)
                        } else if (status == "error") {
                            Log.w(tag, "Realtime channel join rejected on $topic: ${payload?.optJSONObject("response")}")
                        }
                    } else if (event == "system" && payload?.optString("status") == "ok") {
                        Log.d(tag, "Realtime system subscription confirmed")
                        onStatusChanged(true)
                    } else if (event == "postgres_changes") {
                        val dataObj = payload?.optJSONObject("data") ?: payload
                        val table = dataObj?.optString("table") ?: payload?.optString("table") ?: ""
                        val type = dataObj?.optString("type") ?: ""
                        val record = dataObj?.optJSONObject("record") ?: dataObj?.optJSONObject("new")
                        val oldRecord = dataObj?.optJSONObject("old_record") ?: dataObj?.optJSONObject("old")

                        fun extractOldId(vararg altKeys: String): String? {
                            for (k in altKeys) {
                                oldRecord?.optString(k)?.takeIf { it.isNotBlank() }?.let { return it }
                                record?.optString(k)?.takeIf { it.isNotBlank() }?.let { return it }
                            }
                            val idFromOldRecord = oldRecord?.optString("id")?.takeIf { it.isNotBlank() }
                            val idFromRecord = record?.optString("id")?.takeIf { it.isNotBlank() }
                            val idFromDataObj = dataObj?.optString("id")?.takeIf { it.isNotBlank() }
                            val idFromPayload = payload?.optString("id")?.takeIf { it.isNotBlank() }
                            return idFromOldRecord ?: idFromRecord ?: idFromDataObj ?: idFromPayload
                        }

                        if (topic.contains("daily_cashflow") || table == "daily_cashflow") {
                            if (type == "INSERT" || type == "UPDATE") {
                                record?.let {
                                    val cashflow = parseCashflowFromDb(it)
                                    onCashflowChanged?.invoke(type, cashflow, null)
                                }
                            } else if (type == "DELETE") {
                                val oldId = extractOldId()
                                onCashflowChanged?.invoke("DELETE", null, oldId)
                            }
                        } else if (topic.contains("quotations") || table == "quotations") {
                            if (type == "INSERT" || type == "UPDATE") {
                                record?.let {
                                    val q = parseQuotationFromDb(it)
                                    onQuotationChanged?.invoke(type, q, null)
                                }
                            } else if (type == "DELETE") {
                                val oldId = extractOldId()
                                onQuotationChanged?.invoke("DELETE", null, oldId)
                            }
                        } else if (topic.contains("ledger_accounts") || table == "ledger_accounts") {
                            if (type == "INSERT" || type == "UPDATE") {
                                record?.let {
                                    val a = parseLedgerAccountFromDb(it)
                                    onLedgerAccountChanged?.invoke(type, a, null)
                                }
                            } else if (type == "DELETE") {
                                val oldId = extractOldId()
                                onLedgerAccountChanged?.invoke("DELETE", null, oldId)
                            }
                        } else if (topic.contains("ledger_entries") || table == "ledger_entries") {
                            if (type == "INSERT" || type == "UPDATE") {
                                record?.let {
                                    val e = parseLedgerEntryFromDb(it)
                                    onLedgerEntryChanged?.invoke(type, e, null)
                                }
                            } else if (type == "DELETE") {
                                val oldId = extractOldId()
                                onLedgerEntryChanged?.invoke("DELETE", null, oldId)
                            }
                        } else if (topic.contains("gst_bills") || table == "gst_bills") {
                            if (type == "INSERT" || type == "UPDATE") {
                                record?.let {
                                    val bill = parseGstBillFromDb(it)
                                    onGstBillChanged?.invoke(type, bill, null)
                                }
                            } else if (type == "DELETE") {
                                val oldId = extractOldId()
                                onGstBillChanged?.invoke("DELETE", null, oldId)
                            }
                        } else if (topic.contains("color_tint_logs") || table == "color_tint_logs") {
                            if (type == "INSERT" || type == "UPDATE") {
                                record?.let {
                                    val logRec = parseColorTintLogFromDb(it)
                                    onColorTintLogChanged?.invoke(type, logRec, null)
                                }
                            } else if (type == "DELETE") {
                                val oldId = extractOldId("tint_record_id")
                                onColorTintLogChanged?.invoke("DELETE", null, oldId)
                            }
                        } else if (topic.contains("items") || table == "items") {
                            if (type == "INSERT" || type == "UPDATE") {
                                record?.let {
                                    val item = parseItemFromDb(it)
                                    onItemChanged(type, item, null)
                                }
                            } else if (type == "DELETE") {
                                val oldId = extractOldId()
                                Log.d(tag, "Realtime items DELETE event received, oldId=$oldId")
                                onItemChanged("DELETE", null, oldId)
                            }
                        } else if (topic.contains("store_profile") || table == "store_profile") {
                            val parsedProfile = StoreProfileSyncCodec.parseFromStoreProfileRow(record)
                            if (parsedProfile != null) {
                                onStoreProfileChanged?.invoke(parsedProfile)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error parsing realtime message: $text", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                heartbeatJob?.cancel()
                if (realtimeSeq.get() != connSeq) return
                Log.d(tag, "Realtime WS closing: $code / $reason")
                onStatusChanged(false)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                heartbeatJob?.cancel()
                if (realtimeSeq.get() != connSeq) return
                Log.d(tag, "Realtime WS closed: $code / $reason")
                onStatusChanged(false)
                onClosedOrFailed()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                heartbeatJob?.cancel()
                if (realtimeSeq.get() != connSeq) return
                Log.w(tag, "Realtime WS failure: ${t.message}")
                onStatusChanged(false)
                onClosedOrFailed()
            }
        }

        webSocket = wsHttpClient.newWebSocket(request, listener)
        return webSocket
    }

    suspend fun uploadImageToCloudinary(imageBytes: ByteArray, fileName: String = "item_${System.currentTimeMillis()}.jpg"): Result<String> = withContext(Dispatchers.IO) {
        val isPng = fileName.endsWith(".png", ignoreCase = true)
        val mimeType = if (isPng) "image/png" else "image/jpeg"
        try {
            // 1. Request signature from Supabase Edge Function using authenticated user JWT
            val signReq = newRequestBuilder("$baseUrl/functions/v1/sign-cloudinary-upload")
                .post("""{"folder":"items"}""".toRequestBody(jsonMediaType))
                .build()

            val signBodyStr = executeSupabaseCall(signReq).use { signResp ->
                val body = signResp.body?.string() ?: ""
                if (!signResp.isSuccessful) {
                    Log.w(tag, "Edge function signing error (${signResp.code}): $body")
                    return@withContext Result.failure(Exception("Image upload signing failed (${signResp.code})"))
                }
                body
            }

            val signJson = JSONObject(signBodyStr)
            val signature = signJson.optString("signature")
            val timestamp = signJson.optLong("timestamp")
            val cloudApiKey = signJson.optString("apiKey")
            val cloudName = signJson.optString("cloudName")
            val folder = signJson.optString("folder", "items")

            if (signature.isEmpty() || cloudName.isEmpty() || cloudApiKey.isEmpty()) {
                Log.w(tag, "Incomplete signature response from sign-cloudinary-upload")
                return@withContext Result.failure(Exception("Incomplete upload signature from server"))
            }

            // 2. Upload file directly to Cloudinary using signed payload
            val multipartBody = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("api_key", cloudApiKey)
                .addFormDataPart("timestamp", timestamp.toString())
                .addFormDataPart("signature", signature)
                .addFormDataPart("folder", folder)
                .addFormDataPart(
                    "file",
                    fileName,
                    imageBytes.toRequestBody(mimeType.toMediaType())
                )
                .build()

            val uploadReq = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$cloudName/image/upload")
                .post(multipartBody)
                .build()

            okHttpClient.newCall(uploadReq).execute().use { uploadResp ->
                val uploadBody = uploadResp.body?.string() ?: ""
                if (!uploadResp.isSuccessful) {
                    Log.w(tag, "Cloudinary upload failed (${uploadResp.code}): $uploadBody")
                    return@withContext Result.failure(Exception("Image host upload failed (${uploadResp.code})"))
                }

                val uploadJson = JSONObject(uploadBody)
                val secureUrl = uploadJson.optString("secure_url").ifEmpty { uploadJson.optString("url") }
                if (secureUrl.isNotEmpty()) {
                    Result.success(secureUrl)
                } else {
                    Result.failure(Exception("Image upload did not return a URL"))
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Image upload exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun fetchAllAuditLogs(): List<com.example.data.model.AuditLogRecord> = withContext(Dispatchers.IO) {
        try {
            val req = newRequestBuilder("audit_logs?select=*&order=created_at.desc&limit=500")
                .get()
                .build()
            executeSupabaseCall(req).use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string() ?: "[]"
                val jsonArr = JSONArray(body)
                val list = mutableListOf<com.example.data.model.AuditLogRecord>()
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    list.add(
                        com.example.data.model.AuditLogRecord(
                            id = obj.optString("id"),
                            userEmail = obj.optString("user_email", obj.optString("userEmail", "")),
                            action = obj.optString("action", ""),
                            details = obj.optString("details", ""),
                            createdAt = obj.optString("created_at", obj.optString("createdAt", ""))
                        )
                    )
                }
                list
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to fetch audit logs", e)
            emptyList()
        }
    }

    suspend fun insertAuditLog(record: com.example.data.model.AuditLogRecord): Unit = withContext(Dispatchers.IO) {
        try {
            val obj = JSONObject().apply {
                put("id", record.id)
                put("user_email", record.userEmail)
                put("action", record.action)
                put("details", record.details)
                put("created_at", record.createdAt)
            }
            val req = newRequestBuilder("audit_logs?on_conflict=id")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(JSONArray().put(obj).toString().toRequestBody(jsonMediaType))
                .build()
            executeSupabaseCall(req).use { resp ->
                if (!resp.isSuccessful) {
                    Log.e(tag, "Failed to insert audit log: ${resp.body?.string()}")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to insert audit log", e)
        }
    }
}

