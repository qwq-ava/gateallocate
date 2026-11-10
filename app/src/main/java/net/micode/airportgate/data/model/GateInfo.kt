package net.micode.airportgate.data.model

import android.content.Context
import net.micode.airportgate.data.db.GateInfoRepository
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// ========================= 机位信息表数据模型 =========================
//
// 字段口径（2026-11-10 按天津滨海机场真实停机位数据对齐）：
//   gateType        停机位种类 = 真实「功能用途」17 种（CSV function 列），界面下拉选择
//   aircraftClass   可停靠机型 = ICAO 等级 A/B/C/D/E/F（可多选，CSV aircraft_class 列）
//   bridge          内部标记「廊桥 / 远机位」，由 gateType 自动同步，供引擎做「宽体优先廊桥」
//   area/terminal/zone/remark  真实附加信息（东区/西区、T1/T2、机坪分区、备注）
//   minInterval     安全间隔（分钟）、openTime/closeTime 可用时段：无公开数据，属仿真参数
//   status          可用 / 维护 / 停用，引擎只分配「可用」的机位
//
// 持久化：SQLite t_gate（v7 新增上述真实字段），由 data.db.GateInfoRepository 读写。
// 首次启动写入 RealGateData 的 125 条逐编号真实机位；库中仅有旧版 6 条示例数据时
// 一次性替换，不覆盖用户后续自定义编辑。

data class GateInfoRow(
    val id: String = "",
    val code: String = "",
    val bridge: String = GateInfoOptions.BRIDGE,
    val aircraftClass: String = "",
    val minInterval: Int = GateInfoOptions.DEFAULT_INTERVAL,
    val openTime: String = "06:00",
    val closeTime: String = "23:00",
    val status: String = GateInfoOptions.STATUS_AVAILABLE,
    // ---- 真实停机位档案字段（t_gate v7）----
    val gateType: String = GateInfoOptions.DEFAULT_GATE_TYPE,
    val area: String = "",
    val terminal: String = "",
    val zone: String = "",
    val remark: String = ""
) {
    /** 可停靠机型等级列表：「C,D」-> [C, D]（兼容历史遗留的系列名写法） */
    val aircraftClasses: List<String>
        get() = aircraftClass.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }

    /** 是否廊桥机位（引擎据此宽体优先廊桥） */
    val isBridge: Boolean get() = bridge == GateInfoOptions.BRIDGE

    /** 可用时段起止，转为当日分钟数 0–1440（24:00 记为 1440，表示全天开放） */
    val openMin: Int get() = timeToMinutes(openTime) ?: 0
    val closeMin: Int get() = timeToMinutes(closeTime) ?: 1440

    /** 引擎只分配「可用」的机位 */
    val available: Boolean get() = status == GateInfoOptions.STATUS_AVAILABLE

    /** 机位允许的等级（或历史系列名）是否包含给定值 */
    fun accepts(value: String): Boolean = value in aircraftClasses

    /** 可用时段是否容纳给定占用窗口（引擎排机位前的硬约束） */
    fun containsWindow(startMin: Int, endMin: Int): Boolean =
        startMin >= openMin && endMin <= closeMin
}

/** 机位信息表的选项常量（下拉选择范围） */
object GateInfoOptions {
    // 内部廊桥标记：引擎据此做「宽体优先廊桥」，主表不再直接展示这一列
    const val BRIDGE = "廊桥"
    const val REMOTE = "远机位"
    val BRIDGE_TYPES = listOf(BRIDGE, REMOTE)

    /** 停机位种类：CSV function 列去重后的 17 种真实功能用途 */
    val GATE_TYPES: List<String> get() = RealGateData.GATE_TYPES
    const val DEFAULT_GATE_TYPE = "国内航班廊桥机位"

    /** 可停靠机型：ICAO 机型等级 A–F（CSV 实际出现 B/C/D/E/F，A 作为可选等级保留） */
    val AIRCRAFT_CLASSES = listOf("A", "B", "C", "D", "E", "F")

    /** 等级对应的代表机型，仅用于下拉提示，不参与引擎匹配 */
    val GRADE_HINT = mapOf(
        "A" to "小型通用航空器",
        "B" to "支线机／公务机",
        "C" to "A320 / B737",
        "D" to "B757 / B767",
        "E" to "A330 / A350 / B787 / B777",
        "F" to "A380 / B747-8"
    )

    /** 由真实种类自动同步内部廊桥标记，保证引擎判断与界面选择一致 */
    fun bridgeOf(gateType: String): String = if ("廊桥" in gateType) BRIDGE else REMOTE

    // 状态：可用 / 维护 / 停用
    const val STATUS_AVAILABLE = "可用"
    const val STATUS_MAINTENANCE = "维护"
    const val STATUS_DISABLED = "停用"
    val STATUS_TYPES = listOf(STATUS_AVAILABLE, STATUS_MAINTENANCE, STATUS_DISABLED)

    /** 默认安全间隔（分钟）；公开资料无机位级数据，属仿真参数 */
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
    /** 首次启动 / 恢复用的默认数据：天津滨海机场 125 个逐编号真实机位 */
    fun defaultRows(): List<GateInfoRow> = RealGateData.ROWS

    /** 读取全部机位：库空或仍是旧版示例数据时，一次性换成真实 125 条 */
    fun load(context: Context): List<GateInfoRow> {
        val rows = GateInfoRepository.loadAll(context)
        if (rows.isEmpty()) {
            // 首次：先尝试迁移 v3 残留在 SharedPreferences 的数据，否则落真实数据
            val base = migrateFromPrefs(context).ifEmpty { defaultRows() }
            GateInfoRepository.replaceAll(context, base)
            return base
        }
        // 旧的 6 条演示机位（B27/208/C05/C08/D12/T2-14）整体替换为权威数据。
        // 只要用户加过任何一个自定义编号，就保留用户数据不被覆盖。
        if (rows.all { it.code in RealGateData.LEGACY_CODES }) {
            val base = defaultRows()
            GateInfoRepository.replaceAll(context, base)
            return base
        }
        return rows
    }

    /** 保存（机位维护界面「确定」时整表替换，含全部真实字段） */
    fun save(context: Context, rows: List<GateInfoRow>) {
        GateInfoRepository.replaceAll(context, rows)
    }

    /** 手动恢复为 125 条真实机位数据（概览页按钮，界面上需二次确认） */
    fun resetToRealData(context: Context): List<GateInfoRow> {
        val base = defaultRows()
        GateInfoRepository.replaceAll(context, base)
        return base
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
                ),
                gateType = o.optString("gateType", GateInfoOptions.DEFAULT_GATE_TYPE),
                area = o.optString("area", ""),
                terminal = o.optString("terminal", ""),
                zone = o.optString("zone", ""),
                remark = o.optString("remark", "")
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
