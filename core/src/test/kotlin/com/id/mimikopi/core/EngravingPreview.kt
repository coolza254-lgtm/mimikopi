package com.id.mimikopi.core

import com.id.mimikopi.core.exercise.ScoreGenerators
import com.id.mimikopi.core.lesson.ExerciseType
import com.id.mimikopi.core.lesson.Lesson
import com.id.mimikopi.core.score.PieceFile
import com.id.mimikopi.core.score.Score
import com.id.mimikopi.core.score.ScoreLayout
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test

/**
 * Draws real layouts to HTML the way the apps' ScoreView draws them (same mapping, Bravura at four staff spaces),
 * for looking at the engraving on a machine without the apps. Opt-in: set MIMIKOPI_ENGRAVE_DIR to a folder.
 */
class EngravingPreview {
    private val space = 14.0

    @Test
    fun drawSamples() {
        val dir = System.getenv("MIMIKOPI_ENGRAVE_DIR")?.let(::File)
        assumeTrue("set MIMIKOPI_ENGRAVE_DIR to draw", dir != null)
        dir!!.mkdirs()
        val font = File("../app/src/main/res/font/bravura.otf").absoluteFile.toURI()
        val lessons = File("../content/lessons").listFiles()!!.filter { it.name.endsWith(".json") }.map { Lesson.parse(it.readText()) }
        fun drill(type: ExerciseType, nth: Int = 0) = lessons.sortedBy { it.id.substringBefore('.').toInt() * 100 + it.id.substringAfter('.').toInt() }
            .flatMap { l -> l.drills.filter { it.type == type }.map { l to it } }.getOrNull(nth)
        val samples = buildList {
            drill(ExerciseType.E_FINGER_PATTERN)?.let { (l, d) -> add("finger-${l.id}" to Score.from(ScoreGenerators.fingerPatterns(d, 7))) }
            drill(ExerciseType.E_READ_PIECE)?.let { (l, d) -> add("read-${l.id}" to Score.from(ScoreGenerators.read(d, 7))) }
            drill(ExerciseType.E_READ_PIECE, 6)?.let { (l, d) -> add("read-${l.id}" to Score.from(ScoreGenerators.read(d, 7))) }
            for (id in listOf("heroic-polonaise-theme")) add(id to Score.from(PieceFile.parse(File("../content/pieces/$id.json").readText())))
            for (id in listOf("ex-scale-c-both", "ex-lh-patterns-c")) add(id to Score.from(PieceFile.parse(File("../content/exercises/$id.json").readText())))
        }
        val body = StringBuilder()
        for ((name, score) in samples) {
            val lines = ScoreLayout.systems(score, 90.0)
            body.append("<h2>$name</h2>")
            for (r in lines) {
                val slice = score.slice(r.first, r.last)
                val end = r.last == score.measures - 1
                body.append(svg(ScoreLayout.justified(slice, 90.0, finalBar = end, timeSignature = r.first == 0), r.first + 1))
            }
        }
        File(dir, "engraving.html").writeText(
            """<!doctype html><html><head><meta charset="utf-8"><style>
            @font-face{font-family:Bravura;src:url('$font')}
            body{margin:24px;background:#fff;font-family:sans-serif} h2{font-size:14px;color:#888;margin:28px 0 0}
            </style></head><body>$body</body></html>""",
        )
    }

    private fun svg(l: ScoreLayout, firstBar: Int): String {
        val s = space
        fun px(x: Double) = x * s + s
        fun py(y: Double) = (y - (l.top - ScoreLayout.PAD_ABOVE)) * s
        val w = px(l.width) + s
        val h = l.spacesTall * s
        val out = StringBuilder("""<svg xmlns="http://www.w3.org/2000/svg" width="$w" height="$h" viewBox="0 0 $w $h">""")
        // As the apps draw it: the bar number once, at the start of the line above the clef.
        out.append("""<text x="${px(0.0)}" y="${py(l.top - 2.4)}" font-size="${s * 1.2}" fill="#999">$firstBar</text>""")
        for (ln in l.lines) out.append("""<line x1="${px(ln.x1)}" y1="${py(ln.y1)}" x2="${px(ln.x2)}" y2="${py(ln.y2)}" stroke="#000" stroke-width="${ln.thickness * s}"/>""")
        for (g in l.glyphs) out.append("""<text x="${px(g.x)}" y="${py(g.y)}" font-family="Bravura" font-size="${s * 4}">&#x${g.codepoint.code.toString(16)};</text>""")
        for (lb in l.labels) out.append("""<text x="${px(lb.x)}" y="${py(lb.y)}" text-anchor="middle" dominant-baseline="central" font-size="${s * 1.3}" font-weight="bold">${lb.text}</text>""")
        for (t in l.ties) {
            val x1 = px(t.x1); val x2 = px(t.x2); val y = py(t.y); val b = (if (t.above) -1 else 1) * s * 0.9
            out.append("""<path d="M$x1 ${y}Q${(x1 + x2) / 2} ${y + b * 2} $x2 $y" fill="none" stroke="#000" stroke-width="${s * 0.16}"/>""")
        }
        return out.append("</svg><br>").toString()
    }
}
