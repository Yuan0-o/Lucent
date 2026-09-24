package com.lucent.app.data

enum class SplashStyle(val key: String) {
    CAT("cat"),
    PEN("pen");

    companion object {
        val DEFAULT = CAT

        fun fromKey(key: String?): SplashStyle = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
