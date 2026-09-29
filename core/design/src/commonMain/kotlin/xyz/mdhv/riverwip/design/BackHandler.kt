package xyz.mdhv.riverwip.design

import androidx.compose.runtime.Composable

/**
 * "Back", wherever the platform has one: the system back gesture / button on Android; Escape,
 * Alt+Left and the mouse back button on desktop (routed through [BackDispatcher]). When several
 * enabled handlers are composed, the most recently composed one wins, exactly like Android's.
 */
@Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)
