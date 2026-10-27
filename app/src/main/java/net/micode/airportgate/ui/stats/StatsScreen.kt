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
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import java.util.Calendar
import net.micode.airportgate.data.db.GateRepository
import net.micode.airportgate.model.GateInfoStore
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

    // 机位构成与机位信息表联动：机位档案不随时间变化，三个页签共用同一份
    val context = LocalContext.current
    val gates = GateInfoStore.load(context)
    val gateTotal = gates.size.coerceAtLeast(1)
    val pctOf = { n: Int -> n * 100f / gateTotal }
    val bridgeOk = gates.count { it.isBridge && it.available }
    val bridgeBad = gates.count { it.isBridge && !it.available }
    val remoteOk = gates.count { !it.isBridge && it.available }
    val remoteBad = gates.count { !it.isBridge && !it.available }
    val liveRegions = listOf(
        RegionStat("廊桥机位 · 可用", "$bridgeOk 个 · 占 ${"%.1f".format(pctOf(bridgeOk))}%", pctOf(bridgeOk), Blue),
        RegionStat("廊桥机位 · 维护停用", "$bridgeBad 个 · 占 ${"%.1f".format(pctOf(bridgeBad))}%", pctOf(bridgeBad), Color(0xFF8A9099)),
        RegionStat("远机位 · 可用", "$remoteOk 个 · 占 ${"%.1f".format(pctOf(remoteOk))}%", pctOf(remoteOk), Green),
        RegionStat("远机位 · 维护停用", "$remoteBad 个 · 占 ${"%.1f".format(pctOf(remoteBad))}%", pctOf(remoteBad), Orange)
    )

    // ---- 核心指标卡：今日实时 / 近 7 日 / 本月 全部从数据库实时算（t_gate_usage）----
    // 仅在切换页签时重算，避免每次重组都查库
    val data = remember(tabIndex) { buildStatsFor(tabIndex, context, liveRegions) }

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
                    Text("廊桥 / 远机位 · 状态分布（实时）", fontSize = 12.sp, color = TextSub)
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

/**
 * 统计页三个页签对应的日期区间 + 各自的上一个周期（用于算环比）。
 *  0 = 今日实时（vs 昨日）
 *  1 = 近 7 日（vs 前 7 日）
 *  2 = 本月（1 日 ~ 今日，vs 上月同日区间）
 */
private data class PeriodRange(
    val start: String, val end: String, val days: Int,
    val prevStart: String, val prevEnd: String, val prevDays: Int
)

private fun periodFor(tab: Int): PeriodRange {
    val cal = Calendar.getInstance()
    val y = cal.get(Calendar.YEAR)
    val m = cal.get(Calendar.MONTH)
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val today = "%04d-%02d-%02d".format(y, m + 1, day)
    fun shift(days: Int): String {
        val c = Calendar.getInstance().apply {
            time = cal.time
            add(Calendar.DAY_OF_MONTH, days)
        }
        return "%04d-%02d-%02d".format(
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )
    }
    return when (tab) {
        0 -> PeriodRange(today, today, 1, shift(-1), shift(-1), 1)        // 今日 vs 昨日
        1 -> PeriodRange(shift(-6), today, 7, shift(-13), shift(-7), 7)   // 近 7 日 vs 前 7 日
        2 -> {                                                            // 本月 vs 上月同日区间
            val start = "%04d-%02d-01".format(y, m + 1)
            val pCal = Calendar.getInstance().apply { time = cal.time; add(Calendar.MONTH, -1) }
            val pStart = "%04d-%02d-01".format(pCal.get(Calendar.YEAR), pCal.get(Calendar.MONTH) + 1)
            val pEnd = "%04d-%02d-%02d".format(pCal.get(Calendar.YEAR), pCal.get(Calendar.MONTH) + 1, day)
            PeriodRange(start, today, day, pStart, pEnd, day)
        }
        else -> PeriodRange(today, today, 1, today, today, 1)
    }
}

/**
 * 按页签从 t_gate_usage 实时计算核心率（利用率 / 靠桥率 / 周转 / 环比），
 * 取代原先写死的演示数据。若区间内没有分配占用记录（未跑过一键分配），
 * 给出诚实提示而非假数字。
 */
private fun buildStatsFor(tab: Int, context: Context, regions: List<RegionStat>): StatsData {
    val p = periodFor(tab)
    val cur = GateRepository.rangeStat(context, p.start, p.end, p.days)
    val prev = GateRepository.rangeStat(context, p.prevStart, p.prevEnd, p.prevDays)
    val avgDelta = if (prev.avgUtil <= 0f) "环比 —" else {
        val d = (cur.avgUtil - prev.avgUtil) / prev.avgUtil * 100f
        "较上一周期 %s%.1f%%".format(if (d >= 0) "+" else "", d)
    }
    val warning = when {
        cur.flightCount == 0 ->
            "当前区间暂无分配占用记录，请先在「机位分配」页执行一键分配，统计才会出数"
        cur.avgUtil < 0.5f ->
            "整体利用率偏低（${"%.1f".format(cur.avgUtil * 100)}%），建议优化机位排布或提高廊桥率"
        else ->
            "运行平稳，本期靠桥率 ${"%.1f".format(cur.bridgeRate * 100)}%"
    }
    return StatsData(
        avgUtil = "%.1f%%".format(cur.avgUtil * 100),
        avgDelta = avgDelta,
        bridgeRate = "%.1f%%".format(cur.bridgeRate * 100),
        bridgeDetail = "廊桥航班 ${cur.bridgeCount} / ${cur.flightCount}",
        turnover = "%.1f 次/日".format(cur.turnover),
        industryAvg = "行业均值 3.8 次/日",
        peak = "",
        peakUtil = "",
        regions = regions,
        warning = warning
    )
}
