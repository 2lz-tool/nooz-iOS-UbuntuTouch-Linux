package xyz.mdhv.riverwip.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import xyz.mdhv.riverwip.design.res.Res
import java.awt.Color
import java.awt.FileDialog
import java.awt.Font
import java.awt.Frame
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.font.LineBreakMeasurer
import java.awt.font.TextAttribute
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.text.AttributedString
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.imageio.ImageIO

/**
 * Desktop "share": there is no system share sheet, so the clipping is rendered to the same
 * masthead card the phone makes, saved wherever the reader chooses, and its caption (title, link)
 * is put on the clipboard so it can be pasted next to the image.
 */
@Composable
actual fun rememberClippingSharer(): ClippingSharer {
    val scope = rememberCoroutineScope()
    return remember(scope) { DesktopClippingSharer(scope) }
}

private class DesktopClippingSharer(private val scope: CoroutineScope) : ClippingSharer {
    override fun share(title: String, source: String?, author: String?, url: String?) {
        scope.launch {
            val caption = buildString {
                append(title)
                if (!url.isNullOrBlank()) append('\n').append(url)
                append("\nClipped with Nooz")
            }
            withContext(Dispatchers.Swing) {
                runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(caption), null) }
            }
            val png = withContext(Dispatchers.Default) { runCatching { ClippingCard.renderPng(title, source, author) }.getOrNull() }
                ?: return@launch
            val target = withContext(Dispatchers.Swing) {
                val dialog = FileDialog(null as Frame?, "Save clipping", FileDialog.SAVE).apply { file = "nooz-clipping.png" }
                dialog.isVisible = true
                if (dialog.file == null || dialog.directory == null) null else File(dialog.directory, dialog.file)
            } ?: return@launch
            withContext(Dispatchers.IO) { runCatching { target.writeBytes(png) } }
        }
    }
}

/** Java2D twin of the Android card renderer: same canvas, same rhythm, same fonts. */
internal object ClippingCard {
    private const val W = 1080
    private const val MARGIN = 84
    private val PAPER = Color(0xF7, 0xF5, 0xEF)
    private val INK = Color(0x1A, 0x1A, 0x1A)
    private val MUTED = Color(0x6B, 0x66, 0x5E)

    private const val MAST_TO_BASELINE = 116
    private const val BASELINE_TO_RULE1 = 40
    private const val RULE1_TO_RULE2 = 11
    private const val RULE2_TO_HEADLINE = 42
    private const val HEADLINE_TO_RULE3 = 34
    private const val RULE3_TO_BYLINE = 52
    private const val BYLINE_LINE_HEIGHT = 42
    private const val BYLINE_TO_FOOTER = 40

    private suspend fun font(name: String, fallback: String, size: Float): Font = runCatching {
        Font.createFont(Font.TRUETYPE_FONT, ByteArrayInputStream(Res.readBytes("font/$name.ttf"))).deriveFont(size)
    }.getOrElse { Font(fallback, Font.PLAIN, size.toInt()) }

    suspend fun renderPng(title: String, source: String?, author: String?): ByteArray {
        val serif = font("hyle_print_medium", Font.SERIF, 78f)
        val wordmark = font("pt_serif_regular", Font.SERIF, 128f)
            .deriveFont(mapOf(TextAttribute.TRACKING to -0.02f))
        val sans = font("hyle_grotesk_classic_medium", Font.SANS_SERIF, 30f)
            .deriveFont(mapOf(TextAttribute.TRACKING to 0.06f))
        val footerFont = font("hyle_grotesk_classic_medium", Font.SANS_SERIF, 28f)
            .deriveFont(mapOf(TextAttribute.TRACKING to 0.08f))
        val contentW = W - 2 * MARGIN

        // Measure on a scratch image so the height comes from the same numbers the drawing uses.
        val scratch = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics()
        scratch.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        val frc = scratch.fontRenderContext
        val headlineText = title.trim().ifEmpty { " " }
        val headlineAttr = AttributedString(headlineText).apply { addAttribute(TextAttribute.FONT, serif) }
        val measurer = LineBreakMeasurer(headlineAttr.iterator, frc)
        val lines = buildList {
            while (measurer.position < headlineText.length) add(measurer.nextLayout(contentW.toFloat()))
        }
        val lineGap = 8
        val headlineHeight = lines.sumOf { (it.ascent + it.descent + it.leading).toInt() + lineGap }
        val bylines = listOfNotNull(source, author).map { it.trim() }.filter { it.isNotBlank() }
            .map { ellipsize(it.uppercase(Locale.ROOT), scratch.getFontMetrics(sans), contentW) }
        val footerMetrics = scratch.getFontMetrics(footerFont)
        scratch.dispose()

        val height = MARGIN + MAST_TO_BASELINE + BASELINE_TO_RULE1 + RULE1_TO_RULE2 + RULE2_TO_HEADLINE +
            headlineHeight + HEADLINE_TO_RULE3 + RULE3_TO_BYLINE + bylines.size * BYLINE_LINE_HEIGHT +
            BYLINE_TO_FOOTER + footerMetrics.height + MARGIN

        val image = BufferedImage(W, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.color = PAPER
        g.fillRect(0, 0, W, height)

        var y = MARGIN
        g.color = INK
        g.font = wordmark
        y += MAST_TO_BASELINE
        g.drawString("Nooz", (W - g.fontMetrics.stringWidth("Nooz")) / 2, y)
        y += BASELINE_TO_RULE1
        g.stroke = java.awt.BasicStroke(3f); g.drawLine(MARGIN, y, W - MARGIN, y); y += RULE1_TO_RULE2
        g.stroke = java.awt.BasicStroke(1.5f); g.drawLine(MARGIN, y, W - MARGIN, y); y += RULE2_TO_HEADLINE

        var lineY = y.toFloat()
        for (layout in lines) {
            lineY += layout.ascent
            layout.draw(g, MARGIN.toFloat(), lineY)
            lineY += layout.descent + layout.leading + lineGap
        }
        y += headlineHeight + HEADLINE_TO_RULE3
        g.drawLine(MARGIN, y, W - MARGIN, y); y += RULE3_TO_BYLINE

        g.color = MUTED
        g.font = sans
        for (line in bylines) { g.drawString(line, MARGIN, y); y += BYLINE_LINE_HEIGHT }
        y += BYLINE_TO_FOOTER

        g.font = footerFont
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))
        val footer = "$date  ·  clipped with Nooz"
        g.drawString(footer, (W - g.fontMetrics.stringWidth(footer)) / 2, y + footerMetrics.ascent)
        g.dispose()

        val out = java.io.ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    private fun ellipsize(text: String, metrics: java.awt.FontMetrics, maxWidth: Int): String {
        if (metrics.stringWidth(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && metrics.stringWidth(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }
}
