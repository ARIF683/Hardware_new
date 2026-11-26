package com.example.data.pref

import android.content.Context
import com.example.data.model.AnnouncementConfig
import com.example.data.model.FestivalConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
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
    val customFestivals: List<FestivalConfig> = emptyList(),
    val hiddenAutoFestivalKeys: List<String> = emptyList(),
    val announcement: AnnouncementConfig = AnnouncementConfig(
        enabled = false,
        message = "",
        severity = "info",
        dismissible = true,
        startAtMillis = 0L,
        endAtMillis = 0L
    ),
    val updatedAt: Long = 0L
) {
    fun effectiveCustomFestivals(): List<FestivalConfig> {
        if (customFestivals.isNotEmpty()) return customFestivals
        val hasLegacy = customFestival.enabled ||
            customFestival.festivalName.isNotBlank() ||
            customFestival.greetingMessage.isNotBlank() ||
            customFestival.bannerImageUrl.isNotBlank()
        return if (hasLegacy) listOf(customFestival) else emptyList()
    }

    companion object {
        const val MODE_AUTO = "AUTO"
        const val MODE_CUSTOM = "CUSTOM"
        const val MODE_OFF = "OFF"
    }
}

object FestivalStudioCodec {
    private fun festivalToJson(cfg: FestivalConfig): JSONObject = JSONObject().apply {
        put("enabled", cfg.enabled)
        put("festivalName", cfg.festivalName)
        put("greetingMessage", cfg.greetingMessage)
        put("badgeText", cfg.badgeText)
        put("accentColorHex", cfg.accentColorHex)
        put("bannerImageUrl", cfg.bannerImageUrl)
        put("startDate", cfg.startDate)
        put("endDate", cfg.endDate)
        put("stylePreset", cfg.stylePreset)
    }

    private val HEX_COLOR_REGEX = Regex("^#[0-9A-Fa-f]{6}$")
    private val DATE_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    private fun sanitizeHttpsUrl(raw: String): String {
        val t = raw.trim()
        return if (t.startsWith("https://", ignoreCase = true)) t.take(500) else ""
    }

