package com.lucent.app.nativebridge

expect object LucentNative {
    val available: Boolean
    fun pbkdf2Sha256(password: ByteArray, salt: ByteArray, iterations: Int, keyLenBytes: Int): ByteArray?
    fun pbkdf2Sha256(password: CharArray, salt: ByteArray, iterations: Int, keyLenBytes: Int): ByteArray?
    fun aesGcmSeal(key: ByteArray, iv: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray?
    fun aesGcmOpen(key: ByteArray, iv: ByteArray, aad: ByteArray, sealed: ByteArray): ByteArray?
    fun blobFrame(tMs: Float, width: Float, height: Float, out: FloatArray): Boolean
}
