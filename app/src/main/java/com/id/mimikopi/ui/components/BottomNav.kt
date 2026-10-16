package com.id.mimikopi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.R
import com.id.mimikopi.ui.theme.Tokens

/** A top-level destination shown in the bottom bar. */
data class Tab(val route: String, val label: Int)

val TABS = listOf(
    Tab("reading", R.string.tab_reading),
    Tab("ear", R.string.tab_ear),
    Tab("songs", R.string.tab_songs),
    Tab("sections", R.string.tab_sections),
)

/** The bottom bar (M15): four words, no icons; the current tab is white with a small dot above it. */
@Composable
fun BottomNav(current: String?, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Tokens.Palette.Paper).navigationBarsPadding()) {
        Hairline()
        Row(Modifier.fillMaxWidth().height(64.dp)) {
            TABS.forEach { tab ->
                val selected = current == tab.route
                Column(
                    Modifier.weight(1f).fillMaxHeight()
                        .selectable(selected = selected, role = Role.Tab) { if (!selected) onSelect(tab.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.size(4.dp).then(if (selected) Modifier.background(Tokens.Palette.Ink, CircleShape) else Modifier))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(tab.label),
                        fontFamily = Tokens.Fonts.body,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) Tokens.Palette.Ink else Tokens.Palette.InkMuted,
                    )
                }
            }
        }
    }
}
