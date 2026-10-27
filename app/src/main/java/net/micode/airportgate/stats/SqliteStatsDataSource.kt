package net.micode.airportgate.stats

import android.content.Context
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.micode.airportgate.data.db.GateDbContract
import net.micode.airportgate.data.db.GateDbHelper
import net.micode.airportgate.model.GateInfoRow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// ========================= 真实数据源：直接读 allocation-module 的 SQLite =========================
//
// 这四张表（见 data.db.GateDbHelper / GateDbContract）就是统计所需的全部原始数据：
//   t_gate(t_gate)                → gates()
//   t_flight(t_flight)            → flights()
//   t_assignment(t_assignment)    → assignments()
//   t_gate_usage(t_gate_usage)    → usageRecords()
//
// 本类把这些数据库行映射成 stats 计算模块用的领域模型
// （FlightInfo / GateInfoRow / Assignment / UsageRecord），
// 于是「计算逻辑」(GateUtilizationCalculator) 一行都不用改，只换数据源即可用上真实数据：
//
//     val ds = SqliteStatsDataSource(context)
//     val result = GateUtilizationCalculator.compute(windowStart, windowEnd, ds)
//
// 说明：allocation-module 用 android.database.sqlite（零第三方依赖），这里直接复用
//       GateDbHelper.get(context).readableDatabase 即可，无需再引 Room。

class SqliteStatsDataSource(private val context: Context) : StatsDataSource {

    private val db get() = GateDbHelper.get(context).readableDatabase

    override suspend fun gates(): List<GateInfoRow> = withContext(Dispatchers.IO) {
        val g = GateDbContract.Gate
        val out = mutableListOf<GateInfoRow>()
        db.query(g.TABLE, null, null, null, null, null, "${g.C_CODE} ASC").use { c ->
            while (c.moveToNext()) {
                out += GateInfoRow(
                    id = c.str(g.C_ROW_ID),
                    code = c.str(g.C_CODE),
                    bridge = c.str(g.C_BRIDGE),
                    aircraftClass = c.str(g.C_AIRCRAFT_CLASS),
                    minInterval = c.int(g.C_MIN_INTERVAL),
                    openTime = c.str(g.C_OPEN_TIME),
                    closeTime = c.str(g.C_CLOSE_TIME),
                    status = c.str(g.C_STATUS)
                )
            }
        }
        out
    }

    override suspend fun flights(): List<FlightInfo> = withContext(Dispatchers.IO) {
        val f = GateDbContract.Flight
        val out = mutableListOf<FlightInfo>()
        db.query(f.TABLE, null, null, null, null, null, null).use { c ->
            while (c.moveToNext()) {
                val date = c.str(f.C_FLIGHT_DATE)
                val arrivalMin = c.nullableInt(f.C_ARRIVAL_TIME)
                val departureMin = c.nullableInt(f.C_DEPARTURE_TIME)
                // 占用窗口优先用 arrival/departure（当日分钟数），缺失时用 sched_time 兜底
                val arrival = epoch(date, arrivalMin)
                    ?: epoch(date, c.str(f.C_SCHED_TIME).takeIf { it.isNotEmpty() })
                    ?: continue
                val departure = epoch(date, departureMin) ?: arrival
                if (departure <= arrival) continue
                out += FlightInfo(
                    flightNo = c.str(f.C_FLIGHT_NO),
                    arrival = arrival,
                    departure = departure,
                    // requiredAircraftType / requiredStandType 计算时未使用，按 DB 原值映射即可
                    requiredAircraftType = c.str(f.C_AIRCRAFT),
                    requiredStandType = if (c.int(f.C_IS_BRIDGE) == 1) "廊桥" else "远机位"
                )
            }
        }
        out
    }

