package net.micode.airportgate.data.db

import android.content.ContentValues
import android.content.Context
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import net.micode.airportgate.model.FlightDraft
import net.micode.airportgate.model.GateInfoStore
import net.micode.airportgate.model.minutesToTime
import net.micode.airportgate.model.timeToMinutes
import net.micode.airportgate.model.timestampNow

// ========================= 数据访问层 =========================
//
// 对外只暴露业务方法，表结构细节封装在 GateDbContract 里。

/**
 * 给分配引擎用的航班占用窗口（分钟级）。
 * arrivalMin / departureMin 是当日分钟数 0–1439，引擎可直接做区间冲突检测。
 */
data class FlightOccupy(
    val id: Long,
    val flightNo: String,
    val date: String,
    val direction: String,
    val aircraft: String,
    val arrivalMin: Int,     // 占用开始（分钟）
    val departureMin: Int    // 占用结束（分钟）
)

/**
 * 分组维度的机位利用情况。
 * group 取「廊桥机位 / 远机位」——机位档案不再有区域 / 航站楼字段，
 * 按停机位种类分组是引擎真正关心的维度。
 */
data class RegionUsage(
    val group: String,
    val gateCount: Int,
    val flightCount: Int,
    val occupyMinutes: Int,
    val util: Float // 0f ~ 1f
)

/** 某一天的机位运行统计 */
data class DayUsageStat(
    val flightCount: Int,       // 计入统计的航班数
    val bridgeCount: Int,       // 靠桥航班数
    val gateCount: Int,         // 参与统计的机位数
    val occupyMinutes: Int,     // 总占用时长（分钟）
    val windowMinutes: Int,     // 统计窗口时长（分钟）
    val bridgeRate: Float,      // 靠桥率
    val avgUtil: Float,         // 平均机位利用率
    val turnover: Float         // 日均周转次数
)

object GateRepository {

    private fun db(context: Context): SQLiteDatabase =
        GateDbHelper.get(context).writableDatabase

    // ---------------- 1. 航班信息表 ----------------

