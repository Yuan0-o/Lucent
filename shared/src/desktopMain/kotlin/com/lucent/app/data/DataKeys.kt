package com.lucent.app.data
import com.lucent.app.platform.filesDir

import com.lucent.app.platform.PlatformContext
import java.io.File
import java.security.SecureRandom

actual object DataKeys {

    private const val KEY_DIR = "keys"
    private const val ATTACHMENT_KEY_FILE = "attachments.key"
    private const val DATABASE_KEY_FILE = "database.key"
    private const val KEY_BYTES = 32

    private val lock = Any()
    @Volatile private var attachmentKey: ByteArray? = null
    @Volatile private var databaseKeyHex: String? = null

    private fun keyDir(context: PlatformContext): File =
        File(context.filesDir, KEY_DIR).apply { if (!exists()) mkdirs() }

    private fun decodeKey(base64: String): ByteArray? {
        if (base64.isEmpty()) return null
        val bytes = try {
            java.util.Base64.getDecoder().decode(base64)
        } catch (t: Throwable) {
            null
        }
        return if (bytes != null && bytes.size == KEY_BYTES) bytes else null
    }

    private fun atomicWrite(file: File, contents: String): Boolean {
        val temp = File(file.parentFile, "${file.name}.tmp")
        return try {
            java.io.FileOutputStream(temp).use { out ->
                out.write(contents.toByteArray(Charsets.UTF_8))
                out.flush()
                out.fd.sync()
            }
            if (AtomicFiles.replace(temp, file)) true else { temp.delete(); false }
        } catch (t: Throwable) {
            temp.delete()
            false
        }
    }

    private fun getOrCreate(context: PlatformContext, fileName: String): ByteArray {
        val file = File(keyDir(context), fileName)
        if (file.exists()) {
            val stored = try { file.readText() } catch (t: Throwable) { "" }
            decodeKey(LocalSecrets.decrypt(stored))?.let { return it }
            throw IllegalStateException(
                "The encryption key in $fileName could not be read. Data protected by it cannot " +
                    "be decrypted on this machine; restore from a .lcb backup."
            )
        }
        val fresh = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        val freshBase64 = java.util.Base64.getEncoder().encodeToString(fresh)
        if (!atomicWrite(file, LocalSecrets.encrypt(freshBase64))) {
            throw IllegalStateException("Could not store the encryption key")
        }
        return fresh
    }

    actual fun attachmentKey(context: PlatformContext): ByteArray {
        attachmentKey?.let { return it }
        synchronized(lock) {
            attachmentKey?.let { return it }
            val key = getOrCreate(context, ATTACHMENT_KEY_FILE)
            attachmentKey = key
            return key
        }
    }

    actual fun databasePassphrase(context: PlatformContext): String {
        databaseKeyHex?.let { return it }
        synchronized(lock) {
            databaseKeyHex?.let { return it }
            val bytes = getOrCreate(context, DATABASE_KEY_FILE)
            val hex = bytes.joinToString("") { "%02x".format(it) }
            val passphrase = "x'$hex'"
            databaseKeyHex = passphrase
            return passphrase
        }
    }

    actual fun hasDatabaseKey(context: PlatformContext): Boolean =
        File(keyDir(context), DATABASE_KEY_FILE).exists()

    actual fun resetCacheForTesting() {
        synchronized(lock) {
            attachmentKey = null
            databaseKeyHex = null
        }
    }
}
