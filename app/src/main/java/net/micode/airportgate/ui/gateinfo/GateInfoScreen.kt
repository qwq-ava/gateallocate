package net.micode.airportgate.ui.gateinfo

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.micode.airportgate.data.model.DataSync
import net.micode.airportgate.data.model.GateInfoOptions
import net.micode.airportgate.data.model.GateInfoRow
import net.micode.airportgate.data.model.GateInfoStore
import net.micode.airportgate.ui.components.AppCard
import net.micode.airportgate.ui.components.SubTopBar
import net.micode.airportgate.ui.components.VerticalDividerCompat
import net.micode.airportgate.ui.theme.Blue
import net.micode.airportgate.ui.theme.TextMain
import net.micode.airportgate.ui.theme.TextSub

// ========================= 机位信息维护 =========================
//
// 分两层：
//   1) GateInfoRoute：子导航（概览 <-> 可编辑表格）+ 落库，MainActivity 调它。
//   2) GateInfoScreen：概览页。只保留统计卡与「更改机位信息 / 恢复内置数据」两个入口，
//      逐条机位明细在可编辑表格页查看，概览页不再重复列出。
//
// 数据来自天津滨海国际机场《航空器运行保障方案》2014-08 逐编号停机位表，
// 由 data/model/RealGateData.kt 内置；报告/答辩须注明数据期号。

/**
 * 机位信息维护总入口。
 *
 * @param onBack  返回主界面
 * @param showMsg 弹提示（保存 / 恢复数据后反馈）
 */
@Composable
fun GateInfoRoute(onBack: () -> Unit, showMsg: (String) -> Unit) {
    val context = LocalContext.current
    // 进入界面 / 从表格返回时都重读一次，保证统计跟最新数据一致
    var rows by remember { mutableStateOf(GateInfoStore.load(context)) }
    var editing by remember { mutableStateOf(false) }

    if (editing) {
        // 表格里按返回键 -> 回概览页，而不是直接退出到主界面
        BackHandler { editing = false }
        GateInfoTableScreen(
            initial = rows,
            onConfirm = { saved ->
                rows = saved
                GateInfoStore.save(context, saved)
                // 机位数据已变更，触发分配界面自动重排
                DataSync.bump()
                editing = false
                showMsg("机位信息已保存：共 ${saved.size} 个机位")
            },
            onCancel = { editing = false }
        )
    } else {
        GateInfoScreen(
            rows = rows,
            onOpenTable = {
                rows = GateInfoStore.load(context)
                editing = true
            },
            onBack = onBack,
            showMsg = showMsg,
            onResetRealData = {
                rows = GateInfoStore.resetToRealData(context)
                DataSync.bump()
                showMsg("已恢复为 125 条真实停机位数据")
            }
        )
    }
}

// ========================= 概览页（机位维护组员实现，已接入真实数据） =========================

@Composable
fun GateInfoScreen(
    rows: List<GateInfoRow>,
    onOpenTable: () -> Unit,
    onBack: () -> Unit,
    showMsg: (String) -> Unit,
    onResetRealData: () -> Unit = {}
) {
    val total = rows.size
    val bridge = rows.count { it.isBridge }
    val remote = total - bridge
    val available = rows.count { it.available }
    val maintenance = rows.count { it.status == GateInfoOptions.STATUS_MAINTENANCE }
    val disabled = rows.count { it.status == GateInfoOptions.STATUS_DISABLED }
    val east = rows.count { it.area == "东区" }
    val west = rows.count { it.area == "西区" }
    val gradeStat = GateInfoOptions.AIRCRAFT_CLASSES.mapNotNull { grade ->
        val n = rows.count { grade in it.aircraftClasses }
        if (n > 0) "$grade 类 $n" else null
    }.joinToString(" · ")
    var showResetConfirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        SubTopBar("机位信息维护", onBack)
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                AppCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        StatCol("机位总数", "$total", TextMain, Modifier.weight(1f))
                        VerticalDividerCompat()
                        StatCol("廊桥", "$bridge", Blue, Modifier.weight(1f))
                        VerticalDividerCompat()
                        StatCol("远机位", "$remote", TextMain, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "可用 $available · 维护 $maintenance · 停用 $disabled",
                        fontSize = 12.sp,
                        color = TextSub,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    if (east + west > 0) {
                        Text(
                            "东区 $east · 西区 $west",
                            fontSize = 12.sp,
                            color = TextSub,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                    if (gradeStat.isNotEmpty()) {
                        Text(
                            gradeStat,
                            fontSize = 11.sp,
                            color = TextSub,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    // 更改机位信息 -> 可编辑表格，确定后整表落库
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Blue, RoundedCornerShape(8.dp))
                            .clickable { onOpenTable() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("更改机位信息", color = Blue, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    // 恢复内置权威数据（二次确认，避免误覆盖手工修改）
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF4F5F7))
                            .clickable { showResetConfirm = true }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("恢复内置 125 条真实停机位数据", color = TextSub, fontSize = 12.sp)
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("恢复真实机位数据") },
            text = {
                Text("将用内置的 125 条天津滨海机场真实停机位替换当前列表，当前的手工修改会被覆盖。确定继续？")
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    onResetRealData()
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
fun StatCol(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = valueColor)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 13.sp, color = TextSub)
    }
}
