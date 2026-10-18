package com.id.mimikopi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.ui.theme.Tokens

/*
 * Plain charts for the progress pages: ink on paper, one hairline baseline, no colour, no animation. Labels sit under
 * the chart (first, middle, last) so the shape stays the message.
 */

/** Bars for a series ([values] as fractions of the tallest, null = no data that period), with labels under. */
@Composable
fun BarChart(values: List<Float?>, labels: List<String>, modifier: Modifier = Modifier, height: Dp = 110.dp) {
    val ink = Tokens.Palette.Ink
    val line = Tokens.Palette.Line
    val hair = Tokens.Palette.Hair
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = (slot * 0.62f).coerceAtMost(28.dp.toPx())
            drawLine(hair, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            values.forEachIndexed { i, v ->
                val x = i * slot + (slot - barW) / 2
                if (v == null || v <= 0f) {
                    drawRoundRect(line, Offset(x, size.height - 3.dp.toPx()), Size(barW, 3.dp.toPx()), CornerRadius(1.5f.dp.toPx()))
                } else {
                    val h = (size.height * v.coerceIn(0.03f, 1f))
                    drawRoundRect(ink, Offset(x, size.height - h), Size(barW, h), CornerRadius(3.dp.toPx()))
                }
            }
        }
        ChartLabels(labels)
    }
}

/**
 * A step line through [points] (x and y as fractions 0..1, x increasing): the value holds until the next point, as
 * levels do between tests.
 */
@Composable
fun StepChart(points: List<Pair<Float, Float>>, labels: List<String>, modifier: Modifier = Modifier, height: Dp = 110.dp) {
    val ink = Tokens.Palette.Ink
    val hair = Tokens.Palette.Hair
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val pad = 3.dp.toPx()
            fun y(v: Float) = size.height - pad - v.coerceIn(0f, 1f) * (size.height - 2 * pad)
            drawLine(hair, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            if (points.isEmpty()) return@Canvas
            val path = Path()
            points.forEachIndexed { i, (px, py) ->
                val x = px.coerceIn(0f, 1f) * size.width
                if (i == 0) path.moveTo(x, y(py)) else {
                    path.lineTo(x, y(points[i - 1].second))
                    path.lineTo(x, y(py))
                }
            }
            path.lineTo(size.width, y(points.last().second))
            drawPath(path, ink, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(ink, 3.5f.dp.toPx(), Offset(size.width - 1.dp.toPx(), y(points.last().second)))
        }
        ChartLabels(labels)
    }
}

@Composable
private fun ChartLabels(labels: List<String>) {
    if (labels.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        labels.forEachIndexed { i, l ->
            Text(l, fontSize = 11.sp, color = Tokens.Palette.Dim)
            if (i < labels.size - 1) Spacer(Modifier.weight(1f))
        }
    }
}
