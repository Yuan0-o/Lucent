package com.lucent.app.data

import okio.Buffer
import okio.EOFException
import okio.IOException
import okio.Path
import okio.Sink
import okio.Source
import okio.buffer
import okio.FileSystem

object FileCrypto {

    private val MAGIC = "LCNTCRY1".encodeToByteArray()
    private const val VERSION: Byte = 1

    private const val NONCE_PREFIX_LEN = 8
    private const val GCM_TAG_BITS = 128
    private const val GCM_TAG_BYTES = 16

    const val CHUNK = 64 * 1024

    private const val HEADER_LEN = 8 + 1 + NONCE_PREFIX_LEN
    private const val FRAME_HEADER_LEN = 1 + 4

    fun isEncrypted(fileSystem: FileSystem, path: Path): Boolean {
        if (!fileSystem.exists(path)) return false
        val metadata = fileSystem.metadataOrNull(path)
        if (metadata == null || (metadata.size ?: 0L) < HEADER_LEN) return false
        return try {
            fileSystem.source(path).buffer().use { source ->
                val head = source.readByteArray(MAGIC.size + 1.toLong())
                head.copyOfRange(0, MAGIC.size).contentEquals(MAGIC) && head[MAGIC.size] == VERSION
            }
        } catch (t: Throwable) {
            false
        }
    }

    fun plaintextSizeOf(fileSystem: FileSystem, path: Path): Long {
        val metadata = fileSystem.metadataOrNull(path) ?: return 0L
        val total = metadata.size ?: 0L
        if (total <= HEADER_LEN) return 0
        val body = total - HEADER_LEN
        val perFrame = (FRAME_HEADER_LEN + GCM_TAG_BYTES).toLong()
        val frames = ((body + CHUNK + perFrame - 1) / (CHUNK + perFrame)).coerceAtLeast(1)
        return (body - frames * perFrame).coerceAtLeast(0)
    }

    fun encryptingSink(sink: Sink, key: ByteArray): Sink {
        val noncePrefix = secureRandomBytes(NONCE_PREFIX_LEN)
        val buffered = sink.buffer()
        buffered.write(MAGIC)
        buffered.writeByte(VERSION.toInt())
        buffered.write(noncePrefix)
        buffered.flush()
        return EncryptingSink(sink, key, noncePrefix)
    }

    fun decryptingSource(source: Source, key: ByteArray): Source {
        val buffered = source.buffer()
        val head = buffered.readByteArray(HEADER_LEN.toLong())
        if (!head.copyOfRange(0, MAGIC.size).contentEquals(MAGIC) || head[MAGIC.size] != VERSION) {
            throw IOException("Not a Lucent-encrypted stream")
        }
        val noncePrefix = head.copyOfRange(MAGIC.size + 1, HEADER_LEN)
        return DecryptingSource(buffered, key, noncePrefix)
    }

    fun encrypt(plain: ByteArray, key: ByteArray): ByteArray {
        val buffer = Buffer()
        encryptingSink(buffer, key).buffer().use { it.write(plain) }
        return buffer.readByteArray()
    }

    fun decrypt(cipherText: ByteArray, key: ByteArray): ByteArray =
        decryptingSource(Buffer().write(cipherText), key).buffer().use { it.readByteArray() }

    private fun nonceFor(prefix: ByteArray, counter: Int): ByteArray {
        val nonce = ByteArray(12)
        prefix.copyInto(nonce, 0, 0, NONCE_PREFIX_LEN)
        nonce[8] = (counter ushr 24).toByte()
        nonce[9] = (counter ushr 16).toByte()
        nonce[10] = (counter ushr 8).toByte()
        nonce[11] = counter.toByte()
        return nonce
    }

    private fun aadFor(counter: Int, isFinal: Boolean) = byteArrayOf(
        if (isFinal) 1 else 0,
        (counter ushr 24).toByte(),
        (counter ushr 16).toByte(),
        (counter ushr 8).toByte(),
        counter.toByte()
    )

    private class EncryptingSink(
        private val sink: Sink,
        private val key: ByteArray,
        private val noncePrefix: ByteArray
    ) : Sink {
        private val buffer = Buffer()
        private var counter = 0
        private var closed = false

        override fun write(source: Buffer, byteCount: Long) {
            buffer.write(source, byteCount)
            while (buffer.size >= CHUNK) {
                writeFrame(isFinal = false, take = CHUNK)
            }
        }

        private fun writeFrame(isFinal: Boolean, take: Int) {
            val plain = buffer.readByteArray(take.toLong())
            val aad = aadFor(counter, isFinal)
            val nonce = nonceFor(noncePrefix, counter)
            
            val sealed = aesGcmEncrypt(key, nonce, plain, aad)

            val frameHeader = Buffer()
            frameHeader.writeByte(if (isFinal) 1 else 0)
            frameHeader.writeInt(sealed.size)
            frameHeader.write(sealed)

            sink.write(frameHeader, frameHeader.size)
            counter++
        }

        override fun flush() {
            sink.flush()
        }

        override fun timeout() = sink.timeout()

        override fun close() {
            if (closed) return
            closed = true
            writeFrame(isFinal = true, take = buffer.size.toInt())
            sink.flush()
            sink.close()
        }
    }

    private class DecryptingSource(
        private val source: Source,
        private val key: ByteArray,
        private val noncePrefix: ByteArray
    ) : Source {
        private val plainBuffer = Buffer()
        private val bufferedSource = source.buffer()
        private var counter = 0
        private var sawFinal = false

        override fun read(sink: Buffer, byteCount: Long): Long {
            if (byteCount == 0L) return 0L
            if (plainBuffer.size == 0L) {
                if (sawFinal) return -1L
                if (!readFrame()) return -1L
                if (plainBuffer.size == 0L) return -1L
            }
            val take = minOf(byteCount, plainBuffer.size)
            sink.write(plainBuffer, take)
            return take
        }

        private fun readFrame(): Boolean {
            if (bufferedSource.exhausted()) {
                throw EOFException("Encrypted stream is truncated — no end-of-stream frame")
            }
            val flag = bufferedSource.readByte().toInt()
            val isFinal = flag == 1

            val length = bufferedSource.readInt()
            if (length < GCM_TAG_BYTES || length > CHUNK + GCM_TAG_BYTES) {
                throw IOException("Encrypted stream is corrupt — implausible frame length")
            }

            val sealed = bufferedSource.readByteArray(length.toLong())

            val plain = try {
                aesGcmDecrypt(key, nonceFor(noncePrefix, counter), sealed, aadFor(counter, isFinal))
            } catch (t: Throwable) {
                throw IOException("Could not decrypt — wrong key, or the file has been altered", t)
            }
            
            plainBuffer.write(plain)
            counter++
            sawFinal = isFinal
            return true
        }

        override fun timeout() = source.timeout()

        override fun close() {
            source.close()
            plainBuffer.clear()
        }
    }
}
