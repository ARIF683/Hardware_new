package com.example.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class UserAccount(val email: String, val isAdmin: Boolean)

class AuthManager(context: Context) {
    companion object {
        const val MAX_AUTH_TRIES = 5
        const val MIN_PASSWORD_LENGTH = 8
        const val OTP_EXPIRY_SECONDS = 300
        const val OTP_EXPIRY_MS = 300_000L
        const val RESEND_COOLDOWN_SECONDS = 60
        const val RESEND_COOLDOWN_MS = 60_000L
        private const val TAMPER_LOCKOUT_MS = 60_000L

        const val AUTH_REDIRECT_SCHEME = "hardwarestock"
        const val AUTH_REDIRECT_HOST = "auth-callback"
        const val AUTH_REDIRECT_URL = "$AUTH_REDIRECT_SCHEME://$AUTH_REDIRECT_HOST"

        private const val PREFS_NAME = "stock_manager_auth"
        private const val KEY_AUTHED = "is_authed"
        private const val KEY_ADMIN = "is_admin"
        private const val KEY_CURRENT_USER_EMAIL = "current_user_email"
        private const val KEY_FAILS = "login_fails"
        private const val KEY_LOCK_UNTIL = "lock_until"
        private const val KEY_LOCK_STRIKES = "lock_strikes"
        private const val KEY_OTP_EXPIRES_AT = "otp_expires_at"
        private const val KEY_RESEND_AVAILABLE_AT = "otp_resend_available_at"
        private const val KEY_SECURITY_STATE_ENC = "auth_security_state_enc"
        private const val KEY_REGISTERED_USERS = "registered_users"
        private const val KEY_ROLES_UPDATED_AT = "registered_users_updated_at"
        private const val KEY_PENDING_SIGNUP_EMAIL = "pending_signup_email"
        private const val KEY_LAST_EMAIL = "last_signed_in_email"

        private const val KEY_ENC_AUTHED = "is_authed_enc"
        private const val KEY_ENC_ADMIN = "is_admin_enc"
        private const val KEY_ENC_CURRENT_USER_EMAIL = "current_user_email_enc"
        private const val KEY_ENC_REGISTERED_USERS = "registered_users_enc"
        private const val KEY_ENC_ROLES_UPDATED_AT = "registered_users_updated_at_enc"
        private const val KEY_ENC_PENDING_SIGNUP_EMAIL = "pending_signup_email_enc"
        private const val KEY_ENC_LAST_EMAIL = "last_signed_in_email_enc"

        @androidx.annotation.VisibleForTesting
        internal fun seedTestSessionForTest(
            context: Context,
            email: String,
            isAdmin: Boolean = false
        ) {
            AuthManager(context).seedTestSession(email, isAdmin)
        }

        internal fun parseRetryAfterSeconds(headerValue: String?): Int? {
            val raw = headerValue?.trim() ?: return null
            return raw.toLongOrNull()?.coerceIn(1L, 3600L)?.toInt()
        }

        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        fun isValidEmail(email: String): Boolean {
            val clean = email.trim()
            return clean.length in 5..254 &&
                !clean.any { it.isWhitespace() || it.isISOControl() } &&
                EMAIL_REGEX.matches(clean)
        }

        fun isValidPassword(password: String): Boolean = password.length >= MIN_PASSWORD_LENGTH

        fun formatOtpCountdown(seconds: Int): String {
            val safe = seconds.coerceAtLeast(0)
            val mins = safe / 60
            val secs = safe % 60
            return "%02d:%02d".format(mins, secs)
        }

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
            return when {
                extracted.contains("email not confirmed", ignoreCase = true) ->
                    "Your email is not verified yet. Open your inbox and tap the confirmation link first."
                else -> extracted
            }
        }

        internal const val PROACTIVE_REFRESH_LEEWAY_MS = 5 * 60 * 1000L
        private val SESSION_REFRESH_LOCK = Any()

        internal fun isMetadataAdmin(userObj: JSONObject?): Boolean {
            if (userObj == null) return false
            val appMeta = userObj.optJSONObject("app_metadata")
            return appMeta?.optBoolean("is_admin", false) == true ||
                appMeta?.optString("role").equals("admin", ignoreCase = true)
        }

        internal fun parseJwtExpiryMillis(jwt: String): Long {
            val clean = jwt.trim()
            if (clean.isBlank()) return 0L
            return try {
                val parts = clean.split('.')
                if (parts.size < 2) return 0L
                val payloadB64 = parts[1]
                val decoded = android.util.Base64.decode(
                    payloadB64,
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
                )
                val json = JSONObject(String(decoded, Charsets.UTF_8))
                val expSec = json.optLong("exp", 0L)
                if (expSec > 0L) expSec * 1000L else 0L
            } catch (_: Throwable) {
                0L
            }
        }

