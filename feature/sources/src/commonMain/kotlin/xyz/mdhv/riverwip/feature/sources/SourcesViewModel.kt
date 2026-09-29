package xyz.mdhv.riverwip.feature.sources

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlin.reflect.KClass
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.mdhv.riverwip.data.repo.CatalogueRepository
import xyz.mdhv.riverwip.data.repo.ItemRepository
import xyz.mdhv.riverwip.data.repo.SettingsRepository
import xyz.mdhv.riverwip.data.repo.SourceRepository
import xyz.mdhv.riverwip.data.repo.WeeklyAggregateRepository
import xyz.mdhv.riverwip.model.Classifier
import xyz.mdhv.riverwip.model.FeedAutodiscovery
import xyz.mdhv.riverwip.model.ReaderFilter
import xyz.mdhv.riverwip.model.Region
import xyz.mdhv.riverwip.model.ServiceDef
import xyz.mdhv.riverwip.model.Source
import xyz.mdhv.riverwip.model.SourceHealth
import xyz.mdhv.riverwip.model.Starters
import xyz.mdhv.riverwip.model.Topic
import xyz.mdhv.riverwip.model.WeeklyAggregate
import xyz.mdhv.riverwip.model.toSourceOrNull

/** Transient state for the add-by-URL flow. Never persisted. */
sealed interface AddUiState {
    data object Idle : AddUiState
    data object Loading : AddUiState
    data class Added(val title: String) : AddUiState
    data class Choices(val candidates: List<FeedAutodiscovery.DiscoveredFeed>) : AddUiState
    data class Error(val message: String) : AddUiState
}

/** Result of the last manual catalogue refresh (brief §P6). Never fetched automatically. */
sealed interface CatalogueRefreshUiState {
    data object Idle : CatalogueRefreshUiState
    data object Loading : CatalogueRefreshUiState
    data class Refreshed(val serviceCount: Int) : CatalogueRefreshUiState
    data class Error(val message: String) : CatalogueRefreshUiState
}

