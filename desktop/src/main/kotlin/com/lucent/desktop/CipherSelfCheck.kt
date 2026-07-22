package com.lucent.desktop

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * End-to-end proof that the desktop database is really encrypted at rest — run in CI by the
 * `:desktop:cipherSelfCheck` Gradle task (see build.gradle.kts) as a red/green gate before the
 * installer is packaged.
 *
 * Deliberately standalone: plain JDBC against a temp file, no app classes, no Context — so it
 * exercises exactly one thing, the driver Gradle resolved, and can never rot along with UI code.
 * It prints one diagnostic and asserts the three functional claims that together mean
 * "encrypted", in order of how they fail:
 *
 *  0. (Diagnostic, never a gate.) Identify the cipher core with the app's own
 *     [com.lucent.app.data.probeCipherCore] — the same code Db.kt runs at startup, so CI
 *     exercises exactly what ships. The first version of this check hard-required
 *     `PRAGMA cipher_version` here and went red on 2026-07-22 08:36 for two stacked reasons:
 *     that pragma is SQLCipher vocabulary the MC core doesn't answer, and this driver's
 *     `executeQuery` THROWS on zero-column statements instead of returning an empty set.
 *  1. A database created under `PRAGMA cipher='sqlcipher'; legacy=4; key=x'…'` does NOT carry
 *     the plaintext "SQLite format 3" file header. Fails -> the pragmas ran but encryption
 *     never engaged (this is also exactly how a swapped-in org.xerial driver dies here: stock
 *     SQLite ignores the unknown pragmas and writes plaintext).
 *  2. Re-opening with the SAME key reads the row back.
 *  3. Re-opening with a DIFFERENT key fails. Fails (i.e. the wrong key reads happily) -> the
 *     "encryption" is decorative.
 *
 * Exits non-zero (via the uncaught exception) on the first broken claim, which is what turns the
 * CI step red; prints one summary line when everything holds.
 */
fun main() {
    Class.forName("org.sqlite.JDBC")
    val file = File.createTempFile("lucent-cipher-check", ".db").apply { delete() }
    val rightKey = "x'" + "ab".repeat(32) + "'"
    val wrongKey = "x'" + "cd".repeat(32) + "'"

    fun open(): Connection = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
    fun Connection.keyWith(key: String) = createStatement().use { st ->
        st.execute("PRAGMA cipher='sqlcipher'")
        st.execute("PRAGMA legacy=4")
        st.execute("PRAGMA key=\"$key\"")
    }

    try {
        // 0: diagnostics with the app's own probe, then create a keyed database. The probe's
        // outcome is printed but never gates the check — the functional claims below decide.
        open().use { c ->
            val core = com.lucent.app.data.probeCipherCore(c)
            if (core == null) {
                println("NOTE: no probe positively identified the cipher core; the functional checks below are the verdict.")
            } else {
                println("cipher core identified: $core")
            }
            c.keyWith(rightKey)
            c.createStatement().use { st ->
                st.executeUpdate("CREATE TABLE t(x TEXT)")
                st.executeUpdate("INSERT INTO t VALUES('lucent')")
            }
        }
        val head = ByteArray(16)
        file.inputStream().use { require(it.read(head) == head.size) { "database file is unreadably short" } }
        val plaintextHeader = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)
        require(!head.contentEquals(plaintextHeader)) {
            "the database file still starts with the plaintext SQLite header — encryption did NOT engage"
        }
        println("file header scrambled: not a plaintext SQLite file")

        // 3: the right key reads the row back.
        open().use { c ->
            c.keyWith(rightKey)
            val got = c.createStatement().use { st ->
                st.executeQuery("SELECT x FROM t").use { rs -> if (rs.next()) rs.getString(1) else null }
            }
            require(got == "lucent") { "keyed re-open read back '$got' instead of the stored row" }
        }
        println("right key reads the data back")

        // 4: a wrong key must NOT read anything.
        val wrongKeyWorked = try {
            open().use { c ->
                c.keyWith(wrongKey)
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
