package com.id.mimikopi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.R
import com.id.mimikopi.ui.theme.Tokens

/** A top-level destination shown in the bottom bar. */
data class Tab(val route: String, val label: Int, val icon: Int)

val TABS = listOf(
    Tab("today", R.string.tab_today, R.drawable.ic_nav_today),
    Tab("skills", R.string.tab_skills, R.drawable.ic_nav_skills),
    Tab("piano", R.string.tab_piano, R.drawable.ic_nav_piano),
    Tab("songs", R.string.tab_songs, R.drawable.ic_nav_songs),
)

/**
 * The bottom bar (Design J): a white capsule floating 20/18 from the edges with a soft shadow. The current tab is an
 * accentSoft capsule with its icon and word; the others show only their icon (their name is read by TalkBack).
 */
@Composable
fun BottomNav(current: String?, onSelect: (String) -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 18.dp)) {
        Row(
            Modifier.fillMaxWidth().height(64.dp)
                .shadow(Tokens.Elevation.raised, shape, ambientColor = Tokens.Elevation.tint, spotColor = Tokens.Elevation.tint)
                .clip(shape).background(Tokens.Palette.Group).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TABS.forEach { tab ->
                val selected = current == tab.route
                val label = stringResource(tab.label)
                Row(
                    Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(26.dp))
                        .then(if (selected) Modifier.background(Tokens.Palette.AccentSoft) else Modifier)
                        .selectable(selected = selected, role = Role.Tab) { if (!selected) onSelect(tab.route) }
                        .semantics { contentDescription = label },
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(tab.icon), contentDescription = null,
                        tint = if (selected) Tokens.Palette.AccentDeep else Tokens.Palette.InkMuted,
                        modifier = Modifier.size(if (selected) 19.dp else 22.dp))
                    if (selected) Text(label, fontFamily = Tokens.Fonts.body, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.AccentDeep)
                }
            }
        }
    }
}
