package xyz.mdhv.riverwip.model

/**
 * The topic colour palette (brief §2/§8). ARGB (0xAARRGGBB), pure Ints so the
 * CVD guarantee is testable without Compose.
 *
 * Hard constraint: the primary user is red–green colourblind. No meaning rides on
 * red-vs-green — the palette is chosen to stay pairwise-distinguishable under both
 * protanopia and deuteranopia (unit-tested; the build fails if any pair
 * collapses), and every colour encoding in the UI is paired with a non-colour
 * channel (label, position, pattern). The scheme is in the Okabe–Ito /
 * Paul-Tol colourblind-safe lineage, spread across lightness and the blue–yellow
 * axis (the axis CVD preserves).
 *
 * NOTE: these are working values, re-skinnable with the final tokens. Behaviour
 * (and the CVD test) does not depend on the exact hues, only on their separation.
 */
object TopicPalette {

    // Paul Tol's "muted" qualitative scheme (colourblind-safe by construction) +
    // a pale grey for the catch-all. Assignment spreads related topics apart.
    val colors: Map<Topic, Int> = mapOf(
        Topic.POLITICS to 0xFFDDCC77.toInt(), // sand
        Topic.CONFLICT to 0xFF882255.toInt(), // wine
        Topic.BUSINESS to 0xFF88CCEE.toInt(), // cyan
        Topic.TECH to 0xFF332288.toInt(),     // indigo
        Topic.SCIENCE to 0xFF44AA99.toInt(),  // teal
        Topic.CLIMATE to 0xFF999933.toInt(),  // olive
        Topic.HEALTH to 0xFFCC6677.toInt(),   // rose
        Topic.CULTURE to 0xFFAA4499.toInt(),  // purple
        Topic.SPORT to 0xFF117733.toInt(),    // green
        Topic.OTHER to 0xFFD9D9D9.toInt(),    // light grey (catch-all; lightness set apart from teal)
    )

    fun colorFor(topic: Topic): Int = colors[topic] ?: 0xFF7F7F7F.toInt()
}
