package xyz.mdhv.riverwip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlin.reflect.KClass
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import xyz.mdhv.riverwip.data.repo.CatalogueModel
import xyz.mdhv.riverwip.data.repo.DataExporter
import xyz.mdhv.riverwip.data.repo.DictionaryRepository
import xyz.mdhv.riverwip.data.repo.ModelCatalogueRepository
import xyz.mdhv.riverwip.data.repo.ModelDownloadState
import xyz.mdhv.riverwip.data.repo.SettingsRepository
import xyz.mdhv.riverwip.data.repo.TranslationRepository
import xyz.mdhv.riverwip.inference.byok.ByokConfig
import xyz.mdhv.riverwip.inference.byok.ByokConfigStore
import xyz.mdhv.riverwip.inference.local.StorageBudget
import xyz.mdhv.riverwip.design.HyleGroteskClassic
import xyz.mdhv.riverwip.design.HyleGroteskPlus
import xyz.mdhv.riverwip.design.HylePrint
import xyz.mdhv.riverwip.design.SectionHeading
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.design.rememberFileDialogs
import xyz.mdhv.riverwip.design.topFadingEdge
import xyz.mdhv.riverwip.model.AppSettings
import xyz.mdhv.riverwip.model.DictionaryOption
import xyz.mdhv.riverwip.model.ImageStyle
import xyz.mdhv.riverwip.model.Locales
import xyz.mdhv.riverwip.model.LocaleCoverage
import xyz.mdhv.riverwip.model.PaperGrain
import xyz.mdhv.riverwip.design.res.*
import xyz.mdhv.riverwip.model.ReadMarkStyle
import xyz.mdhv.riverwip.model.ReadingAsideStyle
import xyz.mdhv.riverwip.model.ReaderFont
import xyz.mdhv.riverwip.model.TextScale
import xyz.mdhv.riverwip.model.ThemeMode
import xyz.mdhv.riverwip.model.TranslationOption

