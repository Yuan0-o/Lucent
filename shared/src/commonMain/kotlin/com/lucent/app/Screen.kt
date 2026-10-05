package com.lucent.app

enum class Screen {
    Tasks, Notes, Notebooks, Drafts, Archive, Trash, Hidden, Assistant, Search, Insights, Settings;

    val label: String
        get() = when (this) {
            Tasks -> com.lucent.app.i18n.S.tabTasks
            Notes -> com.lucent.app.i18n.S.tabNotes
            Notebooks -> com.lucent.app.i18n.S.screenNotebooks
            Drafts -> com.lucent.app.i18n.S.screenDrafts
            Archive -> com.lucent.app.i18n.S.navArchive
            Trash -> com.lucent.app.i18n.S.screenTrash
            Hidden -> com.lucent.app.i18n.S.screenHidden
            Assistant -> com.lucent.app.i18n.S.tabAssistant
            Search -> com.lucent.app.i18n.S.tabSearch
            Insights -> com.lucent.app.i18n.S.tabInsights
            Settings -> com.lucent.app.i18n.S.tabSettings
        }

    val panel: com.lucent.app.ui.HomePanel?
        get() = when (this) {
            Drafts -> com.lucent.app.ui.HomePanel.Drafts
            Archive -> com.lucent.app.ui.HomePanel.Archive
            Trash -> com.lucent.app.ui.HomePanel.Trash
            Hidden -> com.lucent.app.ui.HomePanel.Hidden
            else -> null
        }
}
