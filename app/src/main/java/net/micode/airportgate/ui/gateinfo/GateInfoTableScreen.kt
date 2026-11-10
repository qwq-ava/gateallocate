package net.micode.airportgate.ui.gateinfo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.micode.airportgate.data.model.GateInfoOptions
import net.micode.airportgate.data.model.GateInfoRow
import net.micode.airportgate.data.model.timeToMinutes
import net.micode.airportgate.ui.components.SubTopBar
import net.micode.airportgate.ui.theme.Blue
import net.micode.airportgate.ui.theme.Green
import net.micode.airportgate.ui.theme.Orange
import net.micode.airportgate.ui.theme.TextMain
import net.micode.airportgate.ui.theme.TextSub
import java.util.UUID

// ========================= 机位信息表（可编辑，横向四列） =========================
//
// 布局照机位维护组员给出的设计稿：一张横向表格，每行一个机位，
// 四列 —— 机位编号 / 停机位种类 / 可停靠机型 / 状态，行尾删除。
//
// 字段口径（2026-11-10 换成天津滨海机场真实停机位数据）：
//   停机位种类  17 种真实功能用途（CSV function 列）-> 选择后自动同步内部廊桥标记
//   可停靠机型  ICAO 等级 A–F 多选              -> 引擎按机型等级匹配（兼容历史系列名）
//   状态        可用 / 维护 / 停用              -> 引擎只分配「可用」
//
// 安全间隔、可用时段、区域、航站楼、机坪分区、备注收进「点行展开」区里改，
// 全部字段随底部「确定」整表落库；125 行用 LazyColumn 渲染。

/** 输入 0615 -> 06:15 */
private fun formatTimeInput(raw: String): String {
    val d = raw.filter { it.isDigit() }.take(4)
    return when {
        d.length <= 2 -> d
        d.length == 3 -> "${d.substring(0, 2)}:${d.substring(2)}"
        else -> "${d.substring(0, 2)}:${d.substring(2, 4)}"
    }
}

/** 状态取色：可用=绿，维护=橙，停用=灰 */
private fun statusColor(status: String): Color = when (status) {
    GateInfoOptions.STATUS_AVAILABLE -> Green
    GateInfoOptions.STATUS_MAINTENANCE -> Orange
    else -> TextSub
}

private val CELL_BORDER = Color(0xFFE5E7EB)

