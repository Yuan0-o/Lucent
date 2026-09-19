package com.lucent.app.nativebridge

object LucentNative {

    val available: Boolean = try {
        System.loadLibrary("lucent_native")
        true
    } catch (t: Throwable) {
        false
    }


    fun pbkdf2Sha256(password: ByteArray, salt: ByteArray, iterations: Int, keyLenBytes: Int): ByteArray? {
        if (!available) return null
        return try {
            nativePbkdf2Sha256(password, salt, iterations, keyLenBytes)
        } catch (t: Throwable) {
            null
        }
    }

    fun pbkdf2Sha256(password: CharArray, salt: ByteArray, iterations: Int, keyLenBytes: Int): ByteArray? =
        pbkdf2Sha256(String(password).toByteArray(Charsets.UTF_8), salt, iterations, keyLenBytes)

    fun aesGcmSeal(key: ByteArray, iv: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray? {
        if (!available) return null
        return try {
            nativeAesGcmSeal(key, iv, aad, plaintext)
        } catch (t: Throwable) {
            null
        }
    }

    fun aesGcmOpen(key: ByteArray, iv: ByteArray, aad: ByteArray, sealed: ByteArray): ByteArray? {
        if (!available) return null
        return try {
            nativeAesGcmOpen(key, iv, aad, sealed)
        } catch (t: Throwable) {
            null
        }
    }


    fun blobFrame(tMs: Float, width: Float, height: Float, out: FloatArray): Boolean {
        if (!available || out.size < 36) return false
        return try {
            nativeBlobFrame(tMs, width, height, out)
        } catch (t: Throwable) {
            false
        }
    }

    private external fun nativePbkdf2Sha256(password: ByteArray, salt: ByteArray, iterations: Int, keyLen: Int): ByteArray?
    private external fun nativeAesGcmSeal(key: ByteArray, iv: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray?
    private external fun nativeAesGcmOpen(key: ByteArray, iv: ByteArray, aad: ByteArray, sealed: ByteArray): ByteArray?
    private external fun nativeBlobFrame(tMs: Float, width: Float, height: Float, out: FloatArray): Boolean
}
