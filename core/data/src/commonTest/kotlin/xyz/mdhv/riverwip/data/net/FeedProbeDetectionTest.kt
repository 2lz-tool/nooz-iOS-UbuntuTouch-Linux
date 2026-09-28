package xyz.mdhv.riverwip.data.net

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

/** Pure detection helpers — run on the JVM in CI, no device needed. */
class FeedProbeDetectionTest {

    @Test fun detectsRssAtomRdfJson() {
        assertEquals("rss", FeedProbe.detectFeedFormat("<?xml version=\"1.0\"?><rss version=\"2.0\"><channel>", "application/xml"))
        assertEquals("atom", FeedProbe.detectFeedFormat("<feed xmlns=\"http://www.w3.org/2005/Atom\">", "application/atom+xml"))
        assertEquals("rdf", FeedProbe.detectFeedFormat("<rdf:RDF><item rdf:about=\"x\">", "application/xml"))
        assertEquals("json", FeedProbe.detectFeedFormat("[{\"id\":\"1\",\"content\":\"hi\"}]", "application/json"))
        assertNull(FeedProbe.detectFeedFormat("<html><body>hi</body></html>", "text/html"))
    }

    @Test fun countsItemsThenEntries() {
        assertEquals(3, FeedProbe.countItems("<item>a</item><item>b</item><item >c</item>"))
        assertEquals(2, FeedProbe.countItems("<feed><entry>a</entry><entry>b</entry></feed>"))
        assertEquals(0, FeedProbe.countItems("<html></html>"))
    }

    @Test fun extractsChannelTitleIncludingCdata() {
        assertEquals("BBC News", FeedProbe.extractFeedTitle("<rss><channel><title>BBC News</title><item>"))
        assertEquals("The Hindu", FeedProbe.extractFeedTitle("<channel><title><![CDATA[The Hindu]]></title>"))
    }

    @Test fun looksHtmlDetectsPages() {
        assertTrue(FeedProbe.looksHtml("<!doctype html><html>", null))
        assertTrue(FeedProbe.looksHtml("x", "text/html; charset=utf-8"))
    }
}