class SettingsViewModel(
    private val repo: SettingsRepository,
    private val dictionaryRepo: DictionaryRepository,
    private val translationRepo: TranslationRepository,
    private val dataExporter: DataExporter,
    private val byokStore: ByokConfigStore,
    private val modelCatalogueRepo: ModelCatalogueRepository,
) : ViewModel() {

    // On-device model download (#18 follow-up): the real ai-catalogue list.
    val modelCatalogue: StateFlow<List<CatalogueModel>> = modelCatalogueRepo.observeCatalogue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())
    val modelCatalogueLastRefreshedAt: StateFlow<Long?> = modelCatalogueRepo.observeLastRefreshedAt()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    var modelDownloadStates: Map<String, ModelDownloadState> by mutableStateOf(emptyMap())
        private set
    var modelCatalogueRefreshing: Boolean by mutableStateOf(false)
        private set
    var modelCatalogueError: String? by mutableStateOf(null)
        private set

    fun downloadableModels(models: List<CatalogueModel>, kind: String = "LLM_GGUF"): List<CatalogueModel> =
        modelCatalogueRepo.downloadable(models, kind)

    /** Cheap file-existence check — called straight from composition, no cached/stale copy to keep in sync. */
    fun isModelDownloaded(model: CatalogueModel): Boolean =
        modelCatalogueRepo.isGroupDownloaded(modelCatalogueRepo.groupMembers(modelCatalogue.value, model))

    fun refreshModelCatalogue() {
        if (modelCatalogueRefreshing) return
        modelCatalogueRefreshing = true
        modelCatalogueError = null
        viewModelScope.launch {
            val result = modelCatalogueRepo.refresh()
            modelCatalogueRefreshing = false
            if (result.isFailure) {
                modelCatalogueError = result.exceptionOrNull()?.message ?: "Couldn't refresh the model list."
            }
        }
    }

    fun downloadModel(model: CatalogueModel) {
        if (modelDownloadStates[model.id] is ModelDownloadState.Downloading) return
        // The whole companion group (e.g. Cast's TTS model + its paired voice),
        // so one tap yields a complete, loadable install — not just the .onnx.
        val members = modelCatalogueRepo.groupMembers(modelCatalogue.value, model)
        val neededBytes = members.filter { !modelCatalogueRepo.isDownloaded(it) }.sumOf { it.sizeBytes }
        val available = modelCatalogueRepo.availableStorageBytes()
        if (!StorageBudget.canDownload(neededBytes, available)) {
            modelDownloadStates = modelDownloadStates + (model.id to ModelDownloadState.Failed("Not enough free storage for this model."))
            return
        }
        modelDownloadStates = modelDownloadStates + (model.id to ModelDownloadState.Downloading(0f))
        viewModelScope.launch {
            val result = modelCatalogueRepo.downloadGroup(members) { progress ->
                modelDownloadStates = modelDownloadStates + (model.id to ModelDownloadState.Downloading(progress))
            }
            modelDownloadStates = modelDownloadStates + (model.id to
                if (result.isSuccess) ModelDownloadState.Ready
                else ModelDownloadState.Failed(result.exceptionOrNull()?.message ?: "Download failed.")
                )
        }
    }

    fun deleteModel(model: CatalogueModel) {
        modelCatalogueRepo.deleteGroup(modelCatalogueRepo.groupMembers(modelCatalogue.value, model))
        modelDownloadStates = modelDownloadStates - model.id
    }

    // BYOK (#18): the user's own OpenAI-compatible endpoint. SharedPreferences
    // isn't reactive, so mirror it into Compose state and refresh on save/clear.
    var byokConfig: ByokConfig by mutableStateOf(byokStore.load())
        private set

    fun saveByok(baseUrl: String, apiKey: String, model: String) {
        val config = ByokConfig(baseUrl, apiKey, model)
        byokStore.save(config)
        byokConfig = byokStore.load()
    }

    fun clearByok() {
        byokStore.clear()
        byokConfig = byokStore.load()
    }
    val settings: StateFlow<AppSettings> =
        repo.observeSettings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AppSettings())

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repo.setThemeMode(mode) }
    fun setFont(font: ReaderFont) = viewModelScope.launch { repo.setReaderFont(font) }
    fun setShowReadingTime(show: Boolean) = viewModelScope.launch { repo.setShowReadingTime(show) }
    fun setTextScale(scale: TextScale) = viewModelScope.launch { repo.setTextScale(scale) }
    fun setHighlightLoadedLanguage(on: Boolean) = viewModelScope.launch { repo.setHighlightLoadedLanguage(on) }
    fun setImmersiveReader(on: Boolean) = viewModelScope.launch { repo.setImmersiveReader(on) }
    fun setTwoFingerBrightness(on: Boolean) = viewModelScope.launch { repo.setTwoFingerBrightness(on) }
    fun setTwoFingerThemeFlick(on: Boolean) = viewModelScope.launch { repo.setTwoFingerThemeFlick(on) }
    fun completeOnboarding() = viewModelScope.launch { repo.setOnboarded(true) }
    fun setNoozFlashEnabled(on: Boolean) = viewModelScope.launch { repo.setNoozFlashEnabled(on) }
    fun setNoozCastEnabled(on: Boolean) = viewModelScope.launch { repo.setNoozCastEnabled(on) }
    fun setTodayInHistoryEnabled(on: Boolean) = viewModelScope.launch { repo.setTodayInHistoryEnabled(on) }
    fun setPaperGrain(grain: PaperGrain) = viewModelScope.launch { repo.setPaperGrain(grain) }
    fun setReadMarkStyle(style: ReadMarkStyle) = viewModelScope.launch { repo.setReadMarkStyle(style) }
    fun setReadingAsideStyle(style: ReadingAsideStyle) = viewModelScope.launch { repo.setReadingAsideStyle(style) }
    fun setUnreadPinchFilter(enabled: Boolean) = viewModelScope.launch { repo.setUnreadPinchFilter(enabled) }
    fun setShowFeedImages(on: Boolean) = viewModelScope.launch { repo.setShowFeedImages(on) }
    fun setHideNsfwImages(on: Boolean) = viewModelScope.launch { repo.setHideNsfwImages(on) }
    fun setImageStyle(style: ImageStyle) = viewModelScope.launch { repo.setImageStyle(style) }
    fun setLensTermEnabled(term: String, enabled: Boolean) = viewModelScope.launch { repo.setLensTermEnabled(term, enabled) }
    fun addLensCustomTerm(term: String) = viewModelScope.launch { repo.addLensCustomTerm(term) }
    fun removeLensCustomTerm(term: String) = viewModelScope.launch { repo.removeLensCustomTerm(term) }

    // Dictionary lens: one-click download of a chosen dictionary (owner's spec).
    val dictionaryOptions: List<DictionaryOption> = dictionaryRepo.options
    val downloadedDictionaryId: StateFlow<String?> =
        dictionaryRepo.observeDownloadedId().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    var downloadingDictionaryId: String? by mutableStateOf(null)
        private set
    var dictionaryError: String? by mutableStateOf(null)
        private set

    fun downloadDictionary(option: DictionaryOption) {
        if (downloadingDictionaryId != null) return
        downloadingDictionaryId = option.id
        dictionaryError = null
        viewModelScope.launch {
            val result = dictionaryRepo.download(option)
            downloadingDictionaryId = null
            if (result.isFailure) {
                dictionaryError = result.exceptionOrNull()?.message ?: "Couldn't download the dictionary."
            }
        }
    }

    // Word-level translation (owner's ask): same one-at-a-time download shape
    // as the dictionary above, over a bilingual pair instead of a definition set.
    val translationOptions: List<TranslationOption> = translationRepo.options
    val installedTranslationId: StateFlow<String?> =
        translationRepo.observeInstalledId().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    var downloadingTranslationId: String? by mutableStateOf(null)
        private set
    var translationError: String? by mutableStateOf(null)
        private set

    fun downloadTranslation(option: TranslationOption) {
        if (downloadingTranslationId != null) return
        downloadingTranslationId = option.id
        translationError = null
        viewModelScope.launch {
            val result = translationRepo.download(option)
            downloadingTranslationId = null
            if (result.isFailure) {
                translationError = result.exceptionOrNull()?.message ?: "Couldn't download that language."
            }
        }
    }

    fun removeTranslation() = viewModelScope.launch { translationRepo.remove() }

    /** Assemble the user's whole profile as JSON, for the export-to-file action (#9). */
    suspend fun exportJson(): String = dataExporter.exportJson(System.currentTimeMillis())

    class Factory(
        private val repo: SettingsRepository,
        private val dictionaryRepo: DictionaryRepository,
        private val translationRepo: TranslationRepository,
        private val dataExporter: DataExporter,
        private val byokStore: ByokConfigStore,
        private val modelCatalogueRepo: ModelCatalogueRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T =
            SettingsViewModel(repo, dictionaryRepo, translationRepo, dataExporter, byokStore, modelCatalogueRepo) as T
    }
}

