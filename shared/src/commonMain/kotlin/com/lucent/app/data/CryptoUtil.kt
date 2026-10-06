package com.lucent.app.data

import kotlin.io.encoding.Base64

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
        return pbkdf2Sha256(PASSPHRASE, salt, PBKDF2_ITERATIONS, KEY_BITS)
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val salt = secureRandomBytes(SALT_LENGTH)
        val iv = secureRandomBytes(IV_LENGTH)
        val keyBytes = deriveKeyBytes(salt)
        val plain = plainText.toByteArray(Charsets.UTF_8)
        val encrypted = com.lucent.app.nativebridge.LucentNative
            .aesGcmSeal(keyBytes, iv, ByteArray(0), plain)
            ?: run {
                aesGcmEncrypt(keyBytes, iv, plain)
            }
        return Base64.encode(salt + iv + encrypted)
    }

    fun decrypt(cipherText: String): String {
        if (cipherText.isEmpty()) return ""
        return try {
            val combined = Base64.decode(cipherText)
            if (combined.size <= SALT_LENGTH + IV_LENGTH) return ""
            val salt = combined.copyOfRange(0, SALT_LENGTH)
            val iv = combined.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
            val encrypted = combined.copyOfRange(SALT_LENGTH + IV_LENGTH, combined.size)
            val keyBytes = deriveKeyBytes(salt)
            val plain = com.lucent.app.nativebridge.LucentNative
                .aesGcmOpen(keyBytes, iv, ByteArray(0), encrypted)
                ?: run {
                    aesGcmDecrypt(keyBytes, iv, encrypted)
                }
            String(plain, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}
