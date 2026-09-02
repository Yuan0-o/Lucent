package com.lucent.app.ui

import com.lucent.app.Screen

/**
 * Remembers which tab the user was on, across the app-lock gate (C-group task 8).
 *
 * ### The bug this fixes
 *
 * `LucentApp`'s current tab lived in `rememberSaveable { mutableStateOf(Screen.Tasks) }`. While the
 * lock screen is showing, `LucentApp` is **not composed at all** — that is deliberate and correct,
 * because it is what guarantees nothing behind the lock can be read or touched. But it also means
 * the whole composition, and every `remember`/`rememberSaveable` inside it, is *disposed*. When the
 * user unlocks, `LucentApp` is composed from scratch and its initial value — `Screen.Tasks` — wins.
 *
 * So every unlock landed on the home tab regardless of where the user had been, and any half-typed
 * edit on another tab was gone with the composition.
 *
 * `rememberSaveable` cannot fix this. It restores across *configuration changes and process death*,
 * where the composable is re-created in place; it does not survive its parent being removed from
 * the tree, which is exactly what the lock gate does.
 *
 * ### Why a process-level object
 *
 * The value has to outlive the composition, so it has to live outside it. This is the same shape
 * [AppLockController] already uses for the lock state itself, and for the same reason — both are
 * facts about the *process*, not about any one composition.
 *
 * [current] is a plain field rather than snapshot state on purpose: it is written *by* the tab
 * switch and read only when a fresh composition needs its starting value, so making it observable
 * would invite a recomposition loop for no benefit.
 *
 * ### Scope, stated plainly
 *
 * This restores the **tab**. It does not restore an open editor or unsaved text inside one — that
 * needs the editor's own draft persistence, which is A-group's draft-area work; see the hook
 * comment in `MainActivity`. Restoring the tab is what task 8 asked for and it is what stops the
 * most common form of the loss: unlocking, finding yourself on Tasks, and having to navigate back
 * to a note you were halfway through.
 */
object LastScreen {

    /**
     * The tab to open on the next composition of `LucentApp`. Defaults to the app's own home tab,
     * so a genuinely fresh process still starts where it always did.
     */
    @Volatile
    var current: Screen = Screen.Tasks

    /** Called whenever the user changes tab, so the value is always the *last* one they chose. */
    fun remember(screen: Screen) {
        current = screen
    }

    /**
     * Hydrate from the persisted value at startup, so a cold start also returns to the last tab
     * rather than to the home tab. Unknown or absent names fall back to the default, which keeps an
     * install that predates this setting working without a migration.
     */
    fun hydrate(storedName: String?) {
        if (storedName.isNullOrBlank()) return
        current = Screen.entries.firstOrNull { it.name == storedName } ?: Screen.Tasks
    }

    /** The value to persist. Kept as the enum *name* so it stays readable in a settings dump. */
    fun persistedName(): String = current.name
}
