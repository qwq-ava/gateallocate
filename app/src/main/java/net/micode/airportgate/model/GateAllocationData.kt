package net.micode.airportgate.model

// ========================= 机位分配：实体模型 =========================
// 对齐《软件设计规格说明书》4.2 类设计：
//   GateInfo   含 编号、等级、类型、状态
//   FlightInfo 含 航班号、机型、起降时间（复用 DayFlight 的 startMin/endMin/aircraft）
//   GateAllocation 含 分配ID、航班号、机位编号、分配日期、开始时间、结束时间、占用时长

/** 机位信息（GateInfo）：编号 / 等级 / 类型 / 状态 */
data class GateInfo(
    val gate: String,        // 机位编号
    val isBridge: Boolean,   // 类型：true=廊桥机位，false=远机位
    val level: Int,          // 等级：大机型保障能力 1..3（3 最高）
    val status: String = "可用"
) {
    /** 占用时长展示用类型名 */
    val typeName: String get() = if (isBridge) "廊桥" else "远机位"
}

/** 一条分配结果（GateAllocation 的界面呈现形态） */
data class Allocation(
    val allocationId: String, // 分配ID
    val flightNo: String,     // 航班号
    val gate: String,         // 机位编号
    val startMin: Int,        // 开始时间
    val endMin: Int,          // 结束时间
    val isBridge: Boolean     // 机位类型
) {
    /** 占用时长（分钟） */
    val duration: Int get() = endMin - startMin
}

/** 一键分配的结果汇总（AllocationResult） */
data class AllocationOutcome(
    val assignments: List<Allocation>,   // 成功分配的机位占用
    val failedFlightNos: List<String>,   // 无可用机位、分配失败的航班号
    val moveCount: Int,                  // 回溯腾挪次数
    val elapsedMs: Long,                 // 算法耗时
    val conflictBefore: Int,             // 分配前冲突数
    val conflictAfter: Int               // 分配后冲突数
) {
    val successCount: Int get() = assignments.size
}

// ---------- 机位池：与甘特图 7 个机位一致（廊桥 3 + 远机位 4） ----------
val GATE_INFOS = listOf(
    GateInfo("B27", isBridge = true, level = 3),
    GateInfo("B32", isBridge = true, level = 3),
    GateInfo("208", isBridge = true, level = 2),
    GateInfo("C05", isBridge = false, level = 1),
    GateInfo("C08", isBridge = false, level = 1),
    GateInfo("T2-14", isBridge = false, level = 2),
    GateInfo("D12", isBridge = false, level = 1)
)

/** 宽体机判定（廊桥优先策略用） */
fun isWideBody(aircraft: String): Boolean = when {
    aircraft.startsWith("A330") -> true
    aircraft.startsWith("A350") -> true
    aircraft.startsWith("B787") -> true
    aircraft.startsWith("B777") -> true
    aircraft.startsWith("B747") -> true
    aircraft.startsWith("A380") -> true
    else -> false
}
