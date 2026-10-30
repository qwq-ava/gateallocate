package net.micode.airportgate.biz.engine

import net.micode.airportgate.data.model.Allocation
import net.micode.airportgate.data.model.AllocationOutcome
import net.micode.airportgate.data.model.AssignmentRow
import net.micode.airportgate.data.model.DayFlight
import net.micode.airportgate.data.model.FlightDraft
import net.micode.airportgate.data.model.GateForEngine
import net.micode.airportgate.data.model.GateUsageRow
import net.micode.airportgate.data.model.timeToMinutes

// ========================= 引擎 <-> 界面 / 数据库 数据适配 =========================

/** FlightDraft（t_flight 行）-> 引擎航班输入（到达/离开时刻转当日分钟） */
fun FlightDraft.toFlightIn(): GateAllocationEngine.FlightIn {
    val a = timeToMinutes(arrivalTime) ?: 0
    val d = timeToMinutes(departureTime) ?: 0
    return GateAllocationEngine.FlightIn(flightNo, aircraft, a, d)
}

/** GateForEngine（机位档案最小视图）-> 引擎机位输入（1:1 字段对齐） */
fun GateForEngine.toGateIn(): GateAllocationEngine.GateIn =
    GateAllocationEngine.GateIn(code, isBridge, aircraftClasses, minInterval, openMin, closeMin, status)

/** 统计同机位时段重叠的对数（分配前/后冲突对比） */
fun countConflicts(flights: List<DayFlight>): Int {
    var conflicts = 0
    flights.groupBy { it.gate }.forEach { (_, group) ->
        val sorted = group.sortedBy { it.startMin }
        for (i in 0 until sorted.size - 1) {
            if (sorted[i].endMin > sorted[i + 1].startMin) conflicts++
        }
    }
    return conflicts
}

/**
 * 全量重排：把当天全部（有效、未取消）航班重新分配。
 * 航班来自 t_flight（FlightDraft），机位来自 t_gate（GateForEngine，经 GateInfoStore）。
 * 原数据中的机位占用全部清空，由引擎重新着色，可消除历史冲突。
 *
 * @return Pair(重新分配后的航班列表（用于甘特图/今日动态展示）, 分配结果汇总)
 */
fun reallocateAll(
    flights: List<FlightDraft>,
    gates: List<GateForEngine>
): Pair<List<DayFlight>, AllocationOutcome> {
    val conflictBefore = 0 // 重排前无既有机位占用，冲突基准记为 0

    val result = GateAllocationEngine.startAutoAllocation(
        flights.map { it.toFlightIn() },
        gates.map { it.toGateIn() },
        emptyList()
    )

    // 保留航班的航线/机型等原始信息，仅更新机位与廊桥属性
    val byFlightNo = flights.associateBy { it.flightNo }
    val newFlights = result.assignments.map { a ->
        val origin = byFlightNo[a.flightNo]
        DayFlight(
            flightNo = a.flightNo,
            gate = a.gate,
            startMin = a.startMin,
            endMin = a.endMin,
            isBridge = a.bridge,
            from = origin?.origin ?: "",
            to = origin?.dest ?: "",
            aircraft = origin?.aircraft ?: ""
        )
    }

    val assignments = result.assignments.mapIndexed { index, a ->
        Allocation(
            allocationId = "AL%03d".format(index + 1),
            flightNo = a.flightNo,
            gate = a.gate,
            startMin = a.startMin,
            endMin = a.endMin,
            isBridge = a.bridge
        )
    }

    val outcome = AllocationOutcome(
        assignments = assignments,
        failedFlightNos = result.failedFlightNos,
        moveCount = result.moveCount,
        elapsedMs = result.elapsedMs,
        conflictBefore = conflictBefore,
        conflictAfter = countConflicts(newFlights)
    )
    return newFlights to outcome
}

// ========================= 分配结果 -> 落库行（t_assignment / t_gate_usage） =========================

/** 分钟数 -> "HH:mm" */
private fun fmtHHmm(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

/** 本场机场：从本场出发为出港，否则为进港（FlightDraft 无 direction 时按起点推导） */
private const val HOME_AIRPORT = "上海浦东"
private fun deriveDirection(from: String): String = if (from == HOME_AIRPORT) "出港" else "进港"

/**
 * AllocationOutcome -> t_assignment 行（对齐 GateRepository.addAssignment 入参）。
 * 需要原始航班列表以取机型 / 进出港，date 用于 flight_date。
 */
fun toAssignmentRows(
    outcome: AllocationOutcome,
    date: String,
    flights: List<FlightDraft>,
    operator: String = "系统",
    version: Int = 1
): List<AssignmentRow> {
    val byFlightNo = flights.associateBy { it.flightNo }
    return outcome.assignments.map { a ->
        val f = byFlightNo[a.flightNo]
        AssignmentRow(
            flightNo = a.flightNo,
            date = date,
            gateNo = a.gate,
            aircraft = f?.aircraft ?: "",
            direction = f?.direction?.takeIf { it.isNotBlank() } ?: deriveDirection(f?.origin ?: ""),
            planStart = fmtHHmm(a.startMin),
            planEnd = fmtHHmm(a.endMin),
            isBridge = a.isBridge,
            assignType = "自动分配",
            operator = operator,
            conflict = false,
            version = version,
            status = "已分配"
        )
    }
}

/**
 * AllocationOutcome -> t_gate_usage 行（对齐 GateRepository.addUsage 入参）。
 * region 由机位类型推导（廊桥机位 / 远机位）；actual 暂以 plan 代替，
 * occupyMinutes 取计划时长。
 */
fun toUsageRows(
    outcome: AllocationOutcome,
    date: String,
    flights: List<FlightDraft>
): List<GateUsageRow> {
    return outcome.assignments.map { a ->
        val start = fmtHHmm(a.startMin)
        val end = fmtHHmm(a.endMin)
        GateUsageRow(
            date = date,
            gateNo = a.gate,
            flightNo = a.flightNo,
            region = if (a.isBridge) "廊桥机位" else "远机位",
            isBridge = a.isBridge,
            needBus = !a.isBridge,
            planStart = start,
            planEnd = end,
            actualStart = start,
            actualEnd = end,
            occupyMinutes = (a.endMin - a.startMin).coerceAtLeast(0)
        )
    }
}
