package com.lucent.app.data

import okio.Buffer
import okio.IOException
import okio.Sink
import okio.Source
import okio.buffer

object BackupCrypto {

    private val MAGIC = "LCNTBAK1".encodeToByteArray()

    private const val VERSION: Byte = 1
    private const val VERSION_WITH_RECOVERY: Byte = 2
    const val VERSION_MULTI_PASSWORD: Byte = 3

    private const val SALT_LEN = 16
    private const val KEY_BITS = 256
    private const val HEADER_LEN = 8 + 1 + 1 + 4 + SALT_LEN

    const val PASSWORD_ITERATIONS = 210_000

    private const val APP_KEY_ITERATIONS = 10_000

    private val APP_PASSPHRASE = "Lucent-backup-passphrase-v2".toCharArray()

    enum class Mode(val id: Byte) {
        APP_KEY(0),
        PASSWORD(1);

        companion object {
            fun fromId(id: Byte): Mode? = entries.firstOrNull { it.id == id }
        }
    }

    data class Slot(
        val salt: ByteArray,
        val iterations: Int,
        val iv: ByteArray,
        val wrapped: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Slot) return false
            return iterations == other.iterations &&
                salt.contentEquals(other.salt) &&
                iv.contentEquals(other.iv) &&
                wrapped.contentEquals(other.wrapped)
        }

        override fun hashCode(): Int {
            var h = iterations * 31 + salt.contentHashCode()
            h = h * 31 + iv.contentHashCode()
            h = h * 31 + wrapped.contentHashCode()
            return h
        }
    }

    data class Header(
        val mode: Mode,
        val iterations: Int,
        val salt: ByteArray,
        val recovery: BackupRecovery.Envelope? = null,
        val byteLength: Int = HEADER_LEN,
        val version: Byte = VERSION,
        val slots: List<Slot> = emptyList()
    ) {
        val needsPassword: Boolean get() = mode == Mode.PASSWORD

        val hasRecovery: Boolean get() = needsPassword && recovery != null

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Header) return false
            return mode == other.mode && iterations == other.iterations &&
                salt.contentEquals(other.salt) && recovery == other.recovery &&
                byteLength == other.byteLength && version == other.version &&
                slots == other.slots
        }

        override fun hashCode(): Int {
            var h = (mode.hashCode() * 31 + iterations) * 31 + salt.contentHashCode()
            h = h * 31 + (recovery?.hashCode() ?: 0)
            h = h * 31 + byteLength
            h = h * 31 + version
            h = h * 31 + slots.hashCode()
            return h
        }
    }


    fun looksEncrypted(bytes: ByteArray): Boolean =
        bytes.size >= HEADER_LEN && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun readHeader(bytes: ByteArray): Header? {
        if (!looksEncrypted(bytes)) return null
        val version = bytes[MAGIC.size]
        if (version != VERSION && version != VERSION_WITH_RECOVERY && version != VERSION_MULTI_PASSWORD) return null
        val mode = Mode.fromId(bytes[MAGIC.size + 1]) ?: return null
        if (version == VERSION_MULTI_PASSWORD) {
            if (mode != Mode.PASSWORD) return null
            if (bytes.size < MAGIC.size + 3) return null
            val slotCount = bytes[MAGIC.size + 2].toInt() and 0xFF
            if (slotCount !in 1..3) return null
            var p = MAGIC.size + 3
            val slots = mutableListOf<Slot>()
            for (i in 0 until slotCount) {
                if (bytes.size < p + SALT_LEN + 4 + 12 + 4) return null
                val salt = bytes.copyOfRange(p, p + SALT_LEN)
                p += SALT_LEN
                val iter = ((bytes[p].toInt() and 0xFF) shl 24) or
                    ((bytes[p + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[p + 2].toInt() and 0xFF) shl 8) or
                    (bytes[p + 3].toInt() and 0xFF)
                p += 4
                if (iter <= 0) return null
                val iv = bytes.copyOfRange(p, p + 12)
                p += 12
                val wrappedLen = ((bytes[p].toInt() and 0xFF) shl 24) or
                    ((bytes[p + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[p + 2].toInt() and 0xFF) shl 8) or
                    (bytes[p + 3].toInt() and 0xFF)
                p += 4
                if (wrappedLen <= 0 || bytes.size < p + wrappedLen) return null
                val wrapped = bytes.copyOfRange(p, p + wrappedLen)
                p += wrappedLen
                slots.add(Slot(salt, iter, iv, wrapped))
            }
            if (bytes.size < p + 4) return null
            val envLen = ((bytes[p].toInt() and 0xFF) shl 24) or
                ((bytes[p + 1].toInt() and 0xFF) shl 16) or
                ((bytes[p + 2].toInt() and 0xFF) shl 8) or
                (bytes[p + 3].toInt() and 0xFF)
            p += 4
            if (envLen < 0 || bytes.size < p + envLen) return null
            val envelope = if (envLen == 0) null else BackupRecovery.fromJson(
                bytes.copyOfRange(p, p + envLen).decodeToString()
            )
            val firstSlot = slots.first()
            return Header(
                mode = mode,
                iterations = firstSlot.iterations,
                salt = firstSlot.salt,
                recovery = envelope,
                byteLength = p + envLen,
                version = version,
                slots = slots
            )
        }
        var p = MAGIC.size + 2
        val iterations = ((bytes[p].toInt() and 0xFF) shl 24) or
            ((bytes[p + 1].toInt() and 0xFF) shl 16) or
            ((bytes[p + 2].toInt() and 0xFF) shl 8) or
            (bytes[p + 3].toInt() and 0xFF)
        p += 4
        if (iterations <= 0 || bytes.size < p + SALT_LEN) return null
        val salt = bytes.copyOfRange(p, p + SALT_LEN)
        p += SALT_LEN
        if (version == VERSION) return Header(mode, iterations, salt, version = version)

        if (bytes.size < p + 4) return null
        val envLen = ((bytes[p].toInt() and 0xFF) shl 24) or
            ((bytes[p + 1].toInt() and 0xFF) shl 16) or
            ((bytes[p + 2].toInt() and 0xFF) shl 8) or
            (bytes[p + 3].toInt() and 0xFF)
        p += 4
        if (envLen < 0 || bytes.size < p + envLen) return null
        val envelope = if (envLen == 0) null else BackupRecovery.fromJson(
            bytes.copyOfRange(p, p + envLen).decodeToString()
        )
        return Header(mode, iterations, salt, envelope, p + envLen, version = version)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        return pbkdf2Sha256(passphrase, salt, iterations, KEY_BITS)
    }

    private fun keyFor(header: Header, password: String?): ByteArray = when (header.mode) {
        Mode.APP_KEY -> deriveKey(APP_PASSPHRASE, header.salt, header.iterations)
        Mode.PASSWORD -> {
            require(!password.isNullOrEmpty()) { "This backup needs a password" }
            deriveKey(password.toCharArray(), header.salt, header.iterations)
        }
    }

    private fun unwrapDekWithPassword(header: Header, password: String): ByteArray? {
        for (slot in header.slots) {
            try {
                val kek = deriveKey(password.toCharArray(), slot.salt, slot.iterations)
                return aesGcmDecrypt(kek, slot.iv, slot.wrapped)
            } catch (_: Throwable) {
            }
        }
        return null
    }

    fun encryptingSink(
        sink: Sink,
        password: String?,
        recovery: BackupRecovery.Envelope? = null
    ): Sink {
        val usePassword = !password.isNullOrEmpty()
        val mode = if (usePassword) Mode.PASSWORD else Mode.APP_KEY
        val iterations = if (usePassword) PASSWORD_ITERATIONS else APP_KEY_ITERATIONS
        val salt = secureRandomBytes(SALT_LEN)
        val envelope = if (usePassword) recovery else null
        val envelopeBytes = envelope
            ?.let { BackupRecovery.toJson(it).encodeToByteArray() }

        val buffered = sink.buffer()
        buffered.write(MAGIC)
        buffered.writeByte(if (envelopeBytes != null) VERSION_WITH_RECOVERY.toInt() else VERSION.toInt())
        buffered.writeByte(mode.id.toInt())
        buffered.writeInt(iterations)
        buffered.write(salt)
        if (envelopeBytes != null) {
            buffered.writeInt(envelopeBytes.size)
            buffered.write(envelopeBytes)
        }
        buffered.flush()

        val key = keyFor(Header(mode, iterations, salt), password)
        return FileCrypto.encryptingSink(sink, key)
    }

    fun encrypt(
        payload: ByteArray,
        password: String?,
        recovery: BackupRecovery.Envelope? = null
    ): ByteArray {
        val buffer = Buffer()
        encryptingSink(buffer, password, recovery).buffer().use { it.write(payload) }
        return buffer.readByteArray()
    }

    fun encryptingSinkMultiPassword(
        sink: Sink,
        passwords: List<String>,
        recovery: BackupRecovery.Envelope? = null,
        dek: ByteArray = secureRandomBytes(32)
    ): Sink {
        require(passwords.isNotEmpty()) { "At least one password required" }
        val validPasswords = passwords.filter { it.isNotEmpty() }.take(3)
        require(validPasswords.isNotEmpty()) { "At least one non-empty password required" }

        val envelopeBytes = recovery?.let { BackupRecovery.toJson(it).encodeToByteArray() }

        val buffered = sink.buffer()
        buffered.write(MAGIC)
        buffered.writeByte(VERSION_MULTI_PASSWORD.toInt())
        buffered.writeByte(Mode.PASSWORD.id.toInt())
        buffered.writeByte(validPasswords.size)

        for (password in validPasswords) {
            val salt = secureRandomBytes(SALT_LEN)
            val iv = secureRandomBytes(12)
            val kek = deriveKey(password.toCharArray(), salt, PASSWORD_ITERATIONS)
            val wrapped = aesGcmEncrypt(kek, iv, dek)
            buffered.write(salt)
            buffered.writeInt(PASSWORD_ITERATIONS)
            buffered.write(iv)
            buffered.writeInt(wrapped.size)
            buffered.write(wrapped)
        }

        if (envelopeBytes != null) {
            buffered.writeInt(envelopeBytes.size)
            buffered.write(envelopeBytes)
        } else {
            buffered.writeInt(0)
        }
        buffered.flush()

        return FileCrypto.encryptingSink(sink, dek)
    }

    fun encryptMultiPassword(
        payload: ByteArray,
        passwords: List<String>,
        recovery: BackupRecovery.Envelope? = null,
        dek: ByteArray = secureRandomBytes(32)
    ): ByteArray {
        val buffer = Buffer()
        encryptingSinkMultiPassword(buffer, passwords, recovery, dek).buffer().use { it.write(payload) }
        return buffer.readByteArray()
    }

    fun decrypt(bytes: ByteArray, password: String?): ByteArray {
        val header = readHeader(bytes) ?: throw IOException("Not a Lucent backup")
        if (header.needsPassword && password.isNullOrEmpty()) throw WrongPasswordException()

        if (header.version == VERSION_MULTI_PASSWORD) {
            val key = unwrapDekWithPassword(header, password!!)
                ?: throw WrongPasswordException()
            val sourceBuffer = Buffer().write(bytes)
            sourceBuffer.skip(header.byteLength.toLong())

            return try {
                FileCrypto.decryptingSource(sourceBuffer, key).buffer().use { it.readByteArray() }
            } catch (t: Throwable) {
                throw WrongPasswordException()
            }
        }

        val key = keyFor(header, password)
        val sourceBuffer = Buffer().write(bytes)
        sourceBuffer.skip(header.byteLength.toLong())

        return try {
            FileCrypto.decryptingSource(sourceBuffer, key).buffer().use { it.readByteArray() }
        } catch (t: Throwable) {
            if (header.needsPassword) throw WrongPasswordException() else throw IOException("Backup file is damaged", t)
        }
    }

    fun decryptWithAnswer(bytes: ByteArray, answer: String): ByteArray? {
        val header = readHeader(bytes) ?: return null
        val envelope = header.recovery ?: return null
        if (header.version == VERSION_MULTI_PASSWORD) {
            val dek = BackupRecovery.recoverKey(envelope, answer) ?: return null
            val sourceBuffer = Buffer().write(bytes)
            sourceBuffer.skip(header.byteLength.toLong())
            return try {
                FileCrypto.decryptingSource(sourceBuffer, dek).buffer().use { it.readByteArray() }
            } catch (_: Throwable) {
                null
            }
        } else {
            val recoveredPassword = BackupRecovery.recover(envelope, answer) ?: return null
            return try {
                decrypt(bytes, recoveredPassword)
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun decryptingSource(source: Source, header: Header, password: String?): Source {
        if (header.needsPassword && password.isNullOrEmpty()) throw WrongPasswordException()
        val key = if (header.version == VERSION_MULTI_PASSWORD) {
            unwrapDekWithPassword(header, password!!) ?: throw WrongPasswordException()
        } else {
            keyFor(header, password)
        }
        return FileCrypto.decryptingSource(source, key)
    }

    fun decryptingSourceWithAnswer(source: Source, header: Header, answer: String): Source {
        val envelope = header.recovery ?: throw WrongPasswordException()
        val key = if (header.version == VERSION_MULTI_PASSWORD) {
            BackupRecovery.recoverKey(envelope, answer) ?: throw WrongPasswordException()
        } else {
            val pw = BackupRecovery.recover(envelope, answer) ?: throw WrongPasswordException()
            keyFor(header, pw)
        }
        return FileCrypto.decryptingSource(source, key)
    }

    class WrongPasswordException : IOException("Wrong backup password")
}
