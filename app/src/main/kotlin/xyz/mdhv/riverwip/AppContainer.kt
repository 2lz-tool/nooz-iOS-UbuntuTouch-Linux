package xyz.mdhv.riverwip

import android.content.Context
import java.io.File
import xyz.mdhv.riverwip.data.AndroidDataPlatform
import xyz.mdhv.riverwip.data.RiverData
import xyz.mdhv.riverwip.data.repo.ArticleRepository
import xyz.mdhv.riverwip.data.repo.CatalogueRepository
import xyz.mdhv.riverwip.data.repo.ClippingRepository
import xyz.mdhv.riverwip.data.repo.DataExporter
import xyz.mdhv.riverwip.data.repo.DictionaryRepository
import xyz.mdhv.riverwip.data.repo.TranslationRepository
import xyz.mdhv.riverwip.data.repo.ItemRepository
import xyz.mdhv.riverwip.data.repo.ModelCatalogueRepository
import xyz.mdhv.riverwip.data.repo.ReadEventRepository
import xyz.mdhv.riverwip.data.repo.SettingsRepository
import xyz.mdhv.riverwip.data.repo.SourceRepository
import xyz.mdhv.riverwip.data.repo.TodayInHistoryRepository
import xyz.mdhv.riverwip.data.repo.WeeklyAggregateRepository
import xyz.mdhv.riverwip.data.work.RiverWorkerFactory
import xyz.mdhv.riverwip.inference.InferenceProvider
import xyz.mdhv.riverwip.inference.InferenceRouter
import xyz.mdhv.riverwip.inference.ProviderFactory
import xyz.mdhv.riverwip.inference.TtsProvider
import xyz.mdhv.riverwip.inference.byok.ByokConfigStore
import xyz.mdhv.riverwip.inference.local.LocalKokoroTtsProvider

/**
 * The manual DI composition root (house style: constructor injection, no
 * framework). The data layer is assembled behind [RiverData] so the app never
 * references a Room type (Room stays an implementation detail of `:core:data`).
 * Each phase grows this graph:
 *  - P1 sources: RiverData → SourceRepository.
 *  - P2 ingest:  RiverData → ItemRepository, ReadEventRepository,
 *                WeeklyAggregateRepository, workerFactory (WorkManager).
 *  - P3 reader:  articleRepository (full-text extraction + LRU cache).
 *  - P4 river:   aggregate store + analysis (uses weeklyAggregateRepository).
 *  - P5 lens:    inferenceRouter (default order: Urbana → local llama.cpp →
 *                ML Kit — the last only in the `full` flavor).
 *  - P6 catalogue: catalogueRepository (remote refresh, no baked-in default
 *                  URL — see STATE.md decision D4).  ← here
 */
class AppContainer(appContext: Context) {

    private val platform = AndroidDataPlatform(appContext)
    private val data: RiverData = RiverData.create(platform)

    val sourceRepository: SourceRepository = data.sourceRepository
    val itemRepository: ItemRepository = data.itemRepository
    val readEventRepository: ReadEventRepository = data.readEventRepository
    val weeklyAggregateRepository: WeeklyAggregateRepository = data.weeklyAggregateRepository
    val articleRepository: ArticleRepository = data.articleRepository
    val catalogueRepository: CatalogueRepository = data.catalogueRepository
    val clippingRepository: ClippingRepository = data.clippingRepository
    val dictionaryRepository: DictionaryRepository = data.dictionaryRepository
    val translationRepository: TranslationRepository = data.translationRepository
    val settingsRepository: SettingsRepository = data.settingsRepository
    val todayInHistoryRepository: TodayInHistoryRepository = data.todayInHistoryRepository
    val dataExporter: DataExporter = data.dataExporter

    private val inferenceProviders: List<InferenceProvider> =
        ProviderFactory.build(appContext, File(appContext.filesDir, "models"))

    /** Downloaded models live in persistent storage (never purged like a cache), never bundled in the APK. */
    val inferenceRouter: InferenceRouter = InferenceRouter(inferenceProviders)

    /**
     * Nooz Flash (owner's #6: "on-device only, BYOK optional") — a narrower
     * router over the same provider instances as [inferenceRouter], deliberately
     * excluding Urbana/ML Kit: the flash digest never routes to a general cloud
     * broker the way the main lens's rewrite can, only the device or a key the
     * user explicitly typed in themselves.
     */
    val flashRouter: InferenceRouter = InferenceRouter(
        listOfNotNull(
            inferenceProviders.find { it.id == "local-llama" },
            inferenceProviders.find { it.id == "byok" },
        ),
    )

    /**
     * Nooz Cast (owner: "if the on-device model exists... enable Nooz Cast as
     * well - unless that required a specific TTS model, in which case the
     * same compulsion must exist") — Kokoro is a different model class than
     * whatever LLM [flashRouter] uses, downloaded and gated independently
     * through its own catalogue entries, but landing in the same flat
     * `models/` directory [modelCatalogueRepository] downloads everything
     * into (see its own `fileFor()` — files are told apart by name, not by
     * directory).
     */
    val ttsProvider: TtsProvider = LocalKokoroTtsProvider(appContext, File(appContext.filesDir, "models"))

    /** The user's own OpenAI-compatible endpoint config (BYOK, #18). Shared with the provider by prefs name. */
    val byokConfigStore: ByokConfigStore = ByokConfigStore(appContext)

    /** Real one-click downloadable models (owner's #18 follow-up) — shares `models/` with [inferenceRouter]'s LocalLlamaProvider. */
    val modelCatalogueRepository: ModelCatalogueRepository = ModelCatalogueRepository(platform)

    /** For `Configuration.Provider` on [RiverApplication] — never touched by feature UI. */
    val workerFactory: RiverWorkerFactory =
        RiverWorkerFactory(data.itemRepository, data.weeklyAggregateRepository, data.articleRepository)
}
