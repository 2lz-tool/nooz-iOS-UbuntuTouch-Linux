package xyz.mdhv.riverwip.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.compose.resources.painterResource
import xyz.mdhv.riverwip.NoozApp
import xyz.mdhv.riverwip.design.BackDispatcher
import xyz.mdhv.riverwip.design.LocalBackDispatcher
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.util.Locale

/** The narrowest window that still lays out as the phone single-pane Stand. */
val MIN_WINDOW_WIDTH = 360.dp
val MIN_WINDOW_HEIGHT = 480.dp

fun main() {
    val dirs = XdgDirs.fromEnvironment()
    val services = DesktopServices(dirs)
    (services.crashReports as FileCrashReports).install("Nooz", VERSION)
    val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    IngestTimer(backgroundScope, services.ingest).start()

    application {
        val windowState = rememberWindowState(size = DpSize(1180.dp, 800.dp))
        val back = remember { BackDispatcher() }
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "Nooz",
            onPreviewKeyEvent = { event ->
                // Escape and Alt+Left are "back", the desktop equivalents of the Android back gesture.
                val isBack = event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Escape || (event.isAltPressed && event.key == Key.DirectionLeft))
                isBack && back.dispatch()
            },
        ) {
            // Below this the layouts stop making sense; the width-driven two-pane / one-pane switch
            // handles every size above it.
            window.minimumSize = Dimension(MIN_WINDOW_WIDTH.value.toInt(), MIN_WINDOW_HEIGHT.value.toInt())

            val direction = layoutDirectionFor(Locale.getDefault(), services.locale.epoch)
            CompositionLocalProvider(
                LocalBackDispatcher provides back,
                LocalLayoutDirection provides direction,
            ) {
                NoozApp(services)
            }
        }
    }
}

/** Right-to-left for the languages that need it (Urdu, Kashmiri, Arabic, Persian...); [epoch] only forces a re-read. */
@Suppress("UNUSED_PARAMETER")
private fun layoutDirectionFor(locale: Locale, epoch: Int): LayoutDirection =
    if (ComponentOrientation.getOrientation(locale).isLeftToRight) LayoutDirection.Ltr else LayoutDirection.Rtl

const val VERSION = "0.3.1"
