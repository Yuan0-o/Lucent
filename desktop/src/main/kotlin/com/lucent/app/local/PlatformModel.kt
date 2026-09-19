package com.lucent.app.local

import android.content.Context
import java.io.File
import java.io.InputStream

typealias PlatformModelSource = File

internal fun openModelSource(context: Context, source: PlatformModelSource): InputStream? =
    source.inputStream()

internal fun modelSourceDisplayName(context: Context, source: PlatformModelSource): String? =
    source.name.ifBlank { "model.gguf" }
