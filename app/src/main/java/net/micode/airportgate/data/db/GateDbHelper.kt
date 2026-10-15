package net.micode.airportgate.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
// ========================= 数据库建表 =========================
//
// 共 3 张业务表：
//   1. t_flight      航班信息表（导入 / 手工录入的原始航班计划）
//   2. t_gate_usage  机位使用记录表（一次占用一条，统计靠桥率 / 利用率的数据源）
//   3. t_assignment  机位分配结果表（分配方案的落库结果，可回溯）
//
// 机位档案不在库里：由机位维护模块用 SharedPreferences 存 JSON（model/GateInfo.kt），
// 原 t_gate 表自 v3 起 DROP，避免与机位维护模块重复建表 / 预置数据。
//
// 说明：为保证课程设计的可读性与可移植性，这里直接使用 Android 自带的
// android.database.sqlite（零第三方依赖），后续若要换成 Room，
// 只需把 Entity/DAO 映射到本文件的字段名即可，表结构不用动。

object GateDbContract {

    const val DB_NAME = "airport_gate.db"
    const val DB_VERSION = 3   // v3：移除 t_gate（机位档案改由机位维护模块用 SharedPreferences 维护）

    /** 数据保留策略（天）；-1 表示永久保留 */
    object Retention {
        const val FLIGHT_KEEP_DAYS = 90      // 航班信息：热数据 7 天，历史保留 90 天后归档
        const val ASSIGNMENT_KEEP_DAYS = 180 // 分配结果：与航班同源，留 180 天便于回溯
        const val USAGE_KEEP_DAYS = 400      // 使用记录：≥13 个月，支撑同比 / 环比
        const val HOT_DAYS = 7               // 航班「热数据」窗口（今天 + 明天 + 未来 7 天）
    }

    // ---------------- 1. 航班信息表 ----------------
    object Flight {
        const val TABLE = "t_flight"
        const val C_ID = "_id"
        const val C_FLIGHT_NO = "flight_no"         // 航班号 MU5102
        const val C_FLIGHT_DATE = "flight_date"     // 航班日期 yyyy-MM-dd
        const val C_DIRECTION = "direction"         // 进出港 进港 / 出港
        const val C_AIRLINE = "airline"             // 航空公司
        const val C_AIRCRAFT = "aircraft_type"      // 机型 A320neo
        const val C_TAIL_NO = "tail_no"             // 机号 / 机尾号
        const val C_SCHED_TIME = "sched_time"       // 计划起降时间 HH:mm（展示/兜底，派生自进出港）
        const val C_ACTUAL_TIME = "actual_time"     // 实际起降时间 HH:mm（后续回填）
        const val C_ARRIVAL_TIME = "arrival_time"   // 机位占用开始 —— INT 当日分钟数 0–1439（分配以此为准）
        const val C_DEPARTURE_TIME = "departure_time" // 机位占用结束 —— INT 当日分钟数 0–1439（分配以此为准）
        const val C_ORIGIN = "origin"               // 始发机场
        const val C_DEST = "dest"                   // 目的机场
        const val C_GATE_NO = "gate_no"             // 分配到的机位（分配后回写）
        const val C_IS_BRIDGE = "is_bridge"         // 是否靠桥 0/1
        const val C_STATUS = "status"               // 待分配 / 已分配 / 已取消 / 已完成（引擎只拉「待分配」）
        const val C_SOURCE = "source"               // 数据来源 手工录入 / Excel导入 / 系统同步
        const val C_REMARK = "remark"               // 备注
        const val C_CREATED = "created_at"
        const val C_UPDATED = "updated_at"
    }

    // ---------------- 2. 机位使用记录表 ----------------
    //
    // 注：原「机位信息表 t_gate」自 v3 起移除（onUpgrade 中 DROP TABLE）。
    // 机位档案由机位维护模块负责，用 SharedPreferences 存 JSON（见 model/GateInfo.kt），
    // 不走 SQLite；引擎取机位请走 GateInfoStore.loadAvailableForEngine()。
    object Usage {
        const val TABLE = "t_gate_usage"
        const val C_ID = "_id"
        const val C_GATE_NO = "gate_no"             // 机位编号
        const val C_FLIGHT_NO = "flight_no"         // 航班号
        const val C_FLIGHT_DATE = "flight_date"     // 航班日期
        const val C_REGION = "region"               // 所属区域（冗余，便于分组统计）
        const val C_IS_BRIDGE = "is_bridge"         // 本次是否靠桥 0/1
        const val C_NEED_BUS = "need_bus"           // 本次是否用摆渡车 0/1
        const val C_PLAN_START = "plan_start"       // 计划占用开始 HH:mm
        const val C_PLAN_END = "plan_end"           // 计划占用结束 HH:mm
        const val C_ACTUAL_START = "actual_start"   // 实际上轮挡 HH:mm
        const val C_ACTUAL_END = "actual_end"       // 实际撤轮挡 HH:mm
        const val C_OCCUPY_MIN = "occupy_minutes"   // 实际占用时长（分钟）——利用率分子
        const val C_TURNOVER = "turnover_flag"      // 是否计入周转 0/1
        const val C_STATUS = "status"               // 正常 / 冲突 / 已取消
        const val C_CREATED = "created_at"
    }

