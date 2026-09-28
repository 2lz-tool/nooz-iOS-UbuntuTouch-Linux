package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObscureWordsTest {

    private val common = setOf("the", "a", "is", "was", "very", "long", "word", "cat", "the", "house", "over")

    @Test fun flagsWordsAbsentFromCommonSet() {
        val spans = ObscureWords.detect("The perspicacious cat.", common, minLength = 7)
        assertEquals(1, spans.size)
        assertEquals("perspicacious", spans.first().word)
    }

    @Test fun spanOffsetsPointAtTheWord() {
        val text = "A truly antediluvian idea."
        val span = ObscureWords.detect(text, common, minLength = 7).first()
        assertEquals("antediluvian", text.substring(span.start, span.end))
    }

    @Test fun skipsShortAndCommonWords() {
        // "house" is common; "over" is common; nothing long+rare here.
        assertTrue(ObscureWords.detect("The cat was over the house.", common, minLength = 7).isEmpty())
    }

    @Test fun skipsMidSentenceProperNouns() {
        // "Zanzibar" is capitalized mid-sentence -> treated as a name, not vocabulary.
        val spans = ObscureWords.detect("We sailed to Zanzibar yesterday.", common, minLength = 7)
        assertTrue(spans.none { it.word == "Zanzibar" })
    }

    @Test fun keepsSentenceStartWord() {
        // A long rare word starting a sentence is kept even though capitalized.
        val spans = ObscureWords.detect("Perspicacity matters.", common, minLength = 7)
        assertEquals("Perspicacity", spans.first().word)
    }

    @Test fun emptyCommonSetFlagsNothing() {
        assertTrue(ObscureWords.detect("Perspicacious antediluvian.", emptySet(), minLength = 7).isEmpty())
    }
}
