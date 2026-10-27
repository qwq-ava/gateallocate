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
import androidx.compose.material3.Icon
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
import net.micode.airportgate.model.DataSync
import net.micode.airportgate.model.FlightDraft
import net.micode.airportgate.model.ImportRecord
import net.micode.airportgate.model.dateOf
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

    // 表格数据放在这一层，关闭表格再进来时填写内容不会丢
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
            val t = GateRepository.loadFlights(context, dateOf(0))
            val tm = GateRepository.loadFlights(context, dateOf(1))
            rowsToday.clear(); rowsToday.addAll(t)
            rowsTomorrow.clear(); rowsTomorrow.addAll(tm)
            if (t.isEmpty() && tm.isEmpty()) {
                showMsg("近两天暂无已导入航班，可直接补录")
            }
        } else {
            if (rowsToday.isEmpty() && rowsTomorrow.isEmpty()) {
                // 卡片式一屏一条，先各给 1 条，其余用「复制新增」快速加
                rowsToday.add(FlightDraft(id = System.nanoTime()))
                rowsTomorrow.add(FlightDraft(id = System.nanoTime()))
            }
        }
        formMode = mode
    }

    if (formMode != null) {
        val mode = formMode!!
        FlightFormScreen(
            mode = mode,
            rowsToday = rowsToday,
            rowsTomorrow = rowsTomorrow,
            onBack = { formMode = null },
            onSubmit = { date, rows ->
                val valid = rows.filter { it.valid }
                val skipped = rows.size - valid.size
                val source = if (mode == FlightFormMode.CREATE) "手工录入" else "信息更改"
                val n = GateRepository.saveFlights(
                    context, date, valid,
                    source = source
                )
                showMsg(
                    "$date 已保存 $n 条" +
                        (if (skipped > 0) "，$skipped 条信息不完整未保存" else "")
                )
                // 写入真实导入批次记录（时间 / 来源 / 条数 / 操作人），取代写死演示数据
                GateRepository.addImportBatch(context, date, source, rows.size, n, skipped)
                importRefresh++
                // 航班数据已变更，触发分配界面自动重排
                DataSync.bump()
                formMode = null
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
                sub = "按表格填写今天 / 明天的航班计划",
                onClick = { openForm(FlightFormMode.CREATE) }
            )
            Spacer(Modifier.height(10.dp))
            ActionEntry(
                icon = Icons.Outlined.Edit,
                title = "航班信息更改",
                sub = "查询并修改已导入的航班信息",
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
