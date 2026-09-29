package xyz.mdhv.riverwip.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberUpdatedState

/** The stack of live back handlers. The desktop window feeds it from key and mouse events. */
class BackDispatcher {
    private val handlers = ArrayList<() -> Unit>()

    internal fun push(handler: () -> Unit) { handlers += handler }
    internal fun remove(handler: () -> Unit) { handlers -= handler }

    /** Runs the newest handler. Returns false when nothing wanted the event (so the window may ignore it). */
    fun dispatch(): Boolean {
        val top = handlers.lastOrNull() ?: return false
        top()
        return true
    }
}

val LocalBackDispatcher = compositionLocalOf<BackDispatcher?> { null }

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    val dispatcher = LocalBackDispatcher.current
    val current = rememberUpdatedState(onBack)
    DisposableEffect(dispatcher, enabled) {
        if (dispatcher == null || !enabled) return@DisposableEffect onDispose { }
        val handler: () -> Unit = { current.value() }
        dispatcher.push(handler)
        onDispose { dispatcher.remove(handler) }
    }
}
