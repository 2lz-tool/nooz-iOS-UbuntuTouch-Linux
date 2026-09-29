package xyz.mdhv.riverwip.design

import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

// TextToSpeech.speak has a per-call length ceiling on some OEM engines (historically ~4000 chars);
// QUEUE_ADD across chunks plays them back to back as one continuous read instead of truncating.
private const val TTS_CHUNK_CHARS = 3_800

private class AndroidTextSpeaker : TextSpeaker {
    var engine: TextToSpeech? = null
    override val available: Boolean get() = true
    override var speaking by mutableStateOf(false)

    override fun speak(text: String) {
        val tts = engine ?: return
        tts.setLanguage(Locale.getDefault())
        for ((index, chunk) in text.chunked(TTS_CHUNK_CHARS).withIndex()) {
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts.speak(chunk, mode, null, "nooz-tts-$index")
        }
    }

    override fun stop() {
        engine?.stop()
        speaking = false
    }
}

@Composable
actual fun rememberTextSpeaker(): TextSpeaker {
    val context = LocalContext.current
    val speaker = remember { AndroidTextSpeaker() }
    DisposableEffect(context) {
        val instance = TextToSpeech(context) { }
        instance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { speaker.speaking = true }
            override fun onDone(utteranceId: String?) { speaker.speaking = false }
            override fun onError(utteranceId: String?) { speaker.speaking = false }
        })
        speaker.engine = instance
        onDispose {
            instance.stop()
            instance.shutdown()
            speaker.engine = null
        }
    }
    return speaker
}

private class AndroidAudioFilePlayer(private val path: String) : AudioFilePlayer {
    var player: MediaPlayer? = null
    override var playing by mutableStateOf(false)

    override fun toggle() {
        val current = player
        if (playing && current != null) {
            current.stop()
            current.release()
            player = null
            playing = false
        } else {
            player = MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener { playing = false }
                prepare()
                start()
            }
            playing = true
        }
    }

    fun release() {
        player?.release()
        player = null
        playing = false
    }
}

@Composable
actual fun rememberAudioFilePlayer(path: String): AudioFilePlayer {
    val player = remember(path) { AndroidAudioFilePlayer(path) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}
