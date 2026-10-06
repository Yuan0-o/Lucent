package com.lucent.app.data

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

actual fun secureRandomBytes(size: Int): ByteArray {
    val bytes = ByteArray(size)
    SecureRandom().nextBytes(bytes)
    return bytes
}

actual fun pbkdf2Sha256(
    password: CharArray,
    salt: ByteArray,
    iterations: Int,
    keyLengthBits: Int
): ByteArray {
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec = PBEKeySpec(password, salt, iterations, keyLengthBits)
    return factory.generateSecret(spec).encoded
}

actual fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray, aad: ByteArray?): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val secretKeySpec = SecretKeySpec(key, "AES")
    val gcmParameterSpec = GCMParameterSpec(128, iv)
    cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmParameterSpec)
    if (aad != null) cipher.updateAAD(aad)
    return cipher.doFinal(plaintext)
}

actual fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray, aad: ByteArray?): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val secretKeySpec = SecretKeySpec(key, "AES")
    val gcmParameterSpec = GCMParameterSpec(128, iv)
    cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, gcmParameterSpec)
    if (aad != null) cipher.updateAAD(aad)
    return cipher.doFinal(ciphertext)
}

actual fun sha256(data: ByteArray): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(data)
}

actual fun aesGcmEncryptingSink(sink: okio.Sink, key: ByteArray, iv: ByteArray): okio.Sink {
    return object : okio.ForwardingSink(sink) {
        private val buffer = okio.Buffer()
        private var closed = false

        override fun write(source: okio.Buffer, byteCount: Long) {
            buffer.write(source, byteCount)
        }

        override fun close() {
            if (closed) return
            closed = true
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val secretKeySpec = SecretKeySpec(key, "AES")
                val gcmParameterSpec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmParameterSpec)
                val ciphertext = cipher.doFinal(buffer.readByteArray())
                super.write(okio.Buffer().write(ciphertext), ciphertext.size.toLong())
            } finally {
                super.close()
            }
        }
    }
}

actual fun aesGcmDecryptingSource(source: okio.Source, key: ByteArray, iv: ByteArray): okio.Source {
    return object : okio.ForwardingSource(source) {
        private var decryptedBuffer: okio.Buffer? = null

        override fun read(sink: okio.Buffer, byteCount: Long): Long {
            if (decryptedBuffer == null) {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val secretKeySpec = SecretKeySpec(key, "AES")
                val gcmParameterSpec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, gcmParameterSpec)
                
                val ciphertext = okio.Buffer()
                ciphertext.writeAll(delegate)
                
                val plaintext = cipher.doFinal(ciphertext.readByteArray())
                decryptedBuffer = okio.Buffer().write(plaintext)
            }
            return decryptedBuffer!!.read(sink, byteCount)
        }
    }
}
