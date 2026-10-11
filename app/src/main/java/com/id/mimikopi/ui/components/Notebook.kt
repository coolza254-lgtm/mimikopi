package com.id.mimikopi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.id.mimikopi.ui.theme.Tokens
import kotlin.math.PI
import kotlin.math.sin

/** A gentle hand-drawn-looking wavy rule, used between sections. Pure decoration. */
@Composable
fun WavyDivider(modifier: Modifier = Modifier) {
    val color = Tokens.Palette.Rose.copy(alpha = 0.55f)
    Canvas(modifier.fillMaxWidth().height(12.dp)) {
        val path = Path()
        val mid = size.height / 2
        val amp = size.height / 4
        val wave = 28.dp.toPx()
        path.moveTo(0f, mid)
        var x = 0f
        while (x <= size.width) {
            path.lineTo(x, mid + amp * sin(2 * PI * x / wave).toFloat())
            x += 2f
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Rounded "notebook page" card with an optional title: light page, hairline border, no shadow. */
@Composable
fun PageCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = Tokens.Palette.Card)
    val border = BorderStroke(Tokens.border, accent ?: Tokens.Palette.Line)
    val shape = RoundedCornerShape(Tokens.Radius.card)
    val elevation = CardDefaults.cardElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(Tokens.Space.m)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, border = border, elevation = elevation, content = inner)
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = shape, colors = colors, border = border, elevation = elevation, content = inner)
    }
}

/** Notebook paper: cream with a faint dot grid. Pure decoration behind the content. */
fun Modifier.paper(): Modifier = this.background(Tokens.Palette.Paper).drawWithCache {
    // One drawPoints call per frame; the grid is rebuilt only when the size changes.
    val step = 18.dp.toPx()
    val w = size.width
    val h = size.height
    val points = ArrayList<Offset>()
    var y = step / 2
    while (y < h) {
        var x = step / 2
        while (x < w) { points += Offset(x, y); x += step }
        y += step
    }
    val dot = 2.dp.toPx()
    onDrawBehind { drawPoints(points, PointMode.Points, Tokens.Palette.Dot, strokeWidth = dot, cap = StrokeCap.Round) }
}

/** Small rounded label (block minutes, "นับผ่าน", unverified facts). */
@Composable
fun Pill(text: String, background: Color, color: Color = Tokens.Palette.Ink, modifier: Modifier = Modifier, dashed: Boolean = false) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .then(if (dashed) Modifier.border(1.dp, Tokens.Palette.InkMuted.copy(alpha = 0.6f), shape) else Modifier.background(background, shape))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** A rounded progress bar (0..1) on the light track. */
@Composable
fun MeterBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Tokens.Palette.Rose, height: androidx.compose.ui.unit.Dp = 8.dp) {
    Box(modifier.height(height).clip(RoundedCornerShape(height / 2)).background(Tokens.Palette.Track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(RoundedCornerShape(height / 2)).background(color))
    }
}

/** The mustard strip of tape on the hero card. */
@Composable
fun Tape(modifier: Modifier = Modifier) {
    Box(modifier.size(64.dp, 20.dp).rotate(-3f).background(Tokens.Palette.Mustard.copy(alpha = 0.85f), RoundedCornerShape(3.dp)))
}
