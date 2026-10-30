package net.micode.airportgate.ui.import

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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.micode.airportgate.data.model.FlightDraft
import net.micode.airportgate.data.model.dateOf
import net.micode.airportgate.ui.components.SubTopBar
import net.micode.airportgate.ui.theme.Blue
import net.micode.airportgate.ui.theme.BlueLight
import net.micode.airportgate.ui.theme.Orange
import net.micode.airportgate.ui.theme.OrangeLight
import net.micode.airportgate.ui.theme.TextMain
import net.micode.airportgate.ui.theme.TextSub

// ========================= 航班信息录入 / 修改 =========================
//
// 形式：卡片式逐条录入（一个航班一张卡片，字段竖排）。
// 相比之前的宽表，取消了横向滚动；机型 / 机场改为下拉点选；
// 新增时自动复制上一条的机型、机场、进出港，只改航班号和时刻即可。
//
// 时间口径：卡片收「到达时刻 / 离开时刻」两个值，即机位占用窗口，
// 落库为 t_flight.arrival_time / departure_time（INT 当日分钟数）；
// 「计划起降」不再手填，由进出港派生（进港取到达、出港取离开），只用于展示兜底。

/** 录入模式：新建导入 / 修改已有 */
enum class FlightFormMode { CREATE, EDIT }

/** 常用机型（下拉点选） */
private val AIRCRAFT_OPTIONS = listOf(
    "A319", "A320", "A320neo", "A321", "A321neo",
    "A330-200", "A330-300", "A350-900",
    "B737-800", "B737 MAX 8", "B787-9", "B777-300ER"
)

/** 常用机场（下拉点选，也支持手动输入） */
private val AIRPORT_OPTIONS = listOf(
    "上海浦东", "上海虹桥", "北京首都", "北京大兴", "广州白云", "深圳宝安",
    "成都天府", "重庆江北", "昆明长水", "西安咸阳", "杭州萧山", "南京禄口",
    "武汉天河", "长沙黄花", "郑州新郑", "厦门高崎", "青岛胶东", "大连周水子",
    "哈尔滨太平", "沈阳桃仙", "三亚凤凰", "海口美兰", "天津滨海", "贵阳龙洞堡",
    "南宁吴圩", "兰州中川", "乌鲁木齐地窝堡", "呼和浩特白塔"
)

/** 时间输入自动补冒号：0615 -> 06:15 */
private fun formatTimeInput(raw: String): String {
    val d = raw.filter { it.isDigit() }.take(4)
    return when {
        d.length <= 2 -> d
        d.length == 3 -> "${d.substring(0, 2)}:${d.substring(2)}"
        else -> "${d.substring(0, 2)}:${d.substring(2, 4)}"
    }
}

