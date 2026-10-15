package net.micode.airportgate.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import net.micode.airportgate.Screen
import net.micode.airportgate.data.db.GateRepository
import net.micode.airportgate.model.FlightDraft
import net.micode.airportgate.model.FlightItem
import net.micode.airportgate.model.GateInfoStore
import net.micode.airportgate.model.dateOf
import net.micode.airportgate.model.nowHHmm
import net.micode.airportgate.model.todayWithWeekday
import net.micode.airportgate.ui.components.AppCard
import net.micode.airportgate.ui.components.FlightRow
import net.micode.airportgate.ui.components.SectionTitle
import net.micode.airportgate.ui.theme.BgGray
import net.micode.airportgate.ui.theme.Blue
import net.micode.airportgate.ui.theme.Green
import net.micode.airportgate.ui.theme.TextMain
import net.micode.airportgate.ui.theme.TextSub

// ========================= 主界面：航班机位分配 =========================

@Composable
fun HomeScreen(onNavigate: (Screen) -> Unit, showMsg: (String) -> Unit) {
    val context = LocalContext.current

    // 全部实时从数据库读（每次进入首页重组时都会重新读，导航回来自然刷新）
    val today = remember { dateOf(0) }
    val gates = remember { GateInfoStore.load(context) }
    val gateTotal = gates.size
    val gateAvailable = gates.count { it.available }
    val bridgeAvailable = gates.count { it.available && it.isBridge }
    val remoteAvailable = gates.count { it.available && !it.isBridge }
    // 机位编号 -> 是否靠桥（用于首页航班行展示「廊桥 / 远机位」）
    val gateBridgeMap = remember { gates.associate { it.code to it.isBridge } }

    // 今日航班：从 t_flight 读（已分配的带机位，待分配的机位为空）
    val todayFlights = remember { GateRepository.loadFlights(context, today) }
    val flightCount = todayFlights.size
    val inCount = todayFlights.count { it.direction == FlightDraft.DIR_IN }
    val outCount = todayFlights.count { it.direction == FlightDraft.DIR_OUT }
    val homeFlights = todayFlights.take(10).map { fd ->
        val gate = fd.gateNo
        val isBridge = if (gate.isNotBlank()) (gateBridgeMap[gate] ?: false) else false
        val desc = if (gate.isBlank()) "待分配"
        else if (isBridge) "廊桥 · 机型 ${fd.aircraft}"
        else "远机位 · 摆渡车"
        FlightItem(
            time = fd.arrivalTime.ifBlank { fd.departureTime.ifBlank { "—" } },
            flightNo = fd.flightNo,
            from = fd.origin,
            to = fd.dest,
            gate = gate.ifBlank { "—" },
            desc = desc,
            status = fd.status.ifBlank { "待分配" }
        )
    }

    Column(Modifier.fillMaxSize()) {
        // ---- 蓝色头部 ----
        Column(
            Modifier
                .fillMaxWidth()
                .background(Blue)
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Text("XX机场", color = Color(0xCCFFFFFF), fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text("航班机位分配", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                todayWithWeekday(),
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x33FFFFFF))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }

        // ---- 内容区 ----
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SectionTitle("核心数据概览") {
                Text("更新于 ${nowHHmm()}", fontSize = 12.sp, color = TextSub)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppCard(Modifier.weight(1f)) {
                    Text("今日航班总数", fontSize = 13.sp, color = TextSub)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$flightCount", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TextMain)
                        Spacer(Modifier.width(4.dp))
                        Text("架次", fontSize = 13.sp, color = TextSub)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("进港 $inCount · 出港 $outCount", fontSize = 12.sp, color = Green)
                }
                AppCard(Modifier.weight(1f)) {
                    Text("可用机位", fontSize = 13.sp, color = TextSub)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$gateAvailable", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TextMain)
                        Spacer(Modifier.width(4.dp))
                        Text("/ $gateTotal", fontSize = 14.sp, color = TextSub)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("廊桥 $bridgeAvailable · 远机位 $remoteAvailable", fontSize = 12.sp, color = TextSub)
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle("今日动态") {
                Text(
                    "查看全部 >",
                    fontSize = 13.sp,
                    color = Blue,
                    modifier = Modifier.clickable { onNavigate(Screen.Assign) }
                )
            }
            Spacer(Modifier.height(10.dp))
            AppCard {
                if (homeFlights.isEmpty()) {
                    Text(
                        "今日暂无航班数据，请先到「航班导入」录入",
                        fontSize = 13.sp,
                        color = TextSub,
                        modifier = Modifier.padding(vertical = 14.dp)
                    )
                } else {
                    homeFlights.forEachIndexed { index, f ->
                        FlightRow(f)
                        if (index != homeFlights.lastIndex) HorizontalDivider(color = Color(0xFFF0F1F3))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
