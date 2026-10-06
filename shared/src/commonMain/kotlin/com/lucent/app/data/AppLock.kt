package com.lucent.app.data

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

@Serializable
private data class Credentials(
    @SerialName("v") val v: Int = 0,
    @SerialName("iter") val iter: Int = 120_000,
    @SerialName("salt") val salt: String = "",
    @SerialName("pwHash") val pwHash: String = "",
    @SerialName("question") val question: String = "",
    @SerialName("ansHash") val ansHash: String = ""
)

@OptIn(ExperimentalEncodingApi::class)
object AppLock {

    private const val VERSION = 1
    private const val ITERATIONS = 120_000
    private const val SALT_LEN = 16
    private const val KEY_BITS = 256

    private val random = SecureRandom()
    private val json = Json { ignoreUnknownKeys = true }

    fun createCredentials(password: String, question: String, answer: String): String {
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val recovery = question.isNotBlank() && answer.isNotBlank()
        return json.encodeToString(
            Credentials(
                v = VERSION,
                iter = ITERATIONS,
                salt = b64(salt),
                pwHash = b64(hash(password.toCharArray(), salt, ITERATIONS)),
                question = if (recovery) question.trim() else "",
                ansHash = if (recovery) b64(hash(normalizeAnswer(answer).toCharArray(), salt, ITERATIONS)) else ""
            )
        )
    }

    fun hasRecovery(credentialsJson: String): Boolean {
        val o = parse(credentialsJson) ?: return false
        return o.question.isNotBlank() && o.ansHash.isNotEmpty()
    }

    fun question(credentialsJson: String): String = parse(credentialsJson)?.question ?: ""

    fun verifyPassword(credentialsJson: String, password: String): Boolean {
        val o = parse(credentialsJson) ?: return false
        return verify(password.toCharArray(), o.salt, o.iter, o.pwHash)
    }

    fun verifyAnswer(credentialsJson: String, answer: String): Boolean {
        if (!hasRecovery(credentialsJson)) return false
        val o = parse(credentialsJson) ?: return false
        return verify(normalizeAnswer(answer).toCharArray(), o.salt, o.iter, o.ansHash)
    }

    fun changePassword(credentialsJson: String, newPassword: String): String? {
        val o = parse(credentialsJson) ?: return null
        val saltBytes = b64ToBytes(o.salt) ?: return null
        return json.encodeToString(
            Credentials(
                v = VERSION,
                iter = o.iter,
                salt = o.salt,
                pwHash = b64(hash(newPassword.toCharArray(), saltBytes, o.iter)),
                question = o.question,
                ansHash = o.ansHash
            )
        )
    }

    private fun verify(input: CharArray, saltStr: String, iter: Int, expectedHashStr: String): Boolean {
        val salt = b64ToBytes(saltStr) ?: return false
        val expected = b64ToBytes(expectedHashStr) ?: return false
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

    private fun parse(jsonStr: String): Credentials? =
        if (jsonStr.isBlank()) null else try { json.decodeFromString<Credentials>(jsonStr) } catch (t: Throwable) { null }

    private fun b64(bytes: ByteArray): String = Base64.encode(bytes).trimEnd('=')
    private fun b64ToBytes(s: String): ByteArray? {
        if (s.isEmpty()) return null
        val padded = s.padEnd(s.length + (4 - s.length % 4) % 4, '=')
        return try { Base64.decode(padded) } catch (t: Throwable) { null }
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
