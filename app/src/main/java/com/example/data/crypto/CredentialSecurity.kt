package com.example.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object CredentialSecurity {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "WifiManagerCredentialKey_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

    private fun encodeBase64(bytes: ByteArray): String {
        return try {
            java.util.Base64.getEncoder().encodeToString(bytes)
        } catch (_: Throwable) {
            try {
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            } catch (_: Throwable) {
                ""
            }
        }
    }

    private fun decodeBase64(str: String): ByteArray {
        return try {
            java.util.Base64.getDecoder().decode(str.trim())
        } catch (_: Throwable) {
            try {
                android.util.Base64.decode(str.trim(), android.util.Base64.NO_WRAP)
            } catch (_: Throwable) {
                ByteArray(0)
            }
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
            } else {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
        } catch (_: Throwable) {
            // Fallback for JVM tests or environments without AndroidKeyStore
            getFallbackKey()
        }
    }

    private var fallbackKeyCache: SecretKey? = null
    private fun getFallbackKey(): SecretKey {
        return fallbackKeyCache ?: run {
            val kg = KeyGenerator.getInstance("AES")
            kg.init(256)
            val key = kg.generateKey()
            fallbackKeyCache = key
            key
        }
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val key = getOrCreateSecretKey()
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
            encodeBase64(combined)
        } catch (_: Throwable) {
            encodeBase64(plainText.toByteArray(Charsets.UTF_8))
        }
    }

    fun decrypt(encryptedText: String): String {
        if (encryptedText.isEmpty()) return ""
        return try {
            val combined = decodeBase64(encryptedText)
            if (combined.size <= GCM_IV_LENGTH) {
                return String(combined, Charsets.UTF_8)
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val key = getOrCreateSecretKey()
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, combined, 0, GCM_IV_LENGTH)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            val plainBytes = cipher.doFinal(combined, GCM_IV_LENGTH, combined.size - GCM_IV_LENGTH)
            String(plainBytes, Charsets.UTF_8)
        } catch (_: Throwable) {
            try {
                val decoded = decodeBase64(encryptedText)
                String(decoded, Charsets.UTF_8)
            } catch (_: Throwable) {
                ""
            }
        }
    }
}
