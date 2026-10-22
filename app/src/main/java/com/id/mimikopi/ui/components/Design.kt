package com.id.mimikopi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.R
import com.id.mimikopi.ui.theme.Tokens

/*
 * The building blocks of the main pages (Design J "มินิมอลกลมกล่อม"): a large title, white rounded blocks of rows lifted
 * by a soft shadow, one accent capsule button per page, a segmented control on a track rail. Everything is round;
 * size, weight and one accent colour make the hierarchy.
 */

/** Page width and side margin for the main pages. */
val PageMaxWidth: Dp = 680.dp
val PageMargin: Dp = 20.dp

/** A small grey heading above a group ("แผนวันนี้"). */
@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Tokens.Palette.InkMuted,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 8.dp))
}

/** A note under a group, in small grey type. */
@Composable
fun GroupFooter(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = Tokens.Palette.InkMuted, modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
}

/** A rounded block of rows on a soft fill; rows are separated with [GroupDivider]. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(Tokens.Radius.cardSmall)
    Column(modifier.fillMaxWidth().softShadow(shape, small = true).clip(shape).background(Tokens.Palette.Group), content = content)
}

/** The line between rows of a group, indented like the system lists. */
@Composable
fun GroupDivider(indent: Dp = 16.dp) {
    Box(Modifier.padding(start = indent).fillMaxWidth().height(1.dp).background(Tokens.Palette.Hair))
}

/**
 * One row: a title, an optional line under it, an optional value on the right and a chevron when it opens something.
 * [leading] is a small mark on the left (a tick, a number).
 */
@Composable
fun GroupRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    titleColor: Color = Tokens.Palette.Ink,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, lineHeight = 22.sp, color = titleColor)
            subtitle?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 2.dp)) }
        }
        value?.let { Text(it, fontSize = 15.sp, color = Tokens.Palette.InkMuted, textAlign = TextAlign.End) }
        trailing?.invoke()
        if (onClick != null && trailing == null) Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = Tokens.Palette.Dim, modifier = Modifier.size(16.dp))
    }
}

/** The page's one main action: an accent capsule, white 16/600, with a ▶ in front when it starts practice ([play]). */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, play: Boolean = false) {
    Row(
        modifier.fillMaxWidth().heightIn(min = Tokens.minTouch).clip(RoundedCornerShape(28.dp))
            .background(if (enabled) Tokens.Palette.Accent else Tokens.Palette.Track)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (enabled) Tokens.Palette.OnAccent else Tokens.Palette.InkMuted
        if (play) Icon(painterResource(R.drawable.ic_play_fill), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** A quieter action: a white capsule with a soft shadow, ink 15/600. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier.heightIn(min = 48.dp).softShadow(shape, small = true).clip(shape).background(Tokens.Palette.Group)
            .clickable(onClick = onClick).padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Ink) }
}

/** A segmented control: one choice among a few, the chosen one raised. */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    // Design J: a track rail, the chosen option a white capsule with a small shadow.
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Tokens.Palette.Track).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { o ->
            val on = o == selected
            val shape = RoundedCornerShape(19.dp)
            Box(
                Modifier.weight(1f).heightIn(min = 38.dp)
                    .then(if (on) Modifier.shadow(2.dp, shape, ambientColor = Tokens.Elevation.tint.copy(alpha = 0.12f), spotColor = Tokens.Elevation.tint.copy(alpha = 0.12f))
                        .clip(shape).background(Tokens.Palette.Group) else Modifier.clip(shape))
                    .clickable { onSelect(o) },
                contentAlignment = Alignment.Center,
            ) { Text(label(o), fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) Tokens.Palette.Ink else Tokens.Palette.InkMuted) }
        }
    }
}

/** Levels as short bars, the earned ones filled. */
@Composable
fun LevelBars(level: Int, max: Int, modifier: Modifier = Modifier, width: Dp = 14.dp) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(max) { i -> Box(Modifier.width(width).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (i < level) Tokens.Palette.Accent else Tokens.Palette.Track)) }
    }
}

/** A round mark at the start of a plan row: done (filled with a tick), current (a ring) or later (faint). */
@Composable
fun StepMark(done: Boolean, current: Boolean) {
    Box(
        Modifier.size(24.dp).then(
            when {
                done -> Modifier.background(Tokens.Palette.Accent, CircleShape)
                current -> Modifier.border(2.dp, Tokens.Palette.Accent, CircleShape)
                else -> Modifier.border(1.5.dp, Tokens.Palette.Dim, CircleShape)
            },
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Tokens.Palette.OnAccent, modifier = Modifier.size(14.dp))
    }
}

/** The large page title with an optional back arrow and whatever sits at the right (MIDI, settings). */
@Composable
fun LargeTitle(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)) {
        Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) BackCircle(onBack)
            Spacer(Modifier.weight(1f))
            trailing()
        }
        Text(title, fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
        subtitle?.let { Text(it, fontSize = 14.sp, lineHeight = 20.sp, color = Tokens.Palette.InkMuted) }
    }
}

/** MIDI state at the top right: a dot (filled = connected) and a word; tap opens the MIDI page. */
@Composable
fun MidiPill(onClick: () -> Unit) {
    val ready = midiConnected()
    Row(
        Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).then(if (ready) Modifier.background(Tokens.Palette.Positive, CircleShape) else Modifier.border(1.dp, Tokens.Palette.InkMuted, CircleShape)))
        Text(if (ready) "MIDI" else "ไม่มี MIDI", fontSize = 13.sp, color = if (ready) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
    }
}

/** The round back button of Design J: a white 44 dp circle with a soft shadow and a chevron. */
@Composable
fun BackCircle(onBack: () -> Unit) {
    Box(
        Modifier.size(44.dp).softShadow(CircleShape, small = true).clip(CircleShape).background(Tokens.Palette.Group).clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back), tint = Tokens.Palette.Ink, modifier = Modifier.size(20.dp))
    }
}

/** A capsule chip in a skill's pastel (or accentSoft when selected). */
@Composable
fun Chip(text: String, color: Tokens.SkillColor? = null, selected: Boolean = false, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val fill = color?.fill ?: if (selected) Tokens.Palette.AccentSoft else Tokens.Palette.Track
    val ink = color?.ink ?: if (selected) Tokens.Palette.AccentDeep else Tokens.Palette.InkMuted
    Box(
        modifier.heightIn(min = 34.dp).clip(RoundedCornerShape(50)).background(fill)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ink) }
}

/** A white round icon holder on a skill card (40 dp), the icon in the skill's colour. */
@Composable
fun SkillIcon(icon: Int, color: Tokens.SkillColor, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Tokens.Palette.Group), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = color.ink, modifier = Modifier.size(size / 2))
    }
}
