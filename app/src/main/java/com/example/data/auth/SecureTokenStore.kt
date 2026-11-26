package com.example.data.auth

import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores sensitive tokens, session flags, and secrets encrypted with an Android Keystore AES-256-GCM key.
 * Plaintext `"jvm:"` encoding/decoding is strictly restricted to Robolectric JVM test environments.
 */
internal class SecureTokenStore(
    private val prefs: SharedPreferences,
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    private val encTokenKey: String = KEY_ENC_TOKEN,
    private val legacyPlaintextKey: String = KEY_LEGACY_PLAINTEXT,
    private val encRefreshTokenKey: String = KEY_ENC_REFRESH_TOKEN
) {
    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val DEFAULT_KEY_ALIAS = "stock_auth_session_aes_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val ENC_PREFIX = "v1:"
        private const val JVM_PREFIX = "jvm:"
        private const val KEY_ENC_TOKEN = "supabase_access_token_enc"
        private const val KEY_LEGACY_PLAINTEXT = "supabase_access_token"
        private const val KEY_ENC_REFRESH_TOKEN = "supabase_refresh_token_enc"

        internal fun isRobolectricRuntime(): Boolean =
            Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
    }

    private fun getOrCreateKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            val existing = keyStore.getKey(keyAlias, null) as? SecretKey
            if (existing != null) return existing

            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val spec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGen.init(spec)
            keyGen.generateKey()
        } catch (_: Throwable) {
            null
        }
    }

    private fun encodeSecret(clean: String): String? {
        val key = getOrCreateKey()
        if (key != null) {
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, key)
                val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
                val encrypted = Base64.encodeToString(
                    cipher.doFinal(clean.toByteArray(Charsets.UTF_8)),
                    Base64.NO_WRAP
                )
                return "$ENC_PREFIX$iv:$encrypted"
            } catch (_: Throwable) {
                if (!isRobolectricRuntime()) return null
            }
        }
        // Only permit JVM Base64 fallback inside Robolectric unit tests
        if (!isRobolectricRuntime()) {
            return null
        }
        val b64 = Base64.encodeToString(clean.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "$JVM_PREFIX$b64"
    }

    private fun decodeSecret(stored: String?): String {
        if (stored.isNullOrBlank()) return ""
        return when {
            stored.startsWith(ENC_PREFIX) -> decryptV1(stored.removePrefix(ENC_PREFIX))
            stored.startsWith(JVM_PREFIX) && isRobolectricRuntime() ->
                decodeJvmFallback(stored.removePrefix(JVM_PREFIX))
            else -> ""
        }
    }

    fun saveToken(token: String): Boolean {
        val clean = token.trim()
        if (clean.isBlank()) {
            prefs.edit()
                .remove(encTokenKey)
                .remove(legacyPlaintextKey)
                .apply()
            return true
        }
        val encoded = encodeSecret(clean) ?: return false
        prefs.edit()
            .remove(legacyPlaintextKey)
            .putString(encTokenKey, encoded)
            .apply()
        return true
    }

    fun readToken(): String {
        val stored = prefs.getString(encTokenKey, null)
        if (!stored.isNullOrBlank()) {
            return decodeSecret(stored)
        }

        // Migrate any pre-existing plaintext value into encrypted storage and scrub plaintext key
        val legacy = prefs.getString(legacyPlaintextKey, null).orEmpty().trim()
        if (legacy.isNotBlank()) {
            val saved = saveToken(legacy)
            if (!saved) {
                prefs.edit().remove(legacyPlaintextKey).apply()
                return ""
            }
            return legacy
        }
        return ""
    }

    fun saveRefreshToken(refreshToken: String): Boolean {
        val clean = refreshToken.trim()
        if (clean.isBlank()) {
            prefs.edit().remove(encRefreshTokenKey).apply()
            return true
        }
        val encoded = encodeSecret(clean) ?: return false
        prefs.edit()
            .putString(encRefreshTokenKey, encoded)
            .apply()
        return true
    }

    fun readRefreshToken(): String =
        decodeSecret(prefs.getString(encRefreshTokenKey, null))

    fun saveSessionTokens(accessToken: String, refreshToken: String): Boolean {
        val cleanAccess = accessToken.trim()
        val cleanRefresh = refreshToken.trim()
        val encodedAccess = if (cleanAccess.isNotBlank()) (encodeSecret(cleanAccess) ?: return false) else null
        val encodedRefresh = if (cleanRefresh.isNotBlank()) (encodeSecret(cleanRefresh) ?: return false) else null

        val editor = prefs.edit().remove(legacyPlaintextKey)
        if (encodedAccess != null) {
            editor.putString(encTokenKey, encodedAccess)
        } else {
            editor.remove(encTokenKey)
        }
        if (encodedRefresh != null) {
            editor.putString(encRefreshTokenKey, encodedRefresh)
        } else if (cleanAccess.isBlank()) {
            editor.remove(encRefreshTokenKey)
        }
        editor.apply()
        return true
    }

    fun clearToken() {
        prefs.edit()
            .remove(encTokenKey)
            .remove(legacyPlaintextKey)
            .remove(encRefreshTokenKey)
            .apply()
    }

    fun saveEncryptedString(key: String, value: String): Boolean {
        if (value.isBlank()) {
            prefs.edit().remove(key).apply()
            return true
        }
        val encoded = encodeSecret(value) ?: return false
        prefs.edit()
            .putString(key, encoded)
            .apply()
        return true
    }

    fun readEncryptedString(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        val decoded = decodeSecret(stored)
        return decoded.ifBlank { null }
    }

    fun saveEncryptedBoolean(key: String, value: Boolean): Boolean =
        saveEncryptedString(key, if (value) "1" else "0")

    fun readEncryptedBoolean(key: String, defaultValue: Boolean = false): Boolean {
        val decoded = readEncryptedString(key) ?: return defaultValue
        return when (decoded) {
            "1", "true" -> true
            "0", "false" -> false
            else -> defaultValue
        }
    }

    fun hasEncryptedValue(key: String): Boolean = prefs.contains(key)

    fun removeEncryptedKey(key: String) {
        prefs.edit().remove(key).apply()
    }

    private fun decryptV1(payload: String): String {
        val parts = payload.split(":", limit = 2)
        if (parts.size != 2) return ""
        val key = getOrCreateKey() ?: return ""
        return try {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (_: Throwable) {
            ""
        }
    }

    private fun decodeJvmFallback(payload: String): String {
        return try {
            String(Base64.decode(payload, Base64.NO_WRAP), Charsets.UTF_8)
        } catch (_: Throwable) {
            ""
        }
    }
}
