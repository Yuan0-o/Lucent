package com.lucent.app.data

import android.content.Context
import java.io.File
import java.io.InputStream

/**
 * Desktop seam for the shared [FontStore]: a picked font's source is a plain [File] from the AWT
 * file dialog.
 */
typealias PlatformFontSource = File

/** Opens [source] for reading. Always succeeds for a file that exists; [context] is unused here. */
internal fun openFontSource(context: Context, source: PlatformFontSource): InputStream? =
    source.inputStream()

/** The file's own name, falling back to "font" when it's blank. */
internal fun fontSourceDisplayName(context: Context, source: PlatformFontSource): String? =
    source.name.ifBlank { "font" }
