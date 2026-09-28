package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayInHistoryTest {

    private fun event(year: Int, text: String = "Something happened.") =
        HistoricalEvent(year = year, text = text, url = "https://en.wikipedia.org/wiki/X")

    // Wikipedia's real blurbs, verbatim from the live 08/12 feed.
    @Test
    fun `strips the aside pointing at an illustration this column never shows`() {
        assertEquals(
            "Japan Air Lines Flight 123 crashed into Mount Takamagahara in Japan.",
            TodayInHistory.clean("Japan Air Lines Flight 123 (aircraft involved pictured) crashed into Mount Takamagahara in Japan."),
        )
        assertEquals(
            "The last known quagga, a subspecies of the plains zebra, died.",
            TodayInHistory.clean("The last known quagga (example pictured), a subspecies of the plains zebra, died."),
        )
    }

    @Test
    fun `keeps parentheticals that are content rather than picture captions`() {
        val text = "The Kursk (an Oscar-class submarine) suffered an explosion."
        assertEquals(text, TodayInHistory.clean(text))
    }

    @Test
    fun `column reads oldest first`() {
        val picked = TodayInHistory.column(listOf(event(2021), event(1990), event(1099)), count = 5)
        assertEquals(listOf(1099, 1990, 2021), picked.map { it.year })
    }

    @Test
    fun `column spans the whole range rather than only the recent end`() {
        // The feed arrives newest-first and front-loads recent decades: taking
        // the first five here would yield 2020..2016 and nothing older.
        val feed = listOf(2020, 2019, 2018, 2017, 2016, 1980, 1900, 1750, 1500, 1099).map { event(it) }
        val picked = TodayInHistory.column(feed, count = 5)

        assertEquals(5, picked.size)
        assertEquals("oldest event should survive", 1099, picked.first().year)
        assertEquals("newest event should survive", 2020, picked.last().year)
        assertTrue("column should reach past living memory", picked.any { it.year < 1900 })
    }

    @Test
    fun `column is deterministic across repeated opens`() {
        val feed = (1..40).map { event(1000 + it * 25) }
        assertEquals(TodayInHistory.column(feed).map { it.year }, TodayInHistory.column(feed).map { it.year })
    }

    @Test
    fun `column never duplicates an event when the feed is short`() {
        val feed = listOf(event(2000), event(1900))
        val picked = TodayInHistory.column(feed, count = 5)
        assertEquals(2, picked.size)
        assertEquals(picked.map { it.year }.distinct(), picked.map { it.year })
    }

    @Test
    fun `degenerate inputs stay empty rather than throwing`() {
        assertEquals(emptyList<HistoricalEvent>(), TodayInHistory.column(emptyList()))
        assertEquals(emptyList<HistoricalEvent>(), TodayInHistory.column(listOf(event(2000)), count = 0))
        assertEquals(1, TodayInHistory.column(listOf(event(2000), event(1900)), count = 1).size)
    }
}
