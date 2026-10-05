package com.lucent.app.ui

internal object SettingsScrollMemory {

    private val offsets = mutableMapOf<SettingsRoute, Int>()

    fun of(route: SettingsRoute): Int = offsets[route] ?: 0

    fun write(route: SettingsRoute, offset: Int) {
        offsets[route] = offset
    }
}
