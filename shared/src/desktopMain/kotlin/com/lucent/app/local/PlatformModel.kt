package com.lucent.app.local

import com.lucent.app.platform.PlatformContext
import java.io.File

actual fun openModelSource(context: PlatformContext, source: Any): java.io.InputStream? =
    (source as File).inputStream()

actual fun modelSourceDisplayName(context: PlatformContext, source: Any): String? =
    (source as File).name.ifBlank { "model.gguf" }
