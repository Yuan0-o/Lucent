package com.lucent.app.ui

import androidx.compose.runtime.mutableStateMapOf

object UnsavedChangesGuard {

    private class Registration(
        val onSave: () -> Unit,
        val onDiscard: () -> Unit,
        val onAutoDraft: () -> Unit
    )

    private val registrations = mutableStateMapOf<String, Registration>()

    val dirty: Boolean get() = registrations.isNotEmpty()

    fun register(
        owner: String,
        onSave: () -> Unit,
        onDiscard: () -> Unit,
        onAutoDraft: () -> Unit = {}
    ) {
        registrations[owner] = Registration(onSave, onDiscard, onAutoDraft)
    }

    fun clear(owner: String) {
        registrations.remove(owner)
    }

    fun save() {
        val pending = registrations.values.toList()
        registrations.clear()
        pending.forEach { it.onSave() }
    }

    fun autoDraft() {
        registrations.values.toList().forEach { it.onAutoDraft() }
    }

    fun discard() {
        val pending = registrations.values.toList()
        registrations.clear()
        pending.forEach { it.onDiscard() }
    }
}
