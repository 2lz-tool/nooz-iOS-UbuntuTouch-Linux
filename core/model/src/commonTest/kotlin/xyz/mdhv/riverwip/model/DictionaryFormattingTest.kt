package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryFormattingTest {

    // A real Webster's 1913 entry (this app's own bundled dictionary, "risk") —
    // two part-of-speech groups (noun, then verb), each numbered 1/2, each
    // closed by a "Syn. --" block. No line breaks at all in the source.
    private val risk = "1. Hazard; danger; peril; exposure to loss, injury, or destruction. The imminent " +
        "and constant risk of assassination, a risk which has shaken very strong nerves. Macaulay. " +
        "2. (Com.) Hazard of loss; liabillity to loss in property. To run a risk, to incur hazard; " +
        "to encounter danger. Syn. -- Danger; hazard; peril; jeopardy; exposure. See Danger. " +
        "1. To expose to risk, hazard, or peril; to venture; as, to risk goods on board of a ship; " +
        "to risk one's person in battle; to risk one's fame by a publication. " +
        "2. To incur the risk or danger of; as, to risk a battle. " +
        "Syn. -- To hazard; peril; endanger; jeopard."

    @Test fun parsesEveryMarkerInTheRiskEntry() {
        val senses = DictionaryFormatting.parse(risk)
        assertEquals(6, senses.size)
        assertEquals(listOf(1, 2, null, 1, 2, null), senses.map { it.number })
        assertEquals(listOf(false, false, true, false, false, true), senses.map { it.isSynonymBlock })
    }

    @Test fun onlyTheVerbGroupsFirstSenseStartsANewGroup() {
        val senses = DictionaryFormatting.parse(risk)
        // Sense 1 (noun) is the very first sense in the entry — not a "restart".
        assertFalse(senses[0].startsNewGroup)
        // Sense 2 (noun) and both Syn blocks never start a group.
        assertFalse(senses[1].startsNewGroup)
        assertFalse(senses[2].startsNewGroup)
        // Sense 1 (verb) is a numbering restart — the only signal a new part
        // of speech began.
        assertTrue(senses[3].startsNewGroup)
        assertFalse(senses[4].startsNewGroup)
        assertFalse(senses[5].startsNewGroup)
    }

    @Test fun bodyTextExcludesItsOwnMarkerAndTrimsCleanly() {
        val senses = DictionaryFormatting.parse(risk)
        assertTrue(senses[0].text.startsWith("Hazard; danger"))
        assertTrue(senses[0].text.endsWith("Macaulay."))
        assertTrue(senses[1].text.startsWith("(Com.) Hazard of loss"))
        assertEquals("Danger; hazard; peril; jeopardy; exposure. See Danger.", senses[2].text)
    }

    @Test fun unNumberedDefinitionIsOneWholeSense() {
        val senses = DictionaryFormatting.parse("A sweet crystalline quaternary ammonium salt.")
        assertEquals(1, senses.size)
        assertEquals(null, senses[0].number)
        assertFalse(senses[0].isSynonymBlock)
        assertFalse(senses[0].startsNewGroup)
    }

    @Test fun decimalNumbersInProseDoNotFalseMatchAsSenseMarkers() {
        // "3.5" has a digit right after the period, not whitespace — must not split here.
        val senses = DictionaryFormatting.parse("Prices rose 3.5 percent last quarter, economists say.")
        assertEquals(1, senses.size)
        assertEquals(null, senses[0].number)
    }

    @Test fun blankInputYieldsNoSenses() {
        assertEquals(emptyList<DictionaryFormatting.Sense>(), DictionaryFormatting.parse("   "))
    }
}
