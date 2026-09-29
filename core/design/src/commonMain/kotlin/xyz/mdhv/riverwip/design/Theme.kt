package xyz.mdhv.riverwip.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import xyz.mdhv.riverwip.model.ReaderFont
import xyz.mdhv.riverwip.model.TextScale
import xyz.mdhv.riverwip.model.ThemeMode

/**
 * Extended token slots that Material3's [androidx.compose.material3.ColorScheme]
 * has no home for (provenance hues, hairlines). Provenance carries meaning only
 * when paired with a non-colour channel — see [Tokens].
 */
data class ExtendedColors(
    val provenanceNative: Color,
    val provenanceCloud: Color,
    val hairline: Color,
    val hairlineStrong: Color,
    val textFaint: Color,
)

private val DarkExtended = ExtendedColors(
    provenanceNative = Tokens.Color.provenanceNative,
    provenanceCloud = Tokens.Color.provenanceCloud,
    hairline = Tokens.Color.borderHairline,
    hairlineStrong = Tokens.Color.borderStrong,
    textFaint = Tokens.Color.textFaint,
)

// Provenance hues stay identical in every tint (the convention never inverts);
// they read fine on paper because they always pair with a label/icon anyway.
private val PaperExtended = ExtendedColors(
    provenanceNative = Tokens.Color.provenanceNative,
    provenanceCloud = Tokens.Color.provenanceCloud,
    hairline = Tokens.Palette.paperHairline,
    hairlineStrong = Tokens.Palette.paperHairlineStrong,
    textFaint = Tokens.Palette.paperInkFaint,
)

private val LocalExtendedColors = staticCompositionLocalOf { PaperExtended }

/** Access extended tokens: `AppTheme.extended.provenanceNative`. */
object AppTheme {
    val extended: ExtendedColors
        @Composable get() = LocalExtendedColors.current
}

// Three surface tints (owner's three-theme reference, 2026-07): White, Paper,
// Dark charcoal. Ink is the accent — the mocks spend colour only on the loom's
// streams and provenance dots.
private fun inkScheme(field: Color, raised: Color) = lightColorScheme(
    primary = Tokens.Palette.paperInk,
    onPrimary = field,
    secondary = Tokens.Palette.paperInkDim,
    onSecondary = field,
    background = field,
    onBackground = Tokens.Palette.paperInk,
    surface = field,
    onSurface = Tokens.Palette.paperInk,
    surfaceVariant = raised,
    onSurfaceVariant = Tokens.Palette.paperInkDim,
    outline = Tokens.Palette.paperHairlineStrong,
    outlineVariant = Tokens.Palette.paperHairline,
    error = Tokens.Palette.paperSignalDanger,
)

private val WhiteScheme = inkScheme(field = Color(0xFFFFFFFF), raised = Tokens.Palette.paperField)
private val PaperScheme = inkScheme(field = Tokens.Palette.paperField, raised = Color(0xFFFFFFFF))

// The dark tint per the owner's reference is a soft charcoal, not the old
// near-black Hyle field.
private val CharcoalScheme = darkColorScheme(
    primary = Color(0xFFECEAE6),
    onPrimary = Color(0xFF262624),
    secondary = Color(0xFF9C9A96),
    onSecondary = Color(0xFF262624),
    background = Color(0xFF262624),
    onBackground = Color(0xFFECEAE6),
    surface = Color(0xFF262624),
    onSurface = Color(0xFFECEAE6),
    surfaceVariant = Color(0xFF31312F),
    onSurfaceVariant = Color(0xFF9C9A96),
    outline = Color(0x40FFFFFF),
    outlineVariant = Color(0x1FFFFFFF),
    error = Color(0xFFF2B8B5),
)

@Composable
fun RiverTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    readerFont: ReaderFont = ReaderFont.GROTESK_CLASSIC,
    textScale: TextScale = TextScale.PEANUT,
    content: @Composable () -> Unit,
) {
    // The one place SYSTEM becomes a literal tint. Everything downstream — the
    // scheme, the extended tokens, and the system-bar icon contrast the host
    // Activity sets — reads the same resolved answer, so the app surface and
    // the status bar can no longer disagree about whether it is dark out.
    val tint = themeMode.resolve(isSystemInDarkTheme())
    val scheme = when (tint) {
        ThemeMode.WHITE -> WhiteScheme
        ThemeMode.DARK -> CharcoalScheme
        // SYSTEM cannot survive resolve(); PAPER is the only tint left.
        else -> PaperScheme
    }
    val extended = if (tint == ThemeMode.DARK) DarkExtended else PaperExtended
    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = scheme,
            typography = riverTypography(readerFont, textScale),
            content = content,
        )
    }
}
