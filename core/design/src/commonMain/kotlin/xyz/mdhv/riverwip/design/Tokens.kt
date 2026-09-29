package xyz.mdhv.riverwip.design

import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp

/**
 * The design token contract.
 *
 * Every surface specifies against these named tokens — never hardcoded literals.
 * Behaviour must not depend on the token *values*: surfaces are re-skinned by
 * changing values here alone.
 *
 * Values are sourced from the **Hyle Design System** (its shared `tokens` JSON,
 * the ecosystem's cross-platform source of truth). Dark-first; the single north-star
 * accent is violet `#8E7BFF`. The provenance convention is fixed and never
 * inverted: warm radium (`#C7EF9E`) = on-device, cold cyan (`#35E0FF`) = cloud
 * — and per the colour-vision constraint, provenance always pairs with a
 * non-colour channel (icon/label), never colour alone.
 */
object Tokens {

    /** Raw palette (ARGB), mirrored from Hyle `color.palette.*`. */
    object Palette {
        // Field — canvas / backdrops. `void` (pure black) is art-only.
        val fieldVoid = ComposeColor(0xFF000000)
        val fieldNear = ComposeColor(0xFF0A0809)
        val fieldRaised = ComposeColor(0xFF121212) // UI surfaces sit here, never pure black (halation).
        val fieldDeep = ComposeColor(0xFF050009)

        // Ink — text, warm off-white at descending opacity.
        val inkPure = ComposeColor(0xFFECE8E4)
        val inkFull = ComposeColor(0xEBECE8E4)
        // 4.57:1 over fieldRaised, 4.56:1 over fieldNear. Was 0x6B (42%), which
        // composites to 3.57:1 and 3.51:1 — the same sub-AA failure as the
        // light theme's paperInkDim, on the same kind of secondary text.
        val inkDim = ComposeColor(0x7FECE8E4)
        val inkFaint = ComposeColor(0x2EECE8E4)

        // Accent — spend scarce light here.
        val accentViolet = ComposeColor(0xFF8E7BFF)
        val accentVioletBright = ComposeColor(0xFFA593FF)
        val accentVioletDeep = ComposeColor(0xFF7867E6)

        // Provenance — ecosystem convention. Never invert.
        val provenanceNative = ComposeColor(0xFFC7EF9E) // radium yellow-green = on-device
        val provenanceCloud = ComposeColor(0xFF35E0FF)  // cold cyan = cloud

        val hairlineDefault = ComposeColor(0x14FFFFFF)
        val hairlineStrong = ComposeColor(0x24FFFFFF)
        val glassPane = ComposeColor(0x850A0809)

        // Paper — the owner's light direction (design mocks, 2026-07): warm
        // near-white newsprint field, near-black ink, grey bylines, faint
        // hairlines. These carry the light theme; the dark set above remains
        // the dark theme.
        val paperField = ComposeColor(0xFFF7F6F3)
        val paperRaised = ComposeColor(0xFFFFFFFF)
        val paperInk = ComposeColor(0xFF141414)
        // 4.60:1 on paperField, 4.97:1 on paperRaised. Was 0xFF8A8A86 = 3.21:1,
        // under the 4.5:1 WCAG AA asks for body text, on the token that carries
        // bylines, timestamps and secondary copy throughout. Still far lighter
        // than paperInk at 17.05:1, so dimmed text still reads as dimmed.
        val paperInkDim = ComposeColor(0xFF70706C)
        val paperInkFaint = ComposeColor(0xFFC9C8C4)
        val paperHairline = ComposeColor(0x1F000000)
        val paperHairlineStrong = ComposeColor(0x33000000)
        val paperSignalDanger = ComposeColor(0xFFB3261E)

        // Feedback. NOTE (colour-vision constraint, §2): the primary user is
        // red–green colourblind. No meaning may ride on danger-vs-success colour
        // alone — these are always paired with icon + text. Kept for surfaces
        // that already carry a non-colour signal.
        val signalDanger = ComposeColor(0xFFE5564B)
        val signalWarning = ComposeColor(0xFFE0941A)
        val signalSuccess = ComposeColor(0xFF5BBF7A)
    }

    /** Semantic colour roles (mirrored from Hyle `color.{text,background,...}`). */
    object Color {
        val textPrimary = Palette.inkFull
        val textSecondary = Palette.inkDim
        val textFaint = Palette.inkFaint
        val textInverse = Palette.fieldNear
        val textAccent = Palette.accentViolet

        val backgroundField = Palette.fieldVoid   // art / river canvas only
        val backgroundSurface = Palette.fieldRaised // all readable UI
        val backgroundGlass = Palette.glassPane

        val borderHairline = Palette.hairlineDefault
        val borderStrong = Palette.hairlineStrong
        val borderFocus = Palette.accentViolet

        val actionPrimary = Palette.accentViolet
        val actionPrimaryHover = Palette.accentVioletBright
        val actionPrimaryActive = Palette.accentVioletDeep
        val onActionPrimary = Palette.fieldNear

        val provenanceNative = Palette.provenanceNative
        val provenanceCloud = Palette.provenanceCloud
    }

    /** Spacing scale (dp), mirrored from Hyle `spacing.*`. */
    object Spacing {
        val none = 0.dp
        val xxs = 4.dp
        val xs = 8.dp
        val sm = 12.dp
        val md = 16.dp
        val lg = 20.dp
        val xl = 24.dp
        val xxl = 32.dp
        val xxxl = 40.dp
        val huge = 48.dp
        val giant = 64.dp
    }

    /** Corner radii (dp), mirrored from Hyle `radius.*`. */
    object Radius {
        val none = 0.dp
        val sm = 4.dp
        val md = 8.dp
        val lg = 12.dp
        val xl = 16.dp
        val full = 9999.dp
    }

    /** Border widths (dp). */
    object Border {
        val thin = 1.dp
        val thick = 2.dp
    }

    /** Motion durations (ms) and easing, mirrored from Hyle `motion.*`. */
    object Motion {
        const val instantMs = 120
        const val calmMs = 300 // default state change — weight, not bounce
        const val paneMs = 420
    }
}
