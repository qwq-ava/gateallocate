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

// ---------- 全天航班计划：甘特图与「今日动态」列表共用的视图模型 ----------
//
// 注意：真实的航班占用数据来自数据库 t_flight（经 GateRepository.loadFlights 读取），
// 不再硬编码在此处；本文件只保留「一天内某航班在某机位的占用」的展示模型 DayFlight
// 及其与甘特图色块 / 列表项的转换函数。

/** 一条全天航班记录（含机位占用起止时刻），由分配引擎输出后填充 */
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
