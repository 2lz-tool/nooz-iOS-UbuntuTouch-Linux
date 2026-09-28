package xyz.mdhv.riverwip.model

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure geometry for the region-picker globe (owner's interactive reference,
 * 2026-07): a dotted orthographic earth the user spins to aim at a longitude
 * sector. Landmass outlines are the owner's own stylised, hand-authored
 * polygons — deliberately not survey-grade. All math here is pure so the dot
 * grid, sector resolution, and projection are unit-testable without Android;
 * the Compose layer only maps [Projected] points to pixels.
 */
object GlobeModel {

    /** Rough landmass outlines as [lon, lat] rings (ported from the owner's reference). */
    private val LAND: List<List<DoubleArray>> = listOf(
        listOf(
            doubleArrayOf(-165.0, 68.0), doubleArrayOf(-150.0, 71.0), doubleArrayOf(-125.0, 72.0), doubleArrayOf(-95.0, 72.0),
            doubleArrayOf(-80.0, 62.0), doubleArrayOf(-95.0, 50.0), doubleArrayOf(-80.0, 45.0), doubleArrayOf(-75.0, 35.0),
            doubleArrayOf(-82.0, 25.0), doubleArrayOf(-97.0, 18.0), doubleArrayOf(-105.0, 20.0), doubleArrayOf(-115.0, 30.0),
            doubleArrayOf(-124.0, 40.0), doubleArrayOf(-124.0, 49.0), doubleArrayOf(-130.0, 55.0), doubleArrayOf(-140.0, 60.0),
            doubleArrayOf(-165.0, 68.0),
        ),
        listOf(
            doubleArrayOf(-80.0, 10.0), doubleArrayOf(-75.0, 5.0), doubleArrayOf(-60.0, 5.0), doubleArrayOf(-50.0, 0.0),
            doubleArrayOf(-35.0, -5.0), doubleArrayOf(-35.0, -20.0), doubleArrayOf(-45.0, -30.0), doubleArrayOf(-58.0, -35.0),
            doubleArrayOf(-68.0, -45.0), doubleArrayOf(-73.0, -40.0), doubleArrayOf(-70.0, -20.0), doubleArrayOf(-75.0, -5.0),
            doubleArrayOf(-80.0, 10.0),
        ),
        listOf(
            doubleArrayOf(-55.0, 60.0), doubleArrayOf(-45.0, 65.0), doubleArrayOf(-25.0, 70.0), doubleArrayOf(-25.0, 83.0),
            doubleArrayOf(-55.0, 83.0), doubleArrayOf(-55.0, 60.0),
        ),
        listOf(
            doubleArrayOf(-10.0, 36.0), doubleArrayOf(-9.0, 44.0), doubleArrayOf(0.0, 50.0), doubleArrayOf(10.0, 54.0),
            doubleArrayOf(20.0, 55.0), doubleArrayOf(30.0, 60.0), doubleArrayOf(40.0, 65.0), doubleArrayOf(60.0, 68.0),
            doubleArrayOf(60.0, 50.0), doubleArrayOf(45.0, 42.0), doubleArrayOf(30.0, 40.0), doubleArrayOf(20.0, 38.0),
            doubleArrayOf(10.0, 38.0), doubleArrayOf(0.0, 38.0), doubleArrayOf(-10.0, 36.0),
        ),
        listOf(
            doubleArrayOf(-17.0, 15.0), doubleArrayOf(-15.0, 25.0), doubleArrayOf(-5.0, 35.0), doubleArrayOf(10.0, 37.0),
            doubleArrayOf(20.0, 32.0), doubleArrayOf(33.0, 31.0), doubleArrayOf(43.0, 12.0), doubleArrayOf(51.0, 12.0),
            doubleArrayOf(45.0, 0.0), doubleArrayOf(40.0, -15.0), doubleArrayOf(35.0, -25.0), doubleArrayOf(25.0, -34.0),
            doubleArrayOf(15.0, -30.0), doubleArrayOf(12.0, -18.0), doubleArrayOf(10.0, -5.0), doubleArrayOf(-5.0, 5.0),
            doubleArrayOf(-17.0, 15.0),
        ),
        listOf(
            doubleArrayOf(40.0, 68.0), doubleArrayOf(60.0, 70.0), doubleArrayOf(90.0, 72.0), doubleArrayOf(130.0, 72.0),
            doubleArrayOf(160.0, 68.0), doubleArrayOf(170.0, 65.0), doubleArrayOf(178.0, 60.0), doubleArrayOf(160.0, 55.0),
            doubleArrayOf(140.0, 45.0), doubleArrayOf(130.0, 35.0), doubleArrayOf(122.0, 32.0), doubleArrayOf(110.0, 20.0),
            doubleArrayOf(100.0, 10.0), doubleArrayOf(95.0, 5.0), doubleArrayOf(80.0, 8.0), doubleArrayOf(70.0, 15.0),
            doubleArrayOf(60.0, 25.0), doubleArrayOf(50.0, 30.0), doubleArrayOf(45.0, 38.0), doubleArrayOf(40.0, 45.0),
            doubleArrayOf(35.0, 50.0), doubleArrayOf(40.0, 68.0),
        ),
        listOf(
            doubleArrayOf(68.0, 24.0), doubleArrayOf(72.0, 20.0), doubleArrayOf(76.0, 10.0), doubleArrayOf(80.0, 8.0),
            doubleArrayOf(85.0, 10.0), doubleArrayOf(88.0, 22.0), doubleArrayOf(92.0, 26.0), doubleArrayOf(85.0, 28.0),
            doubleArrayOf(75.0, 30.0), doubleArrayOf(68.0, 24.0),
        ),
        listOf(
            doubleArrayOf(95.0, 20.0), doubleArrayOf(105.0, 22.0), doubleArrayOf(110.0, 15.0), doubleArrayOf(120.0, 15.0),
            doubleArrayOf(125.0, 10.0), doubleArrayOf(120.0, 0.0), doubleArrayOf(110.0, -5.0), doubleArrayOf(100.0, 0.0),
            doubleArrayOf(95.0, 10.0), doubleArrayOf(95.0, 20.0),
        ),
        listOf(
            doubleArrayOf(130.0, 32.0), doubleArrayOf(133.0, 35.0), doubleArrayOf(140.0, 38.0), doubleArrayOf(143.0, 43.0),
            doubleArrayOf(141.0, 45.0), doubleArrayOf(135.0, 38.0), doubleArrayOf(130.0, 32.0),
        ),
        listOf(
            doubleArrayOf(113.0, -22.0), doubleArrayOf(120.0, -18.0), doubleArrayOf(130.0, -12.0), doubleArrayOf(142.0, -11.0),
            doubleArrayOf(148.0, -20.0), doubleArrayOf(153.0, -28.0), doubleArrayOf(150.0, -35.0), doubleArrayOf(140.0, -38.0),
            doubleArrayOf(130.0, -32.0), doubleArrayOf(115.0, -32.0), doubleArrayOf(113.0, -22.0),
        ),
        listOf(
            doubleArrayOf(-8.0, 52.0), doubleArrayOf(-6.0, 55.0), doubleArrayOf(-3.0, 58.0), doubleArrayOf(0.0, 53.0),
            doubleArrayOf(-2.0, 50.0), doubleArrayOf(-6.0, 50.0), doubleArrayOf(-8.0, 52.0),
        ),
        listOf(
            doubleArrayOf(43.0, -12.0), doubleArrayOf(47.0, -14.0), doubleArrayOf(50.0, -22.0), doubleArrayOf(47.0, -25.0),
            doubleArrayOf(44.0, -20.0), doubleArrayOf(43.0, -12.0),
        ),
    )

