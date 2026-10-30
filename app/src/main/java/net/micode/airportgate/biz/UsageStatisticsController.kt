package net.micode.airportgate.biz

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.micode.airportgate.biz.stats.GateUtilizationCalculator
import net.micode.airportgate.biz.stats.SqliteStatsDataSource
import net.micode.airportgate.biz.stats.UtilizationResult
import java.util.Calendar

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