/**
 * Settings (owner's mock, 2026-07): Theme and Font as rows of circular "T"
 * swatches, plus the Show Reading Time toggle. The mock's fourth row, "Show
 * Progress", was struck through in the owner's own artwork — omitted on
 * purpose, not forgotten.
 */
@OptIn(ExperimentalMaterial3Api::class)
/**
 * The settings surface, in its own page with a back arrow. [compact] is the
 * reader's right room (owner #2: "too many settings in the reader-side
 * space"): only the reading-appearance quick controls, then a "More settings"
 * door to the full page. `compact = false` is the full page, reached either
 * from that door or from Edit's own "Settings" tab (which renders
 * [SettingsBody] directly, without this wrapper — owner: "let the nooz edit
 * show the other settings as well, it needn't be in the settings cog").
 */
@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit,
    compact: Boolean = false,
    onOpenAll: () -> Unit = {},
    onOpenLensWordList: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (compact) "READING" else "SETTINGS", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        SettingsBody(
            vm = vm,
            compact = compact,
            onOpenAll = onOpenAll,
            onOpenLensWordList = onOpenLensWordList,
            modifier = Modifier.padding(padding),
        )
    }
}

/**
 * The settings content on its own, without any surrounding chrome (no
 * Scaffold, no back arrow) — reused by [SettingsScreen]'s own page and by
 * Edit's inline "Settings" tab, which supplies its own header and DONE button
 * instead.
 */
