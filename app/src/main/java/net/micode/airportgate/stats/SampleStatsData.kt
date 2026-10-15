package net.micode.airportgate.stats

import net.micode.airportgate.model.GateInfoRow
import java.util.Calendar

// ========================= 示例数据源（内存实现，仅用于演示/测试） =========================
// 这不是数据库！只是把四个表接口用写死的数据实现了，方便你现在就能跑通计算。
// 将来建好真实数据库后，写一个新的 StatsDataSource 实现（比如 Room 的 Repository）替换掉它即可，
// GateUtilizationCalculator 完全不用改。
object SampleStatsData : StatsDataSource {

    override suspend fun flights(): List<FlightInfo> = listOf(
        FlightInfo("CA1234", at(8, 0), at(9, 30), "C类", "近机位"),
        FlightInfo("MU5678", at(8, 30), at(10, 0), "E类", "近机位"),
        FlightInfo("CZ9012", at(9, 0), at(11, 0), "D类", "远机位"),
        FlightInfo("HU3456", at(9, 30), at(12, 0), "F类", "远机位")
    )

    // 与现有「机位信息表」字段一致：GateInfoRow(id, code, standType, aircraftType, status)
    override suspend fun gates(): List<GateInfoRow> = listOf(
        GateInfoRow("g1", "B27", "近机位", "C类", "开放"),
        GateInfoRow("g2", "208", "近机位", "E类", "开放"),
        GateInfoRow("g3", "C05", "远机位", "D类", "开放"),
        GateInfoRow("g4", "C08", "远机位", "C类", "关闭"),
        GateInfoRow("g5", "D12", "远机位", "F类", "不可用"),
        GateInfoRow("g6", "T2-14", "远机位", "D类", "开放")
    )

    override suspend fun assignments(): List<Assignment> = listOf(
        Assignment(1, "CA1234", "B27", at(7, 50)),
        Assignment(2, "MU5678", "208", at(8, 20)),
        Assignment(3, "CZ9012", "C05", at(8, 50)),
        // D12 状态=不可用，不会被计入可用机位，因此其占用不进入使用率
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
