package com.lucent.app.local

import android.net.Uri
import android.provider.OpenableColumns
import com.lucent.app.platform.PlatformContext
import java.io.InputStream

actual typealias PlatformModelSource = Uri

actual fun openModelSource(context: PlatformContext, source: PlatformModelSource): PlatformInputStream? =
    context.contentResolver.openInputStream(source)

actual fun modelSourceDisplayName(context: PlatformContext, source: PlatformModelSource): String? = try {
    context.contentResolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
} catch (_: Throwable) {
    null
}
