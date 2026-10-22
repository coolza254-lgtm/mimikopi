package com.id.mimikopi.ui.songs

import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import com.id.mimikopi.ui.components.Bravura
import com.id.mimikopi.ui.theme.Tokens

/**
 * The treble clef centred in its circle. Bravura draws the clef from its baseline far below the line box's middle, so
 * it is lifted by 0.78 em (from the glyph's bounds, -0.66 to 1.1 em) to sit in the middle.
 */
@Composable
fun ClefGlyph(size: TextUnit) {
    val lift = with(LocalDensity.current) { (size * 0.78f).toDp() }
    Text("", fontFamily = Bravura, fontSize = size, color = Tokens.Palette.Accent, modifier = Modifier.offset(y = -lift))
}
