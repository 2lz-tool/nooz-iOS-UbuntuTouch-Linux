package xyz.mdhv.riverwip.feature.reader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlin.reflect.KClass
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import xyz.mdhv.riverwip.data.repo.ArticleRepository
import xyz.mdhv.riverwip.data.repo.ClippingRepository
import xyz.mdhv.riverwip.data.repo.ItemRepository
import xyz.mdhv.riverwip.data.repo.ReadEventRepository
import xyz.mdhv.riverwip.data.repo.SettingsRepository
import xyz.mdhv.riverwip.data.repo.SourceRepository
import xyz.mdhv.riverwip.data.repo.TodayInHistoryRepository
import xyz.mdhv.riverwip.data.repo.WeeklyAggregateRepository
import xyz.mdhv.riverwip.inference.DigestRequest
import xyz.mdhv.riverwip.inference.DigestResult
import xyz.mdhv.riverwip.inference.InferenceRouter
import xyz.mdhv.riverwip.inference.Provenance
import xyz.mdhv.riverwip.inference.SynthesisRequest
import xyz.mdhv.riverwip.inference.SynthesisResult
import xyz.mdhv.riverwip.inference.TtsProvider
import xyz.mdhv.riverwip.model.ArticleSearch
import xyz.mdhv.riverwip.model.Classifier
import xyz.mdhv.riverwip.model.DayLoomLayout
import xyz.mdhv.riverwip.model.DwellBucket
import xyz.mdhv.riverwip.model.HistoricalEvent
import xyz.mdhv.riverwip.model.Item
import xyz.mdhv.riverwip.model.ReaderFilter
import xyz.mdhv.riverwip.model.Region
import xyz.mdhv.riverwip.model.Starters
import xyz.mdhv.riverwip.model.Topic
import xyz.mdhv.riverwip.model.WeekBucketing

/** The reader's article state, once a reading session has begun. */
sealed interface ArticleUiState {
    data object Loading : ArticleUiState
    data class Loaded(val paragraphs: List<String>, val fromCache: Boolean) : ArticleUiState
    /** Extraction failed or yielded nothing — fall back to the feed's own summary, if any. */
    data class Fallback(val summary: String?) : ArticleUiState
}

/** Nooz Flash's state (owner's #6) — never fetched automatically; the reader asks for it. */
sealed interface FlashUiState {
    data object Idle : FlashUiState
    data object Loading : FlashUiState
    /**
     * Presented while Nooz Flash is "coming soon": the on-device LLM runtime
     * that powers the digest isn't wired in this build, and no download can
     * change that, so the card invites neither a tap nor a model download.
     * Gated by [FLASH_COMING_SOON]; when that flips, Flash never enters this
     * state and falls straight back to its ordinary Idle → Loading → Ready flow.
     */
    data object ComingSoon : FlashUiState
    /** [headlines] is what was actually compressed — "go deeper" is just showing this list, not a second generation. */
    data class Ready(val flash: String, val provenance: Provenance, val headlines: List<String>) : FlashUiState
    /**
     * The provider ran but declined or errored — never silent (brief §3).
     * [needsSetup] distinguishes "you haven't set up any reader-intelligence
     * provider" (actionable — point at Settings) from a genuine runtime error
     * worth showing verbatim (e.g. a BYOK endpoint returning HTTP 401).
     */
    data class Unavailable(val reason: String, val needsSetup: Boolean = false) : FlashUiState
}

/**
 * Today in History's state (owner's ask, 2026-08). Deliberately has no
 * "needsSetup" case unlike [FlashUiState]/[CastUiState]: there is nothing to
 * configure or download, so the only ways this ends are a column or an
 * honest line about not having reached Wikipedia.
 */
sealed interface HistoryUiState {
    data object Idle : HistoryUiState
    data object Loading : HistoryUiState
    data class Ready(val events: List<HistoricalEvent>) : HistoryUiState
    data class Unavailable(val reason: String) : HistoryUiState
}

