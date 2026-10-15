package net.micode.airportgate.ui.gateinfo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.micode.airportgate.model.GateInfoOptions
import net.micode.airportgate.model.GateInfoRow
import net.micode.airportgate.model.GateInfoStore
import net.micode.airportgate.ui.components.AppCard
import net.micode.airportgate.ui.components.SubTopBar
import net.micode.airportgate.ui.components.VerticalDividerCompat
import net.micode.airportgate.ui.theme.Blue
import net.micode.airportgate.ui.theme.TextMain
import net.micode.airportgate.ui.theme.TextSub

// ========================= 机位信息维护 =========================
//
// 本文件分两层：
//
//   1) GateInfoScreen（机位维护组员实现）
//      概览页：统计三栏 + 「更改机位信息」按钮 + 机位分区明细。
//      纯展示，不碰存储，数据由外部以 rows 传入。
//
//   2) GateInfoRoute（本项目接线用）
//      负责子导航（概览 <-> 可编辑表格）与 SharedPreferences 落盘，
//      MainActivity 调用的是它，签名与原来一致，导航代码不用改。
//
// 字段口径按组长给分配引擎的约定：bridge 用「廊桥 / 远机位」明确枚举
// （不再是「近机位 / 远机位」），status 用「可用 / 维护 / 停用」。

/**
 * 机位信息维护总入口。
 *
 * @param onBack  返回主界面
 * @param showMsg 弹提示（保存成功后反馈）
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
            showMsg = showMsg
        )
    }
}

// ========================= 以下为机位维护组员的实现 =========================

@Composable
fun GateInfoScreen(
    rows: List<GateInfoRow>,
    onOpenTable: () -> Unit,
    onBack: () -> Unit,
    showMsg: (String) -> Unit
) {
    val total = rows.size
    val bridge = rows.count { it.isBridge }
    val remote = total - bridge
    // 组长口径：引擎只分配「可用」机位，另外两个状态单列出来便于核对
    val available = rows.count { it.available }
    val maintenance = rows.count { it.status == GateInfoOptions.STATUS_MAINTENANCE }
    val disabled = rows.count { it.status == GateInfoOptions.STATUS_DISABLED }

    Column(Modifier.fillMaxSize()) {
        SubTopBar("机位信息维护", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
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
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                // 更改机位信息按钮 → 跳转到机位信息表
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
            }
            Spacer(Modifier.height(14.dp))
            // 机位分区明细（参考信息）
            val gateRows = listOf(
                "B01 - B13" to "T1 廊桥 · 近机位 · 可停靠 A330 及以下机型",
                "B14 - B26" to "T1 廊桥 · 近机位 · 可停靠宽体机",
                "201 - 224" to "T2 廊桥 · 近机位 · 双桥载电源",
                "C01 - C30" to "T1 远机位 · 需摆渡车保障",
                "D01 - D46" to "T2 远机位 · 需摆渡车保障"
            )
            AppCard {
                Text("机位分区明细", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextMain)
                Spacer(Modifier.height(8.dp))
                gateRows.forEachIndexed { i, (range, desc) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(
                            range,
                            fontSize = 14.sp,
                            color = Blue,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(100.dp)
                        )
                        Text(desc, fontSize = 13.sp, color = TextSub, modifier = Modifier.weight(1f))
                    }
                    if (i != gateRows.lastIndex) HorizontalDivider(color = Color(0xFFF0F1F3))
                }
            }
        }
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
