package com.example.data.pref

import android.content.Context
import androidx.annotation.DrawableRes
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLogoStyle(
    val id: String,
    val title: String,
    val description: String,
    @DrawableRes val resId: Int
) {
    ORIGINAL(
        id = "original",
        title = "Classic Hardware Logo",
        description = "Original minimal wrench and carton icon",
        resId = R.drawable.ic_stock_logo
    )
}

class LogoPreferenceManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("app_logo_pref", Context.MODE_PRIVATE)

    companion object {
        private var isInitialized = false
        private val _selectedLogo = MutableStateFlow(AppLogoStyle.ORIGINAL)
        private val _bannerUrl = MutableStateFlow<String?>(null)
        private val _customLogoUrl = MutableStateFlow<String?>(null)

        val selectedLogo: StateFlow<AppLogoStyle> = _selectedLogo.asStateFlow()
        val bannerUrl: StateFlow<String?> = _bannerUrl.asStateFlow()
        val customLogoUrl: StateFlow<String?> = _customLogoUrl.asStateFlow()
    }

    init {
        synchronized(LogoPreferenceManager::class.java) {
            if (!isInitialized) {
                _selectedLogo.value = loadLogo()
                _bannerUrl.value = prefs.getString("custom_banner_url", null)
                _customLogoUrl.value = prefs.getString("custom_logo_url", null)
                isInitialized = true
            }
        }
    }

    val selectedLogo: StateFlow<AppLogoStyle> = Companion.selectedLogo
    val bannerUrl: StateFlow<String?> = Companion.bannerUrl
    val customLogoUrl: StateFlow<String?> = Companion.customLogoUrl

    private fun loadLogo(): AppLogoStyle {
        val savedId = prefs.getString("selected_logo_id", AppLogoStyle.ORIGINAL.id)
        return AppLogoStyle.values().find { it.id == savedId } ?: AppLogoStyle.ORIGINAL
    }

    fun selectLogo(logo: AppLogoStyle) {
        prefs.edit().putString("selected_logo_id", logo.id).apply()
        _selectedLogo.value = logo
    }

    fun getBannerUrl(): String? = _bannerUrl.value ?: prefs.getString("custom_banner_url", null)

    fun saveBannerUrl(url: String?) {
        prefs.edit().putString("custom_banner_url", url).apply()
        _bannerUrl.value = url
    }

    fun getCustomLogoUrl(): String? = _customLogoUrl.value ?: prefs.getString("custom_logo_url", null)

    fun saveCustomLogoUrl(url: String?) {
        prefs.edit().putString("custom_logo_url", url).apply()
        _customLogoUrl.value = url
    }

    fun applyRemoteUrls(bannerUrl: String?, logoUrl: String?) {
        if (bannerUrl != null) {
            val cleanBanner = bannerUrl.takeIf { it.isNotBlank() }
            if (cleanBanner != _bannerUrl.value) saveBannerUrl(cleanBanner)
        }
        if (logoUrl != null) {
            val cleanLogo = logoUrl.takeIf { it.isNotBlank() }
            if (cleanLogo != _customLogoUrl.value) saveCustomLogoUrl(cleanLogo)
        }
    }
}
