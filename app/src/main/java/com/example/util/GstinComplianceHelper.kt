package com.example.util

import com.example.data.model.GstBillRecord
import java.util.Locale

enum class StoreGstMode(
    val code: String,
    val label: String,
    val shortLabel: String,
    val docTitle: String,
    val pdfHeaderTag: String
) {
    REGULAR(
        code = "REGULAR",
        label = "Regular GST Registered",
        shortLabel = "Regular GST",
        docTitle = "TAX INVOICE",
        pdfHeaderTag = "TAX INVOICE (Rule 46 CGST Rules)"
    ),
    COMPOSITION(
        code = "COMPOSITION",
        label = "Composition Dealer",
        shortLabel = "Composition",
        docTitle = "BILL OF SUPPLY",
        pdfHeaderTag = "BILL OF SUPPLY (Rule 49 CGST • No ITC / No Tax Charged)"
    ),
    UNREGISTERED(
        code = "UNREGISTERED",
        label = "Unregistered / Retail",
        shortLabel = "Unregistered",
        docTitle = "RETAIL BILL / CASH MEMO",
        pdfHeaderTag = "RETAIL CASH MEMO (Non-GST Unregistered Supplier)"
    );

    companion object {
        fun fromCode(raw: String?): StoreGstMode {
            val clean = raw?.trim()?.uppercase(Locale.US) ?: return REGULAR
            return entries.firstOrNull { it.code == clean } ?: REGULAR
        }
    }
}

enum class Gstr1Bucket(
    val code: String,
    val label: String
) {
    B2B("B2B", "B2B"),
    B2CL("B2CL", "B2CL (>₹2.5L)"),
    B2CS("B2CS", "B2CS (Retail)"),
    COMPOSITION("COMP", "Composition"),
    NON_GST("NON-GST", "Non-GST")
}

data class GstinValidationResult(
    val isValid: Boolean,
    val normalized: String,
    val stateCode: String = "",
    val stateName: String = "",
    val pan: String = "",
    val errorReason: String? = null
)

object GstinComplianceHelper {

    const val RULE_46_B2C_THRESHOLD = 50_000.0
    const val EWAY_BILL_THRESHOLD = 50_000.0
    const val B2CL_THRESHOLD = 250_000.0
    const val CASH_LIMIT_269ST = 200_000.0

    private const val MOD36_CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val GSTIN_REGEX = Regex("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$")

    val INDIAN_STATE_CODES: Map<String, String> = linkedMapOf(
        "01" to "Jammu & Kashmir",
        "02" to "Himachal Pradesh",
        "03" to "Punjab",
        "04" to "Chandigarh",
        "05" to "Uttarakhand",
        "06" to "Haryana",
        "07" to "Delhi",
        "08" to "Rajasthan",
        "09" to "Uttar Pradesh",
        "10" to "Bihar",
        "11" to "Sikkim",
        "12" to "Arunachal Pradesh",
        "13" to "Nagaland",
        "14" to "Manipur",
        "15" to "Mizoram",
        "16" to "Tripura",
        "17" to "Meghalaya",
        "18" to "Assam",
        "19" to "West Bengal",
        "20" to "Jharkhand",
        "21" to "Odisha",
        "22" to "Chhattisgarh",
        "23" to "Madhya Pradesh",
        "24" to "Gujarat",
        "25" to "Daman & Diu",
        "26" to "Dadra & Nagar Haveli and Daman & Diu",
        "27" to "Maharashtra",
        "28" to "Andhra Pradesh (Old)",
        "29" to "Karnataka",
        "30" to "Goa",
        "31" to "Lakshadweep",
        "32" to "Kerala",
        "33" to "Tamil Nadu",
        "34" to "Puducherry",
        "35" to "Andaman & Nicobar Islands",
        "36" to "Telangana",
        "37" to "Andhra Pradesh",
        "38" to "Ladakh",
        "97" to "Other Territory"
    )

    fun normalizeGstin(raw: String?): String =
        raw.orEmpty().replace("\\s+".toRegex(), "").uppercase(Locale.US)

    /**
     * Computes official GSTN Mod-36 check digit character for the first 14 characters.
     */
    fun computeCheckDigit(first14: String): Char? {
        val clean = normalizeGstin(first14)
        if (clean.length != 14) return null
        var sum = 0
        for (i in 0 until 14) {
            val codePoint = MOD36_CHARSET.indexOf(clean[i])
            if (codePoint < 0) return null
            val factor = if (i % 2 == 0) 1 else 2
            val product = codePoint * factor
            val hash = (product / 36) + (product % 36)
            sum += hash
        }
        val checkCodePoint = (36 - (sum % 36)) % 36
        return MOD36_CHARSET[checkCodePoint]
    }

