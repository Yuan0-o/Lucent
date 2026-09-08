package com.lucent.app.data

import android.content.Context
import java.io.File
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * P0-4: the two desktop database paths that can destroy data silently — the plaintext rekey and
 * the wrong-key open — plus proof that a fresh store is really encrypted at rest.
 *
 * Each test uses its own directory and its own key material (DataKeys mints per-context), and the
 * master-key store is pointed at the same directory so nothing touches a real profile.
 */
class DbEncryptionTest {

    private class TestContext(private val dir: File) : Context() {
        override val filesDir: File get() = dir
    }

    private fun freshDir(): File =
        File(System.getProperty("java.io.tmpdir"), "lucent-db-enc-${System.nanoTime()}")
            .apply { mkdirs() }

    private suspend fun use(dir: File, block: suspend () -> Unit) {
        LocalSecrets.filesDirOverride = dir
        LocalSecrets.resetForTesting()
        DataKeys.resetCacheForTesting()
        try {
            block()
        } finally {
            LocalSecrets.filesDirOverride = null
            LocalSecrets.resetForTesting()
            DataKeys.resetCacheForTesting()
        }
    }

    private fun headerIsPlaintext(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        val head = ByteArray(16)
        file.inputStream().use { if (it.read(head) != 16) return false }
        return head.contentEquals("SQLite format 3\u0000".toByteArray(Charsets.ISO_8859_1))
    }

    @Test
    fun freshStoreIsEncryptedAtRest() = runBlocking {
        val dir = freshDir()
        use(dir) {
            val db = Db.open(TestContext(dir))
            db.use { conn ->
                conn.createStatement().use { st ->
                    st.executeUpdate("INSERT INTO notes (title, body, updatedAt) VALUES ('a', 'b', 1)")
                }
            }
            val file = File(dir, "lucent.db")
            assertTrue(file.exists())
            // Page 1 of an encrypted store never begins with the plain-SQLite magic header.
            assertFalse(headerIsPlaintext(file), "fresh store must be encrypted at rest")
        }
    }

    @Test
    fun plaintextStoreIsRekeyedInPlaceAndEveryRowSurvives() = runBlocking {
        val dir = freshDir()
        use(dir) {
            // Build a legacy plaintext store exactly as the pre-release org.xerial era left them:
            // a real SQLite file, no cipher, with user data inside.
            Class.forName("org.sqlite.JDBC")
            val plainFile = File(dir, "lucent.db")
            DriverManager.getConnection("jdbc:sqlite:${plainFile.absolutePath}").use { conn ->
                conn.createStatement().use { st ->
                    st.executeUpdate(
                        "CREATE TABLE notes (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "title TEXT NOT NULL, body TEXT NOT NULL, updatedAt INTEGER NOT NULL)"
                    )
                    st.executeUpdate("INSERT INTO notes (title, body, updatedAt) VALUES ('legacy', 'row', 1700000000000)")
                }
            }
            assertTrue(headerIsPlaintext(plainFile))

            // Opening through Db must encrypt it in place, not copy data out or refuse to start.
            val db = Db.open(TestContext(dir))
            val (title, body) = db.use { conn ->
                conn.createStatement().use { st ->
                    st.executeQuery("SELECT title, body FROM notes").use { rs ->
                        rs.next()
                        rs.getString(1) to rs.getString(2)
                    }
                }
            }
            assertEquals("legacy", title)
            assertEquals("row", body)
            assertFalse(headerIsPlaintext(plainFile), "rekey must leave the header encrypted")
        }
    }

    @Test
    fun wrongKeyOnExistingEncryptedStoreThrowsActionableError() = runBlocking {
        // Two installs: each mints its own database key. Take the first install's encrypted store
        // and try to open it with the second install's key — the exact "restored the db but not
        // the keys" accident. It must throw a message a person can act on, and it must NOT create
        // a second, empty database over the existing one.
        val dirA = freshDir()
        val dirB = freshDir()
        use(dirA) { Db.open(TestContext(dirA)) }
        use(dirB) { Db.open(TestContext(dirB)) }

        val fileA = File(dirA, "lucent.db")
        assertFalse(headerIsPlaintext(fileA))
        val preCopyBytes = fileA.readBytes()

        use(dirB) {
            File(dirA, "lucent.db").copyTo(File(dirB, "lucent.db"), overwrite = true)
            val err = assertFailsWith<IllegalStateException> { Db.open(TestContext(dirB)) }
            assertTrue(
                err.message?.contains("could not be unlocked") == true,
                "message should say the store could not be unlocked, got: ${err.message}"
            )
            assertTrue(err.message!!.contains(".lcb"), "message should point at the .lcb backup")

            // The original bytes were not overwritten by a fresh empty database.
            assertEquals(preCopyBytes.toList(), File(dirB, "lucent.db").readBytes().toList())
        }
    }
}
