package com.example.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class UserAccount(val email: String, val isAdmin: Boolean)

class AuthManager(context: Context) {
    companion object {
        const val APP_HASH = "2f03a273df7e7fc706f021d3722238fcceda044783a8c9082087b8b941de8c3e"
        const val MAX_TRIES = 3
        const val LOCK_MS = 60_000L

        const val AUTH_REDIRECT_SCHEME = "hardwarestock"
        const val AUTH_REDIRECT_HOST = "auth-callback"
        const val AUTH_REDIRECT_URL = "$AUTH_REDIRECT_SCHEME://$AUTH_REDIRECT_HOST"

        private const val PREFS_NAME = "stock_manager_auth"
        private const val KEY_AUTHED = "is_authed"
        private const val KEY_ADMIN = "is_admin"
        private const val KEY_CURRENT_USER_EMAIL = "current_user_email"
        private const val KEY_FAILS = "login_fails"
        private const val KEY_LOCK_UNTIL = "lock_until"
        private const val KEY_REGISTERED_USERS = "registered_users"
        private const val KEY_ROLES_UPDATED_AT = "registered_users_updated_at"
        private const val KEY_PENDING_SIGNUP_EMAIL = "pending_signup_email"
        private const val KEY_LAST_EMAIL = "last_signed_in_email"
        private const val KEY_SUPABASE_ACCESS_TOKEN = "supabase_access_token"

        fun isAuthDeepLink(uri: Uri?): Boolean {
            return uri != null &&
                uri.scheme.equals(AUTH_REDIRECT_SCHEME, ignoreCase = true) &&
                uri.host.equals(AUTH_REDIRECT_HOST, ignoreCase = true)
        }

        internal fun parseEncodedParams(raw: String?): Map<String, String> {
            if (raw.isNullOrBlank()) return emptyMap()
            val map = linkedMapOf<String, String>()
            for (pair in raw.split("&")) {
                if (pair.isBlank()) continue
                val idx = pair.indexOf('=')
                if (idx > 0) {
                    try {
                        val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                        val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                        map[key] = value
                    } catch (_: Exception) {
                        map[pair.substring(0, idx)] = pair.substring(idx + 1)
                    }
                }
            }
            return map
        }

        internal fun extractSupabaseError(rawBody: String, fallbackCode: Int): String {
            return try {
                val obj = JSONObject(rawBody)
                obj.optString("error_description").takeIf { it.isNotBlank() }
                    ?: obj.optString("msg").takeIf { it.isNotBlank() }
                    ?: obj.optString("message").takeIf { it.isNotBlank() }
                    ?: obj.optString("error").takeIf { it.isNotBlank() }
                    ?: "HTTP $fallbackCode"
            } catch (_: Exception) {
                rawBody.takeIf { it.isNotBlank() } ?: "HTTP $fallbackCode"
            }
        }

        internal fun isEmailConfirmed(userObj: JSONObject?): Boolean {
            if (userObj == null) return false
            val emailConfirmed = userObj.optString("email_confirmed_at").trim()
            val confirmedAt = userObj.optString("confirmed_at").trim()
            return (emailConfirmed.isNotBlank() && emailConfirmed != "null") ||
                (confirmedAt.isNotBlank() && confirmedAt != "null")
        }

        internal fun friendlySupabaseError(rawBody: String, fallbackCode: Int): String {
            val extracted = extractSupabaseError(rawBody, fallbackCode)
            return if (extracted.contains("email not confirmed", ignoreCase = true)) {
                "Your email is not verified yet. Open your inbox and tap the confirmation link first."
            } else {
                extracted
            }
        }

        internal fun isMetadataAdmin(userObj: JSONObject?): Boolean {
            if (userObj == null) return false
            val appMeta = userObj.optJSONObject("app_metadata")
            val userMeta = userObj.optJSONObject("user_metadata")
            return appMeta?.optBoolean("is_admin", false) == true ||
                appMeta?.optString("role").equals("admin", ignoreCase = true) ||
                userMeta?.optBoolean("is_admin", false) == true ||
                userMeta?.optString("role").equals("admin", ignoreCase = true)
        }
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isAuthed = MutableStateFlow(prefs.getBoolean(KEY_AUTHED, false))
    val isAuthed: StateFlow<Boolean> = _isAuthed.asStateFlow()

    private val _isAdmin = MutableStateFlow(prefs.getBoolean(KEY_ADMIN, false))
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _currentUserEmail = MutableStateFlow(
        prefs.getString(KEY_CURRENT_USER_EMAIL, null)?.takeIf { it.isNotBlank() }
    )
    val currentUserEmail: StateFlow<String?> = _currentUserEmail.asStateFlow()

    private val _lockRemainingSeconds = MutableStateFlow(0)
    val lockRemainingSeconds: StateFlow<Int> = _lockRemainingSeconds.asStateFlow()

    private val _registeredUsers = MutableStateFlow<List<UserAccount>>(loadInitialUsers())
    val registeredUsers: StateFlow<List<UserAccount>> = _registeredUsers.asStateFlow()

    private val _pendingVerificationEmail = MutableStateFlow(
        prefs.getString(KEY_PENDING_SIGNUP_EMAIL, null)?.takeIf { it.isNotBlank() }
    )
    val pendingVerificationEmail: StateFlow<String?> = _pendingVerificationEmail.asStateFlow()

    private val _authDeepLinkMessage = MutableStateFlow<String?>(null)
    val authDeepLinkMessage: StateFlow<String?> = _authDeepLinkMessage.asStateFlow()

    private val _authDeepLinkError = MutableStateFlow<String?>(null)
    val authDeepLinkError: StateFlow<String?> = _authDeepLinkError.asStateFlow()

    var onRolesChangedListener: ((String, Long) -> Unit)? = null

    private var loginFails = prefs.getInt(KEY_FAILS, 0)
    private var lockUntil = prefs.getLong(KEY_LOCK_UNTIL, 0L)
    private var tickerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    init {
        val now = System.currentTimeMillis()
        if (lockUntil > now) {
            startLockCountdown()
        } else {
            lockUntil = 0
            loginFails = 0
            prefs.edit().remove(KEY_LOCK_UNTIL).remove(KEY_FAILS).apply()
        }
    }

    fun isLocked(): Boolean = System.currentTimeMillis() < lockUntil

    fun getRemainingSeconds(): Int {
        val diff = lockUntil - System.currentTimeMillis()
        return if (diff > 0) ((diff + 999) / 1000).toInt() else 0
    }

    private fun startLockCountdown() {
        tickerJob?.cancel()
        _lockRemainingSeconds.value = getRemainingSeconds()
        tickerJob = scope.launch {
            while (isActive && isLocked()) {
                _lockRemainingSeconds.value = getRemainingSeconds()
                delay(500)
            }
            _lockRemainingSeconds.value = 0
            lockUntil = 0
            loginFails = 0
            prefs.edit().remove(KEY_LOCK_UNTIL).remove(KEY_FAILS).apply()
        }
    }

    private fun hash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    sealed class AuthResult {
        object Success : AuthResult()
        data class Locked(val secondsLeft: Int) : AuthResult()
        data class WrongPassword(val triesLeft: Int) : AuthResult()
        data class Error(val message: String) : AuthResult()
    }

    fun unlockApp(password: String): AuthResult {
        if (isLocked()) {
            return AuthResult.Locked(getRemainingSeconds())
        }
        val trimmed = password.trim()
        if (trimmed.isEmpty()) {
            return AuthResult.Error("Enter the password.")
        }
        val calculatedHash = hash(trimmed)
        if (calculatedHash == APP_HASH) {
            loginFails = 0
            lockUntil = 0
            prefs.edit()
                .putBoolean(KEY_AUTHED, true)
                .remove(KEY_FAILS)
                .remove(KEY_LOCK_UNTIL)
                .apply()
            _isAuthed.value = true
            return AuthResult.Success
        } else {
            loginFails++
            if (loginFails >= MAX_TRIES) {
                lockUntil = System.currentTimeMillis() + LOCK_MS
                prefs.edit()
                    .putInt(KEY_FAILS, loginFails)
                    .putLong(KEY_LOCK_UNTIL, lockUntil)
                    .apply()
                startLockCountdown()
                return AuthResult.Locked(60)
            } else {
                prefs.edit().putInt(KEY_FAILS, loginFails).apply()
                return AuthResult.WrongPassword(MAX_TRIES - loginFails)
            }
        }
    }

    fun logout() {
        prefs.edit()
            .putBoolean(KEY_AUTHED, false)
            .putBoolean(KEY_ADMIN, false)
            .remove(KEY_CURRENT_USER_EMAIL)
            .remove(KEY_SUPABASE_ACCESS_TOKEN)
            .apply()
        _currentUserEmail.value = null
        _isAdmin.value = false
        _isAuthed.value = false
    }

    fun getLastSignedInEmail(): String =
        prefs.getString(KEY_LAST_EMAIL, "").orEmpty()

    fun clearPendingVerification() {
        prefs.edit().remove(KEY_PENDING_SIGNUP_EMAIL).apply()
        _pendingVerificationEmail.value = null
    }

    fun clearDeepLinkStatus() {
        _authDeepLinkMessage.value = null
        _authDeepLinkError.value = null
    }

    private fun setPendingVerificationEmail(email: String) {
        val clean = email.trim().lowercase()
        prefs.edit().putString(KEY_PENDING_SIGNUP_EMAIL, clean).apply()
        _pendingVerificationEmail.value = clean
    }

    private fun loadInitialUsers(): List<UserAccount> {
        val saved = prefs.getString(KEY_REGISTERED_USERS, null)
        if (!saved.isNullOrBlank()) {
            return parseUsersJson(saved)
        }
        return emptyList()
    }

    private fun parseUsersJson(rawJson: String): List<UserAccount> {
        return try {
            val jsonArr = JSONArray(rawJson)
            val list = mutableListOf<UserAccount>()
            for (i in 0 until jsonArr.length()) {
                val obj = jsonArr.optJSONObject(i) ?: continue
                val email = obj.optString("email").trim().lowercase()
                if (email.isBlank()) continue
                val isAdmin = if (obj.has("is_admin")) obj.optBoolean("is_admin") else obj.optBoolean("isAdmin", false)
                list.add(UserAccount(email, isAdmin))
            }
            list.distinctBy { it.email }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeUsers(list: List<UserAccount>): String {
        val jsonArr = JSONArray()
        for (u in list) {
            val obj = JSONObject().apply {
                put("email", u.email.trim().lowercase())
                put("isAdmin", u.isAdmin)
            }
            jsonArr.put(obj)
        }
        return jsonArr.toString()
    }

    private fun saveUsersLocally(list: List<UserAccount>, updatedAt: Long = System.currentTimeMillis()) {
        val cleanList = list
            .map { UserAccount(it.email.trim().lowercase(), it.isAdmin) }
            .filter { it.email.isNotBlank() }
            .distinctBy { it.email }
        val jsonStr = serializeUsers(cleanList)
        prefs.edit()
            .putString(KEY_REGISTERED_USERS, jsonStr)
            .putLong(KEY_ROLES_UPDATED_AT, updatedAt)
            .apply()
        _registeredUsers.value = cleanList

        val activeEmail = _currentUserEmail.value
        if (!activeEmail.isNullOrBlank()) {
            val matched = cleanList.find { it.email.equals(activeEmail, ignoreCase = true) }
            if (matched != null && matched.isAdmin != _isAdmin.value) {
                _isAdmin.value = matched.isAdmin
                prefs.edit().putBoolean(KEY_ADMIN, matched.isAdmin).apply()
            }
        }
    }

    fun getUserRolesJsonOrNull(): String? {
        val current = _registeredUsers.value
        if (current.isEmpty()) return null
        return serializeUsers(current)
    }

    fun applyRemoteUserRoles(remoteRolesJson: String?, remoteUpdatedAt: Long) {
        if (remoteRolesJson.isNullOrBlank()) return
        val localUpdatedAt = prefs.getLong(KEY_ROLES_UPDATED_AT, 0L)
        if (_registeredUsers.value.isNotEmpty() && localUpdatedAt > 0L && remoteUpdatedAt > 0L && remoteUpdatedAt < localUpdatedAt) {
            return
        }
        val parsed = parseUsersJson(remoteRolesJson)
        if (parsed.isNotEmpty()) {
            saveUsersLocally(parsed, if (remoteUpdatedAt > 0L) remoteUpdatedAt else System.currentTimeMillis())
        }
    }

    fun upsertUser(email: String, isAdmin: Boolean) {
        val clean = email.trim().lowercase()
        if (clean.isBlank()) return
        val current = _registeredUsers.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.email.equals(clean, ignoreCase = true) }
        if (existingIdx >= 0) {
            current[existingIdx] = UserAccount(clean, isAdmin)
        } else {
            current.add(UserAccount(clean, isAdmin))
        }
        val now = System.currentTimeMillis()
        saveUsersLocally(current, now)
        onRolesChangedListener?.invoke(serializeUsers(current), now)
        CoroutineScope(Dispatchers.IO).launch {
            pushRemoteUserUpsert(clean, isAdmin)
        }
    }

    fun removeUser(email: String) {
        val clean = email.trim().lowercase()
        val current = _registeredUsers.value.filter { !it.email.equals(clean, ignoreCase = true) }
        val now = System.currentTimeMillis()
        saveUsersLocally(current, now)
        onRolesChangedListener?.invoke(serializeUsers(current), now)
        CoroutineScope(Dispatchers.IO).launch {
            pushRemoteUserDelete(clean)
        }
    }

    fun setUserAdminRole(email: String, isAdmin: Boolean) {
        upsertUser(email, isAdmin)
    }

    private fun isSupabaseConfigured(): Boolean {
        val baseUrl = com.example.BuildConfig.SUPABASE_URL.trim().trim('"').trimEnd('/')
        val apiKey = com.example.BuildConfig.SUPABASE_KEY.trim().trim('"')
        return baseUrl.isNotBlank() &&
            apiKey.isNotBlank() &&
            !baseUrl.contains("YOUR_SUPABASE") &&
            !apiKey.contains("YOUR_SUPABASE")
    }

    private fun activeAuthHeader(): String {
        val token = prefs.getString(KEY_SUPABASE_ACCESS_TOKEN, null).orEmpty()
        val apiKey = com.example.BuildConfig.SUPABASE_KEY.trim().trim('"')
        return if (token.isNotBlank()) "Bearer $token" else "Bearer $apiKey"
    }

    private fun fetchRemoteAppUsersBlocking(overrideToken: String? = null): List<UserAccount>? {
        if (!isSupabaseConfigured()) return null
        return try {
            val baseUrl = com.example.BuildConfig.SUPABASE_URL.trim().trim('"').trimEnd('/')
            val apiKey = com.example.BuildConfig.SUPABASE_KEY.trim().trim('"')
            val authHeader = if (!overrideToken.isNullOrBlank()) "Bearer $overrideToken" else activeAuthHeader()
            val req = Request.Builder()
                .url("$baseUrl/rest/v1/app_users?select=email,is_admin&order=email.asc")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", authHeader)
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string().orEmpty()
                parseUsersJson(body)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun pushRemoteUserUpsert(email: String, isAdmin: Boolean, overrideToken: String? = null): Boolean {
        if (!isSupabaseConfigured()) return false
        return try {
            val baseUrl = com.example.BuildConfig.SUPABASE_URL.trim().trim('"').trimEnd('/')
            val apiKey = com.example.BuildConfig.SUPABASE_KEY.trim().trim('"')
            val authHeader = if (!overrideToken.isNullOrBlank()) "Bearer $overrideToken" else activeAuthHeader()
            val payload = JSONObject()
                .put("email", email.trim().lowercase())
                .put("is_admin", isAdmin)
                .toString()
            val req = Request.Builder()
                .url("$baseUrl/rest/v1/app_users?on_conflict=email")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            httpClient.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    private fun pushRemoteUserDelete(email: String): Boolean {
        if (!isSupabaseConfigured()) return false
        return try {
            val baseUrl = com.example.BuildConfig.SUPABASE_URL.trim().trim('"').trimEnd('/')
            val apiKey = com.example.BuildConfig.SUPABASE_KEY.trim().trim('"')
            val encodedEmail = URLEncoder.encode(email.trim().lowercase(), "UTF-8")
            val req = Request.Builder()
                .url("$baseUrl/rest/v1/app_users?email=eq.$encodedEmail")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", activeAuthHeader())
                .delete()
                .build()
            httpClient.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    fun syncUsersFromCloud(onComplete: ((Boolean) -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            val remoteUsers = fetchRemoteAppUsersBlocking()
            if (remoteUsers != null) {
                val activeEmail = _currentUserEmail.value
                withContext(Dispatchers.Main) {
                    saveUsersLocally(remoteUsers)
                    if (!activeEmail.isNullOrBlank()) {
                        val matched = remoteUsers.find { it.email.equals(activeEmail, ignoreCase = true) }
                        if (matched != null) {
                            _isAdmin.value = matched.isAdmin
                            prefs.edit().putBoolean(KEY_ADMIN, matched.isAdmin).apply()
                        }
                    }
                    onComplete?.invoke(true)
                }
            } else {
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    fun refreshCurrentUserRole(onComplete: ((Boolean) -> Unit)? = null) {
        val activeEmail = _currentUserEmail.value?.trim()?.lowercase()
        if (activeEmail.isNullOrBlank()) {
            onComplete?.invoke(false)
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            var metadataAdmin = false
            val token = prefs.getString(KEY_SUPABASE_ACCESS_TOKEN, null).orEmpty()
            if (isSupabaseConfigured() && token.isNotBlank()) {
                try {
                    val baseUrl = com.example.BuildConfig.SUPABASE_URL.trim().trim('"').trimEnd('/')
                    val apiKey = com.example.BuildConfig.SUPABASE_KEY.trim().trim('"')
                    val req = Request.Builder()
                        .url("$baseUrl/auth/v1/user")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", "Bearer $token")
                        .get()
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val obj = JSONObject(resp.body?.string().orEmpty())
                            metadataAdmin = isMetadataAdmin(obj)
                        }
                    }
                } catch (_: Exception) {
                }
            }

            val remoteUsers = fetchRemoteAppUsersBlocking()
            withContext(Dispatchers.Main) {
                if (remoteUsers != null) {
                    saveUsersLocally(remoteUsers)
                    val matched = remoteUsers.find { it.email.equals(activeEmail, ignoreCase = true) }
                    val resolvedAdmin = (matched?.isAdmin == true) || metadataAdmin
                    _isAdmin.value = resolvedAdmin
                    prefs.edit().putBoolean(KEY_ADMIN, resolvedAdmin).apply()
                    onComplete?.invoke(resolvedAdmin)
                } else {
                    val localMatched = _registeredUsers.value.find { it.email.equals(activeEmail, ignoreCase = true) }
                    val resolvedAdmin = (localMatched?.isAdmin == true) || metadataAdmin
                    _isAdmin.value = resolvedAdmin
                    prefs.edit().putBoolean(KEY_ADMIN, resolvedAdmin).apply()
                    onComplete?.invoke(resolvedAdmin)
                }
            }
        }
    }

    private suspend fun resolveAndCompleteSessionBlocking(
        email: String,
        accessToken: String = "",
        metadataIsAdmin: Boolean = false
    ) {
        val clean = email.trim().lowercase()
        val remoteUsers = fetchRemoteAppUsersBlocking(accessToken.takeIf { it.isNotBlank() })

        val resolvedAdmin: Boolean
        val updatedUsers: List<UserAccount>

        if (remoteUsers != null) {
            val remoteMatch = remoteUsers.find { it.email.equals(clean, ignoreCase = true) }
            resolvedAdmin = (remoteMatch?.isAdmin == true) || metadataIsAdmin
            if (remoteMatch == null) {
                pushRemoteUserUpsert(clean, resolvedAdmin, accessToken.takeIf { it.isNotBlank() })
                updatedUsers = (remoteUsers + UserAccount(clean, resolvedAdmin)).distinctBy { it.email }
            } else if (metadataIsAdmin && !remoteMatch.isAdmin) {
                pushRemoteUserUpsert(clean, true, accessToken.takeIf { it.isNotBlank() })
                updatedUsers = remoteUsers.map {
                    if (it.email.equals(clean, ignoreCase = true)) UserAccount(clean, true) else it
                }
            } else {
                updatedUsers = remoteUsers
            }
        } else {
            val localList = _registeredUsers.value
            val localMatch = localList.find { it.email.equals(clean, ignoreCase = true) }
            resolvedAdmin = (localMatch?.isAdmin == true) || metadataIsAdmin
            updatedUsers = if (localMatch == null) {
                (localList + UserAccount(clean, resolvedAdmin)).distinctBy { it.email }
            } else {
                localList
            }
        }

        withContext(Dispatchers.Main) {
            val now = System.currentTimeMillis()
            saveUsersLocally(updatedUsers, now)
            onRolesChangedListener?.invoke(serializeUsers(updatedUsers), now)

            prefs.edit()
                .putBoolean(KEY_AUTHED, true)
                .putBoolean(KEY_ADMIN, resolvedAdmin)
                .putString(KEY_CURRENT_USER_EMAIL, clean)
                .putString(KEY_LAST_EMAIL, clean)
                .apply {
                    if (accessToken.isNotBlank()) putString(KEY_SUPABASE_ACCESS_TOKEN, accessToken)
                }
                .remove(KEY_PENDING_SIGNUP_EMAIL)
                .apply()

            _pendingVerificationEmail.value = null
            _currentUserEmail.value = clean
            _isAdmin.value = resolvedAdmin
            _isAuthed.value = true
        }
    }

    fun signUpEmailPassword(
        email: String,
        password: String,
        callback: (Boolean, String?) -> Unit
    ) {
        val clean = email.trim().lowercase()
        if (!clean.contains("@") || !clean.contains(".")) {
            callback(false, "Enter a valid email address.")
            return
        }
        if (password.length < 6) {
            callback(false, "Password must be at least 6 characters.")
            return
        }
        clearDeepLinkStatus()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!isSupabaseConfigured()) {
                    resolveAndCompleteSessionBlocking(clean)
                    withContext(Dispatchers.Main) {
                        callback(true, "Account created & signed in.")
                    }
                    return@launch
                }

                val baseUrl = com.example.BuildConfig.SUPABASE_URL.trim().trim('"').trimEnd('/')
                val apiKey = com.example.BuildConfig.SUPABASE_KEY.trim().trim('"')
                val encodedRedirect = URLEncoder.encode(AUTH_REDIRECT_URL, "UTF-8")
                val json = JSONObject()
                    .put("email", clean)
                    .put("password", password)
                    .toString()

                val req = Request.Builder()
                    .url("$baseUrl/auth/v1/signup?redirect_to=$encodedRedirect")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    val respBody = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val obj = try { JSONObject(respBody) } catch (_: Exception) { null }
                        val accessToken = obj?.optString("access_token").orEmpty()
                        val userObj = obj?.optJSONObject("user")
                        val identitiesEmpty = userObj?.optJSONArray("identities")?.length() == 0

                        val hasConfirmationMetadata = userObj != null &&
                            (userObj.has("email_confirmed_at") || userObj.has("confirmed_at"))
                        val emailAlreadyConfirmed = !hasConfirmationMetadata || isEmailConfirmed(userObj)

                        when {
                            identitiesEmpty -> {
                                withContext(Dispatchers.Main) {
                                    callback(false, "An account with this email already exists. Use Sign In instead.")
                                }
                            }
                            accessToken.isNotBlank() && emailAlreadyConfirmed -> {
                                resolveAndCompleteSessionBlocking(
                                    email = clean,
                                    accessToken = accessToken,
                                    metadataIsAdmin = isMetadataAdmin(userObj)
                                )
                                withContext(Dispatchers.Main) {
                                    callback(true, "Account created & signed in!")
                                }
                            }
                            else -> {
                                val remoteUsers = fetchRemoteAppUsersBlocking()
                                val remoteMatch = remoteUsers?.find { it.email.equals(clean, ignoreCase = true) }
                                if (remoteUsers != null && remoteMatch == null) {
                                    pushRemoteUserUpsert(clean, false)
                                }
                                withContext(Dispatchers.Main) {
                                    if (remoteUsers != null) {
                                        val list = if (remoteMatch == null) remoteUsers + UserAccount(clean, false) else remoteUsers
                                        saveUsersLocally(list)
                                    } else if (_registeredUsers.value.none { it.email.equals(clean, ignoreCase = true) }) {
                                        saveUsersLocally(_registeredUsers.value + UserAccount(clean, false))
                                    }
                                    prefs.edit().putString(KEY_LAST_EMAIL, clean).apply()
                                    setPendingVerificationEmail(clean)
                                    callback(
                                        true,
                                        "Confirmation link sent to $clean. Tap the link in your email to verify and open the app."
                                    )
                                }
                            }
                        }
                    } else {
                        val msg = friendlySupabaseError(respBody, resp.code)
                        withContext(Dispatchers.Main) {
                            callback(false, msg)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(false, "Network error while creating account: ${e.message ?: "Check internet connection"}")
                }
            }
        }
    }

    fun resendConfirmationEmail(email: String, callback: (Boolean, String?) -> Unit) {
        val clean = email.trim().lowercase()
        if (!clean.contains("@")) {
            callback(false, "Enter a valid email address.")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!isSupabaseConfigured()) {
                    withContext(Dispatchers.Main) {
                        callback(true, "Confirmation link ready for $clean.")
                    }
                    return@launch
                }
                val baseUrl = com.example.BuildConfig.SUPABASE_URL.trim().trim('"').trimEnd('/')
                val apiKey = com.example.BuildConfig.SUPABASE_KEY.trim().trim('"')
                val encodedRedirect = URLEncoder.encode(AUTH_REDIRECT_URL, "UTF-8")
                val json = JSONObject()
                    .put("type", "signup")
                    .put("email", clean)
                    .toString()

                val req = Request.Builder()
                    .url("$baseUrl/auth/v1/resend?redirect_to=$encodedRedirect")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    val respBody = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        withContext(Dispatchers.Main) {
                            callback(true, "Confirmation link re-sent to $clean.")
                        }
                    } else {
                        val msg = extractSupabaseError(respBody, resp.code)
                        withContext(Dispatchers.Main) {
                            callback(false, msg)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(false, "Failed to resend email: ${e.message ?: "Network error"}")
                }
            }
        }
    }

    fun handleAuthDeepLink(uri: Uri) {
        if (!isAuthDeepLink(uri)) return
        clearDeepLinkStatus()

        val params = linkedMapOf<String, String>()
        params.putAll(parseEncodedParams(uri.encodedQuery))
        params.putAll(parseEncodedParams(uri.encodedFragment))

        val errDesc = params["error_description"]?.replace('+', ' ')
        val errCode = params["error"]
        if (!errDesc.isNullOrBlank() || !errCode.isNullOrBlank()) {
            _authDeepLinkError.value = errDesc ?: errCode ?: "Email verification link is invalid or expired."
            return
        }

        val accessToken = params["access_token"].orEmpty()
        val tokenHash = params["token_hash"].orEmpty()
        val type = params["type"].orEmpty().ifBlank { "signup" }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!isSupabaseConfigured()) {
                    val fallbackEmail = _pendingVerificationEmail.value ?: getLastSignedInEmail()
                    if (fallbackEmail.isNotBlank()) {
                        resolveAndCompleteSessionBlocking(fallbackEmail)
                        withContext(Dispatchers.Main) {
                            _authDeepLinkMessage.value = "Email confirmed!"
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            clearPendingVerification()
                            _authDeepLinkMessage.value = "Email verified! Please sign in with your email and password."
                        }
                    }
                    return@launch
                }

                val baseUrl = com.example.BuildConfig.SUPABASE_URL.trim().trim('"').trimEnd('/')
                val apiKey = com.example.BuildConfig.SUPABASE_KEY.trim().trim('"')

                when {
                    accessToken.isNotBlank() -> {
                        val req = Request.Builder()
                            .url("$baseUrl/auth/v1/user")
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", "Bearer $accessToken")
                            .get()
                            .build()
                        httpClient.newCall(req).execute().use { resp ->
                            val body = resp.body?.string().orEmpty()
                            if (resp.isSuccessful) {
                                val userObj = try { JSONObject(body) } catch (_: Exception) { null }
                                val confirmedEmail = userObj?.optString("email").orEmpty()
                                    .ifBlank { _pendingVerificationEmail.value.orEmpty() }

                                if (confirmedEmail.isNotBlank()) {
                                    resolveAndCompleteSessionBlocking(
                                        email = confirmedEmail,
                                        accessToken = accessToken,
                                        metadataIsAdmin = isMetadataAdmin(userObj)
                                    )
                                    withContext(Dispatchers.Main) {
                                        _authDeepLinkMessage.value = "Email verified! Welcome to HardwareStock."
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        clearPendingVerification()
                                        _authDeepLinkMessage.value = "Email verified! Sign in with your email and password."
                                    }
                                }
                            } else {
                                val msg = extractSupabaseError(body, resp.code)
                                withContext(Dispatchers.Main) {
                                    _authDeepLinkError.value = "Verification failed: $msg"
                                }
                            }
                        }
                    }
                    tokenHash.isNotBlank() -> {
                        val payload = JSONObject()
                            .put("token_hash", tokenHash)
                            .put("type", type)
                            .toString()
                        val req = Request.Builder()
                            .url("$baseUrl/auth/v1/verify")
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", "Bearer $apiKey")
                            .addHeader("Content-Type", "application/json")
                            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                            .build()
                        httpClient.newCall(req).execute().use { resp ->
                            val body = resp.body?.string().orEmpty()
                            if (resp.isSuccessful) {
                                val obj = try { JSONObject(body) } catch (_: Exception) { null }
                                val userObj = obj?.optJSONObject("user")
                                val verifiedToken = obj?.optString("access_token").orEmpty()
                                val confirmedEmail = (userObj?.optString("email") ?: obj?.optString("email")).orEmpty()
                                    .ifBlank { _pendingVerificationEmail.value.orEmpty() }

                                if (confirmedEmail.isNotBlank()) {
                                    resolveAndCompleteSessionBlocking(
                                        email = confirmedEmail,
                                        accessToken = verifiedToken,
                                        metadataIsAdmin = isMetadataAdmin(userObj)
                                    )
                                    withContext(Dispatchers.Main) {
                                        _authDeepLinkMessage.value = "Email verified! Welcome to HardwareStock."
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        clearPendingVerification()
                                        _authDeepLinkMessage.value = "Email verified! Sign in with your email and password."
                                    }
                                }
                            } else {
                                val msg = extractSupabaseError(body, resp.code)
                                withContext(Dispatchers.Main) {
                                    _authDeepLinkError.value = "Verification failed: $msg"
                                }
                            }
                        }
                    }
                    else -> {
                        withContext(Dispatchers.Main) {
                            clearPendingVerification()
                            _authDeepLinkMessage.value = "Email confirmed! Sign in with your email and password."
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _authDeepLinkError.value = "Could not finish verification: ${e.message ?: "Network error"}"
                }
            }
        }
    }

    fun signInEmailPassword(email: String, password: String, callback: (Boolean, String?) -> Unit) {
        val clean = email.trim().lowercase()
        if (clean.isBlank() || password.isBlank()) {
            callback(false, "Enter email and password.")
            return
        }
        clearDeepLinkStatus()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!isSupabaseConfigured()) {
                    resolveAndCompleteSessionBlocking(clean)
                    withContext(Dispatchers.Main) {
                        callback(true, null)
                    }
                    return@launch
                }

                val baseUrl = com.example.BuildConfig.SUPABASE_URL.trim().trim('"').trimEnd('/')
                val apiKey = com.example.BuildConfig.SUPABASE_KEY.trim().trim('"')
                val json = JSONObject()
                    .put("email", clean)
                    .put("password", password)
                    .toString()
                val req = Request.Builder()
                    .url("$baseUrl/auth/v1/token?grant_type=password")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    val respBody = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val obj = try { JSONObject(respBody) } catch (_: Exception) { null }
                        val accessToken = obj?.optString("access_token").orEmpty()
                        val userObj = obj?.optJSONObject("user")
                        val hasConfirmationMetadata = userObj != null &&
                            (userObj.has("email_confirmed_at") || userObj.has("confirmed_at"))
                        if (hasConfirmationMetadata && !isEmailConfirmed(userObj)) {
                            withContext(Dispatchers.Main) {
                                setPendingVerificationEmail(clean)
                                callback(false, "Your email is not verified yet. Open your inbox and tap the confirmation link first.")
                            }
                            return@use
                        }
                        if (accessToken.isBlank()) {
                            withContext(Dispatchers.Main) {
                                callback(false, "Sign in failed: missing session token.")
                            }
                            return@use
                        }
                        resolveAndCompleteSessionBlocking(
                            email = clean,
                            accessToken = accessToken,
                            metadataIsAdmin = isMetadataAdmin(userObj)
                        )
                        withContext(Dispatchers.Main) {
                            callback(true, null)
                        }
                    } else {
                        val msg = friendlySupabaseError(respBody, resp.code)
                        withContext(Dispatchers.Main) {
                            callback(false, msg)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(false, "Sign in failed: ${e.message ?: "Check internet connection"}")
                }
            }
        }
    }
}
