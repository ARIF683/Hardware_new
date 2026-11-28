package com.example.ui



import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthManager
import com.example.data.model.ColorTintLog
import com.example.data.model.DailyCashflowRecord
import com.example.data.model.AnnouncementConfig
import com.example.data.model.AutoFestivalCalendar
import com.example.data.model.FestivalConfig
import com.example.data.model.Item
import com.example.data.model.TransactionRecord
import com.example.data.model.LedgerAccount
import com.example.data.model.LedgerEntry
import com.example.data.model.QuotationRecord
import com.example.data.model.GstBillRecord
import com.example.data.pref.AppSettingsManager
import com.example.data.pref.AppLogoStyle
import com.example.data.pref.BankingInfo
import com.example.data.pref.BankingPreferenceManager
import com.example.data.pref.FestivalPreferenceManager
import com.example.data.pref.FestivalStudioConfig
import com.example.data.pref.LogoPreferenceManager
import com.example.data.pref.StoreIdentity
import com.example.data.pref.StoreProfilePreferenceManager
import com.example.data.remote.AppUpdateManager
import com.example.data.remote.UpdateStatus
import com.example.data.repository.BillRowData
import com.example.data.repository.StockRepository
import com.example.util.DateTimeHelper
import com.example.util.InvoicePrintManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

sealed class CurrentScreen {
    object Main : CurrentScreen()
    data class ItemDetail(val itemId: String) : CurrentScreen()
    object BillFlow : CurrentScreen()
    data class LedgerAccountDetail(val accountId: String) : CurrentScreen()
    object GstCalculator : CurrentScreen()
    object SmartSynonymSearch : CurrentScreen()
    object AuditLogs : CurrentScreen()
}

enum class NavigationTab {
    HOME, ITEMS, BILLING, TRANSACTIONS, SETTINGS
}

data class GroupStat(
    val groupKey: String,
    val itemCount: Int,
    val totalQty: Double,
    val percentage: Int
)

data class StockFlowDayPoint(
    val dayLabel: String,
    val dateKey: String,
    val netQuantity: Double,
    val inQty: Double,
    val outQty: Double
)

class StockViewModel(application: Application) : AndroidViewModel(application) {
    val authManager = AuthManager(application)
    val appSettingsManager = AppSettingsManager(application)
    val repository = StockRepository(application, authManager = authManager, appSettingsManager = appSettingsManager)
    val appUpdateManager = AppUpdateManager(application)
    val logoPreferenceManager = LogoPreferenceManager(application)
    val bankingPreferenceManager = BankingPreferenceManager(application)
    val storeProfilePreferenceManager = StoreProfilePreferenceManager(application)
    val festivalPreferenceManager = FestivalPreferenceManager(application)
    val bankingInfo: StateFlow<BankingInfo> = bankingPreferenceManager.currentBankingInfo
    val storeIdentity: StateFlow<StoreIdentity> = storeProfilePreferenceManager.currentStoreIdentity
    val festivalStudioConfig: StateFlow<FestivalStudioConfig> = festivalPreferenceManager.currentConfig

    val invoicePrefix: StateFlow<String> = appSettingsManager.invoicePrefix
    val nextInvoiceNum: StateFlow<Int> = appSettingsManager.nextInvoiceNum
    val invoicePadDigits: StateFlow<Int> = appSettingsManager.invoicePadDigits
    val invoiceIncludeFy: StateFlow<Boolean> = appSettingsManager.invoiceIncludeFy
    val quotePrefix: StateFlow<String> = appSettingsManager.quotePrefix
    val nextQuoteNum: StateFlow<Int> = appSettingsManager.nextQuoteNum
    val quotePadDigits: StateFlow<Int> = appSettingsManager.quotePadDigits
    val lowStockAlertsEnabled: StateFlow<Boolean> = appSettingsManager.lowStockAlertsEnabled
    val lowStockDefaultThreshold: StateFlow<Int> = appSettingsManager.lowStockDefaultThreshold
    val instantDispatchSlip: StateFlow<Boolean> = appSettingsManager.instantDispatchSlip

    fun setLowStockAlertsEnabled(enabled: Boolean) {
        appSettingsManager.setLowStockAlertsEnabled(enabled)
        showToast(if (enabled) "Low Stock Alerts Enabled" else "Low Stock Alerts Paused")
    }

    fun setLowStockDefaultThreshold(threshold: Int) {
        appSettingsManager.setLowStockDefaultThreshold(threshold)
        showToast("Default low stock threshold updated to $threshold")
    }

    fun setInstantDispatchSlip(enabled: Boolean) {
        appSettingsManager.setInstantDispatchSlip(enabled)
        showToast(if (enabled) "Instant dispatch prompt enabled" else "Instant dispatch prompt disabled")
    }

    fun setInvoiceSequence(
        prefix: String,
        nextNum: Int,
        padDigits: Int = invoicePadDigits.value,
        includeFy: Boolean = invoiceIncludeFy.value,
        quotePrefix: String = this.quotePrefix.value,
        nextQuoteNum: Int = this.nextQuoteNum.value,
        quotePadDigits: Int = this.quotePadDigits.value
    ) {
        if (!isAdmin.value) {
            showToast("Only Admin can update invoice sequence")
            return
        }
        viewModelScope.launch {
            repository.saveAndSyncInvoiceSequence(
                prefix = prefix,
                nextNum = nextNum,
                padDigits = padDigits,
                includeFy = includeFy,
                quotePrefix = quotePrefix,
                nextQuoteNum = nextQuoteNum,
                quotePadDigits = quotePadDigits
            )
            val previewInv = previewNextInvoiceNo()
            val previewQuote = previewNextQuoteNo()
            showToast("Sequence saved · Invoice: $previewInv · Estimate: $previewQuote")
        }
    }

    fun previewNextInvoiceNo(): String {
        val existingNos = allGstBills.value.map { it.invoiceNo }
        return appSettingsManager.previewNextInvoiceNo(existingNos)
    }

    fun consumeNextInvoiceNo(): String {
        val existingNos = allGstBills.value.map { it.invoiceNo }
        return appSettingsManager.consumeNextInvoiceNo(existingNos)
    }

    fun markInvoiceNoUsed(invoiceNo: String) {
        val existingNos = allGstBills.value.map { it.invoiceNo }
        appSettingsManager.advanceInvoiceCounterIfUsed(invoiceNo, existingNos)
    }

    fun previewNextQuoteNo(): String {
        val existingNos = allQuotations.value.map { it.quotationNo }
        return appSettingsManager.previewNextQuoteNo(existingNos)
    }

    fun consumeNextQuoteNo(): String {
        val existingNos = allQuotations.value.map { it.quotationNo }
        return appSettingsManager.consumeNextQuoteNo(existingNos)
    }

