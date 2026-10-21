package com.id.mimikopi.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Stage = Color(0xFF202428)

/** In landscape playback the controls overlay the canvas, so no row steals height from the music or keys. */
@Composable
internal fun FallingViewport(
    fullscreen: Boolean, showKeyLabels: Boolean, onKeyLabels: (Boolean) -> Unit,
    status: String, onStop: () -> Unit, modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    if (fullscreen) Box(modifier.background(Stage).testTag("falling-viewport")) {
        content(Modifier.matchParentSize().testTag("falling-canvas"))
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top,
        ) {
            Row(Modifier.weight(1f, fill = false).background(Stage.copy(alpha = .85f), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("● ซ้าย", color = Color(0xFF78AFE1), fontSize = 11.sp)
                Text("● ขวา", color = Color(0xFFA1EF52), fontSize = 11.sp)
                Text(status, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
            Row(Modifier.background(Stage.copy(alpha = .93f), RoundedCornerShape(16.dp)).padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                KeyLabelSwitch(showKeyLabels, onKeyLabels)
                TextButton(onClick = onStop, modifier = Modifier.heightIn(min = 48.dp).testTag("falling-stop")) {
                    Text("หยุด", color = Color.White)
                }
            }
        }
    } else Column(modifier.background(Stage).testTag("falling-viewport")) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("● ซ้าย", color = Color(0xFF78AFE1), fontSize = 12.sp)
            Text("● ขวา", color = Color(0xFFA1EF52), fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            KeyLabelSwitch(showKeyLabels, onKeyLabels)
        }
        content(Modifier.weight(1f).fillMaxWidth().testTag("falling-canvas"))
    }
}

@Composable
private fun KeyLabelSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Text("ชื่อบนคีย์", color = Color.White, fontSize = 12.sp)
    Switch(checked = checked, onCheckedChange = onChange,
        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFA1EF52), checkedThumbColor = Stage),
        modifier = Modifier.testTag("falling-key-labels").semantics { contentDescription = "แสดงชื่อโน้ตบนคีย์" })
}
