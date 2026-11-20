package com.example.data.auth

import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder

internal class SupabaseAccountOperations(private val auth: AuthManager) {

    private fun jsonMediaType() = "application/json; charset=utf-8".toMediaType()

    private fun supabaseBaseUrl(): String =
        BuildConfig.SUPABASE_URL.trim().trim('"').trimEnd('/')

    private fun supabaseApiKey(): String =
        BuildConfig.SUPABASE_KEY.trim().trim('"')

    fun requestPasswordResetOtp(email: String, callback: (Boolean, String?) -> Unit) {
        val clean = email.trim().lowercase()
        if (!clean.contains("@") || !clean.contains(".")) {
            callback(false, "Enter a valid email address.")
            return
        }
        auth.clearDeepLinkStatus()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!auth.isSupabaseConfigured()) {
                    withContext(Dispatchers.Main) {
                        auth.saveLastSignedInEmail(clean)
                        auth.startOtpExpiryTimer()
                        callback(true, "Reset OTP ready for $clean (expires in 5 minutes).")
                    }
                    return@launch
                }

                val payload = JSONObject().put("email", clean).toString()
                val req = Request.Builder()
                    .url("${supabaseBaseUrl()}/auth/v1/recover")
                    .addHeader("apikey", supabaseApiKey())
                    .addHeader("Authorization", "Bearer ${supabaseApiKey()}")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toRequestBody(jsonMediaType()))
                    .build()

                auth.httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            auth.saveLastSignedInEmail(clean)
                            auth.startOtpExpiryTimer()
                            callback(true, "Password reset OTP sent to $clean (expires in 5 minutes).")
                        }
                    } else {
                        val msg = AuthManager.friendlySupabaseError(body, resp.code)
                        withContext(Dispatchers.Main) {
                            callback(false, msg)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(false, "Failed to send reset OTP: ${e.message ?: "Network error"}")
                }
            }
        }
    }

    fun verifyPasswordResetAndUpdate(
        email: String,
        otp: String,
        newPassword: String,
        callback: (Boolean, String?) -> Unit
    ) {
        val clean = email.trim().lowercase()
        val cleanOtp = otp.trim()
        if (!clean.contains("@") || !clean.contains(".")) {
            callback(false, "Enter a valid email address.")
            return
        }
        if (cleanOtp.length != 6 || !cleanOtp.all { it.isDigit() }) {
            callback(false, "Enter the 6-digit OTP code sent to your email.")
            return
        }
        if (newPassword.length < 6) {
            callback(false, "New password must be at least 6 characters.")
            return
        }
        if (auth.isCurrentOtpExpired()) {
            callback(false, "OTP code has expired (5-minute limit). Tap Resend Reset OTP for a new code.")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!auth.isSupabaseConfigured()) {
                    auth.completeSessionBlocking(clean)
                    withContext(Dispatchers.Main) {
                        callback(true, "Password updated! Welcome back to Stock.")
                    }
                    return@launch
                }

                val verifyPayload = JSONObject()
                    .put("type", "recovery")
                    .put("email", clean)
                    .put("token", cleanOtp)
                    .toString()

                val verifyReq = Request.Builder()
                    .url("${supabaseBaseUrl()}/auth/v1/verify")
                    .addHeader("apikey", supabaseApiKey())
                    .addHeader("Authorization", "Bearer ${supabaseApiKey()}")
                    .addHeader("Content-Type", "application/json")
                    .post(verifyPayload.toRequestBody(jsonMediaType()))
                    .build()

                var recoveryToken = ""
                var userObj: JSONObject? = null
                var verifyError: String? = null

                auth.httpClient.newCall(verifyReq).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val obj = try { JSONObject(body) } catch (_: Exception) { null }
                        recoveryToken = obj?.optString("access_token").orEmpty()
                        userObj = obj?.optJSONObject("user")
                        if (recoveryToken.isBlank()) {
                            verifyError = "Could not verify recovery OTP token."
                        }
                    } else {
                        verifyError = AuthManager.friendlySupabaseError(body, resp.code)
                    }
                }

                if (verifyError != null) {
                    withContext(Dispatchers.Main) { callback(false, verifyError) }
                    return@launch
                }

                val updatePayload = JSONObject().put("password", newPassword).toString()
                val updateReq = Request.Builder()
                    .url("${supabaseBaseUrl()}/auth/v1/user")
                    .addHeader("apikey", supabaseApiKey())
                    .addHeader("Authorization", "Bearer $recoveryToken")
                    .addHeader("Content-Type", "application/json")
                    .put(updatePayload.toRequestBody(jsonMediaType()))
                    .build()

                auth.httpClient.newCall(updateReq).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val updatedUser = try { JSONObject(body) } catch (_: Exception) { userObj }
                        auth.completeSessionBlocking(
                            email = clean,
                            accessToken = recoveryToken,
                            metadataIsAdmin = AuthManager.isMetadataAdmin(updatedUser ?: userObj)
                        )
                        withContext(Dispatchers.Main) {
                            callback(true, "Password updated! You are now signed in.")
                        }
                    } else {
                        val msg = AuthManager.friendlySupabaseError(body, resp.code)
                        withContext(Dispatchers.Main) {
                            callback(false, "Failed to update password: $msg")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(false, "Password reset failed: ${e.message ?: "Network error"}")
                }
            }
        }
    }

    fun requestChangeEmailOtp(newEmail: String, callback: (Boolean, String?) -> Unit) {
        val cleanNew = newEmail.trim().lowercase()
        if (!cleanNew.contains("@") || !cleanNew.contains(".")) {
            callback(false, "Enter a valid new email address.")
            return
        }
        val currentEmail = auth.currentUserEmail.value?.trim()?.lowercase()
        if (!currentEmail.isNullOrBlank() && cleanNew == currentEmail) {
            callback(false, "New email address must be different from your current email.")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!auth.isSupabaseConfigured()) {
                    withContext(Dispatchers.Main) {
                        auth.startOtpExpiryTimer()
                        callback(true, "Verification OTP ready for $cleanNew (expires in 5 minutes).")
                    }
                    return@launch
                }

                val token = auth.activeAccessToken()
                if (token.isBlank()) {
                    withContext(Dispatchers.Main) {
                        callback(false, "Active session expired. Sign out and sign back in to change your email.")
                    }
                    return@launch
                }

                val payload = JSONObject().put("email", cleanNew).toString()
                val req = Request.Builder()
                    .url("${supabaseBaseUrl()}/auth/v1/user")
                    .addHeader("apikey", supabaseApiKey())
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .put(payload.toRequestBody(jsonMediaType()))
                    .build()

                auth.httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            auth.startOtpExpiryTimer()
                            callback(true, "Verification OTP sent to $cleanNew (expires in 5 minutes).")
                        }
                    } else {
                        val msg = AuthManager.friendlySupabaseError(body, resp.code)
                        withContext(Dispatchers.Main) {
                            callback(false, msg)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(false, "Could not request email change: ${e.message ?: "Network error"}")
                }
            }
        }
    }

    fun confirmChangeEmailOtp(
        newEmail: String,
        otp: String,
        callback: (Boolean, String?) -> Unit
    ) {
        val cleanNew = newEmail.trim().lowercase()
        val cleanOtp = otp.trim()
        if (!cleanNew.contains("@") || !cleanNew.contains(".")) {
            callback(false, "Enter a valid new email address.")
            return
        }
        if (cleanOtp.length != 6 || !cleanOtp.all { it.isDigit() }) {
            callback(false, "Enter the 6-digit OTP code sent to your email.")
            return
        }
        if (auth.isCurrentOtpExpired()) {
            callback(false, "OTP code has expired (5-minute limit). Request a new verification code.")
            return
        }

        val oldEmail = auth.currentUserEmail.value?.trim()?.lowercase()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!auth.isSupabaseConfigured()) {
                    auth.completeEmailChangeLocally(oldEmail, cleanNew, "")
                    withContext(Dispatchers.Main) {
                        callback(true, "Email address updated to $cleanNew.")
                    }
                    return@launch
                }

                val activeToken = auth.activeAccessToken()
                val verifyPayload = JSONObject()
                    .put("type", "email_change")
                    .put("email", cleanNew)
                    .put("token", cleanOtp)
                    .toString()

                val reqBuilder = Request.Builder()
                    .url("${supabaseBaseUrl()}/auth/v1/verify")
                    .addHeader("apikey", supabaseApiKey())
                    .addHeader("Authorization", "Bearer ${activeToken.ifBlank { supabaseApiKey() }}")
                    .addHeader("Content-Type", "application/json")
                    .post(verifyPayload.toRequestBody(jsonMediaType()))

                auth.httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val obj = try { JSONObject(body) } catch (_: Exception) { null }
                        val userObj = obj?.optJSONObject("user")
                        val verifiedEmail = userObj?.optString("email")?.trim()?.lowercase().orEmpty()
                        val newToken = obj?.optString("access_token").orEmpty()

                        if (verifiedEmail.isNotBlank() && verifiedEmail != cleanNew) {
                            withContext(Dispatchers.Main) {
                                auth.startOtpExpiryTimer()
                                callback(
                                    false,
                                    "First confirmation received! Now enter the OTP sent to $cleanNew (or disable 'Secure email change' in Supabase Auth settings for single-step verification)."
                                )
                            }
                            return@use
                        }

                        auth.completeEmailChangeLocally(oldEmail, cleanNew, newToken)
                        withContext(Dispatchers.Main) {
                            callback(true, "Email address updated to $cleanNew.")
                        }
                    } else {
                        val msg = AuthManager.friendlySupabaseError(body, resp.code)
                        withContext(Dispatchers.Main) {
                            callback(false, msg)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(false, "Failed to update email: ${e.message ?: "Network error"}")
                }
            }
        }
    }

    fun requestDeleteAccountOtp(callback: (Boolean, String?) -> Unit) {
        val activeEmail = auth.currentUserEmail.value?.trim()?.lowercase()
        if (activeEmail.isNullOrBlank()) {
            callback(false, "No signed-in email found. Please sign in with your email first.")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!auth.isSupabaseConfigured()) {
                    withContext(Dispatchers.Main) {
                        auth.startOtpExpiryTimer()
                        callback(true, "Deletion OTP ready for $activeEmail (expires in 5 minutes).")
                    }
                    return@launch
                }

                val payload = JSONObject()
                    .put("email", activeEmail)
                    .put("create_user", false)
                    .toString()

                val req = Request.Builder()
                    .url("${supabaseBaseUrl()}/auth/v1/otp")
                    .addHeader("apikey", supabaseApiKey())
                    .addHeader("Authorization", "Bearer ${supabaseApiKey()}")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toRequestBody(jsonMediaType()))
                    .build()

                auth.httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            auth.startOtpExpiryTimer()
                            callback(true, "Account deletion OTP sent to $activeEmail (expires in 5 minutes).")
                        }
                    } else {
                        val msg = AuthManager.friendlySupabaseError(body, resp.code)
                        withContext(Dispatchers.Main) {
                            callback(false, msg)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(false, "Failed to send deletion OTP: ${e.message ?: "Network error"}")
                }
            }
        }
    }

    fun confirmDeleteAccountWithOtp(otp: String, callback: (Boolean, String?) -> Unit) {
        val activeEmail = auth.currentUserEmail.value?.trim()?.lowercase()
        if (activeEmail.isNullOrBlank()) {
            callback(false, "No signed-in email found.")
            return
        }
        val cleanOtp = otp.trim()
        if (cleanOtp.length != 6 || !cleanOtp.all { it.isDigit() }) {
            callback(false, "Enter the 6-digit OTP code sent to your email.")
            return
        }
        if (auth.isCurrentOtpExpired()) {
            callback(false, "OTP code has expired (5-minute limit). Request a new deletion code.")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!auth.isSupabaseConfigured()) {
                    auth.completeAccountDeletionLocally(activeEmail)
                    withContext(Dispatchers.Main) {
                        callback(true, "Account permanently deleted.")
                    }
                    return@launch
                }

                // 1. Verify OTP ownership with Supabase Auth
                val verifyPayload = JSONObject()
                    .put("type", "email")
                    .put("email", activeEmail)
                    .put("token", cleanOtp)
                    .toString()

                val verifyReq = Request.Builder()
                    .url("${supabaseBaseUrl()}/auth/v1/verify")
                    .addHeader("apikey", supabaseApiKey())
                    .addHeader("Authorization", "Bearer ${supabaseApiKey()}")
                    .addHeader("Content-Type", "application/json")
                    .post(verifyPayload.toRequestBody(jsonMediaType()))
                    .build()

                var verifiedToken = ""
                var verifyError: String? = null

                auth.httpClient.newCall(verifyReq).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val obj = try { JSONObject(body) } catch (_: Exception) { null }
                        verifiedToken = obj?.optString("access_token").orEmpty()
                        if (verifiedToken.isBlank()) {
                            verifyError = "OTP verification did not return a valid session."
                        }
                    } else {
                        verifyError = AuthManager.friendlySupabaseError(body, resp.code)
                    }
                }

                if (verifyError != null) {
                    withContext(Dispatchers.Main) { callback(false, verifyError) }
                    return@launch
                }

                // 2. Remove row from app_users if RLS permits
                try {
                    val encodedEmail = URLEncoder.encode(activeEmail, "UTF-8")
                    val deleteRowReq = Request.Builder()
                        .url("${supabaseBaseUrl()}/rest/v1/app_users?email=eq.$encodedEmail")
                        .addHeader("apikey", supabaseApiKey())
                        .addHeader("Authorization", "Bearer $verifiedToken")
                        .delete()
                        .build()
                    auth.httpClient.newCall(deleteRowReq).execute().close()
                } catch (_: Exception) {
                }

                // 3. Delete authenticated user via Supabase RPC delete_user_account()
                val rpcReq = Request.Builder()
                    .url("${supabaseBaseUrl()}/rest/v1/rpc/delete_user_account")
                    .addHeader("apikey", supabaseApiKey())
                    .addHeader("Authorization", "Bearer $verifiedToken")
                    .addHeader("Content-Type", "application/json")
                    .post("{}".toRequestBody(jsonMediaType()))
                    .build()

                auth.httpClient.newCall(rpcReq).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        auth.completeAccountDeletionLocally(activeEmail)
                        withContext(Dispatchers.Main) {
                            callback(true, "Your account has been permanently deleted.")
                        }
                    } else {
                        val rawMsg = AuthManager.friendlySupabaseError(body, resp.code)
                        val friendlyMsg = if (resp.code == 404 || rawMsg.contains("delete_user_account", ignoreCase = true)) {
                            "OTP verified, but the delete_user_account() SQL function is missing in Supabase. Add it in Supabase SQL Editor and retry."
                        } else {
                            "Could not delete account: $rawMsg"
                        }
                        withContext(Dispatchers.Main) {
                            callback(false, friendlyMsg)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(false, "Account deletion failed: ${e.message ?: "Network error"}")
                }
            }
        }
    }
}
