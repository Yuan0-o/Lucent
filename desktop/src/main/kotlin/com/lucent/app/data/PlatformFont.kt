package com.lucent.app.data

import android.content.Context
import java.io.File
import java.io.InputStream

typealias PlatformFontSource = File

internal fun openFontSource(context: Context, source: PlatformFontSource): InputStream? =
    source.inputStream()

internal fun fontSourceDisplayName(context: Context, source: PlatformFontSource): String? =
    source.name.ifBlank { "font" }
