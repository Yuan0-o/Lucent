package com.lucent.app.data

import com.lucent.app.platform.PlatformContext
import com.lucent.app.platform.PlatformInputStream
import java.io.File

actual typealias PlatformFontSource = File

actual fun openFontSource(context: PlatformContext, source: PlatformFontSource): java.io.InputStream? =
    source.inputStream()

actual fun fontSourceDisplayName(context: PlatformContext, source: PlatformFontSource): String? =
    source.name.ifBlank { "font" }
