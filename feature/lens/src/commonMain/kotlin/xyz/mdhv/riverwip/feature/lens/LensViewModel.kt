package xyz.mdhv.riverwip.feature.lens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlin.reflect.KClass
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import xyz.mdhv.riverwip.data.repo.DictionaryRepository
import xyz.mdhv.riverwip.data.repo.TranslationRepository
import xyz.mdhv.riverwip.model.TranslationCatalog
import xyz.mdhv.riverwip.inference.InferenceRouter
import xyz.mdhv.riverwip.inference.Provenance
import xyz.mdhv.riverwip.inference.RewriteRequest
import xyz.mdhv.riverwip.inference.RewriteResult
import xyz.mdhv.riverwip.model.AffectSpanDetector
import xyz.mdhv.riverwip.model.FidelityGuard
import xyz.mdhv.riverwip.model.ObscureWords

/** One detected affect span's defuse state (brief §P5). */
sealed interface AffectSpanUiState {
    data object Untouched : AffectSpanUiState
    data object Loading : AffectSpanUiState
    data class Accepted(val rewrittenText: String, val provenance: Provenance) : AffectSpanUiState
    data object Dismissed : AffectSpanUiState
    /** The guard rejected the rewrite, or the provider failed/was unavailable — reason always shown (brief §3). */
    data class Rejected(val reason: String) : AffectSpanUiState
}

/**
 * The lens (brief §P5). All state here is **session-ephemeral**: it survives
 * rotation (a ViewModel does, by construction) but never an app restart, and is
 * never persisted or aggregated — what a user chooses to defuse is itself a
 * bias trace, and this app does not collect it. There is deliberately no
 * Room/DataStore dependency anywhere in this class.
 */
