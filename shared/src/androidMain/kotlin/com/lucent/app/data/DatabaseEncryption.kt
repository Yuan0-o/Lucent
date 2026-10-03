package com.lucent.app.data

import android.content.Context
import android.util.Log
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

object DatabaseEncryption {

    private const val TAG = "LucentDbCrypto"
    const val DB_NAME = "lucent.db"

    private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    private const val LOCKED_MARKER = "keys/db-locked.txt"

    @Volatile private var librariesLoaded = false

    fun lockedNotice(context: Context): String? {
        val marker = File(context.applicationContext.filesDir, LOCKED_MARKER)
        return if (marker.exists()) marker.readText().ifBlank { null } else null
    }

    fun purgeSetAsideDatabases(context: Context) {
        val appContext = context.applicationContext
        val dbDir = appContext.getDatabasePath(DB_NAME).parentFile ?: return
        dbDir.listFiles()?.forEach { f ->
            if (f.name.startsWith("$DB_NAME.locked-") || f.name == "$DB_NAME.pre-encrypt") {
                f.delete()
            }
        }
    }

    fun clearLockedNotice(context: Context) {
        File(context.applicationContext.filesDir, LOCKED_MARKER).delete()
    }

    private fun loadLibraries() {
        if (librariesLoaded) return
        synchronized(this) {
            if (librariesLoaded) return
            System.loadLibrary("sqlcipher")
            librariesLoaded = true
        }
    }

    private val neverDelete = DatabaseErrorHandler { _, _ ->  }

    private fun isPlaintextSqlite(file: File): Boolean {
        if (!file.exists() || file.length() < SQLITE_MAGIC.size) return false
        return try {
            file.inputStream().use { input ->
                val head = ByteArray(SQLITE_MAGIC.size)
                if (input.read(head) != head.size) return false
                head.contentEquals(SQLITE_MAGIC)
            }
        } catch (t: Throwable) {
            false
        }
    }

    fun ensureReady(context: Context): String? {
        val appContext = context.applicationContext
        val dbFile = appContext.getDatabasePath(DB_NAME)

        val passphrase = try {
            loadLibraries()
            DataKeys.databasePassphrase(appContext)
        } catch (t: Throwable) {
            EncryptionStatus.reportDatabase(
                EncryptionStatus.State.PLAINTEXT, "SQLCipher unavailable: ${t.message}"
            )
            StartupLog.event(appContext, "db: SQLCipher unavailable; opening unencrypted")
            Log.e(TAG, "SQLCipher unavailable; the database will stay unencrypted", t)
            return null
        }

        if (!dbFile.exists()) {
            EncryptionStatus.reportDatabase(EncryptionStatus.State.ENCRYPTED, "sqlcipher (new file)")
            return passphrase
        }

        if (isPlaintextSqlite(dbFile)) {
            val migrated = migrateToEncrypted(appContext, dbFile, passphrase)
            if (!migrated) {
                EncryptionStatus.reportDatabase(
                    EncryptionStatus.State.PLAINTEXT, "plaintext -> encrypted migration failed"
                )
                StartupLog.event(appContext, "db: encryption migration failed; opening unencrypted")
                Log.e(TAG, "Could not encrypt the database; continuing unencrypted")
                return null
            }
        }

        if (canOpen(dbFile, passphrase)) {
            EncryptionStatus.reportDatabase(EncryptionStatus.State.ENCRYPTED, "sqlcipher")
            return passphrase
        }

        EncryptionStatus.reportDatabase(
            EncryptionStatus.State.LOCKED_OUT, "existing database rejected the stored key"
        )
        setAside(appContext, dbFile)
        return passphrase
    }

    private fun canOpen(dbFile: File, passphrase: String): Boolean = try {
        SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            passphrase,
            null,
            SQLiteDatabase.OPEN_READONLY,
            neverDelete,
            null
        ).use { it.version >= 0 }
        true
    } catch (t: Throwable) {
        Log.e(TAG, "The database could not be decrypted with the stored key", t)
        false
    }

    private fun setAside(context: Context, dbFile: File) {
        val stamp = System.currentTimeMillis()
        val aside = File(dbFile.parentFile, "$DB_NAME.locked-$stamp")
        dbFile.renameTo(aside)
        File(dbFile.absolutePath + "-wal").delete()
        File(dbFile.absolutePath + "-shm").delete()

        val marker = File(context.filesDir, LOCKED_MARKER)
        marker.parentFile?.mkdirs()
        marker.writeText(
            "Your notes database could not be decrypted on this launch, so it was set aside as " +
                "\"${aside.name}\" and a new empty one was created. Nothing has been deleted. " +
                "Import your most recent backup to restore your notes and tasks."
        )
        Log.e(TAG, "Database set aside as ${aside.name}")
    }

    private fun migrateToEncrypted(context: Context, dbFile: File, passphrase: String): Boolean {
        val encrypted = File(dbFile.parentFile, "$DB_NAME.encrypting")
        val original = File(dbFile.parentFile, "$DB_NAME.pre-encrypt")
        encrypted.delete()
        original.delete()

        val wal = File(dbFile.absolutePath + "-wal")
        val shm = File(dbFile.absolutePath + "-shm")

        try {
            SQLiteDatabase.openOrCreateDatabase(dbFile, "", null, neverDelete).use { plain ->
                val schemaVersion = plain.version

                try {
                    plain.rawExecSQL("PRAGMA wal_checkpoint(TRUNCATE)")
                } catch (t: Throwable) {
                    Log.w(TAG, "WAL checkpoint failed; continuing", t)
                }

                plain.rawExecSQL("ATTACH DATABASE ? AS encrypted KEY ?", encrypted.absolutePath, passphrase)
                plain.rawExecSQL("SELECT sqlcipher_export('encrypted')")
                plain.rawExecSQL("PRAGMA encrypted.user_version = $schemaVersion")
                plain.rawExecSQL("DETACH DATABASE encrypted")
            }

            if (!canOpen(encrypted, passphrase)) {
                throw IllegalStateException("The encrypted copy could not be opened with its own key")
            }

            if (!dbFile.renameTo(original)) {
                throw IllegalStateException("Could not move the plaintext database aside")
            }
            wal.delete()
            shm.delete()
            if (!encrypted.renameTo(dbFile)) {
                original.renameTo(dbFile)
                throw IllegalStateException("Could not move the encrypted database into place")
            }

            original.delete()
            Log.i(TAG, "Database encrypted")
            return true
        } catch (t: Throwable) {
            Log.e(TAG, "Database encryption failed; the original has been left untouched", t)
            encrypted.delete()
            if (!dbFile.exists() && original.exists()) original.renameTo(dbFile)
            original.delete()
            return false
        }
    }
}