    fun markQuoteNoUsed(quotationNo: String) {
        val existingNos = allQuotations.value.map { it.quotationNo }
        appSettingsManager.advanceQuoteCounterIfUsed(quotationNo, existingNos)
    }

    private val themePrefs = application.getSharedPreferences("theme_prefs", android.content.Context.MODE_PRIVATE)
    private val _customThemeColor = MutableStateFlow(
        themePrefs.getString("theme_color", "#EA580C") ?: "#EA580C"
    )
    val customThemeColor: StateFlow<String> = _customThemeColor.asStateFlow()

    private val _themeMode = MutableStateFlow(themePrefs.getString("theme_mode", "system") ?: "system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _isDailyReminderEnabled = MutableStateFlow(
        com.example.notifications.DailyReminderScheduler.isEnabled(application)
    )
    val isDailyReminderEnabled: StateFlow<Boolean> = _isDailyReminderEnabled.asStateFlow()

    fun setDailyReminderEnabled(enabled: Boolean) {
        val app = getApplication<Application>()
        com.example.notifications.DailyReminderScheduler.setEnabled(app, enabled)
        _isDailyReminderEnabled.value = enabled
        showToast(
            if (enabled) "Daily 7:00 – 7:30 PM sales & expenses reminder active"
            else "Evening reminder paused"
        )
    }

    fun sendTestDailyReminder() {
        val app = getApplication<Application>()
        val sent = com.example.notifications.DailyReminderScheduler.showReminderNotification(
            app,
            forceTest = true
        )
        if (!sent) {
            showToast("Enable notification permission to preview the evening reminder")
        } else {
            showToast("Evening sales & expenses reminder notification sent")
        }
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        themePrefs.edit().putString("theme_mode", mode).apply()
        showToast("Switched to ${when(mode) { "light" -> "Light Mode"; "dark" -> "Dark Mode"; else -> "System Default" }}")
    }

    fun setCustomThemeColor(hexColor: String) {
        _customThemeColor.value = hexColor
        themePrefs.edit().putString("theme_color", hexColor).apply()
        showToast("Theme changed successfully")
    }

    private val layoutPrefs = application.getSharedPreferences("layout_prefs", android.content.Context.MODE_PRIVATE)
    private val _itemsViewLayout = MutableStateFlow(layoutPrefs.getString("items_layout", "list") ?: "list")
    val itemsViewLayout: StateFlow<String> = _itemsViewLayout.asStateFlow()

    fun setItemsViewLayout(layout: String) {
        _itemsViewLayout.value = layout
        layoutPrefs.edit().putString("items_layout", layout).apply()
        showToast(if (layout == "list") "Switched to List View (Showcase)" else "Switched to 2-Column Grid")
    }

    private val _navBarStyle = MutableStateFlow(layoutPrefs.getString("nav_style", "classic") ?: "classic")
    val navBarStyle: StateFlow<String> = _navBarStyle.asStateFlow()
    fun setNavBarStyle(style: String) {
        _navBarStyle.value = style
        layoutPrefs.edit().putString("nav_style", style).apply()
        showToast("Navigation style updated")
    }

    private val _cardStyle = MutableStateFlow(layoutPrefs.getString("card_style", "modern_inset") ?: "modern_inset")
    val cardStyle: StateFlow<String> = _cardStyle.asStateFlow()
    fun setCardStyle(style: String) {
        _cardStyle.value = style
        layoutPrefs.edit().putString("card_style", style).apply()
        showToast("Card style updated")
    }

    val selectedLogo: StateFlow<AppLogoStyle> = logoPreferenceManager.selectedLogo
    val bannerUrl: StateFlow<String?> = logoPreferenceManager.bannerUrl
    val customLogoUrl: StateFlow<String?> = logoPreferenceManager.customLogoUrl

    fun selectLogo(logo: AppLogoStyle) {
        if (!isAdmin.value) {
            showToast("Admin access required")
            return
        }
        logoPreferenceManager.selectLogo(logo)
        logoPreferenceManager.saveCustomLogoUrl(null)
        viewModelScope.launch {
            try {
                repository.saveStoreProfile(bannerUrl.value, null)
            } catch (e: Exception) {
                Log.e("StockViewModel", "Failed to sync logo to Supabase", e)
            }
        }
        showToast("Logo changed to ${logo.title}")
    }

    fun updateStoreBanner(url: String?) {
        if (!isAdmin.value) {
            showToast("Admin access required")
            return
        }
        logoPreferenceManager.saveBannerUrl(url)
        viewModelScope.launch {
            try {
                repository.saveStoreProfile(url, customLogoUrl.value)
            } catch (e: Exception) {
                Log.e("StockViewModel", "Failed to sync banner to Supabase", e)
            }
        }
        showToast("Store banner updated & saved!")
    }

    fun updateStoreLogo(url: String?) {
        if (!isAdmin.value) {
            showToast("Admin access required")
            return
        }
        logoPreferenceManager.saveCustomLogoUrl(url)
        viewModelScope.launch {
            try {
                repository.saveStoreProfile(bannerUrl.value, url)
            } catch (e: Exception) {
                Log.e("StockViewModel", "Failed to sync logo to Supabase", e)
            }
        }
        showToast("Store logo updated & saved!")
    }

