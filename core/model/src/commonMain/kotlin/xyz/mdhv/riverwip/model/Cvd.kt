package xyz.mdhv.riverwip.model

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Colour-vision-deficiency simulation and perceptual distance (brief §2/§8). Pure
 * math so the palette guarantee is unit-tested and **fails the build if
 * violated** — the primary user is red–green colourblind, so no topic pair may
 * collapse under protanopia or deuteranopia.
 *
 * Simulation uses the Machado et al. (2009) severity-1.0 matrices applied in
 * linear-light RGB; distance is CIE76 ΔE in CIELAB (D65). ARGB is packed 0xAARRGGBB.
 */
object Cvd {

    enum class Type { PROTANOPIA, DEUTERANOPIA, NORMAL }

    // Machado et al. 2009, severity 1.0 (row-major 3x3), applied to linear RGB.
    private val PROTAN = doubleArrayOf(
        0.152286, 1.052583, -0.204868,
        0.114503, 0.786281, 0.099216,
        -0.003882, -0.048116, 1.051998,
    )
    private val DEUTAN = doubleArrayOf(
        0.367322, 0.860646, -0.227968,
        0.280085, 0.672501, 0.047413,
        -0.011820, 0.042940, 0.968881,
    )

    fun r(argb: Int) = (argb ushr 16) and 0xFF
    fun g(argb: Int) = (argb ushr 8) and 0xFF
    fun b(argb: Int) = argb and 0xFF
    fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun srgbToLinear(c: Double): Double =
        if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun linearToSrgb(c: Double): Double =
        if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1.0 / 2.4) - 0.055

    /** Simulate how [argb] appears under [type]. NORMAL returns it unchanged. */
    fun simulate(argb: Int, type: Type): Int {
        if (type == Type.NORMAL) return argb
        val m = if (type == Type.PROTANOPIA) PROTAN else DEUTAN
        val rl = srgbToLinear(r(argb) / 255.0)
        val gl = srgbToLinear(g(argb) / 255.0)
        val bl = srgbToLinear(b(argb) / 255.0)
        val r2 = (m[0] * rl + m[1] * gl + m[2] * bl).coerceIn(0.0, 1.0)
        val g2 = (m[3] * rl + m[4] * gl + m[5] * bl).coerceIn(0.0, 1.0)
        val b2 = (m[6] * rl + m[7] * gl + m[8] * bl).coerceIn(0.0, 1.0)
        return argb(
            (linearToSrgb(r2) * 255.0).toInt().coerceIn(0, 255),
            (linearToSrgb(g2) * 255.0).toInt().coerceIn(0, 255),
            (linearToSrgb(b2) * 255.0).toInt().coerceIn(0, 255),
        )
    }

    // ---- CIELAB (D65) + CIE76 ΔE ----

    private fun pivotXyz(t: Double): Double = if (t > 0.008856) t.pow(1.0 / 3.0) else (7.787 * t) + (16.0 / 116.0)

    fun toLab(argb: Int): DoubleArray {
        val rl = srgbToLinear(r(argb) / 255.0)
        val gl = srgbToLinear(g(argb) / 255.0)
        val bl = srgbToLinear(b(argb) / 255.0)
        // linear sRGB → XYZ (D65)
        var x = rl * 0.4124 + gl * 0.3576 + bl * 0.1805
        var y = rl * 0.2126 + gl * 0.7152 + bl * 0.0722
        var z = rl * 0.0193 + gl * 0.1192 + bl * 0.9505
        x /= 0.95047; y /= 1.0; z /= 1.08883
        val fx = pivotXyz(x); val fy = pivotXyz(y); val fz = pivotXyz(z)
        return doubleArrayOf(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz))
    }

    /** CIE76 ΔE between two colours in Lab. */
    fun deltaE(a: Int, b: Int): Double {
        val la = toLab(a); val lb = toLab(b)
        val dl = la[0] - lb[0]; val da = la[1] - lb[1]; val db = la[2] - lb[2]
        return sqrt(dl * dl + da * da + db * db)
    }

    /** ΔE as it would appear to a viewer with [type]. */
    fun deltaEUnder(a: Int, b: Int, type: Type): Double =
        deltaE(simulate(a, type), simulate(b, type))
}
