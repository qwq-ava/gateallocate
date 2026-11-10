package net.micode.airportgate

import net.micode.airportgate.control.stats.Assignment
import net.micode.airportgate.control.stats.GateUtilizationCalculator
import net.micode.airportgate.control.stats.UsageRecord
import net.micode.airportgate.data.model.GateInfoOptions
import net.micode.airportgate.data.model.GateInfoRow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「靠桥率」口径的算法行为测试（纯函数 compute()，不依赖数据库 / Android Context）。
 *
 * 覆盖 B（按统计窗口过滤 + 脏机位编号不计入分母）与 C（分子统一到 is_bridge 字段）：
 * 时间用相对 epoch 毫秒（0 = 第 1 天 00:00），只比较窗口相交关系，与真实时区无关。
 */
class BridgeRateTest {

    private val H = 3_600_000L
    private val D = 24 * H

    /** 2 个开放机位：1 廊桥 + 1 远机位（机位档案中存在的编号） */
    private fun gates() = listOf(
        GateInfoRow(id = "g1", code = "101", bridge = "廊桥", status = GateInfoOptions.STATUS_AVAILABLE),
        GateInfoRow(id = "g2", code = "409", bridge = "远机位", status = GateInfoOptions.STATUS_AVAILABLE)
    )

    private fun rate(
        start: Long,
        end: Long,
        usage: List<UsageRecord> = emptyList(),
        assignments: List<Assignment> = emptyList(),
        gateList: List<GateInfoRow> = gates()
    ): Double = GateUtilizationCalculator
        .compute(start, end, emptyList(), gateList, assignments, usage)
        .bridgeRate

    @Test
    fun 按is_bridge算分子_窗口内二靠桥二远机位为百分之五十() {
        val usage = listOf(
            UsageRecord(1, "101", "CA1", 1 * H, 2 * H, isBridge = true),
            UsageRecord(2, "101", "CA2", 3 * H, 4 * H, isBridge = true),
            UsageRecord(3, "409", "CA3", 1 * H, 2 * H, isBridge = false),
            UsageRecord(4, "409", "CA4", 3 * H, 4 * H, isBridge = false)
        )
        assertEquals(50.0, rate(0L, D, usage = usage), 0.001)
    }

    @Test
    fun 靠桥率随统计窗口变化_历史记录不串进今日() {
        val usage = listOf(
            UsageRecord(1, "101", "OLD", 0L, H, isBridge = true),          // 第 1 天：靠桥
            UsageRecord(2, "409", "NEW", D + H, D + 2 * H, isBridge = false) // 第 2 天：远机位
        )
        assertEquals(100.0, rate(0L, D, usage = usage), 0.001)      // 只看第 1 天
        assertEquals(0.0, rate(D, 2 * D, usage = usage), 0.001)     // 只看第 2 天
        assertEquals(50.0, rate(0L, 2 * D, usage = usage), 0.001)   // 近 2 日 = 1/2
    }

    @Test
    fun 机位表中不存在的脏编号既不进分子也不进分母() {
        val usage = listOf(
            UsageRecord(1, "101", "OK", H, 2 * H, isBridge = true),
            UsageRecord(2, "B27", "DIRTY1", H, 2 * H, isBridge = true),   // 旧版示例编号
            UsageRecord(3, "T2-14", "DIRTY2", H, 2 * H, isBridge = false)
        )
        // 若不过滤脏编号会得到 2/3 ≈ 66.7%
        assertEquals(100.0, rate(0L, D, usage = usage), 0.001)
    }

    @Test
    fun 分子以is_bridge为准_不再反推机位档案的bridge() {
        val usage = listOf(
            UsageRecord(1, "409", "CA1", H, 2 * H, isBridge = true),  // 机位是远机位，但本次标记靠桥
            UsageRecord(2, "101", "CA2", H, 2 * H, isBridge = true)   // 机位是廊桥
        )
        // 旧口径（按 t_gate.bridge == "廊桥"）只会得到 50%
        assertEquals(100.0, rate(0L, D, usage = usage), 0.001)
    }

    @Test
    fun 没有使用记录时用分配结果的is_bridge兜底() {
        val assignments = listOf(
            Assignment(1, "CA1", "101", 0L, isBridge = true, occupyStart = H, occupyEnd = 2 * H),
            Assignment(2, "CA2", "409", 0L, isBridge = false, occupyStart = H, occupyEnd = 2 * H)
        )
        assertEquals(50.0, rate(0L, D, assignments = assignments), 0.001)
    }

    @Test
    fun 两表同一航班不重复计数_有使用记录时优先用使用记录() {
        val usage = listOf(
            UsageRecord(1, "101", "CA1", H, 2 * H, isBridge = true)
        )
        val assignments = listOf(
            // 同航班、is_bridge 不同：应被去重，以 usage 的 true 为准
            Assignment(1, "CA1", "101", 0L, isBridge = false, occupyStart = H, occupyEnd = 2 * H),
            Assignment(2, "CA2", "409", 0L, isBridge = false, occupyStart = H, occupyEnd = 2 * H)
        )
        // 有效航班 = CA1(靠桥) + CA2(远机位) → 1/2
        assertEquals(50.0, rate(0L, D, usage = usage, assignments = assignments), 0.001)
    }

    @Test
    fun 空数据与无开放机位时靠桥率为零() {
        assertEquals(0.0, rate(0L, D), 0.001)
        assertEquals(0.0, rate(0L, D, gateList = emptyList()), 0.001)
    }
}
