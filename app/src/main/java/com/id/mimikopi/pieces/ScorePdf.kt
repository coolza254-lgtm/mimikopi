package com.id.mimikopi.pieces

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.res.ResourcesCompat
import com.id.mimikopi.R
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.core.score.ScoreLayout
import java.io.OutputStream

/**
 * PDF export with the same engraving as the screen (M0 plan: PdfDocument, no extra library). A4 portrait, the
 * score wrapped into systems that fit the page width, title and composer on the first page, page numbers.
 */
class ScorePdf(private val context: Context) {
    private val pageW = 595
    private val pageH = 842
    private val margin = 42f
    private val space = 5.2f // points per staff space
    private val systemSpaces = 26f // height of one grand-staff system incl. room for ledger lines and chord symbols

    fun write(score: Score, out: OutputStream) {
        val bravura = ResourcesCompat.getFont(context, R.font.bravura) ?: Typeface.DEFAULT
        val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bravura; textSize = space * 4; color = Color.BLACK }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = space * 2.2f }
        val bold = Paint(text).apply { typeface = Typeface.DEFAULT_BOLD }

        val widthSpaces = ((pageW - 2 * margin) / space).toDouble()
        val systems = ScoreLayout.systems(score, widthSpaces)
        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            val c = page!!.canvas
            y = margin
            if (pageNo == 1) {
                val title = Paint(bold).apply { textSize = 20f; textAlign = Paint.Align.CENTER }
                c.drawText(score.title, pageW / 2f, y + 20f, title)
                if (score.composer.isNotBlank()) c.drawText(score.composer, pageW - margin, y + 44f, Paint(text).apply { textSize = 10f; textAlign = Paint.Align.RIGHT })
                y += 60f
            }
            c.drawText("$pageNo", pageW / 2f, pageH - 20f, Paint(text).apply { textSize = 9f; textAlign = Paint.Align.CENTER })
        }
        newPage()
        for (range in systems) {
            if (y + systemSpaces * space > pageH - margin) newPage()
            val c = page!!.canvas
            val layout = ScoreLayout.of(score.slice(range.first, range.last))
            drawSystem(c, layout, margin, y, glyph, line, text, bold, range.first)
            y += systemSpaces * space
        }
        page?.let { doc.finishPage(it) }
        doc.writeTo(out)
        doc.close()
    }

    private fun drawSystem(c: Canvas, l: ScoreLayout, x0: Float, top: Float, glyph: Paint, line: Paint, text: Paint, bold: Paint, firstMeasure: Int) {
        // y = 0 (treble middle line) sits 9 spaces below the top of the system.
        fun px(x: Double) = x0 + x.toFloat() * space
        fun py(y: Double) = top + (y.toFloat() + 9f) * space
        for (ln in l.lines) {
            line.strokeWidth = ln.thickness.toFloat() * space
            c.drawLine(px(ln.x1), py(ln.y1), px(ln.x2), py(ln.y2), line)
        }
        for (g in l.glyphs) c.drawText(g.codepoint.toString(), px(g.x), py(g.y), glyph)
        for (t in l.ties) {
            val path = Path()
            val y = py(t.y)
            val bulge = (if (t.above) -1f else 1f) * space * 0.9f
            path.moveTo(px(t.x1), y)
            path.quadTo((px(t.x1) + px(t.x2)) / 2, y + bulge * 2, px(t.x2), y)
            line.strokeWidth = space * 0.16f
            c.drawPath(path, line)
        }
        for (lb in l.labels) {
            val p = if (lb.kind == ScoreLayout.LabelKind.CHORD) bold else Paint(text).apply { textAlign = Paint.Align.CENTER; textSize = space * 1.6f }
            c.drawText(lb.text, px(lb.x), py(lb.y) + p.textSize / 3, p)
        }
        // Measure number at the start of each system.
        c.drawText("${firstMeasure + 1}", px(0.0), py(ScoreLayout.TOP_Y - 2.0), Paint(text).apply { textSize = space * 1.5f })
    }
}
