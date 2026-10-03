package com.lucent.app.data

import android.net.Uri
import android.provider.OpenableColumns
import com.lucent.app.platform.PlatformContext
import com.lucent.app.platform.PlatformInputStream

typealias PlatformFontSource = Uri

fun openFontSource(context: PlatformContext, source: PlatformFontSource): PlatformInputStream? =
    context.contentResolver.openInputStream(source)

fun fontSourceDisplayName(context: PlatformContext, source: PlatformFontSource): String? = try {
    context.contentResolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
} catch (_: Throwable) {
    null
}
