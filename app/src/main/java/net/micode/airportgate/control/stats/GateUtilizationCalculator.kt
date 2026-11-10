package net.micode.airportgate.control.stats

import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import net.micode.airportgate.data.model.GateInfoRow

// ========================= 机位使用率计算方法 =========================
//
// 核心公式：
//   机位使用率 = 占用总时长 ÷（开放机位数 × 统计窗口时长）
//
// 数据来源（四张表都用到了）：
//   - 机位信息表  → 筛出 status="可用" 的机位作为「可用机位」
//   - 使用记录表  → 优先用真实占用时段算占用时长；is_bridge 是「靠桥率」分子的优先来源
//   - 航班信息表  → 当某分配没有使用记录时，用到达/起飞时间估算占用
//   - 分配结果表  → 把航班绑到机位；无使用记录时用其 is_bridge / 计划时段兜底算靠桥率
//
// 靠桥率口径（与《数据库设计说明》一致）：窗口内 SUM(is_bridge) / 有效航班数。
// 与利用率共用同一个统计窗口，只计与窗口相交、且机位编号在机位表中存在的记录。
//
// 设计：不依赖任何数据库。既可以直接传 List 调用，也可以传 StatsDataSource 接口调用。
//
// 适配 allocation-module：GateInfoRow 的「近机位」字段名为 bridge（"廊桥"/"远机位"），
// 「开放」状态值为 "可用"，故下方用 bridge / STATUS_OPEN(=可用) 判定。
object GateUtilizationCalculator {

    /**
     * 纯函数版：直接传入四张表的数据，无需数据库，便于测试与即时调用。
     *
     * @param windowStart 统计窗口开始（epoch 毫秒）
     * @param windowEnd   统计窗口结束（epoch 毫秒）
     */
    fun compute(
        windowStart: Long,
        windowEnd: Long,
        flights: List<FlightInfo>,
        gates: List<GateInfoRow>,
        assignments: List<Assignment>,
        usageRecords: List<UsageRecord>
    ): UtilizationResult {
        val window = (windowEnd - windowStart).coerceAtLeast(0L)
        if (window == 0L) {
            return UtilizationResult(0.0, 0.0, emptyList())
        }

        // 只有状态为"可用"的机位才计入可用机位
        val openGates = gates.filter { it.status == STATUS_OPEN }
        val gateByNo = openGates.associateBy { it.code }
        if (openGates.isEmpty()) {
            return UtilizationResult(0.0, 0.0, emptyList())
        }

        // 1) 优先用「使用记录表」累加真实占用时长（按机位，片段裁剪到窗口内）
        val occupyByGate = mutableMapOf<String, Long>()
        val usedGates = mutableSetOf<String>()
        for (rec in usageRecords) {
            if (rec.gateNo !in gateByNo) continue
            val start = max(windowStart, rec.occupyStart)
            val end = min(windowEnd, rec.occupyEnd)
            if (end > start) {
                occupyByGate[rec.gateNo] = (occupyByGate[rec.gateNo] ?: 0L) + (end - start)
                usedGates.add(rec.gateNo)
            }
        }
        // 2) 没有使用记录的分配，用「航班信息表」到达/起飞时间补充估算（仅补充，不重复计）
        val flightByNo = flights.associateBy { it.flightNo }
        for (a in assignments) {
            if (a.gateNo in usedGates || a.gateNo !in gateByNo) continue
            val f = flightByNo[a.flightNo] ?: continue
            val start = max(windowStart, f.arrival)
            val end = min(windowEnd, f.departure)
            if (end > start) {
                occupyByGate[a.gateNo] = (occupyByGate[a.gateNo] ?: 0L) + (end - start)
            }
        }
        // 单机位占用上限 = 窗口时长，防止同一机位多条记录重叠导致 >100%
        for (g in occupyByGate.keys) occupyByGate[g] = min(occupyByGate[g]!!, window)

        val totalOccupy = occupyByGate.values.sum()
        val totalAvailable = openGates.size * window
        val avgUtil = if (totalAvailable > 0) totalOccupy.toDouble() / totalAvailable * 100.0 else 0.0

        // 3) 靠桥率 = 统计窗口内「靠桥航班数」÷「有效航班数」
        //    口径统一到「本次是否靠桥」is_bridge 字段：
        //      · 优先用 t_gate_usage.is_bridge（一次占用一条，与利用率同源）
        //      · 没有使用记录的分配用 t_assignment.is_bridge 兜底，按 flightNo 去重
        //    只统计与 [windowStart, windowEnd) 相交、且机位编号确实存在于机位表的记录：
        //    机位表换血后残留的旧编号既不算分子也不算分母，历史数据也不会串进今日/本月。
        val allGateNos = gates.map { it.code }.toHashSet()
        val inWindow = { start: Long, end: Long -> end > windowStart && start < windowEnd }
        val usageInWindow = usageRecords.filter {
            it.gateNo in allGateNos && inWindow(it.occupyStart, it.occupyEnd)
        }
        val withUsage = usageInWindow.map { it.flightNo }.toHashSet()
        val assignmentOnly = assignments.filter {
            it.gateNo in allGateNos && it.flightNo !in withUsage &&
                inWindow(it.occupyStart, it.occupyEnd)
        }
        val totalFlights = usageInWindow.size + assignmentOnly.size
        val bridgeFlights = usageInWindow.count { it.isBridge } + assignmentOnly.count { it.isBridge }
        val bridgeRate = if (totalFlights > 0) bridgeFlights.toDouble() / totalFlights * 100.0 else 0.0

        // 4) 分区域（按停机位种类：廊桥 / 远机位）各自算使用率
        val regions = openGates.groupBy { it.bridge }.map { (stand, list) ->
            val occ = list.sumOf { occupyByGate[it.code] ?: 0L }
            val avail = list.size * window
            val util = if (avail > 0) occ.toDouble() / avail * 100.0 else 0.0
            RegionUtilization(name = stand, utilization = util, gateCount = list.size)
        }

        return UtilizationResult(
            avgUtilization = round1(avgUtil),
            bridgeRate = round1(bridgeRate),
            regions = regions.map { it.copy(utilization = round1(it.utilization)) }
        )
    }

