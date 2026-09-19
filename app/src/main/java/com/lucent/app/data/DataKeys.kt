package com.lucent.app.data

import android.content.Context
import java.io.File
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

object DataKeys {

    private const val KEY_DIR = "keys"
    private const val ATTACHMENT_KEY_FILE = "attachments.key"
    private const val DATABASE_KEY_FILE = "database.key"
    private const val RECOVERY_SUFFIX = ".recovery"
    private const val KEY_BYTES = 32

    private val lock = Any()
    @Volatile private var attachmentKey: SecretKey? = null
    @Volatile private var databaseKeyHex: String? = null

    private fun keyDir(context: Context): File =
        File(context.applicationContext.filesDir, KEY_DIR).apply { if (!exists()) mkdirs() }

    private fun decodeKey(base64: String): ByteArray? {
        if (base64.isEmpty()) return null
        val bytes = try {
            android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
        } catch (t: Throwable) {
            null
        }
        return if (bytes != null && bytes.size == KEY_BYTES) bytes else null
    }

    private fun atomicWrite(file: File, contents: String): Boolean {
        val temp = File(file.parentFile, "${file.name}.tmp")
        return try {
            temp.writeText(contents)
            if (temp.renameTo(file)) true else { temp.delete(); false }
        } catch (t: Throwable) {
            temp.delete()
            false
        }
    }

    private fun getOrCreate(context: Context, fileName: String): ByteArray {
        val appContext = context.applicationContext
        val file = File(keyDir(appContext), fileName)
        val recoveryFile = File(keyDir(appContext), fileName + RECOVERY_SUFFIX)

        if (file.exists() || recoveryFile.exists()) {
            val primaryBytes = if (file.exists()) {
                val stored = try { file.readText() } catch (t: Throwable) { "" }
                decodeKey(LocalSecrets.decrypt(stored))
            } else null

            if (primaryBytes != null) {
                val recoveryStale = !recoveryFile.exists() || run {
                    val existing = try { recoveryFile.readText() } catch (t: Throwable) { "" }
                    !RecoverableSecret.canDecrypt(appContext, existing)
                }
                if (recoveryStale) writeRecoveryCopy(appContext, recoveryFile, primaryBytes)
                return primaryBytes
            }

            val recoveryBytes = if (recoveryFile.exists()) {
                val storedRecovery = try { recoveryFile.readText() } catch (t: Throwable) { "" }
                decodeKey(RecoverableSecret.decrypt(appContext, storedRecovery))
            } else null

            if (recoveryBytes != null) {
                val rewrapped = LocalSecrets.encrypt(
                    android.util.Base64.encodeToString(recoveryBytes, android.util.Base64.NO_WRAP)
                )
                if (rewrapped.isNotEmpty()) atomicWrite(file, rewrapped)
                return recoveryBytes
            }

            throw IllegalStateException(
                "The encryption key in $fileName could not be read by either the Keystore or the " +
                    "recovery path. Data protected by it cannot be decrypted on this device; " +
                    "restore from a backup."
            )
        }

        val fresh = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        val freshBase64 = android.util.Base64.encodeToString(fresh, android.util.Base64.NO_WRAP)
        if (!atomicWrite(file, LocalSecrets.encrypt(freshBase64))) {
            throw IllegalStateException("Could not store the encryption key")
        }
        writeRecoveryCopy(appContext, recoveryFile, fresh)
        return fresh
    }

    private fun writeRecoveryCopy(context: Context, recoveryFile: File, keyBytes: ByteArray) {
        val base64 = android.util.Base64.encodeToString(keyBytes, android.util.Base64.NO_WRAP)
        val wrapped = RecoverableSecret.encrypt(context, base64)
        if (wrapped.isNotEmpty()) atomicWrite(recoveryFile, wrapped)
    }

    fun attachmentKey(context: Context): SecretKey {
        attachmentKey?.let { return it }
        synchronized(lock) {
            attachmentKey?.let { return it }
            val key = SecretKeySpec(getOrCreate(context, ATTACHMENT_KEY_FILE), "AES")
            attachmentKey = key
            return key
        }
    }

    fun databasePassphrase(context: Context): String {
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

    fun hasDatabaseKey(context: Context): Boolean =
        File(keyDir(context.applicationContext), DATABASE_KEY_FILE).exists()

    fun resetCacheForTesting() {
        synchronized(lock) {
            attachmentKey = null
            databaseKeyHex = null
        }
    }
}
