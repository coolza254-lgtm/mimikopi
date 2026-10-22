package com.id.mimikopi.ui.songs

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
                Text("เพลง", fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                Text("มือถือฝึกพื้นฐาน · เพลงจริงฝึกบนคอม", fontSize = 14.sp, color = Tokens.Palette.InkMuted)
            }
            Spacer(Modifier.height(20.dp))
            PageCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(Tokens.Palette.AccentSoft), contentAlignment = Alignment.Center) {
                        ClefGlyph(28.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("เพลงเป้าหมายอยู่บนคอม", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
                        Text("เพลงส่วนตัว คอร์ดของเพลง และโน้ตที่ซื้อ เก็บและฝึกบนคอมเท่านั้น ไม่ขึ้น GitHub",
                            fontSize = 13.sp, lineHeight = 18.sp, color = Tokens.Palette.InkMuted)
                    }
                }
                Text("บนมือถือฝึกส่วนที่ช่วยให้เล่นเพลงได้เร็วขึ้น: อ่านโน้ต หาคีย์ ฝึกหู และความรู้คอร์ด ผลทั้งหมดซิงค์ไปคอม",
                    fontSize = 14.sp, lineHeight = 20.sp, color = Tokens.Palette.Ink, modifier = Modifier.padding(top = 14.dp))
            }
            if (songs.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Group {
                    GroupRow("ย้ายเพลงส่วนตัว ${songs.size} เพลงไปคอม", subtitle = "เปิดเพลงแล้วกด \"ส่งออก\" จากนั้นนำเข้าบนคอม (เพลงส่วนตัว → นำเข้า)",
                        onClick = { onOpen(Open.PrivateSongs) })
                }
            }

            Column(Modifier.padding(start = 4.dp, top = 28.dp, bottom = 10.dp)) {
                Text("คลังเพลงฝึกอ่าน", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Tokens.Palette.Ink)
            }
            Group {
                GroupRow("เพลงพื้นบ้านและเพลงสอบ", subtitle = "${vm.pieceCount} เพลงหมดลิขสิทธิ์ · ใช้ในบทเรียนและสอบเกรด", onClick = onOpenLibrary)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
