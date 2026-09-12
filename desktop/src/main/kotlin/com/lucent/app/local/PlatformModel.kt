package com.lucent.app.local

import android.content.Context
import java.io.File
import java.io.InputStream

/**
 * Desktop seam for the shared [LocalModelStore]: a picked model's source is a plain [File] from
 * the AWT file dialog.
 */
typealias PlatformModelSource = File

/** Opens [source] for reading. Always succeeds for a file that exists; [context] is unused here. */
internal fun openModelSource(context: Context, source: PlatformModelSource): InputStream? =
    source.inputStream()

/** The file's own name, falling back to "model.gguf" when it's blank. */
internal fun modelSourceDisplayName(context: Context, source: PlatformModelSource): String? =
    source.name.ifBlank { "model.gguf" }
