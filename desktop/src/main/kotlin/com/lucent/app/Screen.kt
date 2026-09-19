package com.lucent.app

enum class Screen {
    Tasks, Notes, Assistant, Search, Insights, Settings;

    val label: String
        get() = when (this) {
            Tasks -> com.lucent.app.i18n.S.tabTasks
            Notes -> com.lucent.app.i18n.S.tabNotes
            Assistant -> com.lucent.app.i18n.S.tabAssistant
            Search -> com.lucent.app.i18n.S.tabSearch
            Insights -> com.lucent.app.i18n.S.tabInsights
            Settings -> com.lucent.app.i18n.S.tabSettings
        }
}
