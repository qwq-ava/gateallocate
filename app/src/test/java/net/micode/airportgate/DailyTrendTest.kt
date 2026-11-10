package net.micode.airportgate

import net.micode.airportgate.control.stats.DayWindow
import net.micode.airportgate.control.stats.GateUtilizationCalculator
import net.micode.airportgate.control.stats.UsageRecord
import net.micode.airportgate.data.model.GateInfoOptions
import net.micode.airportgate.data.model.GateInfoRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 「平均机位利用率按天折线图」的算法行为测试。
 *
 * 只测纯函数 computeDaily()：不碰数据库、不碰 Android Context，
 * 验证逐日窗口切分、跨夜记录分摊、今天半日窗口、以及趋势派生值。
 * 时间用相对 epoch 毫秒（0 = 第 1 天 00:00），只比较时长差，与真实时区无关。
 */
class DailyTrendTest {

    private val H = 3_600_000L
    private val D = 24 * H

    /** 2 个开放机位：1 廊桥 + 1 远机位 */
    private fun gates() = listOf(
        GateInfoRow(
            id = "g1", code = "101", bridge = "廊桥",
            status = GateInfoOptions.STATUS_AVAILABLE
        ),
        GateInfoRow(
            id = "g2", code = "409", bridge = "远机位",
            status = GateInfoOptions.STATUS_AVAILABLE
        )
    )

    private fun days(count: Int = 3): List<DayWindow> = (0 until count).map { i ->
        DayWindow(
            date = "2026-11-0${i + 1}",
            label = "11/${i + 1}",
            start = i * D,
            end = (i + 1) * D,
            isToday = i == count - 1
        )
    }

    private fun trend(
        dayList: List<DayWindow>,
        usage: List<UsageRecord>,
        gateList: List<GateInfoRow> = gates()
    ) = GateUtilizationCalculator.computeDaily(
        dayList, emptyList(), gateList, emptyList(), usage
    )

    @Test
    fun 逐日切窗口_每天各自计算平均利用率() {
        // 第 1 天：101 占 6h；第 2 天：101 占 12h + 409 占 6h；第 3 天：无占用
        // 分母 = 2 个机位 × 24h = 48h
        val usage = listOf(
            UsageRecord(1, "101", "CA1", 0L, 6 * H),
            UsageRecord(2, "101", "CA2", D, D + 12 * H),
            UsageRecord(3, "409", "CA3", D, D + 6 * H)
        )
        val t = trend(days(), usage)

        assertEquals(3, t.points.size)
        assertEquals(12.5, t.points[0].utilization, 0.05)   // 6h / 48h
        assertEquals(37.5, t.points[1].utilization, 0.05)   // 18h / 48h
        assertEquals(0.0, t.points[2].utilization, 0.001)
        assertEquals(37.5, t.peak, 0.05)
        assertEquals("11/2", t.peakLabel)
        assertTrue("日均应为三天算术平均", abs(t.dailyAverage - 50.0 / 3.0) < 0.1)
        assertEquals(0.0, t.today, 0.001)
    }

    @Test
    fun 跨夜占用记录被分摊到相邻两天() {
        // 单条记录 20:00 → 次日 04:00（8h），按窗口裁剪后各天记 4h
        val usage = listOf(UsageRecord(1, "101", "CA9", 20 * H, 28 * H))
        val t = trend(days(2), usage)

        assertEquals(8.3, t.points[0].utilization, 0.05)    // 4h / 48h
        assertEquals(8.3, t.points[1].utilization, 0.05)
    }

    @Test
    fun 今天只统计到当前时刻_分母随窗口缩短() {
        // 今天窗口只到 12:00，分母 = 2 机位 × 12h = 24h；占用 6h → 25%
        val today = DayWindow("2026-11-10", "11/10", 0L, 12 * H, isToday = true)
        val usage = listOf(UsageRecord(1, "101", "CA1", 0L, 6 * H))
        val t = trend(listOf(today), usage)

        assertEquals(25.0, t.points[0].utilization, 0.05)
        assertEquals(25.0, t.today, 0.05)
        assertTrue(t.points[0].isToday)
    }

    @Test
    fun 无开放机位或无数据时各日均为零() {
        val noGates = trend(days(), emptyList(), emptyList())
        assertTrue(noGates.points.size == 3)
        assertTrue(noGates.points.all { it.utilization == 0.0 })
        assertEquals(0.0, noGates.peak, 0.001)

        val noUsage = trend(days(), emptyList())
        assertTrue(noUsage.points.all { it.utilization == 0.0 })
    }

    @Test
    fun 空的日期序列返回空趋势() {
        assertTrue(trend(emptyList(), emptyList()).points.isEmpty())
    }
}
