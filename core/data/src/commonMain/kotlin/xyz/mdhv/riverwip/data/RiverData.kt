package xyz.mdhv.riverwip.data

import xyz.mdhv.riverwip.data.cache.FullTextCache
import xyz.mdhv.riverwip.data.db.RiverDatabase
import xyz.mdhv.riverwip.data.net.FeedProbe
import xyz.mdhv.riverwip.data.net.HttpClient
import xyz.mdhv.riverwip.data.net.ImageStore
import xyz.mdhv.riverwip.data.repo.ArticleRepository
import xyz.mdhv.riverwip.data.repo.CatalogueRepository
import xyz.mdhv.riverwip.data.repo.ClippingRepository
import xyz.mdhv.riverwip.data.repo.DataExporter
import xyz.mdhv.riverwip.data.repo.DictionaryRepository
import xyz.mdhv.riverwip.data.repo.HttpArticleFetcher
import xyz.mdhv.riverwip.data.repo.ItemRepository
import xyz.mdhv.riverwip.data.repo.ReadEventRepository
import xyz.mdhv.riverwip.data.repo.SettingsRepository
import xyz.mdhv.riverwip.data.repo.SourceRepository
import xyz.mdhv.riverwip.data.repo.TodayInHistoryRepository
import xyz.mdhv.riverwip.data.repo.TranslationRepository
import xyz.mdhv.riverwip.data.repo.WeeklyAggregateRepository

/**
 * The data layer's assembly point. Constructs the Room database and wires the
 * repositories, exposing only domain-facing types -- never a Room type. This keeps Room an `implementation` detail of
 * `:core:data`: consumers (the app's composition root) depend on this factory,
 * not on `RoomDatabase`.
 */
class RiverData private constructor(
    val sourceRepository: SourceRepository,
    val itemRepository: ItemRepository,
    val readEventRepository: ReadEventRepository,
    val weeklyAggregateRepository: WeeklyAggregateRepository,
    val articleRepository: ArticleRepository,
    val catalogueRepository: CatalogueRepository,
    val clippingRepository: ClippingRepository,
    val dictionaryRepository: DictionaryRepository,
    val translationRepository: TranslationRepository,
    val settingsRepository: SettingsRepository,
    val todayInHistoryRepository: TodayInHistoryRepository,
    val dataExporter: DataExporter,
    val imageStore: ImageStore,
) {
    companion object {
        /** Full-text cache budget (brief §4: "user-visible storage budget"). Working default; a settings screen can raise/lower it later. */
        const val DEFAULT_FULL_TEXT_CACHE_BYTES: Long = 200L * 1024 * 1024

        fun create(platform: DataPlatform): RiverData {
            val db = RiverDatabase.build(platform.databaseBuilder(), platform.roomDriver)
            val http = HttpClient()
            val probe = FeedProbe(http)
            val itemRepository = ItemRepository(sourceDao = db.sourceDao(), itemDao = db.itemDao(), http = http)
            val weeklyAggregateRepository = WeeklyAggregateRepository(
                itemDao = db.itemDao(), readEventDao = db.readEventDao(), weeklyAggregateDao = db.weeklyAggregateDao(),
            )
            val fullTextCache = FullTextCache(platform.fileSystem, platform.cacheDir / "full-text", DEFAULT_FULL_TEXT_CACHE_BYTES)
            val articleRepository = ArticleRepository(
                itemDao = db.itemDao(), fetcher = HttpArticleFetcher(http), cache = fullTextCache,
                articleTextDao = db.articleTextDao(),
            )
            val sourceRepository = SourceRepository(dao = db.sourceDao(), probe = probe)
            val readEventRepository = ReadEventRepository(dao = db.readEventDao())
            val clippingRepository = ClippingRepository(dao = db.clippingDao())
            val settingsRepository = SettingsRepository(platform)
            return RiverData(
                sourceRepository = sourceRepository,
                itemRepository = itemRepository,
                readEventRepository = readEventRepository,
                weeklyAggregateRepository = weeklyAggregateRepository,
                articleRepository = articleRepository,
                catalogueRepository = CatalogueRepository(platform = platform, http = http),
                clippingRepository = clippingRepository,
                dictionaryRepository = DictionaryRepository(platform, http),
                translationRepository = TranslationRepository(platform),
                settingsRepository = settingsRepository,
                // Its own HttpClient, not the shared `http` above: Wikimedia's
                // user-agent policy wants a UA that identifies this app and
                // offers a contact, which feed fetches have no business
                // carrying to the reader's own sources.
                todayInHistoryRepository = TodayInHistoryRepository(platform),
                imageStore = ImageStore(platform.fileSystem, platform.cacheDir / "images") { http.getBytes(it) },
                dataExporter = DataExporter(
                    settingsRepository = settingsRepository,
                    sourceRepository = sourceRepository,
                    clippingRepository = clippingRepository,
                    readEventRepository = readEventRepository,
                ),
            )
        }
    }
}
