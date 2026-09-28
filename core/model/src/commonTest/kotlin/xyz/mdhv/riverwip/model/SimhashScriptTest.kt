package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dedup must not silently delete real news written in scripts that use
 * combining marks.
 *
 * [Simhash.normalize] used to collapse everything outside `\p{L}\p{N}` to a
 * space. Combining marks (`\p{M}`) are outside both — and that is how Devanagari,
 * Bengali, Telugu, Tamil, Gujarati, Kannada, Malayalam, Odia and Gurmukhi write
 * their vowels and their virama. Two different Hindi headlines were therefore
 * reduced to the same consonant skeleton, landed inside [Simhash.NEAR_DUP_THRESHOLD]
 * of one another, and `Dedup` discarded one of them.
 *
 * The failure had no symptom. `Ingest` calls `Dedup.deduplicate`, which keeps
 * only each cluster's representative and throws the rest away — no log, no
 * error, no counter. A Hindi build simply showed fewer stories than its sources
 * sent, which is indistinguishable from a quiet feed, in an app whose whole
 * claim is measuring what flowed.
 */
class SimhashScriptTest {

    /** Two unrelated Mumbai headlines: heavy *rain* versus heavy *unemployment*. */
    private val hindiRain = "मुंबई में भारी बारिश"
    private val hindiUnemployment = "मुंबई में भारी बेरोजगारी"

    @Test fun normalizeKeepsTheVowelsThatCarryTheMeaning() {
        // The regression in one line: if the marks are gone, so is the word.
        val normalized = Simhash.normalize(hindiRain)
        assertTrue("vowel sign AA survives: $normalized", normalized.contains('ा'))
        assertTrue("vowel sign I survives: $normalized", normalized.contains('ि'))
        assertTrue("anusvara survives: $normalized", normalized.contains('ं'))
        // ...and the text does not disintegrate into single consonants.
        assertFalse("no consonant skeleton: $normalized", normalized.contains("ब र"))
    }

    @Test fun differentHindiHeadlinesAreNotTreatedAsDuplicates() {
        val a = Simhash.of(hindiRain)
        val b = Simhash.of(hindiUnemployment)
        val d = Simhash.distance(a, b)
        assertTrue(
            "distinct Hindi stories must stay outside the threshold, was $d " +
                "(threshold ${Simhash.NEAR_DUP_THRESHOLD})",
            d > Simhash.NEAR_DUP_THRESHOLD,
        )
        assertFalse(Simhash.isNearDuplicate(a, b))
    }

    @Test fun differentTamilHeadlinesAreNotTreatedAsDuplicates() {
        val chennaiRain = "சென்னையில் கனமழை பெய்தது"
        val chennaiTraffic = "சென்னையில் கடும் போக்குவரத்து நெரிசல்"
        val d = Simhash.distance(Simhash.of(chennaiRain), Simhash.of(chennaiTraffic))
        assertTrue("distinct Tamil stories, distance was $d", d > Simhash.NEAR_DUP_THRESHOLD)
    }

    @Test fun differentBengaliHeadlinesAreNotTreatedAsDuplicates() {
        val a = "কলকাতায় ভারী বৃষ্টি"
        val b = "কলকাতায় ভারী যানজট"
        val d = Simhash.distance(Simhash.of(a), Simhash.of(b))
        assertTrue("distinct Bengali stories, distance was $d", d > Simhash.NEAR_DUP_THRESHOLD)
    }

    @Test fun identicalNonLatinTitlesStillCollapse() {
        // The point is not to break dedup — syndication of the same Hindi story
        // across outlets must still collapse.
        assertEquals(0, Simhash.distance(Simhash.of(hindiRain), Simhash.of(hindiRain)))
        assertTrue(Simhash.isNearDuplicate(Simhash.of(hindiRain), Simhash.of(hindiRain)))
    }

    @Test fun theSameTitleInTwoUnicodeFormsStillCollapses() {
        // Feeds are not consistent about composed vs decomposed forms. Devanagari
        // nukta letters have both: U+0929 vs U+0928 U+093C. Without normalising,
        // one publisher's spelling of a headline would not match another's.
        val composed = "क़िला की ख़बर"
        val decomposed = "क़िला की ख़बर"
        val d = Simhash.distance(Simhash.of(composed), Simhash.of(decomposed))
        assertTrue("the same headline in two encodings must collapse, distance was $d", d <= Simhash.NEAR_DUP_THRESHOLD)
    }

    @Test fun latinDedupIsUnchanged() {
        // The calibration in Simhash's own doc comment must still hold: a
        // source-suffix variant collapses, unrelated headlines do not.
        val plain = "Flash floods on the Nepal-Tibet border leave scores dead"
        val suffixed = "Flash floods on the Nepal-Tibet border leave scores dead - Reuters"
        val unrelated = "Democratic states sue to block postal service over mail-in voting"
        assertTrue(
            "syndication variant still collapses",
            Simhash.isNearDuplicate(Simhash.of(plain), Simhash.of(suffixed)),
        )
        assertFalse(
            "unrelated headlines stay apart",
            Simhash.isNearDuplicate(Simhash.of(plain), Simhash.of(unrelated)),
        )
    }

    @Test fun shortHeadlinesGetAProportionallyStricterThreshold() {
        // Urdu ships in the catalogue today (siasat-urdu). This pair — heavy
        // rain in Karachi versus severe heat in Karachi — sits at exactly 8
        // bits even with marks preserved, because a four-word headline gives
        // simhash ~16 shingles to work with. The identical one-word change in
        // Latin sits at 19, so a fixed threshold is systematically harsher on
        // scripts that pack more meaning into fewer characters.
        val a = "کراچی میں شدید بارش"
        val b = "کراچی میں شدید گرمی"
        val budget = Simhash.thresholdFor(a, b)
        assertTrue("short titles earn a stricter budget, was $budget", budget < Simhash.NEAR_DUP_THRESHOLD)
        assertFalse(
            "distinct short Urdu stories must survive dedup",
            Simhash.isNearDuplicate(Simhash.of(a), Simhash.of(b), budget),
        )
    }

    @Test fun theStricterThresholdStillCollapsesWhatItShould() {
        // The floor exists so short titles do not stop deduplicating entirely.
        val title = "मुंबई में भारी बारिश"
        val punctuated = "मुंबई में भारी बारिश!"
        val budget = Simhash.thresholdFor(title, punctuated)
        assertTrue(
            "a punctuation-only variant still collapses",
            Simhash.isNearDuplicate(Simhash.of(title), Simhash.of(punctuated), budget),
        )
    }

    @Test fun longTitlesKeepTheFullThreshold() {
        // Length-awareness must not weaken the case it was calibrated on: a
        // 56-character syndication pair keeps the full 8 bits.
        val plain = "Flash floods on the Nepal-Tibet border leave scores dead"
        val suffixed = "Flash floods on the Nepal-Tibet border leave scores dead - Reuters"
        assertEquals(Simhash.NEAR_DUP_THRESHOLD, Simhash.thresholdFor(plain, suffixed))
        assertTrue(
            Simhash.isNearDuplicate(
                Simhash.of(plain),
                Simhash.of(suffixed),
                Simhash.thresholdFor(plain, suffixed),
            ),
        )
    }
}
