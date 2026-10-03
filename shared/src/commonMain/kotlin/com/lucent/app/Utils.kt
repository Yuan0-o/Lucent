package com.lucent.app

fun collapseExcessBlankLines(text: String): String {
    return text.replace(Regex("\\n{21,}")) {
        "\n".repeat(20)
    }
}
