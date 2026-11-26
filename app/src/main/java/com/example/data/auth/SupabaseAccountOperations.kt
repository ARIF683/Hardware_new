package com.example.data.auth

import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
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

    fun requestPasswordResetOtp(
        email: String,
        callback: (Boolean, String?) -> Unit
    ) {
        if (auth.isLocked()) {
            callback(false, "Too many failed attempts. Try again in ${auth.getRemainingSeconds()}s.")
            return
        }
        val cooldown = auth.getResendCooldownRemainingSeconds()
        if (cooldown > 0) {
            callback(false, "Please wait ${cooldown}s before requesting another OTP.")
            return
        }
        val clean = email.trim().lowercase()
        if (!AuthManager.isValidEmail(clean)) {
            callback(false, "Enter a valid email address.")
            return
        }
        auth.clearDeepLinkStatus()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                auth.authNetworkMutex.withLock {
                    if (auth.isLocked()) {
                        withContext(Dispatchers.Main) {
                            callback(false, "Too many failed attempts. Try again in ${auth.getRemainingSeconds()}s.")
                        }
                        return@withLock
                    }
                    if (!auth.isSupabaseConfigured() || auth.allowOfflineTestFallback()) {
                        if (auth.allowOfflineTestFallback()) {
                            withContext(Dispatchers.Main) {
                                auth.saveLastSignedInEmail(clean)
                                auth.startOtpExpiryTimer()
                                auth.startResendCooldown()
                                callback(true, "Reset OTP ready for $clean (expires in 5 minutes).")
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                callback(false, auth.unconfiguredAuthMessage())
                            }
                        }
                        return@withLock
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
                                auth.startResendCooldown()
                                callback(true, "Password reset OTP sent to $clean (expires in 5 minutes).")
                            }
                        } else if (resp.code == 429) {
                            val retryAfter = AuthManager.parseRetryAfterSeconds(resp.header("Retry-After"))
                            val lockMsg = auth.recordFailedAuthAttempt(httpStatusCode = 429, retryAfterSeconds = retryAfter)
                            withContext(Dispatchers.Main) {
                                callback(false, lockMsg ?: AuthManager.friendlySupabaseError(body, resp.code))
                            }
                        } else {
                            val msg = AuthManager.friendlySupabaseError(body, resp.code)
                            withContext(Dispatchers.Main) {
                                callback(false, msg)
                            }
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
        if (auth.isLocked()) {
            callback(false, "Too many failed attempts. Try again in ${auth.getRemainingSeconds()}s.")
            return
        }
        val clean = email.trim().lowercase()
        val cleanOtp = otp.trim()
        if (!AuthManager.isValidEmail(clean)) {
            callback(false, "Enter a valid email address.")
            return
        }
        if (cleanOtp.length != 6 || !cleanOtp.all { it.isDigit() }) {
            callback(false, "Enter the 6-digit OTP code sent to your email.")
            return
        }
        if (!AuthManager.isValidPassword(newPassword)) {
            callback(false, "New password must be at least ${AuthManager.MIN_PASSWORD_LENGTH} characters.")
            return
        }
        if (auth.isCurrentOtpExpired()) {
            callback(false, "OTP code has expired (5-minute limit). Tap Resend Reset OTP for a new code.")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                auth.authNetworkMutex.withLock {
                    if (auth.isLocked()) {
                        withContext(Dispatchers.Main) {
                            callback(false, "Too many failed attempts. Try again in ${auth.getRemainingSeconds()}s.")
                        }
                        return@withLock
                    }
                    if (!auth.isSupabaseConfigured() || auth.allowOfflineTestFallback()) {
                        if (auth.allowOfflineTestFallback()) {
                            auth.resetAuthFailures()
                            auth.completeSessionBlocking(clean)
                            withContext(Dispatchers.Main) {
                                callback(true, "Password updated! Welcome back to Stock.")
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                callback(false, auth.unconfiguredAuthMessage())
                            }
                        }
                        return@withLock
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
                    var recoveryRefreshToken = ""
                    var userObj: JSONObject? = null
                    var verifyError: String? = null

                    auth.httpClient.newCall(verifyReq).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        if (resp.isSuccessful) {
                            val obj = try { JSONObject(body) } catch (_: Exception) { null }
                            recoveryToken = obj?.optString("access_token").orEmpty()
                            recoveryRefreshToken = obj?.optString("refresh_token").orEmpty()
                            userObj = obj?.optJSONObject("user")
                            if (recoveryToken.isBlank()) {
                                verifyError = "Could not verify recovery OTP token."
                            }
                        } else {
                            val msg = AuthManager.friendlySupabaseError(body, resp.code)
                            val retryAfter = AuthManager.parseRetryAfterSeconds(resp.header("Retry-After"))
                            val lockHint = auth.recordFailedAuthAttempt(httpStatusCode = resp.code, retryAfterSeconds = retryAfter)
                            verifyError = if (lockHint != null && lockHint.startsWith("Too many")) lockHint else "$msg ${lockHint.orEmpty()}".trim()
                        }
                    }

                    if (verifyError != null) {
                        withContext(Dispatchers.Main) { callback(false, verifyError) }
                        return@withLock
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
                            auth.resetAuthFailures()
                            auth.completeSessionBlocking(
                                email = clean,
                                accessToken = recoveryToken,
                                refreshToken = recoveryRefreshToken,
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
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(false, "Password reset failed: ${e.message ?: "Network error"}")
                }
            }
        }
    }

    fun requestChangeEmailOtp(newEmail: String, callback: (Boolean, String?) -> Unit) {
        if (auth.isLocked()) {
            callback(false, "Too many failed attempts. Try again in ${auth.getRemainingSeconds()}s.")
            return
        }
        val cooldown = auth.getResendCooldownRemainingSeconds()
        if (cooldown > 0) {
            callback(false, "Please wait ${cooldown}s before requesting another OTP.")
            return
        }
        val cleanNew = newEmail.trim().lowercase()
        if (!AuthManager.isValidEmail(cleanNew)) {
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
                auth.authNetworkMutex.withLock {
                    if (auth.isLocked()) {
                        withContext(Dispatchers.Main) {
                            callback(false, "Too many failed attempts. Try again in ${auth.getRemainingSeconds()}s.")
                        }
                        return@withLock
                    }
                    if (!auth.isSupabaseConfigured() || auth.allowOfflineTestFallback()) {
                        if (auth.allowOfflineTestFallback()) {
                            withContext(Dispatchers.Main) {
                                auth.startOtpExpiryTimer()
                                auth.startResendCooldown()
                                callback(true, "Verification OTP ready for $cleanNew (expires in 5 minutes).")
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                callback(false, auth.unconfiguredAuthMessage())
                            }
                        }
                        return@withLock
                    }

                    var token = auth.activeAccessToken()
                    if (token.isBlank()) {
                        withContext(Dispatchers.Main) {
                            callback(false, "Active session expired. Sign out and sign back in to change your email.")
                        }
                        return@withLock
                    }

                    val payload = JSONObject().put("email", cleanNew).toString()

                    fun executeChangeRequest(bearer: String) = Request.Builder()
                        .url("${supabaseBaseUrl()}/auth/v1/user")
                        .addHeader("apikey", supabaseApiKey())
                        .addHeader("Authorization", "Bearer $bearer")
                        .addHeader("Content-Type", "application/json")
                        .put(payload.toRequestBody(jsonMediaType()))
                        .build()

                    var resp = auth.httpClient.newCall(executeChangeRequest(token)).execute()
                    if (resp.code == 401 && auth.refreshSessionBlocking()) {
                        resp.close()
                        token = auth.activeAccessToken()
                        resp = auth.httpClient.newCall(executeChangeRequest(token)).execute()
                    }

                    resp.use { finalResp ->
                        val body = finalResp.body?.string().orEmpty()
                        if (finalResp.isSuccessful) {
                            withContext(Dispatchers.Main) {
                                auth.startOtpExpiryTimer()
                                auth.startResendCooldown()
                                callback(true, "Verification OTP sent to $cleanNew (expires in 5 minutes).")
                            }
                        } else if (finalResp.code == 429) {
                            val retryAfter = AuthManager.parseRetryAfterSeconds(finalResp.header("Retry-After"))
                            val lockMsg = auth.recordFailedAuthAttempt(httpStatusCode = 429, retryAfterSeconds = retryAfter)
                            withContext(Dispatchers.Main) {
                                callback(false, lockMsg ?: AuthManager.friendlySupabaseError(body, finalResp.code))
                            }
                        } else {
                            val msg = AuthManager.friendlySupabaseError(body, finalResp.code)
                            withContext(Dispatchers.Main) {
                                callback(false, msg)
                            }
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
        if (auth.isLocked()) {
            callback(false, "Too many failed attempts. Try again in ${auth.getRemainingSeconds()}s.")
            return
        }
        val cleanNew = newEmail.trim().lowercase()
        val cleanOtp = otp.trim()
        if (!AuthManager.isValidEmail(cleanNew)) {
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
                auth.authNetworkMutex.withLock {
                    if (auth.isLocked()) {
                        withContext(Dispatchers.Main) {
                            callback(false, "Too many failed attempts. Try again in ${auth.getRemainingSeconds()}s.")
                        }
                        return@withLock
                    }
                    if (!auth.isSupabaseConfigured() || auth.allowOfflineTestFallback()) {
                        if (auth.allowOfflineTestFallback()) {
                            auth.resetAuthFailures()
                            auth.completeEmailChangeLocally(oldEmail, cleanNew, "")
                            withContext(Dispatchers.Main) {
                                callback(true, "Email address updated to $cleanNew.")
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                callback(false, auth.unconfiguredAuthMessage())
                            }
                        }
                        return@withLock
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
                            val newRefreshToken = obj?.optString("refresh_token").orEmpty()

                            if (verifiedEmail.isNotBlank() && verifiedEmail != cleanNew) {
                                withContext(Dispatchers.Main) {
                                    auth.startOtpExpiryTimer()
                                    auth.startResendCooldown()
                                    callback(
                                        false,
                                        "First confirmation received! Now enter the OTP sent to $cleanNew (or disable 'Secure email change' in Supabase Auth settings for single-step verification)."
                                    )
                                }
                                return@use
                            }

                            auth.resetAuthFailures()
                            auth.completeEmailChangeLocally(oldEmail, cleanNew, newToken, newRefreshToken)
                            withContext(Dispatchers.Main) {
                                callback(true, "Email address updated to $cleanNew.")
                            }
                        } else {
                            val msg = AuthManager.friendlySupabaseError(body, resp.code)
                            val retryAfter = AuthManager.parseRetryAfterSeconds(resp.header("Retry-After"))
                            val lockHint = auth.recordFailedAuthAttempt(httpStatusCode = resp.code, retryAfterSeconds = retryAfter)
                            val fullMsg = if (lockHint != null && lockHint.startsWith("Too many")) lockHint else "$msg ${lockHint.orEmpty()}".trim()
                            withContext(Dispatchers.Main) {
                                callback(false, fullMsg)
                            }
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

    fun requestDeleteAccountOtp(
        callback: (Boolean, String?) -> Unit
    ) {
        if (auth.isLocked()) {
            callback(false, "Too many failed attempts. Try again in ${auth.getRemainingSeconds()}s.")
            return
        }
        val cooldown = auth.getResendCooldownRemainingSeconds()
        if (cooldown > 0) {
            callback(false, "Please wait ${cooldown}s before requesting another OTP.")
            return
        }
        val activeEmail = auth.currentUserEmail.value?.trim()?.lowercase()
        if (activeEmail.isNullOrBlank()) {
            callback(false, "No signed-in email found. Please sign in with your email first.")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                auth.authNetworkMutex.withLock {
                    if (auth.isLocked()) {
                        withContext(Dispatchers.Main) {
                            callback(false, "Too many failed attempts. Try again in ${auth.getRemainingSeconds()}s.")
                        }
                        return@withLock
                    }
                    if (!auth.isSupabaseConfigured() || auth.allowOfflineTestFallback()) {
                        if (auth.allowOfflineTestFallback()) {
                            withContext(Dispatchers.Main) {
                                auth.startOtpExpiryTimer()
                                auth.startResendCooldown()
                                callback(true, "Deletion OTP ready for $activeEmail (expires in 5 minutes).")
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                callback(false, auth.unconfiguredAuthMessage())
                            }
                        }
                        return@withLock
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
                                auth.startResendCooldown()
                                callback(true, "Account deletion OTP sent to $activeEmail (expires in 5 minutes).")
                            }
                        } else if (resp.code == 429) {
                            val retryAfter = AuthManager.parseRetryAfterSeconds(resp.header("Retry-After"))
                            val lockMsg = auth.recordFailedAuthAttempt(httpStatusCode = 429, retryAfterSeconds = retryAfter)
                            withContext(Dispatchers.Main) {
                                callback(false, lockMsg ?: AuthManager.friendlySupabaseError(body, resp.code))
                            }
                        } else {
                            val msg = AuthManager.friendlySupabaseError(body, resp.code)
                            withContext(Dispatchers.Main) {
                                callback(false, msg)
                            }
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
        if (auth.isLocked()) {
            callback(false, "Too many failed attempts. Try again in ${auth.getRemainingSeconds()}s.")
            return
        }
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
                auth.authNetworkMutex.withLock {
                    if (auth.isLocked()) {
                        withContext(Dispatchers.Main) {
                            callback(false, "Too many failed attempts. Try again in ${auth.getRemainingSeconds()}s.")
                        }
                        return@withLock
                    }
                    if (!auth.isSupabaseConfigured() || auth.allowOfflineTestFallback()) {
                        if (auth.allowOfflineTestFallback()) {
                            auth.resetAuthFailures()
                            auth.completeAccountDeletionLocally(activeEmail)
                            withContext(Dispatchers.Main) {
                                callback(true, "Account permanently deleted.")
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                callback(false, auth.unconfiguredAuthMessage())
                            }
                        }
                        return@withLock
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
                            } else {
                                auth.resetAuthFailures()
                            }
                        } else {
                            val msg = AuthManager.friendlySupabaseError(body, resp.code)
                            val retryAfter = AuthManager.parseRetryAfterSeconds(resp.header("Retry-After"))
                            val lockHint = auth.recordFailedAuthAttempt(httpStatusCode = resp.code, retryAfterSeconds = retryAfter)
                            verifyError = if (lockHint != null && lockHint.startsWith("Too many")) lockHint else "$msg ${lockHint.orEmpty()}".trim()
                        }
                    }

                    if (verifyError != null) {
                        withContext(Dispatchers.Main) { callback(false, verifyError) }
                        return@withLock
                    }

                    // 2. Atomically remove app_users row + auth user via Supabase RPC delete_user_account()
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
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(false, "Account deletion failed: ${e.message ?: "Network error"}")
                }
            }
        }
    }
}
