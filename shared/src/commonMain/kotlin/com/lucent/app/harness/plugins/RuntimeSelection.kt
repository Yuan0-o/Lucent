package com.lucent.app.harness.plugins

enum class RuntimeBackend {
    BUILTIN, NONE
}

fun selectBackend(builtinAvailable: Boolean): RuntimeBackend {
    return if (builtinAvailable) RuntimeBackend.BUILTIN else RuntimeBackend.NONE
}