    /**
     * 写入一天的多条航班（同航班号 + 同日期 + 同进出港视为同一条，覆盖更新）。
     *
     * 时间口径：`arrivalTime` / `departureTime` 转成当日分钟数写入
     * `arrival_time` / `departure_time`，`sched_time` 只存展示用的 HH:mm。
     *
     * **status 不会被重置**：已存在的航班会保留原有状态（如「已分配」），
     * 避免用户在导入页改一次数据就把分配结果打回「待分配」导致引擎重复分配。
     *
     * @return 成功写入的条数
     */
    fun saveFlights(
        context: Context,
        date: String,
        rows: List<FlightDraft>,
        source: String = "手工录入"
    ): Int {
        if (rows.isEmpty()) return 0
        val db = db(context)
        val f = GateDbContract.Flight
        val now = timestampNow()
        var ok = 0
        db.beginTransaction()
        try {
            rows.filter { it.valid }.forEach { r ->
                val cv = ContentValues().apply {
                    put(f.C_FLIGHT_NO, r.flightNo.trim())
                    put(f.C_FLIGHT_DATE, date)
                    put(f.C_DIRECTION, r.direction)
                    put(f.C_AIRCRAFT, r.aircraft.trim())
                    put(f.C_SCHED_TIME, r.planTime.trim())
                    put(f.C_ARRIVAL_TIME, timeToMinutes(r.arrivalTime.trim()))
                    put(f.C_DEPARTURE_TIME, timeToMinutes(r.departureTime.trim()))
                    put(f.C_ORIGIN, r.origin.trim())
                    put(f.C_DEST, r.dest.trim())
                    put(f.C_SOURCE, source)
                    put(f.C_STATUS, currentStatus(db, r.flightNo.trim(), date, r.direction))
                    put(f.C_UPDATED, now)
                    put(f.C_CREATED, now)
                }
                val id = db.insertWithOnConflict(
                    f.TABLE, null, cv, SQLiteDatabase.CONFLICT_REPLACE
                )
                if (id > 0) ok++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return ok
    }

    /** 读取某一天的航班（按占用开始时刻升序） */
    fun loadFlights(context: Context, date: String): List<FlightDraft> {
        val f = GateDbContract.Flight
        val out = mutableListOf<FlightDraft>()
        val c = db(context).rawQuery(
            "SELECT ${f.C_ID},${f.C_FLIGHT_NO},${f.C_DIRECTION},${f.C_AIRCRAFT}," +
                "${f.C_ARRIVAL_TIME},${f.C_DEPARTURE_TIME},${f.C_ORIGIN},${f.C_DEST} " +
                "FROM ${f.TABLE} WHERE ${f.C_FLIGHT_DATE}=? " +
                "ORDER BY COALESCE(${f.C_ARRIVAL_TIME}, 1440) ASC, ${f.C_SCHED_TIME} ASC",
            arrayOf(date)
        )
        c.use {
            while (it.moveToNext()) {
                out += FlightDraft(
                    id = it.getLong(0),
                    flightNo = it.getString(1),
                    direction = it.getString(2),
                    aircraft = it.getString(3),
                    arrivalTime = if (it.isNull(4)) "" else minutesToTime(it.getInt(4)),
                    departureTime = if (it.isNull(5)) "" else minutesToTime(it.getInt(5)),
                    origin = it.getString(6),
                    dest = it.getString(7)
                )
            }
        }
        return out
    }

    /** 删除某一天的航班（重新导入前清底用） */
    fun clearFlights(context: Context, date: String): Int =
        db(context).delete(GateDbContract.Flight.TABLE, "${GateDbContract.Flight.C_FLIGHT_DATE}=?", arrayOf(date))

    /** 更新某条航班的状态（分配模块分配完成后调用：待分配 -> 已分配） */
    fun updateFlightStatus(context: Context, id: Long, status: String): Int {
        val f = GateDbContract.Flight
        val cv = ContentValues().apply {
            put(f.C_STATUS, status)
            put(f.C_UPDATED, timestampNow())
        }
        return db(context).update(f.TABLE, cv, "${f.C_ID}=?", arrayOf(id.toString()))
    }

    /** 读取某一天的**待分配**航班（供分配引擎取数，避免重复分配） */
    fun loadPendingFlights(context: Context, date: String): List<FlightDraft> {
        val f = GateDbContract.Flight
        val out = mutableListOf<FlightDraft>()
        val c = db(context).rawQuery(
            "SELECT ${f.C_ID},${f.C_FLIGHT_NO},${f.C_DIRECTION},${f.C_AIRCRAFT}," +
                "${f.C_ARRIVAL_TIME},${f.C_DEPARTURE_TIME},${f.C_ORIGIN},${f.C_DEST} " +
                "FROM ${f.TABLE} WHERE ${f.C_FLIGHT_DATE}=? AND ${f.C_STATUS}='待分配' " +
                "ORDER BY COALESCE(${f.C_ARRIVAL_TIME}, 1440) ASC",
            arrayOf(date)
        )
        c.use {
            while (it.moveToNext()) {
                out += FlightDraft(
                    id = it.getLong(0),
                    flightNo = it.getString(1),
                    direction = it.getString(2),
                    aircraft = it.getString(3),
                    arrivalTime = if (it.isNull(4)) "" else minutesToTime(it.getInt(4)),
                    departureTime = if (it.isNull(5)) "" else minutesToTime(it.getInt(5)),
                    origin = it.getString(6),
                    dest = it.getString(7)
                )
            }
        }
        return out
    }

    /**
     * 给分配引擎用的**分钟级**待分配航班清单。
     * 直接返回占用窗口的分钟数，引擎不用再解析 HH:mm。
     * 缺少 arrival_time / departure_time 的历史行会被跳过。
     */
    fun loadPendingForEngine(context: Context, date: String): List<FlightOccupy> {
        val f = GateDbContract.Flight
        val out = mutableListOf<FlightOccupy>()
        val c = db(context).rawQuery(
            "SELECT ${f.C_ID},${f.C_FLIGHT_NO},${f.C_DIRECTION},${f.C_AIRCRAFT}," +
                "${f.C_ARRIVAL_TIME},${f.C_DEPARTURE_TIME} " +
                "FROM ${f.TABLE} WHERE ${f.C_FLIGHT_DATE}=? AND ${f.C_STATUS}='待分配' " +
                "AND ${f.C_ARRIVAL_TIME} IS NOT NULL AND ${f.C_DEPARTURE_TIME} IS NOT NULL " +
                "ORDER BY ${f.C_ARRIVAL_TIME} ASC",
            arrayOf(date)
        )
        c.use {
            while (it.moveToNext()) {
                out += FlightOccupy(
                    id = it.getLong(0),
                    flightNo = it.getString(1),
                    date = date,
                    direction = it.getString(2),
                    aircraft = it.getString(3),
                    arrivalMin = it.getInt(4),
                    departureMin = it.getInt(5)
                )
            }
        }
        return out
    }

    /** 取已有航班的状态；不存在则按新航班给「待分配」 */
    private fun currentStatus(db: SQLiteDatabase, flightNo: String, date: String, direction: String): String {
        val f = GateDbContract.Flight
        val c = db.rawQuery(
            "SELECT ${f.C_STATUS} FROM ${f.TABLE} WHERE ${f.C_FLIGHT_NO}=? " +
                "AND ${f.C_FLIGHT_DATE}=? AND ${f.C_DIRECTION}=? LIMIT 1",
            arrayOf(flightNo, date, direction)
        )
        c.use {
            if (it.moveToFirst()) {
                val s = it.getString(0)
                if (!s.isNullOrBlank()) return s
            }
        }
        return "待分配"
    }

    // ---------------- 2. 机位使用记录表 ----------------
    //
    // 注：机位档案不在这个库里，见 model/GateInfo.kt（SharedPreferences + JSON）。

    /** 记录一次机位占用（分配执行 / 航班保障结束时调用） */
    fun addUsage(
        context: Context,
        date: String, gateNo: String, flightNo: String, region: String,
        isBridge: Boolean, needBus: Boolean,
        planStart: String, planEnd: String,
        actualStart: String = "", actualEnd: String = "",
        occupyMinutes: Int = 0, status: String = "正常"
    ): Long {
        val u = GateDbContract.Usage
        val cv = ContentValues().apply {
            put(u.C_GATE_NO, gateNo)
            put(u.C_FLIGHT_NO, flightNo)
            put(u.C_FLIGHT_DATE, date)
            put(u.C_REGION, region)
            put(u.C_IS_BRIDGE, if (isBridge) 1 else 0)
            put(u.C_NEED_BUS, if (needBus) 1 else 0)
            put(u.C_PLAN_START, planStart)
            put(u.C_PLAN_END, planEnd)
            put(u.C_ACTUAL_START, actualStart)
            put(u.C_ACTUAL_END, actualEnd)
            put(u.C_OCCUPY_MIN, occupyMinutes)
            put(u.C_TURNOVER, 1)
            put(u.C_STATUS, status)
            put(u.C_CREATED, timestampNow())
        }
        return db(context).insert(u.TABLE, null, cv)
    }

    // ---------------- 3. 分配结果表 ----------------

    /** 落库一条分配结果 */
    fun addAssignment(
        context: Context,
        date: String, flightNo: String, gateNo: String, aircraft: String, direction: String,
        planStart: String, planEnd: String, isBridge: Boolean,
        assignType: String = "自动分配", operator: String = "系统",
        conflict: Boolean = false, version: Int = 1
    ): Long {
        val a = GateDbContract.Assign
        val cv = ContentValues().apply {
            put(a.C_FLIGHT_NO, flightNo)
            put(a.C_FLIGHT_DATE, date)
            put(a.C_GATE_NO, gateNo)
            put(a.C_AIRCRAFT, aircraft)
            put(a.C_DIRECTION, direction)
            put(a.C_PLAN_START, planStart)
            put(a.C_PLAN_END, planEnd)
            put(a.C_IS_BRIDGE, if (isBridge) 1 else 0)
            put(a.C_ASSIGN_TYPE, assignType)
            put(a.C_ASSIGN_TIME, timestampNow())
            put(a.C_OPERATOR, operator)
            put(a.C_CONFLICT, if (conflict) 1 else 0)
            put(a.C_STATUS, "已分配")
            put(a.C_VERSION, version)
            put(a.C_CREATED, timestampNow())
        }
        return db(context).insert(a.TABLE, null, cv)
    }

    /** 某一天的分配结果条数 */
    fun assignmentCount(context: Context, date: String): Int = scalarInt(
        context,
        "SELECT COUNT(*) FROM ${GateDbContract.Assign.TABLE} WHERE ${GateDbContract.Assign.C_FLIGHT_DATE}=?",
        arrayOf(date)
    )

    // ---------------- 统计：靠桥率 / 平均机位利用率 / 机位区域利用率 ----------------

    /**
     * 某一天的机位运行统计。
     * @param windowMinutes 统计窗口时长，默认全天 24h；若机场只在 06:00-23:00 运行，传 17*60 更贴近实际
     */
    fun dayStat(context: Context, date: String, windowMinutes: Int = 24 * 60): DayUsageStat {
        val u = GateDbContract.Usage
        var flights = 0
        var bridges = 0
        var minutes = 0
        val c = db(context).rawQuery(
            "SELECT COUNT(*), SUM(${u.C_IS_BRIDGE}), SUM(${u.C_OCCUPY_MIN}) " +
                "FROM ${u.TABLE} WHERE ${u.C_FLIGHT_DATE}=? AND ${u.C_STATUS}<>'已取消'",
            arrayOf(date)
        )
        c.use {
            if (it.moveToFirst()) {
                flights = it.getInt(0)
                bridges = it.getInt(1)
                minutes = it.getInt(2)
            }
        }
        // 机位档案不在这个库里，走 GateInfoStore（SharedPreferences）
        val gates = GateInfoStore.load(context).size.coerceAtLeast(1)
        return DayUsageStat(
            flightCount = flights,
            bridgeCount = bridges,
            gateCount = gates,
            occupyMinutes = minutes,
            windowMinutes = windowMinutes,
            bridgeRate = if (flights == 0) 0f else bridges.toFloat() / flights,
            avgUtil = (minutes.toFloat() / (gates * windowMinutes)).coerceIn(0f, 1f),
            turnover = flights.toFloat() / gates
        )
    }

    /**
     * 机位分类利用率：按「廊桥机位 / 远机位」分组统计。
     *
     * 原实现按 t_gate.region（T1 廊桥机位 / T2 远机位 …）分组，
     * 但机位档案已移交机位维护模块且不再有区域字段，这里改为按停机位种类分组。
     */
    fun standTypeStat(context: Context, date: String, windowMinutes: Int = 24 * 60): List<RegionUsage> {
        val u = GateDbContract.Usage
        val gates = GateInfoStore.load(context)
        val gateMap = linkedMapOf(
            "廊桥机位" to gates.count { it.isBridge },
            "远机位" to gates.count { !it.isBridge }
        )
        val c = db(context).rawQuery(
            "SELECT ${u.C_IS_BRIDGE}, COUNT(*), SUM(${u.C_OCCUPY_MIN}) " +
                "FROM ${u.TABLE} WHERE ${u.C_FLIGHT_DATE}=? AND ${u.C_STATUS}<>'已取消' " +
                "GROUP BY ${u.C_IS_BRIDGE}",
            arrayOf(date)
        )
        val out = mutableListOf<RegionUsage>()
        c.use {
            while (it.moveToNext()) {
                val name = if (it.getInt(0) == 1) "廊桥机位" else "远机位"
                val cnt = it.getInt(1)
                val mins = it.getInt(2)
                val g = gateMap[name] ?: 0
                out += RegionUsage(
                    group = name,
                    gateCount = g,
                    flightCount = cnt,
                    occupyMinutes = mins,
                    util = if (g == 0) 0f else (mins.toFloat() / (g * windowMinutes)).coerceIn(0f, 1f)
                )
            }
        }
        // 没有使用记录的分类也要列出来（利用率 0）
        gateMap.keys.filter { k -> out.none { it.group == k } }.forEach { name ->
            out += RegionUsage(name, gateMap[name] ?: 0, 0, 0, 0f)
        }
        return out
    }

    // ---------------- 数据保留策略 ----------------

    /**
     * 清理过期数据。建议在 App 启动 / 每天首次进入时调用一次。
     * @return 各表清理掉的条数（航班, 分配结果, 使用记录）
     */
    fun purgeExpired(context: Context, today: String): Triple<Int, Int, Int> {
        val r = GateDbContract.Retention
        val db = db(context)
        val f = GateDbContract.Flight
        val a = GateDbContract.Assign
        val u = GateDbContract.Usage
        val d1 = db.delete(f.TABLE, "${f.C_FLIGHT_DATE} < date(?, '-${r.FLIGHT_KEEP_DAYS} day')", arrayOf(today))
        val d2 = db.delete(a.TABLE, "${a.C_FLIGHT_DATE} < date(?, '-${r.ASSIGNMENT_KEEP_DAYS} day')", arrayOf(today))
        val d3 = db.delete(u.TABLE, "${u.C_FLIGHT_DATE} < date(?, '-${r.USAGE_KEEP_DAYS} day')", arrayOf(today))
        return Triple(d1, d2, d3)
    }

    /** 表行数一览（调试 / 界面展示用） */
    fun tableSizes(context: Context): Map<String, Int> = linkedMapOf(
        GateDbContract.Flight.TABLE to scalarInt(context, "SELECT COUNT(*) FROM ${GateDbContract.Flight.TABLE}"),
        GateDbContract.Usage.TABLE to scalarInt(context, "SELECT COUNT(*) FROM ${GateDbContract.Usage.TABLE}"),
        GateDbContract.Assign.TABLE to scalarInt(context, "SELECT COUNT(*) FROM ${GateDbContract.Assign.TABLE}")
    )

    // ---------------- 工具 ----------------

    private fun scalarInt(context: Context, sql: String, args: Array<String>? = null): Int = try {
        val c = db(context).rawQuery(sql, args)
        c.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    } catch (e: SQLException) {
        0
    }
}
