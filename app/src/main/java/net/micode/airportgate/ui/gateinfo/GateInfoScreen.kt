package net.micode.airportgate.ui.gateinfo

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import net.micode.airportgate.ui.components.SubTopBar
import net.micode.airportgate.ui.components.VerticalDividerCompat
import net.micode.airportgate.ui.theme.Blue
import net.micode.airportgate.ui.theme.TextMain
import net.micode.airportgate.ui.theme.TextSub

// ========================= 机位信息维护 =========================

@Composable
fun GateInfoScreen(onBack: () -> Unit, showMsg: (String) -> Unit) {
    var total by remember { mutableIntStateOf(128) }
    var bridge by remember { mutableIntStateOf(52) }
    var remote by remember { mutableIntStateOf(76) }
    var showEdit by remember { mutableStateOf(false) }

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
                    StatCol("廊桥机位", "$bridge", Blue, Modifier.weight(1f))
                    VerticalDividerCompat()
                    StatCol("远机位", "$remote", TextMain, Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                // 更改机位信息按钮
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Blue, RoundedCornerShape(8.dp))
                        .clickable { showEdit = true }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("更改机位信息", color = Blue, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(14.dp))
            // 机位明细示例
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

    // 编辑对话框
    if (showEdit) {
        var t by remember { mutableIntStateOf(total) }
        var b by remember { mutableIntStateOf(bridge) }
        var r by remember { mutableIntStateOf(remote) }
        AlertDialog(
            onDismissRequest = { showEdit = false },
            title = { Text("更改机位信息", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    NumberStepper("机位总数", t) { t = it }
                    NumberStepper("廊桥机位", b) { b = it }
                    NumberStepper("远机位", r) { r = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    total = t; bridge = b; remote = r
                    showEdit = false
                    showMsg("机位信息已更新：共 $total 个（廊桥 $bridge · 远机位 $remote）")
                }) { Text("保存", color = Blue, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showEdit = false }) { Text("取消", color = TextSub) }
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

@Composable
fun NumberStepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = TextMain, modifier = Modifier.weight(1f))
        TextButton(onClick = { if (value > 0) onChange(value - 1) }) { Text("−", fontSize = 18.sp) }
        Text(
            value.toString(),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextMain,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.width(48.dp)
        )
        TextButton(onClick = { onChange(value + 1) }) { Text("+", fontSize = 18.sp) }
    }
}
