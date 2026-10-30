package net.micode.airportgate.domain

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.micode.airportgate.data.db.GateRepository
import net.micode.airportgate.engine.GateAllocationEngine
import net.micode.airportgate.engine.reallocateAll
import net.micode.airportgate.engine.toAssignmentRows
import net.micode.airportgate.engine.toUsageRows
import net.micode.airportgate.model.DayFlight
import net.micode.airportgate.model.GateInfoStore
import net.micode.airportgate.model.nextDate
import net.micode.airportgate.model.prevDate
import net.micode.airportgate.model.timeToMinutes

/**
 * 控制层（业务逻辑层）：机位分配业务协调者。
 *
 * 承接「读取待分配航班 -> 调用引擎 -> 落库分配结果 -> 回写航班」的完整流程，
 * 使 ui/assign/AssignScreen 只负责渲染与交互，不再包含分配协调逻辑。
 */
data class AllocationResult(
    val flights: List<DayFlight>,
    val gateCodes: List<String>,
    val failedFlightNos: List<String>
)

object AllocationService {
    /**
     * 执行一次完整分配。引擎计算在 Dispatchers.Default 执行，避免阻塞 UI。
     * @param context 上下文
     * @param date    航班日期 yyyy-MM-dd，分配结果按天落库
     * @return 界面所需的展示数据
     */
    suspend fun allocate(context: Context, date: String): AllocationResult = withContext(Dispatchers.Default) {
        // 1) 回读「前一天」已分配结果中跨夜前段的机位，作为本日跨夜后段的固定机位
        val pinsFromPrev = readPrevDayPins(context, date)
        var cur = date
        var pins = pinsFromPrev
        var firstResult: AllocationResult? = null
        var fronts = emptyMap<String, String>()
        var guard = 0
        // 前向级联：本日若有跨夜前段（离开=24:00），次日跨夜后段需固定到同一机位，
        // 因此自动把次日也跑一遍分配（最多串 4 个夜晚，防止极端情况下死循环）
        while (guard < 4) {
            guard++
            val out = allocateDay(context, cur, pins)
            if (firstResult == null) firstResult = out.uiResult
            fronts = out.frontGates
            val next = nextDate(cur)
            val nextBacks = GateRepository.loadFlights(context, next)
                .filter { it.valid && it.status != "已取消" && it.arrivalTime == "00:00" && fronts.containsKey(it.flightNo) }
            if (nextBacks.isEmpty()) break
            pins = nextBacks.associate { it.flightNo to (fronts[it.flightNo] ?: "") }
                .filterValues { it.isNotBlank() }
            if (pins.isEmpty()) break
            cur = next
        }
        firstResult ?: AllocationResult(emptyList(), emptyList(), emptyList())
    }