class LensViewModel(
    private val inferenceRouter: InferenceRouter,
    private val dictionaryRepository: DictionaryRepository,
    private val translationRepository: TranslationRepository,
) : ViewModel() {

    data class SpanKey(val itemId: String, val start: Int, val end: Int)

    private val overrides: SnapshotStateMap<SpanKey, AffectSpanUiState> = mutableStateMapOf()

    // ---- dictionary lens (owner's Kindle-style obscure-word definitions) ----
    // The defuse/override state above is never persisted (a defuse choice is a
    // bias trace); the dictionary is the one benign exception — it only reads
    // the bundled common-word gate and a user-downloaded dictionary, and
    // persists which dictionary was chosen. Which words a reader looks up is
    // never stored.

    /** A dictionary is downloaded, so obscure words can be defined (blue underline). */
    var dictionaryReady: Boolean by mutableStateOf(false)
        private set

    /** The bundled common-word gate; loaded lazily once a dictionary is present. */
    private var commonWords: Set<String> by mutableStateOf(emptySet())

    /**
     * The installed translation pair's destination language ("Spanish"), or
     * null when none is installed (owner's ask: long-press a word while reading
     * a foreign language and see it in your own).
     *
     * A name rather than a boolean because the definition sheet has to *label*
     * the translations — "Spanish" tells the reader which of their languages
     * they are looking at, where an unlabelled block would not.
     */
    var translationTargetName: String? by mutableStateOf(null)
        private set

    init {
        viewModelScope.launch {
            dictionaryRepository.observeDownloadedId().collect { id ->
                dictionaryReady = id != null
                if (id != null && commonWords.isEmpty()) {
                    commonWords = dictionaryRepository.commonWords()
                }
            }
        }
        viewModelScope.launch {
            translationRepository.observeInstalledId().collect { id ->
                translationTargetName = TranslationCatalog.byId(id)?.targetName
            }
        }
    }

    /** True once obscure marks should render: a dictionary is present and the gate is loaded. */
    val obscureActive: Boolean get() = dictionaryReady && commonWords.isNotEmpty()

    /** Obscure words worth a definition — empty until the gate has loaded. */
    fun detectObscure(text: String): List<ObscureWords.WordSpan> =
        if (commonWords.isEmpty()) emptyList() else ObscureWords.detect(text, commonWords)

    /** Look up a word in the downloaded dictionary (null if none / not found). */
    suspend fun define(word: String): String? = dictionaryRepository.define(word)

    /**
     * Word-level translations from the installed bilingual dictionary, or
     * empty when none is installed and when the word simply isn't in it.
     * Local only — the same promise [define] makes: which words a reader
     * looks up is never stored and never sent anywhere.
     */
    suspend fun translate(word: String): List<String> = translationRepository.translate(word)

    /**
     * Pre-underline detected spans. Driven by the persisted "Highlight loaded
     * language" Setting (owner: the reader's always-on eye toggle was confusing
     * and off-mock), so it defaults OFF and the reader syncs it from Settings.
     */
    var underlinesEnabled: Boolean by mutableStateOf(false)
        private set

    /** Global "sterile lens": batch-applies the same per-span pipeline to every span. Off by default. */
    var sterileLensEnabled: Boolean by mutableStateOf(false)
        private set

    /**
     * Advanced settings' word-list customization (owner's ask), synced in
     * from the persisted `AppSettings` the exact same way [underlinesEnabled]
     * already is — this class stays free of any Room/DataStore dependency of
     * its own (see the class doc above); the caller (`ReaderScreen`/
     * `ReaderScreenTwoPane`) reads the real settings and pushes them in via a
     * `LaunchedEffect`.
     */
    var lensDisabledDefaultTerms: Set<String> by mutableStateOf(emptySet())
        private set
    var lensCustomTerms: Set<String> by mutableStateOf(emptySet())
        private set

    // Named distinctly from the property (not `setUnderlinesEnabled`/`setSterileLensEnabled`):
    // Kotlin's `private set` still compiles a same-named synthetic setter for a delegated
    // `var`, and a same-signature public function collides with it at the JVM level
    // (a "platform declaration clash") regardless of the differing Kotlin visibility.
    fun updateUnderlinesEnabled(enabled: Boolean) {
        underlinesEnabled = enabled
    }

    fun updateSterileLensEnabled(enabled: Boolean) {
        sterileLensEnabled = enabled
    }

    fun updateLensDisabledDefaultTerms(terms: Set<String>) {
        lensDisabledDefaultTerms = terms
    }

    fun updateLensCustomTerms(terms: Set<String>) {
        lensCustomTerms = terms
    }

    fun detect(text: String): List<AffectSpanDetector.Span> =
        AffectSpanDetector.detect(text, lensDisabledDefaultTerms, lensCustomTerms)

    fun stateFor(itemId: String, span: AffectSpanDetector.Span): AffectSpanUiState =
        overrides[SpanKey(itemId, span.start, span.end)] ?: AffectSpanUiState.Untouched

    /**
     * Ask the router for a neutral rewrite, then run the deterministic fidelity
     * guard against the *original full sentence* (not just the span) — a
     * fabricated number/entity/negation flip can appear anywhere in the output.
     */
    fun requestDefuse(itemId: String, span: AffectSpanDetector.Span, fullSentence: String) {
        val key = SpanKey(itemId, span.start, span.end)
        overrides[key] = AffectSpanUiState.Loading
        viewModelScope.launch {
            val result = inferenceRouter.rewrite(RewriteRequest(fullSentence, span.text, span.start, span.end))
            overrides[key] = when (result) {
                is RewriteResult.Success -> {
                    val verdict = FidelityGuard.check(fullSentence, result.rewrittenSentence)
                    if (verdict.accepted) {
                        AffectSpanUiState.Accepted(result.rewrittenSentence, result.provenance)
                    } else {
                        AffectSpanUiState.Rejected(verdict.reason)
                    }
                }
                is RewriteResult.Failed -> AffectSpanUiState.Rejected(result.reason)
            }
        }
    }

    /** Batch-apply the same pipeline to every untouched span in [text] (the global sterile-lens toggle). */
    fun defuseAll(itemId: String, text: String) {
        for (span in detect(text)) {
            if (stateFor(itemId, span) is AffectSpanUiState.Untouched) {
                requestDefuse(itemId, span, text)
            }
        }
    }

    fun dismiss(itemId: String, span: AffectSpanDetector.Span) {
        overrides[SpanKey(itemId, span.start, span.end)] = AffectSpanUiState.Dismissed
    }

    /** Individually revertible (brief §P5): the original is always one tap away. */
    fun revert(itemId: String, span: AffectSpanDetector.Span) {
        overrides.remove(SpanKey(itemId, span.start, span.end))
    }

    /** Globally revertible: undo every accepted rewrite for [itemId] at once. */
    fun revertAllFor(itemId: String) {
        overrides.keys.filter { it.itemId == itemId }.forEach { overrides.remove(it) }
    }

    class Factory(
        private val inferenceRouter: InferenceRouter,
        private val dictionaryRepository: DictionaryRepository,
        private val translationRepository: TranslationRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T =
            LensViewModel(inferenceRouter, dictionaryRepository, translationRepository) as T
    }
}
