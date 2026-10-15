package net.micode.airportgate.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// ========================= 机位信息表数据模型 =========================
//
// 字段口径按组长给分配引擎的约定对齐（2026-10-15）：
//   bridge          明确枚举「廊桥 / 远机位」，引擎据此做「宽体优先廊桥」
//   aircraftClass   系列名列表（A330,A320,B737），引擎做包含匹配，不比翼展/长度数值
//   minInterval     安全间隔（分钟），由引擎按机位读取，取代写死的全局 15 分钟
//   openTime/closeTime  可用时段，航班占用窗口必须落在其中
//   status          可用 / 维护 / 停用，引擎只分配「可用」的机位
//
// 持久化：SharedPreferences 存 JSON，无需额外依赖，重启后保留。

data class GateInfoRow(
    val id: String = "",
    val code: String = "",
    val bridge: String = GateInfoOptions.BRIDGE,
    val aircraftClass: String = "",
    val minInterval: Int = GateInfoOptions.DEFAULT_INTERVAL,
    val openTime: String = "06:00",
    val closeTime: String = "23:00",
    val status: String = GateInfoOptions.STATUS_AVAILABLE
) {
    /** 系列名列表：「A330,A320,B737」-> [A330, A320, B737] */
    val aircraftClasses: List<String>
        get() = aircraftClass.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }

    /** 是否廊桥机位（引擎据此宽体优先廊桥） */
    val isBridge: Boolean get() = bridge == GateInfoOptions.BRIDGE

    /** 可用时段起止，转为当日分钟数 0–1439，引擎直接算区间 */
    val openMin: Int get() = timeToMinutes(openTime) ?: 0
    val closeMin: Int get() = timeToMinutes(closeTime) ?: 1439

    /** 引擎只分配「可用」的机位 */
    val available: Boolean get() = status == GateInfoOptions.STATUS_AVAILABLE

    /** 该机位能否停靠某机型系列（包含匹配，不做数值比较） */
    fun accepts(series: String): Boolean = series in aircraftClasses

    /** 可用时段是否容纳给定占用窗口（引擎排机位前的硬约束） */
    fun containsWindow(startMin: Int, endMin: Int): Boolean =
        startMin >= openMin && endMin <= closeMin
}

/** 机位信息表的选项常量（下拉选择范围） */
object GateInfoOptions {
    // 停机位种类：用明确枚举而非布尔，避免 true/false 语义不明
    const val BRIDGE = "廊桥"
    const val REMOTE = "远机位"
    val BRIDGE_TYPES = listOf(BRIDGE, REMOTE)

    /** 可停靠机型系列名（与航班录入界面的机型下拉保持一致） */
    val AIRCRAFT_SERIES = listOf(
        "A319", "A320", "A320neo", "A321", "A321neo",
        "A330-200", "A330-300", "A350-900",
        "B737-800", "B737 MAX 8", "B787-9", "B777-300ER"
    )

    // 状态：可用 / 维护 / 停用
    const val STATUS_AVAILABLE = "可用"
    const val STATUS_MAINTENANCE = "维护"
    const val STATUS_DISABLED = "停用"
    val STATUS_TYPES = listOf(STATUS_AVAILABLE, STATUS_MAINTENANCE, STATUS_DISABLED)

    /** 默认安全间隔（分钟）；实际值由各机位自行维护 */
    const val DEFAULT_INTERVAL = 20
}

/** 交给分配引擎的最小视图：字段都是引擎可直接参与计算的形态 */
data class GateForEngine(
    val code: String,
    val isBridge: Boolean,
    val aircraftClasses: List<String>,
    val minInterval: Int,
    val openMin: Int,
    val closeMin: Int,
    val status: String
)

private fun GateInfoRow.toEngine() = GateForEngine(
    code = code,
    isBridge = isBridge,
    aircraftClasses = aircraftClasses,
    minInterval = minInterval,
    openMin = openMin,
    closeMin = closeMin,
    status = status
)

/**
 * 机位信息表的持久化：用 SharedPreferences 存为 JSON，
 * 无需引入额外依赖，应用重启后依然保留。
 */
object GateInfoStore {
    private const val PREFS = "gate_info_prefs"
    private const val KEY = "gate_rows"

    /** 首次启动的默认示例数据（覆盖廊桥/远机位、各机型系列、各状态、不同安全间隔） */
    fun defaultRows(): List<GateInfoRow> = listOf(
        GateInfoRow("g1", "B27", GateInfoOptions.BRIDGE, "A320,A321,A320neo", 20, "06:00", "23:00", GateInfoOptions.STATUS_AVAILABLE),
        GateInfoRow("g2", "208", GateInfoOptions.BRIDGE, "A330-200,A330-300,A350-900,B787-9,B777-300ER", 25, "06:00", "23:00", GateInfoOptions.STATUS_AVAILABLE),
        GateInfoRow("g3", "C05", GateInfoOptions.REMOTE, "A321,A320neo,B737 MAX 8", 15, "06:00", "23:00", GateInfoOptions.STATUS_AVAILABLE),
        GateInfoRow("g4", "C08", GateInfoOptions.REMOTE, "A319,A320,B737-800", 15, "06:00", "23:00", GateInfoOptions.STATUS_MAINTENANCE),
        GateInfoRow("g5", "D12", GateInfoOptions.REMOTE, "A350-900,B777-300ER,B787-9", 30, "06:00", "23:00", GateInfoOptions.STATUS_DISABLED),
        GateInfoRow("g6", "T2-14", GateInfoOptions.REMOTE, "A321,A320neo,B737-800", 20, "06:00", "23:00", GateInfoOptions.STATUS_AVAILABLE)
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
                        id = o.optString("id", ""),
                        code = o.optString("code", ""),
                        // 旧数据兼容：旧字段名 standType，旧值「近机位」
                        bridge = migrateBridge(
                            o.optString("bridge", o.optString("standType", GateInfoOptions.BRIDGE))
                        ),
                        aircraftClass = o.optString("aircraftClass", ""),
                        minInterval = o.optInt("minInterval", GateInfoOptions.DEFAULT_INTERVAL),
                        openTime = o.optString("openTime", "06:00"),
                        closeTime = o.optString("closeTime", "23:00"),
                        // 旧数据兼容：开放 -> 可用，关闭 -> 维护，不可用 -> 停用
                        status = migrateStatus(
                            o.optString("status", GateInfoOptions.STATUS_AVAILABLE)
                        )
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
                    put("bridge", it.bridge)
                    put("aircraftClass", it.aircraftClass)
                    put("minInterval", it.minInterval)
                    put("openTime", it.openTime)
                    put("closeTime", it.closeTime)
                    put("status", it.status)
                }
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, arr.toString())
            .apply()
    }

    // ---------------- 给分配引擎的入口 ----------------

    /** 引擎只消费「可用」机位，占用窗口已转为分钟数 */
    fun loadAvailableForEngine(context: Context): List<GateForEngine> =
        load(context).filter { it.available }.map { it.toEngine() }

    /** 全部机位（含维护 / 停用），供引擎排查或人工核对 */
    fun loadAllForEngine(context: Context): List<GateForEngine> =
        load(context).map { it.toEngine() }

    /** 旧版字段迁移：近机位 -> 廊桥 */
    private fun migrateBridge(raw: String): String = when (raw) {
        "近机位" -> GateInfoOptions.BRIDGE
        "远机位" -> GateInfoOptions.REMOTE
        else -> raw
    }

    /** 旧版字段迁移：开放 / 关闭 / 不可用 -> 可用 / 维护 / 停用 */
    private fun migrateStatus(raw: String): String = when (raw) {
        "开放" -> GateInfoOptions.STATUS_AVAILABLE
        "关闭" -> GateInfoOptions.STATUS_MAINTENANCE
        "不可用" -> GateInfoOptions.STATUS_DISABLED
        else -> raw
    }
}
