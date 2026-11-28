package com.example.data.remote

import org.json.JSONArray
import org.json.JSONObject

/**
 * Full singleton store & banking profile synced across devices via the dedicated
 * Supabase `store_profile` table (id = PROFILE_CLIENT_ID).
 * Note: Role/privilege data is NEVER serialized or accepted in StoreProfileSyncCodec.
 *
 * Nullable string semantics:
 * - null: field not present / not specified (do not overwrite target during partial updates)
 * - "": explicitly cleared / empty
 * - non-blank: active value
 */
data class RemoteStoreProfile(
    val bannerUrl: String? = null,
    val logoUrl: String? = null,
    val storeName: String? = null,
    val location: String? = null,
    val roleTitle: String? = null,
    val supervisorName: String? = null,
    val bankName: String? = null,
    val accountHolder: String? = null,
    val accountNumber: String? = null,
    val ifscCode: String? = null,
    val branchName: String? = null,
    val upiId: String? = null,
    val signatureUrl: String? = null,
    val qrCodeUrl: String? = null,
    val festivalConfigJson: String? = null,
    val updatedAt: Long = 0L,
    val invoicePrefix: String? = null,
    val invoiceNextNum: Int? = null,
    val invoicePadDigits: Int? = null,
    val invoiceIncludeFy: Boolean? = null,
    val invoiceSeqUpdatedAt: Long? = null,
    val quotePrefix: String? = null,
    val quoteNextNum: Int? = null,
    val quotePadDigits: Int? = null,
    val storeGstMode: String? = null,
    val storeGstin: String? = null,
    val storeStateCode: String? = null
)

object StoreProfileSyncCodec {
    const val PROFILE_CLIENT_ID = "00000000-0000-0000-0000-000000000001"
    const val PROFILE_SUPPLIER = "__STORE_PROFILE__"