@Composable
fun GateInfoTableScreen(
    initial: List<GateInfoRow>,
    onConfirm: (List<GateInfoRow>) -> Unit,
    onCancel: () -> Unit
) {
    // 工作副本：改动先写在这里，按「确定」才提交并保存
    var rows by remember { mutableStateOf(initial.map { it.copy() }) }
    // 当前展开（改安全间隔 / 可用时段）的行；同时只展开一行
    var expandedId by remember { mutableStateOf<String?>(null) }

    fun newRow(): GateInfoRow = GateInfoRow(
        id = UUID.randomUUID().toString(),
        code = "",
        bridge = GateInfoOptions.BRIDGE,
        aircraftClass = "C",
        minInterval = GateInfoOptions.DEFAULT_INTERVAL,
        openTime = "00:00",
        closeTime = "24:00",
        status = GateInfoOptions.STATUS_AVAILABLE,
        gateType = GateInfoOptions.DEFAULT_GATE_TYPE
    )

    fun updateRow(id: String, transform: (GateInfoRow) -> GateInfoRow) {
        rows = rows.map { if (it.id == id) transform(it) else it }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF4F5F7))) {
        SubTopBar(
            title = "机位信息表",
            onBack = onCancel,
            action = {
                TextButton(onClick = { rows = rows + newRow() }) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Blue)
                    Spacer(Modifier.width(2.dp))
                    Text("添加机位", color = Blue, fontSize = 14.sp)
                }
            }
        )

        // ---- 表头 ----
        Surface(color = Color(0xFFF4F5F7)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(22.dp))
                HeaderCell("机位编号", Modifier.weight(0.8f))
                HeaderCell("停机位种类", Modifier.weight(1.6f))
                HeaderCell("可停靠机型", Modifier.weight(0.9f))
                HeaderCell("状态", Modifier.weight(0.8f))
                Spacer(Modifier.width(30.dp))
            }
        }

        // ---- 数据行：125 条真实机位用惰性列表，避免一次性组合上千个输入框 ----
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            if (rows.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无机位\n点右上角「添加机位」开始录入",
                            fontSize = 13.sp,
                            color = TextSub,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
            } else {
                items(rows, key = { it.id }) { row ->
                    Column {
                        GateTableRow(
                            row = row,
                            expanded = expandedId == row.id,
                            onToggleExpand = {
                                expandedId = if (expandedId == row.id) null else row.id
                            },
                            onChange = { updated -> updateRow(row.id) { updated } },
                            onDelete = {
                                if (expandedId == row.id) expandedId = null
                                rows = rows.filter { it.id != row.id }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                item { Spacer(Modifier.height(60.dp)) }
            }
        }

        // ---- 底部：取消 / 确定 ----
        Surface(color = Color.White, shadowElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF0F1F3))
                            .clickable { onCancel() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("取消", color = TextMain, fontSize = 15.sp)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Blue)
                            .clickable {
                                // 提交：去首尾空格，机位编号为空的整行不保存
                                val cleaned = rows
                                    .map { it.copy(code = it.code.trim()) }
                                    .filter { it.code.isNotEmpty() }
                                onConfirm(cleaned)
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "确定",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "共 ${rows.size} 行 · 点行首箭头可改安全间隔、时段与真实档案字段 · 编号为空的行不保存",
                    fontSize = 11.sp,
                    color = TextSub,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 12.sp,
        color = TextSub,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
        textAlign = TextAlign.Center
    )
}

/** 一行 = 箭头 + 四个单元格 + 删除；展开时下方追加次要字段 */
@Composable
private fun GateTableRow(
    row: GateInfoRow,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onChange: (GateInfoRow) -> Unit,
    onDelete: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 展开 / 收起的箭头
            Box(
                Modifier
                    .size(22.dp)
                    .clickable { onToggleExpand() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowDown
                    else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) "收起" else "展开更多设置",
                    tint = Blue,
                    modifier = Modifier.size(18.dp)
                )
            }

            // 机位编号
            CellInput(
                value = row.code,
                hint = "101",
                onValueChange = { onChange(row.copy(code = it.take(10))) },
                modifier = Modifier.weight(0.8f)
            )
            Spacer(Modifier.width(4.dp))

            // 停机位种类：17 种真实功能用途；选完自动同步内部廊桥标记给引擎
            CellDropdown(
                value = row.gateType,
                options = GateInfoOptions.GATE_TYPES,
                valueColor = if (row.isBridge) Blue else Orange,
                onSelect = {
                    onChange(
                        row.copy(
                            gateType = it,
                            bridge = GateInfoOptions.bridgeOf(it)
                        )
                    )
                },
                modifier = Modifier.weight(1.6f)
            )
            Spacer(Modifier.width(4.dp))

            // 可停靠机型：ICAO 等级 A–F（多选，单元格里只显示摘要）
            SeriesCell(
                selected = row.aircraftClasses,
                onToggle = { grade ->
                    val cur = row.aircraftClasses.toMutableList()
                    if (grade in cur) cur.remove(grade) else cur.add(grade)
                    // 按固定顺序回写，避免勾选顺序影响存储内容
                    val ordered = GateInfoOptions.AIRCRAFT_CLASSES.filter { it in cur }
                    onChange(row.copy(aircraftClass = ordered.joinToString(",")))
                },
                modifier = Modifier.weight(0.9f)
            )
            Spacer(Modifier.width(4.dp))

            // 状态
            CellDropdown(
                value = row.status,
                options = GateInfoOptions.STATUS_TYPES,
                valueColor = statusColor(row.status),
                onSelect = { onChange(row.copy(status = it)) },
                modifier = Modifier.weight(0.8f)
            )

            // 删除
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除本条",
                    tint = Color(0xFF9AA0A6),
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        // ---- 展开区：安全间隔 + 可用时段 ----
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(10.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        SmallInput(
                            label = "安全间隔（分钟）",
                            value = row.minInterval.toString(),
                            hint = "20",
                            keyboardType = KeyboardType.Number,
                            onValueChange = { raw ->
                                val n = raw.filter { it.isDigit() }.take(3).toIntOrNull() ?: 0
                                onChange(row.copy(minInterval = n))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        SmallInput(
                            label = "可用时段起",
                            value = row.openTime,
                            hint = "06:00",
                            keyboardType = KeyboardType.Number,
                            onValueChange = { onChange(row.copy(openTime = formatTimeInput(it))) },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        SmallInput(
                            label = "可用时段止",
                            value = row.closeTime,
                            hint = "23:00",
                            keyboardType = KeyboardType.Number,
                            onValueChange = { onChange(row.copy(closeTime = formatTimeInput(it))) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 可用时段校验
                    val o = timeToMinutes(row.openTime)
                    val c = timeToMinutes(row.closeTime)
                    if (o != null && c != null && c <= o) {
                        Spacer(Modifier.height(6.dp))
                        Text("可用时段止需晚于起", fontSize = 11.sp, color = Color(0xFFE24B4A))
                    }

                    // 真实档案字段：区域 / 航站楼 / 机坪分区 / 备注，同样可改、随「确定」一起落库
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        SmallInput(
                            label = "区域",
                            value = row.area,
                            hint = "东区",
                            onValueChange = { onChange(row.copy(area = it.take(20))) },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        SmallInput(
                            label = "航站楼",
                            value = row.terminal,
                            hint = "T1",
                            onValueChange = { onChange(row.copy(terminal = it.take(20))) },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        SmallInput(
                            label = "机坪分区",
                            value = row.zone,
                            hint = "东一区",
                            onValueChange = { onChange(row.copy(zone = it.take(20))) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    SmallInput(
                        label = "备注",
                        value = row.remark,
                        hint = "运行保障备注",
                        onValueChange = { onChange(row.copy(remark = it.take(120))) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (row.aircraftClasses.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "停机位种类：" + row.gateType + "（" + row.bridge + "）",
                            fontSize = 10.sp,
                            color = Color(0xFF9AA0A6)
                        )
                        Text(
                            "可停靠机型：" + row.aircraftClasses.joinToString("、") { "$it 类" } +
                                " · 分配引擎按 ICAO 等级匹配",
                            fontSize = 10.sp,
                            color = Color(0xFF9AA0A6)
                        )
                    }
                }
            }
        }
    }
}

/** 表格单元格：文本输入 */
@Composable
private fun CellInput(
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .border(1.dp, CELL_BORDER, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 13.sp, color = TextMain),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(hint, fontSize = 12.sp, color = Color(0xFFBBBBBB))
                    }
                    inner()
                }
            }
        )
    }
}

/** 表格单元格：单选下拉 */
@Composable
private fun CellDropdown(
    value: String,
    options: List<String>,
    valueColor: Color,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White)
                .border(1.dp, if (expanded) Blue else CELL_BORDER, RoundedCornerShape(6.dp))
                .clickable { expanded = true }
                .padding(start = 6.dp, end = 2.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    value,
                    fontSize = 13.sp,
                    color = valueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "选择",
                    tint = if (expanded) Blue else Color(0xFF9AA0A6),
                    modifier = Modifier.size(16.dp)
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                opt,
                                fontSize = 13.sp,
                                color = if (opt == value) Blue else TextMain
                            )
                        },
                        trailingIcon = {
                            if (opt == value) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Blue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        onClick = { onSelect(opt); expanded = false }
                    )
                }
            }
        }
    }
}

