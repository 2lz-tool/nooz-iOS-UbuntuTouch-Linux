package xyz.mdhv.riverwip.feature.river

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlin.reflect.KClass
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.mdhv.riverwip.data.repo.ItemRepository
import xyz.mdhv.riverwip.data.repo.ReadEventRepository
import xyz.mdhv.riverwip.data.repo.SettingsRepository
import xyz.mdhv.riverwip.data.repo.SourceRepository
import xyz.mdhv.riverwip.data.repo.WeeklyAggregateRepository
import xyz.mdhv.riverwip.model.Item
import xyz.mdhv.riverwip.model.ReadEvent
import xyz.mdhv.riverwip.model.ReaderFilter
import xyz.mdhv.riverwip.model.Region
import xyz.mdhv.riverwip.model.SourceKind
import xyz.mdhv.riverwip.model.WeekBucketing
import xyz.mdhv.riverwip.model.WeeklyAggregate

/**
 * Result of a manual GDELT historical-fetch request (the long-pending "fetch
 * content for any date" item). Never persisted; mirrors
 * [ItemRepository.FetchOutcome] honestly rather than collapsing a partial
 * failure into a plain "done" — [Done] carries the real new-item count, how
 * many sources were actually queried, and any per-source errors.
 */
sealed interface HistoricalFetchState {
    data object Idle : HistoricalFetchState
    data object Loading : HistoricalFetchState
    data class Done(val newItemCount: Int, val sourcesQueried: Int, val errors: List<String>) : HistoricalFetchState
}

/**
 * The day loom's state: day-grained aggregates (computed on demand — the
 * persisted roll-up stays weekly per the brief), the selected day, and the
 * header facts (source count, region). The loom always shows the full supply
 * from the enabled source-set; the standing topic filter never hides supply —
 * omission is the subject, not something to filter away.
 */
