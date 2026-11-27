package com.example.util

import com.example.data.model.LedgerAccount
import com.example.data.model.LedgerEntry
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object KhataMath {
    fun roundPaise(value: Double): Double {
        if (!value.isFinite()) return 0.0
        return (value * 100.0).roundToLong() / 100.0
    }

    fun isSettled(balance: Double): Boolean = abs(roundPaise(balance)) < 0.005

    fun formatAmount(value: Double): String = String.format(Locale.US, "%.2f", abs(roundPaise(value)))
}

data class KhataAccountMeta(
    val cleanNotes: String = "",
    val gstin: String = "",
    val creditDays: Int = 0,
    val siteOrTag: String = "",
    val archived: Boolean = false
)

data class KhataEntryMeta(
    val cleanDescription: String = "",
    val paymentMode: String = "",
    val paymentRef: String = "",
    val dueDate: String = "",
    val siteName: String = "",
    val recordedBy: String = "",
    val isOpeningBalance: Boolean = false,
    val syncedCashflowId: String = ""
)

object KhataMetaCodec {
    private const val ACCOUNT_MARKER = "||KHATA_META:"
    private const val ENTRY_MARKER = "||KMETA:"

    fun parseAccountMeta(rawNotes: String?): KhataAccountMeta {
        val raw = rawNotes.orEmpty()
        val idx = raw.indexOf(ACCOUNT_MARKER)
        if (idx < 0) return KhataAccountMeta(cleanNotes = raw.trim())
        val humanNotes = raw.substring(0, idx).trim()
        val jsonPart = raw.substring(idx + ACCOUNT_MARKER.length).trim()
        return try {
            val obj = JSONObject(jsonPart)
            KhataAccountMeta(
                cleanNotes = humanNotes,
                gstin = obj.optString("gstin", "").trim(),
                creditDays = obj.optInt("creditDays", 0).coerceIn(0, 3650),
                siteOrTag = obj.optString("siteOrTag", "").trim(),
                archived = obj.optBoolean("archived", false)
            )
        } catch (_: Exception) {
            KhataAccountMeta(cleanNotes = raw.trim())
        }
    }

    fun encodeAccountMeta(meta: KhataAccountMeta): String {
        val clean = meta.cleanNotes.replace(ACCOUNT_MARKER, " ").trim()
        val hasStructured = meta.gstin.isNotBlank() ||
            meta.creditDays > 0 ||
            meta.siteOrTag.isNotBlank() ||
            meta.archived
        if (!hasStructured) return clean
        val obj = JSONObject().apply {
            if (meta.gstin.isNotBlank()) put("gstin", meta.gstin.trim().uppercase(Locale.US))
            if (meta.creditDays > 0) put("creditDays", meta.creditDays.coerceIn(0, 3650))
            if (meta.siteOrTag.isNotBlank()) put("siteOrTag", meta.siteOrTag.trim())
            if (meta.archived) put("archived", true)
        }
        return if (clean.isBlank()) "$ACCOUNT_MARKER${obj}" else "$clean $ACCOUNT_MARKER${obj}"
    }

    fun cleanAccountNotes(rawNotes: String?): String = parseAccountMeta(rawNotes).cleanNotes

    fun parseEntryMeta(rawDescription: String?): KhataEntryMeta {
        val raw = rawDescription.orEmpty()
        val idx = raw.indexOf(ENTRY_MARKER)
        if (idx < 0) return KhataEntryMeta(cleanDescription = raw.trim())
        val humanDesc = raw.substring(0, idx).trim()
        val jsonPart = raw.substring(idx + ENTRY_MARKER.length).trim()
        return try {
            val obj = JSONObject(jsonPart)
            KhataEntryMeta(
                cleanDescription = humanDesc,
                paymentMode = obj.optString("mode", "").trim(),
                paymentRef = obj.optString("ref", "").trim(),
                dueDate = obj.optString("due", "").trim(),
                siteName = obj.optString("site", "").trim(),
                recordedBy = obj.optString("by", "").trim(),
                isOpeningBalance = obj.optBoolean("open", false),
                syncedCashflowId = obj.optString("cf", "").trim()
            )
        } catch (_: Exception) {
            KhataEntryMeta(cleanDescription = raw.trim())
        }
    }

