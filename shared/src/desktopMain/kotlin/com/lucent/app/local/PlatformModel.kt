package com.lucent.app.local

import com.lucent.app.platform.PlatformContext
import java.io.File
import java.io.InputStream

actual typealias PlatformModelSource = File

actual fun openModelSource(context: PlatformContext, source: PlatformModelSource): InputStream? =
    source.inputStream()

actual fun modelSourceDisplayName(context: PlatformContext, source: PlatformModelSource): String? =
    source.name.ifBlank { "model.gguf" }
