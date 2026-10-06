package com.lucent.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupRecovery {

    private const val VERSION = 1
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    private const val ITERATIONS = BackupCrypto.PASSWORD_ITERATIONS

    private val random = SecureRandom()

    private val jsonFormat = Json { ignoreUnknownKeys = true }

    @Serializable
    data class EnvelopeJson(
        @SerialName("v") val v: Int = 0,
        @SerialName("question") val question: String = "",
        @SerialName("salt") val salt: String = "",
        @SerialName("iter") val iter: Int = ITERATIONS,
        @SerialName("iv") val iv: String = "",
        @SerialName("wrapped") val wrapped: String = ""
    )

    data class Envelope(
        val question: String,
        val salt: ByteArray,
        val iterations: Int,
        val iv: ByteArray,
        val wrapped: ByteArray
    ) {
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

    fun toJson(envelope: Envelope): String {
        val ej = EnvelopeJson(
            v = VERSION,
            question = envelope.question,
            salt = b64(envelope.salt),
            iter = envelope.iterations,
            iv = b64(envelope.iv),
            wrapped = b64(envelope.wrapped)
        )
        return jsonFormat.encodeToString(ej)
    }

    fun fromJson(json: String): Envelope? {
        if (json.isBlank()) return null
        return try {
            val o = jsonFormat.decodeFromString<EnvelopeJson>(json)
            if (o.v != VERSION) return null
            val question = o.question
            if (question.isBlank()) return null
            val salt = b64d(o.salt) ?: return null
            val iv = b64d(o.iv) ?: return null
            val wrapped = b64d(o.wrapped) ?: return null
            val iter = o.iter
            if (iter <= 0) return null
            Envelope(question, salt, iter, iv, wrapped)
        } catch (_: Throwable) {
            null
        }
    }


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
