package xyz.mdhv.riverwip.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
actual fun rememberFileDialogs(): FileDialogs {
    val scope = rememberCoroutineScope()
    return remember(scope) { AwtFileDialogs(scope) }
}

/** The native (GTK-look on Linux) file dialog. AWT dialogs are modal and must be created on the event thread. */
private class AwtFileDialogs(private val scope: CoroutineScope) : FileDialogs {

    private suspend fun choose(mode: Int, title: String, name: String?): File? = withContext(Dispatchers.Swing) {
        val dialog = FileDialog(null as Frame?, title, mode)
        if (name != null) dialog.file = name
        dialog.isVisible = true // blocks until dismissed
        val file = dialog.file
        val dir = dialog.directory
        if (file == null || dir == null) null else File(dir, file)
    }

    override fun saveText(suggestedName: String, mimeType: String, text: String) {
        scope.launch {
            val target = choose(FileDialog.SAVE, "Save", suggestedName) ?: return@launch
            withContext(Dispatchers.IO) { target.writeText(text) }
        }
    }

    override fun openText(mimeTypes: List<String>, onResult: (String?) -> Unit) {
        scope.launch {
            val source = choose(FileDialog.LOAD, "Open", null)
            val text = source?.let { withContext(Dispatchers.IO) { runCatching { it.readText() }.getOrNull() } }
            onResult(text)
        }
    }
}