class LoomViewModel(
    private val weeklyAggregateRepository: WeeklyAggregateRepository,
    sourceRepository: SourceRepository,
    private val settingsRepository: SettingsRepository,
    private val itemRepository: ItemRepository,
    readEventRepository: ReadEventRepository,
) : ViewModel() {

    private val _days = MutableStateFlow<List<WeeklyAggregate>>(emptyList())
    /** One aggregate per day with any data, ascending by day. */
    val days: StateFlow<List<WeeklyAggregate>> = _days

    val enabledSourceCount: StateFlow<Int> = sourceRepository.observeEnabledCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 0)

    val filter: StateFlow<ReaderFilter> = settingsRepository.observeFilter()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ReaderFilter())

    /**
     * Recent items from enabled sources — the raw headlines the Framings view
     * clusters by story. Only recent items survive (older ones are pruned to
     * the weekly aggregate), so framing contrasts are a recent-days feature;
     * the panel shows an empty state for days whose items are gone.
     */
    val recentItems: StateFlow<List<Item>> = itemRepository.observeItemsForEnabledSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    /** Source id → title, for labelling each framing with its outlet. */
    val sourceTitles: StateFlow<Map<String, String>> = sourceRepository.observeSources()
        .map { list -> list.associate { it.id to it.title } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyMap())

    /**
     * Count of enabled GDELT-kind sources — the only catalogue provider a
     * historical, absolute-date fetch is actually possible for (the
     * long-pending "fetch content for any date" item; RSS/Atom, Google News,
     * Mastodon, and the generic `api` kind have no such capability, full
     * stop). Gates whether the empty-day affordance below offers it at all,
     * rather than showing a control that can never do anything.
     */
    val gdeltEnabledCount: StateFlow<Int> = sourceRepository.observeSources()
        .map { list -> list.count { it.enabled && it.kind == SourceKind.GDELT } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 0)

    /** Every read event, for the Contrast view's read-by-region heatmap (joined to items by the panel). */
    val readEvents: StateFlow<List<ReadEvent>> = readEventRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    /** Region and topic interactions now live in the Contrast space (owner #3), writing the same standing filter. */
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

    /** Selected day start (millis), or the range start when [selectedRangeEnd] is set. Null = the latest day with data (today, once anything flowed). */
    var selectedDayStart: Long? by mutableStateOf(null)
        private set

    /** Set only when the picker chose a range (owner's #4); the loom then weaves the whole range's totals, summed. */
    var selectedRangeEnd: Long? by mutableStateOf(null)
        private set

    var showDatePicker: Boolean by mutableStateOf(false)
        private set

    /** Result of the last manual GDELT historical-fetch request, if any. Never persisted. */
    var historicalFetchState: HistoricalFetchState by mutableStateOf(HistoricalFetchState.Idle)
        private set

    fun resetHistoricalFetchState() {
        historicalFetchState = HistoricalFetchState.Idle
    }

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch { _days.value = weeklyAggregateRepository.dailyAggregates() }
    }

    /**
     * Manually ask GDELT for the day (or range) currently shown, instead of
     * only ever reading whatever's already stored locally — the long-pending
     * "fetch content for any date" item. Scoped to enabled GDELT-kind sources
     * only; see [ItemRepository.fetchHistoricalGdelt] for why RSS/Atom,
     * Google News, Mastodon, and the generic `api` kind have no such
     * capability at all. Merges any new items the normal way, then reloads
     * so the loom reflects them immediately.
     */
    fun fetchHistoricalGdelt(days: List<WeeklyAggregate>) {
        val today = WeekBucketing.periodStart(System.currentTimeMillis(), periodDays = 1)
        val start = selectedDayStart ?: days.lastOrNull()?.weekStart ?: today
        val end = (selectedRangeEnd ?: start) + MILLIS_PER_DAY
        historicalFetchState = HistoricalFetchState.Loading
        viewModelScope.launch {
            val outcomes = itemRepository.fetchHistoricalGdelt(start, end)
            weeklyAggregateRepository.recompute()
            // Inline rather than calling reload() — reload() launches its own
            // child coroutine, which would race the Done state set just below
            // it instead of guaranteeing the loom reflects the new items first.
            _days.value = weeklyAggregateRepository.dailyAggregates()
            historicalFetchState = HistoricalFetchState.Done(
                newItemCount = outcomes.sumOf { it.newItemCount },
                sourcesQueried = outcomes.size,
                errors = outcomes.mapNotNull { it.error },
            )
        }
    }

    fun selectDay(dayStartMillis: Long) {
        selectedDayStart = WeekBucketing.periodStart(dayStartMillis, periodDays = 1)
        selectedRangeEnd = null
        showDatePicker = false
        resetHistoricalFetchState()
    }

    /** A picked range (owner's #4: "the date picker in loom needs to also allow a range selection"). Order-independent; a same-day "range" just collapses to a single-day selection. */
    fun selectRange(startMillis: Long, endMillis: Long) {
        val a = WeekBucketing.periodStart(startMillis, periodDays = 1)
        val b = WeekBucketing.periodStart(endMillis, periodDays = 1)
        val start = minOf(a, b)
        val end = maxOf(a, b)
        selectedDayStart = start
        selectedRangeEnd = end.takeIf { it != start }
        showDatePicker = false
        resetHistoricalFetchState()
    }

    /**
     * Explicit day navigation (owner's #8: a tap-to-open month grid wasn't
     * discoverable as "date navigation"). Steps by whole calendar days,
     * bounded so it can never move past today.
     */
    fun stepDay(deltaDays: Int, days: List<WeeklyAggregate>) {
        val today = WeekBucketing.periodStart(System.currentTimeMillis(), periodDays = 1)
        val current = selectedRangeEnd ?: selectedDayStart ?: days.lastOrNull()?.weekStart ?: today
        val stepped = WeekBucketing.periodStart(
            current + deltaDays * MILLIS_PER_DAY,
            periodDays = 1,
        )
        selectedDayStart = stepped.coerceAtMost(today)
        selectedRangeEnd = null
        resetHistoricalFetchState()
    }

    /** Whether [stepDay] can still move forward from the currently-shown day. */
    fun canStepForward(days: List<WeeklyAggregate>): Boolean {
        val today = WeekBucketing.periodStart(System.currentTimeMillis(), periodDays = 1)
        val current = selectedRangeEnd ?: selectedDayStart ?: days.lastOrNull()?.weekStart ?: today
        return current < today
    }

    fun setDatePickerVisible(visible: Boolean) {
        showDatePicker = visible
    }

    /**
     * The aggregate to weave: the selected day, the *summed* totals across a
     * selected range, the latest day with data, or an empty day. Summing is
     * plain addition over each day's real per-topic counts — never a second
     * invented number for the range as a whole.
     */
    fun aggregateFor(days: List<WeeklyAggregate>): WeeklyAggregate? {
        val start = selectedDayStart ?: return days.lastOrNull()
        val end = selectedRangeEnd
        if (end == null) {
            return days.firstOrNull { it.weekStart == start }
                ?: WeeklyAggregate(start, emptyMap(), emptyMap(), emptyMap())
        }
        val inRange = days.filter { it.weekStart in start..end }
        val stream = HashMap<String, Int>()
        val read = HashMap<String, Int>()
        val sources = HashMap<String, Int>()
        for (day in inRange) {
            day.streamCountsByTopic.forEach { (k, v) -> stream.merge(k, v, Int::plus) }
            day.readCountsByTopic.forEach { (k, v) -> read.merge(k, v, Int::plus) }
            day.sourceCounts.forEach { (k, v) -> sources.merge(k, v, Int::plus) }
        }
        return WeeklyAggregate(start, stream, read, sources)
    }

    companion object {
        private const val MILLIS_PER_DAY = 86_400_000L
    }

    class Factory(
        private val weeklyAggregateRepository: WeeklyAggregateRepository,
        private val sourceRepository: SourceRepository,
        private val settingsRepository: SettingsRepository,
        private val itemRepository: ItemRepository,
        private val readEventRepository: ReadEventRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T =
            LoomViewModel(weeklyAggregateRepository, sourceRepository, settingsRepository, itemRepository, readEventRepository) as T
    }
}
