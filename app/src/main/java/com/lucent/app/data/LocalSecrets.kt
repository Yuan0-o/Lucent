package com.lucent.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object LocalSecrets {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "lucent_local_secret_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128

    private const val PREFIX_KEYSTORE = "k1:"
    private const val PREFIX_PORTABLE = "p1:"

    private fun secretKey(): SecretKey? = try {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        existing ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
        }.generateKey()
    } catch (t: Throwable) {
        null
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val key = secretKey() ?: return PREFIX_PORTABLE + CryptoUtil.encrypt(plainText)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            PREFIX_KEYSTORE + Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
        } catch (t: Throwable) {
            PREFIX_PORTABLE + CryptoUtil.encrypt(plainText)
        }
    }

    fun decrypt(stored: String): String {
        if (stored.isEmpty()) return ""
        return when {
            stored.startsWith(PREFIX_KEYSTORE) -> {
                val key = secretKey() ?: return ""
                try {
                    val combined = Base64.decode(stored.removePrefix(PREFIX_KEYSTORE), Base64.NO_WRAP)
                    if (combined.size <= IV_LENGTH) return ""
                    val iv = combined.copyOfRange(0, IV_LENGTH)
                    val encrypted = combined.copyOfRange(IV_LENGTH, combined.size)
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                    String(cipher.doFinal(encrypted), Charsets.UTF_8)
                } catch (t: Throwable) {
                    ""
                }
            }
            stored.startsWith(PREFIX_PORTABLE) -> CryptoUtil.decrypt(stored.removePrefix(PREFIX_PORTABLE))
            else -> stored
        }
    }
}