@Composable
fun FlightFormScreen(
    mode: FlightFormMode,
    rowsToday: SnapshotStateList<FlightDraft>,
    rowsTomorrow: SnapshotStateList<FlightDraft>,
    onBack: () -> Unit,
    onSubmit: (date: String, rows: List<FlightDraft>) -> Unit
) {
    var dayTab by remember { mutableIntStateOf(0) }
    val vScroll = rememberScrollState()

    val date = if (dayTab == 0) dateOf(0) else dateOf(1)
    val rows = if (dayTab == 0) rowsToday else rowsTomorrow
    val invalidCount = rows.count { !it.valid }

    Column(Modifier.fillMaxSize().background(Color(0xFFF4F5F7))) {
        SubTopBar(if (mode == FlightFormMode.CREATE) "数据导入" else "航班信息更改", onBack)

        // ---- 今天 / 明天 页签 ----
        Surface(color = Color.White, shadowElevation = 1.dp) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF4F5F7))
                ) {
                    listOf("今天航班信息" to dateOf(0), "明天航班信息" to dateOf(1))
                        .forEachIndexed { i, (label, d) ->
                            val active = dayTab == i
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) BlueLight else Color.Transparent)
                                    .clickable { dayTab = i }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    label,
                                    fontSize = 14.sp,
                                    color = if (active) Blue else TextSub,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(d, fontSize = 11.sp, color = if (active) Blue else TextSub)
                            }
                        }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "共 ${rows.size} 条 · 航班日期 $date",
                        fontSize = 12.sp,
                        color = TextSub
                    )
                    Text(
                        if (invalidCount == 0) "全部完整" else "$invalidCount 条待补充",
                        fontSize = 12.sp,
                        color = if (invalidCount == 0) Blue else Orange,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ---- 卡片列表 ----
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(vScroll)
        ) {
            if (rows.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "暂无航班\n点击下方「新增航班」开始录入",
                        fontSize = 13.sp,
                        color = TextSub,
                        lineHeight = 20.sp
                    )
                }
            } else {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    rows.forEachIndexed { index, row ->
                        FlightCard(
                            index = index + 1,
                            row = row,
                            onChange = { rows[index] = it },
                            onDelete = { rows.removeAt(index) }
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    Spacer(Modifier.height(60.dp))
                }
            }
        }

        // ---- 底部操作条 ----
        Surface(color = Color.White, shadowElevation = 8.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 复制上一条新增：带上一条的机型/机场/进出港，只清空航班号和两个时刻
                OutlinedButton(
                    onClick = {
                        val last = rows.lastOrNull()
                        rows.add(
                            if (last != null) last.copy(
                                id = System.nanoTime(),
                                flightNo = "",
                                arrivalTime = "",
                                departureTime = ""
                            )
                            else FlightDraft(id = System.nanoTime())
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("复制新增", fontSize = 13.sp)
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { rows.add(FlightDraft(id = System.nanoTime())) },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "新增空白航班", tint = Blue)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onSubmit(date, rows.toList()) },
                    enabled = rows.isNotEmpty(),
                    modifier = Modifier
                        .weight(1.2f)
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue)
                ) {
                    Text(
                        if (mode == FlightFormMode.CREATE) "保存导入" else "保存修改",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/** 一个航班 = 一张卡片，字段竖排，不横向滚动 */
@Composable
private fun FlightCard(
    index: Int,
    row: FlightDraft,
    onChange: (FlightDraft) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(BlueLight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(index.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Blue)
                }
                Spacer(Modifier.width(8.dp))
                Text("航班 $index", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "删除本条",
                        tint = Color(0xFFE24B4A),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // 航班号 + 机型
            Row(Modifier.fillMaxWidth()) {
                LabeledField(
                    label = "航班号",
                    value = row.flightNo,
                    onValueChange = { onChange(row.copy(flightNo = it)) },
                    hint = "MU5102",
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                PickerField(
                    label = "机型",
                    value = row.aircraft,
                    onValueChange = { onChange(row.copy(aircraft = it)) },
                    options = AIRCRAFT_OPTIONS,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))

            // 到达 / 离开 —— 机位占用窗口，分配引擎以此为准
            Row(Modifier.fillMaxWidth()) {
                LabeledField(
                    label = "到达时刻",
                    value = row.arrivalTime,
                    onValueChange = { onChange(row.copy(arrivalTime = formatTimeInput(it))) },
                    hint = "06:15",
                    isTime = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                LabeledField(
                    label = "离开时刻",
                    value = row.departureTime,
                    onValueChange = { onChange(row.copy(departureTime = formatTimeInput(it))) },
                    hint = "07:00",
                    isTime = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))

            // 进出港 + 计划起降（派生，只读）
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("进出港", fontSize = 11.sp, color = TextSub)
                    Spacer(Modifier.height(4.dp))
                    DirChip(row.direction) { onChange(row.copy(direction = it)) }
                }
                Spacer(Modifier.width(8.dp))
                DerivedField(
                    label = "计划起降",
                    value = row.planTime,
                    tip = if (row.direction == FlightDraft.DIR_IN) "取到达" else "取离开",
                    modifier = Modifier.weight(1f)
                )
            }

            if (row.arrivalTime.length == 5 && row.departureTime.length == 5 && !row.orderValid) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "离开时刻需晚于到达时刻",
                    fontSize = 11.sp,
                    color = Color(0xFFE24B4A)
                )
            } else if (row.occupyMinutes > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "占用机位 ${row.occupyMinutes} 分钟（${row.arrivalTime} – ${row.departureTime}）",
                    fontSize = 11.sp,
                    color = TextSub
                )
            }

            Spacer(Modifier.height(8.dp))

            // 始发 / 目的
            PickerField(
                label = "始发机场",
                value = row.origin,
                onValueChange = { onChange(row.copy(origin = it)) },
                options = AIRPORT_OPTIONS,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            PickerField(
                label = "目的机场",
                value = row.dest,
                onValueChange = { onChange(row.copy(dest = it)) },
                options = AIRPORT_OPTIONS,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** 带标题的普通输入框 */
@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    isTime: Boolean = false
) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = TextSub)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (value.isBlank()) Color(0xFFFBFBFC) else Color.White)
                .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = TextMain),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isTime) KeyboardType.Number else KeyboardType.Text,
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

/** 派生只读字段：值由其他字段算出来（这里是「进港取到达 / 出港取离开」） */
@Composable
private fun DerivedField(
    label: String,
    value: String,
    tip: String,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = TextSub)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF4F5F7))
                .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (value.isEmpty()) "随到达/离开自动带出" else value,
                    fontSize = 14.sp,
                    color = if (value.isEmpty()) Color(0xFFBBBBBB) else TextSub
                )
                if (value.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Text(tip, fontSize = 10.sp, color = Color(0xFFBBBBBB))
                }
            }
        }
    }
}

/** 带下拉候选的输入框：可直接打字，也可点箭头从常用值里选 */
@Composable
private fun PickerField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = TextSub)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (value.isBlank()) Color(0xFFFBFBFC) else Color.White)
                .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = TextMain),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) Text("点右侧箭头选择", fontSize = 12.sp, color = Color(0xFFBBBBBB))
                            inner()
                        }
                    }
                )
                Icon(
                    Icons.Outlined.ArrowDropDown,
                    contentDescription = "选择$label",
                    tint = if (expanded) Blue else TextSub,
                    modifier = Modifier
                        .size(26.dp)
                        .clickable { expanded = true }
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                opt,
                                fontSize = 13.sp,
                                color = if (opt == value) Blue else TextMain,
                                fontWeight = if (opt == value) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = { onValueChange(opt); expanded = false }
                    )
                }
            }
        }
    }
}

/** 进出港：点击在「进港 / 出港」之间切换 */
@Composable
private fun DirChip(value: String, onChange: (String) -> Unit) {
    val isIn = value == FlightDraft.DIR_IN
    Box(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isIn) BlueLight else OrangeLight)
            .border(1.dp, if (isIn) BlueLight else OrangeLight, RoundedCornerShape(8.dp))
            .clickable { onChange(if (isIn) FlightDraft.DIR_OUT else FlightDraft.DIR_IN) }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isIn) Blue else Orange)
            Spacer(Modifier.width(2.dp))
            Icon(
                Icons.Outlined.SwapVert,
                contentDescription = "切换进出港",
                tint = if (isIn) Blue else Orange,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
