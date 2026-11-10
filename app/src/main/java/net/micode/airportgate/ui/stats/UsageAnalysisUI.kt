package net.micode.airportgate.ui.stats

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import net.micode.airportgate.control.stats.UtilizationTrend
import net.micode.airportgate.data.model.RegionStat
import net.micode.airportgate.data.model.StatsData
import net.micode.airportgate.control.UsageStatisticsController
import net.micode.airportgate.control.stats.UtilizationResult
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
fun UsageAnalysisUI(onBack: () -> Unit) {
    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("今日实时", "近 7 日", "本月")
    val context = LocalContext.current

    // 计算结果（数据库异步读取，先空着，读到了再刷新界面）
    var result by remember { mutableStateOf<UtilizationResult?>(null) }
    // 按天变化趋势（近 7 日 / 本月）；「今日实时」页签为空，不显示折线图
    var trend by remember { mutableStateOf(UtilizationTrend(emptyList())) }

    LaunchedEffect(tabIndex) {
        // 业务逻辑交由 biz.UsageStatisticsController 执行（算窗口 -> 调计算模块）
        trend = UtilizationTrend(emptyList())   // 切页签先清掉旧曲线，避免短暂显示上一个区间
        result = UsageStatisticsController.computeStats(context, tabIndex)
        trend = UsageStatisticsController.computeTrend(context, tabIndex)
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
            Spacer(Modifier.height(if (trend.points.isNotEmpty()) 14.dp else 18.dp))
            // ---- 平均机位利用率按天变化趋势（仅「近 7 日」与「本月」） ----
            if (trend.points.isNotEmpty()) {
                UtilizationTrendCard(trend, tabIndex)
                Spacer(Modifier.height(18.dp))
            }
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

// ========================= 平均机位利用率按天折线图 =========================
//
// 数据来源：biz.UsageStatisticsController.computeTrend() —— 与顶部指标卡共用
// 四张表和同一个 GateUtilizationCalculator.compute() 公式，只是把窗口切成「一天一个」。
// 绘图沿用甘特图的做法：Compose Canvas 自绘，不引入第三方图表库。

@Composable
private fun UtilizationTrendCard(trend: UtilizationTrend, tabIndex: Int) {
    val axisMax = niceAxisMax(trend.peak)
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "平均机位利用率变化趋势",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextMain,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (tabIndex == 1) "近 7 日 · 按天" else "本月 · 按天",
                fontSize = 12.sp,
                color = TextSub
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "每天占用总时长 ÷（开放机位数 × 当天已过去时长），今天统计至当前时刻",
            fontSize = 11.sp,
            color = TextSub
        )
        Spacer(Modifier.height(12.dp))
        TrendLineChart(trend, axisMax)
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = Color(0xFFF0F1F3))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TrendStat("峰值", pct(trend.peak, axisMax), Blue)
            TrendStat("峰值日", trend.peakLabel, TextMain)
            TrendStat("日均", pct(trend.dailyAverage, axisMax), Green)
            TrendStat("今日", pct(trend.today, axisMax), Orange)
        }
        if (trend.peak <= 0.0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "所选区间暂无机位占用记录，各日利用率均为 0%；导入航班并执行「一键分配」后即可看到变化。",
                fontSize = 11.sp,
                color = TextSub,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun TrendStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = TextSub)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun TrendLineChart(trend: UtilizationTrend, axisMax: Float) {
    val points = trend.points
    val n = points.size
    val labelStep = if (n <= 8) 1 else ceil(n / 7.0).toInt()
    val peakIndex = points.indexOfFirst { it.utilization == trend.peak }

    val measurer = rememberTextMeasurer()
    val gridColor = Color(0xFFEDEEF0)
    val labelStyle = TextStyle(fontSize = 10.sp, color = TextSub)
    val valueStyle = TextStyle(fontSize = 9.sp, color = TextMain, fontWeight = FontWeight.Bold)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .padding(horizontal = 4.dp)
    ) {
        val left = 40.dp.toPx()
        val right = 14.dp.toPx()
        val top = 20.dp.toPx()
        val bottom = 26.dp.toPx()
        val w = size.width - left - right
        val h = size.height - top - bottom
        if (w <= 0f || h <= 0f) return@Canvas

        // Y 轴刻度 + 水平网格（0 ~ axisMax 四等分）
        for (i in 0..4) {
            val y = top + h * i / 4f
            val value = axisMax * (4 - i) / 4f
            drawLine(
                color = gridColor,
                start = Offset(left, y),
                end = Offset(left + w, y),
                strokeWidth = 1.dp.toPx()
            )
            val t = pct(value.toDouble(), axisMax)
            val tw = measurer.measure(t, labelStyle).size.width
            drawText(
                textMeasurer = measurer,
                text = t,
                topLeft = Offset(left - tw - 6.dp.toPx(), y - 7.dp.toPx()),
                style = labelStyle
            )
        }

        val xs = if (n == 1) listOf(left + w / 2f)
        else points.indices.map { left + w * it / (n - 1) }
        val ys = points.map { p ->
            top + h * (1f - (p.utilization.toFloat() / axisMax).coerceIn(0f, 1f))
        }

        // 折线
        for (i in 0 until n - 1) {
            drawLine(
                color = Blue,
                start = Offset(xs[i], ys[i]),
                end = Offset(xs[i + 1], ys[i + 1]),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        points.forEachIndexed { i, p ->
            val dotColor = if (p.isToday) Orange else Blue
            drawCircle(Color.White, radius = 4.5.dp.toPx(), center = Offset(xs[i], ys[i]))
            drawCircle(dotColor, radius = 3.dp.toPx(), center = Offset(xs[i], ys[i]))

            // 数值标签：点数少时全标；本月天数多时只标峰值点与今日点，避免重叠
            if (n <= 10 || p.isToday || i == peakIndex) {
                val t = pct(p.utilization, axisMax)
                val tw = measurer.measure(t, valueStyle).size.width
                drawText(
                    textMeasurer = measurer,
                    text = t,
                    topLeft = Offset(
                        (xs[i] - tw / 2f).coerceIn(0f, (size.width - tw).coerceAtLeast(0f)),
                        (ys[i] - 17.dp.toPx()).coerceAtLeast(0f)
                    ),
                    style = valueStyle
                )
            }
            // X 轴日期标签：本月抽稀显示，末点（今天）必显示
            if (i % labelStep == 0 || i == n - 1) {
                val tw = measurer.measure(p.label, labelStyle).size.width
                drawText(
                    textMeasurer = measurer,
                    text = p.label,
                    topLeft = Offset(xs[i] - tw / 2f, size.height - bottom + 6.dp.toPx()),
                    style = labelStyle
                )
            }
        }
    }
}

/** Y 轴上限档位：让峰值落在约 3/4 高度，刻度取 1/2/5 系列的整齐数字 */
private val AXIS_STEPS = listOf(0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0)

/**
 * 真实场景下 125 个机位作分母，日利用率常只有 0.x%；
 * 若固定 0~100% 或最低 20%，曲线会被压成一条贴底直线，看不出变化。
 */
private fun niceAxisMax(peak: Double): Float {
    if (peak <= 0.0) return 20f          // 无数据时给一个默认量程
    val target = peak / 0.75
    return (AXIS_STEPS.firstOrNull { it >= target } ?: 100.0).toFloat()
}

/**
 * 百分比文字：小量程保留两位小数，避免 0.x% 全被显示成 0%，
 * 也避免 1% 量程下 0.75% / 0.25% 这类刻度被四舍五入成 0.8% / 0.3%。
 */
private fun pct(v: Double, axisMax: Float): String =
    if (axisMax <= 5f) "%.2f%%".format(v) else "%.1f%%".format(v)