    override suspend fun assignments(): List<Assignment> = withContext(Dispatchers.IO) {
        val a = GateDbContract.Assign
        val out = mutableListOf<Assignment>()
        db.query(a.TABLE, null, null, null, null, null, null).use { c ->
            while (c.moveToNext()) {
                if (c.str(a.C_STATUS) == "已取消") continue // 取消的分配不计入靠桥率
                out += Assignment(
                    id = c.long(a.C_ID),
                    flightNo = c.str(a.C_FLIGHT_NO),
                    gateNo = c.str(a.C_GATE_NO),
                    assignedAt = parseTs(c.str(a.C_CREATED)) ?: 0L
                )
            }
        }
        out
    }

    override suspend fun usageRecords(): List<UsageRecord> = withContext(Dispatchers.IO) {
        val u = GateDbContract.Usage
        val out = mutableListOf<UsageRecord>()
        db.query(u.TABLE, null, null, null, null, null, null).use { c ->
            while (c.moveToNext()) {
                if (c.str(u.C_STATUS) == "已取消") continue // 取消的占用不计入使用率
                val date = c.str(u.C_FLIGHT_DATE)
                val start = epoch(date, c.str(u.C_ACTUAL_START).takeIf { it.isNotEmpty() })
                    ?: epoch(date, c.str(u.C_PLAN_START).takeIf { it.isNotEmpty() })
                    ?: continue
                val end = epoch(date, c.str(u.C_ACTUAL_END).takeIf { it.isNotEmpty() })
                    ?: epoch(date, c.str(u.C_PLAN_END).takeIf { it.isNotEmpty() })
                    ?: continue
                if (end <= start) continue
                out += UsageRecord(
                    id = c.long(u.C_ID),
                    gateNo = c.str(u.C_GATE_NO),
                    flightNo = c.str(u.C_FLIGHT_NO),
                    occupyStart = start,
                    occupyEnd = end
                )
            }
        }
        out
    }

    // ---------------- 工具：游标取值 ----------------

    private fun Cursor.str(col: String): String =
        getString(getColumnIndexOrThrow(col)) ?: ""

    private fun Cursor.int(col: String): Int =
        getInt(getColumnIndexOrThrow(col))

    private fun Cursor.long(col: String): Long =
        getLong(getColumnIndexOrThrow(col))

    private fun Cursor.nullableInt(col: String): Int? {
        val i = getColumnIndexOrThrow(col)
        return if (isNull(i)) null else getInt(i)
    }

    // ---------------- 工具：时间转换 ----------------

    /** "yyyy-MM-dd" + 当日分钟数(0..1439) → epoch 毫秒 */
    private fun epoch(dateYmd: String, minutesOfDay: Int?): Long? {
        if (minutesOfDay == null) return null
        val d = dateYmd.split("-").map { it.toIntOrNull() }
        if (d.size != 3 || d.any { it == null }) return null
        val cal = Calendar.getInstance().apply {
            set(d[0]!!, d[1]!! - 1, d[2]!!, minutesOfDay / 60, minutesOfDay % 60, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /** "yyyy-MM-dd" + "HH:mm" → epoch 毫秒 */
    private fun epoch(dateYmd: String, hhmm: String?): Long? {
        if (hhmm == null) return null
        val t = hhmm.split(":").map { it.toIntOrNull() }
        if (t.size != 2 || t.any { it == null }) return null
        val d = dateYmd.split("-").map { it.toIntOrNull() }
        if (d.size != 3 || d.any { it == null }) return null
        val cal = Calendar.getInstance().apply {
            set(d[0]!!, d[1]!! - 1, d[2]!!, t[0]!!, t[1]!!, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /** 解析 created_at 之类的时间戳字符串（容错：多种格式都试一遍） */
    private fun parseTs(s: String): Long? {
        if (s.isEmpty()) return null
        for (fmt in arrayOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm")) {
            try {
                return SimpleDateFormat(fmt, Locale.US).parse(s)?.time
            } catch (_: Exception) {
            }
        }
        return null
    }
}
