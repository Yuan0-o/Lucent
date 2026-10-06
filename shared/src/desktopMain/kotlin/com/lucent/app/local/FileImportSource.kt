package com.lucent.app.local

import com.lucent.app.platform.PlatformContext
import java.io.File

class FileImportSource(val file: File) : ImportSource {
    override fun displayName(context: PlatformContext): String? =
        file.name.ifBlank { null }

    override fun openStream(context: PlatformContext): okio.Source? =
        okio.FileSystem.SYSTEM.source(okio.Path.Companion.toPath(file.absolutePath))
}
