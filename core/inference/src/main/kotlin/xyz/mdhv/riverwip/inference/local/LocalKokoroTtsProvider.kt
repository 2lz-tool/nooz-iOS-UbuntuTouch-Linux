package xyz.mdhv.riverwip.inference.local

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.mdhv.riverwip.inference.Provenance
import xyz.mdhv.riverwip.inference.SynthesisRequest
import xyz.mdhv.riverwip.inference.SynthesisResult
import xyz.mdhv.riverwip.inference.TtsProvider
import xyz.mdhv.riverwip.inference.local.kokoro.KokoroAudio
import xyz.mdhv.riverwip.inference.local.kokoro.KokoroLexicon
import xyz.mdhv.riverwip.inference.local.kokoro.KokoroPhonemizer
import xyz.mdhv.riverwip.inference.local.kokoro.KokoroVocab
import xyz.mdhv.riverwip.inference.local.kokoro.PhonemeChunk

/**
 * On-device narration via a locally downloaded Kokoro-82M ONNX model (owner's
 * Nooz Cast ask). Independent of [LocalLlamaProvider]'s own model/download —
 * Cast has its own catalogue entry, its own files on disk, and its own gate,
 * never Flash's LLM.
 *
 * The full pipeline is real: [KokoroPhonemizer] turns article text into
 * Kokoro's own phoneme alphabet (a genuine ~173k-word dictionary lifted from
 * misaki, Kokoro's own reference G2P — see that class's doc comment for
 * exactly what it does and doesn't cover), [KokoroVocab] tokenizes those
 * phonemes with the model's real vocabulary, and this class runs them
 * through onnxruntime-android (a real Maven Central dependency) against the
 * real downloaded `.onnx` graph, chunked to fit its documented 510-token
 * context window, writing a real playable WAV via [KokoroAudio].
 *
 * What's honest about its limits rather than fabricated: misaki's own
 * fallback for words neither lexicon has is espeak-ng, a native C library
 * with no Android/Maven artifact this app can bundle — out-of-dictionary
 * words get a from-scratch letter-to-sound approximation instead (see
 * `KokoroLetterToSound`), not espeak-ng parity. And none of this has been
 * verified against a real device's speaker: this sandbox can compile Kotlin
 * but can't run an emulator or listen to output, so audio *correctness*
 * (as opposed to "the pipeline runs and produces a WAV file") is unverified.
 */
