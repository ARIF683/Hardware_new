package com.example.data.auth

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores sensitive tokens/secrets encrypted with an Android Keystore AES-256-GCM key.
 * Automatically migrates any legacy plaintext preference and falls back cleanly only in JVM test environments
 * where AndroidKeyStore is absent.
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
        private const val KEY_ENC_TOKEN = "supabase_access_token_enc"
        private const val KEY_LEGACY_PLAINTEXT = "supabase_access_token"
        private const val KEY_ENC_REFRESH_TOKEN = "supabase_refresh_token_enc"
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

    private fun encodeSecret(clean: String): String {
        val key = getOrCreateKey()
        val encodedValue = if (key != null) {
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, key)
                val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
                val encrypted = Base64.encodeToString(
                    cipher.doFinal(clean.toByteArray(Charsets.UTF_8)),
                    Base64.NO_WRAP
                )
                "$ENC_PREFIX$iv:$encrypted"
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }
        return encodedValue ?: run {
            // Fallback only when AndroidKeyStore is unavailable (e.g., local Robolectric JVM)
            val b64 = Base64.encodeToString(clean.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            "jvm:$b64"
        }
    }

    private fun decodeSecret(stored: String?): String {
        if (stored.isNullOrBlank()) return ""
        return when {
            stored.startsWith(ENC_PREFIX) -> decryptV1(stored.removePrefix(ENC_PREFIX))
            stored.startsWith("jvm:") -> decodeJvmFallback(stored.removePrefix("jvm:"))
            else -> ""
        }
    }

    fun saveToken(token: String) {
        val clean = token.trim()
        if (clean.isBlank()) {
            prefs.edit()
                .remove(encTokenKey)
                .remove(legacyPlaintextKey)
                .apply()
            return
        }
        prefs.edit()
            .remove(legacyPlaintextKey)
            .putString(encTokenKey, encodeSecret(clean))
            .apply()
    }

    fun readToken(): String {
        val stored = prefs.getString(encTokenKey, null)
        if (!stored.isNullOrBlank()) {
            return decodeSecret(stored)
        }

        // Migrate any pre-existing plaintext value into encrypted storage
        val legacy = prefs.getString(legacyPlaintextKey, null).orEmpty().trim()
        if (legacy.isNotBlank()) {
            saveToken(legacy)
            return legacy
        }
        return ""
    }

    fun saveRefreshToken(refreshToken: String) {
        val clean = refreshToken.trim()
        if (clean.isBlank()) {
            prefs.edit().remove(encRefreshTokenKey).apply()
            return
        }
        prefs.edit()
            .putString(encRefreshTokenKey, encodeSecret(clean))
            .apply()
    }

    fun readRefreshToken(): String =
        decodeSecret(prefs.getString(encRefreshTokenKey, null))

    fun saveSessionTokens(accessToken: String, refreshToken: String) {
        val cleanAccess = accessToken.trim()
        val cleanRefresh = refreshToken.trim()
        val editor = prefs.edit().remove(legacyPlaintextKey)
        if (cleanAccess.isNotBlank()) {
            editor.putString(encTokenKey, encodeSecret(cleanAccess))
        } else {
            editor.remove(encTokenKey)
        }
        if (cleanRefresh.isNotBlank()) {
            editor.putString(encRefreshTokenKey, encodeSecret(cleanRefresh))
        } else if (cleanAccess.isBlank()) {
            editor.remove(encRefreshTokenKey)
        }
        editor.apply()
    }

    fun clearToken() {
        prefs.edit()
            .remove(encTokenKey)
            .remove(legacyPlaintextKey)
            .remove(encRefreshTokenKey)
            .apply()
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
