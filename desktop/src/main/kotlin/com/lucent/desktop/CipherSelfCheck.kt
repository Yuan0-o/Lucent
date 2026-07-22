package com.lucent.desktop

import com.lucent.app.data.keyedSqliteUrl
import com.lucent.app.data.probeCipherCore
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * End-to-end proof that the desktop database is really encrypted at rest — run in CI by the
 * `:desktop:cipherSelfCheck` Gradle task (see build.gradle.kts) as a red/green gate before the
 * installer is packaged.
 *
 * Deliberately thin: plain JDBC against a temp file, no Context, no UI — but it keys its
 * databases through the app's own [keyedSqliteUrl] and identifies the core with the app's own
 * [probeCipherCore], so what CI proves is the exact mechanism Db.kt ships. Both helpers exist in
 * the shape they do because two CI reds taught two lessons on 2026-07-22: at 08:36, that the
 * cipher core doesn't answer SQLCipher's `PRAGMA cipher_version` and this driver's `executeQuery`
 * THROWS on zero-column statements; and at 09:05, that the driver's connection constructor runs
 * its own init pragmas immediately (`SQLiteConfig.apply`), so an already-encrypted database dies
 * with SQLITE_NOTADB before any post-connect `PRAGMA key` could run — the key has to ride the
 * URL.
 *
 * It prints one diagnostic and asserts the three functional claims that together mean
 * "encrypted", in order of how they fail:
 *
 *  0. (Diagnostic, never a gate.) Identify the cipher core via [probeCipherCore]. Its outcome
 *     only shapes the log — the claims below are the verdict, and no driver can fake them.
 *  1. A database created through the keyed URL does NOT carry the plaintext "SQLite format 3"
 *     file header. Fails -> the parameters were ignored and encryption never engaged (this is
 *     exactly how a swapped-in org.xerial driver dies here: stock SQLite knows none of these
 *     URI parameters and writes plaintext).
 *  2. Re-opening through the keyed URL with the SAME key reads the row back.
 *  3. Re-opening with a DIFFERENT key fails — at connect or at first read, either is fine.
 *     Fails (i.e. the wrong key reads happily) -> the "encryption" is decorative.
 *
 * Exits non-zero (via the uncaught exception) on the first broken claim, which is what turns the
 * CI step red; prints one summary line when everything holds.
 */
fun main() {
    Class.forName("org.sqlite.JDBC")
    val file = File.createTempFile("lucent-cipher-check", ".db").apply { delete() }
    val rightKey = "ab".repeat(32)
    val wrongKey = "cd".repeat(32)

    fun openKeyed(hexKey: String): Connection =
        DriverManager.getConnection(keyedSqliteUrl(file, hexKey))

    try {
        // 0 + creation: identify the core (diagnostics), then create a keyed database. No
        // post-connect key pragmas anywhere — the URL is the whole mechanism, as in the app.
        openKeyed(rightKey).use { c ->
            val core = probeCipherCore(c)
            if (core == null) {
                println("NOTE: no probe positively identified the cipher core; the functional checks below are the verdict.")
            } else {
                println("cipher core identified: $core")
            }
            c.createStatement().use { st ->
                st.executeUpdate("CREATE TABLE t(x TEXT)")
                st.executeUpdate("INSERT INTO t VALUES('lucent')")
            }
        }

        // 1: the file on disk must not look like plaintext SQLite.
        val head = ByteArray(16)
        file.inputStream().use { require(it.read(head) == head.size) { "database file is unreadably short" } }
        val plaintextHeader = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)
        require(!head.contentEquals(plaintextHeader)) {
            "the database file still starts with the plaintext SQLite header — encryption did NOT engage"
        }
        println("file header scrambled: not a plaintext SQLite file")

        // 2: the right key reads the row back.
        openKeyed(rightKey).use { c ->
            val got = c.createStatement().use { st ->
                st.executeQuery("SELECT x FROM t").use { rs -> if (rs.next()) rs.getString(1) else null }
            }
            require(got == "lucent") { "keyed re-open read back '$got' instead of the stored row" }
        }
        println("right key reads the data back")

        // 3: a wrong key must NOT get anywhere — the connect itself may throw (SQLITE_NOTADB),
        // or the first read may; both count as correct rejection.
        val wrongKeyWorked = try {
            openKeyed(wrongKey).use { c ->
                c.createStatement().use { st -> st.executeQuery("SELECT count(*) FROM t").close() }
            }
            true
        } catch (_: Throwable) {
            false
        }
        require(!wrongKeyWorked) { "a WRONG key opened the database — the encryption is decorative" }
        println("wrong key rejected")

        println("CIPHER SELF-CHECK OK: at-rest encryption verified end to end.")
    } finally {
        file.delete()
    }
}
