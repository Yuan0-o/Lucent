package com.lucent.app.data

import android.content.Context

/**
 * Desktop stub for the Android SQLCipher key/recovery helper. On the desktop the database is opened
 * through the JDBC driver (see Db.kt), which manages its own keying and has no "set aside an
 * undecryptable database and leave a notice" recovery flow — so there is never a locked notice and
 * these are no-ops. Kept API-compatible so the shared Settings screen compiles unchanged.
 */
object DatabaseEncryption {
    /** No locked-out database on the desktop, so there is never a notice to show. */
    fun lockedNotice(context: Context): String? = null
    fun clearLockedNotice(context: Context) { /* no-op on desktop */ }
    fun purgeSetAsideDatabases(context: Context) { /* no-op on desktop */ }
}
