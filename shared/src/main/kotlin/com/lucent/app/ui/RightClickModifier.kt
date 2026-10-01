package com.lucent.app.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.onSecondaryClick(onClick: () -> Unit): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            if (event.type == PointerEventType.Press && event.button == PointerButton.Secondary) {
                event.changes.forEach { it.consume() }
                onClick()
            }
        }
    }
}
