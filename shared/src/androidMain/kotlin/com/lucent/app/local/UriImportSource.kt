package com.lucent.app.local

import android.net.Uri
import okio.source
import android.provider.OpenableColumns
import com.lucent.app.platform.PlatformContext

class UriImportSource(val uri: Uri) : ImportSource {
    override fun displayName(context: PlatformContext): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Throwable) {
        null
    }

    override fun openStream(context: PlatformContext): okio.Source? =
        context.contentResolver.openInputStream(uri)?.let { it.source() }
}
