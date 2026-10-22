package com.id.mimikopi.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.desktop.ui.theme.Tokens

/*
 * The building blocks of the pages, the same as the phone's (Android ui/components/Design.kt), Design J: large
 * titles, white rounded cards lifted by a soft shadow, one dusty-rose capsule button per page, a segmented control on
 * a track rail. Icons are plain characters so nothing depends on Android drawables.
 */

/** The soft rose-tinted shadow under cards (Design J uses shadows, not borders). */
fun Modifier.softShadow(shape: androidx.compose.ui.graphics.Shape, small: Boolean = false): Modifier =
    shadow(if (small) Tokens.Elevation.cardSmall else Tokens.Elevation.card, shape, ambientColor = Tokens.Elevation.tint, spotColor = Tokens.Elevation.tint)

/** Reading width of the text pages; music pages use the whole window. */
val PageMaxWidth: Dp = 760.dp
val PageMargin: Dp = 24.dp

/** A scrolling page column of readable width, centred in the window. */
@Composable
fun Page(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(Tokens.Palette.Paper), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = PageMargin, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Tokens.Palette.InkMuted,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp))
}

@Composable
fun GroupFooter(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = Tokens.Palette.InkMuted, modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp))
}

@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(Tokens.Radius.cardSmall)
    Column(modifier.fillMaxWidth().softShadow(shape, small = true).clip(shape).background(Tokens.Palette.Group), content = content)
}

@Composable
fun GroupDivider(indent: Dp = 16.dp) {
    Box(Modifier.padding(start = indent).fillMaxWidth().height(1.dp).background(Tokens.Palette.Hair))
}

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
            Text(title, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = titleColor)
            subtitle?.let { Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 2.dp)) }
        }
        value?.let { Text(it, fontSize = 15.sp, color = Tokens.Palette.InkMuted, textAlign = TextAlign.End) }
        trailing?.invoke()
        if (onClick != null && trailing == null) Text("›", fontSize = 22.sp, color = Tokens.Palette.Dim)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(Tokens.Radius.button))
            .background(if (enabled) Tokens.Palette.Accent else Tokens.Palette.Track)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (enabled) Tokens.Palette.OnAccent else Tokens.Palette.InkMuted)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        // The soft shadow keeps it visible inside a white card.
        modifier.heightIn(min = 44.dp).softShadow(RoundedCornerShape(22.dp), small = true).clip(RoundedCornerShape(22.dp)).background(Tokens.Palette.Group)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) Tokens.Palette.Ink else Tokens.Palette.Dim) }
}

/** A large, always-visible stop button for music pages (Space / Esc do the same). */
@Composable
fun StopButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = 48.dp).widthIn(min = 120.dp).clip(RoundedCornerShape(24.dp)).background(Tokens.Palette.Accent)
            .clickable(onClick = onClick).padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) { Text("❚❚  หยุด (Space)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.OnAccent) }
}

@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    // Design J: a track rail, the chosen option a white capsule with a small shadow.
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Tokens.Palette.Track).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { o ->
            val on = o == selected
            val shape = RoundedCornerShape(19.dp)
            Box(
                Modifier.weight(1f).heightIn(min = 38.dp)
                    .then(if (on) Modifier.shadow(2.dp, shape, ambientColor = Tokens.Elevation.tint, spotColor = Tokens.Elevation.tint).clip(shape).background(Tokens.Palette.Group) else Modifier.clip(shape))
                    .then(if (enabled) Modifier.clickable { onSelect(o) } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(o), fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) Tokens.Palette.Ink else if (enabled) Tokens.Palette.InkMuted else Tokens.Palette.Dim)
            }
        }
    }
}

@Composable
fun LevelBars(level: Int, max: Int, modifier: Modifier = Modifier, width: Dp = 14.dp, color: Color = Tokens.Palette.Accent) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(max) { i -> Box(Modifier.width(width).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (i < level) color else Tokens.Palette.Track)) }
    }
}

@Composable
fun StepMark(done: Boolean, current: Boolean) {
    Box(
        Modifier.size(24.dp).then(
            when {
                done -> Modifier.background(Tokens.Palette.Accent, CircleShape)
                current -> Modifier.border(2.dp, Tokens.Palette.Accent, CircleShape)
                else -> Modifier.border(1.5.dp, Tokens.Palette.Faint, CircleShape)
            },
        ),
        contentAlignment = Alignment.Center,
    ) { if (done) Text("✓", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.OnAccent) }
}

/** A small rounded label ("นับผ่าน"). */
@Composable
fun Pill(text: String) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(Tokens.Palette.AccentSoft).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.AccentDeep)
    }
}

/** The large page title with a back arrow and whatever sits at the right. */
@Composable
fun LargeTitle(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) BackCircle(onBack)
            Spacer(Modifier.weight(1f))
            trailing()
        }
        Text(title, fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 6.dp))
        subtitle?.let { Text(it, fontSize = 15.sp, lineHeight = 20.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 2.dp)) }
    }
}

/** A card with an optional title, for text blocks on a page. */
@Composable
fun PageCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().softShadow(RoundedCornerShape(Tokens.Radius.card)).clip(RoundedCornerShape(Tokens.Radius.card))
            .background(Tokens.Palette.Group).padding(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        title?.let { Text(it, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink) }
        content()
    }
}

/** A connection dot and word: filled = connected. */
@Composable
fun StatusDot(on: Boolean, text: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(16.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).then(if (on) Modifier.background(Tokens.Palette.Positive, CircleShape) else Modifier.border(1.dp, Tokens.Palette.InkMuted, CircleShape)))
        Text(text, fontSize = 13.sp, color = if (on) Tokens.Palette.Ink else Tokens.Palette.InkMuted)
    }
}

/** The round back button of Design J: a white 44 dp circle with a soft shadow and a chevron. */
@Composable
fun BackCircle(onBack: () -> Unit) {
    Box(
        Modifier.size(44.dp).softShadow(CircleShape, small = true).clip(CircleShape).background(Tokens.Palette.Group).clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) { Text("‹", fontSize = 26.sp, lineHeight = 26.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(bottom = 3.dp)) }
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

/** A round icon holder in a skill's colour: white on a pastel card, the pastel in a list ([filled]). */
@Composable
fun SkillIcon(mark: String, color: Tokens.SkillColor, size: Dp = 40.dp, filled: Boolean = false) {
    Box(Modifier.size(size).clip(CircleShape).background(if (filled) color.fill else Tokens.Palette.Group), contentAlignment = Alignment.Center) {
        Text(mark, fontSize = (size.value * 0.42f).sp, color = color.ink)
    }
}
