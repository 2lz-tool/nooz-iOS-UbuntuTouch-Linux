package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceSearchTest {

    private fun def(
        id: String,
        title: String,
        url: String? = null,
        region: String? = null,
        notes: String? = null,
    ) = ServiceDef(id = id, kind = "rss", title = title, url = url, region = region, notes = notes)

    private val bbcWorld = def("bbc-world", "BBC World", "https://feeds.bbci.co.uk/news/world/rss.xml", "global")
    private val guardianWorld = def("guardian-world", "The Guardian: World", "https://www.theguardian.com/world/rss", "global")
    private val npr = def("npr-news", "NPR News", "https://feeds.npr.org/1001/rss.xml", "global")
    private val tv9Telugu = def("tv9-telugu-ap", "TV9 Telugu: Andhra Pradesh", "https://tv9telugu.com/andhra-pradesh/feed", "india")
    private val all = listOf(bbcWorld, guardianWorld, npr, tv9Telugu)

    @Test fun wordOrderNoLongerMatters() {
        // The old substring filter found nothing for this, though "BBC World"
        // is right there.
        assertTrue(SourceSearch.matches(bbcWorld, "world bbc"))
        assertTrue(SourceSearch.matches(bbcWorld, "bbc world"))
    }

    @Test fun aDomainFindsItsPublisher() {
        // Readers know publishers by domain at least as often as by masthead.
        assertTrue(SourceSearch.matches(npr, "npr.org"))
        assertTrue(SourceSearch.matches(guardianWorld, "theguardian.com"))
        assertFalse(SourceSearch.matches(npr, "theguardian.com"))
    }

    @Test fun aRegionOrLanguageFindsItsFeeds() {
        // D35 keeps the language name in the visible title on purpose; this is
        // what makes that convention reachable, along with the region tag.
        assertTrue(SourceSearch.matches(tv9Telugu, "telugu"))
        assertTrue(SourceSearch.matches(tv9Telugu, "india"))
        assertTrue("terms may match different fields", SourceSearch.matches(tv9Telugu, "india tv9"))
        assertFalse(SourceSearch.matches(bbcWorld, "telugu"))
    }

    @Test fun everyTermMustMatchSomething() {
        assertFalse(SourceSearch.matches(bbcWorld, "bbc telugu"))
        assertFalse(SourceSearch.matches(bbcWorld, "nonexistent"))
    }

    @Test fun anEmptyQueryMatchesEverythingRatherThanNothing() {
        assertTrue(SourceSearch.matches(bbcWorld, ""))
        assertTrue(SourceSearch.matches(bbcWorld, "   "))
        assertEquals(all, SourceSearch.rank(all, "") { it })
        assertEquals(all, SourceSearch.rank(all, "  ") { it })
    }

    @Test fun titleMatchesOutrankIncidentalUrlMatches() {
        // "guardian" appears in The Guardian's title and in nothing else here;
        // add a decoy whose *url* contains it to prove ordering.
        val decoy = def("decoy", "Some Aggregator", "https://example.invalid/via-guardian/rss")
        val ranked = SourceSearch.rank(listOf(decoy, guardianWorld), "guardian") { it }
        assertEquals(listOf(guardianWorld, decoy), ranked)
    }

    @Test fun exactTitleWinsOverPrefixWinsOverWordPrefix() {
        val exact = def("a", "BBC")
        val prefix = def("b", "BBC World Service")
        val wordPrefix = def("c", "The BBC Reader")
        val ranked = SourceSearch.rank(listOf(wordPrefix, prefix, exact), "bbc") { it }
        assertEquals(listOf(exact, prefix, wordPrefix), ranked)
    }

    @Test fun tiesKeepTheirIncomingOrder() {
        // The catalogue is arranged deliberately; a search must not silently
        // reshuffle what it did not actually rank differently.
        val first = def("f", "World Report One", region = "global")
        val second = def("s", "World Report Two", region = "global")
        assertEquals(listOf(first, second), SourceSearch.rank(listOf(first, second), "world report") { it })
        assertEquals(listOf(second, first), SourceSearch.rank(listOf(second, first), "world report") { it })
    }

    @Test fun rankFiltersAsWellAsOrders() {
        val ranked = SourceSearch.rank(all, "world") { it }
        assertTrue("only world feeds", ranked.all { it.title.contains("World") })
        assertEquals(2, ranked.size)
    }

    @Test fun matchingIsCaseInsensitiveAndIgnoresPunctuation() {
        assertTrue(SourceSearch.matches(guardianWorld, "GUARDIAN"))
        assertTrue(SourceSearch.matches(guardianWorld, "guardian: world"))
        assertTrue(SourceSearch.matches(tv9Telugu, "Andhra, Pradesh"))
    }

    @Test fun worksOverTheRealCatalogue() {
        // The point of the exercise: the shipped catalogue is now large enough
        // that these have to actually work against it, not just a fixture.
        val feeds = Starters.verifiedFeeds
        assertTrue("finds Telugu feeds", SourceSearch.rank(feeds, "telugu") { it }.isNotEmpty())
        assertTrue("finds Odia feeds", SourceSearch.rank(feeds, "odia") { it }.isNotEmpty())
        assertTrue("finds by domain", SourceSearch.rank(feeds, "abplive.com") { it }.isNotEmpty())
        val bbc = SourceSearch.rank(feeds, "bbc news") { it }
        assertTrue("BBC News ranks first for its own name", bbc.first().title.startsWith("BBC News"))
        assertTrue("a nonsense query finds nothing", SourceSearch.rank(feeds, "zzzzqqq") { it }.isEmpty())
    }
}
