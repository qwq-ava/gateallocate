package net.micode.airportgate

import net.micode.airportgate.control.engine.flightMatchKey
import net.micode.airportgate.control.engine.matchKey
import net.micode.airportgate.control.engine.reallocateAll
import net.micode.airportgate.control.engine.toAssignmentRows
import net.micode.airportgate.data.model.Allocation
import net.micode.airportgate.data.model.AllocationOutcome
import net.micode.airportgate.data.model.FlightDraft
import net.micode.airportgate.data.model.GateForEngine
import net.micode.airportgate.data.model.GateSlot
import net.micode.airportgate.data.model.markConflicts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 过站航班（同一航班号同时有进港、出港两条记录）的分配回写回归测试。
 *
 * 缺陷背景：真实航班数据里 SC7940 / HU7659 等 7 个航班号各占两条记录，
 * 旧实现用 flightNo 作唯一键（associateBy / firstOrNull），两条只命中一条，
 * 于是 t_flight.gate_no 被写成同一个机位，甘特图出现 10 对「假冲突」红色。
 * 修复后以「航班号 + 占用起止」为键，两条各自回写自己的机位。
 */
class TurnaroundWriteBackTest {

    // 09:10-10:10 进港、09:45-10:45 出港：两段本身重叠 25 分钟
    private val inbound = FlightDraft(
        id = 1L, flightNo = "SC7940", direction = "进港", aircraft = "A320neo",
        arrivalTime = "09:10", departureTime = "10:10", origin = "XIY", dest = "TSN"
    )
    private val outbound = FlightDraft(
        id = 2L, flightNo = "SC7940", direction = "出港", aircraft = "A321",
        arrivalTime = "09:45", departureTime = "10:45", origin = "TSN", dest = "HAK"
    )

    private val outcome = AllocationOutcome(
        assignments = listOf(
            Allocation("AL001", "SC7940", "1", 550, 610, false),
            Allocation("AL002", "SC7940", "5", 585, 645, true)
        ),
        failedFlightNos = emptyList(),
        moveCount = 0,
        elapsedMs = 0L,
        conflictBefore = 0,
        conflictAfter = 0
    )

    private fun slot(gate: String, start: Int, end: Int) = GateSlot(
        gate = gate,
        flightNo = "SC7940",
        startMin = start,
        endMin = end,
        isBridge = false,
        finished = false,
        date = "2026-11-11"
    )

    @Test
    fun matchKey_distinguishesTurnaroundLegs() {
        assertNotEquals(inbound.matchKey(), outbound.matchKey())
        assertEquals(inbound.matchKey(), outcome.assignments[0].matchKey())
        assertEquals(outbound.matchKey(), outcome.assignments[1].matchKey())
        assertEquals(flightMatchKey("SC7940", 550, 610), inbound.matchKey())
    }

    @Test
    fun legacyFlightNoKey_collidesAsDocumented() {
        // 记录旧口径为什么会出错：仅按航班号建索引，两条只剩一条
        assertEquals(1, listOf(inbound, outbound).associateBy { it.flightNo }.size)
        // 新口径两条都保留
        assertEquals(2, listOf(inbound, outbound).associateBy { it.matchKey() }.size)
    }

    @Test
    fun assignmentRows_keepOwnDirectionAndAircraft() {
        val rows = toAssignmentRows(outcome, "2026-11-11", listOf(inbound, outbound))
        assertEquals(2, rows.size)
        assertEquals("进港", rows[0].direction)
        assertEquals("A320neo", rows[0].aircraft)
        assertEquals("1", rows[0].gateNo)
        assertEquals("09:10", rows[0].planStart)
        assertEquals("出港", rows[1].direction)
        assertEquals("A321", rows[1].aircraft)
        assertEquals("5", rows[1].gateNo)
        assertEquals("10:45", rows[1].planEnd)
    }

    @Test
    fun writeBack_usesOwnGate_soOverlappingWindowsDoNotTurnRed() {
        // 复现 AllocationService.allocateDay 的回写匹配表达式
        val written = listOf(inbound, outbound).associate { f ->
            val key = f.matchKey()
            f.id to outcome.assignments.firstOrNull { it.matchKey() == key }?.gate
        }
        assertEquals("1", written[1L])
        assertEquals("5", written[2L])

        // 两段窗口依旧重叠，但落到不同机位，markConflicts 不应标红
        val slots = listOf(slot("1", 550, 610), slot("5", 585, 645))
        assertTrue(markConflicts(slots).none { it.conflict })
        // 反证：若旧逻辑把两条都写成机位 1，则同一机位重叠必然标红
        val broken = listOf(slot("1", 550, 610), slot("1", 585, 645))
        assertEquals(2, markConflicts(broken).count { it.conflict })
    }

    @Test
    fun reallocateAll_preservesOwnRouteForEachLeg() {
        val gates = listOf(
            GateForEngine("1", false, listOf("C"), 0, 0, 1440, "可用"),
            GateForEngine("5", false, listOf("C"), 0, 0, 1440, "可用")
        )
        val (flights, result) = reallocateAll(listOf(inbound, outbound), gates)
        assertEquals(2, result.assignments.size)
        assertEquals(2, flights.size)
        val first = flights.single { it.startMin == 550 }
        val second = flights.single { it.startMin == 585 }
        assertEquals("XIY", first.from)
        assertEquals("TSN", first.to)
        assertEquals("A320neo", first.aircraft)
        assertEquals("TSN", second.from)
        assertEquals("HAK", second.to)
        assertEquals("A321", second.aircraft)
        assertNotEquals(first.gate, second.gate)
    }

    @Test
    fun identicalDuplicateRecords_stillShareKey() {
        // 已知限制：航班号与占用窗口完全相同属于真重复脏数据，键无法区分，
        // 需由录入校验拦截，这里显式记录该边界行为。
        val duplicate = inbound.copy(id = 3L)
        assertEquals(inbound.matchKey(), duplicate.matchKey())
    }
}
