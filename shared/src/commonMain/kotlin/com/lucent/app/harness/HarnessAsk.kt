package com.lucent.app.harness

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HarnessAsk {

    data class Request(val id: Long, val question: String, val options: List<String>)

    private val state = MutableStateFlow<Request?>(null)
    private var answer: ((String) -> Unit)? = null
    private var counter = 1L

    val pending: StateFlow<Request?> = state.asStateFlow()

    @Synchronized
    fun request(question: String, options: List<String>, onAnswer: (String) -> Unit) {
        answer?.invoke("")
        answer = onAnswer
        state.value = Request(counter++, question, options.take(3))
    }

    @Synchronized
    fun respond(text: String) {
        val callback = answer
        answer = null
        state.value = null
        callback?.invoke(text)
    }
}
