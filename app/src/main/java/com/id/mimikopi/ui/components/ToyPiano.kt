package com.id.mimikopi.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.id.mimikopi.core.input.ToyPianoArt

/** The pink toy piano shown beside the piano's name (drawn from [ToyPianoArt], the same picture as on the PC). */
@Composable
fun ToyPiano(size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Image(toyPianoVector, contentDescription = null, modifier = modifier.size(size))
}

private val toyPianoVector: ImageVector by lazy {
    val b = ImageVector.Builder("toyPiano", 64.dp, 64.dp, ToyPianoArt.SIZE, ToyPianoArt.SIZE)
    for (layer in ToyPianoArt.LAYERS) {
        val color = SolidColor(Color(layer.color))
        if (layer.stroke > 0f) {
            b.addPath(addPathNodes(layer.path), stroke = color, strokeLineWidth = layer.stroke, strokeLineCap = StrokeCap.Round)
        } else {
            b.addPath(addPathNodes(layer.path), fill = color)
        }
    }
    b.build()
}
