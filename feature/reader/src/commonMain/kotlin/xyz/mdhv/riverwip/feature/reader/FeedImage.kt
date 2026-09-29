package xyz.mdhv.riverwip.feature.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import xyz.mdhv.riverwip.data.net.ImageStore
import xyz.mdhv.riverwip.model.ImageStyle

/**
 * Where feed images come from (disk cache + download). The app root provides it; without one no
 * image is shown, which is what a preview or a test wants.
 */
val LocalImageStore = staticCompositionLocalOf<ImageStore?> { null }

/** Decoded images kept in memory so scrolling a list back and forth does not decode the same JPEG again. */
private object DecodedImages {
    private const val MAX = 48
    private val map = object : LinkedHashMap<String, ImageBitmap>(MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > MAX
    }

    @Synchronized operator fun get(url: String): ImageBitmap? = map[url]
    @Synchronized operator fun set(url: String, image: ImageBitmap) { map[url] = image }
}

@Composable
private fun rememberRemoteImage(url: String): ImageBitmap? {
    val store = LocalImageStore.current
    var image by remember(url, store) { mutableStateOf(DecodedImages[url]) }
    LaunchedEffect(url, store) {
        if (image != null || store == null) return@LaunchedEffect
        val decoded = withContext(Dispatchers.Default) {
            store.load(url)?.let { bytes -> runCatching { bytes.decodeToImageBitmap() }.getOrNull() }
        }
        if (decoded != null) {
            DecodedImages[url] = decoded
            image = decoded
        }
    }
    return image
}

/**
 * A feed's own image (owner's ask, 2026-07), styled per the reader's choice:
 * plain colour, a tasteful duotone black & white (not a flat desaturation —
 * see [TastefulBlackAndWhite]), or a halftone dot-print stylization in the
 * app's own newspaper-column ink, using the same jittered-dot technique
 * `core/design`'s `PaperGrain` already uses for its texture, adapted here to
 * an image's own per-cell luminance rather than noise (see [HalftoneImage]).
 *
 * Renders nothing at all — not even reserved space — when there's no image
 * to show: a null/blank [imageUrl], or a source's own feed having declared
 * this item adult/explicit while [hideNsfw] is on. That check is never this
 * app's own judgment; see `AppSettings.hideNsfwImages`'s doc for exactly
 * what "declared" means here.
 */
@Composable
fun FeedImage(
    imageUrl: String?,
    declaredNsfw: Boolean,
    hideNsfw: Boolean,
    style: ImageStyle,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    if (imageUrl.isNullOrBlank() || (hideNsfw && declaredNsfw)) return

    when (style) {
        ImageStyle.COLOR -> PhotoImage(imageUrl, contentDescription, null, modifier)
        ImageStyle.BLACK_AND_WHITE -> PhotoImage(imageUrl, contentDescription, ColorFilter.colorMatrix(TastefulBlackAndWhite), modifier)
        ImageStyle.HALFTONE -> HalftoneImage(imageUrl = imageUrl, contentDescription = contentDescription, modifier = modifier)
    }
}

/** The photo itself, cropped to fill, fading in when it arrives. Nothing is drawn (no reserved space) until it has loaded. */
@Composable
private fun PhotoImage(imageUrl: String, contentDescription: String?, colorFilter: ColorFilter?, modifier: Modifier) {
    val bitmap = rememberRemoteImage(imageUrl) ?: return
    val alpha by animateFloatAsState(1f, label = "feed-image-fade")
    Image(
        bitmap = bitmap,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        colorFilter = colorFilter,
        modifier = modifier.alpha(alpha),
    )
}

/**
 * Grayscale (standard BT.601 luminance weights) with a mild contrast boost
 * and a slight warm-tone lean — a flat, unweighted desaturation reads as
 * muddy and washed out, which is exactly the "ugly BW filter" the owner
 * asked to avoid; this reads closer to a newspaper's own halftone-photo
 * reproduction instead. Handcrafted as one 4x5 matrix (contrast folded into
 * the luminance weights, the warm tone into each channel's own translation
 * term) rather than chaining matrix multiplications, so the numbers here
 * are the whole story — no hidden composition to reverse-engineer later.
 */
