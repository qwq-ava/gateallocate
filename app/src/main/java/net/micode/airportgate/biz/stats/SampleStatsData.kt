package net.micode.airportgate.biz.stats

import net.micode.airportgate.data.model.GateInfoOptions
import net.micode.airportgate.data.model.GateInfoRow
import java.util.Calendar

// ========================= 示例数据源（内存实现，仅用于演示/测试） =========================
// 这不是数据库！只是把四个表接口用写死的数据实现了，方便你现在就能跑通计算。
// 将来建好真实数据库后，写一个新的 StatsDataSource 实现（比如 Room 的 Repository）替换掉它即可，
// GateUtilizationCalculator 完全不用改。
//
// 适配 allocation-module：GateInfoRow 构造参数改为具名形式，且
//   - 近机位 → bridge = GateInfoOptions.BRIDGE（"廊桥"）
//   - 远机位 → bridge = GateInfoOptions.REMOTE（"远机位"）
//   - 开放   → status = GateInfoOptions.STATUS_AVAILABLE（"可用"）
//   - 关闭   → STATUS_MAINTENANCE（"维护"）；不可用 → STATUS_DISABLED（"停用"）
object SampleStatsData : StatsDataSource {

    override suspend fun flights(): List<FlightInfo> = listOf(
        FlightInfo("CA1234", at(8, 0), at(9, 30), "C类", "廊桥"),
        FlightInfo("MU5678", at(8, 30), at(10, 0), "E类", "廊桥"),
        FlightInfo("CZ9012", at(9, 0), at(11, 0), "D类", "远机位"),
        FlightInfo("HU3456", at(9, 30), at(12, 0), "F类", "远机位")
    )

    // 与 allocation-module「机位信息表」字段一致：GateInfoRow(id, code, bridge, aircraftClass, minInterval, openTime, closeTime, status)
    override suspend fun gates(): List<GateInfoRow> = listOf(
        GateInfoRow(id = "g1", code = "B27", bridge = GateInfoOptions.BRIDGE, aircraftClass = "C类", status = GateInfoOptions.STATUS_AVAILABLE),
        GateInfoRow(id = "g2", code = "208", bridge = GateInfoOptions.BRIDGE, aircraftClass = "E类", status = GateInfoOptions.STATUS_AVAILABLE),
        GateInfoRow(id = "g3", code = "C05", bridge = GateInfoOptions.REMOTE, aircraftClass = "D类", status = GateInfoOptions.STATUS_AVAILABLE),
        GateInfoRow(id = "g4", code = "C08", bridge = GateInfoOptions.REMOTE, aircraftClass = "C类", status = GateInfoOptions.STATUS_MAINTENANCE),
        GateInfoRow(id = "g5", code = "D12", bridge = GateInfoOptions.REMOTE, aircraftClass = "F类", status = GateInfoOptions.STATUS_DISABLED),
        GateInfoRow(id = "g6", code = "T2-14", bridge = GateInfoOptions.REMOTE, aircraftClass = "D类", status = GateInfoOptions.STATUS_AVAILABLE)
    )

    override suspend fun assignments(): List<Assignment> = listOf(
        Assignment(1, "CA1234", "B27", at(7, 50)),
        Assignment(2, "MU5678", "208", at(8, 20)),
        Assignment(3, "CZ9012", "C05", at(8, 50)),
        // D12 状态=停用，不会被计入可用机位，因此其占用不进入使用率
        Assignment(4, "HU3456", "D12", at(9, 20))
    )

    override suspend fun usageRecords(): List<UsageRecord> = listOf(
        UsageRecord(1, "B27", "CA1234", at(8, 0), at(9, 30)),
        UsageRecord(2, "208", "MU5678", at(8, 30), at(10, 0)),
        UsageRecord(3, "C05", "CZ9012", at(9, 0), at(11, 0))
        // HU3456/D12 没有使用记录：会用航班时刻估算，但 D12 不可用→不计入
    )

    // 构造「今天」某个时刻的 epoch 毫秒（仅示例用）
    private fun at(hour: Int, min: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, min)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
