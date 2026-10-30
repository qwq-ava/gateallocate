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
private val TIME_RE = Regex("^([01]\\d|2[0-3]):[0-5]\\d$|^24:00$")

data class FlightDraft(
    val id: Long = 0L,
    val flightNo: String = "",
    val direction: String = DIR_IN, // 进港 / 出港
    val flightType: String = TYPE_DOMESTIC, // 国内 / 国际（航班属性标注，便于区分保障流程）
    val aircraft: String = "",      // 机型，如 A320neo
    val arrivalTime: String = "",   // HH:mm 计划到达（上轮挡 / 开始占用机位）
    val departureTime: String = "", // HH:mm 计划离开（撤轮挡 / 结束占用机位）
    val origin: String = "",        // 始发机场
    val dest: String = "",          // 目的机场
    val gateNo: String = "",        // 分配到的机位编号（分配后由 markFlightAssigned 回写）
    val status: String = "",        // 分配状态：待分配 / 已分配 / 已取消 / 已完成
    val locked: Boolean = false     // UI/校验专用：已飞完的航班不可修改（不落库，仅控制可编辑性）
) {
    /** 展示 / 兜底用的计划起降时刻：进港看到达，出港看离开 */
    val planTime: String
        get() = if (direction == DIR_IN) arrivalTime else departureTime

    /** 必填项是否都填了 */
    val filled: Boolean
        get() = flightNo.isNotBlank() && aircraft.isNotBlank() &&
                arrivalTime.isNotBlank() && departureTime.isNotBlank() &&
                origin.isNotBlank() && dest.isNotBlank()

    /** 时间格式是否合法（支持宽松写法：06:15 / 24:00 / 6(=06:00) / 15(=15:00) / 615(=06:15) / 0615）；空值视为合法，交由 filled 判断 */
    val timeValid: Boolean
        get() = (arrivalTime.isBlank() || normalizeTime(arrivalTime) != null) &&
                (departureTime.isBlank() || normalizeTime(departureTime) != null)

    /** 占用窗口顺序是否合法：离开必须晚于到达 */
    val orderValid: Boolean
        get() {
            val a = timeToMinutes(arrivalTime) ?: return true
            val d = timeToMinutes(departureTime) ?: return true
            return d > a
        }

    /** 是否跨天航班：离开时刻早于到达时刻（如 22:00 → 次日 02:00），需拆成两段分别落库 */
    val isOvernight: Boolean
        get() {
            val a = timeToMinutes(arrivalTime) ?: return false
            val d = timeToMinutes(departureTime) ?: return false
            return d < a
        }

    /**
     * 该行是否可以直接入库。
     * 注意：跨天航班（isOvernight，如 23:00 到次日 02:00）的离开时刻必然早于到达时刻，
     * 这类航班是合法的，会在保存时自动拆分为两天两段，因此 orderValid 不成立时
     * 只要 isOvernight 成立仍视为可入库。
     */
    val valid: Boolean get() = filled && timeValid && (orderValid || isOvernight)

    /** 占用时长（分钟），用于展示与统计；时间非法时为 0。
     *  跨天航班（离开早于到达，如 23:00→次日 02:00）按「次日时刻 + 24h」折算，返回完整占用分钟数。 */
    val occupyMinutes: Int
        get() {
            val a = timeToMinutes(arrivalTime) ?: return 0
            val d = timeToMinutes(departureTime) ?: return 0
            val raw = d - a
            return if (raw < 0) raw + 1440 else raw
        }

    companion object {
        const val DIR_IN = "进港"
        const val DIR_OUT = "出港"
        const val TYPE_DOMESTIC = "国内"
        const val TYPE_INTL = "国际"
    }
}

/**
 * 把用户各种时间写法规整成标准 HH:mm（含 24:00）。无法解析返回 null。
 * 支持：
 *   - 标准：06:15、24:00
 *   - 纯数字（去掉非数字后按长度规整）：
 *       1 位 = 小时补 00 → 6 → 06:00
 *       2 位 = 小时补 00 → 15 → 15:00
 *       3 位 = HMM      → 615 → 06:15、830 → 08:30
 *       4 位 = HHMM     → 0615 → 06:15、1830 → 18:30
 * 非法值（如 25:00、99、12345）返回 null。
 */
fun normalizeTime(raw: String): String? {
    val s = raw.trim()
    if (s.contains(":")) {
        return if (TIME_RE.matches(s)) s else null
    }
    val d = s.filter { it.isDigit() }
    if (d.isEmpty() || d.length > 4) return null
    val hh: Int
    val mm: Int
    when (d.length) {
        1 -> { hh = d.toInt(); mm = 0 }
        2 -> { hh = d.toInt(); mm = 0 }
        3 -> { hh = d[0].digitToInt(); mm = d.substring(1).toInt() }
        else -> { hh = d.substring(0, 2).toInt(); mm = d.substring(2).toInt() }
    }
    if (hh > 24 || mm > 59) return null
    if (hh == 24 && mm != 0) return null
    return "%02d:%02d".format(hh, mm)
}

/** HH:mm -> 当日分钟数（0–1439，24:00=1440）；格式（含宽松写法）非法返回 null */
fun timeToMinutes(hhmm: String): Int? {
    val norm = normalizeTime(hhmm) ?: return null
    if (norm == "24:00") return 1440
    return norm.substring(0, 2).toInt() * 60 + norm.substring(3, 5).toInt()
}

/** 当日分钟数（0–1439）-> HH:mm；超出范围按取模归一，避免算出 25:70 这种值 */
fun minutesToTime(min: Int): String {
    if (min == 1440) return "24:00"
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

/** 在 yyyy-MM-dd 日期上 +1 天，返回新的 yyyy-MM-dd（用于跨天航班的次段落库） */
fun nextDate(date: String): String {
    val p = date.split("-")
    if (p.size != 3) return date
    val c = Calendar.getInstance()
    c.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
    c.add(Calendar.DAY_OF_YEAR, 1)
    return "%04d-%02d-%02d".format(
        c.get(Calendar.YEAR),
        c.get(Calendar.MONTH) + 1,
        c.get(Calendar.DAY_OF_MONTH)
    )
}

/** 在 yyyy-MM-dd 日期上 -1 天，返回新的 yyyy-MM-dd（用于跨夜前段在「前一天」的回读） */
fun prevDate(date: String): String {
    val p = date.split("-")
    if (p.size != 3) return date
    val c = Calendar.getInstance()
    c.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
    c.add(Calendar.DAY_OF_YEAR, -1)
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

/**
 * 判断某航班是否已飞完、不可再修改：
 *  - 日期早于今天：视为已飞完；
 *  - 日期为今天：离开时刻（departureTime）不晚于当前时刻即为已飞完；
 *  - 未来日期：尚未飞行，可修改。
 * 时间无法解析（空/非法）时按"未飞完"处理，避免误锁。
 */
fun isFlightFlown(date: String, departureTime: String): Boolean {
    val dep = timeToMinutes(departureTime) ?: return false
    val today = dateOf(0)
    return when {
        date < today -> true
        date == today -> dep <= nowMinuteOfDay()
        else -> false
    }
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
