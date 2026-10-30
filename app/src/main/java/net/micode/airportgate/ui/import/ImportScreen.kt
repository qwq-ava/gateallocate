package net.micode.airportgate.ui.import

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.micode.airportgate.data.db.GateRepository
import net.micode.airportgate.domain.ImportService
import net.micode.airportgate.model.FlightDraft
import net.micode.airportgate.model.ImportRecord
import net.micode.airportgate.model.dateOf
import net.micode.airportgate.model.isFlightFlown
import net.micode.airportgate.model.nextDate
import net.micode.airportgate.ui.components.AppCard
import net.micode.airportgate.ui.components.SectionTitle
import net.micode.airportgate.ui.components.StatusChip
import net.micode.airportgate.ui.components.SubTopBar
import net.micode.airportgate.ui.theme.Blue
import net.micode.airportgate.ui.theme.BlueLight
import net.micode.airportgate.ui.theme.Green
import net.micode.airportgate.ui.theme.TextMain
import net.micode.airportgate.ui.theme.TextSub

// ========================= 航班导入 =========================

/** 「查看全部」展示的导入记录时间范围：最近 30 天 */
private const val RECORD_RANGE_DAYS = 30

@Composable
fun ImportScreen(onBack: () -> Unit, showMsg: (String) -> Unit) {
    val context = LocalContext.current

    // null = 停在导入主页；非空 = 打开录入/修改表格
    var formMode by remember { mutableStateOf<FlightFormMode?>(null) }
    var showAllRecords by remember { mutableStateOf(false) }
    // null = 未进入预览；非空 = 已通过校验、等待用户二次确认上传（用例 2 -> 3）
    var previewData by remember { mutableStateOf<Map<String, List<FlightDraft>>?>(null) }
    // 信息更改时由「今日/明日/后天」两段合并成的跨夜航班号集合；删除这些航班时要把两段都清掉
    var mergedOvernightNos by remember { mutableStateOf(setOf<String>()) }
    // 跨夜航班「前段所在日期」映射（航班号 -> 前段日期），删除或改为不跨夜时据此清掉两段
    var mergedFrontDates by remember { mutableStateOf(mapOf<String, String>()) }

    // 表格数据放在这一层，关闭表格再进来时填写内容不会丢
    // 信息更改模式需要「今天 / 明天」两份数据；新建导入只用「明天」
    val rowsToday = remember { mutableStateListOf<FlightDraft>() }
    val rowsTomorrow = remember { mutableStateListOf<FlightDraft>() }

    val requiredFields = listOf(
        "航班号", "航班日期", "进出港", "机型", "到达时刻", "离开时刻", "起降机场"
    )

    // ---- 最近导入记录（最近 30 天，真实批次，从 t_import_batch 读取）----
    // importRefresh 在每次提交成功后 +1，触发重新查库，使列表即时刷新
    var importRefresh by remember { mutableStateOf(0) }
    val records = remember(importRefresh) {
        GateRepository.loadImportBatches(context, RECORD_RANGE_DAYS)
    }

    // ---- 打开表格 ----
    fun openForm(mode: FlightFormMode) {
        if (mode == FlightFormMode.EDIT) {
            // 信息更改：今天 + 明天两页；今天已飞完的航班锁定为只读
            val today = dateOf(0)
            val tomorrow = dateOf(1)
            val dayAfter = dateOf(2)
            val todayFlights = GateRepository.loadFlights(context, today)
                .map { it.copy(locked = isFlightFlown(today, it.departureTime)) }
            val tomorrowFlights = GateRepository.loadFlights(context, tomorrow)
                .map { it.copy(locked = isFlightFlown(tomorrow, it.departureTime)) }
            // 额外加载「后天」：手工录入默认录的是「明天」航班，其跨夜后段落在后天，
            // 合并时需要从这一天才取得到达 00:00 的后段。
            val dayAfterFlights = GateRepository.loadFlights(context, dayAfter)
                .map { it.copy(locked = isFlightFlown(dayAfter, it.departureTime)) }

            // 跨夜航班在库里被拆成两段：前段「到达→24:00」落在日 D，后段「0:00→离开」落在 D+1。
            // 信息更改页签只有今天(D0)/明天(D1)两页，但跨夜航班的前段可能在 D0 或 D1：
            //   - 前段在 D0 → 后段在 D1（页签内可见）→ 合并后整体可改；
            //   - 前段在 D1（多数情况：手工录的是「明天」航班）→ 后段在 D2（不在页签内）
            // 因此对「今天」「明天」两页各自做跨夜合并，并把后段从其所在页移除（避免同一航班出现在两个页签）。
            // mergedFrontDatesLocal 记录每个跨夜航班的「前段日期」，删除或改成不跨夜时据此清掉两段。
            val mergedFrontDatesLocal = mutableMapOf<String, String>()

            fun mergeFronts(frontDate: String, fronts: List<FlightDraft>, backDate: String, backs: List<FlightDraft>): List<FlightDraft> {
                val backByNo = backs.filter { it.arrivalTime == "00:00" }.associateBy { it.flightNo }
                return fronts.map { front ->
                    val back = backByNo[front.flightNo]
                    if (front.departureTime == "24:00" && back != null) {
                        mergedFrontDatesLocal[front.flightNo] = frontDate
                        // 合并：到达取前段、离开取后段；可编辑性以后段所在日(次日)是否已飞完为准
                        front.copy(
                            departureTime = back.departureTime,
                            locked = front.locked || isFlightFlown(backDate, back.departureTime)
                        )
                    } else {
                        front
                    }
                }
            }

            val mergedToday = mergeFronts(today, todayFlights, tomorrow, tomorrowFlights)
            val mergedTomorrowRaw = mergeFronts(tomorrow, tomorrowFlights, dayAfter, dayAfterFlights)

            // 今天前段的后段在明天页，需从明天页移除；明天前段的后段在后天（无页签），自然不显示
            val consumedInTomorrow = todayFlights
                .filter { f -> f.departureTime == "24:00" && tomorrowFlights.any { it.flightNo == f.flightNo && it.arrivalTime == "00:00" } }
                .map { it.flightNo }
                .toSet()
            val mergedTomorrow = mergedTomorrowRaw.filterNot { consumedInTomorrow.contains(it.flightNo) }

            rowsToday.clear(); rowsToday.addAll(mergedToday)
            rowsTomorrow.clear(); rowsTomorrow.addAll(mergedTomorrow)
            mergedOvernightNos = mergedFrontDatesLocal.keys
            mergedFrontDates = mergedFrontDatesLocal.toMap()
            if (todayFlights.isEmpty() && tomorrowFlights.isEmpty() && dayAfterFlights.isEmpty()) {
                showMsg("近三天暂无已导入航班，可直接补录")
            }
        } else {
            mergedOvernightNos = emptySet()
            mergedFrontDates = emptyMap()
            if (rowsTomorrow.isEmpty()) {
                // 卡片式一屏一条，先给 1 条，其余用「复制新增」快速加
                rowsTomorrow.add(FlightDraft(id = System.nanoTime()))
            }
        }
        formMode = mode
    }

    /** 用户确认上传：执行真正落库，并按结果给出成功/失败提示（用例 4/4a/5） */
    fun doConfirmUpload() {
        val data = previewData ?: return
        val isCreate = formMode == FlightFormMode.CREATE
        val sb = StringBuilder()
        var totalSaved = 0
        var hadValid = false
        var failed = false
        try {
            data.forEach { (date, rows) ->
                val toSave = rows.filter { !it.locked }
                hadValid = hadValid || toSave.any { it.valid }
                val res = ImportService.submit(context, date, toSave, isCreate = isCreate)
                totalSaved += res.savedCount
                if (sb.isNotEmpty()) sb.append("；")
                sb.append("$date 入库 ${res.savedCount} 条")
            }
            // 跨夜航班清理（信息更改时由多日两段合并编辑）：
            //   - 某跨夜航班已不在提交数据中 => 被用户删除，需把它「前段日」与「次日」两段都清掉，避免孤儿记录；
            //   - 跨夜航班仍在，但用户改成了「不再跨夜」=> 旧的「0:00 起」后段已成为孤儿，清掉次日那段。
            if (!isCreate && mergedOvernightNos.isNotEmpty()) {
                val allRows = data.values.flatten()
                val presentNos = allRows.mapNotNull { it.flightNo.ifBlank { null } }.toSet()
                val presentMerged = allRows.filter { it.flightNo in mergedOvernightNos && !it.locked }
                mergedOvernightNos.forEach { no ->
                    val fd = mergedFrontDates[no] ?: dateOf(0)
                    if (no !in presentNos) {
                        GateRepository.deleteFlightsByFlightNo(context, fd, no)
                        GateRepository.deleteFlightsByFlightNo(context, nextDate(fd), no)
                    } else {
                        val r = presentMerged.find { it.flightNo == no }
                        if (r != null && !r.isOvernight) {
                            // 改为不跨夜：删除旧的跨夜后段孤儿（落在次日）
                            GateRepository.deleteFlightsByFlightNo(context, nextDate(fd), no)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            failed = true
        }
        // 全部有效数据写入失败 => 视为导入失败（用例 4a）
        if (failed || (hadValid && totalSaved == 0)) {
            showMsg("数据导入失败，请检查后重试")
            previewData = null // 关闭预览，回到表单，由用户再次尝试（用例 4a2）
        } else {
            showMsg("数据导入成功，共导入 $totalSaved 条记录" + if (sb.isNotEmpty()) "（$sb）" else "")
            importRefresh++      // 自动刷新展示（用例 5）
            previewData = null
            formMode = null      // 回到导入主页
        }
    }

    if (previewData != null) {
        // 数据预览与二次确认（用例 2 -> 3）
        PreviewScreen(
            data = previewData!!,
            onConfirm = { doConfirmUpload() },
            onCancel = { previewData = null } // 取消预览，回到表单（用例 3a）
        )
        return
    }

    if (formMode != null) {
        val mode = formMode!!
        // 新建导入仅「明天」一页；信息更改为「今天 / 明天」两页
        val tabs = if (mode == FlightFormMode.EDIT) {
            listOf(
                FlightFormTab(dateOf(0), "今天航班信息", rowsToday),
                FlightFormTab(dateOf(1), "明天航班信息", rowsTomorrow)
            )
        } else {
            listOf(FlightFormTab(dateOf(1), "明天航班信息", rowsTomorrow))
        }
        FlightFormScreen(
            mode = mode,
            tabs = tabs,
            onBack = { formMode = null },
            onSubmit = { dateRowsMap ->
                // 用例 2：先校验。存在未填写完整的航班（已飞完 locked 除外）则阻断预览，
                // 提示用户修改后重新提交（用例 2a）
                val invalid = dateRowsMap.values.flatten().filter { !it.locked && !it.valid }
                if (invalid.isEmpty()) {
                    // 校验通过 -> 进入预览确认环节（用例 2 末尾：提示用户进行数据预览与确认）
                    previewData = dateRowsMap
                } else {
                    showMsg("有 ${invalid.size} 条信息不完整，请检查标红项后重试")
                }
            }
        )
        return
    }

    if (showAllRecords) {
        AllRecordsScreen(records = records, onBack = { showAllRecords = false })
        return
    }

    Column(Modifier.fillMaxSize()) {
        SubTopBar("航班导入", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ---- 班期概览 ----
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.CalendarMonth,
                        null,
                        tint = Blue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("当前班期", fontSize = 13.sp, color = TextSub)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        dateOf(0),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMain
                    )
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "上次导入",
                            fontSize = 11.sp,
                            color = TextSub
                        )
                        Text(
                            records.firstOrNull()?.time ?: "—",
                            fontSize = 12.sp,
                            color = TextSub
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ---- 两个功能入口 ----
            ActionEntry(
                icon = Icons.Outlined.Description,
                title = "数据导入",
                sub = "按表格填写明天的航班计划",
                onClick = { openForm(FlightFormMode.CREATE) }
            )
            Spacer(Modifier.height(10.dp))
            ActionEntry(
                icon = Icons.Outlined.Edit,
                title = "航班信息更改",
                sub = "查询并修改已导入航班（今天 / 明天）",
                onClick = { openForm(FlightFormMode.EDIT) }
            )

            Spacer(Modifier.height(14.dp))

            // ---- 必填字段 ----
            AppCard {
                Text("必填字段", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                Spacer(Modifier.height(8.dp))
                requiredFields.chunked(4).forEach { rowFields ->
                    Row(Modifier.fillMaxWidth()) {
                        rowFields.forEach { field ->
                            FieldChip(field, Modifier.padding(end = 8.dp, bottom = 8.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionTitle("最近导入记录") {
                Text(
                    "查看全部",
                    fontSize = 13.sp,
                    color = Blue,
                    modifier = Modifier.clickable { showAllRecords = true }
                )
            }
            Spacer(Modifier.height(10.dp))
            records.take(3).forEach { r ->
                RecordCard(r)
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 功能入口按钮 */
@Composable
private fun ActionEntry(
    icon: ImageVector,
    title: String,
    sub: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 1.dp
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BlueLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Blue, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextMain)
                Spacer(Modifier.height(3.dp))
                Text(sub, fontSize = 12.sp, color = TextSub)
            }
            Text("›", fontSize = 20.sp, color = TextSub)
        }
    }
}

/** 一条导入记录卡片 */
@Composable
private fun RecordCard(r: ImportRecord) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Description,
                null,
                tint = Green,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                r.fileName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextMain,
                modifier = Modifier.weight(1f)
            )
            StatusChip(r.status)
        }
        Spacer(Modifier.height(8.dp))
        Text("${r.time}    操作人：${r.operator}", fontSize = 12.sp, color = TextSub)
        Spacer(Modifier.height(4.dp))
        Text(r.summary, fontSize = 12.sp, color = TextSub)
    }
}

/** 「查看全部」：独立页面展示最近 RECORD_RANGE_DAYS 天的导入记录 */
@Composable
private fun AllRecordsScreen(records: List<ImportRecord>, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFFF4F5F7))) {
        SubTopBar("导入记录", onBack)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "最近 $RECORD_RANGE_DAYS 天",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextMain
            )
            Spacer(Modifier.width(6.dp))
            Text("共 ${records.size} 条", fontSize = 12.sp, color = TextSub)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            records.forEach { r ->
                AppCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                r.fileName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMain
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${r.time} · 操作人：${r.operator}",
                                fontSize = 11.sp,
                                color = TextSub
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(r.summary, fontSize = 11.sp, color = TextSub)
                        }
                        Spacer(Modifier.width(8.dp))
                        StatusChip(r.status)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * 数据预览与二次确认页（用例 2 末尾：系统提示用户进行数据预览与确认；用例 3：二次确认上传）。
 *
 * - 列出本次将要写入数据库的每一条航班（已飞完的 locked 行不展示，不参与导入）；
 * - 跨天航班显式标注「将拆分为两段入库」；
 * - 底部「确认上传」执行真正落库（见 ImportScreen.doConfirmUpload），
 *   「返回修改」取消预览、回到表单（用例 3a1：清除缓存数据、取消数据预览）。
 */
@Composable
private fun PreviewScreen(
    data: Map<String, List<FlightDraft>>,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val rowsToImport = data.values.flatten().filter { !it.locked }
    val totalFlights = rowsToImport.size
    // 跨天航班会拆两段，故实际入库记录数可能大于航班数
    val totalSegments = rowsToImport.sumOf { if (it.isOvernight) 2 else 1 }

    Column(Modifier.fillMaxSize().background(Color(0xFFF4F5F7))) {
        SubTopBar("数据预览与确认", onCancel)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            AppCard {
                Text("导入概览", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    PreviewStat("航班数", "$totalFlights", Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    PreviewStat("入库记录", "$totalSegments", Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    PreviewStat("状态", "待分配", Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "所有通过校验的航班将持久化至数据库，状态统一为「待分配」。请确认以下数据无误。",
                    fontSize = 12.sp,
                    color = TextSub
                )
            }
            Spacer(Modifier.height(12.dp))

            data.forEach { (date, rows) ->
                val visible = rows.filter { !it.locked }
                if (visible.isEmpty()) return@forEach
                Text(date, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Blue)
                Spacer(Modifier.height(8.dp))
                visible.forEach { r ->
                    AppCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                r.flightNo.ifBlank { "（未填）" },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMain,
                                modifier = Modifier.weight(1f)
                            )
                            StatusChip(r.flightType) // 国内 / 国际 标签
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${r.direction} · ${r.aircraft} · ${r.origin} → ${r.dest}",
                            fontSize = 13.sp,
                            color = TextSub
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "占用窗口 ${r.arrivalTime} – ${r.departureTime}",
                            fontSize = 13.sp,
                            color = TextSub
                        )
                        if (r.isOvernight) {
                            Spacer(Modifier.height(4.dp))
                            val a = r.arrivalTime
                            val d = r.departureTime
                            Text(
                                "⚠ 跨天航班：将自动拆分为「${a}→24:00」与「次日 0:00→${d}」两段分别入库",
                                fontSize = 11.sp,
                                color = Blue
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Surface(color = Color.White, shadowElevation = 8.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    Text("返回修改", fontSize = 14.sp)
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = onConfirm,
                    enabled = totalFlights > 0,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue)
                ) {
                    Text("确认上传", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** 预览页顶部的小统计块 */
@Composable
private fun PreviewStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF4F5F7))
            .padding(vertical = 8.dp, horizontal = 6.dp)
    ) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Blue)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = TextSub)
    }
}

@Composable
fun FieldChip(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFF4F5F7))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text("*", color = Color(0xFFE53935), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(2.dp))
        Text(text, fontSize = 12.sp, color = TextMain)
    }
}
