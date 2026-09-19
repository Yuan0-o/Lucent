package com.lucent.app.data

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtil {

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 10_000
    private const val KEY_BITS = 256
    private val PASSPHRASE = "Lucent-backup-passphrase-v1".toCharArray()

    private fun deriveKeyBytes(salt: ByteArray): ByteArray {
        com.lucent.app.nativebridge.LucentNative
            .pbkdf2Sha256(PASSPHRASE, salt, PBKDF2_ITERATIONS, KEY_BITS / 8)
            ?.let { return it }
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(PASSPHRASE, salt, PBKDF2_ITERATIONS, KEY_BITS)
        return factory.generateSecret(spec).encoded
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { random.nextBytes(it) }
        val keyBytes = deriveKeyBytes(salt)
        val plain = plainText.toByteArray(Charsets.UTF_8)
        val encrypted = com.lucent.app.nativebridge.LucentNative
            .aesGcmSeal(keyBytes, iv, ByteArray(0), plain)
            ?: run {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
                cipher.doFinal(plain)
            }
        return Base64.encodeToString(salt + iv + encrypted, Base64.NO_WRAP)
    }

    fun decrypt(cipherText: String): String {
        if (cipherText.isEmpty()) return ""
        return try {
            val combined = Base64.decode(cipherText, Base64.NO_WRAP)
            if (combined.size <= SALT_LENGTH + IV_LENGTH) return ""
            val salt = combined.copyOfRange(0, SALT_LENGTH)
            val iv = combined.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
            val encrypted = combined.copyOfRange(SALT_LENGTH + IV_LENGTH, combined.size)
            val keyBytes = deriveKeyBytes(salt)
            val plain = com.lucent.app.nativebridge.LucentNative
                .aesGcmOpen(keyBytes, iv, ByteArray(0), encrypted)
                ?: run {
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
                    cipher.doFinal(encrypted)
                }
            String(plain, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}
