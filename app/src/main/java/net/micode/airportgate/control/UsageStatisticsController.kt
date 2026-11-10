package net.micode.airportgate.control

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.micode.airportgate.control.stats.DayWindow
import net.micode.airportgate.control.stats.GateUtilizationCalculator
import net.micode.airportgate.control.stats.SqliteStatsDataSource
import net.micode.airportgate.control.stats.UtilizationResult
import net.micode.airportgate.control.stats.UtilizationTrend
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 控制层（业务逻辑层）：统计业务协调者。
 *
 * 承接「按页签算时间窗口 -> 调统计计算模块 -> 返回原始计算结果」，
 * 使 ui/stats/UsageAnalysisUI 只负责把 UtilizationResult 映射成展示模型并渲染。
 *
 * 说明：统计计算在 Dispatchers.IO 执行，避免阻塞 UI 线程读取 SQLite；
 * 展示层映射（UtilizationResult.toStatsData，含界面主题色）保留在 ui/stats，
 * 以避免 domain 层反向依赖 ui 主题。
 */
object UsageStatisticsController {
    /**
     * 计算某个页签（0=今日实时 / 1=近 7 日 / 2=本月）的利用率统计。
     * @return 原始计算结果 UtilizationResult（不含界面主题色等展示细节）
     */
    suspend fun computeStats(context: Context, tabIndex: Int): UtilizationResult = withContext(Dispatchers.IO) {
        val ds = SqliteStatsDataSource(context)
        val (start, end) = windowFor(tabIndex)
        GateUtilizationCalculator.compute(start, end, ds)
    }

    /**
     * 按天展开的平均机位利用率趋势（折线图数据）。
     * 0=今日实时（只有单点，不画趋势）返回空；1=近 7 日 7 个点；2=本月 1 号至今天。
     * 与 computeStats 使用同一套数据源和计算公式，区别只在窗口粒度。
     */
    suspend fun computeTrend(context: Context, tabIndex: Int): UtilizationTrend = withContext(Dispatchers.IO) {
        val days = dailyWindows(tabIndex)
        if (days.isEmpty()) return@withContext UtilizationTrend(emptyList())
        GateUtilizationCalculator.computeDaily(days, SqliteStatsDataSource(context))
    }

    /**
     * 生成逐日窗口：历史日期为完整 24 小时，今天截止到当前时刻。
     * 只生成到今天为止，不画尚未到来的日期，避免未来空白点把曲线拉平。
     */
    private fun dailyWindows(tab: Int): List<DayWindow> {
        val dayCount = when (tab) {
            1 -> 7                                                        // 近 7 日（含今天）
            2 -> Calendar.getInstance().get(Calendar.DAY_OF_MONTH)          // 本月 1 号 ~ 今天
            else -> return emptyList()                                       // 今日实时不画趋势
        }
        val now = System.currentTimeMillis()
        val fullFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val shortFmt = SimpleDateFormat("M/d", Locale.US)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        cal.add(Calendar.DAY_OF_MONTH, -(dayCount - 1))

        val out = mutableListOf<DayWindow>()
        for (i in 0 until dayCount) {
            val start = cal.timeInMillis
            val date = fullFmt.format(Date(start))
            val label = shortFmt.format(Date(start))
            cal.add(Calendar.DAY_OF_MONTH, 1)       // cal 现在指向次日 00:00
            val nextStart = cal.timeInMillis
            val isToday = i == dayCount - 1
            out += DayWindow(
                date = date,
                label = label,
                start = start,
                end = minOf(nextStart, now),
                isToday = isToday
            )
        }
        return out
    }

    /** 根据页签返回统计窗口 [start, end]（epoch 毫秒）：今日 / 近 7 日 / 本月 */
    private fun windowFor(tab: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        val end = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = when (tab) {
            1 -> { cal.add(Calendar.DAY_OF_MONTH, -6); cal.timeInMillis } // 近 7 日（含今天）
            2 -> { cal.set(Calendar.DAY_OF_MONTH, 1); cal.timeInMillis }   // 本月 1 号起
            else -> cal.timeInMillis                                              // 今日 00:00 起
        }
        return start to end
    }
}
