package androidx.activity.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

object DesktopBackDispatcher {
    private val handlers = ArrayDeque<Entry>()

    class Entry(var enabled: Boolean, var onBack: () -> Unit)

    @Synchronized internal fun register(entry: Entry) { handlers.addLast(entry) }
    @Synchronized internal fun unregister(entry: Entry) { handlers.remove(entry) }

    @Synchronized fun dispatch(): Boolean {
        val target = handlers.lastOrNull { it.enabled } ?: return false
        target.onBack()
        return true
    }
}

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val currentOnBack by rememberUpdatedState(onBack)
    val entry = androidx.compose.runtime.remember { DesktopBackDispatcher.Entry(enabled) { currentOnBack() } }
    entry.enabled = enabled
    DisposableEffect(Unit) {
        DesktopBackDispatcher.register(entry)
        onDispose { DesktopBackDispatcher.unregister(entry) }
    }
}
