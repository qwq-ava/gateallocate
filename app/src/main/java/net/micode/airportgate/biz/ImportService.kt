package net.micode.airportgate.biz

import android.content.Context
import net.micode.airportgate.data.db.GateRepository
import net.micode.airportgate.data.model.DataSync
import net.micode.airportgate.data.model.FlightDraft

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
        val valid = rows.filter { it.valid }
        val skipped = rows.size - valid.size
        val source = if (isCreate) "手工录入" else "信息更改"
        val n = GateRepository.saveFlights(context, date, valid, source = source)
        GateRepository.addImportBatch(context, date, source, rows.size, n, skipped)
        DataSync.bump()
        return ImportResult(n, skipped, source)
    }
}
