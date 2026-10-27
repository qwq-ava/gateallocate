package net.micode.airportgate.stats

import net.micode.airportgate.model.GateInfoOptions

// ========================= 统计计算用领域模型 =========================
// 这里只定义「计算」需要的字段，不涉及任何数据库/表实现。
// 「机位信息表」直接复用 model.GateInfoRow，不在这里再建一份。

/** 航班信息表的一行 */
data class FlightInfo(
    val flightNo: String,          // 航班号（PK）
    val arrival: Long,             // 到达时间（epoch 毫秒）
    val departure: Long,           // 起飞时间（epoch 毫秒）
    val requiredAircraftType: String, // 所需机型：C类 / D类 / E类 / F类
    val requiredStandType: String     // 所需机位种类：近机位 / 远机位
)

/** 分配结果表的一行（航班 ↔ 机位 的对应关系） */
data class Assignment(
    val id: Long,
    val flightNo: String,          // 外键 → 航班信息表.flightNo
    val gateNo: String,            // 外键 → 机位信息表.code
    val assignedAt: Long
)

/** 使用记录表的一行（真实的占用时段） */
data class UsageRecord(
    val id: Long,
    val gateNo: String,            // 外键 → 机位信息表.code
    val flightNo: String,          // 外键 → 航班信息表.flightNo
    val occupyStart: Long,         // 占用开始（epoch 毫秒）
    val occupyEnd: Long            // 占用结束（epoch 毫秒）
)

/** 机位使用率计算结果 */
data class UtilizationResult(
    val avgUtilization: Double,    // 机位使用率（%）
    val bridgeRate: Double,        // 靠桥率（%）
    val regions: List<RegionUtilization>
)

/** 按区域（停机位种类）拆分的使用率 */
data class RegionUtilization(
    val name: String,              // 区域名，如 "近机位" / "远机位"
    val utilization: Double,       // 该区域使用率（%）
    val gateCount: Int             // 该区域开放机位数量
)

/**
 * 计算时使用的状态/种类常量。
 * 适配 allocation-module：该分支的 GateInfoRow 用 bridge="廊桥"/"远机位"、
 * status="可用"/"维护"/"停用"，故这里直接复用 GateInfoOptions 的常量。
 */
const val STATUS_OPEN = GateInfoOptions.STATUS_AVAILABLE   // "可用"
const val STAND_NEAR = GateInfoOptions.BRIDGE             // "廊桥"