    /**
     * Offline 15-character Indian GSTIN validation:
     * 1) Length = 15
     * 2) Valid Indian State/UT prefix (01-38, 97)
     * 3) Official PAN + Entity + 'Z' + Checksum pattern
     * 4) Mod-36 checksum verification
     */
    fun validateGstin(raw: String?): GstinValidationResult {
        val normalized = normalizeGstin(raw)
        if (normalized.isEmpty()) {
            return GstinValidationResult(
                isValid = false,
                normalized = "",
                errorReason = "GSTIN is empty"
            )
        }
        if (normalized.length != 15) {
            return GstinValidationResult(
                isValid = false,
                normalized = normalized,
                stateCode = normalized.take(2).takeIf { INDIAN_STATE_CODES.containsKey(it) } ?: "",
                stateName = INDIAN_STATE_CODES[normalized.take(2)] ?: "",
                errorReason = "GSTIN must be 15 characters (${normalized.length}/15)"
            )
        }
        val stateCode = normalized.substring(0, 2)
        val stateName = INDIAN_STATE_CODES[stateCode]
        if (stateName == null) {
            return GstinValidationResult(
                isValid = false,
                normalized = normalized,
                stateCode = stateCode,
                errorReason = "Invalid GST state code '$stateCode'"
            )
        }
        val pan = normalized.substring(2, 12)
        if (!GSTIN_REGEX.matches(normalized)) {
            return GstinValidationResult(
                isValid = false,
                normalized = normalized,
                stateCode = stateCode,
                stateName = stateName,
                pan = pan,
                errorReason = "Invalid GSTIN format (check PAN / entity digit / 'Z' at pos 14)"
            )
        }
        val expectedCheck = computeCheckDigit(normalized.substring(0, 14))
        val actualCheck = normalized[14]
        if (expectedCheck == null || actualCheck != expectedCheck) {
            return GstinValidationResult(
                isValid = false,
                normalized = normalized,
                stateCode = stateCode,
                stateName = stateName,
                pan = pan,
                errorReason = "Invalid checksum digit '$actualCheck' (expected '$expectedCheck')"
            )
        }
        return GstinValidationResult(
            isValid = true,
            normalized = normalized,
            stateCode = stateCode,
            stateName = stateName,
            pan = pan,
            errorReason = null
        )
    }

    fun extractStateCode(gstinOrState: String?): String? {
        val clean = normalizeGstin(gstinOrState)
        if (clean.length < 2) return null
        val prefix = clean.substring(0, 2)
        return if (INDIAN_STATE_CODES.containsKey(prefix)) prefix else null
    }

    fun stateNameForCodeOrGstin(gstinOrState: String?): String {
        val code = extractStateCode(gstinOrState) ?: return ""
        return INDIAN_STATE_CODES[code].orEmpty()
    }

    /**
     * Auto-detects Inter-State (IGST = true) vs Intra-State (CGST+SGST = false)
     * whenever both store & customer 2-digit GST state codes are available.
     */
    fun resolveInterState(
        storeGstinOrState: String?,
        customerGstinOrState: String?,
        fallback: Boolean = false
    ): Boolean {
        val storeState = extractStateCode(storeGstinOrState)
        val custState = extractStateCode(customerGstinOrState)
        return if (storeState != null && custState != null) {
            storeState != custState
        } else {
            fallback
        }
    }

    fun isRule46AddressRequired(
        grandTotal: Double,
        customerGstin: String?,
        storeGstMode: StoreGstMode
    ): Boolean {
        return storeGstMode == StoreGstMode.REGULAR &&
            grandTotal >= RULE_46_B2C_THRESHOLD &&
            normalizeGstin(customerGstin).isEmpty()
    }

    fun rule46MissingFieldsMessage(
        customerName: String,
        customerAddress: String
    ): String? {
        val cleanName = customerName.trim()
        val cleanAddr = customerAddress.trim()
        val hasRealName = cleanName.isNotEmpty() &&
            !cleanName.equals("Cash Customer", ignoreCase = true) &&
            !cleanName.equals("Cash / Retail Customer", ignoreCase = true)
        val missing = buildList {
            if (!hasRealName) add("Customer Name")
            if (cleanAddr.isEmpty()) add("Full Address & State")
        }
        if (missing.isEmpty()) return null
        return "Rule 46 compliance (₹50,000+ bill without GSTIN): Enter ${missing.joinToString(" & ")}."
    }

    fun isCashLimitExceeded(cashAmount: Double): Boolean = cashAmount >= CASH_LIMIT_269ST

    fun inferGstMode(
        storeGstin: String?,
        totalTaxAmount: Double,
        configuredMode: StoreGstMode? = null
    ): StoreGstMode {
        if (configuredMode != null) {
            return configuredMode
        }
        val cleanStore = normalizeGstin(storeGstin)
        return if (cleanStore.isEmpty() && totalTaxAmount <= 0.005) {
            StoreGstMode.UNREGISTERED
        } else {
            StoreGstMode.REGULAR
        }
    }

    fun classifyGstr1Bucket(
        bill: GstBillRecord,
        storeGstMode: StoreGstMode? = null
    ): Gstr1Bucket {
        val custGstin = normalizeGstin(bill.customerGstin)
        val storeGstin = normalizeGstin(bill.storeGstin)
        val effectiveMode = when {
            storeGstMode == StoreGstMode.COMPOSITION && bill.totalTaxAmount <= 0.005 -> StoreGstMode.COMPOSITION
            storeGstMode == StoreGstMode.UNREGISTERED && bill.totalTaxAmount <= 0.005 -> StoreGstMode.UNREGISTERED
            storeGstin.isEmpty() && bill.totalTaxAmount <= 0.005 -> StoreGstMode.UNREGISTERED
            else -> StoreGstMode.REGULAR
        }

        return when (effectiveMode) {
            StoreGstMode.COMPOSITION -> Gstr1Bucket.COMPOSITION
            StoreGstMode.UNREGISTERED -> Gstr1Bucket.NON_GST
            StoreGstMode.REGULAR -> when {
                custGstin.isNotEmpty() -> Gstr1Bucket.B2B
                bill.isInterState && bill.grandTotal > B2CL_THRESHOLD -> Gstr1Bucket.B2CL
                else -> Gstr1Bucket.B2CS
            }
        }
    }
}
