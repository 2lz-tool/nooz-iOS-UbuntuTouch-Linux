package xyz.mdhv.riverwip.design

import androidx.compose.runtime.Composable

/**
 * "Let the user pick a file" for the two places the app exchanges the user's own data as text
 * (OPML sources today). Android uses the system document picker; desktop uses the native
 * file dialog. Both are asynchronous and never block the UI thread.
 */
interface FileDialogs {
    /** Ask where to save, then write [text] there. Does nothing if the user cancels. */
    fun saveText(suggestedName: String, mimeType: String, text: String)

    /** Ask for a file and hand its text to [onResult], or null if the user cancelled or it could not be read. */
    fun openText(mimeTypes: List<String>, onResult: (String?) -> Unit)
}

@Composable
expect fun rememberFileDialogs(): FileDialogs
