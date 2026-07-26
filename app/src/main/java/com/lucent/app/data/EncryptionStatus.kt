package com.lucent.app.data

import android.content.Context

/**
 * At-rest encryption self-check and reporting (C-group task 17).
 *
 * ### Why this file exists
 *
 * The question asked was "is Lucent's encryption still working?" — and the honest answer, before
 * this file, was **that the logs could not tell you**. That is the actual defect this task uncovered:
 *
 *  - `DatabaseEncryption.ensureReady` (Android) has two paths that return `null`, which the caller
 *    correctly reads as "open the database unencrypted". Both were reported only through
 *    `android.util.Log` — logcat, which is a ring buffer, is cleared on reboot, and is not part of
 *    the exported log at all beyond whatever fraction of a second survives at capture time.
 *  - `Db.openConnection` (desktop) has the same two degradation paths.
 *  - `LocalSecrets.encrypt` returned **its own plaintext input** when the master key was
 *    unavailable, so API keys, the app-lock hash blob and the backup password could be written to
 *    the settings file in the clear while every caller believed it had stored ciphertext.
 *
 * None of these ever wrote a line to [StartupLog], which is the log a user can actually export and
 * send. So a store could silently fall back to plaintext and the exported log would look completely
 * normal — exactly as the attached three-day log does.
 *
 * ### What it does now
 *
 * Every subsystem that keys something reports its outcome here, once, at startup. [summaryLine] is
 * written to [StartupLog] on every launch, so the *next* exported log answers the question on its
 * first page, and [degraded] drives a warning banner in Settings → Security so the user does not
 * have to read a log at all.
 *
 * Reporting is one-way and unconditional: a subsystem reports plaintext whether or not logging is
 * enabled, because the Settings banner must be truthful for a user who never turned logging on.
 */
object EncryptionStatus {

    /** How a given store ended up. */
    enum class State {
        /** Not reached yet this launch. */
        UNKNOWN,

        /** Keyed and verified — the store is encrypted at rest. */
        ENCRYPTED,

        /** Deliberately unencrypted, with a reason. Currently only ever a degradation. */
        PLAINTEXT,

        /** Keyed, but the existing data could not be opened with the key; data was set aside. */
        LOCKED_OUT
    }

    @Volatile var database: State = State.UNKNOWN
        private set

    @Volatile var secrets: State = State.UNKNOWN
        private set

    @Volatile var attachments: State = State.UNKNOWN
        private set

    /** Why a store is not [State.ENCRYPTED], for the log line and the Settings banner. */
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

    /**
     * True when anything protected is *not* actually encrypted right now. This is what turns the
     * Settings banner on, so it deliberately treats [State.UNKNOWN] as fine — an unreached subsystem
     * has not failed, and crying wolf about a store nobody opened this launch would train the user
     * to ignore the banner that matters.
     */
    val degraded: Boolean
        get() = database == State.PLAINTEXT ||
            secrets == State.PLAINTEXT ||
            attachments == State.PLAINTEXT

    /** True when a store exists that this machine's key cannot open — a different, louder problem. */
    val lockedOut: Boolean
        get() = database == State.LOCKED_OUT

    /**
     * The one line written to the startup log at every launch. Deliberately English and
     * machine-greppable ("encryption:") regardless of UI language, matching the existing convention
     * that log lines read the same in any bug report.
     */
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

    /**
     * Write [summaryLine] to the startup log. Called once per launch, after the database has been
     * opened, from the same place the existing "App starting" line is written.
     */
    fun logSummary(context: Context) {
        StartupLog.event(context, summaryLine())
    }

    /**
     * A live end-to-end proof that the secrets store really seals values, run on demand from
     * Settings → Security.
     *
     * A status flag can be wrong — it records what a code path *believed*. This does the actual work:
     * seal a random probe, check the stored form is neither the probe itself nor readable as it, and
     * check it opens back to the original. It is the same shape of proof the desktop build's
     * `cipherSelfCheck` CI step applies to the database, moved in-app so a user can run it on the
     * machine that is actually behaving oddly.
     *
     * @return null when the probe passed, or a short reason when it did not.
     */
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
