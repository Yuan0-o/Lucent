package com.lucent.desktop

import com.lucent.app.data.keyedSqliteUrl
import com.lucent.app.data.probeCipherCore
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

fun main() {
    Class.forName("org.sqlite.JDBC")
    val file = File.createTempFile("lucent-cipher-check", ".db").apply { delete() }
    val rightKey = "ab".repeat(32)
    val wrongKey = "cd".repeat(32)

    fun openKeyed(hexKey: String): Connection =
        DriverManager.getConnection(keyedSqliteUrl(file, hexKey))

    try {
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

        val head = ByteArray(16)
        file.inputStream().use { require(it.read(head) == head.size) { "database file is unreadably short" } }
        val plaintextHeader = "SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1)
        require(!head.contentEquals(plaintextHeader)) {
            "the database file still starts with the plaintext SQLite header — encryption did NOT engage"
        }
        println("file header scrambled: not a plaintext SQLite file")

        openKeyed(rightKey).use { c ->
            val got = c.createStatement().use { st ->
                st.executeQuery("SELECT x FROM t").use { rs -> if (rs.next()) rs.getString(1) else null }
            }
            require(got == "lucent") { "keyed re-open read back '$got' instead of the stored row" }
        }
        println("right key reads the data back")

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
