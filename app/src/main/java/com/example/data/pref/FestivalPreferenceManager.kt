package com.example.data.pref

import android.content.Context
import com.example.data.model.FestivalConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class FestivalStudioConfig(
    val autoCalendarEnabled: Boolean = true,
    val applyFestivalThemeToApp: Boolean = true,
    val overrideMode: String = MODE_AUTO, // "AUTO", "CUSTOM", "OFF"
    val customFestival: FestivalConfig = FestivalConfig(
        enabled = false,
        festivalName = "",
        greetingMessage = "",
        badgeText = "",
        accentColorHex = "",
        bannerImageUrl = "",
        startDate = "",
        endDate = "",
        stylePreset = "diwali"
    ),
    val updatedAt: Long = 0L
) {
    companion object {
        const val MODE_AUTO = "AUTO"
        const val MODE_CUSTOM = "CUSTOM"
        const val MODE_OFF = "OFF"
    }
}

object FestivalStudioCodec {
    fun toJsonString(config: FestivalStudioConfig): String {
        val customObj = JSONObject().apply {
            put("enabled", config.customFestival.enabled)
            put("festivalName", config.customFestival.festivalName)
            put("greetingMessage", config.customFestival.greetingMessage)
            put("badgeText", config.customFestival.badgeText)
            put("accentColorHex", config.customFestival.accentColorHex)
            put("bannerImageUrl", config.customFestival.bannerImageUrl)
            put("startDate", config.customFestival.startDate)
            put("endDate", config.customFestival.endDate)
            put("stylePreset", config.customFestival.stylePreset)
        }
        return JSONObject().apply {
            put("autoCalendarEnabled", config.autoCalendarEnabled)
            put("applyFestivalThemeToApp", config.applyFestivalThemeToApp)
            put("overrideMode", config.overrideMode)
            put("customFestival", customObj)
            put("updatedAt", config.updatedAt)
        }.toString()
    }

    fun fromJsonString(raw: String?): FestivalStudioConfig? {
        if (raw.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(raw)
            val customObj = obj.optJSONObject("customFestival")
            val custom = if (customObj != null) {
                FestivalConfig(
                    enabled = customObj.optBoolean("enabled", false),
                    festivalName = customObj.optString("festivalName", ""),
                    greetingMessage = customObj.optString("greetingMessage", ""),
                    badgeText = customObj.optString("badgeText", ""),
                    accentColorHex = customObj.optString("accentColorHex", ""),
                    bannerImageUrl = customObj.optString("bannerImageUrl", ""),
                    startDate = customObj.optString("startDate", ""),
                    endDate = customObj.optString("endDate", ""),
                    stylePreset = customObj.optString("stylePreset", "default")
                )
            } else {
                FestivalConfig(stylePreset = "diwali")
            }
            val modeRaw = obj.optString("overrideMode", FestivalStudioConfig.MODE_AUTO).uppercase()
            val validMode = when (modeRaw) {
                FestivalStudioConfig.MODE_CUSTOM,
                FestivalStudioConfig.MODE_OFF -> modeRaw
                else -> FestivalStudioConfig.MODE_AUTO
            }
            FestivalStudioConfig(
                autoCalendarEnabled = obj.optBoolean("autoCalendarEnabled", true),
                applyFestivalThemeToApp = obj.optBoolean("applyFestivalThemeToApp", true),
                overrideMode = validMode,
                customFestival = custom,
                updatedAt = obj.optLong("updatedAt", 0L)
            )
        } catch (_: Exception) {
            null
        }
    }
}

class FestivalPreferenceManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_STUDIO_JSON = "festival_studio_json"

        @Volatile
        private var isInitialized = false
        private val _currentConfig = MutableStateFlow(FestivalStudioConfig())
        val studioConfig: StateFlow<FestivalStudioConfig> = _currentConfig.asStateFlow()
    }

    init {
        synchronized(FestivalPreferenceManager::class.java) {
            if (!isInitialized) {
                _currentConfig.value = loadFromPrefs()
                isInitialized = true
            }
        }
    }

    val currentConfig: StateFlow<FestivalStudioConfig> = studioConfig

    private fun loadFromPrefs(): FestivalStudioConfig {
        val raw = prefs.getString(KEY_STUDIO_JSON, null)
        return FestivalStudioCodec.fromJsonString(raw) ?: FestivalStudioConfig()
    }

    fun getRawJsonOrNull(): String? = prefs.getString(KEY_STUDIO_JSON, null)

    fun saveConfig(config: FestivalStudioConfig): String {
        val stamped = if (config.updatedAt > 0L) config else config.copy(updatedAt = System.currentTimeMillis())
        val json = FestivalStudioCodec.toJsonString(stamped)
        prefs.edit().putString(KEY_STUDIO_JSON, json).apply()
        _currentConfig.value = stamped
        return json
    }

    fun applyRemoteJson(rawJson: String?) {
        if (rawJson.isNullOrBlank()) return
        val parsed = FestivalStudioCodec.fromJsonString(rawJson) ?: return
        val current = _currentConfig.value
        if (parsed == current) return
        if (current.updatedAt > 0L && parsed.updatedAt > 0L && parsed.updatedAt < current.updatedAt) {
            return
        }
        prefs.edit().putString(KEY_STUDIO_JSON, rawJson).apply()
        _currentConfig.value = parsed
    }
}
