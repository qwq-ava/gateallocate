package net.micode.airportgate.model

// ========================= 机位分配：实体模型 =========================
// 对齐《软件设计规格说明书》4.2 类设计：
//   GateAllocation 含 分配ID、航班号、机位编号、分配日期、开始时间、结束时间、占用时长
//
// 注意：机位档案（GateInfo）已迁至 model.GateInfo.kt 的 GateInfoRow / GateForEngine，
// 并持久化到 SQLite t_gate；航班来自 t_flight。本文件只保留分配相关的视图模型与落库行结构。

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
    val failedFlightNos: List<String>,  // 无可用机位、分配失败的航班号
    val moveCount: Int,                 // 回溯腾挪次数
    val elapsedMs: Long,                // 算法耗时
    val conflictBefore: Int,            // 分配前冲突数
    val conflictAfter: Int              // 分配后冲突数
) {
    val successCount: Int get() = assignments.size
}

// ---------- 落库行结构（对齐 GateRepository.addAssignment / addUsage，直接写 SQLite） ----------

/** t_assignment 行：对齐 GateRepository.addAssignment 入参（不含 context） */
data class AssignmentRow(
    val flightNo: String,   // 航班号
    val date: String,       // 航班日期 yyyy-MM-dd
    val gateNo: String,     // 分配到的机位 —— 核心结果
    val aircraft: String,   // 机型（冗余，便于校验机型 vs 机位尺寸）
    val direction: String,  // 进出港
    val planStart: String,  // 计划占用起 HH:mm
    val planEnd: String,    // 计划占用止 HH:mm
    val isBridge: Boolean,  // 本次分配是否靠桥
    val assignType: String, // 自动分配 / 手动改派
    val operator: String,   // 操作人
    val conflict: Boolean,  // 是否冲突 0/1
    val version: Int,       // 版本号，改派 +1
    val status: String      // 已分配 / 已执行 / 已取消
)

/** t_gate_usage 行：对齐 GateRepository.addUsage 入参（不含 context） */
data class GateUsageRow(
    val date: String,        // 航班日期 yyyy-MM-dd
    val gateNo: String,      // 机位编号
    val flightNo: String,    // 航班号
    val region: String,      // 所属区域（廊桥机位 / 远机位，冗余，避免统计时 join 机位表）
    val isBridge: Boolean,   // 是否靠桥（靠桥率分子）
    val needBus: Boolean,    // 是否用摆渡车
    val planStart: String,   // 计划占用起 HH:mm
    val planEnd: String,     // 计划占用止 HH:mm
    val actualStart: String, // 实际占用起（保障结束后回填，暂以计划代替）
    val actualEnd: String,   // 实际占用止
    val occupyMinutes: Int   // 实际占用时长（分钟），利用率分子
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