    // ---------------- 3. 分配结果表 ----------------
    object Assign {
        const val TABLE = "t_assignment"
        const val C_ID = "_id"
        const val C_FLIGHT_NO = "flight_no"
        const val C_FLIGHT_DATE = "flight_date"
        const val C_GATE_NO = "gate_no"
        const val C_AIRCRAFT = "aircraft_type"
        const val C_DIRECTION = "direction"
        const val C_PLAN_START = "plan_start"
        const val C_PLAN_END = "plan_end"
        const val C_IS_BRIDGE = "is_bridge"
        const val C_ASSIGN_TYPE = "assign_type"     // 自动分配 / 手动改派
        const val C_ASSIGN_TIME = "assign_time"
        const val C_OPERATOR = "operator"           // 操作人
        const val C_CONFLICT = "conflict_flag"      // 是否冲突 0/1
        const val C_STATUS = "status"               // 已分配 / 已执行 / 已取消
        const val C_VERSION = "version"             // 版本号，改派时 +1
        const val C_REMARK = "remark"
        const val C_CREATED = "created_at"
    }
}

class GateDbHelper private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, GateDbContract.DB_NAME, null, GateDbContract.DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        val f = GateDbContract.Flight
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${f.TABLE} (
                ${f.C_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${f.C_FLIGHT_NO} TEXT NOT NULL,
                ${f.C_FLIGHT_DATE} TEXT NOT NULL,
                ${f.C_DIRECTION} TEXT NOT NULL,
                ${f.C_AIRLINE} TEXT,
                ${f.C_AIRCRAFT} TEXT NOT NULL,
                ${f.C_TAIL_NO} TEXT,
                ${f.C_SCHED_TIME} TEXT NOT NULL,
                ${f.C_ACTUAL_TIME} TEXT,
                ${f.C_ARRIVAL_TIME} INTEGER,
                ${f.C_DEPARTURE_TIME} INTEGER,
                ${f.C_ORIGIN} TEXT NOT NULL,
                ${f.C_DEST} TEXT NOT NULL,
                ${f.C_GATE_NO} TEXT,
                ${f.C_IS_BRIDGE} INTEGER DEFAULT 0,
                ${f.C_STATUS} TEXT DEFAULT '待分配',
                ${f.C_SOURCE} TEXT DEFAULT '手工录入',
                ${f.C_REMARK} TEXT,
                ${f.C_CREATED} TEXT,
                ${f.C_UPDATED} TEXT,
                UNIQUE(${f.C_FLIGHT_NO}, ${f.C_FLIGHT_DATE}, ${f.C_DIRECTION})
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_flight_date ON ${f.TABLE}(${f.C_FLIGHT_DATE})")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_flight_status ON ${f.TABLE}(${f.C_STATUS})")
        // 引擎按「日期 + 待分配」取航班，复合索引避免回表扫描
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_flight_date_status ON ${f.TABLE}(${f.C_FLIGHT_DATE},${f.C_STATUS})"
        )

        val u = GateDbContract.Usage
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${u.TABLE} (
                ${u.C_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${u.C_GATE_NO} TEXT NOT NULL,
                ${u.C_FLIGHT_NO} TEXT NOT NULL,
                ${u.C_FLIGHT_DATE} TEXT NOT NULL,
                ${u.C_REGION} TEXT NOT NULL,
                ${u.C_IS_BRIDGE} INTEGER DEFAULT 0,
                ${u.C_NEED_BUS} INTEGER DEFAULT 0,
                ${u.C_PLAN_START} TEXT,
                ${u.C_PLAN_END} TEXT,
                ${u.C_ACTUAL_START} TEXT,
                ${u.C_ACTUAL_END} TEXT,
                ${u.C_OCCUPY_MIN} INTEGER DEFAULT 0,
                ${u.C_TURNOVER} INTEGER DEFAULT 1,
                ${u.C_STATUS} TEXT DEFAULT '正常',
                ${u.C_CREATED} TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_usage_date ON ${u.TABLE}(${u.C_FLIGHT_DATE})")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_usage_region ON ${u.TABLE}(${u.C_REGION})")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_usage_gate ON ${u.TABLE}(${u.C_GATE_NO})")

        val a = GateDbContract.Assign
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ${a.TABLE} (
                ${a.C_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${a.C_FLIGHT_NO} TEXT NOT NULL,
                ${a.C_FLIGHT_DATE} TEXT NOT NULL,
                ${a.C_GATE_NO} TEXT NOT NULL,
                ${a.C_AIRCRAFT} TEXT,
                ${a.C_DIRECTION} TEXT,
                ${a.C_PLAN_START} TEXT,
                ${a.C_PLAN_END} TEXT,
                ${a.C_IS_BRIDGE} INTEGER DEFAULT 0,
                ${a.C_ASSIGN_TYPE} TEXT DEFAULT '自动分配',
                ${a.C_ASSIGN_TIME} TEXT,
                ${a.C_OPERATOR} TEXT,
                ${a.C_CONFLICT} INTEGER DEFAULT 0,
                ${a.C_STATUS} TEXT DEFAULT '已分配',
                ${a.C_VERSION} INTEGER DEFAULT 1,
                ${a.C_REMARK} TEXT,
                ${a.C_CREATED} TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_assign_date ON ${a.TABLE}(${a.C_FLIGHT_DATE})")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_assign_gate ON ${a.TABLE}(${a.C_GATE_NO})")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 按版本逐级迁移，不丢已有数据
        val f = GateDbContract.Flight

        if (oldVersion < 2) {
            // v2：t_flight 补机位占用窗口（INT 分钟数）
            if (!columnExists(db, f.TABLE, f.C_ARRIVAL_TIME)) {
                db.execSQL("ALTER TABLE ${f.TABLE} ADD COLUMN ${f.C_ARRIVAL_TIME} INTEGER")
            }
            if (!columnExists(db, f.TABLE, f.C_DEPARTURE_TIME)) {
                db.execSQL("ALTER TABLE ${f.TABLE} ADD COLUMN ${f.C_DEPARTURE_TIME} INTEGER")
            }
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_flight_date_status ON ${f.TABLE}(${f.C_FLIGHT_DATE},${f.C_STATUS})"
            )
            // 历史行没有占用窗口，用 sched_time 兜底填一份，避免引擎拿到 NULL
            db.execSQL(
                "UPDATE ${f.TABLE} SET ${f.C_ARRIVAL_TIME} = " +
                    "(CAST(substr(${f.C_SCHED_TIME},1,2) AS INTEGER) * 60 + " +
                    "CAST(substr(${f.C_SCHED_TIME},4,2) AS INTEGER)) " +
                    "WHERE ${f.C_ARRIVAL_TIME} IS NULL AND ${f.C_SCHED_TIME} IS NOT NULL"
            )
            db.execSQL(
                "UPDATE ${f.TABLE} SET ${f.C_DEPARTURE_TIME} = ${f.C_ARRIVAL_TIME} + 45 " +
                    "WHERE ${f.C_DEPARTURE_TIME} IS NULL AND ${f.C_ARRIVAL_TIME} IS NOT NULL"
            )
        }

        if (oldVersion < 3) {
            // v3：机位档案移交机位维护模块（SharedPreferences + JSON），库里的 t_gate 不再有人读。
            // 表名写字面量是因为 GateDbContract.Gate 已随本次改动移除。
            // 机位维护模块的数据不在 SQLite 里，DROP 不会影响它。
            db.execSQL("DROP TABLE IF EXISTS t_gate")
        }
    }

    /** 判断表里是否已有某列（ALTER TABLE 前必须查，重复 ADD COLUMN 会报错） */
    private fun columnExists(db: SQLiteDatabase, table: String, column: String): Boolean {
        db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            while (c.moveToNext()) {
                if (c.getString(c.getColumnIndexOrThrow("name")) == column) return true
            }
        }
        return false
    }

    companion object {
        @Volatile
        private var instance: GateDbHelper? = null

        fun get(context: Context): GateDbHelper =
            instance ?: synchronized(this) {
                instance ?: GateDbHelper(context).also { instance = it }
            }
    }
}
