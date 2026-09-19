package com.lucent.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object RecoverableSecret {

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val GCM_TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val PBKDF2_ITERATIONS = 120_000

    private const val PEPPER = "Lucent-recovery-pepper-v1"

    private val random = SecureRandom()

    @SuppressLint("HardwareIds")
    private fun deviceMaterial(context: Context): CharArray {
        val androidId = try {
            Settings.Secure.getString(context.applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (t: Throwable) {
            null
        }
        val id = androidId?.takeIf { it.isNotBlank() } ?: "lucent-no-android-id"
        return material(id)
    }

    internal fun material(deviceId: String): CharArray = (PEPPER + '|' + deviceId).toCharArray()

    private fun deriveKey(passwordMaterial: CharArray, salt: ByteArray): SecretKey {
        com.lucent.app.nativebridge.LucentNative
            .pbkdf2Sha256(passwordMaterial, salt, PBKDF2_ITERATIONS, KEY_BITS / 8)
            ?.let { return SecretKeySpec(it, "AES") }
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passwordMaterial, salt, PBKDF2_ITERATIONS, KEY_BITS)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    internal fun seal(passwordMaterial: CharArray, plain: ByteArray): ByteArray? {
        return try {
            val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
            val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passwordMaterial, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
            val sealed = cipher.doFinal(plain)
            salt + iv + sealed
        } catch (t: Throwable) {
            null
        }
    }

    internal fun open(passwordMaterial: CharArray, blob: ByteArray): ByteArray? {
        if (blob.size <= SALT_LEN + IV_LEN) return null
        return try {
            val salt = blob.copyOfRange(0, SALT_LEN)
            val iv = blob.copyOfRange(SALT_LEN, SALT_LEN + IV_LEN)
            val sealed = blob.copyOfRange(SALT_LEN + IV_LEN, blob.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(passwordMaterial, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(sealed)
        } catch (t: Throwable) {
            null
        }
    }

    fun encrypt(context: Context, plainBase64: String): String {
        if (plainBase64.isEmpty()) return ""
        val blob = seal(deviceMaterial(context), plainBase64.toByteArray(Charsets.UTF_8)) ?: return ""
        return Base64.encodeToString(blob, Base64.NO_WRAP)
    }

    fun decrypt(context: Context, stored: String): String {
        if (stored.isEmpty()) return ""
        return try {
            val blob = Base64.decode(stored, Base64.NO_WRAP)
            val plain = open(deviceMaterial(context), blob) ?: return ""
            String(plain, Charsets.UTF_8)
        } catch (t: Throwable) {
            ""
        }
    }

    fun canDecrypt(context: Context, stored: String): Boolean = decrypt(context, stored).isNotEmpty()
}
