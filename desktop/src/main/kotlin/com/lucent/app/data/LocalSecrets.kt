package com.lucent.app.data

import android.content.DesktopContext
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Desktop twin of the Android LocalSecrets: seal/open small preference values with AES-256-GCM.
 *
 * ### What replaces the Android Keystore here
 *
 * Android wraps these values under a key the hardware Keystore holds and the app cannot export.
 * Windows has no equivalent the JVM can reach without native code, so the desktop build keeps the
 * same *shape* — values on disk are AES-GCM ciphertext, never plaintext — under a per-install
 * random master key stored beside the data (`keys/master.key`). That is honest local obfuscation
 * rather than hardware binding: someone with full access to the user's profile directory can
 * recover the values, exactly as they could copy the whole profile anyway. What it preserves is
 * that no API key, base URL, or lock hash ever sits readable in a settings file, and that a synced
 * or backed-up settings file leaks nothing on its own.
 *
 * ### Format compatibility
 *
 * The sealed string format matches Android's (`v1:` prefix + Base64(iv | ciphertext+tag)), and —
 * exactly like the original — [decrypt] returns an unprefixed value untouched, so plaintext
 * defaults and legacy values read back correctly and are re-sealed on their next save.
 *
 * ### C-group task 17: the plaintext fallback is gone
 *
 * This class previously had two paths that returned **the caller's own plaintext**: one when the
 * master key could not be loaded (`keyBytes ?: return value`) and one when the cipher threw
 * (`catch { value }`). Both were silent. Their combined effect was that a single unreadable
 * `master.key` — which a forced power-off can produce, see [DataKeys] — quietly switched the whole
 * settings file to plaintext storage of the API key, the app-lock hash blob and the backup
 * password, while every caller went on believing it had stored ciphertext. Nothing logged it and
 * nothing showed it, which is why the three-day log attached to this task looks entirely normal.
 *
 * The fallback is now the same one the Android build has always used: [CryptoUtil], marked with a
 * `p1:` prefix so [decrypt] knows which scheme wrote a given value. That is weaker than the master
 * key (its key is derived from a passphrase compiled into the app, so it is obfuscation, not
 * secrecy) but it is *never plaintext*, it is recorded in [EncryptionStatus] so the startup log and
 * the Settings → Security banner both say so, and a value written while degraded still opens
 * correctly after the master key is repaired.
 */
object LocalSecrets {

    /** Master-key scheme — the normal path. Unchanged, so existing sealed values keep opening. */
    private const val PREFIX = "v1:"

    /** Portable fallback scheme, used only when the master key is unavailable. */
    private const val PREFIX_PORTABLE = "p1:"

    private const val IV_LEN = 12
    private const val GCM_TAG_BITS = 128
    private val random = SecureRandom()

    /** Set once the first seal/open runs, so the Settings banner reflects what actually happened. */
    @Volatile private var reported = false

    private val keyBytes: ByteArray? by lazy {
        try {
            val dir = File(DesktopContext.filesDir, "keys").apply { mkdirs() }
            val file = File(dir, "master.key")
            if (file.exists()) {
                val loaded = java.util.Base64.getDecoder().decode(file.readText().trim())
                if (loaded.size == 32) loaded else null
            } else {
                val fresh = ByteArray(32).also { random.nextBytes(it) }
                writeMasterKeyDurably(dir, file, fresh)
            }
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Write a freshly minted master key so a power cut cannot leave a *present but empty* file.
     *
     * The original wrote the temp file and renamed it. On NTFS a rename can reach the disk before
     * the bytes it points at do, so an abrupt power loss — exactly what task 4's GPU hang forced —
     * could leave `master.key` existing at zero length. On the next launch that file exists, decodes
     * to fewer than 32 bytes, and this whole object silently degrades. Forcing the *contents* to
     * disk before publishing the name closes that window: after the fsync, either the file is absent
     * (so a fresh key is minted, which is recoverable) or it is complete.
     */
    private fun writeMasterKeyDurably(dir: File, file: File, fresh: ByteArray): ByteArray? {
        val tmp = File(dir, "master.key.tmp")
        return try {
            java.io.FileOutputStream(tmp).use { out ->
                out.write(java.util.Base64.getEncoder().encodeToString(fresh).toByteArray(Charsets.UTF_8))
                out.flush()
                out.fd.sync()   // the bytes are on the platter before the name appears
            }
            if (tmp.renameTo(file)) fresh else { tmp.delete(); null }
        } catch (t: Throwable) {
            tmp.delete()
            null
        }
    }

    /** Seal [value]. Returns "" for "" (a cleared value stays cleared); never returns plaintext. */
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

    /** Open a sealed value; a value without a recognised prefix is returned as-is (legacy plaintext). */
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

    /**
     * The fallback path: still encrypted, just with the portable scheme, and *recorded* — which is
     * the half that was missing before. A degraded store is a real finding, not an implementation
     * detail, so it reaches both the exported log and the Settings screen.
     */
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
