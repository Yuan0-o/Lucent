package com.lucent.app.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCrypto {

    private val MAGIC = "LCNTBAK1".toByteArray(Charsets.US_ASCII)

    private const val VERSION: Byte = 1
    private const val VERSION_WITH_RECOVERY: Byte = 2

    private const val SALT_LEN = 16
    private const val KEY_BITS = 256
    private const val HEADER_LEN = 8 + 1 + 1 + 4 + SALT_LEN

    const val PASSWORD_ITERATIONS = 210_000

    private const val APP_KEY_ITERATIONS = 10_000

    private val APP_PASSPHRASE = "Lucent-backup-passphrase-v2".toCharArray()

    private val random = SecureRandom()

    enum class Mode(val id: Byte) {
        APP_KEY(0),
        PASSWORD(1);

        companion object {
            fun fromId(id: Byte): Mode? = entries.firstOrNull { it.id == id }
        }
    }

    data class Header(
        val mode: Mode,
        val iterations: Int,
        val salt: ByteArray,
        val recovery: BackupRecovery.Envelope? = null,
        val byteLength: Int = HEADER_LEN
    ) {
        val needsPassword: Boolean get() = mode == Mode.PASSWORD

        val hasRecovery: Boolean get() = needsPassword && recovery != null

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Header) return false
            return mode == other.mode && iterations == other.iterations &&
                salt.contentEquals(other.salt) && recovery == other.recovery &&
                byteLength == other.byteLength
        }

        override fun hashCode(): Int {
            var h = (mode.hashCode() * 31 + iterations) * 31 + salt.contentHashCode()
            h = h * 31 + (recovery?.hashCode() ?: 0)
            h = h * 31 + byteLength
            return h
        }
    }


    fun looksEncrypted(bytes: ByteArray): Boolean =
        bytes.size >= HEADER_LEN && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun readHeader(bytes: ByteArray): Header? {
        if (!looksEncrypted(bytes)) return null
        val version = bytes[MAGIC.size]
        if (version != VERSION && version != VERSION_WITH_RECOVERY) return null
        val mode = Mode.fromId(bytes[MAGIC.size + 1]) ?: return null
        var p = MAGIC.size + 2
        val iterations = ((bytes[p].toInt() and 0xFF) shl 24) or
            ((bytes[p + 1].toInt() and 0xFF) shl 16) or
            ((bytes[p + 2].toInt() and 0xFF) shl 8) or
            (bytes[p + 3].toInt() and 0xFF)
        p += 4
        if (iterations <= 0 || bytes.size < p + SALT_LEN) return null
        val salt = bytes.copyOfRange(p, p + SALT_LEN)
        p += SALT_LEN
        if (version == VERSION) return Header(mode, iterations, salt)

        if (bytes.size < p + 4) return null
        val envLen = ((bytes[p].toInt() and 0xFF) shl 24) or
            ((bytes[p + 1].toInt() and 0xFF) shl 16) or
            ((bytes[p + 2].toInt() and 0xFF) shl 8) or
            (bytes[p + 3].toInt() and 0xFF)
        p += 4
        if (envLen < 0 || bytes.size < p + envLen) return null
        val envelope = if (envLen == 0) null else BackupRecovery.fromJson(
            String(bytes.copyOfRange(p, p + envLen), Charsets.UTF_8)
        )
        return Header(mode, iterations, salt, envelope, p + envLen)
    }


    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        com.lucent.app.nativebridge.LucentNative
            .pbkdf2Sha256(passphrase, salt, iterations, KEY_BITS / 8)
            ?.let { return SecretKeySpec(it, "AES") }
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun keyFor(header: Header, password: String?): SecretKey = when (header.mode) {
        Mode.APP_KEY -> deriveKey(APP_PASSPHRASE, header.salt, header.iterations)
        Mode.PASSWORD -> {
            require(!password.isNullOrEmpty()) { "This backup needs a password" }
            deriveKey(password.toCharArray(), header.salt, header.iterations)
        }
    }


    fun encryptingStream(
        out: OutputStream,
        password: String?,
        recovery: BackupRecovery.Envelope? = null
    ): OutputStream {
        val usePassword = !password.isNullOrEmpty()
        val mode = if (usePassword) Mode.PASSWORD else Mode.APP_KEY
        val iterations = if (usePassword) PASSWORD_ITERATIONS else APP_KEY_ITERATIONS
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val envelope = if (usePassword) recovery else null
        val envelopeBytes = envelope
            ?.let { BackupRecovery.toJson(it).toByteArray(Charsets.UTF_8) }

        out.write(MAGIC)
        out.write(if (envelopeBytes != null) VERSION_WITH_RECOVERY.toInt() else VERSION.toInt())
        out.write(mode.id.toInt())
        out.write(iterations ushr 24)
        out.write(iterations ushr 16)
        out.write(iterations ushr 8)
        out.write(iterations)
        out.write(salt)
        if (envelopeBytes != null) {
            val n = envelopeBytes.size
            out.write(n ushr 24); out.write(n ushr 16); out.write(n ushr 8); out.write(n)
            out.write(envelopeBytes)
        }

        val key = keyFor(Header(mode, iterations, salt), password)
        return FileCrypto.encryptingStream(out, key)
    }

    fun encrypt(
        payload: ByteArray,
        password: String?,
        recovery: BackupRecovery.Envelope? = null
    ): ByteArray {
        val buffer = ByteArrayOutputStream(payload.size + 128)
        encryptingStream(buffer, password, recovery).use { it.write(payload) }
        return buffer.toByteArray()
    }


    fun decrypt(bytes: ByteArray, password: String?): ByteArray {
        val header = readHeader(bytes) ?: throw IOException("Not a Lucent backup")
        if (header.needsPassword && password.isNullOrEmpty()) throw WrongPasswordException()

        val key = keyFor(header, password)
        val body = bytes.inputStream()
        var skipped = 0L
        while (skipped < header.byteLength) {
            val n = body.skip(header.byteLength - skipped)
            if (n <= 0) throw IOException("Backup file is truncated")
            skipped += n
        }

        return try {
            FileCrypto.decryptingStream(body, key).use { it.readBytes() }
        } catch (t: Throwable) {
            if (header.needsPassword) throw WrongPasswordException() else throw IOException("Backup file is damaged", t)
        }
    }

    fun decryptingStream(input: InputStream, header: Header, password: String?): InputStream {
        if (header.needsPassword && password.isNullOrEmpty()) throw WrongPasswordException()
        return FileCrypto.decryptingStream(input, keyFor(header, password))
    }

    class WrongPasswordException : IOException("Wrong backup password")
}
