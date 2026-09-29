package xyz.mdhv.riverwip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import xyz.mdhv.riverwip.design.Tokens
import xyz.mdhv.riverwip.design.res.*

/**
 * "What's inside" — the short tour of the things a reader will otherwise never
 * find.
 *
 * Nooz hides almost nothing behind a menu: the Loom opens by *pulling down on
 * the stand*, Clippings by a bookmark inside the reader, and Flash, Cast and
 * Today in History are all deliberately **off** until switched on (each is
 * either a standing generation over everything that flowed, or the one fetch
 * this app makes to somewhere the reader didn't add). Every one of those is a
 * defensible design decision on its own, and together they add up to a set of
 * features nobody discovers. This is the counterweight: five lines, each
 * naming a thing and saying where it lives.
 *
 * Deliberately **not** a set of toggles. Flash and Cast each pull down a model,
 * and "off until you decide" is the whole point of how they ship — flipping
 * that decision on a reader's behalf during setup, before the words mean
 * anything to them, would trade one bad outcome for a worse one. So the tour
 * points at the switch instead of being the switch.
 *
 * Shared on purpose: onboarding shows it once as a send-off, and Settings
 * keeps it permanently, because every reader who onboarded before this existed
 * would otherwise never see it at all.
 */
private data class TourEntry(val name: StringResource, val body: StringResource)

// The entries themselves are resource ids, so a translation of this tour is a
// values-<locale>/strings.xml and nothing else. See core/design's strings.xml.
private val TOUR_ENTRIES = listOf(
    TourEntry(Res.string.tour_loom_name, Res.string.tour_loom_body),
    TourEntry(Res.string.tour_flash_name, Res.string.tour_flash_body),
    TourEntry(Res.string.tour_cast_name, Res.string.tour_cast_body),
    TourEntry(Res.string.tour_clippings_name, Res.string.tour_clippings_body),
    TourEntry(Res.string.tour_history_name, Res.string.tour_history_body),
)

/**
 * The tour body, with no framing of its own, so the host decides the heading,
 * the buttons, and whether it sits in a card or a settings list.
 */
@Composable
fun FeatureTourContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.sm),
    ) {
        for (entry in TOUR_ENTRIES) {
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Spacing.xxs)) {
                Text(
                    stringResource(entry.name),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    stringResource(entry.body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            stringResource(Res.string.tour_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