        internal fun resolveExpiryMillis(
            obj: JSONObject?,
            accessToken: String,
            nowMs: Long = System.currentTimeMillis()
        ): Long {
            if (obj != null) {
                val expiresInSec = obj.optLong("expires_in", 0L)
                if (expiresInSec > 0L) {
                    return nowMs + expiresInSec * 1000L
                }
                val rawExpiresAt = obj.optLong("expires_at", 0L)
                if (rawExpiresAt > 0L) {
                    return if (rawExpiresAt < 10_000_000_000L) rawExpiresAt * 1000L else rawExpiresAt
                }
            }
            return parseJwtExpiryMillis(accessToken)
        }
    }

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val tokenStore = SecureTokenStore(prefs)

    private val initialSessionEmail: String? =
        tokenStore.readEncryptedString(KEY_ENC_CURRENT_USER_EMAIL)
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }

    private val hasStoredSessionToken: Boolean =
        tokenStore.readToken().isNotBlank() || tokenStore.readRefreshToken().isNotBlank()

    private val initialAuthed: Boolean =
        tokenStore.readEncryptedBoolean(KEY_ENC_AUTHED, false) &&
            hasStoredSessionToken &&
            initialSessionEmail != null

    private val _isAuthed = MutableStateFlow(initialAuthed)
    val isAuthed: StateFlow<Boolean> = _isAuthed.asStateFlow()

    private val _isAdmin = MutableStateFlow(
        if (initialAuthed) tokenStore.readEncryptedBoolean(KEY_ENC_ADMIN, false) else false
    )
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _currentUserEmail = MutableStateFlow(
        if (initialAuthed) initialSessionEmail else null
    )
    val currentUserEmail: StateFlow<String?> = _currentUserEmail.asStateFlow()

    private val _lockRemainingSeconds = MutableStateFlow(0)
    val lockRemainingSeconds: StateFlow<Int> = _lockRemainingSeconds.asStateFlow()

    private val _otpRemainingSeconds = MutableStateFlow(0)
    val otpRemainingSeconds: StateFlow<Int> = _otpRemainingSeconds.asStateFlow()

    private val _resendCooldownSeconds = MutableStateFlow(0)
    val resendCooldownSeconds: StateFlow<Int> = _resendCooldownSeconds.asStateFlow()

    private val _registeredUsers = MutableStateFlow<List<UserAccount>>(loadInitialUsers())
    val registeredUsers: StateFlow<List<UserAccount>> = _registeredUsers.asStateFlow()

    private val _pendingVerificationEmail = MutableStateFlow(
        tokenStore.readEncryptedString(KEY_ENC_PENDING_SIGNUP_EMAIL)?.takeIf { it.isNotBlank() }
    )
    val pendingVerificationEmail: StateFlow<String?> = _pendingVerificationEmail.asStateFlow()

    private val _authDeepLinkMessage = MutableStateFlow<String?>(null)
    val authDeepLinkMessage: StateFlow<String?> = _authDeepLinkMessage.asStateFlow()

    private val _authDeepLinkError = MutableStateFlow<String?>(null)
    val authDeepLinkError: StateFlow<String?> = _authDeepLinkError.asStateFlow()

    var onRolesChangedListener: ((String, Long) -> Unit)? = null
    var onSessionClearedListener: (() -> Unit)? = null

    @Volatile
    internal var lastCacheWipeJob: Job? = null

    @Volatile
    internal var requireConfiguredInTests: Boolean = false

    @Volatile
    internal var wallClockNowMs: () -> Long = { System.currentTimeMillis() }

    @Volatile
    internal var elapsedClockNowMs: () -> Long = { SystemClock.elapsedRealtime() }

    @Volatile
    internal var bootCountProvider: () -> Int = { readSystemBootCount() }

    private val securityStateLock = Any()
    internal val authNetworkMutex = Mutex()

    private var loginFails: Int = 0
    private var lockStrikes: Int = 0
    private var lockElapsedUntil: Long = 0L
    private var lockStartedElapsedMs: Long = 0L
    private var lockStartedWallMs: Long = 0L
    private var lockDurationMs: Long = 0L
    private var lockBootCount: Int = -1

    private var otpTimerActive: Boolean = false
    private var otpExpiresElapsedAt: Long = 0L
    private var otpStartedElapsedMs: Long = 0L
    private var otpStartedWallMs: Long = 0L
    private var otpDurationMs: Long = 0L
    private var otpBootCount: Int = -1

    private var resendCooldownActive: Boolean = false
    private var resendAvailableElapsedAt: Long = 0L
    private var resendStartedElapsedMs: Long = 0L
    private var resendStartedWallMs: Long = 0L
    private var resendDurationMs: Long = 0L
    private var resendBootCount: Int = -1

    private var tickerJob: Job? = null
    private var otpTickerJob: Job? = null
    private var resendTickerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    internal var httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    init {
        scrubLegacySessionPrefs()
        loadSecurityState()
        if (isLocked()) {
            startLockCountdown()
        }
        if (getOtpRemainingSecondsNow() > 0) {
            launchOtpCountdown()
        }
        if (getResendCooldownRemainingSeconds() > 0) {
            launchResendCooldownCountdown()
        }
    }

    private fun scrubLegacySessionPrefs() {
        val legacyKeys = listOf(
            KEY_AUTHED,
            KEY_ADMIN,
            KEY_CURRENT_USER_EMAIL,
            KEY_REGISTERED_USERS,
            KEY_ROLES_UPDATED_AT,
            KEY_PENDING_SIGNUP_EMAIL,
            KEY_LAST_EMAIL
        )
        if (legacyKeys.any { prefs.contains(it) }) {
            prefs.edit().apply {
                legacyKeys.forEach { remove(it) }
            }.apply()
        }
    }

    private fun readSystemBootCount(): Int {
        return try {
            Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT, -1)
        } catch (_: Throwable) {
            -1
        }
    }

    private fun strikeDurationMs(strikes: Int): Long = when (strikes) {
        1 -> 60_000L
        2 -> 180_000L
        else -> 300_000L
    }

    private fun isRebooted(startedElapsedMs: Long, savedBootCount: Int, elapsedNow: Long, currentBoot: Int): Boolean {
        return (currentBoot >= 0 && savedBootCount >= 0 && currentBoot != savedBootCount) ||
            elapsedNow < startedElapsedMs
    }

    private fun loadSecurityState() {
        synchronized(securityStateLock) {
            val elapsedNow = elapsedClockNowMs()
            val wallNow = wallClockNowMs()
            val currentBoot = bootCountProvider()

            val hasEncrypted = tokenStore.hasEncryptedValue(KEY_SECURITY_STATE_ENC)
            if (hasEncrypted) {
                val decoded = tokenStore.readEncryptedString(KEY_SECURITY_STATE_ENC)
                if (decoded == null) {
                    // Encrypted security blob exists on disk but decryption failed (tampered XML)
                    engageLockoutLocked(TAMPER_LOCKOUT_MS, strikes = 1)
                    scrubLegacySecurityPrefs()
                    return
                }
                val parsed = try { JSONObject(decoded) } catch (_: Exception) { null }
                if (parsed == null) {
                    engageLockoutLocked(TAMPER_LOCKOUT_MS, strikes = 1)
                    scrubLegacySecurityPrefs()
                    return
                }
                loginFails = parsed.optInt("fails", 0).coerceAtLeast(0)
                lockStrikes = parsed.optInt("strikes", 0).coerceIn(0, 5)
                lockElapsedUntil = parsed.optLong("lock_elapsed_until", 0L)
                lockStartedElapsedMs = parsed.optLong("lock_started_elapsed", 0L)
                lockStartedWallMs = parsed.optLong("lock_started_wall", 0L)
                lockDurationMs = parsed.optLong("lock_duration_ms", 0L)
                lockBootCount = parsed.optInt("lock_boot_count", -1)

                otpTimerActive = parsed.optBoolean("otp_active", false)
                otpExpiresElapsedAt = parsed.optLong("otp_expires_elapsed", 0L)
                otpStartedElapsedMs = parsed.optLong("otp_started_elapsed", 0L)
                otpStartedWallMs = parsed.optLong("otp_started_wall", 0L)
                otpDurationMs = parsed.optLong("otp_duration_ms", 0L)
                otpBootCount = parsed.optInt("otp_boot_count", -1)

                resendCooldownActive = parsed.optBoolean("resend_active", false)
                resendAvailableElapsedAt = parsed.optLong("resend_available_elapsed", 0L)
                resendStartedElapsedMs = parsed.optLong("resend_started_elapsed", 0L)
                resendStartedWallMs = parsed.optLong("resend_started_wall", 0L)
                resendDurationMs = parsed.optLong("resend_duration_ms", 0L)
                resendBootCount = parsed.optInt("resend_boot_count", -1)
                scrubLegacySecurityPrefs()
            } else {
                val hasLegacy = prefs.contains(KEY_LOCK_UNTIL) ||
                    prefs.contains(KEY_FAILS) ||
                    prefs.contains(KEY_LOCK_STRIKES) ||
                    prefs.contains(KEY_OTP_EXPIRES_AT) ||
                    prefs.contains(KEY_RESEND_AVAILABLE_AT)
                if (hasLegacy) {
                    loginFails = prefs.getInt(KEY_FAILS, 0).coerceAtLeast(0)
                    lockStrikes = prefs.getInt(KEY_LOCK_STRIKES, 0).coerceIn(0, 5)
                    val legacyLockUntil = prefs.getLong(KEY_LOCK_UNTIL, 0L)
                    if (legacyLockUntil > wallNow) {
                        val rem = legacyLockUntil - wallNow
                        lockStartedElapsedMs = elapsedNow
                        lockStartedWallMs = wallNow
                        lockDurationMs = rem
                        lockElapsedUntil = elapsedNow + rem
                        lockBootCount = currentBoot
                    }
                    val legacyOtpExpires = prefs.getLong(KEY_OTP_EXPIRES_AT, 0L)
                    if (legacyOtpExpires > 0L) {
                        val delta = legacyOtpExpires - wallNow
                        otpTimerActive = true
                        otpStartedElapsedMs = elapsedNow
                        otpStartedWallMs = wallNow
                        otpDurationMs = delta
                        otpExpiresElapsedAt = elapsedNow + delta
                        otpBootCount = currentBoot
                    }
                    val legacyResendAt = prefs.getLong(KEY_RESEND_AVAILABLE_AT, 0L)
                    if (legacyResendAt > wallNow) {
                        val rem = legacyResendAt - wallNow
                        resendCooldownActive = true
                        resendStartedElapsedMs = elapsedNow
                        resendStartedWallMs = wallNow
                        resendDurationMs = rem
                        resendAvailableElapsedAt = elapsedNow + rem
                        resendBootCount = currentBoot
                    }
                    persistSecurityStateLocked()
                }
            }

            // Re-evaluate lock expiration safely across reboots without mutating strikes
            if (lockDurationMs > 0L && getRemainingLockMsLocked() <= 0L) {
                clearActiveLockLocked()
            }
        }
    }

    private fun scrubLegacySecurityPrefs() {
        if (prefs.contains(KEY_FAILS) ||
            prefs.contains(KEY_LOCK_UNTIL) ||
            prefs.contains(KEY_LOCK_STRIKES) ||
            prefs.contains(KEY_OTP_EXPIRES_AT) ||
            prefs.contains(KEY_RESEND_AVAILABLE_AT)
        ) {
            prefs.edit()
                .remove(KEY_FAILS)
                .remove(KEY_LOCK_UNTIL)
                .remove(KEY_LOCK_STRIKES)
                .remove(KEY_OTP_EXPIRES_AT)
                .remove(KEY_RESEND_AVAILABLE_AT)
                .apply()
        }
    }

    private fun persistSecurityStateLocked() {
        val json = JSONObject().apply {
            put("fails", loginFails)
            put("strikes", lockStrikes)
            put("lock_elapsed_until", lockElapsedUntil)
            put("lock_started_elapsed", lockStartedElapsedMs)
            put("lock_started_wall", lockStartedWallMs)
            put("lock_duration_ms", lockDurationMs)
            put("lock_boot_count", lockBootCount)
            put("otp_active", otpTimerActive)
            put("otp_expires_elapsed", otpExpiresElapsedAt)
            put("otp_started_elapsed", otpStartedElapsedMs)
            put("otp_started_wall", otpStartedWallMs)
            put("otp_duration_ms", otpDurationMs)
            put("otp_boot_count", otpBootCount)
            put("resend_active", resendCooldownActive)
            put("resend_available_elapsed", resendAvailableElapsedAt)
            put("resend_started_elapsed", resendStartedElapsedMs)
            put("resend_started_wall", resendStartedWallMs)
            put("resend_duration_ms", resendDurationMs)
            put("resend_boot_count", resendBootCount)
        }
        tokenStore.saveEncryptedString(KEY_SECURITY_STATE_ENC, json.toString())
        scrubLegacySecurityPrefs()
    }

    private fun engageLockoutLocked(durationMs: Long, strikes: Int) {
        val elapsedNow = elapsedClockNowMs()
        val wallNow = wallClockNowMs()
        val currentBoot = bootCountProvider()
        val currentRemaining = if (lockDurationMs > 0L) getRemainingLockMsLocked() else 0L
        val effectiveDuration = maxOf(durationMs, currentRemaining)

        loginFails = 0
        lockStrikes = strikes.coerceIn(1, 5)
        lockStartedElapsedMs = elapsedNow
        lockStartedWallMs = wallNow
        lockDurationMs = effectiveDuration
        lockElapsedUntil = elapsedNow + effectiveDuration
        lockBootCount = currentBoot
        persistSecurityStateLocked()
    }

    private fun clearActiveLockLocked() {
        lockElapsedUntil = 0L
        lockStartedElapsedMs = 0L
        lockStartedWallMs = 0L
        lockDurationMs = 0L
        lockBootCount = -1
        loginFails = 0
        persistSecurityStateLocked()
    }

    private fun getRemainingLockMsLocked(): Long {
        if (lockDurationMs <= 0L) return 0L
        val elapsedNow = elapsedClockNowMs()
        val currentBoot = bootCountProvider()
        if (isRebooted(lockStartedElapsedMs, lockBootCount, elapsedNow, currentBoot)) {
            val wallNow = wallClockNowMs()
            val wallElapsed = wallNow - lockStartedWallMs
            val effectiveElapsed = if (wallElapsed >= 0L) maxOf(wallElapsed, elapsedNow) else elapsedNow
            val remaining = lockDurationMs - effectiveElapsed
            return if (remaining > 0L) {
                lockStartedElapsedMs = elapsedNow
                lockStartedWallMs = wallNow
                lockDurationMs = remaining
                lockElapsedUntil = elapsedNow + remaining
                lockBootCount = currentBoot
                persistSecurityStateLocked()
                remaining
            } else {
                0L
            }
        }
        val remaining = lockElapsedUntil - elapsedNow
        return if (remaining > 0L) remaining else 0L
    }

    private fun getRemainingLockMs(): Long {
        synchronized(securityStateLock) {
            return getRemainingLockMsLocked()
        }
    }

    fun isLocked(): Boolean = getRemainingLockMs() > 0L

    fun getRemainingSeconds(): Int {
        val diff = getRemainingLockMs()
        return if (diff > 0L) ((diff + 999L) / 1000L).toInt() else 0
    }

    private fun getOtpRemainingMsLocked(): Long {
        if (!otpTimerActive) return 0L
        val elapsedNow = elapsedClockNowMs()
        val currentBoot = bootCountProvider()
        if (isRebooted(otpStartedElapsedMs, otpBootCount, elapsedNow, currentBoot)) {
            if (otpDurationMs <= 0L) return 0L
            val wallNow = wallClockNowMs()
            val wallElapsed = wallNow - otpStartedWallMs
            val effectiveElapsed = if (wallElapsed >= 0L) maxOf(wallElapsed, elapsedNow) else otpDurationMs
            val remaining = otpDurationMs - effectiveElapsed
            return if (remaining > 0L) {
                otpStartedElapsedMs = elapsedNow
                otpStartedWallMs = wallNow
                otpDurationMs = remaining
                otpExpiresElapsedAt = elapsedNow + remaining
                otpBootCount = currentBoot
                persistSecurityStateLocked()
                remaining
            } else {
                0L
            }
        }
        val diff = otpExpiresElapsedAt - elapsedNow
        return if (diff > 0L) diff else 0L
    }

    private fun getOtpRemainingSecondsNow(): Int {
        val diff = synchronized(securityStateLock) { getOtpRemainingMsLocked() }
        return if (diff > 0L) ((diff + 999L) / 1000L).toInt() else 0
    }

    internal fun isCurrentOtpExpired(): Boolean {
        synchronized(securityStateLock) {
            if (!otpTimerActive) return false
            return getOtpRemainingMsLocked() <= 0L
        }
    }

    internal fun startOtpExpiryTimer(durationMs: Long = OTP_EXPIRY_MS) {
        synchronized(securityStateLock) {
            val elapsedNow = elapsedClockNowMs()
            val wallNow = wallClockNowMs()
            otpTimerActive = true
            otpStartedElapsedMs = elapsedNow
            otpStartedWallMs = wallNow
            otpDurationMs = durationMs
            otpExpiresElapsedAt = elapsedNow + durationMs
            otpBootCount = bootCountProvider()
            persistSecurityStateLocked()
        }
        launchOtpCountdown()
    }

    internal fun clearOtpTimer() {
        otpTickerJob?.cancel()
        synchronized(securityStateLock) {
            otpTimerActive = false
            otpExpiresElapsedAt = 0L
            otpStartedElapsedMs = 0L
            otpStartedWallMs = 0L
            otpDurationMs = 0L
            otpBootCount = -1
            persistSecurityStateLocked()
        }
        _otpRemainingSeconds.value = 0
    }

    internal fun setOtpExpiresAtForTest(expiresAtMs: Long) {
        synchronized(securityStateLock) {
            val wallNow = wallClockNowMs()
            val elapsedNow = elapsedClockNowMs()
            val delta = expiresAtMs - wallNow
            otpTimerActive = true
            otpStartedElapsedMs = elapsedNow
            otpStartedWallMs = wallNow
            otpDurationMs = delta
            otpExpiresElapsedAt = elapsedNow + delta
            otpBootCount = bootCountProvider()
            persistSecurityStateLocked()
        }
        _otpRemainingSeconds.value = getOtpRemainingSecondsNow()
    }

    private fun launchOtpCountdown() {
        otpTickerJob?.cancel()
        _otpRemainingSeconds.value = getOtpRemainingSecondsNow()
        otpTickerJob = scope.launch {
            while (isActive && getOtpRemainingSecondsNow() > 0) {
                _otpRemainingSeconds.value = getOtpRemainingSecondsNow()
                delay(500)
            }
            _otpRemainingSeconds.value = 0
        }
    }

    private fun getResendRemainingMsLocked(): Long {
        if (!resendCooldownActive) return 0L
        val elapsedNow = elapsedClockNowMs()
        val currentBoot = bootCountProvider()
        if (isRebooted(resendStartedElapsedMs, resendBootCount, elapsedNow, currentBoot)) {
            val wallNow = wallClockNowMs()
            val wallElapsed = wallNow - resendStartedWallMs
            val effectiveElapsed = if (wallElapsed >= 0L) maxOf(wallElapsed, elapsedNow) else elapsedNow
            val remaining = resendDurationMs - effectiveElapsed
            return if (remaining > 0L) {
                resendStartedElapsedMs = elapsedNow
                resendStartedWallMs = wallNow
                resendDurationMs = remaining
                resendAvailableElapsedAt = elapsedNow + remaining
                resendBootCount = currentBoot
                persistSecurityStateLocked()
                remaining
            } else {
                0L
            }
        }
        val diff = resendAvailableElapsedAt - elapsedNow
        return if (diff > 0L) diff else 0L
    }

    fun getResendCooldownRemainingSeconds(): Int {
        val diff = synchronized(securityStateLock) { getResendRemainingMsLocked() }
        return if (diff > 0L) ((diff + 999L) / 1000L).toInt() else 0
    }

    internal fun startResendCooldown(durationMs: Long = RESEND_COOLDOWN_MS) {
        synchronized(securityStateLock) {
            val elapsedNow = elapsedClockNowMs()
            val wallNow = wallClockNowMs()
            resendCooldownActive = durationMs > 0L
            resendStartedElapsedMs = elapsedNow
            resendStartedWallMs = wallNow
            resendDurationMs = durationMs.coerceAtLeast(0L)
            resendAvailableElapsedAt = elapsedNow + durationMs.coerceAtLeast(0L)
            resendBootCount = bootCountProvider()
            persistSecurityStateLocked()
        }
        launchResendCooldownCountdown()
    }

    internal fun clearResendCooldown() {
        resendTickerJob?.cancel()
        synchronized(securityStateLock) {
            resendCooldownActive = false
            resendAvailableElapsedAt = 0L
            resendStartedElapsedMs = 0L
            resendStartedWallMs = 0L
            resendDurationMs = 0L
            resendBootCount = -1
            persistSecurityStateLocked()
        }
        _resendCooldownSeconds.value = 0
    }

    internal fun setResendCooldownForTest(availableAtMs: Long) {
        synchronized(securityStateLock) {
            val wallNow = wallClockNowMs()
            val elapsedNow = elapsedClockNowMs()
            val delta = (availableAtMs - wallNow).coerceAtLeast(0L)
            resendCooldownActive = delta > 0L
            resendStartedElapsedMs = elapsedNow
            resendStartedWallMs = wallNow
            resendDurationMs = delta
            resendAvailableElapsedAt = elapsedNow + delta
            resendBootCount = bootCountProvider()
            persistSecurityStateLocked()
        }
        _resendCooldownSeconds.value = getResendCooldownRemainingSeconds()
    }

    private fun launchResendCooldownCountdown() {
        resendTickerJob?.cancel()
        _resendCooldownSeconds.value = getResendCooldownRemainingSeconds()
        resendTickerJob = scope.launch {
            while (isActive && getResendCooldownRemainingSeconds() > 0) {
                _resendCooldownSeconds.value = getResendCooldownRemainingSeconds()
                delay(500)
            }
            _resendCooldownSeconds.value = 0
            synchronized(securityStateLock) {
                if (getResendRemainingMsLocked() <= 0L) {
                    resendCooldownActive = false
                    resendAvailableElapsedAt = 0L
                    resendStartedElapsedMs = 0L
                    resendStartedWallMs = 0L
                    resendDurationMs = 0L
                    resendBootCount = -1
                    persistSecurityStateLocked()
                }
            }
        }
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
            synchronized(securityStateLock) {
                if (getRemainingLockMsLocked() <= 0L) {
                    clearActiveLockLocked()
                }
            }
        }
    }

    internal suspend fun recordFailedAuthAttempt(
        httpStatusCode: Int = 0,
        retryAfterSeconds: Int? = null
    ): String? {
        return withContext(Dispatchers.Main.immediate) {
            var shouldStartCountdown = false
            val message: String
            synchronized(securityStateLock) {
                if (httpStatusCode == 429) {
                    val strikes = (lockStrikes + 1).coerceAtMost(5)
                    val baseDurationMs = strikeDurationMs(strikes)
                    val serverDurationMs = (retryAfterSeconds ?: 0).coerceIn(0, 3600) * 1000L
                    val effectiveDurationMs = maxOf(baseDurationMs, serverDurationMs)
                    engageLockoutLocked(effectiveDurationMs, strikes)
                    shouldStartCountdown = true
                    val secs = ((effectiveDurationMs + 999L) / 1000L).toInt()
                    message = "Too many failed attempts. Locked for ${secs}s."
                } else {
                    loginFails += 1
                    if (loginFails >= MAX_AUTH_TRIES) {
                        val strikes = (lockStrikes + 1).coerceAtMost(5)
                        val durationMs = strikeDurationMs(strikes)
                        engageLockoutLocked(durationMs, strikes)
                        shouldStartCountdown = true
                        val secs = ((durationMs + 999L) / 1000L).toInt()
                        message = "Too many failed attempts. Locked for ${secs}s."
                    } else {
                        persistSecurityStateLocked()
                        val left = MAX_AUTH_TRIES - loginFails
                        val word = if (left == 1) "attempt" else "attempts"
                        message = "$left $word left before temporary lockout."
                    }
                }
            }
            if (shouldStartCountdown) {
                startLockCountdown()
            }
            message
        }
    }

    internal suspend fun resetAuthFailures() {
        withContext(Dispatchers.Main.immediate) {
            tickerJob?.cancel()
            synchronized(securityStateLock) {
                loginFails = 0
                lockStrikes = 0
                clearActiveLockLocked()
            }
            _lockRemainingSeconds.value = 0
        }
    }

    private fun clearSensitivePrefsSync() {
        com.example.data.pref.BankingPreferenceManager(appContext).clearAllLocalState()
        listOf("gemini_prefs", "store_profile_prefs", "tint_machine_prefs").forEach { name ->
            appContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
        }
    }

    @androidx.annotation.VisibleForTesting
    internal var supabaseConfigOverrideForTests: Pair<String, String>? = null

    @androidx.annotation.VisibleForTesting
    internal fun seedTestSession(
        email: String,
        isAdmin: Boolean = false,
        accessToken: String = "robolectric-test-jwt",
        refreshToken: String = "robolectric-test-refresh",
        expiresAtMillis: Long? = null
    ) {
        val clean = email.trim().lowercase()
        val resolvedExp = (expiresAtMillis ?: parseJwtExpiryMillis(accessToken)).takeIf { it > 0L }
        tokenStore.saveSessionTokens(accessToken, refreshToken, resolvedExp)
        tokenStore.saveEncryptedBoolean(KEY_ENC_AUTHED, true)
        tokenStore.saveEncryptedBoolean(KEY_ENC_ADMIN, isAdmin)
        tokenStore.saveEncryptedString(KEY_ENC_CURRENT_USER_EMAIL, clean)
        tokenStore.saveEncryptedString(KEY_ENC_LAST_EMAIL, clean)
        scrubLegacySessionPrefs()
        _currentUserEmail.value = clean
        _isAdmin.value = isAdmin
        _isAuthed.value = true
    }

    fun logout(revokeServer: Boolean = true) {
        val tokenToRevoke = activeAccessToken()
        clearOtpTimer()
        clearResendCooldown()
        tokenStore.clearToken()
        tokenStore.saveEncryptedBoolean(KEY_ENC_AUTHED, false)
        tokenStore.saveEncryptedBoolean(KEY_ENC_ADMIN, false)
        tokenStore.removeEncryptedKey(KEY_ENC_CURRENT_USER_EMAIL)
        scrubLegacySessionPrefs()
        _currentUserEmail.value = null
        _isAdmin.value = false
        _isAuthed.value = false

        clearSensitivePrefsSync()
        onSessionClearedListener?.invoke()
        lastCacheWipeJob = CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                com.example.data.local.AppDatabase.getDatabase(appContext).clearAllTables()
            }
        }

        if (revokeServer && isSupabaseConfigured() && tokenToRevoke.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val baseUrl = com.example.BuildConfig.SUPABASE_URL.trim().trim('"').trimEnd('/')
                    val apiKey = com.example.BuildConfig.SUPABASE_KEY.trim().trim('"')
                    val req = Request.Builder()
                        .url("$baseUrl/auth/v1/logout")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", "Bearer $tokenToRevoke")
                        .addHeader("Content-Type", "application/json")
                        .post("{}".toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()
                    httpClient.newCall(req).execute().close()
                } catch (e: Exception) {
                    android.util.Log.w("AuthManager", "Server logout revocation failed", e)
                }
            }
        }
    }

    internal fun forceSessionExpiredSignOut(
        reason: String = "Your session has expired. Please sign in again."
    ) {
        scope.launch {
            logout(revokeServer = false)
            _authDeepLinkMessage.value = null
            _authDeepLinkError.value = reason
        }
    }

    fun getLastSignedInEmail(): String =
        tokenStore.readEncryptedString(KEY_ENC_LAST_EMAIL).orEmpty()

    fun clearPendingVerification() {
        clearOtpTimer()
        tokenStore.removeEncryptedKey(KEY_ENC_PENDING_SIGNUP_EMAIL)
        prefs.edit().remove(KEY_PENDING_SIGNUP_EMAIL).apply()
        _pendingVerificationEmail.value = null
    }

    fun clearDeepLinkStatus() {
        _authDeepLinkMessage.value = null
        _authDeepLinkError.value = null
    }

    @androidx.annotation.VisibleForTesting
    internal fun setPendingVerificationEmail(email: String) {
        val clean = email.trim().lowercase()
        tokenStore.saveEncryptedString(KEY_ENC_PENDING_SIGNUP_EMAIL, clean)
        prefs.edit().remove(KEY_PENDING_SIGNUP_EMAIL).apply()
        _pendingVerificationEmail.value = clean
    }

    private fun loadInitialUsers(): List<UserAccount> {
        val saved = tokenStore.readEncryptedString(KEY_ENC_REGISTERED_USERS)
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
        tokenStore.saveEncryptedString(KEY_ENC_REGISTERED_USERS, jsonStr)
        tokenStore.saveEncryptedString(KEY_ENC_ROLES_UPDATED_AT, updatedAt.toString())
        scrubLegacySessionPrefs()
        _registeredUsers.value = cleanList

        val activeEmail = _currentUserEmail.value
        if (!activeEmail.isNullOrBlank()) {
            val matched = cleanList.find { it.email.equals(activeEmail, ignoreCase = true) }
            if (matched != null && matched.isAdmin != _isAdmin.value) {
                _isAdmin.value = matched.isAdmin
                tokenStore.saveEncryptedBoolean(KEY_ENC_ADMIN, matched.isAdmin)
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
        val localUpdatedAt = tokenStore.readEncryptedString(KEY_ENC_ROLES_UPDATED_AT)?.toLongOrNull() ?: 0L
        if (_registeredUsers.value.isNotEmpty() && localUpdatedAt > 0L && remoteUpdatedAt > 0L && remoteUpdatedAt < localUpdatedAt) {
            return
        }
        val parsed = parseUsersJson(remoteRolesJson)
        if (parsed.isNotEmpty()) {
            saveUsersLocally(parsed, if (remoteUpdatedAt > 0L) remoteUpdatedAt else System.currentTimeMillis())
        }
    }

    fun upsertUser(email: String, isAdmin: Boolean) {
        if (!_isAdmin.value) return
        val clean = email.trim().lowercase()
        if (!isValidEmail(clean)) return
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
        if (!_isAdmin.value) return
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
        if (!_isAdmin.value) return
        upsertUser(email, isAdmin)
    }

    private fun resolvedSupabaseBaseUrl(): String =
        (supabaseConfigOverrideForTests?.first ?: com.example.BuildConfig.SUPABASE_URL)
            .trim().trim('"').trimEnd('/')

    private fun resolvedSupabaseApiKey(): String =
        (supabaseConfigOverrideForTests?.second ?: com.example.BuildConfig.SUPABASE_KEY)
            .trim().trim('"')

    internal fun isSupabaseConfigured(): Boolean {
        val baseUrl = resolvedSupabaseBaseUrl()
        val apiKey = resolvedSupabaseApiKey()
        val isHttpsOrLocal = baseUrl.startsWith("https://", ignoreCase = true) ||
            baseUrl.startsWith("http://localhost", ignoreCase = true) ||
            baseUrl.startsWith("http://127.0.0.1", ignoreCase = true)
        return isHttpsOrLocal &&
            apiKey.isNotBlank() &&
            !baseUrl.contains("YOUR_SUPABASE") &&
            !apiKey.contains("YOUR_SUPABASE")
    }

    internal fun allowOfflineTestFallback(): Boolean {
        val isRobolectric = android.os.Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
        return isRobolectric && !requireConfiguredInTests
    }

    internal fun unconfiguredAuthMessage(): String =
        "Cloud authentication is not configured. Add SUPABASE_URL and SUPABASE_KEY in AI Studio Secrets."

    internal fun activeAccessToken(): String = tokenStore.readToken()

    internal fun activeRefreshToken(): String = tokenStore.readRefreshToken()

    internal fun activeTokenExpiresAtMs(): Long = tokenStore.readTokenExpiresAtMs()

    /**
     * Proactively refreshes the access token before expiry so foreground/resume requests
     * share one clean token instead of triggering parallel 401 refresh races.
     */
    internal fun ensureValidSessionBlocking(leewayMs: Long = PROACTIVE_REFRESH_LEEWAY_MS): Boolean {
        val currentToken = activeAccessToken()
        val currentRefresh = activeRefreshToken()
        if (currentToken.isBlank() && currentRefresh.isBlank()) return false
        if (!isSupabaseConfigured()) return currentToken.isNotBlank() || _isAuthed.value

        synchronized(SESSION_REFRESH_LOCK) {
            val token = activeAccessToken()
            val refresh = activeRefreshToken()
            if (token.isBlank() && refresh.isBlank()) return false

            var expiresAt = tokenStore.readTokenExpiresAtMs()
            if (expiresAt <= 0L && token.isNotBlank()) {
                val parsed = parseJwtExpiryMillis(token)
                if (parsed > 0L) {
                    tokenStore.saveTokenExpiresAtMs(parsed)
                    expiresAt = parsed
                }
            }
            if (token.isNotBlank() && (expiresAt <= 0L || wallClockNowMs() + leewayMs < expiresAt)) {
                return true
            }
            return refreshSessionBlocking(failedAccessToken = token.takeIf { it.isNotBlank() })
        }
    }

    internal fun refreshSessionBlocking(failedAccessToken: String? = null): Boolean {
        if (!isSupabaseConfigured()) return false
        synchronized(SESSION_REFRESH_LOCK) {
            val currentAccess = activeAccessToken()
            val currentRefresh = activeRefreshToken()

            // Deduplicate concurrent 401 retries: if another coroutine/worker already rotated
            // failedAccessToken -> currentAccess while we waited on SESSION_REFRESH_LOCK, reuse it!
            val cleanFailed = failedAccessToken?.trim().orEmpty()
            if (cleanFailed.isNotBlank() && currentAccess.isNotBlank() && currentAccess != cleanFailed) {
                return true
            }

            // If called proactively (failedAccessToken == null) and token is still fresh, skip network call.
            if (cleanFailed.isBlank() && currentAccess.isNotBlank()) {
                val storedExpiry = tokenStore.readTokenExpiresAtMs()
                if (storedExpiry > 0L && wallClockNowMs() + PROACTIVE_REFRESH_LEEWAY_MS < storedExpiry) {
                    return true
                }
            }

            if (currentRefresh.isBlank()) return false
            return try {
                val baseUrl = resolvedSupabaseBaseUrl()
                val apiKey = resolvedSupabaseApiKey()
                val payload = JSONObject().put("refresh_token", currentRefresh).toString()
                val req = Request.Builder()
                    .url("$baseUrl/auth/v1/token?grant_type=refresh_token")
                    .addHeader("apikey", apiKey)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val obj = try { JSONObject(body) } catch (_: Exception) { null }
                        val newAccess = obj?.optString("access_token").orEmpty()
                        val newRefresh = obj?.optString("refresh_token").orEmpty().ifBlank { currentRefresh }
                        if (newAccess.isNotBlank()) {
                            val newExpiry = resolveExpiryMillis(obj, newAccess, wallClockNowMs())
                            tokenStore.saveSessionTokens(newAccess, newRefresh, newExpiry.takeIf { it > 0L })
                            val userObj = obj?.optJSONObject("user")
                            if (userObj != null && isMetadataAdmin(userObj) && !_isAdmin.value) {
                                scope.launch {
                                    _isAdmin.value = true
                                    tokenStore.saveEncryptedBoolean(KEY_ENC_ADMIN, true)
                                }
                            }
                            true
                        } else {
                            false
                        }
                    } else if (resp.code == 400 || resp.code == 401 || resp.code == 403) {
                        val latestAccess = activeAccessToken()
                        if (latestAccess.isNotBlank() && latestAccess != currentAccess) {
                            return true
                        }
                        forceSessionExpiredSignOut("Your session has expired. Please sign in again.")
                        false
                    } else {
                        false
                    }
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    internal fun saveLastSignedInEmail(email: String) {
        tokenStore.saveEncryptedString(KEY_ENC_LAST_EMAIL, email.trim().lowercase())
        prefs.edit().remove(KEY_LAST_EMAIL).apply()
    }

    internal suspend fun completeEmailChangeLocally(
        oldEmail: String?,
        newEmail: String,
        newAccessToken: String,
        newRefreshToken: String = "",
        expiresAtMillis: Long? = null
    ) {
        val cleanNew = newEmail.trim().lowercase()
        val cleanOld = oldEmail?.trim()?.lowercase()
        val existingRole = _registeredUsers.value
            .find { cleanOld != null && it.email.equals(cleanOld, ignoreCase = true) }
            ?.isAdmin ?: _isAdmin.value

        withContext(Dispatchers.Main) {
            val updatedList = _registeredUsers.value
                .filter { cleanOld == null || !it.email.equals(cleanOld, ignoreCase = true) }
                .filter { !it.email.equals(cleanNew, ignoreCase = true) } +
                UserAccount(cleanNew, existingRole)
            val now = System.currentTimeMillis()
            saveUsersLocally(updatedList, now)
            onRolesChangedListener?.invoke(serializeUsers(updatedList), now)
            if (newAccessToken.isNotBlank()) {
                val resolvedExp = (expiresAtMillis ?: parseJwtExpiryMillis(newAccessToken)).takeIf { it > 0L }
                tokenStore.saveSessionTokens(newAccessToken, newRefreshToken, resolvedExp)
            }
            tokenStore.saveEncryptedString(KEY_ENC_CURRENT_USER_EMAIL, cleanNew)
            tokenStore.saveEncryptedString(KEY_ENC_LAST_EMAIL, cleanNew)
            scrubLegacySessionPrefs()
            _currentUserEmail.value = cleanNew
            _isAdmin.value = existingRole
            clearOtpTimer()
            clearResendCooldown()
        }

        if (isSupabaseConfigured()) {
            val tok = newAccessToken.takeIf { it.isNotBlank() } ?: activeAccessToken().takeIf { it.isNotBlank() }
            pushRemoteUserUpsert(cleanNew, existingRole, tok)
            if (!cleanOld.isNullOrBlank() && !cleanOld.equals(cleanNew, ignoreCase = true)) {
                pushRemoteUserDelete(cleanOld)
            }
        }
    }

    internal suspend fun completeAccountDeletionLocally(deletedEmail: String) {
        val clean = deletedEmail.trim().lowercase()
        withContext(Dispatchers.Main) {
            val remaining = _registeredUsers.value.filter { !it.email.equals(clean, ignoreCase = true) }
            val now = System.currentTimeMillis()
            saveUsersLocally(remaining, now)
            onRolesChangedListener?.invoke(serializeUsers(remaining), now)
            tokenStore.removeEncryptedKey(KEY_ENC_LAST_EMAIL)
            tokenStore.removeEncryptedKey(KEY_ENC_PENDING_SIGNUP_EMAIL)
            scrubLegacySessionPrefs()
            _pendingVerificationEmail.value = null
            logout(revokeServer = false)
            _authDeepLinkError.value = null
            _authDeepLinkMessage.value = if (clean.isNotBlank()) {
                "Account ($clean) has been permanently deleted."
            } else {
                "Your account has been permanently deleted."
            }
        }
    }

    private fun requireActiveTokenHeader(overrideToken: String? = null): String? {
        val token = overrideToken?.trim()?.takeIf { it.isNotBlank() }
            ?: activeAccessToken().trim().takeIf { it.isNotBlank() }
            ?: return null
        return "Bearer $token"
    }

    private fun fetchRemoteAppUsersBlocking(overrideToken: String? = null): List<UserAccount>? {
        if (!isSupabaseConfigured()) return null
        val authHeader = requireActiveTokenHeader(overrideToken) ?: return null
        return try {
            val baseUrl = com.example.BuildConfig.SUPABASE_URL.trim().trim('"').trimEnd('/')
            val apiKey = com.example.BuildConfig.SUPABASE_KEY.trim().trim('"')
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
        val authHeader = requireActiveTokenHeader(overrideToken) ?: return false
        return try {
            val baseUrl = com.example.BuildConfig.SUPABASE_URL.trim().trim('"').trimEnd('/')
            val apiKey = com.example.BuildConfig.SUPABASE_KEY.trim().trim('"')
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
        val authHeader = requireActiveTokenHeader() ?: return false
        return try {
            val baseUrl = com.example.BuildConfig.SUPABASE_URL.trim().trim('"').trimEnd('/')
            val apiKey = com.example.BuildConfig.SUPABASE_KEY.trim().trim('"')
            val encodedEmail = URLEncoder.encode(email.trim().lowercase(), "UTF-8")
            val req = Request.Builder()
                .url("$baseUrl/rest/v1/app_users?email=eq.$encodedEmail")
                .addHeader("apikey", apiKey)
                .addHeader("Authorization", authHeader)
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
                            tokenStore.saveEncryptedBoolean(KEY_ENC_ADMIN, matched.isAdmin)
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
            var token = activeAccessToken()
            var sessionRevoked = false

            if (isSupabaseConfigured()) {
                ensureValidSessionBlocking()
                token = activeAccessToken()
            }

            if (isSupabaseConfigured() && token.isNotBlank()) {
                val baseUrl = resolvedSupabaseBaseUrl()
                val apiKey = resolvedSupabaseApiKey()

                fun fetchUserInfo(bearer: String): Pair<Int, JSONObject?> {
                    return try {
                        val req = Request.Builder()
                            .url("$baseUrl/auth/v1/user")
                            .addHeader("apikey", apiKey)
                            .addHeader("Authorization", "Bearer $bearer")
                            .get()
                            .build()
                        httpClient.newCall(req).execute().use { resp ->
                            val code = resp.code
                            val body = if (resp.isSuccessful) {
                                try { JSONObject(resp.body?.string().orEmpty()) } catch (_: Exception) { null }
                            } else {
                                null
                            }
                            code to body
                        }
                    } catch (_: Exception) {
                        -1 to null
                    }
                }

                var (statusCode, userObj) = fetchUserInfo(token)
                var refreshSucceeded = false
                if (statusCode == 401 && refreshSessionBlocking(failedAccessToken = token)) {
                    refreshSucceeded = true
                    token = activeAccessToken()
                    if (token.isNotBlank()) {
                        val retried = fetchUserInfo(token)
                        statusCode = retried.first
                        userObj = retried.second
                    }
                }

                when {
                    statusCode in 200..299 -> {
                        metadataAdmin = isMetadataAdmin(userObj)
                    }
                    statusCode == 403 || statusCode == 404 -> {
                        sessionRevoked = true
                    }
                    statusCode == 401 && refreshSucceeded -> {
                        // A freshly rotated access token was still rejected by /auth/v1/user
                        sessionRevoked = true
                    }
                }
            }

            if (sessionRevoked) {
                forceSessionExpiredSignOut("Your session expired or this account is no longer active. Please sign in again.")
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
                return@launch
            }

            val remoteUsers = fetchRemoteAppUsersBlocking()
            withContext(Dispatchers.Main) {
                if (remoteUsers != null) {
                    saveUsersLocally(remoteUsers)
                    val matched = remoteUsers.find { it.email.equals(activeEmail, ignoreCase = true) }
                    val resolvedAdmin = (matched?.isAdmin == true) || metadataAdmin
                    _isAdmin.value = resolvedAdmin
                    tokenStore.saveEncryptedBoolean(KEY_ENC_ADMIN, resolvedAdmin)
                    onComplete?.invoke(resolvedAdmin)
                } else {
                    val localMatched = _registeredUsers.value.find { it.email.equals(activeEmail, ignoreCase = true) }
                    val resolvedAdmin = (localMatched?.isAdmin == true) || metadataAdmin
                    _isAdmin.value = resolvedAdmin
                    tokenStore.saveEncryptedBoolean(KEY_ENC_ADMIN, resolvedAdmin)
                    onComplete?.invoke(resolvedAdmin)
                }
            }
        }
    }

    private suspend fun resolveAndCompleteSessionBlocking(
        email: String,
        accessToken: String = "",
        refreshToken: String = "",
        metadataIsAdmin: Boolean = false,
        expiresAtMillis: Long? = null
    ) {
        val clean = email.trim().lowercase()
        val previousEmail = _currentUserEmail.value?.trim()?.lowercase()
        if (!previousEmail.isNullOrBlank() && !previousEmail.equals(clean, ignoreCase = true)) {
            clearSensitivePrefsSync()
            withContext(Dispatchers.IO) {
                runCatching {
                    com.example.data.local.AppDatabase.getDatabase(appContext).clearAllTables()
                }
            }
            withContext(Dispatchers.Main) {
                onSessionClearedListener?.invoke()
            }
        }

        val effectiveAccessToken = accessToken.trim().ifBlank {
            if (allowOfflineTestFallback()) "robolectric-test-jwt" else ""
        }
        val effectiveRefreshToken = refreshToken.trim().ifBlank {
            if (allowOfflineTestFallback()) "robolectric-test-refresh" else ""
        }

        val remoteUsers = fetchRemoteAppUsersBlocking(effectiveAccessToken.takeIf { it.isNotBlank() })

        val resolvedAdmin: Boolean
        val updatedUsers: List<UserAccount>

        if (remoteUsers != null) {
            val remoteMatch = remoteUsers.find { it.email.equals(clean, ignoreCase = true) }
            resolvedAdmin = (remoteMatch?.isAdmin == true) || metadataIsAdmin
            if (metadataIsAdmin && (remoteMatch == null || !remoteMatch.isAdmin)) {
                pushRemoteUserUpsert(clean, true, effectiveAccessToken.takeIf { it.isNotBlank() })
                updatedUsers = if (remoteMatch == null) {
                    (remoteUsers + UserAccount(clean, true)).distinctBy { it.email }
                } else {
                    remoteUsers.map {
                        if (it.email.equals(clean, ignoreCase = true)) UserAccount(clean, true) else it
                    }
                }
            } else if (remoteMatch == null) {
                updatedUsers = (remoteUsers + UserAccount(clean, resolvedAdmin)).distinctBy { it.email }
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

            if (effectiveAccessToken.isNotBlank() || effectiveRefreshToken.isNotBlank()) {
                val resolvedExp = (expiresAtMillis ?: parseJwtExpiryMillis(effectiveAccessToken)).takeIf { it > 0L }
                tokenStore.saveSessionTokens(effectiveAccessToken, effectiveRefreshToken, resolvedExp)
            }
            tokenStore.saveEncryptedBoolean(KEY_ENC_AUTHED, true)
            tokenStore.saveEncryptedBoolean(KEY_ENC_ADMIN, resolvedAdmin)
            tokenStore.saveEncryptedString(KEY_ENC_CURRENT_USER_EMAIL, clean)
            tokenStore.saveEncryptedString(KEY_ENC_LAST_EMAIL, clean)
            tokenStore.removeEncryptedKey(KEY_ENC_PENDING_SIGNUP_EMAIL)
            scrubLegacySessionPrefs()

            clearOtpTimer()
            clearResendCooldown()
            _pendingVerificationEmail.value = null
            _currentUserEmail.value = clean
            _isAdmin.value = resolvedAdmin
            _isAuthed.value = true
        }
    }

    internal suspend fun completeSessionBlocking(
        email: String,
        accessToken: String = "",
        refreshToken: String = "",
        metadataIsAdmin: Boolean = false,
        expiresAtMillis: Long? = null
    ) {
        resolveAndCompleteSessionBlocking(email, accessToken, refreshToken, metadataIsAdmin, expiresAtMillis)
    }

    fun signUpEmailPassword(
        email: String,
        password: String,
        callback: (Boolean, String?) -> Unit
    ) {
        if (isLocked()) {
            callback(false, "Too many failed attempts. Try again in ${getRemainingSeconds()}s.")
            return
        }
        val clean = email.trim().lowercase()
        if (!isValidEmail(clean)) {
            callback(false, "Enter a valid email address.")
            return
        }
        if (!isValidPassword(password)) {
            callback(false, "Password must be at least $MIN_PASSWORD_LENGTH characters.")
            return
        }
        clearDeepLinkStatus()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                authNetworkMutex.withLock {
                    if (isLocked()) {
                        withContext(Dispatchers.Main) {
                            callback(false, "Too many failed attempts. Try again in ${getRemainingSeconds()}s.")
                        }
                        return@withLock
                    }
                    if (!isSupabaseConfigured()) {
                        if (allowOfflineTestFallback()) {
                            resolveAndCompleteSessionBlocking(clean)
                            withContext(Dispatchers.Main) {
                                callback(true, "Account created & signed in.")
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                callback(false, unconfiguredAuthMessage())
                            }
                        }
                        return@withLock
                    }

                    val baseUrl = com.example.BuildConfig.SUPABASE_URL.trim().trim('"').trimEnd('/')
                    val apiKey = com.example.BuildConfig.SUPABASE_KEY.trim().trim('"')
                    val json = JSONObject()
                        .put("email", clean)
                        .put("password", password)
                        .toString()

                    val req = Request.Builder()
                        .url("$baseUrl/auth/v1/signup")
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
                            val refreshToken = obj?.optString("refresh_token").orEmpty()
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
                                        refreshToken = refreshToken,
                                        metadataIsAdmin = isMetadataAdmin(userObj),
                                        expiresAtMillis = resolveExpiryMillis(obj, accessToken, wallClockNowMs()).takeIf { it > 0L }
                                    )
                                    withContext(Dispatchers.Main) {
                                        callback(true, "Account created & signed in!")
                                    }
                                }
                                else -> {
                                    withContext(Dispatchers.Main) {
                                        if (_registeredUsers.value.none { it.email.equals(clean, ignoreCase = true) }) {
                                            saveUsersLocally(_registeredUsers.value + UserAccount(clean, false))
                                        }
                                        saveLastSignedInEmail(clean)
                                        setPendingVerificationEmail(clean)
                                        startOtpExpiryTimer()
                                        startResendCooldown()
                                        callback(
                                            true,
                                            "OTP code sent to $clean (expires in 5 minutes). Enter the code below."
                                        )
                                    }
                                }
                            }
                        } else if (resp.code == 429) {
                            val retryAfter = parseRetryAfterSeconds(resp.header("Retry-After"))
                            val lockMsg = recordFailedAuthAttempt(httpStatusCode = 429, retryAfterSeconds = retryAfter)
                            withContext(Dispatchers.Main) {
                                callback(false, lockMsg ?: friendlySupabaseError(respBody, resp.code))
                            }
                        } else {
                            val msg = friendlySupabaseError(respBody, resp.code)
                            withContext(Dispatchers.Main) {
                                callback(false, msg)
                            }
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

    fun verifySignupOtp(
        email: String,
        otp: String,
        callback: (Boolean, String?) -> Unit
    ) {
        if (isLocked()) {
            callback(false, "Too many failed attempts. Try again in ${getRemainingSeconds()}s.")
            return
        }
        val clean = email.trim().lowercase()
        val cleanOtp = otp.trim()
        if (!isValidEmail(clean)) {
            callback(false, "Enter a valid email address.")
            return
        }
        if (cleanOtp.length != 6 || !cleanOtp.all { it.isDigit() }) {
            callback(false, "Enter the 6-digit OTP code sent to your email.")
            return
        }
        if (isCurrentOtpExpired()) {
            callback(false, "OTP code has expired (5-minute limit). Tap Resend OTP Code for a new one.")
            return
        }
        clearDeepLinkStatus()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                authNetworkMutex.withLock {
                    if (isLocked()) {
                        withContext(Dispatchers.Main) {
                            callback(false, "Too many failed attempts. Try again in ${getRemainingSeconds()}s.")
                        }
                        return@withLock
                    }
                    if (!isSupabaseConfigured()) {
                        if (allowOfflineTestFallback()) {
                            resolveAndCompleteSessionBlocking(clean)
                            withContext(Dispatchers.Main) {
                                callback(true, "Email verified! Welcome to Stock.")
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                callback(false, unconfiguredAuthMessage())
                            }
                        }
                        return@withLock
                    }

                    val baseUrl = com.example.BuildConfig.SUPABASE_URL.trim().trim('"').trimEnd('/')
                    val apiKey = com.example.BuildConfig.SUPABASE_KEY.trim().trim('"')
                    val payload = JSONObject()
                        .put("type", "signup")
                        .put("email", clean)
                        .put("token", cleanOtp)
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
                            val accessToken = obj?.optString("access_token").orEmpty()
                            val refreshToken = obj?.optString("refresh_token").orEmpty()
                            val hasConfirmationMetadata = userObj != null &&
                                (userObj.has("email_confirmed_at") || userObj.has("confirmed_at"))
                            if (accessToken.isBlank() || (hasConfirmationMetadata && !isEmailConfirmed(userObj))) {
                                withContext(Dispatchers.Main) {
                                    callback(false, "OTP verification incomplete. Please check the code and try again.")
                                }
                                return@use
                            }
                            resetAuthFailures()
                            resolveAndCompleteSessionBlocking(
                                email = clean,
                                accessToken = accessToken,
                                refreshToken = refreshToken,
                                metadataIsAdmin = isMetadataAdmin(userObj),
                                expiresAtMillis = resolveExpiryMillis(obj, accessToken, wallClockNowMs()).takeIf { it > 0L }
                            )
                            withContext(Dispatchers.Main) {
                                callback(true, "Email verified! Welcome to Stock.")
                            }
                        } else {
                            val msg = friendlySupabaseError(body, resp.code)
                            val retryAfter = parseRetryAfterSeconds(resp.header("Retry-After"))
                            val lockHint = recordFailedAuthAttempt(httpStatusCode = resp.code, retryAfterSeconds = retryAfter)
                            val fullMsg = if (lockHint != null && lockHint.startsWith("Too many")) lockHint else "$msg ${lockHint.orEmpty()}".trim()
                            withContext(Dispatchers.Main) {
                                callback(false, fullMsg)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(false, "Failed to verify OTP: ${e.message ?: "Network error"}")
                }
            }
        }
    }

    fun resendConfirmationEmail(
        email: String,
        callback: (Boolean, String?) -> Unit
    ) {
        if (isLocked()) {
            callback(false, "Too many failed attempts. Try again in ${getRemainingSeconds()}s.")
            return
        }
        val cooldown = getResendCooldownRemainingSeconds()
        if (cooldown > 0) {
            callback(false, "Please wait ${cooldown}s before requesting another OTP.")
            return
        }
        val clean = email.trim().lowercase()
        if (!isValidEmail(clean)) {
            callback(false, "Enter a valid email address.")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                authNetworkMutex.withLock {
                    if (isLocked()) {
                        withContext(Dispatchers.Main) {
                            callback(false, "Too many failed attempts. Try again in ${getRemainingSeconds()}s.")
                        }
                        return@withLock
                    }
                    if (!isSupabaseConfigured()) {
                        if (allowOfflineTestFallback()) {
                            withContext(Dispatchers.Main) {
                                startOtpExpiryTimer()
                                startResendCooldown()
                                callback(true, "OTP code ready for $clean (valid for 5 minutes).")
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                callback(false, unconfiguredAuthMessage())
                            }
                        }
                        return@withLock
                    }
                    val baseUrl = com.example.BuildConfig.SUPABASE_URL.trim().trim('"').trimEnd('/')
                    val apiKey = com.example.BuildConfig.SUPABASE_KEY.trim().trim('"')
                    val json = JSONObject()
                        .put("type", "signup")
                        .put("email", clean)
                        .toString()

                    val req = Request.Builder()
                        .url("$baseUrl/auth/v1/resend")
                        .addHeader("apikey", apiKey)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("Content-Type", "application/json")
                        .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()

                    httpClient.newCall(req).execute().use { resp ->
                        val respBody = resp.body?.string().orEmpty()
                        if (resp.isSuccessful) {
                            withContext(Dispatchers.Main) {
                                startOtpExpiryTimer()
                                startResendCooldown()
                                callback(true, "New OTP code sent to $clean (expires in 5 minutes).")
                            }
                        } else if (resp.code == 429) {
                            val retryAfter = parseRetryAfterSeconds(resp.header("Retry-After"))
                            val lockMsg = recordFailedAuthAttempt(httpStatusCode = 429, retryAfterSeconds = retryAfter)
                            withContext(Dispatchers.Main) {
                                callback(false, lockMsg ?: friendlySupabaseError(respBody, resp.code))
                            }
                        } else {
                            val msg = friendlySupabaseError(respBody, resp.code)
                            withContext(Dispatchers.Main) {
                                callback(false, msg)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(false, "Failed to resend OTP: ${e.message ?: "Network error"}")
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
        val refreshToken = params["refresh_token"].orEmpty()
        val tokenHash = params["token_hash"].orEmpty()
        val type = params["type"].orEmpty().ifBlank { "signup" }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!isSupabaseConfigured()) {
                    if (allowOfflineTestFallback()) {
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
                    } else {
                        withContext(Dispatchers.Main) {
                            _authDeepLinkError.value = unconfiguredAuthMessage()
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
                                        refreshToken = refreshToken,
                                        metadataIsAdmin = isMetadataAdmin(userObj)
                                    )
                                    withContext(Dispatchers.Main) {
                                        _authDeepLinkMessage.value = "Email verified! Welcome to Stock."
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
                                val verifiedRefreshToken = obj?.optString("refresh_token").orEmpty()
                                val confirmedEmail = (userObj?.optString("email") ?: obj?.optString("email")).orEmpty()
                                    .ifBlank { _pendingVerificationEmail.value.orEmpty() }

                                if (confirmedEmail.isNotBlank()) {
                                    resolveAndCompleteSessionBlocking(
                                        email = confirmedEmail,
                                        accessToken = verifiedToken,
                                        refreshToken = verifiedRefreshToken,
                                        metadataIsAdmin = isMetadataAdmin(userObj),
                                        expiresAtMillis = resolveExpiryMillis(obj, verifiedToken, wallClockNowMs()).takeIf { it > 0L }
                                    )
                                    withContext(Dispatchers.Main) {
                                        _authDeepLinkMessage.value = "Email verified! Welcome to Stock."
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

    fun signInEmailPassword(
        email: String,
        password: String,
        callback: (Boolean, String?) -> Unit
    ) {
        if (isLocked()) {
            callback(false, "Too many failed attempts. Try again in ${getRemainingSeconds()}s.")
            return
        }
        val clean = email.trim().lowercase()
        if (!isValidEmail(clean) || password.isEmpty()) {
            callback(false, "Enter a valid email and password.")
            return
        }
        clearDeepLinkStatus()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                authNetworkMutex.withLock {
                    if (isLocked()) {
                        withContext(Dispatchers.Main) {
                            callback(false, "Too many failed attempts. Try again in ${getRemainingSeconds()}s.")
                        }
                        return@withLock
                    }
                    if (!isSupabaseConfigured()) {
                        if (allowOfflineTestFallback()) {
                            resetAuthFailures()
                            resolveAndCompleteSessionBlocking(clean)
                            withContext(Dispatchers.Main) {
                                callback(true, null)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                callback(false, unconfiguredAuthMessage())
                            }
                        }
                        return@withLock
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
                            val refreshToken = obj?.optString("refresh_token").orEmpty()
                            val userObj = obj?.optJSONObject("user")
                            val hasConfirmationMetadata = userObj != null &&
                                (userObj.has("email_confirmed_at") || userObj.has("confirmed_at"))
                            if (hasConfirmationMetadata && !isEmailConfirmed(userObj)) {
                                withContext(Dispatchers.Main) {
                                    setPendingVerificationEmail(clean)
                                    callback(false, "Your email is not verified yet. Open your inbox and confirm your email first.")
                                }
                                return@use
                            }
                            if (accessToken.isBlank()) {
                                withContext(Dispatchers.Main) {
                                    callback(false, "Sign in failed: missing session token.")
                                }
                                return@use
                            }
                            resetAuthFailures()
                            resolveAndCompleteSessionBlocking(
                                email = clean,
                                accessToken = accessToken,
                                refreshToken = refreshToken,
                                metadataIsAdmin = isMetadataAdmin(userObj),
                                expiresAtMillis = resolveExpiryMillis(obj, accessToken, wallClockNowMs()).takeIf { it > 0L }
                            )
                            withContext(Dispatchers.Main) {
                                callback(true, null)
                            }
                        } else {
                            val msg = friendlySupabaseError(respBody, resp.code)
                            val retryAfter = parseRetryAfterSeconds(resp.header("Retry-After"))
                            val lockHint = recordFailedAuthAttempt(httpStatusCode = resp.code, retryAfterSeconds = retryAfter)
                            val fullMsg = if (lockHint != null && lockHint.startsWith("Too many")) lockHint else "$msg ${lockHint.orEmpty()}".trim()
                            withContext(Dispatchers.Main) {
                                callback(false, fullMsg)
                            }
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

    private val accountOps by lazy { SupabaseAccountOperations(this) }

    fun requestPasswordResetOtp(
        email: String,
        callback: (Boolean, String?) -> Unit
    ) = accountOps.requestPasswordResetOtp(email, callback)

    fun verifyPasswordResetAndUpdate(
        email: String,
        otp: String,
        newPassword: String,
        callback: (Boolean, String?) -> Unit
    ) = accountOps.verifyPasswordResetAndUpdate(email, otp, newPassword, callback)

    fun requestChangeEmailOtp(newEmail: String, callback: (Boolean, String?) -> Unit) =
        accountOps.requestChangeEmailOtp(newEmail, callback)

    fun confirmChangeEmailOtp(
        newEmail: String,
        otp: String,
        callback: (Boolean, String?) -> Unit
    ) = accountOps.confirmChangeEmailOtp(newEmail, otp, callback)

    fun requestDeleteAccountOtp(
        callback: (Boolean, String?) -> Unit
    ) = accountOps.requestDeleteAccountOtp(callback)

    fun confirmDeleteAccountWithOtp(otp: String, callback: (Boolean, String?) -> Unit) =
        accountOps.confirmDeleteAccountWithOtp(otp, callback)
}