    /**
     * 分配单日航班，并返回「跨夜前段（离开=24:00）的航班号->机位」映射。
     * [pins] 为外部传入的固定占用：键为航班号（即本日跨夜后段），值为前一天前段所在机位，
     * 这些航班会被预置到对应机位、不再参与引擎重排，从而保证跨夜前后段同机位。
     */
    private fun allocateDay(
        context: Context,
        date: String,
        pins: Map<String, String>
    ): DayAllocateOutcome {
        val drafts = GateRepository.loadFlights(context, date)
            .filter { it.valid && it.status != "已取消" }
        val gates = GateInfoStore.loadAvailableForEngine(context)
        val gateCodes = gates.map { it.code }
        if (drafts.isEmpty() || gates.isEmpty()) {
            return DayAllocateOutcome(AllocationResult(emptyList(), gateCodes, emptyList()), emptyMap())
        }
        // 跨夜后段（到达=00:00 且前一天前段已定机位）预置到对应机位
        val existing = mutableListOf<GateAllocationEngine.Occupancy>()
        for (d in drafts) {
            val pinGate = pins[d.flightNo]
            if (pinGate != null && d.arrivalTime == "00:00") {
                val dep = timeToMinutes(d.departureTime) ?: 0
                existing.add(GateAllocationEngine.Occupancy(pinGate, GateAllocationEngine.FlightIn(d.flightNo, d.aircraft, 0, dep)))
            }
        }
        val (newFlights, outcome) = reallocateAll(drafts, gates, existing)
        // 1) 清当天旧结果，避免重复堆积
        GateRepository.clearDayResults(context, date)
        // 2) 写入 t_assignment / t_gate_usage
        val assignmentRows = toAssignmentRows(outcome, date, drafts)
        val usageRows = toUsageRows(outcome, date, drafts)
        assignmentRows.forEach { a ->
            GateRepository.addAssignment(
                context, date, a.flightNo, a.gateNo, a.aircraft, a.direction,
                a.planStart, a.planEnd, a.isBridge, a.assignType, a.operator, a.conflict, a.version
            )
        }
        usageRows.forEach { u ->
            GateRepository.addUsage(
                context, u.date, u.gateNo, u.flightNo, u.region, u.isBridge, u.needBus,
                u.planStart, u.planEnd, u.actualStart, u.actualEnd, u.occupyMinutes
            )
        }
        // 3) 回写航班：机位编号 + 状态（待分配 -> 已分配）
        drafts.forEach { f ->
            val a = outcome.assignments.firstOrNull { it.flightNo == f.flightNo }
            if (a != null) GateRepository.markFlightAssigned(context, f.id, a.gate)
        }
        // 收集本日跨夜前段（离开=24:00）的机位，供次日后段固定
        val frontGates = drafts.filter { it.departureTime == "24:00" }
            .mapNotNull { f ->
                outcome.assignments.firstOrNull { it.flightNo == f.flightNo }?.gate
                    ?.let { g -> f.flightNo to g }
            }.toMap()
        return DayAllocateOutcome(
            AllocationResult(newFlights, gateCodes, outcome.failedFlightNos),
            frontGates
        )
    }

    /** 回读前一天跨夜前段（离开=24:00 且已分配机位）的「航班号->机位」，用于固定本日跨夜后段 */
    private fun readPrevDayPins(context: Context, date: String): Map<String, String> {
        val prev = prevDate(date)
        if (prev >= date) return emptyMap()
        val prevDrafts = GateRepository.loadFlights(context, prev)
            .filter { it.departureTime == "24:00" && it.gateNo.isNotBlank() }
        if (prevDrafts.isEmpty()) return emptyMap()
        val curBacks = GateRepository.loadFlights(context, date)
            .filter { it.arrivalTime == "00:00" }
            .associateBy { it.flightNo }
        val pins = mutableMapOf<String, String>()
        for (p in prevDrafts) {
            if (curBacks.containsKey(p.flightNo)) pins[p.flightNo] = p.gateNo
        }
        return pins
    }

    /** 单日分配结果 + 跨夜前段机位（供前向级联） */
    private data class DayAllocateOutcome(
        val uiResult: AllocationResult,
        val frontGates: Map<String, String>
    )

    /**
     * 探测当前「可分配航班」与「可用机位」是否各自就绪。
     * 界面据此决定「一键分配」按钮是否可点击，并在按钮置灰被点击时分别提示缺哪一项。
     * 仅读取、不落库、不跑引擎。
     */
    suspend fun readiness(context: Context, date: String): AllocationReadiness = withContext(Dispatchers.Default) {
        val drafts = GateRepository.loadFlights(context, date)
            .filter { it.valid && it.status != "已取消" }
        val gates = GateInfoStore.loadAvailableForEngine(context)
        AllocationReadiness(drafts.isNotEmpty(), gates.isNotEmpty())
    }
}

/** 分配就绪状态：航班与机位是否各自存在。 */
data class AllocationReadiness(
    val hasFlights: Boolean,
    val hasGates: Boolean
) {
    val canAllocate: Boolean get() = hasFlights && hasGates
}
