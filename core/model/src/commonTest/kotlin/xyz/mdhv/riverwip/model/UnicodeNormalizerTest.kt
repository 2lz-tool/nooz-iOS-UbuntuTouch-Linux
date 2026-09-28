package xyz.mdhv.riverwip.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** Expected values computed independently with Python's `unicodedata.normalize("NFC", …)`. */
class UnicodeNormalizerTest {

    private fun nfc(vararg cps: Int): List<Int> {
        val s = buildString { for (cp in cps) append(CodePoints.toStringOrNull(cp)!!) }
        val out = UnicodeNormalizer.nfc(s)
        val res = ArrayList<Int>()
        var i = 0
        while (i < out.length) {
            val c = out[i]
            if (c.isHighSurrogate() && i + 1 < out.length) {
                res.add(0x10000 + ((c.code - 0xD800) shl 10) + (out[i + 1].code - 0xDC00)); i += 2
            } else { res.add(c.code); i++ }
        }
        return res
    }

    @Test fun composesLatinAndReordersMarks() {
        assertEquals(listOf(0x00E9), nfc(0x65, 0x301))
        assertEquals(listOf(0x1EA1, 0x307), nfc(0x61, 0x307, 0x323))
        assertEquals(listOf(0x1EA1, 0x307), nfc(0x61, 0x323, 0x307))
        assertEquals(listOf(0x01FA), nfc(0x41, 0x30A, 0x301))
    }

    @Test fun replacesSingletonsAndKeepsExclusions() {
        assertEquals(listOf(0x00C5), nfc(0x212B)) // ANGSTROM SIGN -> A WITH RING ABOVE
        assertEquals(listOf(0x0915, 0x093C), nfc(0x0958)) // composition-excluded Devanagari QA stays decomposed
        assertEquals(listOf(0x0915, 0x093C), nfc(0x0915, 0x093C))
        assertEquals(listOf(0x1D157, 0x1D165), nfc(0x1D15E)) // astral, excluded
        assertEquals(listOf(0x0F71, 0x0F72), nfc(0x0F73))
    }

    @Test fun composesIndicVowelSignsAndHangul() {
        assertEquals(listOf(0x0B4B), nfc(0x0B47, 0x0B3E))
        assertEquals(listOf(0xAC01), nfc(0x1100, 0x1161, 0x11A8))
        assertEquals(listOf(0xAC01), nfc(0xAC00, 0x11A8))
        assertEquals(listOf(0xAC00), nfc(0xAC00))
    }

    @Test fun leavesStableTextAloneWithoutAllocating() {
        val ascii = "Breaking: flood displaces thousands"
        assertSame(ascii, UnicodeNormalizer.nfc(ascii))
        assertEquals("café", UnicodeNormalizer.nfc("café"))
        assertEquals("चुनाव आज है", UnicodeNormalizer.nfc("चुनाव आज है"))
    }

    @Test fun isIdempotent() {
        for (s in listOf("café", "Ạ̊", "각x", "ạ̣̇")) {
            val once = UnicodeNormalizer.nfc(s)
            assertEquals(once, UnicodeNormalizer.nfc(once))
        }
    }
}
