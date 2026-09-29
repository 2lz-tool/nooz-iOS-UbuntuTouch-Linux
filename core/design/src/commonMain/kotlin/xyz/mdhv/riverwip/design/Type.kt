package xyz.mdhv.riverwip.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.resources.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import xyz.mdhv.riverwip.design.res.*
import xyz.mdhv.riverwip.model.ReaderFont
import xyz.mdhv.riverwip.model.TextScale

/**
 * Type scale. Sizes/weights mirror Hyle `typography.json`; the three reading
 * families are Hyle's own — the two sans (**Hyle Grotesk Classic** and **Hyle
 * Grotesk Plus**) and the one serif (**Hyle Print**, a Literata derivative),
 * bundled as verified TTFs (OFL, see `third_party/fonts/`). Display, headline,
 * and title roles are Hyle Print — the newspaper voice of the masthead titles
 * and the loom. The "Nooz" wordmark itself is [WordmarkFontFamily], PT Serif
 * Regular per the owner's spec, not Hyle Print.
 */

/** Hyle Grotesk Classic — the default sans reading + UI voice. */
val HyleGroteskClassic: FontFamily
    @Composable get() {
        val f0 = Font(Res.font.hyle_grotesk_classic_regular, weight = FontWeight.Normal)
        val f1 = Font(Res.font.hyle_grotesk_classic_medium, weight = FontWeight.Medium)
        val f2 = Font(Res.font.hyle_grotesk_classic_bold, weight = FontWeight.Bold)
        return remember(f0, f1, f2) { FontFamily(f0, f1, f2) }
    }

/** Hyle Grotesk Plus — the alternate sans (Classic with the Deco N/R sweep). */
val HyleGroteskPlus: FontFamily
    @Composable get() {
        val f0 = Font(Res.font.hyle_grotesk_plus_regular, weight = FontWeight.Normal)
        val f1 = Font(Res.font.hyle_grotesk_plus_medium, weight = FontWeight.Medium)
        val f2 = Font(Res.font.hyle_grotesk_plus_bold, weight = FontWeight.Bold)
        return remember(f0, f1, f2) { FontFamily(f0, f1, f2) }
    }

/** Hyle Print — the serif reading voice and every display/masthead role. */
val HylePrint: FontFamily
    @Composable get() {
        val f0 = Font(Res.font.hyle_print_regular, weight = FontWeight.Normal)
        val f1 = Font(Res.font.hyle_print_medium, weight = FontWeight.Medium)
        val f2 = Font(Res.font.hyle_print_heavy, weight = FontWeight.ExtraBold)
        return remember(f0, f1, f2) { FontFamily(f0, f1, f2) }
    }

/** The newspaper voice: titles, headlines, the loom's numerals. */
val DisplayFontFamily: FontFamily
    @Composable get() = HylePrint

/**
 * The "Nooz" wordmark only: PT Serif Regular, owner-specified (2026-07) at
 * -2% letter-spacing — see [NoozWordmark]. Not Hyle Print, so it doesn't
 * reuse [DisplayFontFamily]. See `third_party/fonts/README.md`.
 */
val WordmarkFontFamily: FontFamily
    @Composable get() {
        val f0 = Font(Res.font.pt_serif_regular, weight = FontWeight.Normal)
        return remember(f0) { FontFamily(f0) }
    }

/** UI chrome (labels, buttons, captions) stays on the default sans. */
val HyleSans: FontFamily
    @Composable get() = HyleGroteskClassic

@Composable
fun ReaderFont.family(): FontFamily = when (this) {
    ReaderFont.GROTESK_CLASSIC -> HyleGroteskClassic
    ReaderFont.GROTESK_PLUS -> HyleGroteskPlus
    ReaderFont.PRINT -> HylePrint
}

/**
 * Material3 [Typography] for the chosen reader font at the chosen text size
 * (the Settings mock's three-step scale). Feature code keeps using named roles.
 */
@Composable
fun riverTypography(
    readerFont: ReaderFont = ReaderFont.GROTESK_CLASSIC,
    textScale: TextScale = TextScale.PEANUT,
): Typography {
    val k = textScale.multiplier
    fun style(family: FontFamily, sizeSp: Int, weight: FontWeight, lineSp: Int) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = (sizeSp * k).sp,
        lineHeight = (lineSp * k).sp,
    )

    val body = readerFont.family()
    return Typography(
        displayLarge = style(DisplayFontFamily, 34, FontWeight.Medium, 40),  // article headline (Paper mock)
        headlineMedium = style(DisplayFontFamily, 24, FontWeight.Medium, 30),
        headlineSmall = style(DisplayFontFamily, 21, FontWeight.Medium, 27), // list titles (Stand mock)
        titleLarge = style(DisplayFontFamily, 18, FontWeight.Medium, 24),
        titleMedium = style(body, 16, FontWeight.Medium, 22),
        bodyLarge = style(body, 16, FontWeight.Normal, 26),                  // reading body, relaxed
        bodyMedium = style(body, 14, FontWeight.Normal, 21),
        bodySmall = style(body, 12, FontWeight.Normal, 18),
        labelLarge = style(body, 14, FontWeight.Medium, 18),
        labelMedium = style(body, 12, FontWeight.Medium, 16),
        labelSmall = style(body, 11, FontWeight.Medium, 14),
    )
}

/** Default typography — kept for previews and as the pre-settings-load fallback. */
val AppTypography: Typography
    @Composable get() = riverTypography()
