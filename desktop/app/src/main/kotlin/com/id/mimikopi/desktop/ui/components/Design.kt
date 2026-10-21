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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.id.mimikopi.desktop.ui.theme.Tokens

/*
 * The calm building blocks of the pages, the same as the phone's (Android ui/components/Design.kt): large titles,
 * grouped rounded blocks of rows, one filled pill button per page, a segmented control. Black and white; size and
 * weight make the hierarchy. Icons are plain characters so nothing depends on Android drawables.
 */

/** Reading width of the text pages; music pages use the whole window. */
val PageMaxWidth: Dp = 760.dp
val PageMargin: Dp = 24.dp

/** A scrolling page column of readable width, centred in the window. */
@Composable
fun Page(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(Tokens.Palette.Paper), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = PageMargin, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Tokens.Palette.Group), content = content)
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
            Text(title, fontSize = 16.sp, lineHeight = 22.sp, color = titleColor)
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
        modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(27.dp))
            .background(if (enabled) Tokens.Palette.Ink else Tokens.Palette.Line)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) Tokens.Palette.OnInk else Tokens.Palette.InkMuted)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        // The outline keeps it visible inside a Group card, which has the same fill.
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(Tokens.Palette.Group)
            .border(1.dp, Tokens.Palette.Line, RoundedCornerShape(22.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = if (enabled) Tokens.Palette.Ink else Tokens.Palette.Dim) }
}

/** A large, always-visible stop button for music pages (Space / Esc do the same). */
@Composable
fun StopButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = 48.dp).widthIn(min = 120.dp).clip(RoundedCornerShape(24.dp)).background(Tokens.Palette.Ink)
            .clickable(onClick = onClick).padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) { Text("■  หยุด (Space)", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.OnInk) }
}

@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Tokens.Palette.Group).padding(2.dp)) {
        options.forEach { o ->
            val on = o == selected
            Box(
                Modifier.weight(1f).heightIn(min = 36.dp).clip(RoundedCornerShape(8.dp))
                    .then(if (on) Modifier.background(Tokens.Palette.Paper).border(1.dp, Tokens.Palette.Line, RoundedCornerShape(8.dp)) else Modifier)
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
fun LevelBars(level: Int, max: Int, modifier: Modifier = Modifier, width: Dp = 14.dp) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(max) { i -> Box(Modifier.width(width).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i < level) Tokens.Palette.Ink else Tokens.Palette.Line)) }
    }
}

@Composable
fun StepMark(done: Boolean, current: Boolean) {
    Box(
        Modifier.size(24.dp).then(
            when {
                done -> Modifier.background(Tokens.Palette.Ink, CircleShape)
                current -> Modifier.border(2.dp, Tokens.Palette.Ink, CircleShape)
                else -> Modifier.border(1.5.dp, Tokens.Palette.Faint, CircleShape)
            },
        ),
        contentAlignment = Alignment.Center,
    ) { if (done) Text("✓", fontSize = 13.sp, color = Tokens.Palette.OnInk) }
}

/** A small rounded label ("นับผ่าน"). */
@Composable
fun Pill(text: String) {
    Box(Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, Tokens.Palette.Ink, RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, fontSize = 12.sp, color = Tokens.Palette.Ink)
    }
}

/** The large page title with a back arrow and whatever sits at the right. */
@Composable
fun LargeTitle(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) Box(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(horizontal = 6.dp, vertical = 4.dp),
            ) { Text("‹ กลับ", fontSize = 16.sp, color = Tokens.Palette.Ink) }
            Spacer(Modifier.weight(1f))
            trailing()
        }
        Text(title, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
        subtitle?.let { Text(it, fontSize = 15.sp, lineHeight = 20.sp, color = Tokens.Palette.InkMuted, modifier = Modifier.padding(top = 2.dp)) }
    }
}

/** A card with an optional title, for text blocks on a page. */
@Composable
fun PageCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(Tokens.Radius.card)).border(1.dp, Tokens.Palette.Line, RoundedCornerShape(Tokens.Radius.card))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        title?.let { Text(it, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Tokens.Palette.Ink) }
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
