package com.lucent.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.InputStream

/**
 * Android seam for the shared [FontStore]: a picked font's source is a content [Uri] from the
 * system file picker.
 */
typealias PlatformFontSource = Uri

/** Opens [source] for reading, or null if the resolver can't (a revoked grant, a stale Uri). */
internal fun openFontSource(context: Context, source: PlatformFontSource): InputStream? =
    context.contentResolver.openInputStream(source)

/** The picked file's display name, or null when the resolver has none to offer. */
internal fun fontSourceDisplayName(context: Context, source: PlatformFontSource): String? = try {
    context.contentResolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
} catch (_: Throwable) {
    null
}
