package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayLoomLayoutTest {

    private fun keys(vararg pairs: Pair<Topic, Int>) = pairs.associate { it.first.key to it.second }

    @Test fun emptyDayIsEmptyLoom() {
        val loom = DayLoomLayout.layout(emptyMap(), emptyMap())
        assertTrue(loom.bands.isEmpty())
        assertEquals(0, loom.totalFlowed)
    }

    @Test fun topWidthsAreProportionalToSupply() {
        val loom = DayLoomLayout.layout(
            keys(Topic.POLITICS to 300, Topic.SPORT to 100),
            emptyMap(),
        )
        val politics = loom.bands.first { it.topic == Topic.POLITICS }
        val sport = loom.bands.first { it.topic == Topic.SPORT }
        assertEquals(politics.stations[0].w, sport.stations[0].w * 3, 1e-6)
    }

    @Test fun unreadBandsPinchOutAtTheWaist() {
        val loom = DayLoomLayout.layout(keys(Topic.TECH to 100), emptyMap())
        val band = loom.bands.single()
        assertEquals(2, band.stations.size)
        assertEquals(DayLoomLayout.WAIST_Y, band.stations.last().y, 1e-9)
        assertTrue(band.stations.last().w < 1.0)
    }

    @Test fun consumedBandsReachTheBottomWithReadShareWidths() {
        val loom = DayLoomLayout.layout(
            keys(Topic.POLITICS to 100, Topic.CLIMATE to 100),
            keys(Topic.POLITICS to 3, Topic.CLIMATE to 1),
        )
        val politics = loom.bands.first { it.topic == Topic.POLITICS }
        val climate = loom.bands.first { it.topic == Topic.CLIMATE }
        assertEquals(3, politics.stations.size)
        assertEquals(DayLoomLayout.H, politics.stations.last().y, 1e-9)
        assertEquals(politics.stations.last().w, climate.stations.last().w * 3, 1e-6)
        assertEquals(4, loom.totalRead)
    }

    @Test fun drawOrderPutsLargestConsumedLast() {
        val loom = DayLoomLayout.layout(
            keys(Topic.POLITICS to 100, Topic.CLIMATE to 100, Topic.SPORT to 100),
            keys(Topic.POLITICS to 5, Topic.CLIMATE to 1),
        )
        assertEquals(Topic.SPORT, loom.bands.first().topic) // unread drawn first
        assertEquals(Topic.POLITICS, loom.bands.last().topic) // biggest read on top
    }

    @Test fun readNeverExceedsFlowed() {
        val loom = DayLoomLayout.layout(keys(Topic.TECH to 2), keys(Topic.TECH to 99))
        assertEquals(2, loom.totalRead)
    }

    @Test fun dayMixFractionsSumToOne() {
        val mix = DayLoomLayout.dayMix(keys(Topic.POLITICS to 30, Topic.SPORT to 10))
        assertEquals(1.0, mix.sumOf { it.second }, 1e-9)
        assertEquals(0.75, mix.first { it.first == Topic.POLITICS }.second, 1e-9)
        assertTrue(DayLoomLayout.dayMix(emptyMap()).isEmpty())
    }

    @Test fun compactCounts() {
        assertEquals("950", formatCompactCount(950))
        assertEquals("10k", formatCompactCount(10_000))
        assertEquals("12.3k", formatCompactCount(12_345))
        assertEquals("2.5M", formatCompactCount(2_500_000))
    }
}
