package xyz.mdhv.riverwip.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.io.File
import java.util.Locale
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

/** A speech engine found on the PATH, and how to drive it (text goes in on stdin, so length is no issue). */
private class SpeechCommand(val argv: List<String>)

private fun onPath(name: String): Boolean =
    System.getenv("PATH").orEmpty().split(File.pathSeparatorChar).any { it.isNotEmpty() && File(it, name).canExecute() }

private fun findSpeechCommand(): SpeechCommand? {
    val lang = Locale.getDefault().language.ifBlank { "en" }
    return when {
        onPath("spd-say") -> SpeechCommand(listOf("spd-say", "-e", "-w", "-l", lang))
        onPath("espeak-ng") -> SpeechCommand(listOf("espeak-ng", "--stdin", "-v", lang))
        onPath("espeak") -> SpeechCommand(listOf("espeak", "--stdin", "-v", lang))
        else -> null
    }
}

private class DesktopTextSpeaker(private val command: SpeechCommand?) : TextSpeaker {
    private var process: Process? = null
    override val available: Boolean get() = command != null
    override var speaking by mutableStateOf(false)

    @Synchronized
    override fun speak(text: String) {
        val cmd = command ?: return
        stop()
        val started = try {
            ProcessBuilder(cmd.argv).redirectErrorStream(true).start()
        } catch (_: Exception) {
            return
        }
        process = started
        speaking = true
        Thread {
            try {
                started.outputStream.use { it.write(text.toByteArray()) }
                started.inputStream.readBytes()
                started.waitFor()
            } catch (_: Exception) {
            } finally {
                synchronized(this) { if (process === started) { process = null; speaking = false } }
            }
        }.apply { isDaemon = true }.start()
    }

    @Synchronized
    override fun stop() {
        process?.destroy()
        process = null
        speaking = false
    }
}

@Composable
actual fun rememberTextSpeaker(): TextSpeaker {
    val speaker = remember { DesktopTextSpeaker(findSpeechCommand()) }
    DisposableEffect(speaker) { onDispose { speaker.stop() } }
    return speaker
}

private class DesktopAudioFilePlayer(private val path: String) : AudioFilePlayer {
    private var clip: Clip? = null
    override var playing by mutableStateOf(false)

    @Synchronized
    override fun toggle() {
        if (playing) { release(); return }
        try {
            val c = AudioSystem.getClip()
            c.open(AudioSystem.getAudioInputStream(File(path)))
            c.addLineListener { event -> if (event.type == javax.sound.sampled.LineEvent.Type.STOP) playing = false }
            clip = c
            playing = true
            c.start()
        } catch (_: Exception) {
            release()
        }
    }

    @Synchronized
    fun release() {
        clip?.let { it.stop(); it.close() }
        clip = null
        playing = false
    }
}

@Composable
actual fun rememberAudioFilePlayer(path: String): AudioFilePlayer {
    val player = remember(path) { DesktopAudioFilePlayer(path) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}
