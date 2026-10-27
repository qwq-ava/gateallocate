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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import net.micode.airportgate.model.RegionStat
import net.micode.airportgate.model.StatsData
import net.micode.airportgate.domain.StatsService
import net.micode.airportgate.domain.stats.UtilizationResult
import net.micode.airportgate.ui.components.AppCard
import net.micode.airportgate.ui.components.SubTopBar
import net.micode.airportgate.ui.theme.Blue
import net.micode.airportgate.ui.theme.Green
import net.micode.airportgate.ui.theme.Orange
import net.micode.airportgate.ui.theme.TextMain
import net.micode.airportgate.ui.theme.TextSub

// ========================= 统计利用率（真实数据版） =========================
//
// 不再写死数字，而是从 allocation-module 的 SQLite 读取「机位/航班/分配/占用」四张表，
// 交给 stats.GateUtilizationCalculator 算出真实的使用率 / 靠桥率 / 分区域利用率，
// 再映射成界面用的 StatsData / RegionStat。

@Composable
fun StatsScreen(onBack: () -> Unit) {
    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("今日实时", "近 7 日", "本月")
    val context = LocalContext.current

    // 计算结果（数据库异步读取，先空着，读到了再刷新界面）
    var result by remember { mutableStateOf<UtilizationResult?>(null) }

    LaunchedEffect(tabIndex) {
        // 业务逻辑交由 domain.StatsService 执行（算窗口 -> 调计算模块）
        result = StatsService.computeStats(context, tabIndex)
    }

    val data = result?.toStatsData() ?: StatsData(
        avgUtil = "—",
        avgDelta = "计算中…",
        bridgeRate = "—",
        bridgeDetail = "正在读取数据库",
        turnover = "—",
        industryAvg = "行业均值 3.8 次/日",
        peak = "—",
        peakUtil = "—",
        regions = emptyList(),
        warning = "正在从 SQLite 读取真实占用数据…"
    )

    Column(Modifier.fillMaxSize()) {
        SubTopBar("统计利用率", onBack)
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
                    Text("当前统计窗口内", fontSize = 12.sp, color = TextSub)
                }
                Spacer(Modifier.height(14.dp))
                if (data.regions.isEmpty()) {
                    Text("暂无数据", fontSize = 13.sp, color = TextSub)
                } else {
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
                                        .fillMaxWidth((region.pct / 100f).coerceIn(0f, 1f))
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

/** 把计算结果映射成界面用的 StatsData（展示映射留在界面层：含主题色，避免 domain 反向依赖 ui） */
private fun UtilizationResult.toStatsData(): StatsData {
    val maxUtil = regions.maxOfOrNull { it.utilization } ?: 0.0
    val remote = regions.firstOrNull { it.name != "廊桥" }
    val regionsUi = regions.map { r ->
        val isBridge = r.name == "廊桥"
        RegionStat(
            name = if (isBridge) "廊桥机位" else "远机位",
            sub = "${r.gateCount}个开放机位 · 利用率 ${"%.1f".format(r.utilization)}%",
            pct = r.utilization.toFloat().coerceIn(0f, 100f),
            color = if (isBridge) Blue else Green
        )
    }
    val warning = when {
        remote != null && remote.utilization < 65.0 ->
            "远机位利用率低于65%，建议将部分窄体机航班改派廊桥以提升旅客体验"
        avgUtilization >= 80.0 ->
            "整体利用率较高，请关注机位周转与冲突情况"
        else -> "各区域利用率均衡，运行平稳"
    }
    return StatsData(
        avgUtil = "%.1f%%".format(avgUtilization),
        avgDelta = "数据库实时计算",
        bridgeRate = "%.1f%%".format(bridgeRate),
        bridgeDetail = "靠桥率 %.1f%%".format(bridgeRate),
        turnover = "—",
        industryAvg = "行业均值 3.8 次/日",
        peak = "—",
        peakUtil = "峰值 %.1f%%".format(maxUtil),
        regions = regionsUi,
        warning = warning
    )
}
