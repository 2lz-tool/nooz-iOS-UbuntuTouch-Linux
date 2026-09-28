package xyz.mdhv.riverwip.data.repo

import xyz.mdhv.riverwip.data.DataPlatform
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.mdhv.riverwip.model.AppSettings
import xyz.mdhv.riverwip.model.ImageStyle
import xyz.mdhv.riverwip.model.PaperGrain
import xyz.mdhv.riverwip.model.ReadMarkStyle
import xyz.mdhv.riverwip.model.ReaderFilter
import xyz.mdhv.riverwip.model.ReaderFont
import xyz.mdhv.riverwip.model.ReadingAsideStyle
import xyz.mdhv.riverwip.model.Region
import xyz.mdhv.riverwip.model.TextScale
import xyz.mdhv.riverwip.model.ThemeMode

/**
 * Display preferences (theme tint / reader font / reading time / text size) and
 * the standing reader filter (region + topics from the globe). Local DataStore
 * only — never synced or transmitted. Defaults live on [AppSettings]/[ReaderFilter].
 */
class SettingsRepository(private val platform: DataPlatform) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val FONT = stringPreferencesKey("reader_font")
        val READING_TIME = booleanPreferencesKey("show_reading_time")
        val TEXT_SCALE = stringPreferencesKey("text_scale")
        val LENS_HIGHLIGHT = booleanPreferencesKey("highlight_loaded_language")
        val IMMERSIVE = booleanPreferencesKey("immersive_reader")
        val GESTURE_BRIGHTNESS = booleanPreferencesKey("gesture_brightness")
        val GESTURE_THEME_FLICK = booleanPreferencesKey("gesture_theme_flick")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val REGION = stringPreferencesKey("filter_region")
        val TOPICS = stringSetPreferencesKey("filter_topics")
        val NOOZ_FLASH = booleanPreferencesKey("nooz_flash_enabled")
        val NOOZ_CAST = booleanPreferencesKey("nooz_cast_enabled")
        val TODAY_IN_HISTORY = booleanPreferencesKey("today_in_history_enabled")
        val PAPER_GRAIN = stringPreferencesKey("paper_grain")
        val READ_MARK_STYLE = stringPreferencesKey("read_mark_style")
        val UNREAD_PINCH_FILTER = booleanPreferencesKey("unread_pinch_filter")
        val SHOW_FEED_IMAGES = booleanPreferencesKey("show_feed_images")
        val HIDE_NSFW_IMAGES = booleanPreferencesKey("hide_nsfw_images")
        val IMAGE_STYLE = stringPreferencesKey("image_style")
        val LENS_DISABLED_DEFAULT_TERMS = stringSetPreferencesKey("lens_disabled_default_terms")
        val LENS_CUSTOM_TERMS = stringSetPreferencesKey("lens_custom_terms")
        val READING_ASIDE_STYLE = stringPreferencesKey("reading_aside_style")
    }

    fun observeSettings(): Flow<AppSettings> = platform.stores.settings.data.map { prefs ->
        AppSettings(
            themeMode = ThemeMode.fromKey(prefs[Keys.THEME]),
            readerFont = ReaderFont.fromKey(prefs[Keys.FONT]),
            showReadingTime = prefs[Keys.READING_TIME] ?: true,
            textScale = TextScale.fromKey(prefs[Keys.TEXT_SCALE]),
            highlightLoadedLanguage = prefs[Keys.LENS_HIGHLIGHT] ?: false,
            immersiveReader = prefs[Keys.IMMERSIVE] ?: false,
            twoFingerBrightness = prefs[Keys.GESTURE_BRIGHTNESS] ?: true,
            twoFingerThemeFlick = prefs[Keys.GESTURE_THEME_FLICK] ?: true,
            onboarded = prefs[Keys.ONBOARDED] ?: false,
            noozFlashEnabled = prefs[Keys.NOOZ_FLASH] ?: false,
            noozCastEnabled = prefs[Keys.NOOZ_CAST] ?: false,
            todayInHistoryEnabled = prefs[Keys.TODAY_IN_HISTORY] ?: false,
            paperGrain = PaperGrain.fromKey(prefs[Keys.PAPER_GRAIN]),
            readMarkStyle = ReadMarkStyle.fromKey(prefs[Keys.READ_MARK_STYLE]),
            unreadPinchFilter = prefs[Keys.UNREAD_PINCH_FILTER] ?: true,
            showFeedImages = prefs[Keys.SHOW_FEED_IMAGES] ?: true,
            hideNsfwImages = prefs[Keys.HIDE_NSFW_IMAGES] ?: false,
            imageStyle = ImageStyle.fromKey(prefs[Keys.IMAGE_STYLE]),
            lensDisabledDefaultTerms = prefs[Keys.LENS_DISABLED_DEFAULT_TERMS] ?: emptySet(),
            lensCustomTerms = prefs[Keys.LENS_CUSTOM_TERMS] ?: emptySet(),
            readingAsideStyle = ReadingAsideStyle.fromKey(prefs[Keys.READING_ASIDE_STYLE]),
        )
    }

    fun observeFilter(): Flow<ReaderFilter> = platform.stores.settings.data.map { prefs ->
        ReaderFilter(
            region = Region.fromKey(prefs[Keys.REGION]),
            topicKeys = prefs[Keys.TOPICS] ?: emptySet(),
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        platform.stores.settings.edit { it[Keys.THEME] = mode.key }
    }

    suspend fun setReaderFont(font: ReaderFont) {
        platform.stores.settings.edit { it[Keys.FONT] = font.key }
    }

    suspend fun setShowReadingTime(show: Boolean) {
        platform.stores.settings.edit { it[Keys.READING_TIME] = show }
    }

    suspend fun setTextScale(scale: TextScale) {
        platform.stores.settings.edit { it[Keys.TEXT_SCALE] = scale.key }
    }

    suspend fun setHighlightLoadedLanguage(enabled: Boolean) {
        platform.stores.settings.edit { it[Keys.LENS_HIGHLIGHT] = enabled }
    }

    suspend fun setImmersiveReader(enabled: Boolean) {
        platform.stores.settings.edit { it[Keys.IMMERSIVE] = enabled }
    }

    suspend fun setTwoFingerBrightness(enabled: Boolean) {
        platform.stores.settings.edit { it[Keys.GESTURE_BRIGHTNESS] = enabled }
    }

    suspend fun setTwoFingerThemeFlick(enabled: Boolean) {
        platform.stores.settings.edit { it[Keys.GESTURE_THEME_FLICK] = enabled }
    }

    suspend fun setOnboarded(done: Boolean) {
        platform.stores.settings.edit { it[Keys.ONBOARDED] = done }
    }

    suspend fun setNoozFlashEnabled(enabled: Boolean) {
        platform.stores.settings.edit { it[Keys.NOOZ_FLASH] = enabled }
    }

    suspend fun setNoozCastEnabled(enabled: Boolean) {
        platform.stores.settings.edit { it[Keys.NOOZ_CAST] = enabled }
    }

    suspend fun setTodayInHistoryEnabled(enabled: Boolean) {
        platform.stores.settings.edit { it[Keys.TODAY_IN_HISTORY] = enabled }
    }

    suspend fun setPaperGrain(grain: PaperGrain) {
        platform.stores.settings.edit { it[Keys.PAPER_GRAIN] = grain.key }
    }

    suspend fun setReadMarkStyle(style: ReadMarkStyle) {
        platform.stores.settings.edit { it[Keys.READ_MARK_STYLE] = style.key }
    }

    suspend fun setReadingAsideStyle(style: ReadingAsideStyle) {
        platform.stores.settings.edit { it[Keys.READING_ASIDE_STYLE] = style.key }
    }

    suspend fun setUnreadPinchFilter(enabled: Boolean) {
        platform.stores.settings.edit { it[Keys.UNREAD_PINCH_FILTER] = enabled }
    }

    suspend fun setShowFeedImages(enabled: Boolean) {
        platform.stores.settings.edit { it[Keys.SHOW_FEED_IMAGES] = enabled }
    }

    suspend fun setHideNsfwImages(enabled: Boolean) {
        platform.stores.settings.edit { it[Keys.HIDE_NSFW_IMAGES] = enabled }
    }

    suspend fun setImageStyle(style: ImageStyle) {
        platform.stores.settings.edit { it[Keys.IMAGE_STYLE] = style.key }
    }

    /** Advanced settings: turn one default lexicon term on or off. Read-modify-write against whatever's already stored. */
    suspend fun setLensTermEnabled(term: String, enabled: Boolean) {
        platform.stores.settings.edit { prefs ->
            val current = prefs[Keys.LENS_DISABLED_DEFAULT_TERMS] ?: emptySet()
            prefs[Keys.LENS_DISABLED_DEFAULT_TERMS] = if (enabled) current - term else current + term
        }
    }

    /** Advanced settings: add a reader's own word/phrase. No-ops on a blank string. */
    suspend fun addLensCustomTerm(term: String) {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return
        platform.stores.settings.edit { prefs ->
            val current = prefs[Keys.LENS_CUSTOM_TERMS] ?: emptySet()
            prefs[Keys.LENS_CUSTOM_TERMS] = current + trimmed
        }
    }

    suspend fun removeLensCustomTerm(term: String) {
        platform.stores.settings.edit { prefs ->
            val current = prefs[Keys.LENS_CUSTOM_TERMS] ?: emptySet()
            prefs[Keys.LENS_CUSTOM_TERMS] = current - term
        }
    }

    suspend fun setFilter(filter: ReaderFilter) {
        platform.stores.settings.edit {
            it[Keys.REGION] = filter.region.key
            it[Keys.TOPICS] = filter.topicKeys
        }
    }
}
