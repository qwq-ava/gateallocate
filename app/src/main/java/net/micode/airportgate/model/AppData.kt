package net.micode.airportgate.model

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.graphics.Color
import java.util.Calendar

// ========================= 数据模型 =========================
data class FlightItem(
    val time: String,
    val flightNo: String,
    val from: String,
    val to: String,
    val gate: String,
    val desc: String,
    val status: String // 已分配 / 已完成
)

data class ImportRecord(
    val fileName: String,
    val status: String, // 导入成功 / 部分失败
    val time: String,
    val operator: String,
    val summary: String
)

data class RegionStat(
    val name: String,
    val sub: String,
    val pct: Float,
    val color: Color
)

data class StatsData(
    val avgUtil: String,
    val avgDelta: String,
    val bridgeRate: String,
    val bridgeDetail: String,
    val turnover: String,
    val industryAvg: String,
    val peak: String,
    val peakUtil: String,
    val regions: List<RegionStat>,
    val warning: String
)

// ========================= 甘特图数据 =========================

/** 一条甘特图色块 = 一次机位占用 */
data class GateSlot(
    val gate: String,
    val flightNo: String,
    val startMin: Int,
    val endMin: Int,
    val isBridge: Boolean,
    val finished: Boolean,
    val conflict: Boolean = false
)

/** 同机位时段重叠 → 标记冲突（甘特图标红） */
fun markConflicts(slots: List<GateSlot>): List<GateSlot> {
    val out = slots.toMutableList()
    slots.groupBy { it.gate }.forEach { (_, group) ->
        val sorted = group.sortedBy { it.startMin }
        for (i in 0 until sorted.size - 1) {
            if (sorted[i].endMin > sorted[i + 1].startMin) {
                out[out.indexOf(sorted[i])] = sorted[i].copy(conflict = true)
                out[out.indexOf(sorted[i + 1])] = sorted[i + 1].copy(conflict = true)
            }
        }
    }
    return out
}

/** 分钟数 -> "09:25" */
fun fmtTime(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

/** 当前时刻（当天第几分钟），用于红色指示线与「已完成」判定 */
fun nowMinuteOfDay(): Int {
    val c = Calendar.getInstance()
    return (c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE))
        .coerceIn(0, 24 * 60 - 1)
}

// ---------- 全天航班计划：甘特图与「今日动态」列表共用的唯一数据源 ----------

/** 一条全天航班记录（含机位占用起止时刻） */
data class DayFlight(
    val flightNo: String,
    val gate: String,
    val startMin: Int,
    val endMin: Int,
    val isBridge: Boolean,
    val from: String,
    val to: String,
    val aircraft: String
)

private fun hm(h: Int, m: Int) = h * 60 + m

