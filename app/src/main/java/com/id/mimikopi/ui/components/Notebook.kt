package com.id.mimikopi.ui.components

import androidx.compose.foundation.Canvas
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

/** Rounded "notebook page" card with an optional title. */
@Composable
fun PageCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = Tokens.Palette.Card)
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(Tokens.Space.m)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors, content = inner)
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = colors, content = inner)
    }
}
