package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LensGuardTest {

    // ---- adversarial corpus: the guard must catch 100% of seeded violations ----

    private data class Case(val source: String, val badRewrite: String, val expected: FidelityGuard.Kind)

    private val corpus = listOf(
        // (a) numbers: swapped, added, dropped
        Case(
            "The report says 412 people were affected in the region.",
            "The report says 512 people were affected in the region.",
            FidelityGuard.Kind.NUMBER_ADDED, // 512 not in source
        ),
        Case(
            "Officials confirmed 9 arrests overnight.",
            "Officials confirmed 9 arrests and 3 injuries overnight.",
            FidelityGuard.Kind.NUMBER_ADDED,
        ),
        Case(
            "Prices rose 3.5% last quarter.",
            "Prices rose last quarter.",
            FidelityGuard.Kind.NUMBER_REMOVED,
        ),
        Case(
            "Turnout reached 1,200 at the rally.",
            "Turnout reached 1,500 at the rally.",
            FidelityGuard.Kind.NUMBER_ADDED,
        ),
        // (b) entities: substituted, added, dropped
        Case(
            "Modi met Biden in Delhi on Tuesday.",
            "Modi met Trump in Delhi on Tuesday.",
            FidelityGuard.Kind.ENTITY_ADDED, // Trump not in source
        ),
        Case(
            "The court ruled against the ministry.",
            "The Supreme Court ruled against the ministry.",
            FidelityGuard.Kind.ENTITY_ADDED, // Supreme added
        ),
        Case(
            "Reuters reported the outage on Friday.",
            "It was reported the outage on Friday.",
            FidelityGuard.Kind.ENTITY_REMOVED, // Reuters dropped
        ),
        // (c) negation: dropped, added, flipped
        Case(
            "The minister said the deal was not approved.",
            "The minister said the deal was approved.",
            FidelityGuard.Kind.NEGATION_CHANGED,
        ),
        Case(
            "Investigators found evidence of tampering.",
            "Investigators found no evidence of tampering.",
            FidelityGuard.Kind.NEGATION_CHANGED,
        ),
        Case(
            "She couldn't confirm the figures.",
            "She could confirm the figures.",
            FidelityGuard.Kind.NEGATION_CHANGED,
        ),
    )

    @Test fun guardCatchesEverySeededViolation() {
        var caught = 0
        for (c in corpus) {
            val v = FidelityGuard.check(c.source, c.badRewrite)
            assertFalse("should reject: ${c.source} -> ${c.badRewrite}", v.accepted)
            assertTrue(
                "expected ${c.expected} among ${v.violations.map { it.kind }} for '${c.badRewrite}'",
                v.violations.any { it.kind == c.expected },
            )
            caught++
        }
        assertEquals("100% of seeded violations caught", corpus.size, caught)
    }

    // ---- precision: faithful neutralizations must pass ----

    @Test fun faithfulNeutralizationIsAccepted() {
        // Only the loaded verb changes; numbers, entities, negation preserved.
        val faithful = listOf(
            "Modi slammed the opposition over 3 bills." to "Modi criticized the opposition over 3 bills.",
            "The report was utterly damning for the ministry." to "The report was damaging for the ministry.",
            "Officials reportedly ignored 12 warnings." to "Officials ignored 12 warnings.",
            "The bill was blasted by 4 senators." to "The bill was opposed by 4 senators.",
        )
        for ((src, rew) in faithful) {
            val v = FidelityGuard.check(src, rew)
            assertTrue("should accept faithful rewrite: '$rew' (${v.reason})", v.accepted)
        }
    }

    @Test fun negationSynonymSwapKeepsParity() {
        // "not approved" -> "rejected": negation count drops 1->0, so the guard
        // flags it. This is intentional — the guard is conservative about polarity.
        val v = FidelityGuard.check("The deal was not approved.", "The deal was rejected.")
        assertFalse(v.accepted)
    }

    // ---- affect-span detection ----

    @Test fun detectsLoadedVerbWithEvidence() {
        val spans = AffectSpanDetector.detect("Modi slammed the opposition in a shocking move.")
        assertTrue(spans.any { it.term == "slammed" && it.category == BiasLexicon.Category.LOADED_VERB })
        assertTrue(spans.any { it.term == "shocking" && it.category == BiasLexicon.Category.EMOTIVE_ADJECTIVE })
        val slam = spans.first { it.term == "slammed" }
        assertEquals("flagged: 'slammed', loaded verb", slam.evidence)
        // Span offsets point at the real text.
        assertEquals("slammed", "Modi slammed the opposition in a shocking move.".substring(slam.start, slam.end))
    }

    @Test fun spansAreNonOverlappingAndOrdered() {
        val text = "So-called experts very clearly got it wrong."
        val spans = AffectSpanDetector.detect(text)
        for (i in 1 until spans.size) assertTrue(spans[i].start >= spans[i - 1].end)
        // The phrase "so-called" is detected as one hedge span.
        assertTrue(spans.any { it.term == "so-called" })
    }

    @Test fun cleanTextHasNoSpans() {
        assertEquals(0, AffectSpanDetector.count("The committee met on Tuesday to review the budget."))
    }

    // ---- Advanced settings: per-word default disable + custom terms ----

    @Test fun disabledDefaultTermIsSilencedButOthersStillFire() {
        val text = "Modi slammed the opposition in a shocking move."
        val spans = AffectSpanDetector.detect(text, disabledDefaultTerms = setOf("slammed"))
        assertFalse(spans.any { it.term == "slammed" })
        assertTrue(spans.any { it.term == "shocking" && it.category == BiasLexicon.Category.EMOTIVE_ADJECTIVE })
    }

    @Test fun customTermIsDetectedAndTaggedCustomCategory() {
        val spans = AffectSpanDetector.detect("The committee met to review the budget.", customTerms = setOf("committee"))
        val hit = spans.firstOrNull { it.term == "committee" }
        assertTrue(hit != null)
        assertEquals(BiasLexicon.Category.CUSTOM, hit!!.category)
        assertEquals("flagged: 'committee', custom", hit.evidence)
    }

    @Test fun blankAndWhitespaceCustomTermsAreIgnoredNotCrashed() {
        val spans = AffectSpanDetector.detect("Plain text.", customTerms = setOf("", "   "))
        assertEquals(0, spans.size)
    }

    @Test fun unqualifiedDetectStillBehavesExactlyAsBefore() {
        // Regression guard: adding the two new defaulted parameters must never
        // change behaviour for any existing zero-arg caller.
        val text = "Modi slammed the opposition in a shocking move."
        assertEquals(AffectSpanDetector.detect(text), AffectSpanDetector.detect(text, emptySet(), emptySet()))
    }
}
