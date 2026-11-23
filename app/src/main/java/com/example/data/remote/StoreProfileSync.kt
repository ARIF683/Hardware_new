package com.example.data.remote

import org.json.JSONArray
import org.json.JSONObject

/**
 * Full singleton store & banking profile synced across devices via Supabase
 * (`purchases` sentinel row with client_id = PROFILE_CLIENT_ID and supplier = PROFILE_SUPPLIER).
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
    val userRolesJson: String? = null,
    val updatedAt: Long = 0L
)

object StoreProfileSyncCodec {
    const val PROFILE_CLIENT_ID = "00000000-0000-0000-0000-000000000001"
    const val PROFILE_SUPPLIER = "__STORE_PROFILE__"

    private fun JSONObject.optPresentString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val value = optString(key)
        return if (value == "null") null else value
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
            userRolesJson = first.optPresentString("userRolesJson"),
            updatedAt = first.optLong("updatedAt", 0L)
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

    fun toJsonObject(profile: RemoteStoreProfile): JSONObject {
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
            profile.userRolesJson?.let { put("userRolesJson", it) }
            if (profile.updatedAt > 0L) {
                put("updatedAt", profile.updatedAt)
            } else {
                put("updatedAt", System.currentTimeMillis())
            }
        }
    }

    fun toPurchasesPayload(profile: RemoteStoreProfile, billDate: String): JSONObject {
        val linesArr = JSONArray().apply {
            put(toJsonObject(profile))
        }
        return JSONObject().apply {
            put("client_id", PROFILE_CLIENT_ID)
            put("supplier", PROFILE_SUPPLIER)
            put("bill_no", "CONFIG")
            put("bill_date", billDate)
            put("total", 0)
            put("lines", linesArr)
        }
    }

    /**
     * Merges a local update onto an optional existing remote profile so partial updates
     * never wipe unrelated fields that were set on another device.
     */
    fun merge(base: RemoteStoreProfile?, override: RemoteStoreProfile): RemoteStoreProfile {
        if (base == null) return override
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
            userRolesJson = override.userRolesJson ?: base.userRolesJson,
            updatedAt = maxOf(base.updatedAt, override.updatedAt)
        )
    }
}
