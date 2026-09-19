package com.lucent.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.InputStream

typealias PlatformFontSource = Uri

internal fun openFontSource(context: Context, source: PlatformFontSource): InputStream? =
    context.contentResolver.openInputStream(source)

internal fun fontSourceDisplayName(context: Context, source: PlatformFontSource): String? = try {
    context.contentResolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
} catch (_: Throwable) {
    null
}
