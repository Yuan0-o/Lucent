package com.lucent.app.data

import com.lucent.app.platform.PlatformContext
import java.io.File
import java.io.InputStream

actual typealias PlatformFontSource = File

actual fun openFontSource(context: PlatformContext, source: PlatformFontSource): InputStream? =
    source.inputStream()

actual fun fontSourceDisplayName(context: PlatformContext, source: PlatformFontSource): String? =
    source.name.ifBlank { "font" }
