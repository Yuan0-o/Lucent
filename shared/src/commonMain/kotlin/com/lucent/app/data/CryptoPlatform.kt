package com.lucent.app.data

expect fun secureRandomBytes(size: Int): ByteArray
expect fun pbkdf2Sha256(password: CharArray, salt: ByteArray, iterations: Int, keyLengthBits: Int): ByteArray
expect fun aesGcmEncrypt(key: ByteArray, iv: ByteArray, plaintext: ByteArray): ByteArray
expect fun aesGcmDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray
expect fun sha256(data: ByteArray): ByteArray
