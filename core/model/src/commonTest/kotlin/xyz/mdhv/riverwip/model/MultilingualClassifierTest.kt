package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The classifier, in the languages the catalogue actually publishes in.
 *
 * Two bugs met here, and either alone was enough to hide the other.
 *
 * **The lexicon was English-only** while the source catalogue ships 33 India
 * regional feeds across eleven scripts. Every one of those stories classified
 * as `general`: the Loom collapsed to a single band and the Contrast dumbbells
 * emptied, for exactly the readers the India expansion was for. Nothing
 * errored — it looked like a quiet news day, every day.
 *
 * **And the matcher could not have fired even with the terms present.** It was
 * `Regex("\\b" + escape(term) + "\\b")`, and Java defines `\b` against `\w` =
 * `[a-zA-Z_0-9]` unless `UNICODE_CHARACTER_CLASS` is set. There is no
 * word/non-word transition at the edge of a Devanagari or Arabic character the
 * engine does not consider a word character at all, so every non-English term
 * added to the lexicon would have been silently inert. The failure would have
 * looked exactly like "that keyword just isn't in this headline", which is why
 * the boundary cases below are tested independently of any particular word.
 */
class MultilingualClassifierTest {

    private fun topicOf(title: String): Topic =
        Classifier.dominantTopic(Classifier.classify(title))

    @Test fun headlinesClassifyInEveryScriptTheCatalogueShips() {
        val cases = listOf(
            "लोकसभा चुनाव में मतदान शुरू" to Topic.POLITICS,
            "কিরকেট ম্যাচে জয়".replace("কিরকেট", "ক্রিকেট") to Topic.SPORT,
            "స్టాక్ మార్కెట్‌లో ద్రవ్యోల్బణం ఆందోళన" to Topic.BUSINESS,
            "கிரிக்கெட் போட்டியில் வெற்றி" to Topic.SPORT,
            "وزیراعظم نے پالیسی کا اعلان کیا" to Topic.POLITICS,
            "ಇಸ್ರೋ ಉಪಗ್ರಹ ಉಡಾವಣೆ" to Topic.SCIENCE,
            "വെള്ളപ്പൊക്കം: കാലാവസ്ഥ മുന്നറിയിപ്പ്" to Topic.CLIMATE,
            "ચૂંટણી પ્રચાર શરૂ" to Topic.POLITICS,
            "ਹਸਪਤਾਲ ਵਿੱਚ ਟੀਕਾ ਮੁਹਿੰਮ" to Topic.HEALTH,
            "ବନ୍ୟା ପରିସ୍ଥିତି ଗମ୍ଭୀର" to Topic.CLIMATE,
            "क्रिकेट सामन्यात विजय" to Topic.SPORT,
        )
        for ((title, expected) in cases) {
            assertEquals("\"$title\" should classify as $expected", expected, topicOf(title))
        }
    }

    @Test fun englishStillClassifies() {
        // The merge must not disturb what already worked.
        assertEquals(Topic.POLITICS, topicOf("Parliament votes on new legislation"))
        assertEquals(Topic.SPORT, topicOf("England win the World Cup final"))
        // The boundary is load-bearing, not decorative: "warden" is not "war".
        assertEquals(Topic.OTHER, topicOf("The warden opened the gate"))
    }

    @Test fun theMatcherCanFireInAnyScript() {
        // Independent of which words are in the lexicon: this is about the
        // boundary construction itself, which is what was broken.
        for (term in listOf("चुनाव", "নির্বাচন", "தேர்தல்", "انتخابات", "ಚುನಾವಣೆ", "ਚੋਣ")) {
            val matcher = TopicLexicon.matcherFor(term)
            assertTrue("$term must match on its own", matcher.containsMatchIn(term))
            assertTrue("$term must match mid-sentence", matcher.containsMatchIn("आज $term हुआ"))
        }
    }

    @Test fun scriptsWrittenWithoutSpacesMatchByContainment() {
        // A word boundary is not a meaningful idea inside a run of Han: the
        // boundary form would demand a non-letter on each side and could never
        // fire in a real sentence.
        val matcher = TopicLexicon.matcherFor("選挙")
        assertTrue(matcher.containsMatchIn("参議院選挙が始まった"))
    }

    @Test fun combiningMarksDoNotSplitAWord() {
        // Indic matras and viramas are \p{M}, not \p{L}. Without \p{M} in the
        // boundary class, a term ending just before a matra looks like a whole
        // word and matches an inflected form it should not — the same defect
        // fixed in ArticleSearch and Simhash.
        val matcher = TopicLexicon.matcherFor("चुनाव")
        assertTrue("the bare word matches", matcher.containsMatchIn("चुनाव आज है"))
        assertTrue(
            "an inflected form must not count as the bare word",
            !matcher.containsMatchIn("चुनावों"),
        )
    }

    @Test fun everyGeneratedTermCompilesAndIsReachable() {
        // A term that cannot compile would take the whole lexicon down; one
        // that cannot match itself is dead weight nobody would ever notice.
        var checked = 0
        for ((_, terms) in TopicLexicon.allTerms) {
            for (term in terms) {
                val matcher = TopicLexicon.matcherFor(term)
                assertTrue("\"$term\" cannot match itself", matcher.containsMatchIn(term))
                checked++
            }
        }
        assertTrue("the localized lexicon actually loaded: only $checked terms", checked > 900)
    }
}
