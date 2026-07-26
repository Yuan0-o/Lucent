package com.lucent.app.data

import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Security-question recovery for the **backup password** (C-group task 13) — opt-in, default off.
 *
 * ### Why the app lock's approach does not transfer
 *
 * [AppLock] can offer "forgot password" because it only ever needs to *verify* a password: it
 * stores a one-way hash, and answering the security question authorises writing a **new** hash. The
 * old password is never needed again.
 *
 * A backup is the opposite. The `.lcb` file's key **is** the password, stretched through PBKDF2 —
 * so "resetting" it is meaningless. There is no server, no second copy of the key, and no way to
 * re-encrypt a file you cannot decrypt. Forgetting the password of an existing backup is, in the
 * plain sense, unrecoverable. That is not a gap in the implementation; it is what encryption means.
 *
 * ### What this actually does instead
 *
 * It stores, **inside the backup file itself**, the password wrapped under a second key derived
 * from the answer to a security question:
 *
 * ```
 *   envelope = { v, question, salt, iter, iv, wrapped }
 *   wrapped  = AES-256-GCM( backupPassword,  key = PBKDF2(normalise(answer), salt, iter) )
 * ```
 *
 * So "forgot the backup password" becomes: read the question out of the file's plaintext header,
 * answer it, derive the key, unwrap the password, and proceed with the normal decrypt. No back door
 * is added to the payload cipher — the payload is still opened only by the real password. What is
 * added is a second, independently locked copy of that password, travelling with the file.
 *
 * ### The trade-off, stated plainly, because the user has to make this call
 *
 * With an envelope present, the backup's security is **the weaker of the password and the answer**.
 * A security question whose answer is a pet's name, a birthplace, or anything a person could learn
 * from a social media profile does not merely weaken the backup — it *becomes* its real password.
 *
 * That is why this is:
 *  - **off by default**, and
 *  - **per-export**, not a global setting: the user decides for each backup whether that trade is
 *    worth it, because a backup going to a USB stick in a drawer and one going to a shared cloud
 *    folder are not the same risk, and
 *  - accompanied by copy that says the above in as many words rather than calling it "recovery".
 *
 * An export made without an envelope is byte-identical to what the app produced before this file
 * existed. Nothing is added to a backup the user did not ask to add it to.
 *
 * ### Answer normalisation
 *
 * Trimmed and lower-cased, matching [AppLock] exactly, so "Fluffy" and " fluffy " both work. The
 * *same* normalisation must be applied when wrapping and unwrapping or recovery silently fails, so
 * both paths call [normalise] rather than doing it inline.
 */
object BackupRecovery {

    private const val VERSION = 1
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    /**
     * PBKDF2 rounds for the answer.
     *
     * Matched to [BackupCrypto.PASSWORD_ITERATIONS] on purpose. The answer is now an equally
     * valuable target — an attacker will simply attack whichever of the two is cheaper — so
     * stretching it any less would quietly make the envelope the preferred way in, and the number
     * chosen for the password is the one that was already reasoned about.
     */
    private const val ITERATIONS = BackupCrypto.PASSWORD_ITERATIONS

    private val random = SecureRandom()

    /** A parsed envelope. [question] is readable without knowing anything, so it can be displayed. */
    data class Envelope(
        val question: String,
        val salt: ByteArray,
        val iterations: Int,
        val iv: ByteArray,
        val wrapped: ByteArray
    ) {
        // ByteArray in a data class compares by identity; override so equality means what it reads as.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Envelope) return false
            return question == other.question &&
                iterations == other.iterations &&
                salt.contentEquals(other.salt) &&
                iv.contentEquals(other.iv) &&
                wrapped.contentEquals(other.wrapped)
        }

        override fun hashCode(): Int {
            var h = question.hashCode()
            h = h * 31 + iterations
            h = h * 31 + salt.contentHashCode()
            h = h * 31 + iv.contentHashCode()
            h = h * 31 + wrapped.contentHashCode()
            return h
        }
    }

    /**
     * Build an envelope wrapping [backupPassword] under [answer].
     *
     * Returns null — meaning "write no envelope at all" — when either half is blank. This mirrors
     * [AppLock.createCredentials]: half a security question is not a security question, and writing
     * an envelope keyed on an empty answer would produce a backup that a single space could open.
     */
    fun create(question: String, answer: String, backupPassword: String): Envelope? {
        if (question.isBlank() || answer.isBlank() || backupPassword.isEmpty()) return null
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val key = deriveKey(normalise(answer), salt, ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val wrapped = cipher.doFinal(backupPassword.toByteArray(Charsets.UTF_8))
        return Envelope(question.trim(), salt, ITERATIONS, iv, wrapped)
    }

    /**
     * Recover the backup password from [envelope] using [answer], or null if the answer is wrong.
     *
     * GCM authenticates, so a wrong answer fails the tag check and throws rather than producing
     * plausible garbage — there is no way for a near-miss to yield a password-shaped string that
     * then fails confusingly further down.
     */
    fun recover(envelope: Envelope, answer: String): String? {
        if (answer.isBlank()) return null
        return try {
            val key = deriveKey(normalise(answer), envelope.salt, envelope.iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, envelope.iv))
            String(cipher.doFinal(envelope.wrapped), Charsets.UTF_8)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Serialise for the backup file's plaintext header.
     *
     * JSON rather than a packed binary struct because the field that matters — the question — is a
     * free-text string of unpredictable length in any of four languages, and a length-prefixed
     * binary layout buys nothing over JSON for a block this small while being far easier to get
     * subtly wrong.
     */
    fun toJson(envelope: Envelope): String = JSONObject()
        .put("v", VERSION)
        .put("question", envelope.question)
        .put("salt", b64(envelope.salt))
        .put("iter", envelope.iterations)
        .put("iv", b64(envelope.iv))
        .put("wrapped", b64(envelope.wrapped))
        .toString()

    /** Parse an envelope. Returns null for anything malformed — a broken envelope is simply absent. */
    fun fromJson(json: String): Envelope? {
        if (json.isBlank()) return null
        return try {
            val o = JSONObject(json)
            if (o.optInt("v", 0) != VERSION) return null
            val question = o.optString("question", "")
            if (question.isBlank()) return null
            val salt = b64d(o.optString("salt", "")) ?: return null
            val iv = b64d(o.optString("iv", "")) ?: return null
            val wrapped = b64d(o.optString("wrapped", "")) ?: return null
            val iter = o.optInt("iter", ITERATIONS)
            if (iter <= 0) return null
            Envelope(question, salt, iter, iv, wrapped)
        } catch (_: Throwable) {
            null
        }
    }

    // -----------------------------------------------------------------------------------------

    /** Identical to AppLock's normalisation. Both wrap and unwrap call this; neither inlines it. */
    private fun normalise(answer: String): String = answer.trim().lowercase()

    private fun deriveKey(answer: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(answer.toCharArray(), salt, iterations, KEY_BITS)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun b64(bytes: ByteArray): String =
        java.util.Base64.getEncoder().withoutPadding().encodeToString(bytes)

    private fun b64d(s: String): ByteArray? =
        if (s.isEmpty()) null else try { java.util.Base64.getDecoder().decode(s) } catch (_: Throwable) { null }
}
