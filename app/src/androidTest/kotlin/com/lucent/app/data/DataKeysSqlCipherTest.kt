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

@RunWith(AndroidJUnit4::class)
class DataKeysSqlCipherTest {

    private val TEST_DB_NAME = "datakeys-sqlcipher-cycle-test.db"
    private val REKEY_TEST_DB_NAME = "sqlcipher-rekey-primitive-test.db"

    private val neverDelete = DatabaseErrorHandler { _, _ ->  }

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(TEST_DB_NAME)
        context.getDatabasePath(REKEY_TEST_DB_NAME).delete()
    }

    private fun openTestDatabase(context: Context, passphrase: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB_NAME)
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
