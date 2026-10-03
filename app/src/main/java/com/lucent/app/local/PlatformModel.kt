package com.lucent.app.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.InputStream

typealias PlatformModelSource = Uri

internal fun openModelSource(context: Context, source: PlatformModelSource): InputStream? =
    context.contentResolver.openInputStream(source)

internal fun modelSourceDisplayName(context: Context, source: PlatformModelSource): String? = try {
    context.contentResolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
} catch (_: Throwable) {
    null
}
