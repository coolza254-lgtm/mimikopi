package com.id.mimikopi.desktop.port.components

// Ported from the Android app (app/src/main/java/com/id/mimikopi/ui/components/DrillProgress.kt): imports changed, other changes marked "Desktop:".

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.desktop.ui.Tokens

/** Question [index] of [total]: a thin line that fills, then "07/20" in mono. Use inside a Row. */
@Composable
fun androidx.compose.foundation.layout.RowScope.DrillProgress(index: Int, total: Int) {
    MeterBar(if (total == 0) 0f else index.toFloat() / total, Modifier.weight(1f))
    Text("%02d/%02d".format(index, total), fontFamily = Tokens.Fonts.mono, fontSize = 13.sp, color = Tokens.Palette.Ink,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp))
}
