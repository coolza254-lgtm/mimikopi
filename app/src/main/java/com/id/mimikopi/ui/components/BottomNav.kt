package com.id.mimikopi.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.id.mimikopi.R
import com.id.mimikopi.ui.theme.Tokens

/** A top-level destination shown in the bottom bar. */
data class Tab(val route: String, val label: Int, val icon: Int)

val TABS = listOf(
    Tab("home", R.string.tab_home, R.drawable.ic_nav_home),
    Tab("today", R.string.tab_today, R.drawable.ic_nav_today),
    Tab("piano", R.string.tab_piano, R.drawable.ic_nav_piano),
    Tab("songs", R.string.tab_songs, R.drawable.ic_nav_songs),
    Tab("progress", R.string.tab_me, R.drawable.ic_nav_me),
)

/** The bottom bar of the redesign: five tabs, the current one marked with a soft rose pill. */
@Composable
fun BottomNav(current: String?, onSelect: (String) -> Unit) {
    NavigationBar(containerColor = Tokens.Palette.Card, tonalElevation = 0.dp) {
        TABS.forEach { tab ->
            val selected = current == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onSelect(tab.route) },
                icon = { Icon(painterResource(tab.icon), contentDescription = null, modifier = Modifier.size(22.dp)) },
                label = { Text(stringResource(tab.label), style = MaterialTheme.typography.labelMedium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Tokens.Palette.Ink,
                    selectedTextColor = Tokens.Palette.Ink,
                    indicatorColor = Tokens.Palette.RoseSoft,
                    unselectedIconColor = Tokens.Palette.InkMuted,
                    unselectedTextColor = Tokens.Palette.InkMuted,
                ),
            )
        }
    }
}
