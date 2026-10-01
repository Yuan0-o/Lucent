package com.lucent.app.harness.terminal

object PtyKeys {
    const val ESCAPE = "\u001B"
    const val TAB = "\t"
    const val ENTER = "\r"
    const val BACKSPACE = "\u007F"
    const val ARROW_UP = "\u001B[A"
    const val ARROW_DOWN = "\u001B[B"
    const val ARROW_RIGHT = "\u001B[C"
    const val ARROW_LEFT = "\u001B[D"
    const val HOME = "\u001B[H"
    const val END = "\u001B[F"
    const val PAGE_UP = "\u001B[5~"
    const val PAGE_DOWN = "\u001B[6~"
    const val DELETE = "\u001B[3~"
    const val INSERT = "\u001B[2~"

    fun ctrl(letter: Char): String {
        val upper = letter.uppercaseChar()
        if (upper !in 'A'..'Z') return ""
        return (upper.code - 'A'.code + 1).toChar().toString()
    }

    fun alt(sequence: String): String = ESCAPE + sequence
}
