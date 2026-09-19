package com.lucent.app.ui

import com.lucent.app.Screen

object LastScreen {

    @Volatile
    var current: Screen = Screen.Tasks

    fun remember(screen: Screen) {
        current = screen
    }

    fun hydrate(storedName: String?) {
        if (storedName.isNullOrBlank()) return
        current = Screen.entries.firstOrNull { it.name == storedName } ?: Screen.Tasks
    }

    fun persistedName(): String = current.name
}