private val TastefulBlackAndWhite = ColorMatrix(
    floatArrayOf(
        0.34385f, 0.67505f, 0.1311f, 0f, -13.2f,
        0.34385f, 0.67505f, 0.1311f, 0f, -17.2f,
        0.34385f, 0.67505f, 0.1311f, 0f, -23.2f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

// The dot grid's resolution tracks the image's own rendered width (a fixed
// column count either looked chunky at a full-width hero or wasted density on
// a small list thumbnail) rather than one constant for every size. 4dp reads
// as a fine, resolved dot-print at both ends — a real newspaper halftone, not
// the coarse blown-up dots a low, fixed column count gave every size alike.
private val HALFTONE_CELL_SIZE = 4.dp
private const val HALFTONE_MIN_COLUMNS = 12
private const val HALFTONE_MAX_COLUMNS = 160

/**
 * The image reproduced as newsprint would: no photo drawn at all, only a grid of dots whose radius
 * tracks that cell's own darkness -- the app's existing paper-grain speckle technique (a seeded
 * jittered dot field on a `Canvas`), driven by the image's per-cell luminance instead of noise.
 * Each cell is the box-average of the source pixels beneath it, read one band of rows at a time so
 * a large photo is never held as a full pixel array.
 */
@Composable
private fun HalftoneImage(imageUrl: String, contentDescription: String?, modifier: Modifier = Modifier) {
    val source = rememberRemoteImage(imageUrl) ?: return // still loading, or the decode failed -- reserve nothing, same as no image at all

    val ink = MaterialTheme.colorScheme.onBackground
    val paper = MaterialTheme.colorScheme.background
    val description = contentDescription

    BoxWithConstraints(modifier) {
        val cols = (maxWidth / HALFTONE_CELL_SIZE).toInt().coerceIn(HALFTONE_MIN_COLUMNS, HALFTONE_MAX_COLUMNS)
        val rows = (cols / (source.width.toFloat() / source.height.toFloat())).toInt().coerceAtLeast(1)
        val luminance = remember(source, cols, rows) { cellLuminance(source, cols, rows) }

        val canvasModifier = if (description != null) {
            Modifier.fillMaxSize().semantics { this.contentDescription = description }
        } else {
            Modifier.fillMaxSize()
        }

        Canvas(canvasModifier) {
            drawRect(paper)
            val cellW = size.width / cols
            val cellH = size.height / rows
            val maxRadius = minOf(cellW, cellH) / 2f
            for (y in 0 until rows) {
                for (x in 0 until cols) {
                    val radius = (1f - luminance[y * cols + x]) * maxRadius * 0.95f
                    if (radius > 0.5f) {
                        drawCircle(
                            color = ink,
                            radius = radius,
                            center = Offset(x * cellW + cellW / 2f, y * cellH + cellH / 2f),
                        )
                    }
                }
            }
        }
    }
}

/** Perceived luminance (0..1) of each of the `cols x rows` cells of [image], row-major. */
internal fun cellLuminance(image: ImageBitmap, cols: Int, rows: Int): FloatArray {
    val out = FloatArray(cols * rows)
    val w = image.width
    val h = image.height
    val band = IntArray(w * ((h + rows - 1) / rows + 1))
    for (row in 0 until rows) {
        val y0 = (row.toLong() * h / rows).toInt()
        val y1 = ((row + 1).toLong() * h / rows).toInt().coerceAtLeast(y0 + 1).coerceAtMost(h)
        val bandH = y1 - y0
        image.readPixels(band, startX = 0, startY = y0, width = w, height = bandH, bufferOffset = 0, stride = w)
        for (col in 0 until cols) {
            val x0 = (col.toLong() * w / cols).toInt()
            val x1 = ((col + 1).toLong() * w / cols).toInt().coerceAtLeast(x0 + 1).coerceAtMost(w)
            var sum = 0f
            var n = 0
            for (yy in 0 until bandH) {
                val rowBase = yy * w
                for (xx in x0 until x1) {
                    val p = band[rowBase + xx]
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    sum += 0.299f * r + 0.587f * g + 0.114f * b
                    n++
                }
            }
            out[row * cols + col] = if (n == 0) 1f else sum / n / 255f
        }
    }
    return out
}
