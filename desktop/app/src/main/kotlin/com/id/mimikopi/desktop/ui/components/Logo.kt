package com.id.mimikopi.desktop.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's logo, the phone's launcher icon (docs/brand/icon.svg, made by tools/brand/make_icon.py): a pink spiral
 * notebook with a yellow eighth note on cream. The user asked for it on the desktop too (2026-10-23).
 */
val MimikopiLogo: ImageVector by lazy {
    ImageVector.Builder("logo", 108.dp, 108.dp, 108f, 108f).apply {
        addPath(addPathNodes("M0,0 H108 V108 H0 Z"), fill = SolidColor(Color(0xFFFEF5E6)))
        addGroup(translationX = -22.0561f, translationY = -20.4766f, scaleX = 0.121495f, scaleY = 0.121495f)
        addPath(
            addPathNodes("M528,415 H759 A50,50 0 0 1 809,465 V761 A50,50 0 0 1 759,811 H528 A50,50 0 0 1 478,761 V465 A50,50 0 0 1 528,415 Z"),
            stroke = SolidColor(Color(0xFFE6889E)), strokeLineWidth = 32f,
        )
        addPath(
            addPathNodes(
                "M452,466 H500 A25,25 0 0 1 500,516 H452 A25,25 0 0 1 452,466 Z M452,546 H500 A25,25 0 0 1 500,596 H452 A25,25 0 0 1 452,546 Z " +
                    "M452,626 H500 A25,25 0 0 1 500,676 H452 A25,25 0 0 1 452,626 Z M452,706 H500 A25,25 0 0 1 500,756 H452 A25,25 0 0 1 452,706 Z",
            ),
            fill = SolidColor(Color(0xFFE6889E)),
        )
        val yellow = SolidColor(Color(0xFFF3BE52))
        addPath(addPathNodes("M577.3,697.2 A46,36 -22 1 0 662.7,662.8 A46,36 -22 1 0 577.3,697.2 Z"), fill = yellow)
        addPath(addPathNodes("M646,680 V500 H662 V676 Z"), fill = yellow)
        addPath(addPathNodes("M662,500 C670,540 712,548 735,568 C752,584 752,606 742,622 C746,598 732,582 708,574 C690,568 674,566 662,566 Z"), fill = yellow)
        clearGroup()
    }.build()
}

@Composable
fun Logo(size: Dp, modifier: Modifier = Modifier) {
    Image(MimikopiLogo, contentDescription = "ミミコピ", modifier = modifier.size(size).clip(RoundedCornerShape(size * 0.28f)))
}

/** A 24-unit line icon from SVG path data, drawn in [color] (the sidebar and other quiet places). */
fun lineIcon(name: String, color: Color, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        for (d in paths) addPath(addPathNodes(d), stroke = SolidColor(color), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()

private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0"

/** The sidebar icons, as in the approved design. */
object LineIcons {
    val today = listOf(circle(12f, 12f, 8f), circle(12f, 12f, 3f))
    val lessons = listOf("M4,6 H20 M4,12 H20 M4,18 H14")
    val skills = listOf("M5,20 V10 M12,20 V4 M19,20 V13")
    val songs = listOf(circle(7f, 18f, 3f), circle(17f, 16f, 3f), "M10,18 V5 L20,3 V16")
    val ear = listOf("M7,9 A5,5 0 0 1 17,9 C17,13 13,13 13,17 A3,3 0 0 1 7,17")
    val progress = listOf("M4,18 L10,12 L14,16 L20,8")
    val piano = listOf("M5,5 H19 A2,2 0 0 1 21,7 V17 A2,2 0 0 1 19,19 H5 A2,2 0 0 1 3,17 V7 A2,2 0 0 1 5,5 Z", "M8,5 V14 M12,5 V14 M16,5 V14")
    val settings = listOf(circle(12f, 12f, 3f), "M12,2 V5 M12,19 V22 M2,12 H5 M19,12 H22 M5,5 L7,7 M17,17 L19,19 M5,19 L7,17 M17,7 L19,5")
}
