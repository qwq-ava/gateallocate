package net.micode.airportgate.ui.assign

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.pm.ActivityInfo
import net.micode.airportgate.model.DAY_FLIGHTS
import net.micode.airportgate.model.FlightItem
import net.micode.airportgate.model.GateSlot
import net.micode.airportgate.model.findActivity
import net.micode.airportgate.model.dayFlightToItem
import net.micode.airportgate.model.dayFlightToSlot
import net.micode.airportgate.model.fmtTime
import net.micode.airportgate.model.markConflicts
import net.micode.airportgate.model.nowMinuteOfDay
import net.micode.airportgate.ui.components.AppCard
import net.micode.airportgate.ui.components.FlightRow
import net.micode.airportgate.ui.components.SectionTitle
import net.micode.airportgate.ui.components.SubTopBar
import net.micode.airportgate.ui.components.VerticalDividerCompat
import net.micode.airportgate.ui.gantt.FullscreenGanttDialog
import net.micode.airportgate.ui.gantt.GanttCard
import net.micode.airportgate.ui.gantt.SlotDetailDialog
import net.micode.airportgate.ui.theme.Blue
import net.micode.airportgate.ui.theme.Orange
import net.micode.airportgate.ui.theme.TextMain
import net.micode.airportgate.ui.theme.TextSub

// ========================= 机位分配 =========================

@Composable
fun AssignScreen(onBack: () -> Unit) {
    val pending = 34
    val assigned = 252
    var timeQuery by remember { mutableStateOf("") }
    var gateQuery by remember { mutableStateOf("") }

    // 全天 00:00 - 24:00 航班（与甘特图共用同一数据源，按机位占用时刻正序排列）
    val nowMin = remember { nowMinuteOfDay() }
    val allFlights = remember(nowMin) {
        DAY_FLIGHTS.sortedBy { it.startMin }.map { dayFlightToItem(it, nowMin) }
    }

    val filtered = allFlights.filter { f ->
        (timeQuery.isBlank() || f.time.startsWith(timeQuery.trim())) &&
                (gateQuery.isBlank() || f.gate.contains(gateQuery.trim(), ignoreCase = true))
    }

    // ---- 甘特图状态 ----
    var showFullGantt by remember { mutableStateOf(false) }
    var selectedSlot by remember { mutableStateOf<GateSlot?>(null) }
    val activity = LocalContext.current.findActivity()
    LaunchedEffect(showFullGantt) {
        activity?.requestedOrientation = if (showFullGantt) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    DisposableEffect(Unit) {
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    // ---- 甘特图数据：全天 24 小时机位占用时段（同机位时段重叠自动标红） ----
    val gates = listOf("B27", "B32", "208", "C05", "C08", "T2-14", "D12")
    val ganttSlots = remember(nowMin) {
        markConflicts(DAY_FLIGHTS.map { dayFlightToSlot(it, nowMin) })
    }
    // 甘特图同样跟随上方搜索条件过滤
    val shownGates = if (gateQuery.isBlank()) gates else gates.filter { it.contains(gateQuery.trim(), true) }
    val shownSlots = ganttSlots.filter { s ->
        shownGates.contains(s.gate) &&
                (timeQuery.isBlank() || fmtTime(s.startMin).startsWith(timeQuery.trim()))
    }

    Column(Modifier.fillMaxSize()) {
        SubTopBar("机位分配", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ---- 统计 ----
            AppCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$pending", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Orange)
                        Spacer(Modifier.height(4.dp))
                        Text("待分配航班", fontSize = 13.sp, color = TextSub)
                    }
                    VerticalDividerCompat()
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$assigned", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Blue)
                        Spacer(Modifier.height(4.dp))
                        Text("已分配航班", fontSize = 13.sp, color = TextSub)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            // ---- 甘特图（机位 × 时间） ----
            GanttCard(
                gates = shownGates,
                slots = shownSlots,
                onExpand = {
                    selectedSlot = null
                    showFullGantt = true
                },
                onSlotClick = { selectedSlot = it }
            )

            Spacer(Modifier.height(16.dp))
            SectionTitle("今日动态")
            Spacer(Modifier.height(10.dp))
            AppCard {
                // 搜索时间
                SearchField(
                    hint = "搜索时间（00:00 - 23:00）",
                    value = timeQuery,
                    onValueChange = { timeQuery = it }
                )
                Spacer(Modifier.height(10.dp))
                SearchField(
                    hint = "搜索机位",
                    value = gateQuery,
                    onValueChange = { gateQuery = it }
                )
                Spacer(Modifier.height(6.dp))
                if (filtered.isEmpty()) {
                    Text(
                        "没有符合条件的航班",
                        fontSize = 13.sp,
                        color = TextSub,
                        modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                } else {
                    filtered.forEachIndexed { index, f ->
                        FlightRow(f)
                        if (index != filtered.lastIndex) HorizontalDivider(color = Color(0xFFF0F1F3))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    // 甘特图全屏（横屏呈现全部时段）
    if (showFullGantt) {
        FullscreenGanttDialog(
            gates = shownGates,
            slots = shownSlots,
            onSlotClick = { selectedSlot = it },
            onClose = { showFullGantt = false }
        )
    }

    // 色块详情弹窗
    selectedSlot?.let { slot ->
        SlotDetailDialog(slot = slot, onClose = { selectedSlot = null })
    }
}

@Composable
fun SearchField(hint: String, value: String, onValueChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF4F5F7))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Search, null, tint = Color(0xFFB0B4BA), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, color = TextMain),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(hint, fontSize = 14.sp, color = Color(0xFFB0B4BA))
                    }
                    inner()
                }
            }
        )
    }
}
