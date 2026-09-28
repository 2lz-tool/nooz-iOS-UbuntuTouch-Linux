package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedParserTest {

    private val rss = """
        <?xml version="1.0"?>
        <rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/">
          <channel>
            <title>Example News</title>
            <item>
              <title>Parliament passes climate bill</title>
              <link>https://ex.com/a?utm_source=rss</link>
              <pubDate>Wed, 02 Oct 2024 13:00:00 GMT</pubDate>
              <dc:creator>A. Reporter</dc:creator>
              <category>Politics</category>
              <description>&lt;p&gt;The bill on &lt;b&gt;climate&lt;/b&gt; passed.&lt;/p&gt;</description>
            </item>
            <item>
              <title>Cricket team names World Cup squad</title>
              <link>https://ex.com/b</link>
              <category>Sports</category>
            </item>
          </channel>
        </rss>
    """.trimIndent()

    private val atom = """
        <?xml version="1.0"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>Atom Source</title>
          <entry>
            <title>Central bank holds interest rates</title>
            <link rel="alternate" href="https://ex.com/c"/>
            <published>2024-10-02T13:00:00Z</published>
            <author><name>B. Writer</name></author>
            <category term="Business"/>
            <summary>Markets react to the decision.</summary>
          </entry>
        </feed>
    """.trimIndent()

    private val rdf = """
        <?xml version="1.0"?>
        <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                 xmlns="http://purl.org/rss/1.0/" xmlns:dc="http://purl.org/dc/elements/1.1/">
          <channel><title>DW</title></channel>
          <item rdf:about="https://ex.com/d">
            <title>Scientists map a new galaxy</title>
            <link>https://ex.com/d</link>
            <dc:date>2024-10-02T09:30:00Z</dc:date>
          </item>
        </rdf:RDF>
    """.trimIndent()

    private val mastodon = """
        [
          {"id":"1","url":"https://m.social/@x/1","content":"<p>Breaking: a health outbreak reported.</p>",
           "created_at":"2024-10-02T13:00:00.000Z","account":{"acct":"x@m.social"},"tags":[{"name":"health"}]}
        ]
    """.trimIndent()

    private val gdelt = """
        {"articles":[{"url":"https://ex.com/g","title":"Flood displaces thousands","domain":"ex.com","seendate":"20241002T130000Z"}]}
    """.trimIndent()

    @Test fun parsesRssWithCategoriesDatesAndStrippedHtml() {
        val feed = FeedParser.parse(rss)
        assertEquals("Example News", feed.title)
        assertEquals(2, feed.items.size)
        val a = feed.items[0]
        assertEquals("Parliament passes climate bill", a.title)
        assertEquals("https://ex.com/a?utm_source=rss", a.link)
        assertEquals("A. Reporter", a.author)
        assertEquals(listOf("Politics"), a.categories)
        assertNotNull(a.publishedAtMillis)
        assertEquals("The bill on climate passed.", a.summary)
    }

    @Test fun parsesAtomAlternateLink() {
        val feed = FeedParser.parse(atom)
        assertEquals(1, feed.items.size)
        assertEquals("https://ex.com/c", feed.items[0].link)
        assertEquals("B. Writer", feed.items[0].author)
        assertEquals(listOf("Business"), feed.items[0].categories)
    }

    @Test fun parsesRdfItems() {
        val feed = FeedParser.parse(rdf)
        assertEquals(1, feed.items.size)
        assertEquals("Scientists map a new galaxy", feed.items[0].title)
        assertNotNull(feed.items[0].publishedAtMillis)
    }

    @Test fun parsesMastodonJsonArray() {
        val feed = FeedParser.parse(mastodon, contentType = "application/json")
        assertEquals(1, feed.items.size)
        assertEquals("https://m.social/@x/1", feed.items[0].link)
        assertTrue(feed.items[0].summary!!.contains("health outbreak"))
        assertEquals(listOf("health"), feed.items[0].categories)
    }

    @Test fun parsesGdeltJson() {
        val feed = FeedParser.parse(gdelt)
        assertEquals(1, feed.items.size)
        assertEquals("Flood displaces thousands", feed.items[0].title)
        assertNotNull(feed.items[0].publishedAtMillis)
    }

    @Test fun malformedInputYieldsEmptyFeedNotCrash() {
        assertTrue(FeedParser.parse("<not xml").items.isEmpty())
        assertTrue(FeedParser.parse("{bad json", "application/json").items.isEmpty())
        assertTrue(FeedParser.parse("").items.isEmpty())
    }

    @Test fun dateParsingHandlesRfc822AndIso() {
        assertNotNull(FeedParser.parseDate("Wed, 02 Oct 2024 13:00:00 GMT"))
        assertNotNull(FeedParser.parseDate("2024-10-02T13:00:00Z"))
        assertNotNull(FeedParser.parseDate("2024-10-02T13:00:00+05:30"))
        assertEquals(null, FeedParser.parseDate("not a date"))
        assertEquals(null, FeedParser.parseDate(null))
    }

    @Test fun ingestClassifiesAndAttachesEvidenceAndDedups() {
        val src = Source(Ids.sourceId(SourceKind.RSS, "https://ex.com/rss"), SourceKind.RSS, "https://ex.com/rss", "Ex", Tier.USER, true, 0L)
        val items = Ingest.ingest(src, rss, "application/rss+xml", fetchedAt = 1000L)
        assertEquals(2, items.size)
        val politics = items.first { it.title.contains("Parliament") }
        // Topic evidence is attached and traceable (brief §2/§3).
        assertTrue(politics.topics.any { it.topic == Topic.POLITICS })
        assertTrue(politics.simhash != 0L)
        assertEquals("https://ex.com/a", politics.canonicalUrl) // tracking param stripped
    }

    @Test fun ingestDedupsSyndicatedCopies() {
        val src = Source("s", SourceKind.RSS, "u", "Ex", Tier.USER, true, 0L)
        // Verbatim wire reprint across two sources (different URLs, same headline).
        val body = """
            <rss version="2.0"><channel><title>t</title>
              <item><title>Government unveils new climate policy for the coming year</title><link>https://a.com/x</link></item>
              <item><title>Government unveils new climate policy for the coming year</title><link>https://b.com/y</link></item>
            </channel></rss>
        """.trimIndent()
        val items = Ingest.ingest(src, body, "application/rss+xml", 0L)
        assertEquals(1, items.size) // identical syndicated headlines collapse
    }

    @Test fun htmlStripAndUnescape() {
        assertEquals("A & B < C", Html.strip("<p>A &amp; B &lt; C</p>"))
        assertEquals("café — résumé", Html.strip("caf&#233; &mdash; r&#xe9;sum&#233;"))
    }

    @Test fun rssEnclosureImageTakesPriority() {
        val body = """
            <rss version="2.0"><channel><title>t</title>
              <item>
                <title>a</title><link>https://ex.com/a</link>
                <enclosure url="https://ex.com/a.jpg" type="image/jpeg" length="1234"/>
                <description>&lt;img src="https://ex.com/fallback.jpg"/&gt;</description>
              </item>
            </channel></rss>
        """.trimIndent()
        assertEquals("https://ex.com/a.jpg", FeedParser.parse(body).items[0].imageUrl)
    }

    @Test fun rssMediaThumbnailAndMediaContentImage() {
        val thumb = """
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/"><channel><title>t</title>
              <item><title>a</title><link>https://ex.com/a</link>
                <media:thumbnail url="https://ex.com/thumb.jpg"/>
              </item>
            </channel></rss>
        """.trimIndent()
        assertEquals("https://ex.com/thumb.jpg", FeedParser.parse(thumb).items[0].imageUrl)

        val content = """
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/"><channel><title>t</title>
              <item><title>a</title><link>https://ex.com/a</link>
                <media:content url="https://ex.com/vid.mp4" medium="video"/>
                <media:content url="https://ex.com/content.jpg" medium="image"/>
              </item>
            </channel></rss>
        """.trimIndent()
        // A video-medium media:content must never be picked up as an image.
        assertEquals("https://ex.com/content.jpg", FeedParser.parse(content).items[0].imageUrl)
    }

    @Test fun rssFallsBackToFirstImgInDescriptionWhenNoStructuredImage() {
        val body = """
            <rss version="2.0"><channel><title>t</title>
              <item><title>a</title><link>https://ex.com/a</link>
                <description>&lt;p&gt;&lt;img src="https://ex.com/inline.jpg" alt="x"/&gt; caption&lt;/p&gt;</description>
              </item>
            </channel></rss>
        """.trimIndent()
        assertEquals("https://ex.com/inline.jpg", FeedParser.parse(body).items[0].imageUrl)
    }

    @Test fun rssItemWithNoImageAnywhereYieldsNull() {
        val body = """
            <rss version="2.0"><channel><title>t</title>
              <item><title>a</title><link>https://ex.com/a</link><description>Plain text, no image.</description></item>
            </channel></rss>
        """.trimIndent()
        assertEquals(null, FeedParser.parse(body).items[0].imageUrl)
    }

    @Test fun atomEnclosureImageLink() {
        val body = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>t</title>
              <entry>
                <title>a</title>
                <link rel="alternate" href="https://ex.com/a"/>
                <link rel="enclosure" type="image/png" href="https://ex.com/a.png"/>
                <summary>x</summary>
              </entry>
            </feed>
        """.trimIndent()
        assertEquals("https://ex.com/a.png", FeedParser.parse(body).items[0].imageUrl)
    }

    @Test fun rssMediaRatingAdultIsDeclaredNsfw() {
        val body = """
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/"><channel><title>t</title>
              <item><title>a</title><link>https://ex.com/a</link>
                <media:rating>adult</media:rating>
              </item>
            </channel></rss>
        """.trimIndent()
        assertTrue(FeedParser.parse(body).items[0].declaredNsfw)
    }

    @Test fun rssItunesExplicitTrueIsDeclaredNsfw() {
        val body = """
            <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"><channel><title>t</title>
              <item><title>a</title><link>https://ex.com/a</link>
                <itunes:explicit>true</itunes:explicit>
              </item>
            </channel></rss>
        """.trimIndent()
        assertTrue(FeedParser.parse(body).items[0].declaredNsfw)
    }

    @Test fun rssWithNoRatingOrExplicitTagIsNotDeclaredNsfw() {
        val body = """
            <rss version="2.0"><channel><title>t</title>
              <item><title>a</title><link>https://ex.com/a</link></item>
            </channel></rss>
        """.trimIndent()
        assertFalse(FeedParser.parse(body).items[0].declaredNsfw)
    }

    @Test fun rssMediaRatingNonadultAndItunesExplicitFalseAreNotDeclaredNsfw() {
        val body = """
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
              <channel><title>t</title>
                <item><title>a</title><link>https://ex.com/a</link>
                  <media:rating>nonadult</media:rating>
                  <itunes:explicit>false</itunes:explicit>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        assertFalse(FeedParser.parse(body).items[0].declaredNsfw)
    }

    @Test fun atomFallsBackToImgInSummaryHtml() {
        val body = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>t</title>
              <entry>
                <title>a</title>
                <link rel="alternate" href="https://ex.com/a"/>
                <summary>&lt;img src="https://ex.com/atom-inline.jpg"/&gt;</summary>
              </entry>
            </feed>
        """.trimIndent()
        assertEquals("https://ex.com/atom-inline.jpg", FeedParser.parse(body).items[0].imageUrl)
    }
}
