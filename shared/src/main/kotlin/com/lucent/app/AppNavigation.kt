package com.lucent.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lucent.app.ui.HomePanel
import com.lucent.app.ui.SettingsRoute

internal object SettingsNav {
    var handler: ((SettingsRoute) -> Unit)? = null

    fun go(route: SettingsRoute) {
        handler?.invoke(route)
    }
}

object AppNavigation {

    var requestedScreen by mutableStateOf<Screen?>(null)
        private set

    var pendingNoteId by mutableStateOf<Long?>(null)
        private set

    var pendingTaskId by mutableStateOf<Long?>(null)
        private set

    var returnScreen by mutableStateOf<Screen?>(null)
        private set

    internal var settingsRoute by mutableStateOf(SettingsRoute.Root)
        private set

    var composeNoteRequested by mutableStateOf(false)
        private set

    var composeTaskRequested by mutableStateOf(false)
        private set

    var requestedPanel by mutableStateOf<HomePanel?>(null)
        private set

    var pendingEditNoteId by mutableStateOf<Long?>(null)
        private set

    var pendingEditTaskId by mutableStateOf<Long?>(null)
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

    fun requestPanel(panel: HomePanel) {
        requestedPanel = panel
    }

    fun editNote(id: Long) {
        pendingEditNoteId = id
        requestedScreen = Screen.Notes
    }

    fun editTask(id: Long) {
        pendingEditTaskId = id
        requestedScreen = Screen.Tasks
    }

    internal fun rememberSettingsRoute(route: SettingsRoute) {
        settingsRoute = route
    }

    internal fun resetSettingsRoute() {
        settingsRoute = SettingsRoute.Root
    }

    fun consumeScreen(): Screen? = requestedScreen.also { requestedScreen = null }

    fun consumeNoteId(): Long? = pendingNoteId.also { pendingNoteId = null }

    fun consumeTaskId(): Long? = pendingTaskId.also { pendingTaskId = null }

    fun consumeReturnScreen(): Screen? = returnScreen.also { returnScreen = null }

    fun consumeComposeNote(): Boolean = composeNoteRequested.also { composeNoteRequested = false }

    fun consumeComposeTask(): Boolean = composeTaskRequested.also { composeTaskRequested = false }

    fun consumePanel(): HomePanel? = requestedPanel.also { requestedPanel = null }

    fun consumeEditNoteId(): Long? = pendingEditNoteId.also { pendingEditNoteId = null }

    fun consumeEditTaskId(): Long? = pendingEditTaskId.also { pendingEditTaskId = null }

    private var backClaims by mutableStateOf(0)

    val innerBackActive: Boolean
        get() = backClaims > 0

    internal fun claimBack() {
        backClaims += 1
    }

    internal fun releaseBack() {
        backClaims = (backClaims - 1).coerceAtLeast(0)
    }
}

@Composable
fun BackClaim(active: Boolean) {
    DisposableEffect(active) {
        if (active) AppNavigation.claimBack()
        onDispose { if (active) AppNavigation.releaseBack() }
    }
}
