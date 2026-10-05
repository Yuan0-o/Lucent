package com.lucent.app.data

import com.lucent.app.platform.PlatformContext
import java.io.File

actual fun openFontSource(context: PlatformContext, source: Any): java.io.InputStream? =
    (source as File).inputStream()

actual fun fontSourceDisplayName(context: PlatformContext, source: Any): String? =
    (source as File).name.ifBlank { "font" }
