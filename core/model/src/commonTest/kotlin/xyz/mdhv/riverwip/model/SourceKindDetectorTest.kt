package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceKindDetectorTest {
    @Test fun detectsKinds() {
        assertEquals(SourceKind.GOOGLE_NEWS, SourceKindDetector.detect("https://news.google.com/rss?hl=en-IN"))
        assertEquals(SourceKind.GDELT, SourceKindDetector.detect("https://api.gdeltproject.org/api/v2/doc/doc?query=x"))
        assertEquals(SourceKind.MASTODON, SourceKindDetector.detect("https://mastodon.social/api/v1/timelines/tag/news"))
        assertEquals(SourceKind.GUARDIAN, SourceKindDetector.detect("https://content.guardianapis.com/search?api-key=k"))
        assertEquals(SourceKind.RSS, SourceKindDetector.detect("https://feeds.bbci.co.uk/news/rss.xml"))
    }
}