class SourcesViewModel(
    private val repo: SourceRepository,
    private val catalogueRepo: CatalogueRepository,
    private val settingsRepo: SettingsRepository,
    itemRepository: ItemRepository,
    weeklyAggregateRepository: WeeklyAggregateRepository,
) : ViewModel() {

    /** The standing region + topics filter (the Region & Topics tab edits a draft of this). */
    val filter: StateFlow<ReaderFilter> = settingsRepo.observeFilter()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ReaderFilter())

    fun saveFilter(filter: ReaderFilter) {
        viewModelScope.launch { settingsRepo.setFilter(filter) }
    }

    /**
     * Live topic mix per candidate region — the globe's ring. Real counts from
     * the current stream (a region's mix = items from sources that would flow
     * under that region), never invented per-sector numbers.
     */
    val mixByRegion: StateFlow<Map<Region, Map<Topic, Int>>> =
        itemRepository.observeItemsForEnabledSources().map { items ->
            val out = HashMap<Region, HashMap<Topic, Int>>()
            for (region in Region.entries) out[region] = HashMap()
            for (item in items) {
                val tag = Starters.regionBySourceId[item.sourceId]
                val topic = Classifier.dominantTopic(item.topics)
                for (region in Region.entries) {
                    if (ReaderFilter(region).includesSource(tag)) {
                        out.getValue(region).merge(topic, 1, Int::plus)
                    }
                }
            }
            out
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyMap())

    /** Weekly aggregates for the metrics block under the globe (coverage/breadth/over-under). */
    val aggregates: StateFlow<List<WeeklyAggregate>> = weeklyAggregateRepository.observeAggregates()
        .map { list -> list.sortedBy { it.weekStart } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    /** Starter toggle (the mock's check circle): absent → add, present → remove. */
    fun toggleStarter(def: ServiceDef) {
        val id = def.toSourceOrNull(addedAt = 0L)?.id ?: return
        val existing = sources.value.firstOrNull { it.id == id }
        if (existing == null) addStarter(def) else remove(existing.id)
    }

    val sources: StateFlow<List<Source>> =
        repo.observeSources().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val enabledCount: StateFlow<Int> =
        repo.observeEnabledCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), 0)

    /** Local source-health monitor (brief §P6), keyed by source id for easy row lookup. */
    val health: StateFlow<Map<String, SourceHealth>> =
        repo.observeHealth()
            .map { list -> list.associateBy { it.sourceId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyMap())

    /**
     * Concrete, one-click starters grouped by region (brief §P1) — from the
     * built-in verified seed until/unless the user loads a remote catalogue
     * (brief §P6), which transparently takes over the same shape.
     */
    val startersByRegion: StateFlow<Map<String, List<ServiceDef>>> =
        catalogueRepo.observeCatalogue()
            .map { cat -> cat.services.filter { it.url != null }.groupBy { it.region ?: "other" } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyMap())

    /** Query-builder and keyed starters (Google News, GDELT, Mastodon, Guardian, Tier-B reference). */
    val builders: StateFlow<List<ServiceDef>> =
        catalogueRepo.observeCatalogue()
            .map { cat -> cat.services.filter { it.url == null } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val catalogueUrl: StateFlow<String?> =
        catalogueRepo.observeCatalogueUrl().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    val catalogueLastRefreshedAt: StateFlow<Long?> =
        catalogueRepo.observeLastRefreshedAt().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    var catalogueRefreshState: CatalogueRefreshUiState by mutableStateOf(CatalogueRefreshUiState.Idle)
        private set

    fun setCatalogueUrl(url: String) {
        viewModelScope.launch { catalogueRepo.setCatalogueUrl(url) }
    }

    fun refreshCatalogue() {
        catalogueRefreshState = CatalogueRefreshUiState.Loading
        viewModelScope.launch {
            catalogueRefreshState = when (val r = catalogueRepo.refresh()) {
                is CatalogueRepository.RefreshResult.Success ->
                    CatalogueRefreshUiState.Refreshed(r.catalogue.services.size)
                is CatalogueRepository.RefreshResult.Failed -> CatalogueRefreshUiState.Error(r.reason)
            }
        }
    }

    /** Reverts to the built-in verified starters, discarding any cached remote catalogue. */
    fun clearCatalogue() {
        catalogueRefreshState = CatalogueRefreshUiState.Idle
        viewModelScope.launch { catalogueRepo.clearCatalogue() }
    }

    var addState: AddUiState by mutableStateOf(AddUiState.Idle)
        private set

    fun resetAddState() { addState = AddUiState.Idle }

    fun addByUrl(url: String) {
        addState = AddUiState.Loading
        viewModelScope.launch {
            addState = when (val r = repo.addByUrl(url)) {
                is SourceRepository.AddResult.Added -> AddUiState.Added(r.source.title)
                is SourceRepository.AddResult.NeedsChoice -> AddUiState.Choices(r.candidates)
                is SourceRepository.AddResult.Failed -> AddUiState.Error(r.reason)
            }
        }
    }

    fun addCandidate(feed: FeedAutodiscovery.DiscoveredFeed) {
        viewModelScope.launch {
            val r = repo.addResolvedFeed(feed.url, feed.title ?: feed.url)
            addState = AddUiState.Added(r.source.title)
        }
    }

    fun addStarter(def: ServiceDef) {
        viewModelScope.launch {
            addState = when (val r = repo.addStarter(def)) {
                is SourceRepository.AddResult.Added -> AddUiState.Added(r.source.title)
                is SourceRepository.AddResult.Failed -> AddUiState.Error(r.reason)
                is SourceRepository.AddResult.NeedsChoice -> AddUiState.Idle
            }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) { viewModelScope.launch { repo.setEnabled(id, enabled) } }

    fun remove(id: String) { viewModelScope.launch { repo.remove(id) } }

    /**
     * Onboarding's "Quick setup" (owner: it "isn't doing any setup at all" —
     * it used to only skip the Advanced wizard and drop the reader on an
     * empty Stand). A small, editorially diverse set of verified global
     * outlets, so a fresh install has something to read immediately.
     */
    fun quickSetup() {
        val existingIds = sources.value.map { it.id }.toSet()
        for (id in QUICK_SETUP_IDS) {
            val def = Starters.verifiedFeeds.firstOrNull { it.id == id } ?: continue
            val sourceId = def.toSourceOrNull(addedAt = 0L)?.id ?: continue
            if (sourceId !in existingIds) addStarter(def)
        }
    }

    fun importOpml(xml: String, onDone: (Int) -> Unit) {
        viewModelScope.launch { onDone(repo.importOpml(xml)) }
    }

    suspend fun exportOpml(): String = repo.exportOpml()

    companion object {
        // Broad, editorially varied coverage without overwhelming a first run.
        private val QUICK_SETUP_IDS = listOf("bbc-top", "npr-news", "guardian-world", "aljazeera-all", "nyt-top")
    }

    class Factory(
        private val repo: SourceRepository,
        private val catalogueRepo: CatalogueRepository,
        private val settingsRepo: SettingsRepository,
        private val itemRepository: ItemRepository,
        private val weeklyAggregateRepository: WeeklyAggregateRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T =
            SourcesViewModel(repo, catalogueRepo, settingsRepo, itemRepository, weeklyAggregateRepository) as T
    }
}
