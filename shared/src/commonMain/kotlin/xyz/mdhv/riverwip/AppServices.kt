package xyz.mdhv.riverwip

import xyz.mdhv.riverwip.crash.CrashReport
import xyz.mdhv.riverwip.data.net.ImageStore
import xyz.mdhv.riverwip.data.repo.ArticleRepository
import xyz.mdhv.riverwip.data.repo.CatalogueRepository
import xyz.mdhv.riverwip.data.repo.ClippingRepository
import xyz.mdhv.riverwip.data.repo.DataExporter
import xyz.mdhv.riverwip.data.repo.DictionaryRepository
import xyz.mdhv.riverwip.data.repo.ItemRepository
import xyz.mdhv.riverwip.data.repo.ModelCatalogueRepository
import xyz.mdhv.riverwip.data.repo.ReadEventRepository
import xyz.mdhv.riverwip.data.repo.SettingsRepository
import xyz.mdhv.riverwip.data.repo.SourceRepository
import xyz.mdhv.riverwip.data.repo.TodayInHistoryRepository
import xyz.mdhv.riverwip.data.repo.TranslationRepository
import xyz.mdhv.riverwip.data.repo.WeeklyAggregateRepository
import xyz.mdhv.riverwip.inference.InferenceRouter
import xyz.mdhv.riverwip.inference.TtsProvider
import xyz.mdhv.riverwip.inference.byok.ByokConfigStore

/**
 * Everything the app UI needs from the platform it runs on, assembled by that platform's
 * composition root (`AppContainer` on Android, `DesktopServices` on Linux). The UI in `:shared`
 * only ever sees this interface, so the same screens run unchanged on both.
 */
interface AppServices {
    val sourceRepository: SourceRepository
    val itemRepository: ItemRepository
    val readEventRepository: ReadEventRepository
    val weeklyAggregateRepository: WeeklyAggregateRepository
    val articleRepository: ArticleRepository
    val catalogueRepository: CatalogueRepository
    val clippingRepository: ClippingRepository
    val dictionaryRepository: DictionaryRepository
    val translationRepository: TranslationRepository
    val settingsRepository: SettingsRepository
    val todayInHistoryRepository: TodayInHistoryRepository
    val dataExporter: DataExporter
    val imageStore: ImageStore
    val modelCatalogueRepository: ModelCatalogueRepository

    val inferenceRouter: InferenceRouter
    val flashRouter: InferenceRouter
    val ttsProvider: TtsProvider
    val byokConfigStore: ByokConfigStore

    /** False where no on-device model can run (desktop for now): the UI then offers only bring-your-own-key. */
    val localModels: Boolean

    val locale: LocaleController
    val window: WindowControls
    val crashReports: CrashReports
}

/** The services of the running app, for screens that would otherwise have to thread them through a dozen parameters. */
val LocalAppServices = androidx.compose.runtime.staticCompositionLocalOf<AppServices> { error("AppServices not provided") }

/** The interface language. Android delegates to the system's per-app language; desktop sets the JVM default. */
interface LocaleController {
    /** The BCP-47 tag in force, or [SYSTEM_DEFAULT] to follow the system. */
    fun current(): String

    /** Switch language. Android re-creates its activity; desktop bumps [epoch] so the UI rebuilds. */
    fun set(tag: String)

    /** Changes whenever the UI must be rebuilt from scratch to pick up a new language. Read inside composition. */
    val epoch: Int

    companion object {
        const val SYSTEM_DEFAULT = ""
    }
}

/** Window-level effects the reader can ask for. Each is a no-op where the platform has no equivalent. */
interface WindowControls {
    /** Nudge the window's own brightness (Android, no permission needed). */
    fun adjustBrightness(delta: Float)

    /** Tell the system bars / title bar whether the app is currently painting a dark surface. */
    fun setDarkSurface(dark: Boolean)
}

/** A device-only crash report, if the last run crashed. Nothing is ever transmitted. */
interface CrashReports {
    fun pending(): CrashReport.Decoded?
    fun clear()
}
