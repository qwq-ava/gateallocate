package net.micode.airportgate.engine

import net.micode.airportgate.model.Allocation
import net.micode.airportgate.model.AllocationOutcome
import net.micode.airportgate.model.DayFlight
import net.micode.airportgate.model.GateInfo

// ========================= 引擎 <-> 界面数据适配 =========================

/** DayFlight（FlightInfo 界面形态）-> 引擎航班输入 */
fun DayFlight.toFlightIn(): GateAllocationEngine.FlightIn =
    GateAllocationEngine.FlightIn(flightNo, aircraft, startMin, endMin)

/** GateInfo（机位实体） -> 引擎机位输入 */
fun GateInfo.toGateIn(): GateAllocationEngine.GateIn =
    GateAllocationEngine.GateIn(gate, isBridge, level)

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
 * 全量重排：把当天全部航班重新分配（对应 4.1.2 用例主流程）。
 * 原数据中的机位占用全部清空，由引擎重新着色，可消除历史冲突。
 *
 * @return Pair(重新分配后的航班列表, 分配结果汇总)
 */
fun reallocateAll(
    flights: List<DayFlight>,
    gates: List<GateInfo>
): Pair<List<DayFlight>, AllocationOutcome> {
    val conflictBefore = countConflicts(flights)

    val result = GateAllocationEngine.startAutoAllocation(
        flights.map { it.toFlightIn() },
        gates.map { it.toGateIn() },
        emptyList()
    )

    // 保留航班的航线/机型等原始信息，仅更新机位与廊桥属性
    val byFlightNo = flights.associateBy { it.flightNo }
    val gateMap = gates.associateBy { it.gate }
    val newFlights = result.assignments.map { a ->
        val origin = byFlightNo[a.flightNo]!!
        origin.copy(
            gate = a.gate,
            isBridge = gateMap[a.gate]?.isBridge ?: a.bridge
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
