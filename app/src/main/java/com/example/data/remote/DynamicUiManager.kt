package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.model.DynamicUiConfig
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.EOFException
import java.util.concurrent.TimeUnit

sealed class UiSyncResult {
    data object Success : UiSyncResult()
    data class HttpError(val code: Int) : UiSyncResult()
    data class InvalidJson(val reason: String) : UiSyncResult()
    data class NetworkError(val reason: String) : UiSyncResult()
}

class DynamicUiManager(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(DynamicUiConfig::class.java)

    private val prefs = context.getSharedPreferences("dynamic_ui_prefs", Context.MODE_PRIVATE)

    private val _uiConfig = MutableStateFlow(loadCachedConfig())
    val uiConfig: StateFlow<DynamicUiConfig> = _uiConfig.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _lastSyncedTime = MutableStateFlow(prefs.getLong("last_synced_time", 0L))
    val lastSyncedTime: StateFlow<Long> = _lastSyncedTime.asStateFlow()

    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()

    companion object {
        private const val TAG = "DynamicUiManager"
        private const val CONFIG_URL = "https://raw.githubusercontent.com/ARIF683/Hardware_new/main/ui_config.json"
        private const val PREF_KEY_CONFIG = "cached_ui_config"
    }

    private fun loadCachedConfig(): DynamicUiConfig {
        val cached = prefs.getString(PREF_KEY_CONFIG, null)
        if (!cached.isNullOrEmpty()) {
            try {
                adapter.fromJson(cached)?.let { return it }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing cached UI config", e)
            }
        }
        return DynamicUiConfig()
    }

    suspend fun refreshConfigWithStatus(): UiSyncResult = withContext(Dispatchers.IO) {
        _isRefreshing.value = true
        try {
            val request = Request.Builder()
                .url(CONFIG_URL)
                .header("Cache-Control", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val msg = if (response.code == 404) {
                        "ui_config.json not found on GitHub (HTTP 404)"
                    } else {
                        "GitHub returned HTTP ${response.code}"
                    }
                    Log.w(TAG, msg)
                    _lastSyncError.value = msg
                    return@withContext UiSyncResult.HttpError(response.code)
                }

                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    val msg = "GitHub ui_config.json is empty"
                    Log.w(TAG, msg)
                    _lastSyncError.value = msg
                    return@withContext UiSyncResult.InvalidJson(msg)
                }

                val parsed = try {
                    adapter.fromJson(body)
                } catch (e: JsonEncodingException) {
                    val msg = "Invalid JSON syntax in GitHub ui_config.json"
                    Log.w(TAG, "$msg: ${e.message}")
                    _lastSyncError.value = msg
                    return@withContext UiSyncResult.InvalidJson(msg)
                } catch (e: JsonDataException) {
                    val msg = "Invalid JSON field types in GitHub ui_config.json"
                    Log.w(TAG, "$msg: ${e.message}")
                    _lastSyncError.value = msg
                    return@withContext UiSyncResult.InvalidJson(msg)
                } catch (e: EOFException) {
                    val msg = "Incomplete/truncated JSON in GitHub ui_config.json"
                    Log.w(TAG, "$msg: ${e.message}")
                    _lastSyncError.value = msg
                    return@withContext UiSyncResult.InvalidJson(msg)
                }

                if (parsed == null) {
                    val msg = "Could not parse GitHub ui_config.json"
                    _lastSyncError.value = msg
                    return@withContext UiSyncResult.InvalidJson(msg)
                }

                val now = System.currentTimeMillis()
                prefs.edit()
                    .putString(PREF_KEY_CONFIG, body)
                    .putLong("last_synced_time", now)
                    .apply()
                _lastSyncedTime.value = now
                _lastSyncError.value = null
                _uiConfig.value = parsed
                Log.d(
                    TAG,
                    "Successfully refreshed dynamic UI config: ${parsed.theme.storeTitle}, activeFestivals=${parsed.activeFestivals().size}"
                )
                UiSyncResult.Success
            }
        } catch (e: Exception) {
            val msg = "Network error reaching GitHub: ${e.localizedMessage ?: e.javaClass.simpleName}"
            Log.w(TAG, msg)
            _lastSyncError.value = msg
            UiSyncResult.NetworkError(msg)
        } finally {
            _isRefreshing.value = false
        }
    }

    suspend fun refreshConfig(): Boolean = refreshConfigWithStatus() is UiSyncResult.Success
}