    /**
     * 接口版：将来数据库就绪后，让 StatsDataSource 的实现返回真实数据，
     * 直接调用本方法即可，计算逻辑无需改动。
     */
    suspend fun compute(
        windowStart: Long,
        windowEnd: Long,
        source: StatsDataSource
    ): UtilizationResult = compute(
        windowStart = windowStart,
        windowEnd = windowEnd,
        flights = source.flights(),
        gates = source.gates(),
        assignments = source.assignments(),
        usageRecords = source.usageRecords()
    )

    // ========================= 按天趋势 =========================

    /**
     * 纯函数版：把同一批原始数据按 [DayWindow] 逐日切窗口，得到每天的平均机位利用率。
     *
     * 逐日复用上面的 compute()：占用时长会被裁剪到当天窗口内，因此跨夜/跨天的
     * 使用记录会自动分摊到对应日期；分母为「当天已过去的时长 × 开放机位数」。
     * 与「今日实时 / 近 7 日 / 本月」汇总值使用完全相同的公式，只是窗口粒度不同。
     */
    fun computeDaily(
        days: List<DayWindow>,
        flights: List<FlightInfo>,
        gates: List<GateInfoRow>,
        assignments: List<Assignment>,
        usageRecords: List<UsageRecord>
    ): UtilizationTrend {
        if (days.isEmpty()) return UtilizationTrend(emptyList())
        val points = days.map { d ->
            val one = compute(d.start, d.end, flights, gates, assignments, usageRecords)
            DailyUtilization(
                date = d.date,
                label = d.label,
                utilization = one.avgUtilization,
                isToday = d.isToday
            )
        }
        return UtilizationTrend(points)
    }

    /** 接口版：四张表只读一次，再在内存里逐日计算，避免每天重复查库 */
    suspend fun computeDaily(
        days: List<DayWindow>,
        source: StatsDataSource
    ): UtilizationTrend {
        if (days.isEmpty()) return UtilizationTrend(emptyList())
        return computeDaily(
            days = days,
            flights = source.flights(),
            gates = source.gates(),
            assignments = source.assignments(),
            usageRecords = source.usageRecords()
        )
    }

    /** 保留 1 位小数 */
    private fun round1(v: Double): Double = round(v * 10.0) / 10.0
}
