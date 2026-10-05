package com.lucent.app.data

import com.lucent.app.platform.PlatformContext
import com.lucent.app.platform.PlatformInputStream
import java.io.File

typealias PlatformFontSource = File

fun openFontSource(context: PlatformContext, source: PlatformFontSource): PlatformInputStream? =
    source.inputStream()

fun fontSourceDisplayName(context: PlatformContext, source: PlatformFontSource): String? =
    source.name.ifBlank { "font" }
