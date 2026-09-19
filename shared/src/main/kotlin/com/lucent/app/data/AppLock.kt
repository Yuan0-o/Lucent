package com.lucent.app.data

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object AppLock {

    private const val VERSION = 1
    private const val ITERATIONS = 120_000
    private const val SALT_LEN = 16
    private const val KEY_BITS = 256

    private val random = SecureRandom()

    fun createCredentials(password: String, question: String, answer: String): String {
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val recovery = question.isNotBlank() && answer.isNotBlank()
        return JSONObject()
            .put("v", VERSION)
            .put("iter", ITERATIONS)
            .put("salt", b64(salt))
            .put("pwHash", b64(hash(password.toCharArray(), salt, ITERATIONS)))
            .put("question", if (recovery) question.trim() else "")
            .put(
                "ansHash",
                if (recovery) b64(hash(normalizeAnswer(answer).toCharArray(), salt, ITERATIONS)) else ""
            )
            .toString()
    }

    fun hasRecovery(credentialsJson: String): Boolean {
        val o = parse(credentialsJson) ?: return false
        return o.optString("question", "").isNotBlank() && o.optString("ansHash", "").isNotEmpty()
    }

    fun question(credentialsJson: String): String = parse(credentialsJson)?.optString("question", "") ?: ""

    fun verifyPassword(credentialsJson: String, password: String): Boolean {
        val o = parse(credentialsJson) ?: return false
        return verify(password.toCharArray(), o, "pwHash")
    }

    fun verifyAnswer(credentialsJson: String, answer: String): Boolean {
        if (!hasRecovery(credentialsJson)) return false
        val o = parse(credentialsJson) ?: return false
        return verify(normalizeAnswer(answer).toCharArray(), o, "ansHash")
    }

    fun changePassword(credentialsJson: String, newPassword: String): String? {
        val o = parse(credentialsJson) ?: return null
        val question = o.optString("question", "")
        val salt = b64ToBytes(o.optString("salt", "")) ?: return null
        val iter = o.optInt("iter", ITERATIONS)
        return JSONObject()
            .put("v", VERSION)
            .put("iter", iter)
            .put("salt", o.optString("salt", ""))
            .put("pwHash", b64(hash(newPassword.toCharArray(), salt, iter)))
            .put("question", question)
            .put("ansHash", o.optString("ansHash", ""))
            .toString()
    }


    private fun verify(input: CharArray, o: JSONObject, hashField: String): Boolean {
        val salt = b64ToBytes(o.optString("salt", "")) ?: return false
        val iter = o.optInt("iter", ITERATIONS)
        val expected = b64ToBytes(o.optString(hashField, "")) ?: return false
        val actual = hash(input, salt, iter)
        return constantTimeEquals(expected, actual)
    }

    private fun hash(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        com.lucent.app.nativebridge.LucentNative
            .pbkdf2Sha256(password, salt, iterations, KEY_BITS / 8)
            ?.let { return it }
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return factory.generateSecret(spec).encoded
    }

    private fun normalizeAnswer(answer: String): String = answer.trim().lowercase()

    private fun parse(json: String): JSONObject? =
        if (json.isBlank()) null else try { JSONObject(json) } catch (t: Throwable) { null }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun b64ToBytes(s: String): ByteArray? =
        if (s.isEmpty()) null else try { Base64.decode(s, Base64.NO_WRAP) } catch (t: Throwable) { null }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
