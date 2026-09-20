package com.lucent.app.ui

import com.lucent.app.Screen

object LastScreen {

    @Volatile
    var current: Screen = Screen.Tasks

    @Volatile
    var home: Screen = Screen.Tasks

    fun remember(screen: Screen) {
        current = screen
        if (screen != Screen.Settings) home = screen
    }

    fun hydrate(storedName: String?) {
        if (storedName.isNullOrBlank()) return
        current = Screen.entries.firstOrNull { it.name == storedName } ?: Screen.Tasks
        if (current != Screen.Settings) home = current
    }

    fun persistedName(): String = current.name
}
