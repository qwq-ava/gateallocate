package net.micode.airportgate.data.model

import android.content.Context
import net.micode.airportgate.data.db.GateInfoRepository
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// ========================= 机位信息表数据模型 =========================
//
// 字段口径按组长给分配引擎的约定对齐（2026-10-15）：
//   bridge          明确枚举「廊桥 / 远机位」，引擎据此做「宽体优先廊桥」
//   aircraftClass   系列名列表（A330,A320,B737），引擎做包含匹配，不比翼展/长度数值
//   minInterval     安全间隔（分钟），由引擎按机位读取，取代写死的全局 15 分钟
//   openTime/closeTime  可用时段，航班占用窗口必须落在其中
//   status          可用 / 维护 / 停用，引擎只分配「可用」的机位
//
// 持久化（v4 起）：SQLite t_gate，由 data.db.GateInfoRepository 负责读写；
// 首次无数据时写入默认示例，并把 v3 残留在 SharedPreferences 的旧数据迁移过来。

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
 * 机位信息表的持久化入口：v4 起读写 SQLite t_gate（见 data.db.GateInfoRepository）。
 * 对外接口（load / save / loadAvailableForEngine / loadAllForEngine）保持不变，
 * 因此机位维护界面、统计界面、分配引擎都不需要改动。
 */
object GateInfoStore {
    /** 首次启动的默认示例数据（覆盖廊桥/远机位、各机型系列、各状态、不同安全间隔） */
    fun defaultRows(): List<GateInfoRow> = listOf(
        GateInfoRow("g1", "B27", GateInfoOptions.BRIDGE, "A320,A321,A320neo", 20, "06:00", "23:00", GateInfoOptions.STATUS_AVAILABLE),
        GateInfoRow("g2", "208", GateInfoOptions.BRIDGE, "A330-200,A330-300,A350-900,B787-9,B777-300ER", 25, "06:00", "23:00", GateInfoOptions.STATUS_AVAILABLE),
        GateInfoRow("g3", "C05", GateInfoOptions.REMOTE, "A321,A320neo,B737 MAX 8", 15, "06:00", "23:00", GateInfoOptions.STATUS_AVAILABLE),
        GateInfoRow("g4", "C08", GateInfoOptions.REMOTE, "A319,A320,B737-800", 15, "06:00", "23:00", GateInfoOptions.STATUS_MAINTENANCE),
        GateInfoRow("g5", "D12", GateInfoOptions.REMOTE, "A350-900,B777-300ER,B787-9", 30, "06:00", "23:00", GateInfoOptions.STATUS_DISABLED),
        GateInfoRow("g6", "T2-14", GateInfoOptions.REMOTE, "A321,A320neo,B737-800", 20, "06:00", "23:00", GateInfoOptions.STATUS_AVAILABLE)
    )

    /** 读取全部机位：库里有数据直接返回；库空则迁移旧 SharedPreferences 或写入默认数据 */
    fun load(context: Context): List<GateInfoRow> {
        val rows = GateInfoRepository.loadAll(context)
        if (rows.isNotEmpty()) return rows
        // 首次：先尝试迁移 v3 残留在 SharedPreferences 的数据，否则落默认数据
        val base = migrateFromPrefs(context).ifEmpty { defaultRows() }
        GateInfoRepository.replaceAll(context, base)
        return base
    }

    /** 保存（机位维护界面「确定」时整表替换） */
    fun save(context: Context, rows: List<GateInfoRow>) {
        GateInfoRepository.replaceAll(context, rows)
    }

    // ---------------- 给分配引擎的入口 ----------------

    /** 引擎只消费「可用」机位，占用窗口已转为分钟数 */
    fun loadAvailableForEngine(context: Context): List<GateForEngine> =
        load(context).filter { it.available }.map { it.toEngine() }

    /** 全部机位（含维护 / 停用），供引擎排查或人工核对 */
    fun loadAllForEngine(context: Context): List<GateForEngine> =
        load(context).map { it.toEngine() }

    // ---------------- 数据迁移（v3 -> v4） ----------------

    /**
     * 把 v3 写在 SharedPreferences 的机位 JSON 迁移到 SQLite 一次。
     * 之后 GateInfoStore 不再读取 SharedPreferences。
     */
    private fun migrateFromPrefs(context: Context): List<GateInfoRow> {
        return try {
            val sp = context.getSharedPreferences("gate_info_prefs", Context.MODE_PRIVATE)
            val raw = sp.getString("gate_rows", null)
            if (raw.isNullOrEmpty()) return emptyList()
            val arr = JSONArray(raw)
        val out = mutableListOf<GateInfoRow>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += GateInfoRow(
                id = o.optString("id", UUID.randomUUID().toString()),
                code = o.optString("code", ""),
                bridge = migrateBridge(
                    o.optString("bridge", o.optString("standType", GateInfoOptions.BRIDGE))
                ),
                aircraftClass = o.optString("aircraftClass", ""),
                minInterval = o.optInt("minInterval", GateInfoOptions.DEFAULT_INTERVAL),
                openTime = o.optString("openTime", "06:00"),
                closeTime = o.optString("closeTime", "23:00"),
                status = migrateStatus(
                    o.optString("status", GateInfoOptions.STATUS_AVAILABLE)
                )
            )
        }
        out
    } catch (e: Exception) {
        emptyList()
    }
}

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
