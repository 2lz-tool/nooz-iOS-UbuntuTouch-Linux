package xyz.mdhv.riverwip.design

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import xyz.mdhv.riverwip.design.res.*
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * That the app actually *picks* the translations, not merely that they ship.
 *
 * Packaging a string proves nothing about selection: selection depends on the
 * `values-<tag>` qualifier being spelled the way the resource system expects, on
 * the module's resources being merged into the app, and on the runtime locale
 * matching. Get any of that wrong and every locale falls back to English,
 * silently, with the strings still sitting in the build.
 *
 * Runs against Compose Multiplatform's own resolver on the desktop JVM
 * (`Locale.setDefault` is what the desktop resource environment reads), which is
 * the same resolver the Android build uses. Simplified Chinese ships as plain
 * `values-zh`: Compose resources have no script qualifier.
 */
class LocaleResolutionTest {

    private val original = Locale.getDefault()

    @AfterTest fun restore() = Locale.setDefault(original)

    private fun tr(tag: String, res: StringResource, vararg args: Any): String {
        Locale.setDefault(Locale.forLanguageTag(tag))
        return runBlocking { getString(res, *args) }
    }

    private fun paper(tag: String): String = tr(tag, Res.string.screen_paper)

    @Test fun englishIsTheBase() = assertEquals("Paper", paper("en"))
    @Test fun hindiResolves() = assertEquals("अख़बार", paper("hi"))
    @Test fun tamilResolves() = assertEquals("பத்திரிகை", paper("ta"))
    @Test fun urduResolves() = assertEquals("اخبار", paper("ur"))

    @Test fun simplifiedChineseResolvesForEveryChineseTag() {
        // No script qualifier exists, so zh-Hans and zh-CN both land on values-zh.
        assertEquals("报纸", paper("zh-Hans"))
        assertEquals("报纸", paper("zh-CN"))
    }

    @Test fun aThreeLetterTagResolves() {
        // Maithili: three-letter codes go down a different path in some resolvers than two-letter ones.
        assertEquals("अखबार", paper("mai"))
    }

    @Test fun aPartialLocaleFallsBackPerKey() {
        // Kashmiri has a translation for this key and not for most others. Per-key fallback is the
        // property the whole "partial is safe" design rests on, so it is asserted rather than assumed.
        assertEquals("اخبار", paper("ks"))
        assertEquals("Quick setup", tr("ks", Res.string.onboarding_quick_setup), "an untranslated key falls back to English, not to a blank or a key name")
    }

    @Test fun anUnshippedLocaleGetsEnglish() {
        // Icelandic is not in the catalogue; it must land on the base, not on an empty string.
        assertEquals("Paper", paper("is"))
    }

    @Test fun formatArgumentsSurviveTranslation() {
        // A translated format string that lost its placeholder would render a gap here rather than the number.
        val spoken = tr("te", Res.string.loom_stream_action, "రాజకీయాలు", 40, 2)
        assertTrue(spoken.contains("40") && spoken.contains("2"), "the counts are in the string: $spoken")
        assertTrue(!spoken.contains("flowed"), "and it is not the English one: $spoken")
    }

    @Test fun theSettingsScreenResolves() {
        // Settings carries the app's privacy claims; a long body string is where a half-finished migration
        // shows up, because a screen can be wired for its headings and still be English underneath.
        assertEquals("നിഘണ്ടു", tr("ml", Res.string.settings_dictionary))
        val yourData = tr("ml", Res.string.settings_your_data_body)
        assertTrue(!yourData.contains("Export everything"), "resolution reached Malayalam: $yourData")
        assertTrue(yourData.contains("API"), "and API is left as-is inside it: $yourData")
    }

    @Test fun aSettingsFormatStringKeepsItsPlaceholder() {
        // settings_size_license carries two positional arguments; a translation that dropped one would
        // silently render half the row.
        val row = tr("ar", Res.string.settings_size_license, "48 MB", "MIT")
        assertTrue(row.contains("48 MB") && row.contains("MIT"), "both arguments land: $row")
    }

    @Test fun spokenLabelsResolve() {
        // `onClickLabel` and custom-action labels are copy that ONLY a screen reader ever renders, so a
        // sighted check of a Tamil build looks completely translated while every spoken affordance is English.
        assertEquals("அன்றைய தறியைத் திற", tr("ta", Res.string.list_open_loom))
        val define = tr("ta", Res.string.lens_define_word, "பத்திரிகை")
        assertTrue(define.contains("பத்திரிகை"), "the word is interpolated, not dropped: $define")
        assertTrue(!define.startsWith("Define"), "and the frame is Tamil: $define")
    }

    @Test fun everyShippedLocaleDiffersFromEnglish() {
        // A weak assertion on purpose: it cannot check the Bengali is *good*, only that resolution reached it.
        assertNotEquals("Paper", paper("bn"))
    }
}
