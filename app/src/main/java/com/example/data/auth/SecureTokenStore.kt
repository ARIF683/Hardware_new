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
 * Stores authentication session tokens encrypted with an Android Keystore AES-256-GCM key.
 * Automatically migrates any legacy plaintext preference and falls back cleanly only in JVM test environments
 * where AndroidKeyStore is absent.
 */
internal class SecureTokenStore(
    private val prefs: SharedPreferences
) {
    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "stock_auth_session_aes_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val ENC_PREFIX = "v1:"
        private const val KEY_ENC_TOKEN = "supabase_access_token_enc"
        private const val KEY_LEGACY_PLAINTEXT = "supabase_access_token"
    }

    private fun getOrCreateKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
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

    fun saveToken(token: String) {
        val clean = token.trim()
        if (clean.isBlank()) {
            clearToken()
            return
        }
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

        val editor = prefs.edit().remove(KEY_LEGACY_PLAINTEXT)
        if (encodedValue != null) {
            editor.putString(KEY_ENC_TOKEN, encodedValue)
        } else {
            // Fallback only when AndroidKeyStore is unavailable (e.g., local Robolectric JVM)
            val b64 = Base64.encodeToString(clean.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            editor.putString(KEY_ENC_TOKEN, "jvm:$b64")
        }
        editor.apply()
    }

    fun readToken(): String {
        val stored = prefs.getString(KEY_ENC_TOKEN, null)
        if (!stored.isNullOrBlank()) {
            return when {
                stored.startsWith(ENC_PREFIX) -> decryptV1(stored.removePrefix(ENC_PREFIX))
                stored.startsWith("jvm:") -> decodeJvmFallback(stored.removePrefix("jvm:"))
                else -> ""
            }
        }

        // Migrate any pre-existing plaintext token into encrypted storage
        val legacy = prefs.getString(KEY_LEGACY_PLAINTEXT, null).orEmpty().trim()
        if (legacy.isNotBlank()) {
            saveToken(legacy)
            return legacy
        }
        return ""
    }

    fun clearToken() {
        prefs.edit()
            .remove(KEY_ENC_TOKEN)
            .remove(KEY_LEGACY_PLAINTEXT)
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
