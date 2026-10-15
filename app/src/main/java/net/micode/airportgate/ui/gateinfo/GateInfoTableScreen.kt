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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import net.micode.airportgate.model.GateInfoOptions
import net.micode.airportgate.model.GateInfoRow
import net.micode.airportgate.model.timeToMinutes
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
// 字段口径**按组长给分配引擎的约定**（不是早期的「近机位 / C类 / 开放」那套）：
//   停机位种类  廊桥 / 远机位                -> 引擎据此「宽体优先廊桥」
//   可停靠机型  系列名多选（A320,A330-300…） -> 引擎做包含匹配，不比翼展/长度
//   状态        可用 / 维护 / 停用           -> 引擎只分配「可用」
//
// 另外两个组长点名要的字段（安全间隔、可用时段）在四列表里横着放不下，
// 收进「点行展开」的展开区里改 —— 主表一眼能看 6~7 个机位，字段一个不少。

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
        aircraftClass = "",
        minInterval = GateInfoOptions.DEFAULT_INTERVAL,
        openTime = "06:00",
        closeTime = "23:00",
        status = GateInfoOptions.STATUS_AVAILABLE
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
                HeaderCell("机位编号", Modifier.weight(1f))
                HeaderCell("停机位种类", Modifier.weight(1f))
                HeaderCell("可停靠机型", Modifier.weight(1f))
                HeaderCell("状态", Modifier.weight(1f))
                Spacer(Modifier.width(30.dp))
            }
        }

        // ---- 数据行 ----
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp)
        ) {
            if (rows.isEmpty()) {
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
            } else {
                rows.forEach { row ->
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
                Spacer(Modifier.height(60.dp))
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
                    "共 ${rows.size} 行 · 点行首箭头可改安全间隔与可用时段 · 编号为空的行不保存",
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
                hint = "B27",
                onValueChange = { onChange(row.copy(code = it.take(10))) },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(4.dp))

            // 停机位种类
            CellDropdown(
                value = row.bridge,
                options = GateInfoOptions.BRIDGE_TYPES,
                valueColor = if (row.isBridge) Blue else Orange,
                onSelect = { onChange(row.copy(bridge = it)) },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(4.dp))

            // 可停靠机型（系列名多选，单元格里只显示摘要）
            SeriesCell(
                selected = row.aircraftClasses,
                onToggle = { series ->
                    val cur = row.aircraftClasses.toMutableList()
                    if (series in cur) cur.remove(series) else cur.add(series)
                    // 按固定顺序回写，避免勾选顺序影响存储内容
                    val ordered = GateInfoOptions.AIRCRAFT_SERIES.filter { it in cur }
                    onChange(row.copy(aircraftClass = ordered.joinToString(",")))
                },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(4.dp))

            // 状态
            CellDropdown(
                value = row.status,
                options = GateInfoOptions.STATUS_TYPES,
                valueColor = statusColor(row.status),
                onSelect = { onChange(row.copy(status = it)) },
                modifier = Modifier.weight(1f)
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

                    if (row.aircraftClasses.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "可停靠机型：" + row.aircraftClasses.joinToString(", ") +
                                " · 引擎按系列名包含匹配",
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
 * 表格单元格：可停靠机型（多选）。
 * 格子窄，只显示摘要（第一个系列名 +N），完整清单在展开区里看。
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
        selected.size == 1 -> selected.first()
        else -> "${selected.first()}+${selected.size - 1}"
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
                GateInfoOptions.AIRCRAFT_SERIES.forEach { opt ->
                    val checked = opt in selected
                    DropdownMenuItem(
                        text = {
                            Text(
                                opt,
                                fontSize = 13.sp,
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
