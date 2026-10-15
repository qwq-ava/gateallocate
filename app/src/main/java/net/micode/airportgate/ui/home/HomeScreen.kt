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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import net.micode.airportgate.Screen
import net.micode.airportgate.model.FlightItem
import net.micode.airportgate.model.GateInfoStore
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
    // 与机位信息表联动：每次进入首页重新读一次，改完机位回来即可看到新数字
    val context = LocalContext.current
    val gates = GateInfoStore.load(context)
    val gateTotal = gates.size
    val gateAvailable = gates.count { it.available }
    val bridgeAvailable = gates.count { it.available && it.isBridge }
    val remoteAvailable = gates.count { it.available && !it.isBridge }

    val homeFlights = listOf(
        FlightItem("09:42", "MU5137", "上海浦东", "广州白云", "208", "廊桥 · 预计靠桥 10:20", "已分配"),
        FlightItem("09:35", "CA1856", "上海浦东", "北京首都", "B27", "廊桥 · 机型 A330-300", "已分配"),
        FlightItem("09:15", "CZ3802", "上海浦东", "深圳宝安", "C05", "远机位 · 摆渡车 12 分钟", "已分配"),
        FlightItem("08:56", "3U8891", "上海浦东", "重庆江北", "T2-14", "保障完成 · 桥载电源已接", "已完成")
    )

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
                "2026-09-17 星期四",
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
                Text("更新于 09:45", fontSize = 12.sp, color = TextSub)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppCard(Modifier.weight(1f)) {
                    Text("今日航班总数", fontSize = 13.sp, color = TextSub)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("286", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TextMain)
                        Spacer(Modifier.width(4.dp))
                        Text("架次", fontSize = 13.sp, color = TextSub)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("较昨日 +14 · 进出港均衡", fontSize = 12.sp, color = Green)
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
                homeFlights.forEachIndexed { index, f ->
                    FlightRow(f)
                    if (index != homeFlights.lastIndex) HorizontalDivider(color = Color(0xFFF0F1F3))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
