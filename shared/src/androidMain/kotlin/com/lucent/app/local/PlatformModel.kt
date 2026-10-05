package com.lucent.app.local

import android.net.Uri
import android.provider.OpenableColumns
import com.lucent.app.platform.PlatformContext

actual fun openModelSource(context: PlatformContext, source: Any): java.io.InputStream? =
    context.contentResolver.openInputStream(source as Uri)

actual fun modelSourceDisplayName(context: PlatformContext, source: Any): String? = try {
    context.contentResolver.query(source as Uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
} catch (_: Throwable) {
    null
}
