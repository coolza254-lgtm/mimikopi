package com.id.mimikopi.ui.reading

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.id.mimikopi.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.ui.theme.Tokens

@Composable
internal fun ReadingRunBar(status: String, onStop: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("reading-run-bar"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(status, fontSize = 13.sp, color = Tokens.Palette.InkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Button(onClick = onStop, modifier = Modifier.heightIn(min = 48.dp).testTag("reading-stop")) { Text(stringResource(R.string.reader_stop)) }
    }
}
