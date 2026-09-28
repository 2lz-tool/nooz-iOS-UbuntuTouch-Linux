package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimhashDedupTest {

    private fun item(id: String, url: String, title: String) = Item(
        id = id,
        sourceId = "s",
        canonicalUrl = CanonicalUrl.canonicalize(url),
        title = title,
        publishedAt = 0L,
        fetchedAt = 0L,
        simhash = Simhash.of(title),
    )

    @Test fun identicalTitlesHaveZeroDistance() {
        val a = Simhash.of("Floods displace thousands in Assam")
        val b = Simhash.of("Floods displace thousands in Assam")
        assertEquals(0, Simhash.distance(a, b))
    }

    @Test fun sourceSuffixVariantIsNearDuplicate() {
        // The common syndication pattern: same headline, source appended.
        val a = Simhash.of("Government unveils new climate policy for 2027")
        val b = Simhash.of("Government unveils new climate policy for 2027 - Reuters")
        assertTrue("distance=${Simhash.distance(a, b)}", Simhash.isNearDuplicate(a, b))
    }

    @Test fun unrelatedTitlesAreNotDuplicates() {
        val a = Simhash.of("Central bank holds interest rates steady")
        val b = Simhash.of("Cricket team names squad for World Cup")
        assertNotEquals(0, Simhash.distance(a, b))
        // Distinct stories stay well above the near-dup threshold.
        assertTrue(Simhash.distance(a, b) > Simhash.NEAR_DUP_THRESHOLD)
        assertTrue(!Simhash.isNearDuplicate(a, b))
    }

    @Test fun dedupCollapsesExactUrlAcrossSources() {
        val items = listOf(
            item("1", "https://a.com/story?utm_source=x", "Big story"),
            item("2", "https://a.com/story", "Big story (syndicated)"),
        )
        // Same canonical URL → one cluster.
        val clusters = Dedup.cluster(items)
        assertEquals(1, clusters.size)
        assertEquals(2, clusters[0].size)
    }

    @Test fun dedupCollapsesNearDuplicateTitles() {
        val items = listOf(
            item("1", "https://a.com/x", "Government unveils new climate policy for 2027"),
            item("2", "https://b.com/y", "Government unveils new climate policy for 2027"),
            item("3", "https://c.com/z", "Cricket board announces new coach"),
        )
        val reps = Dedup.deduplicate(items)
        assertEquals(2, reps.size) // two climate items merge; cricket stands alone
    }

    @Test fun zeroSimhashNeverMergesByContent() {
        val items = listOf(
            item("1", "https://a.com/x", "").copy(simhash = 0L),
            item("2", "https://b.com/y", "").copy(simhash = 0L),
        )
        assertEquals(2, Dedup.deduplicate(items).size)
    }
}
