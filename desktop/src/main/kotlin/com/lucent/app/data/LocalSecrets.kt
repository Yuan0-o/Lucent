package com.lucent.app.data

import android.content.DesktopContext
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object LocalSecrets {

    private const val PREFIX = "v1:"

    private const val PREFIX_PORTABLE = "p1:"

    private const val PREFIX_DPAPI = "d1:"

    private const val IV_LEN = 12
    private const val GCM_TAG_BITS = 128
    private val random = SecureRandom()

    @Volatile private var reported = false

    @Volatile private var keyBytesValue: ByteArray? = null
    private val keyLock = Any()

    internal interface MasterKeyWrapper {
        fun wrap(bytes: ByteArray): ByteArray?

        fun unwrap(bytes: ByteArray): ByteArray?
    }

    private val dpapiWrapper: MasterKeyWrapper = object : MasterKeyWrapper {
        private fun isWindows(): Boolean =
            System.getProperty("os.name").lowercase().contains("win")

        override fun wrap(bytes: ByteArray): ByteArray? {
            if (!isWindows()) return null
            return try {
                com.sun.jna.platform.win32.Crypt32Util.cryptProtectData(bytes, 0)
            } catch (t: Throwable) {
                null
            }
        }

        override fun unwrap(bytes: ByteArray): ByteArray? {
            if (!isWindows()) return null
            return try {
                com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData(bytes, 0)
            } catch (t: Throwable) {
                null
            }
        }
    }


    internal var filesDirOverride: File? = null

    internal var wrapperOverride: MasterKeyWrapper? = null

    internal fun resetForTesting() {
        synchronized(keyLock) {
            keyBytesValue = null
            reported = false
        }
        EncryptionStatus.reportSecrets(EncryptionStatus.State.UNKNOWN)
    }

    private fun wrapper(): MasterKeyWrapper = wrapperOverride ?: dpapiWrapper

    private fun keyDir(): File {
        val base = filesDirOverride ?: DesktopContext.filesDir
        return File(base, "keys").apply { mkdirs() }
    }

    private val keyBytes: ByteArray?
        get() {
            keyBytesValue?.let { return it }
            synchronized(keyLock) {
                keyBytesValue?.let { return it }
                val loaded = loadMasterKey()
                keyBytesValue = loaded
                return loaded
            }
        }

    private fun loadMasterKey(): ByteArray? {
        return try {
            val dir = keyDir()
            val file = File(dir, "master.key")
            if (file.exists()) {
                val text = file.readText().trim()
                val key = decodeStoredForm(text)
                if (key != null) {
                    rewrapLegacyInPlace(file, text, key)
                    key
                } else {
                    null
                }
            } else {
                val fresh = ByteArray(32).also { random.nextBytes(it) }
                if (writeMasterKeyDurably(dir, file, encodeStoredForm(fresh))) fresh else null
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun encodeStoredForm(key: ByteArray): String {
        val wrapped = wrapper().wrap(key)
        return if (wrapped != null) {
            PREFIX_DPAPI + java.util.Base64.getEncoder().encodeToString(wrapped)
        } else {
            reportUnbound()
            java.util.Base64.getEncoder().encodeToString(key)
        }
    }

    private fun decodeStoredForm(text: String): ByteArray? {
        if (text.startsWith(PREFIX_DPAPI)) {
            val wrapped = try {
                java.util.Base64.getDecoder().decode(text.removePrefix(PREFIX_DPAPI))
            } catch (t: Throwable) {
                return null
            }
            val key = wrapper().unwrap(wrapped) ?: return null
            return if (key.size == 32) key else null
        }
        return try {
            val key = java.util.Base64.getDecoder().decode(text)
            if (key.size == 32) key else null
        } catch (t: Throwable) {
            null
        }
    }

    private fun rewrapLegacyInPlace(file: File, storedText: String, key: ByteArray) {
        if (storedText.startsWith(PREFIX_DPAPI)) return
        val wrapped = wrapper().wrap(key)
        if (wrapped != null) {
            writeMasterKeyDurably(
                file.parentFile, file, PREFIX_DPAPI + java.util.Base64.getEncoder().encodeToString(wrapped)
            )
        } else {
            reportUnbound()
        }
    }

    private fun reportUnbound() {
        if (reported) return
        EncryptionStatus.reportSecrets(
            EncryptionStatus.State.ENCRYPTED,
            "master key is not user-bound (DPAPI unavailable on this OS)"
        )
        reported = true
    }

    private fun writeMasterKeyDurably(dir: File, file: File, stored: String): Boolean {
        val tmp = File(dir, "master.key.tmp")
        return try {
            java.io.FileOutputStream(tmp).use { out ->
                out.write(stored.toByteArray(Charsets.UTF_8))
                out.flush()
                out.fd.sync()
            }
            if (AtomicFiles.replace(tmp, file)) true else { tmp.delete(); false }
        } catch (t: Throwable) {
            tmp.delete()
            false
        }
    }

    fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        val key = keyBytes ?: return degrade(value, "master key unavailable")
        return try {
            val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            val sealed = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            markHealthy()
            PREFIX + java.util.Base64.getEncoder().encodeToString(iv + sealed)
        } catch (t: Throwable) {
            degrade(value, "cipher failed: ${t.javaClass.simpleName}")
        }
    }

    fun decrypt(stored: String): String {
        if (stored.isEmpty()) return ""
        if (stored.startsWith(PREFIX_PORTABLE)) {
            return CryptoUtil.decrypt(stored.removePrefix(PREFIX_PORTABLE))
        }
        if (!stored.startsWith(PREFIX)) return stored
        val key = keyBytes ?: return ""
        return try {
            val combined = java.util.Base64.getDecoder().decode(stored.removePrefix(PREFIX))
            if (combined.size <= IV_LEN) return ""
            val iv = combined.copyOfRange(0, IV_LEN)
            val sealed = combined.copyOfRange(IV_LEN, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(sealed), Charsets.UTF_8)
        } catch (t: Throwable) {
            ""
        }
    }

    private fun degrade(value: String, reason: String): String {
        EncryptionStatus.reportSecrets(EncryptionStatus.State.PLAINTEXT, reason)
        reported = true
        return PREFIX_PORTABLE + CryptoUtil.encrypt(value)
    }

    private fun markHealthy() {
        if (reported) return
        EncryptionStatus.reportSecrets(EncryptionStatus.State.ENCRYPTED)
        reported = true
    }
}
