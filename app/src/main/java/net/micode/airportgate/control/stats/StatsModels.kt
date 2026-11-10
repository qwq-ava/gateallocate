package net.micode.airportgate.control.stats

import net.micode.airportgate.data.model.GateInfoOptions

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
    val assignedAt: Long,
    val isBridge: Boolean = false, // t_assignment.is_bridge —— 靠桥率分子的兜底来源
    val occupyStart: Long = 0L,    // flight_date + plan_start（epoch 毫秒），用于窗口过滤
    val occupyEnd: Long = 0L       // flight_date + plan_end（epoch 毫秒）
)

/** 使用记录表的一行（真实的占用时段） */
data class UsageRecord(
    val id: Long,
    val gateNo: String,            // 外键 → 机位信息表.code
    val flightNo: String,          // 外键 → 航班信息表.flightNo
    val occupyStart: Long,         // 占用开始（epoch 毫秒）
    val occupyEnd: Long,           // 占用结束（epoch 毫秒）
    val isBridge: Boolean = false  // t_gate_usage.is_bridge —— 靠桥率分子的优先来源
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

// ========================= 按天趋势（折线图数据） =========================

/**
 * 折线图上「一天」对应的统计窗口。
 * 历史日期为完整 24 小时，今天截止到当前时刻，避免把尚未到来的时间算进分母。
 */
data class DayWindow(
    val date: String,              // yyyy-MM-dd，用于详情与调试
    val label: String,             // X 轴短标签，如 "11/10"
    val start: Long,               // 当天 00:00（epoch 毫秒）
    val end: Long,                 // min(次日 00:00, 当前时刻)
    val isToday: Boolean = false
)

/** 折线图上的一个点：某天的平均机位利用率 */
data class DailyUtilization(
    val date: String,
    val label: String,
    val utilization: Double,       // 百分比 0..100
    val isToday: Boolean = false
)

/** 按天展开的平均机位利用率趋势（近 7 日 / 本月） */
data class UtilizationTrend(val points: List<DailyUtilization>) {
    /** 区间内单日最高利用率 */
    val peak: Double get() = points.maxOfOrNull { it.utilization } ?: 0.0

    /** 各日利用率的算术平均（不是整窗口加权利用率） */
    val dailyAverage: Double
        get() = if (points.isEmpty()) 0.0 else points.map { it.utilization }.average()

    /** 峰值出现在哪一天（X 轴标签） */
    val peakLabel: String get() = points.maxByOrNull { it.utilization }?.label ?: "—"

    /** 今天（最后一个点）的利用率 */
    val today: Double get() = points.lastOrNull()?.utilization ?: 0.0
}

/**
 * 计算时使用的状态/种类常量。
 * 适配 allocation-module：该分支的 GateInfoRow 用 bridge="廊桥"/"远机位"、
 * status="可用"/"维护"/"停用"，故这里直接复用 GateInfoOptions 的常量。
 */
const val STATUS_OPEN = GateInfoOptions.STATUS_AVAILABLE   // "可用"
// 「廊桥」用于机位档案与区域分组展示；靠桥率已统一到 t_gate_usage / t_assignment 的
// is_bridge 字段，不再用机位档案的 bridge 反推分子（避免机位表换血后 join 不到）。
const val STAND_NEAR = GateInfoOptions.BRIDGE             // "廊桥"
