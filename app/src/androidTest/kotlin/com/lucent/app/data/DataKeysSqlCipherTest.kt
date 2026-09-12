package com.lucent.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P0-3, task 2.2: the real SQLCipher open/write/reopen cycle, plus a raw rekey primitive check --
 * device-level, because it needs the real Android Keystore (through [DataKeys]) and the real
 * SQLCipher native library, neither of which exists in the sandbox that wrote this file. Not run
 * here; needs a device or emulator. See [AppDatabaseMigrationTest]'s class doc for the same caveat
 * in more detail (no JDK/SDK/emulator available while writing this).
 *
 * ### What this shares with the rest of the app on the test device
 *
 * [DataKeys.databasePassphrase] is the same production singleton the real app uses, and on a test
 * device it reads/creates its key file under the same app data directory an actual install would
 * use (the test APK self-instruments the target app's process). That's intentional -- proving the
 * *real* passphrase opens a *real* SQLCipher-encrypted Room database is the whole point -- but it
 * means this should run against a disposable emulator/CI device, not a phone with real Lucent data,
 * and it deliberately never deletes the key files DataKeys creates (only the test-specific database
 * files this suite creates itself are cleaned up in [tearDown]). [DataKeys.resetCacheForTesting] is
 * still called before reading the passphrase, so a stale in-memory cache from another test in the
 * same instrumentation run can't produce a false pass here.
 *
 * ### Two different SQLite APIs are in play here, on purpose
 *
 * The main cycle test goes through Room (`AppDatabase.openHelper.{writable,readable}Database`,
 * AndroidX's `SupportSQLiteDatabase` -- the same interface [AppDatabaseMigrationTest] already
 * exercises via `MigrationTestHelper`, so `.execSQL(String)` / `.query(String)` are already
 * well-established there). The rekey primitive test at the bottom instead calls
 * `net.zetetic.database.sqlcipher.SQLiteDatabase` directly, the same class
 * [DatabaseEncryption.kt] uses -- and deliberately sticks to only the exact methods witnessed there
 * (`openOrCreateDatabase`, `openDatabase`, `rawExecSQL(String)`, `.version`, `.use { }`), since that
 * file never demonstrates a raw query method on this specific class and this sandbox has no
 * compiler to confirm one. The user_version PRAGMA trick in that last test proves data survived a
 * rekey without needing to guess at a query API this file doesn't otherwise use.
 */
@RunWith(AndroidJUnit4::class)
class DataKeysSqlCipherTest {

    private val TEST_DB_NAME = "datakeys-sqlcipher-cycle-test.db"
    private val REKEY_TEST_DB_NAME = "sqlcipher-rekey-primitive-test.db"

    private val neverDelete = DatabaseErrorHandler { _, _ -> /* deliberately does nothing */ }

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(TEST_DB_NAME)
        context.getDatabasePath(REKEY_TEST_DB_NAME).delete()
    }

    private fun openTestDatabase(context: Context, passphrase: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB_NAME)
            // Mirrors AppDatabase.Companion.build() exactly: the whole point of this test is that
            // DataKeys' real passphrase, fed through this exact factory, opens a real database.
            .openHelperFactory(SupportOpenHelperFactory(passphrase.toByteArray(Charsets.UTF_8)))
            .build()

    @Test
    fun openWriteCloseReopen_dataSurvivesUnderTheRealDataKeysPassphrase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DataKeys.resetCacheForTesting()
        val passphrase = DataKeys.databasePassphrase(context)
        context.deleteDatabase(TEST_DB_NAME)

        var db = openTestDatabase(context, passphrase)
        try {
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO notes (id, title, body, updatedAt, tags, attachments, archived, " +
                    "archivedAt, pinned, color, isChecklist, checklist, trashedAt, manualOrder, " +
                    "isDraft, draftSavedAt, hidden, isDoodle, doodle, bodySpans) VALUES " +
                    "(1, 'Encrypted note', 'Body text', 1000, '', '[]', 0, NULL, 0, '', 0, '[]', " +
                    "NULL, 0, 0, NULL, 0, 0, '', '')"
            )
        } finally {
            db.close()
        }

        // Reopen from a cold start with the same passphrase -- proves this is a real, persistent
        // key, not an artifact of the first connection staying open.
        db = openTestDatabase(context, passphrase)
        try {
            db.openHelper.readableDatabase.query("SELECT title, body FROM notes WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Encrypted note", c.getString(0))
                assertEquals("Body text", c.getString(1))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun wrongPassphraseCannotReadDataEncryptedWithTheRealDataKeysPassphrase() {
        // Proves the encryption is real rather than a pass-through: a key that isn't the one
        // DataKeys produced must not be able to read what was written under the real one.
        val context = ApplicationProvider.getApplicationContext<Context>()
        DataKeys.resetCacheForTesting()
        val realPassphrase = DataKeys.databasePassphrase(context)
        context.deleteDatabase(TEST_DB_NAME)

        val seeded = openTestDatabase(context, realPassphrase)
        try {
            seeded.openHelper.writableDatabase.execSQL(
                "INSERT INTO notes (id, title, body, updatedAt, tags, attachments, archived, " +
                    "archivedAt, pinned, color, isChecklist, checklist, trashedAt, manualOrder, " +
                    "isDraft, draftSavedAt, hidden, isDoodle, doodle, bodySpans) VALUES " +
                    "(1, 'Secret', 'Body text', 1000, '', '[]', 0, NULL, 0, '', 0, '[]', NULL, 0, 0, " +
                    "NULL, 0, 0, '', '')"
            )
        } finally {
            seeded.close()
        }

        val wrongPassphrase = "x'" + "00".repeat(32) + "'"
        val reopened = openTestDatabase(context, wrongPassphrase)
        var readSucceeded = true
        try {
            reopened.openHelper.readableDatabase.query("SELECT title FROM notes").use { it.moveToFirst() }
        } catch (t: Throwable) {
            readSucceeded = false
        } finally {
            reopened.close()
        }
        assertFalse(
            "A wrong passphrase must not be able to read data encrypted with the real DataKeys key",
            readSucceeded
        )
    }

    @Test
    fun rawSqlCipherRekeyPrimitiveChangesTheEffectiveKeyWithoutLosingTheDatabase() {
        // The app itself does not currently expose a "change the database's key" feature --
        // DatabaseEncryption.kt only ever migrates plaintext -> encrypted, never re-encrypts an
        // already-encrypted file under a new key. This exercises SQLCipher's own `PRAGMA rekey`
        // primitive directly (bypassing Room and DataKeys entirely) so the underlying capability is
        // proven to work, groundwork for a future key-rotation feature -- it does not claim this
        // reflects any behaviour the app has today. A distinctive PRAGMA user_version, set before
        // the rekey and checked after, is the proof that the SAME file -- not a fresh empty one --
        // is what the new key opens.
        System.loadLibrary("sqlcipher")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath(REKEY_TEST_DB_NAME)
        dbFile.delete()

        val oldKey = "x'" + "11".repeat(32) + "'"
        val newKey = "x'" + "22".repeat(32) + "'"
        val marker = 424242

        SQLiteDatabase.openOrCreateDatabase(dbFile, oldKey, null, neverDelete).use { db ->
            db.rawExecSQL("PRAGMA user_version = $marker")
            db.rawExecSQL("PRAGMA rekey = \"$newKey\"")
        }

        var oldKeyStillOpens = true
        try {
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath, oldKey, null, SQLiteDatabase.OPEN_READONLY, neverDelete, null
            ).use { it.version }
        } catch (t: Throwable) {
            oldKeyStillOpens = false
        }
        assertFalse("After rekey, the old key must no longer open the database", oldKeyStillOpens)

        SQLiteDatabase.openDatabase(
            dbFile.absolutePath, newKey, null, SQLiteDatabase.OPEN_READONLY, neverDelete, null
        ).use { db ->
            assertEquals(marker, db.version)
        }
    }
}
