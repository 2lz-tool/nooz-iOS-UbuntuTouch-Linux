package xyz.mdhv.riverwip.design

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberFileDialogs(): FileDialogs {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The launchers deliver to whichever callback is current, so the pending action lives in these holders.
    val pendingSave = remember { arrayOfNulls<String>(1) }
    val pendingOpen = remember { arrayOfNulls<(String?) -> Unit>(1) }

    val pendingMime = remember { arrayOf("text/xml") }
    // CreateDocument fixes its MIME type at construction; the caller's type is applied when the intent is built.
    val createDocument = remember {
        object : ActivityResultContracts.CreateDocument("text/xml") {
            override fun createIntent(context: android.content.Context, input: String) =
                super.createIntent(context, input).setType(pendingMime[0])
        }
    }
    val saveLauncher = rememberLauncherForActivityResult(createDocument) { uri: Uri? ->
        val text = pendingSave[0]
        pendingSave[0] = null
        if (uri != null && text != null) scope.launch {
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            }
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val callback = pendingOpen[0]
        pendingOpen[0] = null
        if (callback != null) scope.launch(Dispatchers.Main) { callback(uri?.let { readText(context, it) }) }
    }

    return remember(saveLauncher, openLauncher) {
        object : FileDialogs {
            override fun saveText(suggestedName: String, mimeType: String, text: String) {
                pendingSave[0] = text
                pendingMime[0] = mimeType
                saveLauncher.launch(suggestedName)
            }

            override fun openText(mimeTypes: List<String>, onResult: (String?) -> Unit) {
                pendingOpen[0] = onResult
                openLauncher.launch(mimeTypes.toTypedArray())
            }
        }
    }
}

private suspend fun readText(context: android.content.Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }
