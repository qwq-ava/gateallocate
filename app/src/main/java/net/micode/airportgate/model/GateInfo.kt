package net.micode.airportgate.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// ========================= 机位信息表数据模型 =========================

/**
 * 机位信息表的一行。
 * - code          机位编号（可手填）
 * - standType     停机位种类：近机位 / 远机位
 * - aircraftType  可停靠飞机类型：C类 / D类 / E类 / F类
 * - status        状态：开放 / 关闭 / 不可用
 */
data class GateInfoRow(
    val id: String,
    val code: String,
    val standType: String,
    val aircraftType: String,
    val status: String
)

/** 机位信息表的选项常量（下拉选择范围） */
object GateInfoOptions {
    val STAND_TYPES = listOf("近机位", "远机位")
    val AIRCRAFT_TYPES = listOf("C类", "D类", "E类", "F类")
    val STATUS_TYPES = listOf("开放", "关闭", "不可用")
}

/**
 * 机位信息表的持久化：用 SharedPreferences 存为 JSON，
 * 无需引入额外依赖，应用重启后依然保留。
 */
object GateInfoStore {
    private const val PREFS = "gate_info_prefs"
    private const val KEY = "gate_rows"

    /** 首次启动的默认示例数据（覆盖近/远机位、各类机型与状态） */
    fun defaultRows(): List<GateInfoRow> = listOf(
        GateInfoRow("g1", "B27", "近机位", "C类", "开放"),
        GateInfoRow("g2", "208", "近机位", "E类", "开放"),
        GateInfoRow("g3", "C05", "远机位", "D类", "开放"),
        GateInfoRow("g4", "C08", "远机位", "C类", "关闭"),
        GateInfoRow("g5", "D12", "远机位", "F类", "不可用"),
        GateInfoRow("g6", "T2-14", "远机位", "D类", "开放")
    )

    fun load(context: Context): List<GateInfoRow> {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY, null)
        if (raw.isNullOrEmpty()) return defaultRows()
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<GateInfoRow>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    GateInfoRow(
                        id = o.getString("id"),
                        code = o.optString("code", ""),
                        standType = o.optString("standType", GateInfoOptions.STAND_TYPES[0]),
                        aircraftType = o.optString("aircraftType", GateInfoOptions.AIRCRAFT_TYPES[0]),
                        status = o.optString("status", GateInfoOptions.STATUS_TYPES[0])
                    )
                )
            }
            out.ifEmpty { defaultRows() }
        } catch (e: Exception) {
            defaultRows()
        }
    }

    fun save(context: Context, rows: List<GateInfoRow>) {
        val arr = JSONArray()
        rows.forEach {
            arr.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("code", it.code)
                    put("standType", it.standType)
                    put("aircraftType", it.aircraftType)
                    put("status", it.status)
                }
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, arr.toString())
            .apply()
    }
}
