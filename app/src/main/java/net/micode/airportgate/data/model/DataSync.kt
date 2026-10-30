package net.micode.airportgate.data.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 数据版本触发器。
 *
 * 航班导入（ImportScreen 保存航班）或机位维护（GateInfoScreen 保存机位）后调用 [bump]，
 * 分配界面（AssignScreen）监听 [version] 变化后自动重新分配。
 * 用 Compose 的 observable State 实现，无需手动传参或回调。
 */
object DataSync {
    /** 数据版本号：任何底层数据（航班 / 机位）变更后自增 */
    var version by mutableStateOf(0)
        private set

    /** 通知「数据已变更」，触发分配界面重新分配 */
    fun bump() {
        version += 1
    }
}