/**
 * Nooz Cast's state (owner's ask: a natural on-device narrator for the full
 * body, not the robotic system-TTS "Play" [FlashCard] already has) — mirrors
 * [FlashUiState]'s exact shape since it's the same "tap, then wait, then
 * either a result or an honest reason" contract, just over a different
 * provider with no cloud fallback at all.
 */
sealed interface CastUiState {
    data object Idle : CastUiState
    data object Loading : CastUiState
    /** [audioPath] is the rendered narration — always [Provenance.NATIVE]; Cast has no cloud path to mark otherwise (owner: "a private anchor voice should never leave the device"). */
    data class Ready(val audioPath: String, val provenance: Provenance) : CastUiState
    /**
     * The provider ran but declined or errored — never silent (brief §3).
     * [needsSetup] distinguishes "the narration model isn't downloaded yet"
     * (actionable — point at Settings) from a genuine runtime error.
     */
    data class Unavailable(val reason: String, val needsSetup: Boolean = false) : CastUiState
}

/**
 * Shared between the upfront preflight check ([ReaderViewModel.init]) and
 * [ReaderViewModel.requestFlash]'s own post-tap failure, so the reader sees
 * the identical line either way it gets shown (owner's ask: this should be
 * known before tapping, not only discovered after — see [FlashCard]'s slashed
 * bolt for the icon half of that).
 */
private const val FLASH_NOT_CONFIGURED_REASON = "Nooz Flash won't work until a model or API key is configured."

/**
 * Nooz Flash's on-device LLM runtime is wired
 * ([xyz.mdhv.riverwip.inference.local.LocalLlamaProvider] now runs a real,
 * vendored llama.cpp build — see `core/inference/src/main/cpp`) so Flash is
 * back to its ordinary tap-to-compress flow: [FlashUiState.ComingSoon] is
 * unreachable with this flag false, kept only so a future regression (the
 * runtime needing to come back out, say) has a one-line way back to the
 * honest "coming soon" state instead of a silent per-tap failure.
 */
private const val FLASH_COMING_SOON = false

/** Cast's own gate — Kokoro is a wholly different, independently-downloaded model class from whatever LLM [ReaderViewModel.flashState] uses. */
private const val CAST_NOT_CONFIGURED_REASON = "Nooz Cast won't work until the on-device narration model is downloaded."

/** How often the reading-aside clock below checks in, and how long a real reading stretch must run before the next pick (matches the web reader's identical cadence). */
private const val READING_CLOCK_TICK_MS = 5_000L
private const val READING_ASIDE_INTERVAL_MS = 6 * 60 * 1_000L

/**
 * Debounce before a body search hits the FTS index (D37). The caller is a text
 * field, so without this every keystroke is a database query — and the early
 * keystrokes are the most expensive, since a one- or two-letter prefix matches
 * a large share of the corpus.
 */
private const val SEARCH_DEBOUNCE_MS = 220L

/** Outcome of the last manual/auto refresh, so the reader can report it honestly (brief §3: nothing silent). */
data class RefreshResult(
    val newItems: Int,
    val sourcesTried: Int,
    val sourcesFailed: Int,
    val error: String? = null,
) {
    val allFailed: Boolean get() = error != null || (sourcesTried > 0 && sourcesFailed == sourcesTried)
}

