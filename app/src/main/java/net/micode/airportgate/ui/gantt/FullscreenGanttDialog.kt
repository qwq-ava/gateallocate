package net.micode.airportgate.ui.gantt

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.micode.airportgate.data.model.GateSlot
import net.micode.airportgate.data.model.dateOf
import net.micode.airportgate.ui.theme.BgGray
import net.micode.airportgate.ui.theme.TextMain
import net.micode.airportgate.ui.theme.TextSub

/** 全屏甘特图：横屏 + 同一张连续日期轴，一天 24 小时铺满一屏，其余日期左右滑动 */
@Composable
fun FullscreenGanttDialog(
    gates: List<String>,
    slots: List<GateSlot>,
    dates: List<String>,
    onSlotClick: (GateSlot) -> Unit,
    onClose: () -> Unit,
    showNowLine: Boolean = true,
    today: String = dateOf(0)
) {
    val zoom = rememberGanttZoomState()
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = BgGray) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Surface(color = Color.White) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Outlined.FullscreenExit, contentDescription = "退出全屏", tint = TextMain)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                "机位分配甘特图 · 连续日期全览",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMain
                            )
                            Text(
                                "每天 24 小时依次相接（共 ${dates.size} 天）· 左右滑动换日期 · + / − 放大文字 · 点击色块看详情",
                                fontSize = 11.sp,
                                color = TextSub
                            )
                        }
                        GanttLegend()
                        Spacer(Modifier.width(8.dp))
                        ZoomControls(zoom)
                        Spacer(Modifier.width(8.dp))
                    }
                }
                Surface(
                    Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    color = Color.White,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        GateGanttChart(
                            gates = gates,
                            slots = slots,
                            dates = dates,
                            fitWidth = true,
                            onSlotClick = onSlotClick,
                            zoomState = zoom,
                            modifier = Modifier.padding(12.dp),
                            rowHeight = 34.dp,
                            showNowLine = showNowLine,
                            today = today
                        )
                    }
                }
            }
        }
    }
}
