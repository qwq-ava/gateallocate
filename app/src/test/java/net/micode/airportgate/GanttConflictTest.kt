package net.micode.airportgate

import net.micode.airportgate.data.model.GateSlot
import net.micode.airportgate.data.model.markConflicts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 甘特图冲突标记的行为测试。
 *
 * 回归重点：同一机位出现三段以上连续重叠时，旧实现用 out.indexOf(元素) 回写标记，
 * 第二次匹配已 copy 过的对象会返回 -1，抛 IndexOutOfBoundsException 使机位分配页闪退。
 */
class GanttConflictTest {

    private fun slot(
        gate: String,
        no: String,
        start: Int,
        end: Int,
        date: String = "2026-11-11"
    ) = GateSlot(
        gate = gate,
        flightNo = no,
        startMin = start,
        endMin = end,
        isBridge = false,
        finished = false,
        date = date
    )

    @Test
    fun noOverlap_keepsAllClear() {
        val input = listOf(
            slot("101", "F1", 60, 120),
            slot("101", "F2", 130, 190),
            slot("101", "F3", 200, 260)
        )
        val out = markConflicts(input)
        assertEquals(3, out.size)
        assertTrue(out.none { it.conflict })
        assertEquals(input, out) // 无冲突时应原样返回，不产生新副本
    }

    @Test
    fun twoOverlappingSlots_bothMarked() {
        val out = markConflicts(
            listOf(
                slot("101", "F1", 60, 130),
                slot("101", "F2", 120, 190)
            )
        )
        assertTrue(out[0].conflict)
        assertTrue(out[1].conflict)
    }

    @Test
    fun threeConsecutiveOverlaps_noCrash_allMarked() {
        // 旧实现在这里崩溃：F2 第一次被标记后，第二次比较时 indexOf(F2) 返回 -1
        val out = markConflicts(
            listOf(
                slot("101", "F1", 60, 130),
                slot("101", "F2", 120, 200),
                slot("101", "F3", 190, 260)
            )
        )
        assertEquals(3, out.size)
        assertTrue(out.all { it.conflict })
        assertEquals(listOf("F1", "F2", "F3"), out.map { it.flightNo })
    }

    @Test
    fun repeatedMarking_isIdempotent() {
        val input = listOf(
            slot("101", "F1", 60, 300),
            slot("101", "F2", 100, 200),
            slot("101", "F3", 150, 250),
            slot("101", "F4", 220, 280)
        )
        val out = markConflicts(input)
        assertTrue(out.all { it.conflict })
        assertEquals(input.map { it.flightNo }, out.map { it.flightNo })
        assertEquals(input.map { it.startMin }, out.map { it.startMin })
    }

    @Test
    fun sameGateDifferentDates_areNotConflicts() {
        val out = markConflicts(
            listOf(
                slot("101", "F1", 600, 700, "2026-11-11"),
                slot("101", "F2", 600, 700, "2026-11-12")
            )
        )
        assertFalse(out[0].conflict)
        assertFalse(out[1].conflict)
    }

    @Test
    fun sameDateDifferentGates_areNotConflicts() {
        val out = markConflicts(
            listOf(
                slot("101", "F1", 600, 700),
                slot("102", "F2", 600, 700)
            )
        )
        assertTrue(out.none { it.conflict })
    }

    @Test
    fun touchingIntervals_areNotOverlaps() {
        val out = markConflicts(
            listOf(
                slot("101", "F1", 60, 120),
                slot("101", "F2", 120, 180)
            )
        )
        assertTrue(out.none { it.conflict })
    }

    @Test
    fun emptyInput_returnsEmpty() {
        assertTrue(markConflicts(emptyList()).isEmpty())
    }
}