    val effectiveStoreTitle: StateFlow<String> = storeIdentity.map { identity ->
        identity.storeName.ifBlank { "S.A. HARDWARE" }
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        storeIdentity.value.storeName.ifBlank { "S.A. HARDWARE" }
    )
    val activeFestivals: StateFlow<List<FestivalConfig>> = festivalStudioConfig.map { studio ->
        AutoFestivalCalendar.resolveEffectiveFestivals(studio)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AutoFestivalCalendar.resolveEffectiveFestivals(festivalStudioConfig.value)
    )
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeAnnouncement: StateFlow<AnnouncementConfig> = festivalStudioConfig.transformLatest { studio ->
        val raw = studio.announcement
        while (true) {
            val now = System.currentTimeMillis()
            val live = if (raw.isActiveAt(now)) raw else raw.copy(enabled = false)
            emit(live)
            val nextWake = when {
                !raw.enabled || raw.message.isBlank() -> null
                raw.startAtMillis > 0L && now < raw.startAtMillis -> raw.startAtMillis
                raw.endAtMillis > 0L && now < raw.endAtMillis -> raw.endAtMillis
                else -> null
            } ?: break
            val waitMs = (nextWake - now + 100L).coerceIn(1000L, 3_600_000L)
            delay(waitMs)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        festivalStudioConfig.value.announcement.let { raw ->
            if (raw.isActiveAt()) raw else raw.copy(enabled = false)
        }
    )
    val effectiveThemeColor: StateFlow<String> = combine(
        _customThemeColor,
        festivalStudioConfig,
        activeFestivals
    ) { manualHex, studio, active ->
        AutoFestivalCalendar.resolveEffectiveThemeHex(studio, active, manualHex)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AutoFestivalCalendar.resolveEffectiveThemeHex(
            festivalStudioConfig.value,
            activeFestivals.value,
            _customThemeColor.value
        )
    )
    val updateStatus: StateFlow<UpdateStatus> = appUpdateManager.status

    val isAnnouncementDismissed = MutableStateFlow(false)
    val isUpdateBannerDismissed = MutableStateFlow(false)
    val dismissedUpdateTag = MutableStateFlow<String?>(null)
    private var lastUpdateCheckMs: Long = 0L

    val isAuthed: StateFlow<Boolean> = authManager.isAuthed
    val isAdmin: StateFlow<Boolean> = authManager.isAdmin
    val currentUserEmail: StateFlow<String?> = authManager.currentUserEmail
    val lockRemainingSeconds: StateFlow<Int> = authManager.lockRemainingSeconds

    val isCloudConfigured: Boolean = repository.isCloudConfigured()
    val syncStatus: StateFlow<String> = repository.syncStatus
    val isRealtimeLive: StateFlow<Boolean> = repository.isRealtimeLive
    val pendingQueueCount: StateFlow<Int> = repository.pendingQueueCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val allItems: StateFlow<List<Item>> = repository.allItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTransactions: StateFlow<List<TransactionRecord>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentTransactions: StateFlow<List<TransactionRecord>> = repository.recentTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stockFlow7Days: StateFlow<List<StockFlowDayPoint>> = allTransactions.combine(repository.allItems) { txs, _ ->
        val dayFormat = java.text.SimpleDateFormat("EEE", java.util.Locale.ENGLISH)

        // Generate last 7 days in chronological order using local timezone
        val days = (6 downTo 0).map { daysAgo ->
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
            val dateKey = DateTimeHelper.localDateKey(cal.timeInMillis)
            val dayLabel = if (daysAgo == 0) "Today" else dayFormat.format(cal.time)
            Triple(dateKey, dayLabel, daysAgo)
        }

        days.map { (dateKey, dayLabel, _) ->
            val dayTxs = txs.filter { DateTimeHelper.utcIsoToLocalDateKey(it.createdAt) == dateKey }
            val inQty = dayTxs.filter { it.action == "in" }.sumOf { it.qty }
            val outQty = dayTxs.filter { it.action == "out" }.sumOf { it.qty }
            StockFlowDayPoint(
                dayLabel = dayLabel,
                dateKey = dateKey,
                netQuantity = inQty - outQty,
                inQty = inQty,
                outQty = outQty
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayQtyDelta: StateFlow<Double> = allTransactions.combine(repository.allItems) { txs, _ ->
        val todayKey = DateTimeHelper.localDateKey()
        val todayTxs = txs.filter { DateTimeHelper.utcIsoToLocalDateKey(it.createdAt) == todayKey }
        val inQty = todayTxs.filter { it.action == "in" }.sumOf { it.qty }
        val outQty = todayTxs.filter { it.action == "out" }.sumOf { it.qty }
        inQty - outQty
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val allQuotations: StateFlow<List<QuotationRecord>> = repository.allQuotations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allGstBills: StateFlow<List<GstBillRecord>> = repository.allGstBills
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLedgerAccounts: StateFlow<List<LedgerAccount>> = repository.allLedgerAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLedgerEntries: StateFlow<List<LedgerEntry>> = repository.allLedgerEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDailyCashflow: StateFlow<List<DailyCashflowRecord>> = repository.allDailyCashflow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTintLogs: StateFlow<List<ColorTintLog>> = repository.allTintLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentScreen = MutableStateFlow<CurrentScreen>(CurrentScreen.Main)
    val currentScreen: StateFlow<CurrentScreen> = _currentScreen.asStateFlow()

    val allAuditLogs: StateFlow<List<com.example.data.local.AuditLogEntity>> = combine(
        authManager.isAdmin,
        repository.allAuditLogs
    ) { admin, logs ->
        if (admin) logs else emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun syncAuditLogs() {
        if (!authManager.isAdmin.value) return
        viewModelScope.launch {
            repository.syncAuditLogsFromCloud()
        }
    }

    fun openAuditLogs() {
        if (authManager.isAdmin.value) {
            _currentScreen.value = CurrentScreen.AuditLogs
        } else {
            showToast("Admin access required to view audit logs.")
        }
    }

    fun closeAuditLogs() {
        _currentScreen.value = CurrentScreen.Main
    }



    // --- Multi-Unit & Packaging Converter ---
    fun updateItemPackaging(itemId: String, secondaryUnit: String, conversionFactor: Double, secondaryPrice: Double) {
        viewModelScope.launch {
            val item = allItems.value.find { it.id == itemId } ?: return@launch
            val updated = item.copy(
                secondaryUnit = secondaryUnit,
                conversionFactor = if (conversionFactor > 0) conversionFactor else 1.0,
                secondaryPrice = secondaryPrice
            )
            repository.updateItem(updated)
            showToast("Packaging updated: 1 $secondaryUnit = ${conversionFactor.toInt()} ${item.unit}")
        }
    }


    private val _currentTab = MutableStateFlow(NavigationTab.HOME)
    val currentTab: StateFlow<NavigationTab> = _currentTab.asStateFlow()

    // Filters and UI state
    val searchQuery = MutableStateFlow("")
    val groupBy = MutableStateFlow("none") // "none", "name", "cost", "price", "type", "brand", "size", "mrp", "unit"
    val selectedGroup = MutableStateFlow<String?>(null)
    val inStockOnly = MutableStateFlow(false)
    val sortOption = MutableStateFlow("o") // "o", "n", "nz", "q", "c"
    val requestedStockStatusFilter = MutableStateFlow<String?>(null)
    val selectedItemIds = MutableStateFlow<Set<String>>(emptySet())
    val isSelectionMode = MutableStateFlow(false)

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    fun showToast(msg: String) {
        viewModelScope.launch { _toastEvent.emit(msg) }
    }

    init {
        // One-time migration if legacy tactical lock was saved without user preference
        if (!themePrefs.getBoolean("migrated_v2_theme", false)) {
            val savedColor = themePrefs.getString("theme_color", null)
            val editor = themePrefs.edit().putBoolean("migrated_v2_theme", true)
            if (savedColor == "#10B981" || savedColor == null) {
                editor.putString("theme_color", "#EA580C")
                _customThemeColor.value = "#EA580C"
            }
            if (themePrefs.getString("theme_mode", null) == "dark") {
                editor.putString("theme_mode", "system")
                _themeMode.value = "system"
            }
            editor.apply()
        }
        // Trigger initial remote load only when authenticated; otherwise wait for login
        viewModelScope.launch {
            if (authManager.isAuthed.value) {
                repository.loadFromRemote()
            }
        }
        viewModelScope.launch {
            var wasAuthed = authManager.isAuthed.value
            authManager.isAuthed.collect { authedNow ->
                if (!wasAuthed && authedNow) {
                    repository.resumeBackgroundWork()
                    repository.initRealtime(force = true)
                    repository.loadFromRemote()
                } else if (wasAuthed && !authedNow) {
                    repository.pauseBackgroundWork()
                }
                wasAuthed = authedNow
            }
        }
        // Reset announcement dismissal whenever a new announcement arrives from Supabase
        viewModelScope.launch {
            activeAnnouncement.collect {
                isAnnouncementDismissed.value = false
            }
        }
        // Check for latest APK updates from GitHub Releases
        viewModelScope.launch {
            appUpdateManager.checkForUpdates()
        }
        // Ensure permanent daily 7:00-7:30 PM sales & expenses reminder is scheduled
        com.example.notifications.DailyReminderScheduler.ensureScheduled(application)
    }

    fun saveAndSyncFestivalStudio(
        config: FestivalStudioConfig,
        localBannerUris: Map<Int, android.net.Uri> = emptyMap()
    ) {
        if (!isAdmin.value) {
            showToast("Admin access required")
            return
        }
        viewModelScope.launch {
            isAnnouncementDismissed.value = false
            val uploadOk = repository.saveAndSyncFestivalStudio(config, localBannerUris)
            if (uploadOk) {
                showToast("Announcement Studio saved & synced across devices!")
            } else {
                showToast("Saved Announcement Studio settings. Custom image upload requires internet.")
            }
        }
    }

    fun refreshCurrentUserRole(showFeedback: Boolean = false) {
        authManager.refreshCurrentUserRole { resolvedAdmin ->
            if (showFeedback) {
                showToast(
                    if (resolvedAdmin) "Admin role verified & active"
                    else "Role synced: Staff Operator"
                )
            }
        }
    }

    fun syncUserRolesFromCloud() {
        authManager.syncUsersFromCloud { ok ->
            if (ok) {
                showToast("User roles synced from database")
            }
        }
    }

    fun dismissUpdateDialog(tagName: String) {
        dismissedUpdateTag.value = tagName
    }

    fun checkForUpdatesOnResume() {
        val current = appUpdateManager.status.value
        if (current is UpdateStatus.Downloading || current is UpdateStatus.Checking) return
        val now = System.currentTimeMillis()
        if (current !is UpdateStatus.Error && now - lastUpdateCheckMs < 5 * 60 * 1000L) return
        lastUpdateCheckMs = now
        viewModelScope.launch {
            appUpdateManager.checkForUpdates()
        }
    }

    fun checkForUpdates() {
        dismissedUpdateTag.value = null
        isUpdateBannerDismissed.value = false
        lastUpdateCheckMs = System.currentTimeMillis()
        viewModelScope.launch {
            val result = appUpdateManager.checkForUpdates()
            val statusAfter = appUpdateManager.status.value
            when {
                result != null && result.isNewerThanCurrent -> {
                    showToast("New update found: ${result.tagName}")
                }
                statusAfter is UpdateStatus.Error -> {
                    showToast("Update check failed: ${statusAfter.message}")
                }
                else -> {
                    showToast("You are on the latest version!")
                }
            }
        }
    }

    fun startDownloadAndInstall(url: String) {
        viewModelScope.launch {
            appUpdateManager.downloadAndInstall(url)
        }
    }

    val transactionsSubTab = MutableStateFlow(0) // 0 = Sales & Expenses (P&L), 1 = Stock Movement

    fun setTransactionsSubTab(subTab: Int) {
        transactionsSubTab.value = subTab.coerceIn(0, 1)
    }

    fun navigateTo(tab: NavigationTab) {
        if (tab == NavigationTab.TRANSACTIONS) {
            billingKhataSubTab.value = 4
            _currentTab.value = NavigationTab.BILLING
        } else {
            _currentTab.value = tab
        }
        _currentScreen.value = CurrentScreen.Main
        selectedGroup.value = null
        groupBy.value = "none"
        selectedItemIds.value = emptySet()
        isSelectionMode.value = false
    }

    val billingKhataSubTab = MutableStateFlow(0) // 0 = POS, 1 = Quotes / Estimates, 2 = GST Bills & Invoices, 3 = Khata Ledger, 4 = Transactions

    fun setBillingKhataSubTab(subTab: Int) {
        billingKhataSubTab.value = subTab.coerceIn(0, 4)
    }

    fun navigateToPos() {
        billingKhataSubTab.value = 0
        navigateTo(NavigationTab.BILLING)
    }

    fun navigateToQuotes() {
        billingKhataSubTab.value = 1
        navigateTo(NavigationTab.BILLING)
    }

    fun navigateToGstBills() {
        billingKhataSubTab.value = 2
        navigateTo(NavigationTab.BILLING)
    }

    fun navigateToKhata() {
        billingKhataSubTab.value = 3
        navigateTo(NavigationTab.BILLING)
    }

    fun navigateToCashflow(subTab: Int = 0) {
        transactionsSubTab.value = subTab.coerceIn(0, 1)
        billingKhataSubTab.value = 4
        navigateTo(NavigationTab.BILLING)
    }

    fun openItemDetail(itemId: String) {
        _currentScreen.value = CurrentScreen.ItemDetail(itemId)
    }

    fun openBillFlow() {
        _currentScreen.value = CurrentScreen.BillFlow
    }

    val prefilledQuotationForGst = MutableStateFlow<QuotationRecord?>(null)

    fun openGstCalculator() {
        prefilledQuotationForGst.value = null
        _currentScreen.value = CurrentScreen.GstCalculator
    }

    fun openGstCalculatorWithQuotation(quotation: QuotationRecord) {
        prefilledQuotationForGst.value = quotation
        _currentScreen.value = CurrentScreen.GstCalculator
    }

    fun clearPrefilledGstQuotation() {
        prefilledQuotationForGst.value = null
    }

    fun openSmartSynonymSearch() {
        _currentScreen.value = CurrentScreen.SmartSynonymSearch
    }

    fun navigateBack(): Boolean {
        if (_currentScreen.value !is CurrentScreen.Main) {
            _currentScreen.value = CurrentScreen.Main
            return true
        }
        if (selectedGroup.value != null) {
            selectedGroup.value = null
            return true
        }
        if (isSelectionMode.value) {
            isSelectionMode.value = false
            selectedItemIds.value = emptySet()
            return true
        }
        return false
    }

    data class FilterState(
        val query: String,
        val gb: String,
        val sGrp: String?,
        val inStock: Boolean,
        val sort: String
    )

    private val filterStateFlow = combine(
        searchQuery,
        groupBy,
        selectedGroup,
        inStockOnly,
        sortOption
    ) { q, gb, sg, ins, sort ->
        FilterState(q, gb, sg, ins, sort)
    }

    // Filtered items logic
    val filteredItems: StateFlow<List<Item>> = combine(
        allItems,
        filterStateFlow
    ) { items: List<Item>, filters: FilterState ->
        var list: List<Item> = items

        // Filter in stock
        if (filters.inStock) {
            list = list.filter { it.qty > 0 }
        }

        // Filter selected group
        if (filters.sGrp != null && filters.gb != "none") {
            list = list.filter { getGroupKey(it, filters.gb) == filters.sGrp }
        }

        // Sorting
        list = when (filters.sort) {
            "n" -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            "nz" -> list.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
            "q" -> list.sortedByDescending { it.qty }
            "p" -> list.sortedByDescending { it.price }
            "c" -> list.sortedByDescending { it.cost }
            else -> list.sortedBy { it.o }
        }

        // Search matching
        val trimmedQuery = filters.query.trim()
        if (trimmedQuery.isNotEmpty()) {
            val scored = list.mapNotNull { item: Item ->
                val score = com.example.util.ItemSearchMatcher.matchScore(item, trimmedQuery)
                if (score > 0) Pair(item, score) else null
            }

            scored.sortedByDescending { it.second }
                .map { it.first }
        } else {
            list
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Grouping stats
    val groupStats: StateFlow<List<GroupStat>> = combine(
        allItems,
        groupBy,
        inStockOnly
    ) { items, gb, inStock ->
        if (gb == "none") return@combine emptyList()
        val list = if (inStock) items.filter { it.qty > 0 } else items
        val map = mutableMapOf<String, Pair<Int, Double>>()

        for (item in list) {
            val key = getGroupKey(item, gb)
            val current = map[key] ?: Pair(0, 0.0)
            map[key] = Pair(current.first + 1, current.second + item.qty)
        }

        val totalQtyAll = list.sumOf { maxOf(it.qty, 0.0) }
        map.keys.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it }).map { k ->
            val p = map[k] ?: Pair(0, 0.0)
            val pct = if (totalQtyAll > 0) ((maxOf(p.second, 0.0) / totalQtyAll) * 100).toInt() else 0
            GroupStat(
                groupKey = k,
                itemCount = p.first,
                totalQty = p.second,
                percentage = pct
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun getGroupKey(item: Item, gb: String): String {
        return when (gb) {
            "name" -> item.name.ifEmpty { "—" }
            "cost" -> formatRupees(item.cost)
            "price" -> formatRupees(item.price)
            "type" -> item.type.ifEmpty { "—" }
            "brand" -> item.brand.ifEmpty { "—" }
            "size" -> item.size.ifEmpty { "—" }
            "mrp" -> item.mrp?.let { formatRupees(it) } ?: "—"
            "unit" -> item.unit.ifEmpty { "pcs" }
            else -> "—"
        }
    }

    fun toggleItemSelection(id: String) {
        val current = selectedItemIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        selectedItemIds.value = current
        isSelectionMode.value = current.isNotEmpty()
    }

    fun selectAllFiltered() {
        val allIds = filteredItems.value.map { it.id }.toSet()
        selectedItemIds.value = allIds
        isSelectionMode.value = allIds.isNotEmpty()
    }

    fun clearSelection() {
        selectedItemIds.value = emptySet()
        isSelectionMode.value = false
    }

    fun updateItem(item: Item) {
        viewModelScope.launch {
            try {
                repository.saveItem(
                    id = item.id,
                    name = item.name,
                    code = item.code,
                    barcode = item.barcode,
                    type = item.type,
                    brand = item.brand,
                    size = item.size,
                    unit = item.unit,
                    mrp = item.mrp,
                    cost = item.cost,
                    price = item.price,
                    low = item.low,
                    aliases = item.aliases,
                    openingQty = item.qty,
                    imageUrl = item.imageUrl,
                    nfcTagId = item.nfcTagId,
                    shelfLocation = item.shelfLocation,
                    secondaryUnit = item.secondaryUnit,
                    conversionFactor = item.conversionFactor,
                    secondaryPrice = item.secondaryPrice
                )
            } catch (e: Exception) {
                Log.e("StockViewModel", "Failed to update item", e)
            }
        }
    }

    fun saveItem(
        id: String?,
        name: String,
        code: String,
        barcode: String,
        type: String,
        brand: String,
        size: String,
        unit: String,
        mrp: Double?,
        cost: Double,
        price: Double,
        low: Double,
        aliases: String,
        openingQty: Double,
        imageUrl: String? = null,
        nfcTagId: String? = null,
        shelfLocation: String? = null,
        secondaryUnit: String? = null,
        conversionFactor: Double = 1.0,
        secondaryPrice: Double = 0.0
    ) {
        viewModelScope.launch {
            try {
                repository.saveItem(
                    id = id,
                    name = name,
                    code = code,
                    barcode = barcode,
                    type = type,
                    brand = brand,
                    size = size,
                    unit = unit,
                    mrp = mrp,
                    cost = cost,
                    price = price,
                    low = low,
                    aliases = aliases,
                    openingQty = openingQty,
                    imageUrl = imageUrl,
                    nfcTagId = nfcTagId,
                    shelfLocation = shelfLocation,
                    secondaryUnit = secondaryUnit,
                    conversionFactor = conversionFactor,
                    secondaryPrice = secondaryPrice
                )
                showToast(if (id != null) "Item updated" else "Item created")
            } catch (e: Exception) {
                showToast("Failed to save: ${e.message}")
            }
        }
    }

    fun updateItemSellingPrice(itemId: String, newPrice: Double) {
        viewModelScope.launch {
            try {
                val updated = repository.updateItemSellingPrice(itemId, newPrice)
                if (updated != null) {
                    showToast("Selling price updated to ₹${String.format(java.util.Locale.US, "%.2f", newPrice)}")
                } else {
                    showToast("Item not found")
                }
            } catch (e: Exception) {
                showToast("Failed to update price: ${e.message}")
            }
        }
    }

    suspend fun uploadItemImage(bytes: ByteArray): Result<String> {
        return repository.uploadItemImage(bytes)
    }

    fun performTransaction(itemId: String, action: String, qty: Double, note: String) {
        viewModelScope.launch {
            val res = repository.performTransaction(itemId, action, qty, note)
            if (res.isSuccess) {
                showToast("Transaction recorded")
            } else {
                showToast("Error: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun deleteItem(id: String) {
        if (!isAdmin.value) {
            showToast("Admin access required to delete items")
            return
        }
        viewModelScope.launch {
            repository.deleteItem(id)
            if (_currentScreen.value is CurrentScreen.ItemDetail) {
                _currentScreen.value = CurrentScreen.Main
            }
            showToast("Item deleted")
        }
    }

    fun deleteSelectedItems() {
        if (!isAdmin.value) {
            showToast("Admin access required to delete items")
            return
        }
        val ids = selectedItemIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteItems(ids)
            selectedItemIds.value = emptySet()
            isSelectionMode.value = false
            showToast("Deleted ${ids.size} items")
        }
    }

    fun updateSelectedType(type: String) {
        val ids = selectedItemIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.updateBulkType(ids, type)
            selectedItemIds.value = emptySet()
            isSelectionMode.value = false
            showToast("Updated type for ${ids.size} items")
        }
    }

    fun updateSelectedUnit(unit: String) {
        val ids = selectedItemIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.updateBulkUnit(ids, unit)
            selectedItemIds.value = emptySet()
            isSelectionMode.value = false
            showToast("Updated unit for ${ids.size} items")
        }
    }

    fun updateSelectedImage(imageUrl: String) {
        val ids = selectedItemIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.updateBulkImage(ids, imageUrl)
            selectedItemIds.value = emptySet()
            isSelectionMode.value = false
            showToast("Updated photo for ${ids.size} items")
        }
    }

    fun confirmBill(supplier: String, billNo: String, billDate: String, rows: List<BillRowData>, paidInCash: Boolean = false) {
        viewModelScope.launch {
            repository.confirmBill(supplier, billNo, billDate, rows, paidInCash)
            _currentScreen.value = CurrentScreen.Main
            _currentTab.value = NavigationTab.ITEMS
            showToast(if (paidInCash) "Bill confirmed, synced & cash expense recorded ✓" else "Bill confirmed & synced to Khata ✓")
        }
    }

    fun retrySync() {
        viewModelScope.launch {
            if (!repository.isCloudConfigured()) {
                showToast("Running locally · Set SUPABASE_URL & SUPABASE_KEY in Secrets")
                return@launch
            }
            repository.retrySync()
            showToast("Retrying sync…")
        }
    }

    fun clearSyncQueue() {
        viewModelScope.launch {
            repository.clearSyncQueue()
            showToast("Pending sync queue cleared")
        }
    }

    fun ensureRealtimeConnected() {
        viewModelScope.launch {
            repository.resumeBackgroundWork()
        }
    }

    fun pauseBackgroundWork() {
        repository.pauseBackgroundWork()
    }

    override fun onCleared() {
        super.onCleared()
        repository.pauseBackgroundWork()
    }

    fun reconnectRealtime() {
        viewModelScope.launch {
            if (!repository.isCloudConfigured()) {
                repository.initRealtime(force = true)
                showToast("Supabase Secrets not set in this build")
                return@launch
            }
            repository.initRealtime(force = true)
            showToast("Connecting to Realtime…")
        }
    }

    fun reloadFromDatabase(showToast: Boolean = true) {
        viewModelScope.launch {
            if (!repository.isCloudConfigured()) {
                if (showToast) {
                    showToast("Running locally · Set SUPABASE_URL & SUPABASE_KEY in Secrets")
                }
                return@launch
            }
            repository.loadFromRemote()
            if (showToast) {
                showToast("Reloaded from database")
            }
        }
    }

    fun uploadAllToDatabase() {
        viewModelScope.launch {
            if (!repository.isCloudConfigured()) {
                showToast("Set SUPABASE_URL & SUPABASE_KEY in Secrets to enable cloud sync")
                return@launch
            }
            repository.uploadAllToDatabase()
            val status = repository.syncStatus.value
            if (status.startsWith("Synced")) {
                showToast("All cashflow & data successfully synced to DB ✓")
            } else {
                showToast("Sync result: $status")
            }
        }
    }

    fun clearTransactionHistory() {
        if (!isAdmin.value) {
            showToast("Admin access required to clear transaction history")
            return
        }
        viewModelScope.launch {
            repository.clearTransactionHistory()
            showToast("Transaction history cleared")
        }
    }

    fun deleteAllItems() {
        if (!isAdmin.value) {
            showToast("Admin access required to delete all data")
            return
        }
        viewModelScope.launch {
            repository.deleteAllData()
            showToast("All data (items, cashflow, khata, quotes) deleted")
        }
    }

    fun deleteAllData() {
        deleteAllItems()
    }

    fun importItems(items: List<Item>, replace: Boolean) {
        viewModelScope.launch {
            repository.importItems(items, replace)
            showToast("Imported ${items.size} items")
        }
    }

    // ==================== QUOTATION ACTIONS ====================
    fun saveQuotation(quotation: QuotationRecord) {
        viewModelScope.launch {
            repository.saveQuotation(quotation)
            showToast("Saved Estimate #${quotation.quotationNo}")
        }
    }

    fun updateQuotationStatus(quotation: QuotationRecord, newStatus: String) {
        viewModelScope.launch {
            val updated = quotation.copy(status = newStatus)
            repository.saveQuotation(updated)
            showToast("Estimate #${quotation.quotationNo} marked as $newStatus")
        }
    }

    fun deleteQuotation(id: String) {
        viewModelScope.launch {
            repository.deleteQuotation(id)
            showToast("Estimate deleted")
        }
    }

    fun convertQuotationToSale(quotation: QuotationRecord) {
        viewModelScope.launch {
            val result = repository.convertQuotationToSale(quotation)
            if (result.isSuccess) {
                showToast("Converted to Sale! Stock deducted & logged ✓")
            } else {
                showToast("Failed to convert: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    // ==================== GST BILL ACTIONS ====================
    fun saveGstBill(bill: GstBillRecord) {
        viewModelScope.launch {
            repository.saveGstBill(bill)
            showToast("GST Bill #${bill.invoiceNo} saved in Billing ✓")
        }
    }

    fun deleteGstBill(id: String) {
        viewModelScope.launch {
            repository.deleteGstBill(id)
            showToast("GST Bill deleted")
        }
    }

    fun completePosOrGstSale(
        bill: GstBillRecord,
        lineItems: List<com.example.data.model.GstBillLineItem>,
        deductStock: Boolean = true,
        recordCashflowSale: Boolean = true,
        paymentMode: String = "Cash",
        creditAmountToKhata: Double = 0.0,
        customerKhataAccountId: String? = null,
        creditDueDate: String = "",
        onComplete: ((Result<Int>) -> Unit)? = null
    ) {
        val sourceQuotation = prefilledQuotationForGst.value
        prefilledQuotationForGst.value = null
        viewModelScope.launch {
            val res = repository.saveAndFinalizeGstBill(
                bill = bill,
                lineItems = lineItems,
                deductStock = deductStock,
                recordCashflowSale = recordCashflowSale,
                paymentMode = paymentMode,
                prefilledQuotation = sourceQuotation,
                creditAmountToKhata = creditAmountToKhata,
                customerKhataAccountId = customerKhataAccountId,
                creditDueDate = creditDueDate
            )
            if (res.isSuccess) {
                val deductedCount = res.getOrDefault(0)
                val stockNote = if (deductStock && deductedCount > 0) " · Stock deducted" else ""
                val khataNote = if (creditAmountToKhata > 0.005) " · Khata updated" else ""
                showToast("Invoice #${bill.invoiceNo} saved$stockNote$khataNote ✓")
            } else {
                showToast("Failed to save invoice: ${res.exceptionOrNull()?.message}")
            }
            onComplete?.invoke(res)
        }
    }

    fun processSalesReturn(
        bill: GstBillRecord,
        returnMap: Map<String, Double>,
        restock: Boolean,
        reason: String
    ) {
        viewModelScope.launch {
            try {
                val lineItems = InvoicePrintManager.parseGstLineItems(bill.itemsJson)
                val allInventory = repository.getAllItemsList()
                val byId = allInventory.associateBy { it.id }
                val byName = allInventory.associateBy { it.name.trim().lowercase(Locale.US) }

                var totalRefund = 0.0
                var restockedCount = 0
                val missingOrFailed = mutableListOf<String>()

                returnMap.forEach { (itemId, qty) ->
                    if (qty > 0.0) {
                        val line = lineItems.find { it.id == itemId || it.name == itemId }
                        if (line != null) {
                            val perUnitWithTax = if (line.qty > 0.0 && line.totalAmount > 0.0) {
                                line.totalAmount / line.qty
                            } else {
                                line.rate * (1.0 + line.taxRatePercent / 100.0)
                            }
                            totalRefund += qty * perUnitWithTax
                            if (restock) {
                                val targetItem = byId[line.id]
                                    ?: byName[line.name.trim().lowercase(Locale.US)]
                                if (targetItem == null) {
                                    missingOrFailed.add(line.name)
                                } else {
                                    val res = repository.performTransaction(
                                        itemId = targetItem.id,
                                        action = "in",
                                        qty = qty,
                                        note = "Sales Return (Inv #${bill.invoiceNo}): $reason"
                                    )
                                    if (res.isSuccess) {
                                        restockedCount++
                                    } else {
                                        missingOrFailed.add(line.name)
                                    }
                                }
                            }
                        }
                    }
                }

                when {
                    restock && missingOrFailed.isNotEmpty() && restockedCount == 0 ->
                        showToast("Credit Note ₹%.2f — could not restock unmatched items: %s".format(totalRefund, missingOrFailed.joinToString(", ")))
                    restock && missingOrFailed.isNotEmpty() ->
                        showToast("Credit Note ₹%.2f — restocked $restockedCount items; skipped: %s".format(totalRefund, missingOrFailed.joinToString(", ")))
                    else ->
                        showToast("Credit Note processed for ₹%.2f! Stock ${if (restock) "restocked ($restockedCount)" else "written off"} ✓".format(totalRefund))
                }
            } catch (e: Exception) {
                showToast("Failed to process return: ${e.message}")
            }
        }
    }

    fun convertQuotationToGstBill(
        quotation: QuotationRecord,
        invoiceNo: String? = null,
        customerGstin: String = "",
        isInterState: Boolean = false,
        deductStock: Boolean = true,
        onComplete: ((GstBillRecord?) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val result = repository.convertQuotationToGstBill(
                quotation = quotation,
                invoiceNo = invoiceNo,
                customerGstin = customerGstin,
                isInterState = isInterState,
                deductStock = deductStock
            )
            if (result.isSuccess) {
                val bill = result.getOrNull()
                showToast("Converted to GST Bill #${bill?.invoiceNo ?: ""}! Saved in Billing ✓")
                onComplete?.invoke(bill)
            } else {
                showToast("Failed to convert: ${result.exceptionOrNull()?.message}")
                onComplete?.invoke(null)
            }
        }
    }

    // ==================== LEDGER ACTIONS ====================
    fun saveLedgerAccount(account: LedgerAccount) {
        viewModelScope.launch {
            repository.saveLedgerAccount(account)
            showToast("Saved account: ${account.name}")
        }
    }

    fun deleteLedgerAccount(id: String) {
        if (!authManager.isAdmin.value) {
            showToast("Admin access required to delete Khata accounts.")
            return
        }
        viewModelScope.launch {
            repository.deleteLedgerAccount(id)
            showToast("Account deleted")
        }
    }

    fun addLedgerEntry(
        accountId: String,
        type: String,
        amount: Double,
        date: String,
        description: String,
        billRef: String,
        paymentMode: String = "",
        paymentRef: String = "",
        dueDate: String = "",
        siteName: String = "",
        alsoRecordCashflow: Boolean = false
    ) {
        viewModelScope.launch {
            val res = repository.addLedgerEntry(
                accountId = accountId,
                type = type,
                amount = amount,
                date = date,
                description = description,
                billRef = billRef,
                paymentMode = paymentMode,
                paymentRef = paymentRef,
                dueDate = dueDate,
                siteName = siteName,
                alsoRecordCashflow = alsoRecordCashflow
            )
            if (res.isSuccess) {
                showToast("Ledger entry added ✓")
            } else {
                showToast("Error: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun getEntriesForAccount(accountId: String): Flow<List<LedgerEntry>> {
        return repository.getEntriesForAccount(accountId)
    }

    fun updateLedgerEntry(updatedEntry: LedgerEntry) {
        viewModelScope.launch {
            val res = repository.updateLedgerEntry(updatedEntry)
            if (res.isSuccess) {
                showToast("Ledger entry updated & balance recalculated ✓")
            } else {
                showToast("Error updating entry: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun deleteLedgerEntry(entryId: String, accountId: String) {
        viewModelScope.launch {
            repository.deleteLedgerEntry(entryId, accountId)
            showToast("Entry deleted")
        }
    }

    // Daily Sales & Expenses Cashflow Operations
    fun addDailyCashflow(
        type: String, // "SALE" or "EXPENSE"
        amount: Double,
        title: String,
        category: String,
        paymentMode: String,
        date: String,
        note: String
    ) {
        viewModelScope.launch {
            val cleanTitle = title.trim()
            val cleanCategory = category.trim().ifBlank { cleanTitle.ifBlank { if (type.uppercase() == "SALE") "Counter Sale" else "General Expense" } }
            val cleanDate = DateTimeHelper.normalizeDateToIso(date)

            val record = DailyCashflowRecord(
                id = java.util.UUID.randomUUID().toString(),
                type = type.uppercase(),
                amount = amount,
                title = cleanTitle.ifBlank { cleanCategory },
                category = cleanCategory,
                paymentMode = paymentMode.trim().ifBlank { "Cash" },
                date = cleanDate,
                note = note.trim(),
                createdAt = DateTimeHelper.nowIso()
            )
            val res = repository.addDailyCashflow(record)
            if (res.isSuccess) {
                showToast("${if (type == "SALE") "Sale" else "Expense"} of ₹$amount added ✓")
            } else {
                showToast("Error adding record: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun updateDailyCashflow(record: DailyCashflowRecord) {
        viewModelScope.launch {
            val normalized = record.copy(date = DateTimeHelper.normalizeDateToIso(record.date))
            val res = repository.updateDailyCashflow(normalized)
            if (res.isSuccess) {
                showToast("Transaction updated ✓")
            } else {
                showToast("Error updating record: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun deleteDailyCashflow(id: String) {
        viewModelScope.launch {
            val res = repository.deleteDailyCashflow(id)
            if (res.isSuccess) {
                showToast("Transaction deleted")
            } else {
                showToast("Error deleting: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    data class TintReportImportPayload(
        val rows: List<com.example.util.ParsedTintRow>,
        val rawText: String,
        val fileName: String
    )

    private val _incomingTintReport = MutableStateFlow<TintReportImportPayload?>(null)
    val incomingTintReport: StateFlow<TintReportImportPayload?> = _incomingTintReport.asStateFlow()

    fun clearIncomingTintReport() {
        _incomingTintReport.value = null
    }

    suspend fun getAvailableItemsForMatching(): List<Item> {
        val inMemory = allItems.value
        if (inMemory.isNotEmpty()) return inMemory
        val fromDb = try {
            repository.getAllItemsList()
        } catch (e: Exception) {
            Log.w("StockViewModel", "Failed reading items from local DB for matching", e)
            emptyList()
        }
        if (fromDb.isNotEmpty()) return fromDb
        return try {
            withTimeoutOrNull(2000L) {
                repository.allItems.first { it.isNotEmpty() }
            } ?: emptyList()
        } catch (e: Exception) {
            Log.w("StockViewModel", "Timed out waiting for items flow for matching", e)
            emptyList()
        }
    }

    suspend fun parseColorMachineContent(content: String): List<com.example.util.ParsedTintRow> {
        val processedIds = repository.getProcessedTintRecordIds()
        val items = getAvailableItemsForMatching()
        return com.example.util.ColorMachineParser.parse(content, items, processedIds)
    }

    suspend fun parseColorMachineStream(
        inputStream: java.io.InputStream
    ): Pair<List<com.example.util.ParsedTintRow>, String> {
        val processedIds = repository.getProcessedTintRecordIds()
        val items = getAvailableItemsForMatching()
        return com.example.util.ColorMachineParser.parseFromStream(inputStream, items, processedIds)
    }

    fun loadExternalTintReport(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val fileName = com.example.util.SpreadsheetReportParser.getFileName(context, uri)
                val stream = context.contentResolver.openInputStream(uri)
                if (stream == null) {
                    showToast("Could not open file: $fileName")
                    return@launch
                }
                val (rows, rawText) = stream.use { s ->
                    parseColorMachineStream(s)
                }

                if (rows.isNotEmpty()) {
                    _incomingTintReport.value = TintReportImportPayload(rows, rawText, fileName)
                    showToast("Opened tint report ($fileName): ${rows.size} records ready for review")
                } else {
                    showToast("No tint dispense records recognized in $fileName")
                }
            } catch (e: Exception) {
                showToast("Failed to parse report: ${e.message}")
            }
        }
    }

    suspend fun processColorMachineImport(
        rows: List<com.example.util.ParsedTintRow>,
        manualItemMappings: Map<String, String> = emptyMap(),
        autoCreateMissing: Boolean = false
    ): Result<com.example.data.repository.ColorMachineImportResult> {
        return repository.processColorMachineImport(rows, manualItemMappings, autoCreateMissing)
    }

    suspend fun processBillScanImport(
        supplierName: String,
        billNumber: String,
        items: List<com.example.util.ParsedBillItem>
    ): Boolean {
        return repository.processBillScanImport(supplierName, billNumber, items)
    }

    fun saveStoreIdentity(
        storeName: String,
        supervisorName: String,
        roleTitle: String,
        location: String
    ) {
        if (!isAdmin.value) {
            showToast("Admin access required")
            return
        }
        viewModelScope.launch {
            repository.saveAndSyncStoreIdentity(storeName, supervisorName, roleTitle, location)
            showToast("Store & terminal profile saved & synced!")
        }
    }

    fun saveBankDetails(
        bankName: String,
        accountHolder: String,
        accountNumber: String,
        ifscCode: String,
        branchName: String,
        upiId: String
    ) {
        if (!isAdmin.value) {
            showToast("Admin access required")
            return
        }
        viewModelScope.launch {
            repository.saveAndSyncBankDetails(
                bankName, accountHolder, accountNumber, ifscCode, branchName, upiId
            )
            showToast("Banking details saved & synced!")
        }
    }

    fun saveSignatureFromUri(uri: android.net.Uri, onResult: (Boolean) -> Unit = {}) {
        if (!isAdmin.value) {
            showToast("Admin access required")
            onResult(false)
            return
        }
        viewModelScope.launch {
            val res = repository.saveAndSyncSignature(uri)
            if (res.isSuccess) {
                showToast("Authorized signature uploaded & synced!")
                onResult(true)
            } else {
                showToast("Failed to save signature: ${res.exceptionOrNull()?.message}")
                onResult(false)
            }
        }
    }

    fun saveQrCodeFromUri(uri: android.net.Uri, onResult: (Boolean) -> Unit = {}) {
        if (!isAdmin.value) {
            showToast("Admin access required")
            onResult(false)
            return
        }
        viewModelScope.launch {
            val res = repository.saveAndSyncQrCode(uri)
            if (res.isSuccess) {
                showToast("Payment QR code uploaded & synced!")
                onResult(true)
            } else {
                showToast("Failed to save QR code: ${res.exceptionOrNull()?.message}")
                onResult(false)
            }
        }
    }

    fun clearSignature() {
        if (!isAdmin.value) {
            showToast("Admin access required")
            return
        }
        viewModelScope.launch {
            repository.clearAndSyncSignature()
            showToast("Signature removed & synced")
        }
    }

    fun clearQrCode() {
        if (!isAdmin.value) {
            showToast("Admin access required")
            return
        }
        viewModelScope.launch {
            repository.clearAndSyncQrCode()
            showToast("QR code removed & synced")
        }
    }

    companion object {
        fun formatRupees(amount: Double): String {
            return String.format(Locale.ENGLISH, "₹%.2f", amount)
        }
    }
}
