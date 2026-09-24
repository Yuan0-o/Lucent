package com.lucent.app.ui

import com.lucent.app.Screen

object LastScreen {

    @Volatile
    var current: Screen = Screen.Tasks

    @Volatile
    var home: Screen = Screen.Tasks

    @Volatile
    var homeMode: HomeMode = HomeMode.Tasks

    fun remember(screen: Screen) {
        current = screen
        if (screen != Screen.Settings) home = screen
        HomeMode.of(screen)?.let { homeMode = it }
    }

    fun hydrate(storedName: String?) {
        if (storedName.isNullOrBlank()) return
        current = Screen.entries.firstOrNull { it.name == storedName } ?: Screen.Tasks
        if (current != Screen.Settings) home = current
        HomeMode.of(current)?.let { homeMode = it }
    }

    fun persistedName(): String = current.name
}
