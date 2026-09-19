package com.lucent.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AppNavigation {

    var requestedScreen by mutableStateOf<Screen?>(null)
        private set

    var pendingNoteId by mutableStateOf<Long?>(null)
        private set

    var pendingTaskId by mutableStateOf<Long?>(null)
        private set

    var returnScreen by mutableStateOf<Screen?>(null)
        private set

    var composeNoteRequested by mutableStateOf(false)
        private set

    var composeTaskRequested by mutableStateOf(false)
        private set

    fun openNote(id: Long, from: Screen? = null) {
        pendingNoteId = id
        returnScreen = from
        requestedScreen = Screen.Notes
    }

    fun openTask(id: Long, from: Screen? = null) {
        pendingTaskId = id
        returnScreen = from
        requestedScreen = Screen.Tasks
    }

    fun requestComposeNote() {
        composeNoteRequested = true
        requestedScreen = Screen.Notes
    }

    fun requestComposeTask() {
        composeTaskRequested = true
        requestedScreen = Screen.Tasks
    }

    fun requestScreen(screen: Screen) {
        requestedScreen = screen
    }

    fun consumeScreen(): Screen? = requestedScreen.also { requestedScreen = null }

    fun consumeNoteId(): Long? = pendingNoteId.also { pendingNoteId = null }

    fun consumeTaskId(): Long? = pendingTaskId.also { pendingTaskId = null }

    fun consumeReturnScreen(): Screen? = returnScreen.also { returnScreen = null }

    fun consumeComposeNote(): Boolean = composeNoteRequested.also { composeNoteRequested = false }

    fun consumeComposeTask(): Boolean = composeTaskRequested.also { composeTaskRequested = false }
}
