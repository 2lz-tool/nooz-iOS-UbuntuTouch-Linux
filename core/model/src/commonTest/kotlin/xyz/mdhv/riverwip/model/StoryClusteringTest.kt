package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryClusteringTest {

    private fun doc(id: String, source: String, title: String) = StoryClustering.Doc(id, title, source)

    @Test fun sameStoryAcrossSourcesClusters() {
        val docs = listOf(
            doc("a", "guardian", "Russia invades Ukraine as war erupts in Europe"),
            doc("b", "channel4", "Israeli troops cross into Ukraine amid Europe war fears"),
            doc("c", "espn", "Local football team wins the county championship final"),
        )
        val clusters = StoryClustering.cluster(docs)
        // a and b share "ukraine" and "europe" (>= 2) across different sources
        // ("war" is too short to count); c shares nothing.
        assertEquals(1, clusters.size)
        val members = clusters.first().members.map { it.id }.toSet()
        assertEquals(setOf("a", "b"), members)
        assertEquals(2, clusters.first().sourceCount)
    }

    @Test fun unrelatedHeadlinesDoNotCluster() {
        val docs = listOf(
            doc("a", "guardian", "Markets rally on strong jobs report figures"),
            doc("b", "bbc", "Volcano erupts near remote Pacific island chain"),
        )
        assertTrue(StoryClustering.cluster(docs).isEmpty())
    }

    @Test fun sameSourceRepetitionIsNotAFramingContrast() {
        // One outlet re-running near-identical headlines is not a cross-source
        // framing contrast, so no cluster is returned.
        val docs = listOf(
            doc("a", "guardian", "Election results pour in across the country tonight"),
            doc("b", "guardian", "Election results across the country continue tonight"),
        )
        assertTrue(StoryClustering.cluster(docs).isEmpty())
    }

    @Test fun keywordsDropStopwordsAndShortWords() {
        val kw = StoryClustering.keywordsOf("The vote was over after they said more")
        // "the","was","over","after","they","said","more" are stopwords; "vote"
        // survives (4 letters, significant).
        assertTrue(kw.contains("vote"))
        assertFalse(kw.contains("said"))
        assertFalse(kw.contains("over"))
        assertFalse(kw.contains("the"))
    }
}