    fun wrapLon(lon: Double): Double {
        var x = lon % 360.0
        if (x > 180.0) x -= 360.0
        if (x < -180.0) x += 360.0
        return x
    }

    private fun pointInPoly(x: Double, y: Double, ring: List<DoubleArray>): Boolean {
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val (xi, yi) = ring[i]
            val (xj, yj) = ring[j]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    fun isLand(lon: Double, lat: Double): Boolean = LAND.any { pointInPoly(lon, lat, it) }

    /** The 6°-grid landmass dots the globe is drawn from. */
    val dots: List<DoubleArray> by lazy {
        buildList {
            var lat = -84.0
            while (lat <= 84.0) {
                var lon = -180.0
                while (lon < 180.0) {
                    if (isLand(lon, lat)) add(doubleArrayOf(lon, lat))
                    lon += 6.0
                }
                lat += 6.0
            }
        }
    }

    /** A dot projected onto the unit sphere facing the viewer. */
    data class Projected(
        val x: Double,
        val y: Double,
        /** Great-circle distance (radians) from the view centre — 0 at centre, π/2 at the limb. */
        val distance: Double,
    )

    /**
     * Orthographic projection with the view rotated by [yawDeg]/[pitchDeg]
     * (matching the reference's d3 `rotate([yaw, pitch])`: the view centre sits
     * at lon = -yaw, lat = -pitch). Returns null for points on the far side.
     * Output is unit-sphere coordinates: x right, y down, each in [-1, 1].
     */
    fun project(lonDeg: Double, latDeg: Double, yawDeg: Double, pitchDeg: Double): Projected? {
        val lambda = Math.toRadians(lonDeg + yawDeg) // longitude relative to view centre
        val phi = Math.toRadians(latDeg)
        val phi0 = Math.toRadians(-pitchDeg)
        val cosC = sin(phi0) * sin(phi) + cos(phi0) * cos(phi) * cos(lambda)
        if (cosC <= 0.0) return null
        val x = cos(phi) * sin(lambda)
        val y = cos(phi0) * sin(phi) - sin(phi0) * cos(phi) * cos(lambda)
        return Projected(x, -y, acos(cosC.coerceIn(-1.0, 1.0)))
    }

    /** The longitude the view is centred on for a given yaw. */
    fun centerLongitude(yawDeg: Double): Double = wrapLon(-yawDeg)

    /** A dot is inside the selection band when its longitude is within ±[bandHalfDeg] of centre. */
    fun inBand(lonDeg: Double, yawDeg: Double, bandHalfDeg: Double): Boolean {
        if (bandHalfDeg >= GLOBAL_BAND_THRESHOLD) return true
        val dl = wrapLon(lonDeg - centerLongitude(yawDeg))
        return kotlin.math.abs(dl) <= bandHalfDeg
    }

    /** Widening the band to (nearly) the full sphere means Global. */
    const val GLOBAL_BAND_THRESHOLD = 170.0

    const val HALF_PI = PI / 2
}