/** 全天 00:00 - 24:00 的机位分配结果：7 个机位 × 50 个航班时段 */
val DAY_FLIGHTS = listOf(
    // ---- B27（廊桥）----
    DayFlight("CZ3401", "B27", hm(0, 20), hm(2, 10), true, "广州白云", "上海浦东", "A321neo"),
    DayFlight("MU5102", "B27", hm(6, 15), hm(8, 5), true, "昆明长水", "上海浦东", "A330-300"),
    DayFlight("CA1856", "B27", hm(9, 35), hm(11, 20), true, "上海浦东", "北京首都", "A330-300"),
    DayFlight("MU2458", "B27", hm(12, 10), hm(14, 0), true, "上海浦东", "西安咸阳", "A320neo"),
    DayFlight("CZ3892", "B27", hm(15, 20), hm(17, 10), true, "武汉天河", "上海浦东", "B737-800"),
    DayFlight("CA1502", "B27", hm(18, 40), hm(20, 35), true, "上海浦东", "深圳宝安", "A321neo"),
    DayFlight("MU2890", "B27", hm(21, 30), hm(23, 20), true, "厦门高崎", "上海浦东", "A330-200"),

    // ---- B32（廊桥）----
    DayFlight("MU5301", "B32", hm(1, 10), hm(3, 0), true, "上海浦东", "三亚凤凰", "A321neo"),
    DayFlight("CA1202", "B32", hm(6, 50), hm(8, 40), true, "北京首都", "上海浦东", "B787-9"),
    DayFlight("MU5217", "B32", hm(10, 10), hm(11, 50), true, "西安咸阳", "上海浦东", "A350-900"),
    DayFlight("CZ3104", "B32", hm(13, 5), hm(14, 50), true, "上海浦东", "广州白云", "B737-800"),
    DayFlight("MU2186", "B32", hm(16, 0), hm(17, 50), true, "成都天府", "上海浦东", "A320neo"),
    DayFlight("CA1830", "B32", hm(19, 10), hm(21, 0), true, "上海浦东", "青岛胶东", "A321neo"),
    DayFlight("HU7605", "B32", hm(22, 10), hm(23, 55), true, "海口美兰", "上海浦东", "B737 MAX 8"),

    // ---- 208（廊桥）----
    DayFlight("3U8801", "208", hm(2, 30), hm(4, 10), true, "重庆江北", "上海浦东", "A319"),
    DayFlight("CZ6101", "208", hm(7, 20), hm(9, 0), true, "上海浦东", "长沙黄花", "A320neo"),
    DayFlight("MU5137", "208", hm(9, 42), hm(11, 5), true, "上海浦东", "广州白云", "A330-300"),
    DayFlight("CA4192", "208", hm(11, 40), hm(13, 25), true, "天津滨海", "上海浦东", "B737-800"),
    DayFlight("MU2831", "208", hm(14, 40), hm(16, 30), true, "上海浦东", "哈尔滨太平", "A321neo"),
    DayFlight("CZ3421", "208", hm(17, 45), hm(19, 35), true, "深圳宝安", "上海浦东", "A330-200"),
    DayFlight("3U8921", "208", hm(20, 50), hm(22, 40), true, "上海浦东", "重庆江北", "A320neo"),

    // ---- C05（远机位，含一处冲突）----
    DayFlight("MU2701", "C05", hm(0, 45), hm(2, 30), false, "南京禄口", "上海浦东", "A320neo"),
    DayFlight("MU2688", "C05", hm(6, 20), hm(8, 5), false, "上海浦东", "郑州新郑", "B737-800"),
    DayFlight("CZ3582", "C05", hm(9, 0), hm(10, 30), false, "上海浦东", "成都天府", "A321neo"),
    DayFlight("MU2721", "C05", hm(10, 15), hm(11, 45), false, "成都天府", "上海浦东", "A320neo"),
    DayFlight("CA1620", "C05", hm(12, 30), hm(14, 20), false, "上海浦东", "大连周水子", "A319"),
    DayFlight("CZ3586", "C05", hm(15, 10), hm(17, 0), false, "昆明长水", "上海浦东", "B737-800"),
    DayFlight("MU2790", "C05", hm(18, 20), hm(20, 10), false, "上海浦东", "厦门高崎", "A321neo"),
    DayFlight("CZ3590", "C05", hm(21, 15), hm(23, 5), false, "深圳宝安", "上海浦东", "A320neo"),

    // ---- C08（远机位）----
    DayFlight("HU7103", "C08", hm(3, 20), hm(5, 0), false, "上海浦东", "海口美兰", "B787-9"),
    DayFlight("CZ3577", "C08", hm(6, 40), hm(8, 20), false, "青岛胶东", "上海浦东", "A321neo"),
    DayFlight("CZ3588", "C08", hm(9, 50), hm(11, 40), false, "上海浦东", "成都天府", "A350-900"),
    DayFlight("MU5142", "C08", hm(12, 25), hm(14, 10), false, "西安咸阳", "上海浦东", "A320neo"),
    DayFlight("CA1886", "C08", hm(15, 30), hm(17, 20), false, "上海浦东", "武汉天河", "B737-800"),
    DayFlight("CZ3862", "C08", hm(18, 5), hm(19, 55), false, "三亚凤凰", "上海浦东", "A321neo"),
    DayFlight("MU5198", "C08", hm(20, 40), hm(22, 30), false, "上海浦东", "昆明长水", "A330-300"),

    // ---- T2-14（远机位）----
    DayFlight("CZ6011", "T2-14", hm(4, 10), hm(5, 50), false, "上海浦东", "三亚凤凰", "A321neo"),
    DayFlight("3U8891", "T2-14", hm(8, 56), hm(10, 10), false, "上海浦东", "重庆江北", "A320neo"),
    DayFlight("CZ6387", "T2-14", hm(11, 20), hm(13, 10), false, "长沙黄花", "上海浦东", "B737-800"),
    DayFlight("MU5912", "T2-14", hm(14, 15), hm(16, 5), false, "上海浦东", "天津滨海", "A319"),
    DayFlight("3U8887", "T2-14", hm(17, 0), hm(18, 50), false, "重庆江北", "上海浦东", "A320neo"),
    DayFlight("CZ6812", "T2-14", hm(19, 40), hm(21, 30), false, "上海浦东", "深圳宝安", "A321neo"),
    DayFlight("HU7411", "T2-14", hm(22, 20), hm(23, 59), false, "北京首都", "上海浦东", "B787-9"),

    // ---- D12（远机位）----
    DayFlight("3U8701", "D12", hm(5, 5), hm(6, 45), false, "上海浦东", "重庆江北", "A319"),
    DayFlight("HU7281", "D12", hm(8, 43), hm(10, 0), false, "上海浦东", "昆明长水", "B737-800"),
    DayFlight("CZ3428", "D12", hm(10, 50), hm(12, 35), false, "南京禄口", "上海浦东", "A320neo"),
    DayFlight("MU2314", "D12", hm(13, 40), hm(15, 30), false, "上海浦东", "西安咸阳", "A321neo"),
    DayFlight("CA1722", "D12", hm(16, 20), hm(18, 10), false, "哈尔滨太平", "上海浦东", "A330-200"),
    DayFlight("HU7395", "D12", hm(19, 0), hm(20, 50), false, "上海浦东", "长沙黄花", "B737-800"),
    DayFlight("3U8755", "D12", hm(21, 40), hm(23, 30), false, "重庆江北", "上海浦东", "A320neo")
)

/** 全天计划 -> 甘特图色块 */
fun dayFlightToSlot(f: DayFlight, now: Int) = GateSlot(
    gate = f.gate,
    flightNo = f.flightNo,
    startMin = f.startMin,
    endMin = f.endMin,
    isBridge = f.isBridge,
    finished = f.endMin <= now
)

/** 全天计划 -> 「今日动态」列表项 */
fun dayFlightToItem(f: DayFlight, now: Int) = FlightItem(
    time = fmtTime(f.startMin),
    flightNo = f.flightNo,
    from = f.from,
    to = f.to,
    gate = f.gate,
    desc = if (f.isBridge) "廊桥 · 机型 ${f.aircraft}"
    else "远机位 · 摆渡车 ${8 + (f.startMin / 60) % 8} 分钟",
    status = if (f.endMin <= now) "已完成" else "已分配"
)

/** 从 Context 向上查找宿主 Activity（用于切换屏幕方向） */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
