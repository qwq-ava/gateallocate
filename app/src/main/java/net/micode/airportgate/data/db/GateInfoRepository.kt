package net.micode.airportgate.data.db

import android.content.ContentValues
import android.content.Context
import net.micode.airportgate.model.GateInfoOptions
import net.micode.airportgate.model.GateInfoRow

// ========================= 机位信息表（t_gate）数据访问 =========================
//
// 机位档案的持久化层。v4 起机位信息回归 SQLite（此前一度用 SharedPreferences），
// 对外提供 loadAll / replaceAll，供 model.GateInfo.kt 的 GateInfoStore 调用。
// 字段口径对齐 GateInfoRow：row_id 为行唯一标识、code 为机位编号。

object GateInfoRepository {

    private fun db(context: Context) =
        GateDbHelper.get(context).writableDatabase

    /** t_gate 行数（判断是否需要写入默认/迁移数据） */
    fun count(context: Context): Int = try {
        val c = db(context).rawQuery("SELECT COUNT(*) FROM ${GateDbContract.Gate.TABLE}", null)
        c.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    } catch (e: Exception) {
        0
    }

    /** 读取全部机位（按编号升序） */
    fun loadAll(context: Context): List<GateInfoRow> {
        val g = GateDbContract.Gate
        val out = mutableListOf<GateInfoRow>()
        val c = db(context).rawQuery(
            "SELECT ${g.C_ROW_ID},${g.C_CODE},${g.C_BRIDGE},${g.C_AIRCRAFT_CLASS}," +
                "${g.C_MIN_INTERVAL},${g.C_OPEN_TIME},${g.C_CLOSE_TIME},${g.C_STATUS} " +
                "FROM ${g.TABLE} ORDER BY ${g.C_CODE} ASC",
            null
        )
        c.use {
            while (it.moveToNext()) {
                out += GateInfoRow(
                    id = it.getString(0),
                    code = it.getString(1),
                    bridge = it.getString(2),
                    aircraftClass = it.getString(3),
                    minInterval = it.getInt(4),
                    openTime = it.getString(5),
                    closeTime = it.getString(6),
                    status = it.getString(7)
                )
            }
        }
        return out
    }

    /**
     * 整表替换：机位维护界面保存时传入完整列表，先清空再插入。
     * 采用事务，保证「导入中途失败」不会留半截数据。
     */
    fun replaceAll(context: Context, rows: List<GateInfoRow>) {
        val g = GateDbContract.Gate
        val database = db(context)
        database.beginTransaction()
        try {
            database.delete(g.TABLE, null, null)
            rows.forEach { r ->
                val cv = ContentValues().apply {
                    put(g.C_ROW_ID, r.id.ifBlank { java.util.UUID.randomUUID().toString() })
                    put(g.C_CODE, r.code.trim())
                    put(g.C_BRIDGE, r.bridge)
                    put(g.C_AIRCRAFT_CLASS, r.aircraftClass)
                    put(g.C_MIN_INTERVAL, r.minInterval)
                    put(g.C_OPEN_TIME, r.openTime)
                    put(g.C_CLOSE_TIME, r.closeTime)
                    put(g.C_STATUS, r.status)
                }
                database.insert(g.TABLE, null, cv)
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }
}
