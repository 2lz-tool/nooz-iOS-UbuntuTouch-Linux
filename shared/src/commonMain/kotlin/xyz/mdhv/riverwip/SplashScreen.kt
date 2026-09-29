package xyz.mdhv.riverwip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxWithConstraints
import xyz.mdhv.riverwip.design.DisplayFontFamily
import xyz.mdhv.riverwip.design.NoozWordmark
import xyz.mdhv.riverwip.design.Tokens

// Greeked newsprint for the backdrop — deliberately meaningless text (the
// mock's own lorem), never real headlines: the splash must not editorialize.
private const val GREEK_TOP = "lentesque est eu facilisis 4563 ac vehicula quam ultricies"
private const val GREEK_DISPLAY =
    "n mollis purus. rhoncus pretium vehicula quam. Nulla venenatis. Vivamus " +
        "ac turpis hendrerit lectus. magna et scelerisque et diam justo. ultricies enim, non augue quis."
private const val GREEK_SMALL =
    "Suspendisse aliquam mollis purus. Etiam nibh tortor, rhoncus pretium lectus id, cursus " +
        "vehicula quam. Nulla vulputate ultricies venenatis. Vivamus purus leo, interdum ac " +
        "turpis hendrerit, lacinia eleifend lectus. Nullam sit amet urna vitae."

/**
 * The splash (owner's Splash mock, 2026-07): the wordmark set into a page of
 * oversized newsprint — a top partial line and rule, big serif display text
 * that **bleeds off both edges**, and a block of justified fine print below,
 * all greeked at a legible newsprint grey (measured L≈168 on the mock, ≈0.32
 * over the paper). Drawn, not shipped as an image, so it adapts to both themes
 * and costs no asset. "Nooz" rides plainly over the page — no highlight box.
 */
@Composable
fun SplashScreen() {
    val paperGrey = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.32f)
    // Overhang so the big lines run off the left and right edges like a page
    // cropped mid-column.
    val bleed = 26.dp
    Box(Modifier.fillMaxSize().clipToBounds()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(vertical = Tokens.Spacing.lg)
                .clearAndSetSemantics {},
        ) {
            val overWidth = maxWidth + bleed * 2
            Column(Modifier.fillMaxSize()) {
                // Top: a partial line clipped on the right, then the rule.
                Text(
                    text = GREEK_TOP,
                    fontFamily = DisplayFontFamily,
                    fontSize = 26.sp,
                    color = paperGrey,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.width(overWidth).offset(x = -bleed),
                )
                Spacer(Modifier.height(Tokens.Spacing.xs))
                HorizontalDivider(color = paperGrey)
                // Display body: a wider-than-screen column, shifted left so each
                // line bleeds off both margins.
                Text(
                    text = GREEK_DISPLAY,
                    fontFamily = DisplayFontFamily,
                    fontSize = 56.sp,
                    lineHeight = 68.sp,
                    color = paperGrey,
                    // The greeked string is longer than this weighted box is ever
                    // tall on a real phone (see SplashScreen audit); Compose does
                    // not clip a Text's paint to its own bounds by default, so
                    // without this the unfit lines would draw straight through
                    // the fine print below instead of stopping at this block's
                    // edge like the top line's right-hand crop.
                    modifier = Modifier
                        .width(overWidth)
                        .weight(1f)
                        .clipToBounds()
                        .offset(x = -bleed)
                        .padding(top = Tokens.Spacing.md),
                )
                // Fine print: justified, indented from the margin like a caption.
                Text(
                    text = GREEK_SMALL,
                    fontFamily = DisplayFontFamily,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = paperGrey,
                    textAlign = TextAlign.Justify,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = Tokens.Spacing.md, top = Tokens.Spacing.sm),
                )
            }
        }
        // The owner's decided name (design mocks, 2026-07): plain black serif,
        // dead-centre. No *visible* box — but a page-coloured one is still
        // there behind it, sized with breathing room, so it cleanly occludes
        // whatever greeked backdrop text the centred mark happens to land on
        // top of instead of visibly colliding with it (owner's #7).
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = Tokens.Spacing.md, vertical = Tokens.Spacing.xs),
        ) {
            NoozWordmark(
                fontSize = 46.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
