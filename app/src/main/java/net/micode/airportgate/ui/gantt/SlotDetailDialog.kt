package net.micode.airportgate.ui.gantt

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.micode.airportgate.data.model.GateSlot
import net.micode.airportgate.data.model.fmtTime
import net.micode.airportgate.ui.theme.Blue
import net.micode.airportgate.ui.theme.ConflictRed
import net.micode.airportgate.ui.theme.TextMain
import net.micode.airportgate.ui.theme.TextSub

/** 色块详情 */
@Composable
fun SlotDetailDialog(slot: GateSlot, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(slot.flightNo, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                DetailLine("机位", slot.gate)
                DetailLine("占用时段", "${fmtTime(slot.startMin)} - ${fmtTime(slot.endMin)}")
                DetailLine("占用时长", "${slot.endMin - slot.startMin} 分钟")
                DetailLine("机位类型", if (slot.isBridge) "廊桥机位" else "远机位")
                DetailLine("保障状态", if (slot.finished) "已完成" else "进行中")
                if (slot.conflict) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = ConflictRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "该时段与同机位其他航班重叠，请调整分配",
                            fontSize = 13.sp,
                            color = ConflictRed,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("关闭", color = Blue, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(label, fontSize = 13.sp, color = TextSub, modifier = Modifier.width(72.dp))
        Text(value, fontSize = 14.sp, color = TextMain, fontWeight = FontWeight.Bold)
    }
}
