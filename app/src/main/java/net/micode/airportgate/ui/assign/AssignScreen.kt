package net.micode.airportgate.ui.assign

import android.content.pm.ActivityInfo
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
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.micode.airportgate.engine.reallocateAll
import net.micode.airportgate.model.AllocationOutcome
import net.micode.airportgate.model.DAY_FLIGHTS
import net.micode.airportgate.model.FlightItem
import net.micode.airportgate.model.GATE_INFOS
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
import net.micode.airportgate.ui.theme.BlueLight
import net.micode.airportgate.ui.theme.Green
import net.micode.airportgate.ui.theme.Orange
import net.micode.airportgate.ui.theme.TextMain
import net.micode.airportgate.ui.theme.TextSub

// ========================= 机位分配 =========================

@Composable
fun AssignScreen(onBack: () -> Unit) {
    // ---- 当天航班分配结果（初始为既有计划；一键分配后由引擎重排） ----
    var currentFlights by remember { mutableStateOf(DAY_FLIGHTS) }
    var outcome by remember { mutableStateOf<AllocationOutcome?>(null) }
    var allocating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 统计：已分配 = 当前占机位航班数；待分配 = 未成功占用机位的航班数
    val totalFlights = DAY_FLIGHTS.size
    val assigned = currentFlights.size
    val pending = totalFlights - assigned

    var timeQuery by remember { mutableStateOf("") }
    var gateQuery by remember { mutableStateOf("") }

    // 全天 00:00 - 24:00 航班（与甘特图共用同一数据源，按机位占用时刻正序排列）
    val nowMin = remember { nowMinuteOfDay() }
    val allFlights = remember(nowMin, currentFlights) {
        currentFlights.sortedBy { it.startMin }.map { dayFlightToItem(it, nowMin) }
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
    val ganttSlots = remember(nowMin, currentFlights) {
        markConflicts(currentFlights.map { dayFlightToSlot(it, nowMin) })
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
            // ---- 一键分配操作区（区间图着色 + 回溯法自动分配引擎） ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (allocating) BlueLight else Blue)
                        .clickable(enabled = !allocating) {
                            allocating = true
                            scope.launch {
                                // 引擎在后台线程执行，避免阻塞 UI
                                val (newFlights, result) = withContext(Dispatchers.Default) {
                                    reallocateAll(currentFlights, GATE_INFOS)
                                }
                                delay(300) // 让「分配中」状态可见
                                currentFlights = newFlights
                                outcome = result
                                allocating = false
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Bolt, null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (allocating) "分配中..." else "一键分配",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.width(12.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !allocating) {
                            currentFlights = DAY_FLIGHTS
                            outcome = null
                        }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Refresh, null,
                        tint = TextSub,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("重置", color = TextSub, fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "区间图着色 + 回溯",
                    fontSize = 11.sp,
                    color = TextSub
                )
            }

            // ---- 分配结果卡片 ----
            outcome?.let { r ->
                Spacer(Modifier.height(12.dp))
                AppCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "分配完成",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMain
                        )
                        Spacer(Modifier.weight(1f))
                        Text("耗时 ${r.elapsedMs} ms", fontSize = 12.sp, color = TextSub)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth()) {
                        ResultCell(
                            label = "成功",
                            value = "${r.successCount}",
                            color = Blue,
                            modifier = Modifier.weight(1f)
                        )
                        ResultCell(
                            label = "失败",
                            value = "${r.failedFlightNos.size}",
                            color = if (r.failedFlightNos.isNotEmpty()) Orange else TextSub,
                            modifier = Modifier.weight(1f)
                        )
                        ResultCell(
                            label = "回溯腾挪",
                            value = "${r.moveCount} 次",
                            color = TextMain,
                            modifier = Modifier.weight(1f)
                        )
                        ResultCell(
                            label = "时段冲突",
                            value = "${r.conflictBefore} → ${r.conflictAfter}",
                            color = if (r.conflictAfter < r.conflictBefore) Green else TextMain,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (r.failedFlightNos.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "无可用机位：${r.failedFlightNos.joinToString("、")}",
                            fontSize = 12.sp,
                            color = Orange
                        )
                    }
                    if (r.conflictAfter < r.conflictBefore) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "已消除 ${r.conflictBefore - r.conflictAfter} 处同时段冲突",
                            fontSize = 12.sp,
                            color = Green
                        )
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

/** 分配结果卡片中的单项统计 */
@Composable
private fun ResultCell(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 12.sp, color = TextSub)
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
