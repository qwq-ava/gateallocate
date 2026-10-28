package net.micode.airportgate.domain

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.micode.airportgate.data.db.GateRepository
import net.micode.airportgate.engine.reallocateAll
import net.micode.airportgate.engine.toAssignmentRows
import net.micode.airportgate.engine.toUsageRows
import net.micode.airportgate.model.DayFlight
import net.micode.airportgate.model.GateInfoStore

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
        val drafts = GateRepository.loadFlights(context, date)
            .filter { it.valid && it.status != "已取消" }
        val gates = GateInfoStore.loadAvailableForEngine(context)
        val gateCodes = gates.map { it.code }
        if (drafts.isEmpty() || gates.isEmpty()) {
            return@withContext AllocationResult(emptyList(), gateCodes, emptyList())
        }
        val (newFlights, outcome) = reallocateAll(drafts, gates)
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
        AllocationResult(newFlights, gateCodes, outcome.failedFlightNos)
    }

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
