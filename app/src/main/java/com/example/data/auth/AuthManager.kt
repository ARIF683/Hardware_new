package com.example.data.auth

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class AuthManager(context: Context) {
    companion object {
        const val APP_HASH = "2f03a273df7e7fc706f021d3722238fcceda044783a8c9082087b8b941de8c3e"
        const val ADMIN_HASH = "2f03a273df7e7fc706f021d3722238fcceda044783a8c9082087b8b941de8c3e"
        const val MAX_TRIES = 3
        const val LOCK_MS = 60_000L
        const val MAX_ADMIN_TRIES = 3
        const val ADMIN_LOCK_MS = 60_000L

        private const val PBKDF2_PREFIX = "pbkdf2_sha256"
        private const val PBKDF2_ITERATIONS = 32_000
        private const val SALT_BYTES = 16
        private const val KEY_BYTES = 32

        private const val PREFS_NAME = "stock_manager_auth"
        private const val KEY_AUTHED = "is_authed"
        private const val KEY_ADMIN = "is_admin"
        private const val KEY_FAILS = "login_fails"
        private const val KEY_LOCK_UNTIL = "lock_until"
        private const val KEY_ADMIN_FAILS = "admin_login_fails"
        private const val KEY_ADMIN_LOCK_UNTIL = "admin_lock_until"
        private const val KEY_CUSTOM_ADMIN_VERIFIER = "custom_admin_pin_verifier"
        private const val KEY_CUSTOM_ADMIN_UPDATED_AT = "custom_admin_pin_updated_at"

        fun createSaltedPinVerifier(pin: String, iterations: Int = PBKDF2_ITERATIONS): String {
            val salt = ByteArray(SALT_BYTES)
            SecureRandom().nextBytes(salt)
            val derived = pbkdf2HmacSha256(pin.toCharArray(), salt, iterations, KEY_BYTES)
            return "$PBKDF2_PREFIX$$iterations$${salt.toHex()}$${derived.toHex()}"
        }

        fun verifySaltedPinVerifier(pin: String, storedVerifier: String): Boolean {
            val parts = storedVerifier.trim().split("$")
            if (parts.size != 4 || parts[0] != PBKDF2_PREFIX) return false
            val iterations = parts[1].toIntOrNull()?.takeIf { it in 1_000..500_000 } ?: return false
            val salt = hexToBytes(parts[2]) ?: return false
            val expectedHash = hexToBytes(parts[3]) ?: return false
            if (salt.isEmpty() || expectedHash.isEmpty()) return false
            val actualHash = pbkdf2HmacSha256(pin.toCharArray(), salt, iterations, expectedHash.size)
            return MessageDigest.isEqual(actualHash, expectedHash)
        }

        internal fun pbkdf2HmacSha256(
            password: CharArray,
            salt: ByteArray,
            iterations: Int,
            keyLengthBytes: Int
        ): ByteArray {
            val passwordBytes = String(password).toByteArray(Charsets.UTF_8)
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(passwordBytes, "HmacSHA256"))
            val hashLen = 32
            val blockCount = (keyLengthBytes + hashLen - 1) / hashLen
            val output = ByteArray(blockCount * hashLen)
            val intBlock = ByteArray(4)

            for (blockIndex in 1..blockCount) {
                intBlock[0] = (blockIndex ushr 24).toByte()
                intBlock[1] = (blockIndex ushr 16).toByte()
                intBlock[2] = (blockIndex ushr 8).toByte()
                intBlock[3] = blockIndex.toByte()

                mac.reset()
                mac.update(salt)
                mac.update(intBlock)
                var u = mac.doFinal()
                val t = u.copyOf()

                for (iter in 2..iterations) {
                    mac.reset()
                    u = mac.doFinal(u)
                    for (j in t.indices) {
                        t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
                    }
                }
                System.arraycopy(t, 0, output, (blockIndex - 1) * hashLen, hashLen)
            }
            return output.copyOf(keyLengthBytes)
        }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

        private fun hexToBytes(hex: String): ByteArray? {
            if (hex.length % 2 != 0 || hex.isEmpty()) return null
            return try {
                ByteArray(hex.length / 2) { idx ->
                    hex.substring(idx * 2, idx * 2 + 2).toInt(16).toByte()
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isAuthed = MutableStateFlow(prefs.getBoolean(KEY_AUTHED, false))
    val isAuthed: StateFlow<Boolean> = _isAuthed.asStateFlow()

    private val _isAdmin = MutableStateFlow(prefs.getBoolean(KEY_ADMIN, false))
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _lockRemainingSeconds = MutableStateFlow(0)
    val lockRemainingSeconds: StateFlow<Int> = _lockRemainingSeconds.asStateFlow()

    private val _adminLockRemainingSeconds = MutableStateFlow(0)
    val adminLockRemainingSeconds: StateFlow<Int> = _adminLockRemainingSeconds.asStateFlow()

    private val _hasCustomAdminPin = MutableStateFlow(
        !prefs.getString(KEY_CUSTOM_ADMIN_VERIFIER, null).isNullOrBlank()
    )
    val hasCustomAdminPin: StateFlow<Boolean> = _hasCustomAdminPin.asStateFlow()

    private var loginFails = prefs.getInt(KEY_FAILS, 0)
    private var lockUntil = prefs.getLong(KEY_LOCK_UNTIL, 0L)
    private var adminFails = prefs.getInt(KEY_ADMIN_FAILS, 0)
    private var adminLockUntil = prefs.getLong(KEY_ADMIN_LOCK_UNTIL, 0L)
    private var tickerJob: Job? = null
    private var adminTickerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        val now = System.currentTimeMillis()
        if (lockUntil > now) {
            startLockCountdown()
        } else {
            lockUntil = 0
            loginFails = 0
            prefs.edit().remove(KEY_LOCK_UNTIL).remove(KEY_FAILS).apply()
        }
        if (adminLockUntil > now) {
            startAdminLockCountdown()
        } else {
            adminLockUntil = 0
            adminFails = 0
            prefs.edit().remove(KEY_ADMIN_LOCK_UNTIL).remove(KEY_ADMIN_FAILS).apply()
        }
    }

    fun isLocked(): Boolean = System.currentTimeMillis() < lockUntil

    fun isAdminLocked(): Boolean = System.currentTimeMillis() < adminLockUntil

    fun getRemainingSeconds(): Int {
        val diff = lockUntil - System.currentTimeMillis()
        return if (diff > 0) ((diff + 999) / 1000).toInt() else 0
    }

    fun getAdminRemainingSeconds(): Int {
        val diff = adminLockUntil - System.currentTimeMillis()
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

    private fun startAdminLockCountdown() {
        adminTickerJob?.cancel()
        _adminLockRemainingSeconds.value = getAdminRemainingSeconds()
        adminTickerJob = scope.launch {
            while (isActive && isAdminLocked()) {
                _adminLockRemainingSeconds.value = getAdminRemainingSeconds()
                delay(500)
            }
            _adminLockRemainingSeconds.value = 0
            adminLockUntil = 0
            adminFails = 0
            prefs.edit().remove(KEY_ADMIN_LOCK_UNTIL).remove(KEY_ADMIN_FAILS).apply()
        }
    }

    private fun hash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.toHex()
    }

    sealed class AuthResult {
        object Success : AuthResult()
        data class Locked(val secondsLeft: Int) : AuthResult()
        data class WrongPassword(val triesLeft: Int) : AuthResult()
        data class Error(val message: String) : AuthResult()
    }

    sealed class AdminAuthResult {
        object Success : AdminAuthResult()
        data class Locked(val secondsLeft: Int) : AdminAuthResult()
        data class WrongPin(val triesLeft: Int) : AdminAuthResult()
        data class Error(val message: String) : AdminAuthResult()
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
        prefs.edit().putBoolean(KEY_AUTHED, false).apply()
        _isAuthed.value = false
    }

    private fun matchesConfiguredAdminPin(trimmedPin: String): Boolean {
        val customVerifier = prefs.getString(KEY_CUSTOM_ADMIN_VERIFIER, null).orEmpty().trim()
        return if (customVerifier.isNotEmpty()) {
            verifySaltedPinVerifier(trimmedPin, customVerifier)
        } else {
            // Initial fallback only until Admin sets a private salted PIN in Settings
            MessageDigest.isEqual(
                hash(trimmedPin).toByteArray(Charsets.UTF_8),
                ADMIN_HASH.toByteArray(Charsets.UTF_8)
            )
        }
    }

    fun verifyAdminPin(pin: String): AdminAuthResult {
        if (isAdminLocked()) {
            return AdminAuthResult.Locked(getAdminRemainingSeconds())
        }
        val trimmed = pin.trim()
        if (trimmed.isEmpty()) {
            return AdminAuthResult.Error("Enter the admin PIN.")
        }
        return if (matchesConfiguredAdminPin(trimmed)) {
            adminFails = 0
            adminLockUntil = 0L
            _adminLockRemainingSeconds.value = 0
            prefs.edit()
                .remove(KEY_ADMIN_FAILS)
                .remove(KEY_ADMIN_LOCK_UNTIL)
                .apply()
            AdminAuthResult.Success
        } else {
            adminFails++
            if (adminFails >= MAX_ADMIN_TRIES) {
                adminLockUntil = System.currentTimeMillis() + ADMIN_LOCK_MS
                prefs.edit()
                    .putInt(KEY_ADMIN_FAILS, adminFails)
                    .putLong(KEY_ADMIN_LOCK_UNTIL, adminLockUntil)
                    .apply()
                startAdminLockCountdown()
                AdminAuthResult.Locked(getAdminRemainingSeconds().coerceAtLeast(1))
            } else {
                prefs.edit().putInt(KEY_ADMIN_FAILS, adminFails).apply()
                AdminAuthResult.WrongPin(MAX_ADMIN_TRIES - adminFails)
            }
        }
    }

    fun unlockAdminWithResult(pin: String): AdminAuthResult {
        val res = verifyAdminPin(pin)
        if (res is AdminAuthResult.Success) {
            _isAdmin.value = true
            prefs.edit().putBoolean(KEY_ADMIN, true).apply()
        }
        return res
    }

    fun unlockAdmin(pin: String): Boolean {
        return unlockAdminWithResult(pin) is AdminAuthResult.Success
    }

    fun setCustomAdminPin(currentPin: String, newPin: String): Pair<AdminAuthResult, String?> {
        val currentCheck = verifyAdminPin(currentPin)
        if (currentCheck !is AdminAuthResult.Success) {
            return currentCheck to null
        }
        val cleanNew = newPin.trim()
        if (cleanNew.length < 4) {
            return AdminAuthResult.Error("New PIN must be at least 4 digits.") to null
        }
        if (cleanNew.length > 16) {
            return AdminAuthResult.Error("New PIN must be 16 characters or fewer.") to null
        }
        val verifier = createSaltedPinVerifier(cleanNew)
        val now = System.currentTimeMillis()
        prefs.edit()
            .putString(KEY_CUSTOM_ADMIN_VERIFIER, verifier)
            .putLong(KEY_CUSTOM_ADMIN_UPDATED_AT, now)
            .putBoolean(KEY_ADMIN, true)
            .apply()
        _hasCustomAdminPin.value = true
        _isAdmin.value = true
        return AdminAuthResult.Success to verifier
    }

    fun getCustomAdminPinVerifierOrNull(): String? =
        prefs.getString(KEY_CUSTOM_ADMIN_VERIFIER, null)?.takeIf { it.isNotBlank() }

    fun getCustomAdminPinUpdatedAt(): Long =
        prefs.getLong(KEY_CUSTOM_ADMIN_UPDATED_AT, 0L)

    fun applyRemoteAdminPinHash(remoteVerifier: String?, remoteUpdatedAt: Long) {
        if (remoteVerifier.isNullOrBlank()) return
        val parts = remoteVerifier.trim().split("$")
        if (parts.size != 4 || parts[0] != PBKDF2_PREFIX) return

        val localVerifier = prefs.getString(KEY_CUSTOM_ADMIN_VERIFIER, null).orEmpty()
        val localUpdatedAt = prefs.getLong(KEY_CUSTOM_ADMIN_UPDATED_AT, 0L)
        if (localVerifier == remoteVerifier.trim()) return
        if (localVerifier.isNotBlank() && localUpdatedAt > 0L && remoteUpdatedAt > 0L && remoteUpdatedAt < localUpdatedAt) {
            return
        }

        prefs.edit()
            .putString(KEY_CUSTOM_ADMIN_VERIFIER, remoteVerifier.trim())
            .putLong(KEY_CUSTOM_ADMIN_UPDATED_AT, if (remoteUpdatedAt > 0L) remoteUpdatedAt else System.currentTimeMillis())
            .apply()
        _hasCustomAdminPin.value = true
    }

    fun exitAdmin() {
        _isAdmin.value = false
        prefs.edit().putBoolean(KEY_ADMIN, false).apply()
    }
}
