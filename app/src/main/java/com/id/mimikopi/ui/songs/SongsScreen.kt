package com.id.mimikopi.ui.songs

import com.id.mimikopi.core.i18n.tr
import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.id.mimikopi.MimikopiApp
import com.id.mimikopi.R
import com.id.mimikopi.core.recall.PrivateRecall
import com.id.mimikopi.core.recall.PrivateSong
import com.id.mimikopi.core.recall.RecallHand
import com.id.mimikopi.core.recall.TargetSong
import com.id.mimikopi.data.db.SettingEntity
import com.id.mimikopi.ui.components.Chip
import com.id.mimikopi.ui.components.Group
import com.id.mimikopi.ui.components.GroupDivider
import com.id.mimikopi.ui.components.GroupRow
import com.id.mimikopi.ui.components.PageCard
import com.id.mimikopi.ui.components.PageMargin
import com.id.mimikopi.ui.components.PageMaxWidth
import com.id.mimikopi.ui.components.Pill
import com.id.mimikopi.ui.components.paper
import com.id.mimikopi.ui.theme.Tokens
import com.id.mimikopi.ui.today.Open
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class SongsViewModel(app: Application) : AndroidViewModel(app) {
    private val services = app as MimikopiApp
    val songs = services.privateSongs.songs
    val pieceCount: Int get() = services.pieces.pieces.size

    fun refresh() {
        viewModelScope.launch {
            services.privateSongs.reload()
            services.pieces.load()
        }
    }
}

/**
 * Songs on the phone (Design J): private songs, the goal song and bought sheets are practised on the PC only
 * (learning-system.md 2.11, 2026-10-22), so this page says so, offers to send any private songs still here to the PC,
 * and opens the practice library for reading and exams.
 */
@Composable
fun SongsScreen(onOpen: (Open) -> Unit, onOpenLibrary: () -> Unit, vm: SongsViewModel = viewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val songs by vm.songs.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = PageMaxWidth).fillMaxWidth().padding(horizontal = PageMargin)) {
            Column(Modifier.padding(start = 4.dp, top = 32.dp)) {
                Text(tr("เพลง", "Songs", "曲"), fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                Text(tr("มือถือฝึกพื้นฐาน · เพลงจริงฝึกบนคอม", "Basics on the phone · real songs on the PC", "スマホで基礎 · 本物の曲はPCで"), fontSize = 14.sp, color = Tokens.Palette.InkMuted)
            }
            Spacer(Modifier.height(20.dp))
            PageCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(Tokens.Palette.AccentSoft), contentAlignment = Alignment.Center) {
                        ClefGlyph(28.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(tr("เพลงเป้าหมายอยู่บนคอม", "Target songs live on the PC", "目標曲はPCにあります"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                        Text(tr("เพลงส่วนตัว คอร์ดของเพลง และโน้ตที่ซื้อ เก็บและฝึกบนคอมเท่านั้น ไม่ขึ้น GitHub", "Private songs, their chords and bought sheets are kept and practised on the PC only, never sent to GitHub", "個人曲、曲のコード、購入した楽譜はPCでのみ保存・練習し、GitHubには上げません"),
                            fontSize = 13.sp, lineHeight = 18.sp, color = Tokens.Palette.InkMuted)
                    }
                }
                Text(tr("บนมือถือฝึกส่วนที่ช่วยให้เล่นเพลงได้เร็วขึ้น: อ่านโน้ต หาคีย์ ฝึกหู และความรู้คอร์ด ผลทั้งหมดซิงค์ไปคอม", "On the phone, practise what makes songs come faster: reading, finding keys, ear training and chord knowledge. All results sync to the PC", "スマホでは曲を早く弾けるようになる練習を：読譜、キー探し、耳トレ、コードの知識。結果はすべてPCに同期されます"),
                    fontSize = 14.sp, lineHeight = 20.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 14.dp))
            }
            if (songs.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Group {
                    GroupRow(tr("ย้ายเพลงส่วนตัว ${songs.size} เพลงไปคอม", "Move ${songs.size} private songs to the PC", "個人曲${songs.size}曲をPCへ移す"), subtitle = tr("เปิดเพลงแล้วกด \"ส่งออก\" จากนั้นนำเข้าบนคอม (เพลงส่วนตัว → นำเข้า)", "Open a song and tap \"Export\", then import it on the PC (Private songs → Import)", "曲を開いて「エクスポート」を押し、PCでインポート（個人曲 → インポート）"),
                        onClick = { onOpen(Open.PrivateSongs) })
                }
            }

            Column(Modifier.padding(start = 4.dp, top = 28.dp, bottom = 10.dp)) {
                Text(tr("คลังเพลงฝึกอ่าน", "Reading library", "読譜ライブラリ"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
            }
            Group {
                GroupRow(tr("เพลงพื้นบ้านและเพลงสอบ", "Folk songs and exam pieces", "民謡と試験曲"), subtitle = tr("${vm.pieceCount} เพลงหมดลิขสิทธิ์ · ใช้ในบทเรียนและสอบเกรด", "${vm.pieceCount} public-domain pieces · used in lessons and grade exams", "パブリックドメインの曲${vm.pieceCount}曲 · レッスンとグレード試験で使用"), onClick = onOpenLibrary)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
