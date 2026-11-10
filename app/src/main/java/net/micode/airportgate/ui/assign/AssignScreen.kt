package net.micode.airportgate.ui.assign

import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.micode.airportgate.control.AllocationReadiness
import net.micode.airportgate.control.AllocationService
import net.micode.airportgate.control.TimelineResult
import net.micode.airportgate.data.model.DataSync
import net.micode.airportgate.data.model.GateSlot
import net.micode.airportgate.data.model.MINUTES_PER_DAY
import net.micode.airportgate.data.model.dateOf
import net.micode.airportgate.data.model.dayFlightToSlot
import net.micode.airportgate.data.model.findActivity
import net.micode.airportgate.data.model.markConflicts
import net.micode.airportgate.data.model.nowMinuteOfDay
import net.micode.airportgate.ui.components.SubTopBar
import net.micode.airportgate.ui.gantt.FullscreenGanttDialog
import net.micode.airportgate.ui.gantt.GanttCard
import net.micode.airportgate.ui.gantt.SlotDetailDialog
import net.micode.airportgate.ui.theme.Blue
import net.micode.airportgate.ui.theme.ConflictRed
import net.micode.airportgate.ui.theme.TextSub

// ========================= 机位分配 =========================

@Composable
fun AssignScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val today = remember { dateOf(0) }

    // 「一键分配」的目标日期固定为明天（与「数据导入」默认落库日一致）。
    // 只决定分配写到哪天；甘特图始终是一张从今天起的连续多日时间轴，
    // 页面上不再提供「今天 / 明天」切换按钮。
    val opDate = dateOf(1)

    // ---- 甘特图交互状态 ----
    var showFullGantt by remember { mutableStateOf(false) }
    var selectedSlot by remember { mutableStateOf<GateSlot?>(null) }

    // ---- 甘特图数据源：连续多日只读回显（引擎输出后重新回读，保证日期归属正确） ----
    var timeline by remember { mutableStateOf(TimelineResult(emptyList(), emptyList())) }
    val dates = timeline.days.map { it.date }.ifEmpty { listOf(today) }
    val gateCodes = timeline.gateCodes

    var allocating by remember { mutableStateOf(false) }
    var lastFailed by remember { mutableStateOf<List<String>>(emptyList()) }
    var readiness by remember { mutableStateOf(AllocationReadiness(false, false)) }
    val canAllocate = readiness.canAllocate

    val dataVersion = DataSync.version

    /** 只读刷新：重建连续时间轴 + 重新探测目标日的可分配性 */
    suspend fun refresh(targetDate: String) {
        val t = AllocationService.timeline(context)
        timeline = t
        lastFailed = t.days.firstOrNull { it.date == targetDate }?.failedFlightNos ?: emptyList()
        readiness = AllocationService.readiness(context, targetDate)
        selectedSlot = null
    }

    // 数据版本变化（导入航班、维护机位后 DataSync.bump()）时只读回显，
    // 绝不自动分配——分配动作始终由用户点击「一键分配」触发。
    LaunchedEffect(opDate, dataVersion) { refresh(opDate) }

    // 置灰按钮被点击时的两个未就绪提醒弹窗
    var showFlightWarn by remember { mutableStateOf(false) }
    var showGateWarn by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // 真正执行一次分配（点击「一键分配」时调用）。目标日期固定为明天，不再需要防串日判断。
    fun runAllocation() {
        if (allocating || !canAllocate) return
        val targetDate = opDate
        scope.launch {
            allocating = true
            try {
                val res = AllocationService.allocate(context, targetDate)
                delay(300) // 让「分配中」状态可见
                // 重新回读整条时间轴：跨夜后段等次日结果也能立刻显示在图上
                refresh(targetDate)
                lastFailed = res.failedFlightNos
            } finally {
                allocating = false
            }
        }
    }

    // ---- 全屏甘特图：横屏呈现连续日期 ----
    val activity = context.findActivity()
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

    // ---- 甘特图色块：按日期分别判定「已完成」----
    // 过去的日期全部已完成(1440)，今天按当前时刻，未来日期都不判为已完成(-1)。
    val nowMin = remember { nowMinuteOfDay() }
    val ganttSlots = remember(timeline, nowMin, today) {
        markConflicts(timeline.days.flatMap { day ->
            val nowForDay = when {
                day.date < today -> MINUTES_PER_DAY
                day.date == today -> nowMin
                else -> -1
            }
            day.flights.map { dayFlightToSlot(it, nowForDay, day.date) }
        })
    }

    Column(Modifier.fillMaxSize()) {
        SubTopBar("机位分配", onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ---- 一键分配按钮 ----
            // 按钮始终可点击（仅在分配中置为默认禁用态），以便置灰时点击仍能弹出未就绪提醒；
            // 「视觉置灰」通过自定义灰底灰字实现，点击后在 onClick 中分流：就绪则分配，否则弹对应弹窗。
            val buttonEnabled = !allocating
            Button(
                onClick = {
                    if (canAllocate) {
                        runAllocation()
                    } else {
                        // 缺哪一项就弹哪一项的提醒；两项都缺则两个弹窗依次出现
                        if (!readiness.hasFlights) showFlightWarn = true
                        if (!readiness.hasGates) showGateWarn = true
                    }
                },
                enabled = buttonEnabled,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canAllocate) Blue else Color(0xFFE6E7E9),
                    contentColor = if (canAllocate) Color.White else Color(0xFF9AA0A6),
                    disabledContainerColor = Color(0xFFE6E7E9),
                    disabledContentColor = Color(0xFF9AA0A6)
                )
            ) {
                Text(
                    if (allocating) "分配中…" else "一键分配",
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(8.dp))
            // ---- 状态 / 提示行 ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        allocating -> "正在分配，请稍候…"
                        !canAllocate -> "$opDate 尚无可分配数据：请先在「航班导入」录入航班、在「机位信息」维护机位"
                        else -> "甘特图连续显示 ${dates.size} 天，左右滑动查看；点击按钮对 $opDate 分配"
                    },
                    fontSize = 12.sp,
                    color = TextSub
                )
                Spacer(Modifier.weight(1f))
                if (lastFailed.isNotEmpty() && !allocating) {
                    Text(
                        "未分配 ${lastFailed.size} 班",
                        fontSize = 12.sp,
                        color = ConflictRed
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            // ---- 一张连续甘特图：今天 24h 排完接下一天，按日期依次排开 ----
            GanttCard(
                gates = gateCodes,
                slots = ganttSlots,
                dates = dates,
                onExpand = {
                    selectedSlot = null
                    showFullGantt = true
                },
                onSlotClick = { selectedSlot = it },
                showNowLine = true,
                today = today
            )

            Spacer(Modifier.height(16.dp))
        }
    }

    // 甘特图全屏（横屏，同一张连续日期轴）
    if (showFullGantt) {
        FullscreenGanttDialog(
            gates = gateCodes,
            slots = ganttSlots,
            dates = dates,
            onSlotClick = { selectedSlot = it },
            onClose = { showFullGantt = false },
            showNowLine = true,
            today = today
        )
    }

    // 色块详情弹窗
    selectedSlot?.let { slot ->
        SlotDetailDialog(slot = slot, onClose = { selectedSlot = null })
    }

    // 航班未确定提醒弹窗（置灰按钮被点击且缺航班时弹出）
    if (showFlightWarn) {
        AlertDialog(
            onDismissRequest = { showFlightWarn = false },
            confirmButton = {
                TextButton(onClick = { showFlightWarn = false }) { Text("我知道了") }
            },
            title = { Text("航班未确定") },
            text = { Text("当前没有可分配的航班。请先在「航班导入」录入并保存航班信息，再回来分配机位。") }
        )
    }

    // 机位未确定提醒弹窗（置灰按钮被点击且缺机位时弹出）
    if (showGateWarn) {
        AlertDialog(
            onDismissRequest = { showGateWarn = false },
            confirmButton = {
                TextButton(onClick = { showGateWarn = false }) { Text("我知道了") }
            },
            title = { Text("机位未确定") },
            text = { Text("当前没有可用机位。请先在「机位信息」维护并保存机位，再回来分配机位。") }
        )
    }
}
