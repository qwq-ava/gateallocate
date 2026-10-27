package net.micode.airportgate.stats

import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import net.micode.airportgate.model.GateInfoRow

// ========================= 机位使用率计算方法 =========================
//
// 核心公式：
//   机位使用率 = 占用总时长 ÷（开放机位数 × 统计窗口时长）
//
// 数据来源（四张表都用到了）：
//   - 机位信息表  → 筛出 status="可用" 的机位作为「可用机位」
//   - 使用记录表  → 优先用真实占用时段算占用时长（避免重叠，单机位上限=窗口）
//   - 航班信息表  → 当某分配没有使用记录时，用到达/起飞时间估算占用
//   - 分配结果表  → 把航班绑到机位；同时用于计算「靠桥率」
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

        // 3) 靠桥率 = 分配到「廊桥(近机位)」的航班数 ÷ 总分配航班数
        val totalAssign = assignments.size
        val bridgeCount = assignments.count { a -> gateByNo[a.gateNo]?.bridge == STAND_NEAR }
        val bridgeRate = if (totalAssign > 0) bridgeCount.toDouble() / totalAssign * 100.0 else 0.0

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

    /** 保留 1 位小数 */
    private fun round1(v: Double): Double = round(v * 10.0) / 10.0
}
