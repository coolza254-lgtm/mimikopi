package com.id.mimikopi.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.R
import com.id.mimikopi.core.session.BlockKind
import com.id.mimikopi.ui.components.Pill
import com.id.mimikopi.ui.components.Tape
import com.id.mimikopi.ui.components.WavyDivider
import com.id.mimikopi.ui.components.paper
import com.id.mimikopi.ui.components.pressScale
import com.id.mimikopi.ui.components.softShadow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.remember
import com.id.mimikopi.ui.theme.Tokens
import com.id.mimikopi.ui.today.TodayViewModel

/**
 * Home (M13 redesign): today's plan as the hero card, four tiles for the main practice areas, and a list of
 * tools. Tabs at the bottom (today, piano, songs, me) live in the app's bottom bar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onOpenDiagnostics: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPiano: () -> Unit,
    onOpenMetronome: () -> Unit,
    onOpenMidi: () -> Unit,
    onOpenToday: () -> Unit,
    onOpenReader: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenEar: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSongs: () -> Unit,
    onOpenKnowledge: () -> Unit,
    onOpenReview: () -> Unit = {},
    vm: TodayViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    Column(
        Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Image(
                        painter = painterResource(R.drawable.wordmark),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier.height(40.dp),
                    )
                }
                Row(
                    Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Tokens.Palette.Card)
                        .border(Tokens.border, Tokens.Palette.Line, RoundedCornerShape(22.dp))
                        .clickable(onClick = onOpenReview)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_review), contentDescription = null, tint = Tokens.Palette.RoseDeep, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.home_due, ui.dueCount), style = MaterialTheme.typography.labelMedium)
                }
            }

            // Hero: today's plan.
            Box {
                // A raised paper card with a warm soft shadow and a rose edge at the bottom.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .softShadow(RoundedCornerShape(24.dp), Tokens.Elevation.raised)
                        .background(Tokens.Palette.RoseSoft, RoundedCornerShape(24.dp))
                        .padding(bottom = 4.dp),
                ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Tokens.Palette.Card)
                        .border(Tokens.border, Tokens.Palette.Line, RoundedCornerShape(24.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.home_ladder, (ui.stepsMet + 1).coerceAtMost(ui.stepsTotal), ui.stepsTotal), style = MaterialTheme.typography.labelLarge,
                            color = Tokens.Palette.RoseDeep, modifier = Modifier.weight(1f))
                    }
                    val step = ui.step
                    if (step != null) {
                        Text(step.title, style = MaterialTheme.typography.titleLarge)
                    } else {
                        Text(stringResource(R.string.home_all_passed), style = MaterialTheme.typography.titleMedium)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ui.items.filter { !it.optional }.forEachIndexed { i, item ->
                            Pill(stringResource(flowLabel(item.kind)) + if (item.done) " ✓" else "", if (i % 2 == 0) Tokens.Palette.MustardSoft else Tokens.Palette.RoseSoft)
                        }
                    }
                    Button(
                        onClick = onOpenToday,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_play), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(when {
                            ui.allDone -> R.string.home_done_today
                            ui.items.any { it.done } -> R.string.flow_continue
                            else -> R.string.home_start_today
                        }), style = MaterialTheme.typography.labelLarge)
                    }
                }
                }
                Tape(Modifier.offset(x = 22.dp, y = (-10).dp))
            }

            WavyDivider()

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile(R.drawable.ic_nav_songs, Tokens.Palette.RoseSoft, Tokens.Palette.RoseInk, R.string.home_tile_reader, R.string.home_tile_reader_sub, onOpenReader, Modifier.weight(1f))
                    Tile(R.drawable.ic_ear, Tokens.Palette.MustardSoft, Tokens.Palette.MustardInk, R.string.home_tile_ear, R.string.home_tile_ear_sub, onOpenEar, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile(R.drawable.ic_book, Tokens.Palette.MustardSoft, Tokens.Palette.MustardInk, R.string.home_tile_library, R.string.home_tile_library_sub, onOpenLibrary, Modifier.weight(1f))
                    Tile(R.drawable.ic_search, Tokens.Palette.RoseSoft, Tokens.Palette.RoseInk, R.string.home_tile_knowledge, R.string.home_tile_knowledge_sub, onOpenKnowledge, Modifier.weight(1f))
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .softShadow(RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(Tokens.Palette.Card)
                    .border(Tokens.border, Tokens.Palette.Line.copy(alpha = 0.7f), RoundedCornerShape(20.dp)),
            ) {
                ToolRow(R.drawable.ic_metronome, R.string.home_metronome_title, onOpenMetronome)
                HorizontalDivider(color = Tokens.Palette.Line)
                ToolRow(R.drawable.ic_usb, R.string.home_midi_title, onOpenMidi)
                HorizontalDivider(color = Tokens.Palette.Line)
                ToolRow(R.drawable.ic_settings, R.string.home_settings_title, onOpenSettings)
                HorizontalDivider(color = Tokens.Palette.Line)
                ToolRow(R.drawable.ic_device, R.string.home_diagnostics_title, onOpenDiagnostics)
                HorizontalDivider(color = Tokens.Palette.Line)
                ToolRow(R.drawable.ic_info, R.string.home_about_title, onOpenAbout)
            }
        }
    }
}

@Composable
private fun Tile(icon: Int, iconBg: Color, iconTint: Color, title: Int, sub: Int, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressScale(interaction)
            .softShadow(RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Tokens.Palette.Card)
            .border(Tokens.border, Tokens.Palette.Line.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
            .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(40.dp).background(iconBg, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun ToolRow(icon: Int, title: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Tokens.Palette.InkMuted, modifier = Modifier.size(20.dp))
        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = Tokens.Palette.InkMuted, modifier = Modifier.size(18.dp))
    }
}

fun flowLabel(kind: com.id.mimikopi.ui.today.FlowKind): Int = when (kind) {
    com.id.mimikopi.ui.today.FlowKind.CONFIRM -> R.string.flow_kind_confirm
    com.id.mimikopi.ui.today.FlowKind.REVIEW -> R.string.flow_kind_review
    com.id.mimikopi.ui.today.FlowKind.REPEAT -> R.string.flow_kind_repeat
    com.id.mimikopi.ui.today.FlowKind.STEP -> R.string.flow_kind_step
    com.id.mimikopi.ui.today.FlowKind.EAR -> R.string.flow_kind_ear
    com.id.mimikopi.ui.today.FlowKind.BENCH -> R.string.flow_kind_bench
}

fun blockName(kind: BlockKind): Int = when (kind) {
    BlockKind.WARMUP -> R.string.block_warmup
    BlockKind.REVIEW -> R.string.block_review
    BlockKind.RHYTHM -> R.string.block_rhythm
    BlockKind.LESSON -> R.string.block_lesson
    BlockKind.NEW_READING -> R.string.block_new_reading
    BlockKind.EAR -> R.string.block_ear
    BlockKind.FREE_PLAY -> R.string.block_free_short
}