    fun sanitizeFestivalConfig(cfg: FestivalConfig): FestivalConfig {
        val safeColor = cfg.accentColorHex.trim().takeIf { HEX_COLOR_REGEX.matches(it) } ?: ""
        val safePreset = cfg.stylePreset.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "").ifBlank { "default" }
        return cfg.copy(
            festivalName = cfg.festivalName.trim().take(60),
            greetingMessage = cfg.greetingMessage.trim().take(160),
            badgeText = cfg.badgeText.trim().take(40),
            accentColorHex = safeColor,
            bannerImageUrl = sanitizeHttpsUrl(cfg.bannerImageUrl),
            startDate = cfg.startDate.trim().takeIf { DATE_REGEX.matches(it) } ?: "",
            endDate = cfg.endDate.trim().takeIf { DATE_REGEX.matches(it) } ?: "",
            stylePreset = safePreset
        )
    }

    private fun jsonToFestival(obj: JSONObject, defaultPreset: String = "default"): FestivalConfig =
        sanitizeFestivalConfig(
            FestivalConfig(
                enabled = obj.optBoolean("enabled", false),
                festivalName = obj.optString("festivalName", ""),
                greetingMessage = obj.optString("greetingMessage", ""),
                badgeText = obj.optString("badgeText", ""),
                accentColorHex = obj.optString("accentColorHex", ""),
                bannerImageUrl = obj.optString("bannerImageUrl", ""),
                startDate = obj.optString("startDate", ""),
                endDate = obj.optString("endDate", ""),
                stylePreset = obj.optString("stylePreset", defaultPreset)
            )
        )

    fun toJsonString(config: FestivalStudioConfig): String {
        val listToWrite = config.effectiveCustomFestivals().map { sanitizeFestivalConfig(it) }
        val primaryLegacy = listToWrite.firstOrNull() ?: sanitizeFestivalConfig(config.customFestival)
        val customArray = JSONArray().apply {
            listToWrite.forEach { put(festivalToJson(it)) }
        }
        val hiddenArray = JSONArray().apply {
            config.hiddenAutoFestivalKeys.distinct().take(50).forEach { put(it.trim().take(60)) }
        }
        val announcementObj = JSONObject().apply {
            put("enabled", config.announcement.enabled)
            put("message", config.announcement.message.trim().take(300))
            put("severity", when (config.announcement.severity.lowercase()) {
                "warning", "alert", "success" -> config.announcement.severity.lowercase()
                else -> "info"
            })
            put("dismissible", config.announcement.dismissible)
            put("startAtMillis", config.announcement.startAtMillis.coerceAtLeast(0L))
            put("endAtMillis", config.announcement.endAtMillis.coerceAtLeast(0L))
        }
        return JSONObject().apply {
            put("autoCalendarEnabled", config.autoCalendarEnabled)
            put("applyFestivalThemeToApp", config.applyFestivalThemeToApp)
            put("overrideMode", config.overrideMode)
            put("customFestival", festivalToJson(primaryLegacy))
            put("customFestivals", customArray)
            put("hiddenAutoFestivalKeys", hiddenArray)
            put("announcement", announcementObj)
            put("updatedAt", config.updatedAt)
        }.toString()
    }

    fun fromJsonString(raw: String?): FestivalStudioConfig? {
        if (raw.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(raw)
            val customObj = obj.optJSONObject("customFestival")
            val custom = if (customObj != null) {
                jsonToFestival(customObj, defaultPreset = "default")
            } else {
                FestivalConfig(stylePreset = "diwali")
            }

            val hasCustomArray = obj.has("customFestivals")
            val customArr = obj.optJSONArray("customFestivals")
            val customList = if (hasCustomArray && customArr != null) {
                (0 until minOf(customArr.length(), 30)).mapNotNull { idx ->
                    customArr.optJSONObject(idx)?.let { jsonToFestival(it, defaultPreset = "default") }
                }
            } else {
                val hasLegacy = custom.enabled ||
                    custom.festivalName.isNotBlank() ||
                    custom.greetingMessage.isNotBlank() ||
                    custom.bannerImageUrl.isNotBlank()
                if (hasLegacy) listOf(custom) else emptyList()
            }

            val hiddenArr = obj.optJSONArray("hiddenAutoFestivalKeys")
            val hiddenKeys = if (hiddenArr != null) {
                (0 until minOf(hiddenArr.length(), 50)).mapNotNull { idx ->
                    hiddenArr.optString(idx, "").trim().take(60).takeIf { it.isNotEmpty() }
                }
            } else {
                emptyList()
            }

            val annObj = obj.optJSONObject("announcement")
            val announcement = if (annObj != null) {
                val sev = annObj.optString("severity", "info").lowercase()
                val validSev = when (sev) {
                    "warning", "alert", "success" -> sev
                    else -> "info"
                }
                AnnouncementConfig(
                    enabled = annObj.optBoolean("enabled", false),
                    message = annObj.optString("message", "").trim().take(300),
                    severity = validSev,
                    dismissible = annObj.optBoolean("dismissible", true),
                    startAtMillis = annObj.optLong("startAtMillis", 0L).coerceAtLeast(0L),
                    endAtMillis = annObj.optLong("endAtMillis", 0L).coerceAtLeast(0L)
                )
            } else {
                AnnouncementConfig()
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
                customFestival = customList.firstOrNull() ?: custom,
                customFestivals = customList,
                hiddenAutoFestivalKeys = hiddenKeys,
                announcement = announcement,
                updatedAt = obj.optLong("updatedAt", 0L).coerceAtLeast(0L)
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

        fun resetForTest(context: Context) {
            synchronized(FestivalPreferenceManager::class.java) {
                isInitialized = false
                _currentConfig.value = FestivalStudioConfig()
                context.applicationContext.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_STUDIO_JSON)
                    .commit()
            }
        }
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
        val list = config.effectiveCustomFestivals()
        val normalized = config.copy(
            customFestival = list.firstOrNull() ?: FestivalConfig(),
            customFestivals = list,
            updatedAt = if (config.updatedAt > 0L) config.updatedAt else System.currentTimeMillis()
        )
        val json = FestivalStudioCodec.toJsonString(normalized)
        prefs.edit().putString(KEY_STUDIO_JSON, json).apply()
        _currentConfig.value = normalized
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
        val sanitizedJson = FestivalStudioCodec.toJsonString(parsed)
        prefs.edit().putString(KEY_STUDIO_JSON, sanitizedJson).apply()
        _currentConfig.value = parsed
    }
}
