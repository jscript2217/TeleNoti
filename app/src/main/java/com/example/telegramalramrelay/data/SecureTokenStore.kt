package com.example.telegramalramrelay.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts sensitive Telegram credentials using AndroidKeyStore AES-256-GCM
 * and migrates existing plaintext credentials from legacy "settings" SharedPreferences.
 */
class SecureTokenStore(context: Context) {
    private val appContext = context.applicationContext
    private val securePrefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    fun migrateFromLegacyIfNeeded(legacyPrefs: SharedPreferences) {
        synchronized(lock) {
            val existingToken = getBotToken()
            val existingChatId = getChatId()

            val legacyToken = legacyPrefs.getString(KEY_BOT_TOKEN, null)
            val legacyChatId = legacyPrefs.getString(KEY_CHAT_ID, null)

            if (legacyToken == null && legacyChatId == null) {
                return
            }

            val tokenToStore = if (existingToken.isBlank() && !legacyToken.isNullOrBlank()) {
                legacyToken.trim()
            } else {
                existingToken
            }
            val chatIdToStore = if (existingChatId.isBlank() && !legacyChatId.isNullOrBlank()) {
                legacyChatId.trim()
            } else {
                existingChatId
            }

            val encryptedSaved = saveCredentialsEncryptedOnly(tokenToStore, chatIdToStore)
            if (encryptedSaved) {
                val verifiedToken = getBotToken()
                val verifiedChatId = getChatId()
                if (verifiedToken == tokenToStore && verifiedChatId == chatIdToStore) {
                    legacyPrefs.edit()
                        .remove(KEY_BOT_TOKEN)
                        .remove(KEY_CHAT_ID)
                        .apply()
                }
            } else {
                // Keep plaintext fallback intact if Keystore is unavailable so user data is never lost
                saveCredentials(tokenToStore, chatIdToStore)
            }
        }
    }

    fun getBotToken(): String = synchronized(lock) {
        readSecret(KEY_BOT_TOKEN)
    }

    fun getChatId(): String = synchronized(lock) {
        readSecret(KEY_CHAT_ID)
    }

    fun saveCredentials(botToken: String, chatId: String) {
        synchronized(lock) {
            val cleanToken = botToken.trim()
            val cleanChatId = chatId.trim()
            if (!saveCredentialsEncryptedOnly(cleanToken, cleanChatId)) {
                // Fallback encoding if AndroidKeyStore is broken on a specific device
                securePrefs.edit()
                    .putString(KEY_BOT_TOKEN, encodeFallback(cleanToken))
                    .putString(KEY_CHAT_ID, encodeFallback(cleanChatId))
                    .commit()
            }
        }
    }

    private fun saveCredentialsEncryptedOnly(token: String, chatId: String): Boolean {
        return try {
            val encToken = if (token.isEmpty()) "" else encrypt(token)
            val encChatId = if (chatId.isEmpty()) "" else encrypt(chatId)
            securePrefs.edit()
                .putString(KEY_BOT_TOKEN, encToken)
                .putString(KEY_CHAT_ID, encChatId)
                .commit()
        } catch (e: Exception) {
            Log.w(TAG, "Keystore encryption failed; falling back safely", e)
            false
        }
    }

    private fun readSecret(key: String): String {
        val raw = securePrefs.getString(key, null)
        if (raw.isNullOrEmpty()) return ""
        return when {
            raw.startsWith(PREFIX_GCM) -> {
                try {
                    decrypt(raw)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to decrypt $key", e)
                    ""
                }
            }
            raw.startsWith(PREFIX_PLAIN_B64) -> {
                try {
                    val decoded = Base64.decode(raw.removePrefix(PREFIX_PLAIN_B64), Base64.NO_WRAP)
                    String(decoded, StandardCharsets.UTF_8)
                } catch (_: Exception) {
                    ""
                }
            }
            else -> raw
        }
    }

    private fun encodeFallback(value: String): String {
        if (value.isEmpty()) return ""
        val encoded = Base64.encodeToString(value.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        return "$PREFIX_PLAIN_B64$encoded"
    }

    private fun encrypt(plainText: String): String {
        val key = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val combined = ByteArray(1 + iv.size + cipherBytes.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)
        return PREFIX_GCM + Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val payload = Base64.decode(encoded.removePrefix(PREFIX_GCM), Base64.NO_WRAP)
        if (payload.size < 2) return ""
        val ivLen = payload[0].toInt() and 0xFF
        if (ivLen <= 0 || payload.size <= 1 + ivLen) return ""
        val iv = payload.copyOfRange(1, 1 + ivLen)
        val cipherBytes = payload.copyOfRange(1 + ivLen, payload.size)
        val key = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, StandardCharsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        private const val TAG = "SecureTokenStore"
        private const val PREFS_NAME = "telenoti_secure"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "telenoti_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val PREFIX_GCM = "gcm:"
        private const val PREFIX_PLAIN_B64 = "fb:"

        const val KEY_BOT_TOKEN = "botToken"
        const val KEY_CHAT_ID = "chatId"
    }
}
