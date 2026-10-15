package net.micode.airportgate.ui.stats

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
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.micode.airportgate.model.RegionStat
import net.micode.airportgate.model.StatsData
import net.micode.airportgate.ui.components.AppCard
import net.micode.airportgate.ui.components.SectionTitle
import net.micode.airportgate.ui.components.SubTopBar
import net.micode.airportgate.ui.theme.Blue
import net.micode.airportgate.ui.theme.Green
import net.micode.airportgate.ui.theme.Orange
import net.micode.airportgate.ui.theme.TextMain
import net.micode.airportgate.ui.theme.TextSub

// ========================= 统计利用率 =========================

@Composable
fun StatsScreen(onBack: () -> Unit) {
    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("今日实时", "近 7 日", "本月")

    val statsData = listOf(
        StatsData(
            "78.4%", "较上一周期 +2.1%", "86.2%", "廊桥航班 247 / 286",
            "4.2 次/日", "行业均值 3.8 次/日", "08:00-10:00", "峰值利用率 94%",
            listOf(
                RegionStat("T1 廊桥机位", "28个机位 · 周转5.1次", 82.6f, Blue),
                RegionStat("T2 廊桥机位", "24个机位 · 周转4.8次", 87.1f, Blue),
                RegionStat("T1 远机位", "40个机位 · 周转3.4次", 64.3f, Color(0xFF8A9099)),
                RegionStat("T2 远机位", "36个机位 · 周转3.1次", 61.8f, Orange)
            ),
            "远机位利用率低于65%，建议将部分窄体机航班改派廊桥以提升旅客体验"
        ),
        StatsData(
            "74.8%", "较上一周期 +1.2%", "83.5%", "廊桥航班 1592 / 1906",
            "4.0 次/日", "行业均值 3.8 次/日", "08:00-10:00", "峰值利用率 91%",
            listOf(
                RegionStat("T1 廊桥机位", "28个机位 · 周转4.9次", 79.8f, Blue),
                RegionStat("T2 廊桥机位", "24个机位 · 周转4.6次", 83.2f, Blue),
                RegionStat("T1 远机位", "40个机位 · 周转3.2次", 66.1f, Color(0xFF8A9099)),
                RegionStat("T2 远机位", "36个机位 · 周转3.0次", 63.7f, Orange)
            ),
            "近 7 日整体利用率稳步上升，周末峰值略高于工作日"
        ),
        StatsData(
            "72.6%", "较上月 +0.8%", "81.9%", "廊桥航班 6424 / 7843",
            "3.9 次/日", "行业均值 3.8 次/日", "08:00-10:00", "峰值利用率 89%",
            listOf(
                RegionStat("T1 廊桥机位", "28个机位 · 周转4.7次", 77.5f, Blue),
                RegionStat("T2 廊桥机位", "24个机位 · 周转4.4次", 81.3f, Blue),
                RegionStat("T1 远机位", "40个机位 · 周转3.1次", 65.4f, Color(0xFF8A9099)),
                RegionStat("T2 远机位", "36个机位 · 周转2.9次", 62.9f, Orange)
            ),
            "本月利用率整体平稳，建议关注 T2 远机位的低效使用情况"
        )
    )
    val data = statsData[tabIndex]

    Column(Modifier.fillMaxSize()) {
        SubTopBar(
            "统计利用率",
            onBack
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ---- 时间范围 Tab ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp)),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                tabs.forEachIndexed { i, label ->
                    val active = tabIndex == i
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) Blue else Color.White)
                            .clickable { tabIndex = i }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            fontSize = 14.sp,
                            color = if (active) Color.White else TextSub,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            // ---- 指标卡 ----
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppCard(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("平均机位利用率", fontSize = 12.sp, color = TextSub, modifier = Modifier.weight(1f))
                        Text("↑", color = Green, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(data.avgUtil, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextMain)
                    Spacer(Modifier.height(6.dp))
                    Text(data.avgDelta, fontSize = 11.sp, color = Green)
                }
                AppCard(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("靠桥率", fontSize = 12.sp, color = TextSub, modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.Link, null, tint = Blue, modifier = Modifier.size(14.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(data.bridgeRate, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextMain)
                    Spacer(Modifier.height(6.dp))
                    Text(data.bridgeDetail, fontSize = 11.sp, color = TextSub)
                }
            }
            Spacer(Modifier.height(18.dp))
            // ---- 机位区域利用率 ----
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "机位区域利用率",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMain,
                        modifier = Modifier.weight(1f)
                    )
                    Text("今日 00:00 - 09:45", fontSize = 12.sp, color = TextSub)
                }
                Spacer(Modifier.height(14.dp))
                data.regions.forEach { region ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(region.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextMain)
                            Spacer(Modifier.height(2.dp))
                            Text(region.sub, fontSize = 12.sp, color = TextSub)
                        }
                        Spacer(Modifier.width(10.dp))
                        // 进度条
                        Box(
                            Modifier
                                .width(90.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFFEDEEF0))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(region.pct / 100f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(region.color)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${region.pct}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = region.color,
                            modifier = Modifier.width(48.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFFF0F1F3))
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Warning, null, tint = Orange, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(data.warning, fontSize = 13.sp, color = Orange, lineHeight = 18.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