/**
 * 表格单元格：可停靠机型 ICAO 等级 A–F（多选）。
 * 格子窄，只显示摘要（第一个等级 +N），完整等级与代表机型在下拉 / 展开区里看。
 */
@Composable
private fun SeriesCell(
    selected: List<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val summary = when {
        selected.isEmpty() -> "选择"
        selected.size == 1 -> "${selected.first()} 类"
        else -> "${selected.first()} 类+${selected.size - 1}"
    }
    Box(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White)
                .border(1.dp, if (expanded) Blue else CELL_BORDER, RoundedCornerShape(6.dp))
                .clickable { expanded = true }
                .padding(start = 6.dp, end = 2.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    summary,
                    fontSize = 13.sp,
                    color = if (selected.isEmpty()) Color(0xFFBBBBBB) else TextMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "选择机型",
                    tint = if (expanded) Blue else Color(0xFF9AA0A6),
                    modifier = Modifier.size(16.dp)
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                GateInfoOptions.AIRCRAFT_CLASSES.forEach { opt ->
                    val checked = opt in selected
                    DropdownMenuItem(
                        text = {
                            Text(
                                "$opt 类 · ${GateInfoOptions.GRADE_HINT[opt] ?: ""}",
                                fontSize = 12.sp,
                                color = if (checked) Blue else TextMain,
                                fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        trailingIcon = {
                            if (checked) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Blue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        onClick = { onToggle(opt) }
                    )
                }
            }
        }
    }
}

/** 展开区里的带标题小输入框 */
@Composable
private fun SmallInput(
    label: String,
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(modifier) {
        Text(label, fontSize = 10.sp, color = TextSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFFBFBFC))
                .border(1.dp, CELL_BORDER, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, color = TextMain),
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Next
                ),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) Text(hint, fontSize = 12.sp, color = Color(0xFFBBBBBB))
                        inner()
                    }
                }
            )
        }
    }
}
