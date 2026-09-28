package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassifierTest {

    @Test fun lexiconMatchCarriesEvidence() {
        val ev = Classifier.classify(
            title = "Parliament passes new election law after long debate",
        )
        val politics = ev.firstOrNull { it.topic == Topic.POLITICS }
        assertTrue("politics fired", politics != null)
        assertEquals("lexicon:politics", politics!!.ruleId)
        // The exact terms that fired are retained (brief §3 inspectability).
        assertTrue(politics.matchedTerms.any { it == "parliament" })
        assertTrue(politics.matchedTerms.any { it == "election" })
    }

    @Test fun feedCategoryMapsToTaxonomyWithEvidence() {
        val ev = Classifier.classify(
            title = "Match report: a quiet night",
            feedCategories = listOf("Sports"),
        )
        val sport = ev.first { it.topic == Topic.SPORT && it.ruleId == "feedcat:sport" }
        assertEquals(listOf("Sports"), sport.matchedTerms)
    }

    @Test fun unmatchedFallsBackToOther() {
        val ev = Classifier.classify(title = "asdfghjkl qwerty zxcvb")
        assertEquals(1, ev.size)
        assertEquals(Topic.OTHER, ev[0].topic)
        assertEquals("fallback", ev[0].ruleId)
    }

    @Test fun dominantTopicPrefersStrongerSignal() {
        // Feed category (weight 2+) for climate plus a single business term.
        val ev = Classifier.classify(
            title = "Markets watch as monsoon drought hits crops",
            feedCategories = listOf("Environment"),
        )
        assertEquals(Topic.CLIMATE, Classifier.dominantTopic(ev))
    }

    @Test fun multiTopicRetainsAllEvidence() {
        val ev = Classifier.classify(
            title = "War drives up oil prices and rattles the stock market",
        )
        val topics = ev.map { it.topic }.toSet()
        assertTrue(topics.contains(Topic.CONFLICT))
        assertTrue(topics.contains(Topic.BUSINESS))
    }

    @Test fun everyEvidenceRuleIdIsTraceable() {
        // No black-box judgments: every evidence names a rule (brief §3).
        val ev = Classifier.classify("Climate change accelerates glacier melt", feedCategories = listOf("Science"))
        assertTrue(ev.isNotEmpty())
        assertTrue(ev.all { it.ruleId.startsWith("lexicon:") || it.ruleId.startsWith("feedcat:") || it.ruleId == "fallback" })
    }
}
