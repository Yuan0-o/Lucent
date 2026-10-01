package com.lucent.app.harness.plugins

enum class RuntimeBackend {
    TERMUX, BUILTIN, NONE
}

fun selectBackend(mode: String, termuxReady: Boolean, builtinAvailable: Boolean): RuntimeBackend {
    return when (mode) {
        "termux" -> if (termuxReady) RuntimeBackend.TERMUX else RuntimeBackend.NONE
        "builtin" -> if (builtinAvailable) RuntimeBackend.BUILTIN else RuntimeBackend.NONE
        else -> {
            if (builtinAvailable) RuntimeBackend.BUILTIN
            else if (termuxReady) RuntimeBackend.TERMUX
            else RuntimeBackend.NONE
        }
    }
}
