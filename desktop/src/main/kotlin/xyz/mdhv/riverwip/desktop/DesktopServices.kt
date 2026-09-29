package xyz.mdhv.riverwip.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import okio.FileSystem
import okio.Path
import xyz.mdhv.riverwip.AppServices
import xyz.mdhv.riverwip.CrashReports
import xyz.mdhv.riverwip.LocaleController
import xyz.mdhv.riverwip.WindowControls
import xyz.mdhv.riverwip.crash.CrashReport
import xyz.mdhv.riverwip.data.DesktopDataPlatform
import xyz.mdhv.riverwip.data.RiverData
import xyz.mdhv.riverwip.data.repo.ModelCatalogueRepository
import xyz.mdhv.riverwip.inference.InferenceProvider
import xyz.mdhv.riverwip.inference.InferenceRouter
import xyz.mdhv.riverwip.inference.SynthesisRequest
import xyz.mdhv.riverwip.inference.SynthesisResult
import xyz.mdhv.riverwip.inference.TtsProvider
import xyz.mdhv.riverwip.inference.byok.ByokConfigStore
import xyz.mdhv.riverwip.inference.byok.ByokProvider
import xyz.mdhv.riverwip.inference.byok.FileKeyValueStore
import xyz.mdhv.riverwip.inference.byok.KeyValueStore
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.Locale

/**
 * The desktop composition root: the same data layer the phone uses, over XDG directories, with
 * "bring your own key" as the only reader-intelligence provider (no on-device runtime yet).
 */
class DesktopServices(private val dirs: XdgDirs) : AppServices {
    private val platform = DesktopDataPlatform(dirs.data, dirs.cache)
    private val data: RiverData = RiverData.create(platform)

    override val sourceRepository = data.sourceRepository
    override val itemRepository = data.itemRepository
    override val readEventRepository = data.readEventRepository
    override val weeklyAggregateRepository = data.weeklyAggregateRepository
    override val articleRepository = data.articleRepository
    override val catalogueRepository = data.catalogueRepository
    override val clippingRepository = data.clippingRepository
    override val dictionaryRepository = data.dictionaryRepository
    override val translationRepository = data.translationRepository
    override val settingsRepository = data.settingsRepository
    override val todayInHistoryRepository = data.todayInHistoryRepository
    override val dataExporter = data.dataExporter
    override val imageStore = data.imageStore
    override val modelCatalogueRepository = ModelCatalogueRepository(platform)

    override val byokConfigStore = ByokConfigStore(PrivateKeyValueStore(FileKeyValueStore(FileSystem.SYSTEM, dirs.config / "byok.json"), dirs.config / "byok.json"))
    private val providers: List<InferenceProvider> = listOf(ByokProvider(byokConfigStore))
    override val inferenceRouter = InferenceRouter(providers)
    override val flashRouter = InferenceRouter(providers)
    override val ttsProvider: TtsProvider = NoNarrator
    override val localModels = false

    override val locale: LocaleController = DesktopLocaleController(dirs.config / "locale")
    override val window: WindowControls = object : WindowControls {
        override fun adjustBrightness(delta: Float) {} // a desktop window has no brightness of its own
        override fun setDarkSurface(dark: Boolean) {}
    }
    override val crashReports: CrashReports = FileCrashReports(dirs.data / "crash_report.txt")

    val ingest = xyz.mdhv.riverwip.data.IngestCycle(itemRepository, weeklyAggregateRepository, articleRepository)
}

/** Nooz Cast narrates with an on-device voice model; there is none on desktop yet, and it says so instead of failing oddly. */
private object NoNarrator : TtsProvider {
    override val id = "none"
    override suspend fun isAvailable() = false
    override suspend fun synthesize(request: SynthesisRequest) =
        SynthesisResult.Failed("Nooz Cast is not available on this computer yet")
}

/** [KeyValueStore] whose file is readable by its owner only: it holds an API key. */
private class PrivateKeyValueStore(private val delegate: KeyValueStore, private val file: Path) : KeyValueStore {
    override fun get(key: String) = delegate.get(key)

    override fun put(entries: Map<String, String>) {
        delegate.put(entries)
        runCatching { Files.setPosixFilePermissions(file.toFile().toPath(), PosixFilePermissions.fromString("rw-------")) }
    }

    override fun clear() = delegate.clear()
}

/** The interface language, remembered in `config/locale`. Changing it rebuilds the UI (see [epoch]). */
class DesktopLocaleController(private val file: Path) : LocaleController {
    private val fs = FileSystem.SYSTEM
    private var tag: String = read()
    override var epoch by mutableIntStateOf(0)
        private set

    init {
        apply(tag)
    }

    override fun current(): String = tag

    override fun set(tag: String) {
        this.tag = tag
        runCatching {
            file.parent?.let { fs.createDirectories(it) }
            fs.write(file) { writeUtf8(tag) }
        }
        apply(tag)
        epoch++
    }

    private fun read(): String = runCatching {
        if (fs.exists(file)) fs.read(file) { readUtf8() }.trim() else LocaleController.SYSTEM_DEFAULT
    }.getOrDefault(LocaleController.SYSTEM_DEFAULT)

    private fun apply(tag: String) {
        // Empty means "follow the system": the JVM was started with the user's locale as default.
        if (tag.isEmpty()) Locale.setDefault(systemLocale) else Locale.setDefault(Locale.forLanguageTag(tag))
    }

    private companion object {
        val systemLocale: Locale = Locale.getDefault()
    }
}

/** A crash report written by the uncaught-exception handler and shown once in Settings. Never transmitted. */
class FileCrashReports(private val file: Path) : CrashReports {
    private val fs = FileSystem.SYSTEM

    override fun pending(): CrashReport.Decoded? = runCatching {
        if (fs.exists(file)) CrashReport.decode(fs.read(file) { readUtf8() }) else null
    }.getOrNull()

    override fun clear() {
        runCatching { fs.delete(file, mustExist = false) }
    }

    fun install(appLabel: String, version: String) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val device = CrashReport.DeviceInfo(
                    appVersionName = version,
                    appVersionCode = null,
                    os = System.getProperty("os.name") + " " + System.getProperty("os.version"),
                    deviceManufacturer = "Java " + System.getProperty("java.version"),
                    deviceModel = System.getProperty("os.arch"),
                )
                val report = CrashReport.of(appLabel, System.currentTimeMillis(), thread.name, throwable, device)
                file.parent?.let { fs.createDirectories(it) }
                fs.write(file) { writeUtf8(report.encode()) }
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
