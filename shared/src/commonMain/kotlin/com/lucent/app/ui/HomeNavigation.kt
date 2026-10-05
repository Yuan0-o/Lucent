package com.lucent.app.ui

import androidx.compose.runtime.mutableStateOf
import com.lucent.app.Screen

enum class HomeMode {
    Tasks, Notes;

    val label: String
        get() = when (this) {
            Tasks -> com.lucent.app.i18n.S.tabTasks
            Notes -> com.lucent.app.i18n.S.tabNotes
        }

    val screen: Screen
        get() = when (this) {
            Tasks -> Screen.Tasks
            Notes -> Screen.Notes
        }

    fun other(): HomeMode = if (this == Tasks) Notes else Tasks

    companion object {
        fun of(screen: Screen?): HomeMode? = when (screen) {
            Screen.Tasks -> Tasks
            Screen.Notes -> Notes
            else -> null
        }
    }
}

enum class HomePanel {
    Drafts, Archive, Trash, Hidden;

    val title: String
        get() = when (this) {
            Drafts -> com.lucent.app.i18n.S.screenDrafts
            Archive -> com.lucent.app.i18n.S.navArchive
            Trash -> com.lucent.app.i18n.S.screenTrash
            Hidden -> com.lucent.app.i18n.S.screenHidden
        }

    fun label(mode: HomeMode): String = when {
        this == Archive && mode == HomeMode.Tasks -> com.lucent.app.i18n.S.screenCompletedTasks
        this == Archive -> com.lucent.app.i18n.S.screenArchivedNotes
        else -> title
    }

    val logKey: String
        get() = name.lowercase()

    companion object {
        fun visible(hiddenVisible: Boolean): List<HomePanel> =
            entries.filter { it != Hidden || hiddenVisible }
    }
}

object HomeSearch {
    val query = mutableStateOf("")

    fun clear() {
        query.value = ""
    }
}
