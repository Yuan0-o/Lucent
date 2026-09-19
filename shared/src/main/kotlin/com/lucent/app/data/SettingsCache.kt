package com.lucent.app.data

object SettingsCache {

    @Volatile
    var assistantName: String? = null
        private set

    @Volatile
    var notesSort: String? = null
        private set

    @Volatile
    var tasksSort: String? = null
        private set

    @Volatile
    var sessionSnapshot: String? = null
        private set

    fun seed(prefs: SettingsRepository.StartupPrefs) {
        assistantName = prefs.assistantName
        notesSort = prefs.notesSort
        tasksSort = prefs.tasksSort
        sessionSnapshot = prefs.sessionSnapshot.ifBlank { null }
    }
}
