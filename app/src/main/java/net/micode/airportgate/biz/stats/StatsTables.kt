package net.micode.airportgate.biz.stats

import net.micode.airportgate.data.model.GateInfoRow

// ========================= 四张表的接口（仅契约，不含数据库实现） =========================
// 将来建好数据库后，让各个表的 Dao/Repository 实现这些接口即可，
// 计算逻辑（GateUtilizationCalculator）完全不用改。

/** 航班信息表 */
interface FlightTable {
    suspend fun flights(): List<FlightInfo>
}

/** 机位信息表（直接复用现有 model.GateInfoRow，字段：code/bridge/aircraftClass/status） */
interface GateInfoTable {
    suspend fun gates(): List<GateInfoRow>
}

/** 分配结果表 */
interface AssignmentTable {
    suspend fun assignments(): List<Assignment>
}

/** 使用记录表 */
interface UsageRecordTable {
    suspend fun usageRecords(): List<UsageRecord>
}

/**
 * 统计数据源：把上面四张表聚合成一个接口。
 * 数据库就绪后，用一个实现类（如 Room 的 StatsRepository）实现本接口即可。
 */
interface StatsDataSource :
    FlightTable,
    GateInfoTable,
    AssignmentTable,
    UsageRecordTable
