package com.example.data.pref

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.Item
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized reactive store for user-configured invoicing & stock alert settings
 * persisted in `"settings_prefs"`.
 */
class AppSettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

    private val _invoicePrefix = MutableStateFlow(
        prefs.getString(KEY_INVOICE_PREFIX, DEFAULT_INVOICE_PREFIX) ?: DEFAULT_INVOICE_PREFIX
    )
    val invoicePrefix: StateFlow<String> = _invoicePrefix.asStateFlow()

    private val _nextInvoiceNum = MutableStateFlow(
        prefs.getInt(KEY_NEXT_INVOICE_NUM, DEFAULT_NEXT_INVOICE_NUM)
    )
    val nextInvoiceNum: StateFlow<Int> = _nextInvoiceNum.asStateFlow()

    private val _lowStockAlertsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_LOW_STOCK_ALERTS_ENABLED, true)
    )
    val lowStockAlertsEnabled: StateFlow<Boolean> = _lowStockAlertsEnabled.asStateFlow()

    private val _lowStockDefaultThreshold = MutableStateFlow(
        prefs.getInt(KEY_LOW_STOCK_DEFAULT_THRESHOLD, DEFAULT_LOW_STOCK_THRESHOLD)
    )
    val lowStockDefaultThreshold: StateFlow<Int> = _lowStockDefaultThreshold.asStateFlow()

    private val _instantDispatchSlip = MutableStateFlow(
        prefs.getBoolean(KEY_INSTANT_DISPATCH_SLIP, true)
    )
    val instantDispatchSlip: StateFlow<Boolean> = _instantDispatchSlip.asStateFlow()

    fun setLowStockAlertsEnabled(enabled: Boolean) {
        _lowStockAlertsEnabled.value = enabled
        prefs.edit().putBoolean(KEY_LOW_STOCK_ALERTS_ENABLED, enabled).apply()
    }

    fun setLowStockDefaultThreshold(threshold: Int) {
        val safe = threshold.coerceAtLeast(0)
        _lowStockDefaultThreshold.value = safe
        prefs.edit().putInt(KEY_LOW_STOCK_DEFAULT_THRESHOLD, safe).apply()
    }

    fun setInstantDispatchSlip(enabled: Boolean) {
        _instantDispatchSlip.value = enabled
        prefs.edit().putBoolean(KEY_INSTANT_DISPATCH_SLIP, enabled).apply()
    }

    fun setInvoiceSequence(prefix: String, nextNum: Int) {
        val cleanPrefix = prefix.trim().ifBlank { DEFAULT_INVOICE_PREFIX }
        val cleanNext = nextNum.coerceAtLeast(1)
        _invoicePrefix.value = cleanPrefix
        _nextInvoiceNum.value = cleanNext
        prefs.edit()
            .putString(KEY_INVOICE_PREFIX, cleanPrefix)
            .putInt(KEY_NEXT_INVOICE_NUM, cleanNext)
            .apply()
    }

    fun previewNextInvoiceNo(): String {
        val prefix = _invoicePrefix.value.ifBlank { DEFAULT_INVOICE_PREFIX }
        val num = _nextInvoiceNum.value.coerceAtLeast(1)
        return "$prefix$num"
    }

    /**
     * Atomically returns the current formatted invoice number and advances `next_invoice_num` by 1.
     */
    @Synchronized
    fun consumeNextInvoiceNo(): String {
        val prefix = _invoicePrefix.value.ifBlank { DEFAULT_INVOICE_PREFIX }
        val currentNum = _nextInvoiceNum.value.coerceAtLeast(1)
        val formatted = "$prefix$currentNum"
        val nextNum = currentNum + 1
        _nextInvoiceNum.value = nextNum
        prefs.edit().putInt(KEY_NEXT_INVOICE_NUM, nextNum).apply()
        return formatted
    }

    fun effectiveThresholdFor(item: Item): Double {
        return if (item.low > 0.0) item.low else _lowStockDefaultThreshold.value.toDouble()
    }

    fun isLowStockOnly(item: Item): Boolean {
        val threshold = effectiveThresholdFor(item)
        return item.qty > 0.0 && threshold > 0.0 && item.qty <= threshold
    }

    fun isLowOrOutOfStock(item: Item): Boolean {
        return item.qty <= 0.0 || isLowStockOnly(item)
    }

    companion object {
        const val KEY_INVOICE_PREFIX = "invoice_prefix"
        const val KEY_NEXT_INVOICE_NUM = "next_invoice_num"
        const val KEY_LOW_STOCK_ALERTS_ENABLED = "low_stock_alerts_enabled"
        const val KEY_LOW_STOCK_DEFAULT_THRESHOLD = "low_stock_default_threshold"
        const val KEY_INSTANT_DISPATCH_SLIP = "instant_dispatch_slip"

        const val DEFAULT_INVOICE_PREFIX = "BILL-"
        const val DEFAULT_NEXT_INVOICE_NUM = 1043
        const val DEFAULT_LOW_STOCK_THRESHOLD = 15
    }
}