@Composable
fun SettingsBody(
    vm: SettingsViewModel,
    compact: Boolean,
    onOpenAll: () -> Unit = {},
    onOpenLensWordList: () -> Unit = {},
    // Off when Edit's own "About" tab already covers it (owner: About needs
    // to be its own tab), so it isn't shown twice in that context; the
    // standalone Settings page (reached via "More settings") has no tabs to
    // put a separate one in, so it keeps the default of showing it inline.
    showAbout: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val downloadedDictId by vm.downloadedDictionaryId.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .topFadingEdge(scrollState.canScrollBackward)
            .verticalScroll(scrollState)
            .padding(horizontal = Tokens.Spacing.md)
            .padding(top = Tokens.Spacing.xs, bottom = Tokens.Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
    ) {
            if (!compact) CrashSection()

            WhatsInsideSection()
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LanguageSection()
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SectionHeading(stringResource(Res.string.settings_theme))
            Row(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
            ) {
                // Auto comes first: it is the default, and it is the answer for
                // a reader whose phone is already in dark mode (D34).
                SwatchCircle(
                    label = stringResource(Res.string.settings_theme_follow),
                    selected = settings.themeMode == ThemeMode.SYSTEM,
                    background = Tokens.Palette.paperField,
                    backgroundBrush = Brush.linearGradient(
                        // A hard stop, not a blend: this swatch is two tints,
                        // not a gradient the app could ever actually paint.
                        0.0f to Tokens.Palette.paperField,
                        0.5f to Tokens.Palette.paperField,
                        0.5f to Color(0xFF262624),
                        1.0f to Color(0xFF262624),
                    ),
                    glyph = null,
                    letterColor = Tokens.Palette.paperInk,
                    onClick = { vm.setTheme(ThemeMode.SYSTEM) },
                )
                SwatchCircle(
                    label = stringResource(Res.string.settings_theme_white),
                    selected = settings.themeMode == ThemeMode.WHITE,
                    background = Color(0xFFFFFFFF),
                    letterColor = Tokens.Palette.paperInk,
                    onClick = { vm.setTheme(ThemeMode.WHITE) },
                )
                SwatchCircle(
                    label = stringResource(Res.string.settings_theme_paper),
                    selected = settings.themeMode == ThemeMode.PAPER,
                    background = Tokens.Palette.paperField,
                    letterColor = Tokens.Palette.paperInk,
                    onClick = { vm.setTheme(ThemeMode.PAPER) },
                )
                SwatchCircle(
                    label = stringResource(Res.string.settings_theme_dark),
                    selected = settings.themeMode == ThemeMode.DARK,
                    background = Color(0xFF262624),
                    letterColor = Color(0xFFECEAE6),
                    onClick = { vm.setTheme(ThemeMode.DARK) },
                )
            }
            // Says out loud what the split swatch means, and what picking a
            // tint by hand costs: it stops following the phone.
            Text(
                when (settings.themeMode) {
                    ThemeMode.SYSTEM -> "Following your phone — Paper by day, Dark at night."
                    ThemeMode.WHITE -> "White, whatever your phone is set to."
                    ThemeMode.PAPER -> "Paper, whatever your phone is set to."
                    ThemeMode.DARK -> "Dark, whatever your phone is set to."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SectionHeading(stringResource(Res.string.settings_font))
            // The owner's Settings mock: a list of the font names, each set in
            // its own face, a check on the chosen one (not colour swatches).
            Column(modifier = Modifier.selectableGroup()) {
                FontRow(
                    name = "Hyle Grotesk Classic",
                    family = HyleGroteskClassic,
                    selected = settings.readerFont == ReaderFont.GROTESK_CLASSIC,
                    onClick = { vm.setFont(ReaderFont.GROTESK_CLASSIC) },
                )
                FontRow(
                    name = "Hyle Grotesk Plus",
                    family = HyleGroteskPlus,
                    selected = settings.readerFont == ReaderFont.GROTESK_PLUS,
                    onClick = { vm.setFont(ReaderFont.GROTESK_PLUS) },
                )
                FontRow(
                    name = "Hyle Print",
                    family = HylePrint,
                    selected = settings.readerFont == ReaderFont.PRINT,
                    onClick = { vm.setFont(ReaderFont.PRINT) },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SectionHeading(stringResource(Res.string.settings_text_size))
            Row(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
            ) {
                for (scale in TextScale.entries) {
                    val chosen = settings.textScale == scale
                    Text(
                        scale.label,
                        style = MaterialTheme.typography.titleMedium.copy(
                            // Non-colour channel: the chosen size is bold, not just darker.
                            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                        ),
                        color = if (chosen) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .selectable(
                                selected = chosen,
                                role = Role.RadioButton,
                                onClick = { vm.setTextScale(scale) },
                            )
                            .minimumInteractiveComponentSize()
                            .padding(vertical = Tokens.Spacing.xxs),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SectionHeading(stringResource(Res.string.settings_paper_grain))
            Row(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
            ) {
                for (grain in PaperGrain.entries) {
                    val chosen = settings.paperGrain == grain
                    Text(
                        grain.label,
                        style = MaterialTheme.typography.titleMedium.copy(
                            // Non-colour channel: the chosen grain is bold, not just darker.
                            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                        ),
                        color = if (chosen) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .selectable(
                                selected = chosen,
                                role = Role.RadioButton,
                                onClick = { vm.setPaperGrain(grain) },
                            )
                            .minimumInteractiveComponentSize()
                            .padding(vertical = Tokens.Spacing.xxs),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SectionHeading(stringResource(Res.string.settings_mark_read_as))
            Row(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
            ) {
                for (style in ReadMarkStyle.entries) {
                    val chosen = settings.readMarkStyle == style
                    Text(
                        style.label,
                        style = MaterialTheme.typography.titleMedium.copy(
                            // Non-colour channel: the chosen style is bold, not just darker.
                            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                            textDecoration = if (style == ReadMarkStyle.STRIKETHROUGH) TextDecoration.LineThrough else null,
                        ),
                        color = if (chosen) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .selectable(
                                selected = chosen,
                                role = Role.RadioButton,
                                onClick = { vm.setReadMarkStyle(style) },
                            )
                            .minimumInteractiveComponentSize()
                            .padding(vertical = Tokens.Spacing.xxs),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SectionHeading(stringResource(Res.string.settings_while_you_read))
            Text(
                stringResource(Res.string.settings_while_you_read_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
            ) {
                for (style in ReadingAsideStyle.entries) {
                    val chosen = settings.readingAsideStyle == style
                    Text(
                        style.label,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                        ),
                        color = if (chosen) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .selectable(
                                selected = chosen,
                                role = Role.RadioButton,
                                onClick = { vm.setReadingAsideStyle(style) },
                            )
                            .minimumInteractiveComponentSize()
                            .padding(vertical = Tokens.Spacing.xxs),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            SectionHeading(stringResource(Res.string.settings_today_in_history))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = settings.todayInHistoryEnabled,
                        onValueChange = { vm.setTodayInHistoryEnabled(it) },
                        role = Role.Switch,
                    )
                    .padding(vertical = Tokens.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.settings_show_the_column),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    // Says where it fetches from, because this is the only
                    // request Nooz makes to somewhere the reader didn't add.
                    Text(
                        stringResource(Res.string.settings_today_in_history_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.todayInHistoryEnabled, onCheckedChange = null)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = settings.showReadingTime,
                        onValueChange = { vm.setShowReadingTime(it) },
                        role = Role.Switch,
                    )
                    .padding(vertical = Tokens.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.settings_show_reading_time),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = settings.showReadingTime, onCheckedChange = null)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = settings.highlightLoadedLanguage,
                        onValueChange = { vm.setHighlightLoadedLanguage(it) },
                        role = Role.Switch,
                    )
                    .padding(vertical = Tokens.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.settings_highlight_loaded),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        stringResource(Res.string.settings_highlight_loaded_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.highlightLoadedLanguage, onCheckedChange = null)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = settings.immersiveReader,
                        onValueChange = { vm.setImmersiveReader(it) },
                        role = Role.Switch,
                    )
                    .padding(vertical = Tokens.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.settings_immersive),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        stringResource(Res.string.settings_immersive_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.immersiveReader, onCheckedChange = null)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Everything below is the *full* settings — heavier, not reading-in-
            // the-moment stuff. In the reader's compact room it collapses to a
            // single door (owner #2).
            if (compact) {
                Text(
                    stringResource(Res.string.settings_more),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = stringResource(Res.string.settings_open_all)) { onOpenAll() }
                        .padding(vertical = Tokens.Spacing.sm),
                )
                Text(
                    stringResource(Res.string.settings_more_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                SectionHeading(stringResource(Res.string.settings_dictionary))
                Text(
                    stringResource(Res.string.settings_dictionary_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (option in vm.dictionaryOptions) {
                    DictionaryRow(
                        option = option,
                        downloaded = downloadedDictId == option.id,
                        downloading = vm.downloadingDictionaryId == option.id,
                        onDownload = { vm.downloadDictionary(option) },
                    )
                }
                vm.dictionaryError?.let { err ->
                    Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                TranslationSection(vm)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                IntelligenceSection(settings, vm)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                GesturesSection(settings, vm)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                ImagesSection(settings, vm)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                AdvancedSection(onOpenLensWordList = onOpenLensWordList)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                YourDataSection(vm)

                if (showAbout) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AboutSection()
                }
            }
        }
}

/**
 * A pending device-only crash report (owner's #17, Hyle crash-recovery). Shown
 * only when the app crashed last run — headline first, the full trace behind a
 * toggle, copy-to-clipboard, and dismiss. Nothing is ever transmitted.
 */
@Composable
private fun CrashSection() {
    val crashReports = LocalAppServices.current.crashReports
    val clipboard = LocalClipboardManager.current
    var report by remember { mutableStateOf(crashReports.pending()) }
    var showTrace by remember { mutableStateOf(false) }
    val current = report ?: return

    Column(
        Modifier
            .fillMaxWidth()
            .border(Tokens.Border.thin, MaterialTheme.colorScheme.error, RoundedCornerShape(Tokens.Radius.sm))
            .padding(Tokens.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xs),
    ) {
        Text(stringResource(Res.string.settings_crash_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(current.headline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        Text(
            stringResource(Res.string.settings_crash_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showTrace) {
            Text(
                current.fullReport,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.md)) {
            TextButton(onClick = { showTrace = !showTrace }, contentPadding = PaddingValues(0.dp)) {
                Text(if (showTrace) "Hide details" else "Show details")
            }
            TextButton(
                onClick = { clipboard.setText(AnnotatedString(current.fullReport)) },
                contentPadding = PaddingValues(0.dp),
            ) { Text(stringResource(Res.string.settings_copy)) }
            TextButton(
                onClick = { crashReports.clear(); report = null },
                contentPadding = PaddingValues(0.dp),
            ) { Text(stringResource(Res.string.settings_dismiss)) }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/**
 * Reader intelligence (owner's #18). The lens defuse is on-device-first and
 * honest about its gaps; this is where a reader can bring their own key — an
 * OpenAI-compatible endpoint they control — for cloud rewrites, always marked
 * cloud. On-device model execution isn't wired in this build yet, and that's
 * stated plainly rather than dressed up.
 */
@Composable
private fun IntelligenceSection(settings: AppSettings, vm: SettingsViewModel) {
    val config = vm.byokConfig
    var modelPath by remember(config.isComplete) {
        mutableStateOf(if (config.isComplete) ModelPath.BYOK else ModelPath.ON_DEVICE)
    }

    // No expand/collapse here (owner: "it feels like I am already in the
    // reader intelligence setting so there's nothing to expand") — Flash and
    // Cast's own toggles were already unconditionally visible below this
    // header regardless of a since-removed `expanded` flag, so the fold only
    // ever hid the shared model panel beneath them; that partial hide was
    // the confusing part, not a real need to save space on this one tab.
    Column(Modifier.fillMaxWidth().padding(vertical = Tokens.Spacing.xs)) {
        SectionHeading(stringResource(Res.string.settings_reader_intelligence))
        if (config.isComplete) {
            Text(
                stringResource(Res.string.settings_byok_model, config.model),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = settings.noozFlashEnabled,
                onValueChange = { vm.setNoozFlashEnabled(it) },
                role = Role.Switch,
            )
            .padding(vertical = Tokens.Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Nooz Flash",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                stringResource(Res.string.settings_flash_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = settings.noozFlashEnabled, onCheckedChange = null)
    }
    val localModels = LocalAppServices.current.localModels
    // Cast narrates with an on-device voice model; where no model can run there is nothing to switch on.
    if (localModels) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = settings.noozCastEnabled,
                    onValueChange = { vm.setNoozCastEnabled(it) },
                    role = Role.Switch,
                )
                .padding(vertical = Tokens.Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Nooz Cast",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    stringResource(Res.string.settings_cast_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = settings.noozCastEnabled, onCheckedChange = null)
        }
    }
    val catalogue by vm.modelCatalogue.collectAsStateWithLifecycle()
    ModelChoicePanel(
        path = modelPath,
        onPathChange = { modelPath = it },
        byokConfig = config,
        onSaveByok = { url, key, model -> vm.saveByok(url, key, model) },
        onClearByok = { vm.clearByok() },
        localModels = localModels,
        download = ModelDownloadUi(
            models = vm.downloadableModels(catalogue),
            downloadStates = vm.modelDownloadStates,
            isDownloaded = { vm.isModelDownloaded(it) },
            onDownload = { vm.downloadModel(it) },
            onDelete = { vm.deleteModel(it) },
            onRefresh = { vm.refreshModelCatalogue() },
            refreshing = vm.modelCatalogueRefreshing,
            error = vm.modelCatalogueError,
        ),
    )
    if (localModels) {
        // Cast has no on-device/BYOK trichotomy — a private anchor voice never
        // leaves the device — so its own model gets only the download list,
        // scoped to its own kind rather than Flash's LLM_GGUF default.
        SectionHeading(stringResource(Res.string.settings_cast_model), modifier = Modifier.padding(top = Tokens.Spacing.md))
        ModelDownloadList(
            ModelDownloadUi(
                models = vm.downloadableModels(catalogue, kind = "TTS_ONNX"),
                downloadStates = vm.modelDownloadStates,
                isDownloaded = { vm.isModelDownloaded(it) },
                onDownload = { vm.downloadModel(it) },
                onDelete = { vm.deleteModel(it) },
                onRefresh = { vm.refreshModelCatalogue() },
                refreshing = vm.modelCatalogueRefreshing,
                error = vm.modelCatalogueError,
            ),
        )
    }
}

/**
 * The same "what's inside" tour onboarding ends on, kept permanently here
 * (D36). Onboarding runs exactly once and has no replay, so without this every
 * reader who installed before the tour existed — or who tapped Skip — would
 * never learn that the Loom opens by pulling down on the stand, or that Flash
 * and Cast exist at all. Collapsed by default: it is a reminder, not a wall,
 * and the settings a returning reader actually came for should stay near the
 * top.
 */
@Composable
private fun WhatsInsideSection() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = Tokens.Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            SectionHeading(stringResource(Res.string.tour_title))
            Text(
                stringResource(Res.string.settings_whats_inside_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "Collapse what's inside" else "Expand what's inside",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (expanded) {
        FeatureTourContent(modifier = Modifier.padding(bottom = Tokens.Spacing.xs))
    }
}

/**
 * The interface language.
 *
 * Every entry is labelled with the language's **own name for itself**. A picker
 * written entirely in English is a picker for people who already have English,
 * which is exactly the reader this list exists for.
 *
 * Each partial locale says how partial it is, from a count measured off the
 * catalogues at build time rather than asserted here. Someone switching to a
 * language that is a third done deserves to know that before the screen changes
 * under them, not after.
 *
 * Only locales with something translated are offered. `Locales.ALL` is the list
 * Nooz intends to support; `LocaleCoverage.SHIPPED` is what it actually has.
 * Offering the difference would hand a reader their own language and then an
 * English app.
 */
@Composable
private fun LanguageSection() {
    val localeController = LocalAppServices.current.locale
    var expanded by rememberSaveable { mutableStateOf(false) }
    var chosen by remember { mutableStateOf(localeController.current()) }

    val offered = remember {
        Locales.ALL.filter { it.tag in LocaleCoverage.SHIPPED }
    }
    val currentName = offered.firstOrNull { it.tag == chosen }?.endonym
        ?: stringResource(Res.string.language_system_default)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = Tokens.Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            SectionHeading(stringResource(Res.string.language_title))
            Text(
                currentName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (!expanded) return

    Text(
        stringResource(Res.string.language_explainer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = Tokens.Spacing.xs),
    )

    fun choose(tag: String) {
        chosen = tag
        localeController.set(tag)
    }

    Column(modifier = Modifier.selectableGroup()) {
        LanguageRow(
            label = stringResource(Res.string.language_system_default),
            selected = chosen == LocaleController.SYSTEM_DEFAULT,
            onClick = { choose(LocaleController.SYSTEM_DEFAULT) },
        )
        for (locale in offered) {
            val percent = LocaleCoverage.percentFor(locale.tag)
            LanguageRow(
                label = if (percent >= 100) {
                    stringResource(Res.string.language_complete, locale.endonym)
                } else {
                    stringResource(Res.string.language_partial, locale.endonym, percent)
                },
                selected = chosen == locale.tag,
                onClick = { choose(locale.tag) },
            )
        }
    }
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // selectable() rather than clickable(): it publishes the row's
            // selected state to TalkBack, which a plain click target does not.
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Tokens.Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/**
 * Configurable reader gestures (owner's #11). Collapsed by default — a
 * "Gestures" disclosure that opens the second-level toggles, since the defaults
 * are fine and most readers never need them.
 */
@Composable
private fun GesturesSection(settings: AppSettings, vm: SettingsViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = Tokens.Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            SectionHeading(stringResource(Res.string.settings_gestures))
            Text(
                stringResource(Res.string.settings_gestures_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "Collapse gestures" else "Expand gestures",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (expanded) {
        SettingSwitchRow(
            title = stringResource(Res.string.settings_gesture_brightness),
            subtitle = "Slide two fingers up or down to dim or brighten the page.",
            checked = settings.twoFingerBrightness,
            onCheckedChange = { vm.setTwoFingerBrightness(it) },
        )
        SettingSwitchRow(
            title = stringResource(Res.string.settings_gesture_theme),
            subtitle = "Flick two fingers sideways to step the paper tint.",
            checked = settings.twoFingerThemeFlick,
            onCheckedChange = { vm.setTwoFingerThemeFlick(it) },
        )
        SettingSwitchRow(
            title = stringResource(Res.string.settings_gesture_unread),
            subtitle = "On the Stand's list: pinch in to show only unread, pinch out to show everything again.",
            checked = settings.unreadPinchFilter,
            onCheckedChange = { vm.setUnreadPinchFilter(it) },
        )
    }
}

/**
 * Feed images (owner's ask, 2026-07): on/off, a style choice — colour, a
 * tasteful black & white, or a halftone dot-print stylization (see
 * `FeedImage.kt` in `:feature:reader`) — and hiding images the *source's own
 * feed* declared adult/explicit (never this app's own judgment; see
 * [AppSettings.hideNsfwImages]'s own doc for exactly what that checks).
 * Visible by default, not buried — a mainstream visual preference, unlike
 * the power-reader tools in [AdvancedSection] below.
 */
@Composable
private fun ImagesSection(settings: AppSettings, vm: SettingsViewModel) {
    SectionHeading(stringResource(Res.string.settings_images))
    SettingSwitchRow(
        title = stringResource(Res.string.settings_show_feed_images),
        subtitle = "Article thumbnails and hero images, wherever a source's feed supplies one.",
        checked = settings.showFeedImages,
        onCheckedChange = { vm.setShowFeedImages(it) },
    )
    if (settings.showFeedImages) {
        Row(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
        ) {
            for (style in ImageStyle.entries) {
                val chosen = settings.imageStyle == style
                Text(
                    style.label,
                    style = MaterialTheme.typography.titleMedium.copy(
                        // Non-colour channel: the chosen style is bold, not just darker.
                        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = if (chosen) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .selectable(
                            selected = chosen,
                            role = Role.RadioButton,
                            onClick = { vm.setImageStyle(style) },
                        )
                        .minimumInteractiveComponentSize()
                        .padding(vertical = Tokens.Spacing.xxs),
                )
            }
        }
        SettingSwitchRow(
            title = stringResource(Res.string.settings_hide_flagged_images),
            subtitle = "Hides an item's image only when its own feed declared it adult/explicit; never this app's own judgment, and never touches a source that declares nothing.",
            checked = settings.hideNsfwImages,
            onCheckedChange = { vm.setHideNsfwImages(it) },
        )
    }
}

/**
 * Buried on purpose (owner's ask): the reading lens's default/custom word
 * list, and feedback — power-reader and one-off tools most readers never
 * need to open, unlike [ImagesSection] above. Collapsed by default, same
 * disclosure shape as [GesturesSection].
 */
@Composable
private fun AdvancedSection(onOpenLensWordList: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = Tokens.Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            SectionHeading(stringResource(Res.string.settings_advanced))
            Text(
                stringResource(Res.string.settings_advanced_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "Collapse advanced" else "Expand advanced",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (expanded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = stringResource(Res.string.settings_open_word_list), onClick = onOpenLensWordList)
                .padding(vertical = Tokens.Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.settings_word_list),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    stringResource(Res.string.settings_word_list_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        FeedbackRow()
    }
}

/** No dev contact existed anywhere in this app before — this is it, a plain mailto. */
private const val FEEDBACK_EMAIL = "nooz@asystemofcells.com"

/**
 * A clickable row that opens a mailto: to [FEEDBACK_EMAIL] with the given
 * subject. [LocalUriHandler]'s default Android implementation rethrows a
 * missing-mail-app failure as [IllegalArgumentException] (wrapping the
 * underlying `ActivityNotFoundException`) rather than silently no-op'ing —
 * caught here so a device with no mail client configured gets an honest
 * fallback (the address copied to the clipboard) instead of a crash.
 *
 * Shared by [AboutSection]'s stringResource(Res.string.settings_contact) (Play's News & Magazines policy
 * requires a clearly labeled, easy-to-find contact section — the app's one
 * contact address needs to live where that reads, not only buried in
 * Advanced settings) and Advanced settings' own "Send feedback" row.
 */
@Composable
private fun ContactRow(label: String, subtitle: String, emailSubject: String) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    var fallbackMessage by remember { mutableStateOf<String?>(null) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(Res.string.settings_contact_email)) {
                try {
                    uriHandler.openUri("mailto:$FEEDBACK_EMAIL?subject=" + java.net.URLEncoder.encode(emailSubject, "UTF-8").replace("+", "%20"))
                } catch (_: Exception) {
                    clipboard.setText(AnnotatedString(FEEDBACK_EMAIL))
                    fallbackMessage = "No email app found; copied $FEEDBACK_EMAIL to your clipboard instead."
                }
            }
            .padding(vertical = Tokens.Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    fallbackMessage?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FeedbackRow() {
    ContactRow("Send feedback", "Opens an email to $FEEDBACK_EMAIL.", "Nooz feedback")
}

@Composable
private fun SettingSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
            .padding(vertical = Tokens.Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * The user's data is the user's (owner's #9 + brief §4). One tap writes the
 * whole local profile — settings, filter, sources, clippings, and the coarse
 * read log — to a JSON file the user picks. Local only; no keys, no upload.
 */
@Composable
private fun YourDataSection(vm: SettingsViewModel) {
    val scope = rememberCoroutineScope()
    val files = rememberFileDialogs()
    var exporting by remember { mutableStateOf(false) }

    SectionHeading(stringResource(Res.string.settings_your_data))
    Text(
        stringResource(Res.string.settings_your_data_body),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(
        onClick = {
            exporting = true
            scope.launch {
                files.saveText("nooz-data.json", "application/json", vm.exportJson())
                exporting = false
            }
        },
        enabled = !exporting,
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(if (exporting) "Exporting…" else "Export everything")
    }
}

/**
 * About (owner's #16): Nooz is one of the mdhv.xyz apps. Point curious readers
 * at the studio and its siblings — quietly, no tracking, just links.
 */
@Composable
internal fun AboutSection() {
    val uriHandler = LocalUriHandler.current
    SectionHeading(stringResource(Res.string.settings_about))
    Text(
        stringResource(Res.string.settings_about_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    TextButton(onClick = { uriHandler.openUri("https://mdhv.xyz") }, contentPadding = PaddingValues(0.dp)) {
        Text(stringResource(Res.string.settings_visit_studio))
    }
    TextButton(
        onClick = { uriHandler.openUri("https://github.com/mbaliga/nooz") },
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(stringResource(Res.string.settings_view_source))
    }

    // Google Play's News & Magazines policy requires a clearly labeled,
    // easy-to-find in-app contact section -- this needs to be right here on
    // the About tab, not several taps deep in Advanced settings.
    SectionHeading(stringResource(Res.string.settings_contact))
    ContactRow(FEEDBACK_EMAIL, "Questions, feedback, or a correction: email us directly.", "Nooz")

    Text(
        stringResource(Res.string.settings_more_from_studio),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    for (app in SISTER_APPS) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { uriHandler.openUri(app.url) }
                .padding(vertical = Tokens.Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(app.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(app.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * [AboutSection] on its own scrollable page — Edit's dedicated "About" tab
 * (owner: it needs to be a separate tab, not buried at the bottom of the
 * Reader tab's long scroll). [AboutSection] itself stays a bare fragment, no
 * scroll or padding of its own, since [SettingsBody] still embeds it inline
 * too when [SettingsBody]'s `showAbout` is true — the standalone Settings
 * page reached via "More settings" has no tabs to put a separate one in.
 */
@Composable
internal fun AboutTab(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.md),
    ) {
        AboutSection()
    }
}

private data class SisterApp(val name: String, val blurb: String, val url: String)

// The mdhv.xyz family the owner named (#16). Blurbs are deliberately spare;
// links point at the studio's own pages, not third-party stores.
private val SISTER_APPS = listOf(
    SisterApp("Animalcules", "A microcosm you keep.", "https://mdhv.xyz/animalcules"),
    SisterApp("Clackpad", "A keyboard that feels like one.", "https://mdhv.xyz/clackpad"),
    SisterApp("FoneBru", "Your phone, brewed down.", "https://mdhv.xyz/fonebru"),
)

@Composable
private fun DictionaryRow(
    option: xyz.mdhv.riverwip.model.DictionaryOption,
    downloaded: Boolean,
    downloading: Boolean,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(option.name, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(Res.string.settings_size_license, option.sizeHuman, option.license),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Live region: the download status change is spoken, not silent.
        // Resolved before `semantics { }`, which is not a composable scope.
        val downloadingLabel = stringResource(Res.string.settings_downloading_dictionary)
        Box(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, contentAlignment = Alignment.Center) {
            when {
                downloading -> androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(22.dp).semantics { contentDescription = downloadingLabel },
                    strokeWidth = 2.dp,
                )
                downloaded -> Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(Res.string.settings_downloaded),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
                else -> androidx.compose.material3.TextButton(onClick = onDownload) {
                    Text(stringResource(Res.string.settings_download))
                }
            }
        }
    }
}

/**
 * Word-level translation (owner's ask, 2026-08): reading in a second or third
 * language, long-press a word and see it in your own — the way Kindle does.
 *
 * Collapsed by default and deliberately not a language *setting* but a
 * *download*: these are 1–26 MB bilingual dictionaries, one installed at a
 * time, and a reader should choose to spend that rather than discover it spent.
 * Everything after the download is offline; a lookup never leaves the device.
 */
@Composable
private fun TranslationSection(vm: SettingsViewModel) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val installedId by vm.installedTranslationId.collectAsStateWithLifecycle()
    val installed = vm.translationOptions.firstOrNull { it.id == installedId }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = Tokens.Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            SectionHeading(stringResource(Res.string.settings_translation))
            Text(
                installed?.let { "${it.label} — long-press a word as you read." }
                    ?: "Long-press a word and see it in another language.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "Collapse translation" else "Expand translation",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (expanded) {
        Text(
            stringResource(Res.string.settings_translation_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (installed != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(installed.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.removeTranslation() }) { Text(stringResource(Res.string.settings_remove)) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        vm.translationError?.let { err ->
            Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        for (option in vm.translationOptions) {
            TranslationRow(
                option = option,
                installed = installedId == option.id,
                downloading = vm.downloadingTranslationId == option.id,
                onDownload = { vm.downloadTranslation(option) },
            )
        }
        // Recorded in the open rather than left to be discovered: the source
        // publishes 650 pairs and none of them is an Indian language, which is
        // a real gap for a catalogue that just gained feeds in eleven of them.
        Text(
            stringResource(Res.string.settings_no_indic_pair),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Tokens.Spacing.xs),
        )
    }
}

/** One downloadable language pair. Mirrors [DictionaryRow]'s shape. */
@Composable
private fun TranslationRow(
    option: TranslationOption,
    installed: Boolean,
    downloading: Boolean,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(option.label, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(Res.string.settings_size_license, option.approxSizeHuman, option.license),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val downloadingLabel = stringResource(Res.string.settings_downloading_named, option.label)
        Box(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, contentAlignment = Alignment.Center) {
            when {
                downloading -> androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(22.dp).semantics { contentDescription = downloadingLabel },
                    strokeWidth = 2.dp,
                )
                installed -> Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(Res.string.settings_installed),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
                else -> TextButton(onClick = onDownload) { Text(stringResource(Res.string.settings_download)) }
            }
        }
    }
}

/** One circular "T" swatch (the mock's theme selector). Selection = ring + a check badge, never colour alone. */
@Composable
private fun SwatchCircle(
    label: String,
    selected: Boolean,
    background: Color,
    letterColor: Color,
    onClick: () -> Unit,
    fontFamily: FontFamily = HyleGroteskClassic,
    /** Set instead of [background] to paint the swatch as more than one tint. */
    backgroundBrush: Brush? = null,
    /** The specimen letter. Null for the follow-the-phone swatch, whose two tints are the specimen. */
    glyph: String? = "T",
) {
    val ring = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outlineVariant
    Box(modifier = Modifier.size(62.dp)) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .align(Alignment.Center)
                .clip(CircleShape)
                .then(
                    if (backgroundBrush != null) Modifier.background(backgroundBrush)
                    else Modifier.background(background),
                )
                .border(if (selected) Tokens.Border.thick else Tokens.Border.thin, ring, CircleShape)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            if (glyph != null) {
                Text(
                    glyph,
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = fontFamily),
                    color = letterColor,
                )
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** One reading-font choice: the family's own name, set in that family, checked when chosen. */
@Composable
private fun FontRow(
    name: String,
    family: FontFamily,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Tokens.Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = family),
            color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
