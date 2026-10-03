package com.lucent.app.data

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object FileCrypto {

    private val MAGIC = "LCNTCRY1".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1

    private const val NONCE_PREFIX_LEN = 8
    private const val GCM_TAG_BITS = 128
    private const val GCM_TAG_BYTES = 16

    const val CHUNK = 64 * 1024

    private const val HEADER_LEN = 8 + 1 + NONCE_PREFIX_LEN
    private const val FRAME_HEADER_LEN = 1 + 4

    private val random = SecureRandom()


    fun isEncrypted(file: File): Boolean {
        if (!file.exists() || file.length() < HEADER_LEN) return false
        return try {
            file.inputStream().use { input ->
                val head = ByteArray(MAGIC.size + 1)
                if (input.read(head) != head.size) return false
                head.copyOfRange(0, MAGIC.size).contentEquals(MAGIC) && head[MAGIC.size] == VERSION
            }
        } catch (t: Throwable) {
            false
        }
    }

    fun plaintextSizeOf(file: File): Long {
        val total = file.length()
        if (total <= HEADER_LEN) return 0
        val body = total - HEADER_LEN
        val perFrame = (FRAME_HEADER_LEN + GCM_TAG_BYTES).toLong()
        val frames = ((body + CHUNK + perFrame - 1) / (CHUNK + perFrame)).coerceAtLeast(1)
        return (body - frames * perFrame).coerceAtLeast(0)
    }


    fun encryptingStream(out: OutputStream, key: SecretKey): OutputStream {
        val noncePrefix = ByteArray(NONCE_PREFIX_LEN).also { random.nextBytes(it) }
        out.write(MAGIC)
        out.write(VERSION.toInt())
        out.write(noncePrefix)
        return EncryptingOutputStream(out, key, noncePrefix)
    }

    fun decryptingStream(input: InputStream, key: SecretKey): InputStream {
        val head = ByteArray(HEADER_LEN)
        readFully(input, head, head.size)
        if (!head.copyOfRange(0, MAGIC.size).contentEquals(MAGIC) || head[MAGIC.size] != VERSION) {
            throw IOException("Not a Lucent-encrypted stream")
        }
        val noncePrefix = head.copyOfRange(MAGIC.size + 1, HEADER_LEN)
        return DecryptingInputStream(input, key, noncePrefix)
    }


    fun encrypt(plain: ByteArray, key: SecretKey): ByteArray {
        val buffer = ByteArrayOutputStream(plain.size + 64)
        encryptingStream(buffer, key).use { it.write(plain) }
        return buffer.toByteArray()
    }

    fun decrypt(cipherText: ByteArray, key: SecretKey): ByteArray =
        decryptingStream(cipherText.inputStream(), key).use { it.readBytes() }


    private fun nonceFor(prefix: ByteArray, counter: Int): ByteArray {
        val nonce = ByteArray(12)
        System.arraycopy(prefix, 0, nonce, 0, NONCE_PREFIX_LEN)
        nonce[8] = (counter ushr 24).toByte()
        nonce[9] = (counter ushr 16).toByte()
        nonce[10] = (counter ushr 8).toByte()
        nonce[11] = counter.toByte()
        return nonce
    }


    private fun sealFrame(key: SecretKey, nonce: ByteArray, aad: ByteArray, plain: ByteArray, len: Int): ByteArray {
        key.encoded?.let { raw ->
            val exact = if (len == plain.size) plain else plain.copyOfRange(0, len)
            com.lucent.app.nativebridge.LucentNative.aesGcmSeal(raw, nonce, aad, exact)?.let { return it }
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(plain, 0, len)
    }

    private fun openFrame(key: SecretKey, nonce: ByteArray, aad: ByteArray, sealed: ByteArray): ByteArray {
        key.encoded?.let { raw ->
            com.lucent.app.nativebridge.LucentNative.aesGcmOpen(raw, nonce, aad, sealed)?.let { return it }
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(sealed)
    }

    private fun aadFor(counter: Int, isFinal: Boolean) = byteArrayOf(
        if (isFinal) 1 else 0,
        (counter ushr 24).toByte(),
        (counter ushr 16).toByte(),
        (counter ushr 8).toByte(),
        counter.toByte()
    )

    private fun readFully(input: InputStream, buffer: ByteArray, len: Int) {
        var read = 0
        while (read < len) {
            val n = input.read(buffer, read, len - read)
            if (n < 0) throw EOFException("Encrypted stream ended early — the file is truncated")
            read += n
        }
    }

    private class EncryptingOutputStream(
        out: OutputStream,
        private val key: SecretKey,
        private val noncePrefix: ByteArray
    ) : FilterOutputStream(out) {

        private val buffer = ByteArray(CHUNK)
        private var filled = 0
        private var counter = 0
        private var closed = false

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            var offset = off
            var remaining = len
            while (remaining > 0) {
                val take = minOf(remaining, CHUNK - filled)
                System.arraycopy(b, offset, buffer, filled, take)
                filled += take
                offset += take
                remaining -= take
                if (filled == CHUNK) writeFrame(isFinal = false)
            }
        }

        private fun writeFrame(isFinal: Boolean) {
            val sealed = sealFrame(key, nonceFor(noncePrefix, counter), aadFor(counter, isFinal), buffer, filled)

            out.write(if (isFinal) 1 else 0)
            out.write(sealed.size ushr 24)
            out.write(sealed.size ushr 16)
            out.write(sealed.size ushr 8)
            out.write(sealed.size)
            out.write(sealed)

            filled = 0
            counter++
        }

        override fun close() {
            if (closed) return
            closed = true
            writeFrame(isFinal = true)
            out.flush()
            out.close()
        }
    }

    private class DecryptingInputStream(
        input: InputStream,
        private val key: SecretKey,
        private val noncePrefix: ByteArray
    ) : FilterInputStream(input) {

        private var plain = ByteArray(0)
        private var offset = 0
        private var counter = 0
        private var sawFinal = false

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (offset >= plain.size) {
                if (sawFinal) return -1
                if (!readFrame()) return -1
                if (plain.isEmpty()) return -1
            }
            val take = minOf(len, plain.size - offset)
            System.arraycopy(plain, offset, b, off, take)
            offset += take
            return take
        }

        private fun readFrame(): Boolean {
            val flag = `in`.read()
            if (flag < 0) {
                throw IOException("Encrypted stream is truncated — no end-of-stream frame")
            }
            val isFinal = flag == 1

            val lenBytes = ByteArray(4)
            readFully(`in`, lenBytes, 4)
            val length = ((lenBytes[0].toInt() and 0xFF) shl 24) or
                ((lenBytes[1].toInt() and 0xFF) shl 16) or
                ((lenBytes[2].toInt() and 0xFF) shl 8) or
                (lenBytes[3].toInt() and 0xFF)
            if (length < GCM_TAG_BYTES || length > CHUNK + GCM_TAG_BYTES) {
                throw IOException("Encrypted stream is corrupt — implausible frame length")
            }

            val sealed = ByteArray(length)
            readFully(`in`, sealed, length)

            plain = try {
                openFrame(key, nonceFor(noncePrefix, counter), aadFor(counter, isFinal), sealed)
            } catch (t: Throwable) {
                throw IOException("Could not decrypt — wrong key, or the file has been altered", t)
            }
            offset = 0
            counter++
            sawFinal = isFinal
            return true
        }
    }

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
}
