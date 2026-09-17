package com.lucent.app.local

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri

/**
 * P3-2 — a process-wide handle on the application [Context], captured automatically the moment
 * the main process starts, before MainActivity, before AssistantController, before anything else
 * in this app runs.
 *
 * ### Why this exists
 * Several of [LocalLlm]'s public functions that P3-2 turns into IPC calls — [LocalLlm.isSupported],
 * [LocalLlm.stop], [LocalLlm.setGpuEnabled], [LocalLlm.generate], [LocalLlm.shutdown] — are called
 * from [com.lucent.app.ui.AssistantController] (shared code this task must not edit) with no
 * `Context` parameter, exactly as before. [LocalLlm.ensureLoaded] is the one call that DOES
 * receive a `Context`, but it is not guaranteed to be the first call made — [LocalLlm.isSupported]
 * in particular is read as an early gate, and Settings can query it before the user has ever sent
 * a local-mode message. [LocalLlmProxy] needs a `Context` regardless of call order to check the
 * packaged native library and to bind to [com.lucent.app.GenerationService], so something has to
 * make one available independent of any specific call site.
 *
 * ### The mechanism
 * This is the well-established "silent ContentProvider" trick (the same one AndroidX Startup and
 * Firebase's own auto-initialization use): [LocalLlmInitProvider] below declares no
 * `android:process` in the manifest, so Android instantiates it — and calls its [ContentProvider.onCreate] —
 * ONLY in the app's default (main) process, as one of the very first things that happens when
 * that process starts (before [android.app.Application.onCreate]). The isolated ":llm" process
 * never creates this provider at all, and never needs to: `GenerationService`, which is what runs
 * there, already has a `Context` of its own via `Service.getApplicationContext()`.
 *
 * No `Application` subclass exists in this app (AndroidManifest.xml's `<application>` tag has no
 * `android:name`), so adding one just for this would be a much larger, more central change than
 * this small, additive, unexported provider.
 */
object LocalLlmContextHolder {

    /**
     * Non-null from very early in the main process's lifetime onward (see class doc). The `?`
     * exists only for the theoretical window before [LocalLlmInitProvider.onCreate] has run —
     * callers should treat a null here as "not available yet" and fail soft, never crash.
     */
    @Volatile
    var appContext: Context? = null
        internal set
}

/**
 * Does nothing except capture the application [Context] into [LocalLlmContextHolder] on startup.
 * Not exported — nothing outside this app can query it, and nothing needs to: it carries no data,
 * it is purely an initialization hook. See [LocalLlmContextHolder] for why this exists.
 */
class LocalLlmInitProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        LocalLlmContextHolder.appContext = context?.applicationContext
        return true
    }

    // Genuinely a no-op provider: it holds no data, so every query/mutation method below is inert.
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