class LocalKokoroTtsProvider(
    private val context: Context,
    private val modelDir: File,
) : TtsProvider {
    override val id: String = "local-kokoro"

    private val lexicon by lazy { KokoroLexicon(context) }
    private val phonemizer by lazy { KokoroPhonemizer(lexicon) }

    /** The ONNX model and at least one voice embedding must both be on disk — Kokoro needs both to run. */
    fun hasModelOnDisk(): Boolean = modelFile().exists() && voiceFile().exists()

    private fun modelFile(): File = File(modelDir, MODEL_FILE_NAME)
    private fun voiceFile(): File = File(modelDir, DEFAULT_VOICE_FILE_NAME)

    override suspend fun isAvailable(): Boolean = hasModelOnDisk()

    /**
     * Everything below is genuinely heavy — decompressing/hashing a 173k-word
     * dictionary on first use, opening an 86MB ONNX session, running a full
     * forward pass per chunk — so it's dispatched to IO explicitly rather
     * than trusting every future caller to remember to (the same
     * encapsulate-it-here convention [ModelCatalogueRepository]'s own
     * suspend functions already use, not a caller responsibility).
     */
    override suspend fun synthesize(request: SynthesisRequest): SynthesisResult = withContext(Dispatchers.IO) {
        if (!hasModelOnDisk()) return@withContext SynthesisResult.Failed(NOT_ON_DISK)
        runCatching { synthesizeOrThrow(request) }
            .getOrElse { e -> SynthesisResult.Failed(e.message ?: "Nooz Cast couldn't narrate this article") }
    }

    private fun synthesizeOrThrow(request: SynthesisRequest): SynthesisResult {
        val chunks = phonemizer.phonemize(request.text)
        if (chunks.isEmpty()) return SynthesisResult.Failed("Nothing readable in this article's text")

        val voiceRows = readVoiceStyles(voiceFile())
        if (voiceRows.isEmpty()) return SynthesisResult.Failed("The downloaded voice file looks corrupt — try deleting and re-downloading it")

        val env = OrtEnvironment.getEnvironment()
        val audioPieces = mutableListOf<FloatArray>()
        // A long article can be dozens of chunks; one chunk hitting an ONNX
        // error (a still-possible edge case even with KokoroPhonemizer's own
        // 510-char cap, e.g. a pathological single "word" longer than the
        // whole context window) shouldn't throw away every chunk already
        // narrated successfully — only isolated per-chunk failure does that.
        env.createSession(modelFile().absolutePath).use { session ->
            for (chunk in chunks) {
                val samples = runCatching { synthesizeChunk(env, session, chunk, voiceRows) }.getOrNull() ?: continue
                if (audioPieces.isNotEmpty()) audioPieces += FloatArray(CHUNK_GAP_SAMPLES)
                audioPieces += samples
            }
        }
        if (audioPieces.isEmpty()) return SynthesisResult.Failed("Nooz Cast couldn't turn any of this article into narration")

        val total = audioPieces.sumOf { it.size }
        val combined = FloatArray(total)
        var offset = 0
        for (piece in audioPieces) {
            piece.copyInto(combined, offset)
            offset += piece.size
        }

        // A fresh filename per narration, not a fixed one — overwriting a
        // fixed path in place (KokoroAudio.writeWav truncates and rewrites)
        // while a MediaPlayer from a still-open PREVIOUS narration might have
        // it open would corrupt playback rather than just replace the file.
        val outFile = File(context.cacheDir, "nooz_cast_narration_${System.currentTimeMillis()}.wav")
        KokoroAudio.writeWav(combined, outFile)
        return SynthesisResult.Success(outFile.absolutePath, Provenance.NATIVE)
    }

    private fun synthesizeChunk(
        env: OrtEnvironment,
        session: OrtSession,
        chunk: PhonemeChunk,
        voiceRows: Array<FloatArray>,
    ): FloatArray? {
        val tokenIds = KokoroVocab.tokenize(chunk.phonemes)
        if (tokenIds.isEmpty()) return null
        val styleRow = voiceRows[tokenIds.size.coerceIn(0, voiceRows.size - 1)]
        val padded = LongArray(tokenIds.size + 2)
        padded[0] = KokoroVocab.PAD.toLong()
        for (i in tokenIds.indices) padded[i + 1] = tokenIds[i].toLong()
        padded[padded.size - 1] = KokoroVocab.PAD.toLong()

        // Each .use{} block's own return value is its lambda's last
        // expression, propagating naturally up through the nesting to this
        // function's own return — no non-local `return` needed partway
        // through, so there's nothing subtle for a reader (or the compiler)
        // to have to prove about control flow.
        return OnnxTensor.createTensor(env, arrayOf(padded)).use { inputIds ->
            OnnxTensor.createTensor(env, arrayOf(styleRow)).use { style ->
                OnnxTensor.createTensor(env, floatArrayOf(1f)).use { speed ->
                    session.run(mapOf("input_ids" to inputIds, "style" to style, "speed" to speed)).use { results ->
                        // .iterator().next() gets the first *output* entry (Kokoro
                        // has one), whose own .value is the OnnxValue wrapper —
                        // .value again on that (only meaningful for an OnnxTensor)
                        // is what actually unwraps to the raw Java array.
                        val outputTensor = results.iterator().next().value as? OnnxTensor
                        extractSamples(outputTensor?.value)
                    }
                }
            }
        }
    }

    /** onnxruntime-android hands back nested Java arrays matching the output tensor's rank; Kokoro's audio output is documented as shape (1, N) — this unwraps whichever concrete shape actually comes back rather than assuming one blindly. */
    private fun extractSamples(value: Any?): FloatArray? = when (value) {
        is FloatArray -> value
        is Array<*> -> (value.firstOrNull() as? FloatArray) ?: (value.firstOrNull() as? Array<*>)?.let { inner ->
            (inner.firstOrNull() as? FloatArray)
        }
        else -> null
    }

    /** Kokoro's voice packs are a flat little-endian float32 dump, reshape (-1, 1, 256) per the model's own docs — one 256-float style vector per possible phoneme-token length. */
    private fun readVoiceStyles(file: File): Array<FloatArray> {
        val bytes = file.readBytes()
        val floatsTotal = bytes.size / 4
        val rows = floatsTotal / 256
        if (rows <= 0) return emptyArray()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return Array(rows) { r ->
            FloatArray(256) { c -> buffer.getFloat((r * 256 + c) * 4) }
        }
    }

    companion object {
        // These must match ModelCatalogueRepository.fileFor()'s naming exactly
        // ("${model.id}.${extension}", flat under the shared models/ dir) for
        // the two Kokoro catalogue entries (kokoro-82m-q8f16, kokoro-82m-voice-
        // af-heart) — not an arbitrary filename this class gets to pick itself.
        private const val MODEL_FILE_NAME = "kokoro-82m-q8f16.onnx"
        private const val DEFAULT_VOICE_FILE_NAME = "kokoro-82m-voice-af-heart.bin"
        private const val NOT_ON_DISK = "Kokoro narration model is not downloaded yet"

        /** ~200ms of silence at Kokoro's 24kHz output — a natural pause between chunks a long article gets split into, rather than pieces running straight into each other. */
        private const val CHUNK_GAP_SAMPLES = 24_000 / 5
    }
}