class ReaderViewModel(
    private val itemRepository: ItemRepository,
    private val sourceRepository: SourceRepository,
    private val articleRepository: ArticleRepository,
    private val readEventRepository: ReadEventRepository,
    private val weeklyAggregateRepository: WeeklyAggregateRepository,
    private val clippingRepository: ClippingRepository,
    private val settingsRepository: SettingsRepository,
    private val todayInHistoryRepository: TodayInHistoryRepository,
    private val flashRouter: InferenceRouter,
    private val ttsProvider: TtsProvider,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    /** Which items are clipped, so the reader's bookmark shows filled/empty. */
    val savedIds: StateFlow<Set<String>> = clippingRepository.observeSavedIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptySet())

    /** Every item with at least one read event, for the Stand's read marking (owner's ask) and unread filter. */
    val readIds: StateFlow<Set<String>> = readEventRepository.observeAll()
        .map { events -> events.map { it.itemId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptySet())

    /**
     * Snippets from article *bodies* matching the current search, keyed by item
     * id (D37).
     *
     * The Stand's search used to be a substring match on the headline alone,
     * which only ever worked if the reader remembered words from the headline —
     * and fuzzy recall almost never does. What survives is usually a phrase from
     * the middle of a piece. Those bodies live in the `article_text` FTS index,
     * which is a database query, so unlike the title filter it cannot be done
     * inline in composition.
     *
     * The value is the snippet rather than a bare id so the list can show *why*
     * a story matched — without that, a result whose headline contains none of
     * the search terms just looks like a bug.
     */
    private val _bodySearchMatches = MutableStateFlow<Map<String, String>>(emptyMap())
    val bodySearchMatches: StateFlow<Map<String, String>> = _bodySearchMatches.asStateFlow()

    private var bodySearchJob: Job? = null

    /**
     * Runs a body search, debounced, cancelling whatever was in flight — the
     * caller is a text field, so this is invoked on every keystroke.
     */
    fun searchBodies(raw: String) {
        bodySearchJob?.cancel()
        val match = ArticleSearch.toMatchQuery(raw)
        if (match == null) {
            // No query. Deliberately clears rather than leaving stale hits
            // behind, which would keep matches on screen after the box is
            // emptied.
            _bodySearchMatches.value = emptyMap()
            return
        }
        val terms = ArticleSearch.terms(raw)
        bodySearchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            // A malformed MATCH throws rather than returning nothing; the query
            // builder makes that structurally unreachable, but a search box is
            // the wrong place to find out it was wrong.
            val hits = runCatching { articleRepository.searchBodies(match) }.getOrDefault(emptyList())
            _bodySearchMatches.value = hits.mapNotNull { hit ->
                ArticleSearch.snippet(hit.body, terms)?.let { hit.itemId to it }
            }.toMap()
        }
    }

    /** Save or unsave the article as a Nooz-paper clipping. */
    fun toggleClip(item: Item) {
        viewModelScope.launch {
            if (savedIds.value.contains(item.id)) {
                clippingRepository.remove(item.id)
            } else {
                clippingRepository.save(item, sourceTitles.value[item.sourceId])
            }
        }
    }

    private val allItems: StateFlow<List<Item>> = itemRepository.observeItemsForEnabledSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    /** The standing region + topics filter (set on the globe, persisted). */
    val filter: StateFlow<ReaderFilter> = settingsRepository.observeFilter()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ReaderFilter())

    /** Region and topic interactions also reach the standing filter from here — the Stand's own quick-filter sheet, not just Contrast/the globe. */
    fun setRegion(region: Region) {
        viewModelScope.launch { settingsRepository.setFilter(filter.value.copy(region = region)) }
    }

    fun toggleTopic(topicKey: String) {
        viewModelScope.launch {
            val current = filter.value
            val topics = if (topicKey in current.topicKeys) current.topicKeys - topicKey else current.topicKeys + topicKey
            settingsRepository.setFilter(current.copy(topicKeys = topics))
        }
    }

    /** Resets region and topics together, atomically — the quick-filter sheet's "Clear". */
    fun clearFilter() {
        viewModelScope.launch { settingsRepository.setFilter(ReaderFilter()) }
    }

    /**
     * Per-topic article counts for the currently selected region only — the
     * cascading filter's second step (owner: "the topics with how many
     * articles for each remain for the selected region"). Deliberately
     * ignores the current topic selection itself (region-only filter), so
     * toggling a topic chip never changes the counts shown next to the
     * others.
     */
    val topicCountsForRegion: StateFlow<Map<Topic, Int>> = combine(allItems, filter) { list, f ->
        list.asSequence()
            .filter { f.includesSource(Starters.regionBySourceId[it.sourceId]) }
            .map { Classifier.dominantTopic(it.topics) }
            .groupingBy { it }
            .eachCount()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyMap())

    /**
     * The stand's stream: enabled sources, then the standing filter. Region
     * matches a source's declared starter region (URL/OPML additions carry no
     * declared region and count as global); topics match the item's dominant
     * topic.
     */
    val items: StateFlow<List<Item>> = combine(allItems, filter) { list, f ->
        list.filter { item ->
            f.includesSource(Starters.regionBySourceId[item.sourceId])
                && f.matchesTopic(Classifier.dominantTopic(item.topics))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    /** The honest denominator for this surface's copy. */
    val enabledSourceCount: StateFlow<Int> = sourceRepository.observeEnabledCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 0)

    /** Source id → title, for the "Author | Source" bylines (owner's Stand/Paper mocks). */
    val sourceTitles: StateFlow<Map<String, String>> = sourceRepository.observeSources()
        .map { list -> list.associate { it.id to it.title } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyMap())

    /**
     * Today's supply compressed into the day bar (the loom, folded flat):
     * per-topic shares of everything that flowed today from the region's
     * source-set. Topics are never filtered out of the bar — supply is the
     * subject.
     */
    val todayMix: StateFlow<List<Pair<Topic, Double>>> = combine(allItems, filter) { list, f ->
        val dayStart = WeekBucketing.periodStart(clock(), periodDays = 1)
        val counts = HashMap<String, Int>()
        for (item in list) {
            if (item.publishedAt < dayStart) continue
            if (!f.includesSource(Starters.regionBySourceId[item.sourceId])) continue
            counts.merge(Classifier.dominantTopic(item.topics).key, 1, Int::plus)
        }
        DayLoomLayout.dayMix(counts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    /**
     * Today's *reading* distribution (owner's #5: the Stand's top bar must
     * show what the reader actually read, not what merely flowed). Joins each
     * read event back to its item's dominant topic; a read counts regardless
     * of the standing filter at read time — same "never hide" principle
     * [todayMix] applies to supply, applied here to consumption instead.
     */
    val todayReadMix: StateFlow<List<Pair<Topic, Double>>> = combine(allItems, readEventRepository.observeAll()) { list, reads ->
        val dayStart = WeekBucketing.periodStart(clock(), periodDays = 1)
        val itemsById = list.associateBy { it.id }
        val counts = HashMap<String, Int>()
        // Count each article once, not once per open — re-opening the same
        // story (or bouncing in and out) shouldn't tilt the read-distribution
        // bar toward its topic (owner: "tapping the same link over and over
        // changes the distribution"). The raw events still exist for the
        // dwell buckets; this surface just measures *what* was read, not how
        // many times.
        val counted = HashSet<String>()
        for (event in reads) {
            if (event.openedAt < dayStart) continue
            val item = itemsById[event.itemId] ?: continue
            if (!counted.add(event.itemId)) continue
            counts.merge(Classifier.dominantTopic(item.topics).key, 1, Int::plus)
        }
        DayLoomLayout.dayMix(counts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private val _flashState = MutableStateFlow<FlashUiState>(
        if (FLASH_COMING_SOON) FlashUiState.ComingSoon else FlashUiState.Idle,
    )

    /**
     * Nooz Flash (owner's #6): today's flowed headlines — matching the standing
     * filter, same denominator as [todayMix] — compressed to 10 words or fewer.
     * Headlines only, never full article text, so a flash can't surface a claim
     * its own headline didn't already make. [flashRouter] tries the device
     * first and only falls back to a configured key — it never reaches Urbana
     * or a general cloud broker the way the main lens's rewrite can.
     */
    val flashState: StateFlow<FlashUiState> = _flashState

    fun requestFlash() {
        // Coming soon: with no on-device runtime the digest can only fail, so
        // the tap is a no-op (the card shows no tap invitation while gated).
        if (FLASH_COMING_SOON) return
        if (_flashState.value is FlashUiState.Loading) return
        viewModelScope.launch {
            _flashState.value = FlashUiState.Loading
            val dayStart = WeekBucketing.periodStart(clock(), periodDays = 1)
            val headlines = items.value.filter { it.publishedAt >= dayStart }.map { it.title }
            if (headlines.isEmpty()) {
                _flashState.value = FlashUiState.Unavailable("Nothing flowed yet today.")
                return@launch
            }
            _flashState.value = when (val result = flashRouter.digest(DigestRequest(headlines))) {
                is DigestResult.Success -> FlashUiState.Ready(result.flash, result.provenance, headlines)
                is DigestResult.Failed -> {
                    // "No provider set up" is actionable (point at Settings); a
                    // real runtime error (a BYOK endpoint 401, say) is shown as-is.
                    val needsSetup = result.reason.contains("no reader-intelligence provider", ignoreCase = true)
                    FlashUiState.Unavailable(
                        reason = if (needsSetup) FLASH_NOT_CONFIGURED_REASON else result.reason,
                        needsSetup = needsSetup,
                    )
                }
            }
        }
    }

    private val _castState = MutableStateFlow<CastUiState>(CastUiState.Idle)

    /**
     * Nooz Cast (owner's ask): narrates whichever article is currently open
     * or resting ([articleState]) in a natural on-device voice — the full
     * body, not [flashState]'s ten-word line. [ttsProvider] is a single
     * on-device provider, never a router: Cast has no BYOK/cloud fallback at
     * all (owner: "a private anchor voice should never leave the device").
     */
    val castState: StateFlow<CastUiState> = _castState

    fun requestCast() {
        if (_castState.value is CastUiState.Loading) return
        viewModelScope.launch {
            _castState.value = CastUiState.Loading
            if (!ttsProvider.isAvailable()) {
                _castState.value = CastUiState.Unavailable(CAST_NOT_CONFIGURED_REASON, needsSetup = true)
                return@launch
            }
            val body = currentArticleBody()
            if (body.isNullOrBlank()) {
                _castState.value = CastUiState.Unavailable("Open an article to narrate it.")
                return@launch
            }
            _castState.value = when (val result = ttsProvider.synthesize(SynthesisRequest(body))) {
                is SynthesisResult.Success -> CastUiState.Ready(result.audioPath, result.provenance)
                is SynthesisResult.Failed -> CastUiState.Unavailable(result.reason)
            }
        }
    }

    /** The open/resting article's full text — Cast's input, falling back to the feed summary only if the real body never loaded. */
    private fun currentArticleBody(): String? = when (val s = _articleState.value) {
        is ArticleUiState.Loaded -> s.paragraphs.joinToString("\n\n")
        is ArticleUiState.Fallback -> s.summary
        is ArticleUiState.Loading -> null
    }

    private val _historyState = MutableStateFlow<HistoryUiState>(HistoryUiState.Idle)

    /**
     * Today in History (owner's ask, 2026-08): a few dated lines above the
     * day's stories. Only ever populated by [loadTodayInHistory], which the
     * card itself calls when the setting is on, so a reader who leaves the
     * feature off never causes the fetch at all.
     */
    val historyState: StateFlow<HistoryUiState> = _historyState

    /**
     * Idempotent by design: the Stand's list recomposes constantly (every
     * scroll, filter, refresh), and the card asks on each first composition,
     * so anything past the first call while a result is already in hand must
     * be a no-op or this would hammer Wikipedia. The repository caches per
     * calendar day underneath this too, so even a process restart re-reads
     * from disk rather than refetching.
     */
    fun loadTodayInHistory() {
        if (_historyState.value !is HistoryUiState.Idle) return
        fetchTodayInHistory()
    }

    /**
     * An explicit retry, from tapping the card's own failure line. Needed
     * because [loadTodayInHistory]'s guard deliberately won't re-run itself
     * after a failure: someone who was offline when the Stand first opened
     * would otherwise be stuck with that line until the app restarted.
     */
    fun retryTodayInHistory() {
        if (_historyState.value is HistoryUiState.Loading) return
        fetchTodayInHistory()
    }

    private fun fetchTodayInHistory() {
        _historyState.value = HistoryUiState.Loading
        viewModelScope.launch {
            _historyState.value = todayInHistoryRepository.column()
                .fold(
                    onSuccess = { events ->
                        if (events.isEmpty()) {
                            HistoryUiState.Unavailable("Nothing recorded for today's date.")
                        } else {
                            HistoryUiState.Ready(events)
                        }
                    },
                    onFailure = { HistoryUiState.Unavailable("Couldn't reach Wikipedia for today's date.") },
                )
        }
    }

    private val _readingAside = MutableStateFlow<FoundQuote?>(null)

    /** The current found-quote/dateline pick (see [FoundQuote]), or null. Set by the reading clock below; cleared only by a later pick. */
    val readingAside: StateFlow<FoundQuote?> = _readingAside

    // Gates the reading clock below to whenever the reader screen is actually
    // the one on screen and resumed -- set from ReaderDetailScreen's own
    // lifecycle observer. A transient in-memory flag only, never persisted
    // (this app deliberately never stores precise reading duration -- see
    // ReadEventRepository's own doc comment).
    @Volatile
    private var readerActive = false
    private var readingClockMs = 0L

    fun setReaderActive(active: Boolean) {
        readerActive = active
    }

    private val _isRefreshing = MutableStateFlow(false)
    /** True while a fetch is in flight, so the UI can show progress instead of a bare empty state. */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _lastRefresh = MutableStateFlow<RefreshResult?>(null)
    val lastRefresh: StateFlow<RefreshResult?> = _lastRefresh

    init {
        // Fetch once, automatically, the first time the reader is opened with
        // sources added but nothing yet ingested — so adding sources and coming
        // back to the stand "just works" instead of waiting on the background
        // cadence. `.first { it }` suspends until the condition first holds.
        viewModelScope.launch {
            combine(enabledSourceCount, allItems) { count, list -> count > 0 && list.isEmpty() }
                .first { it }
            if (_lastRefresh.value == null) refresh()
        }
        // Nooz Flash, known upfront rather than only after a tap (owner's
        // ask): if nothing is configured yet, show that immediately instead
        // of the ordinary "tap to compress" invitation, which used to be the
        // only way to discover it wouldn't work at all. Guarded on still
        // being Idle so this can never clobber a real request already in
        // flight (or its result) if one somehow won the race.
        viewModelScope.launch {
            if (!FLASH_COMING_SOON && !flashRouter.hasAvailableProvider() && _flashState.value == FlashUiState.Idle) {
                _flashState.value = FlashUiState.Unavailable(FLASH_NOT_CONFIGURED_REASON, needsSetup = true)
            }
        }
        // Nooz Cast gets the same upfront honesty as Flash, above — its own
        // gate, since Kokoro is a separate, independently-downloaded model.
        viewModelScope.launch {
            if (!ttsProvider.isAvailable() && _castState.value == CastUiState.Idle) {
                _castState.value = CastUiState.Unavailable(CAST_NOT_CONFIGURED_REASON, needsSetup = true)
            }
        }
        // The reading-aside clock (owner's ask, matching the web reader): every
        // few minutes of actual reading -- gated on readerActive, so time spent
        // elsewhere in the app or with the screen off never counts -- pick a
        // real line from whatever article is currently open.
        viewModelScope.launch {
            while (isActive) {
                delay(READING_CLOCK_TICK_MS)
                if (!readerActive) continue
                readingClockMs += READING_CLOCK_TICK_MS
                if (readingClockMs < READING_ASIDE_INTERVAL_MS) continue
                readingClockMs = 0L
                val item = selectedItem ?: continue
                val loaded = _articleState.value as? ArticleUiState.Loaded ?: continue
                pickFoundQuote(item.id, sourceTitles.value[item.sourceId], loaded.paragraphs)?.let {
                    _readingAside.value = it
                }
            }
        }
    }

    /**
     * Fetch every enabled source now, ingest, and roll the aggregates forward —
     * the same work the background FetchWorker does, but on demand with visible
     * progress. Per-source failures are isolated.
     */
    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                val outcomes = itemRepository.fetchAndIngestAllEnabled()
                weeklyAggregateRepository.recompute()
                _lastRefresh.value = RefreshResult(
                    newItems = outcomes.sumOf { it.newItemCount },
                    sourcesTried = outcomes.size,
                    sourcesFailed = outcomes.count { !it.succeeded },
                )
            } catch (e: Exception) {
                _lastRefresh.value = RefreshResult(0, 0, 0, error = e.message ?: e.javaClass.simpleName)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    var selectedItem: Item? by mutableStateOf(null)
        private set

    /**
     * True when the current item was auto-selected to *rest* in the parked home
     * (owner #8: the Stand list with the reader peeking), not opened to read.
     * A rest never records a read event — a peeking headline isn't a read — and
     * only becomes a real session once the reader engages it ([markEngaged]).
     */
    var openedAtRest: Boolean by mutableStateOf(false)
        private set

    private val _articleState = MutableStateFlow<ArticleUiState>(ArticleUiState.Loading)
    val articleState: StateFlow<ArticleUiState> = _articleState

    /**
     * Open an item. A real open ([rest] false) records a coarse read event
     * (brief §3 — glance/partial/read, never a precise duration) and loads its
     * text; a rest open ([rest] true — the parked-home peek) loads the text but
     * records nothing until the reader actually engages it.
     */
    fun openItem(item: Item, viaRiver: Boolean = false, rest: Boolean = false) {
        openedAtRest = rest
        selectedItem = item
        _articleState.value = ArticleUiState.Loading
        viewModelScope.launch {
            if (!rest) readEventRepository.record(item.id, DwellBucket.GLANCE, viaRiver)
            val text = articleRepository.textFor(item.id, item.canonicalUrl)
            _articleState.value = if (text != null) {
                ArticleUiState.Loaded(text.paragraphs, text.fromCache)
            } else {
                ArticleUiState.Fallback(item.summary)
            }
        }
    }

    /** The reader brought a resting (peeking) item to full — now it's a real read session; record the glance. */
    fun markEngaged() {
        if (!openedAtRest) return
        openedAtRest = false
        selectedItem?.let { viewModelScope.launch { readEventRepository.record(it.id, DwellBucket.GLANCE, false) } }
    }

    /** The user read past the initial glance — upgrade the dwell bucket (still coarse, never a duration). */
    fun markPartiallyRead(item: Item, viaRiver: Boolean = false) {
        viewModelScope.launch { readEventRepository.record(item.id, DwellBucket.PARTIAL, viaRiver) }
    }

    fun markFullyRead(item: Item, viaRiver: Boolean = false) {
        viewModelScope.launch { readEventRepository.record(item.id, DwellBucket.READ, viaRiver) }
    }

    /**
     * Closing after the text loaded successfully upgrades the dwell bucket to
     * READ — a coarse v1 heuristic (brief §3: buckets only, never a precise
     * duration or scroll-position tracking).
     */
    fun closeItem() {
        val item = selectedItem
        // A never-engaged resting peek isn't a read — don't count it on close.
        if (item != null && !openedAtRest && _articleState.value is ArticleUiState.Loaded) {
            markFullyRead(item)
        }
        openedAtRest = false
        selectedItem = null
    }

    class Factory(
        private val itemRepository: ItemRepository,
        private val sourceRepository: SourceRepository,
        private val articleRepository: ArticleRepository,
        private val readEventRepository: ReadEventRepository,
        private val weeklyAggregateRepository: WeeklyAggregateRepository,
        private val clippingRepository: ClippingRepository,
        private val settingsRepository: SettingsRepository,
        private val todayInHistoryRepository: TodayInHistoryRepository,
        private val flashRouter: InferenceRouter,
        private val ttsProvider: TtsProvider,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T =
            ReaderViewModel(
                itemRepository,
                sourceRepository,
                articleRepository,
                readEventRepository,
                weeklyAggregateRepository,
                clippingRepository,
                settingsRepository,
                todayInHistoryRepository,
                flashRouter,
                ttsProvider,
            ) as T
    }
}