    private fun JSONObject.optPresentString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val value = optString(key)
        return if (value == "null") null else value
    }

    private fun JSONObject.optPresentInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        val raw = opt(key) ?: return null
        return when (raw) {
            is Number -> raw.toInt()
            is String -> raw.trim().toIntOrNull()
            else -> null
        }
    }

    private fun JSONObject.optPresentLong(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        val raw = opt(key) ?: return null
        return when (raw) {
            is Number -> raw.toLong()
            is String -> raw.trim().toLongOrNull()
            else -> null
        }
    }

    private fun JSONObject.optPresentBoolean(key: String): Boolean? {
        if (!has(key) || isNull(key)) return null
        val raw = opt(key) ?: return null
        return when (raw) {
            is Boolean -> raw
            is String -> when (raw.trim().lowercase()) {
                "true" -> true
                "false" -> false
                else -> null
            }
            else -> null
        }
    }

    fun parseFromObject(first: JSONObject?): RemoteStoreProfile? {
        if (first == null) return null
        val logo = first.optPresentString("logoUrl")
            ?: first.optPresentString("avatarUrl")
        return RemoteStoreProfile(
            bannerUrl = first.optPresentString("bannerUrl"),
            logoUrl = logo,
            storeName = first.optPresentString("storeName"),
            location = first.optPresentString("location"),
            roleTitle = first.optPresentString("roleTitle"),
            supervisorName = first.optPresentString("supervisorName"),
            bankName = first.optPresentString("bankName"),
            accountHolder = first.optPresentString("accountHolder"),
            accountNumber = first.optPresentString("accountNumber"),
            ifscCode = first.optPresentString("ifscCode"),
            branchName = first.optPresentString("branchName"),
            upiId = first.optPresentString("upiId"),
            signatureUrl = first.optPresentString("signatureUrl"),
            qrCodeUrl = first.optPresentString("qrCodeUrl"),
            festivalConfigJson = first.optPresentString("festivalConfigJson"),
            updatedAt = first.optLong("updatedAt", 0L),
            invoicePrefix = first.optPresentString("invoicePrefix"),
            invoiceNextNum = first.optPresentInt("invoiceNextNum"),
            invoicePadDigits = first.optPresentInt("invoicePadDigits"),
            invoiceIncludeFy = first.optPresentBoolean("invoiceIncludeFy"),
            invoiceSeqUpdatedAt = first.optPresentLong("invoiceSeqUpdatedAt"),
            quotePrefix = first.optPresentString("quotePrefix"),
            quoteNextNum = first.optPresentInt("quoteNextNum"),
            quotePadDigits = first.optPresentInt("quotePadDigits"),
            storeGstMode = first.optPresentString("storeGstMode"),
            storeGstin = first.optPresentString("storeGstin"),
            storeStateCode = first.optPresentString("storeStateCode")
        )
    }

    fun parseFromLines(linesAny: Any?): RemoteStoreProfile? {
        var first: JSONObject? = null
        when (linesAny) {
            is JSONArray -> {
                if (linesAny.length() > 0) first = linesAny.optJSONObject(0)
            }
            is JSONObject -> {
                first = linesAny
            }
            is String -> {
                val trimmed = linesAny.trim()
                if (trimmed.isNotBlank()) {
                    try {
                        if (trimmed.startsWith("[")) {
                            val parsedArr = JSONArray(trimmed)
                            if (parsedArr.length() > 0) first = parsedArr.optJSONObject(0)
                        } else if (trimmed.startsWith("{")) {
                            first = JSONObject(trimmed)
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("StoreProfileSync", "Failed parsing store profile JSON string", e)
                    }
                }
            }
        }
        return parseFromObject(first)
    }

    fun parseFromStoreProfileRow(row: JSONObject?): RemoteStoreProfile? {
        if (row == null) return null
        val rawProfile = row.opt("profile_json") ?: row.opt("profileJson")
        val parsed = parseFromLines(rawProfile)
        if (parsed != null) {
            val rowUpdatedAt = row.optLong("updated_at", row.optLong("updatedAt", 0L))
            return if (parsed.updatedAt <= 0L && rowUpdatedAt > 0L) {
                parsed.copy(updatedAt = rowUpdatedAt)
            } else {
                parsed
            }
        }
        return null
    }

    fun toJsonObject(profile: RemoteStoreProfile): JSONObject {
        val ts = if (profile.updatedAt > 0L) profile.updatedAt else System.currentTimeMillis()
        return JSONObject().apply {
            profile.bannerUrl?.let { put("bannerUrl", it) }
            profile.logoUrl?.let {
                put("logoUrl", it)
                put("avatarUrl", it)
            }
            profile.storeName?.let { put("storeName", it) }
            profile.location?.let { put("location", it) }
            profile.roleTitle?.let { put("roleTitle", it) }
            profile.supervisorName?.let { put("supervisorName", it) }
            profile.bankName?.let { put("bankName", it) }
            profile.accountHolder?.let { put("accountHolder", it) }
            profile.accountNumber?.let { put("accountNumber", it) }
            profile.ifscCode?.let { put("ifscCode", it) }
            profile.branchName?.let { put("branchName", it) }
            profile.upiId?.let { put("upiId", it) }
            profile.signatureUrl?.let { put("signatureUrl", it) }
            profile.qrCodeUrl?.let { put("qrCodeUrl", it) }
            profile.festivalConfigJson?.let { put("festivalConfigJson", it) }
            profile.invoicePrefix?.let { put("invoicePrefix", it) }
            profile.invoiceNextNum?.let { put("invoiceNextNum", it) }
            profile.invoicePadDigits?.let { put("invoicePadDigits", it) }
            profile.invoiceIncludeFy?.let { put("invoiceIncludeFy", it) }
            profile.invoiceSeqUpdatedAt?.let { put("invoiceSeqUpdatedAt", it) }
            profile.quotePrefix?.let { put("quotePrefix", it) }
            profile.quoteNextNum?.let { put("quoteNextNum", it) }
            profile.quotePadDigits?.let { put("quotePadDigits", it) }
            profile.storeGstMode?.let { put("storeGstMode", it) }
            profile.storeGstin?.let { put("storeGstin", it) }
            profile.storeStateCode?.let { put("storeStateCode", it) }
            put("updatedAt", ts)
        }
    }

    fun toStoreProfilePayload(profile: RemoteStoreProfile, updatedBy: String? = null): JSONObject {
        val profileObj = toJsonObject(profile)
        return JSONObject().apply {
            put("id", PROFILE_CLIENT_ID)
            put("profile_json", profileObj.toString())
            put("updated_at", profileObj.optLong("updatedAt", System.currentTimeMillis()))
            if (!updatedBy.isNullOrBlank()) {
                put("updated_by", updatedBy.trim().lowercase())
            }
        }
    }

    /**
     * Merges a local update onto an optional existing remote profile so partial updates
     * never wipe unrelated fields that were set on another device.
     */
    fun merge(base: RemoteStoreProfile?, override: RemoteStoreProfile): RemoteStoreProfile {
        if (base == null) return override
        val baseHasSeq = base.invoicePrefix != null || base.invoiceNextNum != null || base.quotePrefix != null || base.quoteNextNum != null
        val overrideHasSeq = override.invoicePrefix != null || override.invoiceNextNum != null || override.quotePrefix != null || override.quoteNextNum != null
        val baseSeqTs = base.invoiceSeqUpdatedAt ?: 0L
        val overSeqTs = override.invoiceSeqUpdatedAt ?: 0L
        val preferOverrideSeq = overrideHasSeq && (!baseHasSeq || overSeqTs >= baseSeqTs)

        val sameInvoiceFormat = (base.invoicePrefix ?: "") == (override.invoicePrefix ?: "") &&
            (base.invoicePadDigits ?: 0) == (override.invoicePadDigits ?: 0) &&
            (base.invoiceIncludeFy ?: false) == (override.invoiceIncludeFy ?: false)

        val mergedInvoiceNextNum = when {
            !overrideHasSeq -> base.invoiceNextNum
            !baseHasSeq -> override.invoiceNextNum
            sameInvoiceFormat && baseSeqTs == overSeqTs -> maxOf(base.invoiceNextNum ?: 1, override.invoiceNextNum ?: 1)
            preferOverrideSeq -> override.invoiceNextNum ?: base.invoiceNextNum
            else -> base.invoiceNextNum ?: override.invoiceNextNum
        }
        val mergedQuoteNextNum = when {
            !overrideHasSeq -> base.quoteNextNum
            !baseHasSeq -> override.quoteNextNum
            (base.quotePrefix ?: "") == (override.quotePrefix ?: "") && baseSeqTs == overSeqTs ->
                maxOf(base.quoteNextNum ?: 1, override.quoteNextNum ?: 1)
            preferOverrideSeq -> override.quoteNextNum ?: base.quoteNextNum
            else -> base.quoteNextNum ?: override.quoteNextNum
        }

        return RemoteStoreProfile(
            bannerUrl = override.bannerUrl ?: base.bannerUrl,
            logoUrl = override.logoUrl ?: base.logoUrl,
            storeName = override.storeName ?: base.storeName,
            location = override.location ?: base.location,
            roleTitle = override.roleTitle ?: base.roleTitle,
            supervisorName = override.supervisorName ?: base.supervisorName,
            bankName = override.bankName ?: base.bankName,
            accountHolder = override.accountHolder ?: base.accountHolder,
            accountNumber = override.accountNumber ?: base.accountNumber,
            ifscCode = override.ifscCode ?: base.ifscCode,
            branchName = override.branchName ?: base.branchName,
            upiId = override.upiId ?: base.upiId,
            signatureUrl = override.signatureUrl ?: base.signatureUrl,
            qrCodeUrl = override.qrCodeUrl ?: base.qrCodeUrl,
            festivalConfigJson = override.festivalConfigJson ?: base.festivalConfigJson,
            updatedAt = maxOf(base.updatedAt, override.updatedAt),
            invoicePrefix = if (preferOverrideSeq) (override.invoicePrefix ?: base.invoicePrefix) else (base.invoicePrefix ?: override.invoicePrefix),
            invoiceNextNum = mergedInvoiceNextNum,
            invoicePadDigits = if (preferOverrideSeq) (override.invoicePadDigits ?: base.invoicePadDigits) else (base.invoicePadDigits ?: override.invoicePadDigits),
            invoiceIncludeFy = if (preferOverrideSeq) (override.invoiceIncludeFy ?: base.invoiceIncludeFy) else (base.invoiceIncludeFy ?: override.invoiceIncludeFy),
            invoiceSeqUpdatedAt = maxOf(baseSeqTs, overSeqTs).takeIf { it > 0L },
            quotePrefix = if (preferOverrideSeq) (override.quotePrefix ?: base.quotePrefix) else (base.quotePrefix ?: override.quotePrefix),
            quoteNextNum = mergedQuoteNextNum,
            quotePadDigits = if (preferOverrideSeq) (override.quotePadDigits ?: base.quotePadDigits) else (base.quotePadDigits ?: override.quotePadDigits),
            storeGstMode = override.storeGstMode ?: base.storeGstMode,
            storeGstin = override.storeGstin ?: base.storeGstin,
            storeStateCode = override.storeStateCode ?: base.storeStateCode
        )
    }
}
