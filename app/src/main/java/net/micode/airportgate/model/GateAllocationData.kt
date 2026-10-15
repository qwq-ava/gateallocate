package net.micode.airportgate.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ========================= 机位分配：实体模型 =========================
// 对齐《软件设计规格说明书》4.2 类设计：
//   GateInfo   含 编号、等级、类型、状态
//   FlightInfo 含 航班号、机型、起降时间（复用 DayFlight 的 startMin/endMin/aircraft）
//   GateAllocation 含 分配ID、航班号、机位编号、分配日期、开始时间、结束时间、占用时长

/** 机位信息（对齐 GateForEngine：编号 / 类型 / 可停机型系列 / 安全间隔 / 可用时段 / 状态 / 区域） */
data class GateInfo(
    val code: String,        // 机位编号（对齐 GateForEngine.code）
    val isBridge: Boolean,   // 类型：true=廊桥机位，false=远机位
    val aircraftClasses: List<String> = listOf("A320", "A330", "A350", "B737", "B777", "B787", "B747", "A380"),
    val minInterval: Int = 15,        // 该机位安全间隔（分钟），对应 GateForEngine.minInterval
    val openMin: Int = 0,             // 可用时段起（当日分钟），对应 openMin
    val closeMin: Int = 1439,         // 可用时段止（当日分钟），对应 closeMin
    val status: String = "可用",      // 状态：可用 / 维护 / 停用
    val region: String = "T1 廊桥机位" // 所属区域（机位档案不入库，与机位表约定一致）
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

// ---------- 机位池：与文档默认 6 个机位一致（廊桥 2 + 远机位 4，已移除 B32） ----------
// 演示数据刻意“宽松可停”（全系列），保证 50 航班可解；
// 接入真实 GateInfoStore.loadAvailableForEngine 后由同伴按物理能力收紧 aircraftClasses / minInterval。
val GATE_INFOS = listOf(
    GateInfo("B27", isBridge = true, minInterval = 15, region = "T1 廊桥机位"),
    GateInfo("208", isBridge = true, minInterval = 15, region = "T2 廊桥机位"),
    GateInfo("C05", isBridge = false, minInterval = 20, region = "T1 远机位"),
    GateInfo("C08", isBridge = false, minInterval = 20, region = "T1 远机位"),
    GateInfo("T2-14", isBridge = false, minInterval = 25, region = "T2 远机位"),
    GateInfo("D12", isBridge = false, minInterval = 25, region = "T2 远机位")
)

// ---------- 落库行结构（对齐 GateRepository.addAssignment / addUsage，内存暂存，等同伴合并后直接写库） ----------

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
    val region: String,      // 所属区域（冗余，避免统计时 join 机位表）
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

// ========================= 分配结果暂存（仅界面展示，不落库） =========================
// 30 天结果留存：每次「一键分配」后记录当天的 AssignmentRow；跨界面导航保留在内存，
// 等同伴建好 t_assignment 后，用 engine.toAssignmentRows() 产出的同一份行结构直接对接写库。

object AllocationStore {
    /** 某一天的记录（日期 + 当天分配结果行 + 使用记录行） */
    data class DailyRecord(
        val date: String,
        val assignments: List<AssignmentRow>,
        val usages: List<GateUsageRow>
    )

    private val dateFmt = SimpleDateFormat("MM-dd", Locale.CHINA)
    private val _history = mutableListOf<DailyRecord>()
    val history: List<DailyRecord> get() = _history
    /** 近 30 天累计分配记录条数 */
    val last30Count: Int get() = _history.sumOf { it.assignments.size }

    /** 记录一次分配结果：同一天只保留最新一次，超出 30 天自动丢弃最旧 */
    fun record(assignments: List<AssignmentRow>, usages: List<GateUsageRow>) {
        val date = dateFmt.format(Date())
        _history.removeIf { it.date == date }
        _history.add(DailyRecord(date, assignments, usages))
        while (_history.size > 30) _history.removeAt(0)
    }

    /** 当天已记录的分配条数 */
    fun todayCount(): Int {
        val date = dateFmt.format(Date())
        return _history.firstOrNull { it.date == date }?.assignments?.size ?: 0
    }
}
