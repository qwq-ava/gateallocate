package net.micode.airportgate.domain

import android.content.Context
import net.micode.airportgate.data.db.GateRepository
import net.micode.airportgate.model.DataSync
import net.micode.airportgate.model.FlightDraft
import net.micode.airportgate.model.nextDate
import net.micode.airportgate.model.normalizeTime

/**
 * 控制层（业务逻辑层）：航班导入业务协调者。
 *
 * 承接「校验 -> 落库航班 -> 写批次记录 -> 触发分配重排」的完整流程，
 * 使 ui/import/ImportScreen 只负责收集表单与提示，不再包含导入协调逻辑。
 */
data class ImportResult(
    val savedCount: Int,
    val skippedCount: Int,
    val source: String
)

object ImportService {
    /**
     * 提交一次航班录入 / 更改。
     * @param context  上下文
     * @param date     航班日期 yyyy-MM-dd
     * @param rows     表单中的航班（含有效与无效）
     * @param isCreate true=手工录入（CREATE），false=信息更改（EDIT）
     * @return 结果供界面提示与刷新
     */
    fun submit(context: Context, date: String, rows: List<FlightDraft>, isCreate: Boolean): ImportResult {
        val source = if (isCreate) "手工录入" else "信息更改"

        // 跨天航班（离开早于到达，如 22:00 → 次日 02:00）拆成两段分别落库：
        //   前段 = 当天「到达 → 24:00」，后段 = 次日「0:00 → 离开」，各自写入对应航班日期
        // 已飞完（locked）的航班不参与保存，防止覆盖历史数据
        val expanded = mutableListOf<Pair<String, FlightDraft>>()
        for (r in rows) {
            if (r.locked) continue
            // 落库前把宽松写法（如 6 / 615 / 0615）规整成标准 HH:mm，避免数据库里残留裸数字
            val norm = r.copy(
                arrivalTime = normalizeTime(r.arrivalTime) ?: r.arrivalTime,
                departureTime = normalizeTime(r.departureTime) ?: r.departureTime
            )
            if (norm.valid && norm.isOvernight) {
                expanded.add(date to norm.copy(departureTime = "24:00"))
                expanded.add(nextDate(date) to norm.copy(arrivalTime = "00:00"))
            } else {
                expanded.add(date to norm)
            }
        }

        // 按生效日期分组落库：普通航班落在导入日；跨天后段落在次日
        val byDate = expanded.groupBy({ it.first }, { it.second })
        var saved = 0
        byDate.forEach { (d, rs) ->
            saved += GateRepository.saveFlights(context, d, rs, source = source)
        }

        val validCount = rows.count { it.valid && !it.locked }
        val skipped = rows.size - validCount
        GateRepository.addImportBatch(context, date, source, rows.size, saved, skipped)
        DataSync.bump()
        return ImportResult(saved, skipped, source)
    }
}
