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
 * The desktop build keeps the same *shape* — values on disk are AES-GCM ciphertext, never
 * plaintext — under a per-install random master key stored beside the data (`keys/master.key`).
 *
 * ### P0-1: the master key is now bound to the Windows user account
 *
 * Before P0-1 the master key sat in `keys/master.key` Base64-encoded and unprotected, in the same
 * directory as the data it protects: on Windows, the encrypted database, the wrapped keys and the
 * key that unwraps them all lived in `%APPDATA%\Lucent`, so anything that copied that folder got
 * everything. The stored form now carries a scheme prefix beside the existing `v1:` / `p1:` pair:
 *
 *  - `d1:` — the master key bytes were wrapped with Windows DPAPI
 *    (`CryptProtectData` / `CryptUnprotectData` via JNA, no `CRYPTPROTECT_LOCAL_MACHINE`, so the
 *    ciphertext is bound to the *user account*, not the machine). A copied profile folder no
 *    longer yields the master key on another login.
 *  - bare Base64 — the legacy form from before P0-1. Read transparently, then **re-wrapped in
 *    place** on the first launch that can (a Windows machine), so existing installs migrate with
 *    no user action and no data touched.
 *
 * DPAPI is a Windows API, reachable from a pure JVM via `jna-platform` with no native code shipped.
 * On non-Windows machines (the desktop shim's `filesDir` supports macOS/Linux developer machines)
 * the wrapper is unavailable and the key is stored in the legacy bare form — the values are still
 * AES-GCM ciphertext, never plaintext, but the root key is not user-bound, which is reported
 * through [EncryptionStatus] so the Settings → Security banner and the startup log tell the truth
 * instead of pretending the binding exists. This follows the project's established policy:
 * *degrade one notch, never strand the data*.
 *
 * The `.lcb` backup format is untouched: backups are keyed by password or app key through
 * [CryptoUtil], never by `master.key`, so a DPAPI-wrapped master key does not make a backup
 * machine-specific.
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

    /** P0-1: stored-form marker for "master key was wrapped with Windows DPAPI". */
    private const val PREFIX_DPAPI = "d1:"

    private const val IV_LEN = 12
    private const val GCM_TAG_BITS = 128
    private val random = SecureRandom()

    /** Set once the first seal/open runs, so the Settings banner reflects what actually happened. */
    @Volatile private var reported = false

    // The master key is a process-lifetime value but is loaded lazily and re-read after a
    // resetForTesting(), so it is a guarded nullable rather than a `by lazy`.
    @Volatile private var keyBytesValue: ByteArray? = null
    private val keyLock = Any()

    /**
     * The platform wrapper that binds the master key to the current user account.
     *
     * On Windows this is DPAPI (`CryptProtectData`/`CryptUnprotectData` via JNA). On other
     * operating systems it is unavailable and [wrap] returns null, which keeps the legacy bare
     * stored form and reports the missing binding through [EncryptionStatus].
     */
    internal interface MasterKeyWrapper {
        /** Wrap [bytes] so they can only be unwrapped by the same user account; null = unavailable. */
        fun wrap(bytes: ByteArray): ByteArray?

        /** Unwrap [bytes]; null when the wrapping user differs, the data is corrupt, or unavailable. */
        fun unwrap(bytes: ByteArray): ByteArray?
    }

    private val dpapiWrapper: MasterKeyWrapper = object : MasterKeyWrapper {
        private fun isWindows(): Boolean =
            System.getProperty("os.name").lowercase().contains("win")

        override fun wrap(bytes: ByteArray): ByteArray? {
            if (!isWindows()) return null
            return try {
                // flags 0 = no CRYPTPROTECT_LOCAL_MACHINE → ciphertext is bound to the user account.
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

    // ---- Test seams (P0-1): the desktop object is process-global, so the tests need to point it
    // at a scratch directory and a fake wrapper. Production code never touches these. ----

    /** Test seam: where `keys/` lives; production uses [DesktopContext.filesDir]. */
    internal var filesDirOverride: File? = null

    /** Test seam: the wrapper to use; production uses DPAPI (or null on non-Windows). */
    internal var wrapperOverride: MasterKeyWrapper? = null

    /** Test seam: forget the cached key and the reporting flag so the next call re-reads. */
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

    /**
     * Read the master key from disk, minting and durably writing a fresh one when absent.
     *
     * Stored-form decoding understands both the `d1:` DPAPI-wrapped form (P0-1) and the legacy bare
     * Base64 form. A legacy file found on a machine that can wrap (Windows) is re-wrapped in place;
     * on a machine that cannot, the file is left alone and the missing binding is reported.
     * Returns null when the file exists but cannot be decoded — the caller degrades to `p1:` and
     * reports, exactly as before P0-1.
     */
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

    /** Encode [key] for durable storage: DPAPI-wrapped (`d1:`) when possible, else legacy bare. */
    private fun encodeStoredForm(key: ByteArray): String {
        val wrapped = wrapper().wrap(key)
        return if (wrapped != null) {
            PREFIX_DPAPI + java.util.Base64.getEncoder().encodeToString(wrapped)
        } else {
            reportUnbound()
            java.util.Base64.getEncoder().encodeToString(key)
        }
    }

    /** Decode a stored form back to exactly 32 key bytes, or null when it can't be opened. */
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

    /**
     * P0-1 silent migration: a legacy bare-Base64 `master.key` is re-wrapped in place on the first
     * launch that can (Windows), so the plaintext root disappears with no user action. On a machine
     * that cannot wrap, the file is left untouched and the missing binding is reported instead.
     */
    private fun rewrapLegacyInPlace(file: File, storedText: String, key: ByteArray) {
        if (storedText.startsWith(PREFIX_DPAPI)) return
        val wrapped = wrapper().wrap(key)
        if (wrapped != null) {
            // Best-effort: if the durable rewrite fails the in-memory key still works and the
            // legacy file remains readable; the next launch tries again.
            writeMasterKeyDurably(
                file.parentFile, file, PREFIX_DPAPI + java.util.Base64.getEncoder().encodeToString(wrapped)
            )
        } else {
            reportUnbound()
        }
    }

    /** Tell the Settings → Security banner (via [EncryptionStatus]) that the root key is not user-bound. */
    private fun reportUnbound() {
        if (reported) return
        EncryptionStatus.reportSecrets(
            EncryptionStatus.State.ENCRYPTED,
            "master key is not user-bound (DPAPI unavailable on this OS)"
        )
        reported = true
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
    private fun writeMasterKeyDurably(dir: File, file: File, stored: String): Boolean {
        val tmp = File(dir, "master.key.tmp")
        return try {
            java.io.FileOutputStream(tmp).use { out ->
                out.write(stored.toByteArray(Charsets.UTF_8))
                out.flush()
                out.fd.sync()   // the bytes are on the platter before the name appears
            }
            if (tmp.renameTo(file)) true else { tmp.delete(); false }
        } catch (t: Throwable) {
            tmp.delete()
            false
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
