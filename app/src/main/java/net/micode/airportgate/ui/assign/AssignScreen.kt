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
import net.micode.airportgate.domain.AllocationReadiness
import net.micode.airportgate.domain.AllocationService
import net.micode.airportgate.model.DayFlight
import net.micode.airportgate.model.DataSync
import net.micode.airportgate.model.GateSlot
import net.micode.airportgate.model.dateOf
import net.micode.airportgate.model.dayFlightToSlot
import net.micode.airportgate.model.findActivity
import net.micode.airportgate.model.markConflicts
import net.micode.airportgate.model.nowMinuteOfDay
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
    // 当前航班日期 = 今天（分配结果按天落库，与导入页签口径一致）
    val today = remember { dateOf(0) }

    // ---- 分配结果（引擎输出，用于甘特图展示） ----
    var currentFlights by remember { mutableStateOf<List<DayFlight>>(emptyList()) }
    var gateCodes by remember { mutableStateOf<List<String>>(emptyList()) }
    var allocating by remember { mutableStateOf(false) }
    var lastFailed by remember { mutableStateOf<List<String>>(emptyList()) }

    // 分配就绪状态：航班与机位是否各自存在（用于按钮置灰与未就绪弹窗）
    var readiness by remember { mutableStateOf(AllocationReadiness(false, false)) }
    val canAllocate = readiness.canAllocate

    // 数据版本变化（航班导入 / 机位维护保存后 DataSync.bump()）时重新探测可分配性；
    // 这里只探测、不自动分配——分配动作交由用户点击「一键分配」触发。
    val dataVersion = DataSync.version
    LaunchedEffect(dataVersion) {
        readiness = AllocationService.readiness(context, today)
    }

    // 置灰按钮被点击时的两个未就绪提醒弹窗
    var showFlightWarn by remember { mutableStateOf(false) }
    var showGateWarn by remember { mutableStateOf(false) }

    // 协程作用域：按钮点击时启动分配
    val scope = rememberCoroutineScope()

    // 真正执行一次分配（点击「一键分配」时调用）
    fun runAllocation() {
        if (allocating || !canAllocate) return
        scope.launch {
            allocating = true
            try {
                // 业务逻辑交由 domain.AllocationService 执行：
                // 读取航班+机位 -> 引擎分配 -> 落库 t_assignment/t_gate_usage -> 回写航班
                val res = AllocationService.allocate(context, today)
                delay(300) // 让「分配中」状态可见
                currentFlights = res.flights
                gateCodes = res.gateCodes
                lastFailed = res.failedFlightNos
            } finally {
                allocating = false
            }
        }
    }

    // ---- 甘特图状态 ----
    var showFullGantt by remember { mutableStateOf(false) }
    var selectedSlot by remember { mutableStateOf<GateSlot?>(null) }
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

    // 全天 00:00 - 24:00 航班（甘特图数据源）
    val nowMin = remember { nowMinuteOfDay() }

    // ---- 甘特图数据：全天 24 小时机位占用时段（同机位时段重叠自动标红） ----
    val ganttSlots = remember(nowMin, currentFlights) {
        markConflicts(currentFlights.map { dayFlightToSlot(it, nowMin) })
    }
    val shownGates = gateCodes
    val shownSlots = ganttSlots

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
                        !canAllocate -> "尚无可分配数据：请先在「航班导入」录入航班、在「机位信息」维护机位"
                        else -> "区间图着色 + 回溯，点击上方按钮开始分配"
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