    fun encodeEntryMeta(meta: KhataEntryMeta): String {
        val clean = meta.cleanDescription.replace(ENTRY_MARKER, " ").trim()
        val hasStructured = meta.paymentMode.isNotBlank() ||
            meta.paymentRef.isNotBlank() ||
            meta.dueDate.isNotBlank() ||
            meta.siteName.isNotBlank() ||
            meta.recordedBy.isNotBlank() ||
            meta.isOpeningBalance ||
            meta.syncedCashflowId.isNotBlank()
        if (!hasStructured) return clean
        val obj = JSONObject().apply {
            if (meta.paymentMode.isNotBlank()) put("mode", meta.paymentMode.trim())
            if (meta.paymentRef.isNotBlank()) put("ref", meta.paymentRef.trim())
            if (meta.dueDate.isNotBlank()) {
                val normalizedDue = DateTimeHelper.normalizeDateToIso(meta.dueDate, "").trim()
                if (normalizedDue.isNotBlank()) put("due", normalizedDue)
            }
            if (meta.siteName.isNotBlank()) put("site", meta.siteName.trim())
            if (meta.recordedBy.isNotBlank()) put("by", meta.recordedBy.trim())
            if (meta.isOpeningBalance) put("open", true)
            if (meta.syncedCashflowId.isNotBlank()) put("cf", meta.syncedCashflowId.trim())
        }
        return if (obj.length() == 0) clean
        else if (clean.isBlank()) "$ENTRY_MARKER${obj}"
        else "$clean $ENTRY_MARKER${obj}"
    }

    fun cleanEntryDescription(rawDescription: String?): String = parseEntryMeta(rawDescription).cleanDescription
}

object KhataBalanceHelper {
    data class BalanceStatus(
        val label: String,
        val isPositiveHealth: Boolean,
        val isSettled: Boolean
    )

    fun statusFor(accountType: String, netBalance: Double): BalanceStatus {
        val rounded = KhataMath.roundPaise(netBalance)
        if (KhataMath.isSettled(rounded)) {
            return BalanceStatus(label = "SETTLED", isPositiveHealth = true, isSettled = true)
        }
        val isSupplier = accountType.equals("SUPPLIER", ignoreCase = true)
        return if (isSupplier) {
            if (rounded > 0) BalanceStatus(label = "YOU PAY", isPositiveHealth = false, isSettled = false)
            else BalanceStatus(label = "ADVANCE", isPositiveHealth = true, isSettled = false)
        } else {
            if (rounded > 0) BalanceStatus(label = "YOU GET", isPositiveHealth = true, isSettled = false)
            else BalanceStatus(label = "ADVANCE", isPositiveHealth = false, isSettled = false)
        }
    }

    fun isCreditEntryType(accountType: String, entryType: String): Boolean {
        return if (accountType.equals("SUPPLIER", ignoreCase = true)) {
            entryType.equals("GOT", ignoreCase = true)
        } else {
            entryType.equals("GAVE", ignoreCase = true)
        }
    }

    fun isEntryOverdue(
        account: LedgerAccount,
        entry: LedgerEntry,
        todayIso: String = DateTimeHelper.localDateKey()
    ): Boolean {
        if (KhataMath.isSettled(account.netBalance) || account.netBalance <= 0.0) return false
        if (!isCreditEntryType(account.type, entry.type)) return false
        val due = KhataMetaCodec.parseEntryMeta(entry.description).dueDate
        return due.isNotBlank() && due < todayIso
    }

    fun buildWhatsAppReminderText(
        account: LedgerAccount,
        storeName: String,
        upiId: String,
        entries: List<LedgerEntry>,
        todayIso: String = DateTimeHelper.localDateKey()
    ): String {
        val rounded = KhataMath.roundPaise(account.netBalance)
        val status = statusFor(account.type, rounded)
        val amountStr = KhataMath.formatAmount(rounded)
        val overdueEntries = entries.filter { isEntryOverdue(account, it, todayIso) }
        val overdueBills = overdueEntries.mapNotNull { e ->
            e.billRef.takeIf { it.isNotBlank() }?.let { "#$it" }
        }.distinct().take(3)

        return buildString {
            append("Hello ${account.name},")
            when {
                status.isSettled -> {
                    append("\nYour Khata account with $storeName is currently settled (Rs. 0.00). Thank you!")
                }
                account.type.equals("SUPPLIER", ignoreCase = true) && rounded > 0 -> {
                    append("\nAccount update from $storeName: current payable balance is Rs. $amountStr.")
                }
                account.type.equals("SUPPLIER", ignoreCase = true) && rounded < 0 -> {
                    append("\nAccount update from $storeName: advance balance with you is Rs. $amountStr.")
                }
                rounded < 0 -> {
                    append("\nYour advance balance with $storeName is Rs. $amountStr. Thank you!")
                }
                else -> {
                    append("\nGentle reminder: your pending Khata balance with $storeName is Rs. $amountStr.")
                    if (overdueEntries.isNotEmpty()) {
                        val oldestDue = overdueEntries.minOf { KhataMetaCodec.parseEntryMeta(it.description).dueDate }
                        val billPart = if (overdueBills.isNotEmpty()) " (${overdueBills.joinToString(", ")})" else ""
                        append("\nOverdue since $oldestDue$billPart.")
                    }
                    if (upiId.isNotBlank()) {
                        append("\nYou can pay via UPI ID: ${upiId.trim()}")
                    }
                    append("\nPlease share confirmation once paid. Thank you!")
                }
            }
        }
    }
}
