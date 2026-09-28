package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedUrlsTest {

    @Test fun googleNewsLocaleTriple() {
        val loc = FeedUrls.GNLocale.of("en", "IN")
        assertEquals("en-IN", loc.hl)
        assertEquals("IN", loc.gl)
        assertEquals("IN:en", loc.ceid)
    }

    @Test fun googleNewsTopUsesLocale() {
        val url = FeedUrls.googleNewsTop(FeedUrls.GNLocale.US_EN)
        assertEquals("https://news.google.com/rss?hl=en-US&gl=US&ceid=US%3Aen", url)
    }

    @Test fun googleNewsSearchEncodesQuery() {
        val url = FeedUrls.googleNewsSearch("climate policy", FeedUrls.GNLocale.INDIA_EN)
        assertTrue(url.startsWith("https://news.google.com/rss/search?q=climate%20policy&"))
        assertTrue(url.contains("ceid=IN%3Aen"))
    }

    @Test fun googleNewsSectionUsesTopicName() {
        val url = FeedUrls.googleNewsSection(FeedUrls.GNTopic.TECHNOLOGY, FeedUrls.GNLocale.UK_EN)
        assertTrue(url.contains("/section/topic/TECHNOLOGY?"))
        assertTrue(url.contains("gl=GB"))
    }

    @Test fun gdeltClampsAndFormats() {
        val url = FeedUrls.gdeltDoc(FeedUrls.GdeltQuery(query = "flood india", maxRecords = 999, timespanHours = 48))
        assertTrue(url.contains("query=flood%20india"))
        assertTrue(url.contains("mode=artlist"))
        assertTrue(url.contains("format=json"))
        assertTrue(url.contains("maxrecords=250")) // clamped from 999
        assertTrue(url.contains("timespan=48h"))
        assertTrue(url.contains("sort=datedesc"))
    }

    @Test fun mastodonPublicAndTag() {
        val pub = FeedUrls.mastodonPublic("https://mastodon.social/", limit = 100, localOnly = true)
        assertEquals("https://mastodon.social/api/v1/timelines/public?limit=40&local=true", pub)
        val tag = FeedUrls.mastodonTag("mastodon.social", "#News", limit = 20)
        assertEquals("https://mastodon.social/api/v1/timelines/tag/News?limit=20", tag)
    }

    @Test fun gdeltDateTimeFormatsUtcNoSeparators() {
        val millis = java.time.Instant.parse("2026-07-15T00:00:00Z").toEpochMilli()
        assertEquals("20260715000000", FeedUrls.gdeltDateTime(millis))
        val withTime = java.time.Instant.parse("2026-01-05T07:08:09Z").toEpochMilli()
        assertEquals("20260105070809", FeedUrls.gdeltDateTime(withTime))
    }

    @Test fun gdeltDocForRangeSwapsTimespanForAbsoluteWindowAndKeepsOtherParams() {
        val existing = FeedUrls.gdeltDoc(FeedUrls.GdeltQuery(query = "flood india", maxRecords = 75, timespanHours = 24))
        val start = java.time.Instant.parse("2026-07-15T00:00:00Z").toEpochMilli()
        val end = java.time.Instant.parse("2026-07-16T00:00:00Z").toEpochMilli()
        val rewritten = FeedUrls.gdeltDocForRange(existing, start, end)
        assertTrue(rewritten != null)
        assertTrue(rewritten!!.contains("query=flood%20india"))
        assertTrue(rewritten.contains("mode=artlist"))
        assertTrue(rewritten.contains("format=json"))
        assertTrue(rewritten.contains("maxrecords=75"))
        assertTrue(rewritten.contains("sort=datedesc"))
        assertTrue(rewritten.contains("startdatetime=20260715000000"))
        assertTrue(rewritten.contains("enddatetime=20260716000000"))
        assertTrue(!rewritten.contains("timespan="))
    }

    @Test fun gdeltDocForRangePreservesHandTypedParamsVerbatim() {
        val existing = "https://api.gdeltproject.org/api/v2/doc/doc?query=climate&mode=artlist&format=json&maxrecords=75&timespan=24h&sourcelang=english"
        val rewritten = FeedUrls.gdeltDocForRange(existing, 0L, 86_400_000L)
        assertTrue(rewritten != null)
        assertTrue(rewritten!!.contains("sourcelang=english"))
        assertTrue(rewritten.contains("query=climate"))
        assertTrue(!rewritten.contains("timespan="))
    }

    @Test fun gdeltDocForRangeRejectsNonGdeltUrl() {
        assertEquals(null, FeedUrls.gdeltDocForRange("https://example.com/rss?query=x", 0L, 1L))
    }

    @Test fun gdeltDocForRangeRejectsUrlWithoutQueryString() {
        assertEquals(null, FeedUrls.gdeltDocForRange("https://api.gdeltproject.org/api/v2/doc/doc", 0L, 1L))
    }
}
