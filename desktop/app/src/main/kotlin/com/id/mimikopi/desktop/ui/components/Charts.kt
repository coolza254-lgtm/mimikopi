package com.id.mimikopi.desktop.ui.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.desktop.ui.theme.Tokens

/*
 * Charts for the progress pages (Design J): soft rounded bars stepping from the pale to the full colour of the series,
 * the latest period in full; no axis, no animation. Labels sit under the chart so the shape stays the message.
 */

/** Bars for a series ([values] as fractions of the tallest, null = no data that period), with labels under. */
@Composable
fun BarChart(
    values: List<Float?>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 110.dp,
    steps: List<Color> = listOf(Tokens.Palette.AccentSoft, Tokens.Palette.AccentMid, Tokens.Palette.AccentMidStrong, Tokens.Palette.Accent),
) {
    val empty = Tokens.Palette.Track
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = (slot * 0.78f).coerceAtMost(40.dp.toPx())
            val radius = CornerRadius((barW * 0.3f).coerceAtMost(12.dp.toPx()))
            values.forEachIndexed { i, v ->
                val x = i * slot + (slot - barW) / 2
                if (v == null || v <= 0f) {
                    drawRoundRect(empty, Offset(x, size.height - 6.dp.toPx()), Size(barW, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
                } else {
                    val h = (size.height * v.coerceIn(0.12f, 1f))
                    // The latest bar in full colour; the others step up with their height.
                    val color = if (i == values.lastIndex) steps.last() else steps[((v * (steps.size - 1)).toInt()).coerceIn(0, steps.size - 2)]
                    drawRoundRect(color, Offset(x, size.height - h), Size(barW, h), radius)
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
    val ink = Tokens.Palette.Accent
    val hair = Tokens.Palette.Track
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val pad = 5.dp.toPx()
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
            drawPath(path, ink, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            drawCircle(ink, 5.dp.toPx(), Offset(size.width - 1.dp.toPx(), y(points.last().second)))
        }
        ChartLabels(labels)
    }
}

@Composable
private fun ChartLabels(labels: List<String>) {
    if (labels.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        labels.forEachIndexed { i, l ->
            Text(l, fontSize = 12.sp, color = Tokens.Palette.InkMuted)
            if (i < labels.size - 1) Spacer(Modifier.weight(1f))
        }
    }
}
