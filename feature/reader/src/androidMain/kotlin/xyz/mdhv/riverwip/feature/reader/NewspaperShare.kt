package xyz.mdhv.riverwip.feature.reader

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mdhv.riverwip.design.res.Res

/**
 * Share as a newspaper clipping (owner's spec, 2026-07). The Share action
 * renders the headline as a paper-white masthead card — the "Nooz" wordmark, a
 * double rule, the real title set in the Hyle Print serif, and the real
 * source · author · date — then hands the PNG to the system chooser. Everything
 * on the card is real: the app's own name, the article's own title/source/
 * author, and today's date; nothing is invented. If the image can't be written
 * for any reason it falls back to a plain-text share, so Share never dead-ends.
 */
object NewspaperShare {

    suspend fun share(context: Context, title: String, source: String?, author: String?, url: String?) {
        val uri = runCatching {
            val bitmap = render(context, title, source, author)
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "nooz-clipping.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()

        val caption = buildString {
            append(title)
            if (!url.isNullOrBlank()) append('\n').append(url)
            append("\nClipped with Nooz")
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            if (uri != null) {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
            putExtra(Intent.EXTRA_TEXT, caption)
        }
        context.startActivity(Intent.createChooser(send, null))
    }

    private const val W = 1080
    private const val MARGIN = 84f
    private val PAPER = Color.rgb(0xF7, 0xF5, 0xEF)
    private val INK = Color.rgb(0x1A, 0x1A, 0x1A)
    private val MUTED = Color.rgb(0x6B, 0x66, 0x5E)

    // The vertical rhythm, as literal gaps between one draw and the next. Used
    // to both size the bitmap and lay out the canvas from the *same* numbers,
    // so the two can never drift apart (a prior version sized the bitmap from
    // a rough separate estimate, and byline height wasn't part of it at all —
    // fine for a short byline, cramped or clipped against the footer for a
    // long one).
    private const val MAST_TO_BASELINE = 116f
    private const val BASELINE_TO_RULE1 = 40f
    private const val RULE1_TO_RULE2 = 11f
    private const val RULE2_TO_HEADLINE = 42f
    private const val HEADLINE_TO_RULE3 = 34f
    private const val RULE3_TO_BYLINE = 52f
    private const val BYLINE_LINE_HEIGHT = 42f
    private const val BYLINE_TO_FOOTER = 40f

    private suspend fun render(context: Context, title: String, source: String?, author: String?): Bitmap {
        val serif = font(context, "hyle_print_medium") ?: Typeface.SERIF
        val wordmarkFace = font(context, "pt_serif_regular") ?: Typeface.create(serif, Typeface.NORMAL)
        val sans = font(context, "hyle_grotesk_classic_medium") ?: Typeface.SANS_SERIF

        val contentW = (W - 2 * MARGIN).toInt()

        val headlinePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; typeface = serif; textSize = 78f
        }
        val headline = StaticLayout.Builder
            .obtain(title.trim(), 0, title.trim().length, headlinePaint, contentW)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(8f, 1f)
            .setIncludePad(false)
            .build()

        // Byline: source then author, stacked (never side-by-side — a live-blog
        // byline like "Jonathan Howcroft (now) and Will Magee (later)" ran into
        // the source when both were drawn on one line, left/right-aligned with
        // no width bound). Each line is independently ellipsized as a backstop.
        val bylinePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MUTED; typeface = sans; textSize = 30f; letterSpacing = 0.06f
        }
        val bylineLines = listOfNotNull(source, author)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            // Locale.ROOT, not getDefault(): this uppercases a *publisher's own
            // name*, which does not belong to the reader's locale. Under a
            // Turkish locale getDefault() maps i → İ, so a shared clipping from
            // Livemint went out reading "LİVEMİNT" — someone else's masthead,
            // misspelled, on an image the reader is publishing.
            .map { TextUtils.ellipsize(it.uppercase(Locale.ROOT), bylinePaint, contentW.toFloat(), TextUtils.TruncateAt.END) }

        val footer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MUTED; typeface = sans; textSize = 28f; textAlign = Paint.Align.CENTER; letterSpacing = 0.08f
        }
        val footerMetrics = footer.fontMetrics

        val height = (
            MARGIN + MAST_TO_BASELINE + BASELINE_TO_RULE1 + RULE1_TO_RULE2 + RULE2_TO_HEADLINE +
                headline.height + HEADLINE_TO_RULE3 + RULE3_TO_BYLINE +
                bylineLines.size * BYLINE_LINE_HEIGHT + BYLINE_TO_FOOTER +
                (footerMetrics.descent - footerMetrics.ascent) + MARGIN
            ).toInt()

        val bmp = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(PAPER)

        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; strokeWidth = 3f }
        val hairline = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; strokeWidth = 1.5f }

        var y = MARGIN

        // Masthead wordmark — PT Serif Regular at -2% letter-spacing, the same
        // face as NoozWordmark everywhere else (not the Hyle Print body serif).
        val mast = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; typeface = wordmarkFace; textSize = 128f; textAlign = Paint.Align.CENTER
            letterSpacing = -0.02f
        }
        y += MAST_TO_BASELINE
        canvas.drawText("Nooz", W / 2f, y, mast)
        y += BASELINE_TO_RULE1
        canvas.drawLine(MARGIN, y, W - MARGIN, y, rule); y += RULE1_TO_RULE2
        canvas.drawLine(MARGIN, y, W - MARGIN, y, hairline); y += RULE2_TO_HEADLINE

        // Headline.
        canvas.save(); canvas.translate(MARGIN, y); headline.draw(canvas); canvas.restore()
        y += headline.height + HEADLINE_TO_RULE3
        canvas.drawLine(MARGIN, y, W - MARGIN, y, hairline); y += RULE3_TO_BYLINE

        // Byline: source, then author, stacked — see bylineLines above.
        for (line in bylineLines) {
            canvas.drawText(line, 0, line.length, MARGIN, y, bylinePaint)
            y += BYLINE_LINE_HEIGHT
        }
        y += BYLINE_TO_FOOTER

        // Footer: date + provenance, centred, immediately below whatever came before.
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))
        canvas.drawText("$date  ·  clipped with Nooz", W / 2f, y - footerMetrics.ascent, footer)

        return bmp
    }

    /** The design system's bundled fonts live in Compose resources; Android's Typeface wants a file, so unpack each once. */
    private suspend fun font(context: Context, name: String): Typeface? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(File(context.cacheDir, "fonts").apply { mkdirs() }, "$name.ttf")
            if (!file.exists()) file.writeBytes(Res.readBytes("font/$name.ttf"))
            Typeface.createFromFile(file)
        }.getOrNull()
    }
}

@Composable
actual fun rememberClippingSharer(): ClippingSharer {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context, scope) {
        object : ClippingSharer {
            override fun share(title: String, source: String?, author: String?, url: String?) {
                scope.launch { NewspaperShare.share(context, title, source, author, url) }
            }
        }
    }
}
