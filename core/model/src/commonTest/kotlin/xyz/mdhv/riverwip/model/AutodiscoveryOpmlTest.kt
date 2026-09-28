package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutodiscoveryOpmlTest {

    @Test fun discoversRssAndAtomAndResolvesRelative() {
        val html = """
            <html><head>
              <link rel="alternate" type="application/rss+xml" title="RSS" href="/feed.xml">
              <link rel='alternate' type='application/atom+xml' href='https://cdn.ex.com/atom'>
              <link rel="stylesheet" href="/style.css">
            </head></html>
        """.trimIndent()
        val feeds = FeedAutodiscovery.discoverFromHtml("https://ex.com/blog/post", html)
        assertEquals(2, feeds.size)
        assertEquals("https://ex.com/feed.xml", feeds[0].url)
        assertEquals(FeedAutodiscovery.FeedType.RSS, feeds[0].type)
        assertEquals("RSS", feeds[0].title)
        assertEquals("https://cdn.ex.com/atom", feeds[1].url)
        assertEquals(FeedAutodiscovery.FeedType.ATOM, feeds[1].type)
    }

    @Test fun ignoresNonAlternateLinks() {
        val html = """<link rel="icon" href="/favicon.ico" type="image/x-icon">"""
        assertTrue(FeedAutodiscovery.discoverFromHtml("https://ex.com", html).isEmpty())
    }

    @Test fun looksLikeFeedHeuristic() {
        assertTrue(FeedAutodiscovery.looksLikeFeed("https://ex.com/feed.xml"))
        assertTrue(FeedAutodiscovery.looksLikeFeed("https://mastodon.social/api/v1/timelines/tag/news"))
        assertFalse(FeedAutodiscovery.looksLikeFeed("https://ex.com/about"))
    }

    @Test fun guessPathsRootedAtSite() {
        val guesses = FeedAutodiscovery.guessFeedPaths("https://ex.com/some/page")
        assertTrue(guesses.contains("https://ex.com/feed"))
        assertTrue(guesses.contains("https://ex.com/rss.xml"))
    }

    @Test fun opmlRoundTrip() {
        val sources = listOf(
            Source(Ids.sourceId(SourceKind.RSS, "https://a.com/rss"), SourceKind.RSS, "https://a.com/rss", "A & B News", Tier.USER, true, 1000L),
            Source(Ids.sourceId(SourceKind.RSS, "https://b.com/feed"), SourceKind.RSS, "https://b.com/feed", "B <News>", Tier.USER, true, 1000L),
        )
        val xml = Opml.export(sources)
        val parsed = Opml.parseToSources(xml, addedAt = 2000L)
        assertEquals(2, parsed.size)
        assertEquals("https://a.com/rss", parsed[0].url)
        assertEquals("A & B News", parsed[0].title) // entity round-trips
        assertEquals("B <News>", parsed[1].title)
    }

    @Test fun opmlParsesExternalWithTextAttrFallback() {
        val xml = """
            <opml version="2.0"><body>
              <outline text="Only Text" type="rss" xmlUrl="https://c.com/rss"/>
              <outline title="No Url"/>
            </body></opml>
        """.trimIndent()
        val outlines = Opml.parse(xml)
        assertEquals(1, outlines.size) // the one without xmlUrl is skipped
        assertEquals("Only Text", outlines[0].title)
        assertEquals("https://c.com/rss", outlines[0].xmlUrl)
    }
}
