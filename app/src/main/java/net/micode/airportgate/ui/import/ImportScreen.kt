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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.micode.airportgate.ui.components.AppCard
import net.micode.airportgate.ui.components.SectionTitle
import net.micode.airportgate.ui.components.StatusChip
import net.micode.airportgate.ui.components.SubTopBar
import net.micode.airportgate.ui.theme.Blue
import net.micode.airportgate.ui.theme.BlueLight
import net.micode.airportgate.ui.theme.Green
import net.micode.airportgate.ui.theme.TextMain
import net.micode.airportgate.model.ImportRecord
import net.micode.airportgate.ui.theme.TextSub

// ========================= 航班导入 =========================

@Composable
fun ImportScreen(onBack: () -> Unit, showMsg: (String) -> Unit) {
    var step by remember { mutableIntStateOf(0) } // 当前所处步骤（0=选择文件）
    var tabIndex by remember { mutableIntStateOf(0) }
    val records = listOf(
        ImportRecord("航班计划_20260917.xlsx", "导入成功", "2026-09-17 09:24", "张伟", "共 128 条，成功 128 条，失败 0 条"),
        ImportRecord("航班计划_20260916.xlsx", "部分失败", "2026-09-16 16:32", "李娜", "共 116 条，成功 112 条，失败 4 条")
    )
    val requiredFields = listOf("航班号", "航班日期", "进出港", "机型", "计划时间", "起降机场")

    Column(Modifier.fillMaxSize()) {
        SubTopBar("航班导入", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ---- 步骤条 ----
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf("选择文件", "数据校验", "完成导入").forEachIndexed { i, label ->
                        StepDot(num = i + 1, label = label, active = i <= step)
                        if (i != 2) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(2.dp)
                                    .background(if (i < step) Blue else Color(0xFFE5E7EB))
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.CalendarMonth,
                        null,
                        tint = TextSub,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("当前班期：2026-09-17", fontSize = 13.sp, color = TextSub)
                }
            }

            Spacer(Modifier.height(14.dp))
            // ---- 导入方式 Tab ----
            AppCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF4F5F7))
                ) {
                    listOf("Excel 导入" to Icons.Outlined.Description, "航班信息更改" to Icons.Outlined.Edit)
                        .forEachIndexed { i, (label, icon) ->
                            val active = tabIndex == i
                            Row(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) BlueLight else Color.Transparent)
                                    .clickable { tabIndex = i }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(icon, null, tint = if (active) Blue else TextSub, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    label,
                                    fontSize = 14.sp,
                                    color = if (active) Blue else TextSub,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                }

                Spacer(Modifier.height(14.dp))
                Text("必填字段", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                Spacer(Modifier.height(8.dp))
                // 必填字段标签
                Row(Modifier.fillMaxWidth()) {
                    requiredFields.take(5).forEach { field ->
                        FieldChip(field, Modifier.padding(end = 8.dp, bottom = 8.dp))
                    }
                }
                Row {
                    FieldChip(requiredFields[5], Modifier.padding(end = 8.dp))
                }

                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        Modifier.clickable { showMsg("开始下载导入模板…") },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Download, null, tint = Blue, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("下载导入模板", fontSize = 13.sp, color = Blue)
                    }
                    Text("上次导入：2026-09-16 16:32", fontSize = 12.sp, color = TextSub)
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionTitle("最近导入记录") {
                Text(
                    "查看全部",
                    fontSize = 13.sp,
                    color = Blue,
                    modifier = Modifier.clickable { showMsg("共 36 条导入记录") }
                )
            }
            Spacer(Modifier.height(10.dp))
            records.forEach { r ->
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
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun StepDot(num: Int, label: String, active: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(30.dp)
                .background(if (active) Blue else Color(0xFFE5E7EB), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                num.toString(),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 12.sp,
            color = if (active) Blue else TextSub,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
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
