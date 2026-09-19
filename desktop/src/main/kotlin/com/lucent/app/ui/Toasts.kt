package com.lucent.app.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object LucentToast {

    const val SHORT_MS = 2200L
    const val LONG_MS = 4200L

    data class Entry(val id: Long, val message: String, val longDuration: Boolean)

    private val counter = java.util.concurrent.atomic.AtomicLong(0)
    private val _messages = MutableStateFlow<Entry?>(null)
    val messages: StateFlow<Entry?> = _messages

    fun show(context: Context, message: String, longDuration: Boolean = false) {
        _messages.value = Entry(counter.incrementAndGet(), message, longDuration)
    }

    fun clear(entry: Entry) {
        if (_messages.value?.id == entry.id) _messages.value = null
    }
}
