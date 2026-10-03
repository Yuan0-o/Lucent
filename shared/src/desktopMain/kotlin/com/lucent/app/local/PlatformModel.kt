package com.lucent.app.local

import com.lucent.app.platform.PlatformContext
import com.lucent.app.platform.PlatformInputStream
import java.io.File

typealias PlatformModelSource = File

fun openModelSource(context: PlatformContext, source: PlatformModelSource): PlatformInputStream? =
    source.inputStream()

fun modelSourceDisplayName(context: PlatformContext, source: PlatformModelSource): String? =
    source.name.ifBlank { "model.gguf" }
