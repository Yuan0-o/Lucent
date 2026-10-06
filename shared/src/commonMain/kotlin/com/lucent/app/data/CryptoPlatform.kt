package com.lucent.app.data

import okio.Sink
import okio.Source

expect fun secureRandomBytes(size: Int): ByteArray
expect fun pbkdf2Sha256(password: CharArray, salt: ByteArray, iterations: Int, keyLengthBits: Int): ByteArray
expect fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray, aad: ByteArray? = null): ByteArray
expect fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray, aad: ByteArray? = null): ByteArray
expect fun sha256(data: ByteArray): ByteArray

expect fun aesGcmEncryptingSink(sink: Sink, key: ByteArray, iv: ByteArray): Sink
expect fun aesGcmDecryptingSource(source: Source, key: ByteArray, iv: ByteArray): Source
