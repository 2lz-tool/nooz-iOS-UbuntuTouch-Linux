package xyz.mdhv.riverwip.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobeModelTest {

    @Test fun wrapLonNormalises() {
        assertEquals(0.0, GlobeModel.wrapLon(360.0), 1e-9)
        assertEquals(-170.0, GlobeModel.wrapLon(190.0), 1e-9)
        assertEquals(170.0, GlobeModel.wrapLon(-190.0), 1e-9)
    }

    @Test fun landHitsAndMisses() {
        assertTrue(GlobeModel.isLand(10.0, 45.0))   // Europe
        assertTrue(GlobeModel.isLand(78.0, 20.0))   // India
        assertFalse(GlobeModel.isLand(-40.0, 20.0)) // mid-Atlantic
        assertFalse(GlobeModel.isLand(170.0, -50.0)) // southern ocean
    }

    @Test fun dotGridIsNonTrivialAndStable() {
        val n = GlobeModel.dots.size
        assertTrue("dot grid should be substantial, was $n", n > 200)
        assertEquals(n, GlobeModel.dots.size) // lazy value stable
    }

    @Test fun projectionCentreAndFarSide() {
        // View centred on (0,0): that point projects to the origin with distance 0.
        val centre = GlobeModel.project(0.0, 0.0, 0.0, 0.0)!!
        assertEquals(0.0, centre.x, 1e-9)
        assertEquals(0.0, centre.y, 1e-9)
        assertEquals(0.0, centre.distance, 1e-9)
        // The antipode is invisible.
        assertNull(GlobeModel.project(180.0, 0.0, 0.0, 0.0))
    }

    @Test fun projectionYawBringsPointsIntoView() {
        // Point at lon=90 is on the limb for yaw 0, centred for yaw -90.
        val centred = GlobeModel.project(90.0, 0.0, -90.0, 0.0)!!
        assertEquals(0.0, centred.distance, 1e-9)
        assertEquals(90.0, GlobeModel.centerLongitude(-90.0), 1e-9)
    }

    @Test fun bandMembershipAndGlobalThreshold() {
        assertTrue(GlobeModel.inBand(10.0, 0.0, 16.0))
        assertFalse(GlobeModel.inBand(30.0, 0.0, 16.0))
        assertTrue(GlobeModel.inBand(179.0, 0.0, GlobeModel.GLOBAL_BAND_THRESHOLD))
    }

    @Test fun regionForLongitudeCoversTheCircle() {
        assertEquals(Region.AMERICAS, Region.forLongitude(-100.0))
        assertEquals(Region.EUROPE_AFRICA, Region.forLongitude(10.0))
        assertEquals(Region.MIDEAST_CASIA, Region.forLongitude(50.0))
        assertEquals(Region.SOUTH_ASIA, Region.forLongitude(78.0))
        assertEquals(Region.EAST_SE_ASIA, Region.forLongitude(120.0))
        assertEquals(Region.AUSTRALIA_PACIFIC, Region.forLongitude(150.0))
        assertEquals(Region.AUSTRALIA_PACIFIC, Region.forLongitude(-175.0)) // antimeridian wrap
    }

    @Test fun regionForBandIsSingleSectorWhenNarrow() {
        // A narrow band well inside one sector's span touches only that sector.
        assertEquals(listOf(Region.EUROPE_AFRICA), Region.forBand(10.0, 8.0))
    }

    @Test fun regionForBandSpansAdjacentSectorsWhenWidened() {
        // Centred right on the Europe/Africa <-> Mideast-C.Asia seam (45deg):
        // a wide-enough band touches both, in first-hit (left-to-right) order.
        val hit = Region.forBand(45.0, 20.0)
        assertEquals(listOf(Region.EUROPE_AFRICA, Region.MIDEAST_CASIA), hit)
    }

    @Test fun regionForBandAtGlobalThresholdIsGlobalOnly() {
        assertEquals(listOf(Region.GLOBAL), Region.forBand(0.0, GlobeModel.GLOBAL_BAND_THRESHOLD))
    }

    @Test fun sourceTagMapping() {
        assertEquals(Region.GLOBAL, Region.forSourceTag("global"))
        assertEquals(Region.GLOBAL, Region.forSourceTag(null))
        assertEquals(Region.SOUTH_ASIA, Region.forSourceTag("india"))
        assertEquals(Region.EAST_SE_ASIA, Region.forSourceTag("east-se-asia"))
    }

    @Test fun filterRules() {
        val global = ReaderFilter()
        assertTrue(global.includesSource("india"))
        assertTrue(global.matchesTopic(Topic.SPORT))
        val southAsiaPolitics = ReaderFilter(Region.SOUTH_ASIA, setOf(Topic.POLITICS.key))
        assertTrue(southAsiaPolitics.includesSource("india"))
        assertTrue(southAsiaPolitics.includesSource("global")) // world feeds cover every sector
        assertFalse(southAsiaPolitics.matchesTopic(Topic.SPORT))
        assertTrue(southAsiaPolitics.matchesTopic(Topic.POLITICS))
        assertEquals("Global | All", global.summary())
    }

    @Test fun themeKeysMapToTints() {
        assertEquals(ThemeMode.WHITE, ThemeMode.fromKey("white"))
        assertEquals(ThemeMode.PAPER, ThemeMode.fromKey("paper"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromKey("dark"))
        assertEquals(ThemeMode.PAPER, ThemeMode.fromKey(null))
        // "light" stays a paper-toned legacy alias; "system" is no longer one —
        // since D34 it means what it always said, so a reader who picked it in
        // the first theme iteration gets the follow-the-phone behaviour back.
        assertEquals(ThemeMode.PAPER, ThemeMode.fromKey("light"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey("system"))
    }

    @Test fun themeFlickStillCyclesTheThreeLiteralTints() {
        assertEquals(ThemeMode.PAPER, ThemeMode.WHITE.next().next().next().next()) // cycle of 3
        assertEquals(ThemeMode.DARK, ThemeMode.PAPER.next())
        // Flicking off SYSTEM steps onto an explicit tint that differs from
        // whatever is currently on screen, rather than handing the tint back.
        assertEquals(ThemeMode.WHITE, ThemeMode.SYSTEM.next(systemDark = true))
        assertEquals(ThemeMode.DARK, ThemeMode.SYSTEM.next(systemDark = false))
        // ...and the flick never lands back on SYSTEM from any tint.
        for (mode in ThemeMode.entries) {
            assertTrue("flick from $mode", mode.next(systemDark = true) != ThemeMode.SYSTEM)
            assertTrue("flick from $mode", mode.next(systemDark = false) != ThemeMode.SYSTEM)
        }
    }

    @Test fun systemThemeResolvesToARealTintAndDrivesBarContrast() {
        assertEquals(ThemeMode.DARK, ThemeMode.SYSTEM.resolve(systemDark = true))
        assertEquals(ThemeMode.PAPER, ThemeMode.SYSTEM.resolve(systemDark = false))
        // A hand-picked tint ignores the phone entirely — that is the point of
        // picking one, and the reason bar contrast must follow the resolved
        // tint rather than Configuration.UI_MODE_NIGHT (the S26+ report).
        assertEquals(ThemeMode.WHITE, ThemeMode.WHITE.resolve(systemDark = true))
        assertEquals(ThemeMode.DARK, ThemeMode.DARK.resolve(systemDark = false))
        // SYSTEM must never survive resolution, or a colour scheme lookup falls
        // through to a default and the app paints a tint nobody chose.
        for (systemDark in listOf(true, false)) {
            for (mode in ThemeMode.entries) {
                assertTrue("$mode resolved", mode.resolve(systemDark) != ThemeMode.SYSTEM)
            }
        }
        assertTrue(ThemeMode.SYSTEM.isDarkSurface(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDarkSurface(systemDark = false))
        assertFalse(ThemeMode.WHITE.isDarkSurface(systemDark = true))
        assertTrue(ThemeMode.DARK.isDarkSurface(systemDark = false))
    }
}
