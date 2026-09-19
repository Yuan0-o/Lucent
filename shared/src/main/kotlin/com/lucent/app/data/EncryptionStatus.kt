package com.lucent.app.data

import android.content.Context

object EncryptionStatus {

    enum class State {
        UNKNOWN,

        ENCRYPTED,

        PLAINTEXT,

        LOCKED_OUT
    }

    @Volatile var database: State = State.UNKNOWN
        private set

    @Volatile var secrets: State = State.UNKNOWN
        private set

    @Volatile var attachments: State = State.UNKNOWN
        private set

    @Volatile var databaseReason: String? = null
        private set

    @Volatile var secretsReason: String? = null
        private set

    @Volatile var attachmentsReason: String? = null
        private set

    fun reportDatabase(state: State, reason: String? = null) {
        database = state
        databaseReason = reason
    }

    fun reportSecrets(state: State, reason: String? = null) {
        secrets = state
        secretsReason = reason
    }

    fun reportAttachments(state: State, reason: String? = null) {
        attachments = state
        attachmentsReason = reason
    }

    val degraded: Boolean
        get() = database == State.PLAINTEXT ||
            secrets == State.PLAINTEXT ||
            attachments == State.PLAINTEXT

    val lockedOut: Boolean
        get() = database == State.LOCKED_OUT

    fun summaryLine(): String = buildString {
        append("encryption: db=")
        append(database.name.lowercase())
        databaseReason?.let { append(" (").append(it).append(")") }
        append(", secrets=")
        append(secrets.name.lowercase())
        secretsReason?.let { append(" (").append(it).append(")") }
        append(", attachments=")
        append(attachments.name.lowercase())
        attachmentsReason?.let { append(" (").append(it).append(")") }
    }

    fun logSummary(context: Context) {
        StartupLog.event(context, summaryLine())
    }

    fun probeSecrets(): String? {
        val probe = "lucent-probe-" + java.util.UUID.randomUUID()
        val sealed = try {
            LocalSecrets.encrypt(probe)
        } catch (t: Throwable) {
            return "sealing threw: ${t.message}"
        }
        if (sealed == probe) return "value was stored in plaintext"
        if (sealed.isEmpty()) return "sealing produced nothing"
        if (sealed.contains(probe)) return "plaintext is visible inside the stored value"
        val opened = try {
            LocalSecrets.decrypt(sealed)
        } catch (t: Throwable) {
            return "opening threw: ${t.message}"
        }
        if (opened != probe) return "the sealed value did not open back to the original"
        return null
    }
}
