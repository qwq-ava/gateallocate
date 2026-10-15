package net.micode.airportgate.ui.assign

import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.micode.airportgate.engine.reallocateAll
import net.micode.airportgate.engine.toAssignmentRows
import net.micode.airportgate.engine.toUsageRows
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import net.micode.airportgate.model.AllocationStore
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
import net.micode.airportgate.ui.gantt.FullscreenGanttDialog
import net.micode.airportgate.ui.gantt.GanttCard
import net.micode.airportgate.ui.gantt.SlotDetailDialog
import net.micode.airportgate.ui.theme.Blue
import net.micode.airportgate.ui.theme.BlueLight
import net.micode.airportgate.ui.theme.TextMain
import net.micode.airportgate.ui.theme.TextSub

// ========================= 机位分配 =========================

@Composable
fun AssignScreen(onBack: () -> Unit) {
    // ---- 当天航班分配结果（初始为既有计划；数据一变即由引擎自动重排） ----
    var currentFlights by remember { mutableStateOf(DAY_FLIGHTS) }
    var allocating by remember { mutableStateOf(false) }

    // 正常搜索：时间 + 机位（过滤下方航班列表与甘特图）
    var timeQuery by remember { mutableStateOf("") }
    var gateQuery by remember { mutableStateOf("") }

    // 自动分配：无按钮。以「航班 + 机位」内容哈希作为数据版本号，
    // 版本一变（航班或机位信息更新）LaunchedEffect 自动取消旧任务并重新分配；
    // 结果经 toAssignmentRows() 静默记入 AllocationStore（30 天留存，仅内存），
    // 供后续自建「分配结果表」消费。失败航班号可从 outcome 取（此处不展示）。
    var lastFailed by remember { mutableStateOf<List<String>>(emptyList()) }
    // 每次重组都重算（不 remember），保证换数据源后内容一变版本号立即更新
    val dataVersion = DAY_FLIGHTS.hashCode() * 31 + GATE_INFOS.hashCode()
    LaunchedEffect(dataVersion) {
        allocating = true
        try {
            val (newFlights, result) = withContext(Dispatchers.Default) {
                reallocateAll(DAY_FLIGHTS, GATE_INFOS)
            }
            delay(300) // 让「分配中」状态可见
            currentFlights = newFlights
            lastFailed = result.failedFlightNos
            // 分配结果按真实 addAssignment / addUsage 字段结构暂存（等同伴 GateRepository 合并后直接写库）
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
            val assignmentRows = toAssignmentRows(result, dateStr, DAY_FLIGHTS)
            val usageRows = toUsageRows(result, dateStr, DAY_FLIGHTS, GATE_INFOS)
            AllocationStore.record(assignmentRows, usageRows)
        } finally {
            allocating = false
        }
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

    // 全天 00:00 - 24:00 航班（与甘特图共用同一数据源，按机位占用时刻正序排列）
    val nowMin = remember { nowMinuteOfDay() }
    val allFlights = remember(nowMin, currentFlights) {
        currentFlights.sortedBy { it.startMin }.map { dayFlightToItem(it, nowMin) }
    }

    val filtered = allFlights.filter { f ->
        (timeQuery.isBlank() || f.time.startsWith(timeQuery.trim())) &&
                (gateQuery.isBlank() || f.gate.contains(gateQuery.trim(), ignoreCase = true))
    }

    // ---- 甘特图数据：全天 24 小时机位占用时段（同机位时段重叠自动标红） ----
    val gates = GATE_INFOS.map { it.code }
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
            // ---- 自动分配状态行（无按钮：航班/机位信息一变即自动重排） ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "自动分配：航班 / 机位信息更新后自动重排",
                    fontSize = 12.sp,
                    color = TextSub
                )
                Spacer(Modifier.weight(1f))
                if (allocating) {
                    Text(
                        "分配中…",
                        fontSize = 12.sp,
                        color = Blue
                    )
                } else {
                    Text(
                        "区间图着色 + 回溯",
                        fontSize = 11.sp,
                        color = TextSub
                    )
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
