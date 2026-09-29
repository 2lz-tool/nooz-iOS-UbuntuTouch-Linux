package xyz.mdhv.riverwip.design

import androidx.compose.runtime.Composable

/**
 * Reads text aloud with the platform's own speech engine, entirely on the device. [available] is
 * false where there is no engine (a desktop without speech-dispatcher or espeak-ng), and callers
 * hide their "listen" control rather than show one that cannot work.
 */
interface TextSpeaker {
    val available: Boolean
    val speaking: Boolean
    fun speak(text: String)
    fun stop()
}

@Composable
expect fun rememberTextSpeaker(): TextSpeaker

/** Plays one rendered audio file (Nooz Cast narration). */
interface AudioFilePlayer {
    val playing: Boolean
    fun toggle()
}

@Composable
expect fun rememberAudioFilePlayer(path: String): AudioFilePlayer
