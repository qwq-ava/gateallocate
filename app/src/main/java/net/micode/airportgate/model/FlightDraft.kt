package net.micode.airportgate.model

import java.util.Calendar

// ========================= 航班录入行模型 =========================

/**
 * 「数据导入 / 航班信息更改」卡片里的**一行**。
 *
 * 对应数据库 `t_flight` 中由用户手工填写的那几个字段；
 * **航班日期不在卡片里出现**，由所在页签（今天航班信息 / 明天航班信息）自动决定，
 * 避免用户重复录入，也避免同一天填出两个不同日期。
 *
 * ---- 时间口径（2026-10-15 按组长要求调整）----
 *  - [arrivalTime] / [departureTime] 是**机位占用窗口**的起止，
 *    落库为 `t_flight.arrival_time` / `departure_time`，类型是 INT 当日分钟数（0–1439），
 *    分配引擎一律以这两个值为准；
 *  - [planTime] 不再单独录入，由 [direction] 派生：进港取到达、出港取离开，
 *    只用于列表展示和兜底，写入 `t_flight.sched_time`。
 */
private val TIME_RE = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

data class FlightDraft(
    val id: Long = 0L,
    val flightNo: String = "",
    val direction: String = DIR_IN, // 进港 / 出港
    val aircraft: String = "",      // 机型，如 A320neo
    val arrivalTime: String = "",   // HH:mm 计划到达（上轮挡 / 开始占用机位）
    val departureTime: String = "", // HH:mm 计划离开（撤轮挡 / 结束占用机位）
    val origin: String = "",        // 始发机场
    val dest: String = "",          // 目的机场
    val gateNo: String = "",        // 分配到的机位编号（分配后由 markFlightAssigned 回写）
    val status: String = ""         // 分配状态：待分配 / 已分配 / 已取消 / 已完成
) {
    /** 展示 / 兜底用的计划起降时刻：进港看到达，出港看离开 */
    val planTime: String
        get() = if (direction == DIR_IN) arrivalTime else departureTime

    /** 必填项是否都填了 */
    val filled: Boolean
        get() = flightNo.isNotBlank() && aircraft.isNotBlank() &&
                arrivalTime.isNotBlank() && departureTime.isNotBlank() &&
                origin.isNotBlank() && dest.isNotBlank()

    /** 时间格式是否合法（HH:mm）；空值视为合法，交由 filled 判断 */
    val timeValid: Boolean
        get() = (arrivalTime.isBlank() || TIME_RE.matches(arrivalTime)) &&
                (departureTime.isBlank() || TIME_RE.matches(departureTime))

    /** 占用窗口顺序是否合法：离开必须晚于到达 */
    val orderValid: Boolean
        get() {
            val a = timeToMinutes(arrivalTime) ?: return true
            val d = timeToMinutes(departureTime) ?: return true
            return d > a
        }

    /** 该行是否可以直接入库 */
    val valid: Boolean get() = filled && timeValid && orderValid

    /** 占用时长（分钟），用于展示与统计；时间非法时为 0 */
    val occupyMinutes: Int
        get() {
            val a = timeToMinutes(arrivalTime) ?: return 0
            val d = timeToMinutes(departureTime) ?: return 0
            return (d - a).coerceAtLeast(0)
        }

    companion object {
        const val DIR_IN = "进港"
        const val DIR_OUT = "出港"
    }
}

/** HH:mm -> 当日分钟数（0–1439）；格式非法返回 null */
fun timeToMinutes(hhmm: String): Int? {
    if (!TIME_RE.matches(hhmm)) return null
    return hhmm.substring(0, 2).toInt() * 60 + hhmm.substring(3, 5).toInt()
}

/** 当日分钟数（0–1439）-> HH:mm；超出范围按取模归一，避免算出 25:70 这种值 */
fun minutesToTime(min: Int): String {
    val m = ((min % 1440) + 1440) % 1440
    return "%02d:%02d".format(m / 60, m % 60)
}

// ========================= 日期工具 =========================

/** 相对今天偏移 n 天的日期，格式 yyyy-MM-dd（0=今天，1=明天，-1=昨天） */
fun dateOf(offsetDays: Int = 0): String {
    val c = Calendar.getInstance()
    c.add(Calendar.DAY_OF_YEAR, offsetDays)
    return "%04d-%02d-%02d".format(
        c.get(Calendar.YEAR),
        c.get(Calendar.MONTH) + 1,
        c.get(Calendar.DAY_OF_MONTH)
    )
}

/** 相对今天偏移 n 天的日期，紧凑格式 yyyyMMdd（用于「航班计划_20260917」这种文件名） */
fun dateCompactOf(offsetDays: Int = 0): String = dateOf(offsetDays).replace("-", "")

/** 当前时间 yyyy-MM-dd HH:mm */
fun timestampNow(): String {
    val c = Calendar.getInstance()
    return "%04d-%02d-%02d %02d:%02d:%02d".format(
        c.get(Calendar.YEAR),
        c.get(Calendar.MONTH) + 1,
        c.get(Calendar.DAY_OF_MONTH),
        c.get(Calendar.HOUR_OF_DAY),
        c.get(Calendar.MINUTE),
        c.get(Calendar.SECOND)
    )
}

/** 当前时间 HH:mm（首页"更新于"展示用） */
fun nowHHmm(): String {
    val c = Calendar.getInstance()
    return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}

/** 今天日期 + 中文星期，格式 yyyy-MM-dd 星期X（首页头部展示用） */
fun todayWithWeekday(): String {
    val c = Calendar.getInstance()
    val w = when (c.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SUNDAY -> "日"
        Calendar.MONDAY -> "一"
        Calendar.TUESDAY -> "二"
        Calendar.WEDNESDAY -> "三"
        Calendar.THURSDAY -> "四"
        Calendar.FRIDAY -> "五"
        Calendar.SATURDAY -> "六"
        else -> ""
    }
    return "${dateOf(0)} 星期$w"
}
