package xyz.mdhv.riverwip.model

import java.text.Normalizer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [UnicodeNormalizer] against `java.text.Normalizer`, the implementation it replaced.
 *
 * The tables are newer than the JDK's Unicode data, but normalization data for a
 * character never changes once assigned, so every character the running JDK
 * knows about must normalize identically. Characters the JDK considers unassigned
 * are skipped.
 */
class NormalizationParityTest {

    private fun jdk(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)

    private val assigned: List<Int> by lazy {
        (0..0x10FFFF).filter { it !in 0xD800..0xDFFF && Character.getType(it) != Character.UNASSIGNED.toInt() }
    }

    @Test fun everyAssignedCodePointNormalizesLikeTheJdk() {
        for (cp in assigned) {
            val s = String(Character.toChars(cp))
            assertEquals(jdk(s), UnicodeNormalizer.nfc(s), "U+%04X".format(cp))
        }
    }

    @Test fun everyAssignedCodePointAfterABaseAndAMarkNormalizesLikeTheJdk() {
        // Exercises composition (base + X) and reordering (X + a second mark).
        for (cp in assigned) {
            val x = String(Character.toChars(cp))
            for (s in listOf("a$x", "क$x", "ᄀ$x", "$x̣́", "a$x̣")) {
                assertEquals(jdk(s), UnicodeNormalizer.nfc(s), "sequence ${s.map { "U+%04X".format(it.code) }}")
            }
        }
    }

    @Test fun randomSequencesOfInterestingCharactersMatchTheJdk() {
        val interesting = assigned.filter {
            it in 0x41..0x7A || it in 0xC0..0x24F || it in 0x300..0x36F || it in 0x900..0xD7F || it in 0x1100..0x11FF ||
                it in 0x1E00..0x1FFF || it in 0x2000..0x2FFF || it in 0x3040..0x30FF || it in 0xAC00..0xAD00 ||
                it in 0xFB00..0xFDFF || it in 0x1D100..0x1D1FF || it in 0x1F300..0x1F3FF
        }
        val rnd = Random(20260928)
        repeat(200_000) {
            val s = buildString { repeat(rnd.nextInt(1, 9)) { appendCodePoint(interesting[rnd.nextInt(interesting.size)]) } }
            assertEquals(jdk(s), UnicodeNormalizer.nfc(s), "sequence ${s.map { "U+%04X".format(it.code) }}")
        }
        assertTrue(interesting.size > 5_000)
    }

    @Test fun loneSurrogatesPassThroughUnchanged() {
        for (s in listOf("\uD800", "a\uDC00́", "\uD83D", "x\uD83Dy")) assertEquals(jdk(s), UnicodeNormalizer.nfc(s))
    }
}
