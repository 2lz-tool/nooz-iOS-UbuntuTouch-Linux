package xyz.mdhv.riverwip.feature.reader

import androidx.compose.runtime.Composable

/**
 * Share as a newspaper clipping (owner's spec, 2026-07): the headline rendered as a paper-white
 * masthead card, handed to the platform's own sharing (the system chooser on Android; a
 * "save the clipping" dialog plus the caption on the clipboard on desktop). Everything on the
 * card is real -- the app's name, the article's own title/source/author, today's date.
 */
interface ClippingSharer {
    fun share(title: String, source: String?, author: String?, url: String?)
}

@Composable
expect fun rememberClippingSharer(): ClippingSharer
