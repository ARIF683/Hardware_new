package com.example.data.pref

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.Item
import com.example.data.remote.RemoteStoreProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.util.Locale

/**
 * Centralized reactive store for user-configured invoicing, quotation numbering,
 * & stock alert settings persisted in `"settings_prefs"`.
 */
class AppSettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

    private val _invoicePrefix = MutableStateFlow(
        prefs.getString(KEY_INVOICE_PREFIX, DEFAULT_INVOICE_PREFIX) ?: DEFAULT_INVOICE_PREFIX
    )
    val invoicePrefix: StateFlow<String> = _invoicePrefix.asStateFlow()

    private val _nextInvoiceNum = MutableStateFlow(
        prefs.getInt(KEY_NEXT_INVOICE_NUM, DEFAULT_NEXT_INVOICE_NUM).coerceAtLeast(1)
    )
    val nextInvoiceNum: StateFlow<Int> = _nextInvoiceNum.asStateFlow()

    private val _invoicePadDigits = MutableStateFlow(
        prefs.getInt(KEY_INVOICE_PAD_DIGITS, DEFAULT_PAD_DIGITS).coerceIn(0, 6)
    )
    val invoicePadDigits: StateFlow<Int> = _invoicePadDigits.asStateFlow()

    private val _invoiceIncludeFy = MutableStateFlow(
        prefs.getBoolean(KEY_INVOICE_INCLUDE_FY, false)
    )
    val invoiceIncludeFy: StateFlow<Boolean> = _invoiceIncludeFy.asStateFlow()

    private val _quotePrefix = MutableStateFlow(
        prefs.getString(KEY_QUOTE_PREFIX, DEFAULT_QUOTE_PREFIX) ?: DEFAULT_QUOTE_PREFIX
    )
    val quotePrefix: StateFlow<String> = _quotePrefix.asStateFlow()

    private val _nextQuoteNum = MutableStateFlow(
        prefs.getInt(KEY_NEXT_QUOTE_NUM, DEFAULT_NEXT_QUOTE_NUM).coerceAtLeast(1)
    )
    val nextQuoteNum: StateFlow<Int> = _nextQuoteNum.asStateFlow()

    private val _quotePadDigits = MutableStateFlow(
        prefs.getInt(KEY_QUOTE_PAD_DIGITS, DEFAULT_PAD_DIGITS).coerceIn(0, 6)
    )
    val quotePadDigits: StateFlow<Int> = _quotePadDigits.asStateFlow()

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

    // Strong reference so SharedPreferences keeps notifying across multiple AppSettingsManager instances
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        reloadFromPrefs()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    fun reloadFromPrefs() {
        _invoicePrefix.value = prefs.getString(KEY_INVOICE_PREFIX, DEFAULT_INVOICE_PREFIX) ?: DEFAULT_INVOICE_PREFIX
        _nextInvoiceNum.value = prefs.getInt(KEY_NEXT_INVOICE_NUM, DEFAULT_NEXT_INVOICE_NUM).coerceAtLeast(1)
        _invoicePadDigits.value = prefs.getInt(KEY_INVOICE_PAD_DIGITS, DEFAULT_PAD_DIGITS).coerceIn(0, 6)
        _invoiceIncludeFy.value = prefs.getBoolean(KEY_INVOICE_INCLUDE_FY, false)
        _quotePrefix.value = prefs.getString(KEY_QUOTE_PREFIX, DEFAULT_QUOTE_PREFIX) ?: DEFAULT_QUOTE_PREFIX
        _nextQuoteNum.value = prefs.getInt(KEY_NEXT_QUOTE_NUM, DEFAULT_NEXT_QUOTE_NUM).coerceAtLeast(1)
        _quotePadDigits.value = prefs.getInt(KEY_QUOTE_PAD_DIGITS, DEFAULT_PAD_DIGITS).coerceIn(0, 6)
        _lowStockAlertsEnabled.value = prefs.getBoolean(KEY_LOW_STOCK_ALERTS_ENABLED, true)
        _lowStockDefaultThreshold.value = prefs.getInt(KEY_LOW_STOCK_DEFAULT_THRESHOLD, DEFAULT_LOW_STOCK_THRESHOLD)
        _instantDispatchSlip.value = prefs.getBoolean(KEY_INSTANT_DISPATCH_SLIP, true)
    }

    fun getInvoiceSeqUpdatedAt(): Long = prefs.getLong(KEY_INVOICE_SEQ_UPDATED_AT, 0L)

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

    @Synchronized
    fun setInvoiceSequence(
        prefix: String,
        nextNum: Int,
        padDigits: Int = _invoicePadDigits.value,
        includeFy: Boolean = _invoiceIncludeFy.value,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        val cleanPrefix = normalizePrefix(prefix, DEFAULT_INVOICE_PREFIX)
        val cleanNext = nextNum.coerceAtLeast(1)
        val cleanPad = normalizePadDigits(padDigits)
        _invoicePrefix.value = cleanPrefix
        _nextInvoiceNum.value = cleanNext
        _invoicePadDigits.value = cleanPad
        _invoiceIncludeFy.value = includeFy
        prefs.edit()
            .putString(KEY_INVOICE_PREFIX, cleanPrefix)
            .putInt(KEY_NEXT_INVOICE_NUM, cleanNext)
            .putInt(KEY_INVOICE_PAD_DIGITS, cleanPad)
            .putBoolean(KEY_INVOICE_INCLUDE_FY, includeFy)
            .putLong(KEY_INVOICE_SEQ_UPDATED_AT, updatedAt)
            .apply()
    }

    @Synchronized
    fun setQuoteSequence(
        prefix: String,
        nextNum: Int,
        padDigits: Int = _quotePadDigits.value,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        val cleanPrefix = normalizePrefix(prefix, DEFAULT_QUOTE_PREFIX)
        val cleanNext = nextNum.coerceAtLeast(1)
        val cleanPad = normalizePadDigits(padDigits)
        _quotePrefix.value = cleanPrefix
        _nextQuoteNum.value = cleanNext
        _quotePadDigits.value = cleanPad
        prefs.edit()
            .putString(KEY_QUOTE_PREFIX, cleanPrefix)
            .putInt(KEY_NEXT_QUOTE_NUM, cleanNext)
            .putInt(KEY_QUOTE_PAD_DIGITS, cleanPad)
            .putLong(KEY_INVOICE_SEQ_UPDATED_AT, updatedAt)
            .apply()
    }

    fun previewNextInvoiceNo(existingInvoiceNos: Collection<String> = emptyList()): String {
        val prefix = normalizePrefix(_invoicePrefix.value, DEFAULT_INVOICE_PREFIX)
        val pad = _invoicePadDigits.value
        val fy = _invoiceIncludeFy.value
        val candidateNum = computeNextAvailableNumber(
            startNum = _nextInvoiceNum.value,
            prefix = prefix,
            padDigits = pad,
            includeFy = fy,
            existingNos = existingInvoiceNos,
            defaultPrefix = DEFAULT_INVOICE_PREFIX
        )
        return formatDocumentNo(prefix, candidateNum, pad, fy, DEFAULT_INVOICE_PREFIX)
    }

    /**
     * Atomically returns the next unused formatted invoice number and advances `next_invoice_num`.
     */
    @Synchronized
    fun consumeNextInvoiceNo(existingInvoiceNos: Collection<String> = emptyList()): String {
        val prefix = normalizePrefix(_invoicePrefix.value, DEFAULT_INVOICE_PREFIX)
        val pad = _invoicePadDigits.value
        val fy = _invoiceIncludeFy.value
        val candidateNum = computeNextAvailableNumber(
            startNum = _nextInvoiceNum.value,
            prefix = prefix,
            padDigits = pad,
            includeFy = fy,
            existingNos = existingInvoiceNos,
            defaultPrefix = DEFAULT_INVOICE_PREFIX
        )
        val formatted = formatDocumentNo(prefix, candidateNum, pad, fy, DEFAULT_INVOICE_PREFIX)
        val nextNum = candidateNum + 1
        _nextInvoiceNum.value = nextNum
        prefs.edit()
            .putInt(KEY_NEXT_INVOICE_NUM, nextNum)
            .putLong(KEY_INVOICE_SEQ_UPDATED_AT, System.currentTimeMillis())
            .apply()
        return formatted
    }

    /**
     * If a saved invoice number matches the active prefix/FY format or equals the current preview,
     * advances the counter past it (and any existing collisions). Returns true if counter changed.
     */
    @Synchronized
    fun advanceInvoiceCounterIfUsed(
        usedInvoiceNo: String,
        existingInvoiceNos: Collection<String> = emptyList()
    ): Boolean {
        val cleanUsed = usedInvoiceNo.trim()
        if (cleanUsed.isBlank()) return false
        val prefix = normalizePrefix(_invoicePrefix.value, DEFAULT_INVOICE_PREFIX)
        val pad = _invoicePadDigits.value
        val fy = _invoiceIncludeFy.value
        val basePrefix = buildEffectiveBasePrefix(prefix, fy, DEFAULT_INVOICE_PREFIX)
        val parsedSuffix = extractNumericSuffix(cleanUsed, basePrefix)
        val currentPreview = previewNextInvoiceNo()
        val combinedUsed = HashSet<String>(existingInvoiceNos.size + 1).apply {
            existingInvoiceNos.forEach { add(it) }
            add(cleanUsed)
        }
        val targetMin = when {
            parsedSuffix != null && parsedSuffix >= _nextInvoiceNum.value -> parsedSuffix + 1
            cleanUsed.equals(currentPreview, ignoreCase = true) -> _nextInvoiceNum.value + 1
            else -> _nextInvoiceNum.value
        }
        val resolvedNext = computeNextAvailableNumber(
            startNum = targetMin,
            prefix = prefix,
            padDigits = pad,
            includeFy = fy,
            existingNos = combinedUsed,
            defaultPrefix = DEFAULT_INVOICE_PREFIX
        )
        if (resolvedNext != _nextInvoiceNum.value) {
            _nextInvoiceNum.value = resolvedNext
            prefs.edit()
                .putInt(KEY_NEXT_INVOICE_NUM, resolvedNext)
                .putLong(KEY_INVOICE_SEQ_UPDATED_AT, System.currentTimeMillis())
                .apply()
            return true
        }
        return false
    }

    fun previewNextQuoteNo(existingQuoteNos: Collection<String> = emptyList()): String {
        val prefix = normalizePrefix(_quotePrefix.value, DEFAULT_QUOTE_PREFIX)
        val pad = _quotePadDigits.value
        val candidateNum = computeNextAvailableNumber(
            startNum = _nextQuoteNum.value,
            prefix = prefix,
            padDigits = pad,
            includeFy = false,
            existingNos = existingQuoteNos,
            defaultPrefix = DEFAULT_QUOTE_PREFIX
        )
        return formatDocumentNo(prefix, candidateNum, pad, includeFy = false, defaultPrefix = DEFAULT_QUOTE_PREFIX)
    }

    @Synchronized
    fun consumeNextQuoteNo(existingQuoteNos: Collection<String> = emptyList()): String {
        val prefix = normalizePrefix(_quotePrefix.value, DEFAULT_QUOTE_PREFIX)
        val pad = _quotePadDigits.value
        val candidateNum = computeNextAvailableNumber(
            startNum = _nextQuoteNum.value,
            prefix = prefix,
            padDigits = pad,
            includeFy = false,
            existingNos = existingQuoteNos,
            defaultPrefix = DEFAULT_QUOTE_PREFIX
        )
        val formatted = formatDocumentNo(prefix, candidateNum, pad, includeFy = false, defaultPrefix = DEFAULT_QUOTE_PREFIX)
        val nextNum = candidateNum + 1
        _nextQuoteNum.value = nextNum
        prefs.edit()
            .putInt(KEY_NEXT_QUOTE_NUM, nextNum)
            .putLong(KEY_INVOICE_SEQ_UPDATED_AT, System.currentTimeMillis())
            .apply()
        return formatted
    }

    @Synchronized
    fun advanceQuoteCounterIfUsed(
        usedQuotationNo: String,
        existingQuoteNos: Collection<String> = emptyList()
    ): Boolean {
        val cleanUsed = usedQuotationNo.trim()
        if (cleanUsed.isBlank()) return false
        val prefix = normalizePrefix(_quotePrefix.value, DEFAULT_QUOTE_PREFIX)
        val pad = _quotePadDigits.value
        val basePrefix = buildEffectiveBasePrefix(prefix, includeFy = false, defaultPrefix = DEFAULT_QUOTE_PREFIX)
        val parsedSuffix = extractNumericSuffix(cleanUsed, basePrefix)
        val currentPreview = previewNextQuoteNo()
        val combinedUsed = HashSet<String>(existingQuoteNos.size + 1).apply {
            existingQuoteNos.forEach { add(it) }
            add(cleanUsed)
        }
        val targetMin = when {
            parsedSuffix != null && parsedSuffix >= _nextQuoteNum.value -> parsedSuffix + 1
            cleanUsed.equals(currentPreview, ignoreCase = true) -> _nextQuoteNum.value + 1
            else -> _nextQuoteNum.value
        }
        val resolvedNext = computeNextAvailableNumber(
            startNum = targetMin,
            prefix = prefix,
            padDigits = pad,
            includeFy = false,
            existingNos = combinedUsed,
            defaultPrefix = DEFAULT_QUOTE_PREFIX
        )
        if (resolvedNext != _nextQuoteNum.value) {
            _nextQuoteNum.value = resolvedNext
            prefs.edit()
                .putInt(KEY_NEXT_QUOTE_NUM, resolvedNext)
                .putLong(KEY_INVOICE_SEQ_UPDATED_AT, System.currentTimeMillis())
                .apply()
            return true
        }
        return false
    }

    /**
     * Reconciles local invoice & quotation counters against existing documents (e.g. after cloud pull)
     * so synced records from other devices never cause subsequent bill/quote collisions.
     */
    @Synchronized
    fun reconcileWithExistingDocuments(
        billNos: Collection<String>,
        quoteNos: Collection<String>
    ): Boolean {
        var changed = false

        val invPrefix = normalizePrefix(_invoicePrefix.value, DEFAULT_INVOICE_PREFIX)
        val invPad = _invoicePadDigits.value
        val invFy = _invoiceIncludeFy.value
        val invBase = buildEffectiveBasePrefix(invPrefix, invFy, DEFAULT_INVOICE_PREFIX)
        val maxBillSuffix = billNos.mapNotNull { extractNumericSuffix(it, invBase) }.maxOrNull()
        val invMin = maxOf(_nextInvoiceNum.value, (maxBillSuffix ?: 0) + 1)
        val resolvedInv = computeNextAvailableNumber(invMin, invPrefix, invPad, invFy, billNos, DEFAULT_INVOICE_PREFIX)
        if (resolvedInv != _nextInvoiceNum.value) {
            _nextInvoiceNum.value = resolvedInv
            prefs.edit().putInt(KEY_NEXT_INVOICE_NUM, resolvedInv).apply()
            changed = true
        }

        val qPrefix = normalizePrefix(_quotePrefix.value, DEFAULT_QUOTE_PREFIX)
        val qPad = _quotePadDigits.value
        val qBase = buildEffectiveBasePrefix(qPrefix, includeFy = false, defaultPrefix = DEFAULT_QUOTE_PREFIX)
        val maxQuoteSuffix = quoteNos.mapNotNull { extractNumericSuffix(it, qBase) }.maxOrNull()
        val qMin = maxOf(_nextQuoteNum.value, (maxQuoteSuffix ?: 0) + 1)
        val resolvedQuote = computeNextAvailableNumber(qMin, qPrefix, qPad, false, quoteNos, DEFAULT_QUOTE_PREFIX)
        if (resolvedQuote != _nextQuoteNum.value) {
            _nextQuoteNum.value = resolvedQuote
            prefs.edit().putInt(KEY_NEXT_QUOTE_NUM, resolvedQuote).apply()
            changed = true
        }

        return changed
    }

    @Synchronized
    fun applyRemoteSequenceProfile(remote: RemoteStoreProfile) {
        val remoteHasSeq = remote.invoicePrefix != null ||
            remote.invoiceNextNum != null ||
            remote.invoicePadDigits != null ||
            remote.invoiceIncludeFy != null ||
            remote.quotePrefix != null ||
            remote.quoteNextNum != null ||
            remote.quotePadDigits != null
        if (!remoteHasSeq) return

        val localSeqTs = getInvoiceSeqUpdatedAt()
        val remoteSeqTs = remote.invoiceSeqUpdatedAt ?: 0L
        val sameFormat = (remote.invoicePrefix ?: _invoicePrefix.value) == _invoicePrefix.value &&
            (remote.invoicePadDigits ?: _invoicePadDigits.value) == _invoicePadDigits.value &&
            (remote.invoiceIncludeFy ?: _invoiceIncludeFy.value) == _invoiceIncludeFy.value

        if (remoteSeqTs > localSeqTs || (localSeqTs == 0L && remoteHasSeq)) {
            val newInvPrefix = normalizePrefix(remote.invoicePrefix ?: _invoicePrefix.value, DEFAULT_INVOICE_PREFIX)
            val newInvPad = normalizePadDigits(remote.invoicePadDigits ?: _invoicePadDigits.value)
            val newInvFy = remote.invoiceIncludeFy ?: _invoiceIncludeFy.value
            val newInvNext = (remote.invoiceNextNum ?: _nextInvoiceNum.value).coerceAtLeast(1)

            val newQuotePrefix = normalizePrefix(remote.quotePrefix ?: _quotePrefix.value, DEFAULT_QUOTE_PREFIX)
            val newQuotePad = normalizePadDigits(remote.quotePadDigits ?: _quotePadDigits.value)
            val newQuoteNext = (remote.quoteNextNum ?: _nextQuoteNum.value).coerceAtLeast(1)

            _invoicePrefix.value = newInvPrefix
            _nextInvoiceNum.value = newInvNext
            _invoicePadDigits.value = newInvPad
            _invoiceIncludeFy.value = newInvFy
            _quotePrefix.value = newQuotePrefix
            _nextQuoteNum.value = newQuoteNext
            _quotePadDigits.value = newQuotePad

            prefs.edit()
                .putString(KEY_INVOICE_PREFIX, newInvPrefix)
                .putInt(KEY_NEXT_INVOICE_NUM, newInvNext)
                .putInt(KEY_INVOICE_PAD_DIGITS, newInvPad)
                .putBoolean(KEY_INVOICE_INCLUDE_FY, newInvFy)
                .putString(KEY_QUOTE_PREFIX, newQuotePrefix)
                .putInt(KEY_NEXT_QUOTE_NUM, newQuoteNext)
                .putInt(KEY_QUOTE_PAD_DIGITS, newQuotePad)
                .putLong(KEY_INVOICE_SEQ_UPDATED_AT, remoteSeqTs)
                .apply()
        } else if (sameFormat) {
            val editor = prefs.edit()
            var dirty = false
            val remoteNext = remote.invoiceNextNum
            if (remoteNext != null && remoteNext > _nextInvoiceNum.value) {
                _nextInvoiceNum.value = remoteNext
                editor.putInt(KEY_NEXT_INVOICE_NUM, remoteNext)
                dirty = true
            }
            val remoteQuoteNext = remote.quoteNextNum
            if ((remote.quotePrefix ?: _quotePrefix.value) == _quotePrefix.value &&
                remoteQuoteNext != null && remoteQuoteNext > _nextQuoteNum.value
            ) {
                _nextQuoteNum.value = remoteQuoteNext
                editor.putInt(KEY_NEXT_QUOTE_NUM, remoteQuoteNext)
                dirty = true
            }
            if (dirty) editor.apply()
        }
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
        const val KEY_INVOICE_PAD_DIGITS = "invoice_pad_digits"
        const val KEY_INVOICE_INCLUDE_FY = "invoice_include_fy"
        const val KEY_INVOICE_SEQ_UPDATED_AT = "invoice_seq_updated_at"
        const val KEY_QUOTE_PREFIX = "quote_prefix"
        const val KEY_NEXT_QUOTE_NUM = "next_quote_num"
        const val KEY_QUOTE_PAD_DIGITS = "quote_pad_digits"
        const val KEY_LOW_STOCK_ALERTS_ENABLED = "low_stock_alerts_enabled"
        const val KEY_LOW_STOCK_DEFAULT_THRESHOLD = "low_stock_default_threshold"
        const val KEY_INSTANT_DISPATCH_SLIP = "instant_dispatch_slip"

        const val DEFAULT_INVOICE_PREFIX = "BILL-"
        const val DEFAULT_NEXT_INVOICE_NUM = 1043
        const val DEFAULT_QUOTE_PREFIX = "EST-"
        const val DEFAULT_NEXT_QUOTE_NUM = 1001
        const val DEFAULT_PAD_DIGITS = 0
        const val DEFAULT_LOW_STOCK_THRESHOLD = 15

        fun normalizePrefix(raw: String, defaultPrefix: String = DEFAULT_INVOICE_PREFIX): String {
            return raw.trim().take(24).ifBlank { defaultPrefix }
        }

        fun normalizePadDigits(raw: Int): Int = if (raw in 2..6) raw else 0

        fun currentIndianFyToken(timeMillis: Long = System.currentTimeMillis()): String {
            val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH)
            val startYear = if (month >= Calendar.APRIL) year else year - 1
            val endYear = startYear + 1
            return String.format(Locale.US, "%02d-%02d", startYear % 100, endYear % 100)
        }

        fun buildEffectiveBasePrefix(
            prefix: String,
            includeFy: Boolean = false,
            defaultPrefix: String = DEFAULT_INVOICE_PREFIX,
            fyToken: String = currentIndianFyToken()
        ): String {
            val cleanPrefix = normalizePrefix(prefix, defaultPrefix)
            if (!includeFy) return cleanPrefix
            val endsWithSep = cleanPrefix.endsWith("-") ||
                cleanPrefix.endsWith("/") ||
                cleanPrefix.endsWith("_") ||
                cleanPrefix.endsWith("#")
            return if (endsWithSep) "$cleanPrefix$fyToken/" else "$cleanPrefix-$fyToken/"
        }

        fun formatDocumentNo(
            prefix: String,
            num: Int,
            padDigits: Int = 0,
            includeFy: Boolean = false,
            defaultPrefix: String = DEFAULT_INVOICE_PREFIX,
            fyToken: String = currentIndianFyToken()
        ): String {
            val basePrefix = buildEffectiveBasePrefix(prefix, includeFy, defaultPrefix, fyToken)
            val safeNum = num.coerceAtLeast(1)
            val safePad = normalizePadDigits(padDigits)
            val numStr = if (safePad > 0) safeNum.toString().padStart(safePad, '0') else safeNum.toString()
            return "$basePrefix$numStr"
        }

        fun extractNumericSuffix(docNo: String, expectedBasePrefix: String): Int? {
            val cleanDoc = docNo.trim()
            val cleanBase = expectedBasePrefix.trim()
            if (cleanDoc.isBlank() || cleanBase.isBlank()) return null
            if (!cleanDoc.startsWith(cleanBase, ignoreCase = true)) return null
            val tail = cleanDoc.substring(cleanBase.length).trim()
            if (tail.isEmpty() || !tail.all { it.isDigit() }) return null
            return tail.toIntOrNull()?.takeIf { it > 0 }
        }

        fun isDuplicateDocNo(candidate: String, existingNos: Collection<String>): Boolean {
            val clean = candidate.trim()
            if (clean.isEmpty()) return false
            return existingNos.any { it.trim().equals(clean, ignoreCase = true) }
        }

        fun computeNextAvailableNumber(
            startNum: Int,
            prefix: String,
            padDigits: Int,
            includeFy: Boolean,
            existingNos: Collection<String>,
            defaultPrefix: String = DEFAULT_INVOICE_PREFIX,
            fyToken: String = currentIndianFyToken()
        ): Int {
            var candidate = startNum.coerceAtLeast(1)
            if (existingNos.isEmpty()) return candidate
            val usedUpper = existingNos.asSequence()
                .map { it.trim().uppercase(Locale.US) }
                .filter { it.isNotEmpty() }
                .toHashSet()
            var guard = 0
            while (guard < 100_000) {
                val formatted = formatDocumentNo(prefix, candidate, padDigits, includeFy, defaultPrefix, fyToken)
                    .uppercase(Locale.US)
                if (!usedUpper.contains(formatted)) {
                    return candidate
                }
                candidate++
                guard++
            }
            return candidate
        }
    }
}
